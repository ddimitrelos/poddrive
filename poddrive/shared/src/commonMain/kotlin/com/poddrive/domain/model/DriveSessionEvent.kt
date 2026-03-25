package com.poddrive.domain.model

/**
 * Sealed class representing external inputs to the drive session state machine.
 * Platform layers dispatch events; only DriveSessionManager processes them.
 * [Source: architecture.md#Communication Patterns, AR6]
 * [Stories 3.3, 4.2, 4.5]
 */
sealed class DriveSessionEvent {
    /** A Bluetooth audio device connected — auto-trigger session (FR10, FR11). */
    object BluetoothConnected : DriveSessionEvent()

    /** Bluetooth audio device disconnected — ends session. */
    object BluetoothDisconnected : DriveSessionEvent()

    /** User manually started a drive session from HomeScreen (FR13, Story 3.3). */
    object ManualTrigger : DriveSessionEvent()

    /** User confirmed the briefing (steering wheel or tap) — starts playback. */
    object BriefingConfirmed : DriveSessionEvent()

    /** Skip current episode; advance queue. */
    object SkipEpisode : DriveSessionEvent()

    /** Pause current episode. */
    object PauseSession : DriveSessionEvent()

    /** Resume a paused episode. */
    object ResumeSession : DriveSessionEvent()

    /** Spotify playback failed or deep-link timed out (FR25, Story 4.5). */
    object PlaybackFailed : DriveSessionEvent()

    /** User tapped Retry after a playback failure — re-attempts handoff (Story 4.5). */
    object RetryPlayback : DriveSessionEvent()
}
