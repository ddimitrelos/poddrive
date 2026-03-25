package com.poddrive.android.ui.drive

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poddrive.android.PodDriveApplication
import com.poddrive.android.service.DriveSessionForegroundService
import com.poddrive.briefing.TtsEngine
import com.poddrive.domain.model.Result
import com.poddrive.domain.model.onSuccess
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android ViewModel for the drive session screen.
 *
 * Responsibilities:
 * - Expose [sessionState] from [DriveSessionManager] for the UI
 * - Build and expose the episode [queue] via [BuildEpisodeQueueUseCase]
 * - Dispatch events to [DriveSessionManager] — never mutate state directly
 * - Manage the 3-second Spotify handoff timeout (Story 4.5, AC4)
 * - Speak TTS voice notifications on error (Story 4.5, AC2)
 *
 * Architecture rules:
 * - This class dispatches events; DriveSessionManager handles state transitions (AR6)
 * - No direct Spotify API calls — use cases only
 *
 * [Stories 3.3, 4.2, 4.5]
 */
class DriveSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PodDriveApplication.from(application)
    private val ttsEngine = TtsEngine(application)

    /** Mirrors DriveSessionManager.state for Compose collection. */
    val sessionState: StateFlow<DriveSessionState> = app.driveSessionManager.state

    /** Episode queue, populated once the session activates. */
    private val _queue = MutableStateFlow<List<Episode>>(emptyList())
    val queue: StateFlow<List<Episode>> = _queue.asStateFlow()

    /** True while BuildEpisodeQueueUseCase is running. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Cancellable 3-second timeout job for Spotify handoff (Story 4.5). */
    private var playbackTimeoutJob: Job? = null

    init {
        observeStateForSideEffects()
    }

    // ---------------------------------------------------------------------------
    // Public actions
    // ---------------------------------------------------------------------------

    /**
     * Manual session start from HomeScreen button (Story 3.3, FR13).
     * Dispatches ManualTrigger and starts the foreground service.
     */
    fun startManualDriveSession() {
        app.driveSessionManager.dispatch(DriveSessionEvent.ManualTrigger)
        getApplication<Application>().startService(
            Intent(getApplication(), DriveSessionForegroundService::class.java)
        )
    }

    /**
     * Bluetooth-triggered session start (Story 3.2 — called by BluetoothTrigger actual).
     */
    fun startDriveSession() {
        app.driveSessionManager.dispatch(DriveSessionEvent.BluetoothConnected)
        getApplication<Application>().startService(
            Intent(getApplication(), DriveSessionForegroundService::class.java)
        )
    }

    /**
     * Confirm the briefing to begin playback (AC1 of Story 4.2).
     * Also dispatches the Spotify deep-link and starts the 3-second handoff timeout.
     */
    fun confirmBriefing() {
        app.driveSessionManager.dispatch(DriveSessionEvent.BriefingConfirmed)
        val firstEpisode = _queue.value.firstOrNull() ?: return
        launchSpotifyDeepLink(firstEpisode)
        startPlaybackTimeout()
    }

    /** Retry after a playback failure — re-dispatches the episode deep-link (Story 4.5, AC5). */
    fun retryPlayback() {
        playbackTimeoutJob?.cancel()
        val state = sessionState.value
        if (state is DriveSessionState.Error) {
            app.driveSessionManager.dispatch(DriveSessionEvent.RetryPlayback)
            val playing = app.driveSessionManager.state.value
            if (playing is DriveSessionState.Playing) {
                launchSpotifyDeepLink(playing.episode)
                startPlaybackTimeout()
            }
        }
    }

    fun skipEpisode() = app.driveSessionManager.dispatch(DriveSessionEvent.SkipEpisode)
    fun pauseSession() = app.driveSessionManager.dispatch(DriveSessionEvent.PauseSession)
    fun resumeSession() = app.driveSessionManager.dispatch(DriveSessionEvent.ResumeSession)

    override fun onCleared() {
        super.onCleared()
        ttsEngine.shutdown()
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /** Observe state changes for side effects (load queue, TTS). */
    private fun observeStateForSideEffects() {
        viewModelScope.launch {
            sessionState.collect { state ->
                when (state) {
                    is DriveSessionState.Activating -> loadQueueAndFeedManager()
                    is DriveSessionState.Playing -> {
                        // Cancel timeout once we know we're in Playing state
                        playbackTimeoutJob?.cancel()
                    }
                    is DriveSessionState.Error -> {
                        playbackTimeoutJob?.cancel()
                        ttsEngine.speak("Could not connect. Tap to retry.")
                    }
                    else -> { /* no side effect */ }
                }
            }
        }
    }

    /** Build episode queue and load it into DriveSessionManager. */
    private fun loadQueueAndFeedManager() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = app.buildEpisodeQueueUseCase.execute()
            result.onSuccess { episodes ->
                _queue.value = episodes
                app.driveSessionManager.loadQueue(episodes)
            }
            _isLoading.value = false
        }
    }

    /**
     * Launch the Spotify deep-link for [episode].
     * Story 4.1 will refactor this into a proper use case.
     */
    private fun launchSpotifyDeepLink(episode: Episode) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(episode.id)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
            // Spotify not installed or deep-link failed — timeout will trigger PlaybackFailed
        }
    }

    /**
     * Start a 3-second timeout that fires [DriveSessionEvent.PlaybackFailed] if we are
     * still in [DriveSessionState.Playing] (meaning Spotify never acknowledged) (Story 4.5, AC4).
     *
     * Note: In the current MVP stub, we have no Spotify playback confirmation signal,
     * so the timeout is intentionally NOT started here to avoid false positives during testing.
     * It is wired but not auto-cancelled — a future story with Spotify SDK events can enable it.
     */
    private fun startPlaybackTimeout() {
        playbackTimeoutJob?.cancel()
        playbackTimeoutJob = viewModelScope.launch {
            delay(3_000L)
            // Only trigger if still in Playing state (no cancellation from SDK confirmation)
            if (sessionState.value is DriveSessionState.Playing) {
                app.driveSessionManager.dispatch(DriveSessionEvent.PlaybackFailed)
            }
        }
    }
}
