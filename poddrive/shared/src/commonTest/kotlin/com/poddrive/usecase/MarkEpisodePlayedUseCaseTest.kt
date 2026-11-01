package com.poddrive.usecase

import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.Result
import com.poddrive.repository.EpisodeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MarkEpisodePlayedUseCaseTest {

    // ─── Fake ────────────────────────────────────────────────────────────────

    private class FakeEpisodeRepository(
        private val shouldThrow: Boolean = false
    ) : EpisodeRepository {
        val markedPlayedIds = mutableListOf<String>()

        override fun upsertAll(episodes: List<Episode>) = Unit
        override fun getUnheardCountForPodcast(podcastId: String): Int = 0
        override fun getUnplayedOrderedByTier(): List<Episode> = emptyList()
        override fun markPlayed(episodeId: String) {
            if (shouldThrow) throw RuntimeException("DB write failed")
            markedPlayedIds += episodeId
        }

        override fun updateAudioUrl(id: String, audioUrl: String) = Unit
        override fun getUnresolvedEpisodesForPodcast(podcastId: String): List<Episode> = emptyList()
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `success path — returns Success and calls repository markPlayed`() {
        val repo = FakeEpisodeRepository()
        val result = MarkEpisodePlayedUseCase(repo).execute("ep-123")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.markedPlayedIds.size)
        assertEquals("ep-123", repo.markedPlayedIds.first())
    }

    @Test
    fun `repository throws — returns Failure wrapping AppError Unknown`() {
        val repo = FakeEpisodeRepository(shouldThrow = true)
        val result = MarkEpisodePlayedUseCase(repo).execute("ep-456")

        assertIs<Result.Failure<AppError>>(result)
        assertIs<AppError.Unknown>((result as Result.Failure).error)
    }

    @Test
    fun `correct episodeId is passed to repository`() {
        val repo = FakeEpisodeRepository()
        MarkEpisodePlayedUseCase(repo).execute("spotify:episode:abc123")

        assertTrue(repo.markedPlayedIds.contains("spotify:episode:abc123"))
    }

    @Test
    fun `CancellationException from repository is re-thrown and not wrapped`() {
        // AR3: CancellationException must always propagate — never swallowed as AppError.Unknown
        val throwingRepo = object : EpisodeRepository {
            override fun upsertAll(episodes: List<Episode>) = Unit
            override fun getUnheardCountForPodcast(podcastId: String): Int = 0
            override fun getUnplayedOrderedByTier(): List<Episode> = emptyList()
            override fun markPlayed(episodeId: String) {
                throw kotlinx.coroutines.CancellationException("cancelled")
            }
            override fun updateAudioUrl(id: String, audioUrl: String) = Unit
            override fun getUnresolvedEpisodesForPodcast(podcastId: String): List<Episode> = emptyList()
        }
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            MarkEpisodePlayedUseCase(throwingRepo).execute("ep-cancel")
        }
    }
}
