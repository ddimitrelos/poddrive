package com.poddrive.domain.model

/**
 * Sealed class representing all typed errors in PodDrive's KMP shared layer.
 * All KMP use case return types use Result<T, AppError>.
 * No exceptions cross the KMP boundary — errors are always wrapped as AppError.
 * [Source: architecture.md#API & Communication Patterns, AR5]
 */
sealed class AppError {
    /** Spotify Web API returned an error HTTP status code. */
    data class SpotifyApiError(val code: Int, val message: String) : AppError()

    /** Device has no active network connection. */
    object NetworkUnavailable : AppError()

    /** Spotify app is not installed on the device (required for deep-link handoff). */
    object SpotifyNotInstalled : AppError()

    /** Spotify API rate limit reached — request should be queued and retried. */
    object RateLimitExceeded : AppError()

    /** Catch-all for unexpected errors. The only subtype that wraps a Throwable. */
    data class Unknown(val cause: Throwable) : AppError()
}
