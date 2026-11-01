---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-02b-vision', 'step-02c-executive-summary', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish']
classification:
  projectType: mobile_app
  domain: consumer_media_entertainment
  complexity: medium
  projectContext: greenfield
inputDocuments: ['_bmad-output/brainstorming/brainstorming-session-2026-03-19-002.md', '_bmad-output/project-context.md']
workflowType: 'prd'
briefCount: 0
researchCount: 0
brainstormingCount: 1
projectDocsCount: 0
---

# Product Requirements Document - PodDrive

**Author:** Dimitrios.dimitrelos
**Date:** 2026-03-19
**Last revised:** 2026-04-24 — pivot to podcast-list-centric home screen per [Sprint Change Proposal 2026-04-24](sprint-change-proposal-2026-04-24.md) (drive-session + episode-queue model deferred to Vision phase; new Home Screen Experience requirements added)

---

## Executive Summary

PodDrive is a free iOS and Android companion app that fixes a long-standing gap in Spotify's podcast experience: **there is no fast, low-effort way to see what's new from the podcasts you actually care about.** Spotify's library UI forces users to drill into each show individually to discover unheard episodes — tolerable on the couch, impractical in the car, and fatiguing anywhere.

**Target users:** Active Spotify podcast listeners who follow multiple shows and want to spend less time hunting and more time listening. Drivers are a primary use-context but not a separate product mode.

**The problem:** Finding new episodes across followed podcasts is a multi-tap, per-show navigation task. There is no "inbox" view. Users miss episodes from shows they love, or give up and default to music.

**The solution:** PodDrive's home screen is the product — a sorted list of subscribed podcasts with unlistened episodes, grouped into three color-differentiated bands (LOVED → LIKED → OTHER) and ordered by the latest unlistened episode within each band. Each tile surfaces an unlistened count (color-encoded by freshness), a one-tap quick-play button that starts the most recent unlistened episode, and expands in place to reveal the full unlistened list. A "Continue Listening" bar resumes in-progress episodes; when the library is fully caught up, the screen enters Explore mode to surface recommended podcasts.

**Driver-friendly by default, not by mode:** Big tiles, big buttons, readable text are a universal design constraint, not a separate drive mode. The app is safe to use at a glance in the car *because* it's visually simple everywhere — no Bluetooth auto-activation, no voice briefing, no steering-wheel semantics in this version.

**Revenue model:** Free to users. Podcast creators pay per relevant recommendation delivered in Explore mode — access to compatible listeners, not impression-based advertising.

**Strategic position:** PodDrive does not compete with or require changes from Spotify. It operates as a layer on top of the Spotify Web API and podcast RSS feeds, delivering the inbox mental model Spotify's business model incentivises it to withhold.

### What Makes This Special

**The home screen is the product.** Most podcast apps give you a library; PodDrive gives you a prioritized, glanceable feed of what's actually new from shows you've declared you care about. One tile, one tap, you're listening — no menus, no filtering, no hunting.

**Two-tier priority system, made visible.** LOVED / LIKED / OTHER tagging mirrors how users actually relate to content — not everything they follow deserves equal urgency. The three bands make the hierarchy visible at a glance, and within each band the freshest episodes bubble up automatically.

**Driver-friendly as a universal constraint.** Rather than building a separate "drive mode" that users must remember to activate, PodDrive applies driver-safe sizing, contrast, and tap targets everywhere. The result is a product that's equally usable on the couch, at a desk, and (at a glance) in the car — without the mental overhead of mode-switching.

**Bypasses the root cause.** Rather than attempting to influence Spotify's product decisions or advocate for a feature Spotify has a business incentive to deprioritise, PodDrive fills the gap independently using existing public APIs. It is buildable today.

## Project Classification

- **Project Type:** Mobile App (iOS & Android)
- **Domain:** Consumer Media & Entertainment
- **Complexity:** Medium — Spotify Web API integration, podcast RSS feed parsing, tier-based sort + filter UI, background playback, lightweight recommendation engine for Explore mode, creator marketplace
- **Project Context:** Greenfield — no existing codebase; net-new product

---

## Success Criteria

### User Success

- **Glanceable prioritization:** User opens the app → sees their LOVED podcasts with unlistened episodes at the top of the list, visually distinct from LIKED and OTHER → understands the hierarchy without explanation.
- **Time-to-play:** ≤5 seconds from app open to podcast audio playing via quick-play on the top tile, assuming a fresh session with cached library data.
- **7-day retention:** ≥60% of users who complete one quick-play session return within 7 days.
- **LOVED/LIKED adoption:** ≥80% of users with ≥5 followed podcasts apply at least one LOVED tag within the first session.
- **Caught-up state handled gracefully:** When a user has no unlistened episodes across all tiers, Explore mode presents a single clear recommendation rather than an empty screen.
- **Driver glanceability:** A user at a traffic stop can identify their top-priority unlistened podcast and initiate playback in a single tap without taking their eyes off the road for more than ~1 second (qualitative, validated via MVP user testing).

### Business Success

| Horizon | Target | Signal |
|---------|--------|--------|
| 3 months | 3 active users | MVP validated on real devices in real driving conditions |
| 12 months | 1,000 active users | Product-market fit signal; organic growth possible |
| 12 months | 1 creator partner | Business model validated; revenue model proven in principle |

### Technical Success

- Spotify Web API integration handles token refresh, rate limits, and scope permissions reliably with no manual re-authentication required after initial setup.
- Podcast RSS feeds for subscribed shows refresh within 60 seconds on a standard mobile data connection for a library of up to 50 podcasts; stale indicator surfaces if sync is >24h old.
- Background audio playback survives phone lock and incoming calls without requiring app re-launch.
- App does not drain battery detectably (≤5% additional battery draw per 60-minute listening session vs. baseline Spotify usage).

### Measurable Outcomes

- A user can go from cold start (first install) to first episode playing via the home screen in ≤3 minutes.
- A user with 10 followed podcasts can identify and start the most recent episode from their top-priority show in a single tap from app open.
- Zero critical crashes during a 60-minute listening session in standard conditions (background audio + locked screen + incoming call).

---

## Product Scope

### MVP — Minimum Viable Product

The smallest version of PodDrive that delivers the podcast-list home screen end-to-end:

- **Spotify OAuth login** — authenticate and authorise podcast library access
- **Podcast library sync** — subscribed podcasts + unlistened episode detection via Spotify API and podcast RSS feeds
- **LOVED / LIKED / OTHER tagging** — three-tier priority system stored on-device
- **Three-band home screen** — subscribed podcasts with unlistened episodes, sorted into color-differentiated LOVED / LIKED / OTHER bands, then by latest unlistened episode date within each band
- **Per-tile unlistened count with freshness color** — count badge colored by the freshness of the newest unlistened episode (bright for today, fading for older)
- **Quick-play button** — one tap on a tile's play button starts the most recent unlistened episode (no smart routing)
- **Expand-in-place drill-down** — tap a tile body to reveal unlistened episodes newest-first; tap again to collapse
- **Tier promotion from tile** — change a podcast's tier (OTHER → LIKED → LOVED) directly from the home screen
- **"Show All" library screen** — full subscription view for management (including podcasts with no unlistened episodes)
- **Driver-friendly visual baseline** — big tiles, big buttons, readable text, contrast-safe colors applied universally (no separate drive mode)
- **Background playback plumbing** — ExoPlayer / AVPlayer foundation, episode URL resolution, Spotify deep-link handoff (already built in Epic 4)

### Fast-Follow (immediately after MVP)

- **Continue Listening bar** — conditional bar above the home-screen list that resumes the last in-progress episode with a single tap
- **Explore mode** — empty-state replacement when no subscribed podcast has unlistened episodes; surfaces recommended podcasts with a visual `explore` tag

### Growth Features (Post-MVP)

- **Creator partnership slot** — sponsored Explore recommendations, relevance-gated
- **Lock screen / home screen widget** — "X new episodes across your LOVED podcasts" with tap-to-open
- **Smartwatch integration** — glanceable unlistened count; tap to open the home screen
- **Push notifications** — opt-in alerts when a LOVED podcast drops a new episode
- **Voice assistant deep integration** — "Hey Siri / Google, play my latest podcast via PodDrive"

### Vision (Future)

- **Multi-platform library sources** — aggregate across Apple Podcasts, YouTube, Pocket Casts alongside Spotify
- **Drive Mode (revisited)** — reintroduce the Bluetooth-triggered AI briefing and hands-free flow as a *separate* opt-in experience on top of the home-screen foundation (the drive-session + voice briefing code from Epic 3 is kept as-built for this future phase)
- **Car manufacturer licensing** — embed PodDrive natively in infotainment systems (B2B)
- **Calendar integration** — podcast episodes surfaced as time-blocked calendar events

---

## User Journeys

### Persona: Dimitrios, The Commuter

**Name:** Dimitrios, 38, Senior Consultant
**Situation:** Drives 45 minutes each way to the office, 4 days a week, and also listens at the gym and while cooking. Follows 14 podcasts on Spotify — 3 he considers essential (LOVED), the rest he listens to when he has time.
**Goal:** Never miss a new episode from his essential podcasts, and spend his listening time on content he actually wants to hear — not on hunting for it.
**Obstacle:** Spotify requires him to navigate to each podcast individually, filter unheard episodes, and tap to play. The friction is worst in the car but wears on him everywhere.
**Solution:** PodDrive's home screen shows him at a glance what's new from shows he loves — one tap plays the most recent episode.

---

### Journey 1 — The Happy Path: Monday Morning Coffee

**Opening Scene — The Kitchen**
It's 7:45am. Dimitrios pours coffee and opens PodDrive on his phone. The home screen loads in under a second, already synced overnight.

At the top, in a warm amber band: two LOVED tiles — *The Lex Fridman Podcast* and *Acquired* — each showing a bright "1" unlistened badge (freshly dropped, both from today). Below, in a cooler band, four LIKED tiles with fading-color counts (some today, some from earlier this week). Further down, a handful of OTHER tiles with small counts.

He doesn't scroll. He doesn't filter. He doesn't read. He taps the ▶ button on the Lex Fridman tile and goes to put his shoes on.

**Rising Action — The Drive**
At 8:15am Dimitrios gets in the car. The phone's already playing Lex Fridman in the background, paired to Bluetooth automatically via the OS. He drives to work, eyes on the road, hands on the wheel — no app interaction needed during the drive. Forty minutes later, the episode hasn't finished. He parks and pauses.

**Climax — The Habit**
Three weeks in, Dimitrios has stopped missing Lex Fridman episodes. His morning ritual is coffee + glance + tap. The car is just where the listening happens; the *decision* happened in the kitchen. He mentions it to a colleague: *"It's the podcast app Spotify should've built."*

**Resolution — The New Normal**
Dimitrios's LOVED list now has 4 podcasts. He's promoted two shows from OTHER to LIKED over the past month by long-pressing their tiles on the home screen. He hasn't missed a new Lex Fridman episode in two months.

---

### Journey 2 — The Drill-Down: Choosing Among Unlistened Episodes

**Opening Scene — A Tuesday Evening on the Couch**
Dimitrios opens PodDrive after dinner. *Acquired* is at the top of his LIKED band with a "3" unlistened count. He's curious what the three are — two are older, one just dropped today (the count badge is a bright orange; the older two episodes show as muted on expand).

He taps the *Acquired* tile body (not the ▶ button). The tile expands in place, revealing three episode rows newest-first:
- *NVIDIA Part III — 4h 12m — today* (bright)
- *The TSMC Story — 3h 45m — 5 days ago* (muted)
- *Meta's Reality Labs — 2h 58m — 11 days ago* (muted)

He picks the TSMC one because it's the one he's been saving. One tap on its row, it plays.

**Rising Action — Promoting a Show**
Halfway through the episode he realises *Acquired* really belongs in LOVED for him. He backs out, long-presses the tile, and selects "Promote to LOVED" from the action sheet. The tile animates up into the amber band. Next time Acquired drops an episode, he'll see it at the top.

**Resolution — The Control Feels Earned**
Dimitrios likes that he chose this. No algorithm promoted Acquired — he did, after the content earned it. The list now accurately reflects his priorities.

---

### Journey 3 — The Caught-Up State: Explore Mode (Fast-Follow)

**Opening Scene — A Friday Afternoon**
Dimitrios opens PodDrive. The home screen is unusual today — no tiles. He's caught up across LOVED, LIKED, and OTHER. Instead of an empty screen, PodDrive shows Explore mode: three recommended podcasts with a soft `explore` tag, chosen based on his listening history.

**Rising Action — A Trusted Nudge**
The top recommendation is *The Tim Ferriss Show — "Lessons from 1,000 Episodes"*. The tag makes it clear this is a discovery, not a subscribed show. He taps the ▶ button and listens while he cooks.

**Climax — Subscribe In The Moment**
He likes what he hears. A "Subscribe" button on the expanded view adds Tim Ferriss to his library at the OTHER tier. Next week, when new episodes drop, they'll appear on his home screen without him hunting.

**Resolution — The Empty State Isn't Empty**
Being caught-up isn't a dead end — it's a feature. PodDrive turns the quiet moment into a single thoughtful suggestion, not a firehose.

---

### Journey Requirements Summary

| Capability | Revealed By |
|---|---|
| Spotify OAuth + followed podcast fetch | All journeys — app is useless without authenticated library access |
| Podcast RSS sync + unlistened episode detection | All journeys — foundation of the "what's new" signal |
| LOVED / LIKED / OTHER tagging & persistence | Journey 1, Journey 2 — tier-based visual hierarchy |
| Three-band color sort on home screen | Journey 1 — glanceable prioritization |
| Per-tile unlistened count with freshness color | Journey 1, Journey 2 — glanceable "what's new" signal |
| Per-tile quick-play button | Journey 1 — one-tap listening |
| Expand-in-place drill-down | Journey 2 — episode-level picking without page nav |
| Tier promotion from tile (long-press → action sheet) | Journey 2 — user-driven priority refinement |
| Explore mode with recommendations + `explore` tag | Journey 3 — caught-up state handling (fast-follow) |
| Subscribe-in-place from Explore | Journey 3 — conversion from recommendation to subscription (fast-follow) |
| Background audio playback + Spotify deep-link | Journey 1 — listening continues when screen locks / in-car |
| Graceful Spotify API + RSS failure handling | All journeys — documented in FR29–31, not surfaced in narrative |
| Driver-friendly visual baseline | Applied universally; Journey 1's in-car step is enabled by it |

---

## Domain-Specific Requirements

### Compliance & Regulatory

- **Spotify Developer Policy:** All use of the Spotify Web API must comply with Spotify's Developer Terms of Service — including display requirements for podcast metadata, restrictions on caching content, and commercial use clauses for the creator recommendation feature. Partnership or extended quota access may be required at scale.
- **App Store Guidelines:** iOS (App Store Review Guidelines §2.5, §4.2) and Android (Google Play Policy) require declared background audio permissions and prohibition of misleading automation claims. The app's background audio behaviour (playback continuing when the screen locks) must be clearly described in app store listings.
- **GDPR (if EU users targeted):** User listening history and interest profile data used for recommendations constitutes personal data under GDPR. Requires explicit consent, a privacy policy, right-to-deletion, and data minimisation principles. Listening history should not be stored longer than necessary for recommendation quality.

### Technical Constraints

- **Background audio execution:** Podcast playback must survive phone lock and incoming calls. iOS requires the `audio` background mode entitlement; Android requires a foreground service of type `mediaPlayback`. Both platforms must keep the ExoPlayer/AVPlayer session alive under OS memory pressure.
- **Spotify playback scope:** The Spotify Web API's `user-read-playback-state` and `user-modify-playback-state` scopes are required for playback control. These scopes require Spotify's quota extension approval for production use beyond 25 users.
- **Rate limits:** Spotify Web API enforces rate limits. Podcast library sync, episode metadata fetch, and playback commands must be batched and cached locally to avoid hitting limits during home-screen open events.
- **RSS reliability:** Podcast RSS feeds are third-party sources of varying quality. The system must handle malformed XML, slow responses, and feed outages gracefully, falling back to cached episode lists when fresh RSS is unavailable.

### Integration Requirements

- **Spotify Web API:** OAuth 2.0 PKCE flow for mobile; scopes: `user-library-read`, `user-read-playback-state`, `user-modify-playback-state`, `user-read-recently-played`, `streaming`
- **Podcast RSS feeds:** Per-podcast RSS URL resolution via the Spotify show metadata, with on-device XML parsing (Kotlin Multiplatform shared parser) to build unlistened episode lists
- **Background audio:** ExoPlayer (Android) / AVPlayer (iOS) for MVP playback fallback when Spotify deep-link is unavailable; Spotify deep-link (`spotify:episode:{id}`) as primary playback handoff

**Deferred (kept for Vision / Drive Mode phase):**
- On-device TTS (`AVSpeechSynthesizer` / Android `TextToSpeech`) — code retained from Epic 3 but not in this version's integration surface
- Bluetooth event detection — code retained from Epic 3 but not in this version's integration surface

### Risk Mitigations

| Risk | Mitigation |
|---|---|
| Spotify revokes API access or changes terms | Build with minimal API surface; monitor Spotify developer changelog; consider Spotify partnership early |
| iOS / Android kills background audio session during long playback | Use proper background audio mode (iOS) + foreground service (Android); state restoration on relaunch |
| Quota limit blocks growth beyond 25 users | Apply for Spotify quota extension at 3-month milestone (aligns with 3-user MVP validation target) |
| GDPR non-compliance exposes liability | Privacy-first architecture: store only episode IDs and tier tags locally on device; no server-side listening profiles in next release |

---

## Innovation & Novel Patterns

### Detected Innovation Areas

**1. Explicit User-Defined Priority Hierarchy Made Visible (Primary Innovation)**
PodDrive replaces algorithmic ranking with explicit user declaration — and, critically, makes that declaration visible at the product's primary surface. LOVED / LIKED / OTHER are not just tags; they are three color-differentiated bands on the home screen, with the freshest episode per show bubbling up within each band. Most media apps assume ML-driven curation is superior to user intent. PodDrive bets on the opposite: users know exactly which podcasts they can't miss, and the app should honour that declaration visually and in the moment. This is a philosophical challenge to the dominant product assumption in streaming media.

**2. Driver-Friendly as a Universal Design Constraint, Not a Mode**
Rather than shipping a separate "drive mode" that users must remember to activate — the dominant pattern in car-aware consumer apps — PodDrive applies driver-safe sizing, contrast, and tap-target rules *universally*. The home screen works equally well on the couch, at a desk, and at a glance in the car. This inverts the usual assumption that car-safe UIs require a separate build; instead, safe-at-a-glance is treated as a baseline quality bar that benefits all contexts.

**3. Platform Bypass Architecture**
PodDrive is built on the explicit insight that Spotify has a financial incentive not to solve this problem. Rather than building a feature request or waiting for Spotify to act, PodDrive uses Spotify's public API (plus podcast RSS feeds) to deliver what Spotify won't. This bypass strategy — building value *through* a platform rather than *with* it — is a validated product pattern (cf. Superhuman on Gmail, Notion on Confluence) but is novel in the podcast space.

**4. Creator-Funded Feature Model (Fast-Follow / Growth)**
The sponsored recommendation slot in Explore mode is not an ad product — it is the product's caught-up-state feature. Creators pay for *relevance access*, not impressions. This inverts the traditional mobile ad model: the ad is valuable to the user (a good recommendation), valuable to the creator (a compatible listener), and funds the free product. This alignment of incentives is genuinely novel in the podcast discovery space.

**5. Context-Triggered Proactive AI (Vision / Drive Mode phase — deferred from next release)**
A future PodDrive release will reintroduce the Bluetooth-triggered AI briefing and hands-free flow (originally Epic 3) as an opt-in companion to the home screen. The "ambient intent detection" pattern — AI acting *before* the user requests anything, triggered by an environmental signal — remains a genuinely novel interaction model for media consumption. The infrastructure is built; the product decision is to validate the home-screen foundation first.

### Market Context & Competitive Landscape

- **Existing podcast apps** (Pocket Casts, Overcast, Castro) solve cross-platform aggregation but require manual app interaction — none offer Bluetooth-triggered briefings or Spotify-native integration.
- **Spotify's own car mode** was deprecated in 2023, leaving a gap for drivers without Android Auto.
- **Smart speaker briefings** (Amazon Alexa Flash Briefing, Google Home routines) validate the appetite for audio-first, context-triggered content delivery — but are limited to specific hardware and don't integrate with Spotify's podcast library.
- **No direct competitor** combines: Spotify-native aggregation with RSS-powered unlistened detection, explicit user-declared priority bands made visible on the home screen, driver-friendly-as-baseline design, and creator-funded discovery. Future phases reintroduce car-specific advantages on this foundation.

### Validation Approach

| Innovation | Validation Method | Success Signal |
|---|---|---|
| User-defined priority made visible | First-session LOVED/LIKED adoption rate | ≥80% of users with ≥5 subscriptions tag at least one LOVED podcast |
| Driver-friendly-as-baseline | Qualitative testing with users driving + using | Users report comfort glancing + tapping while driving without mode-switching |
| Home-screen-is-the-product | Time-to-play from app open | ≤5 seconds median to first audio via quick-play |
| Creator-funded slot (fast-follow) | Manual outreach to 1 creator partner at month 6 | Creator agrees to test; user reports recommendation felt relevant |
| Bypass architecture | Spotify API quota compliance at 3-month milestone | App operates within API limits; no ToS violation |

### Risk Mitigation

| Innovation Risk | Fallback |
|---|---|
| Spotify blocks/limits API access for competitor apps | Build relationship with Spotify early; pivot to multi-platform aggregation if necessary |
| Users don't trust AI recommendation (creator slot) | Make creator identity transparent ("Suggested by [Creator]") — trust through disclosure |
| LOVED/LIKED too simple — users want more tiers | Launch with three tiers (LOVED / LIKED / OTHER) — the third tier absorbs the "subscribed but not urgent" case that users otherwise asked for. Add finer granularity post-MVP only if validation demands it. |

---

## Mobile App Specific Requirements

### Project-Type Overview

PodDrive is a native iOS (Swift) and Android (Kotlin) mobile application. The choice of native development is driven by background audio session management reliability and deep integration with platform media APIs (ExoPlayer / AVPlayer, audio focus / interruption handling). Shared `commonMain` code (Kotlin Multiplatform) handles all business logic, data persistence, and API/RSS integration; platform-specific code is limited to UI (SwiftUI / Jetpack Compose) and media-session plumbing.

### Platform Requirements

| Requirement | iOS | Android |
|---|---|---|
| **Minimum OS** | iOS 16+ | Android 10 (API 29)+ |
| **Language** | Swift 5.9+ | Kotlin 1.9+ |
| **UI Framework** | SwiftUI | Jetpack Compose |
| **Shared business logic** | Kotlin/Native consuming `commonMain` | Kotlin Multiplatform `commonMain` |
| **Background audio** | Background Audio mode (`audio` entitlement) | Foreground Service (type `mediaPlayback`) |
| **Media playback** | AVPlayer + AVAudioSession | ExoPlayer (Media3) + AudioFocusRequest |
| **Distribution** | Apple App Store | Google Play Store |
| **Reserved for Vision (Drive Mode)** | `AVAudioSession` Bluetooth route detection + `AVSpeechSynthesizer` TTS | `BluetoothProfile` receiver + `TextToSpeech` API |

### Device Permissions

| Permission | Purpose | Required / Optional |
|---|---|---|
| Background Audio | Keep playback running when screen is locked | Required |
| Network | Spotify API, RSS feeds, episode audio streaming | Required |
| Notifications | New-LOVED-episode alerts (Growth feature) | Optional |
| Bluetooth | Drive Mode Bluetooth detection (Vision feature; reserved, not requested in this release) | Optional / Not requested in next release |
| Microphone | Voice input for Drive Mode (Vision feature) | Optional / Not requested in next release |
| Siri Integration | "Hey Siri, play my latest podcast via PodDrive" (Growth feature) | Optional |

### Offline Mode

PodDrive's next-release behaviour when Spotify or the network is unavailable:

- **Home screen:** Renders from the locally cached podcast library and last-synced unlistened episode list; stale indicator shown if cache is >24 hours old (per existing Epic 2 Staleness Banner).
- **Tier tags:** Stored entirely on-device; no server dependency; always available.
- **Playback:** Requires network and either Spotify deep-link or direct episode URL; if both unavailable, a tile's quick-play button shows an offline state with retry.
- **RSS sync:** Silently retries on next network recovery; does not block home-screen rendering.
- **Explore mode (fast-follow):** Requires network for fresh recommendations; if unavailable, home screen shows a minimal "caught up" state without Explore recommendations.

### Push Notification Strategy

**Next Release (no push notifications required):**
The home screen is the sole activation surface. No notifications needed for core functionality — the user opens the app and sees what's new.

**Growth (opt-in push notifications):**
- **New LOVED Episode Alert:** Immediate push when a new episode drops from a LOVED podcast — "New Lex Fridman episode: 3h 12min." Tap opens the home screen with the relevant tile in focus.
- **Implementation:** APNs (iOS) / FCM (Android); requires backend notification service (Post-MVP infrastructure).

### Store Compliance

**iOS App Store:**
- Background Modes entitlement: `audio` declared in Info.plist (Bluetooth entitlements reserved for future Drive Mode release)
- Privacy manifest required: any user-facing permission descriptions as added
- App Store category: Entertainment
- Age rating: 4+
- Review note: Background audio playback must be clearly described in App Review notes. The app does not auto-activate on Bluetooth in this release; audio only plays after explicit user tap on the home screen.

**Google Play Store:**
- Foreground Service with `mediaPlayback` type required for background audio
- Bluetooth permissions deferred to future Drive Mode release (not declared in next release's AndroidManifest.xml)
- Play Store category: Music & Audio
- Content rating: Everyone

### Implementation Considerations

- **Spotify SDK vs Web API:** The Spotify iOS/Android SDKs offer richer playback control but add dependency risk. The Web API is more stable for a v1 — use deep-link `spotify:episode:{id}` URIs to hand off playback without requiring SDK integration in MVP.
- **State restoration:** If the app is killed by the OS during playback, on relaunch it should restore the last known playback position silently.

---

## Project Scoping & Phased Development

### MVP Strategy & Philosophy

**MVP Approach:** Problem-Solving MVP — prove the core pain point is eliminated end-to-end. The MVP succeeds when a user can open the app, immediately see what's new from their top-priority podcasts in a glanceable hierarchy, and start listening with a single tap. Everything beyond that is secondary.

**Reuse from prior work:** Epics 1 (Auth), 2 (Library & Tier Management), and the playback plumbing from Epic 4 (4.1, 4.5, 4.6, 4.8–4.11) are already built and form the foundation. MVP scope for the pivot is focused on the home-screen experience itself (Epic 8 Core MVP stories 8.1–8.8).

**MVP Resource Requirements:** 1–2 native mobile developers (Android + iOS), 1 designer (to resolve the deferred UX questions from the brainstorm — tap-target mis-tap, tier-promotion mechanics, freshness-color ramp, expand-in-place animation), 1 product owner. No backend infrastructure required for MVP (all data stored on-device; Spotify API + RSS used directly).

**Fastest path to validated learning:** Ship the three-band home screen with quick-play + expand-in-place first. Continue Listening and Explore mode follow immediately as fast-follows. Briefing and drive-session auto-activation are deferred to a future phase.

---

### MVP Feature Set (Phase 1) — Next Release

**Core User Journeys Supported:**
- Journey 1 (Happy Path): Open app → see three-band sorted list → tap quick-play on top tile → episode plays
- Journey 2 (Drill-down): Open app → tap tile body → expanded view shows unlistened episodes → pick one to play
- Journey 3 (Caught-up): No unlistened episodes → home screen enters Explore mode → tap a recommended podcast

**Must-Have Capabilities:**

| Feature | Justification |
|---|---|
| Spotify OAuth 2.0 (PKCE) login | Without auth, nothing works |
| Podcast library sync via Spotify API + RSS | Foundation for the list |
| LOVED / LIKED / OTHER tagging (on-device storage) | Core prioritisation mechanic |
| Three-band sorted home screen | The product's primary UX surface |
| Per-tile unlistened count with freshness color | Glanceable "what's new" signal |
| Per-tile quick-play button | One-tap listening — the core success metric |
| Expand-in-place drill-down | Full-episode visibility without page navigation |
| Tier promotion from tile | Lets users refine priorities in the moment |
| "Show All" library screen | Full-library management (incl. caught-up shows) |
| Driver-friendly visual baseline | Universal design constraint — safe glance in the car |
| Background playback (ExoPlayer/AVPlayer) | Prevents interruption on screen lock |
| Graceful Spotify API + RSS failure handling | Trust through reliability |

**Fast-Follow (ships immediately after MVP launch):**
- **Continue Listening bar** — conditional bar above the list for in-progress episodes
- **Explore mode** — empty-state surface with recommended podcasts + `explore` tag

**Explicitly OUT of this Release:**
- Bluetooth-triggered AI voice briefing (Epic 3 — code kept as-built, deferred to Vision)
- Auto-advance / episode queue (Epic 4 story 4.2 — retired, code being removed)
- Drive-session lifecycle as a product mode (replaced by driver-friendly-everywhere)
- Creator partnership / sponsored recommendation slot (requires business infrastructure)
- Push notifications (requires backend)
- Lock screen / home screen widget
- Smartwatch integration
- Voice input
- Multi-platform support (Apple Podcasts, YouTube)

---

### Post-MVP Features (Phase 2 — Growth) — After Next Release

| Feature | Dependency |
|---|---|
| Creator partnership recommendation slot (in Explore mode) | Business development + backend infrastructure |
| Lock screen / home screen widget | iOS WidgetKit + Android Glance |
| New LOVED episode push alert | APNs/FCM backend service |
| Smartwatch integration (Apple Watch / Wear OS) | Companion app extensions |
| Voice assistant shortcut ("Hey Siri, play my latest via PodDrive") | Shortcut/intent registration |
| Spotify quota extension application | Triggered at 3-month milestone (pre-1000 users) |

---

### Expansion Features (Phase 3 — Vision) — Future

| Feature | Notes |
|---|---|
| Multi-platform library sources (Apple Podcasts, YouTube, Pocket Casts) | Requires aggregation layer; new API integrations |
| Drive Mode — Bluetooth-triggered AI briefing + hands-free flow | Reintroduce the Epic 3 concept as an opt-in companion to the home screen; Epic 3 code kept as-built as scaffolding |
| Car manufacturer licensing (infotainment SDK) | B2B sales cycle; hardware partnership required |
| CarPlay / Android Auto integration | Steering-wheel navigation of the home-screen list |
| Calendar integration (episode as time block) | Calendar API integration |

---

### Risk Mitigation Strategy

| Risk Type | Risk | Mitigation |
|---|---|---|
| **Technical** | iOS kills background audio session during playback | Use proper background audio mode + foreground service (Android); handle OS kill via state restoration on relaunch |
| **Technical** | Spotify API rate limits hit at scale | Local caching of episode library; batch background sync, not on-demand per Home Screen open |
| **Market** | Spotify quota blocks growth beyond 25 users | Apply for quota extension at 3-month milestone; documented in success criteria |
| **Market** | Spotify deprecates API endpoints used | Minimal API surface; monitor changelog; abstract Spotify calls behind a service layer |
| **Resource** | Solo developer capacity | MVP scope is deliberately narrow; native Android first, iOS second if resources allow |

---

## Functional Requirements

### Authentication & Account

- **FR1:** A user can authenticate with their Spotify account using OAuth 2.0 to grant PodDrive access to their podcast library and playback controls.
- **FR2:** The system can silently refresh the Spotify access token without requiring the user to re-authenticate.
- **FR3:** A user can revoke PodDrive's Spotify access and delete all locally stored data.

### Podcast Library Management

- **FR4:** The system can retrieve all podcasts the user follows on Spotify and their associated unheard episodes.
- **FR5:** A user can tag any followed podcast as LOVED or LIKED.
- **FR6:** A user can change a podcast's tier (LOVED → LIKED, LIKED → LOVED, or remove tier).
- **FR7:** The system can identify which episodes from followed podcasts have not yet been played.
- **FR8:** The system can cache the podcast library locally so episode data is available without a live Spotify connection.
- **FR9:** The system can sync the podcast library in the background and update the cache silently.

### Home Screen Experience

> **Status (2026-04-24):** Added per Sprint Change Proposal. FR36–FR43 are Core MVP (Epic 8, stories 8.1–8.8). FR44, FR45 are Fast-Follow (stories 8.9, 8.10).

- **FR36:** The Home Screen displays a sorted list of subscribed podcasts filtered to those with at least one unlistened episode. Podcasts with zero unlistened episodes do not appear on the Home Screen (they remain accessible via the Show All screen — see FR43).
- **FR37:** Podcasts are grouped into three color-differentiated visual bands — LOVED (top), LIKED (middle), OTHER (bottom). Within each band, podcasts are sorted by the publish date of their latest unlistened episode (newest first).
- **FR38:** Each podcast tile displays a count of unlistened episodes. The count badge uses a freshness-color encoding keyed to the publish date of the newest unlistened episode (bright for today, fading for older). Exact color ramp and breakpoints are a UX decision.
- **FR39:** Each podcast tile exposes a quick-play button. A single tap on the button starts playback of the most recent unlistened episode for that podcast. No smart routing, no priority logic — just the latest unlistened.
- **FR40:** A tap on a podcast tile's body (outside the quick-play button) expands the tile in place to reveal its unlistened episodes, sorted newest-first. A second tap (on the tile body) collapses it. The tap-target boundary between tile-body and quick-play-button must be visually and interactively distinct (UX decision — see Deferred Questions).
- **FR41:** A "Continue Listening" bar appears above the podcast list when an episode is in progress (has a persisted playback position and is not yet finished). A single tap on the bar resumes that episode from its persisted position. *(Fast-Follow — story 8.9)*
- **FR42:** When no subscribed podcast has unlistened episodes, the Home Screen enters Explore mode (see FR26–FR28). *(Fast-Follow — story 8.10)*
- **FR43:** A "Show All" affordance on the Home Screen opens a full library view listing every subscribed podcast (including those with zero unlistened episodes), enabling library-level management (viewing, tier changes, unsubscribe).
- **FR44:** A user can promote or demote a podcast's tier (OTHER → LIKED → LOVED, and inverse) directly from a Home Screen tile via long-press, which opens an action sheet with tier options.
- **FR45:** All Home Screen visual elements — tile size, tap targets, typography, contrast — meet driver-friendly standards (large, high-contrast, low-density) applied universally. There is no separate drive mode; driver-friendliness is a universal design constraint.

### Drive Session Activation (Deferred — Drive Mode / Vision phase)

> **Status (2026-04-24):** Deferred from next release per Sprint Change Proposal. Existing Epic 3 code is retained as-built for the future Drive Mode phase. FR10–FR13 remain authoritative for that phase but are not exercised by the next release.

- **FR10:** The system can detect when the user's phone connects to a Bluetooth audio device (car).
- **FR11:** The system can activate a drive session automatically upon Bluetooth connection without any user action.
- **FR12:** The system can remain active in the background with the phone screen locked during a drive session.
- **FR13:** A user can manually trigger a drive session from within the app when Bluetooth auto-detection is unavailable.

### AI Voice Briefing (Deferred — Drive Mode / Vision phase)

> **Status (2026-04-24):** Deferred from next release per Sprint Change Proposal. The on-device TTS + briefing flow built in Epic 3 is retained as-built. FR14–FR20 remain authoritative for the future Drive Mode phase.

- **FR14:** The system can generate and deliver a spoken briefing of new podcast episodes using on-device text-to-speech at drive session start. The briefing addresses the user by first name, uses a warm and conversational tone, and is designed to feel like a personal assistant — not a system notification.
- **FR15:** The briefing presents LOVED episodes before LIKED episodes.
- **FR16:** The briefing includes episode title, podcast name, and duration for each new episode.
- **FR17:** The briefing adapts its message when no new episodes are available from LOVED podcasts.
- **FR18:** The briefing adapts its message when the inbox is completely empty across all tiers.
- **FR19:** A user can respond to the briefing using a steering wheel button or on-screen tap to confirm playback without navigating the app.
- **FR20:** The system can duck (temporarily lower) audio from other apps (e.g., navigation) during the briefing and restore it afterward.

### Playback Control

- **FR21:** The system can initiate playback of a selected podcast episode through Spotify without requiring the user to open or navigate the Spotify app.
- **FR22:** *(removed — SCP 2026-04-24; priority episode queue retired with pivot to podcast-list home screen)*
- **FR23:** *(removed — SCP 2026-04-24; auto-advance retired with pivot to podcast-list home screen)*
- **FR24:** The system can persist the last known playback position for the most recently played episode and expose it to the Home Screen's Continue Listening surface *(see FR41; fast-follow after MVP)*.
- **FR25:** The system can handle Spotify playback failures gracefully and notify the user via voice without crashing.

### Explore Mode Recommendations (Fast-Follow)

> **Status (2026-04-24):** Explore mode is a fast-follow (after Core MVP) per Sprint Change Proposal. Legacy FR26/FR27/FR28 text was rewritten; the original drive-session empty-inbox + hands-free-accept semantics are superseded.

- **FR26:** When no subscribed podcast has unlistened episodes, the Home Screen enters Explore mode and surfaces recommended podcasts (not single episodes) visually tagged as `explore`. Recommendations are sourced from on-device listening history.
- **FR27:** Explore mode presents a small set of recommended podcasts (target: 3) with the `explore` visual tag — concise, not a firehose. The exact count is a UX design decision to be resolved in Epic 8 story 8.10.
- **FR28:** A user can tap an Explore recommendation's ▶ button to play its most recent episode, or tap the tile to subscribe (defaulting to the OTHER tier). No voice or hands-free interaction is required in next release.

### Resilience & Error Handling

- **FR29:** The system can detect a Spotify API connection failure and inform the user via an in-app status message (e.g., staleness banner, offline tile state) without blocking Home Screen rendering.
- **FR30:** The system can recover from a Spotify API timeout and resume the intended action when connectivity is restored.
- **FR31:** The system can restore its last known state (playback position, current episode) if the app is terminated by the OS during playback and relaunched.
- **FR32:** The system can display a staleness indicator when the cached episode library has not synced within 24 hours.

### Privacy & Data

- **FR33:** A user can view what data PodDrive stores about them.
- **FR34:** All LOVED/LIKED/OTHER tags, playback positions, and listening history used for Explore recommendations are stored on-device only and are not transmitted to any server in the next release.
- **FR35:** A user can delete all locally stored data and reset the app to its initial state.

---

## Non-Functional Requirements

### Performance

- **NFR1:** *(deferred — Drive Mode / Vision)* The drive session briefing must begin playing within 10 seconds of Bluetooth audio device connection detection.
- **NFR2:** Time from Home Screen open (with cached library) to first podcast audio playing via quick-play must not exceed 5 seconds under normal network conditions.
- **NFR3:** Podcast library sync (Spotify subscriptions + RSS episode refresh, background) must complete within 60 seconds on a standard mobile data connection for a library of up to 50 followed podcasts.
- **NFR4:** The app must not increase device battery consumption by more than 5% compared to baseline Spotify usage during a 60-minute listening session.
- **NFR5:** *(deferred — Drive Mode / Vision)* On-device TTS briefing generation must add no more than 2 seconds of delay before audio begins.

### Security

- **NFR6:** Spotify OAuth access tokens and refresh tokens must be stored in the platform's secure credential store (iOS Keychain / Android Keystore) and never in plain-text storage.
- **NFR7:** No user listening data, LOVED/LIKED preferences, or episode history may be transmitted to any external server or third-party service in the MVP.
- **NFR8:** The app must request only the minimum required Spotify API scopes and must not request permissions beyond those listed in the Domain Requirements.

### Reliability

- **NFR9:** The app must not crash during a standard 60-minute listening session under normal conditions (background audio + locked screen + incoming call).
- **NFR10:** *(deferred — Drive Mode / Vision)* The Bluetooth connection trigger must activate the drive session on ≥95% of Bluetooth audio connection events in test conditions.
- **NFR11:** The app must recover and resume normal operation within 10 seconds of the OS terminating the background process, without requiring user intervention.
- **NFR12:** Graceful degradation must be implemented for all Spotify API and RSS fetch failure scenarios — no user-visible crashes; clear in-app status messaging (e.g., staleness indicator, offline tile state) for any failure that prevents playback or library refresh.

### Accessibility

- **NFR13:** *(deferred — Drive Mode / Vision)* All core drive session interactions (start briefing, confirm playback, skip episode) must be completable without looking at or touching the screen — via steering wheel media controls or voice.
- **NFR14:** *(deferred — Drive Mode / Vision)* The AI briefing voice must use a clear, natural-sounding TTS voice at a pace comfortable for driving contexts (target: 150–160 words per minute).
- **NFR15:** All app screens must meet WCAG 2.1 Level AA contrast ratios and tap target size requirements.
- **NFR16:** The app must support dynamic text sizing (iOS Dynamic Type / Android font scale) across all screens.

### Integration

- **NFR17:** The Spotify API integration must handle rate limiting transparently — if rate limits are reached, the app must queue the request and retry without user action or error messages.
- **NFR18:** Spotify deep-link playback handoff must succeed within 3 seconds of the user confirming episode selection, assuming Spotify is installed and running.
- **NFR19:** If the Spotify app is not installed on the device, PodDrive must detect this at onboarding and present a clear, actionable message — not a silent failure.
- **NFR20:** The Spotify token refresh cycle must not interrupt an active listening session — token refresh must occur in the background without pausing audio.

### Home Screen Experience

- **NFR21:** The Home Screen must render (including three-band sort and unlistened counts) within 1 second of app foreground on a device with a library of up to 50 podcasts and cached data. Fresh-sync scenarios may take longer but must show a loading state.
- **NFR22:** Tap targets on Home Screen tiles (tile body and quick-play button) must meet driver-friendly minimums of 48dp × 48dp on Android and 44pt × 44pt on iOS at the smallest supported device class, with clear visual separation between the tile-body tap region and the quick-play-button tap region.
- **NFR23:** Typography on the Home Screen must remain legible at arm's length in a driving context — minimum body text of 16sp/pt, minimum tile title of 18sp/pt — with contrast ratios meeting WCAG 2.1 AA (and preferably AAA) against the band's background color.
- **NFR24:** The three band colors (LOVED / LIKED / OTHER) must be distinguishable by users with common forms of color-vision deficiency; band differentiation must not rely solely on hue (use a combination of hue, luminance, and vertical position).
- **NFR25:** The unlistened-count freshness-color encoding (FR38) must remain legible across the full freshness range and must not rely on color alone to convey urgency — the count itself and its position on the tile must remain unambiguous without color.
