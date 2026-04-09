package com.poddrive.audio

/**
 * Platform-agnostic audio player interface.
 *
 * `commonMain` declares the contract; platform-specific `actual` classes implement it:
 * - Android (`androidMain`): backed by ExoPlayer (Media3) with audio focus management.
 * - iOS (`iosMain`): stub — in-app audio is post-MVP on iOS (ADR-002).
 *
 * The class is `open` so tests can subclass it with a [FakeAudioPlayer] that records calls
 * without needing a real Android Context or ExoPlayer.
 *
 * The no-arg constructor in `expect` is the test-subclassing constructor.
 * Production code uses the platform's secondary `constructor(context)` to inject the
 * Android Application context (see [com.poddrive.android.PodDriveApplication.audioPlayer]).
 *
 * [Source: Story 4.10, AC3; ADR-002]
 */
expect open class AudioPlayer() {

    /**
     * Loads [url] into the player, seeks to [resumePositionMs], and starts playback.
     * Requests audio focus before playing (AC4).
     * Replaces any currently playing item.
     *
     * @param url          Direct audio URL (e.g. from [com.poddrive.domain.model.Episode.audioUrl])
     * @param resumePositionMs Byte offset (ms) to seek to before starting. Pass 0 to start from beginning.
     */
    open fun play(url: String, resumePositionMs: Long)

    /** Pauses playback without releasing the player or abandoning audio focus. */
    open fun pause()

    /** Resumes a paused player. No-op if not paused. Re-requests audio focus. */
    open fun resume()

    /**
     * Stops playback, releases the underlying player, and abandons audio focus.
     * Safe to call when no media is playing.
     */
    open fun stop()

    /** Returns the current playback position in milliseconds, or 0 if no media is loaded. */
    open fun currentPositionMs(): Long

    /** Returns true if the player is currently playing (not paused, buffering, or stopped). */
    open fun isPlaying(): Boolean

    /**
     * Returns the underlying platform player object for use by [com.poddrive.android.service.DriveSessionForegroundService]
     * to construct a [androidx.media3.session.MediaSession]. Returns null when no media is
     * currently loaded or when called from the no-arg test constructor.
     *
     * ONLY call this from [DriveSessionForegroundService] — do not use for playback control.
     *
     * [Source: Story 4.10 AC5; Story 4.11 AC4]
     */
    open fun getExoPlayerForSession(): Any?
}
