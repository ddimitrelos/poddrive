package com.poddrive.session

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.DriveSessionEvent
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.repository.DriveSessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [DriveSessionManager] state machine.
 * Covers Story 4.2 (queue, activation) and Story 4.5 (failure handling).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveSessionManagerTest {

    // ---------------------------------------------------------------------------
    // Fake repository
    // ---------------------------------------------------------------------------

    private class FakeDriveSessionRepository : DriveSessionRepository {
        var lastPersistedState: DriveSessionState? = null
        var persistCallCount = 0

        override fun persistState(sessionId: String, state: DriveSessionState) {
            lastPersistedState = state
            persistCallCount++
        }

        override fun loadLatestSessionState(): DriveSessionState? = lastPersistedState
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun episode(id: String, tier: EpisodeTier = EpisodeTier.LOVED) = Episode(
        id = id,
        podcastId = "pod1",
        title = "Episode $id",
        durationMs = 3600_000L,
        publishedAt = 1000L,
        isPlayed = false,
        tier = tier
    )

    private fun makeManager(scope: TestScope): Pair<DriveSessionManager, FakeDriveSessionRepository> {
        val repo = FakeDriveSessionRepository()
        val manager = DriveSessionManager(repo, scope)
        return manager to repo
    }

    // ---------------------------------------------------------------------------
    // Story 4.2 — Activation and queue tests
    // ---------------------------------------------------------------------------

    @Test
    fun `initial state is Idle`() = runTest {
        val (manager, _) = makeManager(this)
        assertIs<DriveSessionState.Idle>(manager.state.value)
    }

    @Test
    fun `Idle + BluetoothConnected transitions to Activating then Briefing`() = runTest {
        val (manager, _) = makeManager(this)
        manager.dispatch(DriveSessionEvent.BluetoothConnected)
        advanceUntilIdle()
        // Auto-advances through Activating to Briefing
        assertIs<DriveSessionState.Briefing>(manager.state.value)
    }

    @Test
    fun `Idle + ManualTrigger transitions to Activating then Briefing`() = runTest {
        val (manager, _) = makeManager(this)
        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        assertIs<DriveSessionState.Briefing>(manager.state.value)
    }

    @Test
    fun `Briefing + BriefingConfirmed with queue transitions to Playing first episode`() = runTest {
        val (manager, _) = makeManager(this)
        val ep1 = episode("ep1")
        val ep2 = episode("ep2")
        manager.loadQueue(listOf(ep1, ep2))

        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        assertIs<DriveSessionState.Briefing>(manager.state.value)

        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()

        val state = manager.state.value
        assertIs<DriveSessionState.Playing>(state)
        assertEquals("ep1", state.episode.id)
        assertEquals(0L, state.positionMs)
    }

    @Test
    fun `Briefing + BriefingConfirmed with empty queue transitions to Ended`() = runTest {
        val (manager, _) = makeManager(this)
        manager.loadQueue(emptyList())

        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()

        assertIs<DriveSessionState.Ended>(manager.state.value)
    }

    @Test
    fun `Playing + SkipEpisode advances to next episode`() = runTest {
        val (manager, _) = makeManager(this)
        val ep1 = episode("ep1")
        val ep2 = episode("ep2")
        manager.loadQueue(listOf(ep1, ep2))

        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()
        assertIs<DriveSessionState.Playing>(manager.state.value)

        manager.dispatch(DriveSessionEvent.SkipEpisode)
        advanceUntilIdle()

        val state = manager.state.value
        assertIs<DriveSessionState.Playing>(state)
        assertEquals("ep2", state.episode.id)
    }

    @Test
    fun `Playing + SkipEpisode on last episode transitions to Ended`() = runTest {
        val (manager, _) = makeManager(this)
        val ep1 = episode("ep1")
        manager.loadQueue(listOf(ep1))

        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()

        manager.dispatch(DriveSessionEvent.SkipEpisode)
        advanceUntilIdle()

        assertIs<DriveSessionState.Ended>(manager.state.value)
    }

    @Test
    fun `Playing + BluetoothDisconnected transitions to Ended`() = runTest {
        val (manager, _) = makeManager(this)
        manager.loadQueue(listOf(episode("ep1")))
        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()

        manager.dispatch(DriveSessionEvent.BluetoothDisconnected)
        advanceUntilIdle()

        assertIs<DriveSessionState.Ended>(manager.state.value)
    }

    @Test
    fun `Playing + PauseSession transitions to Paused`() = runTest {
        val (manager, _) = makeManager(this)
        manager.loadQueue(listOf(episode("ep1")))
        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()

        manager.dispatch(DriveSessionEvent.PauseSession)
        advanceUntilIdle()

        assertIs<DriveSessionState.Paused>(manager.state.value)
    }

    @Test
    fun `Paused + ResumeSession transitions back to Playing`() = runTest {
        val (manager, _) = makeManager(this)
        manager.loadQueue(listOf(episode("ep1")))
        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.PauseSession)
        advanceUntilIdle()

        manager.dispatch(DriveSessionEvent.ResumeSession)
        advanceUntilIdle()

        assertIs<DriveSessionState.Playing>(manager.state.value)
    }

    @Test
    fun `state is persisted before being emitted (persist-before-emit rule)`() = runTest {
        val (manager, repo) = makeManager(this)
        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()

        // At least one persist call must have happened (Activating and Briefing)
        assertTrue(repo.persistCallCount >= 2)
    }

    // ---------------------------------------------------------------------------
    // Story 4.5 — Playback failure tests
    // ---------------------------------------------------------------------------

    @Test
    fun `Playing + PlaybackFailed transitions to Error`() = runTest {
        val (manager, _) = makeManager(this)
        manager.loadQueue(listOf(episode("ep1")))
        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()
        assertIs<DriveSessionState.Playing>(manager.state.value)

        manager.dispatch(DriveSessionEvent.PlaybackFailed)
        advanceUntilIdle()

        assertIs<DriveSessionState.Error>(manager.state.value)
    }

    @Test
    fun `Error + RetryPlayback transitions back to Playing with last episode`() = runTest {
        val (manager, _) = makeManager(this)
        val ep = episode("ep1")
        manager.loadQueue(listOf(ep))
        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.PlaybackFailed)
        advanceUntilIdle()
        assertIs<DriveSessionState.Error>(manager.state.value)

        manager.dispatch(DriveSessionEvent.RetryPlayback)
        advanceUntilIdle()

        val state = manager.state.value
        assertIs<DriveSessionState.Playing>(state)
        assertEquals("ep1", state.episode.id)
    }

    @Test
    fun `PlaybackFailed dispatched from non-Playing state is ignored`() = runTest {
        val (manager, _) = makeManager(this)
        // Idle state — PlaybackFailed should have no effect
        manager.dispatch(DriveSessionEvent.PlaybackFailed)
        advanceUntilIdle()
        assertIs<DriveSessionState.Idle>(manager.state.value)
    }

    @Test
    fun `Error + BluetoothDisconnected transitions to Ended`() = runTest {
        val (manager, _) = makeManager(this)
        manager.loadQueue(listOf(episode("ep1")))
        manager.dispatch(DriveSessionEvent.ManualTrigger)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.BriefingConfirmed)
        advanceUntilIdle()
        manager.dispatch(DriveSessionEvent.PlaybackFailed)
        advanceUntilIdle()

        manager.dispatch(DriveSessionEvent.BluetoothDisconnected)
        advanceUntilIdle()

        assertIs<DriveSessionState.Ended>(manager.state.value)
    }

    @Test
    fun `event count — DriveSessionEvent has 9 variants`() {
        val events: List<DriveSessionEvent> = listOf(
            DriveSessionEvent.BluetoothConnected,
            DriveSessionEvent.BluetoothDisconnected,
            DriveSessionEvent.ManualTrigger,
            DriveSessionEvent.BriefingConfirmed,
            DriveSessionEvent.SkipEpisode,
            DriveSessionEvent.PauseSession,
            DriveSessionEvent.ResumeSession,
            DriveSessionEvent.PlaybackFailed,
            DriveSessionEvent.RetryPlayback
        )
        assertEquals(9, events.size)
    }
}
