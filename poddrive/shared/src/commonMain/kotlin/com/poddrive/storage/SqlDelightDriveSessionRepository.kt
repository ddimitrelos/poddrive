package com.poddrive.storage

import com.poddrive.currentTimeMillis
import com.poddrive.db.PodDrive
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.BriefingScript
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.repository.DriveSessionRepository

/**
 * SQLDelight-backed implementation of [DriveSessionRepository].
 * Persists [DriveSessionState] as a TEXT column in `drive_sessions`.
 * State serialisation is intentionally simple — fine-grained state data (episode, position)
 * is loaded from `playback_state` when restored (Stories 4.3–4.4).
 * [Source: architecture.md#Repository Pattern, DriveSession.sq]
 */
class SqlDelightDriveSessionRepository(private val db: PodDrive) : DriveSessionRepository {

    override fun saveState(sessionId: String, state: DriveSessionState) {
        db.driveSessionQueries.updateSessionState(
            state = stateToString(state),
            updated_at = currentTimeMillis(),
            id = sessionId
        )
    }

    override fun loadLatestState(): DriveSessionState? =
        db.driveSessionQueries.selectLatestSession()
            .executeAsOneOrNull()
            ?.let { stringToState(it.state) }

    override fun saveNewSession(sessionId: String, startedAtMs: Long, state: DriveSessionState) {
        db.driveSessionQueries.upsertSession(
            id = sessionId,
            state = stateToString(state),
            started_at = startedAtMs,
            ended_at = null,
            updated_at = startedAtMs
        )
    }

    override fun endSession(sessionId: String, endedAtMs: Long) {
        db.driveSessionQueries.endSession(
            ended_at = endedAtMs,
            updated_at = endedAtMs,
            id = sessionId
        )
    }

    override fun deleteAll() {
        db.driveSessionQueries.deleteAllSessions()
    }

    // ─── State serialisation ───────────────────────────────────────────────

    private fun stateToString(state: DriveSessionState): String = when (state) {
        is DriveSessionState.Idle -> "Idle"
        is DriveSessionState.Activating -> "Activating"
        is DriveSessionState.Briefing -> "Briefing"
        is DriveSessionState.Playing -> "Playing"
        is DriveSessionState.Paused -> "Paused"
        is DriveSessionState.Error -> "Error"
        is DriveSessionState.Ended -> "Ended"
    }

    /**
     * Restores the coarse-grained state from the persisted string.
     * Fine-grained state (episode, position, script) is restored from other tables in
     * Stories 4.3–4.4 (RestoreSessionUseCase). Stubs are used here for MVP recovery.
     */
    private fun stringToState(stateStr: String): DriveSessionState = when (stateStr) {
        "Activating" -> DriveSessionState.Activating
        "Briefing"   -> DriveSessionState.Briefing(BriefingScript(""))
        "Playing"    -> DriveSessionState.Playing(
            // Stub — actual episode loaded from playback_state in Story 4.4 (RestoreSessionUseCase)
            episode = Episode("", "", "", 0L, 0L, false, EpisodeTier.NONE),
            positionMs = 0L
        )
        "Paused"     -> DriveSessionState.Paused
        "Error"      -> DriveSessionState.Error(AppError.Unknown(RuntimeException("restored error state")))
        "Ended"      -> DriveSessionState.Ended
        else         -> {
            // P-8: warn on unknown state strings — could indicate DB corruption or a future
            // schema version. Falling back to Idle to allow the app to continue operating.
            println("WARN SqlDelightDriveSessionRepository: unknown state '$stateStr' — defaulting to Idle")
            DriveSessionState.Idle
        }
    }
}
