package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository

/**
 * Builds the ordered episode queue for a drive session.
 *
 * Ordering contract (FR22, AC1, AC2):
 *   1. All unheard LOVED episodes, sorted by publishedAt DESC (most recent first)
 *   2. All unheard LIKED episodes, sorted by publishedAt DESC
 *   NONE-tier episodes are excluded from the drive queue.
 *
 * The SQL query `selectUnplayedOrderedByTier` in Episode.sq already applies this order;
 * this use case filters out NONE-tier episodes and returns the Result-wrapped list.
 *
 * Never throws — all errors returned as [Result.Failure].
 * [Source: architecture.md#Use Case rule, AR3]
 * [Story 4.2]
 */
class BuildEpisodeQueueUseCase(private val episodeRepository: EpisodeRepository) {

    /**
     * Execute the queue build.
     * Returns a [Result.Success] with the ordered list (LOVED then LIKED, each newest-first).
     * Returns an empty list if no LOVED or LIKED episodes are available.
     * Returns [Result.Failure] only on unexpected repository exceptions.
     */
    fun execute(): Result<List<Episode>, AppError> {
        return try {
            val ordered = episodeRepository.getUnplayedOrderedByTier()
            // Filter out NONE — only LOVED and LIKED belong in the drive queue
            val queue = ordered.filter { it.tier != EpisodeTier.NONE }
            Result.Success(queue)
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }
}
