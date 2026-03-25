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

**AC1 — LOVED episodes precede LIKED in queue**
**Given** a drive session is active with LOVED and LIKED episodes available
**When** the episode queue is built by `BuildEpisodeQueueUseCase`
**Then** all unheard LOVED episodes appear before all unheard LIKED episodes in the queue (FR22)

**AC2 — Within-tier ordering by most recent publication**
**Given** episodes within the same tier
**When** the queue is sorted
**Then** episodes are ordered by most recent `publishedAt` descending within each tier

**AC3 — Queue visible on drive dashboard**
**Given** `DriveSessionState` is `Playing` or `Briefing`
**When** `DriveSessionScreen` is displayed
**Then** the queue is visible as scrollable `EpisodeTile` rows (UX-DR4)

**AC4 — Skeleton rows during loading**
**Given** the queue is being loaded
**When** `DriveSessionUiState` is `Loading`
**Then** 3 skeleton `EpisodeTile` placeholder rows display (UX-DR10)

**AC5 — Queue persisted to SQLDelight**
**Given** the queue has been built
**When** the session is backgrounded or the OS kills the process
**Then** the queue survives because it is read from `episode_tiers` + `episodes` tables in SQLDelight (AR3, AR6)

---

## Scope

**Prerequisite context (stories 3.1–3.3 were marked done without code — implementing here):**
The drive session infrastructure (DriveSessionState full sealed class, DriveSessionEvent full sealed class, DriveSessionManager, DriveSessionRepository, DriveSessionViewModel, DriveSessionForegroundService) was never committed to the repo. These are foundational prerequisites for Story 4.2.

**New files:**
| File | Purpose |
|------|---------|
| `shared/.../session/DriveSessionManager.kt` | State machine — only mutates DriveSessionState |
| `shared/.../repository/DriveSessionRepository.kt` | Interface for session persistence |
| `shared/.../storage/SqlDelightDriveSessionRepository.kt` | SQLDelight implementation |
| `shared/.../usecase/BuildEpisodeQueueUseCase.kt` | Builds LOVED-first ordered queue (FR22) |
| `androidApp/.../ui/drive/DriveSessionViewModel.kt` | Android ViewModel — session state + queue |
| `androidApp/.../ui/drive/DriveSessionScreen.kt` | Scrollable EpisodeTile list + skeleton |
| `androidApp/.../service/DriveSessionForegroundService.kt` | Foreground service for background audio |
| `shared/.../commonTest/.../usecase/BuildEpisodeQueueUseCaseTest.kt` | Unit tests |
| `shared/.../commonTest/.../session/DriveSessionManagerTest.kt` | State machine unit tests |

**Modified files:**
| File | Change |
|------|--------|
| `shared/.../domain/model/DriveSessionState.kt` | Add Briefing, Playing, Paused, Error, Ended states |
| `shared/.../domain/model/DriveSessionEvent.kt` | Add all events (BluetoothDisconnected, ManualTrigger, BriefingConfirmed, SkipEpisode, PauseSession, ResumeSession) |
| `shared/.../repository/EpisodeRepository.kt` | Add `getUnplayedOrderedByTier(): List<Episode>` |
| `shared/.../storage/SqlDelightEpisodeRepository.kt` | Implement `getUnplayedOrderedByTier()` |
| `androidApp/.../MainActivity.kt` | Swap to DriveSessionScreen when isDriveActive |
| `androidApp/.../PodDriveApplication.kt` | Wire DriveSessionManager + BuildEpisodeQueueUseCase |
| `androidApp/.../AndroidManifest.xml` | Declare DriveSessionForegroundService |

---

## Tasks / Subtasks

- [x] T1 — Complete `DriveSessionState` sealed class (AC3, prerequisite)
  - [x] Add `data class Briefing(val script: String)` state
  - [x] Add `data class Playing(val episode: Episode, val positionMs: Long)` state
  - [x] Add `object Paused` state
  - [x] Add `data class Error(val error: AppError)` state
  - [x] Add `object Ended` state
  - [x] Update doc comment to reflect all states

- [x] T2 — Complete `DriveSessionEvent` sealed class (AC1, prerequisite)
  - [x] Add `object BluetoothDisconnected`
  - [x] Add `object ManualTrigger` (FR13, Story 3.3)
  - [x] Add `object BriefingConfirmed`
  - [x] Add `object SkipEpisode`
  - [x] Add `object PauseSession`
  - [x] Add `object ResumeSession`

- [x] T3 — Create `DriveSessionRepository` interface + SQLDelight implementation (AC5, prerequisite)
  - [x] Interface: `persistState(sessionId: String, state: DriveSessionState)`; `loadLatestSessionState(): DriveSessionState?`
  - [x] `SqlDelightDriveSessionRepository` maps DriveSessionState ↔ state TEXT column
  - [x] `Idle` maps to "Idle", `Activating` to "Activating", `Playing` to "Playing:{episodeId}:{positionMs}", etc.

- [x] T4 — Create `DriveSessionManager` (state machine, AC1/AC3/AC5, prerequisite)
  - [x] Mutex-protected `transitionTo()` — persist THEN emit (AR6)
  - [x] `resolveTransition()`: Idle + BluetoothConnected/ManualTrigger → Activating
  - [x] Activating → Briefing auto-stub (immediate, no real TTS)
  - [x] Briefing + BriefingConfirmed → Playing(firstEpisode, 0)
  - [x] Playing + SkipEpisode → Playing(nextEpisode, 0) or Ended if queue empty
  - [x] Playing + PauseSession → Paused; Paused + ResumeSession → Playing
  - [x] Any non-Ended + BluetoothDisconnected → Ended
  - [x] `currentQueue: List<Episode>` set by `loadQueue(episodes)`

- [x] T5 — Add `getUnplayedOrderedByTier()` to `EpisodeRepository` and impl (AC1, AC2)
  - [x] Add method to `EpisodeRepository` interface
  - [x] Implement in `SqlDelightEpisodeRepository` using existing `selectUnplayedOrderedByTier` query
  - [x] Map SQLDelight result rows → `Episode` domain models (with tier from JOIN)

- [x] T6 — Create `BuildEpisodeQueueUseCase` (AC1, AC2, AC5)
  - [x] `execute(): Result<List<Episode>, AppError>` — calls `episodeRepository.getUnplayedOrderedByTier()`
  - [x] Returns LOVED episodes first (sorted by publishedAt DESC), then LIKED (sorted by publishedAt DESC), then NONE filtered out
  - [x] Wraps any exception in `Result.Failure(AppError.Unknown(e))`

- [x] T7 — Create `DriveSessionViewModel` (AC3, AC4)
  - [x] `AndroidViewModel` — `getApplication<PodDriveApplication>()` for singletons
  - [x] `sessionState: StateFlow<DriveSessionState>` — collect from `DriveSessionManager`
  - [x] `queue: StateFlow<List<Episode>>` — loaded on session activation
  - [x] `isLoading: StateFlow<Boolean>` — true while `BuildEpisodeQueueUseCase` is running
  - [x] `startManualDriveSession()` — dispatch ManualTrigger + start foreground service
  - [x] `startDriveSession()` — for Bluetooth-triggered sessions (dispatch BluetoothConnected)
  - [x] `confirmBriefing()` — dispatch BriefingConfirmed

- [x] T8 — Create `DriveSessionForegroundService` (prerequisite, AC3)
  - [x] `Service` with `startForeground()` notification (FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
  - [x] Creates a `CoroutineScope`; cancels on `onDestroy()`
  - [x] Does NOT duplicate `DriveSessionManager` logic — just keeps process alive

- [x] T9 — Create `DriveSessionScreen` composable (AC3, AC4)
  - [x] Shows `Loading` state: 3 `EpisodeTileSkeleton` rows
  - [x] Shows `Ready` state: `LazyColumn` of `EpisodeTile` rows
  - [x] `EpisodeTile`: title, podcast name, duration, tier badge (LOVED/LIKED icon)
  - [x] Back gesture suppressed; no bottom nav bar

- [x] T10 — Update `MainActivity` to swap screens (AC3, prerequisite)
  - [x] Inject `driveViewModel: DriveSessionViewModel by viewModels()`
  - [x] Show `DriveSessionScreen` when `isDriveActive` (state is not Idle/Ended)
  - [x] Pass `driveViewModel` to `HomeScreen`

- [x] T11 — Update `PodDriveApplication` (prerequisite wiring)
  - [x] Add `driveSessionManager: DriveSessionManager`
  - [x] Add `buildEpisodeQueueUseCase: BuildEpisodeQueueUseCase`
  - [x] Wire singletons in `onCreate()`

- [x] T12 — Declare `DriveSessionForegroundService` in `AndroidManifest.xml`
  - [x] Add `<service android:name=".service.DriveSessionForegroundService" android:foregroundServiceType="mediaPlayback"/>`
  - [x] Ensure `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions present

- [x] T13 — Write unit tests for `BuildEpisodeQueueUseCase` (AC1, AC2)
  - [x] `LOVED episodes precede LIKED in queue`
  - [x] `Within LOVED tier, sorted by publishedAt DESC`
  - [x] `Within LIKED tier, sorted by publishedAt DESC`
  - [x] `NONE tier episodes excluded from queue`
  - [x] `Empty repository returns empty list`

- [x] T14 — Write unit tests for `DriveSessionManager` (AC1/AC3 transitions)
  - [x] `Idle + BluetoothConnected → Activating`
  - [x] `Idle + ManualTrigger → Activating`
  - [x] `Briefing + BriefingConfirmed → Playing with first episode`
  - [x] `Playing + SkipEpisode → Playing with next episode`
  - [x] `Playing + SkipEpisode on last episode → Ended`
  - [x] `Any + BluetoothDisconnected → Ended`

---

## Dev Notes

### Architecture Constraints (must follow)
- Only `DriveSessionManager` mutates `DriveSessionState` (AR6)
- Persist-before-emit: every state change written to `DriveSessionRepository` BEFORE `MutableStateFlow.value = newState`
- ViewModels dispatch events; never set state directly
- No direct Spotify HTTP calls from ViewModel — all via use cases

### `selectUnplayedOrderedByTier` query (already in Episode.sq)
```sql
SELECT e.id, e.podcast_id, e.title, e.duration_ms, e.published_at, e.is_played,
       COALESCE(et.tier, 'NONE') AS tier
FROM episodes e
LEFT JOIN episode_tiers et ON e.id = et.episode_id
WHERE e.is_played = 0
ORDER BY
    CASE COALESCE(et.tier, 'NONE')
        WHEN 'LOVED' THEN 0
        WHEN 'LIKED' THEN 1
        ELSE 2
    END,
    e.published_at DESC;
```
This already handles AC1 and AC2. `BuildEpisodeQueueUseCase` just needs to call it and filter out NONE.

### DriveSessionState expansion
```kotlin
sealed class DriveSessionState {
    object Idle : DriveSessionState()
    object Activating : DriveSessionState()
    data class Briefing(val script: String) : DriveSessionState()
    data class Playing(val episode: Episode, val positionMs: Long) : DriveSessionState()
    object Paused : DriveSessionState()
    data class Error(val error: AppError) : DriveSessionState()
    object Ended : DriveSessionState()
}
```

### DriveSessionManager skeleton
```kotlin
class DriveSessionManager(
    private val sessionRepository: DriveSessionRepository,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DriveSessionState>(DriveSessionState.Idle)
    val state: StateFlow<DriveSessionState> = _state.asStateFlow()

    var currentQueue: List<Episode> = emptyList()
        private set

    fun loadQueue(episodes: List<Episode>) { currentQueue = episodes }

    fun dispatch(event: DriveSessionEvent) {
        scope.launch {
            mutex.withLock {
                val next = resolveTransition(_state.value, event) ?: return@withLock
                sessionRepository.persistState("singleton", next)  // persist first
                _state.value = next                                  // then emit
            }
        }
    }

    private fun resolveTransition(state: DriveSessionState, event: DriveSessionEvent): DriveSessionState? = when (state) {
        is DriveSessionState.Idle -> when (event) {
            is DriveSessionEvent.BluetoothConnected -> DriveSessionState.Activating
            is DriveSessionEvent.ManualTrigger -> DriveSessionState.Activating
            else -> null
        }
        is DriveSessionState.Activating -> DriveSessionState.Briefing("Ready to drive.")  // stub
        is DriveSessionState.Briefing -> when (event) {
            is DriveSessionEvent.BriefingConfirmed -> {
                val first = currentQueue.firstOrNull()
                if (first != null) DriveSessionState.Playing(first, 0L)
                else DriveSessionState.Ended
            }
            else -> null
        }
        is DriveSessionState.Playing -> when (event) {
            is DriveSessionEvent.SkipEpisode -> {
                val idx = currentQueue.indexOf(state.episode)
                val next = currentQueue.getOrNull(idx + 1)
                if (next != null) DriveSessionState.Playing(next, 0L) else DriveSessionState.Ended
            }
            is DriveSessionEvent.PauseSession -> DriveSessionState.Paused
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            else -> null
        }
        is DriveSessionState.Paused -> when (event) {
            is DriveSessionEvent.ResumeSession -> DriveSessionState.Playing(/* recover from state */)
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            else -> null
        }
        else -> when (event) {
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            else -> null
        }
    }
}
```

### Activating → Briefing auto-transition
`Activating` has no event to wait for — the manager auto-advances immediately in `dispatch()` after emitting `Activating` (same `scope.launch` call). This is a stub until Story 3.4 (TTS, deferred).

### Build command
```bash
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
./gradlew :shared:testDebugUnitTest --max-workers 1
```

### References
- [Source: epics.md#Story 4.2] — user story, ACs, FR22, UX-DR4, UX-DR10, AR3, AR6
- [Source: architecture.md#Communication Patterns] — DriveSessionState, DriveSessionEvent sealed classes
- [Source: architecture.md#Process Patterns] — persist-before-emit rule
- [Source: Episode.sq#selectUnplayedOrderedByTier] — pre-built LOVED→LIKED SQL query

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Fixed `Unresolved reference 'tier'` in `SqlDelightDriveSessionRepository.kt:94` — `selectTierForEpisode` returns single `String?` column, not a data class; changed `t.tier` → `t`
- Fixed `Unresolved reference 'isStale'` in `HomeScreen.kt:217` — pre-existing bug: added `isStale: Boolean = false` to `HomeViewModel.UiState.Success`
- Fixed `Unresolved reference 'onSuccess'` in `DriveSessionViewModel.kt:148` — added missing `import com.poddrive.domain.model.onSuccess`
- Created `poddrive/local.properties` with `sdk.dir` — was missing entirely, causing all builds to fail

### Completion Notes List

- All 14 tasks implemented and verified. `assembleDebug` and `testDebugUnitTest` pass with 0 failures.
- `BuildEpisodeQueueUseCaseTest`: 7 tests, 0 failures. `DriveSessionManagerTest`: 16 tests, 0 failures.
- Stories 3.1–3.3 prerequisite infrastructure (DriveSessionState, DriveSessionEvent, DriveSessionManager, DriveSessionRepository, DriveSessionViewModel, DriveSessionForegroundService, DriveSessionScreen) was never committed; implemented within this story's scope as noted.
- `Activating → Briefing` auto-advances without a trigger event (stub until Story 3.4 TTS).
- `BuildEpisodeQueueUseCase.execute()` filters NONE-tier episodes; SQLDelight `selectUnplayedOrderedByTier` query handles LOVED→LIKED ordering natively.
- `FakeEpisodeRepository` in `SyncPodcastLibraryUseCaseTest` updated with `getUnplayedOrderedByTier()` to satisfy updated interface.

### File List

**New files:**
- `shared/src/commonMain/kotlin/com/poddrive/repository/DriveSessionRepository.kt`
- `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightDriveSessionRepository.kt`
- `shared/src/commonMain/kotlin/com/poddrive/session/DriveSessionManager.kt`
- `shared/src/commonMain/kotlin/com/poddrive/usecase/BuildEpisodeQueueUseCase.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionScreen.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/service/DriveSessionForegroundService.kt`
- `shared/src/commonTest/kotlin/com/poddrive/usecase/BuildEpisodeQueueUseCaseTest.kt`
- `shared/src/commonTest/kotlin/com/poddrive/session/DriveSessionManagerTest.kt`
- `poddrive/local.properties`

**Modified files:**
- `shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionState.kt`
- `shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionEvent.kt`
- `shared/src/commonMain/kotlin/com/poddrive/repository/EpisodeRepository.kt`
- `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightEpisodeRepository.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/MainActivity.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt`
- `androidApp/src/main/AndroidManifest.xml`
- `androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeViewModel.kt` (bug fix: added `isStale`)
- `shared/src/commonTest/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCaseTest.kt` (added `getUnplayedOrderedByTier()` to fake)

---

## Change Log

| Date | Change |
|------|--------|
| 2026-03-25 | Story created — ready for dev (prerequisite code from 3.x also included in scope) |
| 2026-03-25 | Story implemented — all 14 tasks complete; build + tests pass; status → review |
