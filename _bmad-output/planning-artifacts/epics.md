---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
inputDocuments:
  - '_bmad-output/planning-artifacts/prd.md'
  - '_bmad-output/planning-artifacts/architecture.md'
  - '_bmad-output/planning-artifacts/ux-design-specification.md'
  - '_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-24.md'
revisedFrom: '2026-03-19 (pre-pivot drive-session breakdown)'
revisionDate: '2026-04-28'
revisionDriver: 'Sprint Change Proposal 2026-04-24 — pivot to podcast-list-centric Home Screen; Drive Mode + AI briefing deferred to Vision phase'
---

# PodDrive - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for PodDrive, decomposing requirements from the PRD, UX Design Specification, and Architecture Decision Document into implementable stories.

> **Pivot context (2026-04-24).** PodDrive has pivoted from a Bluetooth-triggered, voice-first drive-session product to a podcast-list-centric Home Screen product. **Epics 1, 2, 4 (partial) ship as-built.** **Epic 3 is superseded** — drive-session code is retained as scaffolding but not exercised in next release. **Epic 5 is redirected into Epic 8** (empty-inbox recommendation reframed as Home-Screen Explore mode). **Epic 6 stories are reframed** (no voice notification — in-app status messaging). **Epic 7 retains privacy/data-reset stories; design-tokens + accessibility move into Epic 8.** **Epic 8 is new** — the podcast-list Home Screen product surface.

---

## Requirements Inventory

### Functional Requirements

**Authentication & Account**
FR1: A user can authenticate with their Spotify account using OAuth 2.0 to grant PodDrive access to their podcast library and playback controls.
FR2: The system can silently refresh the Spotify access token without requiring the user to re-authenticate.
FR3: A user can revoke PodDrive's Spotify access and delete all locally stored data.

**Podcast Library Management**
FR4: The system can retrieve all podcasts the user follows on Spotify and their associated unheard episodes.
FR5: A user can tag any followed podcast as LOVED or LIKED.
FR6: A user can change a podcast's tier (LOVED → LIKED, LIKED → LOVED, or remove tier).
FR7: The system can identify which episodes from followed podcasts have not yet been played.
FR8: The system can cache the podcast library locally so episode data is available without a live Spotify connection.
FR9: The system can sync the podcast library in the background and update the cache silently.

**Drive Session Activation — DEFERRED to Drive Mode / Vision phase (Epic 3 scaffolding retained as-built)**
FR10: *(deferred)* The system can detect when the user's phone connects to a Bluetooth audio device.
FR11: *(deferred)* The system can activate a drive session automatically upon Bluetooth connection without user action.
FR12: *(deferred)* The system can remain active in the background with the phone screen locked during a drive session.
FR13: *(deferred)* A user can manually trigger a drive session from within the app when Bluetooth auto-detection is unavailable.

**AI Voice Briefing — DEFERRED to Drive Mode / Vision phase (Epic 3 scaffolding retained as-built)**
FR14: *(deferred)* The system can generate and deliver a spoken briefing of new podcast episodes using on-device TTS at drive session start.
FR15: *(deferred)* The briefing presents LOVED episodes before LIKED episodes.
FR16: *(deferred)* The briefing includes episode title, podcast name, and duration for each new episode.
FR17: *(deferred)* The briefing adapts its message when no new episodes are available from LOVED podcasts.
FR18: *(deferred)* The briefing adapts its message when the inbox is completely empty across all tiers.
FR19: *(deferred)* A user can respond to the briefing using a steering wheel button or on-screen tap to confirm playback.
FR20: *(deferred)* The system can duck audio from other apps during the briefing and restore it afterward.

**Playback Control**
FR21: The system can initiate playback of a selected podcast episode through Spotify without requiring the user to open or navigate the Spotify app.
FR22: *(retired — SCP 2026-04-24; priority episode queue removed alongside the pivot)*
FR23: *(retired — SCP 2026-04-24; auto-advance removed alongside the pivot)*
FR24: The system can persist the last known playback position for the most recently played episode and expose it to the Home Screen's Continue Listening surface (see FR41).
FR25: The system can handle Spotify playback failures gracefully and notify the user via in-app status without crashing.

**Explore Mode Recommendations (Fast-Follow — Home Screen)**
FR26: When no subscribed podcast has unlistened episodes, the Home Screen enters Explore mode and surfaces recommended podcasts (not single episodes) visually tagged as `explore`. Recommendations are sourced from on-device listening history.
FR27: Explore mode presents a small set of recommended podcasts (target: 3) with the `explore` visual tag — concise, not a firehose.
FR28: A user can tap an Explore recommendation's ▶ button to play its most recent episode, or tap the tile to subscribe (defaulting to the OTHER tier). No voice or hands-free interaction is required in next release.

**Resilience & Error Handling**
FR29: The system can detect a Spotify API connection failure and inform the user via an in-app status message (e.g., staleness banner, offline tile state) without blocking Home Screen rendering.
FR30: The system can recover from a Spotify API timeout and resume the intended action when connectivity is restored.
FR31: The system can restore its last known state (playback position, current episode) if the app is terminated by the OS during playback and relaunched.
FR32: The system can display a staleness indicator when the cached episode library has not synced within 24 hours.

**Privacy & Data**
FR33: A user can view what data PodDrive stores about them.
FR34: All LOVED/LIKED/OTHER tags, playback positions, and listening history used for Explore recommendations are stored on-device only and are not transmitted to any server in next release.
FR35: A user can delete all locally stored data and reset the app to its initial state.

**Home Screen Experience (NEW — added by SCP 2026-04-24)**
FR36: The Home Screen displays a sorted list of subscribed podcasts filtered to those with at least one unlistened episode. Podcasts with zero unlistened episodes do not appear on the Home Screen (they remain accessible via the Show All screen — see FR43).
FR37: Podcasts are grouped into three colour-differentiated freshness bands — TODAY (top), THIS WEEK (middle), OLDER (bottom). Within each band, podcasts are sorted by tier (LOVED → LIKED → untiered) and then by latest-unlistened-episode publish date (newest first).
FR38: Each podcast tile displays a count of unlistened episodes. The count badge uses a freshness-colour encoding keyed to a `FreshnessBucket` enum (`TODAY`, `THIS_WEEK`, `OLDER`) computed in `commonMain`.
FR39: Each podcast tile exposes a quick-play button. A single tap on the button starts playback of the most recent unlistened episode for that podcast — no smart routing.
FR40: A tap on a podcast tile's body (outside the quick-play button) expands the tile in place to reveal its unlistened episodes, sorted newest-first; a second tap collapses it. The tap-target boundary between tile-body and quick-play-button must be unambiguous.
FR41: A "Continue Listening" bar appears above the podcast list when an episode is in progress (has a persisted playback position and is not yet finished). A single tap on the bar resumes that episode from its persisted position. *(Fast-Follow — story 8.9)*
FR42: When no subscribed podcast has unlistened episodes, the Home Screen enters Explore mode (see FR26–FR28). *(Fast-Follow — story 8.10)*
FR43: A "Show All" affordance on the Home Screen opens a full library view listing every subscribed podcast (including those with zero unlistened episodes), enabling library-level management.
FR44: A user can promote or demote a podcast's tier (OTHER → LIKED → LOVED, and inverse) directly from a Home Screen tile via long-press, which opens an action sheet with tier options.
FR45: All Home Screen visual elements — tile size, tap targets, typography, contrast — meet driver-friendly standards (large, high-contrast, low-density) applied universally. There is no separate drive mode; driver-friendliness is a universal design constraint.

### NonFunctional Requirements

**Performance**
NFR1: *(deferred — Drive Mode / Vision)* The drive session briefing must begin playing within 10 seconds of Bluetooth audio device connection detection.
NFR2: Time from Home Screen open (with cached library) to first podcast audio playing via quick-play must not exceed 5 seconds under normal network conditions.
NFR3: Podcast library sync (Spotify subscriptions + RSS episode refresh, background) must complete within 60 seconds on a standard mobile data connection for a library of up to 50 followed podcasts.
NFR4: The app must not increase device battery consumption by more than 5% compared to baseline Spotify usage during a 60-minute listening session.
NFR5: *(deferred — Drive Mode / Vision)* On-device TTS briefing generation must add no more than 2 seconds of delay before audio begins.

**Security**
NFR6: Spotify OAuth access tokens and refresh tokens must be stored in the platform's secure credential store (iOS Keychain / Android Keystore) and never in plain-text storage.
NFR7: No user listening data, LOVED/LIKED preferences, or episode history may be transmitted to any external server or third-party service in next release.
NFR8: The app must request only the minimum required Spotify API scopes and must not request permissions beyond those required.

**Reliability**
NFR9: The app must not crash during a standard 60-minute listening session under normal conditions (background audio + locked screen + incoming call).
NFR10: *(deferred — Drive Mode / Vision)* The Bluetooth connection trigger must activate the drive session on ≥95% of Bluetooth audio connection events in test conditions.
NFR11: The app must recover and resume normal operation within 10 seconds of the OS terminating the background process, without requiring user intervention.
NFR12: Graceful degradation must be implemented for all Spotify API and RSS fetch failure scenarios — no user-visible crashes; clear in-app status messaging for any failure that prevents playback or library refresh.

**Accessibility**
NFR13: *(deferred — Drive Mode / Vision)* All core drive session interactions must be completable without looking at or touching the screen — via steering wheel media controls or voice.
NFR14: *(deferred — Drive Mode / Vision)* The AI briefing voice must use a clear, natural-sounding TTS voice at 150–160 wpm.
NFR15: All app screens must meet WCAG 2.1 Level AA contrast ratios and tap target size requirements.
NFR16: The app must support dynamic text sizing (iOS Dynamic Type / Android font scale) across all screens.

**Integration**
NFR17: The Spotify API integration must handle rate limiting transparently — if rate limits are reached, the app must queue the request and retry without user action or error messages.
NFR18: Spotify deep-link playback handoff must succeed within 3 seconds of the user confirming episode selection, assuming Spotify is installed and running.
NFR19: If the Spotify app is not installed on the device, PodDrive must detect this at onboarding and present a clear, actionable message — not a silent failure.
NFR20: The Spotify token refresh cycle must not interrupt an active listening session — token refresh must occur in the background without pausing audio.

**Home Screen Experience (NEW — added by SCP 2026-04-24)**
NFR21: The Home Screen must render (including three-band sort and unlistened counts) within 1 second of app foreground on a device with a library of up to 50 podcasts and cached data. Fresh-sync scenarios may take longer but must show a loading state.
NFR22: Tap targets on Home Screen tiles (tile body and quick-play button) must meet driver-friendly minimums of 48dp × 48dp on Android and 44pt × 44pt on iOS at the smallest supported device class, with **≥8dp/pt visual separation** between adjacent interactive elements.
NFR23: Typography on the Home Screen must remain legible at arm's length in a driving context — minimum body text 16sp/pt, minimum tile title 18sp/pt — with contrast ratios meeting WCAG 2.1 AA against the band's background colour. Layout must reflow at 200% Dynamic Type without text truncation.
NFR24: The three band colours (TODAY / THIS WEEK / OLDER) and tier accents (LOVED / LIKED) must be distinguishable by users with common forms of colour-vision deficiency. Differentiation must not rely solely on hue (use a combination of hue, luminance, position, and icon shape).
NFR25: The unlistened-count freshness-colour encoding (FR38) must remain legible across the full freshness range and must not rely on colour alone to convey urgency — the count itself and its position on the tile must remain unambiguous without colour.

### Additional Requirements

- AR1: KMP monorepo structure — `poddrive/shared/`, `androidApp/`, `iosApp/`; Story 1 of Epic 1 is the KMP monorepo initialization + Android project setup
- AR2: Shared `commonMain` business logic layer — `SpotifyApiClient` (Ktor), domain models (`Podcast`, `Episode`, `EpisodeTier`), LOVED/LIKED persistence, `playback_state` singleton (Continue Listening source of truth), `FreshnessBucket` enum + computation
- AR3: SQLDelight as cross-platform database with schema defined in `commonMain`; entities: `Podcast`, `Episode`, `EpisodeTier`, `PlaybackState`, `DriveSession` *(retained as-built; quiescent in next release)*
- AR4: `expect/actual` pattern for all platform-specific implementations: `AudioPlayer` (ExoPlayer / AVPlayer — ADR-002), SecureStorage (iOS Keychain / Android EncryptedSharedPreferences), local DB drivers; **scaffolding (deferred):** `BluetoothTrigger`, `TtsEngine`
- AR5: `Result<T, AppError>` sealed error handling pattern across all KMP use cases; `AppError` subtypes: `SpotifyApiError`, `NetworkUnavailable`, `SpotifyNotInstalled`, `RateLimitExceeded`, `Unknown`; no exceptions cross the KMP boundary
- AR6: **Home-screen composition via independent Flows.** `HomeScreenViewModel` composes `GetSortedPodcastsUseCase`, `GetUnlistenedCountUseCase`, `GetContinueListeningUseCase`, and `GetExploreRecommendationsUseCase` into a single `HomeScreenState`. Use cases do not reference one another. *(`DriveSessionState` sealed class retained as scaffolding for the Vision phase — no next-release code path observes, emits, or imports it.)*
- AR7: Android-first development (Jetpack Compose + Foreground Service `mediaPlayback` type tied to in-app `AudioPlayer`, NOT to drive-session lifecycle); iOS follows same KMP shared logic (SwiftUI + Background Audio mode for in-app playback continuation)
- AR8: Spotify integration via Web API + RSS feeds + ExoPlayer/AVPlayer (ADR-002 — primary audio path); `spotify:episode:{id}` deep-link as fallback. Ktor `Auth` plugin handles transparent bearer token refresh (NFR20)
- AR9: GitHub Actions + Fastlane CI/CD: Android → Google Play internal track; iOS → TestFlight; Gradle Version Catalog (`libs.versions.toml`) for dependency management
- AR10: KMP Use Cases in `commonMain` — **Home Screen (Epic 8 Core MVP):** `GetSortedPodcastsUseCase`, `GetLatestUnlistenedUseCase`, `GetUnlistenedCountUseCase`, `PromoteTierUseCase`. **Home Screen (Fast-Follow):** `GetContinueListeningUseCase`, `GetExploreRecommendationsUseCase`. **Library/Playback/Auth (retained):** `SyncPodcastLibraryUseCase`, `SetEpisodeTierUseCase`, `MarkEpisodePlayedUseCase`, `ResolvePodcastRssFeedUseCase`, `ResolveEpisodeAudioUrlsUseCase`, `HandlePlaybackFailureUseCase`, `AuthenticateWithSpotifyUseCase`, `RevokeSpotifyAccessUseCase`, `SearchSpotifyShowsUseCase`. **Retired:** `BuildEpisodeQueueUseCase` (code removal pending). **Scaffolding (deferred):** `StartDriveSessionUseCase`, `GetEpisodeBriefingUseCase`, `RestoreSessionUseCase`.
- AR11: No backend infrastructure in next release — all Spotify API calls direct from device; no server-side user profiles; GDPR compliance via on-device-only data storage
- AR12: Rate limiting strategy — episode library fetch batched on background sync or pull-to-refresh (NEVER synchronously during home-screen open — NFR2); Ktor `HttpRequestRetry` with 3 retries + exponential backoff; local request queue for playback commands
- AR13: **Freshness-colour rule lives in `commonMain`.** `GetUnlistenedCountUseCase` emits both the integer count AND a `FreshnessBucket` enum value. Platform UI maps `FreshnessBucket` → colour token. Rationale: freshness is a business rule, must be testable in `commonTest`, must render identically across Android/iOS.
- AR14: **Tier promotion is write-only.** `PromoteTierUseCase` writes the new tier to `PodcastRepository` and returns; no event emission. The sorted list re-emits via `GetSortedPodcastsUseCase`'s Flow; UI animation derives from the list diff.
- AR15: **Persist-before-emit rule for `playback_state`.** Position snapshots are written to SQLDelight before being emitted to Continue Listening observers; supports NFR11 (state restoration after OS kill via `playback_state` singleton row).

### UX Design Requirements

> The pre-pivot UX-DRs (drive-mode dashboard, briefing animation, voice scripts, locked drive navigation) are retired alongside Drive Mode. The set below reflects the 2026-04-24 home-screen UX spec.

UX-DR1: Design token system implemented as `Theme.kt` (Android Compose `MaterialTheme` extensions) and `DesignTokens.swift` (iOS SwiftUI environment values) — shared specification covering: dark-first universal colour palette, single typography scale (Display 28 / H1 24 / H2 20 / Tile Title 18 / Body 16 / Subhead 14 / Caption 13 / Count 12), 8dp/pt base grid, motion tokens (220ms expand / 280ms band-move / 80–120ms press) with reduced-motion variants, and custom iconography (LOVED filled heart, LIKED outline thumbs).
UX-DR2: Universal dark-first colour scheme: base `#0F0F12`, elevated `#1C1C24`, overlay `#272736`, divider `#2D2D38`, text-primary `#F9FAFB`, text-secondary `#C0C0C8`. Three freshness band tints (`color-band-today-bg #1A1416`, `color-band-thisweek-bg #14171C`, `color-band-older-bg #101013`) — tints behind tiles, never on tiles. Tier accents `color-tier-loved #F87171` (filled heart only) and `color-tier-liked #60A5FA` (outline thumbs only). Action accent `color-action-primary #22D3EE` (cyan ▶, intentionally distinct from tier colours).
UX-DR3: Single universal navigation surface. **No tab bar, no navigation drawer, no bottom nav.** App bar provides Show All link + Settings link only. ShowAll, expanded tile, and Settings are reached from the home screen. The pre-pivot three-mode navigation rule (Idle / Drive / Post-Drive) is retired.
UX-DR4: Home screen layout — `LazyColumn` (Compose) / `List + LazyVStack` (SwiftUI) with: optional `ContinueListeningBar` (when present, scrolls with list, non-sticky); for each non-empty band `[FreshnessBandHeader (sticky), …PodcastTile]`; `ExplorePanel` below the bands. Empty bands suppressed entirely (no header for empty band). Pull-to-refresh re-syncs library + freshness.
UX-DR5: `PodcastTile` (76dp/pt fixed height): 60dp/pt rounded artwork (8dp corner radius), Tile Title 18sp Semibold (1 line max), `TierBadge` ICON_ONLY (heart or thumbs in tier colour), `CountBadge` (FreshnessBucket-keyed), and elevated 48dp/pt circular `color-action-primary` quick-play ▶ with its own dedicated 48dp/pt hit-box. Two-zone tap: tile body (everything except ▶) → expand-in-place; ▶ → play top unplayed episode. Long-press tile body → `TierActionSheet`.
UX-DR6: Three freshness bands with sticky band headers (`FreshnessBandHeader`): "TODAY", "THIS WEEK", "OLDER" — Subhead 14sp UPPERCASE +0.5 letter-spacing on the band's tinted background. Within each band, tiles sorted LOVED → LIKED → untiered, then by most-recent-unplayed-episode publish date descending.
UX-DR7: Onboarding flow (revised — Bluetooth permission step removed, "add first show" search step removed, forced tier-assignment removed): Welcome ("See what's new in your podcasts. One tap to play.") → Spotify OAuth (with inline error + retry) → Initial library import → optional skippable tier-priming nudge ("Long-press a show to mark it LOVED") → Home Screen. Permissions in MVP: Spotify OAuth only (no BT, no notifications, no background-execution prompt).
UX-DR8: Visual-confirms-action with haptics (TTS retired in MVP). Quick-play ▶: 80ms scale-in / 120ms scale-out + short success haptic + Spotify deep-link / ExoPlayer launch. Long-press tier promotion: 500ms hold → medium impact haptic → action sheet. Subscribe from Explore: heavy haptic + 220ms slide-in animation. All animations collapse to instant snap under Reduce Motion.
UX-DR9: Universal touch target minimum: 48dp Android / 44pt iOS with **≥8dp/pt visual separation** between adjacent interactive elements (NFR22). Bottom sheets (`ModalBottomSheet` / `.confirmationDialog`) for non-destructive actions (`TierActionSheet`); confirmation dialogs only for destructive actions (unsubscribe, logout, full-data reset). No full-screen modals after onboarding.
UX-DR10: Empty and loading states. **Caught-up:** quiet hero copy "You're caught up. Pull to refresh or browse Explore below." + prominently-placed Explore. **Empty library:** hero "You haven't subscribed to any podcasts on Spotify yet. Browse Explore below." + Explore. **Loading:** skeleton tiles matching real-component dimensions (no layout shift); 1.2s shimmer (static dim under Reduce Motion). **Offline:** non-blocking `OfflineBanner` — never modal. **Spotify deep-link in flight:** brief 100ms haptic + dim overlay; auto-dismisses on app switch. **Tile zero-unplayed (only via expand):** "No new episodes." inside expanded tile.
UX-DR11: LOVED/LIKED differentiation uses colour AND distinct iconography simultaneously — filled heart for LOVED (coral `#F87171`), outline thumbs for LIKED (blue `#60A5FA`) — never colour alone. `TierBadge` is the canonical implementation reused across `PodcastTile`, `ShowTile`, and band headers. Tier icons must be distinguishable at 16dp/pt (smallest size used).
UX-DR12: Responsive design: **Compact** (<400dp) — side margins 12dp/pt, tile artwork 60dp/pt, ▶ 48dp/pt, tile title may wrap to 2 lines. **Standard** (400–599dp) — designed layout, 16dp/pt margins. **Medium** (600–839dp) — single-column centred, max content width 480dp/pt, bands span full content width. **Expanded** (840dp+) — letter-boxed at 480dp content width, gutters use `color-surface-base`. **Never two-column** at any breakpoint (preserves "scan one band in <2s" goal).
UX-DR13: Accessibility requirements: Universal WCAG 2.1 AA across every surface (AAA achieved on tile titles + band headers per the 18-combination matrix — 14.2:1 measured). Full TalkBack (Android) and VoiceOver (iOS) support with focus order: app bar → Show All → ContinueListeningBar (if present) → for each band [header → tiles in order, each tile a single focusable group with custom actions: Click=expand, Quick play, Show options]. Reduce Motion preference collapses all band-move, expand, press, and shimmer animations to instant snap. Dynamic Type honoured to 200% with no text truncation. CI gates: 18-combo Compose Preview / SwiftUI Preview screenshot diff per PR; tap-target spacing assertion ≥8dp/pt; contrast-ratio computation on design tokens; deuteranopia + protanopia simulation per release.
UX-DR14: Tile expand-in-place sort-freeze rule (FR40). While any tile is expanded, band-move animation is suspended — count badges may re-colour in place (FreshnessBucket change), but the tile does not move bands until collapsed. On collapse, if the bucket has changed, the tile re-settles into its new band with the standard 280ms band-move animation. Only one tile may be expanded at a time.
UX-DR15: Tier promotion via long-press (FR44). 500ms hold on tile body fires medium-impact haptic and opens platform-native `ModalBottomSheet`/`.confirmationDialog` with **Promote to LOVED / Demote to LIKED / Unsubscribe** options. Promote/Demote: no confirm — band-move animation is the feedback; 4-second "Promoted to LOVED. Undo." snackbar with inverse-`PromoteTierUseCase` undo. Unsubscribe: confirmation dialog required (destructive).
UX-DR16: Continue Listening visibility rule (FR41 — story 8.9). `ContinueListeningBar` renders ONLY when `playback_state.position_ms > 0 AND playback_state.episode_id IS NOT NULL AND playback_state.position_ms < episode.duration_ms`. Bar is non-sticky — scrolls with list. If the resumable episode is unsubscribed or marked played mid-session, the bar disappears on the next Flow emission with no error.
UX-DR17: Explore-to-Subscribe three-action contract (FR26–FR28, FR42 — story 8.10). Each Explore tile exposes: (1) ▶ → play preview via deep-link/ExoPlayer (does NOT subscribe); (2) tile body tap → preview sheet (artwork, name, description, recent 5 episodes); (3) `+` → instant subscribe with 4-second undo Snackbar. Subscribe writes locally, calls Spotify API, and re-emits the library Flow — the new tile lands in its appropriate freshness band with a 220ms slide-in animation.

### FR Coverage Map

| FR | Owning Epic | Notes |
|---|---|---|
| FR1 | Epic 1 | Spotify OAuth 2.0 PKCE — done |
| FR2 | Epic 1 | Silent token refresh — done |
| FR3 | Epic 1 | Revoke access + delete local data — done |
| FR4 | Epic 2 | Fetch followed podcasts + unheard episodes — done |
| FR5 | Epic 2 | Tag podcast as LOVED/LIKED — done |
| FR6 | Epic 2 | Change/remove tier — done |
| FR7 | Epic 2 | Identify unplayed episodes — done |
| FR8 | Epic 2 | Local cache — done |
| FR9 | Epic 2 | Background sync — done |
| FR10–FR13 | Epic 3 *(deferred)* | Drive session activation — scaffolding retained |
| FR14–FR20 | Epic 3 *(deferred)* | AI voice briefing — scaffolding retained |
| FR21 | Epic 4 | Spotify episode handoff — done |
| FR22 | *(retired)* | Priority queue — code removal pending |
| FR23 | *(retired)* | Auto-advance — never built |
| FR24 | Epic 8 (8.9) | Playback position persisted to `playback_state` (Continue Listening source) |
| FR25 | Epic 4 | Spotify playback failure handling — done; voice-notification reframed as in-app status |
| FR26 | Epic 8 (8.10) | Explore mode recommendations — Fast-Follow |
| FR27 | Epic 8 (8.10) | Single recommendation set — Fast-Follow |
| FR28 | Epic 8 (8.10) | Explore tile actions (preview/subscribe) — Fast-Follow |
| FR29 | Epic 6 | Spotify API failure → in-app status (reframed from voice notification) |
| FR30 | Epic 6 | Timeout recovery + retry |
| FR31 | Epic 6 | OS-kill state recovery — `playback_state` singleton |
| FR32 | Epic 2 | Staleness indicator (>24h) — done (Story 2.5) |
| FR33 | Epic 7 | Data transparency view |
| FR34 | Epic 7 | All data on-device only |
| FR35 | Epic 7 | Full data reset |
| FR36 | Epic 8 (8.1, 8.2) | Sorted list filtered to podcasts with unlistened episodes |
| FR37 | Epic 8 (8.1) | Three-band sort (TODAY / THIS WEEK / OLDER) + in-band tier order |
| FR38 | Epic 8 (8.5) | Unlistened count + FreshnessBucket badge |
| FR39 | Epic 8 (8.3) | Per-tile quick-play ▶ |
| FR40 | Epic 8 (8.4) | Tile body expand-in-place |
| FR41 | Epic 8 (8.9) | Continue Listening bar — Fast-Follow |
| FR42 | Epic 8 (8.10) | Caught-up → Explore mode — Fast-Follow |
| FR43 | Epic 8 (8.7) | Show All library screen |
| FR44 | Epic 8 (8.6) | Long-press tier promotion |
| FR45 | Epic 8 (8.8) | Driver-friendly visual baseline (universal) |

## Epic List

### Epic 1: Project Foundation & Spotify Authentication — DONE
Users can install PodDrive, connect their Spotify account securely via OAuth 2.0, and revoke access at any time. Establishes the KMP monorepo, shared infrastructure, CI/CD pipeline, and authenticated entry point.
**FRs covered:** FR1, FR2, FR3
**Key NFRs:** NFR6, NFR19, NFR20
**Status:** All 5 stories done.

### Epic 2: Podcast Library & Tier Management — DONE
Users can view all followed podcasts and unheard episodes, tag shows LOVED or LIKED, adjust or remove tiers, browse offline, and the library syncs in the background with a staleness indicator when stale.
**FRs covered:** FR4, FR5, FR6, FR7, FR8, FR9, FR32
**Key NFRs:** NFR3, NFR7, NFR8, NFR17
**Status:** All 6 stories done. (Tier filter tabs from Story 2.3 are demoted; the home screen sort-by-band replaces tier filtering as the primary surface — Show All still uses tier sections.)

### Epic 3: Drive Session & Spoken Briefing — SUPERSEDED (Drive Mode / Vision phase)
Originally: users connect via Bluetooth, receive a personalised TTS briefing, and confirm playback hands-free.
**Status:** Superseded by SCP 2026-04-24. Stories 3.1, 3.2, 3.3, 3.6 were built and the code is retained as scaffolding (not exercised in next release). Stories 3.4 (TTS briefing) and 3.5 (audio ducking + hands-free confirm) remain deferred to the future Drive Mode / Vision phase. **No new work** in next release. Bluetooth + TTS init must be permission-gated behind a disabled feature flag (Architecture Risk 6).
**FRs covered (deferred):** FR10–FR20
**Key NFRs (deferred):** NFR1, NFR5, NFR10, NFR13, NFR14

### Epic 4: Spotify Playback & Episode Queue — RESCOPED (in progress)
Rescoped by SCP 2026-04-24: priority queue + auto-advance retired; playback plumbing retained as the foundation for Epic 8.
**Active stories:** 4.1 (deep-link handoff — done), 4.5 (playback failure handling — done), 4.6 (auto-play — done), 4.8 (RSS feed discovery — done), 4.9 (audio URL resolution — done), 4.10 (ExoPlayer integration — done), 4.11 (in-app playback controls — done).
**Retired:** 4.2 (priority queue — code removal pending), 4.3 (auto-advance — never built).
**Redirected:** 4.4 (playback position resumption) → Story 8.9 (Continue Listening Bar).
**Deferred:** 4.7 (active device polling — superseded by ADR-002 RSS+ExoPlayer path).
**FRs covered:** FR21, FR24 (now via 8.9), FR25
**Key NFRs:** NFR2, NFR9, NFR18

### Epic 5: Empty Inbox & Smart Recommendations — REDIRECTED → Epic 8
Originally: when the inbox is empty, briefing recommends one episode hands-free.
**Status:** Story 5.1 (recommendation generation) redirected into Epic 8 Story 8.10 (Home Screen Explore mode — recommends podcasts, not single episodes). Story 5.2 (hands-free accept/decline) deferred to the Drive Mode / Vision phase alongside Epic 3.
**FRs covered (reframed in Epic 8):** FR26, FR27, FR28

### Epic 6: Resilience, Recovery & Error Handling — REFRAMED (backlog)
The system handles Spotify API failures, network outages, and OS process termination gracefully. **Reframe:** voice notifications retired; failures are communicated via in-app status (staleness banner, offline tile state, snackbar). State recovery uses `playback_state` singleton, not `DriveSessionState`.
**FRs covered:** FR29, FR30, FR31
**Key NFRs:** NFR11, NFR12

### Epic 7: Privacy, Data Transparency & Settings — REFRAMED (backlog)
Users can view what data PodDrive stores, confirm preferences stay on-device, and reset all local data.
**Status:** Stories 7.1 (data transparency) and 7.2 (full data reset) remain. Story 7.3 (design tokens) and Story 7.4 (accessibility compliance) **redirected into Epic 8** (Story 8.8 covers the universal driver-friendly visual baseline + the design tokens for the new home-screen palette; accessibility is a CI gate threaded through every Epic 8 story).
**FRs covered:** FR33, FR34, FR35
**Key NFRs:** NFR7, NFR15, NFR16

### Epic 8: Podcast-List Home Screen — NEW (next-release primary surface)
The home screen *is* the product. A sorted list of subscribed podcasts filtered to those with unlistened episodes, banded by freshness (TODAY / THIS WEEK / OLDER), tile-level quick-play and expand-in-place, long-press tier promotion, with Continue Listening + Explore mode as fast-follows.
**FRs covered:** FR36, FR37, FR38, FR39, FR40, FR41 (FF), FR42 (FF), FR43, FR44, FR45 — and (via redirection) FR24, FR26–FR28
**Key NFRs:** NFR2, NFR21, NFR22, NFR23, NFR24, NFR25, NFR15, NFR16
**Stories:** 8.1–8.8 are Core MVP (must ship together); 8.9, 8.10 are Fast-Follow.

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
So that I never need to re-authenticate mid-use and no active listening session is interrupted.

**Acceptance Criteria:**

**Given** a valid refresh token is stored in `SecureStorage`
**When** the access token expires
**Then** the Ktor `Auth` plugin transparently refreshes the token using the stored refresh token (AR8)
**And** the token refresh does not interrupt any active in-app playback or pause any audio (NFR20)
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

**Goal:** Users can view all followed podcasts with unheard episode counts, tag shows LOVED or LIKED, browse offline, and complete an onboarding flow to add their first show. Library syncs in the background with a staleness indicator.

> **Post-pivot note:** Story 2.3 (Tier Filter Tabs) is demoted — the home screen's three-band sort replaces tier filtering as the primary surface. Show All (Story 8.7) retains tier sections. Existing implementation kept as-is to keep Show All consistent.

### Story 2.1: Fetch & Display Podcast Library

As an **authenticated user**,
I want to see all my followed Spotify podcasts with their unheard episode counts,
So that I can review my full podcast queue at a glance.

**Acceptance Criteria:**

**Given** the user is authenticated
**When** the Library screen loads
**Then** all followed Spotify podcasts are fetched via `SyncPodcastLibraryUseCase` and displayed (FR4)
**And** each show shows: podcast artwork, name, unheard episode count, and `TierBadge` if a tier is set (FR4, FR7)
**And** unheard episodes are correctly identified as episodes with no play history (FR7)
**And** all tap targets meet the universal ≥48dp Android / ≥44pt iOS minimum (NFR22, UX-DR9)

---

### Story 2.2: LOVED/LIKED Tier Assignment & Management

As a **user**,
I want to tag any podcast as LOVED or LIKED, change its tier, or remove the tag via a long-press menu,
So that PodDrive knows which shows matter most to me and can prioritise them.

**Acceptance Criteria:**

**Given** a podcast is displayed in the library
**When** the user long-presses a show row
**Then** a bottom sheet appears with options: Set LOVED / Set LIKED / Remove Tier (FR5, FR6)
**And** selecting a tier saves it via `SetEpisodeTierUseCase` and persists to SQLDelight on-device only (NFR7, AR3)
**And** the `TierBadge` updates immediately using both colour AND distinct iconography: filled heart for LOVED (`#F87171`), outline thumbs for LIKED (`#60A5FA`) — never colour alone (UX-DR2, UX-DR11)
**And** the bottom sheet is used for this non-destructive action — no modal alert dialog (UX-DR9)

---

### Story 2.3: Tier Filter Tabs on Library Screen — DEMOTED

> Pre-pivot story; kept for Show All / Library management. The home screen does NOT use tier filter tabs (it uses freshness bands instead — see Story 8.1).

As a **user**,
I want to filter my podcast list by All / Loved / Liked tabs on the Library screen,
So that I can focus on shows in a specific tier when managing my library.

**Acceptance Criteria:**

**Given** the Library screen is displaying the full library
**When** the user taps a `TierFilterTab` pill (All / Loved / Liked)
**Then** the list filters to show only podcasts matching the selected tier
**And** an empty filter state shows a helpful prompt (e.g. "No Loved shows yet — long-press a show to tag it")

---

### Story 2.4: Local Episode Cache

As a **user**,
I want my podcast library and episode data to be available without an active internet connection,
So that I can browse my queue even when offline.

**Acceptance Criteria:**

**Given** the library has been synced at least once
**When** the device has no network connection
**Then** the library loads from the SQLDelight local cache (FR8, AR3)
**And** unheard episode counts and LOVED/LIKED tiers are served from the cache
**And** an `OfflineBanner` (non-blocking, never modal) communicates offline status (UX-DR10)

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
**And** if the cache has not synced within 24 hours, a staleness indicator is displayed (FR32)
**And** Spotify API rate limits are handled transparently — requests queued and retried without user-visible errors (NFR17, AR12)
**And** library sync NEVER runs synchronously during home-screen open (NFR2 budget protection — AR12)

---

### Story 2.6: Add First Show — Onboarding Flow — REVISED

> Revised post-pivot: Bluetooth permission step REMOVED, tier-priming step is now optional and skippable, "add first show" search step REMOVED.

As a **new user completing onboarding**,
I want a frictionless setup that gets me to my home screen quickly,
So that I see the value of the app immediately rather than answering setup questions.

**Acceptance Criteria:**

**Given** the user has launched the app for the first time
**When** they complete onboarding
**Then** the flow is: Welcome ("See what's new in your podcasts. One tap to play.") → Spotify OAuth → Initial library import → optional tier-priming nudge ("Long-press a show to mark it LOVED") → Home Screen (UX-DR7)
**And** ONLY Spotify OAuth permission is requested — NO Bluetooth, NO notification, NO background-execution prompts (UX-DR7)
**And** OAuth errors surface inline with a retry option, not a modal (UX-DR10)
**And** if the user has zero Spotify subscriptions, an empty-library hero routes them to Explore rather than a dead end (UX-DR10)
**And** the tier-priming step is fully skippable

---

## Epic 3: Drive Session & Spoken Briefing — SUPERSEDED (scaffolding retained)

> **Status (2026-04-24):** This epic is superseded by SCP 2026-04-24. The product has pivoted to a podcast-list-centric Home Screen (Epic 8). Drive Mode + AI briefing are deferred to the Vision phase. **All Epic 3 code is retained as-built**; no next-release code path exercises it. **No new Epic 3 work** is in next-release scope. Bluetooth and TTS initialisation must be permission-gated behind a disabled feature flag (Architecture Gap §6) so users are not asked for permissions the app does not use.

The original story breakdown is preserved below for the future Drive Mode / Vision phase. Stories 3.1, 3.2, 3.3, 3.6 were built and merged before the pivot. Stories 3.4 and 3.5 remain deferred (never started).

### Story 3.1: Drive Session State Machine & Background Service — done (scaffolding)

(Pre-pivot acceptance criteria preserved verbatim — see commit history before 2026-04-24.)

### Story 3.2: Bluetooth Audio Device Detection & Auto-Session Activation — done (scaffolding)

### Story 3.3: Manual Drive Session Trigger — done (scaffolding)

### Story 3.4: TTS Spoken Briefing Generation & Delivery — DEFERRED (Drive Mode / Vision)

### Story 3.5: Audio Ducking & Hands-Free Playback Confirmation — DEFERRED (Drive Mode / Vision)

### Story 3.6: Drive Mode UI — Dashboard Screen — done (scaffolding)

---

## Epic 4: Spotify Playback & Episode Queue — RESCOPED

**Goal (revised):** Users can play episodes through Spotify deep-link or in-app ExoPlayer/AVPlayer (ADR-002), with graceful failure handling, RSS-discovered audio URLs, and persisted playback position. Priority queue + auto-advance retired.

### Story 4.1: Spotify Episode Deep-Link Handoff — done

As a **user**,
I want to tap an episode and have it open immediately in Spotify (when ExoPlayer fallback is needed),
So that playback starts without me needing to navigate the Spotify app.

**Acceptance Criteria:**

**Given** the user has selected an episode for playback and the audio URL is unavailable (RSS resolution failed)
**When** the system initiates Spotify handoff
**Then** the system initiates playback via `spotify:episode:{id}` deep-link URI (FR21, AR8)
**And** Spotify opens and begins playing the episode within 3 seconds of confirmation (NFR18)
**And** if the Spotify app is not running, PodDrive launches it before issuing the deep-link

---

### Story 4.2: Priority Episode Queue — RETIRED (SCP 2026-04-24)

> Retired alongside the pivot. Production code removal pending (surgical — see Architecture Gap §1).

### Story 4.3: Auto-Advance to Next Episode — RETIRED (SCP 2026-04-24)

> Retired alongside the pivot. Never built.

### Story 4.4: Playback Position Resumption — REDIRECTED → Story 8.9

> Redirected. Continue Listening Bar (Story 8.9) is the new home for playback-position resumption via `playback_state` singleton.

---

### Story 4.5: Spotify Playback Failure Handling — done

As a **user**,
I want clear, non-blocking notification if playback fails, without the app crashing,
So that I'm aware of the issue and can retry.

**Acceptance Criteria:**

**Given** an episode is selected for playback
**When** a Spotify deep-link, ExoPlayer/AVPlayer, or RSS resolution failure occurs
**Then** the app does not crash (FR25, NFR12)
**And** an in-app error message is shown via inline tile state and/or snackbar — never a modal (UX-DR10)
**And** `HandlePlaybackFailureUseCase` maps the failure to the appropriate `AppError` subtype (AR5)
**And** the user can retry from the same tile without leaving the home screen
**And** the home screen continues to render and remain interactive throughout the failure (NFR12)

---

### Story 4.6: Spotify Episode Auto-Play — done

As a **user**,
I want the chosen episode to auto-play once it's handed off to Spotify,
So that I don't have to tap "play" again inside the Spotify app.

**Acceptance Criteria:**

**Given** Spotify deep-link is the playback path
**When** the deep-link fires
**Then** Spotify opens already-playing the episode at the correct position
**And** if the episode does not auto-play, PodDrive logs the failure for diagnostics but does not surface a user-visible error

---

### Story 4.7: Active Device Polling for Single-Tap Playback — DEFERRED

> Superseded by ADR-002 (RSS+ExoPlayer audio path). Single-tap playback is now achieved via in-app `AudioPlayer` (Story 4.10) rather than active-device polling.

---

### Story 4.8: RSS Feed Discovery — done

As a **developer**,
I want to resolve each subscribed podcast's RSS feed URL from Spotify show metadata,
So that PodDrive can fetch episode audio URLs directly without depending solely on Spotify deep-link.

**Acceptance Criteria:**

**Given** a Spotify show ID
**When** `ResolvePodcastRssFeedUseCase` is invoked
**Then** the show's RSS feed URL is resolved (via Spotify show metadata or direct lookup) and persisted to the `podcasts.rss_feed_url` column (AR3, AR8)
**And** if no RSS feed is available, the column remains null and Spotify deep-link is used as fallback
**And** rate-limited requests respect Ktor `HttpRequestRetry` policy (AR12)

---

### Story 4.9: Episode Audio URL Resolution — done

As a **developer**,
I want each unlistened episode's direct audio URL fetched from its podcast's RSS feed,
So that ExoPlayer/AVPlayer can stream the episode without a Spotify context-switch.

**Acceptance Criteria:**

**Given** a podcast with a known `rss_feed_url`
**When** `ResolveEpisodeAudioUrlsUseCase` is invoked during library sync
**Then** episode audio URLs are parsed from RSS and persisted to the `episodes.audio_url` column (AR3)
**And** malformed XML, slow responses, and feed outages are handled gracefully — no crash; null `audio_url` triggers Spotify deep-link fallback (NFR12)
**And** the parser is shared in `commonMain` so behaviour is identical on Android and iOS (AR2)

---

### Story 4.10: ExoPlayer Integration — done

As a **user**,
I want podcast audio to play in PodDrive directly via ExoPlayer (Android) / AVPlayer (iOS),
So that quick-play feels instantaneous and does not require a Spotify context-switch.

**Acceptance Criteria:**

**Given** an episode has an `audio_url` resolved (Story 4.9)
**When** quick-play is invoked
**Then** the `AudioPlayer` (expect/actual; ExoPlayer Android, AVPlayer iOS) plays the episode directly (AR4, AR8)
**And** Android `PlaybackForegroundService` (mediaPlayback type, decoupled from drive-session lifecycle — AR7) takes audio focus
**And** iOS Background Audio mode allows playback to continue when the app is backgrounded (AR7)
**And** if `audio_url` is null, the system falls back to Spotify deep-link (Story 4.1)
**And** time from quick-play tap to first audio frame ≤2s on a 4G/Wi-Fi connection (NFR2 contributing budget)

---

### Story 4.11: In-App Playback Controls — done

As a **user**,
I want to play, pause, skip, and seek within the in-app player,
So that I have full control over playback without needing to open Spotify.

**Acceptance Criteria:**

**Given** an episode is playing via `AudioPlayer`
**When** the user invokes a transport control (play/pause/skip/seek)
**Then** the control is honoured via the platform media session
**And** the platform lock-screen / control-centre media controls reflect current state
**And** `playback_state` is updated on pause, stop, and at periodic intervals during playback (persist-before-emit — AR15)

---

## Epic 5: Empty Inbox & Smart Recommendations — REDIRECTED → Epic 8

> **Status (2026-04-24):** Redirected. Empty-inbox single-episode recommendation (drive-mode flow) is replaced by Home Screen Explore mode in Epic 8 Story 8.10 — recommends *podcasts*, not single episodes, and surfaces below the (empty) library rather than as a voice-driven flow.

### Story 5.1: Empty Inbox Recommendation Generation — REDIRECTED → Story 8.10

### Story 5.2: Hands-Free Accept or Decline of Recommendation — DEFERRED (Drive Mode / Vision)

---

## Epic 6: Resilience, Recovery & Error Handling — REFRAMED

**Goal (revised):** The system handles Spotify API failures, network outages, and OS process termination gracefully — recovering to the last known state without user intervention. Failures are surfaced via in-app status messaging (staleness banner, offline tile state, snackbar) — voice notification is retired alongside Drive Mode.

### Story 6.1: Spotify API Connection Failure — In-App Status Notification

As a **user**,
I want non-blocking in-app notification if PodDrive cannot reach Spotify's API,
So that I understand why my library is stale or my playback didn't start, without modal interruption.

**Acceptance Criteria:**

**Given** the app is foregrounded and a Spotify API call is made
**When** the system detects a connection failure (no network, DNS failure, or unreachable endpoint)
**Then** the app does not crash (FR29, NFR12)
**And** an `OfflineBanner` (non-blocking) renders above the library, communicating offline status (UX-DR10)
**And** affected tiles render in a dimmed offline state where applicable
**And** the cached library remains fully browsable
**And** `AppError.NetworkUnavailable` is surfaced through the `Result<T, AppError>` chain (AR5)
**And** no user-visible crash dialog or unhandled exception is shown (NFR12)

---

### Story 6.2: Spotify API Timeout Recovery & Request Retry

As a **user**,
I want PodDrive to automatically retry failed or timed-out Spotify requests and resume the intended action,
So that brief network hiccups don't block my listening experience.

**Acceptance Criteria:**

**Given** a Spotify API request times out or returns a rate-limit (429) response
**When** the error is caught by the Ktor HTTP client
**Then** the request is retried up to 3 times with exponential backoff (FR30, AR12)
**And** rate-limit responses are handled transparently — the user sees no error during retry (NFR17, AR12)
**And** if retries succeed, the intended action resumes without user intervention (FR30)
**And** if all 3 retries fail, the failure is surfaced via inline status (banner / dimmed tile / snackbar) — never modal (NFR12, UX-DR10)
**And** `AppError.RateLimitExceeded` and `AppError.SpotifyApiError` are correctly mapped and propagated (AR5)

---

### Story 6.3: OS Mid-Playback Process Kill — State Recovery

As a **user**,
I want PodDrive to restore my playback position automatically if the OS terminates the app mid-listening,
So that I can pick up where I left off without any action from me.

**Acceptance Criteria:**

**Given** an episode was playing via `AudioPlayer` and `playback_state` was being persisted on interval (AR15)
**When** the OS terminates the app process and the user relaunches PodDrive
**Then** on relaunch the home screen reads the persisted `playback_state` singleton row (FR31, AR3, AR15)
**And** if `position_ms > 0 AND episode_id IS NOT NULL AND position_ms < episode.duration_ms`, the `ContinueListeningBar` renders (Story 8.9, FR41)
**And** tapping the bar resumes playback from the persisted position via `AudioPlayer.play(audioUrl, resumePositionMs = position_ms)`
**And** the recovery completes within 10 seconds of app relaunch without user intervention (NFR11)
**And** if the persisted state is unrecoverable (e.g. episode unsubscribed), the bar simply does not render — no crash, no error message (NFR12)

---

## Epic 7: Privacy, Data Transparency & Settings — REFRAMED

**Goal (revised):** Users can view what data PodDrive stores locally, confirm preferences stay on-device, and reset all data. Design tokens + accessibility compliance from the original Epic 7 are redirected into Epic 8 (Story 8.8).

### Story 7.1: Data Transparency — View Stored Data

As a **user**,
I want to view exactly what data PodDrive stores about me,
So that I can make an informed decision about keeping or removing the app.

**Acceptance Criteria:**

**Given** the user navigates to Settings
**When** they tap "My Data"
**Then** a screen lists all categories of locally stored data: Spotify credentials (in SecureStorage), LOVED/LIKED/OTHER tier preferences, episode play history, `playback_state` (Continue Listening), cached podcast library + RSS audio URLs (FR33)
**And** the screen confirms that none of this data is transmitted to any external server or third party in next release (FR34, NFR7)
**And** all text on this screen meets WCAG 2.1 Level AA contrast requirements (NFR15)
**And** the screen supports Dynamic Type at all system text size settings, reflowing without truncation (NFR16, NFR23)

---

### Story 7.2: Full Data Reset

As a **user**,
I want to delete all locally stored data and reset the app to its initial state,
So that I can fully remove my personal data from the device.

**Acceptance Criteria:**

**Given** the user is on the Settings screen
**When** they tap "Delete All Data & Reset"
**Then** a confirmation dialog appears (destructive action) explaining what will be deleted (UX-DR9)
**And** on confirmation, all SQLDelight tables are cleared, all SecureStorage credentials are deleted, and all in-memory state is reset (FR35)
**And** the app returns to the unauthenticated welcome screen
**And** no locally stored data is recoverable after the reset
**And** the user can re-authenticate and start fresh via Story 1.3

---

### Story 7.3: Design Token System Implementation — REDIRECTED → Story 8.8

> Redirected. Design tokens for the new home-screen palette (universal dark-first colour system, three band tints, FreshnessBucket ramp, tier accents, single typography scale, motion tokens) are implemented as part of Story 8.8 alongside the universal driver-friendly visual baseline.

### Story 7.4: Accessibility Compliance & Responsive Design — REDIRECTED → threaded through Epic 8

> Redirected. Accessibility (WCAG 2.1 AA universal, AAA on tile titles + band headers, TalkBack/VoiceOver support, Dynamic Type 200% reflow, Reduce Motion variants, deuteranopia/protanopia validation) is a CI gate threaded through every Epic 8 story, with the consolidated infrastructure delivered in Story 8.8.

---

## Epic 8: Podcast-List Home Screen

**Goal:** The home screen *is* the product. A sorted list of subscribed podcasts filtered to those with unlistened episodes, banded by freshness (TODAY / THIS WEEK / OLDER), with tile-level quick-play, expand-in-place drill-down, long-press tier promotion, a Show All escape hatch, and (Fast-Follow) a Continue Listening bar plus Explore mode for the caught-up state.

> **Story sequencing.** Stories 8.1–8.8 are Core MVP and must ship together — they realise the home-screen product surface. Stories 8.9 and 8.10 are Fast-Follow (ship immediately after MVP). Build order respects dependencies: design tokens (8.8) → TierBadge (covered in 8.6 / reused from 2.2) → PodcastTile + FreshnessBandHeader → HomeScreen composition (8.1) → quick-play (8.3) → expand-in-place (8.4) → freshness colour (8.5) → tier promotion (8.6) → Show All (8.7) → 8.9 → 8.10.

### Story 8.1: Three-Band Podcast List Sort

As a **user**,
I want my home screen to show my subscribed podcasts grouped into freshness bands (TODAY / THIS WEEK / OLDER), sorted within each band by tier (LOVED → LIKED → untiered) then by latest-unlistened-episode date,
So that I can see what's new and worth playing without scanning or filtering.

**Acceptance Criteria:**

**Given** the user is authenticated and the library cache is populated
**When** the home screen loads
**Then** podcasts are rendered in three sticky-headed bands — TODAY, THIS WEEK, OLDER — with band headers using Subhead 14sp UPPERCASE +0.5 letter-spacing on the band's tinted background (FR37, UX-DR4, UX-DR6)
**And** within each band, podcasts are sorted: LOVED first, then LIKED, then untiered, then by latest-unlistened-episode publish date descending (FR37, UX-DR6)
**And** sort + band derivation is performed in `commonMain` via `GetSortedPodcastsUseCase`, observed by `HomeScreenViewModel` as a `Flow` (AR6, AR10)
**And** empty bands are NOT rendered — band header is suppressed entirely (UX-DR4)
**And** if all three bands are empty, the caught-up hero state renders (Story 8.10 wires Explore beneath it)
**And** the home screen renders within 1 second of app foreground for a library of up to 50 podcasts with cached data (NFR21)
**And** band background tints render correctly (`#1A1416` / `#14171C` / `#101013`) with text contrast meeting WCAG AA on `color-text-primary` (UX-DR2, NFR23)
**And** band differentiation does not rely on hue alone — colour + position + uppercase header label are all distinguishers (NFR24, UX-DR13)
**And** TalkBack/VoiceOver focus order is: band header → tiles in band order, with band header announcing "TODAY band, N podcasts" (UX-DR13)

---

### Story 8.2: No-Unlistened Invisible Filter

As a **user**,
I want podcasts with zero unlistened episodes to disappear from my home screen,
So that the home screen is a glance-and-go surface for what's new — not a full library view.

**Acceptance Criteria:**

**Given** the user has a library of subscribed podcasts including some with zero unlistened episodes
**When** the home screen renders
**Then** only podcasts with at least one unlistened episode appear on the home screen (FR36)
**And** podcasts with zero unlistened episodes remain accessible via Show All (Story 8.7) without exception (FR43)
**And** if a podcast becomes "all played" while the home screen is open (e.g. user marks last episode played from inside an expanded tile), the tile is removed on the next `GetSortedPodcastsUseCase` Flow emission with a 220ms fade-out animation (UX-DR8)
**And** if a podcast becomes "newly has unlistened" during a sync (new episode dropped), the tile inserts into its appropriate band with a 220ms slide-in animation (UX-DR8)
**And** Reduce Motion preference collapses both insert and removal animations to instant (UX-DR13, UX-DR8)

---

### Story 8.3: Per-Tile Quick-Play Button

As a **user**,
I want a prominent quick-play ▶ on every podcast tile that plays the most recent unlistened episode in one tap,
So that the most common action — "play what's new from this show" — is one tap from the home screen.

**Acceptance Criteria:**

**Given** the home screen is rendered with at least one podcast tile visible
**When** the user taps the quick-play ▶ on a tile
**Then** `GetLatestUnlistenedUseCase` resolves the most recent unlistened episode for that podcast (FR39, AR10)
**And** if `audio_url` is present, `AudioPlayer.play(episode.audioUrl, resumePositionMs = 0)` starts playback (Story 4.10, AR4, AR8)
**And** if `audio_url` is null, the system falls back to Spotify deep-link `spotify:episode:{id}` (Story 4.1, AR8)
**And** the ▶ has its own dedicated 48dp Android / 44pt iOS circular hit-box, separated from the tile body by ≥8dp/pt (NFR22, UX-DR9, UX-DR5)
**And** tapping ▶ never expands the tile body — the two zones are mutually exclusive (UX-DR5)
**And** the ▶ press animation runs (80ms scale-in / 120ms scale-out + short success haptic) — collapsed to instant under Reduce Motion (UX-DR8, UX-DR13)
**And** the ▶ uses `color-action-primary` (`#22D3EE`) — visually distinct from `color-tier-loved` (coral) and `color-tier-liked` (blue) so it is never confused for a tier indicator (UX-DR2)
**And** time from tap to first audio frame ≤2s on a 4G/Wi-Fi connection contributes to the NFR2 ≤5s end-to-end budget (NFR2)
**And** TalkBack/VoiceOver exposes the ▶ as a custom action labelled "Quick play, double-tap to play top episode" on the tile group (UX-DR13)

---

### Story 8.4: Expand-in-Place Drill-Down with Sort-Freeze

As a **user**,
I want to tap a tile's body to reveal that podcast's unlistened episodes inline, without leaving the home screen,
So that I can choose a specific episode without page navigation.

**Acceptance Criteria:**

**Given** the home screen is rendered
**When** the user taps a tile's body (NOT the ▶)
**Then** the tile expands downward in place, revealing unlistened episodes sorted newest-first (FR40)
**And** episode rows fade in with a 60ms staggered delay; total animation 220ms ease-out (UX-DR4, UX-DR8)
**And** tapping the tile body again (or a second tile body) collapses the first; only one tile may be expanded at a time (UX-DR4)
**And** each episode row exposes a flat ▶ (32dp/pt; no elevation) that fires Spotify deep-link / `AudioPlayer` directly to that episode (UX-DR4)
**And** if the podcast has more than 5 unlistened episodes, a "See all (N)" link is shown at the bottom of the expanded list (UX-DR4)
**And** while ANY tile is expanded, band-move animations are SUSPENDED — count badges may re-colour in place but tiles do not move bands until collapsed (UX-DR14, sort-freeze rule)
**And** on collapse, if the freshness bucket has changed, the tile re-settles into its new band with the standard 280ms band-move animation (UX-DR14)
**And** Reduce Motion collapses expand/collapse to instant snap with no fade and no stagger (UX-DR13, UX-DR8)
**And** TalkBack/VoiceOver announces "Expanded. N unplayed episodes." on expansion and "Collapsed." on collapse (UX-DR13)

---

### Story 8.5: Unlistened Count + Freshness Colour Badge

As a **user**,
I want each tile's unlistened-count badge to be colour-keyed to how fresh the newest unlistened episode is,
So that I can see at a glance which shows have brand-new episodes vs. older backlogs.

**Acceptance Criteria:**

**Given** a podcast tile is rendered
**When** `GetUnlistenedCountUseCase` emits for that podcast
**Then** the use case emits BOTH the integer count AND a `FreshnessBucket` enum value (`TODAY`, `THIS_WEEK`, `OLDER`) computed in `commonMain` (FR38, AR13)
**And** the `CountBadge` renders with the correct freshness colour: `TODAY` → `#F59E0B` saturated amber fill with `#0F0F12` text; `THIS_WEEK` → `#9C7C42` dimmed amber fill with `#F9FAFB` text; `OLDER` → transparent fill with `1dp #2D2D38` outline and `#C0C0C8` text (UX-DR2, FR38)
**And** count text size is identical across all three states (12sp Mono/Tabular Bold) — only fill + text colour changes (NFR25)
**And** the `OLDER` outline-only badge communicates "old/many" without colour-shouting (NFR25, UX-DR2)
**And** colour does NOT carry meaning alone — count text and band position are independent legibility channels (NFR25, UX-DR13)
**And** all 18 combinations of `band background × freshness state × tier icon` pass WCAG AA contrast in CI via Compose Preview / SwiftUI Preview screenshot diffs (NFR24, UX-DR13)
**And** badge contrast is validated under deuteranopia + protanopia simulation per release (UX-DR13)
**And** unit tests in `commonTest` verify the `FreshnessBucket` derivation rule (e.g. publish date within 24h → TODAY; within 7 days → THIS_WEEK; else OLDER) (AR13)

---

### Story 8.6: Manual Tier Promotion from Tile (Long-Press)

As a **user**,
I want to long-press any tile to promote, demote, or unsubscribe a podcast,
So that I can refine my library priorities directly from the home screen without navigating to a settings menu.

**Acceptance Criteria:**

**Given** the home screen is rendered
**When** the user long-presses a tile body for ≥500ms (NOT the ▶ — long-press on ▶ does nothing)
**Then** a medium-impact haptic fires and a platform-native `ModalBottomSheet` (Android) / `.confirmationDialog` (iOS) opens with options: Promote to LOVED / Demote to LIKED / Unsubscribe (FR44, UX-DR15)
**And** selecting Promote or Demote calls `PromoteTierUseCase(podcastId, newTier)` — write-only (AR14, AR10)
**And** the sorted list re-emits via `GetSortedPodcastsUseCase`'s Flow; the tile animates to its new in-band position via `Modifier.animateItemPlacement(tween(280))` (Compose) / `.matchedGeometryEffect` (SwiftUI) (UX-DR8, UX-DR15)
**And** promotion does NOT move the tile across freshness bands — only re-orders within band; tier is in-band sort, not cross-band (UX-DR15, AR6)
**And** a 4-second "Promoted to LOVED. Undo." snackbar shows; tap Undo reverses via inverse `PromoteTierUseCase` call (UX-DR15)
**And** Unsubscribe is destructive and requires a confirmation dialog (UX-DR9, UX-DR15)
**And** Reduce Motion collapses the band-move animation to instant snap (UX-DR13)
**And** TalkBack/VoiceOver exposes "Show options" as a custom action (replacing long-press, which is unreliable for screen-reader users) (UX-DR13)
**And** TalkBack/VoiceOver announces "Promoted to LOVED. Now first in TODAY band." after promotion (UX-DR13)

---

### Story 8.7: Show All Library Screen

As a **user**,
I want a Show All escape hatch from the home screen that lists every subscribed podcast (including those with zero unlistened episodes), grouped by tier,
So that I can manage my full library without the home screen becoming a dense list-management surface.

**Acceptance Criteria:**

**Given** the home screen is rendered
**When** the user taps "Show All →" on the app bar
**Then** the Show All screen opens, listing every subscribed podcast — including those with zero unlistened episodes (FR43)
**And** podcasts are grouped by tier (LOVED / LIKED / OTHER) — three tier sections always visible; no tier filter tabs (UX-DR3)
**And** each `ShowTile` shows: artwork, name, unplayed count (or "0 unplayed"), `TierBadge`, and a long-press affordance for tier change / unsubscribe (UX-DR9, UX-DR15)
**And** an inline search field (debounced 300ms) filters across all tier sections simultaneously (UX-DR-search)
**And** all tap targets meet ≥48dp Android / ≥44pt iOS with ≥8dp/pt visual separation (NFR22, UX-DR9)
**And** the screen supports Dynamic Type to 200% with no truncation (NFR16, NFR23)
**And** TalkBack/VoiceOver focus order: search field → tier section header → tiles in section, with each tier section header announcing "[Tier] section, N podcasts" (UX-DR13)

---

### Story 8.8: Driver-Friendly Visual Baseline (Design Tokens + Universal A11y Infra)

As a **developer**,
I want a unified design-token layer and the accessibility CI infrastructure in place,
So that every Home Screen story can consume tokens consistently, the 18-combination contrast matrix is validated automatically, and tap-target separation is enforced by tests.

**Acceptance Criteria:**

**Given** the KMP project is in place
**When** Story 8.8 is complete
**Then** `Theme.kt` (Android Compose `MaterialTheme` extension) and `DesignTokens.swift` (iOS SwiftUI environment values) define the universal token set (UX-DR1, FR45):
  - Colour tokens — `color-surface-base #0F0F12`, `color-surface-elevated #1C1C24`, `color-surface-overlay #272736`, `color-divider #2D2D38`, `color-text-primary #F9FAFB`, `color-text-secondary #C0C0C8`, `color-text-tertiary #8A8A92`, three band tints, three `color-fresh-*` ramp values, two tier accents, action accents (UX-DR2)
  - Typography tokens — Display 28 / H1 24 / H2 20 / Tile Title 18 / Body 16 / Subhead 14 (UPPERCASE +0.5 letter-spacing) / Caption 13 / Count Badge 12 (Mono Bold) (UX-DR1)
  - Spacing — 8dp/pt base grid; 76dp/pt fixed tile height; 24dp/pt band-header gap; 32dp/pt between bands; 16dp/pt mobile / 12dp/pt compact / 24dp/pt standard side margins (UX-DR1, UX-DR12)
  - Motion tokens with reduced-motion variants — 220ms expand / 180ms collapse / 280ms band-move / 80ms ▶ scale-in / 120ms ▶ scale-out / 220ms tile insert / 1.2s shimmer loop (UX-DR1, UX-DR8, UX-DR13)
  - Iconography — filled heart for LOVED, outline thumbs for LIKED, distinguishable at 16dp/pt (UX-DR11, UX-DR1)
**And** every Home Screen component references tokens — NO hardcoded colours, sizes, or motion durations in `*Tile.kt` / `*View.swift` files
**And** universal tap target enforcement: `Modifier.minimumInteractiveComponentSize()` (Compose) / `.frame(minHeight: 44)` (SwiftUI) on every interactive element (NFR22)
**And** CI gates added (UX-DR13):
  - Compose Preview / SwiftUI Preview screenshot-diff test for the 18-combination contrast matrix on every PR (NFR24, NFR25)
  - Tap-target spacing assertion: `PodcastTile` ▶ hit-box and tile-body hit-box must not overlap; ≥8dp/pt separation enforced by test (NFR22)
  - Contrast-ratio computation: design tokens parsed in CI; every documented (foreground, background) pair must compute ≥ documented ratio (NFR23, NFR24, NFR25)
  - `AccessibilityChecks.enable()` in Espresso (Android) on every UI test; Xcode Accessibility Inspector audit on iOS — fails build on level Error (UX-DR13)
**And** Reduce Motion preference detection (`LocalAccessibilityManager.current.isReduceMotionEnabled` / `@Environment(\.accessibilityReduceMotion)`) selects reduced-variant tokens (UX-DR13)
**And** Dynamic Type tested at 100% / 150% / 200% on every Home Screen surface — no text truncation under 200% (NFR16, NFR23)
**And** deuteranopia + protanopia simulation pass per release (manual + Stark Figma plugin) (UX-DR13)

---

### Story 8.9: Continue Listening Bar (Fast-Follow)

As a **user**,
I want a slim "Continue Listening" bar above my library when I have an episode in progress,
So that I can resume from where I left off in one tap without hunting for the episode.

**Acceptance Criteria:**

**Given** the user has previously played an episode and `playback_state` has been persisted (AR15)
**When** the home screen loads
**Then** `GetContinueListeningUseCase` reads the `playback_state` singleton row (AR10)
**And** the `ContinueListeningBar` renders ABOVE the bands ONLY when `position_ms > 0 AND episode_id IS NOT NULL AND position_ms < episode.duration_ms` (FR41, UX-DR16)
**And** the bar shows: ▶ glyph + "Continue: [Episode title]" + "[MM:SS / MM:SS]" position (UX-DR16)
**And** tapping the bar resumes via `AudioPlayer.play(audioUrl, resumePositionMs = position_ms)` (or Spotify deep-link with position offset if `audio_url` is null) (FR24, FR41, AR4, AR8)
**And** the bar is non-sticky — it scrolls with the list rather than pinning to the top (UX-DR16)
**And** if the resumable episode's podcast is unsubscribed (locally or on Spotify), the bar disappears on the next Flow emission with no error (UX-DR16)
**And** if the resumable episode is marked played, the bar disappears on the next Flow emission with no error (UX-DR16)
**And** only ONE bar renders — most recently played episode (UX-DR16)
**And** when the user returns to PodDrive after Spotify playback, `playback_state` is best-effort re-synced from Spotify's "currently playing" API before rendering (UX-DR16)
**And** persist-before-emit rule applies: position snapshots are written to SQLDelight before being emitted to observers (AR15)
**And** state recovery after OS kill resumes within 10 seconds of relaunch (NFR11, FR31, Story 6.3)
**And** TalkBack/VoiceOver announces the bar as "Continue listening: [Episode title], [MM:SS] of [MM:SS]" (UX-DR13)

---

### Story 8.10: Home Screen Explore Mode (Fast-Follow)

As a **user with no unlistened episodes**,
I want a small set of recommended podcasts surfaced on the home screen instead of an empty state,
So that being caught up is a feature — not a dead end.

**Acceptance Criteria:**

**Given** the user is authenticated and the library has been synced
**When** every band is empty (zero unlistened episodes across all subscribed podcasts) — and library sync has completed (NOT empty cache) (Architecture Gap §3)
**Then** the home screen renders the caught-up state: quiet hero copy "You're caught up. Pull to refresh or browse Explore below." + the `ExplorePanel` prominently below (FR42, UX-DR10)
**And** `GetExploreRecommendationsUseCase` sources recommendations from on-device listening history — never transmitted to any external server (FR26, NFR7, AR10)
**And** the `ExplorePanel` surfaces a small set (target: 3) of recommended *podcasts* (NOT single episodes) visually tagged as `explore` (FR26, FR27, UX-DR17)
**And** each Explore tile exposes the three-action contract (UX-DR17):
  - ▶ → play preview via `AudioPlayer` / Spotify deep-link of the most recent episode; does NOT subscribe (FR28)
  - Tile body tap → preview sheet (artwork, name, description, recent 5 episodes)
  - `+` button → instant subscribe with 4-second undo Snackbar
**And** subscribe writes the new podcast locally and calls Spotify subscribe API; the library Flow re-emits and the new tile lands in its appropriate freshness band with a 220ms slide-in animation (FR28, UX-DR17, UX-DR8)
**And** subscribe failure surfaces an inline error in the panel ("Couldn't subscribe — try again"); no modal; the `+` button reverts to its idle state (UX-DR17, UX-DR10)
**And** the `ExplorePanel` is ALWAYS rendered below the bands (even when bands are populated), but is *prominently placed* (full panel visible without scrolling) only when all bands are empty (UX-DR4)
**And** if no recommendations can be generated (e.g. brand-new user with no listening history), the panel renders a quiet "Listen to a few episodes and we'll suggest shows here." copy (UX-DR10)
**And** Reduce Motion collapses the slide-in animation to instant insert (UX-DR13)
**And** all Explore tile tap targets meet ≥48dp / ≥44pt with ≥8dp/pt separation (NFR22, UX-DR9)
**And** TalkBack/VoiceOver announces each Explore tile as a group: "[Podcast name], suggestion based on your history. Subscribe button. Quick play button. Tap to preview." (UX-DR13)
**And** if Explore v1 needs richer listening-history signal than current `is_played` boolean, schema addition is flagged here for Architect resolution (Architecture Gap §5)
