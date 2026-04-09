package com.poddrive.storage

import com.poddrive.db.PodDrive
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast
import com.poddrive.repository.PodcastRepository

/**
 * SQLDelight-backed implementation of [PodcastRepository].
 * All methods execute synchronously on the calling coroutine's dispatcher.
 * The `podcasts` table schema and queries are defined in PodcastQueries.sq.
 * [Source: architecture.md#Repository Pattern, AR3]
 */
class SqlDelightPodcastRepository(private val db: PodDrive) : PodcastRepository {

    override fun upsertAll(podcasts: List<Podcast>) {
        db.podcastQueries.transaction {
            // Fetch all existing tiers in one query before the loop (avoids N+1 per podcast).
            val existingTiers: Map<String, String> = db.podcastQueries.selectAll()
                .executeAsList()
                .associate { it.id to it.tier }
            podcasts.forEach { podcast ->
                db.podcastQueries.upsert(
                    id = podcast.id,
                    name = podcast.name,
                    description = podcast.description,
                    image_url = podcast.imageUrl,
                    episode_count = podcast.episodeCount.toLong(),
                    // Preserve existing tier — SyncPodcastLibraryUseCase passes EpisodeTier.NONE
                    // for new podcasts; the upsert preserves whatever is stored for existing ones.
                    tier = existingTiers[podcast.id] ?: podcast.tier.name,
                    last_synced_at = podcast.lastSyncedAt
                )
            }
        }
    }

    override fun getAllWithUnheardCount(): List<Podcast> =
        db.podcastQueries.selectAllWithUnheardCount().executeAsList().map { row ->
            Podcast(
                id = row.id,
                name = row.name,
                description = row.description,
                imageUrl = row.image_url,
                episodeCount = row.episode_count.toInt(),
                unheardEpisodeCount = row.unheard_count.toInt(),
                tier = EpisodeTier.entries.find { it.name == row.tier } ?: EpisodeTier.NONE,
                lastSyncedAt = row.last_synced_at,
                rssFeedUrl = row.rss_feed_url
            )
        }

    override fun getByTierWithUnheardCount(tier: String): List<Podcast> =
        db.podcastQueries.selectAllWithUnheardCountByTier(tier).executeAsList().map { row ->
            Podcast(
                id = row.id,
                name = row.name,
                description = row.description,
                imageUrl = row.image_url,
                episodeCount = row.episode_count.toInt(),
                unheardEpisodeCount = row.unheard_count.toInt(),
                tier = EpisodeTier.entries.find { it.name == row.tier } ?: EpisodeTier.NONE,
                lastSyncedAt = row.last_synced_at,
                rssFeedUrl = row.rss_feed_url
            )
        }

    override fun setPodcastTier(id: String, tier: EpisodeTier) {
        db.podcastQueries.updateTier(tier = tier.name, id = id)
    }

    override fun updateRssFeedUrl(id: String, rssFeedUrl: String) {
        db.podcastQueries.updateRssFeedUrl(rssFeedUrl = rssFeedUrl, id = id)
    }

    override suspend fun getById(id: String): Podcast? {
        return db.podcastQueries.selectById(id).executeAsOneOrNull()?.let { row ->
            Podcast(
                id = row.id,
                name = row.name,
                description = row.description,
                imageUrl = row.image_url,
                episodeCount = row.episode_count.toInt(),
                unheardEpisodeCount = row.unheard_count.toInt(),
                tier = EpisodeTier.valueOf(row.tier),
                lastSyncedAt = row.last_synced_at,
                rssFeedUrl = row.rss_feed_url
            )
        }
    }
}
