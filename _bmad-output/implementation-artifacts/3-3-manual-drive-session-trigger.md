---
status: done
story_id: "3.3"
story_key: 3-3-manual-drive-session-trigger
epic: 3
created: 2026-03-25
---

# Story 3.3 — Manual Drive Session Trigger

## User Story

As a **user**,
I want to manually start a drive session from within the app when Bluetooth auto-detection is unavailable,
so that I can still use PodDrive in vehicles without Bluetooth or when auto-detection fails.

---

## Acceptance Criteria

**AC1 — Manual trigger dispatches ManualTrigger event → Idle → Activating**
**Given** the user is on the home screen and no active drive session exists (state is `Idle`)
**When** they tap the "Start Drive" button (FR13)
**Then** `DriveSessionManager.dispatch(ManualTrigger)` is called
**And** state transitions `Idle → Activating`

**AC2 — DriveSessionForegroundService starts on manual trigger**
**Given** the user taps "Start Drive"
**When** `startManualDriveSession()` is called on `DriveSessionViewModel`
**Then** `DriveSessionForegroundService` is started via `startService()`
**And** the session continues with screen locked (same background behaviour as auto-triggered session)

**AC3 — Drive screen becomes sole root; bottom nav and back suppressed**
**Given** `DriveSessionState` is not `Idle` or `Ended`
**When** state transitions to `Activating` (via manual trigger)
**Then** `MainActivity` replaces `HomeScreen` with `DriveSessionScreen` (existing reactive swap)
**And** the bottom navigation bar is not visible
**And** back gesture is suppressed (UX-DR3)

**AC4 — "Start Drive" button meets ≥64dp drive-mode touch target minimum**
**Given** the "Start Drive" button is rendered in `HomeScreen`
**Then** its minimum touch target height is ≥64dp (UX-DR9)
**And** it is positioned below the idle-state prompt ("Connect your car to start your first session")

---

## Scope

**No new files** — all changes are additive modifications to existing files.

**Modified files:**
| File | Change |
|------|--------|
| `shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionEvent.kt` | Add `object ManualTrigger : DriveSessionEvent()` |
| `shared/src/commonMain/kotlin/com/poddrive/session/DriveSessionManager.kt` | Add `Idle + ManualTrigger → Activating` in `resolveTransition()` |
| `androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt` | Add `startManualDriveSession()` method |
| `androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeScreen.kt` | Add `driveViewModel` param; add "Start Drive" button ≥64dp below idle prompt |
| `androidApp/src/main/kotlin/com/poddrive/android/MainActivity.kt` | Pass `driveViewModel` instance to `HomeScreen` |
| `shared/src/commonTest/kotlin/com/poddrive/session/DriveSessionManagerTest.kt` | Add `Idle + ManualTrigger → Activating` test case |

**No new Gradle dependencies** — all required classes are already in the project.

---

## Tasks / Subtasks

- [x] T1 — Add `ManualTrigger` event to `DriveSessionEvent.kt` (AC1)
  - [x] Add `object ManualTrigger : DriveSessionEvent()` inside the sealed class
  - [x] Add comment referencing Story 3.3 and FR13

- [x] T2 — Add `Idle + ManualTrigger → Activating` transition in `DriveSessionManager.resolveTransition()` (AC1)
  - [x] In the `Idle` state branch of `resolveTransition()`, add `is DriveSessionEvent.ManualTrigger -> DriveSessionState.Activating`
  - [x] Persist-before-emit and mutex handling are already in `transitionTo()` — no extra logic needed
  - [x] The existing `Activating → Briefing` stub auto-transition fires automatically — do NOT add TTS

- [x] T3 — Add `startManualDriveSession()` to `DriveSessionViewModel.kt` (AC1, AC2)
  - [x] Non-suspending: `dispatch()` already uses internal scope; no `viewModelScope.launch` needed
  - [x] Call `app.driveSessionManager.dispatch(DriveSessionEvent.ManualTrigger)`
  - [x] Call `getApplication<Application>().startService(Intent(getApplication(), DriveSessionForegroundService::class.java))`
  - [x] Import `DriveSessionForegroundService` from `com.poddrive.android.service`

- [x] T4 — Update `HomeScreen.kt` to accept `driveViewModel` and render "Start Drive" button (AC1, AC4)
  - [x] Add parameter `driveViewModel: DriveSessionViewModel = viewModel()` to `HomeScreen` composable signature
  - [x] Pass `driveViewModel` down to the inner content composable (e.g., `PodcastLibraryContent`) or use it directly in the `LazyColumn` scope
  - [x] Add a `Button` item in the `LazyColumn`, immediately after the idle-state prompt Text at line 265
  - [x] Button: `onClick = { driveViewModel.startManualDriveSession() }`, `modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp)`, text = "Start Drive"
  - [x] No conditional visibility needed — `HomeScreen` is only shown when `!isDriveActive` (controlled by `MainActivity`)

- [x] T5 — Update `MainActivity.kt` to pass `driveViewModel` to `HomeScreen` (AC3)
  - [x] `driveViewModel` is already instantiated as `private val driveViewModel: DriveSessionViewModel by viewModels()`
  - [x] Update the `HomeScreen(...)` call to: `HomeScreen(onRevoked = { onboardingViewModel.resetToIdle() }, driveViewModel = driveViewModel)`

- [x] T6 — Add `ManualTrigger` unit test to `DriveSessionManagerTest.kt` (AC1)
  - [x] Test: `ManualTrigger dispatched from Idle transitions to Activating`
  - [x] Reuse `FakeDriveSessionRepository`, `TestScope`, `runTest` patterns from existing tests
  - [x] Optionally test: `ManualTrigger dispatched from non-Idle state is ignored` (resolveTransition returns null → no state change)

---

## Dev Notes

### Exact Code Changes

**T1 — DriveSessionEvent.kt**
```kotlin
sealed class DriveSessionEvent {
    object BluetoothConnected : DriveSessionEvent()
    object BluetoothDisconnected : DriveSessionEvent()
    object BriefingConfirmed : DriveSessionEvent()
    object SkipEpisode : DriveSessionEvent()
    object PauseSession : DriveSessionEvent()
    object ResumeSession : DriveSessionEvent()
    /** Story 3.3: Manual fallback trigger — FR13 */
    object ManualTrigger : DriveSessionEvent()
}
```

**T2 — DriveSessionManager.resolveTransition() — Idle branch**

The `Idle` state branch currently handles `BluetoothConnected`. Extend it:
```kotlin
is DriveSessionState.Idle -> when (event) {
    is DriveSessionEvent.BluetoothConnected -> DriveSessionState.Activating  // existing
    is DriveSessionEvent.ManualTrigger -> DriveSessionState.Activating        // Story 3.3
    else -> null
}
```
No other changes to `DriveSessionManager` — persist-before-emit, mutex, and auto-transition to `Briefing` are all already handled.

**T3 — DriveSessionViewModel.kt — new method**
```kotlin
/**
 * Manually starts a drive session — fallback when Bluetooth auto-trigger is unavailable (FR13, Story 3.3).
 * Dispatches [DriveSessionEvent.ManualTrigger] and starts [DriveSessionForegroundService].
 */
fun startManualDriveSession() {
    app.driveSessionManager.dispatch(DriveSessionEvent.ManualTrigger)
    getApplication<Application>().startService(
        Intent(getApplication(), DriveSessionForegroundService::class.java)
    )
}
```
Required import: `import com.poddrive.android.service.DriveSessionForegroundService`

Note: `dispatch()` is non-suspending (it launches internally via `scope`). No `viewModelScope.launch` wrapper needed.

**T4 — HomeScreen.kt — updated signature + button**
```kotlin
@Composable
fun HomeScreen(
    onRevoked: () -> Unit,
    driveViewModel: DriveSessionViewModel = viewModel()  // Story 3.3
) { ... }
```

Inside the `LazyColumn` items, immediately after the idle-state prompt (after line 269 in current file):
```kotlin
// Story 3.3: Manual drive session trigger — FR13, UX-DR9 (≥64dp touch target)
item {
    Button(
        onClick = { driveViewModel.startManualDriveSession() },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("Start Drive")
    }
}
```

No conditional guard needed — `HomeScreen` is only rendered when session is `Idle` or `Ended` (controlled by `MainActivity`).

**T5 — MainActivity.kt — HomeScreen call update**
```kotlin
// Change from:
HomeScreen(onRevoked = { onboardingViewModel.resetToIdle() })
// To:
HomeScreen(
    onRevoked = { onboardingViewModel.resetToIdle() },
    driveViewModel = driveViewModel
)
```
`driveViewModel` is already declared as `private val driveViewModel: DriveSessionViewModel by viewModels()` — no new declaration needed.

**T6 — DriveSessionManagerTest.kt — new test**
```kotlin
@Test
fun `ManualTrigger dispatched from Idle transitions to Activating`() = runTest {
    val manager = DriveSessionManager(FakeDriveSessionRepository(), testScope)
    // Initial state is Idle
    assertEquals(DriveSessionState.Idle, manager.state.value)

    manager.dispatch(DriveSessionEvent.ManualTrigger)
    testScope.advanceUntilIdle()

    // After ManualTrigger, same Activating → Briefing stub as BluetoothConnected
    assertTrue(
        manager.state.value is DriveSessionState.Activating ||
        manager.state.value is DriveSessionState.Briefing
    )
}
```
Adapt exact assertion to match the existing test patterns for `BluetoothConnected` in the same file (the auto-transition to `Briefing` may fire, just as it does for BT trigger).

### Architecture Rules — Must Follow

- **Only `DriveSessionManager` mutates `DriveSessionState`** — `DriveSessionViewModel.startManualDriveSession()` dispatches an event, never sets state directly (AR6)
- **`BluetoothTrigger` must NOT be modified** — manual trigger bypasses it entirely; no coupling between `BluetoothTrigger` and manual trigger (AR4)
- **`Activating → Briefing` auto-transition is already a stub** in `DriveSessionManager` — fires automatically after `ManualTrigger` same as after `BluetoothConnected`; do NOT add TTS logic (Story 3.4, deferred MVP Phase 2)
- **Navigation swap is fully wired** — `MainActivity` already observes `isDriveActive` and swaps screens (Story 3.2); story 3.3 only needs to ensure `driveViewModel` is passed to `HomeScreen` (AC3)
- **`DriveSessionForegroundService` is idempotent** — multiple `startService()` calls are safe (Story 3.2 pattern)

### Scope Boundaries — Do NOT Implement in 3.3

- ❌ Full Drive Mode UI (`DriveSessionCard`, `EpisodeTile`, `BriefingAnimation`, stats strip) → Story 3.6
- ❌ TTS briefing content or `GetEpisodeBriefingUseCase` → Story 3.4 (deferred MVP Phase 2)
- ❌ "End Session" / "Long-press End Session" button → Story 3.6
- ❌ Drive mode colour scheme (#0F0F12 background etc.) → Story 3.6
- ❌ Bluetooth device type filtering or per-session user confirmation → post-MVP
- ❌ iOS manual trigger implementation → Android-first MVP

### Previous Story Learnings (from 3.2)

- `dispatch()` in `DriveSessionManager` is already protected by `Mutex + scope.launch` — safe to call from any thread without wrapping in a coroutine at the call site
- `DriveSessionForegroundService` tracks its own `collectJob`; repeated `startService()` calls are handled without duplication
- `DriveSessionViewModel` is an `AndroidViewModel` — use `getApplication<Application>()` for context-dependent calls like `startService()`
- **Same instance rule:** `MainActivity` already instantiates `driveViewModel` via `by viewModels()`; pass this same instance to `HomeScreen` to avoid two ViewModels observing the same state
- Test pattern: `FakeDriveSessionRepository`, `TestScope`, `runTest` — see `DriveSessionManagerTest.kt` at `poddrive/shared/src/commonTest/kotlin/com/poddrive/session/DriveSessionManagerTest.kt`
- Gradle build/test commands:
  ```bash
  JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
  ./gradlew assembleDebug --max-workers 1

  JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
  TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
  ./gradlew testDebugUnitTest --max-workers 1
  ```

### Project Structure Notes

- `DriveSessionEvent.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/domain/model/`
- `DriveSessionManager.kt`: `poddrive/shared/src/commonMain/kotlin/com/poddrive/session/`
- `DriveSessionViewModel.kt`: `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/`
- `HomeScreen.kt`: `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/`
- `MainActivity.kt`: `poddrive/androidApp/src/main/kotlin/com/poddrive/android/`
- `DriveSessionManagerTest.kt`: `poddrive/shared/src/commonTest/kotlin/com/poddrive/session/`

### References

- [Source: epics.md#Story 3.3] — user story, AC, FR13 (manual trigger), UX-DR3 (nav suppression), UX-DR9 (64dp touch targets)
- [Source: architecture.md#Drive Session State Machine] — `DriveSessionState` and `DriveSessionEvent` sealed classes; persist-before-emit rule (AR6)
- [Source: architecture.md#Project Structure] — KMP package paths, expect/actual pattern (AR4)
- [Source: ux-design-specification.md#Navigation Patterns] — UX-DR3: back gesture and bottom nav suppressed during active drive session
- [Source: ux-design-specification.md#Touch Targets] — UX-DR9: ≥64dp minimum for drive-mode targets
- [Source: DriveSessionViewModel.kt] — comment "Story 3.3 (manual trigger)" already present; `dispatch()` signature to extend
- [Source: DriveSessionManager.kt] — `resolveTransition()` Idle branch and `Activating → Briefing` stub auto-transition
- [Source: story 3-2-bluetooth-audio-device-detection-and-auto-session-activation.md#Dev Notes] — `PodDriveApplication` wiring pattern, `startService()` usage, test patterns

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- T1: Added `object ManualTrigger : DriveSessionEvent()` with FR13 comment to `DriveSessionEvent.kt`
- T2: Extended `Idle` branch in `DriveSessionManager.resolveTransition()` to handle `ManualTrigger → Activating`; existing persist-before-emit, mutex, and `Activating → Briefing` auto-transition wiring unchanged
- T3: Added `startManualDriveSession()` to `DriveSessionViewModel.kt` with imports for `Intent` and `DriveSessionForegroundService`
- T4: Added `driveViewModel: DriveSessionViewModel = viewModel()` param to `HomeScreen`; threaded through `ShowsTabContent` → `PodcastList`; added "Start Drive" `Button` item in `LazyColumn` with `heightIn(min = 64.dp)` (AC4/UX-DR9); added `Button` and `DriveSessionViewModel` imports
- T5: Updated `MainActivity.kt` `HomeScreen(...)` call to pass `driveViewModel = driveViewModel` (same instance already declared via `by viewModels()`)
- T6: Updated event count test from 6 → 7 (with `ManualTrigger` in list); added `ManualTrigger dispatched from Idle transitions to Activating` and `ManualTrigger dispatched from non-Idle state is ignored` tests
- Build: `assembleDebug BUILD SUCCESSFUL` — no compile errors
- Tests: `DriveSessionManagerTest` 20 tests, 0 failures, 0 errors (includes both new ManualTrigger tests)

### File List

- `poddrive/shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionEvent.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/session/DriveSessionManager.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeScreen.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/MainActivity.kt`
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/session/DriveSessionManagerTest.kt`

---

## Change Log

| Date | Change |
|------|--------|
| 2026-03-25 | Story created — ready for dev |
| 2026-03-25 | Story implemented — ManualTrigger event, state machine transition, startManualDriveSession() ViewModel method, Start Drive button (≥64dp), driveViewModel threading, 2 new unit tests (20 total, 0 failures) |
| 2026-03-25 | Code review complete — 1 patch applied (F01: StalenessBanner hardcoded color → MaterialTheme.colorScheme.onSurfaceVariant); 12 deferred (pre-existing/stubs 3.4+); 16 rejected noise. Story → done |
