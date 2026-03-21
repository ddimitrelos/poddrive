package com.poddrive.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.fail

class ResultTest {

    // ------------------------------------------------------------------
    // Success
    // ------------------------------------------------------------------

    @Test
    fun `Success wraps value`() {
        val result: Result<String, AppError> = Result.Success("hello")
        assertIs<Result.Success<String>>(result)
        assertEquals("hello", result.value)
    }

    @Test
    fun `Success with null value is allowed`() {
        val result: Result<String?, AppError> = Result.Success(null)
        assertIs<Result.Success<String?>>(result)
        assertNull(result.value)
    }

    // ------------------------------------------------------------------
    // Failure
    // ------------------------------------------------------------------

    @Test
    fun `Failure wraps AppError`() {
        val error = AppError.NetworkUnavailable
        val result: Result<String, AppError> = Result.Failure(error)
        assertIs<Result.Failure<AppError>>(result)
        assertEquals(error, result.error)
    }

    @Test
    fun `Failure with SpotifyApiError preserves details`() {
        val error = AppError.SpotifyApiError(429, "Too Many Requests")
        val result: Result<Unit, AppError> = Result.Failure(error)
        val failure = result as Result.Failure
        assertIs<AppError.SpotifyApiError>(failure.error)
        assertEquals(429, (failure.error as AppError.SpotifyApiError).code)
    }

    // ------------------------------------------------------------------
    // onSuccess / onFailure extensions
    // ------------------------------------------------------------------

    @Test
    fun `onSuccess callback fires for Success`() {
        var called = false
        Result.Success("data").onSuccess { called = true }
        assertEquals(true, called)
    }

    @Test
    fun `onSuccess callback does NOT fire for Failure`() {
        var called = false
        val result: Result<String, AppError> = Result.Failure(AppError.NetworkUnavailable)
        result.onSuccess { called = true }
        assertEquals(false, called)
    }

    @Test
    fun `onFailure callback fires for Failure`() {
        var capturedError: AppError? = null
        Result.Failure(AppError.RateLimitExceeded).onFailure { capturedError = it }
        assertNotNull(capturedError)
        assertEquals(AppError.RateLimitExceeded, capturedError)
    }

    @Test
    fun `onFailure callback does NOT fire for Success`() {
        var called = false
        val result: Result<Int, AppError> = Result.Success(42)
        result.onFailure { called = true }
        assertEquals(false, called)
    }

    // ------------------------------------------------------------------
    // map extension
    // ------------------------------------------------------------------

    @Test
    fun `map transforms Success value`() {
        val result: Result<Int, AppError> = Result.Success(5)
        val mapped = result.map { it * 2 }
        assertIs<Result.Success<Int>>(mapped)
        assertEquals(10, mapped.value)
    }

    @Test
    fun `map passes through Failure unchanged`() {
        val error = AppError.SpotifyNotInstalled
        val result: Result<Int, AppError> = Result.Failure(error)
        val mapped = result.map { it * 2 }
        assertIs<Result.Failure<AppError>>(mapped)
        assertEquals(error, mapped.error)
    }

    // ------------------------------------------------------------------
    // getOrNull / errorOrNull
    // ------------------------------------------------------------------

    @Test
    fun `getOrNull returns value for Success`() {
        val result: Result<String, AppError> = Result.Success("ok")
        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun `getOrNull returns null for Failure`() {
        val result: Result<String, AppError> = Result.Failure(AppError.NetworkUnavailable)
        assertNull(result.getOrNull())
    }

    @Test
    fun `errorOrNull returns error for Failure`() {
        val result: Result<String, AppError> = Result.Failure(AppError.NetworkUnavailable)
        assertEquals(AppError.NetworkUnavailable, result.errorOrNull())
    }

    @Test
    fun `errorOrNull returns null for Success`() {
        val result: Result<String, AppError> = Result.Success("ok")
        assertNull(result.errorOrNull())
    }

    // ------------------------------------------------------------------
    // Sealed class exhaustive when expression
    // ------------------------------------------------------------------

    @Test
    fun `Result when expression is exhaustive`() {
        val results: List<Result<String, AppError>> = listOf(
            Result.Success("value"),
            Result.Failure(AppError.NetworkUnavailable)
        )

        for (result in results) {
            val label = when (result) {
                is Result.Success -> "success:${result.value}"
                is Result.Failure -> "failure"
            }
            assertNotNull(label)
        }
    }

    // ------------------------------------------------------------------
    // Chaining / composition
    // ------------------------------------------------------------------

    @Test
    fun `onSuccess and onFailure chain correctly`() {
        var successFired = false
        var failureFired = false

        Result.Success("x")
            .onSuccess { successFired = true }
            .onFailure { failureFired = true }

        assertEquals(true, successFired)
        assertEquals(false, failureFired)
    }
}
