package com.poddrive.domain.model

/**
 * Sealed class representing all external inputs to the drive session state machine.
 * Platform layers dispatch events; only [com.poddrive.session.DriveSessionManager] processes them.
 * [Source: architecture.md#Communication Patterns, AR6]
 */
sealed class DriveSessionEvent {
    /** Bluetooth audio device connected — initiates drive session activation. */
    object BluetoothConnected : DriveSessionEvent()

    /** Bluetooth audio device disconnected — ends the active drive session. */
    object BluetoothDisconnected : DriveSessionEvent()

    /** User or system confirms briefing has been heard — transitions to Playing. */
    object BriefingConfirmed : DriveSessionEvent()

    /** User skips current episode — returns to Activating to load next episode. */
    object SkipEpisode : DriveSessionEvent()

    /** User pauses playback — transitions Playing → Paused. */
    object PauseSession : DriveSessionEvent()

    /** User resumes playback — transitions Paused → Playing (restores last episode + position). */
    object ResumeSession : DriveSessionEvent()

    /**
     * Manual drive session trigger — fallback when Bluetooth auto-detection is unavailable (FR13).
     * Dispatched from [com.poddrive.android.ui.drive.DriveSessionViewModel.startManualDriveSession].
     * [Source: Story 3.3, architecture.md#Communication Patterns]
     */
    object ManualTrigger : DriveSessionEvent()

    /**
     * Spotify playback failure or deep-link handoff timeout (FR25, Story 4.5).
     * Transitions any active state to [DriveSessionState.Error].
     * [error] is the typed error for display and logging.
     */
    data class PlaybackFailed(val error: AppError) : DriveSessionEvent()

    /**
     * User or system requests retry after a playback failure (Story 4.5, AC5).
     * Transitions [DriveSessionState.Error] back to [DriveSessionState.Activating].
     */
    object RetryPlayback : DriveSessionEvent()

    /**
     * User tapped an episode tile with a resolved [com.poddrive.domain.model.Episode.audioUrl]
     * and ExoPlayer has started playing it in-app (Story 4.10, AC7).
     *
     * Transitions [DriveSessionState.Activating] or [DriveSessionState.Playing] →
     * [DriveSessionState.Playing] with the new [episode] and positionMs = 0.
     *
     * Dispatched by [com.poddrive.android.ui.drive.DriveSessionViewModel.onEpisodeTapped]
     * immediately after [com.poddrive.audio.AudioPlayer.play] is called.
     */
    data class EpisodeStarted(val episode: Episode) : DriveSessionEvent()
}
