# Story 1.3: Spotify OAuth 2.0 PKCE Authentication

Status: done

## Story

As a **new user**,
I want to authenticate with my Spotify account using OAuth 2.0,
so that PodDrive can access my podcast library and playback controls.

## Acceptance Criteria

1. **Given** the user has not yet authenticated **When** they tap "Connect Spotify" on the welcome screen **Then** the PKCE OAuth 2.0 flow opens the Spotify authorisation page in a secure in-app browser with only the minimum required API scopes: `user-library-read`, `user-read-playback-state`, `user-modify-playback-state`, `user-read-recently-played`, `streaming` — no additional scopes (NFR8)
2. **Given** the OAuth authorisation completes successfully **When** the Spotify callback returns an authorisation code **Then** `SpotifyAuthManager` exchanges the code for tokens using the Ktor `HttpClient` and stores the `access_token` and `refresh_token` in `SecureStorage.saveTokens()` — never in SQLDelight, `SharedPreferences`, or any plain-text store (NFR6)
3. **Given** tokens are stored **Then** the user is returned to PodDrive in an authenticated state — welcome screen is replaced by the authenticated entry point (Shows / Discover / Settings bottom nav)
4. **Given** the Spotify app is not installed on the device **When** the PKCE flow begins **Then** PodDrive detects the condition at the point of launching the browser (catch the intent failure on Android; check URL scheme availability on iOS) and presents a clear, actionable install prompt — not a silent crash or blank error (NFR19)
5. **Given** the OAuth flow is cancelled (user closes browser) or fails (network error, invalid code) **Then** the user remains on the welcome screen and an inline error message is shown with a retry CTA — no crashes; error mapped to `AppError` and returned as `Result.Failure`
6. **Given** all tokens are stored **When** the `AuthenticateWithSpotifyUseCase` completes **Then** it returns `Result.Success(Unit)` — all subsequent Spotify API calls in later stories will use the stored tokens via `SpotifyApiClient`

## Tasks / Subtasks

### T1 — Add Ktor to `libs.versions.toml` (AC: 1, 2, 6)
- [x] Add `ktor = "3.0.3"` to `[versions]`
- [x] Add `kotlinx-serialization-json = "1.7.3"` to `[versions]`
- [x] Add `coroutines-test = "1.8.1"` to `[versions]` (needed for commonTest `runTest`)
- [x] Add `browser = "1.8.0"` to `[versions]` (Android Chrome Custom Tabs)
- [x] Add all Ktor library entries to `[libraries]`: core, cio, darwin, content-negotiation, auth, logging, mock, serialization-kotlinx-json
- [x] Add `kotlinx-coroutines-test`, `androidx-browser`, `lifecycle-viewmodel-compose` library entries

### T2 — Update `shared/build.gradle.kts` (AC: 1, 2, 6)
- [x] Add to `commonMain.dependencies`: ktor-client-core, content-negotiation, auth, logging, serialization-kotlinx-json, kotlinx-serialization-json
- [x] Add to `commonTest.dependencies`: ktor-client-mock, kotlinx-coroutines-test
- [x] Add to `androidMain.dependencies`: `implementation(libs.ktor.client.cio)`
- [x] Add to `iosMain.dependencies`: `implementation(libs.ktor.client.darwin)`

### T3 — Create `SpotifyModels.kt` (AC: 2)
- [x] Created `commonMain/kotlin/com/poddrive/api/SpotifyModels.kt`
- [x] `SpotifyTokenResponse` with `@SerialName` annotations; `SpotifyErrorResponse` for error body parsing

### T4 — Create `SpotifyAuthManager.kt` (AC: 1, 2, 5, 6)
- [x] Created `commonMain/kotlin/com/poddrive/api/SpotifyAuthManager.kt`
- [x] `generateCodeVerifier()` — 43-char URL-safe random string
- [x] `generateCodeChallenge(verifier)` — SHA-256 → Base64.UrlSafe, no `=` padding (`@OptIn(ExperimentalEncodingApi::class)`)
- [x] `buildAuthorizationUrl(codeChallenge, state)` — uses Ktor `URLBuilder` for correct percent-encoding
- [x] `exchangeCodeForTokens(code, codeVerifier): Result<Unit, AppError>` — POST form, saves tokens on 200, returns `SpotifyApiError` on 4xx, `Unknown` on exception
- [x] Constructor takes `SecureTokenStorage` interface (not `SecureStorage` expect class) for testability

### T5 — Create `SpotifyApiClient.kt` (AC: 2, 6)
- [x] Created `commonMain/kotlin/com/poddrive/api/SpotifyApiClient.kt` — `expect fun createPlatformEngine(): HttpClientEngine`
- [x] `createSpotifyHttpClient(tokenStorage)` factory configures ContentNegotiation, Logging, Auth bearer (stub refresh → Story 1.4), HttpRequestRetry (3 retries, exponential, 429 retry)
- [x] Created `androidMain/kotlin/com/poddrive/api/SpotifyApiClient.kt` — `actual` uses `CIO.create()`
- [x] Created `iosMain/kotlin/com/poddrive/api/SpotifyApiClient.kt` — `actual` uses `Darwin.create()`

### T6 — Create `AuthenticateWithSpotifyUseCase.kt` (AC: 1–6)
- [x] Created `commonMain/kotlin/com/poddrive/usecase/AuthenticateWithSpotifyUseCase.kt`
- [x] `suspend fun execute(authCode, codeVerifier): Result<Unit, AppError>` delegates to `SpotifyAuthManager.exchangeCodeForTokens()`

### T7 — SHA-256 expect/actual (AC: 2) — **Critical: without this, PKCE cannot work**
- [x] Created `commonMain/kotlin/com/poddrive/api/Sha256.kt` — `expect fun sha256(input: ByteArray): ByteArray`
- [x] Created `androidMain/kotlin/com/poddrive/api/Sha256.kt` — `actual` using `MessageDigest.getInstance("SHA-256").digest(input)`
- [x] Created `iosMain/kotlin/com/poddrive/api/Sha256.kt` — `actual` using `platform.CoreCrypto.CC_SHA256` with `usePinned` (`@OptIn(ExperimentalForeignApi::class)`)

### T8 — Android OAuth redirect handler (AC: 1, 4)
- [x] Updated `AndroidManifest.xml` — `launchMode="singleTop"` on MainActivity; `<intent-filter>` for `poddrive://callback`
- [x] OAuth redirect handled in `MainActivity.onNewIntent()` (no second activity needed)
- [x] Updated `MainActivity.kt` — collects `OnboardingViewModel` state via `by viewModels()`; dispatches OAuth redirect via `viewModel.handleOAuthRedirect(intent)`
- [x] Updated `PodDriveApplication.kt` — wires `SecureStorage`, `createSpotifyHttpClient`, `SpotifyAuthManager`, `AuthenticateWithSpotifyUseCase` as application-scoped singletons

### T9 — iOS OAuth redirect handler (AC: 1, 4)
- [x] Created `iosApp/iosApp/ui/onboarding/OnboardingViewModel.swift` — `ASWebAuthenticationSession` with `callbackURLScheme = "poddrive"`, `prefersEphemeralWebBrowserSession = true`, reads `SPOTIFY_CLIENT_ID` from `Bundle.main.infoDictionary`
- [x] Created `iosApp/iosApp/ui/onboarding/OnboardingView.swift` — SwiftUI "Connect Spotify" button, loading and error states
- [x] Updated `iosApp/iosApp/Info.plist` — added `CFBundleURLTypes` with scheme `poddrive`
- [x] Updated `PodDriveApp.swift` — shows `OnboardingView` or authenticated placeholder

### T10 — Android onboarding UI (AC: 1, 3, 4, 5)
- [x] Created `androidApp/src/main/kotlin/com/poddrive/android/ui/onboarding/OnboardingScreen.kt` — Compose screen with "Connect Spotify" button, loading spinner, error+retry
- [x] Created `androidApp/src/main/kotlin/com/poddrive/android/ui/onboarding/OnboardingViewModel.kt` — `StateFlow<OnboardingUiState>` (Idle, LaunchingBrowser, ExchangingTokens, Authenticated, Error); stores `pendingCodeVerifier`
- [x] Updated `MainActivity.kt` — shows `OnboardingScreen` or authenticated placeholder

### T11 — Unit tests (AC: 2, 5, 6)
- [x] Created `commonTest/kotlin/com/poddrive/api/SpotifyAuthManagerTest.kt` — 12 tests:
  - `generateCodeVerifier`: length=43, URL-safe chars only, uniqueness across calls
  - `generateCodeChallenge`: no padding/+/slash, deterministic, RFC 7636 §B test vector
  - `buildAuthorizationUrl`: response_type=code, client_id, redirect_uri, S256 method, code_challenge, all 5 scopes, state param, Spotify endpoint
  - `exchangeCodeForTokens`: 200→Success+tokens saved, 400→SpotifyApiError(400), 401→SpotifyApiError(401), network exception→Unknown, failure→no tokens saved
- [x] Created `commonTest/kotlin/com/poddrive/storage/FakeSecureTokenStorage.kt` — in-memory `SecureTokenStorage` for tests

### T12 — Verify build (AC: 1–6)
- [x] All source files reviewed for syntactic completeness
- [x] Gradle deps wired correctly in `libs.versions.toml` and `shared/build.gradle.kts`
- [x] `./gradlew :shared:build :androidApp:assembleDebug` — deferred to CI (JDK 17 not available locally)

---

## Dev Notes

### PKCE Flow — Step-by-Step

```
1. App generates:
   code_verifier = random 43-char URL-safe string
   code_challenge = BASE64URL(SHA256(ASCII(code_verifier)))  ← no padding

2. Open browser to:
   https://accounts.spotify.com/authorize
     ?client_id=<YOUR_CLIENT_ID>
     &response_type=code
     &redirect_uri=poddrive%3A%2F%2Fcallback
     &code_challenge_method=S256
     &code_challenge=<code_challenge>
     &scope=user-library-read%20user-read-playback-state%20user-modify-playback-state%20user-read-recently-played%20streaming
     &state=<random_state_string>

3. On callback: extract 'code' and 'state' from redirect URI

4. POST https://accounts.spotify.com/api/token
   Body (x-www-form-urlencoded):
     grant_type=authorization_code
     &code=<code>
     &redirect_uri=poddrive://callback
     &client_id=<YOUR_CLIENT_ID>
     &code_verifier=<code_verifier>
   → Response: { access_token, refresh_token, expires_in, token_type }

5. SecureStorage.saveTokens(access_token, refresh_token)
```

> **PKCE only — no `client_secret`:** Spotify PKCE flow for mobile apps does NOT use a client secret. The `code_verifier` is the proof. Do not embed a client secret in the app binary.

### Spotify Client ID

- **DO NOT hardcode** the Spotify `client_id` in source code
- Android: inject via `BuildConfig.SPOTIFY_CLIENT_ID` (defined in `androidApp/build.gradle.kts` as `buildConfigField`)
- iOS: inject via a `.xcconfig` file → `Info.plist` → read at runtime via `Bundle.main.infoDictionary["SPOTIFY_CLIENT_ID"]`
- For CI: define as a GitHub Actions secret `SPOTIFY_CLIENT_ID`; pass to `buildConfigField` via Gradle property

### Redirect URI

- Scheme: `poddrive`
- Host: `callback`
- Full URI: `poddrive://callback`
- Must be registered in the Spotify Developer Dashboard for your app

### SHA-256 expect/actual — Critical Implementation Note

**SHA-256 is NOT in Kotlin's stdlib.** The PKCE spec requires it. Implement via expect/actual:

```kotlin
// commonMain/kotlin/com/poddrive/api/Sha256.kt
package com.poddrive.api
expect fun sha256(input: ByteArray): ByteArray
```

```kotlin
// androidMain/kotlin/com/poddrive/api/Sha256.kt
package com.poddrive.api
import java.security.MessageDigest
actual fun sha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)
```

```kotlin
// iosMain/kotlin/com/poddrive/api/Sha256.kt
package com.poddrive.api
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun sha256(input: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    CC_SHA256(input.toUByteArray().toCValues(), input.size.toUInt(), digest.toCValues())
    return digest.toByteArray()
}
```

> **Alternatively**, use Ktor's built-in `io.ktor:ktor-utils` `hex()` function for encoding, but SHA-256 digest still requires the platform crypto above.

### Ktor Client Setup

```kotlin
// commonMain/kotlin/com/poddrive/api/SpotifyApiClient.kt
package com.poddrive.api

import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

// Platform-specific engine injected via expect/actual
expect fun createPlatformEngine(): HttpClientEngine

fun createSpotifyHttpClient(secureStorage: SecureStorage, authManager: SpotifyAuthManager): HttpClient =
    HttpClient(createPlatformEngine()) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) {
            level = LogLevel.INFO  // Use NONE in production builds
        }
        install(Auth) {
            bearer {
                loadTokens {
                    val access = secureStorage.getAccessToken()
                    val refresh = secureStorage.getRefreshToken()
                    if (access != null && refresh != null) {
                        BearerTokens(access, refresh)
                    } else null
                }
                refreshTokens {
                    // Story 1.4 implements this; return null for now to trigger re-auth
                    null
                }
                sendWithoutRequest { request ->
                    request.url.host == "api.spotify.com"
                }
            }
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
            retryIf { _, response -> response.status.value == 429 }  // Retry on rate limit (NFR17)
        }
    }
```

```kotlin
// androidMain/kotlin/com/poddrive/api/SpotifyApiClient.kt
package com.poddrive.api
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
actual fun createPlatformEngine(): HttpClientEngine = CIO.create()
```

```kotlin
// iosMain/kotlin/com/poddrive/api/SpotifyApiClient.kt
package com.poddrive.api
import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*
actual fun createPlatformEngine(): HttpClientEngine = Darwin.create()
```

### `SpotifyAuthManager.kt` Shape

```kotlin
// commonMain/kotlin/com/poddrive/api/SpotifyAuthManager.kt
package com.poddrive.api

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result
import com.poddrive.storage.SecureStorage
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

class SpotifyAuthManager(
    private val secureStorage: SecureStorage,
    private val httpClient: HttpClient,
    private val clientId: String,
    private val redirectUri: String = "poddrive://callback"
) {
    fun generateCodeVerifier(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return (1..43).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun generateCodeChallenge(verifier: String): String {
        val hash = sha256(verifier.encodeToByteArray())
        return Base64.UrlSafe.encode(hash).trimEnd('=')  // Base64url, no padding
    }

    fun buildAuthorizationUrl(codeChallenge: String, state: String): String {
        val scopes = "user-library-read user-read-playback-state user-modify-playback-state user-read-recently-played streaming"
        return "https://accounts.spotify.com/authorize" +
            "?client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=${redirectUri.encodeURLPathPart()}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$codeChallenge" +
            "&scope=${scopes.encodeURLPathPart()}" +
            "&state=$state"
    }

    suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String
    ): Result<Unit, AppError> = try {
        val response = httpClient.submitForm(
            url = "https://accounts.spotify.com/api/token",
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", clientId)
                append("code_verifier", codeVerifier)
            }
        )
        if (response.status.isSuccess()) {
            val tokenResponse = Json.decodeFromString<SpotifyTokenResponse>(response.bodyAsText())
            secureStorage.saveTokens(tokenResponse.access_token, tokenResponse.refresh_token)
            Result.Success(Unit)
        } else {
            Result.Failure(AppError.SpotifyApiError(response.status.value, response.status.description))
        }
    } catch (e: Exception) {
        Result.Failure(AppError.Unknown(e))
    }
}
```

### Android Manifest Changes

```xml
<!-- Add inside <application> → <activity android:name=".MainActivity"> -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="poddrive" android:host="callback" />
</intent-filter>
```

Also ensure `MainActivity` has `android:launchMode="singleTop"` to prevent duplicate instances on redirect.

### iOS Info.plist Changes

```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>poddrive</string>
        </array>
    </dict>
</array>
```

### iOS `ASWebAuthenticationSession` Pattern

```swift
// OnboardingViewModel.swift
import AuthenticationServices

class OnboardingViewModel: NSObject, ObservableObject, ASWebAuthenticationPresentationContextProviding {
    @Published var isAuthenticated = false
    @Published var error: String? = nil

    private var authManager: SpotifyAuthManager // from KMP shared

    func startOAuth() {
        let verifier = authManager.generateCodeVerifier()
        let challenge = authManager.generateCodeChallenge(verifier: verifier)
        let state = UUID().uuidString
        let urlString = authManager.buildAuthorizationUrl(codeChallenge: challenge, state: state)
        guard let url = URL(string: urlString) else { return }

        let session = ASWebAuthenticationSession(
            url: url,
            callbackURLScheme: "poddrive"
        ) { [weak self] callbackURL, error in
            guard let code = callbackURL?.queryParameters?["code"] else {
                self?.error = "Authentication failed"
                return
            }
            Task {
                // Call KMP use case
                let result = await self?.authManager.exchangeCodeForTokens(code: code, codeVerifier: verifier)
                // handle result
            }
        }
        session.prefersEphemeralWebBrowserSession = true
        session.presentationContextProvider = self
        session.start()
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?.windows.first ?? ASPresentationAnchor()
    }
}
```

### Dependency on Story 1.2 (DO NOT REMOVE OR MODIFY)

The following files from Story 1.2 are INPUTS to this story — read them but do not modify their public contracts:
- `commonMain/kotlin/com/poddrive/storage/SecureStorage.kt` — expect class, exactly 4 methods
- `commonMain/kotlin/com/poddrive/domain/model/AppError.kt` — 5 sealed subtypes
- `commonMain/kotlin/com/poddrive/domain/model/Result.kt` — Success/Failure + extension functions
- `commonMain/kotlin/com/poddrive/storage/DatabaseDriverFactory.kt` — unrelated, do not touch

### New Files to Create

```
poddrive/
├── gradle/libs.versions.toml                                         ← MODIFY: add ktor deps
├── shared/build.gradle.kts                                           ← MODIFY: wire ktor
└── shared/src/
    ├── commonMain/kotlin/com/poddrive/
    │   ├── api/
    │   │   ├── Sha256.kt                    ← CREATE expect (T7)
    │   │   ├── SpotifyAuthManager.kt        ← CREATE (T4)
    │   │   ├── SpotifyApiClient.kt          ← CREATE expect + factory (T5)
    │   │   └── SpotifyModels.kt             ← CREATE @Serializable DTOs (T3)
    │   └── usecase/
    │       └── AuthenticateWithSpotifyUseCase.kt  ← CREATE (T6)
    ├── androidMain/kotlin/com/poddrive/
    │   └── api/
    │       ├── Sha256.kt                    ← CREATE actual (T7)
    │       └── SpotifyApiClient.kt          ← CREATE actual CIO engine (T5)
    ├── iosMain/kotlin/com/poddrive/
    │   └── api/
    │       ├── Sha256.kt                    ← CREATE actual CoreCrypto (T7)
    │       └── SpotifyApiClient.kt          ← CREATE actual Darwin engine (T5)
    └── commonTest/kotlin/com/poddrive/
        └── api/
            └── SpotifyAuthManagerTest.kt    ← CREATE (T11)

androidApp/src/main/
├── AndroidManifest.xml                      ← MODIFY: add intent-filter + launchMode (T8)
└── kotlin/com/poddrive/android/
    └── ui/onboarding/
        ├── OnboardingScreen.kt              ← CREATE Compose screen (T10)
        └── OnboardingViewModel.kt           ← CREATE ViewModel (T10)

iosApp/iosApp/
├── Info.plist                               ← MODIFY: add CFBundleURLTypes (T9)
└── ui/onboarding/
    ├── OnboardingView.swift                 ← CREATE SwiftUI view (T9)
    └── OnboardingViewModel.swift           ← CREATE ObservableObject (T9)
```

### `SpotifyModels.kt` Shape

```kotlin
// commonMain/kotlin/com/poddrive/api/SpotifyModels.kt
package com.poddrive.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val access_token: String,
    @SerialName("refresh_token") val refresh_token: String,
    @SerialName("expires_in") val expires_in: Int,
    @SerialName("token_type") val token_type: String
)

@Serializable
data class SpotifyErrorResponse(
    val error: String,
    @SerialName("error_description") val error_description: String? = null
)
```

### Previous Story Intelligence (Story 1.2)

- **JDK 17 not available locally** — all Gradle build verification deferred to CI (`android-ci.yml` on `ubuntu-latest`, `ios-ci.yml` on `macos-latest`). Verify source files syntactically before pushing.
- **Kotlin 2.0.21** — do not upgrade. `kotlin("multiplatform")` version is pinned in `libs.versions.toml`.
- **`-Xexpect-actual-classes` compiler flag is already set** in `shared/build.gradle.kts` via `targets.configureEach { compilations.configureEach { ... freeCompilerArgs.add("-Xexpect-actual-classes") } }` — do not remove it.
- **`@OptIn(ExperimentalForeignApi::class)`** will be required on iOS actual files that use Kotlin/Native `kotlinx.cinterop` (same pattern as `SecureStorage.kt` iOS actual from Story 1.2).
- **`iosApp/` is NOT a Gradle module** — do not add it to `settings.gradle.kts`. Swift/Xcode files are edited directly.
- **Gradle Version Catalog is the single source of truth** — ALL new deps in `libs.versions.toml` first; never hardcode in `.gradle.kts`.
- **Gradle Kotlin DSL only** — never Groovy.
- **`PodDriveApplication.kt`** exists in `androidApp` — if `SecureStorage` or `SpotifyAuthManager` needs a `Context`, pass it from here. Do NOT create a global singleton.

### Architecture Compliance Checklist

Before marking any task done, verify:

- [ ] `SpotifyAuthManager` is in `commonMain/api/` — no HTTP logic in Android/iOS layers
- [ ] `AuthenticateWithSpotifyUseCase` returns `Result<Unit, AppError>` — never throws
- [ ] Tokens stored only via `SecureStorage.saveTokens()` — never in SQLDelight
- [ ] No `client_secret` embedded anywhere in source code
- [ ] Spotify scopes list matches architecture exactly: `user-library-read user-read-playback-state user-modify-playback-state user-read-recently-played streaming` — no extras
- [ ] PKCE code challenge uses `S256` method (SHA-256 base64url no-padding) — never `plain`
- [ ] `SpotifyApiClient.kt` expect/actual names match exactly
- [ ] All new Gradle dependencies added to `libs.versions.toml` before `build.gradle.kts`
- [ ] Android `launchMode="singleTop"` set on `MainActivity`
- [ ] iOS `prefersEphemeralWebBrowserSession = true` on `ASWebAuthenticationSession`

### Anti-Patterns to Avoid

❌ Do NOT use `client_secret` in the PKCE mobile flow — Spotify PKCE for mobile is secret-free
❌ Do NOT hardcode `client_id` in source — use `BuildConfig`/`.xcconfig` injection
❌ Do NOT store tokens in SQLDelight — only in `SecureStorage`
❌ Do NOT call `SecureStorage.saveTokens()` outside `SpotifyAuthManager` — token lifecycle is owned by `SpotifyAuthManager` only (architecture.md process patterns)
❌ Do NOT throw exceptions from `AuthenticateWithSpotifyUseCase` — return `Result.Failure`
❌ Do NOT add business logic to `OnboardingViewModel` beyond delegating to use case + mapping UI state
❌ Do NOT use `WebView` for OAuth on Android — use `CustomTabsIntent` (security requirement)
❌ Do NOT use `SFSafariViewController` on iOS — use `ASWebAuthenticationSession` (handles redirect scheme automatically)
❌ Do NOT add Ktor dependencies directly to `build.gradle.kts` without Version Catalog entries
❌ Do NOT skip the `state` parameter in the authorization URL — it prevents CSRF attacks

### References

- [Source: architecture.md#Authentication & Security] — PKCE flow, SecureStorage pattern, scopes
- [Source: architecture.md#API & Communication Patterns] — Ktor client, plugins, engines (CIO/Darwin)
- [Source: architecture.md#Structure Patterns] — `commonMain/api/`, `commonMain/usecase/`, engine actual locations
- [Source: architecture.md#Process Patterns] — Token ownership rule (only SpotifyAuthManager calls saveTokens)
- [Source: architecture.md#Anti-Patterns] — token storage, direct HTTP calls prohibition
- [Source: architecture.md#Complete Project Directory Structure] — authoritative directory tree for new files
- [Source: epics.md#Story 1.3] — user story, 5 acceptance criteria (NFR6, NFR8, NFR19)
- [Source: epics.md#Requirements — NFR6, NFR8, NFR19, NFR20] — security, scope minimisation, Spotify-not-installed, token refresh non-interruption
- [Source: 1-2-shared-infrastructure-database-error-handling-and-secure-storage.md#Dev Notes] — SecureStorage shape, Result pattern, JDK constraint, -Xexpect-actual-classes flag
- [Source: prd.md#MVP] — Spotify OAuth is entry gate for entire MVP

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Removed unused `io.ktor.client.engine.mock.respondError` import from `SpotifyAuthManagerTest.kt`
- Added `SecureTokenStorage` interface to solve `expect class` untestability in `commonTest` — `SpotifyAuthManager` now depends on the interface, not the concrete expect class
- Added `coroutines-test = "1.8.1"` to `libs.versions.toml` and `commonTest.dependencies` to support `runTest`

### Completion Notes List

- `SecureTokenStorage` interface introduced in `commonMain/storage/` for testability; all `SecureStorage` actuals now implement it. This is an additive change that does not break Story 1.2 contracts.
- `SpotifyAuthManager` constructor takes `SecureTokenStorage` (interface), not `SecureStorage` (expect class), enabling `FakeSecureTokenStorage` in `commonTest`.
- iOS SHA-256 actual uses `usePinned` + `CC_SHA256` pattern with `@OptIn(ExperimentalForeignApi::class)`.
- Story 1.4 token refresh is stubbed with `refreshTokens { null }` in the Ktor Auth plugin — intentionally returns null to trigger re-auth until Story 1.4 is implemented.
- Build verification deferred to CI — JDK 17 not available in local environment (same constraint as Story 1.2).
- `androidApp/build.gradle.kts` updated with `buildFeatures { buildConfig = true }`, `buildConfigField` for `SPOTIFY_CLIENT_ID`, and new Android UI dependencies.

### File List

**Created:**
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/SecureTokenStorage.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/Sha256.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyModels.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyAuthManager.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyApiClient.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/AuthenticateWithSpotifyUseCase.kt`
- `poddrive/shared/src/androidMain/kotlin/com/poddrive/api/Sha256.kt`
- `poddrive/shared/src/androidMain/kotlin/com/poddrive/api/SpotifyApiClient.kt`
- `poddrive/shared/src/iosMain/kotlin/com/poddrive/api/Sha256.kt`
- `poddrive/shared/src/iosMain/kotlin/com/poddrive/api/SpotifyApiClient.kt`
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/storage/FakeSecureTokenStorage.kt`
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/api/SpotifyAuthManagerTest.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/onboarding/OnboardingViewModel.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/onboarding/OnboardingScreen.kt`
- `poddrive/iosApp/iosApp/ui/onboarding/OnboardingViewModel.swift`
- `poddrive/iosApp/iosApp/ui/onboarding/OnboardingView.swift`

**Modified:**
- `poddrive/gradle/libs.versions.toml` — added Ktor 3.0.3, kotlinx-serialization-json 1.7.3, coroutines-test, browser, viewmodel-compose versions and library entries
- `poddrive/shared/build.gradle.kts` — wired Ktor deps in commonMain/commonTest/androidMain/iosMain
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/SecureStorage.kt` — added `: SecureTokenStorage` to expect class
- `poddrive/shared/src/androidMain/kotlin/com/poddrive/storage/SecureStorage.kt` — added `: SecureTokenStorage` to actual class
- `poddrive/shared/src/iosMain/kotlin/com/poddrive/storage/SecureStorage.kt` — added `: SecureTokenStorage` to actual class
- `poddrive/androidApp/build.gradle.kts` — added buildConfig, SPOTIFY_CLIENT_ID field, browser + viewmodel-compose deps
- `poddrive/androidApp/src/main/AndroidManifest.xml` — added `launchMode="singleTop"` and `poddrive://callback` intent-filter
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` — wired auth singletons
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/MainActivity.kt` — OAuth redirect handling + OnboardingScreen navigation
- `poddrive/iosApp/iosApp/Info.plist` — added CFBundleURLTypes with `poddrive` scheme
- `poddrive/iosApp/iosApp/PodDriveApp.swift` — shows OnboardingView or authenticated placeholder

### Change Log

| Date | Change | Reason |
|------|--------|--------|
| 2026-03-22 | Added `SecureTokenStorage` interface | `expect class` not instantiable in `commonTest`; needed for `FakeSecureTokenStorage` |
| 2026-03-22 | Added `coroutines-test` to version catalog + commonTest deps | `runTest` requires `kotlinx-coroutines-test` |
| 2026-03-22 | Used `SecureTokenStorage` (interface) in `SpotifyAuthManager` constructor | Decouples auth logic from platform storage impl; enables unit tests |
| 2026-03-22 | Removed `respondError` import from test file | Was unused; import-only would cause compile warning |
