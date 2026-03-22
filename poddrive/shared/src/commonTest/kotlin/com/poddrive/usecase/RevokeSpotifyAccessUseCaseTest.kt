package com.poddrive.usecase

import com.poddrive.domain.model.Result
import com.poddrive.storage.FakeSecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.poddrive.api.SpotifyAuthManager

/**
 * Unit tests for [RevokeSpotifyAccessUseCase] (Story 1.5).
 *
 * Verifies that [execute] clears both tokens from [SecureTokenStorage] and returns
 * [Result.Success]. Uses [FakeSecureTokenStorage] and [MockEngine] — no platform
 * Keychain or network calls.
 */
class RevokeSpotifyAccessUseCaseTest {

    private val existingAccessToken = "existing_access_token"
    private val existingRefreshToken = "existing_refresh_token"

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Pre-seeded storage with existing tokens. */
    private fun makeStorage(): FakeSecureTokenStorage =
        FakeSecureTokenStorage().also { it.saveTokens(existingAccessToken, existingRefreshToken) }

    /**
     * Creates a [RevokeSpotifyAccessUseCase] backed by [storage].
     * The [HttpClient] is not used during revocation (revokeAccess() is local-only)
     * but [SpotifyAuthManager] requires a non-null client.
     */
    private fun makeUseCase(storage: FakeSecureTokenStorage): RevokeSpotifyAccessUseCase {
        val engine = MockEngine { respond("", HttpStatusCode.OK, headersOf()) }
        val client = HttpClient(engine)
        val manager = SpotifyAuthManager(storage, client, "test_client_id")
        return RevokeSpotifyAccessUseCase(manager)
    }

    // -----------------------------------------------------------------------
    // Token clearing
    // -----------------------------------------------------------------------

    @Test
    fun `execute clears access token from storage`() = runTest {
        val storage = makeStorage()
        val useCase = makeUseCase(storage)

        useCase.execute()

        assertNull(storage.getAccessToken(), "Access token must be cleared after revocation")
    }

    @Test
    fun `execute clears refresh token from storage`() = runTest {
        val storage = makeStorage()
        val useCase = makeUseCase(storage)

        useCase.execute()

        assertNull(storage.getRefreshToken(), "Refresh token must be cleared after revocation")
    }

    // -----------------------------------------------------------------------
    // Return value
    // -----------------------------------------------------------------------

    @Test
    fun `execute returns Result dot Success`() = runTest {
        val storage = makeStorage()
        val useCase = makeUseCase(storage)

        val result = useCase.execute()

        assertTrue(result is Result.Success, "execute() must return Result.Success")
    }
}
