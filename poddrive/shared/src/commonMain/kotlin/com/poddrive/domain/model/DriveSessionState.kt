package com.poddrive.domain.model

/**
 * Sealed class representing all states in the drive session lifecycle.
 * Only DriveSessionManager may mutate this state — never platform ViewModels.
 * Every transition must be persisted to SQLDelight BEFORE being emitted to observers
 * (persist-before-emit rule, AR6).
 *
 * Full state machine:
 *   Idle → Activating → Briefing → Playing ⇄ Paused
 *                                         ↓
 *                                       Error ──(RetryPlayback)──→ Playing
 *                                         ↓
 *                                       Ended (also from any state on BluetoothDisconnected)
 *
 * [Source: architecture.md#Communication Patterns, AR6]
 * [Stories 4.2, 4.5]
 */
sealed class DriveSessionState {
    /** No active drive session. App is on home screen. */
    object Idle : DriveSessionState()

    /** Bluetooth connected (or manual trigger). Loading queue + building briefing. */
    object Activating : DriveSessionState()

    /**
     * Briefing script ready — TTS stub until Story 3.4.
     * [script] is a human-readable summary of queued episodes.
     */
    data class Briefing(val script: String) : DriveSessionState()

    /**
     * Actively playing an episode via Spotify deep-link.
     * [episode] is the currently playing episode; [positionMs] is the last known position.
     */
    data class Playing(val episode: Episode, val positionMs: Long) : DriveSessionState()

    /** Session paused (e.g. user pressed pause). Resumes from same episode. */
    object Paused : DriveSessionState()

    /**
     * Playback failed or Spotify handoff timed out (FR25, NFR12).
     * UI shows error panel with Retry action (UX-DR10).
     */
    data class Error(val error: AppError) : DriveSessionState()

    /** Session completed or Bluetooth disconnected. Returns to Idle on next BT connect. */
    object Ended : DriveSessionState()
}
