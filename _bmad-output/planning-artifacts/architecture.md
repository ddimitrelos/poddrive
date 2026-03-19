---
stepsCompleted: ['step-01-init', 'step-02-context', 'step-03-starter', 'step-04-decisions', 'step-05-patterns', 'step-06-structure', 'step-07-validation', 'step-08-complete']
lastStep: 8
status: 'complete'
completedAt: '2026-03-19'
inputDocuments: ['_bmad-output/planning-artifacts/prd.md', '_bmad-output/project-context.md', '_bmad-output/planning-artifacts/implementation-readiness-report-2026-03-19.md']
workflowType: 'architecture'
project_name: 'BMAD Test'
user_name: 'Dimitrios.dimitrelos'
date: '2026-03-19'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

---

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
35 FRs across 7 domains — Authentication (3), Podcast Library (6), Drive Session Activation (4), AI Voice Briefing (7), Playback Control (5), Recommendation & Discovery (3), Resilience & Error Handling (4), Privacy & Data (3). The Drive Session + Briefing cluster (FR10–FR20) represents the product's core value proposition and the highest architectural complexity.

**Non-Functional Requirements:**
20 NFRs across 5 categories — Performance (5), Security (3), Reliability (4), Accessibility (4), Integration (4). Critical constraints:
- NFR1: Briefing begins within 10s of Bluetooth connection
- NFR2: First audio playing within 30s of Bluetooth connection
- NFR4: ≤5% additional battery draw per 60-min drive
- NFR6: Tokens in Keychain/Keystore only — never plain text
- NFR7: Zero data transmitted to servers in MVP
- NFR10: BT trigger fires on ≥95% of connection events
- NFR11: State recovery within 10s of OS kill

**Scale & Complexity:**
- Primary domain: Native Mobile (iOS Swift + Android Kotlin)
- Complexity level: Medium
- Estimated architectural components: 8–10 (Auth, Library Sync, BT Trigger, Session Manager, Briefing Engine, Playback Controller, Recommendation Engine, Local Storage, Error Handler, State Restoration)

### Technical Constraints & Dependencies

- **iOS background execution**: Audio session activation required as background mode trigger — not background app refresh alone
- **Spotify API quota**: `user-modify-playback-state` and `streaming` scopes require quota extension beyond 25 users — must apply at 3-month milestone
- **Spotify rate limits**: Episode fetch + library sync must be batched and cached — never hit limits during the Bluetooth trigger window
- **Spotify Web API vs SDK**: PRD decision is Web API + deep-link URI for MVP (avoids SDK dependency risk)
- **GDPR**: On-device-only storage in MVP eliminates most GDPR complexity, but consent, privacy policy, and right-to-deletion must still be implemented
- **No backend MVP**: All Spotify API calls from device; no push notifications; no server-side user profiles

### Cross-Cutting Concerns Identified

1. **Background lifecycle management** — affects BT trigger, audio session, library sync, state restoration; must be solved consistently across both platforms
2. **Spotify API abstraction layer** — rate limiting, token refresh, error handling, and quota compliance affect every component that touches Spotify
3. **On-device security** — token storage, data minimisation, app-level encryption decisions affect Auth, Storage, and Privacy components
4. **Platform API parity** — Bluetooth, TTS, audio session, and local storage all use different native APIs; shared interfaces must be designed carefully
5. **Graceful degradation via voice** — error communication pattern is audio-first (not UI-first); this shapes every error handling path in the system
6. **State machine consistency** — drive session lifecycle (idle → activating → briefing → playing → paused → ended) must be managed atomically with OS lifecycle events

---

## Starter Template Evaluation

### Primary Technology Domain

Native Mobile (iOS + Android) — mandated by PRD. No cross-platform framework (React Native, Flutter) is permitted due to background audio, Bluetooth trigger, and on-device TTS requirements that demand deep native platform API access.

### Project Structure Decision: Kotlin Multiplatform (KMP) Monorepo

**Rationale:** Solo/small team with Android-first development strategy (per PRD). KMP allows shared domain logic (episode sync, LOVED/LIKED model, Spotify API client, recommendation engine, state machine) to be written once in Kotlin, consumed natively by both Android (Jetpack Compose) and iOS (SwiftUI via Swift/ObjC interop). Reduces duplication for the substantial shared business logic layer without sacrificing native UI or platform API access.

**Monorepo Structure:**
```
poddrive/
├── shared/                     # Kotlin Multiplatform shared module
│   └── src/
│       ├── commonMain/         # Pure Kotlin business logic
│       ├── androidMain/        # Android-specific implementations
│       └── iosMain/            # iOS-specific implementations (via expect/actual)
├── androidApp/                 # Android Jetpack Compose app
└── iosApp/                     # iOS SwiftUI app (Xcode project)
```

**What lives in `shared/commonMain` (written once):**
- Spotify API client (OAuth, token refresh, episode fetch, rate limiting)
- Episode domain model (Podcast, Episode, Tier enum)
- LOVED/LIKED tag persistence logic
- Episode queue & continuity engine
- Briefing script generator
- Recommendation engine (v1 heuristic)
- Drive session state machine
- Local storage interfaces (expect/actual pattern)
- Error handling & retry logic

**What stays platform-native (expect/actual or UI layer):**
- Bluetooth detection (iOS: AVAudioSession route change; Android: BluetoothProfile broadcast receiver)
- TTS engine (iOS: AVSpeechSynthesizer; Android: TextToSpeech)
- Audio session / audio focus management
- Secure credential storage (iOS: Keychain; Android: Keystore)
- Local database implementation (iOS: CoreData or SQLite; Android: Room)
- Background service/mode declaration
- UI (SwiftUI vs Jetpack Compose)

### Platform Initialization

**Android (first — per PRD priority):**
Android Studio → New Project → Empty Activity (Jetpack Compose)
- Language: Kotlin 1.9+
- Minimum SDK: API 29 (Android 10)
- Build system: Gradle (Kotlin DSL)

**iOS:**
Xcode → New Project → App (SwiftUI, Swift)
- Minimum deployment: iOS 16+
- Interface: SwiftUI
- Language: Swift 5.9+

**KMP Setup:**
```bash
# Add KMP Gradle plugin to shared module
plugins {
    kotlin("multiplatform") version "2.x"
    kotlin("plugin.serialization") version "2.x"
}
```

**Note:** KMP monorepo initialization + Android project setup is Story 1 of Epic 1.

---

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- KMP monorepo structure with shared domain layer
- SQLDelight as cross-platform database (schema in commonMain)
- Ktor Client for all Spotify Web API communication (commonMain)
- expect/actual SecureStorage for token management
- Platform-native ViewModels calling shared KMP use cases

**Important Decisions (Shape Architecture):**
- Result<T, AppError> error handling pattern across KMP boundary
- In-memory + SQLDelight dual-layer caching for session state
- DriveSessionState persisted on every transition for OS-kill recovery
- GitHub Actions + Fastlane for CI/CD

**Deferred Decisions (Post-MVP):**
- Shared KMP ViewModels (emerging pattern — evaluate at Growth phase)
- Server infrastructure (triggered by push notifications feature)
- Spotify quota extension application (3-month milestone trigger)

---

### Data Architecture

**Database: SQLDelight (KMP)**
- Schema defined once in `shared/commonMain` — generates typesafe Kotlin (Android) and Swift (iOS) query code
- Android driver: `AndroidSqliteDriver` (SQLite-backed)
- iOS driver: `NativeSqliteDriver` (SQLite-backed)
- Entities: `Podcast`, `Episode`, `EpisodeTier`, `PlaybackState`, `DriveSession`
- Rationale: Eliminates data model duplication; single source of truth for schema; aligns with KMP architecture decision

**Caching Strategy:**
- Episode library: persisted in SQLDelight; refreshed on Bluetooth connect or background sync
- Active drive session state: in-memory `DriveSessionState` + written to SQLDelight on every state transition (supports OS-kill recovery per NFR11)
- Spotify access/refresh tokens: SecureStorage only — never in SQLDelight
- Stale indicator: library sync timestamp stored in SQLDelight; UI shows staleness if >24h (FR32)

---

### Authentication & Security

**Spotify OAuth 2.0 PKCE:**
- PKCE flow initiated from platform UI layer (requires browser/custom tab)
- Token exchange and all subsequent authenticated requests handled in `commonMain` SpotifyApiClient
- Scopes: `user-library-read`, `user-read-playback-state`, `user-modify-playback-state`, `user-read-recently-played`, `streaming`

**Secure Token Storage — expect/actual pattern:**
```kotlin
// commonMain
expect class SecureStorage {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clear()
}
// androidMain → EncryptedSharedPreferences
// iosMain → Keychain Security framework
```

**Data Minimisation (NFR7):**
- No user data transmitted to any server in MVP
- Only episode IDs, tier tags, and playback positions stored on-device
- LOVED/LIKED preferences: SQLDelight, on-device only

---

### API & Communication Patterns

**HTTP Client: Ktor Client (commonMain)**
- Engine: `CIO` (Android), `Darwin` (iOS)
- All Spotify Web API calls live in `shared/commonMain/SpotifyApiClient`
- Plugins: `ContentNegotiation` (kotlinx.serialization), `Auth` (bearer token + auto-refresh), `HttpRequestRetry` (exponential backoff)

**Error Handling — Result pattern:**
```kotlin
// commonMain
sealed class AppError {
    data class SpotifyApiError(val code: Int, val message: String) : AppError()
    object NetworkUnavailable : AppError()
    object SpotifyNotInstalled : AppError()
    object RateLimitExceeded : AppError()
    data class Unknown(val cause: Throwable) : AppError()
}
// All API calls return: Result<T, AppError>
// No exceptions cross the KMP boundary
```

**Rate Limiting Strategy:**
- Episode library fetch batched on Bluetooth connect — never triggered on-demand during active session
- Ktor `HttpRequestRetry` plugin: 3 retries, exponential backoff, respects `Retry-After` header
- Local request queue for playback commands during high-traffic windows

**Token Refresh (NFR20):**
- Ktor `Auth` plugin handles transparent bearer token refresh
- Refresh occurs in background — never pauses audio or briefing

---

### UI State Management

**Pattern: Platform-native ViewModels → Shared KMP Use Cases**

```
Android:                          iOS:
ViewModel (StateFlow)    →    ObservableObject (@Published)
       ↓                                ↓
   KMP Use Cases (commonMain)
       ↓
   KMP Domain Layer (commonMain)
       ↓
   SpotifyApiClient / SQLDelight / SecureStorage
```

**Shared KMP Use Cases (commonMain):**
- `StartDriveSessionUseCase` — validates BT trigger, loads episode queue, returns briefing data
- `SyncPodcastLibraryUseCase` — fetches followed podcasts + unheard episodes from Spotify
- `GetEpisodeBriefingUseCase` — generates briefing script from queue (LOVED first, then LIKED)
- `SetEpisodeTierUseCase` — persists LOVED/LIKED tag to SQLDelight
- `GetRecommendationUseCase` — heuristic interest match for empty-inbox fallback
- `RestoreSessionUseCase` — reads persisted DriveSessionState from SQLDelight on app relaunch

**Platform UI State:**
- Android: `StateFlow<UiState>` in `ViewModel`, collected in Compose
- iOS: `@Published var state: UiState` in `ObservableObject`, consumed in SwiftUI

---

### Infrastructure & Deployment

- **CI/CD**: GitHub Actions — Android build + test; iOS build + test (macOS runner)
- **Android distribution**: Fastlane → Google Play internal track
- **iOS distribution**: Fastlane → TestFlight
- **No server infrastructure in MVP**
- **Environment config**: `BuildConfig` fields (Android) / `.xcconfig` files (iOS)
- **Dependency management**: Gradle Version Catalog (`libs.versions.toml`) for KMP project

### Decision Impact: Implementation Sequence

1. KMP monorepo + Gradle setup with Version Catalog
2. SQLDelight schema (`Podcast`, `Episode`, `EpisodeTier`, `PlaybackState`) + SecureStorage expect/actual
3. `SpotifyApiClient` (Ktor) + OAuth PKCE flow + token refresh
4. Bluetooth trigger (platform expect/actual) + `DriveSessionState` state machine
5. Briefing script generator + TTS engine expect/actual (`AVSpeechSynthesizer` / `TextToSpeech`)
6. Playback deep-link handoff + Episode Continuity Engine
7. Recommendation engine v1 (heuristic)
8. Error handling paths + voice notification layer

### Cross-Component Dependencies

- **SpotifyApiClient** depends on SecureStorage (tokens) and SQLDelight (episode cache write)
- **DriveSession state machine** depends on BT trigger (platform) + SpotifyApiClient + BriefingEngine
- **BriefingEngine** depends on GetEpisodeBriefingUseCase + TTS expect/actual (platform)
- **PlaybackController** depends on SpotifyApiClient + DeepLinkHandler (platform)
- **RestoreSessionUseCase** depends on SQLDelight (reads persisted session state)

---

## Implementation Patterns & Consistency Rules

### Critical Conflict Points: 8 areas identified

---

### Naming Patterns

**Kotlin (commonMain + androidMain):**
- Classes/Objects/Enums: PascalCase → `SpotifyApiClient`, `DriveSessionState`
- Functions/variables: camelCase → `startDriveSession()`, `accessToken`
- Constants: SCREAMING_SNAKE_CASE → `MAX_RETRY_ATTEMPTS`
- Use cases: `{Verb}{Noun}UseCase` → `StartDriveSessionUseCase`
- Repositories: `{Domain}Repository` interface → `EpisodeRepository`
- expect/actual: same name in both → `expect class SecureStorage`

**Swift (iosApp):**
- Types/Protocols: PascalCase → `DriveSessionViewModel`
- Functions/variables: camelCase → `startDriveSession()`, `accessToken`
- Enum cases: camelCase → `.loved`, `.liked`, `.none`
- iOS-side wrappers for KMP classes: suffix with `Wrapper` if needed → `SecureStorageWrapper`

**SQLDelight:**
- Table names: snake_case, plural → `podcasts`, `episodes`, `drive_sessions`
- Column names: snake_case → `episode_id`, `podcast_name`, `played_at`
- Query files: PascalCase matching entity → `EpisodeQueries.sq`, `PodcastQueries.sq`
- Primary keys: always named `id` (TEXT/UUID)

**Files & Modules:**
- KMP source files: PascalCase matching class → `SpotifyApiClient.kt`
- iOS source files: PascalCase matching type → `DriveSessionViewModel.swift`
- Gradle modules: kebab-case → `shared`, `android-app`
- Android packages: `com.poddrive.{module}` → `com.poddrive.shared.domain`

---

### Structure Patterns

**KMP Module Layout (mandatory):**
```
shared/src/
├── commonMain/kotlin/com/poddrive/
│   ├── domain/          # Models (Podcast, Episode, DriveSessionState)
│   ├── usecase/         # Use cases (one file per use case)
│   ├── repository/      # Repository interfaces only
│   ├── api/             # SpotifyApiClient, SpotifyModels
│   ├── storage/         # SecureStorage expect + SqlDelightStorage
│   └── recommendation/  # RecommendationEngine
├── androidMain/kotlin/com/poddrive/
│   ├── storage/         # SecureStorage actual (EncryptedSharedPreferences)
│   ├── bluetooth/       # BluetoothTrigger actual
│   └── tts/             # TtsEngine actual (Android TextToSpeech)
└── iosMain/kotlin/com/poddrive/
    ├── storage/         # SecureStorage actual (Keychain)
    ├── bluetooth/       # BluetoothTrigger actual
    └── tts/             # TtsEngine actual (AVSpeechSynthesizer)
```

**Use Case rule:** One use case per file. No logic in files named `*UseCase.kt` beyond orchestrating domain + repository calls.

**Repository rule:** Interfaces only in `commonMain/repository/`. Implementations live in `commonMain` (SQLDelight) or platform `*Main` (secure storage, BT, TTS). Never put repository implementations in the UI layer.

---

### Format Patterns

**Domain Models (commonMain) — immutable data classes:**
```kotlin
data class Episode(
    val id: String,          // Spotify episode URI
    val podcastId: String,
    val title: String,
    val durationMs: Long,
    val publishedAt: Long,   // Unix timestamp ms — always Long, never String
    val isPlayed: Boolean,
    val tier: EpisodeTier
)

enum class EpisodeTier { LOVED, LIKED, NONE }
```

**Timestamps:** Always `Long` (Unix ms) in domain models and SQLDelight. Format for display only at the UI layer — never store formatted strings.

**IDs:** Always `String` — Spotify URIs are strings (`spotify:episode:xyz`). Never cast to numeric types.

**Error results (mandatory pattern):**
```kotlin
// ALL use case return types must use kotlin.Result:
suspend fun execute(): Result<T>
// Never: throw exceptions from use cases
// Never: return null to signal failure
```

---

### Communication Patterns

**Drive Session State Machine (sealed class — agents must not add ad-hoc states):**
```kotlin
sealed class DriveSessionState {
    object Idle : DriveSessionState()
    object Activating : DriveSessionState()
    data class Briefing(val script: BriefingScript) : DriveSessionState()
    data class Playing(val episode: Episode, val positionMs: Long) : DriveSessionState()
    object Paused : DriveSessionState()
    data class Error(val error: AppError) : DriveSessionState()
    object Ended : DriveSessionState()
}
```

**State transitions:** Only `DriveSessionManager` (commonMain) may mutate `DriveSessionState`. Platform ViewModels observe — they never mutate.

**Events/Actions (sealed class):**
```kotlin
sealed class DriveSessionEvent {
    object BluetoothConnected : DriveSessionEvent()
    object BluetoothDisconnected : DriveSessionEvent()
    object BriefingConfirmed : DriveSessionEvent()
    object SkipEpisode : DriveSessionEvent()
    object PauseSession : DriveSessionEvent()
    object ResumeSession : DriveSessionEvent()
}
```

---

### Process Patterns

**All Spotify API access:** Via `SpotifyApiClient` only. Never construct Spotify HTTP requests outside `SpotifyApiClient`.

**Background work:** Kotlin coroutines (`CoroutineScope`) in `commonMain`. Never `Thread()`, `AsyncTask`, or `DispatchQueue` for business logic. Platform background services (Android `ForegroundService`, iOS background audio mode) are wrappers only — they launch the KMP coroutine scope.

**Error-to-voice mapping:** `AppError → String` mapping lives in `BriefingScriptGenerator`. Platform TTS calls the generated string. Agents must not hardcode error messages in the platform UI layer.

**Persist-on-transition rule:** Every `DriveSessionState` change must be written to SQLDelight before the state is emitted to observers. Order: persist → emit. Never emit → persist.

**Token refresh:** Never call `SecureStorage.saveTokens()` outside `SpotifyApiClient`. Token lifecycle is owned by `SpotifyApiClient` only.

---

### Enforcement Guidelines

**All AI Agents MUST:**
- Read `shared/commonMain` interfaces before implementing any platform feature
- Return `Result<T>` from all use cases — never throw
- Write `DriveSessionState` to SQLDelight before emitting to UI observers
- Route all Spotify API calls through `SpotifyApiClient`
- Use SQLDelight query methods — never raw SQL strings in Kotlin code
- Place platform-specific code in `androidMain` / `iosMain` only
- Match expect/actual class names exactly — no aliases

**Anti-Patterns — Never Do These:**
- ❌ Business logic in Android `ViewModel` or iOS `ObservableObject` — use cases only
- ❌ Direct HTTP calls to Spotify API outside `SpotifyApiClient`
- ❌ Storing tokens in SQLDelight or `SharedPreferences`
- ❌ Adding new `DriveSessionState` variants without updating all `when` expressions
- ❌ Emitting state before persisting it to SQLDelight
- ❌ Formatting timestamps in domain models
- ❌ Using nullable return types to signal failure — use `Result`

---

## Project Structure & Boundaries

### Complete Project Directory Structure

```
poddrive/
├── .github/
│   └── workflows/
│       ├── android-ci.yml
│       └── ios-ci.yml
├── fastlane/
│   ├── Fastfile
│   └── Appfile
├── gradle/
│   └── libs.versions.toml              # Gradle Version Catalog
├── build.gradle.kts                    # Root build
├── settings.gradle.kts
│
├── shared/                             # KMP shared module
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/poddrive/
│       │   ├── domain/model/
│       │   │   ├── Podcast.kt
│       │   │   ├── Episode.kt
│       │   │   ├── EpisodeTier.kt
│       │   │   ├── DriveSessionState.kt
│       │   │   ├── DriveSessionEvent.kt
│       │   │   ├── BriefingScript.kt
│       │   │   └── AppError.kt
│       │   ├── repository/             # Interfaces only
│       │   │   ├── EpisodeRepository.kt
│       │   │   ├── PodcastRepository.kt
│       │   │   └── DriveSessionRepository.kt
│       │   ├── usecase/
│       │   │   ├── StartDriveSessionUseCase.kt     # FR10, FR11
│       │   │   ├── SyncPodcastLibraryUseCase.kt    # FR4, FR7, FR8, FR9
│       │   │   ├── GetEpisodeBriefingUseCase.kt    # FR14–FR18
│       │   │   ├── SetEpisodeTierUseCase.kt        # FR5, FR6
│       │   │   ├── GetRecommendationUseCase.kt     # FR26, FR27
│       │   │   └── RestoreSessionUseCase.kt        # FR24, FR31
│       │   ├── api/
│       │   │   ├── SpotifyApiClient.kt             # All Spotify HTTP calls
│       │   │   ├── SpotifyModels.kt                # @Serializable response DTOs
│       │   │   └── SpotifyAuthManager.kt           # FR1, FR2 — PKCE + token refresh
│       │   ├── storage/
│       │   │   ├── SecureStorage.kt                # expect class — FR33–FR35
│       │   │   ├── SqlDelightEpisodeRepository.kt  # EpisodeRepository impl
│       │   │   └── SqlDelightPodcastRepository.kt  # PodcastRepository impl
│       │   ├── session/
│       │   │   └── DriveSessionManager.kt          # State machine — FR10–FR13
│       │   ├── briefing/
│       │   │   ├── BriefingScriptGenerator.kt      # FR14–FR18, error→voice mapping
│       │   │   └── TtsEngine.kt                    # expect class — FR14, FR20
│       │   ├── bluetooth/
│       │   │   └── BluetoothTrigger.kt             # expect class — FR10, FR11
│       │   └── recommendation/
│       │       └── RecommendationEngine.kt         # FR26, FR27
│       │
│       ├── commonTest/kotlin/com/poddrive/
│       │   ├── usecase/                # Unit tests for all use cases
│       │   ├── api/                    # SpotifyApiClient tests (mock engine)
│       │   ├── session/                # DriveSessionManager state machine tests
│       │   └── briefing/              # BriefingScriptGenerator tests
│       │
│       ├── androidMain/kotlin/com/poddrive/
│       │   ├── storage/
│       │   │   └── SecureStorage.kt    # actual — EncryptedSharedPreferences
│       │   ├── bluetooth/
│       │   │   └── BluetoothTrigger.kt # actual — BluetoothProfile broadcast receiver
│       │   └── tts/
│       │       └── TtsEngine.kt        # actual — Android TextToSpeech
│       │
│       └── iosMain/kotlin/com/poddrive/
│           ├── storage/
│           │   └── SecureStorage.kt    # actual — Keychain Security framework
│           ├── bluetooth/
│           │   └── BluetoothTrigger.kt # actual — AVAudioSession route change
│           └── tts/
│               └── TtsEngine.kt        # actual — AVSpeechSynthesizer
│
├── sqldelight/
│   └── com/poddrive/
│       ├── PodcastQueries.sq           # podcasts table
│       ├── EpisodeQueries.sq           # episodes table
│       └── DriveSessionQueries.sq      # drive_sessions + playback_state tables
│
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml         # BLUETOOTH_CONNECT, ForegroundService declaration
│       └── kotlin/com/poddrive/android/
│           ├── MainActivity.kt
│           ├── PodDriveApplication.kt
│           ├── ui/
│           │   ├── onboarding/
│           │   │   └── OnboardingScreen.kt     # FR1, first LOVED/LIKED setup
│           │   ├── home/
│           │   │   ├── HomeScreen.kt           # Episode inbox — FR4–FR8, FR32
│           │   │   └── HomeViewModel.kt
│           │   ├── drive/
│           │   │   ├── DriveSessionScreen.kt   # FR12, FR13, FR19, FR25
│           │   │   └── DriveSessionViewModel.kt
│           │   ├── library/
│           │   │   ├── LibraryScreen.kt        # FR5, FR6 — LOVED/LIKED tagging
│           │   │   └── LibraryViewModel.kt
│           │   ├── settings/
│           │   │   └── SettingsScreen.kt       # FR3, FR33–FR35
│           │   └── theme/
│           │       └── Theme.kt
│           └── service/
│               └── DriveSessionForegroundService.kt  # FR12 — background audio
│
└── iosApp/
    ├── iosApp.xcodeproj/
    └── iosApp/
        ├── PodDriveApp.swift               # @main entry point
        ├── Info.plist                      # Background Modes, BT usage description
        ├── iosApp.entitlements
        ├── ui/
        │   ├── onboarding/
        │   │   └── OnboardingView.swift    # FR1, first LOVED/LIKED setup
        │   ├── home/
        │   │   ├── HomeView.swift          # FR4–FR8, FR32
        │   │   └── HomeViewModel.swift
        │   ├── drive/
        │   │   ├── DriveSessionView.swift  # FR12, FR13, FR19, FR25
        │   │   └── DriveSessionViewModel.swift
        │   ├── library/
        │   │   ├── LibraryView.swift       # FR5, FR6
        │   │   └── LibraryViewModel.swift
        │   ├── settings/
        │   │   └── SettingsView.swift      # FR3, FR33–FR35
        │   └── theme/
        │       └── Theme.swift
        └── audio/
            └── AudioSessionManager.swift  # AVAudioSession ducking — FR20
```

---

### Architectural Boundaries

**KMP Boundary (shared ↔ platform):**
- `commonMain` exposes only: use cases, domain models, repository interfaces, expect classes
- Platform layers (`androidMain`, `iosMain`) provide `actual` implementations only
- Android/iOS UI layers call use cases — never import from `api/` or `storage/` directly

**Spotify API Boundary:**
- Single entry point: `SpotifyApiClient.kt`
- Auth management: `SpotifyAuthManager.kt` (called by SpotifyApiClient internally)
- Deep-link playback handoff: platform UI layer via `spotify:episode:{id}` URI
- Spotify response DTOs never leak beyond `api/SpotifyModels.kt` — mapped to domain models at the boundary

**Audio Boundary:**
- Android: `DriveSessionForegroundService` owns audio focus; delegates session logic to KMP
- iOS: `AudioSessionManager.swift` configures `AVAudioSession`; delegates to KMP DriveSessionManager

---

### Requirements → Structure Mapping

| FR Group | FRs | Primary Files |
|---|---|---|
| Authentication | FR1–3 | `SpotifyAuthManager.kt`, `SecureStorage` (actual), `OnboardingScreen` |
| Podcast Library | FR4–9 | `SyncPodcastLibraryUseCase`, `SpotifyApiClient`, `EpisodeQueries.sq` |
| BT Trigger | FR10–13 | `BluetoothTrigger` (actual), `DriveSessionManager`, `DriveSessionForegroundService` |
| AI Briefing | FR14–20 | `GetEpisodeBriefingUseCase`, `BriefingScriptGenerator`, `TtsEngine` (actual), `AudioSessionManager` |
| Playback | FR21–25 | `DriveSessionManager`, `DriveSessionViewModel`, deep-link in platform UI |
| Recommendation | FR26–28 | `RecommendationEngine`, `GetRecommendationUseCase` |
| Resilience | FR29–32 | `AppError`, `SpotifyApiClient` retry, `RestoreSessionUseCase`, `DriveSessionQueries.sq` |
| Privacy & Data | FR33–35 | `SecureStorage.clear()`, SQLDelight delete queries, `SettingsScreen` |

---

### Data Flow

```
Bluetooth event (platform)
    → BluetoothTrigger.kt (actual)
    → DriveSessionManager.kt (commonMain) — emits Activating state + persists
    → SyncPodcastLibraryUseCase → SpotifyApiClient → Spotify Web API
    → SqlDelightEpisodeRepository (cache write)
    → GetEpisodeBriefingUseCase → BriefingScriptGenerator
    → TtsEngine.kt (actual) — speaks briefing through car speakers
    → DriveSessionState.Briefing emitted to platform ViewModel
    → User confirms (steering wheel / tap)
    → DriveSessionEvent.BriefingConfirmed → DriveSessionManager
    → Deep-link URI dispatched (platform) → Spotify app plays episode
    → DriveSessionState.Playing emitted + persisted to SQLDelight
```

---

### Integration Points

**External:**
- Spotify Web API: `https://api.spotify.com/v1/` — all calls via `SpotifyApiClient`
- Spotify app: deep-link `spotify:episode:{id}` via platform Intent (Android) / URL scheme (iOS)
- Platform TTS: on-device only — no external API dependency

**Internal:**
- KMP shared module → Android: Gradle dependency in `androidApp/build.gradle.kts`
- KMP shared module → iOS: XCFramework generated by Kotlin/Native Gradle task

---

## Architecture Validation Results

### Coherence Validation ✅

All technology choices are compatible and non-conflicting. KMP + SQLDelight + Ktor + expect/actual form a well-established production stack. Platform-native ViewModels calling shared KMP use cases is the lowest-risk KMP architecture pattern for MVP. PKCE OAuth + no backend is correct for mobile. On-device TTS requires no network and meets NFR1/NFR5 performance constraints.

Patterns are internally consistent: `Result<T>` error handling, sealed state machine, and persist-before-emit rule all reinforce each other and directly address NFR9, NFR11, NFR12.

---

### Requirements Coverage Validation ✅

- **Functional Requirements:** 35/35 covered ✅
- **Non-Functional Requirements:** 20/20 covered ✅
- All 8 FR categories mapped to specific files and directories
- All 4 key technical constraints (iOS background execution, Spotify quota, rate limits, GDPR) addressed architecturally

---

### Gap Analysis

**Important (non-blocking — no critical gaps):**

1. **Media button mapping (FR19, FR28):** `DriveSessionEvent.BriefingConfirmed` and `SkipEpisode` are defined but platform media button → event mapping is an implementation detail: Android `MediaSession` + `MediaButtonReceiver`; iOS `MPRemoteCommandCenter`. Define in drive session implementation story.

2. **Recommendation heuristic v1:** `RecommendationEngine` is defined but the specific algorithm for "interest-based" matching is unspecified — acceptable as a story-level implementation detail for FR26.

3. **KMP → iOS XCFramework integration:** For MVP, Gradle generates XCFramework embedded directly in `iosApp.xcodeproj`. Document this as the first decision in Story 1; migrate to SPM local package post-MVP if needed.

---

### Architecture Completeness Checklist

**✅ Requirements Analysis**
- [x] 35 FRs analyzed for architectural implications
- [x] 20 NFRs addressed with specific architectural decisions
- [x] Scale and complexity assessed (Medium, dual native platform)
- [x] 6 cross-cutting concerns identified and mapped

**✅ Architectural Decisions**
- [x] Platform strategy: KMP monorepo (shared domain, native UI)
- [x] Database: SQLDelight (commonMain schema)
- [x] HTTP client: Ktor (CIO + Darwin engines)
- [x] Auth: PKCE + expect/actual SecureStorage
- [x] Error handling: `Result<T>` + `AppError` sealed class
- [x] State management: Platform ViewModels → KMP use cases
- [x] CI/CD: GitHub Actions + Fastlane

**✅ Implementation Patterns**
- [x] Naming conventions (Kotlin PascalCase/camelCase, SQLDelight snake_case)
- [x] KMP module layout (commonMain/androidMain/iosMain)
- [x] 8 anti-patterns documented
- [x] `DriveSessionState` sealed class defined
- [x] `DriveSessionEvent` sealed class defined
- [x] Persist-before-emit rule documented

**✅ Project Structure**
- [x] Complete directory tree (poddrive/ monorepo)
- [x] All 35 FRs mapped to specific files
- [x] Architectural boundaries defined (KMP, Spotify API, Audio)
- [x] Data flow documented end-to-end

---

### Architecture Readiness Assessment

**Overall Status: READY FOR IMPLEMENTATION**

**Confidence Level: High**

**Key Strengths:**
- KMP eliminates duplication in the substantial shared domain layer (Spotify API client, state machine, briefing engine, recommendation engine) while preserving native platform API access required by the PRD
- Persist-before-emit rule + `RestoreSessionUseCase` directly address the highest-risk reliability requirement (NFR11: 10s OS-kill recovery)
- On-device TTS + SQLDelight cache means NFR1 (briefing within 10s) is achievable without any network dependency in the trigger window
- expect/actual cleanly encapsulates all platform-divergent code (BT, TTS, SecureStorage) behind shared interfaces

**Areas for Future Enhancement (Post-MVP):**
- Shared KMP ViewModels (evaluate at Growth phase when patterns mature)
- Server infrastructure (triggered by push notifications feature)
- SPM local package for KMP → iOS integration

---

### Implementation Handoff

**AI Agent Guidelines:**
- Read `shared/commonMain` interfaces before implementing any platform feature
- Follow all patterns in the Implementation Patterns section exactly
- Refer to the Requirements → Structure Mapping table for FR traceability
- The `DriveSessionState` sealed class is authoritative — do not add states without updating all `when` expressions

**First Implementation Story:**
KMP monorepo setup with Gradle (Kotlin DSL) + Version Catalog (`gradle/libs.versions.toml`) + Android project in `androidApp/` + Xcode project in `iosApp/` + empty `shared/` module with `commonMain` package structure. XCFramework embedding into Xcode project must be configured in this story.
