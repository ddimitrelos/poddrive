package com.poddrive.domain.model

/**
 * Domain model for a podcast episode.
 * id: Spotify episode URI e.g. "spotify:episode:xyz" — always String.
 * durationMs, publishedAt: Unix ms (Long) — never formatted strings.
 * tier: Inherited from the parent podcast at sync time.
 * [Source: architecture.md#Format Patterns]
 */
data class Episode(
    val id: String,
    val podcastId: String,
    val title: String,
    val durationMs: Long,
    val publishedAt: Long,
    val isPlayed: Boolean,
    val tier: EpisodeTier,
    /** Direct audio URL resolved from the podcast's RSS feed (Story 4.9). Null until resolved. */
    val audioUrl: String? = null
)
