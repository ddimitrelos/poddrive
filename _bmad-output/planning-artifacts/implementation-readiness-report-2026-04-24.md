---
stepsCompleted: ['step-01-document-discovery', 'step-02-prd-analysis', 'step-03-epic-coverage-validation', 'step-04-ux-alignment', 'step-05-epic-quality-review', 'step-06-final-assessment']
lastStep: 6
status: complete
workflow: bmad-check-implementation-readiness
date: 2026-04-24
project_name: BMAD Test
user_name: Dimitrios.dimitrelos
context: Post-pivot readiness audit — follows SCP 2026-04-24 (podcast-list home screen pivot); architecture.md revised 2026-04-24; PRD revised 2026-04-24; epics.md and ux-design-specification.md pivot status pending
inputDocuments:
  - _bmad-output/planning-artifacts/prd.md
  - _bmad-output/planning-artifacts/architecture.md
  - _bmad-output/planning-artifacts/epics.md
  - _bmad-output/planning-artifacts/ux-design-specification.md
  - _bmad-output/planning-artifacts/sprint-change-proposal-2026-04-24.md
  - _bmad-output/implementation-artifacts/sprint-status.yaml
---

# Implementation Readiness Assessment Report

**Date:** 2026-04-24
**Project:** BMAD Test (PodDrive)
**Trigger:** Post-pivot audit after Sprint Change Proposal 2026-04-24 (podcast-list-centric home screen pivot)
**Prior report:** [implementation-readiness-report-2026-03-19.md](implementation-readiness-report-2026-03-19.md) — superseded by this run

---

## Step 1 — Document Discovery

### Inventory (`_bmad-output/planning-artifacts/`)

**Whole Documents (no sharded variants found):**

| Type | File | Size | Last Modified | Pivot-status |
|---|---|---|---|---|
| PRD | `prd.md` | 49,013 B | 2026-04-24 19:40 | ✅ Revised for pivot (FR36–FR45 added; FR10–FR20 deferred) |
| Architecture | `architecture.md` | 67,291 B | 2026-04-24 20:20 | ✅ Revised for pivot (this session) |
| Epics | `epics.md` | 49,219 B | **2026-03-19** | ⚠️ **Not yet updated** — pre-pivot state |
| UX Spec | `ux-design-specification.md` | 35,884 B | **2026-03-19** | ⚠️ **Not yet updated** — pre-pivot state (SCP §4.4 handoff) |
| UX Directions | `ux-design-directions.html` | 43,746 B | 2026-03-19 | Supporting material; pre-pivot |
| SCP | `sprint-change-proposal-2026-04-24.md` | 22,824 B | 2026-04-24 | ✅ Authoritative change source |
| DoD | `definition-of-done.md` | 3,245 B | 2026-03-19 | Governance doc; pivot-neutral |
| Prior IR report | `implementation-readiness-report-2026-03-19.md` | 21,760 B | 2026-03-19 | Historical — superseded |

**Sharded Documents:** none found. All document types are single-file.

### Supporting implementation artifacts (`_bmad-output/implementation-artifacts/`)

- `sprint-status.yaml` — to be read in Step 2+
- 22 story spec files across Epics 1–4 (including retired `4-2-priority-episode-queue.md`, `4-3-auto-advance-to-next-episode.md`)
- **Epic 8 story files:** none present — expected; Epic 8 story creation is downstream of this readiness check (SCP §5 handoff)

### Duplicates

None. No document exists as both whole and sharded form.

### Missing / Stale

- ⚠️ **`epics.md` stale** (2026-03-19): pre-pivot. SCP §4.3 requires Epic 8 to be introduced and Epics 3, 4, 5 to be rescoped in this document. User's own brief stated *"Sprint status and epics are already updated"* — this file's mtime contradicts that claim. Step 2+ analysis will confirm whether Epic 8 content is present despite the stale mtime, or whether `epics.md` is genuinely out of date.
- ⚠️ **`ux-design-specification.md` stale** (2026-03-19): pre-pivot. SCP §4.4 lists 7 new UX specifications required before Epic 8 story creation can complete. This is expected — UX revision is the next SCP-sequenced handoff — but it bounds what this readiness check can validate.

### Assessment scope note

Because UX and epics are partially pre-pivot, this readiness check will necessarily be a **partial-readiness audit**: it will verify PRD ↔ Architecture alignment (both revised), verify Epic 8 presence/absence in epics.md, and flag the UX spec as a known blocker for final pre-implementation readiness rather than fail the run on its absence.

---

## Step 2 — PRD Analysis

**Source:** `prd.md` (49 KB, revised 2026-04-24 per Sprint Change Proposal).

### Functional Requirements Extracted

**Authentication & Account**
- **FR1:** A user can authenticate with their Spotify account using OAuth 2.0 to grant PodDrive access to their podcast library and playback controls.
- **FR2:** The system can silently refresh the Spotify access token without requiring the user to re-authenticate.
- **FR3:** A user can revoke PodDrive's Spotify access and delete all locally stored data.

**Podcast Library Management**
- **FR4:** The system can retrieve all podcasts the user follows on Spotify and their associated unheard episodes.
- **FR5:** A user can tag any followed podcast as LOVED or LIKED.
- **FR6:** A user can change a podcast's tier (LOVED → LIKED, LIKED → LOVED, or remove tier).
- **FR7:** The system can identify which episodes from followed podcasts have not yet been played.
- **FR8:** The system can cache the podcast library locally so episode data is available without a live Spotify connection.
- **FR9:** The system can sync the podcast library in the background and update the cache silently.

**Home Screen Experience** *(added per SCP 2026-04-24 — Core MVP except where flagged Fast-Follow)*
- **FR36:** The Home Screen displays a sorted list of subscribed podcasts filtered to those with at least one unlistened episode. Podcasts with zero unlistened episodes do not appear on the Home Screen (accessible via Show All — FR43).
- **FR37:** Podcasts are grouped into three color-differentiated visual bands — LOVED (top), LIKED (middle), OTHER (bottom). Within each band, podcasts are sorted by the publish date of their latest unlistened episode (newest first).
- **FR38:** Each podcast tile displays a count of unlistened episodes. The count badge uses a freshness-color encoding keyed to the publish date of the newest unlistened episode (bright for today, fading for older). Exact color ramp and breakpoints are a UX decision.
- **FR39:** Each podcast tile exposes a quick-play button. A single tap on the button starts playback of the most recent unlistened episode. No smart routing — just the latest unlistened.
- **FR40:** A tap on a podcast tile's body (outside the quick-play button) expands the tile in place to reveal unlistened episodes, sorted newest-first; second tap collapses. Tap-target boundary between tile-body and quick-play-button must be visually and interactively distinct (UX decision).
- **FR41:** *(Fast-Follow — story 8.9)* A "Continue Listening" bar appears above the podcast list when an episode is in progress; single tap resumes from the persisted position.
- **FR42:** *(Fast-Follow — story 8.10)* When no subscribed podcast has unlistened episodes, the Home Screen enters Explore mode (see FR26–FR28).
- **FR43:** A "Show All" affordance on the Home Screen opens a full library view listing every subscribed podcast (including zero-unlistened), enabling library-level management.
- **FR44:** A user can promote/demote a podcast's tier (OTHER → LIKED → LOVED, and inverse) directly from a Home Screen tile via long-press, which opens an action sheet.
- **FR45:** All Home Screen visual elements — tile size, tap targets, typography, contrast — meet driver-friendly standards (large, high-contrast, low-density) applied universally. No separate drive mode.

**Drive Session Activation** *(DEFERRED — Drive Mode / Vision phase; code retained as-built)*
- **FR10:** Detect phone connection to a Bluetooth audio device (car).
- **FR11:** Activate a drive session automatically upon Bluetooth connection without user action.
- **FR12:** Remain active in background with phone screen locked during a drive session.
- **FR13:** User can manually trigger a drive session when Bluetooth auto-detection is unavailable.

**AI Voice Briefing** *(DEFERRED — Drive Mode / Vision phase; code retained as-built)*
- **FR14:** Generate + deliver spoken briefing via on-device TTS at drive session start; first-name address, warm tone.
- **FR15:** Briefing presents LOVED episodes before LIKED.
- **FR16:** Briefing includes episode title, podcast name, duration.
- **FR17:** Adapt message when no LOVED episodes available.
- **FR18:** Adapt message when inbox completely empty across all tiers.
- **FR19:** User can confirm playback via steering wheel button or on-screen tap.
- **FR20:** Duck other-app audio during briefing, restore after.

**Playback Control**
- **FR21:** The system can initiate playback of a selected podcast episode through Spotify without requiring the user to open/navigate the Spotify app.
- **FR22:** *(REMOVED — SCP 2026-04-24; priority episode queue retired)*
- **FR23:** *(REMOVED — SCP 2026-04-24; auto-advance retired)*
- **FR24:** The system can persist the last known playback position for the most recently played episode and expose it to the Home Screen's Continue Listening surface (see FR41).
- **FR25:** The system can handle Spotify playback failures gracefully and notify the user via voice without crashing.

**Explore Mode Recommendations** *(Fast-Follow — Epic 8 story 8.10; reframed per SCP)*
- **FR26:** When no subscribed podcast has unlistened episodes, the Home Screen enters Explore mode and surfaces recommended podcasts (not single episodes) visually tagged `explore`. Source: on-device listening history.
- **FR27:** Explore mode presents a small set of recommended podcasts (target: 3) with the `explore` visual tag. Exact count is a UX decision (story 8.10).
- **FR28:** A user can tap an Explore recommendation's ▶ button to play its most recent episode, or tap the tile to subscribe (defaulting to OTHER tier). No voice/hands-free in next release.

**Resilience & Error Handling**
- **FR29:** Detect Spotify API connection failure and inform via in-app status message (staleness banner, offline tile state) without blocking Home Screen rendering.
- **FR30:** Recover from Spotify API timeout and resume the intended action when connectivity is restored.
- **FR31:** Restore last known state (playback position, current episode) if the app is OS-terminated during playback.
- **FR32:** Display a staleness indicator when the cached episode library has not synced within 24 hours.

**Privacy & Data**
- **FR33:** A user can view what data PodDrive stores about them.
- **FR34:** All LOVED/LIKED/OTHER tags, playback positions, and listening history used for Explore recommendations are stored on-device only in the next release.
- **FR35:** A user can delete all locally stored data and reset the app to its initial state.

**FR Totals:**
| Category | Count |
|---|---|
| **Active in next release** | **32** |
| Deferred (Drive Mode / Vision scaffolding) | 11 (FR10–FR20) |
| Removed (SCP 2026-04-24) | 2 (FR22, FR23) |
| **Total FR slots in PRD** | **45** |

### Non-Functional Requirements Extracted

**Performance**
- **NFR1:** *(DEFERRED — Drive Mode / Vision)* Drive session briefing must begin within 10s of Bluetooth audio device connection detection.
- **NFR2:** Time from Home Screen open (with cached library) to first audio via quick-play must not exceed 5s under normal network conditions.
- **NFR3:** Podcast library sync must complete within 60s on a standard mobile data connection for up to 50 followed podcasts.
- **NFR4:** App must not increase battery consumption by more than 5% vs baseline Spotify usage over a 60-minute listening session.
- **NFR5:** *(DEFERRED — Drive Mode / Vision)* On-device TTS briefing generation must add no more than 2s of delay.

**Security**
- **NFR6:** Spotify OAuth access + refresh tokens must be stored in the platform secure credential store (iOS Keychain / Android Keystore); never plain-text.
- **NFR7:** No user listening data, LOVED/LIKED preferences, or episode history may be transmitted to any external server or third-party service in the next release.
- **NFR8:** The app must request only the minimum required Spotify API scopes.

**Reliability**
- **NFR9:** App must not crash during a standard 60-minute listening session (background audio + locked screen + incoming call).
- **NFR10:** *(DEFERRED — Drive Mode / Vision)* Bluetooth connection trigger must activate the drive session on ≥95% of BT audio connection events.
- **NFR11:** App must recover and resume normal operation within 10s of OS-terminated background process.
- **NFR12:** Graceful degradation for all Spotify API + RSS fetch failure scenarios; no user-visible crashes.

**Accessibility**
- **NFR13:** *(DEFERRED — Drive Mode / Vision)* Core drive session interactions completable hands-free via steering wheel or voice.
- **NFR14:** *(DEFERRED — Drive Mode / Vision)* AI briefing voice at 150–160 wpm.
- **NFR15:** All app screens must meet WCAG 2.1 Level AA contrast ratios and tap target size requirements.
- **NFR16:** Support dynamic text sizing (iOS Dynamic Type / Android font scale) across all screens.

**Integration**
- **NFR17:** Spotify API integration must handle rate limiting transparently (queue + retry without user action).
- **NFR18:** Spotify deep-link playback handoff must succeed within 3s of user confirmation, assuming Spotify installed + running.
- **NFR19:** If Spotify app is not installed, PodDrive must detect at onboarding with a clear actionable message — not a silent failure.
- **NFR20:** Spotify token refresh cycle must not interrupt active listening session.

**Home Screen Experience** *(new per SCP 2026-04-24)*
- **NFR21:** Home Screen must render (three-band sort + unlistened counts) within 1s of app foreground for a library of up to 50 podcasts with cached data.
- **NFR22:** Tap targets on Home Screen tiles (tile body + quick-play button) must meet 48dp×48dp (Android) / 44pt×44pt (iOS) minimums at smallest supported device, with clear visual separation between tap regions.
- **NFR23:** Typography minimum: body text 16sp/pt, tile title 18sp/pt. WCAG 2.1 AA (prefer AAA) contrast against each band's background.
- **NFR24:** Three band colors must be distinguishable by users with common color-vision deficiency; band differentiation must not rely solely on hue.
- **NFR25:** Unlistened-count freshness-color encoding (FR38) must remain legible across full range and not rely on color alone.

**NFR Totals:**
| Category | Count |
|---|---|
| **Active in next release** | **20** |
| Deferred (Drive Mode / Vision scaffolding) | 5 (NFR1, NFR5, NFR10, NFR13, NFR14) |
| **Total NFRs in PRD** | **25** |

### Additional Requirements / Constraints Extracted

**Technical constraints (PRD Risk Mitigation §):**
- iOS background audio session handling (foreground service + state restoration)
- Spotify API rate limiting (local cache, batch background sync; not on-demand per Home Screen open)
- Spotify quota: 25-user cap until extension granted (3-month milestone trigger)
- Spotify API endpoint deprecation risk (abstract via service layer)
- Solo-developer capacity constraint (Android-first, iOS second if resources allow)

**Platform constraints (Domain Requirements §, not re-read in this step — carried from prior IR run):**
- Android 10+ (API 29), iOS 16+
- Scopes: `user-library-read`, `user-read-playback-state`, `user-modify-playback-state`, `user-read-recently-played`, `streaming`

**Business constraints:**
- MVP revenue model: creator-pays per relevant Explore recommendation delivered (deferred to Fast-Follow alongside 8.10)
- No server infrastructure in next release (NFR7 enforces on-device-only data)

**Data source constraints:**
- Explore recommendations sourced from on-device listening history (FR26)
- Episode audio via RSS feed (primary, ADR-002) with Spotify deep-link fallback
- No additional data sources in next release

### PRD Completeness Assessment

**Positives:**
- PRD is internally consistent post-revision: FR22/FR23 cleanly removed, FR24/FR26–28 reframed in place, FR36–FR45 block added with clear Core MVP vs Fast-Follow labels
- Every Deferred FR/NFR is explicitly annotated with `*(DEFERRED — Drive Mode / Vision phase)*` — no silent scope ambiguity
- New NFR21–NFR25 directly cover the Home Screen driver-friendliness + accessibility constraints introduced by FR45
- SCP 2026-04-24 is linked from the revision banner at the top of the PRD

**Gaps / Ambiguities flagged for later steps:**
1. **FR38 freshness-color encoding** defers "exact color ramp and breakpoints" to UX — this is the expected UX-side deferral and will surface in Step 5 (UX analysis).
2. **FR40 tap-target boundary** defers visual/interactive distinction between tile-body and quick-play to UX — same UX-side deferral.
3. **FR27 Explore-recommendation count (target: 3)** defers exact count to UX (story 8.10).
4. **FR44 tier-promotion mechanism** specifies long-press + action sheet — but SCP §6 Deferred Q2 notes this is still open (inline button vs long-press vs Show-All-only). PRD has chosen long-press; check that epics + UX both align with this choice in later steps.
5. **FR24 + FR41 interaction:** FR24 says "most recently played episode"; FR41 says "an episode in progress". A podcast that was played-to-end is the "most recently played" but is NOT "in progress". PRD implies the Continue Listening bar only surfaces when episode is mid-playback — this matches the architecture's derivation rule — but the PRD wording is slightly loose. Minor; flag for Step 4+ consistency check.
6. **FR26–FR28 subscription flow:** FR28 mentions tapping the tile to subscribe, defaulting to OTHER tier. No FR covers the inverse flow (unsubscribe from Home Screen) beyond FR43's generic "Show All for library-level management". Acceptable — unsubscribe is not a MVP-critical home-screen affordance.

**No PRD-level blockers identified.** PRD is ready for traceability validation against epics.

---

## Step 3 — Epic Coverage Validation

**Source:** `epics.md` (49 KB, **dated 2026-03-19 — pre-pivot**). Contains 7 epics and 29 stories covering FR1–FR35 per its embedded *FR Coverage Map*. Does **not** reflect SCP 2026-04-24.

### Epic FR Coverage Extracted (from epics.md §FR Coverage Map + §Epic List)

| FR (per epics.md numbering) | Claimed Epic | Claimed Story Coverage |
|---|---|---|
| FR1 | Epic 1 | Story 1.3 — Spotify OAuth 2.0 PKCE |
| FR2 | Epic 1 | Story 1.4 — Silent token refresh |
| FR3 | Epic 1 | Story 1.5 — Revoke Spotify access |
| FR4 | Epic 2 | Story 2.1 — Fetch & display podcast library |
| FR5 | Epic 2 | Story 2.2 — LOVED/LIKED tier assignment |
| FR6 | Epic 2 | Story 2.2 — Tier change/remove |
| FR7 | Epic 2 | Story 2.1 — Unplayed episode identification |
| FR8 | Epic 2 | Story 2.4 — Local episode cache |
| FR9 | Epic 2 | Story 2.5 — Background sync |
| FR10 | Epic 3 | Story 3.2 — BT audio device detection |
| FR11 | Epic 3 | Story 3.2 — Auto-activate drive session |
| FR12 | Epic 3 | Story 3.1 — Background service |
| FR13 | Epic 3 | Story 3.3 — Manual trigger |
| FR14 | Epic 3 | Story 3.4 — TTS briefing generation |
| FR15 | Epic 3 | Story 3.4 — LOVED before LIKED |
| FR16 | Epic 3 | Story 3.4 — Title/podcast/duration |
| FR17 | Epic 3 | Story 3.4 — No-LOVED adapt |
| FR18 | Epic 3 | Story 3.4 — Empty-inbox adapt |
| FR19 | Epic 3 | Story 3.5 — Hands-free confirmation |
| FR20 | Epic 3 | Story 3.5 — Audio ducking |
| FR21 | Epic 4 | Story 4.1 — Deep-link handoff |
| FR22 | Epic 4 | Story 4.2 — Priority episode queue |
| FR23 | Epic 4 | Story 4.3 — Auto-advance |
| FR24 | Epic 4 | Story 4.4 — Playback position resumption (drive-session-framed) |
| FR25 | Epic 4 | Story 4.5 — Spotify playback failure handling |
| FR26 | Epic 5 | Story 5.1 — Single-episode empty-inbox recommendation (voice-framed) |
| FR27 | Epic 5 | Story 5.1 — Exactly one recommendation, never a list (voice-framed) |
| FR28 | Epic 5 | Story 5.2 — Hands-free accept/decline (voice-framed) |
| FR29 | Epic 6 | Story 6.1 — Voice notification on API failure (voice-framed) |
| FR30 | Epic 6 | Story 6.2 — API timeout recovery with retry |
| FR31 | Epic 6 | Story 6.3 — OS-kill recovery (queue-inclusive) |
| FR32 | Epic 2 | Story 2.5 — Staleness indicator |
| FR33 | Epic 7 | Story 7.1 — Data transparency |
| FR34 | Epic 7 | Story 7.1 — On-device-only confirmation |
| FR35 | Epic 7 | Story 7.2 — Full data reset |

**Total FRs in epics.md:** 35 (FR1–FR35, old numbering).
**FRs in epics that are NO LONGER in PRD:** FR22, FR23 (REMOVED per SCP §4.1).
**FRs in PRD that are ABSENT from epics:** FR36–FR45 (all 10 Home Screen Experience FRs).

### Coverage Matrix (PRD-revised ↔ epics.md)

| FR (PRD 2026-04-24) | Short text | Epic coverage in epics.md | Status |
|---|---|---|---|
| FR1 | Spotify OAuth | Epic 1 / 1.3 | ✓ Covered |
| FR2 | Silent token refresh | Epic 1 / 1.4 | ✓ Covered |
| FR3 | Revoke + delete local data | Epic 1 / 1.5 | ✓ Covered |
| FR4 | Fetch followed podcasts + unheard | Epic 2 / 2.1 | ✓ Covered |
| FR5 | Tag LOVED/LIKED | Epic 2 / 2.2 | ✓ Covered |
| FR6 | Change/remove tier | Epic 2 / 2.2 | ✓ Covered |
| FR7 | Identify unplayed episodes | Epic 2 / 2.1 | ✓ Covered |
| FR8 | Local cache | Epic 2 / 2.4 | ✓ Covered |
| FR9 | Background sync | Epic 2 / 2.5 | ✓ Covered |
| FR10–FR13 | Drive session activation (deferred) | Epic 3 / 3.1–3.3 | ✓ Covered (code retained as scaffolding) |
| FR14–FR20 | TTS briefing + hands-free (deferred) | Epic 3 / 3.4–3.5 | ✓ Covered (code retained as scaffolding) |
| FR21 | Spotify episode playback | Epic 4 / 4.1 | ✓ Covered |
| FR22 | *(REMOVED)* | Epic 4 / 4.2 | ⚠️ **Stale coverage** — story 4.2 points at a retired FR |
| FR23 | *(REMOVED)* | Epic 4 / 4.3 | ⚠️ **Stale coverage** — story 4.3 points at a retired FR |
| FR24 | Persist position + expose to Continue Listening (FR41) | Epic 4 / 4.4 | 🟡 **Partial / mis-scoped** — story 4.4 frames resumption as drive-session-reconnect, not Home Screen surfacing |
| FR25 | Graceful Spotify playback failure handling | Epic 4 / 4.5 | ✓ Covered (voice-framing now deferred but handling logic still applies) |
| FR26 | Explore mode — recommended **podcasts** (not episodes), visual `explore` tag | Epic 5 / 5.1 | ❌ **Mis-scoped** — epics deliver single **episode** via TTS on empty-inbox; PRD now asks for multi-podcast visual Explore panel |
| FR27 | Explore presents small set (target 3) | Epic 5 / 5.1 | ❌ **Contradicts** — epics explicitly say "never a list"; PRD says target 3 |
| FR28 | Tap ▶ to play / tap tile to subscribe | Epic 5 / 5.2 | ❌ **Mis-scoped** — epics cover hands-free accept/decline only; PRD is tap-driven |
| FR29 | In-app staleness banner / offline tile state (not voice) | Epic 6 / 6.1 | 🟡 **Partial / mis-scoped** — epics deliver voice-only notification within drive briefing; PRD now asks for visual banner on Home Screen |
| FR30 | Timeout recovery + retry | Epic 6 / 6.2 | ✓ Covered |
| FR31 | Restore playback position + current episode on OS kill | Epic 6 / 6.3 | 🟡 **Partial / mis-scoped** — epics restore queue + session state; PRD scope narrowed (queue removed) but core recovery still applies |
| FR32 | Staleness indicator >24h | Epic 2 / 2.5 | ✓ Covered |
| FR33 | View stored data | Epic 7 / 7.1 | ✓ Covered |
| FR34 | On-device-only data | Epic 7 / 7.1 | ✓ Covered |
| FR35 | Full reset | Epic 7 / 7.2 | ✓ Covered |
| **FR36** | Home Screen filtered list (unlistened only) | **— none —** | ❌ **MISSING** |
| **FR37** | Three-band sort (LOVED/LIKED/OTHER, newest-first within band) | **— none —** | ❌ **MISSING** |
| **FR38** | Unlistened-count tile badge with freshness-color encoding | **— none —** | ❌ **MISSING** |
| **FR39** | Quick-play ▶ on tile → newest unlistened | **— none —** | ❌ **MISSING** |
| **FR40** | Tap tile body → expand in-place to episode list | **— none —** | ❌ **MISSING** |
| **FR41** | Continue Listening bar (Fast-Follow 8.9) | **— none —** | ❌ **MISSING** |
| **FR42** | Auto-enter Explore mode when inbox empty (Fast-Follow 8.10) | **— none —** | ❌ **MISSING** |
| **FR43** | Show All affordance → full library view | **— none —** | ❌ **MISSING** |
| **FR44** | Long-press tier promote/demote on tile | **— none —** | ❌ **MISSING** |
| **FR45** | Driver-friendly visuals universally (no separate drive mode) | **— none —** | ❌ **MISSING** |

### Missing Requirements

#### Critical Missing FRs (Core MVP — block Epic 8 creation)

- **FR36 — Home Screen filtered podcast list.** The entire home-screen surface is absent from epics.md. Impact: this is the primary UX for the next release; nothing ships without it. Recommendation: introduce **Epic 8 — Home Screen Experience** with dedicated stories covering FR36–FR40 + FR43–FR45.
- **FR37 — Three-band sort.** The LOVED/LIKED/OTHER banding + within-band sort-by-newest-unlistened is the core ordering contract. Missing from epics. Recommendation: `GetSortedPodcastsUseCase` story inside Epic 8.
- **FR38 — Unlistened-count + freshness-color badge.** No epic covers the count-computation or color-encoding work. Recommendation: `GetUnlistenedCountUseCase` + UI tile-badge story inside Epic 8 (architecture.md §Home-Screen Data Flow already documents `FreshnessBucket` enum derivation).
- **FR39 — Tile quick-play button.** No epic covers the one-tap-to-newest-unlistened play path. Recommendation: `GetLatestUnlistenedUseCase` + PodcastTile component story inside Epic 8.
- **FR40 — Expand-in-place episode list.** No epic covers the tile expansion affordance. Recommendation: Epic 8 tile-expansion story; reuses existing episode-sort logic from `Episode.sq:selectUnplayedByPodcast`.
- **FR43 — Show All affordance.** No epic covers the full-library view. Recommendation: Epic 8 story for `ShowAllScreen` (architecture.md already reserves folder `ui/home/ShowAllScreen`).
- **FR44 — Long-press tier promotion.** No epic covers the Home Screen tile long-press → action-sheet mechanism. Recommendation: Epic 8 story wrapping `PromoteTierUseCase` (architecture.md already defines this use case and the Flow-diff animation approach).
- **FR45 — Driver-friendly visuals universally.** No epic covers the universal driver-friendly typography/tap-target application. Recommendation: Epic 8 cross-cutting story (typography, contrast, tap-target audit against NFR22–NFR25).

#### High Priority Missing FRs (Fast-Follow — Epic 8 stories 8.9, 8.10)

- **FR41 — Continue Listening bar.** Fast-Follow per SCP. Architecture already documents derivation from `playback_state` singleton (no schema change). Recommendation: story 8.9.
- **FR42 — Explore mode on empty inbox.** Fast-Follow per SCP. Linked to FR26–FR28 (reframed). Recommendation: story 8.10, together with FR26–FR28 reframing.

#### Mis-Scoped Coverage (FRs where epics.md claims coverage but intent has shifted)

These are **not** uncovered, but the existing epic text contradicts the revised PRD and must be rewritten before Epic 8 ships:

- **FR22, FR23** — `REMOVED` in PRD. Story 4.2 (Priority Episode Queue) and Story 4.3 (Auto-Advance) must be marked **retired** in epics.md. Side-task already spawned to remove `BuildEpisodeQueueUseCase` from `commonMain`.
- **FR26, FR27, FR28** — Reframed from *voice-driven single-episode inbox-empty recommendation* to *visual Explore panel of ~3 recommended **podcasts** with ▶ / subscribe affordance*. Epic 5 + Stories 5.1, 5.2 must be rewritten as part of Epic 8 story 8.10 (Fast-Follow).
- **FR29** — Reframed from *voice notification during briefing* to *in-app staleness banner / offline tile state on Home Screen*. Story 6.1 must be rewritten.
- **FR24** — Still covered in essence, but story 4.4 should be generalised from *drive-session reconnect* to *any playback resume from persisted position, including Continue Listening bar*.
- **FR31** — Restoration scope narrowed (queue concept gone). Story 6.3 should drop queue-restoration ACs and align with PRD's *(playback position, current episode)* phrasing.

### Coverage Statistics

- **Total PRD FRs (active in next release):** 32
- **Active PRD FRs covered correctly in epics.md:** 18 (FR1–FR9, FR21, FR25, FR30, FR32, FR33, FR34, FR35, plus FR10–FR20 as deferred scaffolding — 18 strictly correct + 11 scaffolding = 29/43 FRs including deferred)
- **Active PRD FRs covered but mis-scoped in epics.md:** 5 (FR24, FR26, FR27, FR28, FR29, FR31 — 6 mis-scoped; note FR24 and FR31 are partial)
- **Active PRD FRs absent from epics.md:** 10 (FR36–FR45)
- **Coverage percentage — strict correctness (active only):** 18 / 32 = **56%**
- **Coverage percentage — including mis-scoped entries counted as covered:** 24 / 32 = **75%**
- **Coverage percentage — full PRD (45 FR slots incl. deferred + removed):** correctly-covered 29 / 45 = **64%**
- **Removed FRs still referenced in epics.md (stale):** 2 (FR22, FR23)

### Assessment

**`epics.md` is genuinely pre-pivot.** The earlier working assumption — "sprint status and epics are already updated" — holds only for `sprint-status.yaml` and the two retired story spec files (`4-2-priority-episode-queue.md`, `4-3-auto-advance-to-next-episode.md`). The canonical `epics.md` still declares 35 FRs in the old numbering, still lists FR22 and FR23 as first-class covered items in Epic 4, still frames Explore (Epic 5) and Spotify-API-failure (Story 6.1) as voice-first flows, and contains **zero** references to FR36–FR45 or to Epic 8.

**This is a hard prerequisite for Epic 8 story creation.** `epics.md` must be revised in a dedicated pass (via `bmad-create-epics-and-stories` or equivalent) to:
1. Re-baseline the *Requirements Inventory* §FR list to PRD 2026-04-24 numbering (FR1–FR45, with FR22/FR23 marked *REMOVED* and FR10–FR20 + NFR1/NFR5/NFR10/NFR13/NFR14 marked *deferred*).
2. Re-baseline the AR list against the revised architecture (AR6 now scaffolding; AR7 now `PlaybackForegroundService`; AR10 use-case list rewritten per architecture §Project Structure).
3. Mark Stories 4.2 and 4.3 as **retired**, leave the spec files in place with retirement headers (already done for the spec files; epic text still needs the same treatment).
4. Rescope Epic 4 (drop queue/auto-advance), Epic 5 (convert to Fast-Follow / merge into Epic 8 story 8.10), Epic 6 story 6.1 (visual banner instead of voice), Epic 6 story 6.3 (drop queue restoration).
5. Introduce **Epic 8 — Home Screen Experience** with stories 8.1–8.10 covering FR36–FR45 and NFR21–NFR25.

**No blocker exists that cannot be resolved via an `epics.md` revision pass.** The PRD is internally consistent, the architecture is aligned with the PRD, and the use cases + data model required for Epic 8 are already specified in architecture.md. The gap is purely at the epics-and-stories traceability layer.

### Risks flagged from Step 3

1. **Side-task for `BuildEpisodeQueueUseCase` removal is not yet completed** — the retired stories will remain stale in both the epic doc and the codebase until that side-task ships.
2. **FR26–FR28 double-coverage risk** — if Epic 5 is not explicitly retired/merged before Epic 8 story 8.10 is authored, implementers may inherit voice-first ACs into what should be a visual Explore panel.
3. **NFR coverage not checked in this step** (Step 3 is FR-only per skill definition). NFR21–NFR25 are absent from epics.md and will need to be folded into Epic 8 story ACs during the revision pass.
4. **AR list in epics.md is stale** (AR1–AR12 old text); Step 6+ of this readiness workflow may need to re-verify alignment once `epics.md` is revised.

---

## Step 4 — UX Alignment

**Source:** `ux-design-specification.md` (35 KB, **dated 2026-03-19 — pre-pivot**) + `ux-design-directions.html` (43 KB, supporting visual exploration, pre-pivot).

### UX Document Status

**Found — but pre-pivot.** The UX spec predates SCP 2026-04-24. It is internally coherent and well-structured, but describes a **drive-session-centric, voice-first product** that no longer matches either the revised PRD (2026-04-24) or the revised architecture (2026-04-24). SCP §4.4 explicitly lists 7 new UX specifications required before Epic 8 story creation can proceed.

### UX ↔ PRD Alignment (revised PRD)

**Aligned elements (still usable as-is or with minor tweaks):**

| UX element | PRD FR/NFR anchor | Alignment |
|---|---|---|
| Design token system — color palette, typography, 8dp grid, iconography | UX-DR1 (general foundation) | ✓ Reusable; tokens `color-loved #F87171`, `color-liked #F60A5FA` still apply to Home Screen bands |
| `TierBadge` component (ICON_ONLY + FULL) | FR5, FR6, FR44 | ✓ Reusable; LOVED heart / LIKED thumbs icons already specified and non-color-reliant (UX-DR11) |
| Accessibility requirements (WCAG 2.1 AA, Dynamic Type, colour-blindness testing) | NFR15, NFR16, NFR23, NFR24, NFR25 | ✓ Reusable; should be promoted from "management mode" to universal |
| Platform strategy (SwiftUI + Compose via KMP, platform-native components) | Architecture §Project Structure | ✓ Still valid |
| Spotify OAuth + onboarding flow (Welcome → OAuth → Add first show → Set tier) | FR1, FR5 | ✓ Largely reusable; note BT permission step is now optional/deferred (FR10 deferred) |
| Bottom sheets for non-destructive actions (no alert dialogs) | FR44 (long-press tier sheet) | ✓ Reusable pattern for the Home Screen long-press action sheet |
| Dark-first theming, 8dp grid, `sp` text sizing | NFR23 | ✓ Reusable |

**Misaligned / contradicted elements (require revision):**

| UX element (pre-pivot) | PRD position post-pivot | Alignment |
|---|---|---|
| "Connect your car, hear your podcasts" defining experience (§2.1) | Home Screen is the defining experience; Drive Session is deferred | ❌ Contradicts product vision |
| Three UI modes: Idle / Drive Session Active / Post-Drive (UX-DR3) | One mode. No separate "drive mode". FR45: driver-friendly universally | ❌ Contradicts FR45 + NFR21–NFR25 |
| `DriveSessionCard` as the central anchor of the primary screen | No DriveSessionCard on Home Screen; replaced by sorted podcast list + Continue Listening bar | ❌ Wrong surface |
| `BriefingAnimation` full-screen overlay as signature beat | Briefing is deferred (Story 3.4 scaffolded, not wired) | ❌ Deferred |
| `EpisodeTile` Drive variant (64dp, 28sp) as the primary selection target | Episode rows live inside expanded `PodcastTile` (FR40); not the primary surface | 🟡 Repurposable — `EpisodeTile` standard variant still applies inside the tile expansion |
| `EmptyStatePanel` WITH_RECOMMENDATION / NO_RECOMMENDATION single-episode model | FR26–FR28 reframed: multi-podcast Explore panel (target: 3) with ▶ + subscribe affordances | ❌ Contradicts reframed FR26–FR28 |
| `ShowTile` (artwork + name + new episode count + TierBadge + long-press menu) as idle-screen element | PodDrive Home Screen requires **PodcastTile** with: three-band context, newest-unlistened-date within band, freshness-color count badge, quick-play button with distinct tap target, tile-body expansion affordance, long-press → action sheet | 🟡 Partial fit — ShowTile anatomy is the starting point but needs significant rework |
| `TierFilterTabs` (All / Loved / Liked) on idle screen | No explicit filter tabs in PRD; three-band visual sort replaces them. Show All (FR43) provides library-level access | 🟡 Possibly retired or demoted to Show All screen |
| TTS-first feedback rule; TTS scripts ("PodDrive is ready...", "Opening [title] in Spotify...", etc.) | TTS code retained as scaffolding but not wired to Home Screen UX | ❌ Not applicable to Home Screen primary flows |
| "Drive mode locked navigation — back gesture suppressed, bottom nav hidden" (UX-DR3) | Standard navigation applies universally; no drive-mode lock | ❌ Contradicts FR45 |
| "Drive mode 64dp / management 44dp touch target split" (UX-DR9) | Universal driver-friendly per NFR22 (48dp Android / 44pt iOS) | 🟡 Baseline still valid; drive-mode-only 64dp is deferred |

**Missing UX elements (PRD FRs with no UX specification):**

| PRD FR/NFR | Missing UX coverage |
|---|---|
| FR36 | Home Screen filtered podcast list layout |
| FR37 | Three-band visual design — band backgrounds, divider treatment, within-band sort visualisation, band-collision handling (e.g. newly-promoted tier moving between bands) |
| FR38 | **Freshness-color ramp specification** — exact color breakpoints (today / this-week / older per architecture `FreshnessBucket`), WCAG contrast validation, non-color-reliant fallback (icon/label) |
| FR39 + FR40 | **Tap-target boundary** between tile-body and quick-play button — visual treatment + hit-area + haptic/animation feedback on each |
| FR40 | Tile expand-in-place interaction — expansion animation, collapse affordance, episode-list layout inside expanded tile, scroll behaviour |
| FR41 | Continue Listening bar — placement (above list vs sticky), content (episode title + podcast + progress + cover art?), tap behaviour, empty/hidden state logic |
| FR42 + FR26–FR28 | Explore mode panel — trigger, layout (3 podcast tiles?), `explore` visual tag, ▶ vs tile-tap distinction, subscribe flow |
| FR43 | Show All screen — layout, zero-unlistened podcast presentation, tier management affordances |
| FR44 | Home Screen long-press action sheet — contents (Promote to LIKED / Demote to OTHER / Promote to LOVED / Remove tier), animation of tile moving between bands after promotion (architecture.md recommends Flow-diff via `animateItemPlacement` / `.matchedGeometryEffect`) |
| FR45 | Universal driver-friendly visuals — promotion of AAA-contrast + large-tap-target + low-density rules from drive-mode-only to all screens |
| NFR21 | Performance budget — 1s Home Screen render for ≤50 podcasts |
| NFR22 | Tap target minimums — 48dp Android / 44pt iOS on smallest supported device; visual separation between tile-body and quick-play |
| NFR23 | Typography — body 16sp/pt, tile title 18sp/pt, AA (prefer AAA) contrast per band background |
| NFR24 | Band-color colour-blindness accessibility |
| NFR25 | Freshness-color legibility across ramp; non-color-only encoding |

### UX ↔ Architecture Alignment (revised architecture)

**Aligned:**

| UX need | Architecture support |
|---|---|
| Design tokens in shared spec; `Theme.kt` + `DesignTokens.swift` | Architecture §Project Structure documents `ui/theme/` folder on both platforms |
| Native platform UI (SwiftUI + Compose) via KMP shared logic | Architecture §Project Structure; UI folder split per-platform |
| Instant Home Screen render with cached data | Architecture §Home-Screen Data Flow — all Home Screen use cases emit Flows from SQLDelight; no network on home load |
| Tier change persisted on-device only | Architecture §Data Architecture — `episode_tiers` / `podcast_tiers` tables unchanged; `PromoteTierUseCase` is write-only |
| Continue Listening bar derivation | Architecture §Data Architecture — `playback_state` singleton + derivation rule documented |
| Tile expand-in-place episode list | Architecture — existing `Episode.sq:selectUnplayedByPodcast` query already supports the required ordering |
| Freshness-color computation location | Architecture §UI State Management — `FreshnessBucket` enum computed in commonMain via `GetUnlistenedCountUseCase`; UI maps bucket → color |
| Tier-promotion animation (tile moves between bands) | Architecture §UI State Management — Flow-diff via `animateItemPlacement` / `.matchedGeometryEffect` recommended |
| Explore recommendation source | Architecture §UI State Management — `GetExploreRecommendationsUseCase` in commonMain; source: on-device listening history |

**Unsupported / gap:**

| UX need | Architecture gap |
|---|---|
| Listening history schema | Architecture §Validation Gap Analysis item #5: no explicit schema for Explore's "on-device listening history" source. Architecture defers to Fast-Follow story 8.10; UX spec revision should not lock a visual design that assumes a specific history shape until schema is settled. |
| Show All screen performance at large library sizes | NFR21 targets 1s render for ≤50 podcasts on the Home Screen (filtered set). No explicit NFR for Show All (unfiltered set, which may be larger). UX + performance budget for Show All to be specified together. |

### Alignment Issues (summary)

1. **Primary surface mismatch.** UX spec's primary surface is the drive-session dashboard (`DriveSessionCard` + `EpisodeTile` queue + "Open in Spotify" CTA). PRD + architecture's primary surface is the Home Screen (sorted PodcastTile list + Continue Listening bar + Show All affordance). Every Phase-1 "Core MVP" custom component in the UX spec (`BriefingAnimation`, `DriveSessionCard`, `EpisodeTile` Drive variant) targets the wrong surface for the next release.
2. **Universal driver-friendly vs. drive-mode-only.** UX spec applies AAA contrast + 64dp touch targets only in drive mode; management mode uses AA + 44dp. PRD FR45 + NFR22–NFR25 promote driver-friendly to a universal requirement. UX spec's dual-tier accessibility model must be flattened.
3. **Reframed Explore panel.** UX spec models Explore as a voice-triggered single-episode empty-state fallback (`EmptyStatePanel`). PRD reframes Explore as a visual multi-podcast panel with ▶ + subscribe affordances. Different component, different interaction paradigm.
4. **No PodcastTile specification.** The closest UX element (`ShowTile`) is missing the three-band context, the within-band sort-by-newest-unlistened, the freshness-color count badge, the quick-play-button vs tile-body tap-target distinction, and the tile-expansion affordance. At least four interactions + one color system live inside this single component.
5. **No freshness-color ramp.** FR38 + NFR25 require an exact color ramp keyed to `FreshnessBucket` (TODAY / THIS_WEEK / OLDER). No specification exists. This is the single most user-visible novel visual element in the release.
6. **No tap-target boundary visual.** FR39 + FR40 + NFR22 require the tile-body and quick-play button to be "visually and interactively distinct". UX spec does not yet show how this boundary is rendered or how hit-testing resolves ambiguity.
7. **No long-press action sheet.** FR44 specifies long-press → action sheet on Home Screen tile. UX spec has long-press for `ShowTile` (management context) but no equivalent Home Screen design — including the animation of a tile moving between bands after tier change (addressed in architecture.md but not in UX).
8. **No Continue Listening bar design.** FR41 (Fast-Follow). Placement, anatomy, progress visualisation, and hidden-state logic all unspecified.
9. **No Show All screen design.** FR43. Layout and behaviour for zero-unlistened podcasts, library-level management affordances, and tier-edit entry points all unspecified.

### Warnings

- ⚠️ **UX spec revision is a hard prerequisite for Epic 8 story creation.** Per SCP §4.4, the 7 new UX specifications are: (1) Home Screen composition + three-band visual sort; (2) PodcastTile anatomy including quick-play vs tile-body distinction and long-press affordance; (3) Freshness-color ramp + non-color-reliant encoding; (4) Tile expand-in-place episode list interaction; (5) Continue Listening bar (Fast-Follow 8.9); (6) Explore panel visual + interaction (Fast-Follow 8.10); (7) Show All screen. Epic 8 story ACs cannot be written to implementable precision without these.
- ⚠️ **Architecture-UX contract on freshness-color is incomplete at the UX layer only.** Architecture.md fully specifies the data-side contract (`FreshnessBucket` enum, computed in commonMain, UI maps bucket → color). UX owns the color ramp, breakpoint tuning, and WCAG validation. No architectural rework is needed; this is a pure UX deliverable.
- ⚠️ **Dark-first theming may need reconsideration for the Home Screen.** Current UX spec justifies dark-first with Android Auto / in-car conventions. Home Screen is now the universal surface used in all contexts (not only in-car). Color palette decision should be revisited as part of the three-band color design — three bands need distinguishable backgrounds with AA (prefer AAA) foreground contrast and colour-blindness safety (NFR23, NFR24).
- ⚠️ **Retained-as-scaffolding components.** `BriefingAnimation` and `DriveSessionCard` remain in the architecture as scaffolding for Drive Mode / Vision phase. UX spec should mark their Phase-1 roadmap entries as "deferred — Drive Mode / Vision" rather than delete them, so the component library records the decision.
- ⚠️ **Onboarding flow Bluetooth step** — UX spec's onboarding step 3 is "Request Bluetooth permission with rationale". FR10–FR13 are deferred. Onboarding revision should either remove the Bluetooth step or mark it optional/deferred to avoid requesting a permission whose feature is not shipping.

### Assessment

**UX spec is a partial-readiness blocker.** The PRD and architecture are implementable today, but Epic 8 stories cannot be written to the precision required for development without the 7 new UX specifications listed above. The existing UX spec contains valuable reusable foundations (design tokens, accessibility baseline, `TierBadge`, bottom-sheet interaction patterns, Spotify onboarding flow) and a coherent component philosophy that should be carried forward. The gap is concentrated on the Home Screen surface — a single, high-stakes visual surface that introduces 5 novel interactions (three-band sort, quick-play vs tile-body split, freshness-color, tile expand-in-place, long-press tier promotion) plus 2 Fast-Follow additions (Continue Listening, Explore). A dedicated UX revision pass is the natural next handoff after this readiness audit completes.

### Risks flagged from Step 4

1. **Double-implementation risk on `ShowTile` / `PodcastTile`.** If the UX revision repurposes `ShowTile` instead of introducing a new `PodcastTile`, the Home Screen tile and the Show All screen tile may end up with divergent behaviour (quick-play button on one but not the other; long-press action sheet contents differ). UX revision should decide explicitly whether these are one component with variants or two components.
2. **Freshness-color + band-color interaction risk.** The badge color (freshness) sits on top of the band background (LOVED/LIKED/OTHER). Three-band × three-bucket × two tier-icons = 18 combinations requiring WCAG validation. Colour-blindness safety constraint (NFR24) applies to both dimensions simultaneously.
3. **Tile-expansion + within-band sort stability risk.** When a user expands a tile, and a background sync arrives that changes the newest-unlistened-episode date (potentially re-ordering tiles within the band), the expanded tile may jump. UX revision should specify whether expanded-tile ordering is frozen during expansion, animated, or deferred to the next list settle.
4. **Long-press tier change + band-move animation cost.** The architecture recommends Flow-diff + `animateItemPlacement` / `.matchedGeometryEffect`. UX revision should size-test the animation with large libraries (50+ podcasts) to confirm perceived performance stays inside NFR21 budget when a promotion causes both an intra-band re-sort and a cross-band move simultaneously.
5. **`ux-design-directions.html` is also pre-pivot.** The three-direction exploration (Minimal Focus / Dashboard / Ambient) is for the drive session, not the Home Screen. A similar design-direction exploration may be valuable for the Home Screen three-band treatment before locking a specific visual.

---

## Step 5 — Epic Quality Review

**Scope note:** This step reviews the **pre-pivot `epics.md`** (2026-03-19) against the create-epics-and-stories best-practices rubric. Stale-content findings from Step 3 (Epic 8 missing, FR22/23 obsolete, FR26–FR29 mis-scoped) are not re-relitigated here — this step focuses on **structural quality** of the epic/story decomposition itself. Quality findings carry forward into the `CE` revision pass.

### Epic-Level Findings

#### User-Value Focus

| Epic | User-centric framing? | Assessment |
|---|---|---|
| Epic 1 — Project Foundation & Spotify Authentication | Mixed (title mentions foundation + auth) | 🟡 **Minor concern** — the epic name leads with a technical milestone ("Project Foundation"). Acceptable under the greenfield exemption (§5B), but a pure user-value framing would be "Connect Spotify and secure the user's account". |
| Epic 2 — Podcast Library & Tier Management | ✓ User-centric | ✅ |
| Epic 3 — Drive Session & Spoken Briefing | ✓ User-centric | ✅ |
| Epic 4 — Spotify Playback & Episode Queue | ✓ User-centric | ✅ (note: entire epic partially retired per pivot; see Step 3) |
| Epic 5 — Empty Inbox & Smart Recommendations | ✓ User-centric | ✅ (retired/merged per pivot) |
| Epic 6 — Resilience, Recovery & Error Handling | ✓ User-centric (framed as "graceful recovery", not "error code mapping") | ✅ |
| Epic 7 — Privacy, Data Transparency & Settings | ✓ User-centric | ✅ |

**Verdict:** No technical-milestone epics. Epic 1 edges the rubric but is justified by greenfield setup — not a violation.

#### Epic Independence

Epic build order is 1 → 2 → 3 → 4 → 5/6/7. No backward references detected; no Epic N requires Epic N+1 features.

One coupling flagged:

- **Epic 4 (Playback) ACs assume Epic 3 context.** Story 4.1 opens with *"Given a drive session is active and the briefing has completed"*. This is not a forward dependency (Epic 3 precedes Epic 4), but it makes Epic 4 inseparable from the drive-session lifecycle. Post-pivot, this is a problem of a different kind: Epic 4 Story 4.1 needs to be re-framed as *"Given the user taps quick-play on a Home Screen tile"* — independent of any drive session. Flag for `CE` revision.

**Verdict:** Structurally clean pre-pivot. Post-pivot, Epic 4 story ACs need re-anchoring to Home Screen flows (Step 3 already noted FR24 mis-scoping).

### Story-Level Findings

#### Story Sizing Violations

| Story | Concern | Severity |
|---|---|---|
| **Story 1.2 — Shared Infrastructure (DB + Errors + Secure Storage)** | Bundles three orthogonal infra concerns (SQLDelight config, `AppError` sealed class, `SecureStorage` expect/actual). Each is independently testable and has its own test matrix. | 🟠 **Major — oversized** |
| **Story 3.4 — TTS Spoken Briefing Generation & Delivery** | 9 acceptance criteria spanning FR14 (warm tone + name), FR15 (LOVED-before-LIKED ordering), FR16 (title + podcast + duration), FR17 (no-LOVED adapt), FR18 (empty-inbox adapt), NFR5 (2s start), NFR14 (150–160 wpm), UX-DR5 (BriefingAnimation), UX-DR8 (TTS scripts). Covers script generation logic + TTS playback + UI animation + performance budget in a single story. | 🔴 **Critical — epic-sized story** (Drive Mode / Vision scope, so lower urgency, but must be split if / when Vision-phase work begins) |
| **Story 7.4 — Accessibility Compliance & Responsive Design** | Mega-cross-cutting story: TalkBack + VoiceOver focus order (drive screen), Reduce Motion, Dynamic Type at 150%/200%, WCAG AA on non-drive screens, colour-blindness simulation, `AccessibilityChecks.enable()` in Espresso + Xcode Accessibility Inspector in CI, compact-breakpoint layout, tablet-breakpoint layout. Roughly 9 distinct deliverables across 2 platforms. | 🔴 **Critical — epic-sized story** |
| Story 1.1 — KMP Monorepo & Build Foundation | Bundles monorepo init + Compose + SwiftUI shells + Gradle Version Catalog + GitHub Actions CI + Fastlane (Google Play internal + TestFlight). Large, but defensible as a greenfield "green-build baseline" story. | 🟡 **Minor — bundled but coherent** |
| Story 2.2 — LOVED/LIKED Tier Assignment & Management | Bundles tier set + tier change + tier remove + TierBadge rendering. Coherent single interaction surface. | ✅ Acceptable |

#### AC Quality

- ✅ **Given/When/Then format applied consistently** across all 29 stories.
- ✅ **FR/NFR/UX-DR references inline in ACs** — strong traceability pattern (e.g., Story 2.5 AC cites FR32, NFR3, NFR17, AR12 inline).
- 🟡 **Missing error-path coverage in some stories:**
  - Story 2.1 (Fetch & Display Library) — no AC for empty library (first-time user with zero followed podcasts) or Spotify-API-failure at fetch time.
  - Story 2.4 (Local Episode Cache) — no AC for cache-corruption / empty-cache-on-first-launch.
  - Story 3.3 (Manual Drive Session Trigger) — no AC for "user taps Start Drive while another drive session is already active" (idempotency).
  - Story 4.4 (Playback Position Resumption) — partially covered (AC for "no prior position exists") but no AC for position-beyond-episode-duration edge case (episode re-release shortened).
- 🟡 **Vague performance language in a few places:**
  - Story 1.3 OAuth — no explicit timeout AC for OAuth redirect stall.
  - Story 5.1 — "on-device listening history" is cited but no AC specifies schema or minimum-history requirement before a recommendation can be produced.

#### Forward / Circular Dependency Scan

- ✅ No story references a future story by number.
- ✅ No circular dependencies between stories.
- 🟡 **Implicit cross-epic coupling in Epic 4 noted above** (Story 4.1 assumes drive-session context from Epic 3).

#### Database / Entity Creation Timing

The pre-pivot rubric says tables should be created when first needed, not upfront.

- Story 1.2 AC says *"SQLDelight is configured in `commonMain` with platform drivers wired"* — **ambiguous** whether tables are defined upfront or per-story. The architecture document has the schema split across three `.sq` files (Podcast.sq, Episode.sq, DriveSession.sq) — which the actual codebase does follow — but the epics.md ACs do not explicitly delegate table creation to the stories that need them.
- Story 2.1 AC implicitly requires `podcasts` table but does not say *"creates the podcasts table"*.
- Story 3.1 AC implicitly requires `drive_sessions` table but does not say *"creates the drive_sessions table"*.
- Story 4.4 AC mentions "PlaybackState in SQLDelight" with no explicit creation step.

🟡 **Minor concern — table-creation responsibility is not clearly assigned in story ACs.** In practice the shared `shared/src/commonMain/sqldelight/com/poddrive/*.sq` layout lives alongside domain code, so SQLDelight tables effectively materialise with the story that adds the `.sq` file. The gap is documentation-only; the codebase pattern is sound.

#### Starter Template Check

Architecture does not mandate an external KMP starter template. Story 1.1 builds the monorepo from scratch. ✅ Greenfield setup handled correctly per §5B of the rubric.

### Cross-Cutting Quality Observations

1. **NFR coverage discipline is strong** where stories exist, but NFR21–NFR25 (new post-pivot) have **zero** story anchors in the current epics.md — deferred to `CE` revision per Step 3.
2. **UX-DR citation density is high** — nearly every story cites the UX-DR items it fulfils. Discipline should be preserved when the UX revision introduces new UX-DRs for the Home Screen.
3. **Accessibility is bundled into Story 7.4** at the end of the sequence. Post-pivot, NFR22–NFR25 are Home Screen-critical and must be **per-story ACs on Epic 8 stories**, not postponed to a single omnibus story.
4. **Performance budgets per story are inconsistent.** Some stories cite specific NFRs (Story 2.5 cites NFR3 60s sync; Story 3.4 cites NFR5 2s TTS start). Others elide them. `CE` revision should anchor NFR21 (1s Home Screen render) to every Epic 8 story that touches rendering, not just one.

### Severity-Classified Violations

#### 🔴 Critical Violations

- **Story 3.4 — oversized.** 9 ACs spanning 5 FRs + 2 NFRs + 2 UX-DRs. Recommendation: split into (a) briefing script generation (FR14–FR18 in commonMain), (b) TTS playback + ducking (NFR5, FR20 — merge with Story 3.5), (c) `BriefingAnimation` UI component (UX-DR5, UX-DR8). Deferred urgency because Epic 3 is Drive Mode / Vision-phase per pivot, but the structural defect persists and will resurface when this work is picked back up.
- **Story 7.4 — oversized.** ~9 distinct accessibility + responsive-design deliverables across 2 platforms. Recommendation: split into (a) semantic labels + TalkBack/VoiceOver focus order, (b) Reduce Motion + Dynamic Type testing, (c) responsive breakpoints, (d) automated accessibility checks in CI. Post-pivot, the semantic-labels + focus-order portion must move earlier (anchored to Epic 8 home screen stories) rather than live at Epic 7.
- **Post-pivot structural gap (cross-reference Step 3):** Epic 8 does not exist. 10 FRs + 5 NFRs with no story anchors. This is the single largest critical violation of the rubric's *"Every FR must have a traceable implementation path"* master rule.

#### 🟠 Major Issues

- **Story 1.2 — bundled infrastructure.** Three concerns (SQLDelight + AppError + SecureStorage) in one story. Recommendation: split into three stories — each is small, independently testable, and has a clear acceptance surface. Low urgency (already shipped per sprint-status), but the pattern should not be repeated.
- **Epic 4 coupling to Epic 3 context.** Story 4.1 (and 4.4) ACs assume drive-session active state. Post-pivot, these must be re-framed as Home Screen-triggered playback. Flag for `CE` revision, not a fix to the existing story text unless Epic 4 is revised as part of the pivot.
- **FR26–FR28 single-story bundle.** Epic 5 has only 2 stories (5.1 generation + 5.2 accept/decline) for 3 FRs. Post-pivot reframing to visual Explore panel will require more decomposition (separate stories for panel render, ▶ action, subscribe-from-tile action). Not a current-epics defect per se, but the pre-pivot sizing sets a bad precedent for the reframe.

#### 🟡 Minor Concerns

- Epic 1 title leads with technical framing ("Project Foundation").
- Story 2.1, 2.4, 3.3, 4.4 — missing error-path / edge-case ACs enumerated above.
- Story 1.3 — no OAuth-redirect-timeout AC.
- Story 5.1 — listening-history schema / minimum-history-size not specified.
- Database table creation not explicitly delegated in story ACs (documentation-only gap; codebase pattern is fine).
- Story 1.1 — bundled greenfield setup (acceptable but heavy).

### Best-Practices Compliance Checklist (per epic, pre-pivot state)

| Epic | Delivers user value | Independent | Stories sized | No forward deps | Tables when needed | Clear ACs | FR traceability |
|---|---|---|---|---|---|---|---|
| Epic 1 | 🟡 greenfield | ✅ | 🟠 Story 1.2 bundled | ✅ | 🟡 implicit | ✅ | ✅ |
| Epic 2 | ✅ | ✅ (req Epic 1) | ✅ | ✅ | 🟡 implicit | 🟡 error paths thin | ✅ |
| Epic 3 | ✅ | ✅ | 🔴 Story 3.4 oversized | ✅ | 🟡 implicit | ✅ | ✅ (deferred) |
| Epic 4 | ✅ | 🟡 coupled to Epic 3 | ✅ | ✅ | 🟡 implicit | 🟡 edge cases | ⚠️ stale (FR22/23 retired) |
| Epic 5 | ✅ | ✅ | 🟡 2 stories for 3 FRs | ✅ | ✅ | ✅ | ⚠️ stale (FR26–28 reframed) |
| Epic 6 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ FR29 mis-scoped |
| Epic 7 | ✅ | ✅ | 🔴 Story 7.4 oversized | ✅ | ✅ | ✅ | ✅ |
| **Epic 8** | **— absent —** | **— absent —** | **— absent —** | **— absent —** | **— absent —** | **— absent —** | **❌ 10 FRs + 5 NFRs uncovered** |

### Remediation Guidance (actionable)

For the `CE` (Create Epics and Stories) revision pass — ordered by priority:

1. **[P0] Introduce Epic 8** with 8–10 stories covering FR36–FR45 and NFR21–NFR25. Recommended decomposition:
   - 8.1 `GetSortedPodcastsUseCase` + three-band sort (FR36, FR37, NFR21)
   - 8.2 `GetLatestUnlistenedUseCase` + quick-play button (FR39, NFR22)
   - 8.3 `GetUnlistenedCountUseCase` + `FreshnessBucket` + count badge (FR38, NFR25)
   - 8.4 PodcastTile anatomy + tap-target boundary (FR39, FR40, NFR22)
   - 8.5 Tile expand-in-place episode list (FR40)
   - 8.6 Long-press action sheet + `PromoteTierUseCase` + band-move animation (FR44)
   - 8.7 Show All screen (FR43)
   - 8.8 Universal driver-friendly styling audit (FR45, NFR22–NFR25)
   - 8.9 Continue Listening bar — Fast-Follow (FR41, FR24)
   - 8.10 Explore panel + subscribe-from-tile — Fast-Follow (FR26–FR28, FR42)

2. **[P0] Retire Stories 4.2 and 4.3** in epics.md — mark as retired, link to SCP §4.1.
3. **[P0] Rewrite Story 4.1 and 4.4** to anchor at Home Screen quick-play and Continue Listening, not drive-session reconnection.
4. **[P0] Rewrite Story 6.1** — visual staleness banner / offline tile state, not TTS notification.
5. **[P0] Re-baseline AR list** in epics.md against revised architecture (AR6 scaffolding, AR7 `PlaybackForegroundService` rename, AR10 use-case list).
6. **[P1] Split Story 3.4** (deferred Drive Mode / Vision) when that work is picked back up.
7. **[P1] Split Story 7.4** — move semantic labels + focus order to Epic 8 per-story ACs; keep the rest in a smaller Epic 7 accessibility story.
8. **[P2] Add missing error-path ACs** to Stories 2.1, 2.4, 3.3, 4.4 during the revision pass.
9. **[P2] Explicitly delegate table creation** in the ACs of the first story to use each table (e.g., Story 2.1 "creates the `podcasts` + `episodes` tables").

### Assessment

**Pre-pivot structural quality is solid with two oversized-story exceptions (3.4, 7.4) and one bundling exception (1.2).** No forward dependencies, no circular deps, consistent BDD structure, strong FR/NFR/UX-DR traceability at the AC level, proper greenfield setup pattern. The `CE` workflow's outputs, when run on this project originally (2026-03-19), produced a quality epic decomposition.

**Post-pivot structural integrity is broken at the macro level.** Epic 8 absence + Epic 4 retirement candidates + Epic 5 reframing + Story 6.1 reframing + AR-list staleness all compound into a situation where `epics.md` cannot be patched incrementally — it needs a dedicated `CE` revision pass. The good news: the pre-pivot quality discipline (Given/When/Then, inline trace refs, right-sized stories in most cases) provides a clear house-style template for the Epic 8 authoring work.

### Risks flagged from Step 5

1. **Oversized-story pattern recurrence.** If Epic 8 is authored under time pressure during `CE` revision, the `3.4` / `7.4` oversizing pattern may recur — especially for the long-press + band-animation story (8.6), which bundles a use case + action sheet UI + list-transition animation across two platforms. Explicitly size-check Epic 8 stories against the rubric before approving.
2. **Accessibility deferral risk.** Story 7.4 currently parks all accessibility work at the end. If Epic 8 does not inherit per-story accessibility ACs, NFR22–NFR25 will slip to post-MVP again. Recommendation: mandate an accessibility AC on every Epic 8 story.
3. **Edge-case / error-path thinness.** The Story 2.1 / 2.4 / 3.3 pattern of omitting error-path ACs may carry into Epic 8 (e.g., what does the Home Screen show when cache is empty on first launch? when Spotify API is unreachable at sync? when `playback_state` points to an episode no longer in cache?). These questions must be in Epic 8 ACs.
4. **Table-creation responsibility gap** may cause implementers to add tables without the commit context that should accompany them. Minor, but fixable in the revision pass.

---

## Summary and Recommendations

### Overall Readiness Status

## 🟠 **NEEDS WORK** — not ready for implementation

The PRD and architecture are implementable today. The **epics** and **UX spec** are not. Two downstream revision passes are required before Epic 8 implementation can begin. No issue found is unresolvable, and no ambiguity exists about what needs to change.

### Readiness by Artifact

| Artifact | Status | Verdict |
|---|---|---|
| `prd.md` (2026-04-24) | ✅ Ready | Internally consistent post-revision; FR/NFR scope cleanly separated into active / deferred / removed. No blockers. |
| `architecture.md` (2026-04-24) | ✅ Ready | Fully aligned with revised PRD; use cases, data model, state management, and project structure are all specified to implementable precision. |
| `sprint-change-proposal-2026-04-24.md` | ✅ Authoritative | Change decisions documented; scope boundaries clear; downstream handoffs enumerated. |
| `epics.md` (2026-03-19) | 🟠 Pre-pivot — revision required | Solid pre-pivot structural quality, but 10 FRs + 5 NFRs have zero story coverage; Stories 4.1/4.2/4.3/4.4/5.1/5.2/6.1 are either stale, mis-scoped, or require re-anchoring. Cannot be patched incrementally. |
| `ux-design-specification.md` (2026-03-19) | 🟠 Pre-pivot — revision required | Valuable reusable foundations (tokens, accessibility baseline, TierBadge, onboarding shell); but the primary surface (Home Screen) has zero UX specification. SCP §4.4 lists 7 new UX deliverables needed. |
| `sprint-status.yaml` | 🟡 Partially updated | Reflects retired stories 4.2/4.3 but lacks Epic 8 entries. Will auto-regenerate from next `SP` run. |

### Critical Issues Requiring Immediate Action

**🔴 Blockers for Epic 8 implementation** (must resolve before `SP` / `CS` can proceed):

1. **Epic 8 does not exist in `epics.md`.** 10 FRs (FR36–FR45) and 5 NFRs (NFR21–NFR25) have no story anchors. This is the largest single violation of the readiness rubric's *"Every FR must have a traceable implementation path"* master rule.
2. **Home Screen UX specification does not exist.** The primary user surface for the next release — three-band sort, PodcastTile anatomy, quick-play vs tile-body tap-target boundary, freshness-color ramp, tile expansion, long-press action sheet + band-move animation, Continue Listening bar, Explore panel, Show All screen — has zero design specification. Epic 8 story ACs cannot be written to implementable precision without these.
3. **Stale FR coverage in `epics.md`.** FR22 and FR23 (removed per SCP §4.1) are still listed as first-class covered items in Epic 4 (Stories 4.2, 4.3). Stale references must be marked retired.
4. **Mis-scoped FR coverage.** FR24, FR26, FR27, FR28, FR29, FR31 have epic coverage that contradicts the revised PRD (voice-vs-visual, episode-vs-podcast, single-vs-list, queue-restoration-vs-position-only). Stories 4.1, 4.4, 5.1, 5.2, 6.1, 6.3 require rewrites.
5. **AR list in `epics.md` is stale.** AR1–AR12 references pre-pivot architecture (AR6 `DriveSessionState` as live state machine; AR7 `DriveSessionForegroundService`; AR10 old use-case list). Must be re-baselined against revised architecture.

**🟠 Major quality issues** (carry-forward risk — fix during revision passes):

6. **Oversized-story pattern.** Stories 3.4 (TTS Briefing) and 7.4 (Accessibility + Responsive) each bundle 9+ deliverables. Must be split — and the pattern must not recur when Epic 8 is authored (especially story 8.6 long-press + `PromoteTierUseCase` + band-move animation, which risks the same bundling).
7. **Accessibility deferred to Story 7.4 omnibus.** Post-pivot, NFR22–NFR25 are Home Screen–critical; they must become per-story ACs on Epic 8 stories, not postponed.
8. **Edge-case / error-path AC thinness.** Stories 2.1, 2.4, 3.3, 4.4 omit common error paths. Epic 8 authoring must not repeat this.
9. **Side-task debt.** Four follow-up side-tasks were spawned during architecture revision (BuildEpisodeQueueUseCase removal, PlaybackForegroundService rename, deleteAllSessions migration, feature-flag gating for BT+TTS) — none yet completed. These are not blockers but will cause orphan code / stale state if left indefinitely.

### Recommended Next Steps

**Required sequence — in order:**

1. **Revise UX — run `CU` (Create UX)** in a fresh context window.
   Agent: 🎨 Sally (UX Designer). Skill: `bmad-bmm-create-ux-design`.
   Deliverables: the 7 new specifications listed in SCP §4.4:
   - Home Screen composition + three-band visual sort
   - PodcastTile anatomy including quick-play vs tile-body tap-target distinction and long-press affordance
   - Freshness-color ramp + non-color-reliant encoding (keyed to `FreshnessBucket` TODAY / THIS_WEEK / OLDER per architecture.md)
   - Tile expand-in-place episode list interaction
   - Continue Listening bar (Fast-Follow 8.9)
   - Explore panel visual + interaction (Fast-Follow 8.10)
   - Show All screen
   Mandatory side-constraints: universal driver-friendly rules (FR45 + NFR22–NFR25); colour-blindness safety across band × freshness-bucket × tier-icon matrix (NFR24).

2. **Revise Epics — run `CE` (Create Epics and Stories)** in a fresh context window.
   Agent: 📋 John (Product Manager). Skill: `bmad-bmm-create-epics-and-stories`.
   Deliverables:
   - Introduce **Epic 8 — Home Screen Experience** with 8–10 stories (suggested 8.1–8.10 decomposition in Step 5 §Remediation Guidance).
   - Re-baseline *Requirements Inventory* to PRD 2026-04-24 numbering (FR1–FR45; FR22/23 marked *REMOVED*; FR10–FR20 + NFR1/NFR5/NFR10/NFR13/NFR14 marked *deferred — Drive Mode / Vision*).
   - Re-baseline *Additional Requirements* (AR1–AR12) against revised architecture (AR6 scaffolding; AR7 `PlaybackForegroundService`; AR10 new use-case list).
   - Mark Stories 4.2, 4.3 as retired.
   - Rewrite Stories 4.1, 4.4, 6.1 to anchor at Home Screen flows, not drive-session lifecycle.
   - Retire Epic 5 (merge into story 8.10).
   - Enforce oversized-story rubric (no single story should bundle more than ~5 ACs spanning more than ~2 FRs).
   - Ensure NFR22–NFR25 accessibility ACs are per-story on Epic 8, not deferred.

3. **Complete the 4 outstanding side-tasks** before or in parallel with Epic 8 story kickoff:
   - BuildEpisodeQueueUseCase removal from commonMain
   - `DriveSessionForegroundService` → `PlaybackForegroundService` rename
   - `deleteAllSessions()` cleanup / migration strategy
   - Feature-flag gating for Bluetooth + TTS init (to prevent scaffolded-but-unwired code from running)

4. **Re-run `IR` (this workflow)** against the revised `epics.md` + `ux-design-specification.md` to confirm green readiness. Consider a different high-quality LLM for the re-run (validation workflows benefit from independent review).

5. **Sprint Planning — run `SP`** in a fresh context window to regenerate `sprint-status.yaml` for Epic 8.

6. **Begin implementation** via `CS` → `DS` → `CR` loop on Epic 8 stories in priority order (8.1–8.8 Core MVP; 8.9 + 8.10 Fast-Follow post-launch).

### Guardrails for the revision passes

- **Do not touch `prd.md` or `architecture.md`.** Both are revised and consistent. Any impulse to edit them during UX / Epics revision is a red flag — route through SCP (`CC`) instead.
- **Do not delete `DriveSessionState`, `BriefingAnimation`, `DriveSessionCard`, or TTS/Bluetooth code from the codebase.** They are explicitly retained as Drive Mode / Vision scaffolding per architecture §AR6, §AR7, §Validation Gap Analysis. The side-task for `BuildEpisodeQueueUseCase` removal is the **only** code-removal task — all other scaffolding stays.
- **Do not reintroduce voice-first flows on Home Screen.** TTS is scaffolding only; Home Screen interactions are visual + tactile for the next release.
- **Every Epic 8 story must cite NFR21 + NFR22–NFR25** inline in ACs if it renders UI. Accessibility is not an afterthought story.

### Final Note

This assessment identified **29 distinct findings across 6 categories** — organised as:

- **5 critical blockers** for Epic 8 implementation (all resolvable via `CU` + `CE` revision passes; no unresolvable risks)
- **4 major quality issues** to address during the revision passes (oversized stories, accessibility deferral, thin error-path ACs, side-task debt)
- **7 architectural risks** flagged in Steps 3–5 for `CE` authoring awareness (ShowTile/PodcastTile divergence, freshness × band WCAG matrix, tile-expansion sort stability, long-press band-move animation cost at scale, pre-pivot design directions, implicit table creation, edge-case thinness)
- **5 warnings** about UX scope (freshness-color owned by UX; dark-first theme reconsideration; scaffolding component labelling; onboarding Bluetooth step gate; retained-as-scaffolding positioning)
- **6 remediation priorities** (P0/P1/P2) for the `CE` revision
- **2 partial-coverage metrics** (active-FR coverage: 56% strict / 75% lenient; deferred coverage: 100% as scaffolding)

**Address the 5 critical blockers before proceeding to implementation.** The recommended path is `CU` → `CE` → re-run `IR` → `SP` → implementation. These findings can be used to improve the artifacts, or you may choose to proceed as-is — but proceeding without the UX and Epic 8 revisions will produce Epic 8 stories with implementable-precision gaps that will surface as story-validation (`VS`) failures during Sprint Planning.

---

## Assessor

**Assessed by:** 🏗️ Winston (Architect)
**Skill:** `bmad-check-implementation-readiness` (bmm 3-solutioning, sequence 70)
**Date:** 2026-04-24
**Prior report:** [implementation-readiness-report-2026-03-19.md](implementation-readiness-report-2026-03-19.md) (superseded)
**Scope:** Post-pivot audit following SCP 2026-04-24 (podcast-list-centric Home Screen pivot).
**Steps executed:** 1 (Document Discovery) → 2 (PRD Analysis) → 3 (Epic Coverage Validation) → 4 (UX Alignment) → 5 (Epic Quality Review) → 6 (Final Assessment).
**Duration:** Single-session audit.

---

