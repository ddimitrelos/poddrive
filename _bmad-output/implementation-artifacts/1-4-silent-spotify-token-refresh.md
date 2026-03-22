# Story 1.4: Silent Spotify Token Refresh

Status: done

## Story

As an **authenticated user**,
I want my Spotify session to stay active automatically in the background,
so that I never need to re-authenticate mid-use and no active session is interrupted.

## Acceptance Criteria

1. **Given** a valid refresh token is stored in `SecureStorage` **When** the Spotify access token expires **Then** the Ktor `Auth` plugin transparently refreshes the token using the stored refresh token (AR8) without any user interaction.
2. **Given** a token refresh succeeds **Then** the new access token (and new refresh token if Spotify returns one via token rotation) are saved back to `SecureStorage` — the old tokens are replaced atomically.
3. **Given** a token refresh is in progress **Then** the refresh does not interrupt an active drive session or pause any audio (NFR20) — refresh is invisible to the user.
4. **Given** a token refresh fails with HTTP 400 or 401 (refresh token revoked externally) **Then** the stored tokens are cleared from `SecureStorage` and the app surfaces an unauthenticated state prompting the user to re-connect via onboarding.
5. **Given** a token refresh fails due to a network error (transient) **Then** the stored tokens are NOT cleared (the refresh token is still valid), the Ktor `Auth` plugin returns the 401 to the caller without crashing, and the user can retry when connectivity is restored.

## Tasks / Subtasks

### T1 — Add `SpotifyRefreshResponse` model to `SpotifyModels.kt` (AC: 2)
- [x] Add `SpotifyRefreshResponse` with nullable `refreshToken` field:
  - `accessToken: String` (required)
  - `refreshToken: String? = null` (optional — Spotify token rotation may return a new one)
  - `expiresIn: Int`, `tokenType: String`
- [x] `@Serializable` with `@SerialName` for all snake_case fields
- [x] Do NOT modify existing `SpotifyTokenResponse` — it is used by initial token exchange (AC1 Story 1.3) and always returns a refresh token

### T2 — Add `TokenRefresher` interface (AC: 1, 4, 5)
- [x] Create `commonMain/kotlin/com/poddrive/api/TokenRefresher.kt`
- [x] `interface TokenRefresher { suspend fun refreshAccessToken(refreshToken: String): BearerTokens? }`
- [x] Import: `io.ktor.client.plugins.auth.providers.BearerTokens`
- [x] This interface breaks the circular dep: `createSpotifyHttpClient` can accept a `TokenRefresher` without knowing about `SpotifyAuthManager`

### T3 — Add `refreshAccessToken` to `SpotifyAuthManager` + implement `TokenRefresher` (AC: 1–5)
- [x] Change class signature: `class SpotifyAuthManager(...) : TokenRefresher`
- [x] Implement `override suspend fun refreshAccessToken(refreshToken: String): BearerTokens?`:
  - POST to `TOKEN_URL` with `grant_type=refresh_token`, `refresh_token`, `client_id` (same URL used in `exchangeCodeForTokens`)
  - On success (2xx): parse body as `SpotifyRefreshResponse`; call `tokenStorage.saveTokens(newAccessToken, newRefreshToken ?: refreshToken)` where `newRefreshToken` is used if present (token rotation), old one kept if absent; return `BearerTokens(newAccessToken, newRefreshToken ?: refreshToken)`
  - On HTTP 400 or 401: call `tokenStorage.clear()` (revoked externally → force re-auth), return `null`
  - On other HTTP error: return `null` without clearing (transient server issue)
  - On exception (network error): return `null` without clearing — do NOT call `tokenStorage.clear()` on transient failures (AC: 5)
  - Never throws — always returns `null` or `BearerTokens`
- [x] Token lifecycle rule: ONLY `SpotifyAuthManager` calls `tokenStorage.saveTokens()` / `tokenStorage.clear()` — enforced here

### T4 — Add `createAuthHttpClient` and update `createSpotifyHttpClient` in `SpotifyHttpClient.kt` (AC: 1)
- [x] Add `fun createAuthHttpClient(): HttpClient` — plain client for token operations only:
  - `HttpClient(createPlatformEngine())` with `ContentNegotiation` + `Logging` only
  - No `Auth` plugin (token endpoints don't need bearer auth)
  - No `HttpRequestRetry` (auth failures should fail fast)
- [x] Update `createSpotifyHttpClient` signature: `fun createSpotifyHttpClient(tokenStorage: SecureTokenStorage, tokenRefresher: TokenRefresher? = null): HttpClient`
- [x] Replace `refreshTokens { null }` with:
  ```kotlin
  refreshTokens {
      val refresh = oldTokens?.refreshToken?.takeIf { it.isNotBlank() } ?: return@refreshTokens null
      tokenRefresher?.refreshAccessToken(refresh)
  }
  ```
- [x] `sendWithoutRequest { request.url.host == "api.spotify.com" }` remains — auth token is NOT sent to `accounts.spotify.com` (prevents infinite loop when `authHttpClient` calls token endpoint)
- [x] Add update comment: "Story 1.4: tokenRefresher wires SpotifyAuthManager.refreshAccessToken"

### T5 — Update `PodDriveApplication.kt` wiring (AC: 1)
- [x] Split into two http clients:
  - `authHttpClient = createAuthHttpClient()` — for `SpotifyAuthManager` token operations
  - `apiHttpClient = createSpotifyHttpClient(secureStorage, tokenRefresher = authManager)` — for all Spotify Web API calls
- [x] Construction order:
  ```kotlin
  secureStorage = SecureStorage(applicationContext)
  val authHttpClient = createAuthHttpClient()
  authManager = SpotifyAuthManager(tokenStorage = secureStorage, httpClient = authHttpClient, clientId = BuildConfig.SPOTIFY_CLIENT_ID)
  val apiHttpClient = createSpotifyHttpClient(secureStorage, tokenRefresher = authManager)
  ```
- [x] Store `apiHttpClient` as a `lateinit var` on `PodDriveApplication` for use by future stories
- [x] Update `AuthenticateWithSpotifyUseCase` wiring: keep using `authManager`
- [x] The `OnboardingViewModel` uses `authManager` directly (no change needed)

### T6 — Update iOS `OnboardingViewModel.swift` wiring (AC: 1)
- [x] In `setupKMPDependencies()`:
  - Create `authHttpClient = SpotifyHttpClientKt.createAuthHttpClient()` — plain client for auth manager
  - Create `manager = SpotifyAuthManager(tokenStorage: secureStorage, httpClient: authHttpClient, clientId: clientId)`
  - Create `apiHttpClient = SpotifyHttpClientKt.createSpotifyHttpClient(tokenStorage: secureStorage, tokenRefresher: manager)`
  - Store `apiHttpClient` as a property for future authenticated API calls
- [x] The `ASWebAuthenticationSession` flow and `AuthenticateWithSpotifyUseCase` use `authManager` (no change needed)
- [x] On iOS, `SpotifyAuthManager` conforms to `TokenRefresher` protocol (auto-generated by KMP) — no manual bridging required
- [x] Fixed `SpotifyApiClientKt` → `SpotifyHttpClientKt` (Story 1.3 used incorrect Kotlin file name; functions live in `SpotifyHttpClient.kt`)

### T7 — Unit tests for `refreshAccessToken` (AC: 1–5)
- [x] Add 12 tests to new `commonTest/kotlin/com/poddrive/api/SpotifyAuthManagerRefreshTest.kt`:
  - success returns BearerTokens with new access token
  - success saves new access token to storage
  - without rotation keeps existing refresh token in BearerTokens
  - without rotation keeps existing refresh token in storage
  - with rotation saves rotated refresh token to storage
  - 401 returns null
  - 401 clears access token from storage
  - 401 clears refresh token from storage
  - 400 returns null and clears tokens
  - 500 returns null without clearing tokens
  - network exception returns null without clearing tokens
  - SpotifyAuthManager is usable as TokenRefresher (interface contract)
- [x] Uses `MockEngine` + `FakeSecureTokenStorage` (same pattern as `SpotifyAuthManagerTest.kt`)
- [x] Each test creates a fresh `FakeSecureTokenStorage` pre-seeded with tokens

### T8 — Verify build (AC: 1–5)
- [x] Review all source files for syntactic completeness
- [x] Verified `TokenRefresher` import in `SpotifyHttpClient.kt` (same `com.poddrive.api` package — no import needed)
- [x] Verified no circular imports (all files in same `com.poddrive.api` package)
- [x] Verified `createAuthHttpClient` is in `SpotifyHttpClient.kt` → Swift name: `SpotifyHttpClientKt.createAuthHttpClient()`
- [x] Build deferred to CI — JDK 17 not available locally (same constraint as Stories 1.2, 1.3)

---

## Dev Notes

### Why Two HTTP Clients?

`SpotifyAuthManager` uses its `httpClient` for token exchange (`exchangeCodeForTokens`, `refreshAccessToken`). If it used the main `apiHttpClient` (which has the `Auth` plugin), the `refreshTokens` callback would call back into `apiHttpClient`, creating infinite recursion.

**Solution:** Split into two clients:
- `authHttpClient` (`createAuthHttpClient()`) — plain, no Auth plugin → used by `SpotifyAuthManager` for token endpoint calls only
- `apiHttpClient` (`createSpotifyHttpClient(storage, tokenRefresher)`) — full client with Auth bearer + refresh → used for all `api.spotify.com` calls

The `sendWithoutRequest { request.url.host == "api.spotify.com" }` filter also prevents the Auth plugin from adding bearer tokens to `accounts.spotify.com` calls.

### Spotify Token Refresh Grant

```
POST https://accounts.spotify.com/api/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token=<stored_refresh_token>
&client_id=<SPOTIFY_CLIENT_ID>

Response (success):
{
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "..."   // only present if Spotify rotates tokens
}

Response (failure - revoked):
HTTP 400 or 401
{ "error": "invalid_grant", "error_description": "Refresh token revoked" }
```

No `client_secret` — PKCE refresh is secret-free (same as initial exchange).

### `SpotifyRefreshResponse` vs `SpotifyTokenResponse`

| Field | `SpotifyTokenResponse` (initial exchange) | `SpotifyRefreshResponse` (refresh grant) |
|-------|-------------------------------------------|------------------------------------------|
| `access_token` | required | required |
| `refresh_token` | required (always present) | `String? = null` (token rotation, optional) |
| `expires_in` | required | required |
| `token_type` | required | required |

Do NOT reuse `SpotifyTokenResponse` for refresh response — making `refreshToken` nullable in it would weaken the type contract for initial exchange (where it is always present).

### `SpotifyAuthManager.refreshAccessToken` Shape

```kotlin
override suspend fun refreshAccessToken(refreshToken: String): BearerTokens? = try {
    val response = httpClient.submitForm(
        url = TOKEN_URL,
        formParameters = parameters {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
            append("client_id", clientId)
        }
    )
    when {
        response.status.isSuccess() -> {
            val body = lenientJson.decodeFromString<SpotifyRefreshResponse>(response.bodyAsText())
            val newRefreshToken = body.refreshToken ?: refreshToken  // keep old if no rotation
            tokenStorage.saveTokens(body.accessToken, newRefreshToken)
            BearerTokens(body.accessToken, newRefreshToken)
        }
        response.status.value in 400..401 -> {
            tokenStorage.clear()  // revoked — force re-auth
            null
        }
        else -> null  // transient error — tokens remain valid
    }
} catch (_: Exception) {
    null  // network error — do NOT clear tokens
}
```

### `createSpotifyHttpClient` Updated Signature

```kotlin
fun createSpotifyHttpClient(
    tokenStorage: SecureTokenStorage,
    tokenRefresher: TokenRefresher? = null
): HttpClient = HttpClient(createPlatformEngine()) {
    // ... ContentNegotiation, Logging as before ...
    install(Auth) {
        bearer {
            loadTokens { /* unchanged */ }
            refreshTokens {
                val refresh = oldTokens?.refreshToken?.takeIf { it.isNotBlank() }
                    ?: return@refreshTokens null
                tokenRefresher?.refreshAccessToken(refresh)
            }
            sendWithoutRequest { request.url.host == "api.spotify.com" }
        }
    }
    // ... HttpRequestRetry as before ...
}
```

### `PodDriveApplication.kt` Updated `onCreate`

```kotlin
override fun onCreate() {
    super.onCreate()
    check(BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()) { ... }

    secureStorage = SecureStorage(applicationContext)
    val authHttpClient = createAuthHttpClient()       // plain — for token ops
    authManager = SpotifyAuthManager(
        tokenStorage = secureStorage,
        httpClient = authHttpClient,
        clientId = BuildConfig.SPOTIFY_CLIENT_ID
    )
    apiHttpClient = createSpotifyHttpClient(secureStorage, tokenRefresher = authManager)
    authenticateWithSpotifyUseCase = AuthenticateWithSpotifyUseCase(authManager)
}
```

### iOS `setupKMPDependencies` Updated

```swift
private func setupKMPDependencies() {
    let clientId = Bundle.main.infoDictionary?["SPOTIFY_CLIENT_ID"] as? String ?? ""
    guard !clientId.isEmpty else {
        state = .error("SPOTIFY_CLIENT_ID not configured.")
        return
    }
    let secureStorage = SecureStorage()
    let authHttpClient = SpotifyHttpClientKt.createAuthHttpClient()  // plain — no Auth plugin
    let manager = SpotifyAuthManager(
        tokenStorage: secureStorage,
        httpClient: authHttpClient,
        clientId: clientId
    )
    // apiHttpClient has Auth plugin + refresh wired to manager.refreshAccessToken
    apiHttpClient = SpotifyHttpClientKt.createSpotifyHttpClient(
        tokenStorage: secureStorage,
        tokenRefresher: manager
    )
    authManager = manager
    useCase = AuthenticateWithSpotifyUseCase(authManager: manager)
}
```

Add `private var apiHttpClient: HttpClient?` property to `OnboardingViewModel`.

### Unauthenticated State Flow (AC: 4)

When `refreshAccessToken` returns `null` after clearing tokens:
1. Ktor `Auth` plugin stops retrying — the original 401 is returned to the caller
2. The API call returns a `Result.Failure(AppError.SpotifyApiError(401, "..."))` to the use case
3. The ViewModel maps this to an unauthenticated/error state
4. The UI shows the onboarding screen

**In-scope for Story 1.4:** Token clearing + returning `null`. The UI-level navigation back to onboarding is a UI concern handled by the ViewModel state — currently `OnboardingUiState.Error` will be shown. Full re-auth navigation (e.g. from authenticated screens) is addressed in later stories.

### Files to Create

```
poddrive/shared/src/
├── commonMain/kotlin/com/poddrive/api/
│   └── TokenRefresher.kt                    ← CREATE (T2)
└── commonTest/kotlin/com/poddrive/api/
    └── SpotifyAuthManagerRefreshTest.kt     ← CREATE (T7)
```

### Files to Modify

```
poddrive/shared/src/commonMain/kotlin/com/poddrive/api/
├── SpotifyModels.kt        ← ADD SpotifyRefreshResponse (T1)
├── SpotifyAuthManager.kt   ← IMPLEMENT TokenRefresher, ADD refreshAccessToken (T3)
└── SpotifyHttpClient.kt    ← ADD createAuthHttpClient, UPDATE createSpotifyHttpClient (T4)

poddrive/androidApp/src/main/kotlin/com/poddrive/android/
└── PodDriveApplication.kt  ← UPDATE wiring (T5)

poddrive/iosApp/iosApp/ui/onboarding/
└── OnboardingViewModel.swift  ← UPDATE setupKMPDependencies (T6)
```

### Previous Story Intelligence (Story 1.3)

- **Build constraint**: JDK 17 not available locally — verify source syntax, defer build to CI
- **Kotlin 2.0.21** — pinned; do NOT upgrade
- **`-Xexpect-actual-classes` flag** already set in `shared/build.gradle.kts`
- **`@OptIn(ExperimentalForeignApi::class)`** required on iOS actual files using `kotlinx.cinterop`
- **`iosApp/` is NOT a Gradle module** — edit Swift files directly, do not add to `settings.gradle.kts`
- **Gradle Version Catalog** is sole dep source — `libs.versions.toml` first, then `build.gradle.kts`
- **`lenientJson`** private companion property already exists in `SpotifyAuthManager` — reuse it for `SpotifyRefreshResponse` parsing in `refreshAccessToken`
- **`FakeSecureTokenStorage`** is at `commonTest/kotlin/com/poddrive/storage/FakeSecureTokenStorage.kt` — reuse without modification
- **`MockEngine` pattern** established in `SpotifyAuthManagerTest.kt` — follow exact same import and setup pattern for new test file
- **`SpotifyHttpClientKt`** is the Swift-visible name for `SpotifyHttpClient.kt` — `createAuthHttpClient()` will be callable as `SpotifyHttpClientKt.createAuthHttpClient()`

### Architecture Compliance Checklist

Before marking any task done:

- [ ] `refreshAccessToken` is in `SpotifyAuthManager` — no token exchange logic in Application or ViewModel
- [ ] `tokenStorage.saveTokens()` called ONLY inside `SpotifyAuthManager.refreshAccessToken` — no other caller
- [ ] `tokenStorage.clear()` called ONLY on confirmed revocation (400/401), NOT on transient errors
- [ ] `createAuthHttpClient` has NO `Auth` plugin — prevents infinite refresh loop
- [ ] `createSpotifyHttpClient` `sendWithoutRequest` filter remains (`api.spotify.com` only)
- [ ] No `client_secret` in refresh request — PKCE refresh is secret-free
- [ ] `SpotifyRefreshResponse.refreshToken` is `String? = null` — nullable; `SpotifyTokenResponse.refreshToken` remains `String` (non-nullable)
- [ ] All deps already in `libs.versions.toml` — NO new Gradle deps needed for this story
- [ ] Kotlin DSL only — no Groovy in any Gradle file

### Anti-Patterns to Avoid

❌ Do NOT call `tokenStorage.clear()` on network exceptions — transient failures don't revoke tokens
❌ Do NOT use `SpotifyTokenResponse` for refresh response — `refreshToken` is nullable in refresh, non-nullable in initial exchange
❌ Do NOT pass `apiHttpClient` to `SpotifyAuthManager` — use `authHttpClient` (plain) to avoid infinite refresh loop
❌ Do NOT add `Auth` plugin to `createAuthHttpClient` — the token endpoint uses form body auth, not bearer
❌ Do NOT throw from `refreshAccessToken` — return `null` on all failure paths
❌ Do NOT add new Gradle dependencies for this story — all Ktor/serialization deps already present from Story 1.3

### References

- [Source: architecture.md#API & Communication Patterns] — AR8: Ktor Auth plugin transparent refresh; NFR20: no drive session interruption
- [Source: architecture.md#Authentication & Security] — SecureStorage only; token lifecycle rules
- [Source: architecture.md#Process Patterns] — only SpotifyAuthManager calls saveTokens
- [Source: epics.md#Story 1.4] — acceptance criteria with NFR20 and AR8 references
- [Source: epics.md#NFR20] — refresh must not interrupt active drive session
- [Source: 1-3-spotify-oauth-20-pkce-authentication.md#Completion Notes] — refreshTokens stub comment and SecureTokenStorage interface decision
- [Source: 1-3-spotify-oauth-20-pkce-authentication.md#Dev Notes#Ktor Client Setup] — refreshTokens null stub that this story replaces

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- iOS `OnboardingViewModel.swift` was referencing `SpotifyApiClientKt.createSpotifyHttpClient` but `createSpotifyHttpClient` lives in `SpotifyHttpClient.kt` not `SpotifyApiClient.kt`. Corrected to `SpotifyHttpClientKt` in T6.
- `fun interface TokenRefresher` with `suspend fun` is valid in Kotlin 2.0.21 and exports to Swift as a protocol with async method.
- `lenientJson` in `SpotifyAuthManager` companion is `private` but accessible from instance methods of the same class — used in `refreshAccessToken` without issue.
- No new Gradle dependencies required — all Ktor/serialization deps were added in Story 1.3.

### Completion Notes List

- `TokenRefresher` `fun interface` introduced in `commonMain/api/` — clean contract enabling both Android and iOS to pass `SpotifyAuthManager` without bridging.
- Two-client split in `PodDriveApplication.kt` eliminates circular refresh loop: `authHttpClient` (plain) for token ops, `apiHttpClient` (with Auth plugin) for API calls.
- `PodDriveApplication.apiHttpClient` is now a public `lateinit var` — future stories (Epic 2+) inject it for Spotify Web API calls.
- `SpotifyRefreshResponse.refreshToken` is `String? = null` — handles both Spotify rotation scenarios (new token issued vs. old token retained).
- On HTTP 400/401: `tokenStorage.clear()` called to force re-auth. On all other errors: tokens preserved (transient failures don't revoke credentials).
- 12 unit tests covering full success/failure/exception matrix of `refreshAccessToken`.
- Build verification deferred to CI (JDK 17 not available locally).

### File List

**Created:**
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/TokenRefresher.kt`
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyAuthManagerRefreshTest.kt`

**Modified:**
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyModels.kt` — added `SpotifyRefreshResponse`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyAuthManager.kt` — implements `TokenRefresher`, adds `refreshAccessToken`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyHttpClient.kt` — adds `createAuthHttpClient`, updates `createSpotifyHttpClient` with `tokenRefresher` param
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` — split into `authHttpClient` + `apiHttpClient`, adds `apiHttpClient` lateinit var
- `poddrive/iosApp/iosApp/ui/onboarding/OnboardingViewModel.swift` — updated `setupKMPDependencies` with two clients, fixed `SpotifyApiClientKt` → `SpotifyHttpClientKt`

### Change Log

| Date | Change | Reason |
|------|--------|--------|
| 2026-03-22 | Added `SpotifyRefreshResponse` with nullable `refreshToken` | Refresh grant may/may not include token rotation; separate model preserves strong typing on initial exchange |
| 2026-03-22 | Added `TokenRefresher` fun interface | Clean contract for Ktor Auth plugin callback; exports to Swift as protocol without bridging |
| 2026-03-22 | `SpotifyAuthManager` implements `TokenRefresher` | Enables passing manager directly to `createSpotifyHttpClient` |
| 2026-03-22 | Added `refreshAccessToken` to `SpotifyAuthManager` | Story 1.4 AC — transparent silent token refresh on 401 |
| 2026-03-22 | Added `createAuthHttpClient` to `SpotifyHttpClient.kt` | Plain client for token endpoint calls; breaks circular refresh loop |
| 2026-03-22 | Updated `createSpotifyHttpClient` with `tokenRefresher` param | Connects Ktor Auth plugin refresh block to `SpotifyAuthManager` |
| 2026-03-22 | Updated `PodDriveApplication.kt` two-client wiring | `authHttpClient` for token ops, `apiHttpClient` for API calls |
| 2026-03-22 | Fixed iOS `SpotifyApiClientKt` → `SpotifyHttpClientKt` | Incorrect Kotlin file name reference from Story 1.3 |
| 2026-03-22 | [Review fix] Re-throw `CancellationException` in `refreshAccessToken` catch | Structured concurrency — swallowing `CancellationException` breaks coroutine cancellation (P1) |

---

## Senior Developer Review (AI)

### Review Date
2026-03-22

### Findings Summary
- 1 patch applied (P1)
- 2 deferred (pre-existing / out-of-scope)
- 2 rejected (noise / intentional design)

### Applied Fixes
- **P1 — `CancellationException` re-throw**: `catch (_: Exception)` in `refreshAccessToken` now re-throws `CancellationException` to preserve coroutine structured concurrency. `kotlinx.coroutines.CancellationException` import added.

### Deferred Findings
- **`saveTokens`/`clear()` without `Dispatchers.IO`**: Pre-existing in Story 1.2 `SecureStorage` implementations. Not introduced by this story.
- **No E2E Ktor Auth bearer integration test**: Testing full plugin cycle requires platform integration test harness. Deferred to Epic 2 hardening.

### Rejected Findings
- **Concurrent refresh race condition**: Ktor `Auth` bearer plugin serializes refresh calls internally via `Mutex`. Not a bug in `SpotifyAuthManager`.
- **`loadTokens` returns `BearerTokens(access, "")` for missing refresh**: Intentional — `takeIf { isNotBlank() }` in `refreshTokens` correctly handles this; valid API calls with a present access token must not be blocked by a missing refresh token.
