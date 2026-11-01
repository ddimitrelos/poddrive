# Story 4.6: Spotify Episode Auto-Play via Web API

Status: done

## Story

As a driver using PodDrive,
I want a tapped episode to begin playing automatically in Spotify,
so that I don't have to take my eyes off the road to press Play in Spotify after the handoff.

## Background & Motivation

Story 4.1 implemented deep-link handoff: tapping an episode fires
`Intent(ACTION_VIEW, Uri.parse("spotify:episode:<id>"))`, which opens Spotify and navigates to
the episode page. However, Spotify does **not** auto-play on deep-link navigation — the user
must manually press Play in Spotify.

### Why Spotify App Remote SDK is NOT the solution

The Spotify App Remote SDK (android-sdk-0.8.0) was evaluated and **cannot** be used here:
- `PlayerApi.play(URI)` only accepts Track, Album, Artist, and Playlist URIs.
- Episode URIs (`spotify:episode:…`) are silently rejected — the call returns without error but
  does not start playback. This is a documented limitation of the App Remote SDK's Android library.

### Correct approach: Spotify Web API `PUT /v1/me/player/play`

The Spotify Web API endpoint `PUT https://api.spotify.com/v1/me/player/play` accepts episode
URIs in its `uris` array and works for **free Spotify users** (podcast episodes are free).
The request body:
```json
{ "uris": ["spotify:episode:<id>"] }
```
requires the `user-modify-playback-state` OAuth scope, which is not currently requested.

### Timing constraint

The Web API `play` endpoint targets an active Spotify device. After the deep-link fires, Spotify
needs ~1–2 seconds to become the active playback device before the API call succeeds. A 1 500 ms
delay after the deep-link is the minimum safe value (matches the discarded AudioManager stopgap
delay, but unlike that stopgap this call specifies the exact episode URI).

### Handoff timeout interaction

The existing 3-second `HANDOFF_TIMEOUT_MS` (Story 4.1) fires if the user has not returned to
PodDrive. If the Web API call succeeds, the handoff is complete even though the user stayed in
Spotify — `clearHandingOff()` must be called programmatically on API success so the timeout job
is cancelled and the episode is marked played. If the API call fails (no active device, 403
Premium-required for non-podcast content, etc.) the existing timeout path handles the UX
gracefully — the episode is already visible in Spotify for manual tap.

---

## Acceptance Criteria

1. **AC1 — Auto-play fires**: After the user taps an episode tile or the "Open in Spotify" CTA,
   the tapped episode begins playing in Spotify within 3 seconds without any further user
   interaction.

2. **AC2 — Free-user support**: Auto-play works for users on a free Spotify plan (podcast
   episodes are playback-free). The story MUST NOT gate this feature on Spotify Premium.

3. **AC3 — Scope added**: The OAuth authorization URL includes `user-modify-playback-state` in
   the `scope` parameter. Existing authenticated users are not forcibly logged out; the new scope
   is requested on next re-authorization.

4. **AC4 — Handoff completes on API success**: When `PUT /v1/me/player/play` returns 2xx, the
   ViewModel calls `clearHandingOff()` immediately (cancelling the 3-second timeout job and
   marking the episode as played), rather than waiting for the user to return to PodDrive.
   **Late-success guard**: if the API responds *after* the 3-second timeout has already fired
   (i.e. `isHandingOff == false`), the success response is silently discarded — the session
   remains in its Error state and `clearHandingOff()` is NOT called (IG-2 resolution).

5. **AC5 — Graceful fallback on API failure**: If the Web API call returns a non-2xx response
   or throws a network exception, no additional error is shown to the user. The episode is
   already visible in Spotify from the deep-link (Story 4.1 behavior). The handoff timeout
   continues running normally and will expire if the user does not return.

6. **AC6 — No double-play guard**: If `clearHandingOff()` is called by the API success path
   before the user returns to PodDrive (ON_RESUME), the lifecycle observer must not call
   `clearHandingOff()` a second time (already guarded by `isHandingOff` flag).

7. **AC7 — Unit test coverage**: `SpotifyPlayerApiClientTest` covers: 2xx success (204, 200),
   4xx/5xx errors (401, 403, 404), HTTP 429 → `RateLimitExceeded`, network exception,
   CancellationException propagation, blank URI guard.
   `SpotifyAutoPlayCoordinatorTest` covers (in `commonTest`, no Robolectric required): success
   (true), failure paths (false for 4xx/5xx/network), CancellationException propagation, blank
   episodeId guard, delay-before-API-call timing assertion.
   `DriveSessionViewModelTest` covers: `HANDOFF_TIMEOUT_MS` value, `AUTO_PLAY_DELAY_MS` value,
   and the ordering invariant (`AUTO_PLAY_DELAY_MS < HANDOFF_TIMEOUT_MS`).

---

## Tasks / Subtasks

- [x] **Task 1 — Add OAuth scope** (AC3)
  - [x] 1.1 In `SpotifyAuthManager.REQUIRED_SCOPES`, append `user-modify-playback-state`
        (keep existing 3 scopes; do NOT add `streaming` — that is only for Web Playback SDK
        which is browser-only and not applicable to Android)
  - [x] 1.2 Update the KDoc comment above `REQUIRED_SCOPES` to reflect the new scope list
  - [x] 1.3 Update `SpotifyAuthManagerTest.buildAuthorizationUrl contains all required scopes`
        to assert `user-modify-playback-state` is present in the URL

- [x] **Task 2 — Create `SpotifyPlayerApiClient`** (AC1, AC4, AC5)
  - [x] 2.1 Create `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyPlayerApiClient.kt`
  - [x] 2.2 Inject the authenticated `HttpClient` (same instance from `SpotifyHttpClient.kt` that
        already handles token refresh via `Auth` plugin — do NOT create a new `HttpClient` here)
  - [x] 2.3 Implement `suspend fun playEpisode(episodeUri: String): Result<Unit, AppError>`:
        - `PUT https://api.spotify.com/v1/me/player/play`
        - Body: `{"uris": ["<episodeUri>"]}`; Content-Type: `application/json`
        - 2xx → `Result.Success(Unit)`
        - 4xx/5xx → `Result.Failure(AppError.SpotifyApiError(code, message))`
        - Network exception → `Result.Failure(AppError.Unknown(e))`; re-throw
          `CancellationException` per architecture rule
        - Use `kotlinx.serialization` for body serialization (no raw string building)
  - [x] 2.4 Register `SpotifyPlayerApiClient` in `PodDriveApplication` DI (alongside existing
        API clients) — single instance, lazy construction

- [x] **Task 3 — Wire auto-play into `DriveSessionViewModel`** (AC1, AC4, AC5, AC6)
  - [x] 3.1 Add `SpotifyPlayerApiClient` reference via `podApp` (same pattern as other use cases)
  - [x] 3.2 In `onOpenInSpotify(episodeId)`, launch a coroutine that:
        1. Delays `AUTO_PLAY_DELAY_MS` (1 500 ms — new constant, separate from
           `HANDOFF_TIMEOUT_MS`)
        2. If `_uiState.value.isHandingOff` is still true (guard against cancel by
           `onSpotifyNotInstalled` / `onPlaybackFailed`):
           - Calls `podApp.spotifyPlayerApiClient.playEpisode(episodeId)`
           - On `Result.Success`: calls `clearHandingOff()` (cancels timeout job, marks played)
           - On `Result.Failure`: does nothing — timeout job continues; AC5 fallback applies
  - [x] 3.3 Do NOT cancel the `handoffTimeoutJob` when the auto-play coroutine is launched; the
        timeout is the safety net if both the API call and the user-return path fail

- [x] **Task 4 — Unit tests** (AC7)
  - [x] 4.1 Create `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyPlayerApiClientTest.kt`
        using `MockEngine` (same pattern as `SpotifyAuthManagerTest`):
        - `playEpisode returns Success on HTTP 204`
        - `playEpisode returns SpotifyApiError on HTTP 403`
        - `playEpisode returns Unknown on network exception`
  - [x] 4.2 Add tests to `DriveSessionViewModelTest.kt`:
        - Constant verification tests (HANDOFF_TIMEOUT_MS, AUTO_PLAY_DELAY_MS, ordering invariant)
        - Note: full coroutine flow tests (advanceTimeBy) require Robolectric not yet configured;
          API client behavior is fully covered by SpotifyPlayerApiClientTest (6 tests)

---

## Dev Notes

### Architecture Rules (MUST follow)
1. **No raw Color literals** — not applicable here (no new UI), but follow for any Composable changes.
2. **`isHandingOff` is UI-local** — this story does NOT add `autoPlayPending` or similar fields to
   `DriveSessionState`. All new state lives in `DriveUiState` or ViewModel locals.
3. **`ActivityNotFoundException` stays in the Composable layer** — not relevant to Web API calls;
   Web API exceptions are caught in the use case / client.
4. **Single `HttpClient` instance** — `SpotifyPlayerApiClient` must receive the same `HttpClient`
   that `SpotifyLibraryApiClient` uses (created in `SpotifyHttpClient.kt`, already configured with
   the `Auth` plugin for token refresh). Do NOT instantiate a new `HttpClient`.
5. **`CancellationException` re-throw** — all `catch (e: Exception)` blocks in suspend functions
   must re-throw if `e is CancellationException` (see `SpotifyAuthManager.refreshAccessToken`).
6. **KMP commonMain only** — `SpotifyPlayerApiClient` goes in `commonMain`. No Android-specific code.

### OAuth Scope Change Impact
Adding `user-modify-playback-state` to `REQUIRED_SCOPES` means:
- **New users**: will see the updated permission list during first-time OAuth.
- **Existing users**: will continue working with the old token until the token is revoked or the
  user triggers re-auth. Spotify does not automatically request new scopes on token refresh.
- **No forced logout** is required — this story should NOT add any migration or forced re-auth logic.

### Key Files to Touch
| File | Change |
|------|--------|
| `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyAuthManager.kt` | Add scope |
| `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyPlayerApiClient.kt` | **NEW** |
| `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` | Register client |
| `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt` | Wire auto-play |
| `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyAuthManagerTest.kt` | Update scope test |
| `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyPlayerApiClientTest.kt` | **NEW** |
| `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/DriveSessionViewModelTest.kt` | Add auto-play tests |

### Spotify Web API Reference (verified 2026-04-06)
- **Endpoint**: `PUT https://api.spotify.com/v1/me/player/play`
- **Auth**: Bearer token with `user-modify-playback-state` scope
- **Body** (start episode playback):
  ```json
  { "uris": ["spotify:episode:6FZj6eVQLmvii10w73SxEw"] }
  ```
- **Success**: HTTP 204 No Content (no response body)
- **No active device**: HTTP 404 `{"error":{"status":404,"message":"Player command failed: No active device found"}}`
- **Premium required** (for music tracks — NOT for podcast episodes): HTTP 403
- **Bad OAuth token / missing scope**: HTTP 401 (Ktor `Auth` plugin will attempt token refresh first)
- **Rate limit**: HTTP 429 — treat as transient failure (AC5 fallback applies)

### Timing Constants
```kotlin
companion object {
    internal const val HANDOFF_TIMEOUT_MS = 3_000L   // existing — Story 4.1
    internal const val AUTO_PLAY_DELAY_MS  = 1_500L   // new — Story 4.6
}
```

### `SpotifyPlayerApiClient` skeleton (for reference)
```kotlin
class SpotifyPlayerApiClient(private val httpClient: HttpClient) {

    suspend fun playEpisode(episodeUri: String): Result<Unit, AppError> = try {
        val response = httpClient.put("https://api.spotify.com/v1/me/player/play") {
            contentType(ContentType.Application.Json)
            setBody(PlayRequest(uris = listOf(episodeUri)))
        }
        if (response.status.isSuccess()) {
            Result.Success(Unit)
        } else {
            Result.Failure(
                AppError.SpotifyApiError(
                    code = response.status.value,
                    message = response.bodyAsText()
                )
            )
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.Failure(AppError.Unknown(e))
    }

    @Serializable
    private data class PlayRequest(val uris: List<String>)
}
```

### `onOpenInSpotify` updated flow (for reference)
```kotlin
fun onOpenInSpotify(episodeId: String) {
    if (_uiState.value.isHandingOff) return
    _uiState.update { it.copy(isHandingOff = true, handoffEpisodeId = episodeId) }
    handoffTimeoutJob?.cancel()
    handoffTimeoutJob = viewModelScope.launch {
        delay(HANDOFF_TIMEOUT_MS)
        if (_uiState.value.isHandingOff) {
            podApp.driveSessionManager.dispatch(
                DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout)
            )
            _uiState.update { it.copy(isHandingOff = false, handoffEpisodeId = null) }
        }
    }
    // NEW: auto-play coroutine (separate from timeout job)
    viewModelScope.launch {
        delay(AUTO_PLAY_DELAY_MS)
        if (_uiState.value.isHandingOff) {
            val result = podApp.spotifyPlayerApiClient.playEpisode(episodeId)
            if (result is Result.Success) {
                clearHandingOff()
            }
            // on failure: do nothing — timeout job continues (AC5)
        }
    }
}
```

### References
- [Source: Story 4.1 — Spotify Episode Deep-Link Handoff]
- [Source: `_bmad-output/planning-artifacts/architecture.md` — Authentication & Security, AR8]
- [Source: `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyAuthManager.kt` — REQUIRED_SCOPES, TokenRefresher]
- [Source: `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyHttpClient.kt` — HttpClient creation]
- [Source: `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt` — onOpenInSpotify, clearHandingOff]
- [Source: Spotify Web API reference — PUT /v1/me/player/play (verified 2026-04-06)]
- [Source: Spotify App Remote SDK Android — episode URI limitation (research finding, 2026-04-06)]

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Build output dir is `C:/gradle-build-cache/poddrive/` (not `shared/build/`) per settings.gradle.kts redirect to avoid OneDrive locking. Test results must be checked in the build cache dir.

### Completion Notes List

- Task 1: `REQUIRED_SCOPES` updated from 3 to 4 scopes; `user-modify-playback-state` added. KDoc comment updated. `SpotifyAuthManagerTest` scope assertion updated. All 19 auth manager tests pass.
- Task 2: `SpotifyPlayerApiClient` created in `commonMain/kotlin/com/poddrive/api/`. Uses `kotlinx.serialization` `@Serializable` for `PlayRequest`. Proper `CancellationException` re-throw. `SpotifyPlayerApiClient` registered as `spotifyPlayerApiClient: SpotifyPlayerApiClient` in `PodDriveApplication.onCreate()`.
- Task 3: `DriveSessionViewModel` updated — `spotifyPlayerApiClient` injectable via constructor default (enables future testing). `AUTO_PLAY_DELAY_MS = 1_500L` added to companion. Auto-play coroutine launched in `onOpenInSpotify()` — separate from handoff timeout job. On API success: `clearHandingOff()` called (marks episode played, reloads queue, cancels timeout). On API failure: silent fallback, timeout continues.
- Task 4: `SpotifyPlayerApiClientTest` — 7 tests (204 success, 200 success, 403, 404, 401, network exception, CancellationException propagation). `DriveSessionViewModelTest` — 3 constant verification tests. Note: full coroutine flow tests for ViewModel auto-play path require Robolectric (not yet in project). Core API behavior fully covered by `SpotifyPlayerApiClientTest`.
- **Total test results after initial dev: 120 shared tests + 3 androidApp tests = 123 tests, 0 failures.**
- **Review pass (code-review findings IG-1, IG-2, P-1 through P-9):**
  - IG-1 resolved: extracted `SpotifyAutoPlayCoordinator` to `commonMain`; 11 new tests in `SpotifyAutoPlayCoordinatorTest` (success, failure, network error, cancellation, blank-ID guard, delay timing, constant value).
  - IG-2 resolved: added `isHandingOff` guard in `onOpenInSpotify` success path — late API responses (>3 s RTT) are silently discarded; AC4 updated to document this invariant.
  - P-1: `podApp` now uses `by lazy { getApplication() }` + `sessionState` is lazy; `from(app)` called at most once (injectable default only).
  - P-2: `PLAY_ENDPOINT` dead constant removed from `SpotifyPlayerApiClient`.
  - P-3: `handoffTimeoutJob?.cancel()` now called BEFORE `_uiState.update(isHandingOff=true)`.
  - P-4: `require(episodeId.isNotBlank())` added to `onOpenInSpotify`; `require(episodeUri.isNotBlank())` added to `playEpisode`.
  - P-5: `isHandingOff` guard added to `onSpotifyNotInstalled` and `onPlaybackFailed`.
  - P-6: TOCTOU comment added in the auto-play success path.
  - P-7: Idempotency guard (`if (!isHandingOff) return`) added to `clearHandingOff`.
  - P-8: HTTP 429 mapped to `AppError.RateLimitExceeded` in `SpotifyPlayerApiClient`; test added.
  - P-9: `buildAuthorizationUrl does NOT include streaming scope` test added to `SpotifyAuthManagerTest`.

### File List

- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyAuthManager.kt` (modified — REQUIRED_SCOPES + comment)
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyPlayerApiClient.kt` (new; modified in review pass — removed PLAY_ENDPOINT, added blank guard, 429→RateLimitExceeded)
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyAutoPlayCoordinator.kt` (new — review pass IG-1 resolution)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` (modified — spotifyAutoPlayCoordinator replaces spotifyPlayerApiClient)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModel.kt` (modified — review pass: P-1..P-7, IG-1, IG-2 fixes)
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyAuthManagerTest.kt` (modified — scope assertion + streaming-absence test)
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyPlayerApiClientTest.kt` (new; modified in review pass — 429 test, blank URI test)
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyAutoPlayCoordinatorTest.kt` (new — review pass IG-1 resolution)
- `poddrive/androidApp/build.gradle.kts` (modified — testImplementation kotlin("test") + coroutines-test)
- `poddrive/androidApp/src/test/kotlin/com/poddrive/android/ui/drive/DriveSessionViewModelTest.kt` (new; updated in review pass — references SpotifyAutoPlayCoordinator.AUTO_PLAY_DELAY_MS)
- `_bmad-output/implementation-artifacts/4-6-spotify-episode-auto-play.md` (this file)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (modified — story status updates)
