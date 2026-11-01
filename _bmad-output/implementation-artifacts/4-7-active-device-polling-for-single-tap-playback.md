# Story 4.7: Active-Device Polling for Reliable Single-Tap Playback

Status: review

## Story

As a driver using PodDrive on a free Spotify account,
I want the tapped episode to start playing in Spotify automatically — even when Spotify
was not running before I tapped —
so that I never have to look at my phone to press Play after a handoff.

## Background & Motivation

Story 4.6 achieved auto-play by calling `PUT /v1/me/player/play` after a fixed 1,500 ms
delay. This works when Spotify is already running (warm start) but fails on cold start:

- Spotify must fully initialize and register itself as the **active playback device** with
  Spotify's cloud backend before the `PUT` endpoint will accept commands
- Cold-start device registration takes 2–4 s on mid-range hardware
- The 1,500 ms delay expires before registration completes →
  `HTTP 404 No active device` → silent fallback → **user must press Play in Spotify (second tap)**

### Why not the Spotify App Remote SDK?

Evaluated in Story 4.6 (research date: 2026-04-06):
`PlayerApi.play(uri)` silently rejects episode URIs. The Android SDK accepts only
Track, Album, Artist, and Playlist URIs. Not viable for podcast playback.

### Solution: adaptive active-device polling

Instead of a fixed delay, `SpotifyAutoPlayCoordinator` will:

1. Begin polling `GET /v1/me/player` immediately after the deep link fires
2. As soon as Spotify registers an active device, fire `PUT /v1/me/player/play`
3. If no device appears within `DEVICE_POLL_TIMEOUT_MS` (5,000 ms), exit silently —
   the existing `HANDOFF_TIMEOUT_MS` (3,000 ms) safety net handles the UX

**Warm start:** first poll succeeds at ~0–500 ms (~3× faster than the old fixed 1,500 ms delay)
**Cold start:** first poll succeeds at ~2,000–3,000 ms when device finally registers —
 well within the user's patience window and now reliable

### Timing relationship with HANDOFF_TIMEOUT_MS

`DEVICE_POLL_TIMEOUT_MS` (5,000 ms) intentionally exceeds `HANDOFF_TIMEOUT_MS` (3,000 ms).
When the handoff timeout fires it sets `isHandingOff = false`. The Story 4.6 AC7 guard
(`if (!isHandingOff()) break`) causes the polling loop to exit cleanly on the next
iteration — DEVICE_POLL_TIMEOUT_MS is the backstop for the rare case where neither
condition has triggered.

```
t=0 ms     Deep link fires → isHandingOff = true → polling starts
t=0 ms     Poll 1: GET /v1/me/player → 404 (Spotify cold-starting)
t=500 ms   Poll 2: → 404
t=1000 ms  Poll 3: → 200 {"device": {"id": "abc…"}} ← registered
t=1000 ms  PUT /v1/me/player/play → 204 ← playing ✓
           clearHandingOff() → handoffTimeoutJob cancelled

--- worst-case (slow device, no auto-play success) ---

t=3000 ms  HANDOFF_TIMEOUT_MS fires → isHandingOff = false
t=~3000 ms Next poll: isHandingOff guard → loop exits cleanly
```

---

## Acceptance Criteria

1. **AC1 — Cold-start auto-play**: When Spotify is not running at the time of the tap,
   the episode begins playing in Spotify without any user interaction, within 5 seconds
   of the tap.

2. **AC2 — Warm-start auto-play (regression)**: When Spotify is already running, auto-play
   fires no later than the old 1,500 ms fixed delay (first successful poll at ~0–500 ms).
   Story 4.6 behaviour must not regress.

3. **AC3 — Free-tier**: Feature works for users on a free Spotify account. Podcast episodes
   are not gated behind Premium. The `HTTP 403` path (music tracks, premium-only) is handled
   identically to Story 4.6 AC5 — silent fallback.

4. **AC4 — Graceful exit on timeout**: If no active device is detected within
   `DEVICE_POLL_TIMEOUT_MS` (5,000 ms), the coordinator exits the loop silently.
   The existing `HANDOFF_TIMEOUT_MS` (3,000 ms) continues as the UX safety net
   (no change to Story 4.1/4.5 error paths).

5. **AC5 — Polling rate**: The coordinator polls at `DEVICE_POLL_INTERVAL_MS` (500 ms),
   making at most `DEVICE_POLL_TIMEOUT_MS / DEVICE_POLL_INTERVAL_MS` = 10 API calls per
   handoff. Polling stops as soon as a device is found or the loop exits.

6. **AC6 — `AUTO_PLAY_DELAY_MS` retired**: The constant
   `SpotifyAutoPlayCoordinator.AUTO_PLAY_DELAY_MS` is removed. All tests and references
   must be updated to use `DEVICE_POLL_INTERVAL_MS` and `DEVICE_POLL_TIMEOUT_MS`.

7. **AC7 — `isHandingOff` guard preserved**: The guard introduced in Story 4.6 AC4 —
   silently discarding a successful API response if `isHandingOff == false` at the moment
   of response — applies equally inside the polling loop. If `isHandingOff` becomes false
   during polling, the loop exits without calling `clearHandingOff()` or `playEpisode()`.

8. **AC8 — Unit test coverage**:
   - `SpotifyPlayerApiClientTest`: `hasActiveDevice` returns `true` on HTTP 200 with device,
     `false` on 200 with null device, `false` on 204, `false` on network exception,
     re-throws `CancellationException`
   - `SpotifyAutoPlayCoordinatorTest`: polls until device found then plays; plays immediately
     on first-poll success; exits silently after timeout without device; stops polling when
     `isHandingOff` becomes false (AC7); `CancellationException` propagates out of loop

---

## Tasks / Subtasks

- [x] **Task 1 — `SpotifyPlayerApiClient.hasActiveDevice()`** (AC5, AC8)
  - [x] 1.1 Add `suspend fun hasActiveDevice(): Boolean` to `SpotifyPlayerApiClient`:
        - `GET https://api.spotify.com/v1/me/player`
        - HTTP 200 with non-null `device` in body → `true`
        - HTTP 200 with `device: null`, HTTP 204 (no active session), or any non-2xx → `false`
        - Network / IO exceptions → `false` (transient; next poll may succeed)
        - `CancellationException` → **re-throw** (architecture rule)
  - [x] 1.2 Add to `SpotifyModels.kt`:
        ```kotlin
        @Serializable
        data class ActivePlayerResponse(val device: DeviceInfo? = null)

        @Serializable
        data class DeviceInfo(val id: String)
        ```
        Use the existing `lenient` JSON config (already sets `ignoreUnknownKeys = true`
        in `SpotifyHttpClient`).
  - [x] 1.3 Add tests to `SpotifyPlayerApiClientTest`:
        - `hasActiveDevice returns true on 200 with device`
        - `hasActiveDevice returns false on 200 with null device`
        - `hasActiveDevice returns false on 204 No Content`
        - `hasActiveDevice returns false on network exception`
        - `hasActiveDevice rethrows CancellationException`

- [x] **Task 2 — Refactor `SpotifyAutoPlayCoordinator`** (AC1–AC7)
  - [x] 2.1 Replace `delay(AUTO_PLAY_DELAY_MS)` + single `playEpisode()` call with a
        polling loop. Inject a testable clock lambda:
        ```kotlin
        class SpotifyAutoPlayCoordinator(
            private val playerApiClient: SpotifyPlayerApiClient,
            private val isHandingOff: () -> Boolean,
            private val clock: () -> Long = { System.currentTimeMillis() }
        ) {
            suspend fun autoPlay(episodeUri: String): Boolean {
                require(episodeUri.isNotBlank())
                val deadline = clock() + DEVICE_POLL_TIMEOUT_MS
                while (clock() < deadline) {
                    if (!isHandingOff()) return false   // AC7 guard
                    if (playerApiClient.hasActiveDevice()) {
                        val result = playerApiClient.playEpisode(episodeUri)
                        return result is Result.Success
                    }
                    delay(DEVICE_POLL_INTERVAL_MS)
                    if (!isHandingOff()) return false   // AC7 guard (post-delay)
                }
                return false   // AC4 — timeout exit
            }

            companion object {
                const val DEVICE_POLL_INTERVAL_MS = 500L
                const val DEVICE_POLL_TIMEOUT_MS  = 5_000L
            }
        }
        ```
  - [x] 2.2 Remove `AUTO_PLAY_DELAY_MS` constant entirely
  - [x] 2.3 Update `PodDriveApplication` wiring: pass `isHandingOff` lambda from
        `DriveSessionViewModel` (or read from `_uiState`) — same pattern as existing
        coordinator injection

- [x] **Task 3 — Update tests** (AC6, AC8)
  - [x] 3.1 Update `SpotifyAutoPlayCoordinatorTest`:
        - Remove `AUTO_PLAY_DELAY_MS` timing assertions
        - Add: `autoPlay polls until device found then returns true`
        - Add: `autoPlay returns true immediately when first poll finds device`
        - Add: `autoPlay returns false after full timeout with no device`
        - Add: `autoPlay returns false immediately when isHandingOff is false on entry`
        - Add: `autoPlay returns false when isHandingOff becomes false mid-loop`
        - Add: `autoPlay CancellationException propagates out of loop`
        - Use `TestCoroutineScheduler` / `advanceTimeBy` via `runTest` (already used in
          coordinator tests per Story 4.6)
  - [x] 3.2 Update `DriveSessionViewModelTest.kt`:
        - Replace `AUTO_PLAY_DELAY_MS` reference with `DEVICE_POLL_INTERVAL_MS`
        - Add constant invariant: `assert(DEVICE_POLL_TIMEOUT_MS > HANDOFF_TIMEOUT_MS)`
          (intentional — polling outlasts the handoff timeout; AC7 guard is the exit gate)

- [x] **Task 4 — Update sprint status** (housekeeping)
  - [x] 4.1 Add `4-7-active-device-polling-for-single-tap-playback: in-progress` to
        `_bmad-output/implementation-artifacts/sprint-status.yaml`

---

## Dev Notes

### Architecture Rules (MUST follow)

1. `SpotifyPlayerApiClient` and `SpotifyAutoPlayCoordinator` live in `commonMain` — no
   Android/iOS imports anywhere in these files.
2. `CancellationException` must be re-thrown in all `catch (e: Exception)` blocks —
   see `SpotifyAuthManager.refreshAccessToken` for the established pattern.
3. New serializable models go in `SpotifyModels.kt` — do not create a separate models file.
4. Do NOT change `DriveSessionState`, `DriveSessionManager`, or `DriveSessionViewModel`
   public method signatures.
5. `HANDOFF_TIMEOUT_MS` stays at 3,000 ms — do not modify (Story 4.1 contract).
6. Do NOT add new OAuth scopes — `user-read-playback-state` (already present) covers
   `GET /v1/me/player`.

### `hasActiveDevice` response parsing

Spotify `GET /v1/me/player` responses:

| Status | Body | `hasActiveDevice` result |
|---|---|---|
| 200 | `{"device": {"id": "abc", ...}, ...}` | `true` |
| 200 | `{"device": null, ...}` | `false` |
| 204 No Content | _(empty)_ | `false` |
| 401 Unauthorized | _(error body)_ | `false` (Ktor Auth plugin retries first) |
| Network exception | — | `false` (retry on next poll) |
| `CancellationException` | — | re-throw |

Parse with `response.body<ActivePlayerResponse>()` — the existing `lenient` JSON
deserializer in `SpotifyHttpClient` already has `ignoreUnknownKeys = true`, so the
full player state object (shuffleState, repeatState, etc.) is safely ignored.

For HTTP 204, `response.body<ActivePlayerResponse>()` will throw because there is no
body to parse — catch this and return `false`, or check `response.status.value == 204`
before attempting to parse.

### Constant values

```kotlin
// SpotifyAutoPlayCoordinator companion object
internal const val DEVICE_POLL_INTERVAL_MS = 500L   // replaces AUTO_PLAY_DELAY_MS
internal const val DEVICE_POLL_TIMEOUT_MS  = 5_000L

// DriveSessionViewModel companion object (unchanged)
internal const val HANDOFF_TIMEOUT_MS = 3_000L      // Story 4.1 — DO NOT CHANGE
```

### Key Files to Touch

| File | Change |
|------|--------|
| `shared/src/commonMain/kotlin/com/poddrive/api/SpotifyPlayerApiClient.kt` | Add `hasActiveDevice()` |
| `shared/src/commonMain/kotlin/com/poddrive/api/SpotifyModels.kt` | Add `ActivePlayerResponse`, `DeviceInfo` |
| `shared/src/commonMain/kotlin/com/poddrive/api/SpotifyAutoPlayCoordinator.kt` | Replace fixed delay with polling loop; remove `AUTO_PLAY_DELAY_MS` |
| `shared/src/commonTest/kotlin/com/poddrive/api/SpotifyPlayerApiClientTest.kt` | Add `hasActiveDevice` tests |
| `shared/src/commonTest/kotlin/com/poddrive/api/SpotifyAutoPlayCoordinatorTest.kt` | Rewrite timing tests; add polling scenarios |
| `androidApp/src/test/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModelTest.kt` | Update constant references; add `DEVICE_POLL_TIMEOUT_MS > HANDOFF_TIMEOUT_MS` invariant |
| `_bmad-output/implementation-artifacts/sprint-status.yaml` | Add story 4.7 status |

### References

- [ADR-001] `_bmad-output/planning-artifacts/architecture.md` — decision rationale
- [Story 4.6] `_bmad-output/implementation-artifacts/4-6-spotify-episode-auto-play.md` —
  predecessor; defines `isHandingOff` guard, `SpotifyAutoPlayCoordinator` pattern,
  App Remote SDK rejection finding
- [Story 4.1] `_bmad-output/implementation-artifacts/4-1-spotify-episode-deep-link-handoff.md` —
  defines `HANDOFF_TIMEOUT_MS`
- Spotify Web API: `GET /v1/me/player` — active device check
- Spotify Web API: `PUT /v1/me/player/play` — unchanged from Story 4.6

---

## Dev Agent Record

### Implementation Plan

Replaced the fixed `AUTO_PLAY_DELAY_MS = 1500ms` strategy with an adaptive polling loop:

1. **`SpotifyModels.kt`** — added `ActivePlayerResponse(device: DeviceInfo?)` and `DeviceInfo(id: String)` to deserialise `GET /v1/me/player` responses.
2. **`SpotifyPlayerApiClient.hasActiveDevice()`** — polls `GET /v1/me/player`; returns `true` on 200+device, `false` on 204/non-2xx/network error, re-throws `CancellationException`.
3. **`SpotifyAutoPlayCoordinator`** — full rewrite: new constructor takes `isHandingOff: () -> Boolean` + injectable `clock: () -> Long`; `autoPlay(uri)` replaces `attemptAutoPlay(uri, delayMs)`; polling loop exits on device found, `isHandingOff` guard, or timeout; `AUTO_PLAY_DELAY_MS` removed.
4. **`PodDriveApplication`** — removed `spotifyAutoPlayCoordinator`, exposed `playerApiClient: SpotifyPlayerApiClient` instead. Coordinator is now created lazily inside `DriveSessionViewModel` so the `isHandingOff` lambda can close over `_uiState`.
5. **`DriveSessionViewModel`** — constructor changed from receiving a coordinator singleton to `coordinatorOverride: SpotifyAutoPlayCoordinator? = null`; coordinator created by `lazy { }` with `{ _uiState.value.isHandingOff }` lambda; call updated from `attemptAutoPlay` to `autoPlay`.
6. **Tests** — `SpotifyPlayerApiClientTest` grew to 14 tests (+5 for `hasActiveDevice`); `SpotifyAutoPlayCoordinatorTest` fully rewritten (10 tests covering all polling scenarios, guards, timeouts, CancellationException); `DriveSessionViewModelTest` updated (removed old `AUTO_PLAY_DELAY_MS` tests, added `DEVICE_POLL_INTERVAL_MS` and `DEVICE_POLL_TIMEOUT_MS > HANDOFF_TIMEOUT_MS` invariant).

### Completion Notes

- All 4 tasks and 14 subtasks checked off.
- ✅ `SpotifyPlayerApiClientTest`: 14/14 passing (5 new `hasActiveDevice` tests)
- ✅ `SpotifyAutoPlayCoordinatorTest`: 10/10 passing (full polling suite with virtual-time clock)
- ✅ `DriveSessionViewModelTest`: 3/3 passing (updated constants)
- ✅ No regressions: `shared:testDebugUnitTest` and `androidApp:testDebugUnitTest` both `BUILD SUCCESSFUL`
- Note: `DEVICE_POLL_INTERVAL_MS` and `DEVICE_POLL_TIMEOUT_MS` are `public` (not `internal`) to allow cross-module access from `androidApp:DriveSessionViewModelTest` — consistent with prior `AUTO_PLAY_DELAY_MS` visibility.

---

## File List

- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyModels.kt` — added `ActivePlayerResponse`, `DeviceInfo`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyPlayerApiClient.kt` — added `hasActiveDevice()`, added `get`/`body` imports
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyAutoPlayCoordinator.kt` — complete rewrite (polling loop, new constructor, `autoPlay`, removed `AUTO_PLAY_DELAY_MS`)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` — removed `spotifyAutoPlayCoordinator`, added `playerApiClient`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt` — lazy coordinator, `autoPlay` call, updated comments
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyPlayerApiClientTest.kt` — added 5 `hasActiveDevice` tests
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyAutoPlayCoordinatorTest.kt` — complete rewrite with 10 polling scenario tests
- `poddrive/androidApp/src/test/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModelTest.kt` — replaced `AUTO_PLAY_DELAY_MS` tests, added `DEVICE_POLL` constant/invariant tests
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — story 4.7 status updated

---

## Change Log

- 2026-04-08: Story 4.7 implemented — replaced fixed 1 500 ms auto-play delay with adaptive active-device polling (Story 4.7)
