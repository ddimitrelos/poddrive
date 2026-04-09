package com.poddrive.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val MIN_MATCH_LENGTH = 5

/**
 * Discovers the RSS feed URL for a podcast via the iTunes Podcast Search API.
 *
 * Endpoint: GET https://itunes.apple.com/search?term={name}&entity=podcast&limit=10
 * - Free, no authentication required.
 * - Returns a `feedUrl` field per result.
 *
 * Matching algorithm (AC3): selects the first result whose normalized `collectionName`
 * contains the normalized `podcastName` (or vice versa), case-insensitive, stripping punctuation.
 *
 * All errors are swallowed — returns null on any failure (AC5).
 * [CancellationException] is always re-thrown (AC7).
 *
 * [Source: Story 4.8, ADR-002]
 */
class ItunesPodcastSearchClient(private val httpClient: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns the RSS feed URL for the podcast with the given name, or null if not found.
     */
    suspend fun findFeedUrl(podcastName: String): String? {
        return try {
            val response = httpClient.get("https://itunes.apple.com/search") {
                parameter("term", podcastName)
                parameter("entity", "podcast")
                parameter("limit", 10)
            }
            if (!response.status.isSuccess()) return null

            val body = response.bodyAsText()
            val searchResponse = json.decodeFromString<ItunesSearchResponse>(body)
            val normalizedQuery = normalize(podcastName)

            searchResponse.results.firstOrNull { podcast ->
                val normalizedResult = normalize(podcast.collectionName)
                // Exact match is always valid regardless of length.
                // Containment match is only used when both sides are at least MIN_MATCH_LENGTH chars,
                // preventing short names like "AI" or "Tech" from matching unrelated podcasts.
                when {
                    normalizedResult == normalizedQuery -> true
                    normalizedResult.length >= MIN_MATCH_LENGTH && normalizedQuery.length >= MIN_MATCH_LENGTH ->
                        normalizedResult.contains(normalizedQuery) || normalizedQuery.contains(normalizedResult)
                    else -> false
                }
            }?.feedUrl?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun normalize(name: String): String =
        name.lowercase().replace(Regex("[^\\w\\s]"), "").trim()
}

@Serializable
private data class ItunesSearchResponse(
    val results: List<ItunesPodcast> = emptyList()
)

@Serializable
private data class ItunesPodcast(
    @SerialName("collectionName") val collectionName: String,
    @SerialName("feedUrl") val feedUrl: String? = null
)
