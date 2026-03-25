package com.poddrive.domain.model

/**
 * Sealed class representing all states in the drive session lifecycle.
 * Only [com.poddrive.session.DriveSessionManager] may mutate this state.
 * Every transition must be persisted to SQLDelight BEFORE being emitted to observers
 * (persist-before-emit rule, AR6).
 *
 * Full state machine: Idle → Activating → Briefing → Playing → Paused → Error → Ended
 * [Source: architecture.md#Communication Patterns, AR6]
 */
sealed class DriveSessionState {
    /** No active drive session. Initial state on app launch. */
    object Idle : DriveSessionState()

    /** Bluetooth connected (or manual trigger) — building episode queue and briefing. */
    object Activating : DriveSessionState()

    /**
     * Briefing script generated; TTS playback in progress.
     * Full briefing content generation is Story 3.4 (deferred MVP Phase 2).
     */
    data class Briefing(val script: BriefingScript) : DriveSessionState()

    /**
     * Spotify playback is active for the given episode at [positionMs].
     * [episode] and [positionMs] are managed by the PlaybackController (Stories 4.1–4.4).
     */
    data class Playing(val episode: Episode, val positionMs: Long) : DriveSessionState()

    /** Playback is paused; session remains active. */
    object Paused : DriveSessionState()

    /**
     * Spotify playback failure or deep-link timeout (Story 4.5).
     * [error] carries the typed error for display and logging.
     * Transitions to [Activating] via [com.poddrive.domain.model.DriveSessionEvent.RetryPlayback].
     */
    data class Error(val error: AppError) : DriveSessionState()

    /** Drive session ended (Bluetooth disconnected or user ended). Terminal state. */
    object Ended : DriveSessionState()
}
