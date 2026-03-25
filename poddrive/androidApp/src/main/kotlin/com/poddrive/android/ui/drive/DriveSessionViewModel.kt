package com.poddrive.android.ui.drive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.poddrive.android.PodDriveApplication
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Drive session ViewModel. Observes [com.poddrive.session.DriveSessionManager] state
 * and exposes it to the drive UI.
 *
 * ViewModels observe state — they NEVER mutate [DriveSessionState] directly (AR6).
 * All state mutations are dispatched via events to [com.poddrive.session.DriveSessionManager].
 *
 * Full drive UI (DriveSessionCard, EpisodeTile list, etc.) is Story 3.6.
 * Manual session trigger is Story 3.3 (implemented here as the entry point).
 * Error retry is Story 4.5.
 *
 * [Source: architecture.md#UI State Management; stories 3.3, 4.5]
 */
class DriveSessionViewModel(app: Application) : AndroidViewModel(app) {

    private val podApp = PodDriveApplication.from(app)

    /** Reactive drive session state — collect in Compose via [collectAsState()]. */
    val sessionState: StateFlow<DriveSessionState> = podApp.driveSessionManager.state

    /**
     * Manually starts a drive session — fallback when Bluetooth auto-trigger is unavailable
     * (FR13, Story 3.3). Dispatches [DriveSessionEvent.ManualTrigger] to the session manager.
     *
     * Note: [DriveSessionForegroundService] start is handled in the full Story 3.2 wiring.
     */
    fun startManualDriveSession() {
        podApp.driveSessionManager.dispatch(DriveSessionEvent.ManualTrigger)
    }

    /**
     * Retries Spotify playback after a failure (Story 4.5).
     * Dispatches [DriveSessionEvent.RetryPlayback] — state transitions Error → Activating.
     * The caller is responsible for re-initiating the Spotify deep-link handoff (Story 4.1).
     */
    fun retryPlayback() {
        podApp.driveSessionManager.dispatch(DriveSessionEvent.RetryPlayback)
    }
}
