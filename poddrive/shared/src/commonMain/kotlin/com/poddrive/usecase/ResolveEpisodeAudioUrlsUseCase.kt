package com.poddrive.usecase

import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.Podcast
import com.poddrive.repository.EpisodeRepository
import com.poddrive.rss.RssEpisode
import com.poddrive.rss.RssParser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/**
 * Resolves direct audio URLs for episodes by fetching and parsing the podcast's RSS feed.
 *
 * - If `podcast.rssFeedUrl` is null → returns immediately (AC3 guard).
 * - Fetches `GET {rssFeedUrl}`; non-200 or network exceptions → returns without updating (AC3).
 * - For each unresolved episode, finds the best-matching RSS `<item>` by normalized title
 *   containment + ±2 day date tolerance. Exact title match takes priority (AC4).
 * - Writes the `<enclosure url>` (or `<link>` fallback) to `EpisodeRepository` (AC5).
 * - Episodes already having a non-null `audio_url` are skipped via the DB query (AC6).
 * - All errors swallowed — method never throws (AC3, AC5).
 * - [CancellationException] is re-thrown.
 *
 * [Source: Story 4.9, ADR-002]
 */
open class ResolveEpisodeAudioUrlsUseCase(
    private val episodeRepository: EpisodeRepository,
    private val httpClient: HttpClient,
    private val rssParser: RssParser
) {
    open suspend fun execute(podcast: Podcast) {
        if (podcast.rssFeedUrl == null) return

        try {
            val response = httpClient.get(podcast.rssFeedUrl)
            if (!response.status.isSuccess()) return

            val body = response.bodyAsText()
            val rssItems = rssParser.parse(body)
            if (rssItems.isEmpty()) return

            val unresolvedEpisodes = episodeRepository.getUnresolvedEpisodesForPodcast(podcast.id)
            for (episode in unresolvedEpisodes) {
                val match = findMatch(episode, rssItems) ?: continue
                val audioUrl = match.enclosureUrl ?: continue
                episodeRepository.updateAudioUrl(episode.id, audioUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // AC3, AC5: swallow all errors — never propagate to the user
        }
    }

    /**
     * Finds the best-matching [RssEpisode] for the given [episode] using:
     * 1. Normalized title containment (case-insensitive, punctuation stripped)
     * 2. Publication date within ±2 days
     *
     * Exact title match takes priority over partial containment. Returns null if no match.
     */
    private fun findMatch(episode: Episode, rssItems: List<RssEpisode>): RssEpisode? {
        val normalizedEpisode = episode.title.normalize()
        val dateTolerance = 2L * 24L * 60L * 60L * 1000L  // ±2 days in ms

        // Separate items with a known date from those with an unparseable date (pubDateMs == 0L).
        // Dated items are checked against the ±2-day window. Undated items are only used as a
        // last-resort fallback so that a malformed RSS date does not silently match every episode.
        val datedCandidates = rssItems.filter { rss ->
            rss.pubDateMs != 0L && abs(rss.pubDateMs - episode.publishedAt) <= dateTolerance
        }
        val undatedCandidates = rssItems.filter { rss -> rss.pubDateMs == 0L }

        // Priority 1: exact title match within date-bounded candidates
        datedCandidates.firstOrNull { it.title.normalize() == normalizedEpisode }
            ?.let { return it }

        // Priority 2: containment match within date-bounded candidates
        datedCandidates.firstOrNull { rss ->
            val n = rss.title.normalize()
            n.contains(normalizedEpisode) || normalizedEpisode.contains(n)
        }?.let { return it }

        // Priority 3: exact title match within undated candidates (date unknown — fallback only)
        undatedCandidates.firstOrNull { it.title.normalize() == normalizedEpisode }
            ?.let { return it }

        // Priority 4: containment match within undated candidates
        return undatedCandidates.firstOrNull { rss ->
            val n = rss.title.normalize()
            n.contains(normalizedEpisode) || normalizedEpisode.contains(n)
        }
    }

    private fun String.normalize(): String =
        lowercase().replace(Regex("[^\\w\\s]"), "").trim()
}
