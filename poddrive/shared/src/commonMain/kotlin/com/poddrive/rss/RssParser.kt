package com.poddrive.rss

/**
 * A single episode entry parsed from an RSS `<item>` element.
 *
 * @property title Raw title text from `<title>`.
 * @property pubDateMs Publication date as Unix milliseconds; 0 if the date could not be parsed.
 * @property enclosureUrl Direct audio URL from `<enclosure url="…">`, or `<link>` as fallback;
 *   null if neither element is present.
 */
data class RssEpisode(
    val title: String,
    val pubDateMs: Long,
    val enclosureUrl: String?
)

/**
 * Platform-specific RSS XML parser.
 *
 * - Android `actual`: uses `XmlPullParserFactory` to parse `<item>` elements.
 * - iOS `actual`: stub returning empty list (in-app playback is post-MVP per ADR-002).
 *
 * [Source: Story 4.9, ADR-002]
 */
expect open class RssParser() {
    /** Parses [xmlBody] and returns a list of [RssEpisode] items. Returns empty list on any error. */
    open fun parse(xmlBody: String): List<RssEpisode>
}
