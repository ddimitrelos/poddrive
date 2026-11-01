package com.poddrive.usecase

import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast
import com.poddrive.repository.EpisodeRepository
import com.poddrive.rss.RssEpisode
import com.poddrive.rss.RssParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveEpisodeAudioUrlsUseCaseTest {

    // -----------------------------------------------------------------------
    // Fakes
    // -----------------------------------------------------------------------

    private class FakeEpisodeRepository(
        private val unresolvedEpisodes: List<Episode> = emptyList()
    ) : EpisodeRepository {
        val updatedAudioUrls = mutableMapOf<String, String>()

        override fun upsertAll(episodes: List<Episode>) = Unit
        override fun getUnheardCountForPodcast(podcastId: String): Int = 0
        override fun getUnplayedOrderedByTier(): List<Episode> = emptyList()
        override fun markPlayed(episodeId: String) = Unit
        override fun updateAudioUrl(id: String, audioUrl: String) {
            updatedAudioUrls[id] = audioUrl
        }
        override fun getUnresolvedEpisodesForPodcast(podcastId: String): List<Episode> =
            unresolvedEpisodes.filter { it.podcastId == podcastId }
    }

    /** Fake RSS parser that returns a pre-configured list — bypasses XML entirely. */
    private class FakeRssParser(private val items: List<RssEpisode> = emptyList()) : RssParser() {
        override fun parse(xmlBody: String): List<RssEpisode> = items
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    private val BASE_DATE = 1712476800000L  // 2026-04-07 08:00:00 UTC

    private fun makePodcast(rssFeedUrl: String? = "https://feeds.example.com/tech-talks") = Podcast(
        id = "spotify:show:123",
        name = "Tech Talks",
        description = "",
        imageUrl = "",
        episodeCount = 10,
        unheardEpisodeCount = 5,
        tier = EpisodeTier.NONE,
        lastSyncedAt = 0L,
        rssFeedUrl = rssFeedUrl
    )

    private fun makeEpisode(
        id: String = "spotify:episode:ep1",
        title: String = "Episode 1 Intro to KMP",
        publishedAt: Long = BASE_DATE,
        audioUrl: String? = null
    ) = Episode(
        id = id,
        podcastId = "spotify:show:123",
        title = title,
        durationMs = 1800000L,
        publishedAt = publishedAt,
        isPlayed = false,
        tier = EpisodeTier.NONE,
        audioUrl = audioUrl
    )

    private fun makeHttpClient(
        body: String = "fake-rss-body",
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Text.Xml.toString())
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    fun `execute resolves and stores audio_url for matched episode`() = runTest {
        val episode = makeEpisode()
        val rssItem = RssEpisode(
            title = "Episode 1: Intro to KMP",
            pubDateMs = BASE_DATE,
            enclosureUrl = "https://example.com/ep1.mp3"
        )
        val repo = FakeEpisodeRepository(unresolvedEpisodes = listOf(episode))
        val useCase = ResolveEpisodeAudioUrlsUseCase(
            episodeRepository = repo,
            httpClient = makeHttpClient(),
            rssParser = FakeRssParser(listOf(rssItem))
        )

        useCase.execute(makePodcast())

        assertEquals("https://example.com/ep1.mp3", repo.updatedAudioUrls["spotify:episode:ep1"])
    }

    @Test
    fun `execute skips episode that already has audio_url`() = runTest {
        // Episode with audio_url already set — getUnresolvedEpisodesForPodcast returns empty
        val repo = FakeEpisodeRepository(unresolvedEpisodes = emptyList())
        val rssItem = RssEpisode("Episode 1", BASE_DATE, "https://example.com/ep1.mp3")
        val useCase = ResolveEpisodeAudioUrlsUseCase(
            episodeRepository = repo,
            httpClient = makeHttpClient(),
            rssParser = FakeRssParser(listOf(rssItem))
        )

        useCase.execute(makePodcast())

        // Nothing written because there are no unresolved episodes
        assertEquals(0, repo.updatedAudioUrls.size)
    }

    @Test
    fun `execute stores nothing when RSS fetch returns 404`() = runTest {
        val repo = FakeEpisodeRepository(unresolvedEpisodes = listOf(makeEpisode()))
        val useCase = ResolveEpisodeAudioUrlsUseCase(
            episodeRepository = repo,
            httpClient = makeHttpClient(body = "Not Found", status = HttpStatusCode.NotFound),
            rssParser = FakeRssParser()
        )

        useCase.execute(makePodcast())

        assertEquals(0, repo.updatedAudioUrls.size)
    }

    @Test
    fun `execute stores nothing when no title plus date match found`() = runTest {
        val episode = makeEpisode(title = "Episode 1 Intro to KMP", publishedAt = BASE_DATE)
        // RSS has a completely different episode title far in the future
        val rssItem = RssEpisode(
            title = "Completely Different Show Title",
            pubDateMs = BASE_DATE + 30 * ONE_DAY_MS,  // 30 days apart → outside ±2 day window
            enclosureUrl = "https://example.com/other.mp3"
        )
        val repo = FakeEpisodeRepository(unresolvedEpisodes = listOf(episode))
        val useCase = ResolveEpisodeAudioUrlsUseCase(
            episodeRepository = repo,
            httpClient = makeHttpClient(),
            rssParser = FakeRssParser(listOf(rssItem))
        )

        useCase.execute(makePodcast())

        assertNull(repo.updatedAudioUrls["spotify:episode:ep1"])
    }

    @Test
    fun `execute does nothing when podcast rssFeedUrl is null`() = runTest {
        val repo = FakeEpisodeRepository(unresolvedEpisodes = listOf(makeEpisode()))
        var httpCalled = false
        val engine = MockEngine { _ ->
            httpCalled = true
            respond(content = "", status = HttpStatusCode.OK, headers = headersOf())
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val useCase = ResolveEpisodeAudioUrlsUseCase(
            episodeRepository = repo,
            httpClient = httpClient,
            rssParser = FakeRssParser()
        )

        useCase.execute(makePodcast(rssFeedUrl = null))

        assertEquals(false, httpCalled, "HTTP must NOT be called when rssFeedUrl is null")
        assertEquals(0, repo.updatedAudioUrls.size)
    }

    @Test
    fun `execute prefers exact title match over partial containment`() = runTest {
        val episode = makeEpisode(title = "Tech Talk", publishedAt = BASE_DATE)
        val exactMatch = RssEpisode("Tech Talk", BASE_DATE, "https://example.com/exact.mp3")
        val partialMatch = RssEpisode("The Tech Talk Extended Edition", BASE_DATE, "https://example.com/partial.mp3")
        val repo = FakeEpisodeRepository(unresolvedEpisodes = listOf(episode))
        val useCase = ResolveEpisodeAudioUrlsUseCase(
            episodeRepository = repo,
            httpClient = makeHttpClient(),
            rssParser = FakeRssParser(listOf(partialMatch, exactMatch))  // partial listed first
        )

        useCase.execute(makePodcast())

        assertEquals("https://example.com/exact.mp3", repo.updatedAudioUrls["spotify:episode:ep1"],
            "Exact title match should be preferred over partial containment")
    }
}
