# Story 4.9: Episode Audio URL Resolution from RSS

Status: done

## Story

As a PodDrive user,
I want each episode in my queue to have its audio URL resolved from the podcast's RSS feed,
so that the app can play it directly without going through Spotify.

## Background & Motivation

Story 4.8 discovers the RSS `feedUrl` for each podcast and stores it in the `podcasts` table.
This story consumes that URL: it fetches the RSS XML, parses it, and extracts the direct audio
`<enclosure>` URL for each episode. The result is stored in a new `audio_url TEXT` column on the
`episodes` table.

Story 4.10 (ExoPlayer integration) consumes `audio_url` to play episodes in-app.

**Matching strategy:** Episodes are matched between Spotify (id, title, publishedAt) and RSS
(`<item>` title + `<pubDate>`) by normalized title containment + publication date within ±2 days.
An exact title match is preferred; fuzzy containment is the fallback.

**RSS XML parsing** cannot live in `commonMain` (no cross-platform XML parser). It lives in
`androidMain` behind an `expect/actual` interface.

**Failure is safe:** If the RSS fetch fails, the XML is malformed, or no episode match is
found, `audio_url` remains `NULL` and the episode falls back to the existing deep-link path
(Story 4.1). No user-facing error is shown.

---

## Acceptance Criteria

1. **AC1 — Schema migration:** A new `audio_url TEXT` column exists on the `episodes`
   SQLDelight table. Existing rows default to `NULL`.

2. **AC2 — Resolution runs at sync time:** After `SyncPodcastLibraryUseCase` upserts episodes
   from Spotify, it calls `ResolveEpisodeAudioUrlsUseCase` for each podcast whose
   `rss_feed_url` is non-null. Already-resolved episodes (non-null `audio_url`) are skipped.

3. **AC3 — RSS fetch:** The use case performs `GET {rss_feed_url}` and passes the response
   body to the platform RSS parser. HTTP non-200 or network exceptions result in `audio_url`
   remaining `NULL` (no crash, no user error).

4. **AC4 — Episode matching:** For each Spotify episode in the podcast, the RSS parser finds
   the best-matching `<item>` by:
   - Normalized title containment (case-insensitive, punctuation stripped), AND
   - `<pubDate>` within ±2 days of `episode.publishedAt`
   - Exact title match takes priority over partial containment when both criteria are met

5. **AC5 — `audio_url` extracted from `<enclosure>`:** The matched RSS `<item>`'s
   `<enclosure url="…">` attribute value is stored as `audio_url`. If `<enclosure>` is absent,
   the fallback is the `<link>` element value. If neither is present, `audio_url` stays `NULL`.

6. **AC6 — Stored once, reused:** Episodes with a non-null `audio_url` are skipped on
   subsequent syncs. The RSS feed is only re-fetched if the podcast has unresolved episodes.

7. **AC7 — `expect/actual` RSS parser:** XML parsing is done through an `expect class RssParser`
   in `commonMain`, with `actual` implementation in `androidMain` using Android's `XmlPullParser`.
   The iOS `actual` is a stub returning an empty list (iOS playback is post-MVP per architecture).

8. **AC8 — Unit test coverage:**
   - `RssParserTest` (androidTest): parses a multi-episode RSS fixture; returns correct enclosure
     URLs; handles missing `<enclosure>` (falls back to `<link>`); handles malformed XML
     gracefully (returns empty list, no exception)
   - `ResolveEpisodeAudioUrlsUseCaseTest` (commonTest): resolves URL for matched episode;
     skips episode with existing `audio_url`; stores nothing on HTTP 404; stores nothing when
     no RSS match

---

## Tasks / Subtasks

- [x] **Task 1 — SQLDelight schema** (AC1)
  - [x] 1.1 Add `audio_url TEXT` column to `episodes` table in `Episode.sq` (or migration file)
  - [x] 1.2 Add query `updateAudioUrl`:
        ```sql
        updateAudioUrl:
        UPDATE episodes SET audio_url = :audioUrl WHERE id = :id;
        ```
  - [x] 1.3 Add query `selectUnresolvedEpisodesForPodcast`:
        ```sql
        selectUnresolvedEpisodesForPodcast:
        SELECT * FROM episodes WHERE podcast_id = :podcastId AND audio_url IS NULL AND is_played = 0;
        ```
  - [x] 1.4 Update `Episode.kt` domain model: add `val audioUrl: String?`
  - [x] 1.5 Update `SqlDelightEpisodeRepository` row mapping to include `audio_url`
  - [x] 1.6 Add `updateAudioUrl(id: String, audioUrl: String)` and
        `getUnresolvedEpisodesForPodcast(podcastId: String): List<Episode>` to
        `EpisodeRepository` interface and `SqlDelightEpisodeRepository` implementation

- [x] **Task 2 — `expect/actual RssParser`** (AC4, AC5, AC7, AC8)
  - [x] 2.1 Create `shared/src/commonMain/kotlin/com/poddrive/rss/RssParser.kt`:
        ```kotlin
        data class RssEpisode(
            val title: String,
            val pubDateMs: Long,      // Unix ms; 0 if unparseable
            val enclosureUrl: String? // audio URL from <enclosure> or <link>
        )

        expect class RssParser() {
            fun parse(xmlBody: String): List<RssEpisode>
        }
        ```
  - [x] 2.2 Create `shared/src/androidMain/kotlin/com/poddrive/rss/RssParser.kt`:
        - `actual class RssParser` using `android.util.Xml.newPullParser()`
        - Parse `<item>` elements: extract `<title>`, `<pubDate>` (RFC 822 → Unix ms),
          `<enclosure url="…">`, and `<link>`
        - On any `XmlPullParserException` or parse error: return empty list (AC3/AC8)
  - [x] 2.3 Create `shared/src/iosMain/kotlin/com/poddrive/rss/RssParser.kt`:
        - Stub `actual class RssParser` returning empty list (post-MVP)

- [x] **Task 3 — `ResolveEpisodeAudioUrlsUseCase`** (AC2–AC6, AC8)
  - [x] 3.1 Create `shared/src/commonMain/kotlin/com/poddrive/usecase/ResolveEpisodeAudioUrlsUseCase.kt`:
        ```kotlin
        class ResolveEpisodeAudioUrlsUseCase(
            private val episodeRepository: EpisodeRepository,
            private val httpClient: HttpClient,    // plain itunesHttpClient — no Spotify auth
            private val rssParser: RssParser
        ) {
            suspend fun execute(podcast: Podcast)  // errors swallowed; Unit return
        }
        ```
        - If `podcast.rssFeedUrl == null` → return immediately
        - Fetch `GET podcast.rssFeedUrl` → on non-200 or exception → return
        - Parse response body via `rssParser.parse(body)`
        - For each episode in `episodeRepository.getUnresolvedEpisodesForPodcast(podcast.id)`:
          - Find best matching `RssEpisode` (AC4 matching algorithm)
          - If match found and `enclosureUrl` non-null → `episodeRepository.updateAudioUrl(episode.id, url)`

  - [x] 3.2 Matching algorithm helper (private):
        ```kotlin
        private fun findMatch(episode: Episode, rssItems: List<RssEpisode>): RssEpisode? {
            val normalized = episode.title.normalize()
            val dateTolerance = 2 * 24 * 60 * 60 * 1000L  // ±2 days in ms
            val candidates = rssItems.filter { rss ->
                abs(rss.pubDateMs - episode.publishedAt) <= dateTolerance
            }
            return candidates.firstOrNull { it.title.normalize() == normalized }
                ?: candidates.firstOrNull { it.title.normalize().contains(normalized)
                                         || normalized.contains(it.title.normalize()) }
        }
        private fun String.normalize() =
            lowercase().replace(Regex("[^\\w\\s]"), "").trim()
        ```

- [x] **Task 4 — Wire into `SyncPodcastLibraryUseCase`** (AC2)
  - [x] 4.1 Inject `ResolveEpisodeAudioUrlsUseCase` into `SyncPodcastLibraryUseCase`
  - [x] 4.2 After episode upsert loop, call `resolveEpisodeAudioUrlsUseCase.execute(podcast)`
        for each podcast with non-null `rssFeedUrl`
  - [x] 4.3 Register in `PodDriveApplication` — reuse `itunesHttpClient` from Story 4.8
        (same plain HttpClient, same engine, no auth)

- [x] **Task 5 — Unit tests** (AC8)
  - [x] 5.1 Create `androidApp/src/test/kotlin/com/poddrive/rss/RssParserTest.kt`
        (Android unit test — uses `android.util.Xml`):
        - `parse returns episodes from valid RSS fixture`
        - `parse extracts enclosure URL correctly`
        - `parse falls back to link when enclosure absent`
        - `parse returns empty list on malformed XML`
        - Use a raw XML string fixture — no file I/O needed
  - [x] 5.2 Create `shared/src/commonTest/kotlin/com/poddrive/usecase/ResolveEpisodeAudioUrlsUseCaseTest.kt`
        using `MockEngine` for HTTP + fake `RssParser`:
        - `execute resolves and stores audio_url for matched episode`
        - `execute skips episode with existing audio_url`
        - `execute stores nothing when RSS fetch returns 404`
        - `execute stores nothing when no title+date match found`
        - `execute does nothing when podcast rssFeedUrl is null`

---

## Dev Notes

### Architecture Rules (MUST follow)

1. `RssParser` is an `expect/actual` class — `commonMain` declares the interface, `androidMain`
   has the `XmlPullParser` implementation. iOS `actual` is a stub returning empty list.
2. `ResolveEpisodeAudioUrlsUseCase` reuses `itunesHttpClient` from Story 4.8 (plain client,
   no Spotify auth headers). Never pass `apiHttpClient` here.
3. All errors are swallowed in `ResolveEpisodeAudioUrlsUseCase.execute()` — never propagate
   to the user.
4. `audio_url` column is write-once per episode in normal operation. Once non-null, it is not
   overwritten on subsequent syncs (AC6). If a CDN URL expires, a separate "refresh stale
   audio URLs" task can be added post-MVP.
5. `CancellationException` must be re-thrown in the HTTP fetch call.

### RSS `<item>` structure reference

```xml
<item>
  <title>Episode 42 — Deep Dive</title>
  <pubDate>Mon, 07 Apr 2026 08:00:00 +0000</pubDate>
  <enclosure url="https://anchor.fm/s/abc123/podcast/play/123/episode.mp3"
             length="45231234" type="audio/mpeg"/>
  <link>https://anchor.fm/mypodcast/episodes/...</link>
</item>
```

RFC 822 date parsing: use `java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)`
in the Android `actual` implementation.

### Key Files to Touch

| File | Change |
|---|---|
| `shared/.../sqldelight/com/poddrive/Episode.sq` | Add column + 2 queries |
| `shared/.../domain/model/Episode.kt` | Add `audioUrl: String?` |
| `shared/.../repository/EpisodeRepository.kt` | Add `updateAudioUrl()`, `getUnresolvedEpisodesForPodcast()` |
| `shared/.../storage/SqlDelightEpisodeRepository.kt` | Implement new methods; update mapping |
| `shared/.../rss/RssParser.kt` (commonMain) | **NEW** — expect class + RssEpisode model |
| `shared/.../rss/RssParser.kt` (androidMain) | **NEW** — XmlPullParser actual |
| `shared/.../rss/RssParser.kt` (iosMain) | **NEW** — stub actual |
| `shared/.../usecase/ResolveEpisodeAudioUrlsUseCase.kt` | **NEW** |
| `shared/.../usecase/SyncPodcastLibraryUseCase.kt` | Inject + call |
| `androidApp/.../PodDriveApplication.kt` | Register use case |
| `androidApp/.../rss/RssParserTest.kt` | **NEW** |
| `shared/.../usecase/ResolveEpisodeAudioUrlsUseCaseTest.kt` | **NEW** |

### References

- [ADR-002] `_bmad-output/planning-artifacts/architecture.md`
- [Story 4.8] `4-8-rss-feed-discovery.md` — produces `rss_feed_url` consumed here
- [Story 4.10] `4-10-exoplayer-integration.md` — consumes `audio_url` produced here

---

## Dev Agent Record

### Implementation Plan

Implemented episode audio URL resolution from RSS feeds as a best-effort enrichment after episode upsert in the sync pipeline.

**Key decisions:**
- `audio_url TEXT` added directly to `Episode.sq` schema (no `.sqm` migration files in project).
- `selectUnplayedOrderedByTier` updated to explicitly select `e.audio_url` so the domain `Episode` model carries the value.
- `expect open class RssParser` declared in `commonMain` with `open fun parse()`. Both `androidMain` and `iosMain` actuals are `open` to allow test-only subclassing in `commonTest` via `FakeRssParser`.
- `actual class RssParser` (androidMain) uses `XmlPullParserFactory.newInstance()` (NOT `android.util.Xml.newPullParser()`) so the implementation is testable in JVM unit tests without Robolectric.
- `kxml2:2.3.0` added to `androidApp` `testImplementation` to provide a real `XmlPullParserFactory` on the JVM test classpath.
- `createItunesHttpClient()` factory extracted to `SpotifyHttpClient.kt` (shared module) — removes the need to declare `ktor-client-content-negotiation` and `kotlinx-serialization-json` as explicit `androidApp` dependencies.
- `ResolveEpisodeAudioUrlsUseCase` is `open class` with `open execute()` for consistent test-subclassing pattern established in Story 4.8.
- Date tolerance filter uses `pubDateMs == 0L` as a pass-through condition for unparseable dates (they participate in title matching rather than being silently excluded).

### Completion Notes

- All 8 ACs satisfied.
- `:shared:testDebugUnitTest`: 159 tests, 2 pre-existing failures (SpotifyAutoPlayCoordinatorTest), 6 new tests all pass.
- `:androidApp:testDebugUnitTest`: BUILD SUCCESSFUL — `RssParserTest` (10 tests) all pass.

---

## File List

- `poddrive/shared/src/commonMain/sqldelight/com/poddrive/Episode.sq` — added `audio_url TEXT` column; updated `selectUnplayedOrderedByTier` to include `e.audio_url`; added `updateAudioUrl` and `selectUnresolvedEpisodesForPodcast` queries
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/domain/model/Episode.kt` — added `audioUrl: String? = null`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/repository/EpisodeRepository.kt` — added `updateAudioUrl()` and `getUnresolvedEpisodesForPodcast()`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightEpisodeRepository.kt` — added `audioUrl` to `getUnplayedOrderedByTier()` mapping; implemented `updateAudioUrl()` and `getUnresolvedEpisodesForPodcast()`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/rss/RssParser.kt` — **NEW** expect class + `RssEpisode` data class
- `poddrive/shared/src/androidMain/kotlin/com/poddrive/rss/RssParser.kt` — **NEW** actual implementation using `XmlPullParserFactory`
- `poddrive/shared/src/iosMain/kotlin/com/poddrive/rss/RssParser.kt` — **NEW** stub actual returning empty list
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/ResolveEpisodeAudioUrlsUseCase.kt` — **NEW** use case with matching algorithm
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCase.kt` — added `resolveEpisodeAudioUrlsUseCase` parameter; calls resolution after episode upsert
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyHttpClient.kt` — added `createItunesHttpClient()` factory function
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` — registered `resolveEpisodeAudioUrlsUseCase`; refactored `itunesHttpClient` to use `createItunesHttpClient()`
- `poddrive/androidApp/build.gradle.kts` — added `kxml2:2.3.0` testImplementation
- `poddrive/androidApp/src/test/kotlin/com/poddrive/rss/RssParserTest.kt` — **NEW** 10 JVM unit tests
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/ResolveEpisodeAudioUrlsUseCaseTest.kt` — **NEW** 6 unit tests
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/BuildEpisodeQueueUseCaseTest.kt` — added `updateAudioUrl`/`getUnresolvedEpisodesForPodcast` stubs to `FakeEpisodeRepository`
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/MarkEpisodePlayedUseCaseTest.kt` — same stubs added
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCaseTest.kt` — same stubs added

---

## Change Log

- 2026-04-09: Story 4.9 implemented — episode audio URL resolution from RSS XML. Added `audio_url` column to `episodes` table, `expect/actual RssParser`, `ResolveEpisodeAudioUrlsUseCase`, wired into sync pipeline. 16 new/modified tests pass across `:shared` and `:androidApp`.
