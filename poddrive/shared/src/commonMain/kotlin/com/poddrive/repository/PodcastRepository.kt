package com.poddrive.repository

import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast

/**
 * Data access interface for the `podcasts` SQLDelight table.
 * Implementations live in `com.poddrive.storage` — never in the UI layer.
 * [Source: architecture.md#Repository Pattern]
 */
interface PodcastRepository {
    /** Upsert all podcasts. Preserves existing `tier` values — does NOT overwrite tier on upsert. */
    fun upsertAll(podcasts: List<Podcast>)
    /** Returns all podcasts with their unheard episode count (LEFT JOIN with episodes table). */
    fun getAllWithUnheardCount(): List<Podcast>
    /** Returns all podcasts for the given tier with unheard counts. */
    fun getByTierWithUnheardCount(tier: String): List<Podcast>
    /** Updates the tier for a single podcast by ID. Use EpisodeTier.NONE to remove the tag. */
    fun setPodcastTier(id: String, tier: EpisodeTier)         // ← ADD (Story 2.2)
}
