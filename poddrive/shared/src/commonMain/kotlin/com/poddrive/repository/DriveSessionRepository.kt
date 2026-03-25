package com.poddrive.repository

import com.poddrive.domain.model.DriveSessionState

/**
 * Data access interface for the `drive_sessions` SQLDelight table.
 * Implementation lives in [com.poddrive.storage.SqlDelightDriveSessionRepository].
 * [Source: architecture.md#Repository Pattern, AR6 persist-before-emit]
 */
interface DriveSessionRepository {
    /**
     * Persists the new state for an existing session.
     * MUST be called BEFORE emitting the new state to observers (AR6 persist-before-emit).
     */
    fun saveState(sessionId: String, state: DriveSessionState)

    /** Returns the [DriveSessionState] from the most recently started drive session, or null. */
    fun loadLatestState(): DriveSessionState?

    /**
     * Creates a new session row and persists its initial state.
     * Called once at session start when [sessionId] is first assigned.
     */
    fun saveNewSession(sessionId: String, startedAtMs: Long, state: DriveSessionState)

    /** Marks a session as ended with the given [endedAtMs] timestamp. */
    fun endSession(sessionId: String, endedAtMs: Long)

    /** Deletes all drive session rows (used by RevokeSpotifyAccessUseCase). */
    fun deleteAll()
}
