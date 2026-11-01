package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.EpisodeQueue
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the prioritised episode queue for a drive session (FR22, Story 4.2).
 *
 * Ordering: LOVED episodes first, then LIKED, then NONE. Within each tier, episodes
 * are sorted by most recent [com.poddrive.domain.model.Episode.publishedAt] descending.
 * This ordering is defined in and delegated to [EpisodeRepository.getUnplayedOrderedByTier]
 * which uses the `selectUnplayedOrderedByTier` SQLDelight query.
 *
 * **Queue persistence:** The underlying `episodes` and `episode_tiers` SQLDelight tables
 * are already persisted on every library sync. No separate queue table is needed —
 * the queue is always re-derivable and survives app backgrounding (AR3, AR6).
 *
 * Never throws — all errors returned as [Result.Failure].
 * [Source: architecture.md#Use Case rule, AR3]
 */
class BuildEpisodeQueueUseCase(private val episodeRepository: EpisodeRepository) {

    /**
     * Returns a [Result.Success] containing the prioritised [EpisodeQueue],
     * or a [Result.Failure] wrapping the cause as [AppError.Unknown].
     */
    suspend fun execute(): Result<EpisodeQueue, AppError> {
        return try {
            // P-13: getUnplayedOrderedByTier() is a synchronous SQLDelight call — switch to
            // Dispatchers.IO so it never blocks the main thread (AR3).
            val episodes = withContext(Dispatchers.IO) {
                episodeRepository.getUnplayedOrderedByTier()
            }
            Result.Success(EpisodeQueue(episodes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }
}
