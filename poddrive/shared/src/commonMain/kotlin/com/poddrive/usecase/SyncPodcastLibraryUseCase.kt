package com.poddrive.usecase

import com.poddrive.api.SpotifyLibraryApiClient
import com.poddrive.currentTimeMillis
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository
import com.poddrive.repository.PodcastRepository
import kotlinx.coroutines.CancellationException

/**
 * Fetches followed Spotify podcasts and their unheard episodes, persists to SQLDelight,
 * then returns the full library with unheard counts for immediate UI display. (FR4, FR7, FR8)
 *
 * Sync strategy: full sync — fetches all followed shows + all episodes per show.
 * Background-sync optimization (batch on BT connect, staleness indicator) is Story 2.5.
 *
 * Episode "unheard" detection: uses `resume_point.fully_played == false` from Spotify API.
 * A null `resume_point` (no play history) is treated as unheard.
 *
 * Tier inheritance: episodes inherit the parent podcast's tier at sync time.
 * The `episode_tiers` table stores per-episode tier overrides (managed in Stories 2.2+).
 *
 * Never throws — all errors returned as [Result.Failure].
 * [Source: architecture.md#Use Case rule, AR3]
 */
class SyncPodcastLibraryUseCase(
    private val libraryApiClient: SpotifyLibraryApiClient,
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository
) {
    suspend fun execute(): Result<List<Podcast>, AppError> {
        return try {
            // 1. Fetch all followed shows
            val showsResult = libraryApiClient.fetchFollowedShows()
            if (showsResult is Result.Failure) return showsResult

            val shows = (showsResult as Result.Success).value
            val now = currentTimeMillis()

            // 2. Map Spotify shows → Podcast domain models
            val podcasts = shows.map { show ->
                Podcast(
                    id = show.uri,  // "spotify:show:xxx"
                    name = show.name,
                    description = show.description,
                    imageUrl = show.images.maxByOrNull { it.width ?: 0 }?.url ?: "",
                    episodeCount = show.totalEpisodes,
                    unheardEpisodeCount = 0,  // placeholder; recalculated by repository query
                    tier = EpisodeTier.NONE,  // new podcasts default to NONE; existing tier preserved by repo
                    lastSyncedAt = now
                )
            }

            // 3. Upsert podcasts (preserves existing tiers)
            podcastRepository.upsertAll(podcasts)

            // 4. For each show, fetch episodes and upsert
            for (show in shows) {
                val episodesResult = libraryApiClient.fetchShowEpisodes(show.id)
                if (episodesResult is Result.Failure) {
                    // Non-fatal: continue with other shows, return partial results
                    continue
                }
                val episodes = (episodesResult as Result.Success).value
                    .map { ep ->
                        Episode(
                            id = ep.uri,  // "spotify:episode:xxx"
                            podcastId = show.uri,
                            title = ep.name,
                            durationMs = ep.durationMs,
                            publishedAt = parseSpotifyDateToEpochMs(ep.releaseDate),
                            isPlayed = ep.resumePoint?.fullyPlayed ?: false,
                            tier = EpisodeTier.NONE  // tier managed separately in episode_tiers table
                        )
                    }
                episodeRepository.upsertAll(episodes)
            }

            // 5. Return updated library with unheard counts from DB
            Result.Success(podcastRepository.getAllWithUnheardCount())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }
}

/**
 * Parses Spotify's `release_date` field to epoch milliseconds.
 * Handles three precision variants: "YYYY-MM-DD", "YYYY-MM", "YYYY".
 * Uses the proleptic Gregorian algorithm — no external library required.
 */
internal fun parseSpotifyDateToEpochMs(releaseDate: String): Long {
    val parts = releaseDate.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return 0L
    if (year <= 0) return 0L  // treat year 0000 / BC dates as unknown (avoids negative epoch)
    val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
    // Proleptic Gregorian → Julian Day Number (standard Fliegel-Van Flandern algorithm)
    val a = (14 - month) / 12
    val y = year + 4800 - a
    val m = month + 12 * a - 3
    val jdn = day + (153 * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045L
    val epochDays = jdn - 2440588L  // JDN 2440588 = 1970-01-01
    return epochDays * 24L * 60L * 60L * 1000L
}
