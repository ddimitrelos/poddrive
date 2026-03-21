package com.poddrive.domain.model

/**
 * Sealed class representing external inputs to the drive session state machine.
 * Platform layers dispatch events; only DriveSessionManager processes them.
 * Full event set completed in Story 3.1.
 * [Source: architecture.md#Communication Patterns, AR6]
 */
sealed class DriveSessionEvent {
    object BluetoothConnected : DriveSessionEvent()
    // Remaining events (BluetoothDisconnected, BriefingConfirmed, SkipEpisode,
    // PauseSession, ResumeSession) added in Story 3.1
}
