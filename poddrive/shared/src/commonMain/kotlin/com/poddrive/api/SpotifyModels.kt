package com.poddrive.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class SpotifyErrorResponse(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null
)

/**
 * Response body for the Spotify refresh-token grant (POST /api/token, grant_type=refresh_token).
 *
 * [refreshToken] is nullable because Spotify may or may not issue a new token via rotation.
 * When absent, the caller must reuse the existing refresh token.
 * Compare with [SpotifyTokenResponse] where [refreshToken] is always present (initial exchange).
 *
 * [Source: architecture.md#Authentication & Security — token refresh (NFR20)]
 */
@Serializable
data class SpotifyRefreshResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String
)
