package com.poddrive.api

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Fetches podcast library data from the Spotify Web API.
 *
 * Uses the [apiHttpClient] (bearer-token authenticated, Auth plugin handles silent refresh).
 * Calls api.spotify.com — never accounts.spotify.com.
 *
 * Pagination: follows `next` links until null.
 * Rate limiting: handled transparently by Ktor's HttpRequestRetry plugin (3 retries, exp. backoff).
 *
 * [Source: architecture.md#API & Communication Patterns — "All Spotify Web API calls via SpotifyApiClient"]
 */
class SpotifyLibraryApiClient(private val httpClient: HttpClient) {

    /**
     * Fetches ALL followed Spotify shows (podcasts) for the authenticated user.
     * Follows `next` pagination links until exhausted.
     *
     * Requires scope: `user-library-read`
     */
    suspend fun fetchFollowedShows(): Result<List<SpotifyShow>, AppError> {
        return try {
            val items = mutableListOf<SpotifyShowItem>()
            var url: String? = "$BASE_URL/v1/me/shows"
            var isFirstPage = true
            while (url != null) {
                val response = if (isFirstPage) {
                    isFirstPage = false
                    httpClient.get(url) { parameter("limit", 50) }
                } else {
                    httpClient.get(url)  // subsequent pages include limit in the next URL
                }
                if (!response.status.isSuccess()) {
                    return Result.Failure(
                        AppError.SpotifyApiError(response.status.value, response.status.description)
                    )
                }
                val page = response.body<SpotifyShowsResponse>()
                items += page.items
                url = page.next
            }
            Result.Success(items.map { it.show })
        } catch (e: CancellationException) {
            throw e
        } catch (e: kotlinx.io.IOException) {
            Result.Failure(AppError.NetworkUnavailable)
        } catch (e: io.ktor.client.network.sockets.ConnectTimeoutException) {
            Result.Failure(AppError.NetworkUnavailable)
        } catch (e: io.ktor.client.network.sockets.SocketTimeoutException) {
            Result.Failure(AppError.NetworkUnavailable)
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }

    /**
     * Fetches episodes for a given [showBaseId] (base ID, NOT the "spotify:show:" URI).
     * Follows `next` pagination links. Passes `market=from_token` so that `resume_point`
     * is populated in each episode object.
     *
     * Requires scope: `user-library-read`
     */
    suspend fun fetchShowEpisodes(showBaseId: String): Result<List<SpotifyEpisodeObject>, AppError> {
        return try {
            val items = mutableListOf<SpotifyEpisodeObject>()
            var url: String? = "$BASE_URL/v1/shows/$showBaseId/episodes"
            var isFirstPage = true
            while (url != null) {
                val response = if (isFirstPage) {
                    isFirstPage = false
                    httpClient.get(url) {
                        parameter("limit", 50)
                        parameter("market", "from_token")
                    }
                } else {
                    httpClient.get(url)
                }
                if (!response.status.isSuccess()) {
                    return Result.Failure(
                        AppError.SpotifyApiError(response.status.value, response.status.description)
                    )
                }
                val page = response.body<SpotifyEpisodesResponse>()
                items += page.items
                url = page.next
            }
            Result.Success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: kotlinx.io.IOException) {
            Result.Failure(AppError.NetworkUnavailable)
        } catch (e: io.ktor.client.network.sockets.ConnectTimeoutException) {
            Result.Failure(AppError.NetworkUnavailable)
        } catch (e: io.ktor.client.network.sockets.SocketTimeoutException) {
            Result.Failure(AppError.NetworkUnavailable)
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }

    /**
     * Searches for Spotify podcast shows matching [query].
     * Returns up to 20 results ordered by Spotify relevance.
     * Passes `market=from_token` so images and metadata match the user's locale.
     *
     * Requires scope: `user-library-read` (bearer token already ensures authentication).
     */
    suspend fun searchShows(query: String): Result<List<SpotifyShow>, AppError> {
        return try {
            val response = httpClient.get("$BASE_URL/v1/search") {
                parameter("q", query)
                parameter("type", "show")
                parameter("limit", 20)
                parameter("market", "from_token")
            }
            if (!response.status.isSuccess()) {
                return Result.Failure(
                    AppError.SpotifyApiError(response.status.value, response.status.description)
                )
            }
            val body = response.body<SpotifySearchResponse>()
            Result.Success(body.shows.items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: kotlinx.io.IOException) {
            Result.Failure(AppError.NetworkUnavailable)
        } catch (e: io.ktor.client.network.sockets.ConnectTimeoutException) {
            Result.Failure(AppError.NetworkUnavailable)
        } catch (e: io.ktor.client.network.sockets.SocketTimeoutException) {
            Result.Failure(AppError.NetworkUnavailable)
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }

    companion object {
        const val BASE_URL = "https://api.spotify.com"
    }
}
