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
    val tier: EpisodeTier,
    val lastSyncedAt: Long
)
