# Story 4.10: ExoPlayer Integration — In-App Audio Playback Engine

Status: done

## Story

As a PodDrive user,
I want episodes with a resolved audio URL to play directly inside the app,
so that I hear them immediately after a single tap, with no Spotify app involvement.

## Background & Motivation

Stories 4.8 and 4.9 resolve and store a direct audio URL (`audio_url`) for each episode.
This story wires that URL into ExoPlayer (Media3), replacing the Spotify deep-link handoff
(Story 4.1) as the primary playback mechanism for free-tier users.

**Two-layer playback strategy (ADR-002):**
- `audio_url` present → ExoPlayer plays in-app (this story)
- `audio_url` null → existing deep-link to Spotify (Story 4.1 unchanged, becomes fallback)

**ExoPlayer in `DriveSessionForegroundService`:** The `DriveSessionForegroundService`
already owns background audio execution. ExoPlayer is added here so it participates in
Android audio focus, MediaSession, and foreground notification — all of which `ForegroundService`
already manages.

**`expect/actual AudioPlayer` interface:** `DriveSessionManager` (commonMain) orchestrates
session state. It must not import ExoPlayer (Android-only). A thin `expect/actual AudioPlayer`
interface bridges commonMain ↔ androidMain.

---

## Acceptance Criteria

1. **AC1 — Single-tap in-app playback:** When the user taps an episode tile on the drive
   dashboard and the episode has a non-null `audio_url`, the episode begins playing via
   ExoPlayer within the app. No Spotify app is opened.

2. **AC2 — Fallback preserved:** When `audio_url` is null, the existing deep-link path
   (Story 4.1) fires exactly as before. No behaviour regression for Spotify-exclusive content.

3. **AC3 — `expect/actual AudioPlayer` interface:** `commonMain` defines:
   ```kotlin
   expect class AudioPlayer {
       fun play(url: String, resumePositionMs: Long = 0)
       fun pause()
       fun resume()
       fun stop()
       fun currentPositionMs(): Long
       fun isPlaying(): Boolean
   }
   ```
   `androidMain` provides `actual class AudioPlayer` backed by `androidx.media3:media3-exoplayer`.
   iOS `actual` is a stub (post-MVP).

4. **AC4 — Audio focus:** ExoPlayer requests Android audio focus on `play()` and releases it
   on `stop()` / `pause()`. Other apps duck or pause when PodDrive plays. Follows
   `AudioManager.AUDIOFOCUS_GAIN` / `AUDIOFOCUS_LOSS` handling pattern.

5. **AC5 — Background playback:** Playback continues when the user locks the screen or
   switches apps. `DriveSessionForegroundService` holds the WakeLock and MediaSession
   notification. ExoPlayer is created and held inside the service.

6. **AC6 — Resume position:** ExoPlayer starts at `episode.resumePositionMs` (from
   SQLDelight `playback_state` table, already populated by Story 4.4 — if that story is not
   yet done, default to 0). Position is written to `playback_state` every 5 seconds while
   playing.

7. **AC7 — `DriveSessionState.Playing` updated:** When ExoPlayer starts, `DriveSessionManager`
   transitions to `DriveSessionState.Playing(episode, positionMs)`. `positionMs` is read from
   ExoPlayer, not from Spotify. The `Playing` state data class is unchanged.

8. **AC8 — Unit / integration test coverage:**
   - `AudioPlayerTest` (androidTest with Robolectric): `play()` creates ExoPlayer and calls
     `prepare()`; `pause()` / `resume()` delegate correctly; `stop()` releases player
   - `DriveSessionViewModelTest`: when episode has `audio_url`, `onEpisodeTapped()` calls
     `audioPlayer.play()` and does NOT fire Spotify Intent; when `audio_url` is null,
     Spotify Intent fires (regression test for AC2)

---

## Tasks / Subtasks

- [ ] **Task 1 — Add ExoPlayer dependency** (AC1, AC5)
  - [ ] 1.1 In `androidApp/build.gradle.kts`, add:
        ```kotlin
        implementation(libs.androidx.media3.exoplayer)
        implementation(libs.androidx.media3.session)
        ```
  - [ ] 1.2 Add to `gradle/libs.versions.toml`:
        ```toml
        [versions]
        media3 = "1.3.1"

        [libraries]
        androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
        androidx-media3-session   = { group = "androidx.media3", name = "media3-session",   version.ref = "media3" }
        ```

- [ ] **Task 2 — `expect/actual AudioPlayer`** (AC3, AC4, AC5, AC8)
  - [ ] 2.1 Create `shared/src/commonMain/kotlin/com/poddrive/audio/AudioPlayer.kt`
        (expect class with interface as defined in AC3)
  - [ ] 2.2 Create `shared/src/androidMain/kotlin/com/poddrive/audio/AudioPlayer.kt`
        (`actual class AudioPlayer(private val context: Context)` backed by `ExoPlayer`):
        - `play(url, resumePositionMs)`: builds `MediaItem.fromUri(url)`, calls
          `exoPlayer.setMediaItem()`, `seekTo(resumePositionMs)`, `prepare()`, `play()`
        - `pause()` / `resume()` → `exoPlayer.pause()` / `exoPlayer.play()`
        - `stop()` → `exoPlayer.stop()` + `exoPlayer.release()`
        - `currentPositionMs()` → `exoPlayer.currentPosition`
        - `isPlaying()` → `exoPlayer.isPlaying`
        - Audio focus: use `AudioFocusRequest` (API 26+) requesting `AUDIOFOCUS_GAIN`;
          on `AUDIOFOCUS_LOSS` call `pause()`; on `AUDIOFOCUS_LOSS_TRANSIENT` call `pause()`
  - [ ] 2.3 Create `shared/src/iosMain/kotlin/com/poddrive/audio/AudioPlayer.kt`
        — stub `actual class` with no-op methods, `currentPositionMs()` returns 0,
        `isPlaying()` returns false

- [ ] **Task 3 — Wire into `DriveSessionForegroundService`** (AC4, AC5, AC6)
  - [ ] 3.1 Instantiate `AudioPlayer(applicationContext)` in `DriveSessionForegroundService.onCreate()`
  - [ ] 3.2 Expose `audioPlayer` to `DriveSessionViewModel` via `PodDriveApplication`
        (add `val audioPlayer: AudioPlayer by lazy { ... }`)
  - [ ] 3.3 Add position-saving loop: launch a coroutine in the service that calls
        `podApp.driveSessionRepository.savePlaybackPosition(episodeId, audioPlayer.currentPositionMs())`
        every 5 seconds while `audioPlayer.isPlaying()` is true (AC6)

- [ ] **Task 4 — Update `DriveSessionViewModel.onEpisodeTapped()`** (AC1, AC2, AC7)
  - [ ] 4.1 Rename `onOpenInSpotify(episodeId)` to `onEpisodeTapped(episode: Episode)` or
        keep the old name and add a routing layer — developer discretion, keep the diff minimal
  - [ ] 4.2 Add routing:
        ```kotlin
        fun onEpisodeTapped(episode: Episode) {
            if (episode.audioUrl != null) {
                // AC1 — in-app path
                podApp.audioPlayer.play(episode.audioUrl, episode.resumePositionMs)
                podApp.driveSessionManager.dispatch(
                    DriveSessionEvent.EpisodeStarted(episode)
                )
            } else {
                // AC2 — fallback: existing deep-link path
                onOpenInSpotify(episode.id)
            }
        }
        ```
  - [ ] 4.3 Add `DriveSessionEvent.EpisodeStarted(episode: Episode)` to `DriveSessionEvent`
        sealed class; handle in `DriveSessionManager` to transition to
        `DriveSessionState.Playing(episode, positionMs = 0)`
  - [ ] 4.4 Update `DriveSessionScreen.kt` composable: episode tile `onClick` calls
        `viewModel.onEpisodeTapped(episode)` instead of `viewModel.onOpenInSpotify(episode.id)`

- [ ] **Task 5 — Unit tests** (AC8)
  - [ ] 5.1 Create `androidApp/src/test/kotlin/com/poddrive/android/audio/AudioPlayerTest.kt`
        (Robolectric):
        - `play() prepares and starts ExoPlayer`
        - `pause() delegates to ExoPlayer.pause`
        - `stop() releases ExoPlayer`
  - [ ] 5.2 Update `DriveSessionViewModelTest.kt`:
        - `onEpisodeTapped with audio_url calls audioPlayer.play and does not launch Spotify intent`
        - `onEpisodeTapped without audio_url launches Spotify intent (regression AC2)`

---

## Dev Notes

### Architecture Rules (MUST follow)

1. `AudioPlayer` expect/actual: `commonMain` declares only the interface. ExoPlayer imports
   are exclusively in `androidMain`. Never import `androidx.media3` in `commonMain`.
2. `AudioPlayer` instance is owned by `DriveSessionForegroundService` — not by ViewModel.
   ViewModel accesses it via `PodDriveApplication.audioPlayer`.
3. `DriveSessionEvent.EpisodeStarted` is a new event — add it to `DriveSessionEvent.kt` and
   handle ALL `when` expressions that branch on `DriveSessionEvent`. The architecture rule
   "no unhandled `when` arms" is enforced by making `DriveSessionEvent` sealed.
4. Do NOT remove or modify the `onOpenInSpotify()` flow — it remains the fallback path (AC2).
5. `DriveSessionState.Playing` data class is unchanged — `positionMs` now comes from
   ExoPlayer instead of Spotify's playback state.

### ExoPlayer simple usage pattern (for reference)

```kotlin
// androidMain
actual class AudioPlayer(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    actual fun play(url: String, resumePositionMs: Long) {
        requestAudioFocus()
        val player = ExoPlayer.Builder(context).build().also { exoPlayer = it }
        player.setMediaItem(MediaItem.fromUri(url))
        player.seekTo(resumePositionMs)
        player.prepare()
        player.play()
    }

    actual fun stop() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        abandonAudioFocus()
    }
    // ... pause, resume, currentPositionMs, isPlaying
}
```

### Key Files to Touch

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add media3 version + library entries |
| `androidApp/build.gradle.kts` | Add media3 dependencies |
| `shared/.../audio/AudioPlayer.kt` (commonMain) | **NEW** — expect class |
| `shared/.../audio/AudioPlayer.kt` (androidMain) | **NEW** — ExoPlayer actual |
| `shared/.../audio/AudioPlayer.kt` (iosMain) | **NEW** — stub actual |
| `shared/.../domain/model/DriveSessionEvent.kt` | Add `EpisodeStarted(episode)` |
| `shared/.../session/DriveSessionManager.kt` | Handle `EpisodeStarted` → `Playing` |
| `androidApp/.../service/DriveSessionForegroundService.kt` | Instantiate AudioPlayer; position loop |
| `androidApp/.../PodDriveApplication.kt` | Expose `audioPlayer` |
| `androidApp/.../ui/drive/DriveSessionViewModel.kt` | Add `onEpisodeTapped()`; routing logic |
| `androidApp/.../ui/drive/DriveSessionScreen.kt` | Update onClick to `onEpisodeTapped` |
| `androidApp/.../audio/AudioPlayerTest.kt` | **NEW** |
| `androidApp/.../ui/drive/DriveSessionViewModelTest.kt` | Update + add routing tests |

### References

- [ADR-002] `_bmad-output/planning-artifacts/architecture.md`
- [Story 4.9] `4-9-episode-audio-url-resolution.md` — produces `audio_url` consumed here
- [Story 4.11] `4-11-in-app-playback-controls.md` — builds UI controls on top of this story
- [Story 4.1] `4-1-spotify-episode-deep-link-handoff.md` — fallback path (unchanged)
