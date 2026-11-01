package com.poddrive.rss

/**
 * iOS stub for [RssParser].
 *
 * In-app audio playback on iOS is post-MVP (ADR-002 scopes ExoPlayer to Android only).
 * This stub ensures the KMP project compiles for iOS targets without any RSS parsing code.
 */
actual open class RssParser actual constructor() {
    actual open fun parse(xmlBody: String): List<RssEpisode> = emptyList()
}
