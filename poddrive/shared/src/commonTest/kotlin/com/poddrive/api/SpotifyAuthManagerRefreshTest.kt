package com.poddrive.api

import com.poddrive.storage.FakeSecureTokenStorage
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [SpotifyAuthManager.refreshAccessToken] (Story 1.4).
 *
 * Covers all return-value and side-effect scenarios:
 * - Success with and without token rotation
 * - HTTP 400/401 (revocation) → tokens cleared
 * - Other HTTP errors → tokens preserved
 * - Network exceptions → tokens preserved
 * - [SpotifyAuthManager] implements [TokenRefresher] interface
 *
 * Uses [MockEngine] and [FakeSecureTokenStorage] — no platform Keychain or network calls.
 */
class SpotifyAuthManagerRefreshTest {

    private val testClientId = "test_client_id"
    private val existingAccessToken = "old_access_token"
    private val existingRefreshToken = "old_refresh_token"

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Pre-seeded storage with existing tokens. */
    private fun makeStorage(): FakeSecureTokenStorage =
        FakeSecureTokenStorage().also { it.saveTokens(existingAccessToken, existingRefreshToken) }

    /** Creates a manager backed by a [MockEngine] that returns [responseBody] with [status]. */
    private fun makeManagerWithResponse(
        storage: FakeSecureTokenStorage,
        responseBody: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): SpotifyAuthManager {
        val engine = MockEngine {
            respond(
                content = responseBody,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return SpotifyAuthManager(storage, client, testClientId)
    }

    /** Creates a manager backed by a [MockEngine] that throws [exception] on every request. */
    private fun makeManagerWithException(
        storage: FakeSecureTokenStorage,
        exception: Throwable = RuntimeException("Simulated network failure")
    ): SpotifyAuthManager {
        val engine = MockEngine { throw exception }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return SpotifyAuthManager(storage, client, testClientId)
    }

    // -----------------------------------------------------------------------
    // Success — no token rotation
    // -----------------------------------------------------------------------

    @Test
    fun `refreshAccessToken success returns BearerTokens with new access token`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithResponse(
            storage,
            """{"access_token":"new_access","expires_in":3600,"token_type":"Bearer"}"""
        )

        val result = manager.refreshAccessToken(existingRefreshToken)

        assertNotNull(result, "Expected BearerTokens on success")
        assertEquals("new_access", result.accessToken)
    }

    @Test
    fun `refreshAccessToken success saves new access token to storage`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithResponse(
            storage,
            """{"access_token":"new_access","expires_in":3600,"token_type":"Bearer"}"""
        )

        manager.refreshAccessToken(existingRefreshToken)

        assertEquals("new_access", storage.getAccessToken(), "New access token must be persisted")
    }

    @Test
    fun `refreshAccessToken without rotation keeps existing refresh token in BearerTokens`() = runTest {
        val storage = makeStorage()
        // No refresh_token field → token rotation not applied by Spotify
        val manager = makeManagerWithResponse(
            storage,
            """{"access_token":"new_access","expires_in":3600,"token_type":"Bearer"}"""
        )

        val result = manager.refreshAccessToken(existingRefreshToken)

        assertNotNull(result)
        assertEquals(
            existingRefreshToken,
            result.refreshToken,
            "Original refresh token must be reused in BearerTokens when rotation is absent"
        )
    }

    @Test
    fun `refreshAccessToken without rotation keeps existing refresh token in storage`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithResponse(
            storage,
            """{"access_token":"new_access","expires_in":3600,"token_type":"Bearer"}"""
        )

        manager.refreshAccessToken(existingRefreshToken)

        assertEquals(
            existingRefreshToken,
            storage.getRefreshToken(),
            "Original refresh token must remain persisted when rotation is absent"
        )
    }

    // -----------------------------------------------------------------------
    // Success — with token rotation
    // -----------------------------------------------------------------------

    @Test
    fun `refreshAccessToken with rotation saves rotated refresh token to storage`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithResponse(
            storage,
            """{"access_token":"new_access","refresh_token":"rotated_refresh","expires_in":3600,"token_type":"Bearer"}"""
        )

        val result = manager.refreshAccessToken(existingRefreshToken)

        assertNotNull(result)
        assertEquals("rotated_refresh", result.refreshToken,
            "Rotated refresh token must appear in returned BearerTokens")
        assertEquals("rotated_refresh", storage.getRefreshToken(),
            "Rotated refresh token must be persisted")
        assertEquals("new_access", storage.getAccessToken())
    }

    // -----------------------------------------------------------------------
    // HTTP 401 — refresh token revoked externally
    // -----------------------------------------------------------------------

    @Test
    fun `refreshAccessToken 401 returns null`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithResponse(
            storage,
            """{"error":"invalid_grant","error_description":"Refresh token revoked"}""",
            HttpStatusCode.Unauthorized
        )

        val result = manager.refreshAccessToken(existingRefreshToken)

        assertNull(result, "Must return null on 401")
    }

    @Test
    fun `refreshAccessToken 401 clears access token from storage`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithResponse(
            storage,
            """{"error":"invalid_grant"}""",
            HttpStatusCode.Unauthorized
        )

        manager.refreshAccessToken(existingRefreshToken)

        assertNull(storage.getAccessToken(), "Access token must be cleared on 401")
    }

    @Test
    fun `refreshAccessToken 401 clears refresh token from storage`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithResponse(
            storage,
            """{"error":"invalid_grant"}""",
            HttpStatusCode.Unauthorized
        )

        manager.refreshAccessToken(existingRefreshToken)

        assertNull(storage.getRefreshToken(), "Refresh token must be cleared on 401")
    }

    // -----------------------------------------------------------------------
    // HTTP 400 — bad_request (also indicates revocation)
    // -----------------------------------------------------------------------

    @Test
    fun `refreshAccessToken 400 returns null and clears tokens`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithResponse(
            storage,
            """{"error":"invalid_grant"}""",
            HttpStatusCode.BadRequest
        )

        val result = manager.refreshAccessToken(existingRefreshToken)

        assertNull(result, "Must return null on 400")
        assertNull(storage.getAccessToken(), "Access token must be cleared on 400")
        assertNull(storage.getRefreshToken(), "Refresh token must be cleared on 400")
    }

    // -----------------------------------------------------------------------
    // HTTP 500 — transient server error
    // -----------------------------------------------------------------------

    @Test
    fun `refreshAccessToken 500 returns null without clearing tokens`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithResponse(
            storage,
            """{"error":"internal_server_error"}""",
            HttpStatusCode.InternalServerError
        )

        val result = manager.refreshAccessToken(existingRefreshToken)

        assertNull(result, "Must return null on 500")
        assertEquals(existingAccessToken, storage.getAccessToken(),
            "Access token must NOT be cleared on transient 500")
        assertEquals(existingRefreshToken, storage.getRefreshToken(),
            "Refresh token must NOT be cleared on transient 500")
    }

    // -----------------------------------------------------------------------
    // Network exception — transient failure
    // -----------------------------------------------------------------------

    @Test
    fun `refreshAccessToken network exception returns null without clearing tokens`() = runTest {
        val storage = makeStorage()
        val manager = makeManagerWithException(storage)

        val result = manager.refreshAccessToken(existingRefreshToken)

        assertNull(result, "Must return null on network exception")
        assertEquals(existingAccessToken, storage.getAccessToken(),
            "Access token must NOT be cleared on network error")
        assertEquals(existingRefreshToken, storage.getRefreshToken(),
            "Refresh token must NOT be cleared on network error")
    }

    // -----------------------------------------------------------------------
    // Interface contract
    // -----------------------------------------------------------------------

    @Test
    fun `SpotifyAuthManager is usable as TokenRefresher`() {
        val storage = makeStorage()
        val engine = MockEngine {
            respond("", HttpStatusCode.OK, headersOf())
        }
        val client = HttpClient(engine)
        val manager = SpotifyAuthManager(storage, client, testClientId)

        // Compile-time and runtime assertion: manager is assignable to TokenRefresher
        val refresher: TokenRefresher = manager
        assertNotNull(refresher)
    }
}
