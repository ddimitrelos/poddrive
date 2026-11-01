---
stepsCompleted: ['step-01-init', 'step-02-context', 'step-03-starter', 'step-04-decisions', 'step-05-patterns', 'step-06-structure', 'step-07-validation', 'step-08-complete']
lastStep: 8
status: 'complete'
completedAt: '2026-03-19'
lastRevised: '2026-04-24'
inputDocuments: ['_bmad-output/planning-artifacts/prd.md', '_bmad-output/project-context.md', '_bmad-output/planning-artifacts/implementation-readiness-report-2026-03-19.md', '_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-24.md']
workflowType: 'architecture'
project_name: 'BMAD Test'
user_name: 'Dimitrios.dimitrelos'
date: '2026-03-19'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

---

## Revision History

**Revised 2026-04-24** — pivoted to podcast-list-centric home screen per [Sprint Change Proposal 2026-04-24](sprint-change-proposal-2026-04-24.md).

- **Removed from `commonMain`:** `BuildEpisodeQueueUseCase` (code removal pending — tracked in SCP §5).
- **Added to `commonMain`:** `GetSortedPodcastsUseCase`, `GetLatestUnlistenedUseCase`, `GetUnlistenedCountUseCase`, `GetContinueListeningUseCase` (fast-follow), `GetExploreRecommendationsUseCase` (fast-follow), `PromoteTierUseCase`.
- **Retained as scaffolding (not exercised in next release):** `DriveSessionState`, `DriveSessionManager`, `DriveSessionEvent`, `drive_sessions` table, `BluetoothTrigger` (expect/actual), `TtsEngine` (expect/actual), `BriefingScriptGenerator`, `StartDriveSessionUseCase`, `GetEpisodeBriefingUseCase`, `RestoreSessionUseCase`. These remain in the codebase for the Drive Mode / Vision phase. No next-release code path observes, emits, or launches them.
- **Decoupled:** Android Foreground Service and iOS Background Audio are now tied to in-app playback (`AudioPlayer`), not to any drive-session lifecycle.
- **Schema:** no changes. `playback_state` singleton serves Continue Listening as-is; `drive_sessions` table retained but quiescent.

Sections below have been revised in place. Original drive-session-centred content is preserved in annotated form where it still informs the Vision phase.

---

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
45 FRs across 8 domains — Authentication (3), Podcast Library (6), Drive Session Activation *(deferred — Drive Mode / Vision)*, AI Voice Briefing *(deferred — Drive Mode / Vision)*, Playback Control (4), **Home Screen Experience (10 — FR36–FR45)**, Explore Mode Recommendations (3 — FR26–FR28, reframed), Resilience & Error Handling (4), Privacy & Data (3). The Home Screen Experience cluster (FR36–FR45) is the primary architectural surface for next release. The Drive Session + Briefing cluster (FR10–FR20) is retained as-built scaffolding for a future Drive Mode phase and is not exercised by next-release code paths.

**Non-Functional Requirements:**
20 NFRs across 5 categories — Performance (5), Security (3), Reliability (4), Accessibility (4), Integration (4). Critical constraints for next release:
- **NFR2 (primary)**: Home Screen open → first audio via quick-play ≤5s under normal network conditions
- NFR3: Library sync ≤60s for 50 followed podcasts
- NFR4: ≤5% additional battery draw per 60-min listening session
- NFR6: Tokens in Keychain/Keystore only — never plain text
- NFR7: Zero data transmitted to servers in next release
- NFR31: Playback-state restoration after OS kill (revised from NFR11)
- NFR1 *(deferred — Drive Mode / Vision)*: Briefing begins within 10s of Bluetooth connection
- NFR5 *(deferred — Drive Mode / Vision)*: TTS briefing adds ≤2s before audio

**Scale & Complexity:**
- Primary domain: Native Mobile (iOS Swift + Android Kotlin)
- Complexity level: Medium
- Next-release architectural components (7–9): Auth, Library Sync (Spotify + RSS), Home Screen Composer, Sort Engine, Continue Listening Service, Explore Recommender, In-App Playback (ExoPlayer/AVPlayer via `AudioPlayer` expect/actual), Local Storage, Error Handler
- Retained as scaffolding (not wired): BT Trigger, Drive Session Manager, Briefing Engine, TTS Engine, State Restoration for drive sessions

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
6. **State composition** — home-screen state is composed from multiple independent shared-domain flows (sorted podcasts, per-tile unlistened counts, continue-listening, explore mode). No single state machine gates the UX. Platform `HomeScreenViewModel` is the composition boundary; `commonMain` use cases emit independent `Flow`s and do not reference one another. *(Drive-session state-machine consistency — idle → activating → briefing → playing → paused → ended — is retained as scaffolding for the Vision phase but not load-bearing in the next release.)*

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
- **Home-screen composition via independent use-case flows**; `playback_state` singleton is the Continue Listening source of truth (FR41, NFR31)
- **Freshness-color encoding computed in `commonMain`** — `GetUnlistenedCountUseCase` emits a `FreshnessBucket` enum (UI maps bucket → color token); keeps the rule testable in `commonTest` and consistent across platforms (FR38)
- GitHub Actions + Fastlane for CI/CD

**Deferred (Drive Mode / Vision phase — retained as scaffolding):**
- `DriveSessionState` persisted on every transition for OS-kill recovery — retained in `commonMain/domain/model/` but not written by next-release code paths
- In-memory + SQLDelight dual-layer caching for drive-session state — not active in next release

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
- Entities: `Podcast`, `Episode`, `EpisodeTier`, `PlaybackState`, `DriveSession` *(retained as-built; quiescent in next release)*
- Rationale: Eliminates data model duplication; single source of truth for schema; aligns with KMP architecture decision

**Schema Review (2026-04-24 pivot):**
- **`playback_state` (singleton)** — **no change**. Carries everything Continue Listening (FR41, story 8.9) needs: `episode_id`, `position_ms`, `updated_at`. In-progress detection is derivable as `position_ms > 0 AND episode_id IS NOT NULL AND position_ms < episode.duration_ms` — no `is_in_progress` column required.
- **`episodes`, `podcasts`, `episode_tiers`** — **no change**. Three-band sort (LOVED/LIKED/OTHER by latest-unlistened-date) is fully derivable from `podcasts.tier` + `episodes.is_played` + `episodes.published_at`. `audio_url` and `rss_feed_url` (ADR-002) already present.
- **`drive_sessions`** — **retained as-built but quiescent** in next release. No queue-specific tables or columns exist; SCP §4.3's "queue tables to drop" list is effectively empty (the queue was use-case-only). A one-shot `deleteAllSessions` migration on first launch after this release is recommended to flush stale Epic 3 test data (see Risk 1 in Validation §).
- **Listening-history richness (open)** — current schema only carries `is_played` (boolean). If Explore mode v1 (story 8.10) needs when/how-much-listened signal, a schema addition (`played_at INTEGER` on `episodes`, or a new `listening_history` table) will be required. Not added pre-emptively; flagged for 8.10 story creation.

**Caching Strategy:**
- Episode library: persisted in SQLDelight; refreshed on background sync or home-screen pull-to-refresh (never synchronously during home-screen open — see NFR2)
- Playback position: written to `playback_state` on pause, stop, and on interval while playing (persist-before-emit); no in-memory cache layer
- Home-screen state: derived directly from SQLDelight `Flow`s — no in-memory composition cache
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
- Episode library fetch runs in background or on home-screen pull-to-refresh — never synchronously during home-screen open (NFR2: ≤5s from open to audio via quick-play)
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

**Shared KMP Use Cases (commonMain) — next-release active set:**

```kotlin
// Home Screen Experience — Epic 8 Core MVP (FR36–FR40, FR43–FR45)
GetSortedPodcastsUseCase         // FR36, FR37 — three-band sort (LOVED/LIKED/OTHER)
                                 //              then by latest-unlistened-date within band
GetLatestUnlistenedUseCase       // FR39       — single most-recent unlistened episode per podcast
GetUnlistenedCountUseCase        // FR38       — count + FreshnessBucket per tile
PromoteTierUseCase               // FR44       — OTHER↔LIKED↔LOVED from tile long-press (write-only)

// Home Screen — Fast-Follow (stories 8.9, 8.10)
GetContinueListeningUseCase      // FR41 / 8.9  — reads playback_state singleton
GetExploreRecommendationsUseCase // FR26–28 / 8.10 — on-device listening history

// Library, Playback, Auth (retained — unchanged)
SyncPodcastLibraryUseCase, SetEpisodeTierUseCase, MarkEpisodePlayedUseCase,
ResolvePodcastRssFeedUseCase, ResolveEpisodeAudioUrlsUseCase,
HandlePlaybackFailureUseCase, SearchSpotifyShowsUseCase,
AuthenticateWithSpotifyUseCase, RevokeSpotifyAccessUseCase

// Retired 2026-04-24 (SCP §4.3) — code removal pending (SCP §5)
// BuildEpisodeQueueUseCase

// Deferred — Drive Mode / Vision scaffolding (retained as-built, not exercised)
// StartDriveSessionUseCase, GetEpisodeBriefingUseCase, RestoreSessionUseCase
```

**Home-Screen Composition (new):**
`HomeScreenViewModel` (Android) / `HomeScreenObservableObject` (iOS) composes four independent `Flow` outputs — `GetSortedPodcastsUseCase`, `GetUnlistenedCountUseCase` (keyed per podcast), `GetContinueListeningUseCase`, `GetExploreRecommendationsUseCase` — into a single `HomeScreenState`. Use cases do not reference one another; composition is strictly at the platform-ViewModel boundary. This replaces the former `DriveSessionState`-centred composition.

**Freshness-color encoding (FR38) — computed in `commonMain`:**
`GetUnlistenedCountUseCase` emits both the integer count **and** a `FreshnessBucket` enum (`TODAY`, `THIS_WEEK`, `OLDER` — exact breakpoints are a UX decision, owned by story 8.5). Platform UI maps `FreshnessBucket` → color token. Rationale: freshness is a business rule, must be testable in `commonTest`, and must render identically across Android/iOS — moving it to UI would fork the rule into two codebases.

**Tier promotion (FR44) — write-only use case:**
`PromoteTierUseCase` writes the new tier to `PodcastRepository` and returns. No event emission. The sorted list re-emits via `GetSortedPodcastsUseCase`'s Flow; UI animation (Compose `animateItemPlacement`, SwiftUI `.matchedGeometryEffect`) derives from the list diff. Keeps the use case pure and testable.

**Platform UI State:**
- Android: `StateFlow<HomeScreenState>` in `ViewModel`, collected in Compose
- iOS: `@Published var state: HomeScreenState` in `ObservableObject`, consumed in SwiftUI

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

### Cross-Component Dependencies (revised 2026-04-24)

**Next-release active dependencies:**
- **SpotifyApiClient** depends on SecureStorage (tokens) and SQLDelight (episode cache write)
- **`HomeScreenViewModel`** composes four independent flows: `GetSortedPodcastsUseCase` + `GetUnlistenedCountUseCase` + `GetContinueListeningUseCase` + `GetExploreRecommendationsUseCase`
- **`GetSortedPodcastsUseCase`** depends on `PodcastRepository` + `EpisodeRepository` (joined read; three-band sort derived)
- **`GetUnlistenedCountUseCase`** depends on `EpisodeRepository` (count + freshness bucket)
- **`GetContinueListeningUseCase`** depends on `PlaybackStateRepository` (singleton row) + `EpisodeRepository` (episode metadata for display)
- **`PromoteTierUseCase`** depends on `PodcastRepository` (write-only; sorted list re-emits via Flow)
- **`PlaybackForegroundService`** depends on `AudioPlayer` (expect/actual) + `PlaybackStateRepository` (persist-before-emit of position snapshots)

**Scaffolding dependencies (Drive Mode / Vision — not exercised):**
- `DriveSessionManager` depends on BT trigger (platform) + SpotifyApiClient + BriefingEngine
- `BriefingEngine` depends on `GetEpisodeBriefingUseCase` + TTS expect/actual (platform)
- `RestoreSessionUseCase` depends on SQLDelight (reads persisted `drive_sessions` row)

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

**Drive Session State Machine — RETAINED AS SCAFFOLDING (not exercised in next release):**

The `DriveSessionState` sealed hierarchy remains defined in `commonMain/domain/model/DriveSessionState.kt` and is retained as-built for the deferred Drive Mode / Vision phase. **Next-release code paths (Epic 8 home screen + in-app playback) do not observe, emit, or import `DriveSessionState`.** Home-screen ViewModels observe repository flows directly (see *Home-Screen Composition* above). `DriveSessionManager` and `DriveSessionEvent` are likewise retained as scaffolding.

```kotlin
// Retained for Drive Mode / Vision phase — not wired to Epic 8.
sealed class DriveSessionState {
    object Idle : DriveSessionState()
    object Activating : DriveSessionState()
    data class Briefing(val script: BriefingScript) : DriveSessionState()
    data class Playing(val episode: Episode, val positionMs: Long) : DriveSessionState()
    object Paused : DriveSessionState()
    data class Error(val error: AppError) : DriveSessionState()
    object Ended : DriveSessionState()
}

// Retained for Drive Mode / Vision phase — not wired to Epic 8.
sealed class DriveSessionEvent {
    object BluetoothConnected : DriveSessionEvent()
    object BluetoothDisconnected : DriveSessionEvent()
    object BriefingConfirmed : DriveSessionEvent()
    object SkipEpisode : DriveSessionEvent()
    object PauseSession : DriveSessionEvent()
    object ResumeSession : DriveSessionEvent()
}
```

**State transitions (historical, Vision phase):** Only `DriveSessionManager` (commonMain) may mutate `DriveSessionState`. Platform ViewModels observe — they never mutate. This rule is preserved for when Drive Mode is re-enabled.

**Anti-pattern for next release:** New Epic 8 ViewModels **must not** subscribe to or import `DriveSessionState`, `DriveSessionManager`, or `DriveSessionEvent`. Wire home-screen UI state off the new use-case flows (`GetSortedPodcastsUseCase`, `GetUnlistenedCountUseCase`, `GetContinueListeningUseCase`, `GetExploreRecommendationsUseCase`). Rationale: the pivot replaces a single state machine with composable independent flows — mixing the two models produces the worst of both.

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
│       │   │   # Home Screen — Epic 8 Core MVP
│       │   │   ├── GetSortedPodcastsUseCase.kt         # FR36, FR37 — three-band sort
│       │   │   ├── GetLatestUnlistenedUseCase.kt       # FR39 — quick-play source
│       │   │   ├── GetUnlistenedCountUseCase.kt        # FR38 — count + FreshnessBucket
│       │   │   ├── PromoteTierUseCase.kt               # FR44 — tile long-press (write-only)
│       │   │   # Home Screen — Epic 8 Fast-Follow
│       │   │   ├── GetContinueListeningUseCase.kt      # FR41 — story 8.9
│       │   │   ├── GetExploreRecommendationsUseCase.kt # FR26–28 — story 8.10
│       │   │   # Library, Tier, Playback (retained)
│       │   │   ├── SyncPodcastLibraryUseCase.kt        # FR4, FR7–9
│       │   │   ├── SetEpisodeTierUseCase.kt            # FR5, FR6
│       │   │   ├── MarkEpisodePlayedUseCase.kt         # playback
│       │   │   ├── ResolvePodcastRssFeedUseCase.kt     # ADR-002 (4.8)
│       │   │   ├── ResolveEpisodeAudioUrlsUseCase.kt   # ADR-002 (4.9)
│       │   │   ├── HandlePlaybackFailureUseCase.kt     # FR25, FR29
│       │   │   ├── AuthenticateWithSpotifyUseCase.kt   # FR1, FR2
│       │   │   ├── RevokeSpotifyAccessUseCase.kt       # FR3, FR35
│       │   │   ├── SearchSpotifyShowsUseCase.kt
│       │   │   # Retired 2026-04-24 (SCP §4.3) — code removal pending
│       │   │   # ├── BuildEpisodeQueueUseCase.kt       # REMOVED
│       │   │   # Drive Mode / Vision scaffolding — retained as-built, not exercised
│       │   │   ├── StartDriveSessionUseCase.kt     # [scaffolding] FR10, FR11
│       │   │   ├── GetEpisodeBriefingUseCase.kt    # [scaffolding] FR14–FR18
│       │   │   └── RestoreSessionUseCase.kt        # [scaffolding] FR31 (drive-session variant)
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
│           │   ├── home/                       # PRIMARY UX SURFACE — Epic 8
│           │   │   ├── HomeScreen.kt           # FR36–FR45 — three-band list, quick-play, expand-in-place
│           │   │   ├── HomeViewModel.kt        # composes 4 use-case Flows → HomeScreenState
│           │   │   ├── components/
│           │   │   │   ├── PodcastTile.kt              # FR38, FR39, FR40, FR44
│           │   │   │   ├── ContinueListeningBar.kt     # FR41 — story 8.9
│           │   │   │   └── ExplorePanel.kt             # FR26–28 — story 8.10
│           │   │   └── ShowAllScreen.kt        # FR43 — full library management
│           │   ├── library/
│           │   │   ├── LibraryScreen.kt        # FR5, FR6 — LOVED/LIKED tagging
│           │   │   └── LibraryViewModel.kt
│           │   ├── settings/
│           │   │   └── SettingsScreen.kt       # FR3, FR33–FR35
│           │   ├── drive/                      # [scaffolding — Drive Mode / Vision, not in next-release nav]
│           │   │   ├── DriveSessionScreen.kt   # FR12, FR13, FR19, FR25 (unused)
│           │   │   └── DriveSessionViewModel.kt
│           │   └── theme/
│           │       └── Theme.kt
│           └── service/
│               └── PlaybackForegroundService.kt     # In-app playback audio focus + media session
│                                                    # (renamed from DriveSessionForegroundService)
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
        │   ├── home/                        # PRIMARY UX SURFACE — Epic 8
        │   │   ├── HomeView.swift           # FR36–FR45
        │   │   ├── HomeViewModel.swift      # composes 4 use-case Flows → HomeScreenState
        │   │   ├── components/
        │   │   │   ├── PodcastTile.swift           # FR38, FR39, FR40, FR44
        │   │   │   ├── ContinueListeningBar.swift  # FR41 — story 8.9
        │   │   │   └── ExplorePanel.swift          # FR26–28 — story 8.10
        │   │   └── ShowAllView.swift        # FR43 — full library management
        │   ├── drive/                        # [scaffolding — Drive Mode / Vision, not in next-release nav]
        │   │   ├── DriveSessionView.swift   # FR12, FR13, FR19, FR25 (unused)
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

**Audio Boundary (revised 2026-04-24 — decoupled from drive-session lifecycle):**
- **Android:** `PlaybackForegroundService` (successor to `DriveSessionForegroundService`) owns audio focus and media-session controls for in-app ExoPlayer playback. Lifecycle is tied to `AudioPlayer.play/stop`, not to any session concept. Launched on user tap of quick-play or Continue Listening; torn down when playback ends or is explicitly stopped. *Rename is documented here; class rename in `androidApp/` is a follow-up refactor that should land alongside this doc revision for naming parity.*
- **iOS:** `AudioSessionManager.swift` configures `AVAudioSession` for in-app AVPlayer playback (post-MVP per ADR-002). Background Audio mode is declared for playback continuation when the app is backgrounded; not for drive-session activation.
- **Retained as scaffolding:** The drive-session-coupled foreground service code and iOS AVAudioSession drive-mode configuration remain in the codebase for the Vision phase. No next-release code path launches them. `BluetoothTrigger` (expect/actual) and `TtsEngine` (expect/actual) are similarly retained; see Risk 6 in Validation § regarding permission / initialisation gating.

---

### Requirements → Structure Mapping

| FR Group | FRs | Primary Files |
|---|---|---|
| Authentication | FR1–3 | `SpotifyAuthManager.kt`, `SecureStorage` (actual), `OnboardingScreen` |
| Podcast Library | FR4–9 | `SyncPodcastLibraryUseCase`, `SpotifyApiClient`, `EpisodeQueries.sq` |
| **Home Screen** (Epic 8) | **FR36–FR45** | `GetSortedPodcastsUseCase`, `GetUnlistenedCountUseCase`, `GetLatestUnlistenedUseCase`, `GetContinueListeningUseCase`, `GetExploreRecommendationsUseCase`, `PromoteTierUseCase`, `HomeScreen` / `HomeView` + `HomeViewModel`, `PodcastTile`, `ContinueListeningBar`, `ExplorePanel`, `ShowAllScreen` / `ShowAllView` |
| Playback | FR21, FR24, FR25 | `AudioPlayer` (expect/actual — ADR-002), `PlaybackForegroundService`, `HandlePlaybackFailureUseCase`, `MarkEpisodePlayedUseCase`, `playback_state` singleton |
| Explore Recommendations | FR26–FR28 | `GetExploreRecommendationsUseCase`, `ExplorePanel` |
| Resilience | FR29–32 | `AppError`, `SpotifyApiClient` retry, `playback_state` restoration (NFR31), staleness indicator |
| Privacy & Data | FR33–35 | `SecureStorage.clear()`, SQLDelight delete queries, `SettingsScreen` |
| BT Trigger *(deferred — Drive Mode / Vision)* | FR10–13 | `BluetoothTrigger` (actual), `DriveSessionManager` — retained, not exercised |
| AI Briefing *(deferred — Drive Mode / Vision)* | FR14–20 | `GetEpisodeBriefingUseCase`, `BriefingScriptGenerator`, `TtsEngine` (actual) — retained, not exercised |

---

### Data Flow — Home Screen (next-release primary)

```
App open
    → HomeScreenViewModel.observe()
    → GetSortedPodcastsUseCase (Flow)    → SqlDelightPodcastRepository + SqlDelightEpisodeRepository
                                            (three-band sort derived; no schema change)
    → GetUnlistenedCountUseCase (Flow)   → SqlDelightEpisodeRepository
                                            (count + FreshnessBucket per podcast)
    → GetContinueListeningUseCase (Flow) → SqlDelightPlaybackStateRepository (singleton row)
    → [if all counts = 0]
      GetExploreRecommendationsUseCase (Flow) → on-device listening-history store
    → HomeScreenState composed at ViewModel → UI renders three-band list
                                              + optional Continue Listening bar
                                              + Explore mode when caught up

User taps ▶ quick-play on tile
    → HomeScreenViewModel.quickPlay(podcastId)
    → GetLatestUnlistenedUseCase(podcastId) → Episode
    → audio_url present? (ADR-002)
         YES → AudioPlayer.play(episode.audioUrl, resumePositionMs = 0)   [primary]
                → PlaybackForegroundService (Android) / AudioSessionManager (iOS) takes audio focus
                → playback_state persisted on interval + pause/stop (persist-before-emit)
         NO  → Deep-link spotify:episode:{id}                             [fallback]

User taps tile body (FR40)
    → HomeScreenViewModel.toggleExpand(podcastId)
    → Expanded view uses cached episode list from GetSortedPodcastsUseCase payload
    → (No new shared-domain call required — expansion is pure UI state)

User long-presses tile (FR44)
    → platform action sheet → PromoteTierUseCase(podcastId, newTier)
    → SqlDelightPodcastRepository.updateTier
    → GetSortedPodcastsUseCase Flow re-emits (podcast moves bands)
    → UI re-renders with animation (Compose animateItemPlacement /
                                    SwiftUI .matchedGeometryEffect)

User taps Continue Listening bar (FR41 — story 8.9)
    → HomeScreenViewModel.resumeContinueListening()
    → playback_state.episode_id + position_ms read
    → AudioPlayer.play(audioUrl, resumePositionMs = position_ms)
```

### Data Flow — Drive Mode / Vision (retained as scaffolding, not exercised)

```
Bluetooth event → BluetoothTrigger.kt (actual) → DriveSessionManager
  → GetEpisodeBriefingUseCase → BriefingScriptGenerator
  → TtsEngine.kt (actual) → DriveSessionState.Briefing → …
```
*(Preserved here as a reference; not invoked by next-release code paths. Full flow in commit history prior to 2026-04-24.)*

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

### Coherence Validation ✅ (revised 2026-04-24)

All technology choices are compatible and non-conflicting. KMP + SQLDelight + Ktor + expect/actual form a well-established production stack. Platform-native ViewModels calling shared KMP use cases is the lowest-risk KMP architecture pattern for next release. PKCE OAuth + no backend is correct for mobile. ExoPlayer / AVPlayer via `AudioPlayer` expect/actual (ADR-002) serves the primary in-app playback path; Spotify deep-link remains as fallback.

Patterns are internally consistent in the new composition model: `Result<T>` error handling and the persist-before-emit rule extend cleanly to `playback_state` (Continue Listening, NFR31). Home-screen composition via independent flows replaces the former single-state-machine model — the two models are kept orthogonal by the Drive Mode scaffolding rules in the Communication Patterns §.

---

### Requirements Coverage Validation ✅ (revised 2026-04-24)

- **Functional Requirements:** 45 FRs total — 38 active in next release, 12 deferred (FR10–FR20 Drive Mode / Vision scaffolding), FR22 & FR23 removed (SCP §4.2). Active FRs all mapped to specific files and directories.
- **Non-Functional Requirements:** 20/20 retained; NFR1 and NFR5 deferred alongside Drive Mode. NFR2 becomes the primary performance target for next release.
- All 8 active FR categories mapped in Requirements → Structure table
- 4 key technical constraints (iOS background execution, Spotify quota, rate limits, GDPR) still addressed; iOS background execution now framed around in-app playback continuation, not drive-session activation

---

### Gap Analysis (revised 2026-04-24)

**Important (non-blocking — no critical gaps):**

1. **`BuildEpisodeQueueUseCase` dead-code removal.** File still present at `shared/src/commonMain/kotlin/com/poddrive/usecase/BuildEpisodeQueueUseCase.kt`. Tracked by SCP §5 as surgical-removal task. Until removed, a gap exists between doc (retired) and code (present).

2. **Drive Mode scaffolding debt.** `drive_sessions` table, `DriveSessionManager`, `DriveSessionEvent`, `DriveSessionState`, `BluetoothTrigger` (expect/actual), `TtsEngine` (expect/actual), `BriefingScriptGenerator`, and three deferred use cases are retained but unused. Accept as explicit tech debt carried for the Vision phase. Reassess cost if Drive Mode is not re-greenlit within ~6 months (see Risk 6 for permission-gating recommendation).

3. **Home-screen loading state ambiguity.** When library sync is in-flight and SQLDelight has no cached podcasts, `HomeScreenState` must be empty-but-not-Explore. Explore mode triggers on *sync-complete + zero unlistened*, not on *empty cache*. Flag for story 8.10.

4. **Continue Listening / Sorted List interaction (Deferred Q3 extended).** If the user is mid-way through an episode of Podcast A, does that episode still count in `GetUnlistenedCountUseCase`'s tally for Podcast A? Recommendation: "in-progress" is a third state distinct from played/unplayed; the count excludes it so Continue Listening bar and the count do not disagree. If Continue Listening is strictly single-episode, derivation from `is_played` + `playback_state` works without schema change. Confirm during story 8.9.

5. **Listening-history richness.** Current schema carries `is_played` (boolean) but no `played_at` timestamp or partial-consumption data. If Explore v1 needs "shows you've listened to multiple episodes of recently", a schema addition is required. Not added pre-emptively; flagged for story 8.10.

6. **Bluetooth + TTS init cost in retained scaffolding.** Android `BLUETOOTH_CONNECT` permission requires runtime consent; TTS engine init happens on app launch. If the scaffolding remains live, the user is asked for permissions the app does not use. **Recommendation:** gate `BluetoothTrigger.actual` receiver registration and `TtsEngine.actual` init behind a disabled feature flag; keep code present, keep initialisation dormant. Honours "retain as-built" without user-facing consequences.

7. **`DriveSessionForegroundService` → `PlaybackForegroundService` rename.** Doc reflects the rename. Code rename is a follow-up refactor affecting `androidApp/` call sites and `AndroidManifest.xml` service declaration. Should land alongside this doc revision for naming parity.

8. **Orphaned `drive_sessions` data.** No next-release writer; any rows written during Epic 3 testing remain forever. **Recommendation:** one-shot `deleteAllSessions` migration on first launch after this release to flush stale data. Cheap, safe.

9. **KMP → iOS XCFramework integration:** For next release, Gradle generates XCFramework embedded directly in `iosApp.xcodeproj`. Migrate to SPM local package post-MVP if needed.

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

**Key Strengths (next-release focus):**
- KMP eliminates duplication in the shared home-screen domain layer (sort engine, unlistened-count + freshness rule, continue-listening, explore) while preserving native UI
- Independent-flow composition at the ViewModel boundary is simpler to reason about than a single state machine: each use case is unit-testable in `commonTest` in isolation, and the home screen is a function of its inputs
- Persist-before-emit rule extends cleanly to `playback_state`, supporting NFR31 (state recovery after OS kill) via the Continue Listening bar
- ADR-002 RSS + ExoPlayer path makes NFR2 (≤5s from home-open to audio) achievable without Spotify app context-switch for the ~100% of tested Greek catalog with accessible RSS
- expect/actual cleanly encapsulates all platform-divergent code (`AudioPlayer`, SecureStorage, and scaffolding BT + TTS) behind shared interfaces

**Deferred Capabilities (Drive Mode / Vision phase — retained as-built):**
- Drive-session auto-activation on Bluetooth (FR10–FR13) — infrastructure intact, not exercised
- AI voice briefing + hands-free confirmation (FR14–FR20) — `BriefingScriptGenerator`, `TtsEngine`, `DriveSessionManager` intact
- Drive-session OS-kill recovery via `DriveSessionState` + `drive_sessions` table — intact, quiescent

**Areas for Future Enhancement (Post-pivot MVP):**
- Shared KMP ViewModels (evaluate at Growth phase when patterns mature)
- Server infrastructure (triggered by creator marketplace or Explore v2 remote recommendations)
- SPM local package for KMP → iOS integration
- Listening-history schema enrichment (once Explore v1 signal-quality is validated)

---

### Implementation Handoff

**AI Agent Guidelines (revised 2026-04-24):**
- Read `shared/commonMain` interfaces before implementing any platform feature
- Follow all patterns in the Implementation Patterns section exactly
- Refer to the Requirements → Structure Mapping table for FR traceability
- **Epic 8 rule:** home-screen ViewModels compose `Flow` outputs from the six home-screen use cases directly. Do not import or observe `DriveSessionState`, `DriveSessionManager`, or `DriveSessionEvent` — those are Drive Mode / Vision scaffolding.
- **Drive Mode scaffolding rule:** if touching retained Drive Mode code, preserve the sealed-class authority — do not add `DriveSessionState` variants without updating all `when` expressions, even though the state is not currently exercised.
- **Freshness rule:** compute `FreshnessBucket` in `GetUnlistenedCountUseCase`; UI maps bucket → color token. Do not place freshness logic in platform code.

**Next Implementation Story (post-pivot):**
Surgical removal of `BuildEpisodeQueueUseCase` per SCP §5, followed by Epic 8 story 8.1 (three-band sort — `GetSortedPodcastsUseCase`). Prerequisite for subsequent Epic 8 stories that compose against the sort.

---

## Architecture Decision Records (ADRs)

### ADR-001: Active-Device Polling for Reliable Free-Tier Single-Tap Podcast Playback

**Date:** 2026-04-08
**Status:** Superseded by ADR-002
**Superseded reason:** `PUT /v1/me/player/play` (Spotify Web API Player endpoint) requires a Spotify Premium account. The free-tier exception for podcast episodes documented in Story 4.6 was incorrect. Confirmed empirically 2026-04-09.
**Deciders:** Winston (Architect), Dimitrios.dimitrelos

#### Context

Story 4.6 achieved auto-play by calling `PUT /v1/me/player/play` with a fixed 1,500 ms delay after the deep-link fires. This works reliably when Spotify is already running (warm start) but has a **race condition on cold start**:

- Deep link fires → Android launches Spotify (cold)
- Spotify must initialize, connect to its backend, and register itself as the active playback device with Spotify's cloud
- Cold-start device registration typically takes 2–4 s on mid-range hardware
- The fixed 1,500 ms delay expires before registration completes → `HTTP 404 No active device` → silent fallback → **user must press Play manually in Spotify (second tap)**

This is the root cause of non-premium users experiencing a second click, even though Story 4.6 is fully implemented.

#### Options Evaluated

| Option | Verdict |
|---|---|
| Increase fixed delay (e.g. 3,000 ms) | Fragile — device-dependent; wastes time on warm starts; still fails on very slow cold starts |
| Spotify App Remote SDK `PlayerApi.play(uri)` | **Rejected** — Android SDK silently ignores episode URIs; only Track/Album/Artist/Playlist URIs are accepted. Verified in Story 4.6 research (2026-04-06). |
| Spotify Web Playback SDK (browser streaming) | **Rejected** — premium accounts only; violates free-tier requirement |
| RSS + ExoPlayer in-app audio playback | Out of scope for MVP; licensing complexity; breaks Spotify playback position sync |
| **Active-device polling (selected)** | Poll `GET /v1/me/player` up to 5,000 ms at 500 ms intervals; fire `PUT /v1/me/player/play` as soon as device registration is confirmed |

#### Decision

Replace the fixed `delay(AUTO_PLAY_DELAY_MS)` in `SpotifyAutoPlayCoordinator` with an **adaptive active-device polling loop**:

1. After the deep link fires, immediately begin polling `GET /v1/me/player`
2. If the response contains a non-null `device` object → device is registered → fire `PUT /v1/me/player/play` → break
3. If no device appears within `DEVICE_POLL_TIMEOUT_MS` (5,000 ms) → exit silently; existing `HANDOFF_TIMEOUT_MS` safety net handles UX
4. Polling interval: `DEVICE_POLL_INTERVAL_MS` = 500 ms (10 polls max; negligible Spotify rate limit impact — only during active user handoff)

This approach is **adaptive**: it fires the play command the moment Spotify is ready, making it both faster on warm starts (~0–500 ms vs. 1,500 ms flat) and reliable on cold starts (~2–3 s when the device finally registers).

#### Consequences

**Positive:**
- Eliminates second-click for cold-start Spotify on free-tier accounts
- Faster auto-play on warm starts (first successful poll at 0 ms + ~100 ms RTT vs. fixed 1,500 ms)
- No new SDK dependencies — stays entirely within existing `SpotifyAutoPlayCoordinator` + `SpotifyPlayerApiClient` + Ktor architecture
- `user-read-playback-state` scope (required for `GET /v1/me/player`) is already in `REQUIRED_SCOPES` — no OAuth change needed

**Negative:**
- Up to 10 `GET /v1/me/player` calls per handoff in the worst case (cold-start, slow device). Mitigated: only during active user-initiated handoffs; never background polling.
- Slightly more complex coordinator logic (polling loop replaces single delay + call)

**Neutral:**
- The 3,000 ms `HANDOFF_TIMEOUT_MS` safety net (Story 4.1) is unchanged
- `AUTO_PLAY_DELAY_MS` constant is retired; replaced by `DEVICE_POLL_INTERVAL_MS` and `DEVICE_POLL_TIMEOUT_MS`
- `DEVICE_POLL_TIMEOUT_MS` (5,000 ms) intentionally exceeds `HANDOFF_TIMEOUT_MS` (3,000 ms); the `isHandingOff` guard (Story 4.6 AC4/AC7) causes the loop to exit cleanly when the handoff timeout fires

#### Timing Diagram

```
t=0 ms     Deep link fires → isHandingOff = true → polling starts
t=0 ms     Poll 1: GET /v1/me/player → 404 (Spotify still cold-starting)
t=500 ms   Poll 2: → 404
t=1000 ms  Poll 3: → 200 {"device": {"id": "abc123"}} ← device registered
t=1000 ms  PUT /v1/me/player/play → 204 ← episode playing ✓
           clearHandingOff() called → handoffTimeoutJob cancelled

--- worst-case cold start ---

t=3000 ms  HANDOFF_TIMEOUT_MS fires (user hasn't returned)
           → isHandingOff = false
t=3000 ms  Next poll iteration: isHandingOff guard triggers → loop exits
           (DEVICE_POLL_TIMEOUT_MS is the backstop for the rare case
            where handoff timeout has not fired yet)
```

#### Implementation Scope

- **Modified:** `SpotifyAutoPlayCoordinator.kt` (`commonMain`) — polling loop replaces fixed delay
- **Modified:** `SpotifyPlayerApiClient.kt` (`commonMain`) — add `hasActiveDevice(): Boolean`
- **Modified:** `SpotifyModels.kt` (`commonMain`) — add `ActivePlayerResponse`, `DeviceInfo`
- **Modified:** `SpotifyAutoPlayCoordinatorTest.kt`, `SpotifyPlayerApiClientTest.kt`, `DriveSessionViewModelTest.kt`
- **No changes to:** `DriveSessionState`, `DriveSessionManager`, `DriveSessionViewModel` public API, OAuth scopes, `HANDOFF_TIMEOUT_MS`

> **This ADR is superseded. The Web API Player endpoint requires Spotify Premium — the approach was never viable for free-tier users. See ADR-002.**

---

### ADR-002: In-App Podcast Playback via RSS + ExoPlayer (free-tier, single-tap)

**Date:** 2026-04-09
**Status:** Accepted
**Deciders:** Winston (Architect), Dimitrios.dimitrelos

#### Context

All Spotify-controlled playback mechanisms have been eliminated for free-tier users:

| Mechanism | Why eliminated |
|---|---|
| Spotify App Remote SDK `PlayerApi.play(uri)` | Silently rejects episode URIs — only Track/Album/Artist/Playlist supported (Story 4.6, 2026-04-06) |
| Spotify Web API `PUT /v1/me/player/play` | Requires Spotify Premium — confirmed empirically 2026-04-09; Story 4.6 assumption was incorrect |
| Spotify deep link only (no auto-play) | Opens Spotify to the episode page but user must tap Play — second click, not acceptable |

The only remaining path to single-tap, free-tier playback is to **take Spotify out of the audio playback loop entirely**: fetch the episode's audio file directly from the podcast's public RSS feed and play it in-app using ExoPlayer.

#### Key Constraints Discovered

- **Spotify API exposes no RSS data.** `SpotifyShow` and `SpotifyEpisodeObject` have no `feedUrl`, `rssUrl`, or equivalent field. RSS feed URL must be sourced externally.
- **anchor.fm (Spotify-owned) feeds are publicly accessible.** Initial assumption that Spotify-hosted anchor.fm feeds would be gated is incorrect — empirically confirmed 2026-04-09 (see Empirical Validation below). Only bespoke Spotify Originals with exclusive distribution have no public RSS.
- **KMP RSS XML parsing.** `commonMain` has no access to `javax.xml` or Android's `XmlPullParser`. Parsing must be done in `androidMain` via `expect/actual`.

#### Empirical Validation — Greek Spotify Podcast Catalog (2026-04-09)

iTunes Search API + live RSS accessibility experiment run against the Greek Spotify podcast catalog (229 unique podcasts):

| Metric | Result |
|---|---|
| Podcasts with `feedUrl` in iTunes API | **99.6%** (228 / 229) |
| Live RSS feeds accessible (HTTP 200 + valid XML) | **100%** (18 / 18 sampled) |
| anchor.fm (Spotify-owned) feeds accessible without auth | **Yes** — all 3 tested returned full public RSS XML |
| Share of Greek catalog on anchor.fm | 43% (98 / 228) |
| Share on standard RSS hosts (Megaphone, Buzzsprout, Simplecast, etc.) | 57% (130 / 228) |

**Conclusion:** RSS-based in-app playback is viable for virtually the entire Greek Spotify podcast catalog. Bespoke Spotify Originals (not present in the GR sample) remain the only category requiring the deep-link fallback.

#### Decision

Adopt a **two-layer playback strategy**:

1. **Primary path (free-tier, single-tap):** Resolve podcast RSS feed via iTunes Search API → parse episode audio URL from RSS → play directly in-app via `ExoPlayer` (Media3)
2. **Fallback path (Spotify-exclusive or RSS unavailable):** Existing `spotify:episode:ID` deep link → user manually taps Play in Spotify

#### RSS Feed Discovery: iTunes Search API

Spotify does not expose RSS URLs. The **iTunes Podcast Search API** (`https://itunes.apple.com/search?term={name}&entity=podcast`) is the industry-standard free, no-auth-required directory covering the vast majority of public podcasts. Response includes `feedUrl` per result.

Called once per podcast at sync time. Result stored in new `rss_feed_url TEXT` column on `podcasts` table. A null value = Spotify-exclusive or discovery failed = use fallback path.

#### Episode Audio URL Resolution

RSS feed XML → parse `<item>` elements → match episode by title (normalized, case-insensitive) + publication date (±1 day tolerance) → extract `<enclosure url="…" type="audio/mpeg"/>`.

Stored in new `audio_url TEXT` column on `episodes` table. Null = unresolved = use fallback.

Resolution runs during `SyncPodcastLibraryUseCase` (after episode fetch) or lazily on first queue load.

#### ExoPlayer (Media3) Integration

ExoPlayer replaces the Spotify deep-link handoff for episodes with a known `audio_url`:

```
User taps episode
  → audio_url present in SQLDelight? YES
      → AudioPlayer.play(audioUrl, resumePositionMs)   ← in-app, immediate, no context switch
      → DriveSessionState.Playing emitted
  → audio_url null (Spotify-exclusive)?
      → existing deep link (spotify:episode:ID)        ← fallback, user taps Play in Spotify
```

**`AudioPlayer` expect/actual interface:**
```kotlin
// commonMain
expect class AudioPlayer {
    fun play(url: String, resumePositionMs: Long = 0)
    fun pause()
    fun resume()
    fun stop()
    fun currentPositionMs(): Long
}
// androidMain → ExoPlayerAudioPlayer (wraps androidx.media3:media3-exoplayer)
// iosMain → AVPlayerAudioPlayer (wraps AVPlayer) — deferred post-MVP
```

`DriveSessionForegroundService` owns the `AudioPlayer` instance and its lifecycle.

#### New Schema Columns

```sql
-- podcasts table (Podcast.sq)
ALTER TABLE podcasts ADD COLUMN rss_feed_url TEXT;          -- null = Spotify-exclusive

-- episodes table (Episode.sq)
ALTER TABLE episodes ADD COLUMN audio_url TEXT;             -- null = unresolved / fallback
```

#### Consequences

**Positive:**
- Single-tap playback for free-tier users on all public podcasts
- No Spotify app required for audio playback — user stays in PodDrive
- Audio focus, background playback, MediaSession controls all owned by PodDrive
- Playback position sync within PodDrive is now direct (ExoPlayer position) — no Spotify dependency

**Negative:**
- Spotify-exclusive podcasts (no public RSS) remain on the two-tap deep-link path
- iTunes Search API matching is fuzzy — edge cases where podcast name search returns wrong show (mitigated: match by name + publisher + episode count)
- RSS XML must be fetched and cached; adds network call to sync flow
- `audio_url` must be refreshed periodically (episode URLs may change or expire on some CDNs)
- Stories 4.1 (deep link), 4.6 (Web API auto-play), and 4.7 (device polling) are partially superseded; 4.1 becomes the fallback path only

**Neutral:**
- `SpotifyPlayerApiClient`, `SpotifyAutoPlayCoordinator`, and related Web API plumbing remain in the codebase as dead code for now — removal is a separate clean-up task
- `DriveSessionState.Playing` is unchanged — `positionMs` now comes from ExoPlayer instead of Spotify

#### Implementation Stories Required

| Story | Scope |
|---|---|
| 4.8 | RSS Feed Discovery — iTunes Search API → `rss_feed_url` column |
| 4.9 | Episode Audio URL Resolution — RSS XML parse → `audio_url` column |
| 4.10 | ExoPlayer Integration — `AudioPlayer` expect/actual + `DriveSessionForegroundService` wiring |
| 4.11 | In-App Playback Controls — progress, pause, skip within PodDrive drive screen |

Story 4.1 is demoted to fallback-only status (no story change needed — behavior is unchanged for Spotify-exclusive content). Stories 4.6 and 4.7 are superseded; their Web API / polling code is dead but not yet removed.
