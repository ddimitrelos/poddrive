package com.poddrive.api

import com.poddrive.storage.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Minimal Ktor [HttpClient] for token endpoint operations (no bearer Auth plugin).
 *
 * Used by [SpotifyAuthManager] for:
 * - Initial authorization code exchange (exchangeCodeForTokens)
 * - Silent token refresh (refreshAccessToken — Story 1.4)
 *
 * The token endpoints at accounts.spotify.com use form-body credentials, NOT bearer tokens.
 * Using a separate plain client (without the Auth plugin) avoids infinite refresh loops:
 * if [createSpotifyHttpClient]'s Auth plugin were also used for token ops, a 401 from the
 * token endpoint would trigger refreshTokens → token endpoint → 401 → refreshTokens → ...
 *
 * [Source: architecture.md#API & Communication Patterns — AR8]
 */
fun createAuthHttpClient(): HttpClient =
    HttpClient(createPlatformEngine()) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        // No Auth plugin — token endpoints do not use bearer authentication
        // No HttpRequestRetry — auth failures should fail fast, not retry
    }

/**
 * Factory function that creates the Ktor [HttpClient] used for all Spotify Web API calls.
 *
 * Plugins configured:
 * - ContentNegotiation with kotlinx.serialization JSON (ignoreUnknownKeys = true)
 * - Logging (INFO level)
 * - Auth bearer: loads tokens from [tokenStorage]; delegates refresh to [tokenRefresher] (Story 1.4)
 * - HttpRequestRetry: 3 retries on server errors with exponential backoff
 *
 * Token refresh (Story 1.4):
 *   When the Auth plugin receives a 401, it calls [tokenRefresher.refreshAccessToken] with the
 *   stored refresh token. On success, the original request is retried with the new token.
 *   On null return, the 401 propagates to the caller for re-auth handling.
 *
 *   Pass [tokenRefresher] = null (default) only when no refresh is needed (e.g. unit tests that
 *   test pre-refresh behaviour — the null default preserves backwards compatibility).
 *
 * IMPORTANT: Do NOT pass [SpotifyAuthManager] as the [HttpClient] constructor argument for
 * [SpotifyAuthManager] itself — use [createAuthHttpClient] to break the circular dependency.
 *
 * [Source: architecture.md#API & Communication Patterns]
 */
/**
 * Creates a plain [HttpClient] for calling unauthenticated public APIs (e.g. iTunes Search).
 *
 * Does NOT install the Auth plugin — never attaches Spotify bearer tokens.
 * Configured with ContentNegotiation + JSON so callers can parse responses normally.
 *
 * [Source: Story 4.8, Story 4.9 — iTunes Search API and RSS feed fetching]
 */
fun createItunesHttpClient(): HttpClient =
    HttpClient(createPlatformEngine()) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

fun createSpotifyHttpClient(
    tokenStorage: SecureTokenStorage,
    tokenRefresher: TokenRefresher? = null
): HttpClient =
    HttpClient(createPlatformEngine()) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) = println("KTOR>> $message")
            }
        }
        install(Auth) {
            bearer {
                loadTokens {
                    // Only require an access token to be present — a missing refresh token
                    // (e.g. storage corruption) must not block valid API requests.
                    val access = tokenStorage.getAccessToken()
                    if (access != null) {
                        BearerTokens(access, tokenStorage.getRefreshToken() ?: "")
                    } else {
                        null
                    }
                }
                refreshTokens {
                    // Story 1.4: delegate token refresh to SpotifyAuthManager.
                    // oldTokens?.refreshToken is the value loaded by loadTokens above.
                    // Returns null → Auth plugin surfaces the 401 to the caller (re-auth required).
                    val refresh = oldTokens?.refreshToken?.takeIf { it.isNotBlank() }
                        ?: return@refreshTokens null
                    tokenRefresher?.refreshAccessToken(refresh)
                }
                // Only attach bearer token to Spotify API requests — NOT to accounts.spotify.com
                // (token endpoints). This prevents accidental auth header leakage and avoids
                // triggering refreshTokens for token endpoint calls.
                sendWithoutRequest { request ->
                    request.url.host == "api.spotify.com"
                }
            }
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
    }
