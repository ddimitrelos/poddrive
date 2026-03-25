package com.poddrive.session

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import com.poddrive.repository.DriveSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for the drive session lifecycle.
 *
 * Architecture rules (must never be violated):
 * - ONLY this class may mutate [DriveSessionState] (AR6).
 * - Every state change is persisted to SQLDelight BEFORE being emitted to StateFlow observers
 *   (persist-before-emit rule, AR6).
 * - [dispatch] is non-suspending; transitions run inside [scope].
 * - [Mutex] prevents concurrent mutations — only one transition fires at a time.
 *
 * State machine:
 *   Idle ──(BluetoothConnected/ManualTrigger)──→ Activating ──(auto)──→ Briefing
 *   Briefing ──(BriefingConfirmed)──→ Playing ──(SkipEpisode)──→ Playing(next) | Ended
 *                                              ──(PauseSession)──→ Paused
 *                                              ──(PlaybackFailed)──→ Error
 *   Paused ──(ResumeSession)──→ Playing
 *   Error ──(RetryPlayback)──→ Playing(lastEpisode)
 *   Any ──(BluetoothDisconnected)──→ Ended
 *
 * [Source: architecture.md#Communication Patterns, AR6]
 * [Stories 4.2, 4.5]
 */
class DriveSessionManager(
    private val sessionRepository: DriveSessionRepository,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow<DriveSessionState>(DriveSessionState.Idle)
    val state: StateFlow<DriveSessionState> = _state.asStateFlow()

    /** Episode queue loaded before the session enters Briefing state. */
    private var currentQueue: List<Episode> = emptyList()

    /** Retained so Error + RetryPlayback can restore the last playing episode. */
    private var lastPlayingEpisode: Episode? = null

    /** Load the episode queue — called by ViewModel after BuildEpisodeQueueUseCase completes. */
    fun loadQueue(episodes: List<Episode>) {
        currentQueue = episodes
    }

    /**
     * Dispatch an event to the state machine.
     * Non-suspending — the transition launches inside [scope].
     * Safe to call from any thread.
     */
    fun dispatch(event: DriveSessionEvent) {
        scope.launch {
            mutex.withLock {
                val current = _state.value
                val next = resolveTransition(current, event) ?: return@withLock
                // persist-before-emit (AR6)
                sessionRepository.persistState(SESSION_ID, next)
                _state.value = next
                // Activating → Briefing auto-advance (stub; real TTS is Story 3.4)
                if (next is DriveSessionState.Activating) {
                    val briefing = DriveSessionState.Briefing("Ready to drive.")
                    sessionRepository.persistState(SESSION_ID, briefing)
                    _state.value = briefing
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // State machine
    // ---------------------------------------------------------------------------

    private fun resolveTransition(
        state: DriveSessionState,
        event: DriveSessionEvent
    ): DriveSessionState? = when (state) {

        is DriveSessionState.Idle -> when (event) {
            is DriveSessionEvent.BluetoothConnected -> DriveSessionState.Activating
            is DriveSessionEvent.ManualTrigger -> DriveSessionState.Activating
            else -> null
        }

        is DriveSessionState.Activating -> null // auto-transitions in dispatch(); no event needed

        is DriveSessionState.Briefing -> when (event) {
            is DriveSessionEvent.BriefingConfirmed -> {
                val first = currentQueue.firstOrNull()
                if (first != null) {
                    lastPlayingEpisode = first
                    DriveSessionState.Playing(first, 0L)
                } else {
                    DriveSessionState.Ended
                }
            }
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            else -> null
        }

        is DriveSessionState.Playing -> {
            lastPlayingEpisode = state.episode // always track for RetryPlayback
            when (event) {
                is DriveSessionEvent.SkipEpisode -> {
                    val idx = currentQueue.indexOf(state.episode)
                    val next = currentQueue.getOrNull(idx + 1)
                    if (next != null) {
                        lastPlayingEpisode = next
                        DriveSessionState.Playing(next, 0L)
                    } else {
                        DriveSessionState.Ended
                    }
                }
                is DriveSessionEvent.PauseSession -> DriveSessionState.Paused
                is DriveSessionEvent.PlaybackFailed ->
                    DriveSessionState.Error(AppError.Unknown(Exception("Spotify playback failed")))
                is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
                else -> null
            }
        }

        is DriveSessionState.Paused -> when (event) {
            is DriveSessionEvent.ResumeSession -> {
                val episode = lastPlayingEpisode ?: return null
                DriveSessionState.Playing(episode, 0L)
            }
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            else -> null
        }

        is DriveSessionState.Error -> when (event) {
            is DriveSessionEvent.RetryPlayback -> {
                val episode = lastPlayingEpisode ?: return null
                DriveSessionState.Playing(episode, 0L)
            }
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            else -> null
        }

        is DriveSessionState.Ended -> null // terminal — requires new session

        else -> when (event) {
            is DriveSessionEvent.BluetoothDisconnected -> DriveSessionState.Ended
            else -> null
        }
    }

    companion object {
        const val SESSION_ID = "singleton"
    }
}
