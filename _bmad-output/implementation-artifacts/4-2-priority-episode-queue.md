---
status: review
story_id: "4.2"
story_key: 4-2-priority-episode-queue
epic: 4
created: 2026-03-25
---

# Story 4.2 — Priority Episode Queue

## User Story

As a **user**,
I want my episodes queued automatically with LOVED shows first, then LIKED,
so that my most important content always plays first without manual sorting.

---

## Acceptance Criteria

**AC1 — LOVED before LIKED, within tier by most recent publication date**
**Given** a drive session is active with LOVED and LIKED episodes available
**When** the episode queue is built by `BuildEpisodeQueueUseCase`
**Then** all unheard LOVED episodes appear before all unheard LIKED episodes (FR22)
**And** within each tier, episodes are ordered by most recent `publishedAt` descending

**AC2 — Queue UI (deferred to Story 3.6)**
**Given** a drive session is active
**When** the drive dashboard is displayed
**Then** the queue renders as scrollable `EpisodeTile` rows (UX-DR4) [deferred]
**And** 3 skeleton rows display during loading (UX-DR10) [deferred]

**AC3 — Queue persisted to SQLDelight**
**Given** episodes with tier assignments are stored in `episodes` + `episode_tiers` tables
**When** `BuildEpisodeQueueUseCase.execute()` is called
**Then** the ordered queue is derived from the existing `selectUnplayedOrderedByTier` SQLDelight query (AR3, AR6)
**And** the queue ordering survives app backgrounding because the underlying tables are already persisted

**AC4 — `BuildEpisodeQueueUseCase` is wired in `PodDriveApplication`**
**Given** the app is initialised
**When** a drive session needs the episode queue
**Then** `PodDriveApplication.buildEpisodeQueueUseCase` is available

---

## Scope

**New files:**
| File | Purpose |
|------|---------|
| `shared/src/commonMain/kotlin/com/poddrive/domain/model/EpisodeQueue.kt` | Domain model wrapping ordered episode list |
| `shared/src/commonMain/kotlin/com/poddrive/usecase/BuildEpisodeQueueUseCase.kt` | Use case: returns `Result<EpisodeQueue, AppError>` |
| `shared/src/commonTest/kotlin/com/poddrive/usecase/BuildEpisodeQueueUseCaseTest.kt` | Unit tests |

**Modified files:**
| File | Change |
|------|--------|
| `shared/src/commonMain/kotlin/com/poddrive/repository/EpisodeRepository.kt` | Add `getUnplayedOrderedByTier(): List<Episode>` |
| `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightEpisodeRepository.kt` | Implement `getUnplayedOrderedByTier()` using existing `selectUnplayedOrderedByTier` query |
| `androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` | Add `buildEpisodeQueueUseCase` lateinit var and wire it in `onCreate()` |

**No new Gradle dependencies.** The `selectUnplayedOrderedByTier` SQL query already exists in `Episode.sq`.

**AC2 (drive dashboard UI) is deferred to Story 3.6** — this story covers the domain layer only.

---

## Tasks / Subtasks

- [x] T1 — Add `getUnplayedOrderedByTier()` to `EpisodeRepository` interface (AC1, AC3)
  - [x] Declare `fun getUnplayedOrderedByTier(): List<Episode>` in `EpisodeRepository.kt`
  - [x] Add KDoc: "Returns all unplayed episodes ordered LOVED first, LIKED second, NONE last; within each tier by most recent `publishedAt` DESC (FR22, Story 4.2)"

- [x] T2 — Implement `getUnplayedOrderedByTier()` in `SqlDelightEpisodeRepository` (AC1, AC3)
  - [x] Call `db.episodeQueries.selectUnplayedOrderedByTier().executeAsList()`
  - [x] Map each row to `Episode(id, podcast_id, title, duration_ms, published_at, is_played != 0L, EpisodeTier.valueOf(tier))`
  - [x] Note: the query already joins `episode_tiers` — `tier` column is `COALESCE(et.tier, 'NONE')`

- [x] T3 — Create `EpisodeQueue` domain model (AC1)
  - [x] `data class EpisodeQueue(val episodes: List<Episode>)` in `domain/model/`
  - [x] Add KDoc explaining LOVED-first ordering and derivation from `selectUnplayedOrderedByTier`

- [x] T4 — Create `BuildEpisodeQueueUseCase` (AC1, AC3)
  - [x] `class BuildEpisodeQueueUseCase(private val episodeRepository: EpisodeRepository)`
  - [x] `suspend fun execute(): Result<EpisodeQueue, AppError>`
  - [x] Wrap in try/catch; rethrow `CancellationException`; wrap all other exceptions as `AppError.Unknown`
  - [x] Never throws — follows AR3 use case rule

- [x] T5 — Wire `BuildEpisodeQueueUseCase` into `PodDriveApplication` (AC4)
  - [x] Add `lateinit var buildEpisodeQueueUseCase: BuildEpisodeQueueUseCase`
  - [x] In `onCreate()`: `buildEpisodeQueueUseCase = BuildEpisodeQueueUseCase(episodeRepository)`

- [x] T6 — Unit tests for `BuildEpisodeQueueUseCase` (AC1, AC3)
  - [x] Test: empty queue when `getUnplayedOrderedByTier()` returns empty list
  - [x] Test: episodes returned in LOVED-first, LIKED-second order (ordering is repository's responsibility; use case preserves it)
  - [x] Test: repository exception wraps to `AppError.Unknown`
  - [x] Use `FakeEpisodeRepository` implementing `EpisodeRepository`

---

## Dev Notes

### Key Insight: Queue Persistence Is "Free"

The `selectUnplayedOrderedByTier` SQL query already exists in `Episode.sq` and does exactly
what story 4.2 requires:
- LOVED episodes first (`CASE tier WHEN 'LOVED' THEN 0 WHEN 'LIKED' THEN 1 ELSE 2 END`)
- Within tier: sorted by `published_at DESC`

Because the `episodes` and `episode_tiers` tables are already persisted in SQLDelight (written by
`SyncPodcastLibraryUseCase` and `SetEpisodeTierUseCase`), the queue ordering **survives app
backgrounding without a separate queue table** (AR3, AR6 satisfied). The queue is always
re-derivable from the same query.

### T1/T2 — EpisodeRepository and SqlDelightEpisodeRepository

Add to `EpisodeRepository.kt`:
```kotlin
/**
 * Returns all unplayed episodes ordered: LOVED first, LIKED second, NONE last.
 * Within each tier, ordered by most recent [Episode.publishedAt] descending (FR22, Story 4.2).
 * Uses the pre-existing [selectUnplayedOrderedByTier] SQLDelight query.
 */
fun getUnplayedOrderedByTier(): List<Episode>
```

Add to `SqlDelightEpisodeRepository.kt`:
```kotlin
override fun getUnplayedOrderedByTier(): List<Episode> =
    db.episodeQueries.selectUnplayedOrderedByTier().executeAsList().map { row ->
        Episode(
            id = row.id,
            podcastId = row.podcast_id,
            title = row.title,
            durationMs = row.duration_ms,
            publishedAt = row.published_at,
            isPlayed = row.is_played != 0L,
            tier = EpisodeTier.valueOf(row.tier)
        )
    }
```

The `selectUnplayedOrderedByTier` query returns a custom row type (not `Episodes`) because it
includes the joined `tier` column. Access via `db.episodeQueries.selectUnplayedOrderedByTier()`.

### T3 — EpisodeQueue.kt
```kotlin
package com.poddrive.domain.model

/**
 * Ordered list of unheard episodes for a drive session.
 * Episodes are sorted: LOVED first, then LIKED, then NONE.
 * Within each tier, sorted by most recent [Episode.publishedAt] descending (FR22).
 * Built by [com.poddrive.usecase.BuildEpisodeQueueUseCase].
 * Persisted via SQLDelight episode + episode_tiers tables — no separate queue table required.
 * [Source: epics.md#Story 4.2, architecture.md#Data Architecture]
 */
data class EpisodeQueue(val episodes: List<Episode>)
```

### T4 — BuildEpisodeQueueUseCase.kt
```kotlin
package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.EpisodeQueue
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository
import kotlinx.coroutines.CancellationException

/**
 * Builds the prioritised episode queue for a drive session (FR22, Story 4.2).
 * Episodes are ordered: LOVED first, then LIKED, then NONE.
 * Within each tier, ordered by most recent publication date.
 *
 * Queue persistence: the episode+tier tables are already in SQLDelight.
 * No separate queue table is needed — the ordering is stable and re-derivable (AR3, AR6).
 *
 * Never throws — all errors returned as [Result.Failure]. [Source: architecture.md#AR3]
 */
class BuildEpisodeQueueUseCase(private val episodeRepository: EpisodeRepository) {
    suspend fun execute(): Result<EpisodeQueue, AppError> {
        return try {
            val episodes = episodeRepository.getUnplayedOrderedByTier()
            Result.Success(EpisodeQueue(episodes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }
}
```

### T5 — PodDriveApplication.kt changes

Add after `setEpisodeTierUseCase` declaration:
```kotlin
lateinit var buildEpisodeQueueUseCase: BuildEpisodeQueueUseCase
    private set
```

Add in `onCreate()` after `setEpisodeTierUseCase = ...`:
```kotlin
buildEpisodeQueueUseCase = BuildEpisodeQueueUseCase(episodeRepository)
```

Add import: `import com.poddrive.usecase.BuildEpisodeQueueUseCase`

### T6 — BuildEpisodeQueueUseCaseTest.kt

```kotlin
package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuildEpisodeQueueUseCaseTest {

    private class FakeEpisodeRepository(
        private val orderedEpisodes: List<Episode> = emptyList(),
        private val shouldThrow: Boolean = false
    ) : EpisodeRepository {
        override fun upsertAll(episodes: List<Episode>) = Unit
        override fun getUnheardCountForPodcast(podcastId: String): Int = 0
        override fun getUnplayedOrderedByTier(): List<Episode> {
            if (shouldThrow) throw RuntimeException("DB failure")
            return orderedEpisodes
        }
    }

    private fun ep(id: String, tier: EpisodeTier, publishedAt: Long = 0L) =
        Episode(id, "show-1", "Title $id", 1_000L, publishedAt, isPlayed = false, tier)

    @Test
    fun `returns empty queue when no unplayed episodes exist`() = runTest {
        val result = BuildEpisodeQueueUseCase(FakeEpisodeRepository()).execute()
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).value.episodes.isEmpty())
    }

    @Test
    fun `preserves LOVED-first LIKED-second ordering from repository`() = runTest {
        val episodes = listOf(
            ep("loved-1", EpisodeTier.LOVED, publishedAt = 2_000L),
            ep("loved-2", EpisodeTier.LOVED, publishedAt = 1_000L),
            ep("liked-1", EpisodeTier.LIKED, publishedAt = 3_000L),
            ep("none-1",  EpisodeTier.NONE,  publishedAt = 9_000L)
        )
        val result = BuildEpisodeQueueUseCase(FakeEpisodeRepository(episodes)).execute()
        assertIs<Result.Success<*>>(result)
        val queue = (result as Result.Success).value
        assertEquals(4, queue.episodes.size)
        assertEquals("loved-1", queue.episodes[0].id)
        assertEquals("loved-2", queue.episodes[1].id)
        assertEquals("liked-1", queue.episodes[2].id)
        assertEquals("none-1",  queue.episodes[3].id)
    }

    @Test
    fun `wraps repository exception as AppError Unknown`() = runTest {
        val result = BuildEpisodeQueueUseCase(FakeEpisodeRepository(shouldThrow = true)).execute()
        assertIs<Result.Failure<*>>(result)
        assertIs<AppError.Unknown>((result as Result.Failure).error)
    }
}
```

### Architecture Rules — Must Follow

- **Use case returns Result<T, AppError>** — never throws (AR3)
- **Repository interface in `repository/`** — only `SqlDelightEpisodeRepository` in `storage/`
- **Episode IDs are Spotify URIs (String)** — never cast to numeric types (AR4)
- **`getUnplayedOrderedByTier()` is non-suspending** — SQLDelight sync call; wrap the use case in try/catch only
- **No separate queue table** — queue is derived from existing `episodes` + `episode_tiers` tables

### Scope Boundaries — Do NOT Implement in 4.2

- ❌ `EpisodeTile` composable or drive dashboard UI → Story 3.6
- ❌ Skeleton loading rows → Story 3.6 (UX-DR10)
- ❌ Auto-advance to next episode → Story 4.3
- ❌ Playback position resumption → Story 4.4
- ❌ Spotify deep-link handoff → Story 4.1

### Prerequisites Note

Stories 3.1–3.3 are marked as done in sprint-status.yaml but their implementation
code (DriveSessionManager, DriveSessionViewModel, DriveSessionForegroundService) is missing
from the repository. These must be created as part of the prerequisite work before
stories 4.x can compile and be tested. The prerequisites needed for 4.2 specifically:
- None beyond what already exists. Story 4.2 is pure domain layer, no DriveSessionManager dependency.

### Build Commands (from Story 3.3 learnings)

```bash
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
./gradlew assembleDebug --max-workers 1

JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
./gradlew testDebugUnitTest --max-workers 1
```

### Project Structure Notes

- `EpisodeQueue.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/domain/model/`
- `BuildEpisodeQueueUseCase.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/`
- `EpisodeRepository.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/repository/`
- `SqlDelightEpisodeRepository.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/`
- `BuildEpisodeQueueUseCaseTest.kt`: `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/`
- `PodDriveApplication.kt`: `poddrive/androidApp/src/main/kotlin/com/poddrive/android/`
- SQLDelight query: `poddrive/shared/src/commonMain/sqldelight/com/poddrive/Episode.sq` (already exists)

### References

- [Source: epics.md#Story 4.2] — user story, ACs, FR22, UX-DR4, UX-DR10, AR3, AR6
- [Source: architecture.md#Data Architecture] — SQLDelight as KMP database; AR3, AR6 persist-before-emit
- [Source: Episode.sq#selectUnplayedOrderedByTier] — existing query used by T2
- [Source: EpisodeRepository.kt] — interface to extend
- [Source: SqlDelightEpisodeRepository.kt] — implementation to extend

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- All 6 tasks completed. `selectUnplayedOrderedByTier` query was pre-existing in `Episode.sq` — no SQL changes needed.
- `SyncPodcastLibraryUseCaseTest.FakeEpisodeRepository` updated to implement new `getUnplayedOrderedByTier()` interface method.
- `assembleDebug` and `testDebugUnitTest` both pass (104 tests, 0 failures).

### File List

**New:**
- `shared/src/commonMain/kotlin/com/poddrive/domain/model/EpisodeQueue.kt`
- `shared/src/commonMain/kotlin/com/poddrive/usecase/BuildEpisodeQueueUseCase.kt`
- `shared/src/commonTest/kotlin/com/poddrive/usecase/BuildEpisodeQueueUseCaseTest.kt`

**Modified:**
- `shared/src/commonMain/kotlin/com/poddrive/repository/EpisodeRepository.kt`
- `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightEpisodeRepository.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt`
- `shared/src/commonTest/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCaseTest.kt` (FakeEpisodeRepository stub)

---

## Change Log

| Date | Change |
|------|--------|
| 2026-03-25 | Story created — ready for dev (from backlog, story file generated from epics.md) |
| 2026-03-25 | Implementation complete — all tasks done, build and tests pass, status → review |
