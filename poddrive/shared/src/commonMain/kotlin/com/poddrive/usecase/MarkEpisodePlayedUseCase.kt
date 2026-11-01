package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository
import kotlinx.coroutines.CancellationException

/**
 * Marks a single episode as played in the local SQLDelight database (Story 4.1, AC4).
 *
 * Called by [com.poddrive.android.ui.drive.DriveSessionViewModel.clearHandingOff] after a
 * successful Spotify deep-link handoff, so the episode is removed from the drive queue on
 * the next queue reload.
 *
 * Follows the [com.poddrive.usecase.SetEpisodeTierUseCase] pattern:
 * synchronous [execute] returning [Result]<Unit, AppError>.
 * Never throws — all errors returned as [Result.Failure].
 * [Source: architecture.md#Use Case rule, AR3, AR5; Story 4.1]
 */
class MarkEpisodePlayedUseCase(private val episodeRepository: EpisodeRepository) {

    /**
     * Marks the episode with [episodeId] as played.
     * Returns [Result.Success] on success, or [Result.Failure] wrapping the cause as
     * [AppError.Unknown] if the repository throws.
     */
    fun execute(episodeId: String): Result<Unit, AppError> {
        return try {
            episodeRepository.markPlayed(episodeId)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }
}
