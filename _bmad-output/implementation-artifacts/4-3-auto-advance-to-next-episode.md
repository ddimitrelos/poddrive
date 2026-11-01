---
status: ready-for-dev
story_id: "4.3"
story_key: 4-3-auto-advance-to-next-episode
epic: 4
created: 2026-04-17
---

# Story 4.3 — Auto-Advance to Next Episode

## User Story

As a **user**,
I want the next episode to start automatically when the current one ends,
so that my drive session continues seamlessly without any interaction.

---

## Acceptance Criteria

**AC1 — Auto-advance on episode completion**
**Given** an episode is playing in-app via ExoPlayer
**When** the episode ends naturally (ExoPlayer reaches `STATE_ENDED`)
**Then** the system marks the finished episode as played
**And** automatically starts the next unheard episode from the queue without any user action (FR23)
**And** the `EpisodeTile` list updates to reflect the new current episode

**AC2 — Queue tile updates correctly after auto-advance**
**Given** auto-advance has just started the next episode
**When** the drive dashboard is visible
**Then** the previously finished episode is no longer in the queue
**And** the newly playing episode is highlighted / at the top

**AC3 — Graceful session end when queue is exhausted**
**Given** the last episode in the queue has just finished
**When** `BuildEpisodeQueueUseCase` returns an empty queue
**Then** `DriveSessionEvent.QueueExhausted` is dispatched
**And** the session transitions `Activating → DriveSessionState.Ended`
**And** the drive screen shows the session-ended view (no crash, no infinite loop)
**Note:** TTS voice notification on queue exhaustion is deferred to Story 3.4 (MVP Phase 2).

**AC4 — Stability during a full drive session**
**Given** a queue of ≥ 2 episodes plays through
**When** episodes auto-advance end-to-end
**Then** the app does not crash or deadlock (NFR9)
**And** the UI remains responsive throughout

---

## Scope

**Modified files only — no new files required:**

| File | Change |
|------|--------|
| `shared/src/commonMain/kotlin/com/poddrive/audio/AudioPlayer.kt` | Add `setOnPlaybackComplete(callback: (() -> Unit)?)` to expect declaration |
| `shared/src/androidMain/kotlin/com/poddrive/audio/AudioPlayer.kt` | Implement completion callback via `Player.Listener.onPlaybackStateChanged(STATE_ENDED)` |
| `shared/src/iosMain/kotlin/com/poddrive/audio/AudioPlayer.kt` | Add no-op stub |
| `shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionEvent.kt` | Add `QueueExhausted` event |
| `shared/src/commonMain/kotlin/com/poddrive/session/DriveSessionManager.kt` | Handle `QueueExhausted` in `resolveTransition` |
| `androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt` | Register completion listener in `init`, add `onEpisodeCompleted()` |
| `androidApp/src/test/kotlin/com/poddrive/android/audio/AudioPlayerTest.kt` | Update `FakeAudioPlayer` with `setOnPlaybackComplete` |
| `shared/src/commonTest/kotlin/com/poddrive/session/DriveSessionManagerTest.kt` | Add `QueueExhausted` transition tests |

**No new Gradle dependencies.** ExoPlayer `Player.Listener` is already imported via `androidx.media3:media3-exoplayer` (added in Story 4.10).

**Spotify path (null `audioUrl`) is NOT in scope** — that path uses the external Spotify app and we have no end-of-episode signal for it. Auto-advance applies only to in-app ExoPlayer playback (Story 4.10 / ADR-002 primary path).

---

## Tasks / Subtasks

- [ ] T1 — Add `setOnPlaybackComplete` to `AudioPlayer` expect + actuals (AC1)
  - [ ] Add `open fun setOnPlaybackComplete(callback: (() -> Unit)?)` to `shared/src/commonMain/.../AudioPlayer.kt` (expect)
  - [ ] Implement in `shared/src/androidMain/.../AudioPlayer.kt` — see Dev Notes T1
  - [ ] Add no-op stub to `shared/src/iosMain/.../AudioPlayer.kt`

- [ ] T2 — Add `DriveSessionEvent.QueueExhausted` (AC3)
  - [ ] Add `object QueueExhausted : DriveSessionEvent()` in `DriveSessionEvent.kt`
  - [ ] Add KDoc: "All unplayed episodes consumed — queue is empty (Story 4.3, AC3). Transitions Activating → Ended."

- [ ] T3 — Handle `QueueExhausted` in `DriveSessionManager.resolveTransition` (AC3)
  - [ ] In the `Activating` branch: add `is DriveSessionEvent.QueueExhausted → DriveSessionState.Ended`
  - [ ] Add source comment `// Story 4.3: queue exhausted — end session gracefully`

- [ ] T4 — Wire completion callback + implement `onEpisodeCompleted()` in `DriveSessionViewModel` (AC1–AC3)
  - [ ] In `init {}`, before the `viewModelScope.launch` block, add: `podApp.audioPlayer.setOnPlaybackComplete { onEpisodeCompleted() }`
  - [ ] Add `private fun onEpisodeCompleted()` — see Dev Notes T4

- [ ] T5 — Tests: `DriveSessionManagerTest` (AC3)
  - [ ] Add `resolveTransition Activating + QueueExhausted = Ended`
  - [ ] Add `resolveTransition Playing + QueueExhausted = null` (not a valid transition from Playing)

- [ ] T6 — Tests: Update `AudioPlayerTest.FakeAudioPlayer` (AC1)
  - [ ] Override `setOnPlaybackComplete` to store the callback
  - [ ] Add `fun triggerCompletion()` helper to fire the stored callback
  - [ ] Add test: `setOnPlaybackComplete callback is invoked when triggerCompletion called`

---

## Dev Notes

### T1 — Android `AudioPlayer` — completion callback via `Player.Listener`

**Expect (`commonMain/audio/AudioPlayer.kt`) — add one line:**
```kotlin
open fun setOnPlaybackComplete(callback: (() -> Unit)?)
```

**Android actual — full change set:**

Add two fields after `private val mainHandler by lazy { ... }`:
```kotlin
private var onPlaybackComplete: (() -> Unit)? = null
private var completionListener: Player.Listener? = null
```

Add the new `actual open fun`:
```kotlin
actual open fun setOnPlaybackComplete(callback: (() -> Unit)?) {
    onPlaybackComplete = callback
}
```

In `play()`, after `val player = ExoPlayer.Builder(ctx).build().also { exoPlayer = it }`, attach the listener before `prepare()`:
```kotlin
// Story 4.3: attach completion listener BEFORE prepare so STATE_ENDED is never missed
completionListener?.let { player.removeListener(it) }   // remove stale listener from previous episode
val listener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            mainHandler.post { onPlaybackComplete?.invoke() }
        }
    }
}
player.addListener(listener)
completionListener = listener
```

In `stop()`, clean up before releasing:
```kotlin
actual open fun stop() {
    completionListener?.let { exoPlayer?.removeListener(it) }
    completionListener = null
    exoPlayer?.stop()
    exoPlayer?.release()
    exoPlayer = null
    abandonAudioFocus()
}
```

**Required import** (already present from Story 4.10 — verify it's there):
```kotlin
import androidx.media3.common.Player
```

**iOS stub — `iosMain/audio/AudioPlayer.kt`:**
```kotlin
actual open fun setOnPlaybackComplete(callback: (() -> Unit)?) = Unit
```

**Why `Player.Listener.onPlaybackStateChanged`?**
ExoPlayer transitions to `Player.STATE_ENDED` when the media item finishes. It does NOT call `stop()` automatically — the player stays in `STATE_ENDED` until explicitly stopped/released. `mainHandler.post { }` is required because ExoPlayer may call this from a background thread; all ExoPlayer mutations must happen on main (established in Story 4.10, P1 note in existing code).

### T2 — `DriveSessionEvent.QueueExhausted`

Add to `DriveSessionEvent.kt` after `RetryPlayback`:
```kotlin
/**
 * All unplayed episodes have been consumed — the queue is empty (Story 4.3, AC3).
 * Dispatched by [com.poddrive.android.ui.drive.DriveSessionViewModel.onEpisodeCompleted]
 * when [com.poddrive.usecase.BuildEpisodeQueueUseCase] returns an empty queue.
 * Transitions [DriveSessionState.Activating] → [DriveSessionState.Ended].
 */
object QueueExhausted : DriveSessionEvent()
```

### T3 — `DriveSessionManager.resolveTransition`

In the `is DriveSessionState.Activating` branch, add ONE line before `else → null`:
```kotlin
is DriveSessionEvent.QueueExhausted        -> DriveSessionState.Ended   // Story 4.3
```

Do NOT handle `QueueExhausted` from `Playing` — by design, `SkipEpisode` is dispatched first (Playing → Activating), then `QueueExhausted` is dispatched (Activating → Ended) after the async queue reload. If `QueueExhausted` arrives from `Playing` it is correctly ignored (returns null → silently dropped).

### T4 — `DriveSessionViewModel` — `init` registration + `onEpisodeCompleted()`

**In `init {}`** — add BEFORE the `viewModelScope.launch` block (line ~125):
```kotlin
// Story 4.3: Register ExoPlayer completion callback for seamless auto-advance.
// Must be set before any episode plays. Runs on main thread (AudioPlayer posts via mainHandler).
podApp.audioPlayer.setOnPlaybackComplete { onEpisodeCompleted() }
```

**New private function** — add after `onSkipEpisode()` (around line 301):
```kotlin
/**
 * Called by [AudioPlayer] when an episode ends naturally via ExoPlayer STATE_ENDED (Story 4.3, AC1).
 * Auto-advances to the next unheard episode without user interaction.
 *
 * Flow:
 * 1. Guard: ignore if session is not Playing (completion fired after explicit skip/stop).
 * 2. Dispatch [DriveSessionEvent.SkipEpisode] → session transitions Playing → Activating.
 * 3. Mark current episode as played (same pattern as [onSkipEpisode]).
 * 4. Reload queue. If next episode has audioUrl → auto-play. Else → queue tile visible for Spotify fallback.
 * 5. If queue empty → dispatch [DriveSessionEvent.QueueExhausted] → Activating → Ended.
 *
 * Does NOT call [AudioPlayer.stop] — playback has already ended (STATE_ENDED).
 * [Source: Story 4.3, AC1–AC3]
 */
private fun onEpisodeCompleted() {
    val currentState = sessionState.value
    // Guard: completion callback may fire after an explicit skip or stop — ignore in that case.
    if (currentState !is DriveSessionState.Playing) return
    Log.d(TAG, "onEpisodeCompleted: episodeId=${currentState.episode.id}")
    // Step 2: Transition Playing → Activating (same event as skip, different initiator).
    // Do NOT call audioPlayer.stop() — ExoPlayer is already in STATE_ENDED.
    podApp.driveSessionManager.dispatch(DriveSessionEvent.SkipEpisode)
    viewModelScope.launch {
        // Step 3: Mark episode as played.
        val markResult = withContext(Dispatchers.IO) {
            podApp.markEpisodePlayedUseCase.execute(currentState.episode.id)
        }
        if (markResult is com.poddrive.domain.model.Result.Failure) {
            Log.w(TAG, "markEpisodePlayedUseCase failed on episode completion: ${markResult.error}")
        }
        // Step 4: Reload queue.
        val queueResult = withContext(Dispatchers.IO) {
            podApp.buildEpisodeQueueUseCase.execute()
        }
        if (queueResult is com.poddrive.domain.model.Result.Success) {
            val episodes = queueResult.value.episodes
            _uiState.update { it.copy(episodes = episodes) }
            val next = episodes.firstOrNull()
            if (next != null) {
                if (next.audioUrl != null) {
                    // In-app path: ExoPlayer auto-plays next episode (AC1).
                    onEpisodeTapped(next)
                }
                // audioUrl == null: Spotify fallback tile is visible; user taps to open Spotify.
            } else {
                // Step 5: Queue exhausted — end session gracefully (AC3).
                Log.d(TAG, "onEpisodeCompleted: queue exhausted — dispatching QueueExhausted")
                podApp.driveSessionManager.dispatch(DriveSessionEvent.QueueExhausted)
            }
        }
    }
}
```

**Critical: no `audioPlayer.stop()` call here.** The existing `onSkipEpisode()` calls `stop()` because it's user-initiated (episode is still playing). Here ExoPlayer has already reached `STATE_ENDED` — calling `stop()` again is unnecessary and would trigger `abandonAudioFocus()` prematurely before the next episode starts.

### T5 — `DriveSessionManagerTest` additions

Add to `DriveSessionManagerTest.kt` (after existing QueueExhausted-adjacent tests):
```kotlin
@Test
fun `resolveTransition Activating + QueueExhausted = Ended`() = runTest {
    val mgr = manager()
    assertEquals(
        DriveSessionState.Ended,
        mgr.resolveTransition(DriveSessionState.Activating, DriveSessionEvent.QueueExhausted)
    )
}

@Test
fun `resolveTransition Playing + QueueExhausted = null (not valid from Playing)`() = runTest {
    val mgr = manager()
    val playingState = DriveSessionState.Playing(
        Episode("ep-1", "show-1", "Title", 60_000L, 0L, false, EpisodeTier.LIKED),
        positionMs = 0L
    )
    assertEquals(null, mgr.resolveTransition(playingState, DriveSessionEvent.QueueExhausted))
}
```

### T6 — `AudioPlayerTest.FakeAudioPlayer` additions

In `AudioPlayerTest.kt`, update `FakeAudioPlayer` to track and trigger completion:
```kotlin
private class FakeAudioPlayer : AudioPlayer() {
    val playCalls = mutableListOf<Pair<String, Long>>()
    var pauseCount = 0
    var resumeCount = 0
    var stopCount = 0
    private var _isPlaying = false
    private var _positionMs = 0L
    // Story 4.3: completion callback support
    private var completionCallback: (() -> Unit)? = null

    override fun play(url: String, resumePositionMs: Long) {
        playCalls += url to resumePositionMs
        _isPlaying = true
        _positionMs = resumePositionMs
    }
    override fun pause() { pauseCount++; _isPlaying = false }
    override fun resume() { resumeCount++; _isPlaying = true }
    override fun stop() { stopCount++; _isPlaying = false; _positionMs = 0L }
    override fun currentPositionMs(): Long = _positionMs
    override fun isPlaying(): Boolean = _isPlaying
    // Story 4.3
    override fun setOnPlaybackComplete(callback: (() -> Unit)?) { completionCallback = callback }
    fun triggerCompletion() { completionCallback?.invoke() }
}
```

Add test:
```kotlin
@Test
fun `setOnPlaybackComplete callback is invoked by triggerCompletion`() {
    val player = FakeAudioPlayer()
    var called = false
    player.setOnPlaybackComplete { called = true }
    player.triggerCompletion()
    assertTrue(called)
}

@Test
fun `setOnPlaybackComplete null clears callback`() {
    val player = FakeAudioPlayer()
    var called = false
    player.setOnPlaybackComplete { called = true }
    player.setOnPlaybackComplete(null)
    player.triggerCompletion()
    assertFalse(called)
}
```

---

## Architecture Rules — Must Follow

- **Only `DriveSessionManager` mutates `DriveSessionState`** (AR6) — `onEpisodeCompleted()` only dispatches events, never touches `_state` directly.
- **Persist-before-emit** (AR6) — `DriveSessionManager` already handles this for all events including the new `QueueExhausted`.
- **Use case returns `Result<T, AppError>`** (AR3) — `onEpisodeCompleted` handles `Result.Failure` gracefully.
- **All color / theme references via `MaterialTheme.colorScheme`** — no raw `Color(0xFF...)` literals. No UI changes in this story.
- **No `audioPlayer.stop()` in `onEpisodeCompleted()`** — episode has already ended; calling stop abandons audio focus and releases ExoPlayer before the next episode can start.
- **`ExoPlayer` listener must be attached BEFORE `prepare()`** to avoid missing `STATE_ENDED` for very short media items.
- **`mainHandler.post { }` required around callback invocation** from `Player.Listener` — ExoPlayer may call state change callbacks on a non-main thread.

## Anti-Patterns to Avoid

- ❌ Do NOT add auto-advance for the Spotify deep-link path — we have no end-of-episode signal for external Spotify playback.
- ❌ Do NOT call `audioPlayer.stop()` in `onEpisodeCompleted()` — it is already in `STATE_ENDED`.
- ❌ Do NOT create a new `AudioPlayer` instance — `PodDriveApplication.audioPlayer` is the app-scoped singleton (`val audioPlayer by lazy`). One instance only.
- ❌ Do NOT handle `QueueExhausted` from `DriveSessionState.Playing` in `resolveTransition` — the sequencing (Playing → Activating via SkipEpisode, then Activating → Ended via QueueExhausted) is deliberate and keeps state transitions atomic.
- ❌ Do NOT add TTS on queue exhaustion — Story 3.4 is deferred MVP Phase 2.

---

## Previous Story Intelligence (Story 4.11 / 4.10 learnings)

- `AudioPlayer` is `expect open class` — test subclasses override methods, no Robolectric needed for unit tests.
- `FakeAudioPlayer` lives in `androidApp/src/test/` (not `commonTest`) — it uses the no-arg `AudioPlayer()` constructor.
- `DriveSessionViewModel.onSkipEpisode()` is the direct template for `onEpisodeCompleted()` — same mark-played → reload → auto-play pattern. Key difference: no `audioPlayer.stop()` call.
- `DriveSessionViewModel.init {}` already accesses `podApp.driveSessionManager` — adding `podApp.audioPlayer.setOnPlaybackComplete` in the same block is consistent and safe.
- `Player.Listener` import: `import androidx.media3.common.Player` — verify it is present in `androidMain/AudioPlayer.kt` before adding the listener code. The existing file already imports `androidx.media3.exoplayer.ExoPlayer` and `androidx.media3.common.MediaItem`; `Player` is in the same package group.
- Position polling (`startPositionPolling`) runs every 1 s via `delay()`. After `STATE_ENDED`, position polling will continue reading `currentPositionMs()` from a stopped player (returns 0). This is harmless — the polling job is stopped in the `state.collect` observer when `DriveSessionState.Playing` → `Activating` fires.

---

## Build Commands

```bash
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
./gradlew assembleDebug --max-workers 1

JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
./gradlew testDebugUnitTest --max-workers 1
```

---

## File Locations — Quick Reference

| File | Absolute path (from repo root) |
|------|-------------------------------|
| AudioPlayer expect | `poddrive/shared/src/commonMain/kotlin/com/poddrive/audio/AudioPlayer.kt` |
| AudioPlayer Android actual | `poddrive/shared/src/androidMain/kotlin/com/poddrive/audio/AudioPlayer.kt` |
| AudioPlayer iOS stub | `poddrive/shared/src/iosMain/kotlin/com/poddrive/audio/AudioPlayer.kt` |
| DriveSessionEvent | `poddrive/shared/src/commonMain/kotlin/com/poddrive/domain/model/DriveSessionEvent.kt` |
| DriveSessionManager | `poddrive/shared/src/commonMain/kotlin/com/poddrive/session/DriveSessionManager.kt` |
| DriveSessionViewModel | `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt` |
| DriveSessionManagerTest | `poddrive/shared/src/commonTest/kotlin/com/poddrive/session/DriveSessionManagerTest.kt` |
| AudioPlayerTest | `poddrive/androidApp/src/test/kotlin/com/poddrive/android/audio/AudioPlayerTest.kt` |

---

## References

- [Source: epics.md#Story 4.3] — user story, ACs, FR23, NFR9
- [Source: architecture.md#ADR-002] — ExoPlayer primary path; Spotify deep-link fallback
- [Source: DriveSessionManager.kt] — `resolveTransition` function, `SkipEpisode` transition
- [Source: DriveSessionEvent.kt] — existing event sealed class
- [Source: DriveSessionViewModel.kt#onSkipEpisode] — template for `onEpisodeCompleted` pattern
- [Source: AudioPlayer.kt (androidMain)] — existing `play()`, `stop()`, `Player.Listener` pattern

---

## Dev Agent Record

### Agent Model Used

*To be filled in by dev agent*

### Completion Notes

*To be filled in by dev agent*

### File List

*To be filled in by dev agent*

---

## Change Log

| Date | Change |
|------|--------|
| 2026-04-17 | Story created — ready for dev |
