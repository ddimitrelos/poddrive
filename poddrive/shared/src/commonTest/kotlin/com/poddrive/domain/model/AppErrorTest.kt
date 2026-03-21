package com.poddrive.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class AppErrorTest {

    // ------------------------------------------------------------------
    // SpotifyApiError
    // ------------------------------------------------------------------

    @Test
    fun `SpotifyApiError holds code and message`() {
        val error = AppError.SpotifyApiError(code = 401, message = "Unauthorized")
        assertEquals(401, error.code)
        assertEquals("Unauthorized", error.message)
    }

    @Test
    fun `SpotifyApiError equality works by value`() {
        val a = AppError.SpotifyApiError(404, "Not Found")
        val b = AppError.SpotifyApiError(404, "Not Found")
        assertEquals(a, b)
    }

    @Test
    fun `SpotifyApiError with different codes are not equal`() {
        val a = AppError.SpotifyApiError(401, "Unauthorized")
        val b = AppError.SpotifyApiError(403, "Forbidden")
        assertNotEquals(a, b)
    }

    // ------------------------------------------------------------------
    // Object subtypes — singleton identity
    // ------------------------------------------------------------------

    @Test
    fun `NetworkUnavailable is a singleton object`() {
        val a: AppError = AppError.NetworkUnavailable
        val b: AppError = AppError.NetworkUnavailable
        assertEquals(a, b)
    }

    @Test
    fun `SpotifyNotInstalled is a singleton object`() {
        val a: AppError = AppError.SpotifyNotInstalled
        val b: AppError = AppError.SpotifyNotInstalled
        assertEquals(a, b)
    }

    @Test
    fun `RateLimitExceeded is a singleton object`() {
        val a: AppError = AppError.RateLimitExceeded
        val b: AppError = AppError.RateLimitExceeded
        assertEquals(a, b)
    }

    // ------------------------------------------------------------------
    // Unknown
    // ------------------------------------------------------------------

    @Test
    fun `Unknown wraps the cause Throwable`() {
        val cause = RuntimeException("unexpected")
        val error = AppError.Unknown(cause = cause)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `Unknown cause message is accessible`() {
        val error = AppError.Unknown(cause = IllegalStateException("bad state"))
        assertEquals("bad state", error.cause.message)
    }

    // ------------------------------------------------------------------
    // Sealed class exhaustive when expression
    // ------------------------------------------------------------------

    @Test
    fun `all AppError subtypes are handled in when expression`() {
        val errors: List<AppError> = listOf(
            AppError.SpotifyApiError(500, "Server Error"),
            AppError.NetworkUnavailable,
            AppError.SpotifyNotInstalled,
            AppError.RateLimitExceeded,
            AppError.Unknown(RuntimeException("test"))
        )

        for (error in errors) {
            // This when expression must compile — if a subtype is missing it won't
            val label: String = when (error) {
                is AppError.SpotifyApiError -> "SpotifyApiError"
                AppError.NetworkUnavailable -> "NetworkUnavailable"
                AppError.SpotifyNotInstalled -> "SpotifyNotInstalled"
                AppError.RateLimitExceeded -> "RateLimitExceeded"
                is AppError.Unknown -> "Unknown"
            }
            assertNotNull(label)
        }
    }

    @Test
    fun `AppError subtypes are distinct types`() {
        val error: AppError = AppError.NetworkUnavailable
        assertIs<AppError.NetworkUnavailable>(error)
    }
}
