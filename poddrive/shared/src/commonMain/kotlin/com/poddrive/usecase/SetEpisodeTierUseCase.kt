package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Result
import com.poddrive.repository.PodcastRepository
import kotlinx.coroutines.CancellationException

/**
 * Persists a LOVED/LIKED tier (or NONE to remove) for a given podcast.
 *
 * Updates the `podcasts.tier` column via [PodcastRepository.setPodcastTier].
 * The `episode_tiers` table (per-episode overrides) is managed separately — this use case
 * only changes the podcast-level tier which drives the drive briefing priority (FR15).
 *
 * Never throws — all errors returned as [Result.Failure].
 * Data stored on-device only — never transmitted to any server (NFR7).
 * [Source: epics.md#Story 2.2, AR5, AR10]
 */
class SetEpisodeTierUseCase(
    private val podcastRepository: PodcastRepository
) {
    /**
     * Sets the tier for podcast [podcastId] to [tier].
     * Pass [EpisodeTier.NONE] to remove the current tag.
     */
    fun execute(podcastId: String, tier: EpisodeTier): Result<Unit, AppError> {
        return try {
            podcastRepository.setPodcastTier(podcastId, tier)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }
}
