package com.poddrive.session

import com.poddrive.currentTimeMillis
import com.poddrive.domain.model.BriefingScript
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.repository.DriveSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * - **Testability**: [resolveTransition] is `internal` for white-box unit testing; [scope] is
 *   injected so tests can pass a [kotlinx.coroutines.test.TestScope].
 *
 * [Source: architecture.md#Communication Patterns, AR6; story 3.1, story 4.5]
 */
class DriveSessionManager(
    private val repository: DriveSessionRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val timeSource: () -> Long = { currentTimeMillis() }
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DriveSessionState>(DriveSessionState.Idle)

    /** Reactive session state. Observe in platform ViewModels via `collectAsState()`. */
    val state: StateFlow<DriveSessionState> = _state.asStateFlow()

    /** Assigned on first transition out of Idle; persisted with every subsequent transition. */
    private var currentSessionId: String? = null

    /**
     * Stored to support [DriveSessionState.Paused] → [DriveSessionState.Playing] restoration.
     * Session-scoped only — not persisted to SQLDelight in MVP (full restore is Story 4.4).
     */
    private var lastPlayingState: DriveSessionState.Playing? = null

    /**
     * Dispatches an event to the state machine. Fire-and-forget — returns immediately.
     * If the event causes no valid transition from the current state, it is silently ignored.
     */
    fun dispatch(event: DriveSessionEvent) {
        scope.launch {
            mutex.withLock {
                val next = resolveTransition(_state.value, event) ?: return@withLock
                val sessionId = currentSessionId
                    ?: "session-${timeSource()}".also { currentSessionId = it }
                // Persist-before-emit (AR6): write to SQLDelight BEFORE updating the StateFlow
                if (_state.value is DriveSessionState.Idle) {
                    repository.saveNewSession(sessionId, timeSource(), next)
                } else {
                    repository.saveState(sessionId, next)
                }
                if (next is DriveSessionState.Playing) lastPlayingState = next
                _state.value = next
            }
        }
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
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed        -> DriveSessionState.Error(event.error)  // Story 4.5
            else                                       -> null
        }
        is DriveSessionState.Briefing -> when (event) {
            is DriveSessionEvent.SkipEpisode           -> DriveSessionState.Activating
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed        -> DriveSessionState.Error(event.error)  // Story 4.5
            else                                       -> null
        }
        is DriveSessionState.Playing -> when (event) {
            is DriveSessionEvent.PauseSession          -> DriveSessionState.Paused
            is DriveSessionEvent.SkipEpisode           -> DriveSessionState.Activating
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed        -> DriveSessionState.Error(event.error)  // Story 4.5
            else                                       -> null
        }
        is DriveSessionState.Paused -> when (event) {
            // Restores last playing episode; falls back to Activating if no episode was stored
            is DriveSessionEvent.ResumeSession         -> lastPlayingState ?: DriveSessionState.Activating
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            is DriveSessionEvent.PlaybackFailed        -> DriveSessionState.Error(event.error)  // Story 4.5
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
