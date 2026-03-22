package com.poddrive.api

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result
import com.poddrive.storage.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Handles all Spotify token lifecycle operations:
 * - PKCE authorization URL construction
 * - Initial authorization code → token exchange
 * - Silent background token refresh (Story 1.4, implements [TokenRefresher])
 *
 * Implements [TokenRefresher] so it can be passed to [createSpotifyHttpClient] directly,
 * enabling the Ktor `Auth` plugin to call [refreshAccessToken] transparently on 401.
 *
 * Token lifecycle rule: ONLY [SpotifyAuthManager] calls [SecureTokenStorage.saveTokens]
 * or [SecureTokenStorage.clear] — enforced by architecture.
 * [Source: architecture.md#Process Patterns]
 */
class SpotifyAuthManager(
    private val tokenStorage: SecureTokenStorage,
    private val httpClient: HttpClient,
    private val clientId: String,
    private val redirectUri: String = REDIRECT_URI
) : TokenRefresher {
    /**
     * Generates a cryptographically secure, URL-safe code verifier (43 characters).
     * Uses [secureRandomBytes] (CSPRNG) to satisfy RFC 7636 §4.1 entropy requirement.
     * 32 random bytes → Base64url (no padding) = 43 characters, ~256 bits entropy.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun generateCodeVerifier(): String {
        val bytes = secureRandomBytes(32)
        return Base64.UrlSafe.encode(bytes).trimEnd('=')
    }

    /**
     * Derives the PKCE code_challenge from [verifier].
     * Algorithm: BASE64URL(SHA-256(ASCII([verifier]))), no padding characters.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun generateCodeChallenge(verifier: String): String {
        val hash = sha256(verifier.encodeToByteArray())
        return Base64.UrlSafe.encode(hash).trimEnd('=')
    }

    /**
     * Builds the full Spotify authorization URL for opening in a secure browser.
     * Includes all required PKCE + scope parameters.
     * The [state] parameter prevents CSRF attacks.
     */
    fun buildAuthorizationUrl(codeChallenge: String, state: String): String {
        require(clientId.isNotBlank()) { "clientId must not be blank — set SPOTIFY_CLIENT_ID" }
        val builder = URLBuilder(AUTHORIZE_URL)
        builder.parameters.apply {
            append("client_id", clientId)
            append("response_type", "code")
            append("redirect_uri", redirectUri)
            append("code_challenge_method", "S256")
            append("code_challenge", codeChallenge)
            append("scope", REQUIRED_SCOPES)
            append("state", state)
        }
        return builder.buildString()
    }

    /**
     * Exchanges an authorisation [code] for access and refresh tokens via Spotify token endpoint.
     *
     * On success: saves tokens to [tokenStorage] and returns [Result.Success].
     * On HTTP error: returns [Result.Failure] with [AppError.SpotifyApiError], using the
     *   Spotify error body message when available (e.g. "invalid_grant").
     * On network/IO exception: returns [Result.Failure] with [AppError.Unknown].
     *
     * Never throws — all errors are returned as [Result.Failure].
     */
    suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String
    ): Result<Unit, AppError> = try {
        val response = httpClient.submitForm(
            url = TOKEN_URL,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", clientId)
                append("code_verifier", codeVerifier)
            }
        )
        if (response.status.isSuccess()) {
            val tokenResponse = Json { ignoreUnknownKeys = true }
                .decodeFromString<SpotifyTokenResponse>(response.bodyAsText())
            tokenStorage.saveTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
            Result.Success(Unit)
        } else {
            val errorMessage = parseSpotifyErrorMessage(response.bodyAsText())
                ?: response.status.description
            Result.Failure(
                AppError.SpotifyApiError(
                    code = response.status.value,
                    message = errorMessage
                )
            )
        }
    } catch (e: Exception) {
        Result.Failure(AppError.Unknown(e))
    }

    /**
     * User-initiated token revocation (Story 1.5).
     *
     * Clears all stored tokens from [tokenStorage], forcing the user back to the
     * unauthenticated state. Called by [RevokeSpotifyAccessUseCase] in response to
     * the "Disconnect Spotify" user action.
     *
     * Token lifecycle rule: ONLY [SpotifyAuthManager] calls [SecureTokenStorage.clear]
     * or [SecureTokenStorage.saveTokens]. This is the public revocation entry point.
     * [Source: architecture.md#Process Patterns]
     */
    fun revokeAccess() {
        tokenStorage.clear()
    }

    /**
     * Silently exchanges [refreshToken] for a fresh access token (Story 1.4).
     *
     * Implements [TokenRefresher] — called by the Ktor `Auth` plugin on 401 responses.
     *
     * On success (2xx):
     *   - Saves new access token (and new refresh token if Spotify rotates it) to [tokenStorage]
     *   - Returns [BearerTokens] so Ktor retries the original request
     * On HTTP 400/401 (revoked externally):
     *   - Clears all tokens to force re-auth flow
     *   - Returns null
     * On other HTTP errors (transient 5xx):
     *   - Returns null without clearing tokens (they may still be valid)
     * On network exception:
     *   - Returns null without clearing tokens (transient failure)
     *
     * Never throws — all failure paths return null.
     * [Source: architecture.md#Authentication & Security — NFR20, AR8]
     */
    override suspend fun refreshAccessToken(refreshToken: String): BearerTokens? = try {
        val response = httpClient.submitForm(
            url = TOKEN_URL,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", clientId)
            }
        )
        when {
            response.status.isSuccess() -> {
                val body = lenientJson.decodeFromString<SpotifyRefreshResponse>(response.bodyAsText())
                // Preserve old refresh token when Spotify doesn't rotate (rotation is optional)
                val newRefreshToken = body.refreshToken ?: refreshToken
                tokenStorage.saveTokens(body.accessToken, newRefreshToken)
                BearerTokens(body.accessToken, newRefreshToken)
            }
            response.status.value in 400..401 -> {
                // Refresh token is revoked — clear credentials and force re-authentication
                tokenStorage.clear()
                null
            }
            else -> {
                // Transient server error — tokens still valid, caller receives original 401
                null
            }
        }
    } catch (e: Exception) {
        // Re-throw CancellationException to preserve coroutine structured concurrency (AR8)
        if (e is CancellationException) throw e
        // Network error — do NOT clear tokens; they remain valid for retry
        null
    }

    companion object {
        const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val REDIRECT_URI = "poddrive://callback"

        /**
         * Minimum required Spotify OAuth scopes (NFR8).
         * DO NOT add scopes beyond this list without a product requirement.
         */
        const val REQUIRED_SCOPES =
            "user-library-read user-read-playback-state user-modify-playback-state " +
                "user-read-recently-played streaming"

        private val lenientJson = Json { ignoreUnknownKeys = true }

        /** Attempts to extract a human-readable message from a Spotify error response body. */
        internal fun parseSpotifyErrorMessage(body: String): String? = try {
            val err = lenientJson.decodeFromString<SpotifyErrorResponse>(body)
            err.errorDescription ?: err.error
        } catch (_: Exception) {
            null
        }
    }
}
