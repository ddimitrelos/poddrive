package com.poddrive.domain.model

/**
 * Domain model for a followed Spotify podcast.
 * id: Spotify show URI e.g. "spotify:show:xyz" — always String, never numeric.
 * lastSyncedAt: Unix ms — never a formatted string.
 * [Source: architecture.md#Format Patterns]
 */
data class Podcast(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val episodeCount: Int,
    /** Number of unheard (is_played = 0) episodes from the local SQLDelight cache. Computed at query time via JOIN — not stored as a column. (FR7) */
    val unheardEpisodeCount: Int,
    val tier: EpisodeTier,
    val lastSyncedAt: Long
)
