package com.poddrive.rss

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Android `actual` implementation of [RssParser] using `XmlPullParserFactory`.
 *
 * Uses `XmlPullParserFactory.newInstance()` rather than `android.util.Xml.newPullParser()`
 * so that the implementation is testable in JVM unit tests (kxml2 provides the factory
 * on the test classpath without requiring Robolectric).
 *
 * Parses `<item>` elements and extracts:
 * - `<title>` — episode title
 * - `<pubDate>` — RFC 822 publication date → Unix ms
 * - `<enclosure url="…">` — direct audio URL (preferred)
 * - `<link>` — fallback URL when `<enclosure>` is absent
 *
 * Returns an empty list on any [XmlPullParserException] or unexpected parse error.
 */
actual open class RssParser actual constructor() {

    actual open fun parse(xmlBody: String): List<RssEpisode> {
        return try {
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val parser = factory.newPullParser().apply {
                setInput(StringReader(xmlBody))
            }
            parseItems(parser)
        } catch (e: XmlPullParserException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseItems(parser: XmlPullParser): List<RssEpisode> {
        val episodes = mutableListOf<RssEpisode>()
        var eventType = parser.eventType

        var inItem = false
        var title = ""
        var pubDateMs = 0L
        var enclosureUrl: String? = null
        var link: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item" -> {
                            inItem = true
                            title = ""
                            pubDateMs = 0L
                            enclosureUrl = null
                            link = null
                        }
                        "title" -> if (inItem) title = parser.nextText()
                        "pubDate" -> if (inItem) pubDateMs = parseRfc822(parser.nextText())
                        "enclosure" -> if (inItem) {
                            enclosureUrl = parser.getAttributeValue(null, "url")
                                ?.takeIf { it.isNotBlank() }
                        }
                        "link" -> if (inItem) {
                            link = parser.nextText().takeIf { it.isNotBlank() }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        episodes.add(
                            RssEpisode(
                                title = title,
                                pubDateMs = pubDateMs,
                                enclosureUrl = enclosureUrl ?: link
                            )
                        )
                        inItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return episodes
    }

    companion object {
        private val RFC_822_FORMAT = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

        fun parseRfc822(dateStr: String): Long {
            return try {
                RFC_822_FORMAT.parse(dateStr.trim())?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }
}
