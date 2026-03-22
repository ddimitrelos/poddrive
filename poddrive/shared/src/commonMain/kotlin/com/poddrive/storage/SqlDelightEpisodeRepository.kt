package com.poddrive.storage

import com.poddrive.db.PodDrive
import com.poddrive.domain.model.Episode
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
        db.episodeQueries.selectUnplayedByPodcast(podcastId).executeAsList().size
}
