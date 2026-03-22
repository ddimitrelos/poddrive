package com.poddrive.usecase

import com.poddrive.api.SpotifyAuthManager
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.Result

/**
 * Orchestrates the final step of Spotify OAuth: token exchange.
 *
 * The platform UI layer (Android/iOS) is responsible for:
 * 1. Generating code_verifier and building the authorization URL (via [SpotifyAuthManager])
 * 2. Opening the browser / ASWebAuthenticationSession
 * 3. Capturing the redirect callback and extracting the authorization code
 * 4. Calling [execute] with the code and matching code_verifier
 *
 * This use case delegates to [SpotifyAuthManager.exchangeCodeForTokens] and maps the result.
 * Never throws — all errors are returned as [Result.Failure].
 *
 * [Source: architecture.md#Use Case rule — one use case per file, orchestration only]
 */
class AuthenticateWithSpotifyUseCase(
    private val authManager: SpotifyAuthManager
) {
    /**
     * Exchanges [authCode] for tokens and persists them via [SpotifyAuthManager].
     *
     * @param authCode The authorization code extracted from the OAuth redirect URI.
     * @param codeVerifier The PKCE code verifier generated before the OAuth flow started.
     * @return [Result.Success] on successful token exchange and storage.
     *         [Result.Failure] with an [AppError] on any failure.
     */
    suspend fun execute(authCode: String, codeVerifier: String): Result<Unit, AppError> =
        authManager.exchangeCodeForTokens(authCode, codeVerifier)
}
