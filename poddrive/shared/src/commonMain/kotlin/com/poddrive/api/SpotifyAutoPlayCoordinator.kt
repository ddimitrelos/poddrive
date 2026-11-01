package com.poddrive.api

import com.poddrive.domain.model.Result
import kotlinx.coroutines.delay

/**
 * Coordinates the Spotify episode auto-play sequence (Story 4.7).
 *
 * Replaces the Story 4.6 fixed-delay approach with adaptive active-device polling:
 * polls `GET /v1/me/player` every [DEVICE_POLL_INTERVAL_MS] until Spotify registers an active
 * device, then fires `PUT /v1/me/player/play`. Exits silently if no device appears within
 * [DEVICE_POLL_TIMEOUT_MS] or if the [isHandingOff] guard becomes false.
 *
 * Timing relationship with [HANDOFF_TIMEOUT_MS] (DriveSessionViewModel):
 * [DEVICE_POLL_TIMEOUT_MS] (5 000 ms) intentionally exceeds [HANDOFF_TIMEOUT_MS] (3 000 ms).
 * When the handoff timeout fires it sets isHandingOff = false; the AC7 guard causes the loop
 * to exit on the next iteration. [DEVICE_POLL_TIMEOUT_MS] is the backstop for the rare case
 * where neither condition triggers.
 *
 * This class has no Android dependencies and is safe to instantiate in pure JVM / KMP tests.
 *
 * [Source: Story 4.7 — Active-Device Polling for Reliable Single-Tap Playback]
 */
class SpotifyAutoPlayCoordinator(
    private val playerApiClient: SpotifyPlayerApiClient,
    private val isHandingOff: () -> Boolean,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Polls for an active Spotify device, then fires the play command.
     *
     * Returns `true` if playback started (PUT responded with 2xx).
     * Returns `false` on timeout, [isHandingOff] guard exit, or play API failure.
     * [CancellationException] propagates — cancel the enclosing coroutine to abort.
     *
     * @param episodeUri Full Spotify episode URI, e.g. `spotify:episode:6FZj6eVQLmvii10w73SxEw`.
     *   Must not be blank.
     */
    /**
     * Polls by attempting `PUT /v1/me/player/play` directly each iteration.
     *
     * `GET /v1/me/player` (hasActiveDevice) only returns a device when Spotify is already playing —
     * opening Spotify via deep link shows the episode but does not create an active player session,
     * so hasActiveDevice would always return false. Instead we attempt the play command directly:
     *
     * - **204 / 2xx** → playback started, return true
     * - **404** (no active device yet — Spotify still initialising) → wait and retry
     * - **403** (Premium required for this content type) → exit silently, silent fallback
     * - **429** (rate limit) → wait and retry
     * - Other errors / exceptions → wait and retry
     */
    suspend fun autoPlay(episodeUri: String): Boolean {
        require(episodeUri.isNotBlank()) { "episodeUri must not be blank" }
        val deadline = clock() + DEVICE_POLL_TIMEOUT_MS
        var pollCount = 0
        while (clock() < deadline) {
            if (!isHandingOff()) {
                System.err.println("D/AutoPlay: exiting — isHandingOff=false (pre-poll, pollCount=$pollCount)")
                return false
            }
            pollCount++
            val result = playerApiClient.playEpisode(episodeUri)
            System.err.println("D/AutoPlay: poll #$pollCount playEpisode result=$result elapsed=${clock() - (deadline - DEVICE_POLL_TIMEOUT_MS)}ms")
            when {
                result is Result.Success -> return true
                result is Result.Failure && result.error is com.poddrive.domain.model.AppError.SpotifyApiError -> {
                    val code = (result.error as com.poddrive.domain.model.AppError.SpotifyApiError).code
                    if (code == 403) {
                        System.err.println("D/AutoPlay: 403 Premium required — silent fallback")
                        return false   // Not retryable — Premium required for this content
                    }
                    // 404 = no active device yet, 429 = rate limit, 5xx = server error → retry
                }
                // Result.Failure with Unknown (network error) → retry
            }
            delay(DEVICE_POLL_INTERVAL_MS)
            if (!isHandingOff()) {
                System.err.println("D/AutoPlay: exiting — isHandingOff=false (post-delay, pollCount=$pollCount)")
                return false
            }
        }
        System.err.println("D/AutoPlay: exiting — timeout after $pollCount polls")
        return false
    }

    companion object {
        /** Interval between device-availability polls. */
        const val DEVICE_POLL_INTERVAL_MS = 500L
        /**
         * Maximum time to wait for a device before giving up.
         * Intentionally exceeds DriveSessionViewModel.HANDOFF_TIMEOUT_MS (3 000 ms) — the
         * isHandingOff guard is the primary exit gate once the handoff timeout fires.
         */
        const val DEVICE_POLL_TIMEOUT_MS = 5_000L
    }
}
