package com.poddrive.usecase

import com.poddrive.api.SpotifyLibraryApiClient
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncPodcastLibraryUseCaseTest {

    // -----------------------------------------------------------------------
    // Fakes
    // -----------------------------------------------------------------------

    private class FakePodcastRepository : PodcastRepository {
        val upserted = mutableListOf<Podcast>()
        var stored = mutableListOf<Podcast>()

        override fun upsertAll(podcasts: List<Podcast>) {
            upserted.addAll(podcasts)
            // Simulate persisting — getAllWithUnheardCount returns what was upserted
            podcasts.forEach { incoming ->
                val existing = stored.indexOfFirst { it.id == incoming.id }
                if (existing >= 0) {
                    // Preserve tier (like the real repo does via selectById check)
                    stored[existing] = incoming.copy(tier = stored[existing].tier)
                } else {
                    stored.add(incoming)
                }
            }
        }

        override fun getAllWithUnheardCount(): List<Podcast> = stored.toList()

        override fun getByTierWithUnheardCount(tier: String): List<Podcast> =
            stored.filter { it.tier.name == tier }

        override fun setPodcastTier(id: String, tier: EpisodeTier) = Unit
    }

    private class FakeEpisodeRepository : EpisodeRepository {
        val upserted = mutableListOf<Episode>()

        override fun upsertAll(episodes: List<Episode>) {
            upserted.addAll(episodes)
        }

        override fun getUnheardCountForPodcast(podcastId: String): Int =
            upserted.count { it.podcastId == podcastId && !it.isPlayed }

        override fun getUnplayedOrderedByTier(): List<Episode> = upserted.filter { !it.isPlayed }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun makeUseCase(
        showsResponse: String = SHOWS_JSON,
        episodesResponse: String = EPISODES_JSON,
        showsStatus: HttpStatusCode = HttpStatusCode.OK,
        episodesStatus: HttpStatusCode = HttpStatusCode.OK,
        podcastRepo: FakePodcastRepository = FakePodcastRepository(),
        episodeRepo: FakeEpisodeRepository = FakeEpisodeRepository()
    ): Triple<SyncPodcastLibraryUseCase, FakePodcastRepository, FakeEpisodeRepository> {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/v1/me/shows") -> respond(
                    content = showsResponse,
                    status = showsStatus,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                )
                url.contains("/v1/shows/") -> respond(
                    content = episodesResponse,
                    status = episodesStatus,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                )
                else -> respond(
                    content = "{}",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                )
            }
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val apiClient = SpotifyLibraryApiClient(httpClient)
        val useCase = SyncPodcastLibraryUseCase(apiClient, podcastRepo, episodeRepo)
        return Triple(useCase, podcastRepo, episodeRepo)
    }

    // -----------------------------------------------------------------------
    // Success path
    // -----------------------------------------------------------------------

    @Test
    fun `execute success - shows and episodes upserted, Result_Success returned`() = runTest {
        val (useCase, podcastRepo, episodeRepo) = makeUseCase()

        val result = useCase.execute()

        assertIs<Result.Success<List<Podcast>>>(result)
        // Shows were upserted
        assertTrue(podcastRepo.upserted.isNotEmpty(), "Podcasts should be upserted")
        // Episodes were upserted
        assertTrue(episodeRepo.upserted.isNotEmpty(), "Episodes should be upserted")
        // Result contains the persisted library
        assertTrue(result.value.isNotEmpty(), "Result should contain podcasts")
    }

    @Test
    fun `execute success - podcast id is show uri not base id`() = runTest {
        val (useCase, podcastRepo, _) = makeUseCase()

        useCase.execute()

        val upsertedIds = podcastRepo.upserted.map { it.id }
        assertTrue(
            upsertedIds.all { it.startsWith("spotify:show:") },
            "Podcast.id must use full Spotify URI, not base ID"
        )
    }

    @Test
    fun `execute success - selects largest image by width`() = runTest {
        val (useCase, podcastRepo, _) = makeUseCase()

        useCase.execute()

        val podcast = podcastRepo.upserted.firstOrNull()
        // The SHOWS_JSON has two images: width 300 and width 640 — should pick 640
        assertEquals("https://i.scdn.co/image/large.jpg", podcast?.imageUrl)
    }

    // -----------------------------------------------------------------------
    // API failure path
    // -----------------------------------------------------------------------

    @Test
    fun `execute - shows API failure returns Result_Failure`() = runTest {
        val (useCase, _, _) = makeUseCase(
            showsResponse = """{"error":{"status":401,"message":"Unauthorized"}}""",
            showsStatus = HttpStatusCode.Unauthorized
        )

        val result = useCase.execute()

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.SpotifyApiError>(result.error)
        assertEquals(401, (result.error as AppError.SpotifyApiError).code)
    }

    // -----------------------------------------------------------------------
    // Partial episode failure (non-fatal)
    // -----------------------------------------------------------------------

    @Test
    fun `execute - episode API failure for one show continues with other shows`() = runTest {
        val episodeFail = makeUseCase(
            episodesResponse = """{"error":{"status":500,"message":"Server Error"}}""",
            episodesStatus = HttpStatusCode.InternalServerError
        )
        val (useCase, podcastRepo, episodeRepo) = episodeFail

        val result = useCase.execute()

        // Use case should still succeed (shows were upserted)
        assertIs<Result.Success<List<Podcast>>>(result)
        // Podcasts were upserted even though episodes failed
        assertTrue(podcastRepo.upserted.isNotEmpty())
        // No episodes were upserted (all shows failed)
        assertTrue(episodeRepo.upserted.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Empty shows list
    // -----------------------------------------------------------------------

    @Test
    fun `execute - empty shows list returns Result_Success with empty list`() = runTest {
        val (useCase, _, episodeRepo) = makeUseCase(
            showsResponse = """{"items":[],"next":null,"total":0}"""
        )

        val result = useCase.execute()

        assertIs<Result.Success<List<Podcast>>>(result)
        assertEquals(0, result.value.size, "Empty shows should return empty podcast list")
        assertTrue(episodeRepo.upserted.isEmpty(), "No episodes should be fetched for empty library")
    }

    // -----------------------------------------------------------------------
    // resume_point handling
    // -----------------------------------------------------------------------

    @Test
    fun `execute - fully_played=true maps to isPlayed=true`() = runTest {
        val episodesJson = """
            {
              "items": [{
                "id": "ep1base",
                "uri": "spotify:episode:ep1",
                "name": "Episode 1",
                "duration_ms": 1800000,
                "release_date": "2024-01-15",
                "resume_point": {"fully_played": true, "resume_position_ms": 1800000}
              }],
              "next": null,
              "total": 1
            }
        """.trimIndent()
        val (useCase, _, episodeRepo) = makeUseCase(episodesResponse = episodesJson)

        useCase.execute()

        val episode = episodeRepo.upserted.firstOrNull()
        assertEquals(true, episode?.isPlayed, "fully_played=true must map to isPlayed=true")
    }

    @Test
    fun `execute - fully_played=false maps to isPlayed=false`() = runTest {
        val episodesJson = """
            {
              "items": [{
                "id": "ep1base",
                "uri": "spotify:episode:ep1",
                "name": "Episode 1",
                "duration_ms": 1800000,
                "release_date": "2024-01-15",
                "resume_point": {"fully_played": false, "resume_position_ms": 600000}
              }],
              "next": null,
              "total": 1
            }
        """.trimIndent()
        val (useCase, _, episodeRepo) = makeUseCase(episodesResponse = episodesJson)

        useCase.execute()

        val episode = episodeRepo.upserted.firstOrNull()
        assertEquals(false, episode?.isPlayed, "fully_played=false must map to isPlayed=false (unheard)")
    }

    @Test
    fun `execute - null resume_point maps to isPlayed=false (unheard)`() = runTest {
        val episodesJson = """
            {
              "items": [{
                "id": "ep2base",
                "uri": "spotify:episode:ep2",
                "name": "Episode 2",
                "duration_ms": 3600000,
                "release_date": "2024-02-20"
              }],
              "next": null,
              "total": 1
            }
        """.trimIndent()
        val (useCase, _, episodeRepo) = makeUseCase(episodesResponse = episodesJson)

        useCase.execute()

        val episode = episodeRepo.upserted.firstOrNull()
        assertEquals(false, episode?.isPlayed, "Null resume_point must be treated as unheard (isPlayed=false)")
    }

    // -----------------------------------------------------------------------
    // parseSpotifyDateToEpochMs — date parsing variants
    // -----------------------------------------------------------------------

    @Test
    fun `parseSpotifyDateToEpochMs - YYYY-MM-DD returns correct epoch ms`() {
        // 2024-01-15 → should be a valid positive epoch
        val result = parseSpotifyDateToEpochMs("2024-01-15")
        assertTrue(result > 0L, "YYYY-MM-DD date should produce positive epoch ms")
        // 2024-01-15 00:00:00 UTC = 1705276800000 ms
        assertEquals(1705276800000L, result, "2024-01-15 should parse to 1705276800000ms")
    }

    @Test
    fun `parseSpotifyDateToEpochMs - YYYY-MM defaults to day=1`() {
        val fullDate = parseSpotifyDateToEpochMs("2024-01-01")
        val monthOnly = parseSpotifyDateToEpochMs("2024-01")
        assertEquals(fullDate, monthOnly, "YYYY-MM should default to day 1 of that month")
    }

    @Test
    fun `parseSpotifyDateToEpochMs - YYYY defaults to month=1 day=1`() {
        val fullDate = parseSpotifyDateToEpochMs("2024-01-01")
        val yearOnly = parseSpotifyDateToEpochMs("2024")
        assertEquals(fullDate, yearOnly, "YYYY should default to January 1st of that year")
    }

    // -----------------------------------------------------------------------
    // Pagination
    // -----------------------------------------------------------------------

    @Test
    fun `execute - paginates shows across multiple pages`() = runTest {
        var showsCallCount = 0
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/v1/me/shows") -> {
                    showsCallCount++
                    val body = if (showsCallCount == 1) SHOWS_PAGE_1_JSON else SHOWS_PAGE_2_JSON
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                    )
                }
                url.contains("/v1/shows/") -> respond(
                    content = EPISODES_JSON,
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                )
                else -> respond(
                    content = "{}",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                )
            }
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val podcastRepo = FakePodcastRepository()
        val episodeRepo = FakeEpisodeRepository()
        val useCase = SyncPodcastLibraryUseCase(SpotifyLibraryApiClient(httpClient), podcastRepo, episodeRepo)

        val result = useCase.execute()

        assertIs<Result.Success<List<Podcast>>>(result)
        assertEquals(2, showsCallCount, "Should have fetched exactly 2 pages of shows")
        assertEquals(2, podcastRepo.upserted.size, "Both pages' shows should be upserted")
        assertTrue(podcastRepo.upserted.any { it.id == "spotify:show:show1base" }, "Show from page 1 must be present")
        assertTrue(podcastRepo.upserted.any { it.id == "spotify:show:show2base" }, "Show from page 2 must be present")
    }

    // -----------------------------------------------------------------------
    // Test fixtures
    // -----------------------------------------------------------------------

    companion object {
        val SHOWS_JSON = """
            {
              "items": [{
                "show": {
                  "id": "show1base",
                  "uri": "spotify:show:show1base",
                  "name": "Tech Talks",
                  "description": "A podcast about technology",
                  "images": [
                    {"url": "https://i.scdn.co/image/small.jpg", "width": 300, "height": 300},
                    {"url": "https://i.scdn.co/image/large.jpg", "width": 640, "height": 640}
                  ],
                  "total_episodes": 50
                }
              }],
              "next": null,
              "total": 1
            }
        """.trimIndent()

        val EPISODES_JSON = """
            {
              "items": [{
                "id": "ep1base",
                "uri": "spotify:episode:ep1base",
                "name": "Episode 1",
                "duration_ms": 1800000,
                "release_date": "2024-01-15",
                "resume_point": {"fully_played": false, "resume_position_ms": 0}
              }],
              "next": null,
              "total": 1
            }
        """.trimIndent()

        /** First page of a paginated shows response — next points to page 2. */
        val SHOWS_PAGE_1_JSON = """
            {
              "items": [{
                "show": {
                  "id": "show1base",
                  "uri": "spotify:show:show1base",
                  "name": "Tech Talks",
                  "description": "A podcast about technology",
                  "images": [{"url": "https://i.scdn.co/image/large.jpg", "width": 640, "height": 640}],
                  "total_episodes": 50
                }
              }],
              "next": "https://api.spotify.com/v1/me/shows?offset=50&limit=50",
              "total": 2
            }
        """.trimIndent()

        /** Second (final) page of a paginated shows response — next is null. */
        val SHOWS_PAGE_2_JSON = """
            {
              "items": [{
                "show": {
                  "id": "show2base",
                  "uri": "spotify:show:show2base",
                  "name": "Science Weekly",
                  "description": "A podcast about science",
                  "images": [{"url": "https://i.scdn.co/image/science.jpg", "width": 640, "height": 640}],
                  "total_episodes": 30
                }
              }],
              "next": null,
              "total": 2
            }
        """.trimIndent()
    }
}
