package com.poddrive.repository

import com.poddrive.domain.model.DriveSessionState

/**
 * Interface for persisting and restoring drive session state.
 * Supports the persist-before-emit rule (AR6) and OS-kill recovery (NFR11).
 * Implementation lives in `com.poddrive.storage`.
 * [Source: architecture.md#Repository Pattern, AR6]
 * [Story 4.2]
 */
interface DriveSessionRepository {
    /**
     * Persist [state] for the given session to SQLDelight.
     * Called by DriveSessionManager BEFORE emitting the new state to observers.
     */
    fun persistState(sessionId: String, state: DriveSessionState)

    /**
     * Load the most recent persisted session state from SQLDelight.
     * Returns null if no session has been persisted yet.
     * Used by RestoreSessionUseCase on app relaunch (NFR11).
     */
    fun loadLatestSessionState(): DriveSessionState?
}
