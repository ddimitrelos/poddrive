package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.repository.DriveSessionRepository
import com.poddrive.session.DriveSessionManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HandlePlaybackFailureUseCaseTest {

    // ─── Fakes ───────────────────────────────────────────────────────────────

    private class FakeDriveSessionRepository : DriveSessionRepository {
        val savedStates = mutableListOf<Pair<String, String>>()
        var latestState: DriveSessionState? = null

        override fun saveState(sessionId: String, state: DriveSessionState) {
            savedStates += sessionId to state::class.simpleName!!
        }
        override fun loadLatestState(): DriveSessionState? = latestState
        override fun saveNewSession(sessionId: String, startedAtMs: Long, state: DriveSessionState) {
            savedStates += sessionId to state::class.simpleName!!
        }
        override fun endSession(sessionId: String, endedAtMs: Long) = Unit
        override fun deleteAll() = Unit
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Creates a manager + use case wired to the test scheduler. */
    private fun kotlinx.coroutines.test.TestScope.makeUseCase(
        repo: FakeDriveSessionRepository = FakeDriveSessionRepository()
    ): Pair<DriveSessionManager, HandlePlaybackFailureUseCase> {
        val mgr = DriveSessionManager(repo, this)
        return mgr to HandlePlaybackFailureUseCase(mgr)
    }

    // ─── awaitHandoffOrTimeout ────────────────────────────────────────────────

    @Test
    fun `successful handoff within timeout returns Success and state unchanged`() = runTest {
        val (mgr, uc) = makeUseCase()
        // Start a session so we're in Activating
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Activating>(mgr.state.value)

        val result = uc.awaitHandoffOrTimeout { "spotify-uri" }
        testScheduler.advanceUntilIdle()

        assertIs<com.poddrive.domain.model.Result.Success<String>>(result)
        assertEquals("spotify-uri", (result as com.poddrive.domain.model.Result.Success).value)
        // State must not have changed — no failure event dispatched
        assertIs<DriveSessionState.Activating>(mgr.state.value)
    }

    @Test
    fun `timeout dispatches PlaybackFailed and transitions to Error with SpotifyHandoffTimeout`() = runTest {
        val (mgr, uc) = makeUseCase()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Activating>(mgr.state.value)

        val result = uc.awaitHandoffOrTimeout<Unit> {
            // Simulate a block that never completes within the timeout
            kotlinx.coroutines.delay(HandlePlaybackFailureUseCase.HANDOFF_TIMEOUT_MS + 1_000L)
        }
        testScheduler.advanceUntilIdle()

        assertIs<com.poddrive.domain.model.Result.Failure<AppError>>(result)
        assertEquals(
            AppError.SpotifyHandoffTimeout,
            (result as com.poddrive.domain.model.Result.Failure).error
        )
        val state = mgr.state.value
        assertIs<DriveSessionState.Error>(state)
        assertEquals(AppError.SpotifyHandoffTimeout, state.error)
    }

    @Test
    fun `unexpected exception dispatches PlaybackFailed and transitions to Error with Unknown`() = runTest {
        val (mgr, uc) = makeUseCase()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Activating>(mgr.state.value)

        val cause = RuntimeException("network gone")
        val result = uc.awaitHandoffOrTimeout<Unit> { throw cause }
        testScheduler.advanceUntilIdle()

        assertIs<com.poddrive.domain.model.Result.Failure<AppError>>(result)
        val error = (result as com.poddrive.domain.model.Result.Failure).error
        assertIs<AppError.Unknown>(error)
        assertEquals(cause.message, (error as AppError.Unknown).cause.message)
        val state = mgr.state.value
        assertIs<DriveSessionState.Error>(state)
        assertIs<AppError.Unknown>(state.error)
    }

    // ─── retry ────────────────────────────────────────────────────────────────

    @Test
    fun `retry from Error transitions to Activating`() = runTest {
        val (mgr, uc) = makeUseCase()
        mgr.dispatch(DriveSessionEvent.ManualTrigger)
        testScheduler.advanceUntilIdle()
        mgr.dispatch(DriveSessionEvent.PlaybackFailed(AppError.SpotifyHandoffTimeout))
        testScheduler.advanceUntilIdle()
        assertIs<DriveSessionState.Error>(mgr.state.value)

        uc.retry()
        testScheduler.advanceUntilIdle()

        assertIs<DriveSessionState.Activating>(mgr.state.value)
    }
}
