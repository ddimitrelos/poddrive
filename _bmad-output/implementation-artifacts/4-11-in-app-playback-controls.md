# Story 4.11: In-App Playback Controls

Status: done

## Story

As a driver using PodDrive,
I want to see playback controls (pause, resume, progress) directly on the drive dashboard
while an episode plays in-app,
so that I can manage playback without ever leaving the PodDrive app.

## Background & Motivation

Story 4.10 wires ExoPlayer to play episodes in-app. Once an episode is playing, the drive
dashboard must reflect that state: show a progress bar, expose a pause/resume control, and
allow skipping to the next episode — all without switching context to any other app.

This story also ensures **media button integration**: hardware play/pause buttons (steering
wheel controls, headset buttons) work correctly while PodDrive is playing in-app.

---

## Acceptance Criteria

1. **AC1 — Playback progress visible:** While an episode is playing in-app
   (`DriveSessionState.Playing` with `audioUrl != null`), the drive dashboard shows:
   - Episode title and podcast name
   - A linear progress bar showing `currentPositionMs / episode.durationMs`
   - Elapsed time and remaining time labels (formatted as `mm:ss`)
   - Progress updates every 1 second

2. **AC2 — Pause / Resume control:** A single tap on the pause button calls
   `viewModel.onPausePlayback()` → `AudioPlayer.pause()` → `DriveSessionManager` transitions
   to `DriveSessionState.Paused`. A tap on resume calls `viewModel.onResumePlayback()` →
   `AudioPlayer.resume()` → back to `DriveSessionState.Playing`.

3. **AC3 — Skip control:** A tap on the skip button advances to the next episode in the queue.
   Calls `viewModel.onSkipEpisode()` → dispatches `DriveSessionEvent.SkipEpisode` (already
   defined). The next episode starts playing via `AudioPlayer.play()` if it has an `audio_url`.

4. **AC4 — MediaSession integration:** PodDrive registers a `MediaSession` (Media3
   `MediaSessionService` or `MediaSession` directly) so that:
   - System media notification shows episode title, podcast name, and play/pause action
   - Hardware media buttons (steering wheel, headset) trigger play/pause/skip
   - Lock screen media controls are visible

5. **AC5 — Controls hidden for Spotify fallback:** When `audio_url` is null and the episode
   was handed off to Spotify (Story 4.1), the drive dashboard shows the existing
   `HANDING_OFF` → Spotify state UI. The new in-app playback UI is shown only when PodDrive
   owns the audio (i.e. ExoPlayer is playing).

6. **AC6 — Playback position persisted on pause:** When the user pauses, the current
   ExoPlayer position is written to `playback_state` in SQLDelight immediately (in addition
   to the 5-second periodic save from Story 4.10).

7. **AC7 — Unit / UI test coverage:**
   - `DriveSessionViewModelTest`: `onPausePlayback()` calls `audioPlayer.pause()` and dispatches
     `PauseSession`; `onResumePlayback()` calls `audioPlayer.resume()` and dispatches `ResumeSession`
   - `DriveSessionScreenTest` (Compose UI test): progress bar renders at correct fraction for
     a given `positionMs / durationMs`; pause button is shown in `Playing` state; resume button
     is shown in `Paused` state

---

## Tasks / Subtasks

- [ ] **Task 1 — `DriveUiState` playback fields** (AC1, AC5)
  - [ ] 1.1 Add to `DriveUiState`:
        ```kotlin
        val isPlayingInApp: Boolean = false
        val playbackPositionMs: Long = 0L
        val playbackDurationMs: Long = 0L
        val playbackEpisodeTitle: String = ""
        val playbackPodcastName: String = ""
        ```
  - [ ] 1.2 In `DriveSessionViewModel`, launch a 1-second polling coroutine in
        `DriveSessionState.Playing` scope that reads `audioPlayer.currentPositionMs()` and
        updates `_uiState`:
        ```kotlin
        private fun startPositionPolling(episode: Episode) {
            positionPollingJob?.cancel()
            positionPollingJob = viewModelScope.launch {
                while (isActive) {
                    _uiState.update { it.copy(
                        playbackPositionMs = podApp.audioPlayer.currentPositionMs(),
                        isPlayingInApp = podApp.audioPlayer.isPlaying()
                    )}
                    delay(1_000L)
                }
            }
        }
        ```
  - [ ] 1.3 Cancel `positionPollingJob` when session transitions to `Paused`, `Error`, or `Ended`

- [ ] **Task 2 — Pause / Resume / Skip in ViewModel** (AC2, AC3, AC6)
  - [ ] 2.1 Add `onPausePlayback()`:
        - `podApp.audioPlayer.pause()`
        - Write position to `playback_state` immediately (AC6)
        - `podApp.driveSessionManager.dispatch(DriveSessionEvent.PauseSession)`
  - [ ] 2.2 Add `onResumePlayback()`:
        - `podApp.audioPlayer.resume()`
        - `podApp.driveSessionManager.dispatch(DriveSessionEvent.ResumeSession)`
  - [ ] 2.3 `onSkipEpisode()` already dispatches `DriveSessionEvent.SkipEpisode` — verify
        that `DriveSessionManager` reacts by loading the next episode and calling
        `onEpisodeTapped(nextEpisode)` (wire if not already done in Story 4.10)

- [ ] **Task 3 — Drive Dashboard UI** (AC1, AC2, AC3, AC5)
  - [ ] 3.1 In `DriveSessionScreen.kt`, add an `InAppPlaybackControls` composable that
        renders when `uiState.isPlayingInApp || sessionState is DriveSessionState.Paused`:
        ```
        [Episode Title]
        [Podcast Name]
        [===Progress Bar====] 12:34 / 45:00
        [Pause/Resume]  [Skip]
        ```
  - [ ] 3.2 Progress bar: `LinearProgressIndicator(progress = positionMs / durationMs.toFloat())`
  - [ ] 3.3 Elapsed / remaining labels: format `positionMs` and `(durationMs - positionMs)`
        using a `formatDuration(ms: Long): String` helper (`%02d:%02d`.format(min, sec))
  - [ ] 3.4 Pause/Resume button: show pause icon when `isPlayingInApp == true`; show play
        icon when `sessionState is Paused`
  - [ ] 3.5 Ensure `InAppPlaybackControls` is NOT shown when `uiState.isHandingOff` is true
        (Spotify fallback path — AC5)

- [ ] **Task 4 — MediaSession integration** (AC4)
  - [ ] 4.1 In `DriveSessionForegroundService`, create a `MediaSession` using Media3:
        ```kotlin
        private lateinit var mediaSession: MediaSession
        // in onCreate():
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        ```
  - [ ] 4.2 Set `MediaMetadata` on `MediaSession` when episode changes (title, artist = podcast name)
  - [ ] 4.3 Handle `MediaSession.Callback.onPause()` / `onPlay()` / `onSkipToNext()` by
        delegating to `podApp.driveSessionManager` events — same as tapping in the UI
  - [ ] 4.4 Release `mediaSession` in `DriveSessionForegroundService.onDestroy()`

- [ ] **Task 5 — Tests** (AC7)
  - [ ] 5.1 Update `DriveSessionViewModelTest.kt`:
        - `onPausePlayback calls audioPlayer.pause and dispatches PauseSession`
        - `onResumePlayback calls audioPlayer.resume and dispatches ResumeSession`
  - [ ] 5.2 Create `androidApp/src/androidTest/kotlin/com/poddrive/android/ui/drive/DriveSessionScreenTest.kt`
        (Compose UI test with `createComposeRule()`):
        - `shows progress bar at correct fraction in Playing state`
        - `shows pause button when isPlayingInApp is true`
        - `shows resume button when sessionState is Paused`
        - `does not show in-app controls when isHandingOff is true`

---

## Dev Notes

### Architecture Rules (MUST follow)

1. `positionPollingJob` lives in `DriveSessionViewModel` — not in the service or in
   `commonMain`. It is UI-local state (same category as `isHandingOff`).
2. `MediaSession` is created and owned by `DriveSessionForegroundService`, not the ViewModel.
   MediaSession callback → DriveSessionManager event is the only coupling path.
3. `formatDuration()` is a pure Kotlin function — place it in a `ui/util/` file in `androidApp`,
   not in `commonMain` (it's a display concern).
4. `DriveSessionEvent.PauseSession` and `ResumeSession` already exist in the sealed class —
   do not add new variants. Verify they are handled in `DriveSessionManager` (they should
   be from the architecture definition).
5. All `when` expressions on `DriveSessionEvent` must be exhaustive — compiler will enforce
   this for the sealed class.

### Progress bar formatting example

```kotlin
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
```

### Key Files to Touch

| File | Change |
|---|---|
| `androidApp/.../ui/drive/DriveSessionViewModel.kt` | Add position polling; `onPausePlayback()`; `onResumePlayback()` |
| `androidApp/.../ui/drive/DriveSessionScreen.kt` | Add `InAppPlaybackControls` composable |
| `androidApp/.../service/DriveSessionForegroundService.kt` | Add `MediaSession` lifecycle |
| `androidApp/.../ui/drive/DriveSessionViewModelTest.kt` | Add pause/resume tests |
| `androidApp/.../ui/drive/DriveSessionScreenTest.kt` | **NEW** — Compose UI tests |

### References

- [ADR-002] `_bmad-output/planning-artifacts/architecture.md`
- [Story 4.10] `4-10-exoplayer-integration.md` — ExoPlayer + AudioPlayer wiring this story builds on
- [Story 4.1] `4-1-spotify-episode-deep-link-handoff.md` — fallback UI path (unchanged; AC5)
- [UX spec] `_bmad-output/planning-artifacts/ux-design-specification.md` — drive dashboard design
