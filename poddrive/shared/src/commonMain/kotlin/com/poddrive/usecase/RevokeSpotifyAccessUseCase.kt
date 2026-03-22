package com.poddrive.usecase

import com.poddrive.api.SpotifyAuthManager
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result
import kotlinx.coroutines.CancellationException

/**
 * Clears all stored Spotify credentials in response to a user-initiated "Disconnect Spotify"
 * action (Story 1.5, FR3).
 *
 * Delegates to [SpotifyAuthManager.revokeAccess] which calls [SecureTokenStorage.clear].
 * No network call is made — this is a local-only operation.
 *
 * After [execute] returns [Result.Success], the caller must reset any in-memory auth state
 * (Android: [OnboardingViewModel.resetToIdle], iOS: set isAuthenticated = false).
 *
 * Never throws — always returns [Result.Success].
 *
 * [Source: architecture.md#Use Case rule — one use case per file, orchestration only]
 * [Source: architecture.md#Process Patterns — token lifecycle owned by SpotifyAuthManager]
 */
class RevokeSpotifyAccessUseCase(
    private val authManager: SpotifyAuthManager
) {
    /**
     * Clears all Spotify tokens from SecureStorage.
     *
     * @return [Result.Success] always — local Keychain/EncryptedSharedPreferences delete
     *         cannot fail in a way that requires user action.
     */
    suspend fun execute(): Result<Unit, AppError> {
        return try {
            authManager.revokeAccess()
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(AppError.Unknown(e))
        }
    }
}
