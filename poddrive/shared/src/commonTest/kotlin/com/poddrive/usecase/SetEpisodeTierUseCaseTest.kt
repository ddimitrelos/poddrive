package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast
import com.poddrive.domain.model.Result
import com.poddrive.repository.PodcastRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SetEpisodeTierUseCaseTest {

    // -----------------------------------------------------------------------
    // Fake
    // -----------------------------------------------------------------------

    private class FakePodcastRepository : PodcastRepository {
        data class TierUpdate(val id: String, val tier: EpisodeTier)

        val tierUpdates = mutableListOf<TierUpdate>()
        var shouldThrow: Exception? = null

        override fun upsertAll(podcasts: List<Podcast>) = Unit
        override fun getAllWithUnheardCount(): List<Podcast> = emptyList()
        override fun getByTierWithUnheardCount(tier: String): List<Podcast> = emptyList()

        override fun setPodcastTier(id: String, tier: EpisodeTier) {
            shouldThrow?.let { throw it }
            tierUpdates.add(TierUpdate(id, tier))
        }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    fun `execute sets LOVED tier and returns Success`() {
        val repo = FakePodcastRepository()
        val useCase = SetEpisodeTierUseCase(repo)

        val result = useCase.execute("spotify:show:abc", EpisodeTier.LOVED)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.tierUpdates.size)
        assertEquals(FakePodcastRepository.TierUpdate("spotify:show:abc", EpisodeTier.LOVED), repo.tierUpdates[0])
    }

    @Test
    fun `execute sets LIKED tier and returns Success`() {
        val repo = FakePodcastRepository()
        val useCase = SetEpisodeTierUseCase(repo)

        val result = useCase.execute("spotify:show:xyz", EpisodeTier.LIKED)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(EpisodeTier.LIKED, repo.tierUpdates[0].tier)
    }

    @Test
    fun `execute sets NONE tier (remove tag) and returns Success`() {
        val repo = FakePodcastRepository()
        val useCase = SetEpisodeTierUseCase(repo)

        val result = useCase.execute("spotify:show:xyz", EpisodeTier.NONE)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(EpisodeTier.NONE, repo.tierUpdates[0].tier)
    }

    @Test
    fun `execute returns Failure when repository throws`() {
        val repo = FakePodcastRepository()
        repo.shouldThrow = RuntimeException("SQLite locked")
        val useCase = SetEpisodeTierUseCase(repo)

        val result = useCase.execute("spotify:show:abc", EpisodeTier.LOVED)

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.Unknown>(result.error)
    }
}
