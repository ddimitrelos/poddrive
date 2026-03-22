package com.poddrive.api

import io.ktor.client.plugins.auth.providers.BearerTokens

/**
 * Contract for transparently refreshing a Spotify access token.
 *
 * Implemented by [SpotifyAuthManager] and consumed by [createSpotifyHttpClient]'s
 * Ktor `Auth` bearer `refreshTokens` block.
 *
 * Using an interface (rather than a raw lambda) allows the Swift/iOS layer to pass
 * an [SpotifyAuthManager] instance directly without bridging ceremony.
 *
 * Return semantics:
 * - [BearerTokens]: refresh succeeded; Ktor retries the failed request with the new token
 * - null: refresh failed (revoked or network error); Ktor surfaces the original 401 to the caller
 *
 * [Source: architecture.md#API & Communication Patterns — NFR20, AR8]
 */
fun interface TokenRefresher {
    /**
     * Attempts to exchange [refreshToken] for a fresh access token.
     *
     * Implementations MUST NOT throw — return null on all failure paths.
     */
    suspend fun refreshAccessToken(refreshToken: String): BearerTokens?
}
