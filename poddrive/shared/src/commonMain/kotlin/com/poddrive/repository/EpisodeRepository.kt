package com.poddrive.repository

import com.poddrive.domain.model.Episode

/**
 * Data access interface for the `episodes` and `episode_tiers` SQLDelight tables.
 * Implementations live in `com.poddrive.storage` — never in the UI layer.
 * [Source: architecture.md#Repository Pattern]
 */
interface EpisodeRepository {
    /** Upsert all episodes. Preserves existing `episode_tiers` rows — uses INSERT OR UPDATE. */
    fun upsertAll(episodes: List<Episode>)
    /** Returns the number of unheard episodes for the given podcast URI. */
    fun getUnheardCountForPodcast(podcastId: String): Int
    /**
     * Returns all unplayed episodes ordered: LOVED first, then LIKED, then NONE.
     * Within each tier, sorted by most recent [Episode.publishedAt] descending (FR22, Story 4.2).
     * Uses the pre-existing `selectUnplayedOrderedByTier` SQLDelight query which joins
     * `episodes` with `episode_tiers` to resolve each episode's effective tier.
     */
    fun getUnplayedOrderedByTier(): List<Episode>
}
