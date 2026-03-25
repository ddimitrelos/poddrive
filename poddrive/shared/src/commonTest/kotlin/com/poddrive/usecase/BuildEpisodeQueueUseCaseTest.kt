package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [BuildEpisodeQueueUseCase].
 * Verifies LOVED→LIKED ordering (FR22, AC1) and within-tier date sorting (AC2).
 * [Story 4.2]
 */
class BuildEpisodeQueueUseCaseTest {

    // ---------------------------------------------------------------------------
    // Fake
    // ---------------------------------------------------------------------------

    private class FakeEpisodeRepository(
        private val episodes: List<Episode> = emptyList(),
        private val throwOnGet: Boolean = false
    ) : EpisodeRepository {
        override fun upsertAll(episodes: List<Episode>) = Unit
        override fun getUnheardCountForPodcast(podcastId: String): Int = 0
        override fun getUnplayedOrderedByTier(): List<Episode> {
            if (throwOnGet) throw RuntimeException("DB error")
            // Return pre-sorted (mimicking SQL ORDER BY tier priority then publishedAt DESC)
            return episodes
                .filter { !it.isPlayed }
                .sortedWith(compareBy<Episode> { tierOrder(it.tier) }.thenByDescending { it.publishedAt })
        }
        private fun tierOrder(tier: EpisodeTier) = when (tier) {
            EpisodeTier.LOVED -> 0
            EpisodeTier.LIKED -> 1
            EpisodeTier.NONE -> 2
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun episode(
        id: String,
        tier: EpisodeTier,
        publishedAt: Long,
        isPlayed: Boolean = false
    ) = Episode(
        id = id,
        podcastId = "pod1",
        title = "Episode $id",
        durationMs = 3600_000L,
        publishedAt = publishedAt,
        isPlayed = isPlayed,
        tier = tier
    )

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `LOVED episodes precede LIKED in queue`() {
        val episodes = listOf(
            episode("liked1", EpisodeTier.LIKED, 2000L),
            episode("loved1", EpisodeTier.LOVED, 1000L),
            episode("liked2", EpisodeTier.LIKED, 3000L),
            episode("loved2", EpisodeTier.LOVED, 4000L)
        )
        val useCase = BuildEpisodeQueueUseCase(FakeEpisodeRepository(episodes))

        val result = useCase.execute()
        assertIs<Result.Success<List<Episode>>>(result)
        val queue = result.value

        val firstLovedIndex = queue.indexOfFirst { it.tier == EpisodeTier.LOVED }
        val firstLikedIndex = queue.indexOfFirst { it.tier == EpisodeTier.LIKED }
        assertTrue(firstLovedIndex < firstLikedIndex, "All LOVED must precede LIKED")
    }

    @Test
    fun `within LOVED tier episodes sorted by publishedAt DESC`() {
        val episodes = listOf(
            episode("loved_old", EpisodeTier.LOVED, 1000L),
            episode("loved_new", EpisodeTier.LOVED, 5000L),
            episode("loved_mid", EpisodeTier.LOVED, 3000L)
        )
        val useCase = BuildEpisodeQueueUseCase(FakeEpisodeRepository(episodes))

        val result = useCase.execute()
        assertIs<Result.Success<List<Episode>>>(result)
        val queue = result.value.filter { it.tier == EpisodeTier.LOVED }

        assertEquals("loved_new", queue[0].id)
        assertEquals("loved_mid", queue[1].id)
        assertEquals("loved_old", queue[2].id)
    }

    @Test
    fun `within LIKED tier episodes sorted by publishedAt DESC`() {
        val episodes = listOf(
            episode("liked_old", EpisodeTier.LIKED, 100L),
            episode("liked_new", EpisodeTier.LIKED, 900L),
            episode("liked_mid", EpisodeTier.LIKED, 500L)
        )
        val useCase = BuildEpisodeQueueUseCase(FakeEpisodeRepository(episodes))

        val result = useCase.execute()
        assertIs<Result.Success<List<Episode>>>(result)
        val queue = result.value.filter { it.tier == EpisodeTier.LIKED }

        assertEquals("liked_new", queue[0].id)
        assertEquals("liked_mid", queue[1].id)
        assertEquals("liked_old", queue[2].id)
    }

    @Test
    fun `NONE tier episodes are excluded from queue`() {
        val episodes = listOf(
            episode("loved1", EpisodeTier.LOVED, 1000L),
            episode("none1", EpisodeTier.NONE, 2000L),
            episode("liked1", EpisodeTier.LIKED, 3000L),
            episode("none2", EpisodeTier.NONE, 4000L)
        )
        val useCase = BuildEpisodeQueueUseCase(FakeEpisodeRepository(episodes))

        val result = useCase.execute()
        assertIs<Result.Success<List<Episode>>>(result)
        val queue = result.value

        assertTrue(queue.none { it.tier == EpisodeTier.NONE }, "NONE episodes must be excluded")
        assertEquals(2, queue.size)
    }

    @Test
    fun `played episodes are excluded from queue`() {
        val episodes = listOf(
            episode("loved_played", EpisodeTier.LOVED, 5000L, isPlayed = true),
            episode("loved_unplayed", EpisodeTier.LOVED, 3000L, isPlayed = false)
        )
        val useCase = BuildEpisodeQueueUseCase(FakeEpisodeRepository(episodes))

        val result = useCase.execute()
        assertIs<Result.Success<List<Episode>>>(result)
        assertEquals(1, result.value.size)
        assertEquals("loved_unplayed", result.value[0].id)
    }

    @Test
    fun `empty repository returns empty queue`() {
        val useCase = BuildEpisodeQueueUseCase(FakeEpisodeRepository(emptyList()))

        val result = useCase.execute()
        assertIs<Result.Success<List<Episode>>>(result)
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `repository exception returns Failure`() {
        val useCase = BuildEpisodeQueueUseCase(FakeEpisodeRepository(throwOnGet = true))

        val result = useCase.execute()
        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.Unknown>(result.error)
    }
}
