---
workflow: bmad-correct-course
date: 2026-04-24
author: Dimitrios.dimitrelos
mode: batch
scope_classification: Major
status: approved
approved_by: Dimitrios.dimitrelos
approved_date: 2026-04-24
source: brainstorming-session-2026-04-24-1200.md
---

# Sprint Change Proposal — PodDrive Pivot to Podcast-List-Centric UX

## Section 1 — Issue Summary

### Problem statement

Product direction has pivoted. The existing **drive-session + priority-episode-queue** model (Epics 3 & 4) delivered a voice-briefing experience centered on auto-detected Bluetooth drives. In use, the **priority episode queue** specifically was identified as not delivering user value. The user has committed to a new product thesis:

> *"The whole user experience should come from the screen with the list of podcasts that opens up when you open the app."*

This is a **strategic pivot**, not a technical course-correction. Concretely:

- The **priority episode queue** (Story 4.2, done) is no longer part of the product
- The **drive-session-centric UX** becomes a secondary concern — next version is explicitly *not* car-specific; instead it is "friendly to use while driving" (big tiles, big buttons, readable text) as a design constraint applied universally
- The **launch screen becomes the experience** — a sorted list of podcasts with per-tile quick-play, expand-in-place drill-down, unlistened-count badges, and conditional "Continue Listening" / "Explore" surfaces

### Triggering artifact

A brainstorming session completed 2026-04-24 (see [brainstorming-session-2026-04-24-1200.md](../brainstorming/brainstorming-session-2026-04-24-1200.md)) produced 14 design decisions across 7 first-principles foundations and 7 what-if explorations. The session concluded with a prioritized MVP of 8 core items + 2 fast-follow items.

### Issue category

**Strategic pivot.** Not a technical limitation, not a misunderstood requirement — a deliberate product redirection based on user-experience judgment.

### Evidence

- Brainstorming session document (signed off by user 2026-04-24)
- User's explicit statement: "the episode queue feature is not valuable"
- User's explicit statement on target UX: "podcasts sorted, first by indication (loved, liked) and then by the date of the latest episode that I have not listened to"
- User's explicit statement on driver-friendliness: "for the next version of the app, no car-specific features. Let's just make it friendly for the user to use while driving (big tiles, big buttons, easy to read text)"

---

## Section 2 — Impact Analysis

### 2.1 Epic Impact

| Epic | Status | Impact | Action |
|------|--------|--------|--------|
| **Epic 1** Foundation & Auth | done | None | No change |
| **Epic 2** Library & Tier Management | done | Reused heavily by new home screen — tier data model, subscription list, staleness indicator all remain valid | No change; reuse |
| **Epic 3** Drive Session & Briefing | in-progress | **Major deprioritization.** Voice briefing and auto-activation are explicitly out of scope for next version. Stories 3.1, 3.2, 3.3, 3.6 remain built (sunk cost); 3.4, 3.5 stay deferred. Drive session UI (3.6) is now dead code. | Mark Epic 3 as **superseded**; keep build intact but remove as primary nav path |
| **Epic 4** Playback & Queue | in-progress | **Queue concept retired.** Story 4.2 (done) retired + code removed. Story 4.3 (ready-for-dev) retired (never built). Story 4.4 (backlog) replaced by new "Continue Listening" story. Stories 4.1, 4.5, 4.6, 4.8, 4.9, 4.10, 4.11 (playback plumbing, RSS, ExoPlayer) remain valid foundation for the new model | Retire 4.2, 4.3; redirect 4.4; rescope epic |
| **Epic 5** Empty Inbox Recommendations | backlog | Redirected — recommendation concept survives but moves from *drive-session TTS empty state* to *home-screen "Explore mode"* when no subscribed podcast has unlistened episodes | Redirect into new epic |
| **Epic 6** Resilience & Error Handling | backlog | Largely still relevant (API failures, process kill recovery) but drive-session-specific recovery semantics (Story 6.3) weaken without drive session | Keep; lightly rescope |
| **Epic 7** Privacy & Settings | backlog | Unaffected | No change |
| **NEW Epic 8** Podcast-List Home Screen | — | **New epic introduced** to house the 8 Core MVP + 2 fast-follow items from brainstorm | Create new epic with ~10 stories |

### 2.2 Story Impact — Detailed

**Retire / remove:**
- **Story 4.2 Priority Episode Queue** (status: done) → RETIRE. Code removal required from `BuildEpisodeQueueUseCase`, `DriveSessionViewModel` queue state, SQLDelight queue persistence (if exists), `EpisodeTile` queue rendering. Git commit `5d98092c` is a candidate for partial revert; however surgical removal recommended because Story 4.5 (done) and later stories built atop related playback code.
- **Story 4.3 Auto-Advance to Next Episode** (status: ready-for-dev) → RETIRE. Never built. Remove story spec, remove from sprint-status.

**Redirect / redesign:**
- **Story 4.4 Playback Position Resumption** (status: backlog) → replaced by new Epic 8 story "Continue Listening Bar" (different behavior — no drive-session context, just last-played episode with remaining time)
- **Story 5.1 Empty Inbox Recommendation** (status: backlog) → redirected into new Epic 8 story "Home Screen Explore Mode" (visual tag + subscribe flow, not TTS briefing)
- **Story 5.2 Hands-Free Accept/Decline** (status: deferred) → remains deferred; drive-session-specific

**Status unchanged but functionality re-contextualized:**
- Stories 4.1, 4.5, 4.6, 4.8–4.11: playback plumbing stays but now drives the per-tile quick-play button instead of queued auto-advance
- Stories 3.1–3.3, 3.6: built drive-session infrastructure persists but no longer the primary UX surface

**New stories required (Epic 8 — Podcast-List Home Screen):**

| # | Story | Priority |
|---|-------|----------|
| 8.1 | Three-band podcast list sort (Loved / Liked / Other, color-differentiated) | Core MVP |
| 8.2 | "No-unlistened = invisible" filter on home screen | Core MVP |
| 8.3 | Per-tile quick-play button (dumb + latest unlistened) | Core MVP |
| 8.4 | Expand-in-place drill-down (tile expands to show unlistened episodes) | Core MVP |
| 8.5 | Unlistened count with freshness-color encoding | Core MVP |
| 8.6 | Manual tier promotion from home-screen tile | Core MVP |
| 8.7 | "Show All" library screen (full subscription view for management) | Core MVP |
| 8.8 | Driver-friendly visual baseline (big tiles, big buttons, readable text — applied universally) | Core MVP |
| 8.9 | Continue Listening bar (conditional, for in-progress episodes) | Fast-follow |
| 8.10 | Home Screen Explore Mode (empty state with recommendations) | Fast-follow |

### 2.3 Artifact Conflicts

**PRD (`_bmad-output/planning-artifacts/prd.md` — not re-read for this proposal, action below):**
- **FR22** (episodes queued in priority order) — REMOVE
- **FR23** (auto-advance to next episode) — REMOVE
- **FR24** (resume from last position) — REVISE into "Continue Listening" behavior
- **FR26, FR27, FR28** (single recommendation, empty inbox, hands-free accept) — REVISE to home-screen Explore mode (no TTS, no steering wheel)
- **FR14–FR20** (briefing-related FRs) — DEPRIORITIZE (keep for future-phase; mark "not in next version")
- **NEW FRs required:** podcast list sort by tier + recency, per-tile quick-play, expand-in-place drill-down, unlistened count + freshness, Continue Listening bar, Explore mode, Show All screen, driver-friendly visual constraint
- **MVP scope restated** around Epic 8 + Epics 1/2 + playback plumbing from Epic 4

**Architecture (`_bmad-output/planning-artifacts/architecture.md`):**
- **AR6** `DriveSessionState` sealed class — consider whether drive session state persists at all in the next version; the states `Briefing`, `Playing`, `Paused` may collapse into simpler playback state tied to the home screen rather than a session lifecycle
- **AR2** Shared `commonMain` logic — `BuildEpisodeQueueUseCase` removed; new use cases: `GetSortedPodcastsUseCase`, `GetLatestUnlistenedUseCase`, `GetUnlistenedCountUseCase`, etc.
- **AR3** SQLDelight schema — queue-related tables/columns can be dropped; no new schema required for sort (derivable from Episode + Podcast tables)
- **Data model impact** is modest — we already have tier + unlistened-count data via Epic 2 work

**UX Design Spec (`_bmad-output/planning-artifacts/ux-design-specification.md`):**
- **UX-DR3** three-mode navigation (Idle / Drive Active / Post-Drive) — collapses to single-mode navigation with unified home screen
- **UX-DR4** two-zone drive dashboard (DriveSessionCard + EpisodeTile queue + Open-in-Spotify CTA) — obsolete
- **UX-DR5** Drive-specific components (`BriefingAnimation`, `DriveSessionCard`, drive-variant `EpisodeTile`) — deprioritized; keep built, remove from MVP nav path
- **UX-DR6** `ShowTile`, `TierFilterTabs`, `EmptyStatePanel` — largely reusable, need updates: `ShowTile` grows quick-play button + freshness-color count; `EmptyStatePanel` becomes Explore mode surface
- **UX-DR8** TTS-first feedback rules — not applicable in next version
- **NEW UX requirements:** three-band color sort, expand-in-place interaction, Continue Listening bar, freshness-color encoding, Show All screen, Explore tag visual

**Technical / Code:**
- **Code removal:** queue-building logic, queue UI rendering in drive screen, queue persistence (if any)
- **Code reuse:** RSS feed, ExoPlayer, episode URL resolution, Spotify deep-link, tier management — all directly useful for new home screen
- **New code:** home screen three-band list, quick-play button wiring, expand-in-place UI, Continue Listening bar, Explore mode panel, Show All screen
- **Tests:** `DriveSessionScreenTest` requires rewrite or retirement; new test coverage for home screen components

### 2.4 Sprint & Timeline Impact

- **Current sprint:** Epic 4 (in-progress). Stories 4.3 (ready-for-dev) and 4.4 (backlog) halted immediately.
- **Work already complete:** 4.2 code removal required (technical debt to resolve before new work)
- **Epic 3 work complete:** kept as-built but not exercised by next version's nav
- **Timeline estimate:** Epic 8 is a fresh 10-story epic; depending on team velocity, multiple sprints. Epic 4 residual work (4.5 done, 4.6 done, 4.11 done — playback plumbing) is good news — quick-play button rides on existing playback code.

---

## Section 3 — Recommended Approach

### Path selected: **Hybrid — Partial Rollback + MVP Review**

**Not a good fit — Option 1 (Direct Adjustment):** Adding/modifying stories within existing epic structure cannot absorb the scale of this pivot. A new epic is required; existing epics need rescoping, not just amendment.

**Partial fit — Option 2 (Potential Rollback):** Story 4.2 code *should* be removed (rollback). But Epics 3's built work (drive session state machine, Bluetooth detection, manual trigger, drive dashboard) shouldn't be rolled back — leaving it in place as dead code is cheaper than reverting and risks breaking Epic 4 playback stories that interact with it.

**Partial fit — Option 3 (PRD MVP Review):** The MVP definitely needs restatement. FRs related to briefing + queue + drive-session-empty-state are deferred; new FRs required for the home-screen experience.

**Recommended hybrid:**
1. **Rollback:** Remove Story 4.2 production code (surgical removal, not full git revert — to preserve Story 4.5 adjacency work)
2. **Retire:** Story 4.3 (never built, clean remove from backlog)
3. **Rescope:** Epic 3 marked superseded (kept as-built, not in MVP nav); Epic 4 residual stories (4.4, 4.7) retired/redirected; Epic 5 redirected into Epic 8
4. **MVP review:** PRD restated — remove FR22/23, revise FR24/26/27/28, deprioritize FR14–20, add new home-screen FRs
5. **New epic:** Epic 8 — Podcast-List Home Screen (~10 stories)
6. **Architecture update:** simplify `DriveSessionState` or deprecate; document new use cases in `commonMain`
7. **UX update:** collapse three-mode navigation to one; deprioritize drive-specific components

### Justification

The pivot is coherent as a single proposal rather than piecemeal course-corrections. The user's brainstorming session produced an internally consistent design (8 core decisions + meta-principles) that should be realized as one epic. Piecemeal correction risks architectural drift — e.g., retiring 4.2 without retiring 4.3 leaves auto-advance in the backlog with no queue to advance through.

Surgical rollback of 4.2 (rather than full git revert) preserves the playback plumbing that stories 4.5, 4.6, 4.8–4.11 depend on and that Epic 8 will also leverage.

Accepting Epic 3's built work as "dead code kept for reuse" is pragmatic. Some of Epic 3's infrastructure (SQLDelight persistence patterns, `DriveSessionState` as a reference for state handling, Bluetooth detection as a future-version hook) retains optional value. Reverting these would cost effort without returning ROI.

### Effort & Risk

- **Effort:** High (new 10-story epic, PRD rewrite, UX spec update, code removal)
- **Risk:** Medium — most risk is in **what's not yet designed**: the deferred questions from the brainstorm (tap-target mis-tap between tile and ▶ button, tier promotion UX mechanics, failure modes for quick-play, Spotify offline behavior) need resolution during story creation, not during implementation
- **Timeline impact:** Current sprint work on Epic 4 (4.3, 4.4) halts. Epic 8 stories need creation before dev can resume. Budget ~1 planning sprint equivalent before code work restarts.

---

## Section 4 — Detailed Change Proposals

### 4.1 Sprint Status Changes (`sprint-status.yaml`)

```yaml
# EPIC 3 — mark as superseded (built stories remain done but not primary nav)
epic-3: superseded  # was: in-progress
# stories 3.1, 3.2, 3.3, 3.6: keep status=done; functionality retained but not in MVP nav
# stories 3.4, 3.5: keep status=deferred

# EPIC 4 — rescoped
epic-4: rescoped-pivot  # was: in-progress
4-2-priority-episode-queue: retired  # was: done — code removal pending
4-3-auto-advance-to-next-episode: retired  # was: ready-for-dev — never built
4-4-playback-position-resumption: redirected-to-epic-8  # was: backlog — replaced by 8.9 Continue Listening bar

# EPIC 5 — redirected
epic-5: redirected-to-epic-8  # was: backlog
5-1-empty-inbox-recommendation-generation: redirected-to-epic-8  # replaced by 8.10 Explore mode
5-2-hands-free-accept-or-decline-of-recommendation: deferred  # stays deferred (drive-session-specific)

# NEW EPIC 8 — Podcast-List Home Screen
epic-8: backlog
8-1-three-band-podcast-list-sort: backlog
8-2-no-unlistened-invisible-filter: backlog
8-3-per-tile-quick-play-button: backlog
8-4-expand-in-place-drill-down: backlog
8-5-unlistened-count-freshness-color: backlog
8-6-manual-tier-promotion-from-tile: backlog
8-7-show-all-library-screen: backlog
8-8-driver-friendly-visual-baseline: backlog
8-9-continue-listening-bar: backlog
8-10-home-screen-explore-mode: backlog
epic-8-retrospective: optional
```

### 4.2 PRD Changes (`prd.md`) — high-level

**Remove:**
- FR22 (queue in priority order)
- FR23 (auto-advance to next episode)

**Revise:**
- FR24 — "resume from last known position" → reframed as **Continue Listening bar** on home screen (not drive-session-triggered)
- FR26, FR27, FR28 — empty-inbox recommendation → reframed as home-screen **Explore mode** (visual tag, no TTS, no steering-wheel confirmation)

**Deprioritize (mark "not in next version"):**
- FR14, FR15, FR16, FR17, FR18 (briefing generation and delivery)
- FR19, FR20 (hands-free confirmation, audio ducking)

**Add (new FRs for home screen):**
- `FR_NEW_1`: Home screen displays a sorted list of subscribed podcasts filtered to those with unlistened episodes
- `FR_NEW_2`: Podcasts are sorted in three color-differentiated bands — LOVED (top), LIKED (middle), OTHER (bottom) — then by latest unlistened episode date within each band
- `FR_NEW_3`: Each podcast tile displays an unlistened-episode count, color-encoded by the freshness of the newest unlistened episode
- `FR_NEW_4`: Each podcast tile exposes a quick-play button that starts the most recent unlistened episode
- `FR_NEW_5`: Tapping a podcast tile (outside the quick-play button) expands the tile in place to reveal unlistened episodes, newest first; tap again to collapse
- `FR_NEW_6`: A "Continue Listening" bar appears above the list when an episode is in-progress; single tap resumes
- `FR_NEW_7`: When no subscribed podcast has unlistened episodes, the home screen enters Explore mode presenting recommended podcasts with a visual `explore` tag
- `FR_NEW_8`: A "Show All" affordance opens a full library view for subscription management
- `FR_NEW_9`: Tier promotion (OTHER → LIKED → LOVED) is accessible from each podcast tile
- `FR_NEW_10`: All home-screen visual elements meet driver-friendly sizing and contrast standards universally (no separate drive mode)

**MVP restatement:** next version's MVP = Epics 1, 2 (already done) + Epic 4 playback plumbing (already done: 4.1, 4.5, 4.6, 4.8–4.11) + **Epic 8 Core MVP stories (8.1–8.8)**. Fast-follow: 8.9, 8.10.

### 4.3 Architecture Changes (`architecture.md`) — high-level

- Remove `BuildEpisodeQueueUseCase` (AR2)
- Add use cases to `commonMain`: `GetSortedPodcastsUseCase`, `GetLatestUnlistenedUseCase`, `GetUnlistenedCountUseCase`, `GetContinueListeningUseCase`, `GetExploreRecommendationsUseCase`, `PromoteTierUseCase`
- Review `DriveSessionState` (AR6) — sealed class may simplify or deprecate; state machine no longer load-bearing in next version
- Review drive-session SQLDelight tables — queue-related columns/tables removable; `PlaybackState` table retained for Continue Listening
- AR7 (Android Foreground Service / iOS Background Audio) — keep for in-app playback but not session-scoped

### 4.4 UX Design Spec Changes (`ux-design-specification.md`) — high-level

- UX-DR3 collapses to single-mode navigation (no drive-mode switch)
- UX-DR4 drive dashboard obsolete in MVP nav
- UX-DR5 drive-specific components deprioritized (kept built for optional future re-use)
- UX-DR6 `ShowTile` revised: add quick-play button + unlistened count with freshness color
- UX-DR8 TTS-first feedback rules do not apply
- **New UX requirements needed:**
  - Three-band color sort specification (color tokens per band, visual boundary design)
  - Expand-in-place interaction spec (animation, collapsed vs. expanded state, tap-target mitigation between tile-tap and ▶-button-tap)
  - Freshness-color encoding spec (color ramp, breakpoints: today / this-week / older)
  - Continue Listening bar spec (height, content, tap behavior)
  - Explore mode spec (card layout, `explore` tag visual, subscribe flow)
  - Show All screen spec (full library browse + management actions)
  - Tier promotion UX on tile (inline button vs. long-press menu — DEFERRED DECISION from brainstorm, to be resolved in Epic 8 story creation)

---

## Section 5 — Implementation Handoff

### Scope classification: **Major**

This is a **fundamental replan** — new epic, PRD rewrite, UX spec rewrite, architecture review. Not implementable without PM/Architect alignment first.

### Handoff sequence

1. **PM / Architect** (first handoff) — own:
   - PRD revision (remove/revise/add FRs per Section 4.2)
   - Architecture doc update (use cases, state model, DB schema review)
   - Approval of Epic 8 shape (10 stories as listed vs. different decomposition)

2. **UX Designer** (parallel to 1) — own:
   - UX spec revision for UX-DR3 through UX-DR8
   - NEW UX specifications for the seven items listed in Section 4.4
   - Resolve deferred design questions from brainstorm: tap-target mis-tap, tier promotion mechanics, failure modes, Explore content source

3. **Scrum Master / Product Owner** (after 1–2) — own:
   - Run `bmad-create-epics-and-stories` against revised PRD to formalize Epic 8 stories
   - Update `sprint-status.yaml` per Section 4.1
   - Sprint re-planning for next iteration

4. **Development team** (after 3) — own:
   - Technical task: surgical removal of Story 4.2 production code (queue-building, queue UI rendering, queue persistence if present). Recommend own branch/PR with clear reference to this proposal.
   - Implementation of Epic 8 stories as formalized in step 3
   - Test coverage updates (`DriveSessionScreenTest` rewrite/retirement; new home screen component tests)

### Success criteria

- PRD, Architecture, UX Design Spec all updated and internally consistent with the pivot
- Epic 8 stories created with acceptance criteria resolving all deferred brainstorm questions
- Story 4.2 production code removed with no regressions in Story 4.5, 4.6, 4.8–4.11 functionality
- `sprint-status.yaml` reflects new state (Epics 3/4/5 rescoped; Epic 8 created)
- All changes traceable back to brainstorming-session-2026-04-24-1200.md

---

## Section 6 — Deferred Decisions (carried forward from brainstorm)

These questions were flagged in the brainstorming session as unresolved and must be answered during Epic 8 story creation or UX specification — not during implementation:

1. **Tap-target mis-tap** — tile (expand) vs. ▶ button (quick-play) on the same row. Needs design resolution (distinct hit zones, confirmation gesture, or visual differentiation).
2. **Tier promotion mechanism** — inline button on tile vs. long-press menu vs. Show-All-only.
3. **Long-press semantics** — is long-press used at all? For what?
4. **Explore mode content source** — what drives recommendations? (Current Story 5.1 used on-device listening history; same source, different surface.)
5. **Failure modes** — offline, Spotify auth expired, RSS fetch failure, zero episodes in Explore. How do these render on the new home screen?
6. **Fate of Epic 3 built code** — dead code kept for future re-use, or removed entirely? This proposal recommends keep-as-built; revisit if maintenance cost grows.
7. **Continue Listening bar vs. pure list mode** — if bar is fast-follow rather than core, does MVP launch look broken without it? (Bar is flagged fast-follow in brainstorm; re-examine priority during sprint planning.)

---

## Appendix — Source Artifacts

- Brainstorming session: [brainstorming-session-2026-04-24-1200.md](../brainstorming/brainstorming-session-2026-04-24-1200.md)
- Current epics: [epics.md](epics.md)
- Current sprint status: [sprint-status.yaml](../implementation-artifacts/sprint-status.yaml)
- Retired story: [4-2-priority-episode-queue.md](../implementation-artifacts/4-2-priority-episode-queue.md)
- Retired story: [4-3-auto-advance-to-next-episode.md](../implementation-artifacts/4-3-auto-advance-to-next-episode.md)
