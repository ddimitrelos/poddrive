package com.poddrive.audio

/**
 * iOS stub for [AudioPlayer].
 *
 * In-app audio playback on iOS is post-MVP (ADR-002 scopes ExoPlayer to Android only).
 * This stub ensures the KMP project compiles for iOS targets without any ExoPlayer code.
 *
 * [Source: Story 4.10, AC3; ADR-002]
 */
actual open class AudioPlayer actual constructor() {
    actual open fun play(url: String, resumePositionMs: Long) = Unit
    actual open fun pause() = Unit
    actual open fun resume() = Unit
    actual open fun stop() = Unit
    actual open fun currentPositionMs(): Long = 0L
    actual open fun isPlaying(): Boolean = false
    actual open fun getExoPlayerForSession(): Any? = null
}
