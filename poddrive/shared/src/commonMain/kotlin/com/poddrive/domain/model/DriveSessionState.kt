package com.poddrive.domain.model

/**
 * Sealed class representing all states in the drive session lifecycle.
 * Only DriveSessionManager (Story 3.1) may mutate this state.
 * Every transition must be persisted to SQLDelight BEFORE being emitted to observers
 * (persist-before-emit rule, AR6).
 *
 * Full state machine: Idle → Activating → Briefing → Playing → Paused → Error → Ended
 * States beyond Idle and Activating are completed in Story 3.1.
 * [Source: architecture.md#Communication Patterns, AR6]
 */
sealed class DriveSessionState {
    object Idle : DriveSessionState()
    object Activating : DriveSessionState()
    // Remaining states (Briefing, Playing, Paused, Error, Ended) added in Story 3.1
}
