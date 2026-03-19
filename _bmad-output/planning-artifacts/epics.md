---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
inputDocuments:
  - '_bmad-output/planning-artifacts/prd.md'
  - '_bmad-output/planning-artifacts/architecture.md'
  - '_bmad-output/planning-artifacts/ux-design-specification.md'
---

# PodDrive - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for PodDrive, decomposing the requirements from the PRD, UX Design Specification, and Architecture Decision Document into implementable stories.

---

## Requirements Inventory

### Functional Requirements

FR1: A user can authenticate with their Spotify account using OAuth 2.0 PKCE flow to grant PodDrive access to their podcast library and playback controls.
FR2: The system can silently refresh the Spotify access token without requiring the user to re-authenticate.
FR3: A user can revoke PodDrive's Spotify access and delete all locally stored data.
FR4: The system can retrieve all podcasts the user follows on Spotify and their associated unheard episodes.
FR5: A user can tag any followed podcast as LOVED or LIKED.
FR6: A user can change a podcast's tier (LOVED → LIKED, LIKED → LOVED, or remove tier).
FR7: The system can identify which episodes from followed podcasts have not yet been played.
FR8: The system can cache the podcast library locally so episode data is available without a live Spotify connection.
FR9: The system can sync the podcast library in the background and update the cache silently.
FR10: The system can detect when the user's phone connects to a Bluetooth audio device (car).
FR11: The system can activate a drive session automatically upon Bluetooth connection without any user action.
FR12: The system can remain active in the background with the phone screen locked during a drive session.
FR13: A user can manually trigger a drive session from within the app when Bluetooth auto-detection is unavailable.
FR14: The system can generate and deliver a spoken briefing of new podcast episodes using on-device TTS at drive session start; the briefing addresses the user by first name, uses a warm and conversational tone, and is designed to feel like a personal assistant.
FR15: The briefing presents LOVED episodes before LIKED episodes.
FR16: The briefing includes episode title, podcast name, and duration for each new episode.
FR17: The briefing adapts its message when no new episodes are available from LOVED podcasts.
FR18: The briefing adapts its message when the inbox is completely empty across all tiers.
FR19: A user can respond to the briefing using a steering wheel button or on-screen tap to confirm playback without navigating the app.
FR20: The system can duck (temporarily lower) audio from other apps (e.g., navigation) during the briefing and restore it afterward.
FR21: The system can initiate playback of a selected podcast episode through Spotify without requiring the user to open or navigate the Spotify app.
FR22: The system can queue multiple episodes for sequential playback in priority order (LOVED first, then LIKED).
FR23: When an episode ends, the system can automatically begin playing the next unheard episode from the queue.
FR24: The system can resume playback from the last known position when a drive session reconnects to the same episode.
FR25: The system can handle Spotify playback failures gracefully and notify the user via voice without crashing.
FR26: When a user's episode inbox is empty, the system can suggest a single episode from outside the user's subscriptions based on their listening history and interests.
FR27: The system presents one recommendation — not a list — when the inbox is empty.
FR28: A user can accept or decline the suggested episode without touching the screen.
FR29: The system can detect a Spotify API connection failure and inform the user via voice during the briefing.
FR30: The system can recover from a Spotify API timeout and resume the intended action when connectivity is restored.
FR31: The system can restore its last known state (queue, playback position, session context) if the app is terminated by the OS mid-drive and relaunched.
FR32: The system can display a staleness indicator when the cached episode library has not synced within 24 hours.
FR33: A user can view what data PodDrive stores about them.
FR34: All LOVED/LIKED tags and episode history used for recommendations are stored on-device only and are not transmitted to any server in the MVP.
FR35: A user can delete all locally stored data and reset the app to its initial state.

### NonFunctional Requirements

NFR1: The drive session briefing must begin playing within 10 seconds of Bluetooth audio device connection detection.
NFR2: Time from Bluetooth connection to first podcast audio playing must not exceed 30 seconds under normal network conditions.
NFR3: Podcast library sync (background) must complete within 60 seconds on a standard mobile data connection for a library of up to 50 followed podcasts.
NFR4: The app must not increase device battery consumption by more than 5% compared to baseline Spotify usage during a 60-minute drive session.
NFR5: On-device TTS briefing generation must add no more than 2 seconds of delay before audio begins.
NFR6: Spotify OAuth access tokens and refresh tokens must be stored in the platform's secure credential store (iOS Keychain / Android Keystore) and never in plain-text storage.
NFR7: No user listening data, LOVED/LIKED preferences, or episode history may be transmitted to any external server or third-party service in the MVP.
NFR8: The app must request only the minimum required Spotify API scopes and must not request permissions beyond those listed in the Domain Requirements.
NFR9: The app must not crash during a standard 60-minute drive session under normal conditions (background audio + Bluetooth + locked screen + incoming call).
NFR10: The Bluetooth connection trigger must activate the drive session on ≥95% of Bluetooth audio connection events in test conditions.
NFR11: The app must recover and resume normal operation within 10 seconds of the OS terminating the background process, without requiring user intervention.
NFR12: Graceful degradation must be implemented for all Spotify API failure scenarios — no user-visible crashes; voice notification required for any failure that prevents playback.
NFR13: All core drive session interactions (start briefing, confirm playback, skip episode) must be completable without looking at or touching the screen — via steering wheel media controls or voice.
NFR14: The AI briefing voice must use a clear, natural-sounding TTS voice at a pace comfortable for driving contexts (target: 150–160 words per minute).
NFR15: All non-drive-mode app screens (settings, LOVED/LIKED tagging, onboarding) must meet WCAG 2.1 Level AA contrast and tap target size requirements.
NFR16: The app must support dynamic text sizing (iOS Dynamic Type / Android font scale) for all non-briefing screens.
NFR17: The Spotify API integration must handle rate limiting transparently — if rate limits are reached, the app must queue the request and retry without user action or error messages.
NFR18: Spotify deep-link playback handoff must succeed within 3 seconds of the user confirming episode selection, assuming Spotify is installed and running.
NFR19: If the Spotify app is not installed on the device, PodDrive must detect this at onboarding and present a clear, actionable message — not a silent failure.
NFR20: The Spotify token refresh cycle must not interrupt an active drive session — token refresh must occur in the background without pausing audio or the briefing.

### Additional Requirements

- AR1: KMP monorepo structure — `poddrive/shared/`, `androidApp/`, `iosApp/`; Story 1 of Epic 1 is the KMP monorepo initialization + Android project setup (explicitly noted in Architecture document)
- AR2: Shared `commonMain` business logic layer: `SpotifyApiClient` (Ktor), domain models (`Podcast`, `Episode`, `EpisodeTier`), LOVED/LIKED persistence, episode queue & continuity engine, briefing script generator, recommendation engine v1, `DriveSessionState` state machine
- AR3: SQLDelight as cross-platform database with schema defined in `commonMain`; entities: `Podcast`, `Episode`, `EpisodeTier`, `PlaybackState`, `DriveSession`; caches episode library + persists drive session state for OS-kill recovery
- AR4: `expect/actual` pattern for all platform-specific implementations: Bluetooth trigger, TTS engine, audio session/focus, SecureStorage (iOS Keychain / Android EncryptedSharedPreferences), local DB drivers
- AR5: `Result<T, AppError>` sealed error handling pattern across all KMP use cases; `AppError` subtypes: `SpotifyApiError`, `NetworkUnavailable`, `SpotifyNotInstalled`, `RateLimitExceeded`, `Unknown`; no exceptions cross the KMP boundary
- AR6: `DriveSessionState` sealed class (`Idle → Activating → Briefing → Playing → Paused → Error → Ended`) persisted to SQLDelight on every state transition — only `DriveSessionManager` may mutate state
- AR7: Android-first development (Jetpack Compose + Foreground Service `mediaPlayback` type); iOS follows same KMP shared logic (SwiftUI + Background Audio mode declared in Info.plist)
- AR8: Spotify integration via Web API + deep-link URIs (`spotify:episode:{id}`) — no Spotify SDK in MVP; Ktor `Auth` plugin handles transparent bearer token refresh (NFR20)
- AR9: GitHub Actions + Fastlane CI/CD: Android → Google Play internal track; iOS → TestFlight; Gradle Version Catalog (`libs.versions.toml`) for dependency management
- AR10: KMP Use Cases in `commonMain`: `StartDriveSessionUseCase`, `SyncPodcastLibraryUseCase`, `GetEpisodeBriefingUseCase`, `SetEpisodeTierUseCase`, `GetRecommendationUseCase`, `RestoreSessionUseCase`
- AR11: No backend infrastructure in MVP — all Spotify API calls direct from device; no server-side user profiles; GDPR compliance via on-device-only data storage
- AR12: Rate limiting strategy: episode library fetch batched on Bluetooth connect only; Ktor `HttpRequestRetry` with 3 retries + exponential backoff; local request queue for playback commands

### UX Design Requirements

UX-DR1: Design token system must be implemented as `Theme.kt` (Android Compose `MaterialTheme` extensions) and `DesignTokens.swift` (iOS SwiftUI environment values) — shared specification covering: colour palette, typography scale, 8dp spacing grid, and custom iconography (LOVED heart, LIKED thumbs, drive session, Spotify handoff arrow)
UX-DR2: Drive mode colour scheme applied throughout: background `#0F0F12`, surface `#1C1C24`, elevated `#272736`; accent amber `#F59E0B`; LOVED coral `#F87171`; LIKED blue `#60A5FA`; handoff green `#34D399`; drive mode text combinations must exceed WCAG AAA (7:1+ contrast)
UX-DR3: Three distinct UI modes with navigation rules: (1) Idle/Management — standard bottom nav (Shows / Discover / Settings); (2) Drive Session Active — back gesture and bottom nav suppressed, drive screen is the only root; (3) Post-Drive (session ended)
UX-DR4: Drive session screen layout — two-zone dashboard: fixed top zone (`DriveSessionCard` with `BriefingAnimation` + stats strip showing LOVED N / LIKED N / Total N) + scrollable `EpisodeTile` queue below + full-width bottom-anchored "Open in Spotify" CTA; no more than 2 taps from session screen to Spotify playback
UX-DR5: Custom components Phase 1 (gates MVP drive experience): `BriefingAnimation` (full-screen overlay, ~1.2s enter, waveform pulses with TTS, respects Reduce Motion), `DriveSessionCard` (4 states: BRIEFING_ACTIVE / BRIEFING_COMPLETE / HANDING_OFF / ERROR), `EpisodeTile` drive variant (64dp height, 28sp title, single tap → deep-link), `TierBadge` ICON_ONLY (8dp coloured dot, never colour-alone)
UX-DR6: Custom components Phase 2 (completes MVP): `EmptyStatePanel` (WITH_RECOMMENDATION and NO_RECOMMENDATION states; TTS reads headline + recommendation on enter), `ShowTile` (artwork + name + new episode count + TierBadge + long-press tier/remove menu), `TierFilterTabs` (All / Loved / Liked pill tabs on idle screen), `TierBadge` FULL variant (icon + label pill)
UX-DR7: Onboarding flow: Welcome screen ("Your podcast co-pilot") → Spotify OAuth (with error + retry) → Bluetooth permission request with rationale explanation → Add first show via search (debounced 300ms, results show artwork + name + episode count) → Set tier LOVED/LIKED → Add more or complete → Idle screen ("Connect your car to start your first session")
UX-DR8: TTS-first feedback rule in drive mode — every state change announces via TTS before screen updates; required TTS scripts: "PodDrive is ready. You have N loved episodes." (session start) | "Opening [title] in Spotify." (episode selected) | "Session restored. Your queue is ready." (OS-kill recovery) | "No new episodes. Here is a recommendation." (empty inbox) | "Could not connect. Tap to retry." (error)
UX-DR9: Touch target minimums enforced at component level: all drive-mode elements ≥64dp; all management-mode elements ≥44dp; icon-only buttons 64dp circular; drive mode: one primary button visible at a time, bottom-anchored; no modal/alert dialogs for non-destructive actions — use bottom sheets
UX-DR10: Empty and error states: `EmptyStatePanel` never shows a blank screen — always WITH_RECOMMENDATION or NO_RECOMMENDATION calm dismissal; `DriveSessionCard` ERROR state includes retry action; Spotify handoff 3s timeout → ERROR state; queue fetch shows 3 skeleton `EpisodeTile` rows during loading
UX-DR11: LOVED/LIKED differentiation uses colour AND distinct iconography simultaneously (heart icon for LOVED, thumbs icon for LIKED) — never colour alone; `TierBadge` is the canonical implementation reused across `EpisodeTile`, `ShowTile`, and queue headers
UX-DR12: Responsive design: compact breakpoint (<400dp) reduces `Drive Count` to 40sp and outer padding to 12dp; standard (400–599dp) is the primary designed layout; tablet (≥600dp) gracefully degrades to single-column centred at 480dp max-width
UX-DR13: Accessibility requirements: full TalkBack (Android) and VoiceOver (iOS) support with correct focus order (DriveSessionCard → EpisodeTiles → CTA); `BriefingAnimation` checks Reduce Motion preference; Dynamic Type tested at 150% and 200% per release; colour blindness simulation (deuteranopia + protanopia) per release; `AccessibilityChecks.enable()` in Espresso + Xcode Accessibility Inspector in CI

### FR Coverage Map

FR1: Epic 1 — Spotify OAuth 2.0 PKCE authentication
FR2: Epic 1 — Silent access token refresh
FR3: Epic 1 — Revoke Spotify access and delete local data
FR4: Epic 2 — Fetch followed podcasts and unheard episodes
FR5: Epic 2 — Tag podcast as LOVED or LIKED
FR6: Epic 2 — Change or remove podcast tier
FR7: Epic 2 — Identify unplayed episodes
FR8: Epic 2 — Cache podcast library locally
FR9: Epic 2 — Background library sync
FR10: Epic 3 — Detect Bluetooth audio device connection
FR11: Epic 3 — Auto-activate drive session on Bluetooth connect
FR12: Epic 3 — Background operation with screen locked
FR13: Epic 3 — Manual drive session trigger
FR14: Epic 3 — Generate and deliver spoken TTS briefing
FR15: Epic 3 — LOVED episodes presented before LIKED in briefing
FR16: Epic 3 — Briefing includes episode title, podcast name, duration
FR17: Epic 3 — Briefing adapts when no LOVED episodes available
FR18: Epic 3 — Briefing adapts when inbox completely empty
FR19: Epic 3 — Steering wheel / tap confirmation to start playback
FR20: Epic 3 — Audio ducking during briefing and restoration after
FR21: Epic 4 — Initiate Spotify episode playback via deep-link
FR22: Epic 4 — Queue multiple episodes in priority order
FR23: Epic 4 — Auto-advance to next episode on completion
FR24: Epic 4 — Resume from last known playback position
FR25: Epic 4 — Handle Spotify playback failures with voice notification
FR26: Epic 5 — Suggest one episode when inbox is empty
FR27: Epic 5 — Present single recommendation only (not a list)
FR28: Epic 5 — Accept or decline recommendation hands-free
FR29: Epic 6 — Detect Spotify API failure and inform user via voice
FR30: Epic 6 — Recover from Spotify API timeout and resume
FR31: Epic 6 — Restore state after OS mid-drive termination
FR32: Epic 2 — Display staleness indicator when cache is >24 hours old
FR33: Epic 7 — Display what data PodDrive stores
FR34: Epic 7 — All preferences stored on-device only, not transmitted
FR35: Epic 7 — Delete all local data and reset app to initial state

## Epic List

### Epic 1: Project Foundation & Spotify Authentication
Users can install PodDrive, connect their Spotify account securely via OAuth 2.0, and revoke access at any time. Establishes the full KMP monorepo, shared infrastructure, CI/CD pipeline, and authenticated entry point all subsequent epics build upon.
**FRs covered:** FR1, FR2, FR3
**Key NFRs:** NFR6, NFR19, NFR20
**Key ARs:** AR1–AR12

### Epic 2: Podcast Library & Tier Management
Users can view all followed podcasts and their unheard episodes, tag shows as LOVED or LIKED, adjust or remove tiers, filter by tier, and browse offline. The library syncs in the background with a staleness indicator when stale.
**FRs covered:** FR4, FR5, FR6, FR7, FR8, FR9, FR32
**Key NFRs:** NFR3, NFR7, NFR8, NFR17

### Epic 3: Drive Session & Spoken Briefing
Users connect to their car via Bluetooth (or manually start a session) and receive a personalised spoken briefing of unheard episodes — LOVED first, then LIKED — with audio ducking and a single steering-wheel or tap confirmation to begin playback.
**FRs covered:** FR10, FR11, FR12, FR13, FR14, FR15, FR16, FR17, FR18, FR19, FR20
**Key NFRs:** NFR1, NFR4, NFR5, NFR10, NFR13, NFR14

### Epic 4: Spotify Playback & Episode Queue
Users can play episodes through Spotify via deep-link handoff, with an auto-advancing priority queue, playback position resumption across sessions, and graceful voice notification on playback failure.
**FRs covered:** FR21, FR22, FR23, FR24, FR25
**Key NFRs:** NFR2, NFR9, NFR18

### Epic 5: Empty Inbox & Smart Recommendations
When the user's episode inbox is empty, the system surfaces a single relevant episode recommendation which the user can accept or decline completely hands-free.
**FRs covered:** FR26, FR27, FR28

### Epic 6: Resilience, Recovery & Error Handling
The system handles Spotify API failures, network outages, and OS process termination gracefully — always recovering to the last known state without user intervention and communicating all failures via voice.
**FRs covered:** FR29, FR30, FR31
**Key NFRs:** NFR11, NFR12

### Epic 7: Privacy, Data Transparency & Settings
Users can view what data PodDrive stores locally, confirm all preferences stay on-device only, and fully reset or delete their local data at any time.
**FRs covered:** FR33, FR34, FR35
**Key NFRs:** NFR7

---

## Epic 1: Project Foundation & Spotify Authentication

**Goal:** Users can install PodDrive, connect their Spotify account securely, and revoke access at any time. Establishes the full KMP monorepo, CI/CD, shared infrastructure, and authenticated entry point all subsequent epics build upon.

### Story 1.1: KMP Monorepo & Build Foundation

As a **developer**,
I want the KMP monorepo initialised with Android and iOS app shells, CI/CD pipeline, and Gradle version catalog,
So that the team has a green, buildable project baseline to develop all subsequent features against.

**Acceptance Criteria:**

**Given** a fresh repository
**When** the project is built
**Then** a KMP monorepo exists with `shared/`, `androidApp/`, `iosApp/` modules matching the architecture spec (AR1)
**And** Gradle Version Catalog (`libs.versions.toml`) manages all dependencies (AR9)
**And** both Android (Jetpack Compose) and iOS (SwiftUI) app shells launch on device/emulator with a placeholder screen
**And** GitHub Actions CI runs on push and produces a green build for both platforms (AR9)
**And** Fastlane is configured for Android → Google Play internal track and iOS → TestFlight (AR9)

---

### Story 1.2: Shared Infrastructure — Database, Error Handling & Secure Storage

As a **developer**,
I want the core shared infrastructure (SQLDelight, `Result<T, AppError>`, and `SecureStorage` expect/actual) in place,
So that all subsequent features have a consistent, tested foundation for data persistence, error handling, and secure credential storage.

**Acceptance Criteria:**

**Given** the KMP monorepo from Story 1.1
**When** the shared infrastructure layer is added to `commonMain`
**Then** SQLDelight is configured in `commonMain` with platform drivers wired via `expect/actual` for Android and iOS (AR3, AR4)
**And** `AppError` sealed class exists with subtypes: `SpotifyApiError`, `NetworkUnavailable`, `SpotifyNotInstalled`, `RateLimitExceeded`, `Unknown` (AR5)
**And** all KMP use case return types use `Result<T, AppError>` — no exceptions cross the KMP boundary (AR5)
**And** `SecureStorage` expect/actual interface compiles, with Android using `EncryptedSharedPreferences` and iOS using `Keychain` (AR4, NFR6)
**And** unit tests verify `AppError` mapping and `SecureStorage` read/write/delete on both platforms

---

### Story 1.3: Spotify OAuth 2.0 PKCE Authentication

As a **new user**,
I want to authenticate with my Spotify account using OAuth 2.0,
So that PodDrive can access my podcast library and playback controls.

**Acceptance Criteria:**

**Given** the user has not yet authenticated
**When** they tap "Connect Spotify" on the welcome screen
**Then** the PKCE OAuth 2.0 flow opens the Spotify authorisation page with only the minimum required API scopes (NFR8)
**And** on successful authorisation, the access token and refresh token are stored in `SecureStorage` — never in plain-text storage (NFR6)
**And** the user is returned to PodDrive in an authenticated state
**And** if Spotify is not installed, PodDrive detects this and presents a clear, actionable install prompt — not a silent failure (NFR19)
**And** if the OAuth flow is cancelled or fails, the user remains on the welcome screen with an error message and a retry option

---

### Story 1.4: Silent Spotify Token Refresh

As an **authenticated user**,
I want my Spotify session to stay active automatically in the background,
So that I never need to re-authenticate mid-use and no active session is interrupted.

**Acceptance Criteria:**

**Given** a valid refresh token is stored in `SecureStorage`
**When** the access token expires
**Then** the Ktor `Auth` plugin transparently refreshes the token using the stored refresh token (AR8)
**And** the token refresh does not interrupt an active drive session or pause any audio (NFR20)
**And** the new access token is stored back to `SecureStorage` replacing the old one
**And** if the refresh fails (e.g. revoked externally), the session transitions to unauthenticated state and prompts the user to re-connect

---

### Story 1.5: Revoke Spotify Access

As an **authenticated user**,
I want to revoke PodDrive's Spotify access and clear all stored credentials,
So that I can fully disconnect the app when I no longer want it to have access to my account.

**Acceptance Criteria:**

**Given** the user is authenticated
**When** they select "Disconnect Spotify" from Settings
**Then** all Spotify access tokens and refresh tokens are deleted from `SecureStorage`
**And** any in-memory auth state is cleared
**And** the app returns to the unauthenticated welcome screen
**And** no credentials remain retrievable from the device after revocation (NFR6)
**And** the user can re-authenticate at any time by repeating the OAuth flow

---

## Epic 2: Podcast Library & Tier Management

**Goal:** Users can view all followed podcasts with unheard episode counts, tag shows LOVED or LIKED, filter by tier, browse offline, and complete an onboarding flow to add their first show.

### Story 2.1: Fetch & Display Podcast Library

As an **authenticated user**,
I want to see all my followed Spotify podcasts with their unheard episode counts,
So that I can review my full podcast queue at a glance.

**Acceptance Criteria:**

**Given** the user is authenticated
**When** the Shows screen loads
**Then** all followed Spotify podcasts are fetched via `SyncPodcastLibraryUseCase` and displayed as `ShowTile` components (UX-DR6)
**And** each `ShowTile` shows: podcast artwork, name, unheard episode count, and `TierBadge` if a tier is set (FR4, FR7)
**And** the management-mode UI is active with bottom nav (Shows / Discover / Settings) (UX-DR3)
**And** unheard episodes are correctly identified as episodes with no play history (FR7)
**And** all tap targets on management screens meet the ≥44dp minimum (UX-DR9)

---

### Story 2.2: LOVED/LIKED Tier Assignment & Management

As a **user**,
I want to tag any podcast as LOVED or LIKED, change its tier, or remove the tag via a long-press menu,
So that PodDrive knows which shows matter most to me and can prioritise them in my drive briefing.

**Acceptance Criteria:**

**Given** a podcast is displayed in the library
**When** the user long-presses a `ShowTile`
**Then** a bottom sheet appears with options: Set LOVED / Set LIKED / Remove Tier (FR5, FR6)
**And** selecting a tier saves it via `SetEpisodeTierUseCase` and persists to SQLDelight on-device only — not transmitted externally (NFR7, AR3)
**And** the `TierBadge` updates immediately using both colour AND distinct iconography: heart icon for LOVED (coral `#F87171`), thumbs icon for LIKED (blue `#60A5FA`) — never colour alone (UX-DR11, UX-DR2)
**And** `TierBadge` in ICON_ONLY variant renders as an 8dp coloured dot with the correct icon (UX-DR5)
**And** the bottom sheet is used for this non-destructive action — no modal alert dialogs (UX-DR9)

---

### Story 2.3: Tier Filter Tabs on Shows Screen

As a **user**,
I want to filter my podcast list by All / Loved / Liked tabs,
So that I can quickly focus on shows in a specific tier.

**Acceptance Criteria:**

**Given** the Shows screen is displaying the full library
**When** the user taps a `TierFilterTab` pill (All / Loved / Liked) (UX-DR6)
**Then** the list filters to show only podcasts matching the selected tier
**And** "All" shows all followed podcasts; "Loved" shows only LOVED-tagged; "Liked" shows only LIKED-tagged
**And** an empty filter state shows a helpful prompt (e.g. "No Loved shows yet — long-press a show to tag it")

---

### Story 2.4: Local Episode Cache

As a **user**,
I want my podcast library and episode data to be available without an active internet connection,
So that I can browse my queue even when offline.

**Acceptance Criteria:**

**Given** the library has been synced at least once
**When** the device has no network connection
**Then** the Shows screen loads from the SQLDelight local cache (FR8, AR3)
**And** unheard episode counts and LOVED/LIKED tiers are served from the cache
**And** no network error is shown when browsing cached data — the cache is transparent (FR8)

---

### Story 2.5: Background Library Sync & Staleness Indicator

As a **user**,
I want my library to sync automatically in the background and see a warning when data is stale,
So that my episode list stays current without manual refreshes.

**Acceptance Criteria:**

**Given** the user is authenticated and the app is running
**When** the background sync runs via `SyncPodcastLibraryUseCase`
**Then** the full library sync completes within 60 seconds for up to 50 podcasts on a standard mobile data connection (NFR3)
**And** the cache updates silently without any visible loading state (FR9)
**And** if the cache has not synced within 24 hours, a staleness indicator is displayed on the Shows screen (FR32)
**And** Spotify API rate limits are handled transparently — requests queued and retried without user-visible errors (NFR17, AR12)

---

### Story 2.6: Add First Show — Onboarding Flow

As a **new user completing onboarding**,
I want to search for a podcast, add it to my library, and set its tier before reaching the main app,
So that PodDrive has at least one prioritised show ready for my first drive session.

**Acceptance Criteria:**

**Given** the user has completed Spotify authentication
**When** they reach the "Add first show" onboarding step (UX-DR7)
**Then** a search field with debounced input (300ms) fetches Spotify podcast results displaying artwork, name, and episode count
**And** the user can select a podcast and set it as LOVED or LIKED
**And** tapping "Add more" returns to search; tapping "Done" proceeds to the idle screen
**And** the idle screen displays: "Connect your car to start your first session" (UX-DR7)
**And** the step is skippable if the user already has followed podcasts on Spotify

---

## Epic 3: Drive Session & Spoken Briefing

**Goal:** Users connect to their car via Bluetooth (or manually) and receive a personalised spoken briefing of unheard episodes — LOVED first, then LIKED — with audio ducking and hands-free confirmation.

### Story 3.1: Drive Session State Machine & Background Service

As a **user**,
I want PodDrive to maintain an active drive session in the background with my screen locked,
So that the session continues uninterrupted throughout my drive.

**Acceptance Criteria:**

**Given** a drive session is started
**When** the phone screen locks or the user switches apps
**Then** the Android Foreground Service (`mediaPlayback` type) keeps the session alive (FR12, AR7)
**And** iOS Background Audio mode is declared in `Info.plist` and audio continues uninterrupted (AR7)
**And** `DriveSessionState` sealed class transitions correctly: `Idle → Activating → Briefing → Playing → Paused → Error → Ended` (AR6)
**And** every state transition is persisted to SQLDelight (AR6)
**And** only `DriveSessionManager` may mutate `DriveSessionState` — no other component writes state directly (AR6)
**And** the app battery consumption does not exceed 5% above baseline Spotify usage during a 60-minute session (NFR4)

---

### Story 3.2: Bluetooth Audio Device Detection & Auto-Session Activation

As a **user**,
I want PodDrive to detect when I connect to my car's Bluetooth audio and start a drive session automatically,
So that my briefing begins without any action on my part.

**Acceptance Criteria:**

**Given** the user has the app installed with Bluetooth permission granted
**When** the phone connects to a Bluetooth audio device
**Then** the system detects the connection via the `expect/actual` Bluetooth trigger (FR10, AR4)
**And** a drive session activates automatically without any user action (FR11)
**And** the drive session briefing begins within 10 seconds of the Bluetooth connection event (NFR1)
**And** the Bluetooth trigger successfully activates the drive session on ≥95% of Bluetooth audio connection events in test conditions (NFR10)
**And** back gesture and bottom nav are suppressed — the drive screen becomes the only root (UX-DR3)

---

### Story 3.3: Manual Drive Session Trigger

As a **user**,
I want to manually start a drive session from within the app when Bluetooth auto-detection is unavailable,
So that I can still use PodDrive in vehicles without Bluetooth or when auto-detection fails.

**Acceptance Criteria:**

**Given** the user is on the idle screen and no Bluetooth audio device is connected
**When** they tap the manual "Start Drive" button (FR13)
**Then** a drive session activates and transitions to `Activating` state
**And** the drive mode UI takes over with bottom nav suppressed (UX-DR3)
**And** the session follows the same briefing and playback flow as an auto-triggered session
**And** the manual trigger button meets the ≥64dp drive-mode touch target minimum (UX-DR9)

---

### Story 3.4: TTS Spoken Briefing Generation & Delivery

As a **user**,
I want to hear a personalised spoken summary of my unheard episodes at the start of each drive,
So that I know what's in my queue without looking at my phone.

**Acceptance Criteria:**

**Given** a drive session has activated
**When** the session transitions to `Briefing` state
**Then** `GetEpisodeBriefingUseCase` generates a briefing script presenting LOVED episodes before LIKED (FR15)
**And** each episode is announced with its title, podcast name, and duration (FR16)
**And** the briefing addresses the user by first name in a warm, conversational tone (FR14)
**And** on-device TTS begins playing within 2 seconds of session activation (NFR5)
**And** the TTS voice speaks at 150–160 words per minute (NFR14)
**And** if no new LOVED episodes exist, the briefing adapts its message accordingly (FR17)
**And** if the inbox is completely empty across all tiers, a distinct empty-state message plays (FR18)
**And** `BriefingAnimation` full-screen overlay plays (~1.2s enter, waveform pulses with TTS audio) and respects Reduce Motion preference (UX-DR5, UX-DR8)
**And** `DriveSessionCard` displays in `BRIEFING_ACTIVE` state during delivery (UX-DR5)

---

### Story 3.5: Audio Ducking & Hands-Free Playback Confirmation

As a **user**,
I want navigation and other audio to lower during the briefing, and to confirm playback with a single steering wheel press or screen tap,
So that I can hear the briefing clearly and start playback without looking at my phone.

**Acceptance Criteria:**

**Given** the briefing is playing
**When** another audio app (e.g. navigation) is active
**Then** PodDrive ducks (temporarily lowers) the competing audio during the briefing and restores it afterward (FR20)
**And** when the briefing completes, the user can confirm playback via a single steering wheel media button press or an on-screen tap (FR19, NFR13)
**And** the confirmation requires no app navigation — one interaction from the session screen starts playback
**And** all drive-mode interactive elements are ≥64dp and only one primary button is visible at a time, bottom-anchored (UX-DR9)
**And** the required TTS script plays at session start: "PodDrive is ready. You have N loved episodes." (UX-DR8)

---

### Story 3.6: Drive Mode UI — Dashboard Screen

As a **user**,
I want a clear, distraction-free drive dashboard that shows my session stats and episode queue,
So that I can glance at relevant info safely while driving.

**Acceptance Criteria:**

**Given** a drive session is active
**When** the drive screen is displayed
**Then** the two-zone dashboard layout renders: fixed top zone with `DriveSessionCard` + stats strip (LOVED N / LIKED N / Total N) and scrollable `EpisodeTile` queue below (UX-DR4)
**And** a full-width bottom-anchored "Open in Spotify" CTA is always visible (UX-DR4)
**And** `DriveSessionCard` renders correctly across all 4 states: `BRIEFING_ACTIVE`, `BRIEFING_COMPLETE`, `HANDING_OFF`, `ERROR` (UX-DR5)
**And** each `EpisodeTile` in drive variant is 64dp height with 28sp title; single tap triggers the Spotify deep-link (UX-DR5)
**And** the drive mode colour scheme is applied: background `#0F0F12`, surface `#1C1C24`, accent amber `#F59E0B` with WCAG AAA (7:1+) contrast (UX-DR2)
**And** no more than 2 taps are required from the session screen to reach Spotify playback (UX-DR4)

---

## Epic 4: Spotify Playback & Episode Queue

**Goal:** Users can play episodes through Spotify via deep-link handoff, with an auto-advancing priority queue, playback position resumption, and graceful voice notification on failure.

### Story 4.1: Spotify Episode Deep-Link Handoff

As a **user**,
I want to tap an episode (or confirm via steering wheel) and have it open immediately in Spotify,
So that playback starts without me needing to navigate the Spotify app.

**Acceptance Criteria:**

**Given** a drive session is active and the briefing has completed
**When** the user confirms episode selection via steering wheel button or taps an `EpisodeTile`
**Then** the system initiates playback via `spotify:episode:{id}` deep-link URI (FR21, AR8)
**And** Spotify opens and begins playing the episode within 3 seconds of confirmation (NFR18)
**And** `DriveSessionCard` transitions to `HANDING_OFF` state during the handoff (UX-DR5)
**And** the TTS script "Opening [title] in Spotify." plays before the handoff (UX-DR8)
**And** if the Spotify app is not running, PodDrive launches it before issuing the deep-link
**And** time from Bluetooth connection to first podcast audio playing does not exceed 30 seconds under normal network conditions (NFR2)

---

### Story 4.2: Priority Episode Queue

As a **user**,
I want my episodes queued automatically with LOVED shows first, then LIKED,
So that my most important content always plays first without manual sorting.

**Acceptance Criteria:**

**Given** a drive session is active with LOVED and LIKED episodes available
**When** the episode queue is built by the continuity engine
**Then** all unheard LOVED episodes appear before all unheard LIKED episodes in the queue (FR22)
**And** within each tier, episodes are ordered by most recent publication date
**And** the queue is visible as scrollable `EpisodeTile` rows on the drive dashboard (UX-DR4)
**And** 3 skeleton `EpisodeTile` rows display during queue loading (UX-DR10)
**And** the queue is persisted to SQLDelight so it survives app backgrounding (AR3, AR6)

---

### Story 4.3: Auto-Advance to Next Episode

As a **user**,
I want the next episode to start automatically when the current one ends,
So that my drive session continues seamlessly without any interaction.

**Acceptance Criteria:**

**Given** an episode is playing through Spotify
**When** the episode ends
**Then** the system automatically begins playing the next unheard episode from the queue (FR23)
**And** the queue position advances and the `EpisodeTile` list updates to reflect the current episode
**And** if the queue is exhausted, the session transitions gracefully and an appropriate TTS message plays
**And** the app does not crash during a standard 60-minute drive session under normal conditions (NFR9)

---

### Story 4.4: Playback Position Resumption

As a **user**,
I want playback to resume from where I left off if a session reconnects to the same episode,
So that I never lose my place mid-episode.

**Acceptance Criteria:**

**Given** a drive session was previously active with a specific episode playing
**When** a new drive session connects to the same episode
**Then** `RestoreSessionUseCase` retrieves the last known playback position from `PlaybackState` in SQLDelight (FR24, AR3)
**And** the deep-link handoff passes the resume position to Spotify
**And** the TTS script "Session restored. Your queue is ready." plays on resumption (UX-DR8)
**And** if no prior position exists for the episode, playback starts from the beginning

---

### Story 4.5: Spotify Playback Failure Handling

As a **user**,
I want to be notified by voice if Spotify playback fails, without the app crashing,
So that I'm aware of the issue and can respond safely while driving.

**Acceptance Criteria:**

**Given** a drive session is active
**When** a Spotify playback failure or deep-link timeout occurs
**Then** the app does not crash — it transitions `DriveSessionState` to `Error` (FR25, NFR12)
**And** a voice notification plays: "Could not connect. Tap to retry." (FR25, UX-DR8)
**And** `DriveSessionCard` transitions to `ERROR` state with a visible retry action (UX-DR10)
**And** a 3-second Spotify handoff timeout triggers the ERROR state (UX-DR10)
**And** the retry action attempts the deep-link handoff again without requiring a full session restart

---

## Epic 5: Empty Inbox & Smart Recommendations

**Goal:** When the user's episode inbox is empty, the system surfaces a single relevant episode recommendation which the user can accept or decline completely hands-free.

### Story 5.1: Empty Inbox Recommendation Generation

As a **user**,
I want PodDrive to suggest one relevant episode when my inbox is empty,
So that I always have something worthwhile to listen to even when I'm caught up on my subscriptions.

**Acceptance Criteria:**

**Given** a drive session activates and the briefing finds zero unheard episodes across all tiers
**When** the briefing script is generated
**Then** `GetRecommendationUseCase` selects exactly one episode from outside the user's subscriptions based on on-device listening history (FR26, FR27)
**And** only one recommendation is presented — never a list (FR27)
**And** all recommendation data is stored and processed on-device only — not transmitted externally (NFR7)
**And** the TTS script plays: "No new episodes. Here is a recommendation." followed by the episode title and podcast name (UX-DR8)
**And** the `EmptyStatePanel` renders in `WITH_RECOMMENDATION` state on the drive dashboard (UX-DR6)
**And** if no suitable recommendation can be generated, the panel renders in `NO_RECOMMENDATION` state with a calm dismissal message (UX-DR10)

---

### Story 5.2: Hands-Free Accept or Decline of Recommendation

As a **user**,
I want to accept or decline the suggested episode using my steering wheel or a single tap,
So that I can act on the recommendation without touching my screen.

**Acceptance Criteria:**

**Given** a recommendation has been presented via TTS and `EmptyStatePanel`
**When** the user presses the steering wheel media button or taps the on-screen accept action
**Then** the recommended episode is queued and handed off to Spotify via deep-link, following the same flow as Story 4.1 (FR28)
**And** when the user presses the skip/next steering wheel button or taps the decline action
**Then** the recommendation is dismissed and the session transitions to `Ended` gracefully
**And** both accept and decline require no screen navigation — one action from the session screen (NFR13)
**And** all drive-mode touch targets remain ≥64dp (UX-DR9)

---

## Epic 6: Resilience, Recovery & Error Handling

**Goal:** The system handles Spotify API failures, network outages, and OS process termination gracefully — always recovering to the last known state without user intervention and communicating all failures via voice.

### Story 6.1: Spotify API Connection Failure — Voice Notification

As a **user**,
I want to be informed by voice if PodDrive cannot reach Spotify's API during a drive session,
So that I understand why my briefing or playback hasn't started without needing to look at my phone.

**Acceptance Criteria:**

**Given** a drive session is active and a Spotify API call is made
**When** the system detects a connection failure (no network, DNS failure, or unreachable endpoint)
**Then** the system does not crash — `DriveSessionState` transitions to `Error` (FR29, NFR12)
**And** the TTS script plays: "Could not connect to Spotify. Tap to retry." (FR29, UX-DR8)
**And** `DriveSessionCard` renders in `ERROR` state with a retry action visible (UX-DR10)
**And** the `AppError.NetworkUnavailable` type is surfaced correctly through the `Result<T, AppError>` chain (AR5)
**And** no user-visible crash dialog or unhandled exception is shown at any point (NFR12)

---

### Story 6.2: Spotify API Timeout Recovery & Request Retry

As a **user**,
I want PodDrive to automatically retry failed or timed-out Spotify requests and resume the intended action,
So that brief network hiccups don't end my drive session or require any action from me.

**Acceptance Criteria:**

**Given** a Spotify API request times out or returns a rate-limit (429) response
**When** the error is caught by the Ktor HTTP client
**Then** the request is retried up to 3 times with exponential backoff (FR30, AR12)
**And** rate-limit responses are handled transparently — the user sees no error during retry (NFR17, AR12)
**And** if retries succeed, the intended action resumes without user intervention (FR30)
**And** if all 3 retries fail, the system transitions to `Error` state and notifies the user via voice (NFR12)
**And** `AppError.RateLimitExceeded` and `AppError.SpotifyApiError` are correctly mapped and propagated (AR5)

---

### Story 6.3: OS Mid-Drive Process Kill — Session State Recovery

As a **user**,
I want PodDrive to restore my drive session automatically if the OS terminates the app mid-drive,
So that my queue and playback position are recovered without any action from me.

**Acceptance Criteria:**

**Given** a drive session is active with queue and playback position persisted to SQLDelight
**When** the OS terminates the app process and the user relaunches PodDrive
**Then** `RestoreSessionUseCase` reads the last persisted `DriveSessionState`, queue, and `PlaybackState` from SQLDelight (FR31, AR3, AR6)
**And** the drive session resumes within 10 seconds of app relaunch without requiring user intervention (NFR11)
**And** the TTS script plays: "Session restored. Your queue is ready." (UX-DR8)
**And** the drive dashboard is restored showing the correct queue and current episode position
**And** if the persisted state is unrecoverable, the system starts a fresh drive session gracefully rather than crashing (NFR12)

---

## Epic 7: Privacy, Settings & Accessibility Polish

**Goal:** Users can view what data PodDrive stores, confirm it stays on-device, and fully reset the app. The app also meets all accessibility and responsive design standards across both platforms.

### Story 7.1: Data Transparency — View Stored Data

As a **user**,
I want to view exactly what data PodDrive stores about me,
So that I can make an informed decision about keeping or removing the app.

**Acceptance Criteria:**

**Given** the user navigates to Settings
**When** they tap "My Data"
**Then** a screen lists all categories of locally stored data: Spotify credentials, LOVED/LIKED tier preferences, episode play history, drive session history, cached podcast library (FR33)
**And** the screen confirms that none of this data is transmitted to any external server or third party (FR34, NFR7)
**And** all text on this screen meets WCAG 2.1 Level AA contrast requirements (NFR15)
**And** the screen supports Dynamic Type at all system text size settings (NFR16)

---

### Story 7.2: Full Data Reset

As a **user**,
I want to delete all locally stored data and reset the app to its initial state,
So that I can fully remove my personal data from the device.

**Acceptance Criteria:**

**Given** the user is on the Settings screen
**When** they tap "Delete All Data & Reset"
**Then** a confirmation bottom sheet appears explaining what will be deleted — no modal alert dialog (UX-DR9)
**And** on confirmation, all SQLDelight tables are cleared, all SecureStorage credentials are deleted, and all in-memory state is reset (FR35)
**And** the app returns to the unauthenticated welcome screen
**And** no locally stored data is recoverable after the reset
**And** the user can re-authenticate and start fresh via Story 1.3

---

### Story 7.3: Design Token System Implementation

As a **developer**,
I want a shared design token system implemented on both platforms,
So that all UI components use consistent colours, typography, spacing, and iconography without per-component hardcoding.

**Acceptance Criteria:**

**Given** the Android and iOS app shells exist
**When** the design token system is implemented
**Then** `Theme.kt` extends Android Compose `MaterialTheme` with the full PodDrive token set: colour palette, typography scale, 8dp spacing grid, and custom iconography (LOVED heart, LIKED thumbs, drive session, Spotify handoff arrow) (UX-DR1)
**And** `DesignTokens.swift` implements the equivalent token set as SwiftUI environment values (UX-DR1)
**And** all colour tokens from UX-DR2 are defined: `#0F0F12`, `#1C1C24`, `#272736`, `#F59E0B`, `#F87171`, `#60A5FA`, `#34D399`
**And** all existing UI components are updated to reference tokens rather than hardcoded values
**And** the custom iconography is available as vector assets on both platforms

---

### Story 7.4: Accessibility Compliance & Responsive Design

As a **user with accessibility needs**,
I want PodDrive to work correctly with screen readers, large text, and across different screen sizes,
So that I can use the app regardless of my accessibility requirements or device.

**Acceptance Criteria:**

**Given** the app is running on Android or iOS
**When** TalkBack (Android) or VoiceOver (iOS) is enabled
**Then** focus order is correct on the drive screen: `DriveSessionCard` → `EpisodeTiles` → CTA (UX-DR13)
**And** all interactive elements have correct content descriptions / accessibility labels
**And** `BriefingAnimation` checks the Reduce Motion preference and uses a static alternative if enabled (UX-DR13)
**And** Dynamic Type renders correctly at 150% and 200% font scale on all non-briefing screens (UX-DR13, NFR16)
**And** all non-drive-mode screens meet WCAG 2.1 Level AA contrast and ≥44dp tap target requirements (NFR15)
**And** colour blindness simulation (deuteranopia + protanopia) runs per release with no critical failures (UX-DR13)
**And** `AccessibilityChecks.enable()` is active in Espresso tests and Xcode Accessibility Inspector runs in CI (UX-DR13)
**And** at the compact breakpoint (<400dp), Drive Count reduces to 40sp and outer padding to 12dp (UX-DR12)
**And** tablet layout (≥600dp) renders single-column centred at 480dp max-width (UX-DR12)
