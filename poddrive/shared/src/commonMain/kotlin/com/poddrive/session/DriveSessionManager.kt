package com.poddrive.session

import com.poddrive.currentTimeMillis
import com.poddrive.domain.model.BriefingScript
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.repository.DriveSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the drive session state machine. The ONLY class that may mutate [DriveSessionState] (AR6).
 *
 * Design rules:
 * - **Persist-before-emit**: [DriveSessionRepository.saveState] is called BEFORE [_state].value
 *   is updated, so crash recovery always sees the correct state (AR6).
 * - **Mutex**: all transitions run inside [mutex] to prevent race conditions from concurrent
 *   [dispatch] calls (e.g. simultaneous BT event + manual trigger).
 * - **Fire-and-forget**: [dispatch] launches a coroutine internally — callers do NOT need to
 *   wrap it in a coroutine scope or `viewModelScope.launch`.
 * - **SupervisorJob**: the default scope uses [SupervisorJob] so a repository exception in one
 *   dispatch does not cancel the scope and disable all future dispatches (P-4).
 * - **Repository failure safety**: if the repository throws during persist-before-emit, the
 *   transition is aborted — [currentSessionId] and [_state] are never mutated (P-2).
 * - **Testability**: [resolveTransition] is `internal` for white-box unit testing; [scope] is
 *   injected so tests can pass a [kotlinx.coroutines.test.TestScope].
 *
 * [Source: architecture.md#Communication Patterns, AR6; story 3.1, story 4.5]
 */
class DriveSessionManager(
    private val repository: DriveSessionRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val timeSource: () -> Long = { currentTimeMillis() }
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DriveSessionState>(DriveSessionState.Idle)

    /** Reactive session state. Observe in platform ViewModels via `collectAsState()`. */
    val state: StateFlow<DriveSessionState> = _state.asStateFlow()

    /**
     * Assigned on first successful transition out of Idle; reset to null when the session
     * transitions to [DriveSessionState.Ended] so a future session gets a fresh ID (P-1).
     */
    private var currentSessionId: String? = null

    /**
     * Stored to support [DriveSessionState.Paused] → [DriveSessionState.Playing] restoration.
     * Cleared when the session transitions to [DriveSessionState.Ended] or
     * [DriveSessionState.Error] to prevent stale episode data leaking into a new session (P-5).
     * Session-scoped only — not persisted to SQLDelight in MVP (full restore is Story 4.4).
     */
    private var lastPlayingState: DriveSessionState.Playing? = null

    /**
     * Dispatches an event to the state machine. Fire-and-forget — returns immediately.
     * If the event causes no valid transition from the current state, it is silently ignored.
     * If the repository throws during persist-before-emit, the transition is rolled back and
     * the state machine stays at the current state (P-2).
     */
    fun dispatch(event: DriveSessionEvent) {
        scope.launch {
            mutex.withLock {
                val next = resolveTransition(_state.value, event) ?: return@withLock
                val now = timeSource()  // P-6: single capture — used for session ID and startedAtMs
                val isNewSession = currentSessionId == null
                val sessionId = currentSessionId ?: "session-$now"

                // Persist-before-emit (AR6): write to SQLDelight BEFORE updating the StateFlow.
                // P-2: on repository failure, bail out without mutating currentSessionId or state.
                try {
                    when {
                        isNewSession -> repository.saveNewSession(sessionId, now, next)
                        next is DriveSessionState.Ended -> {
                            // P-7: persist the Ended state then stamp ended_at
                            repository.saveState(sessionId, next)
                            repository.endSession(sessionId, now)
                        }
                        else -> repository.saveState(sessionId, next)
                    }
                } catch (e: Exception) {
                    // Repository failure: state machine stays at current state.
                    // currentSessionId is NOT mutated — the failed row was never committed (P-2).
                    return@withLock
                }

                // Commit the session ID only after a successful persist (P-2).
                if (isNewSession) currentSessionId = sessionId

                // P-1 + P-5: reset session-scoped state on terminal transition so a future
                // session (if Ended → Idle is ever added) starts clean.
                if (next is DriveSessionState.Ended) {
                    currentSessionId = null
                    lastPlayingState = null
                }

                if (next is DriveSessionState.Playing) lastPlayingState = next
                if (next is DriveSessionState.Error) lastPlayingState = null  // P-5

                _state.value = next
            }
        }
    }

    /**
     * Cancels the internal coroutine scope. Call when the manager is no longer needed
     * (e.g. clean shutdown in tests). Has no effect if the scope was injected externally (P-4).
     */
    fun cancel() {
        scope.cancel()
    }

    /**
     * Pure state-machine transition function. Returns the next state or null if the event
     * is not valid from [current]. Internal visibility enables unit testing of transitions
     * without coroutines (test [resolveTransition] directly for exhaustive coverage).
     */
    internal fun resolveTransition(
        current: DriveSessionState,
        event: DriveSessionEvent
    ): DriveSessionState? = when (current) {
        is DriveSessionState.Idle -> when (event) {
            is DriveSessionEvent.BluetoothConnected -> DriveSessionState.Activating
            is DriveSessionEvent.ManualTrigger      -> DriveSessionState.Activating  // Story 3.3
            else                                    -> null
        }
        is DriveSessionState.Activating -> when (event) {
            is DriveSessionEvent.BriefingConfirmed     -> DriveSessionState.Briefing(BriefingScript(""))
            // Story 4.10: ExoPlayer started playing an episode in-app → transition to Playing.
            is DriveSessionEvent.EpisodeStarted        -> DriveSessionState.Playing(event.episode, 0L)
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed        -> DriveSessionState.Error(event.error)  // Story 4.5
            else                                       -> null
        }
        is DriveSessionState.Briefing -> when (event) {
            // IG-1: Briefing → Playing transition is pending Story 3.4.
            // A "BriefingComplete" event (or equivalent) will be added when TTS wiring is
            // implemented. Until then, the only non-failure exits from Briefing are SkipEpisode
            // and connection loss. [Source: architecture.md#Communication Patterns, Story 3.4]
            is DriveSessionEvent.SkipEpisode           -> DriveSessionState.Activating
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed        -> DriveSessionState.Error(event.error)  // Story 4.5
            else                                       -> null
        }
        is DriveSessionState.Playing -> when (event) {
            is DriveSessionEvent.PauseSession          -> DriveSessionState.Paused
            is DriveSessionEvent.SkipEpisode           -> DriveSessionState.Activating
            // Story 4.10: User taps a different episode while one is playing — replace it.
            is DriveSessionEvent.EpisodeStarted        -> DriveSessionState.Playing(event.episode, 0L)
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed        -> DriveSessionState.Error(event.error)  // Story 4.5
            else                                       -> null
        }
        is DriveSessionState.Paused -> when (event) {
            // Restores last playing episode; falls back to Activating if no episode was stored.
            // IG-2: PlaybackFailed is also valid from Paused (e.g. Spotify disconnects mid-pause).
            // AC1 in Story 4.5 listed Activating/Briefing/Playing; Paused is included here as
            // a deliberate extension — spec updated accordingly.
            is DriveSessionEvent.ResumeSession         -> lastPlayingState ?: DriveSessionState.Activating
            is DriveSessionEvent.SkipEpisode           -> DriveSessionState.Activating  // P5: allow skip while paused
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed        -> DriveSessionState.Error(event.error)  // Story 4.5, IG-2
            else                                       -> null
        }
        is DriveSessionState.Error -> when (event) {
            is DriveSessionEvent.RetryPlayback         -> DriveSessionState.Activating  // Story 4.5
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            else                                       -> null
        }
        is DriveSessionState.Ended -> null  // Terminal state — no valid transitions out
    }
}
