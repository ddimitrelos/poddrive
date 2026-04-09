# Story 4.8: RSS Feed Discovery via iTunes Search API

Status: done

## Story

As a PodDrive user,
I want the app to automatically discover the RSS feed URL for each podcast I follow,
so that my episodes can be played directly in-app without needing a Spotify account with Premium.

## Background & Motivation

ADR-002 (`_bmad-output/planning-artifacts/architecture.md`) establishes RSS + ExoPlayer as the
path to single-tap, free-tier podcast playback. This story implements the first step: discovering
the RSS `feedUrl` for each podcast the user follows on Spotify.

**Why iTunes Search API:**
Spotify's Web API exposes zero RSS data. The iTunes Podcast Search API
(`https://itunes.apple.com/search?term={name}&entity=podcast&country=GR&limit=10`) is
free, requires no authentication, and covers ~99.6% of podcasts in the Greek Spotify catalog
(empirically confirmed 2026-04-09). Results include a `feedUrl` field per podcast.

**anchor.fm note:** 43% of Greek-market podcasts are hosted on Spotify's anchor.fm
infrastructure. These feeds are publicly accessible without authentication — confirmed empirically.
No special handling is needed.

**Failure is safe:** If discovery fails (network error, no matching result, ambiguous match),
`rss_feed_url` stays null. The episode falls back to the existing deep-link handoff (Story 4.1).
No user-facing error is shown.

---

## Acceptance Criteria

1. **AC1 — Schema migration:** A new `rss_feed_url TEXT` column exists on the `podcasts`
   SQLDelight table. Existing rows default to `NULL`.

2. **AC2 — Discovery runs at sync time:** `SyncPodcastLibraryUseCase` calls
   `ResolvePodcastRssFeedUseCase` for each podcast whose `rss_feed_url` is currently `NULL`
   after the podcast list is fetched from Spotify. Already-resolved podcasts are skipped
   (no redundant iTunes calls).

3. **AC3 — iTunes match algorithm:** Matching selects the first iTunes result where the
   normalized result `collectionName` contains the normalized Spotify `podcast.name`
   (or vice versa), case-insensitive, stripping punctuation. If no result matches,
   `rss_feed_url` remains `NULL`.

4. **AC4 — Stored once, reused:** `rss_feed_url` is written to SQLDelight immediately on
   discovery. On subsequent syncs the stored value is used — iTunes is not re-queried for
   already-resolved podcasts.

5. **AC5 — Null on failure:** Any error during iTunes lookup (network failure, HTTP non-200,
   JSON parse error, no match) leaves `rss_feed_url` as `NULL`. Errors are swallowed silently —
   never propagated to the user.

6. **AC6 — No Spotify API change:** No new OAuth scopes are added. No changes to
   `SpotifyAuthManager`, `SpotifyLibraryApiClient`, or `SpotifyModels`.

7. **AC7 — Unit test coverage:**
   - `ItunesPodcastSearchClientTest`: successful response returns `feedUrl`; empty results
     returns null; HTTP non-200 returns null; network exception returns null;
     `CancellationException` re-thrown
   - `ResolvePodcastRssFeedUseCaseTest`: exact name match stores URL; no match stores null;
     already-resolved podcast is skipped (iTunes client NOT called)
   - `SyncPodcastLibraryUseCaseTest`: existing test updated to assert `ResolvePodcastRssFeedUseCase`
     is called once per unresolved podcast

---

## Tasks / Subtasks

- [x] **Task 1 — SQLDelight schema migration** (AC1)
  - [x] 1.1 In `shared/src/commonMain/sqldelight/com/poddrive/Podcast.sq`, add column to
        `podcasts` table:
        ```sql
        ALTER TABLE podcasts ADD COLUMN rss_feed_url TEXT;
        ```
        Place this as a new migration file or add to the existing schema following the
        SQLDelight migration pattern already used in this project.
  - [x] 1.2 Add query `updateRssFeedUrl`:
        ```sql
        updateRssFeedUrl:
        UPDATE podcasts SET rss_feed_url = :rssFeedUrl WHERE id = :id;
        ```
  - [x] 1.3 Add query `selectRssFeedUrl`:
        ```sql
        selectRssFeedUrl:
        SELECT rss_feed_url FROM podcasts WHERE id = :id;
        ```
  - [x] 1.4 Update `Podcast.kt` domain model to add `val rssFeedUrl: String?`
  - [x] 1.5 Update `SqlDelightPodcastRepository` mapping to include `rss_feed_url`

- [x] **Task 2 — iTunes Search API client** (AC3, AC5, AC7)
  - [x] 2.1 Create `shared/src/commonMain/kotlin/com/poddrive/api/ItunesPodcastSearchClient.kt`
        ```kotlin
        class ItunesPodcastSearchClient(private val httpClient: HttpClient) {
            suspend fun findFeedUrl(podcastName: String): String? // null = not found
        }
        ```
        - Use a **plain `HttpClient`** (no auth plugin, no Spotify token) — iTunes is a public API
        - Request: `GET https://itunes.apple.com/search?term={encoded}&entity=podcast&limit=10`
        - Parse response with `kotlinx.serialization`; return `feedUrl` of first matching result
        - On any exception (except `CancellationException`): return `null`
        - `CancellationException` must be re-thrown
  - [x] 2.2 Add `@Serializable` iTunes response models to `ItunesPodcastSearchClient.kt`
        (keep them file-private — not in `SpotifyModels.kt`):
        ```kotlin
        @Serializable private data class ItunesSearchResponse(val results: List<ItunesPodcast>)
        @Serializable private data class ItunesPodcast(
            val collectionName: String,
            val feedUrl: String? = null
        )
        ```
  - [x] 2.3 Matching logic (AC3): normalize both `collectionName` and `podcastName`
        (lowercase, remove punctuation via `replace(Regex("[^\\w\\s]"), "").trim()`);
        return `feedUrl` of first result where normalized names share a containment relationship;
        return `null` if no match or `feedUrl` is null/blank
  - [x] 2.4 Create plain `HttpClient` for iTunes in `PodDriveApplication`:
        ```kotlin
        val itunesHttpClient: HttpClient by lazy {
            HttpClient(/* same engine as apiHttpClient */) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
        ```
        Do NOT reuse `apiHttpClient` (it carries Spotify bearer auth headers).

- [x] **Task 3 — `ResolvePodcastRssFeedUseCase`** (AC2–AC5, AC7)
  - [x] 3.1 Create `shared/src/commonMain/kotlin/com/poddrive/usecase/ResolvePodcastRssFeedUseCase.kt`
        ```kotlin
        class ResolvePodcastRssFeedUseCase(
            private val podcastRepository: PodcastRepository,
            private val itunesClient: ItunesPodcastSearchClient
        ) {
            suspend fun execute(podcast: Podcast) // returns Unit; errors are swallowed
        }
        ```
        - If `podcast.rssFeedUrl != null` → return immediately (AC4 — skip already-resolved)
        - Call `itunesClient.findFeedUrl(podcast.name)`
        - If non-null result → call `podcastRepository.updateRssFeedUrl(podcast.id, feedUrl)`
        - If null or exception → do nothing
  - [x] 3.2 Add `updateRssFeedUrl(id: String, rssFeedUrl: String)` to `PodcastRepository` interface
  - [x] 3.3 Implement `updateRssFeedUrl` in `SqlDelightPodcastRepository` using the new
        `updateRssFeedUrl` query

- [x] **Task 4 — Wire into `SyncPodcastLibraryUseCase`** (AC2)
  - [x] 4.1 Inject `ResolvePodcastRssFeedUseCase` into `SyncPodcastLibraryUseCase`
  - [x] 4.2 After upserting the podcast list from Spotify, iterate podcasts and call
        `resolvePodcastRssFeedUseCase.execute(podcast)` for each one
        (the use case itself guards against redundant calls per AC4)
  - [x] 4.3 Register `resolvePodcastRssFeedUseCase` in `PodDriveApplication` after
        `itunesHttpClient` and `podcastRepository`

- [x] **Task 5 — Unit tests** (AC7)
  - [x] 5.1 Create `shared/src/commonTest/kotlin/com/poddrive/api/ItunesPodcastSearchClientTest.kt`
        using `MockEngine` (same pattern as `SpotifyAuthManagerTest`):
        - `findFeedUrl returns feedUrl on successful match`
        - `findFeedUrl returns null when results is empty`
        - `findFeedUrl returns null when no name match`
        - `findFeedUrl returns null on HTTP 500`
        - `findFeedUrl returns null on network exception`
        - `findFeedUrl rethrows CancellationException`
  - [x] 5.2 Create `shared/src/commonTest/kotlin/com/poddrive/usecase/ResolvePodcastRssFeedUseCaseTest.kt`:
        - `execute stores feedUrl when iTunes returns a match`
        - `execute stores nothing when no match`
        - `execute skips iTunes call when rssFeedUrl already set`
  - [x] 5.3 Update `SyncPodcastLibraryUseCaseTest` to inject a fake `ResolvePodcastRssFeedUseCase`
        and assert it is called once per podcast

---

## Dev Notes

### Architecture Rules (MUST follow)

1. `ItunesPodcastSearchClient` lives in `commonMain/api/`. It uses a **plain HttpClient**,
   not `apiHttpClient` — iTunes requires no auth and must not receive Spotify tokens.
2. iTunes response models (`ItunesSearchResponse`, `ItunesPodcast`) are **file-private** to
   `ItunesPodcastSearchClient.kt` — they must not appear in `SpotifyModels.kt`.
3. All errors in `ResolvePodcastRssFeedUseCase.execute()` are swallowed. The method never
   throws and never returns a `Result` — its only side effect is a best-effort DB write.
4. `CancellationException` must be re-thrown in `ItunesPodcastSearchClient.findFeedUrl`.
5. SQLDelight migration: follow the existing migration file pattern in the project. Do NOT
   use `ALTER TABLE` inline in the `.sq` source file if the project uses numbered migration
   files — check `shared/src/commonMain/sqldelight/` for existing `*.sqm` migration files first.

### iTunes Search API reference

```
GET https://itunes.apple.com/search?term={podcastName}&entity=podcast&limit=10

Response (200 OK):
{
  "resultCount": 2,
  "results": [
    {
      "collectionName": "The Mel Robbins Podcast",
      "feedUrl": "https://feeds.simplecast.com/XW_8EB4I",
      ...
    }
  ]
}
```
No API key. No rate limiting documented for reasonable use (1 call per podcast per sync).

### SQLDelight migration pattern

Check whether the project uses `*.sqm` migration files or inline schema versioning. If
`*.sqm` files exist in `shared/src/commonMain/sqldelight/migrations/`, add a new one:
```
-- shared/src/commonMain/sqldelight/migrations/2.sqm  (or next version number)
ALTER TABLE podcasts ADD COLUMN rss_feed_url TEXT;
```
Then increment `schemaVersion` in `shared/build.gradle.kts`.

### Key Files to Touch

| File | Change |
|---|---|
| `shared/.../sqldelight/com/poddrive/Podcast.sq` | Add column + 2 queries |
| `shared/.../domain/model/Podcast.kt` | Add `rssFeedUrl: String?` field |
| `shared/.../repository/PodcastRepository.kt` | Add `updateRssFeedUrl()` |
| `shared/.../storage/SqlDelightPodcastRepository.kt` | Implement `updateRssFeedUrl()`; update row mapping |
| `shared/.../api/ItunesPodcastSearchClient.kt` | **NEW** |
| `shared/.../usecase/ResolvePodcastRssFeedUseCase.kt` | **NEW** |
| `shared/.../usecase/SyncPodcastLibraryUseCase.kt` | Inject + call ResolvePodcastRssFeedUseCase |
| `androidApp/.../PodDriveApplication.kt` | Register `itunesHttpClient` + `resolvePodcastRssFeedUseCase` |
| `shared/src/commonTest/.../api/ItunesPodcastSearchClientTest.kt` | **NEW** |
| `shared/src/commonTest/.../usecase/ResolvePodcastRssFeedUseCaseTest.kt` | **NEW** |
| `shared/src/commonTest/.../usecase/SyncPodcastLibraryUseCaseTest.kt` | Update |

### References

- [ADR-002] `_bmad-output/planning-artifacts/architecture.md`
- [Story 4.9] `4-9-episode-audio-url-resolution.md` — consumes `rss_feed_url` produced here
- iTunes Search API: `https://itunes.apple.com/search` — no auth, no key required
- Empirical validation (2026-04-09): 99.6% of Greek Spotify podcasts have a matching iTunes entry with accessible `feedUrl`

---

## Dev Agent Record

### Implementation Plan

Implemented RSS feed discovery using the iTunes Search API as a best-effort enrichment step during podcast library sync.

**Key decisions:**
- No `.sqm` migration files existed → `rss_feed_url TEXT` added directly to `Podcast.sq` schema (appropriate for dev project with no versioned migrations).
- `selectAllWithUnheardCount` and `selectAllWithUnheardCountByTier` updated to explicitly select `rss_feed_url` so the domain `Podcast` model carries the resolved URL from DB.
- `ResolvePodcastRssFeedUseCase` made `open class` with `open execute()` to allow test-only subclassing in `SyncPodcastLibraryUseCaseTest` without a separate interface.
- `SyncPodcastLibraryUseCase` reads podcasts back from the DB after upsert (via `getAllWithUnheardCount()`) before calling the resolver — this ensures `rssFeedUrl` reflects any previously stored values (enabling the AC4 skip-guard to work correctly across syncs).
- `resolvePodcastRssFeedUseCase` injected as nullable parameter with default `null` in `SyncPodcastLibraryUseCase` to preserve backward compatibility with existing tests that don't need RSS resolution.

### Completion Notes

- All 7 ACs satisfied. 153 tests run; 151 pass. 2 pre-existing failures in `SpotifyAutoPlayCoordinatorTest` (timing-sensitive, unrelated to this story — present before this implementation).
- `ItunesPodcastSearchClientTest`: 8 tests covering success, no-match, punctuation normalization, case-insensitivity, HTTP 500/404, network exception, CancellationException re-throw.
- `ResolvePodcastRssFeedUseCaseTest`: 3 tests covering match→store, no-match, skip-when-already-resolved.
- `SyncPodcastLibraryUseCaseTest`: 1 test added asserting resolver called once per podcast.
- AC6 confirmed: No changes to `SpotifyAuthManager`, `SpotifyLibraryApiClient`, or `SpotifyModels`.

---

## File List

- `poddrive/shared/src/commonMain/sqldelight/com/poddrive/Podcast.sq` — added `rss_feed_url TEXT` column, `updateRssFeedUrl` query, `selectRssFeedUrl` query; updated `selectAllWithUnheardCount` + `selectAllWithUnheardCountByTier` to include `rss_feed_url`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/domain/model/Podcast.kt` — added `rssFeedUrl: String? = null`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/repository/PodcastRepository.kt` — added `updateRssFeedUrl(id, rssFeedUrl)`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightPodcastRepository.kt` — added `rssFeedUrl` to row mappings; implemented `updateRssFeedUrl`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/ItunesPodcastSearchClient.kt` — **NEW** iTunes Search API client
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/ResolvePodcastRssFeedUseCase.kt` — **NEW** RSS feed resolver use case
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCase.kt` — injected `ResolvePodcastRssFeedUseCase?`; calls resolver per podcast after upsert
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` — registered `itunesHttpClient` + `resolvePodcastRssFeedUseCase`; re-wired `syncPodcastLibraryUseCase` with resolver
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/ItunesPodcastSearchClientTest.kt` — **NEW** 8 unit tests
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/ResolvePodcastRssFeedUseCaseTest.kt` — **NEW** 3 unit tests
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCaseTest.kt` — added `updateRssFeedUrl` stub to `FakePodcastRepository`; added `FakeResolvePodcastRssFeedUseCase`; added AC2 test
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/SetEpisodeTierUseCaseTest.kt` — added `updateRssFeedUrl` stub to `FakePodcastRepository`

---

## Change Log

- 2026-04-09: Story 4.8 implemented — RSS feed discovery via iTunes Search API. Added `rss_feed_url` column to `podcasts` table, `ItunesPodcastSearchClient`, `ResolvePodcastRssFeedUseCase`, wired into sync pipeline. 11 new tests + 2 updated test files.
