---
status: done
story_id: "4.5"
story_key: 4-5-spotify-playback-failure-handling
epic: 4
created: 2026-03-25
---

# Story 4.5 — Spotify Playback Failure Handling

## User Story

As a **user**,
I want to be notified if Spotify playback fails, without the app crashing,
so that I'm aware of the issue and can retry safely while driving.

---

## Acceptance Criteria

**AC1 — App transitions to Error state on playback failure; does not crash**
**Given** a drive session is active (state is `Activating`, `Briefing`, `Playing`, or `Paused`)
**When** a Spotify playback failure or deep-link timeout occurs
**Then** the app does not crash — `DriveSessionManager` dispatches `PlaybackFailed(error)` (FR25, NFR12)
**And** `DriveSessionState` transitions to `Error(error)`
**Note:** `Paused` is included as a valid source state (IG-2 code review finding) — a failure can
occur while playback is paused (e.g. Spotify disconnects mid-pause). Covered by test
`resolveTransition Paused + PlaybackFailed = Error` in `DriveSessionManagerTest`.

**AC2 — Voice notification (deferred to Story 3.4)**
**Given** `DriveSessionState` transitions to `Error`
**When** TTS engine is wired (Story 3.4, deferred MVP Phase 2)
**Then** voice plays: "Could not connect. Tap to retry." (FR25, UX-DR8)
**Note:** `FAILURE_SCRIPT` constant is defined in `HandlePlaybackFailureUseCase` for future wiring

**AC3 — Drive UI shows ERROR state (deferred to Story 3.6)**
**Given** `DriveSessionState.Error` is emitted
**When** the drive dashboard is displayed (Story 3.6)
**Then** `DriveSessionCard` renders in ERROR state with visible retry action (UX-DR10)

**AC4 — 3-second Spotify handoff timeout triggers Error**
**Given** `HandlePlaybackFailureUseCase.awaitHandoffOrTimeout()` is called
**When** the handoff block does not complete within `HANDOFF_TIMEOUT_MS` (3 000 ms)
**Then** `AppError.SpotifyHandoffTimeout` is dispatched as `PlaybackFailed`
**And** the use case returns `Result.Failure(AppError.SpotifyHandoffTimeout)`

**AC5 — Retry transitions state back to Activating**
**Given** `DriveSessionState` is `Error`
**When** `HandlePlaybackFailureUseCase.retry()` is called
**Then** `DriveSessionEvent.RetryPlayback` is dispatched
**And** state transitions `Error → Activating`

---

## Scope

**New files:**
| File | Purpose |
|------|---------|
| `shared/src/commonMain/kotlin/com/poddrive/domain/model/BriefingScript.kt` | Placeholder domain model for Briefing state (Story 3.4 deferred) |
| `shared/src/commonMain/kotlin/com/poddrive/repository/DriveSessionRepository.kt` | Repository interface for drive session persistence |
| `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightDriveSessionRepository.kt` | SQLDelight-backed implementation |
| `shared/src/commonMain/kotlin/com/poddrive/session/DriveSessionManager.kt` | State machine — the ONLY class that mutates DriveSessionState |
| `androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt` | Android ViewModel exposing session state to UI |
| `shared/src/commonMain/kotlin/com/poddrive/usecase/HandlePlaybackFailureUseCase.kt` | Timeout wrapper + failure/retry dispatch use case |
| `shared/src/commonTest/kotlin/com/poddrive/session/DriveSessionManagerTest.kt` | State machine unit tests (error paths) |
| `shared/src/commonTest/kotlin/com/poddrive/usecase/HandlePlaybackFailureUseCaseTest.kt` | Use case unit tests |

**Modified files:**
| File | Change |
|------|--------|
| `shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionState.kt` | Add `Briefing`, `Playing`, `Paused`, `Error`, `Ended` states |
| `shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionEvent.kt` | Add all missing events + `PlaybackFailed`, `RetryPlayback` |
| `shared/src/commonMain/kotlin/com/poddrive/domain/model/AppError.kt` | Add `SpotifyHandoffTimeout` |
| `androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` | Add `driveSessionManager` and `DriveSessionRepository` wiring |

**No new Gradle dependencies.** Uses existing `kotlinx.coroutines.core` (`withTimeout`, `StateFlow`, `Mutex`).

**AC2 (TTS) and AC3 (Drive UI) are deferred** — this story covers the domain/state-machine layer only.

---

## Tasks / Subtasks

- [x] T1 — Create `BriefingScript.kt` placeholder and update `DriveSessionState.kt` (AC1)
  - [x] Create `data class BriefingScript(val text: String)` in `domain/model/`
  - [x] Add `Briefing(val script: BriefingScript)`, `Playing(val episode: Episode, val positionMs: Long)`, `Paused`, `Error(val error: AppError)`, `Ended` to `DriveSessionState`
  - [x] Keep existing `Idle` and `Activating` states unchanged

- [x] T2 — Update `DriveSessionEvent.kt` (AC1, AC5)
  - [x] Add `BluetoothDisconnected`, `BriefingConfirmed`, `SkipEpisode`, `PauseSession`, `ResumeSession` (from architecture)
  - [x] Add `ManualTrigger` (Story 3.3)
  - [x] Add `data class PlaybackFailed(val error: AppError)` (Story 4.5, AC1)
  - [x] Add `object RetryPlayback` (Story 4.5, AC5)

- [x] T3 — Add `SpotifyHandoffTimeout` to `AppError.kt` (AC4)
  - [x] `object SpotifyHandoffTimeout : AppError()` with KDoc referencing 3-second timeout (UX-DR10)

- [x] T4 — Create `DriveSessionRepository.kt` interface and `SqlDelightDriveSessionRepository.kt` impl
  - [x] Interface: `saveState(sessionId, state)`, `loadLatestState()`, `saveNewSession(sessionId, startedAtMs, state)`, `endSession(sessionId, endedAtMs)`, `deleteAll()`
  - [x] Implementation: map `DriveSessionState` to/from String for SQLDelight `state TEXT` column
  - [x] `stateToString()` and `stringToState()` are private helpers

- [x] T5 — Create `DriveSessionManager.kt` in `session/` package (AC1, AC5)
  - [x] `class DriveSessionManager(repo: DriveSessionRepository, scope: CoroutineScope = CoroutineScope(Dispatchers.Default))`
  - [x] `val state: StateFlow<DriveSessionState>` — starts at `Idle`
  - [x] `fun dispatch(event: DriveSessionEvent)` — fire-and-forget, mutex-protected
  - [x] `internal fun resolveTransition(current, event): DriveSessionState?` — pure function, testable
  - [x] Persist-before-emit: call `repository.saveState()` before `_state.value = nextState` (AR6)
  - [x] Key transitions: `* + PlaybackFailed → Error`, `Error + RetryPlayback → Activating`

- [x] T6 — Create `DriveSessionViewModel.kt` stub in `androidApp/.../ui/drive/` (compile fix)
  - [x] `class DriveSessionViewModel(app: Application) : AndroidViewModel(app)`
  - [x] `val sessionState: StateFlow<DriveSessionState>` from `PodDriveApplication.driveSessionManager.state`
  - [x] `fun startManualDriveSession()` — dispatches `ManualTrigger` (Story 3.3 contract)

- [x] T7 — Wire `DriveSessionManager` in `PodDriveApplication.kt`
  - [x] Add `lateinit var driveSessionRepository: DriveSessionRepository` + `lateinit var driveSessionManager: DriveSessionManager`
  - [x] In `onCreate()`: instantiate `SqlDelightDriveSessionRepository(database)` and `DriveSessionManager(driveSessionRepository)`

- [x] T8 — Create `HandlePlaybackFailureUseCase.kt` (AC1, AC4, AC5)
  - [x] `suspend fun <T> awaitHandoffOrTimeout(block: suspend () -> T): Result<T, AppError>`
  - [x] Timeout of `HANDOFF_TIMEOUT_MS = 3_000L` using `withTimeout`
  - [x] On `TimeoutCancellationException` → dispatch `PlaybackFailed(SpotifyHandoffTimeout)` → `Result.Failure`
  - [x] On other exceptions → dispatch `PlaybackFailed(Unknown(e))` → `Result.Failure`
  - [x] On success → `Result.Success(value)` (no dispatch)
  - [x] `fun retry()` → dispatch `RetryPlayback`
  - [x] `FAILURE_SCRIPT = "Could not connect. Tap to retry."` constant

- [x] T9 — Unit tests for `DriveSessionManager` error paths (AC1, AC5)
  - [x] `FakeDriveSessionRepository` implementing `DriveSessionRepository`
  - [x] Test: `PlaybackFailed` from `Activating` → `Error`
  - [x] Test: `RetryPlayback` from `Error` → `Activating`
  - [x] Test: `BluetoothDisconnected` from `Error` → `Ended`
  - [x] Test: `ManualTrigger` from `Idle` → `Activating` (verifies basic SM works)
  - [x] Test: state is persisted before being emitted (persist-before-emit rule)

- [x] T10 — Unit tests for `HandlePlaybackFailureUseCase` (AC1, AC4, AC5)
  - [x] Use real `DriveSessionManager` with `FakeDriveSessionRepository` + test scope
  - [x] First transition manager to `Activating` (via ManualTrigger) before testing failure
  - [x] Test: successful handoff within timeout → `Result.Success`, state unchanged
  - [x] Test: timeout → `PlaybackFailed` dispatched, state → `Error`, returns `Failure(SpotifyHandoffTimeout)`
  - [x] Test: exception → `PlaybackFailed` dispatched, state → `Error`, returns `Failure(Unknown)`
  - [x] Test: `retry()` from `Error` → state → `Activating`

---

## Dev Notes

### Architecture: DriveSessionManager (session/ package)

```kotlin
package com.poddrive.session

import com.poddrive.currentTimeMillis
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.BriefingScript
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.repository.DriveSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the drive session state machine. The ONLY class that mutates [DriveSessionState] (AR6).
 * Persist-before-emit rule: repository.saveState() is called before _state.value is updated.
 * Thread-safe via [Mutex] — dispatch() is fire-and-forget.
 */
class DriveSessionManager(
    private val repository: DriveSessionRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val timeSource: () -> Long = { currentTimeMillis() }
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DriveSessionState>(DriveSessionState.Idle)
    val state: StateFlow<DriveSessionState> = _state.asStateFlow()

    private var currentSessionId: String? = null
    // Stored for Paused → Playing restoration (session-scoped state, not persisted in MVP)
    private var lastPlayingState: DriveSessionState.Playing? = null

    fun dispatch(event: DriveSessionEvent) {
        scope.launch {
            mutex.withLock {
                val next = resolveTransition(_state.value, event) ?: return@withLock
                val sessionId = currentSessionId ?: "session-${timeSource()}".also { currentSessionId = it }
                // Persist-before-emit (AR6)
                if (_state.value is DriveSessionState.Idle && next is DriveSessionState.Activating) {
                    repository.saveNewSession(sessionId, timeSource(), next)
                } else {
                    repository.saveState(sessionId, next)
                }
                if (next is DriveSessionState.Playing) lastPlayingState = next
                _state.value = next
            }
        }
    }

    internal fun resolveTransition(
        current: DriveSessionState,
        event: DriveSessionEvent
    ): DriveSessionState? = when (current) {
        is DriveSessionState.Idle -> when (event) {
            is DriveSessionEvent.BluetoothConnected -> DriveSessionState.Activating
            is DriveSessionEvent.ManualTrigger -> DriveSessionState.Activating
            else -> null
        }
        is DriveSessionState.Activating -> when (event) {
            is DriveSessionEvent.BriefingConfirmed -> DriveSessionState.Briefing(BriefingScript(""))
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed -> DriveSessionState.Error(event.error)
            else -> null
        }
        is DriveSessionState.Briefing -> when (event) {
            is DriveSessionEvent.SkipEpisode -> DriveSessionState.Activating
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed -> DriveSessionState.Error(event.error)
            else -> null
        }
        is DriveSessionState.Playing -> when (event) {
            is DriveSessionEvent.PauseSession -> DriveSessionState.Paused
            is DriveSessionEvent.SkipEpisode -> DriveSessionState.Activating
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed -> DriveSessionState.Error(event.error)
            else -> null
        }
        is DriveSessionState.Paused -> when (event) {
            is DriveSessionEvent.ResumeSession -> lastPlayingState ?: DriveSessionState.Activating
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed -> DriveSessionState.Error(event.error)
            else -> null
        }
        is DriveSessionState.Error -> when (event) {
            is DriveSessionEvent.RetryPlayback -> DriveSessionState.Activating
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            else -> null
        }
        is DriveSessionState.Ended -> null  // Terminal state — no transitions out
    }
}
```

### Architecture: HandlePlaybackFailureUseCase

```kotlin
package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.Result
import com.poddrive.session.DriveSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class HandlePlaybackFailureUseCase(private val driveSessionManager: DriveSessionManager) {

    suspend fun <T> awaitHandoffOrTimeout(handoffBlock: suspend () -> T): Result<T, AppError> {
        return try {
            val result = withTimeout(HANDOFF_TIMEOUT_MS) { handoffBlock() }
            Result.Success(result)
        } catch (e: TimeoutCancellationException) {
            val error = AppError.SpotifyHandoffTimeout
            driveSessionManager.dispatch(DriveSessionEvent.PlaybackFailed(error))
            Result.Failure(error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = AppError.Unknown(e)
            driveSessionManager.dispatch(DriveSessionEvent.PlaybackFailed(error))
            Result.Failure(error)
        }
    }

    fun retry() {
        driveSessionManager.dispatch(DriveSessionEvent.RetryPlayback)
    }

    companion object {
        const val HANDOFF_TIMEOUT_MS = 3_000L
        /** Voice script (FR25, UX-DR8). Wired to TTS in Story 3.4 (deferred). */
        const val FAILURE_SCRIPT = "Could not connect. Tap to retry."
    }
}
```

### DriveSessionRepository Interface

```kotlin
package com.poddrive.repository

import com.poddrive.domain.model.DriveSessionState

interface DriveSessionRepository {
    fun saveState(sessionId: String, state: DriveSessionState)
    fun loadLatestState(): DriveSessionState?
    fun saveNewSession(sessionId: String, startedAtMs: Long, state: DriveSessionState)
    fun endSession(sessionId: String, endedAtMs: Long)
    fun deleteAll()
}
```

### SqlDelightDriveSessionRepository

```kotlin
package com.poddrive.storage

import com.poddrive.currentTimeMillis
import com.poddrive.db.PodDrive
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.BriefingScript
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.repository.DriveSessionRepository

class SqlDelightDriveSessionRepository(private val db: PodDrive) : DriveSessionRepository {

    override fun saveState(sessionId: String, state: DriveSessionState) {
        db.driveSessionQueries.updateSessionState(
            state = stateToString(state),
            updated_at = currentTimeMillis(),
            id = sessionId
        )
    }

    override fun loadLatestState(): DriveSessionState? =
        db.driveSessionQueries.selectLatestSession().executeAsOneOrNull()
            ?.let { stringToState(it.state) }

    override fun saveNewSession(sessionId: String, startedAtMs: Long, state: DriveSessionState) {
        db.driveSessionQueries.upsertSession(
            id = sessionId,
            state = stateToString(state),
            started_at = startedAtMs,
            ended_at = null,
            updated_at = startedAtMs
        )
    }

    override fun endSession(sessionId: String, endedAtMs: Long) {
        db.driveSessionQueries.endSession(
            ended_at = endedAtMs,
            updated_at = endedAtMs,
            id = sessionId
        )
    }

    override fun deleteAll() {
        db.driveSessionQueries.deleteAllSessions()
    }

    private fun stateToString(state: DriveSessionState): String = when (state) {
        is DriveSessionState.Idle -> "Idle"
        is DriveSessionState.Activating -> "Activating"
        is DriveSessionState.Briefing -> "Briefing"
        is DriveSessionState.Playing -> "Playing"
        is DriveSessionState.Paused -> "Paused"
        is DriveSessionState.Error -> "Error"
        is DriveSessionState.Ended -> "Ended"
    }

    private fun stringToState(stateStr: String): DriveSessionState = when (stateStr) {
        "Activating" -> DriveSessionState.Activating
        "Briefing" -> DriveSessionState.Briefing(BriefingScript(""))
        "Playing" -> DriveSessionState.Playing(
            Episode("", "", "", 0L, 0L, false, EpisodeTier.NONE), 0L
        )
        "Paused" -> DriveSessionState.Paused
        "Error" -> DriveSessionState.Error(AppError.Unknown(RuntimeException("restored")))
        "Ended" -> DriveSessionState.Ended
        else -> DriveSessionState.Idle
    }
}
```

### DriveSessionViewModel (stub — compile fix for Story 3.3 reference)

```kotlin
package com.poddrive.android.ui.drive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.poddrive.android.PodDriveApplication
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import kotlinx.coroutines.flow.StateFlow

class DriveSessionViewModel(app: Application) : AndroidViewModel(app) {
    private val podApp = PodDriveApplication.from(app)
    val sessionState: StateFlow<DriveSessionState> = podApp.driveSessionManager.state

    fun startManualDriveSession() {
        podApp.driveSessionManager.dispatch(DriveSessionEvent.ManualTrigger)
    }
}
```

### PodDriveApplication additions

Add after `setEpisodeTierUseCase` declaration:
```kotlin
lateinit var driveSessionRepository: DriveSessionRepository
    private set

lateinit var driveSessionManager: DriveSessionManager
    private set
```

Add in `onCreate()` after database setup:
```kotlin
driveSessionRepository = SqlDelightDriveSessionRepository(database)
driveSessionManager = DriveSessionManager(driveSessionRepository)
```

Imports needed:
```kotlin
import com.poddrive.repository.DriveSessionRepository
import com.poddrive.session.DriveSessionManager
import com.poddrive.storage.SqlDelightDriveSessionRepository
```

### Test Patterns

```kotlin
// FakeDriveSessionRepository
private class FakeDriveSessionRepository : DriveSessionRepository {
    val savedStates = mutableListOf<Pair<String, String>>()
    override fun saveState(sessionId: String, state: DriveSessionState) {
        savedStates += sessionId to state::class.simpleName!!
    }
    override fun loadLatestState(): DriveSessionState? = null
    override fun saveNewSession(sessionId: String, startedAtMs: Long, state: DriveSessionState) {
        savedStates += sessionId to state::class.simpleName!!
    }
    override fun endSession(sessionId: String, endedAtMs: Long) = Unit
    override fun deleteAll() = Unit
}

// DriveSessionManagerTest — dispatch + advanceUntilIdle pattern
@Test
fun `PlaybackFailed from Activating transitions to Error`() = runTest {
    val manager = DriveSessionManager(FakeDriveSessionRepository(), this)
    manager.dispatch(DriveSessionEvent.ManualTrigger)
    advanceUntilIdle()
    assertIs<DriveSessionState.Activating>(manager.state.value)
    manager.dispatch(DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout))
    advanceUntilIdle()
    val state = manager.state.value
    assertIs<DriveSessionState.Error>(state)
    assertEquals(AppError.SpotifyHandoffTimeout, state.error)
}
```

NOTE: Pass `this` (the TestScope) to `DriveSessionManager` constructor so dispatched
coroutines run in the test scheduler. Call `advanceUntilIdle()` after each `dispatch()` call.

### Architecture Rules — Must Follow

- **Only `DriveSessionManager` mutates `DriveSessionState`** (AR6) — ViewModels observe only
- **Persist-before-emit** (AR6) — `repository.saveState()` called BEFORE `_state.value = next`
- **Mutex** — all transitions run inside `mutex.withLock {}` to prevent race conditions
- **`dispatch()` is fire-and-forget** — it launches internally; callers do NOT wrap in viewModelScope
- **No business logic in ViewModel** — use cases + managers handle all logic (architecture anti-pattern rule)
- **`DriveSessionEvent` sealed class** — adding `PlaybackFailed` and `RetryPlayback` requires updating all `when` expressions that match on `DriveSessionEvent` (Kotlin compiler will enforce exhaustiveness)

### Scope Boundaries — Do NOT Implement in 4.5

- ❌ TTS "Could not connect. Tap to retry." wiring → Story 3.4 (deferred MVP Phase 2)
- ❌ `DriveSessionCard` ERROR state UI → Story 3.6
- ❌ `DriveSessionForegroundService` → Story 3.2 (already marked done)
- ❌ Bluetooth detection wiring → Story 3.2
- ❌ Spotify deep-link handoff call site (that invokes `awaitHandoffOrTimeout`) → Story 4.1
- ❌ Auto-advance to next episode → Story 4.3

### Previous Story Learnings

- `currentTimeMillis()` is available in `commonMain` via `import com.poddrive.currentTimeMillis` (from `Platform.kt`)
- `Result<T, E>` has two params (not standard `kotlin.Result`) — use `com.poddrive.domain.model.Result`
- Test coroutines: use `runTest { }` with `advanceUntilIdle()` after dispatches; pass `this` as scope to `DriveSessionManager`
- `kotlinx.coroutines.test.runTest` is available in `commonTest` (see `shared/build.gradle.kts`)
- `withTimeout` from `kotlinx.coroutines` is available (already in `commonMain.dependencies`)
- `TimeoutCancellationException` must be caught BEFORE generic `CancellationException`

### Build Commands

```bash
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
./gradlew assembleDebug --max-workers 1

JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
./gradlew testDebugUnitTest --max-workers 1
```

### Project Structure Notes

- `BriefingScript.kt`, `DriveSessionState.kt`, `DriveSessionEvent.kt`, `AppError.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/domain/model/`
- `DriveSessionRepository.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/repository/`
- `SqlDelightDriveSessionRepository.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/`
- `DriveSessionManager.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/session/`
- `DriveSessionViewModel.kt`: `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/`
- `HandlePlaybackFailureUseCase.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/`
- `DriveSessionManagerTest.kt`: `poddrive/shared/src/commonTest/kotlin/com/poddrive/session/`
- `HandlePlaybackFailureUseCaseTest.kt`: `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/`

### References

- [Source: epics.md#Story 4.5] — user story, ACs, FR25, NFR12, UX-DR8, UX-DR10
- [Source: architecture.md#Communication Patterns] — `DriveSessionState` sealed class, AR6 persist-before-emit
- [Source: architecture.md#API & Communication Patterns] — `AppError` sealed class, AR5
- [Source: DriveSession.sq] — `upsertSession`, `updateSessionState`, `endSession` queries
- [Source: story 3-3-manual-drive-session-trigger.md#Dev Notes] — `dispatch()` mutex/scope pattern, `currentTimeMillis()` import

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- All 10 tasks completed. Stories 3.1–3.3 prerequisite code (DriveSessionManager, DriveSessionViewModel, DriveSessionRepository, full DriveSessionState/Event sealed classes) was missing from the repository despite sprint status showing "done". All prerequisites implemented here.
- `DriveSessionManager.dispatch()` uses `scope.launch + Mutex` for thread-safe fire-and-forget event processing; `resolveTransition()` is `internal` for white-box testing.
- `advanceUntilIdle()` in coroutines-test 1.8.1 requires `testScheduler.advanceUntilIdle()` call style (not bare `advanceUntilIdle()`).
- `assertEquals(cause, ...)` for exception identity fails under `StandardTestDispatcher` because `withTimeout` dispatches the block through coroutine machinery — fixed to compare `.message` instead.
- `HomeViewModel.UiState.Success` was missing `isStale: Boolean` field referenced by `HomeScreen.kt` — added with `= false` default to fix pre-existing compile error.
- `assembleDebug` and `testDebugUnitTest` both pass (104 tests, 0 failures).

### File List

**New:**
- `shared/src/commonMain/kotlin/com/poddrive/domain/model/BriefingScript.kt`
- `shared/src/commonMain/kotlin/com/poddrive/repository/DriveSessionRepository.kt`
- `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightDriveSessionRepository.kt`
- `shared/src/commonMain/kotlin/com/poddrive/session/DriveSessionManager.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt`
- `shared/src/commonMain/kotlin/com/poddrive/usecase/HandlePlaybackFailureUseCase.kt`
- `shared/src/commonTest/kotlin/com/poddrive/session/DriveSessionManagerTest.kt`
- `shared/src/commonTest/kotlin/com/poddrive/usecase/HandlePlaybackFailureUseCaseTest.kt`

**Modified:**
- `shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionState.kt`
- `shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionEvent.kt`
- `shared/src/commonMain/kotlin/com/poddrive/domain/model/AppError.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeViewModel.kt` (isStale fix)
- `shared/src/commonTest/kotlin/com/poddrive/domain/model/AppErrorTest.kt` (exhaustive when)

---

## Change Log

| Date | Change |
|------|--------|
| 2026-03-25 | Story created — ready for dev (from backlog, story file generated from epics.md) |
| 2026-03-25 | Implementation complete — all tasks done, build and tests pass, status → review |
