package com.poddrive.android.ui.drive

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poddrive.android.PodDriveApplication
import com.poddrive.android.service.DriveSessionForegroundService
import com.poddrive.api.SpotifyAutoPlayCoordinator
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.audio.AudioPlayer
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI-local state for the drive dashboard. Kept separate from [DriveSessionState] so that
 * transient UI concerns (handoff flag, episode list, playback progress) never pollute the
 * state machine (AR6).
 *
 * [isHandingOff] — true from the moment the user taps an episode until Spotify is foregrounded
 *   or a timeout/error clears it. Drives the HANDING_OFF overlay on the drive screen.
 * [handoffEpisodeId] — the episode being handed off; null when not handing off (P-8).
 *   Tracked here so tile taps on non-first episodes are handled correctly.
 * [spotifyIntentLaunched] — true only after `startActivity` returned without throwing, confirming
 *   that the Spotify Intent was actually dispatched. Gates the ON_RESUME lifecycle observer in
 *   DriveSessionScreen so that returning from an unrelated interrupt (call, notification shade,
 *   permission dialog) does not prematurely trigger [DriveSessionViewModel.clearHandingOff].
 * [episodes] — current unplayed episode queue (reloaded after each successful handoff).
 *
 * Story 4.11 — in-app playback progress fields:
 * [isPlayingInApp] — true when ExoPlayer is actively playing; false when paused or stopped.
 * [playbackPositionMs] — polled from [com.poddrive.audio.AudioPlayer] every second while playing.
 * [playbackDurationMs] — total duration of the current episode (0 when no in-app session).
 * [playbackEpisodeTitle] — title of the episode currently playing in-app.
 *
 * [Source: Story 4.1 Dev Notes — HANDING_OFF is UI-local; Story 4.11; architecture.md#UI State Management]
 */
data class DriveUiState(
    val episodes: List<Episode> = emptyList(),
    val isHandingOff: Boolean = false,
    val handoffEpisodeId: String? = null,
    val spotifyIntentLaunched: Boolean = false,
    // Story 4.11: in-app playback progress
    val isPlayingInApp: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val playbackEpisodeTitle: String = "",
    val playbackPodcastName: String = ""
)

/**
 * Drive session ViewModel. Observes [com.poddrive.session.DriveSessionManager] state
 * and exposes it to the drive UI.
 *
 * ViewModels observe state — they NEVER mutate [DriveSessionState] directly (AR6).
 * All state mutations are dispatched via events to [com.poddrive.session.DriveSessionManager],
 * either directly (for session lifecycle events) or via the appropriate use case.
 *
 * Responsibilities:
 * - Manual session trigger (Story 3.3)
 * - Error retry (Story 4.5)
 * - Spotify deep-link handoff: HANDING_OFF state + 3s timeout (Story 4.1, AC1, AC3)
 * - Mark episode played + reload queue after successful handoff (Story 4.1, AC4)
 * - Episode auto-play via active-device polling (Story 4.7)
 * - In-app playback via ExoPlayer: routing, play/pause/resume/skip (Stories 4.10, 4.11)
 * - Playback position polling every 1 s while in-app episode is playing (Story 4.11)
 *
 * [coordinatorOverride] is provided only in tests; production uses a lazily-constructed
 * coordinator that closes over [_uiState] for the [isHandingOff] lambda.
 * [Source: architecture.md#UI State Management; stories 3.3, 4.1, 4.5, 4.7, 4.10, 4.11]
 */
class DriveSessionViewModel(
    app: Application,
    coordinatorOverride: SpotifyAutoPlayCoordinator? = null
) : AndroidViewModel(app) {

    // AndroidViewModelFactory uses reflection to find a (Application) constructor.
    // Kotlin default parameters do not generate that overload in bytecode, so we add
    // an explicit secondary constructor to satisfy the factory.
    constructor(app: Application) : this(app, null)

    // P-1: podApp accessed via getApplication() so PodDriveApplication.from() is called lazily.
    private val podApp: PodDriveApplication by lazy { getApplication() }

    /** Reactive drive session state — collect in Compose via [collectAsState()]. */
    val sessionState: StateFlow<DriveSessionState> by lazy {
        podApp.driveSessionManager.state
    }

    private val _uiState = MutableStateFlow(DriveUiState())

    // Story 4.7: Coordinator is created lazily so the isHandingOff lambda can close over
    // autoPlayEnabled — which is not available in the constructor default-parameter position.
    // coordinatorOverride is non-null only in unit tests (injected directly).
    // NOTE: the lambda checks autoPlayEnabled (not _uiState.isHandingOff) so that the 3s UI
    // handoff timeout does not prematurely kill polling on cold-start devices that need 3–4s
    // for Spotify to register as an active device.
    private val autoPlayCoordinator: SpotifyAutoPlayCoordinator by lazy {
        coordinatorOverride ?: SpotifyAutoPlayCoordinator(
            playerApiClient = podApp.playerApiClient,
            isHandingOff = { autoPlayEnabled }
        )
    }

    /** UI-local state: episode queue, handoff flag, handoff episode ID, playback progress. */
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    // Story 4.11: Position polling job — active while an in-app episode is playing.
    private var positionPollingJob: Job? = null

    init {
        // Story 4.11: Observe session state to start/stop position polling and update playback
        // progress fields in [_uiState]. Launched in init so polling is active as soon as the
        // ViewModel is created, independent of any composable lifecycle.
        viewModelScope.launch {
            podApp.driveSessionManager.state.collect { state ->
                when (state) {
                    is DriveSessionState.Playing -> {
                        val podcastName = runCatching {
                            withContext(Dispatchers.IO) {
                                podApp.podcastRepository.getById(state.episode.podcastId)?.name ?: ""
                            }
                        }.getOrDefault("")
                        _uiState.update { current ->
                            current.copy(
                                isPlayingInApp = true,
                                playbackDurationMs = state.episode.durationMs,
                                playbackEpisodeTitle = state.episode.title,
                                playbackPodcastName = podcastName
                            )
                        }
                        startPositionPolling()
                        // Start foreground service so Android keeps audio alive when screen locks
                        // (Story 4.10 AC5). Also initialises MediaSession for hardware media
                        // buttons and lock screen controls (Story 4.11 AC4).
                        DriveSessionForegroundService.startWithEpisode(
                            getApplication(), state.episode.title, podcastName
                        )
                    }
                    is DriveSessionState.Paused -> {
                        stopPositionPolling()
                        _uiState.update { it.copy(isPlayingInApp = false) }
                        // Keep the service alive while paused so the notification stays visible
                        // and MediaSession still responds to media buttons.
                    }
                    is DriveSessionState.Ended,
                    is DriveSessionState.Error -> {
                        stopPositionPolling()
                        _uiState.update {
                            it.copy(
                                isPlayingInApp = false,
                                playbackDurationMs = 0L,
                                playbackPositionMs = 0L,
                                playbackEpisodeTitle = "",
                                playbackPodcastName = ""
                            )
                        }
                        // Stop the foreground service — session is over, release WakeLock
                        // and remove the media notification.
                        DriveSessionForegroundService.stop(getApplication())
                    }
                    else -> { /* Idle, Activating, Briefing: no position polling needed */ }
                }
            }
        }
    }

    /** Active handoff timeout job — cancelled by [clearHandingOff] to prevent ghost transitions. */
    private var handoffTimeoutJob: Job? = null

    /**
     * Separate cancellation flag for the autoplay coroutine. Set to true at the start of every
     * handoff and only flipped to false on explicit failure paths (Spotify not installed, Intent
     * error). The UI handoff timeout does NOT clear this flag — Spotify cold-start can take
     * 3–4 s on slow devices, which would otherwise exit the polling loop just as the device
     * registers. The coordinator polls up to its own [DEVICE_POLL_TIMEOUT_MS] (5 s) deadline.
     */
    private var autoPlayEnabled = false

    /**
     * Routes an episode tap to the correct playback path (Story 4.10, AC1/AC2).
     *
     * - If [episode.audioUrl] is non-null → in-app path: ExoPlayer plays directly and
     *   [DriveSessionEvent.EpisodeStarted] transitions the session to [DriveSessionState.Playing].
     *   No Spotify Intent is fired.
     * - If [episode.audioUrl] is null → Spotify fallback: the composable layer should call
     *   [launchSpotifyEpisode] (i.e. this path is NOT reached via this function — the
     *   [DriveSessionScreen] composable routes to `launchSpotifyEpisode` when `audioUrl == null`).
     *
     * The routing decision is exposed as [shouldPlayInApp] for unit testing without Robolectric.
     *
     * [Source: Story 4.10, AC1/AC2; architecture.md#UI State Management]
     */
    fun onEpisodeTapped(episode: Episode) {
        // Capture locally — cross-module smart cast is not supported by the Kotlin compiler.
        val url = episode.audioUrl ?: return   // composable routes null-audioUrl to Spotify path
        Log.d(TAG, "onEpisodeTapped: in-app playback for episodeId=${episode.id} url=$url")
        // Story 4.4 is backlog — resume position defaults to 0 until that story provides it.
        podApp.audioPlayer.play(url, 0L)
        podApp.driveSessionManager.dispatch(DriveSessionEvent.EpisodeStarted(episode))
    }

    /**
     * Manually starts a drive session — fallback when Bluetooth auto-trigger is unavailable
     * (FR13, Story 3.3). Dispatches [DriveSessionEvent.ManualTrigger] to the session manager.
     */
    fun startManualDriveSession() {
        podApp.driveSessionManager.dispatch(DriveSessionEvent.ManualTrigger)
    }

    /**
     * Retries Spotify playback after a failure (Story 4.5, AC5).
     * Delegates to [com.poddrive.usecase.HandlePlaybackFailureUseCase.retry] which dispatches
     * [DriveSessionEvent.RetryPlayback] — state transitions Error → Activating.
     * The caller is responsible for re-initiating the Spotify deep-link handoff (Story 4.1).
     */
    fun retryPlayback() {
        podApp.handlePlaybackFailureUseCase.retry()
    }

    // -------------------------------------------------------------------------
    // Story 4.11: In-app playback controls — pause / resume / skip
    // -------------------------------------------------------------------------

    /**
     * Pauses in-app ExoPlayer playback (Story 4.11, AC2).
     * Calls [AudioPlayer.pause] then dispatches [DriveSessionEvent.PauseSession] so the state
     * machine transitions [DriveSessionState.Playing] → [DriveSessionState.Paused].
     *
     * Position persistence (AC6): Story 4.4 (playback position resumption) is backlog; position
     * is NOT written to the `playback_state` table yet. The 5-second periodic position saving
     * from Story 4.10 is the only persistence mechanism in the current sprint.
     */
    fun onPausePlayback() {
        Log.d(TAG, "onPausePlayback: pausing in-app audio")
        podApp.audioPlayer.pause()
        podApp.driveSessionManager.dispatch(DriveSessionEvent.PauseSession)
    }

    /**
     * Resumes in-app ExoPlayer playback after a pause (Story 4.11, AC2).
     * Calls [AudioPlayer.resume] then dispatches [DriveSessionEvent.ResumeSession] so the state
     * machine transitions [DriveSessionState.Paused] → [DriveSessionState.Playing].
     */
    fun onResumePlayback() {
        Log.d(TAG, "onResumePlayback: resuming in-app audio")
        podApp.audioPlayer.resume()
        podApp.driveSessionManager.dispatch(DriveSessionEvent.ResumeSession)
    }

    /**
     * Skips the current in-app episode and auto-advances to the next one (Story 4.11, AC3).
     *
     * Steps:
     * 1. Stop ExoPlayer (releases the player).
     * 2. Mark current episode as played so it is removed from the queue.
     * 3. Dispatch [DriveSessionEvent.SkipEpisode] → [DriveSessionState.Activating].
     * 4. Reload the episode queue.
     * 5. Auto-play the first remaining episode if it has an [Episode.audioUrl] (in-app path).
     *    If the next episode has no `audioUrl`, the session stays in `Activating` and the user
     *    can tap the Spotify fallback tile.
     */
    fun onSkipEpisode() {
        val currentState = sessionState.value
        Log.d(TAG, "onSkipEpisode: stopping current audio and skipping")
        podApp.audioPlayer.stop()
        podApp.driveSessionManager.dispatch(DriveSessionEvent.SkipEpisode)
        viewModelScope.launch {
            if (currentState is DriveSessionState.Playing) {
                val result = withContext(Dispatchers.IO) {
                    podApp.markEpisodePlayedUseCase.execute(currentState.episode.id)
                }
                if (result is com.poddrive.domain.model.Result.Failure) {
                    Log.w(TAG, "markEpisodePlayedUseCase failed during skip: ${result.error}")
                }
            }
            val queueResult = withContext(Dispatchers.IO) {
                podApp.buildEpisodeQueueUseCase.execute()
            }
            if (queueResult is com.poddrive.domain.model.Result.Success) {
                val episodes = queueResult.value.episodes
                _uiState.update { it.copy(episodes = episodes) }
                val next = episodes.firstOrNull()
                if (next != null && next.audioUrl != null) {
                    onEpisodeTapped(next)
                }
                // If next episode has no audioUrl: session stays in Activating, user sees
                // the queue and can tap the Spotify fallback tile.
            }
        }
    }

    // -------------------------------------------------------------------------
    // Position polling (Story 4.11, AC1)
    // -------------------------------------------------------------------------

    /**
     * Starts polling [com.poddrive.audio.AudioPlayer.currentPositionMs] every
     * [POSITION_POLL_INTERVAL_MS] milliseconds and writing the result to [_uiState].
     * Cancels any previously running polling job before starting a new one (P-3).
     */
    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = viewModelScope.launch {
            // `while (true)` is safe here: delay() is cancellation-cooperative and will throw
            // CancellationException when positionPollingJob is cancelled via stopPositionPolling().
            while (true) {
                _uiState.update { it.copy(playbackPositionMs = podApp.audioPlayer.currentPositionMs()) }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    /** Cancels the position polling coroutine. Safe to call if no polling is active. */
    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    /**
     * Marks an episode as finished (played) from the long-press context menu in the queue.
     * Removes the episode from the visible queue immediately after the DB write.
     */
    fun markEpisodeFinished(episodeId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                podApp.markEpisodePlayedUseCase.execute(episodeId)
            }
            if (result is com.poddrive.domain.model.Result.Failure) {
                Log.w(TAG, "markEpisodeFinished failed for episode $episodeId: ${result.error}")
            }
            loadEpisodeQueue()
        }
    }

    /** Loads (or reloads) the episode queue from the repository into [uiState]. */
    fun loadEpisodeQueue() {
        viewModelScope.launch {
            // P-2: DB read dispatched to IO to avoid blocking the main thread.
            val result = withContext(Dispatchers.IO) {
                podApp.buildEpisodeQueueUseCase.execute()
            }
            if (result is com.poddrive.domain.model.Result.Success) {
                _uiState.update { it.copy(episodes = result.value.episodes) }
            }
        }
    }

    /**
     * Called when the user taps an [com.poddrive.android.ui.drive.DriveSessionScreen] episode
     * tile or the "Open in Spotify" CTA (AC1).
     *
     * Sets [DriveUiState.isHandingOff] = true and starts two coroutines:
     * 1. A 3-second timeout job (AC3, UX-DR10) — fires [AppError.SpotifyHandoffTimeout] if the
     *    user has not returned to PodDrive and auto-play has not succeeded.
     * 2. An auto-play polling job (Story 4.7) — delegates to [SpotifyAutoPlayCoordinator.autoPlay].
     *    On success: [clearHandingOff] cancels the timeout and marks the episode played.
     *    On failure: silent fallback — the timeout continues (AC5).
     *
     * The composable layer fires the Intent immediately after calling this; if
     * [ActivityNotFoundException] is caught, it calls [onSpotifyNotInstalled] instead.
     */
    fun onOpenInSpotify(episodeId: String) {
        require(episodeId.isNotBlank()) { "episodeId must not be blank" }   // P-4
        Log.d(TAG, "onOpenInSpotify: episodeId=$episodeId isHandingOff=${_uiState.value.isHandingOff}")
        // Guard against double-taps — if already handing off, ignore the second tap.
        if (_uiState.value.isHandingOff) return
        // P-3: Cancel the previous timeout job BEFORE setting isHandingOff=true to prevent a
        // narrow race where the old job reads the new isHandingOff=true and fires a spurious
        // PlaybackFailed dispatch.
        handoffTimeoutJob?.cancel()
        autoPlayEnabled = true
        _uiState.update { it.copy(isHandingOff = true, handoffEpisodeId = episodeId, spotifyIntentLaunched = false) }
        handoffTimeoutJob = viewModelScope.launch {
            delay(HANDOFF_TIMEOUT_MS)
            // Still handing off after 3 s → timeout error (AC3)
            if (_uiState.value.isHandingOff) {
                podApp.driveSessionManager.dispatch(
                    DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout)
                )
                _uiState.update { it.copy(isHandingOff = false, handoffEpisodeId = null, spotifyIntentLaunched = false) }
            }
        }
        // Story 4.7: auto-play via active-device polling (separate job — does not replace timeout).
        // The coordinator polls GET /player until device found, then fires PUT /player/play.
        //
        // Thread-safety note (P-1): both this coroutine and the timeout job above run on the
        // Main dispatcher (viewModelScope default). The Main dispatcher is single-threaded —
        // only one coroutine body executes at a time between suspension points. Therefore the
        // timeout job cannot be mid-execution when this coroutine's isHandingOff check runs,
        // and vice versa. The existing isHandingOff guards on both sides are sufficient; there
        // is no true concurrent race on the Main dispatcher.
        viewModelScope.launch {
            Log.d(TAG, "autoPlay coroutine starting for $episodeId")
            val success = autoPlayCoordinator.autoPlay(episodeId)
            autoPlayEnabled = false
            Log.d(TAG, "autoPlay returned success=$success isHandingOff=${_uiState.value.isHandingOff}")
            if (success) {
                if (_uiState.value.isHandingOff) {
                    // Normal path: autoplay beat the 3s timeout — cancel it and do full cleanup.
                    clearHandingOff()
                } else {
                    // Late-success path: the 3s UI timeout already fired and set the session to
                    // Error state, but Spotify did start playing (cold-start device). Mark the
                    // episode played and reload the queue so the UI reflects reality.
                    Log.d(TAG, "autoPlay late success — marking episode played after timeout")
                    viewModelScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            podApp.markEpisodePlayedUseCase.execute(episodeId)
                        }
                        if (result is com.poddrive.domain.model.Result.Failure) {
                            Log.w(TAG, "markEpisodePlayedUseCase failed (late): ${result.error}")
                        }
                        loadEpisodeQueue()
                    }
                }
            }
            // on failure: timeout job continues as graceful fallback
        }
    }

    /**
     * Called from the composable layer after [android.content.Intent.startActivity] returns
     * without throwing, confirming the Spotify Intent was actually dispatched.
     *
     * Sets [DriveUiState.spotifyIntentLaunched] = true, which gates the ON_RESUME lifecycle
     * observer so that returning from an unrelated interrupt (incoming call, permission dialog,
     * notification shade) does not prematurely trigger [clearHandingOff].
     */
    fun onSpotifyIntentLaunched() {
        if (!_uiState.value.isHandingOff) return
        _uiState.update { it.copy(spotifyIntentLaunched = true) }
    }

    /**
     * Called from the composable when [android.content.ActivityNotFoundException] is caught (AC2).
     * Dispatches [DriveSessionEvent.PlaybackFailed] with [AppError.SpotifyNotInstalled] then
     * clears handoff state.
     */
    fun onSpotifyNotInstalled() {
        // P-5: Guard — only dispatch if a handoff is actually in progress. Prevents spurious
        // PlaybackFailed dispatches from late broadcasts arriving outside of a handoff.
        if (!_uiState.value.isHandingOff) return
        autoPlayEnabled = false   // Spotify not installed — stop polling immediately
        podApp.driveSessionManager.dispatch(
            DriveSessionEvent.PlaybackFailed(AppError.SpotifyNotInstalled)
        )
        // Cancel the timeout job — error is already dispatched above
        handoffTimeoutJob?.cancel()
        _uiState.update { it.copy(isHandingOff = false, handoffEpisodeId = null, spotifyIntentLaunched = false) }
    }

    /**
     * Called from the composable when a general [Exception] is caught during Intent firing.
     * Dispatches [DriveSessionEvent.PlaybackFailed] with [AppError.Unknown] then clears state.
     */
    fun onPlaybackFailed() {
        // P-5: Guard — only dispatch if a handoff is actually in progress.
        if (!_uiState.value.isHandingOff) return
        autoPlayEnabled = false   // Intent failed — stop polling immediately
        podApp.driveSessionManager.dispatch(
            DriveSessionEvent.PlaybackFailed(AppError.Unknown(RuntimeException("Intent launch failed")))
        )
        handoffTimeoutJob?.cancel()
        _uiState.update { it.copy(isHandingOff = false, handoffEpisodeId = null, spotifyIntentLaunched = false) }
    }

    /**
     * Called when the Spotify handoff completes successfully (i.e. Spotify was foregrounded or
     * the auto-play API call succeeded).
     * Cancels the timeout job, marks the episode as played (AC4), and reloads the queue so
     * the episode is removed from the visible list.
     *
     * Idempotent: if [DriveUiState.isHandingOff] is already false (e.g. the timeout fired
     * first), this is a no-op — the DB write is skipped (P-7).
     */
    fun clearHandingOff() {
        // P-7: Idempotency guard — if isHandingOff is already false (timeout fired first, or
        // this was called twice), skip the DB write. All callers run on the Main dispatcher so
        // the read-check-clear sequence is not truly concurrent, but this guard makes the intent
        // explicit and protects against future dispatcher changes.
        if (!_uiState.value.isHandingOff) return
        autoPlayEnabled = false
        handoffTimeoutJob?.cancel()
        val episodeId = _uiState.value.handoffEpisodeId
        _uiState.update { it.copy(isHandingOff = false, handoffEpisodeId = null, spotifyIntentLaunched = false) }
        if (episodeId != null) {
            viewModelScope.launch {
                // DB write dispatched to IO. Queue reloads regardless of mark result so the UI
                // always reflects the true DB state (episode re-appears if write failed).
                val result = withContext(Dispatchers.IO) {
                    podApp.markEpisodePlayedUseCase.execute(episodeId)
                }
                if (result is com.poddrive.domain.model.Result.Failure) {
                    Log.w(TAG, "markEpisodePlayedUseCase failed for episode $episodeId: ${result.error}")
                }
                loadEpisodeQueue()
            }
        }
    }

    companion object {
        /** 3-second Spotify deep-link handoff timeout (UX-DR10, AC3). DO NOT CHANGE. */
        internal const val HANDOFF_TIMEOUT_MS = 3_000L
        /** Interval between ExoPlayer position reads while in-app audio is playing (Story 4.11, AC1). */
        internal const val POSITION_POLL_INTERVAL_MS = 1_000L
        private const val TAG = "DriveSessionViewModel"

        /**
         * Returns true when [episode] should be played in-app via ExoPlayer (Story 4.10, AC1).
         * Returns false when the Spotify deep-link fallback should be used (AC2).
         *
         * Exposed as an `internal` companion function so [DriveSessionViewModelTest] can
         * verify routing logic without instantiating [AndroidViewModel] or needing Robolectric.
         */
        internal fun shouldPlayInApp(episode: Episode): Boolean = episode.audioUrl != null
    }
}
