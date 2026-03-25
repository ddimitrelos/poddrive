package com.poddrive.storage

import com.poddrive.db.PodDrive
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.repository.DriveSessionRepository
import com.poddrive.currentTimeMillis

/**
 * SQLDelight-backed implementation of [DriveSessionRepository].
 * Maps [DriveSessionState] to/from a compact TEXT representation stored in the
 * `drive_sessions.state` column.
 *
 * State serialisation format:
 *   Idle          → "Idle"
 *   Activating    → "Activating"
 *   Briefing(s)   → "Briefing:<script>"
 *   Playing(e,p)  → "Playing:<episodeId>:<positionMs>"
 *   Paused        → "Paused"
 *   Error(e)      → "Error:<message>"
 *   Ended         → "Ended"
 *
 * [Source: architecture.md#Data Architecture, AR3, AR6]
 * [Story 4.2]
 */
class SqlDelightDriveSessionRepository(
    private val db: PodDrive,
    private val episodeRepository: com.poddrive.repository.EpisodeRepository
) : DriveSessionRepository {

    companion object {
        const val SESSION_ID = "singleton"
    }

    override fun persistState(sessionId: String, state: DriveSessionState) {
        val stateText = serialiseState(state)
        val now = currentTimeMillis()
        db.driveSessionQueries.upsertSession(
            id = sessionId,
            state = stateText,
            started_at = now,
            ended_at = null,
            updated_at = now
        )
    }

    override fun loadLatestSessionState(): DriveSessionState? {
        val row = db.driveSessionQueries.selectLatestSession().executeAsOneOrNull()
            ?: return null
        return deserialiseState(row.state)
    }

    // ---------------------------------------------------------------------------
    // Serialisation helpers
    // ---------------------------------------------------------------------------

    private fun serialiseState(state: DriveSessionState): String = when (state) {
        is DriveSessionState.Idle -> "Idle"
        is DriveSessionState.Activating -> "Activating"
        is DriveSessionState.Briefing -> "Briefing:${state.script}"
        is DriveSessionState.Playing -> "Playing:${state.episode.id}:${state.positionMs}"
        is DriveSessionState.Paused -> "Paused"
        is DriveSessionState.Error -> "Error:${state.error}"
        is DriveSessionState.Ended -> "Ended"
    }

    private fun deserialiseState(text: String): DriveSessionState {
        return when {
            text == "Idle" -> DriveSessionState.Idle
            text == "Activating" -> DriveSessionState.Activating
            text.startsWith("Briefing:") -> DriveSessionState.Briefing(text.removePrefix("Briefing:"))
            text.startsWith("Playing:") -> {
                val parts = text.removePrefix("Playing:").split(":")
                val episodeId = parts.getOrNull(0) ?: return DriveSessionState.Ended
                val positionMs = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                // Look up episode from DB; fall back to Ended if not found
                val episode = findEpisodeById(episodeId)
                    ?: return DriveSessionState.Ended
                DriveSessionState.Playing(episode, positionMs)
            }
            text == "Paused" -> DriveSessionState.Paused
            text.startsWith("Error:") -> DriveSessionState.Error(AppError.Unknown(Exception(text.removePrefix("Error:"))))
            text == "Ended" -> DriveSessionState.Ended
            else -> DriveSessionState.Idle
        }
    }

    private fun findEpisodeById(episodeId: String): Episode? {
        val row = db.episodeQueries.selectById(episodeId).executeAsOneOrNull() ?: return null
        // selectTierForEpisode returns a single TEXT column — SQLDelight yields String? directly
        val tierString = db.episodeQueries.selectTierForEpisode(episodeId).executeAsOneOrNull()
        val tier = tierString?.let { t ->
            runCatching { EpisodeTier.valueOf(t) }.getOrDefault(EpisodeTier.NONE)
        } ?: EpisodeTier.NONE
        return Episode(
            id = row.id,
            podcastId = row.podcast_id,
            title = row.title,
            durationMs = row.duration_ms,
            publishedAt = row.published_at,
            isPlayed = row.is_played != 0L,
            tier = tier
        )
    }
}
