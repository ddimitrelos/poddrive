package com.poddrive.api

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sends playback commands to the Spotify Web API.
 *
 * Uses [httpClient] (the bearer-authenticated `apiHttpClient` from [createSpotifyHttpClient]).
 * Do NOT pass [createAuthHttpClient] here — it lacks the `Auth` plugin and cannot attach tokens.
 *
 * Requires OAuth scope: `user-modify-playback-state` (added in Story 4.6).
 *
 * [Source: Story 4.6 — Spotify Episode Auto-Play via Web API]
 */
class SpotifyPlayerApiClient(private val httpClient: HttpClient) {

    /**
     * Instructs Spotify to start playback of [episodeUri] on the user's active device.
     *
     * [episodeUri] must be a full Spotify episode URI, e.g. `spotify:episode:6FZj6eVQLmvii10w73SxEw`.
     *
     * **Requires Spotify Premium.** The Spotify Web API's player endpoints (`PUT /v1/me/player/play`)
     * are Premium-only for ALL content types, including podcast episodes. Free-tier users will receive
     * HTTP 403 with reason `PREMIUM_REQUIRED`. There is no podcast exception.
     * See: https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback
     *
     * Returns:
     * - [Result.Success] when Spotify responds with 2xx (typically 204 No Content).
     * - [Result.Failure] with [AppError.RateLimitExceeded] on HTTP 429 (caller should back off).
     * - [Result.Failure] with [AppError.SpotifyApiError] on other 4xx/5xx (e.g. 404 no active
     *   device, 403 PREMIUM_REQUIRED for free-tier accounts, 401 scope missing).
     * - [Result.Failure] with [AppError.Unknown] on network / IO exception.
     *
     * Never throws — [CancellationException] is re-thrown, all other exceptions are wrapped.
     *
     * @throws IllegalArgumentException if [episodeUri] is blank.
     */
    suspend fun playEpisode(episodeUri: String): Result<Unit, AppError> {
        require(episodeUri.isNotBlank()) { "episodeUri must not be blank" }
        return try {
            val response = httpClient.put("$BASE_URL/v1/me/player/play") {
                contentType(ContentType.Application.Json)
                setBody(PlayRequest(uris = listOf(episodeUri)))
            }
            when {
                response.status.isSuccess() -> Result.Success(Unit)
                response.status.value == 429 -> Result.Failure(AppError.RateLimitExceeded)
                else -> Result.Failure(
                    AppError.SpotifyApiError(
                        code = response.status.value,
                        message = response.bodyAsText().ifBlank { response.status.description }
                    )
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Failure(AppError.Unknown(e))
        }
    }

    /**
     * Checks whether Spotify has an active playback device registered in the cloud.
     *
     * Polls `GET /v1/me/player`:
     * - HTTP 200 with a non-null `device` field → `true` (device registered, ready for play commands)
     * - HTTP 200 with `device: null` or HTTP 204 (no active session) → `false`
     * - Any other non-2xx or network/IO exception → `false` (transient; next poll may succeed)
     * - `CancellationException` → **re-thrown** (architecture rule)
     *
     * Does not throw (except for [CancellationException]).
     *
     * Requires OAuth scope: `user-read-playback-state` (already present from Story 4.6).
     */
    suspend fun hasActiveDevice(): Boolean {
        return try {
            val response = httpClient.get("$BASE_URL/v1/me/player")
            val status = response.status.value
            val body = if (status != 204) response.bodyAsText() else "(no body)"
            System.err.println("D/hasActiveDevice: HTTP $status body=$body")
            when {
                status == 204 -> false
                response.status.isSuccess() -> response.body<ActivePlayerResponse>().device != null
                else -> false
            }
        } catch (e: Exception) {
            System.err.println("D/hasActiveDevice: exception ${e::class.simpleName}: ${e.message}")
            if (e is CancellationException) throw e
            false
        }
    }

    @Serializable
    private data class PlayRequest(
        @SerialName("uris") val uris: List<String>
    )

    companion object {
        const val BASE_URL = "https://api.spotify.com"
    }
}
