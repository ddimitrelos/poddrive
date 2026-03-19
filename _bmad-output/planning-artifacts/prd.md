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

---

## Executive Summary

PodDrive is a free iOS and Android companion app that solves a dangerous, unaddressed gap in Spotify's podcast experience: the inability to safely discover and play new episodes while driving. Spotify's music-first architecture treats podcasts as passive, browsable content — requiring multi-step manual navigation that is impractical and unsafe at speed. PodDrive bypasses this entirely by acting as a personal podcast assistant, activated automatically when the user's phone connects to the car's Bluetooth.

**Target users:** Frequent drivers who are active podcast listeners using Spotify, without Android Auto or a voice-native car interface.

**The problem:** Finding new episodes from followed podcasts requires navigating to each podcast individually, filtering unheard content, and manually initiating playback — a task that demands visual attention Spotify's car interface cannot safely provide. The problem exists on all surfaces but becomes dangerous in the car.

**The solution:** PodDrive aggregates new episodes from all Spotify-followed podcasts into a single prioritised feed (LOVED / LIKED tiers), triggers a voice AI briefing on Bluetooth connection, and enables zero-screen-interaction playback. When the queue is empty, one interest-matched episode is suggested — no browsing, no menus.

**Revenue model:** Free to users. Podcast creators pay per relevant recommendation delivered in the "empty queue" slot — access to compatible listeners, not impression-based advertising.

**Strategic position:** PodDrive does not compete with or require changes from Spotify. It operates as a layer on top of the Spotify Web API, delivering the feed/inbox mental model Spotify's business model incentivises it to withhold.

### What Makes This Special

**The PA moment:** PodDrive's core differentiator is the Bluetooth-triggered AI briefing — the car connection event becomes the signal to prepare the user's listening session. No app-opening, no navigation, no decision fatigue. The product anticipates the user's intent and acts before they need to ask. This transforms a frustrating multi-tap process into a single, hands-free experience that genuinely feels like a personal assistant.

**Two-tier priority system:** LOVED / LIKED podcast tagging mirrors how users actually relate to content — not everything they follow deserves equal urgency. This simple model enables the briefing to be immediately useful ("2 new episodes from your LOVED podcasts") rather than overwhelming.

**Bypasses the root cause:** Rather than attempting to influence Spotify's product decisions or advocate for a feature Spotify has a business incentive to deprioritise, PodDrive fills the gap independently using existing public APIs. It is buildable today.

## Project Classification

- **Project Type:** Mobile App (iOS & Android)
- **Domain:** Consumer Media & Entertainment
- **Complexity:** Medium — Spotify Web API integration, Bluetooth event handling, background processing, voice UI, lightweight AI recommendation engine, creator marketplace
- **Project Context:** Greenfield — no existing codebase; net-new product

---

## Success Criteria

### User Success

- **The PA moment:** User connects to car Bluetooth → AI briefing plays → episode starts → zero screen touches required. This full flow completes successfully on first attempt without onboarding assistance.
- **Briefing completion rate:** ≥70% of Bluetooth connection events result in the briefing playing to completion (user does not skip or dismiss).
- **Time-to-first-play:** ≤30 seconds from Bluetooth connection to podcast audio playing, with zero required screen interactions.
- **7-day retention:** ≥60% of users who complete the first full briefing-to-playback flow return within 7 days.
- **LOVED/LIKED adoption:** ≥80% of users with ≥5 followed podcasts apply at least one LOVED tag within the first session.

### Business Success

| Horizon | Target | Signal |
|---------|--------|--------|
| 3 months | 3 active users | MVP validated on real devices in real driving conditions |
| 12 months | 1,000 active users | Product-market fit signal; organic growth possible |
| 12 months | 1 creator partner | Business model validated; revenue model proven in principle |

### Technical Success

- Spotify Web API integration handles token refresh, rate limits, and scope permissions reliably with no manual re-authentication required after initial setup.
- Bluetooth connection event reliably triggers the briefing within 10 seconds on both iOS and Android.
- Background audio session survives phone lock, incoming calls, and navigation app audio without requiring app re-launch.
- App does not drain battery detectably (≤5% additional battery draw per 60-minute drive vs. baseline Spotify usage).

### Measurable Outcomes

- A user can go from cold start (first install) to first fully hands-free episode in ≤5 minutes.
- A user with 10 followed podcasts can hear a briefing of all new episodes and select one to play without unlocking their phone.
- Zero critical crashes during a 60-minute drive session in standard conditions (background audio + Bluetooth + locked screen).

---

## Product Scope

### MVP — Minimum Viable Product

The smallest version of PodDrive that delivers the PA moment end-to-end:

- **Spotify OAuth login** — authenticate and authorise podcast library access
- **Podcast Inbox** — aggregated feed of new/unheard episodes from all followed podcasts
- **LOVED / LIKED tagging** — two-tier priority system; LOVED episodes surface first
- **Bluetooth-triggered AI briefing** — voice summary of new episodes by tier on car connection
- **One-tap / zero-touch playback** — deep-links into Spotify playback; no manual navigation required
- **Episode Continuity Engine** — auto-queues next unheard episode when current one ends
- **Interest-based safety net** — one relevant episode suggestion when inbox is empty; no menu

### Growth Features (Post-MVP)

- **Creator partnership slot** — sponsored recommendation in the empty-queue fallback, relevance-gated
- **Lock screen / home screen widget** — "X new episodes" with one-tap Play, no app open required
- **Morning briefing notification** — daily digest at user-set time with one-tap Play action
- **Smartwatch integration** — glanceable new episode count; tap to queue before driving
- **Voice assistant deep integration** — "Hey Siri / Google, play my new podcasts" via PodDrive

### Vision (Future)

- **Multi-platform support** — aggregate across Apple Podcasts, YouTube, Pocket Casts alongside Spotify
- **Car manufacturer licensing** — embed the briefing layer natively in infotainment systems (B2B)
- **Full audio-only mode** — steering wheel button navigation; screen entirely optional
- **Calendar integration** — podcast episodes surfaced as time-blocked calendar events

---

## User Journeys

### Persona: Dimitrios, The Commuter

**Name:** Dimitrios, 38, Senior Consultant
**Situation:** Drives 45 minutes each way to the office, 4 days a week. Follows 14 podcasts on Spotify — 3 he considers essential (LOVED), the rest he listens to when he has time. He used to look forward to his commute as "podcast time" but the frustration of hunting for new episodes while driving has made him give up and default to music instead.
**Goal:** Never miss a new episode from his essential podcasts, and fill every commute with content he actually wants to hear.
**Obstacle:** Spotify requires him to navigate to each podcast individually, filter unheard episodes, and tap to play — all while driving. He's nearly rear-ended someone doing this.
**Solution:** PodDrive knows what he wants before he asks.

---

### Journey 1 — The Happy Path: Monday Morning Commute

**Opening Scene — The Driveway**
It's 8:15am. Dimitrios gets into his car, tosses his phone on the mount, and starts the engine. His phone connects to the car Bluetooth. Before he's even buckled in, PodDrive activates in the background.

A calm AI voice speaks through the car speakers:
*"Good morning, Dimitrios. You have 2 new episodes from your LOVED podcasts — The Lex Fridman Podcast, 3 hours 12 minutes, and Acquired, 2 hours 4 minutes. You also have 7 new episodes from your LIKED podcasts. Want me to start with Lex Fridman?"*

Dimitrios says "Yes" — or simply taps the steering wheel's play button.

**Rising Action — The Drive**
Lex Fridman plays immediately through Spotify. Dimitrios pulls out of the driveway, eyes on the road, hands on the wheel. Forty minutes later, the episode hasn't finished. He arrives at the office, parks, and pauses.

On the way home that evening, Bluetooth reconnects. PodDrive picks up exactly where he left off — no briefing needed, just a soft notification: *"Resuming Lex Fridman from where you left off."*

**Climax — The Commute That Changes Things**
Three weeks in, Dimitrios realises he hasn't once touched his phone while driving to manage audio. The commute has become genuinely enjoyable again. He mentions it to a colleague: *"It's like having a PA who's already queued up everything I want to hear."*

**Resolution — The New Normal**
Dimitrios's LOVED list now has 4 podcasts. He's started using his morning commute to stay on top of his essential listening and his evening commute to work through LIKED podcasts when he feels like it. He hasn't missed a new Lex Fridman episode in two months.

---

### Journey 2 — Edge Case: Empty Inbox & Mid-Drive Episode End

**Opening Scene — A Friday Afternoon**
Dimitrios gets in the car for the drive home. Bluetooth connects. PodDrive briefs him:
*"Hi Dimitrios. No new episodes from your LOVED podcasts today. You have 1 new episode from your LIKED podcasts — How I Built This, 58 minutes. Or I can suggest something based on what you usually enjoy. What would you like?"*

He says "suggest something." PodDrive responds:
*"Based on your listening history, I think you'd enjoy a recent episode of The Tim Ferriss Show — 'Lessons from 1,000 Episodes', 1 hour 22 minutes. Want me to play it?"*

One confident suggestion. No list. No scrolling.

**Rising Action — Mid-Drive Silence**
Halfway home, How I Built This ends. Silence. Dimitrios hasn't told PodDrive what to do next. Rather than leaving him in silence or autoplaying something random, PodDrive gently queues the next newest LIKED episode and announces:
*"Starting 'Hidden Brain — How Your Brain Decides', 52 minutes."*

**Climax — The Failure Scenario (and Recovery)**
One morning, Bluetooth connects but Spotify is slow to respond. PodDrive detects the delay and says:
*"Having trouble connecting to Spotify — I'll retry in a moment."*
Ten seconds later: *"Ready. Starting your LOVED episodes."*
If Spotify fails entirely, PodDrive says: *"Spotify isn't responding right now. I've queued your episodes for when it reconnects. Safe drive."*

No crash. No frozen screen. Graceful degradation with a clear status message.

**Resolution — Trust Built Through Reliability**
Dimitrios has now experienced PodDrive handling a delayed connection, an empty inbox, and a mid-drive episode transition — all without touching his phone. The edge cases didn't break the PA feeling. They reinforced it.

---

### Journey Requirements Summary

| Capability | Revealed By |
|---|---|
| Spotify OAuth + followed podcast fetch | Both journeys — app is useless without authenticated library access |
| New/unheard episode detection | Happy path — briefing depends on accurate "what's new" logic |
| LOVED / LIKED tagging & persistence | Happy path — tier-based briefing order |
| Bluetooth connection event trigger | Both journeys — the entire activation model depends on this |
| AI voice briefing (text-to-speech) | Both journeys — core PA interaction pattern |
| Spotify deep-link / playback handoff | Both journeys — zero-touch play requirement |
| Episode Continuity Engine | Edge case — auto-queue when episode ends mid-drive |
| Interest-based recommendation | Edge case — empty inbox fallback |
| Graceful Spotify API failure handling | Edge case — connection delay / API timeout recovery |
| Resume playback state | Happy path — picks up where user left off on reconnect |

---

## Domain-Specific Requirements

### Compliance & Regulatory

- **Spotify Developer Policy:** All use of the Spotify Web API must comply with Spotify's Developer Terms of Service — including display requirements for podcast metadata, restrictions on caching content, and commercial use clauses for the creator recommendation feature. Partnership or extended quota access may be required at scale.
- **App Store Guidelines:** iOS (App Store Review Guidelines §2.5, §4.2) and Android (Google Play Policy) require declared background audio permissions, Bluetooth usage justification, and prohibition of misleading automation claims. The AI briefing feature must be clearly described in app store listings.
- **GDPR (if EU users targeted):** User listening history and interest profile data used for recommendations constitutes personal data under GDPR. Requires explicit consent, a privacy policy, right-to-deletion, and data minimisation principles. Listening history should not be stored longer than necessary for recommendation quality.

### Technical Constraints

- **iOS background execution:** The Bluetooth connection trigger relies on Core Bluetooth or audio session APIs. iOS limits background execution strictly — the briefing trigger must use an approved background mode (audio, background fetch, or push notification) to survive phone lock.
- **Spotify playback scope:** The Spotify Web API's `user-read-playback-state` and `user-modify-playback-state` scopes are required for episode queuing and playback control. These scopes require Spotify's quota extension approval for production use beyond 25 users.
- **Rate limits:** Spotify Web API enforces rate limits. Episode fetch, library sync, and playback commands must be batched and cached locally to avoid hitting limits during the Bluetooth trigger window.

### Integration Requirements

- **Spotify Web API:** OAuth 2.0 PKCE flow for mobile; scopes: `user-library-read`, `user-read-playback-state`, `user-modify-playback-state`, `user-read-recently-played`, `streaming`
- **Text-to-speech engine:** On-device TTS (iOS `AVSpeechSynthesizer` / Android `TextToSpeech`) for the AI briefing — avoids external API dependency and works offline
- **Bluetooth event detection:** iOS `CBCentralManager` or `AVAudioSession` route change notifications; Android `BluetoothProfile` broadcast receiver

### Risk Mitigations

| Risk | Mitigation |
|---|---|
| Spotify revokes API access or changes terms | Build with minimal API surface; monitor Spotify developer changelog; consider Spotify partnership early |
| iOS kills background process before briefing starts | Use audio session activation as the trigger — audio apps get background priority |
| Quota limit blocks growth beyond 25 users | Apply for Spotify quota extension at 3-month milestone (aligns with 3-user MVP validation target) |
| GDPR non-compliance exposes liability | Privacy-first architecture: store only episode IDs and tier tags locally on device; no server-side listening profiles in MVP |

---

## Innovation & Novel Patterns

### Detected Innovation Areas

**1. Context-Triggered Proactive AI (Primary Innovation)**
PodDrive introduces a new mobile interaction paradigm: the AI acts *before* the user requests anything, triggered by an environmental signal (Bluetooth car connection) rather than an explicit in-app action. Current AI assistants (Siri, Google Assistant) require invocation. PodDrive's briefing requires nothing — it listens for the context and acts. This "ambient intent detection" pattern is rare in consumer mobile apps and represents a genuinely new interaction model for media consumption.

**2. Platform Bypass Architecture**
PodDrive is built on the explicit insight that Spotify has a financial incentive not to solve this problem. Rather than building a feature request or waiting for Spotify to act, PodDrive uses Spotify's public API to deliver what Spotify won't. This bypass strategy — building value *through* a platform rather than *with* it — is a validated product pattern (cf. Superhuman on Gmail, Notion on Confluence) but is novel in the podcast space.

**3. Creator-Funded Feature Model**
The sponsored recommendation slot is not an ad product — it is the product's empty-inbox fallback feature. Creators pay for *relevance access*, not impressions. This inverts the traditional mobile ad model: the ad is valuable to the user (a good recommendation), valuable to the creator (a compatible listener), and funds the free product. This alignment of incentives is genuinely novel in the podcast discovery space.

**4. Explicit User-Defined Priority Hierarchy**
LOVED/LIKED replaces algorithmic ranking with explicit user declaration. Most media apps assume ML-driven curation is superior to user intent. PodDrive bets on the opposite: users know exactly which podcasts they can't miss, and the app should honour that declaration without second-guessing it. This is a philosophical challenge to the dominant product assumption in streaming media.

### Market Context & Competitive Landscape

- **Existing podcast apps** (Pocket Casts, Overcast, Castro) solve cross-platform aggregation but require manual app interaction — none offer Bluetooth-triggered briefings or Spotify-native integration.
- **Spotify's own car mode** was deprecated in 2023, leaving a gap for drivers without Android Auto.
- **Smart speaker briefings** (Amazon Alexa Flash Briefing, Google Home routines) validate the appetite for audio-first, context-triggered content delivery — but are limited to specific hardware and don't integrate with Spotify's podcast library.
- **No direct competitor** combines: Spotify-native, Bluetooth-triggered, voice-briefing, LOVED/LIKED priority, and creator-funded recommendations.

### Validation Approach

| Innovation | Validation Method | Success Signal |
|---|---|---|
| Context-triggered briefing | 3-user MVP on real Bluetooth connections | Briefing plays on ≥90% of connection events without manual trigger |
| User-defined priority | LOVED/LIKED adoption rate in first session | ≥80% of users tag at least one LOVED podcast |
| Creator-funded slot | Manual outreach to 1 creator partner in month 6 | Creator agrees to test the model; user reports recommendation felt relevant |
| Bypass architecture | Spotify API quota compliance at 3-month milestone | App operates within API limits; no ToS violation |

### Risk Mitigation

| Innovation Risk | Fallback |
|---|---|
| Spotify blocks/limits API access for competitor apps | Build relationship with Spotify early; pivot to multi-platform aggregation if necessary |
| Users don't trust AI recommendation (creator slot) | Make creator identity transparent ("Suggested by [Creator]") — trust through disclosure |
| LOVED/LIKED too simple — users want more tiers | Launch with two tiers; add "queued for later" third tier in Growth phase based on feedback |

---

## Mobile App Specific Requirements

### Project-Type Overview

PodDrive is a native iOS (Swift) and Android (Kotlin) mobile application. The choice of native development is non-negotiable for this product: the Bluetooth connection trigger, background audio session management, and on-device TTS reliability all require deep platform API access that cross-platform frameworks cannot guarantee at the quality level the PA experience demands.

### Platform Requirements

| Requirement | iOS | Android |
|---|---|---|
| **Minimum OS** | iOS 16+ | Android 10 (API 29)+ |
| **Language** | Swift 5.9+ | Kotlin 1.9+ |
| **UI Framework** | SwiftUI (app UI) + AVFoundation (audio) | Jetpack Compose (app UI) + MediaSession (audio) |
| **Background execution** | Background Audio mode + Background App Refresh | Foreground Service (audio type) |
| **Bluetooth** | `AVAudioSession` route change notifications + `CoreBluetooth` | `BluetoothProfile` broadcast receiver + `AudioManager` |
| **TTS** | `AVSpeechSynthesizer` | `TextToSpeech` API |
| **Distribution** | Apple App Store | Google Play Store |

### Device Permissions

| Permission | Purpose | Required / Optional |
|---|---|---|
| Bluetooth | Detect car connection event to trigger briefing | Required |
| Microphone | Voice response to briefing ("Yes, play it") | Optional (MVP: button fallback) |
| Background Audio | Keep briefing and playback running when screen is locked | Required |
| Notifications | Morning briefing digest (Growth feature) | Optional |
| Siri Integration | "Hey Siri, play my podcasts via PodDrive" (Growth feature) | Optional |

### Offline Mode

PodDrive's MVP behaviour when Spotify is unavailable:

- **Episode list:** Cached locally on last successful sync; stale indicator shown if >24 hours old
- **Briefing:** Generates from cached episode data — briefing still plays even without live Spotify connection
- **Playback:** Requires Spotify to be available; if unavailable, briefing ends with: *"Spotify isn't responding right now. I've queued your episodes for when it reconnects."*
- **LOVED/LIKED tags:** Stored entirely on-device; no server dependency
- **Sync:** Episode library re-syncs silently on next successful Spotify API connection

### Push Notification Strategy

**MVP (no push notifications required):**
The Bluetooth connection event is the sole trigger. No notifications needed for core functionality.

**Growth (opt-in push notifications):**
- **Morning Briefing Digest:** Daily notification at user-set time — "3 new episodes waiting. Tap to queue." One-tap Play action directly from notification tray.
- **New LOVED Episode Alert:** Immediate push when a new episode drops from a LOVED podcast — "New Lex Fridman episode: 3h 12min. Play now?"
- **Implementation:** APNs (iOS) / FCM (Android); requires backend notification service (Post-MVP infrastructure)

### Store Compliance

**iOS App Store:**
- Background Modes entitlement: `audio`, `bluetooth-central` declared in Info.plist
- Privacy manifest required: Bluetooth usage description, microphone usage description (if voice input added)
- App Store category: Entertainment
- Age rating: 4+
- Review note: Background audio activation on Bluetooth connection must be clearly described in App Review notes to avoid rejection for "automated behaviour"

**Google Play Store:**
- `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions declared in AndroidManifest.xml
- Foreground Service with `mediaPlayback` type required for background audio
- Play Store category: Music & Audio
- Content rating: Everyone

### Implementation Considerations

- **Audio session priority:** PodDrive must duck (lower volume of) navigation app audio during the briefing, then restore it. Both platforms support audio ducking — implement via `AVAudioSession.setCategory(.playback, options: .duckOthers)` on iOS and `AudioFocusRequest` with `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` on Android.
- **Spotify SDK vs Web API:** The Spotify iOS/Android SDKs offer richer playback control but add dependency risk. The Web API is more stable for a v1 — use deep-link `spotify:episode:{id}` URIs to hand off playback without requiring SDK integration in MVP.
- **State restoration:** If the app is killed by the OS mid-drive, on relaunch it should restore the last known playback position and queue silently.

---

## Project Scoping & Phased Development

### MVP Strategy & Philosophy

**MVP Approach:** Problem-Solving MVP — prove the core pain point is eliminated end-to-end. The MVP succeeds when a driver can connect to their car Bluetooth, hear a briefing of their new podcast episodes, and start listening without touching their phone. Nothing more is required to validate the product's reason to exist.

**MVP Resource Requirements:** 1–2 native mobile developers (Android + iOS), 1 designer (optional for MVP — functional UI is sufficient), 1 product owner. No backend infrastructure required for MVP (all data stored on-device; Spotify API used directly).

**Fastest path to validated learning:** Build the Bluetooth trigger → briefing → Spotify deep-link flow first. Everything else is secondary.

---

### MVP Feature Set (Phase 1) — Target: Month 0–3

**Core User Journeys Supported:**
- Journey 1 (Happy Path): Bluetooth connect → briefing → hands-free playback
- Journey 2 (Edge Case): Empty inbox → single recommendation; mid-drive episode continuation

**Must-Have Capabilities:**

| Feature | Justification |
|---|---|
| Spotify OAuth 2.0 (PKCE) login | Without auth, nothing works |
| Podcast library sync (followed podcasts + new episodes) | Foundation for all features |
| LOVED / LIKED tagging (on-device storage) | Core prioritisation mechanic |
| Bluetooth connection event detection | The entire activation model |
| AI voice briefing (on-device TTS) | The PA moment — product's reason to exist |
| Episode queue: LOVED first, then LIKED | Briefing must respect priority hierarchy |
| Spotify deep-link playback handoff | Zero-touch play — core success criterion |
| Episode Continuity Engine | Prevents silence mid-drive |
| Interest-based safety net (basic heuristic v1) | Empty inbox fallback — single suggestion |
| Graceful Spotify API failure handling | Trust through reliability |
| Resume playback state on reconnect | Seamless multi-trip experience |

**Explicitly OUT of MVP:**
- Creator partnership / sponsored recommendation slot (requires business infrastructure)
- Push notifications (requires backend)
- Lock screen / home screen widget
- Smartwatch integration
- Voice input ("Yes, play it") — steering wheel button or screen tap as fallback
- Multi-platform support (Apple Podcasts, YouTube)

---

### Post-MVP Features (Phase 2 — Growth) — Target: Month 4–9

| Feature | Dependency |
|---|---|
| Creator partnership recommendation slot | Business development + backend infrastructure |
| Lock screen / home screen widget | iOS WidgetKit + Android Glance |
| Morning briefing push notification | APNs/FCM backend service |
| New LOVED episode push alert | Same backend service as morning briefing |
| Voice input ("Yes / No / Skip") | Microphone permission + speech recognition |
| Smartwatch integration (Apple Watch / Wear OS) | Companion app extensions |
| Spotify quota extension application | Triggered at 3-month milestone (pre-1000 users) |

---

### Expansion Features (Phase 3 — Vision) — Target: Month 10+

| Feature | Notes |
|---|---|
| Multi-platform support (Apple Podcasts, YouTube) | Requires aggregation layer; new API integrations |
| Car manufacturer licensing (infotainment SDK) | B2B sales cycle; hardware partnership required |
| Full audio-only driving mode (steering wheel buttons) | CarPlay / Android Auto integration |
| "Hey Siri / Google, play my podcasts via PodDrive" | Deep voice assistant integration |
| Calendar integration (episode as time block) | Calendar API integration |

---

### Risk Mitigation Strategy

| Risk Type | Risk | Mitigation |
|---|---|---|
| **Technical** | iOS kills background process before briefing | Audio session activation as trigger; file App Review note explaining behaviour |
| **Technical** | Spotify API rate limits hit at scale | Local caching of episode library; batch sync on Bluetooth connect, not on demand |
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

### Drive Session Activation

- **FR10:** The system can detect when the user's phone connects to a Bluetooth audio device (car).
- **FR11:** The system can activate a drive session automatically upon Bluetooth connection without any user action.
- **FR12:** The system can remain active in the background with the phone screen locked during a drive session.
- **FR13:** A user can manually trigger a drive session from within the app when Bluetooth auto-detection is unavailable.

### AI Voice Briefing

- **FR14:** The system can generate and deliver a spoken briefing of new podcast episodes using on-device text-to-speech at drive session start. The briefing addresses the user by first name, uses a warm and conversational tone, and is designed to feel like a personal assistant — not a system notification.
- **FR15:** The briefing presents LOVED episodes before LIKED episodes.
- **FR16:** The briefing includes episode title, podcast name, and duration for each new episode.
- **FR17:** The briefing adapts its message when no new episodes are available from LOVED podcasts.
- **FR18:** The briefing adapts its message when the inbox is completely empty across all tiers.
- **FR19:** A user can respond to the briefing using a steering wheel button or on-screen tap to confirm playback without navigating the app.
- **FR20:** The system can duck (temporarily lower) audio from other apps (e.g., navigation) during the briefing and restore it afterward.

### Playback Control

- **FR21:** The system can initiate playback of a selected podcast episode through Spotify without requiring the user to open or navigate the Spotify app.
- **FR22:** The system can queue multiple episodes for sequential playback in priority order (LOVED first, then LIKED).
- **FR23:** When an episode ends, the system can automatically begin playing the next unheard episode from the queue.
- **FR24:** The system can resume playback from the last known position when a drive session reconnects to the same episode.
- **FR25:** The system can handle Spotify playback failures gracefully and notify the user via voice without crashing.

### Recommendation & Discovery

- **FR26:** When a user's episode inbox is empty, the system can suggest a single episode from outside the user's subscriptions based on their listening history and interests.
- **FR27:** The system presents one recommendation — not a list — when the inbox is empty.
- **FR28:** A user can accept or decline the suggested episode without touching the screen.

### Resilience & Error Handling

- **FR29:** The system can detect a Spotify API connection failure and inform the user via voice during the briefing.
- **FR30:** The system can recover from a Spotify API timeout and resume the intended action when connectivity is restored.
- **FR31:** The system can restore its last known state (queue, playback position, session context) if the app is terminated by the OS mid-drive and relaunched.
- **FR32:** The system can display a staleness indicator when the cached episode library has not synced within 24 hours.

### Privacy & Data

- **FR33:** A user can view what data PodDrive stores about them.
- **FR34:** All LOVED/LIKED tags and episode history used for recommendations are stored on-device only and are not transmitted to any server in the MVP.
- **FR35:** A user can delete all locally stored data and reset the app to its initial state.

---

## Non-Functional Requirements

### Performance

- **NFR1:** The drive session briefing must begin playing within 10 seconds of Bluetooth audio device connection detection.
- **NFR2:** Time from Bluetooth connection to first podcast audio playing must not exceed 30 seconds under normal network conditions.
- **NFR3:** Podcast library sync (background) must complete within 60 seconds on a standard mobile data connection for a library of up to 50 followed podcasts.
- **NFR4:** The app must not increase device battery consumption by more than 5% compared to baseline Spotify usage during a 60-minute drive session.
- **NFR5:** On-device TTS briefing generation must add no more than 2 seconds of delay before audio begins.

### Security

- **NFR6:** Spotify OAuth access tokens and refresh tokens must be stored in the platform's secure credential store (iOS Keychain / Android Keystore) and never in plain-text storage.
- **NFR7:** No user listening data, LOVED/LIKED preferences, or episode history may be transmitted to any external server or third-party service in the MVP.
- **NFR8:** The app must request only the minimum required Spotify API scopes and must not request permissions beyond those listed in the Domain Requirements.

### Reliability

- **NFR9:** The app must not crash during a standard 60-minute drive session under normal conditions (background audio + Bluetooth + locked screen + incoming call).
- **NFR10:** The Bluetooth connection trigger must activate the drive session on ≥95% of Bluetooth audio connection events in test conditions.
- **NFR11:** The app must recover and resume normal operation within 10 seconds of the OS terminating the background process, without requiring user intervention.
- **NFR12:** Graceful degradation must be implemented for all Spotify API failure scenarios — no user-visible crashes; voice notification required for any failure that prevents playback.

### Accessibility

- **NFR13:** All core drive session interactions (start briefing, confirm playback, skip episode) must be completable without looking at or touching the screen — via steering wheel media controls or voice.
- **NFR14:** The AI briefing voice must use a clear, natural-sounding TTS voice at a pace comfortable for driving contexts (target: 150–160 words per minute).
- **NFR15:** All non-drive-mode app screens (settings, LOVED/LIKED tagging, onboarding) must meet WCAG 2.1 Level AA contrast and tap target size requirements.
- **NFR16:** The app must support dynamic text sizing (iOS Dynamic Type / Android font scale) for all non-briefing screens.

### Integration

- **NFR17:** The Spotify API integration must handle rate limiting transparently — if rate limits are reached, the app must queue the request and retry without user action or error messages.
- **NFR18:** Spotify deep-link playback handoff must succeed within 3 seconds of the user confirming episode selection, assuming Spotify is installed and running.
- **NFR19:** If the Spotify app is not installed on the device, PodDrive must detect this at onboarding and present a clear, actionable message — not a silent failure.
- **NFR20:** The Spotify token refresh cycle must not interrupt an active drive session — token refresh must occur in the background without pausing audio or the briefing.
