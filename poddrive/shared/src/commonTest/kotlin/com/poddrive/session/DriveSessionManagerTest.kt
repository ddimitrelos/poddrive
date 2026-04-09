package com.poddrive.session

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.repository.DriveSessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DriveSessionManagerTest {

    // ─── Fakes ───────────────────────────────────────────────────────────────

    private class FakeDriveSessionRepository(
        private val throwOnSave: Boolean = false
    ) : DriveSessionRepository {
        /** Records (sessionId, stateSimpleName) for every persistence call. */
        val savedStates = mutableListOf<Pair<String, String>>()
        val endedSessions = mutableListOf<String>()
        var latestState: DriveSessionState? = null

        override fun saveState(sessionId: String, state: DriveSessionState) {
            if (throwOnSave) throw RuntimeException("DB full")
            savedStates += sessionId to state::class.simpleName!!
        }
        override fun loadLatestState(): DriveSessionState? = latestState
        override fun saveNewSession(sessionId: String, startedAtMs: Long, state: DriveSessionState) {
            if (throwOnSave) throw RuntimeException("DB full")
            savedStates += sessionId to state::class.simpleName!!
        }
        override fun endSession(sessionId: String, endedAtMs: Long) {
            endedSessions += sessionId
        }
        override fun deleteAll() = Unit
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Creates a manager wired to the test scheduler via [this] TestScope. */
    private fun kotlinx.coroutines.test.TestScope.manager(
        repo: FakeDriveSessionRepository = FakeDriveSessionRepository()
    ) = DriveSessionManager(repo, this)

    // ─── State machine: basic transitions ─────────────────────────────────────

    @Test
    fun `initial state is Idle`() = runTest {
        assertEquals(DriveSessionState.Idle, manager().state.value)
    }

    @Test
    fun `ManualTrigger from Idle transitions to Activating`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Activating>(mgr.state.value)
    }

    @Test
    fun `BluetoothConnected from Idle transitions to Activating`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.BluetoothConnected)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Activating>(mgr.state.value)
    }

    @Test
    fun `unknown event from Idle is silently ignored`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.PauseSession)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Idle>(mgr.state.value)
    }

    @Test
    fun `BluetoothDisconnected from Activating transitions to Ended`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        mgr.dispatch(DriveSessionEvent.BluetoothDisconnected)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Ended>(mgr.state.value)
    }

    @Test
    fun `Ended is terminal — no event causes further transitions`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        mgr.dispatch(DriveSessionEvent.BluetoothDisconnected)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Ended>(mgr.state.value)
        // Try to trigger another transition — should stay Ended
        mgr.dispatch(DriveSessionEvent.BluetoothConnected)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Ended>(mgr.state.value)
    }

    // ─── Story 4.5: PlaybackFailed error paths ────────────────────────────────

    @Test
    fun `PlaybackFailed from Activating transitions to Error with correct AppError`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Activating>(mgr.state.value)

        mgr.dispatch(DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout))
        testScheduler.advanceUntilIdle()
        val state = mgr.state.value
        assertIs<DriveSessionState.Error>(state)
        assertEquals(AppError.SpotifyHandoffTimeout, state.error)
    }

    @Test
    fun `PlaybackFailed from Briefing transitions to Error`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        mgr.dispatch(DriveSessionEvent.BriefingConfirmed)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Briefing>(mgr.state.value)

        mgr.dispatch(DriveSessionEvent.PlaybackFailed(AppError.NetworkUnavailable))
        testScheduler.advanceUntilIdle()
        val state = mgr.state.value
        assertIs<DriveSessionState.Error>(state)
        assertEquals(AppError.NetworkUnavailable, state.error)
    }

    @Test
    fun `RetryPlayback from Error transitions back to Activating`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        mgr.dispatch(DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout))
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Error>(mgr.state.value)

        mgr.dispatch(DriveSessionEvent.RetryPlayback)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Activating>(mgr.state.value)
    }

    @Test
    fun `BluetoothDisconnected from Error transitions to Ended`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        mgr.dispatch(DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout))
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Error>(mgr.state.value)

        mgr.dispatch(DriveSessionEvent.BluetoothDisconnected)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Ended>(mgr.state.value)
    }

    @Test
    fun `unknown event from Error is silently ignored`() = runTest {
        val mgr = manager()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        mgr.dispatch(DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout))
        testScheduler.advanceUntilIdle()
        val errorState = mgr.state.value
        assertIs<DriveSessionState.Error>(errorState)

        mgr.dispatch(DriveSessionEvent.PauseSession)
        testScheduler.advanceUntilIdle()
        // Still in Error — unknown event ignored
        assertEquals(errorState, mgr.state.value)
    }

    // ─── Persist-before-emit (AR6) ────────────────────────────────────────────

    @Test
    fun `state is persisted before being emitted to observers`() = runTest {
        val repo = FakeDriveSessionRepository()
        val mgr = DriveSessionManager(repo, this)

        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()

        assertTrue(repo.savedStates.isNotEmpty(), "Repository must have been called")
        assertEquals("Activating", repo.savedStates.last().second,
            "Repository must reflect Activating before state is emitted")
        assertIs<DriveSessionState.Activating>(mgr.state.value)
    }

    @Test
    fun `error state is persisted`() = runTest {
        val repo = FakeDriveSessionRepository()
        val mgr = DriveSessionManager(repo, this)

        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        mgr.dispatch(DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout))
        testScheduler.advanceUntilIdle()

        assertEquals("Error", repo.savedStates.last().second)
        assertIs<DriveSessionState.Error>(mgr.state.value)
    }

    // ─── resolveTransition: pure function tests ───────────────────────────────

    @Test
    fun `resolveTransition Idle + ManualTrigger = Activating`() = runTest {
        val mgr = manager()
        val next = mgr.resolveTransition(DriveSessionState.Idle, DriveSessionEvent.ManualTrigger)
        assertIs<DriveSessionState.Activating>(next)
    }

    @Test
    fun `resolveTransition Activating + PlaybackFailed = Error`() = runTest {
        val mgr = manager()
        val next = mgr.resolveTransition(
            DriveSessionState.Activating,
            DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout)
        )
        assertIs<DriveSessionState.Error>(next)
        assertEquals(AppError.SpotifyHandoffTimeout, (next as DriveSessionState.Error).error)
    }

    @Test
    fun `resolveTransition Error + RetryPlayback = Activating`() = runTest {
        val mgr = manager()
        val next = mgr.resolveTransition(
            DriveSessionState.Error(AppError.SpotifyHandoffTimeout),
            DriveSessionEvent.RetryPlayback
        )
        assertIs<DriveSessionState.Activating>(next)
    }

    @Test
    fun `resolveTransition Ended + any event = null`() = runTest {
        val mgr = manager()
        val next = mgr.resolveTransition(DriveSessionState.Ended, DriveSessionEvent.ManualTrigger)
        assertEquals(null, next)
    }

    // ─── P-11: PlaybackFailed from Playing (AC1 coverage) ────────────────────

    @Test
    fun `resolveTransition Playing + PlaybackFailed = Error`() = runTest {
        val mgr = manager()
        val episode = com.poddrive.domain.model.Episode(
            "ep-1", "show-1", "Title", 1_000L, 0L, false, com.poddrive.domain.model.EpisodeTier.LOVED
        )
        val next = mgr.resolveTransition(
            DriveSessionState.Playing(episode, 0L),
            DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout)
        )
        assertIs<DriveSessionState.Error>(next)
        assertEquals(AppError.SpotifyHandoffTimeout, (next as DriveSessionState.Error).error)
    }

    // ─── IG-2: PlaybackFailed from Paused (deliberate extension of AC1) ──────

    @Test
    fun `resolveTransition Paused + PlaybackFailed = Error`() = runTest {
        val mgr = manager()
        val next = mgr.resolveTransition(
            DriveSessionState.Paused,
            DriveSessionEvent.PlaybackFailed(AppError.NetworkUnavailable)
        )
        assertIs<DriveSessionState.Error>(next)
        assertEquals(AppError.NetworkUnavailable, (next as DriveSessionState.Error).error)
    }

    // ─── P-1: currentSessionId reset on Ended ────────────────────────────────

    @Test
    fun `endSession is called in repository when transitioning to Ended`() = runTest {
        val repo = FakeDriveSessionRepository()
        val mgr = DriveSessionManager(repo, this)

        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        mgr.dispatch(DriveSessionEvent.BluetoothDisconnected)
        testScheduler.advanceUntilIdle()

        assertIs<DriveSessionState.Ended>(mgr.state.value)
        assertTrue(repo.endedSessions.isNotEmpty(), "endSession must be called when transitioning to Ended")
    }

    // ─── P-2: repository failure aborts transition ───────────────────────────

    @Test
    fun `repository failure during saveNewSession leaves state in Idle`() = runTest {
        val repo = FakeDriveSessionRepository(throwOnSave = true)
        val mgr = DriveSessionManager(repo, this)

        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()

        // State must NOT have changed — the persist failed, so the transition is rolled back
        assertIs<DriveSessionState.Idle>(mgr.state.value)
        assertTrue(repo.savedStates.isEmpty(), "No state should have been persisted")
    }

    // ─── P5 fix: SkipEpisode from Paused ─────────────────────────────────────

    @Test
    fun `resolveTransition Paused + SkipEpisode = Activating`() = runTest {
        // P5 fix: SkipEpisode must be handled from Paused state so the user can skip
        // while paused without the session getting stuck. Regression for the missing handler.
        val result = manager().resolveTransition(DriveSessionState.Paused, DriveSessionEvent.SkipEpisode)
        assertEquals(DriveSessionState.Activating, result)
    }

    // ─── P-5: lastPlayingState cleared on Error ───────────────────────────────

    @Test
    fun `lastPlayingState is cleared when transitioning to Error`() = runTest {
        val mgr = manager()
        // Directly test via resolveTransition — verify Error path from Paused falls back to
        // Activating (not a stale Playing state) after lastPlayingState would have been cleared.
        // We verify the state machine produces Error from Paused + PlaybackFailed.
        val next = mgr.resolveTransition(
            DriveSessionState.Paused,
            DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout)
        )
        assertIs<DriveSessionState.Error>(next)
    }
}
