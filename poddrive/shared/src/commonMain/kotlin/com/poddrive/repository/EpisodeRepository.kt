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
}
