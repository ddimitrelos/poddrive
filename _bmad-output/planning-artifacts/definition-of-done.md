# PodDrive — Definition of Done

_Created: 2026-03-19_

---

## Story Definition of Done

A story is **done** when ALL of the following are true:

### Code Quality
- [ ] Code compiles with no TypeScript errors (`strict: true`, no implicit `any`)
- [ ] `npm run lint` passes with no errors or warnings
- [ ] `npm run build` completes successfully
- [ ] No anti-patterns from `project-context.md` are present (no `Math.random()` in Server Components, no `"use cache"` directive, no `tailwind.config.js`, etc.)

### Functionality
- [ ] All acceptance criteria defined in the story file are met
- [ ] Feature works end-to-end in the dev environment (`npm run dev`)
- [ ] Edge cases and error states defined in the story are handled
- [ ] No regressions introduced to previously completed stories

### Code Standards
- [ ] Only `"use client"` components use hooks, browser APIs, or event handlers
- [ ] Path alias `@/*` used for all internal imports
- [ ] External `fetch` calls wrapped in `try/catch` with appropriate fallback values
- [ ] No new npm packages added without explicit user approval
- [ ] No files created outside the agreed structure (`src/app/`, `_bmad-output/`, etc.)

### Review
- [ ] Story moved to `review` status in `sprint-status.yaml`
- [ ] Code review completed (via `bmad-code-review` workflow)
- [ ] Any review findings resolved or explicitly accepted

### Sign-off
- [ ] Story status updated to `done` in `sprint-status.yaml`

---

## Epic Definition of Done

An epic is **done** when ALL of the following are true:

- [ ] All stories within the epic are `done`
- [ ] Integration between stories within the epic has been verified end-to-end
- [ ] Epic status updated to `done` in `sprint-status.yaml`
- [ ] Retrospective conducted (or explicitly deferred — `optional` status acceptable)

---

## Project Definition of Done

The project is **done** when ALL of the following are true:

### All Epics Complete
- [ ] All 7 epics are `done`
- [ ] All 31 stories are `done`

### Technical Success Criteria (from PRD)
- [ ] Spotify token refresh, rate limits, and scope permissions handled reliably
- [ ] Bluetooth connection event triggers briefing within **≤10 seconds** on iOS and Android
- [ ] Background audio session survives phone lock, incoming calls, and navigation app audio
- [ ] Battery draw **≤5%** additional per 60-minute drive

### User Success Criteria (from PRD)
- [ ] Briefing completion rate **≥70%** of Bluetooth connection events
- [ ] Time-to-first-play **≤30 seconds** from Bluetooth connection to podcast audio
- [ ] User can go cold start → first hands-free episode in **≤5 minutes**
- [ ] User with 10 followed podcasts can hear a briefing and select an episode without unlocking phone
- [ ] **Zero critical crashes** during a 60-minute drive in standard conditions

### MVP Launch Criteria
- [ ] Validated on real devices in real driving conditions
- [ ] 3 active users confirmed

---

## Notes

- **No automated test suite** is currently configured. Story acceptance verification is manual or via dev server smoke testing until a testing framework is explicitly adopted.
- This document should be reviewed and updated if the technology stack changes or a testing framework is added.
