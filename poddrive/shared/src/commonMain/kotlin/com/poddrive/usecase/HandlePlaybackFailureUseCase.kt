package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.Result
import com.poddrive.session.DriveSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Handles Spotify playback failures and deep-link handoff timeouts (FR25, NFR12, Story 4.5).
 *
 * Responsibilities:
 * 1. Wraps Spotify handoff calls in a [HANDOFF_TIMEOUT_MS]-millisecond timeout (UX-DR10).
 * 2. On timeout: dispatches [DriveSessionEvent.PlaybackFailed] with [AppError.SpotifyHandoffTimeout]
 *    → [DriveSessionManager] transitions to [com.poddrive.domain.model.DriveSessionState.Error].
 * 3. On other exceptions: wraps in [AppError.Unknown] and dispatches [DriveSessionEvent.PlaybackFailed].
 * 4. On retry: dispatches [DriveSessionEvent.RetryPlayback]
 *    → state machine transitions [DriveSessionState.Error] → [DriveSessionState.Activating].
 *
 * **Voice notification** "Could not connect. Tap to retry." (FR25, UX-DR8) is stored as [FAILURE_SCRIPT].
 * TTS wiring is deferred to Story 3.4 (MVP Phase 2).
 *
 * **Drive UI** ERROR state with retry button is Story 3.6.
 *
 * Never throws — on failure a [DriveSessionEvent.PlaybackFailed] is dispatched and
 * [Result.Failure] is returned.
 * [Source: epics.md#Story 4.5, architecture.md#AR3, AR6]
 */
class HandlePlaybackFailureUseCase(private val driveSessionManager: DriveSessionManager) {

    /**
     * Executes [handoffBlock] with a [HANDOFF_TIMEOUT_MS]-ms timeout.
     *
     * On success: returns [Result.Success] without dispatching any event.
     * On [TimeoutCancellationException]: dispatches [PlaybackFailed(SpotifyHandoffTimeout)],
     *   returns [Result.Failure(AppError.SpotifyHandoffTimeout)].
     * On other exceptions: dispatches [PlaybackFailed(Unknown(e))],
     *   returns [Result.Failure(AppError.Unknown)].
     * [CancellationException] (coroutine cancellation) is always re-thrown — never swallowed.
     */
    suspend fun <T> awaitHandoffOrTimeout(handoffBlock: suspend () -> T): Result<T, AppError> {
        return try {
            val result = withTimeout(HANDOFF_TIMEOUT_MS) { handoffBlock() }
            Result.Success(result)
        } catch (e: TimeoutCancellationException) {
            // TimeoutCancellationException is a subtype of CancellationException, so it
            // MUST be caught before the generic CancellationException guard below.
            val error = AppError.SpotifyHandoffTimeout
            driveSessionManager.dispatch(DriveSessionEvent.PlaybackFailed(error))
            Result.Failure(error)
        } catch (e: CancellationException) {
            throw e  // Propagate coroutine cancellation — never swallow
        } catch (e: Exception) {
            val error = AppError.Unknown(e)
            driveSessionManager.dispatch(DriveSessionEvent.PlaybackFailed(error))
            Result.Failure(error)
        }
    }

    /**
     * Dispatches [DriveSessionEvent.RetryPlayback] to the session manager.
     * State transitions: [DriveSessionState.Error] → [DriveSessionState.Activating].
     * The caller is responsible for re-initiating the Spotify deep-link handoff (Story 4.1).
     */
    fun retry() {
        driveSessionManager.dispatch(DriveSessionEvent.RetryPlayback)
    }

    companion object {
        /** 3-second Spotify deep-link handoff timeout (UX-DR10, Story 4.5). */
        const val HANDOFF_TIMEOUT_MS = 3_000L

        /**
         * Voice notification script for playback failure (FR25, UX-DR8).
         * Consumed by TtsEngine when Story 3.4 (deferred MVP Phase 2) is implemented.
         */
        const val FAILURE_SCRIPT = "Could not connect. Tap to retry."
    }
}
