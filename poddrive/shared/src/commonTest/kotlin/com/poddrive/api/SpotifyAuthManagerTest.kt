package com.poddrive.api

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpotifyAuthManagerTest {

    private val fakeStorage = FakeSecureTokenStorage()
    private val testClientId = "test_client_id_123"

    // -----------------------------------------------------------------------
    // generateCodeVerifier
    // -----------------------------------------------------------------------

    @Test
    fun `generateCodeVerifier returns string of exactly 43 characters`() {
        val manager = makeManager()
        val verifier = manager.generateCodeVerifier()
        assertEquals(43, verifier.length, "Code verifier must be exactly 43 characters")
    }

    @Test
    fun `generateCodeVerifier contains only URL-safe characters`() {
        val manager = makeManager()
        val verifier = manager.generateCodeVerifier()
        val urlSafePattern = Regex("[A-Za-z0-9\\-._~]+")
        assertTrue(
            urlSafePattern.matches(verifier),
            "Code verifier must only contain URL-safe chars [A-Za-z0-9-._~], got: $verifier"
        )
    }

    @Test
    fun `generateCodeVerifier produces unique values on successive calls`() {
        val manager = makeManager()
        val v1 = manager.generateCodeVerifier()
        val v2 = manager.generateCodeVerifier()
        // Statistically guaranteed — probability of collision is ~1/(64^43)
        assertFalse(v1 == v2, "Two successive verifiers should differ")
    }

    // -----------------------------------------------------------------------
    // generateCodeChallenge
    // -----------------------------------------------------------------------

    @Test
    fun `generateCodeChallenge returns base64url string without padding`() {
        val manager = makeManager()
        val verifier = manager.generateCodeVerifier()
        val challenge = manager.generateCodeChallenge(verifier)
        assertFalse(challenge.contains('='), "Base64url output must not contain padding '='")
        assertFalse(challenge.contains('+'), "Base64url output must not contain '+'")
        assertFalse(challenge.contains('/'), "Base64url output must not contain '/'")
    }

    @Test
    fun `generateCodeChallenge is deterministic for same verifier`() {
        val manager = makeManager()
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val c1 = manager.generateCodeChallenge(verifier)
        val c2 = manager.generateCodeChallenge(verifier)
        assertEquals(c1, c2, "Code challenge must be deterministic for the same verifier")
    }

    @Test
    fun `generateCodeChallenge produces correct SHA256 base64url for known verifier`() {
        // RFC 7636 §B test vector
        // verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        // expected S256 challenge: "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        val manager = makeManager()
        val challenge = manager.generateCodeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            challenge,
            "Challenge must match RFC 7636 §B test vector"
        )
    }

    // -----------------------------------------------------------------------
    // buildAuthorizationUrl
    // -----------------------------------------------------------------------

    @Test
    fun `buildAuthorizationUrl contains response_type=code`() {
        val url = buildTestUrl()
        assertTrue(url.contains("response_type=code"), "URL must include response_type=code")
    }

    @Test
    fun `buildAuthorizationUrl contains client_id`() {
        val url = buildTestUrl()
        assertTrue(url.contains("client_id=$testClientId"), "URL must include client_id")
    }

    @Test
    fun `buildAuthorizationUrl contains redirect_uri`() {
        val url = buildTestUrl()
        assertTrue(url.contains("redirect_uri="), "URL must include redirect_uri")
        assertTrue(url.contains("poddrive"), "redirect_uri must include the custom scheme")
    }

    @Test
    fun `buildAuthorizationUrl contains code_challenge_method=S256`() {
        val url = buildTestUrl()
        assertTrue(
            url.contains("code_challenge_method=S256"),
            "URL must specify S256 PKCE method"
        )
    }

    @Test
    fun `buildAuthorizationUrl contains code_challenge`() {
        val url = buildTestUrl()
        assertTrue(url.contains("code_challenge=test_challenge"), "URL must include code_challenge")
    }

    @Test
    fun `buildAuthorizationUrl contains all five required scopes`() {
        val url = buildTestUrl()
        val requiredScopes = listOf(
            "user-library-read",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-recently-played",
            "streaming"
        )
        requiredScopes.forEach { scope ->
            assertTrue(url.contains(scope), "URL must include scope: $scope")
        }
    }

    @Test
    fun `buildAuthorizationUrl contains state parameter`() {
        val url = buildTestUrl()
        assertTrue(url.contains("state=test_state"), "URL must include CSRF state parameter")
    }

    @Test
    fun `buildAuthorizationUrl points to Spotify authorize endpoint`() {
        val url = buildTestUrl()
        assertTrue(
            url.startsWith("https://accounts.spotify.com/authorize"),
            "URL must target Spotify authorize endpoint"
        )
    }

    // -----------------------------------------------------------------------
    // exchangeCodeForTokens
    // -----------------------------------------------------------------------

    @Test
    fun `exchangeCodeForTokens returns Success and saves tokens on HTTP 200`() = runTest {
        val responseJson = """
            {
              "access_token": "test_access_token",
              "refresh_token": "test_refresh_token",
              "expires_in": 3600,
              "token_type": "Bearer"
            }
        """.trimIndent()

        val manager = makeManager(mockResponse = responseJson, status = HttpStatusCode.OK)
        val result = manager.exchangeCodeForTokens("auth_code_123", "verifier_abc")

        assertIs<Result.Success<Unit>>(result)
        assertEquals("test_access_token", fakeStorage.getAccessToken())
        assertEquals("test_refresh_token", fakeStorage.getRefreshToken())
    }

    @Test
    fun `exchangeCodeForTokens returns SpotifyApiError on HTTP 400`() = runTest {
        val manager = makeManager(
            mockResponse = """{"error":"invalid_grant","error_description":"Bad code"}""",
            status = HttpStatusCode.BadRequest
        )
        val result = manager.exchangeCodeForTokens("bad_code", "verifier")

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.SpotifyApiError>(result.error)
        assertEquals(400, (result.error as AppError.SpotifyApiError).code)
    }

    @Test
    fun `exchangeCodeForTokens returns SpotifyApiError on HTTP 401`() = runTest {
        val manager = makeManager(
            mockResponse = """{"error":"invalid_client"}""",
            status = HttpStatusCode.Unauthorized
        )
        val result = manager.exchangeCodeForTokens("code", "verifier")

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.SpotifyApiError>(result.error)
        assertEquals(401, (result.error as AppError.SpotifyApiError).code)
    }

    @Test
    fun `exchangeCodeForTokens returns Unknown on network exception`() = runTest {
        val errorEngine = MockEngine { _ ->
            throw Exception("Network unavailable")
        }
        val httpClient = HttpClient(errorEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val manager = SpotifyAuthManager(
            tokenStorage = fakeStorage,
            httpClient = httpClient,
            clientId = testClientId
        )

        val result = manager.exchangeCodeForTokens("code", "verifier")

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.Unknown>(result.error)
        assertNotNull((result.error as AppError.Unknown).cause)
    }

    @Test
    fun `exchangeCodeForTokens does NOT save tokens on failure`() = runTest {
        val manager = makeManager(
            mockResponse = """{"error":"invalid_grant"}""",
            status = HttpStatusCode.BadRequest
        )
        manager.exchangeCodeForTokens("bad_code", "verifier")

        assertEquals(null, fakeStorage.getAccessToken(), "Tokens must not be saved on failure")
        assertEquals(null, fakeStorage.getRefreshToken(), "Tokens must not be saved on failure")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun makeManager(
        mockResponse: String = "",
        status: HttpStatusCode = HttpStatusCode.OK
    ): SpotifyAuthManager {
        val engine = MockEngine { _ ->
            respond(
                content = mockResponse,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return SpotifyAuthManager(
            tokenStorage = fakeStorage,
            httpClient = httpClient,
            clientId = testClientId
        )
    }

    private fun buildTestUrl(): String {
        val manager = makeManager()
        return manager.buildAuthorizationUrl(
            codeChallenge = "test_challenge",
            state = "test_state"
        )
    }
}
