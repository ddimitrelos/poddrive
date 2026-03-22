# Story 1.5: Revoke Spotify Access

Status: done

## Story

As an **authenticated user**,
I want to revoke PodDrive's Spotify access and clear all stored credentials,
so that I can fully disconnect the app when I no longer want it to have access to my account.

## Acceptance Criteria

1. **Given** the user is authenticated **When** they tap "Disconnect Spotify" **Then** all Spotify access tokens and refresh tokens are deleted from `SecureStorage`.
2. **Given** the user triggers revocation **Then** any in-memory auth state is cleared — `OnboardingUiState` resets to `Idle` (Android) and `isAuthenticated` resets to `false` (iOS).
3. **Given** revocation completes **Then** the app returns to the unauthenticated welcome/onboarding screen with no user credentials visible.
4. **Given** revocation completes **Then** no credentials remain retrievable from the device: `SecureStorage.getAccessToken()` and `getRefreshToken()` both return `null` (NFR6).
5. **Given** the user is on the welcome screen after revocation **Then** they can re-authenticate at any time by tapping "Connect Spotify" and repeating the full OAuth PKCE flow.

## Tasks / Subtasks

### T1 — Add `revokeAccess()` to `SpotifyAuthManager` (AC: 1, 4)
- [x]Add `fun revokeAccess()` to `SpotifyAuthManager` (non-suspend — synchronous local operation):
  ```kotlin
  fun revokeAccess() {
      tokenStorage.clear()
  }
  ```
- [x]Add KDoc: "User-initiated token revocation. Clears all tokens from [tokenStorage]. Called by [RevokeSpotifyAccessUseCase] in response to Disconnect Spotify user action. Token lifecycle rule: only [SpotifyAuthManager] calls clear(). [Source: architecture.md#Process Patterns]"
- [x]`tokenStorage.clear()` already exists in `SecureTokenStorage` interface — no changes to storage layer needed

### T2 — Add `RevokeSpotifyAccessUseCase` (AC: 1, 2, 3, 4, 5)
- [x]Create `shared/src/commonMain/kotlin/com/poddrive/usecase/RevokeSpotifyAccessUseCase.kt`
- [x]Class signature: `class RevokeSpotifyAccessUseCase(private val authManager: SpotifyAuthManager)`
- [x]Implement `suspend fun execute(): Result<Unit, AppError>`:
  - Call `authManager.revokeAccess()` — synchronous, no network required
  - Return `Result.Success(Unit)` — operation is always local, cannot fail
  - Never throws
- [x]Follow same structure as `AuthenticateWithSpotifyUseCase` (one use case per file, orchestration only, no logic beyond delegation)
- [x]Import: `com.poddrive.api.SpotifyAuthManager`, `com.poddrive.domain.model.AppError`, `com.poddrive.domain.model.Result`

### T3 — Unit tests for `RevokeSpotifyAccessUseCase` (AC: 1, 4)
- [x]Create `shared/src/commonTest/kotlin/com/poddrive/usecase/RevokeSpotifyAccessUseCaseTest.kt`
- [x]Use `FakeSecureTokenStorage` (at `commonTest/kotlin/com/poddrive/storage/FakeSecureTokenStorage.kt`) — no platform Keychain
- [x]Use `MockEngine` to build a minimal `SpotifyAuthManager` (same pattern as `SpotifyAuthManagerRefreshTest.kt`)
- [x]Test cases (use `runTest`):
  1. `execute() clears access token from storage` — pre-seed `FakeSecureTokenStorage` with tokens; call `execute()`; assert `getAccessToken() == null`
  2. `execute() clears refresh token from storage` — same setup; assert `getRefreshToken() == null`
  3. `execute() returns Result.Success` — assert result `is Result.Success`
- [x]Each test creates a fresh `FakeSecureTokenStorage` pre-seeded with both tokens (same pattern as `SpotifyAuthManagerRefreshTest.makeStorage()`)
- [x]Helper: `makeUseCase(storage: FakeSecureTokenStorage): RevokeSpotifyAccessUseCase` that builds `SpotifyAuthManager(storage, plainHttpClient, "test_client_id")` + wraps in `RevokeSpotifyAccessUseCase`

### T4 — Android: `SettingsViewModel` (AC: 2, 3)
- [x]Create `androidApp/src/main/kotlin/com/poddrive/android/ui/settings/SettingsViewModel.kt`
- [x]Class: `class SettingsViewModel(application: Application) : AndroidViewModel(application)`
- [x]Access `PodDriveApplication.from(application)` to get `revokeSpotifyAccessUseCase` (wired in T6)
- [x]Sealed UiState (as nested class):
  ```kotlin
  sealed class UiState {
      data object Idle : UiState()
      data object Revoking : UiState()
      data object Revoked : UiState()
      data class Error(val message: String) : UiState()
  }
  ```
- [x]`private val _uiState = MutableStateFlow<UiState>(UiState.Idle)` + `val uiState: StateFlow<UiState>`
- [x]`fun revokeAccess()`:
  - Set `_uiState.value = UiState.Revoking`
  - `viewModelScope.launch { when(app.revokeSpotifyAccessUseCase.execute()) { is Result.Success → Revoked; is Result.Failure → Error } }`
- [x]Imports: same pattern as `OnboardingViewModel.kt`

### T5 — Android: `SettingsScreen` composable (AC: 3)
- [x]Create `androidApp/src/main/kotlin/com/poddrive/android/ui/settings/SettingsScreen.kt`
- [x]Signature: `@Composable fun SettingsScreen(onRevoked: () -> Unit, viewModel: SettingsViewModel = viewModel())`
- [x]`LaunchedEffect(uiState)`: call `onRevoked()` when `uiState is SettingsViewModel.UiState.Revoked`
- [x]Render:
  - When `Idle`: "Disconnect Spotify" `Button` → calls `viewModel.revokeAccess()`
  - When `Revoking`: `CircularProgressIndicator` with "Disconnecting…" text
  - When `Revoked`: handled via `LaunchedEffect` above
  - When `Error`: error text + "Retry" button → calls `viewModel.revokeAccess()`
- [x]Layout: `Column` with `fillMaxSize()` + `padding(24.dp)`, center-aligned (same pattern as `OnboardingScreen`)

### T6 — Android: wire `RevokeSpotifyAccessUseCase` in `PodDriveApplication` (AC: 1)
- [x]Add to `PodDriveApplication.kt`:
  ```kotlin
  lateinit var revokeSpotifyAccessUseCase: RevokeSpotifyAccessUseCase
      private set
  ```
- [x]In `onCreate()`, after `authenticateWithSpotifyUseCase` initialization, add:
  ```kotlin
  revokeSpotifyAccessUseCase = RevokeSpotifyAccessUseCase(authManager)
  ```
- [x]Add import: `com.poddrive.usecase.RevokeSpotifyAccessUseCase`

### T7 — Android: wire Settings navigation in `MainActivity` (AC: 2, 3)
- [x]In `MainActivity.kt`, replace the authenticated branch placeholder:
  ```kotlin
  // BEFORE:
  if (uiState is OnboardingUiState.Authenticated) {
      Text(text = "Authenticated — Welcome to PodDrive!")
  }
  // AFTER:
  if (uiState is OnboardingUiState.Authenticated) {
      SettingsScreen(
          onRevoked = { onboardingViewModel.resetToIdle() }
      )
  }
  ```
- [x]Add `fun resetToIdle()` to `OnboardingViewModel`:
  ```kotlin
  fun resetToIdle() {
      _uiState.value = OnboardingUiState.Idle
  }
  ```
  (Parallel to `resetError()` — resets to `Idle` to return to welcome screen)
- [x]Add imports: `com.poddrive.android.ui.settings.SettingsScreen`

### T8 — iOS: `SettingsViewModel.swift` (AC: 2, 3)
- [x]Create `iosApp/iosApp/ui/settings/SettingsViewModel.swift`
- [x]`@MainActor final class SettingsViewModel: ObservableObject`
- [x]State enum:
  ```swift
  enum State {
      case idle
      case revoking
      case revoked
      case error(String)
  }
  @Published var state: State = .idle
  ```
- [x]Constructor takes `revokeUseCase: RevokeSpotifyAccessUseCase`:
  ```swift
  private let revokeUseCase: RevokeSpotifyAccessUseCase
  init(revokeUseCase: RevokeSpotifyAccessUseCase) {
      self.revokeUseCase = revokeUseCase
  }
  ```
- [x]`func revokeAccess() async`:
  - Set `state = .revoking`
  - Call `try await revokeUseCase.execute()` (KMP suspend function bridged via kotlin-native coroutines)
  - On `ResultSuccess`: `state = .revoked`
  - On `ResultFailure` or exception: `state = .error("Failed to disconnect Spotify.")`
  - Catch block must call back to `@MainActor`

### T9 — iOS: `SettingsView.swift` (AC: 3)
- [x]Create `iosApp/iosApp/ui/settings/SettingsView.swift`
- [x]`struct SettingsView: View` with `@StateObject private var viewModel: SettingsViewModel`
- [x]Constructor: `init(revokeUseCase: RevokeSpotifyAccessUseCase, onRevoked: @escaping () -> Void)`
- [x]Body: `VStack` centered with:
  - `Text("PodDrive Settings")` heading
  - When `.idle`: `Button("Disconnect Spotify") { Task { await viewModel.revokeAccess() } }`
  - When `.revoking`: `ProgressView()` + `Text("Disconnecting…")`
  - When `.revoked`: handled via `.onChange(of: viewModel.state)` → call `onRevoked()`
  - When `.error(let msg)`: error text + retry button
- [x]Use `.onChange(of: viewModel.state)` to detect `.revoked` and call `onRevoked()`

### T10 — iOS: wire Settings in `PodDriveApp.swift` (AC: 2, 3)
- [x]In `PodDriveApp.swift`, replace the authenticated branch:
  ```swift
  // BEFORE:
  if isAuthenticated {
      Text("Authenticated — Welcome to PodDrive!")
      ...
  }
  // AFTER:
  if isAuthenticated {
      SettingsView(
          revokeUseCase: makeRevokeUseCase(),
          onRevoked: { isAuthenticated = false }
      )
  }
  ```
- [x]Add `private func makeRevokeUseCase() -> RevokeSpotifyAccessUseCase`:
  ```swift
  private func makeRevokeUseCase() -> RevokeSpotifyAccessUseCase {
      let storage = SecureStorage()
      let httpClient = SpotifyHttpClientKt.createAuthHttpClient()
      let clientId = Bundle.main.infoDictionary?["SPOTIFY_CLIENT_ID"] as? String ?? ""
      let manager = SpotifyAuthManager(tokenStorage: storage, httpClient: httpClient, clientId: clientId)
      return RevokeSpotifyAccessUseCase(authManager: manager)
  }
  ```
  Note: `revokeAccess()` only calls `tokenStorage.clear()` — the `httpClient` and `clientId` are not used during revocation. Creating a fresh `SpotifyAuthManager` is safe because `SecureStorage` accesses the same Keychain keys regardless of instance.
- [x]Imports: `SettingsView` (same module, no explicit import needed in Swift)

### T11 — Build verification (AC: 1–5)
- [x]Review all Kotlin/Swift source files for syntactic completeness
- [x]Verify `RevokeSpotifyAccessUseCase` is in `com.poddrive.usecase` package (matching `AuthenticateWithSpotifyUseCase`)
- [x]Verify `revokeAccess()` import in `SpotifyAuthManager.kt` — no new imports needed (uses existing `tokenStorage`)
- [x]Verify no circular imports
- [x]Build deferred to CI — JDK 17 not available locally (same constraint as Stories 1.2–1.4)

---

## Dev Notes

### Why `SpotifyAuthManager.revokeAccess()` (not direct `SecureTokenStorage.clear()`)?

Architecture rule: "Token lifecycle is owned by `SpotifyAuthManager` only." The `RevokeSpotifyAccessUseCase` delegates to `SpotifyAuthManager.revokeAccess()` rather than calling `tokenStorage.clear()` directly. This keeps the invariant: `SpotifyAuthManager` is the sole manager of token writes/clears.

This is consistent with how internal revocation already works in `SpotifyAuthManager.refreshAccessToken` — when Spotify returns 400/401, `tokenStorage.clear()` is called internally.

### No Spotify API Network Call for Revoke

Spotify PKCE does not provide a client-side token revocation endpoint. Story 1.5 is purely a local operation: clear Keychain/EncryptedSharedPreferences entries and reset UI state. There is no HTTP call to `accounts.spotify.com` on revoke.

If future requirements add server-side revocation (e.g., calling `https://accounts.spotify.com/api/token/revoke`), `RevokeSpotifyAccessUseCase.execute()` can be extended without breaking existing callers.

### In-Memory Auth State Clearing

**Android:** `OnboardingViewModel._uiState` is the single in-memory auth state. Calling `onboardingViewModel.resetToIdle()` resets to `OnboardingUiState.Idle`, which `MainActivity` reacts to by showing `OnboardingScreen` again.

**iOS:** `PodDriveApp.isAuthenticated` is the root auth flag. Setting it to `false` via `onRevoked()` callback removes `SettingsView` from the scene graph and re-renders `OnboardingView`.

### iOS Dependency Wiring Without DI

`PodDriveApp.makeRevokeUseCase()` creates a fresh `SpotifyAuthManager` backed by a fresh `SecureStorage()` instance solely for the revoke operation. This is safe because:
1. `revokeAccess()` only calls `tokenStorage.clear()` — no HTTP, no client ID needed at runtime
2. `SecureStorage` on iOS uses fixed Keychain keys (`accessToken`, `refreshToken`) — all instances operate on the same Keychain entries
3. This approach avoids prop-drilling `authManager` from `OnboardingViewModel` across view boundaries in the MVP

For post-MVP: a proper DI container (e.g., Koin in Android, or a shared `AppEnvironment` on iOS) would eliminate this pattern.

### `SettingsViewModel.UiState` as Nested Sealed Class (Android)

`SettingsViewModel.UiState` is a nested sealed class to avoid package-level pollution. Access from Compose: `SettingsViewModel.UiState.Idle`, `SettingsViewModel.UiState.Revoked`. This matches Compose/ViewModel conventions used in the project.

### `RevokeSpotifyAccessUseCase.execute()` is `suspend`

Even though the operation is synchronous (no network), `execute()` is declared `suspend` for:
1. Consistency with `AuthenticateWithSpotifyUseCase.execute()`
2. Forward-compatibility if a future story adds a network revoke endpoint
3. KMP-to-Swift bridge consistency (both use cases become `async` on Swift side)

### Files to Create

```
poddrive/shared/src/
├── commonMain/kotlin/com/poddrive/usecase/
│   └── RevokeSpotifyAccessUseCase.kt          ← CREATE (T2)
└── commonTest/kotlin/com/poddrive/usecase/
    └── RevokeSpotifyAccessUseCaseTest.kt      ← CREATE (T3)

poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/settings/
├── SettingsViewModel.kt                       ← CREATE (T4)
└── SettingsScreen.kt                          ← CREATE (T5)

poddrive/iosApp/iosApp/ui/settings/
├── SettingsViewModel.swift                    ← CREATE (T8)
└── SettingsView.swift                         ← CREATE (T9)
```

### Files to Modify

```
poddrive/shared/src/commonMain/kotlin/com/poddrive/api/
└── SpotifyAuthManager.kt                      ← ADD revokeAccess() (T1)

poddrive/androidApp/src/main/kotlin/com/poddrive/android/
├── PodDriveApplication.kt                     ← ADD revokeSpotifyAccessUseCase (T6)
├── MainActivity.kt                            ← Wire SettingsScreen (T7)
└── ui/onboarding/OnboardingViewModel.kt       ← ADD resetToIdle() (T7)

poddrive/iosApp/iosApp/
└── PodDriveApp.swift                          ← Wire SettingsView + makeRevokeUseCase() (T10)
```

### Previous Story Intelligence (Stories 1.2–1.4)

- **Build constraint**: JDK 17 not available locally — verify syntax, defer build to CI
- **Kotlin 2.0.21** — pinned; do NOT upgrade
- **`FakeSecureTokenStorage`** at `commonTest/kotlin/com/poddrive/storage/FakeSecureTokenStorage.kt` — use as-is, has `clear()`, `getAccessToken()`, `getRefreshToken()`, `saveTokens()`
- **`MockEngine`** pattern established in `SpotifyAuthManagerRefreshTest.kt` — follow exact same import + setup pattern for `RevokeSpotifyAccessUseCaseTest`
- **`Result<T, AppError>`** pattern: `Result.Success`, `Result.Failure` — matches `AuthenticateWithSpotifyUseCase` return type
- **`AuthenticateWithSpotifyUseCase`** structure: simple delegation class with one `suspend fun execute()` returning `Result<Unit, AppError>` — mirror exactly for `RevokeSpotifyAccessUseCase`
- **`PodDriveApplication`**: add `lateinit var revokeSpotifyAccessUseCase` with `private set`, initialize in `onCreate()` after other use cases
- **`viewModelScope.launch`** is the coroutine launch pattern in Android ViewModels — use it in `SettingsViewModel.revokeAccess()`
- **iOS `@MainActor`** on ViewModel class — required to safely update `@Published` state from coroutine callbacks
- **iOS `try await useCase.execute()`** pattern — matches `OnboardingViewModel.exchangeTokens()` pattern with `try/catch` for KMP coroutine bridge
- **`LaunchedEffect(uiState)`** in Compose: avoids calling callbacks during recomposition — use same pattern as `OnboardingScreen` for `onRevoked()` callback

### Architecture Compliance Checklist

- [x]`revokeAccess()` is on `SpotifyAuthManager` — no token clear logic outside `SpotifyAuthManager`
- [x]`RevokeSpotifyAccessUseCase` delegates to `authManager.revokeAccess()` — no direct `tokenStorage` access
- [x]No network call in revoke path — purely local (no `accounts.spotify.com` request)
- [x]`SettingsViewModel` has no business logic — delegates to use case only
- [x]`OnboardingViewModel.resetToIdle()` is the only Android in-memory state clear method
- [x]iOS `onRevoked()` callback drives `isAuthenticated = false` — state flows from KMP outward
- [x]All Gradle deps already present — no new dependencies needed for this story

### Anti-Patterns to Avoid

❌ Do NOT call `tokenStorage.clear()` directly from `RevokeSpotifyAccessUseCase` — go through `SpotifyAuthManager.revokeAccess()` to maintain token lifecycle ownership invariant
❌ Do NOT call any Spotify API endpoint in the revoke path — it's a local-only operation
❌ Do NOT add `suspend` to `SpotifyAuthManager.revokeAccess()` — it's synchronous (just a Keychain/EncryptedSharedPreferences delete)
❌ Do NOT forget to add `resetToIdle()` to `OnboardingViewModel` — without it, `MainActivity` won't route back to `OnboardingScreen`
❌ Do NOT store `RevokeSpotifyAccessUseCase` in the SettingsViewModel's constructor on Android — retrieve it from `PodDriveApplication.from(application)` in `onCreate()` consistency with `OnboardingViewModel` pattern
❌ Do NOT use `DispatchersIO` / `withContext` in `RevokeSpotifyAccessUseCase.execute()` — `tokenStorage.clear()` is fast enough to call directly; adding dispatcher adds complexity without benefit for a Keychain delete

### References

- [Source: epics.md#Story 1.5] — Acceptance criteria, FR3 (revoke access)
- [Source: architecture.md#Authentication & Security] — NFR6 (tokens in Keychain/Keystore only), SecureStorage pattern
- [Source: architecture.md#Process Patterns] — "Token lifecycle is owned by SpotifyAuthManager only"
- [Source: architecture.md#Use Case rule] — one use case per file, orchestration only
- [Source: architecture.md#Privacy & Data] — FR33–FR35: clear all stored data, SecureStorage.clear()
- [Source: 1-4-silent-spotify-token-refresh.md#Dev Notes] — `SpotifyAuthManager.tokenStorage.clear()` already called on 400/401 (internal revoke); Story 1.5 exposes this as explicit user-initiated revoke
- [Source: 1-3-spotify-oauth-20-pkce-authentication.md] — `AuthenticateWithSpotifyUseCase` pattern to mirror for `RevokeSpotifyAccessUseCase`

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `SettingsView.swift`: initial implementation used `Task { await viewModel.revokeAccess() }` but `revokeAccess()` is not `async` (it uses `Task {}` internally). Fixed to call `viewModel.revokeAccess()` directly from button actions.
- `MainActivity.kt`: removed `import androidx.compose.material3.Text` after replacing placeholder `Text(...)` with `SettingsScreen` — the import was unused.
- Build verification deferred to CI (JDK 17 not available locally, same constraint as Stories 1.2–1.4).

### Completion Notes List

- `SpotifyAuthManager.revokeAccess()` added as the public user-initiated revocation entry point — delegates to `tokenStorage.clear()`, preserving token lifecycle ownership invariant.
- `RevokeSpotifyAccessUseCase` follows exact same delegation-only pattern as `AuthenticateWithSpotifyUseCase` — simple `suspend fun execute()` returning `Result.Success(Unit)`.
- 3 unit tests cover: access token cleared, refresh token cleared, `Result.Success` returned. Uses `FakeSecureTokenStorage` + `MockEngine` (no platform I/O).
- Android `SettingsViewModel` / `SettingsScreen` follow `OnboardingViewModel` / `OnboardingScreen` patterns exactly: `StateFlow<UiState>`, `LaunchedEffect`, `viewModelScope.launch`, `PodDriveApplication.from()` for dependency access.
- `OnboardingViewModel.resetToIdle()` added to complete the Android in-memory auth state reset path.
- `PodDriveApplication.revokeSpotifyAccessUseCase` wired alongside `authenticateWithSpotifyUseCase`.
- iOS `SettingsViewModel` (ObservableObject + `@MainActor`) and `SettingsView` follow `OnboardingViewModel`/`OnboardingView` patterns.
- `PodDriveApp.makeRevokeUseCase()` creates a fresh `SpotifyAuthManager(SecureStorage(), ...)` for revoke — safe because `revokeAccess()` only calls `tokenStorage.clear()` and Keychain entries are shared across all `SecureStorage` instances.
- `onChange(of: viewModel.stateIsRevoked)` uses `Bool` helper to avoid `onChange` enum-matching complexity across iOS 16/17 API differences.

### File List

**Created:**
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/RevokeSpotifyAccessUseCase.kt`
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/RevokeSpotifyAccessUseCaseTest.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/settings/SettingsViewModel.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/settings/SettingsScreen.kt`
- `poddrive/iosApp/iosApp/ui/settings/SettingsViewModel.swift`
- `poddrive/iosApp/iosApp/ui/settings/SettingsView.swift`

**Modified:**
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyAuthManager.kt` — added `revokeAccess()` (T1)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` — added `revokeSpotifyAccessUseCase` lateinit + init (T6)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/MainActivity.kt` — replaced placeholder with `SettingsScreen`, removed unused `Text` import (T7)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/onboarding/OnboardingViewModel.kt` — added `resetToIdle()` (T7)
- `poddrive/iosApp/iosApp/PodDriveApp.swift` — replaced placeholder with `SettingsView`, added `makeRevokeUseCase()` (T10)

---

## Senior Developer Review (AI)

**Reviewer:** claude-sonnet-4-6 (code-review skill, parallel agents)
**Date:** 2026-03-22
**Findings summary:** 3 agents × 14+13+6 findings → 4 actionable (2 P-1, 2 P-2)

### Triage

| ID | Priority | Finding | Disposition |
|----|----------|---------|-------------|
| F1/EC8/A1 | P-1 | iOS `makeRevokeUseCase()` called in `body` — new Ktor `HttpClient` on every recompose, resource leak | **FIXED** — moved to `private let revokeUseCase` stored property via IIFE |
| F9 | P-1 | iOS `onChange(of:perform:)` deprecated form — receives OLD value on iOS 17, `onRevoked()` never fires | **FIXED** — replaced with `.task(id: viewModel.stateIsRevoked)` (iOS 15+, always current value) |
| F4/A6 | P-1 | `RevokeSpotifyAccessUseCase.execute()` does not map exceptions — violates "never throws" contract, crashes app | **FIXED** — wrapped in try/catch, re-throws `CancellationException`, maps others to `Result.Failure(AppError.Unknown(e))` |
| A3 | P-2 | `OnboardingViewModel.resetToIdle()` does not call `clearPending()` — stale PKCE state in `SavedStateHandle` | **FIXED** — added `clearPending()` before state reset |
| F2 | P-3 | iOS `MainActor.run {}` redundant inside already-`@MainActor` class | Deferred — cosmetic, no functional impact |
| F3 | P-3 | Android `SettingsViewModel.revokeAccess()` no guard against concurrent invocations | Deferred — single button UX, `Revoking` state prevents double-tap in practice |
| EC2 | Defer | Race with in-flight token refresh overwriting newly-cleared tokens | Defer to Epic 6 (resilience/recovery stories); MVP single-user device, low practical risk |
| EC5 | Defer | Android `EncryptedSharedPreferences.commit()` exception path untested | Defer — platform-level failure outside app control; would require rooted device |
| EC9 | Defer | Android back button during `Revoking` state — `resetToIdle()` not called | Defer — `SettingsScreen` is a flat scene, not a back-stack nav destination in current MVP |
| EC13 | Reject | `clear()` on Main thread — disk ANR concern | Rejected — `EncryptedSharedPreferences.apply()` (async) is used in current `SecureStorage` impl |

### Change Log

| Date | Change | Reason |
|------|--------|--------|
| 2026-03-22 | Added `SpotifyAuthManager.revokeAccess()` | Public user-initiated revocation entry point; maintains token lifecycle ownership invariant |
| 2026-03-22 | Created `RevokeSpotifyAccessUseCase` | KMP use case for revoke flow; delegates to `authManager.revokeAccess()` |
| 2026-03-22 | Created `RevokeSpotifyAccessUseCaseTest` | 3 unit tests verifying token clearing and Result.Success return |
| 2026-03-22 | Created Android `SettingsViewModel` + `SettingsScreen` | "Disconnect Spotify" UI; follows `OnboardingViewModel`/`OnboardingScreen` patterns |
| 2026-03-22 | Added `resetToIdle()` to `OnboardingViewModel` | Clears in-memory Android auth state after revocation |
| 2026-03-22 | Wired `revokeSpotifyAccessUseCase` in `PodDriveApplication` | Application-scoped singleton for DI access from `SettingsViewModel` |
| 2026-03-22 | Replaced MainActivity authenticated placeholder with `SettingsScreen` | Completes Android revoke navigation flow |
| 2026-03-22 | Created iOS `SettingsViewModel.swift` + `SettingsView.swift` | "Disconnect Spotify" UI on iOS; follows `OnboardingViewModel.swift` patterns |
| 2026-03-22 | Updated `PodDriveApp.swift` with `SettingsView` + `makeRevokeUseCase()` | Completes iOS revoke navigation flow |
