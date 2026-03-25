package com.poddrive.storage

import com.poddrive.db.PodDrive
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.repository.EpisodeRepository

/**
 * SQLDelight-backed implementation of [EpisodeRepository].
 * [Source: architecture.md#Repository Pattern, AR3]
 */
class SqlDelightEpisodeRepository(private val db: PodDrive) : EpisodeRepository {

    override fun upsertAll(episodes: List<Episode>) {
        db.episodeQueries.transaction {
            episodes.forEach { episode ->
                db.episodeQueries.upsertEpisode(
                    id = episode.id,
                    podcast_id = episode.podcastId,
                    title = episode.title,
                    duration_ms = episode.durationMs,
                    published_at = episode.publishedAt,
                    is_played = if (episode.isPlayed) 1L else 0L
                )
            }
        }
    }

    override fun getUnheardCountForPodcast(podcastId: String): Int =
        db.episodeQueries.countUnplayedByPodcast(podcastId).executeAsOne().toInt()

    override fun getUnplayedOrderedByTier(): List<Episode> =
        db.episodeQueries.selectUnplayedOrderedByTier().executeAsList().map { row ->
            Episode(
                id = row.id,
                podcastId = row.podcast_id,
                title = row.title,
                durationMs = row.duration_ms,
                publishedAt = row.published_at,
                isPlayed = row.is_played != 0L,
                tier = EpisodeTier.valueOf(row.tier)
            )
        }
}
