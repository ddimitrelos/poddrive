package com.poddrive.usecase

import com.poddrive.api.SpotifyLibraryApiClient
import com.poddrive.api.SpotifyShow
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result
import kotlinx.coroutines.CancellationException

/**
 * Searches for Spotify podcast shows matching [query].
 * Delegates to [SpotifyLibraryApiClient.searchShows] and returns up to 20 results.
 * Never throws — all errors are returned as [Result.Failure].
 * [Source: epics.md#Story 2.6 AC1, UX-DR7]
 */
class SearchSpotifyShowsUseCase(
    private val libraryApiClient: SpotifyLibraryApiClient
) {
    suspend fun execute(query: String): Result<List<SpotifyShow>, AppError> {
        return try {
            libraryApiClient.searchShows(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }
}
