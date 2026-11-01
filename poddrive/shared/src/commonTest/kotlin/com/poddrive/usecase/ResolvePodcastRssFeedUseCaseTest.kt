package com.poddrive.usecase

import com.poddrive.api.ItunesPodcastSearchClient
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast
import com.poddrive.repository.PodcastRepository
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

class ResolvePodcastRssFeedUseCaseTest {

    // -----------------------------------------------------------------------
    // Fakes
    // -----------------------------------------------------------------------

    private class FakePodcastRepository : PodcastRepository {
        val updatedUrls = mutableMapOf<String, String>()

        override fun upsertAll(podcasts: List<Podcast>) = Unit
        override fun getAllWithUnheardCount(): List<Podcast> = emptyList()
        override fun getByTierWithUnheardCount(tier: String): List<Podcast> = emptyList()
        override fun setPodcastTier(id: String, tier: EpisodeTier) = Unit
        override fun updateRssFeedUrl(id: String, rssFeedUrl: String) {
            updatedUrls[id] = rssFeedUrl
        }
        override suspend fun getById(id: String): Podcast? = null
    }

    private fun makePodcast(id: String = "spotify:show:123", rssFeedUrl: String? = null) = Podcast(
        id = id,
        name = "Tech Talks",
        description = "A tech podcast",
        imageUrl = "",
        episodeCount = 10,
        unheardEpisodeCount = 5,
        tier = EpisodeTier.NONE,
        lastSyncedAt = 0L,
        rssFeedUrl = rssFeedUrl
    )

    private fun makeItunesClient(feedUrl: String?): ItunesPodcastSearchClient {
        val responseBody = if (feedUrl != null) {
            """
            {
              "resultCount": 1,
              "results": [
                {
                  "collectionName": "Tech Talks",
                  "feedUrl": "$feedUrl"
                }
              ]
            }
            """.trimIndent()
        } else {
            """{"resultCount": 0, "results": []}"""
        }
        val engine = MockEngine { _ ->
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return ItunesPodcastSearchClient(httpClient)
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    fun `execute stores feedUrl when iTunes returns a match`() = runTest {
        val repo = FakePodcastRepository()
        val itunesClient = makeItunesClient("https://feeds.example.com/tech-talks")
        val useCase = ResolvePodcastRssFeedUseCase(repo, itunesClient)
        val podcast = makePodcast(rssFeedUrl = null)

        useCase.execute(podcast)

        assertEquals("https://feeds.example.com/tech-talks", repo.updatedUrls["spotify:show:123"])
    }

    @Test
    fun `execute stores nothing when iTunes returns no match`() = runTest {
        val repo = FakePodcastRepository()
        val itunesClient = makeItunesClient(feedUrl = null)
        val useCase = ResolvePodcastRssFeedUseCase(repo, itunesClient)
        val podcast = makePodcast(rssFeedUrl = null)

        useCase.execute(podcast)

        assertNull(repo.updatedUrls["spotify:show:123"])
    }

    @Test
    fun `execute skips iTunes call when rssFeedUrl is already set`() = runTest {
        var itunesCalled = false
        val engine = MockEngine { _ ->
            itunesCalled = true
            respond(
                content = """{"resultCount": 0, "results": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val itunesClient = ItunesPodcastSearchClient(httpClient)
        val repo = FakePodcastRepository()
        val useCase = ResolvePodcastRssFeedUseCase(repo, itunesClient)
        val podcast = makePodcast(rssFeedUrl = "https://already-set.example.com/feed")

        useCase.execute(podcast)

        assertEquals(false, itunesCalled, "iTunes must NOT be called when rssFeedUrl is already set")
        assertNull(repo.updatedUrls["spotify:show:123"], "updateRssFeedUrl must NOT be called for already-resolved podcasts")
    }
}
