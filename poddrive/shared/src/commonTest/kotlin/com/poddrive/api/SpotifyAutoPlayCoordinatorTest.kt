package com.poddrive.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [SpotifyAutoPlayCoordinator].
 *
 * All tests use virtual time via [runTest]. The [clock] lambda is wired to [currentTime] so
 * virtual-time advances from [delay] calls drive the `clock() < deadline` exit condition.
 *
 * [Source: Story 4.7 — Active-Device Polling for Reliable Single-Tap Playback (AC8)]
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyAutoPlayCoordinatorTest {

    // -----------------------------------------------------------------------
    // Polling — device found after N polls (AC1, AC5)
    // -----------------------------------------------------------------------

    @Test
    fun `autoPlay polls until device found then returns true`() = runTest {
        // Fail the first 2 polls, succeed on the 3rd.
        val client = makePollingClient(failPollCount = 2)
        val coordinator = makeCoordinator(client)

        assertTrue(coordinator.autoPlay("spotify:episode:abc123"))
    }

    @Test
    fun `autoPlay returns true immediately when first poll finds device`() = runTest {
        // Device already present on first GET /player call (warm start).
        val client = makePollingClient(failPollCount = 0)
        val coordinator = makeCoordinator(client)

        assertTrue(coordinator.autoPlay("spotify:episode:abc123"))
    }

    // -----------------------------------------------------------------------
    // Timeout exit (AC4)
    // -----------------------------------------------------------------------

    @Test
    fun `autoPlay returns false after full timeout with no device`() = runTest {
        // hasActiveDevice always returns false (204); isHandingOff always true.
        // clock = currentTime ensures virtual-time delay() calls advance the clock.
        val client = makeStaticClient(
            getStatus = HttpStatusCode.NoContent, getBody = "",
            putStatus = HttpStatusCode.NoContent
        )
        val coordinator = SpotifyAutoPlayCoordinator(
            playerApiClient = client,
            isHandingOff = { true },
            clock = { currentTime }
        )

        val result = coordinator.autoPlay("spotify:episode:abc123")
        assertFalse(result)
        // All 10 polls (TIMEOUT / INTERVAL = 10) must have elapsed.
        assertTrue(
            currentTime >= SpotifyAutoPlayCoordinator.DEVICE_POLL_TIMEOUT_MS,
            "Expected at least DEVICE_POLL_TIMEOUT_MS virtual time to elapse"
        )
    }

    // -----------------------------------------------------------------------
    // isHandingOff guard (AC7)
    // -----------------------------------------------------------------------

    @Test
    fun `autoPlay returns false immediately when isHandingOff is false on entry`() = runTest {
        val client = makeStaticClient(
            getStatus = HttpStatusCode.OK,
            getBody = """{"device": {"id": "d1"}}""",
            putStatus = HttpStatusCode.NoContent
        )
        val coordinator = makeCoordinator(client, isHandingOff = { false })

        assertFalse(coordinator.autoPlay("spotify:episode:abc123"))
    }

    @Test
    fun `autoPlay returns false when isHandingOff becomes false mid-loop`() = runTest {
        // hasActiveDevice always returns false (no device); isHandingOff flips to false
        // after 2 invocations so the post-delay guard catches it.
        var callCount = 0
        val client = makeStaticClient(
            getStatus = HttpStatusCode.NoContent, getBody = "",
            putStatus = HttpStatusCode.NoContent
        )
        val coordinator = SpotifyAutoPlayCoordinator(
            playerApiClient = client,
            isHandingOff = { callCount++ < 2 },  // true for first 2 calls, then false
            clock = { currentTime }
        )

        assertFalse(coordinator.autoPlay("spotify:episode:abc123"))
    }

    // -----------------------------------------------------------------------
    // CancellationException propagation (AC8)
    // -----------------------------------------------------------------------

    @Test
    fun `autoPlay CancellationException propagates out of loop`() = runTest {
        val cancelEngine = MockEngine { _ -> throw CancellationException("cancelled") }
        val client = SpotifyPlayerApiClient(
            HttpClient(cancelEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
        val coordinator = makeCoordinator(client)

        var threw = false
        try {
            coordinator.autoPlay("spotify:episode:abc123")
        } catch (e: CancellationException) {
            threw = true
        }
        assertTrue(threw, "CancellationException must propagate out of the polling loop")
    }

    // -----------------------------------------------------------------------
    // Input validation
    // -----------------------------------------------------------------------

    @Test
    fun `autoPlay throws IllegalArgumentException on blank episodeUri`() = runTest {
        val coordinator = makeCoordinator(makePollingClient(failPollCount = 0))
        assertFailsWith<IllegalArgumentException> {
            coordinator.autoPlay("")
        }
    }

    @Test
    fun `autoPlay throws IllegalArgumentException on whitespace-only episodeUri`() = runTest {
        val coordinator = makeCoordinator(makePollingClient(failPollCount = 0))
        assertFailsWith<IllegalArgumentException> {
            coordinator.autoPlay("   ")
        }
    }

    // -----------------------------------------------------------------------
    // Constants (AC5, AC6)
    // -----------------------------------------------------------------------

    @Test
    fun `DEVICE_POLL_INTERVAL_MS is 500ms`() {
        assertEquals(500L, SpotifyAutoPlayCoordinator.DEVICE_POLL_INTERVAL_MS)
    }

    @Test
    fun `DEVICE_POLL_TIMEOUT_MS is 5000ms`() {
        assertEquals(5_000L, SpotifyAutoPlayCoordinator.DEVICE_POLL_TIMEOUT_MS)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a coordinator with [clock] = { currentTime } (virtual time from [runTest]),
     * [isHandingOff] = { true } by default, and the supplied [client].
     */
    private fun makeCoordinator(
        client: SpotifyPlayerApiClient,
        isHandingOff: () -> Boolean = { true }
    ): SpotifyAutoPlayCoordinator = SpotifyAutoPlayCoordinator(
        playerApiClient = client,
        isHandingOff = isHandingOff,
        clock = { 0L }   // fixed at 0 → deadline = DEVICE_POLL_TIMEOUT_MS; loop exits on device/guard
    )

    /**
     * Returns a client whose GET /player responds with 204 for the first [failPollCount] calls,
     * then with 200+device. PUT /player/play always returns 204.
     */
    private fun makePollingClient(failPollCount: Int): SpotifyPlayerApiClient {
        var getCallCount = 0
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    getCallCount++
                    if (getCallCount <= failPollCount) {
                        respond(
                            content = "",
                            status = HttpStatusCode.NoContent,
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                        )
                    } else {
                        respond(
                            content = """{"device": {"id": "device123"}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                        )
                    }
                }
                HttpMethod.Put -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                )
                else -> error("Unexpected method ${request.method} ${request.url}")
            }
        }
        return SpotifyPlayerApiClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
    }

    /**
     * Returns a client with fixed GET and PUT responses.
     */
    private fun makeStaticClient(
        getStatus: HttpStatusCode,
        getBody: String,
        putStatus: HttpStatusCode = HttpStatusCode.NoContent
    ): SpotifyPlayerApiClient {
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    content = getBody,
                    status = getStatus,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                )
                HttpMethod.Put -> respond(
                    content = "",
                    status = putStatus,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                )
                else -> error("Unexpected method ${request.method} ${request.url}")
            }
        }
        return SpotifyPlayerApiClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
    }

}
