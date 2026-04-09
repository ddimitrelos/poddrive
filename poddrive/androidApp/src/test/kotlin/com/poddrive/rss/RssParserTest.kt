package com.poddrive.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM unit tests for [RssParser] (Android actual implementation).
 *
 * Uses `XmlPullParserFactory.newInstance()` which is backed by kxml2 on the test classpath
 * (declared in androidApp build.gradle.kts as testImplementation("net.sf.kxml:kxml2:2.3.0")).
 * No Robolectric is required.
 */
class RssParserTest {

    private val parser = RssParser()

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    private val validRssFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Tech Talks</title>
            <item>
              <title>Episode 1: Intro to KMP</title>
              <pubDate>Mon, 07 Apr 2026 08:00:00 +0000</pubDate>
              <enclosure url="https://example.com/ep1.mp3" length="45000000" type="audio/mpeg"/>
              <link>https://example.com/ep1</link>
            </item>
            <item>
              <title>Episode 2: Advanced KMP</title>
              <pubDate>Mon, 14 Apr 2026 08:00:00 +0000</pubDate>
              <enclosure url="https://example.com/ep2.mp3" length="52000000" type="audio/mpeg"/>
              <link>https://example.com/ep2</link>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private val feedWithoutEnclosure = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>Episode Without Enclosure</title>
              <pubDate>Mon, 07 Apr 2026 08:00:00 +0000</pubDate>
              <link>https://example.com/ep-no-enclosure</link>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private val feedWithNoLink = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>Bare Episode</title>
              <pubDate>Mon, 07 Apr 2026 08:00:00 +0000</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    fun `parse returns all items from valid RSS feed`() {
        val result = parser.parse(validRssFeed)

        assertEquals(2, result.size, "Should parse 2 items from feed")
    }

    @Test
    fun `parse extracts correct titles`() {
        val result = parser.parse(validRssFeed)

        assertEquals("Episode 1: Intro to KMP", result[0].title)
        assertEquals("Episode 2: Advanced KMP", result[1].title)
    }

    @Test
    fun `parse extracts enclosure URL correctly`() {
        val result = parser.parse(validRssFeed)

        assertEquals("https://example.com/ep1.mp3", result[0].enclosureUrl)
        assertEquals("https://example.com/ep2.mp3", result[1].enclosureUrl)
    }

    @Test
    fun `parse extracts pubDate as positive epoch ms`() {
        val result = parser.parse(validRssFeed)

        assertTrue(result[0].pubDateMs > 0L, "pubDateMs should be positive")
        assertTrue(result[1].pubDateMs > result[0].pubDateMs, "Second episode should be later than first")
    }

    @Test
    fun `parse falls back to link when enclosure is absent`() {
        val result = parser.parse(feedWithoutEnclosure)

        assertEquals(1, result.size)
        assertEquals("https://example.com/ep-no-enclosure", result[0].enclosureUrl)
    }

    @Test
    fun `parse returns null enclosureUrl when neither enclosure nor link is present`() {
        val result = parser.parse(feedWithNoLink)

        assertEquals(1, result.size)
        assertNull(result[0].enclosureUrl)
    }

    @Test
    fun `parse returns empty list on malformed XML`() {
        val result = parser.parse("this is not xml <<<>>>")

        assertTrue(result.isEmpty(), "Malformed XML should return empty list")
    }

    @Test
    fun `parse returns empty list on empty string`() {
        val result = parser.parse("")

        assertTrue(result.isEmpty(), "Empty string should return empty list")
    }

    @Test
    fun `parseRfc822 parses valid RFC 822 date`() {
        val ms = RssParser.parseRfc822("Mon, 07 Apr 2026 08:00:00 +0000")

        assertTrue(ms > 0L)
    }

    @Test
    fun `parseRfc822 returns 0 for invalid date string`() {
        val ms = RssParser.parseRfc822("not a date")

        assertEquals(0L, ms)
    }
}
