package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuildEpisodeQueueUseCaseTest {

    // ─── Fakes ───────────────────────────────────────────────────────────────

    private class FakeEpisodeRepository(
        private val orderedEpisodes: List<Episode> = emptyList(),
        private val shouldThrow: Boolean = false,
        private val throwCancellation: Boolean = false
    ) : EpisodeRepository {
        override fun upsertAll(episodes: List<Episode>) = Unit
        override fun getUnheardCountForPodcast(podcastId: String): Int = 0
        override fun getUnplayedOrderedByTier(): List<Episode> {
            if (throwCancellation) throw kotlinx.coroutines.CancellationException("cancelled")
            if (shouldThrow) throw RuntimeException("DB failure")
            return orderedEpisodes
        }
        override fun markPlayed(episodeId: String) = Unit
        override fun updateAudioUrl(id: String, audioUrl: String) = Unit
        override fun getUnresolvedEpisodesForPodcast(podcastId: String): List<Episode> = emptyList()
    }

    private fun ep(id: String, tier: EpisodeTier, publishedAt: Long = 0L) =
        Episode(id, "show-1", "Title $id", 1_000L, publishedAt, isPlayed = false, tier)

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `returns empty queue when no unplayed episodes exist`() = runTest {
        val result = BuildEpisodeQueueUseCase(FakeEpisodeRepository()).execute()
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).value.episodes.isEmpty())
    }

    @Test
    fun `preserves LOVED-first LIKED-second ordering returned by repository`() = runTest {
        // The repository (selectUnplayedOrderedByTier) is responsible for sorting.
        // The use case must not re-sort — it preserves the repository's order exactly.
        val episodes = listOf(
            ep("loved-1", EpisodeTier.LOVED, publishedAt = 2_000L),
            ep("loved-2", EpisodeTier.LOVED, publishedAt = 1_000L),
            ep("liked-1", EpisodeTier.LIKED, publishedAt = 3_000L),
            ep("none-1",  EpisodeTier.NONE,  publishedAt = 9_000L)
        )
        val result = BuildEpisodeQueueUseCase(FakeEpisodeRepository(episodes)).execute()
        assertIs<Result.Success<*>>(result)
        val queue = (result as Result.Success).value
        assertEquals(4, queue.episodes.size)
        assertEquals("loved-1", queue.episodes[0].id)
        assertEquals("loved-2", queue.episodes[1].id)
        assertEquals("liked-1", queue.episodes[2].id)
        assertEquals("none-1",  queue.episodes[3].id)
    }

    @Test
    fun `single LOVED episode returns queue of one`() = runTest {
        val result = BuildEpisodeQueueUseCase(
            FakeEpisodeRepository(listOf(ep("ep-1", EpisodeTier.LOVED)))
        ).execute()
        assertIs<Result.Success<*>>(result)
        assertEquals(1, (result as Result.Success).value.episodes.size)
        assertEquals("ep-1", result.value.episodes.first().id)
    }

    @Test
    fun `wraps repository exception as AppError Unknown`() = runTest {
        val result = BuildEpisodeQueueUseCase(FakeEpisodeRepository(shouldThrow = true)).execute()
        assertIs<Result.Failure<*>>(result)
        assertIs<AppError.Unknown>((result as Result.Failure).error)
    }

    @Test
    fun `CancellationException from repository is re-thrown and not wrapped`() = runTest {
        // AR3: CancellationException must always propagate — never swallowed as AppError.Unknown
        var threw = false
        try {
            BuildEpisodeQueueUseCase(FakeEpisodeRepository(throwCancellation = true)).execute()
        } catch (e: kotlinx.coroutines.CancellationException) {
            threw = true
        }
        assertTrue(threw, "CancellationException must be re-thrown, not wrapped in AppError.Unknown")
    }
}
