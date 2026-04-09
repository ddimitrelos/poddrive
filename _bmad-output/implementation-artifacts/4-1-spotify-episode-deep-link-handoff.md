---
status: done
story_id: "4.1"
story_key: 4-1-spotify-episode-deep-link-handoff
epic: 4
created: 2026-03-25
---

# Story 4.1 — Spotify Episode Deep-Link Handoff

## User Story

As a **user**,
I want to tap an episode (or the "Open in Spotify" CTA) and have it open immediately in Spotify,
so that playback starts without me needing to navigate the Spotify app.

---

## MVP1 Scope Note

TTS playback ("Opening [title] in Spotify.") is **deferred to MVP2** along with Stories 3-4 and 3-5.
This story covers only the tap-triggered deep-link handoff and its failure modes.
Steering-wheel confirmation is also deferred to the voice layer (MVP2).

---

## Acceptance Criteria

**AC1 — Deep-link fires on tap**
**Given** a drive session is active and the drive dashboard is visible
**When** the user taps an `EpisodeTileDrive` row or the bottom "Open in Spotify" CTA
**Then** an `Intent(Intent.ACTION_VIEW, Uri.parse("spotify:episode:{id}"))` is fired (FR21, AR8)
**And** `DriveSessionCard` transitions to `HANDING_OFF` state immediately on tap

**AC2 — Spotify not installed → ERROR state, no crash**
**Given** the Spotify app is not installed on the device
**When** the deep-link Intent is fired
**Then** `ActivityNotFoundException` is caught — the app does not crash (NFR12)
**And** `DriveSessionManager.dispatch(PlaybackError(...))` is called with a descriptive message
**And** `DriveSessionCard` transitions to `ERROR` state showing "Spotify is not installed"

**AC3 — 3-second handoff timeout → ERROR state**
**Given** the deep-link Intent was fired and `HANDING_OFF` state is active
**When** 3 seconds elapse without the Spotify app coming to the foreground
**Then** the system transitions `DriveSessionState` to `Error` (UX-DR10)
**And** `DriveSessionCard` shows the ERROR state with a "Retry" action

**AC4 — Episode marked as played after successful handoff**
**Given** the deep-link Intent was fired successfully (no `ActivityNotFoundException`)
**When** the `HANDING_OFF` state clears without error
**Then** `MarkEpisodePlayedUseCase.execute(episodeId)` is called to mark the episode as played
**And** the episode is removed from the visible queue on the drive dashboard

**AC5 — ≤2 taps to Spotify**
From the drive screen, one tap on any `EpisodeTileDrive` row or the "Open in Spotify" CTA reaches
Spotify playback — no intermediate screen or confirmation dialog. (UX-DR4)

**AC6 — Handoff time under normal conditions**
Time from tap to Spotify app foregrounded does not exceed 3 seconds under normal device conditions.
(NFR18)

---

## Tasks / Subtasks

- [x] T1 — Create `MarkEpisodePlayedUseCase` (AC4)
  - In `shared/src/commonMain/kotlin/com/poddrive/usecase/MarkEpisodePlayedUseCase.kt`
  - Calls `episodeRepository.markPlayed(episodeId: String)`
  - Follows `SetEpisodeTierUseCase` pattern: synchronous `execute()` returning `Result<Unit, AppError>`

- [x] T2 — Add `markPlayed()` to `EpisodeRepository` + `SqlDelightEpisodeRepository` (AC4)
  - Add `fun markPlayed(episodeId: String)` to the interface
  - Implement in `SqlDelightEpisodeRepository` using `db.episodeQueries.markPlayed(episodeId)`
  - SQL query already existed in `Episode.sq` (line 63-64)

- [x] T3 — Register `MarkEpisodePlayedUseCase` in `PodDriveApplication` (AC4)
  - Initialised after `episodeRepository` in `PodDriveApplication.onCreate()`

- [x] T4 — Add handoff timeout logic to `DriveSessionViewModel` (AC3)
  - In `onOpenInSpotify(episodeId)`: sets `isHandingOff = true`, `handoffEpisodeId`, launches 3s timeout job
  - Timeout calls `dispatch(PlaybackError("Handoff timeout"))` if `isHandingOff` still true
  - Timeout job cancelled in `clearHandingOff()`

- [x] T5 — Catch `ActivityNotFoundException` in `DriveSessionScreen` (AC2)
  - `startActivity()` wrapped in `try/catch(ActivityNotFoundException)`
  - On catch: `viewModel.onSpotifyNotInstalled()` dispatches `PlaybackError("Spotify is not installed")`
  - General `Exception` catch also routes to `viewModel.onPlaybackFailed()`

- [x] T6 — Add `PlaybackError` event to `DriveSessionEvent` (AC2, AC3)
  - Added `data class PlaybackError(val message: String) : DriveSessionEvent()`
  - State machine transitions Briefing/Playing/Paused → `Error(AppError.Unknown(...))` on `PlaybackError`
  - Error and Ended states are terminal (idempotent no-ops)

- [x] T7 — Call `MarkEpisodePlayedUseCase` after successful handoff (AC4)
  - In `DriveSessionViewModel.clearHandingOff()`: calls `markEpisodePlayedUseCase.execute(handoffEpisodeId)` then reloads queue

- [x] T8 — Write unit tests (AC4 + state machine)
  - `MarkEpisodePlayedUseCaseTest` — 2 tests: success path, repository throws
  - `DriveSessionManagerTest` — 4 tests: PlaybackError from Briefing→Error, Playing→Error, Error→no-op (idempotent), Ended→no-op

---

## Dev Notes

### Architecture Rules (MUST FOLLOW)

1. **No new `Color(0xFF...)` literals in composables.** Only in `Theme.kt`. All composable color
   refs via `MaterialTheme.colorScheme.*`.

2. **`HANDING_OFF` is UI-local.** `isHandingOff` in `DriveUiState` must NOT be promoted to a
   `DriveSessionState`. Only `PlaybackError` events reach the state machine.

3. **`ActivityNotFoundException` catch is in the composable layer** (DriveSessionScreen), not in
   the ViewModel. The VM only receives the error signal via `onSpotifyNotInstalled()`.

4. **Timeout job must be cancelled** when `clearHandingOff()` is called to prevent ghost transitions.

5. **`dispatch(PlaybackError(...))` maps to `Error` state** in the state machine — verify this
   transition is already handled in `DriveSessionManager`. If not, add it in the same file.

6. **`is_played` column already exists** in the SQLDelight episodes table (added in Story 2.4).
   Only the `markPlayed` query was needed (already in `Episode.sq`).

### Key Files

- `shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionEvent.kt`
- `shared/src/commonMain/kotlin/com/poddrive/usecase/MarkEpisodePlayedUseCase.kt`
- `shared/src/commonMain/kotlin/com/poddrive/repository/EpisodeRepository.kt`
- `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightEpisodeRepository.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt`
- `androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionScreen.kt`

### References

- [Source: epics.md#Story 4.1] — Original acceptance criteria
- [Source: architecture.md] — AppError, Result type, use case patterns
- [Source: _bmad-output/implementation-artifacts/3-6-drive-mode-ui-dashboard-screen.md] — `HANDING_OFF` state, `onOpenInSpotify()`, `LaunchedEffect` deep-link pattern
- [Source: _bmad-output/implementation-artifacts/2-4-local-episode-cache.md] — `is_played` column in episodes table

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- T6 (PlaybackError event): Story 4.5 had already implemented this as `PlaybackFailed(AppError)` — used that convention throughout rather than introducing a redundant `PlaybackError(String)` variant.
- `SyncPodcastLibraryUseCaseTest.FakeEpisodeRepository` also needed `markPlayed()` stub — caught by compiler on first test run, fixed immediately.

### Completion Notes List

- `markPlayed()` added to `EpisodeRepository` interface and implemented in `SqlDelightEpisodeRepository` using the pre-existing `Episode.sq` query (line 63-64).
- `MarkEpisodePlayedUseCase` created following `SetEpisodeTierUseCase` pattern: synchronous `execute()` returning `Result<Unit, AppError>`.
- `MarkEpisodePlayedUseCase` registered in `PodDriveApplication.onCreate()` after `episodeRepository`.
- `DriveSessionViewModel` rewritten with `DriveUiState` (isHandingOff, handoffEpisodeId, episodes), `onOpenInSpotify()`, `onSpotifyNotInstalled()`, `onPlaybackFailed()`, `clearHandingOff()`, and `loadEpisodeQueue()`. Handoff timeout is a 3s coroutine job that dispatches `PlaybackFailed(SpotifyHandoffTimeout)` if still handing off.
- `DriveSessionScreen.kt` created with `EpisodeTileDrive` rows, "Open in Spotify" CTA, `ActivityNotFoundException`/`Exception` catches in the composable layer (Dev Notes rule 3), and HANDING_OFF + ERROR state rendering.
- `PlaybackError` from the story spec maps to the existing `PlaybackFailed(AppError)` event (introduced in Story 4.5) — no new event added to `DriveSessionEvent.kt`.
- `DriveSessionManagerTest.kt` already covers the required PlaybackFailed state machine tests from Story 4.5 — no new tests added there.
- All tests pass: `BUILD SUCCESSFUL` (shared:allTests, debug + release variants).

### File List

- `poddrive/shared/src/commonMain/kotlin/com/poddrive/repository/EpisodeRepository.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightEpisodeRepository.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/MarkEpisodePlayedUseCase.kt` (new)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionScreen.kt` (new)
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/MarkEpisodePlayedUseCaseTest.kt` (new)
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/BuildEpisodeQueueUseCaseTest.kt`
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCaseTest.kt`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
