package com.poddrive.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItunesPodcastSearchClientTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun makeClient(
        responseBody: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): ItunesPodcastSearchClient {
        val engine = MockEngine { _ ->
            respond(
                content = responseBody,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return ItunesPodcastSearchClient(httpClient)
    }

    // -----------------------------------------------------------------------
    // Successful match
    // -----------------------------------------------------------------------

    @Test
    fun `findFeedUrl returns feedUrl on successful name match`() = runTest {
        val client = makeClient(
            """
            {
              "resultCount": 1,
              "results": [
                {
                  "collectionName": "Tech Talks",
                  "feedUrl": "https://feeds.example.com/tech-talks"
                }
              ]
            }
            """.trimIndent()
        )

        val result = client.findFeedUrl("Tech Talks")

        assertEquals("https://feeds.example.com/tech-talks", result)
    }

    @Test
    fun `findFeedUrl matches when collectionName contains podcastName (substring)`() = runTest {
        val client = makeClient(
            """
            {
              "resultCount": 1,
              "results": [
                {
                  "collectionName": "The Tech Talks Podcast",
                  "feedUrl": "https://feeds.example.com/tech-talks"
                }
              ]
            }
            """.trimIndent()
        )

        val result = client.findFeedUrl("Tech Talks")

        assertEquals("https://feeds.example.com/tech-talks", result)
    }

    @Test
    fun `findFeedUrl matches case-insensitively`() = runTest {
        val client = makeClient(
            """
            {
              "resultCount": 1,
              "results": [
                {
                  "collectionName": "TECH TALKS",
                  "feedUrl": "https://feeds.example.com/tech-talks"
                }
              ]
            }
            """.trimIndent()
        )

        val result = client.findFeedUrl("tech talks")

        assertEquals("https://feeds.example.com/tech-talks", result)
    }

    @Test
    fun `findFeedUrl matches ignoring punctuation`() = runTest {
        val client = makeClient(
            """
            {
              "resultCount": 1,
              "results": [
                {
                  "collectionName": "Tech Talks: The Show",
                  "feedUrl": "https://feeds.example.com/tech-talks"
                }
              ]
            }
            """.trimIndent()
        )

        // "Tech Talks: The Show" normalizes to "tech talks the show"
        // "Tech Talks" normalizes to "tech talks"
        // "tech talks the show".contains("tech talks") → true
        val result = client.findFeedUrl("Tech Talks")

        assertEquals("https://feeds.example.com/tech-talks", result)
    }

    // -----------------------------------------------------------------------
    // No match
    // -----------------------------------------------------------------------

    @Test
    fun `findFeedUrl returns null when results list is empty`() = runTest {
        val client = makeClient("""{"resultCount": 0, "results": []}""")

        val result = client.findFeedUrl("Nonexistent Podcast")

        assertNull(result)
    }

    @Test
    fun `findFeedUrl returns null when no result name matches`() = runTest {
        val client = makeClient(
            """
            {
              "resultCount": 1,
              "results": [
                {
                  "collectionName": "Completely Different Show",
                  "feedUrl": "https://feeds.example.com/different"
                }
              ]
            }
            """.trimIndent()
        )

        val result = client.findFeedUrl("Tech Talks")

        assertNull(result)
    }

    @Test
    fun `findFeedUrl returns null when matching result has null feedUrl`() = runTest {
        val client = makeClient(
            """
            {
              "resultCount": 1,
              "results": [
                {
                  "collectionName": "Tech Talks"
                }
              ]
            }
            """.trimIndent()
        )

        val result = client.findFeedUrl("Tech Talks")

        assertNull(result)
    }

    // -----------------------------------------------------------------------
    // HTTP error
    // -----------------------------------------------------------------------

    @Test
    fun `findFeedUrl returns null on HTTP 500`() = runTest {
        val client = makeClient(
            responseBody = """{"error":"Server Error"}""",
            status = HttpStatusCode.InternalServerError
        )

        val result = client.findFeedUrl("Tech Talks")

        assertNull(result)
    }

    @Test
    fun `findFeedUrl returns null on HTTP 404`() = runTest {
        val client = makeClient(
            responseBody = "Not Found",
            status = HttpStatusCode.NotFound
        )

        val result = client.findFeedUrl("Tech Talks")

        assertNull(result)
    }

    // -----------------------------------------------------------------------
    // Network exception
    // -----------------------------------------------------------------------

    @Test
    fun `findFeedUrl returns null on network exception`() = runTest {
        val engine = MockEngine { _ ->
            throw Exception("Network unavailable")
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = ItunesPodcastSearchClient(httpClient)

        val result = client.findFeedUrl("Tech Talks")

        assertNull(result)
    }

    // -----------------------------------------------------------------------
    // CancellationException re-thrown
    // -----------------------------------------------------------------------

    @Test
    fun `findFeedUrl rethrows CancellationException`() = runTest {
        val engine = MockEngine { _ ->
            throw CancellationException("Cancelled")
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = ItunesPodcastSearchClient(httpClient)

        var threw = false
        try {
            client.findFeedUrl("Tech Talks")
        } catch (e: CancellationException) {
            threw = true
        }

        assertEquals(true, threw, "CancellationException must be re-thrown")
    }
}
