package com.poddrive.usecase

import com.poddrive.api.ItunesPodcastSearchClient
import com.poddrive.domain.model.Podcast
import com.poddrive.repository.PodcastRepository

/**
 * Discovers and persists the RSS feed URL for a single podcast via the iTunes Search API.
 *
 * - If the podcast already has a `rssFeedUrl`, returns immediately (AC4 — no redundant calls).
 * - On successful discovery, writes the URL to SQLDelight via [PodcastRepository.updateRssFeedUrl].
 * - All errors are swallowed — the method never throws (AC5).
 * - The caller is responsible for passing a [Podcast] with an up-to-date `rssFeedUrl` so the
 *   skip-guard works correctly.
 *
 * [Source: Story 4.8, ADR-002]
 */
open class ResolvePodcastRssFeedUseCase(
    private val podcastRepository: PodcastRepository,
    private val itunesClient: ItunesPodcastSearchClient
) {
    open suspend fun execute(podcast: Podcast) {
        if (podcast.rssFeedUrl != null) return  // AC4: already resolved — skip

        try {
            val feedUrl = itunesClient.findFeedUrl(podcast.name) ?: return
            podcastRepository.updateRssFeedUrl(podcast.id, feedUrl)
        } catch (e: Exception) {
            // AC5: swallow all errors — never propagate to the user
        }
    }
}
