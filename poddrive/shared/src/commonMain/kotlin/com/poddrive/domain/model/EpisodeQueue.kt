package com.poddrive.domain.model

/**
 * Ordered list of unheard episodes for a drive session.
 * Episodes are sorted: LOVED first, then LIKED, then NONE.
 * Within each tier, sorted by most recent [Episode.publishedAt] descending (FR22).
 *
 * Built by [com.poddrive.usecase.BuildEpisodeQueueUseCase] using the
 * `selectUnplayedOrderedByTier` SQLDelight query.
 *
 * Persistence: no separate queue table is required. The ordering is stable and
 * re-derivable from the existing `episodes` + `episode_tiers` SQLDelight tables,
 * which survive app backgrounding (AR3, AR6).
 *
 * [Source: epics.md#Story 4.2, architecture.md#Data Architecture]
 */
data class EpisodeQueue(val episodes: List<Episode>)
