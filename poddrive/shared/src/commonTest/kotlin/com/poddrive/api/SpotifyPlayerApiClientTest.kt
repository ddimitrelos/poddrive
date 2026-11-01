package com.poddrive.api

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result
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
import kotlin.test.assertNotNull

class SpotifyPlayerApiClientTest {

    // -----------------------------------------------------------------------
    // playEpisode — success
    // -----------------------------------------------------------------------

    @Test
    fun `playEpisode returns Success on HTTP 204`() = runTest {
        val client = makeClient(status = HttpStatusCode.NoContent, body = "")
        val result = client.playEpisode("spotify:episode:6FZj6eVQLmvii10w73SxEw")

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `playEpisode returns Success on HTTP 200`() = runTest {
        // Some Spotify API versions return 200 instead of 204; both must be treated as success.
        val client = makeClient(status = HttpStatusCode.OK, body = "")
        val result = client.playEpisode("spotify:episode:abc123")

        assertIs<Result.Success<Unit>>(result)
    }

    // -----------------------------------------------------------------------
    // playEpisode — API errors
    // -----------------------------------------------------------------------

    @Test
    fun `playEpisode returns SpotifyApiError on HTTP 403`() = runTest {
        val body = """{"error":{"status":403,"message":"Player command failed: Premium required"}}"""
        val client = makeClient(status = HttpStatusCode.Forbidden, body = body)
        val result = client.playEpisode("spotify:episode:abc")

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.SpotifyApiError>(result.error)
        assertEquals(403, (result.error as AppError.SpotifyApiError).code)
    }

    @Test
    fun `playEpisode returns SpotifyApiError on HTTP 404 no active device`() = runTest {
        val body = """{"error":{"status":404,"message":"No active device found"}}"""
        val client = makeClient(status = HttpStatusCode.NotFound, body = body)
        val result = client.playEpisode("spotify:episode:abc")

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.SpotifyApiError>(result.error)
        assertEquals(404, (result.error as AppError.SpotifyApiError).code)
    }

    @Test
    fun `playEpisode returns SpotifyApiError on HTTP 401`() = runTest {
        val client = makeClient(status = HttpStatusCode.Unauthorized, body = """{"error":"Unauthorized"}""")
        val result = client.playEpisode("spotify:episode:abc")

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.SpotifyApiError>(result.error)
        assertEquals(401, (result.error as AppError.SpotifyApiError).code)
    }

    // -----------------------------------------------------------------------
    // playEpisode — network exception
    // -----------------------------------------------------------------------

    @Test
    fun `playEpisode returns Unknown on network exception`() = runTest {
        val errorEngine = MockEngine { _ ->
            throw Exception("Network unavailable")
        }
        val httpClient = HttpClient(errorEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = SpotifyPlayerApiClient(httpClient)

        val result = client.playEpisode("spotify:episode:abc")

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.Unknown>(result.error)
        assertNotNull((result.error as AppError.Unknown).cause)
    }

    @Test
    fun `playEpisode does NOT wrap CancellationException — it propagates`() = runTest {
        val cancelEngine = MockEngine { _ ->
            throw kotlinx.coroutines.CancellationException("cancelled")
        }
        val httpClient = HttpClient(cancelEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = SpotifyPlayerApiClient(httpClient)

        var cancelled = false
        try {
            client.playEpisode("spotify:episode:abc")
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        kotlin.test.assertTrue(cancelled, "CancellationException must propagate, not be wrapped")
    }

    // -----------------------------------------------------------------------
    // playEpisode — rate limit
    // -----------------------------------------------------------------------

    @Test
    fun `playEpisode returns RateLimitExceeded on HTTP 429`() = runTest {
        val client = makeClient(status = HttpStatusCode.TooManyRequests, body = "")
        val result = client.playEpisode("spotify:episode:abc")

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.RateLimitExceeded>(result.error)
    }

    // -----------------------------------------------------------------------
    // playEpisode — validation
    // -----------------------------------------------------------------------

    @Test
    fun `playEpisode throws IllegalArgumentException on blank episodeUri`() = runTest {
        val client = makeClient(status = HttpStatusCode.NoContent, body = "")
        var threw = false
        try {
            client.playEpisode("")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        kotlin.test.assertTrue(threw, "playEpisode must throw IllegalArgumentException for blank URI")
    }

    // -----------------------------------------------------------------------
    // hasActiveDevice — success
    // -----------------------------------------------------------------------

    @Test
    fun `hasActiveDevice returns true on 200 with device`() = runTest {
        val body = """{"device": {"id": "device123"}}"""
        val client = makeClient(status = HttpStatusCode.OK, body = body)
        kotlin.test.assertTrue(client.hasActiveDevice())
    }

    @Test
    fun `hasActiveDevice returns false on 200 with null device`() = runTest {
        val body = """{"device": null}"""
        val client = makeClient(status = HttpStatusCode.OK, body = body)
        kotlin.test.assertFalse(client.hasActiveDevice())
    }

    @Test
    fun `hasActiveDevice returns false on 204 No Content`() = runTest {
        val client = makeClient(status = HttpStatusCode.NoContent, body = "")
        kotlin.test.assertFalse(client.hasActiveDevice())
    }

    @Test
    fun `hasActiveDevice returns false on network exception`() = runTest {
        val errorEngine = MockEngine { _ ->
            throw Exception("Network unavailable")
        }
        val httpClient = HttpClient(errorEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = SpotifyPlayerApiClient(httpClient)
        kotlin.test.assertFalse(client.hasActiveDevice())
    }

    @Test
    fun `hasActiveDevice rethrows CancellationException`() = runTest {
        val cancelEngine = MockEngine { _ ->
            throw kotlinx.coroutines.CancellationException("cancelled")
        }
        val httpClient = HttpClient(cancelEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = SpotifyPlayerApiClient(httpClient)
        var threw = false
        try {
            client.hasActiveDevice()
        } catch (e: kotlinx.coroutines.CancellationException) {
            threw = true
        }
        kotlin.test.assertTrue(threw, "CancellationException must propagate from hasActiveDevice")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun makeClient(
        status: HttpStatusCode,
        body: String
    ): SpotifyPlayerApiClient {
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return SpotifyPlayerApiClient(httpClient)
    }
}
