package com.poddrive.android.ui.drive

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poddrive.android.ui.home.TierBadgeIconOnly
import com.poddrive.domain.model.AppError
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier

/**
 * Drive mode dashboard screen — shows the active session state, episode queue, and handles
 * Spotify deep-link handoffs (Story 4.1).
 *
 * Architecture rules followed:
 * - [ActivityNotFoundException] is caught HERE, not in the ViewModel (Dev Notes rule 3).
 * - [DriveUiState.isHandingOff] is UI-local — never promoted to [DriveSessionState] (Dev Notes rule 2).
 * - All color references via [MaterialTheme.colorScheme] — no raw Color(0xFF…) literals (Dev Notes rule 1).
 *
 * [Source: Story 4.1, AC1-AC5; architecture.md#UI State Management]
 */
@Composable
fun DriveSessionScreen(
    viewModel: DriveSessionViewModel = viewModel()
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Load episode queue when screen first appears
    LaunchedEffect(Unit) { viewModel.loadEpisodeQueue() }

    // Call clearHandingOff when app returns to foreground after Spotify opens (AC4).
    // Guards on BOTH isHandingOff AND spotifyIntentLaunched to avoid triggering on unrelated
    // interrupts (incoming call, permission dialog, notification shade) that also fire ON_RESUME
    // while a handoff is in progress.
    // Uses viewModel.uiState.value (not the composed `uiState`) to read current state from a
    // non-composable lambda without a stale-capture issue.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val state = viewModel.uiState.value
                if (state.isHandingOff && state.spotifyIntentLaunched) {
                    viewModel.clearHandingOff()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val state = sessionState) {
        is DriveSessionState.Error -> {
            DriveErrorContent(
                error = state.error,
                onRetry = { viewModel.retryPlayback() }
            )
        }
        is DriveSessionState.Ended -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Drive session ended", style = MaterialTheme.typography.bodyLarge)
            }
        }
        else -> {
            // Story 4.11: Show in-app playback controls when ExoPlayer is playing or paused.
            // The controls are hidden during a Spotify handoff (AC5) and when the episode was
            // opened via the Spotify deep-link path (playbackDurationMs == 0).
            val showInAppControls = !uiState.isHandingOff &&
                (uiState.isPlayingInApp ||
                    (sessionState is DriveSessionState.Paused && uiState.playbackDurationMs > 0))
            Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                if (showInAppControls) {
                    InAppPlaybackControls(
                        uiState = uiState,
                        isPlaying = uiState.isPlayingInApp,
                        onPause = { viewModel.onPausePlayback() },
                        onResume = { viewModel.onResumePlayback() },
                        onSkip = { viewModel.onSkipEpisode() }
                    )
                    HorizontalDivider()
                }
                DriveActiveContent(
                    uiState = uiState,
                    onEpisodeTap = { episode ->
                        if (episode.audioUrl != null) {
                            // AC1: in-app path — ExoPlayer plays directly, no Spotify Intent.
                            viewModel.onEpisodeTapped(episode)
                        } else {
                            // AC2: fallback — fire the Spotify deep-link (unchanged behaviour).
                            launchSpotifyEpisode(context, episode.id, viewModel)
                        }
                    },
                    onOpenInSpotifyCta = {
                        val firstEpisode = uiState.episodes.firstOrNull() ?: return@DriveActiveContent
                        if (firstEpisode.audioUrl != null) {
                            viewModel.onEpisodeTapped(firstEpisode)
                        } else {
                            launchSpotifyEpisode(context, firstEpisode.id, viewModel)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Fires a Spotify episode deep-link and routes the result back to [viewModel] (P-5, P-6).
 *
 * - URI-encodes [episodeId] to handle any special characters in Spotify IDs (P-5).
 * - Catches [ActivityNotFoundException] in the composable layer per Dev Notes rule 3 (AC2).
 * - Extracted to eliminate duplicated try/catch blocks (P-6).
 */
private fun launchSpotifyEpisode(
    context: Context,
    episodeId: String,
    viewModel: DriveSessionViewModel
) {
    viewModel.onOpenInSpotify(episodeId)
    try {
        // episodeId is a full Spotify URI (e.g. "spotify:episode:6FZj6eVQLmvii10w73SxEw")
        // — use it directly rather than wrapping it, which would double-encode the colons.
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(episodeId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        // Intent dispatched successfully — mark as launched so the ON_RESUME observer knows
        // the foreground transition was caused by Spotify opening (not an unrelated interrupt).
        viewModel.onSpotifyIntentLaunched()
    } catch (e: ActivityNotFoundException) {
        // AC2: Spotify not installed → dispatch error to state machine
        viewModel.onSpotifyNotInstalled()
    } catch (e: Exception) {
        viewModel.onPlaybackFailed()
    }
}

/**
 * Shows the episode queue with tappable rows and the bottom "Open in Spotify" CTA (AC1, AC5).
 * Shows a HANDING_OFF indicator when [DriveUiState.isHandingOff] is true.
 *
 * @param tapEnabled IG-1: true only when the session state allows Spotify playback (Playing/Paused).
 */
@Composable
private fun DriveActiveContent(
    uiState: DriveUiState,
    onEpisodeTap: (Episode) -> Unit,
    onOpenInSpotifyCta: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top "Open in Spotify" CTA — positioned at top to avoid Android nav bar overlap.
        // Not gated by tapEnabled: this CTA is the primary entry point in MVP1 and must
        // work from any session state. (AC1, AC5 — ≤2 taps to Spotify)
        Button(
            onClick = onOpenInSpotifyCta,
            enabled = !uiState.isHandingOff && uiState.episodes.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)  // UX-DR9: ≥64dp touch target
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (uiState.isHandingOff) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = 24.dp))
                    Text("Opening in Spotify…")
                }
            } else {
                Text("Open in Spotify")
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (uiState.episodes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No episodes queued",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.episodes, key = { it.id }) { episode ->
                    EpisodeTileDrive(
                        episode = episode,
                        isHandingOff = uiState.isHandingOff && uiState.handoffEpisodeId == episode.id,
                        onClick = { onEpisodeTap(episode) }
                    )
                    HorizontalDivider()
                }
            }
        }

    }
}

/**
 * Single episode row in drive mode. One tap fires the deep-link (AC1, AC5 — ≤2 taps).
 * Touch target enforced via `heightIn(min = 72.dp)` (UX-DR9).
 */
@Composable
private fun EpisodeTileDrive(
    episode: Episode,
    isHandingOff: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = !isHandingOff, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatDurationMs(episode.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (episode.tier != EpisodeTier.NONE) {
            TierBadgeIconOnly(tier = episode.tier)
        }
    }
}

/**
 * Error state with a Retry button (AC2, AC3).
 * Message adapts to the specific [AppError] type.
 */
@Composable
private fun DriveErrorContent(
    error: AppError,
    onRetry: () -> Unit
) {
    val message = when (error) {
        is AppError.SpotifyNotInstalled -> "Spotify is not installed"
        is AppError.SpotifyHandoffTimeout -> "Could not open Spotify — timed out"
        is AppError.NetworkUnavailable -> "No network connection"
        is AppError.SpotifyApiError -> "Spotify error (${error.code})"
        else -> "Playback error — please retry"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .padding(top = 24.dp)
                .heightIn(min = 64.dp)  // UX-DR9
        ) {
            Text("Retry")
        }
    }
}

// ---------------------------------------------------------------------------
// Story 4.11 — In-app playback controls (AC1–AC3, AC5)
// ---------------------------------------------------------------------------

/**
 * Shows the current episode title, a progress bar, elapsed/remaining time labels, and
 * pause/resume + skip buttons while PodDrive owns the audio via ExoPlayer.
 *
 * - Hidden during a Spotify handoff (`isHandingOff == true`) — AC5.
 * - Pause/resume button alternates based on [isPlaying].
 * - Skip triggers [onSkip] which auto-advances to the next episode (AC3).
 *
 * `internal` visibility allows [DriveSessionScreenTest] to invoke it directly for Compose UI tests.
 *
 * [Source: Story 4.11, AC1-AC3, AC5]
 */
@Composable
internal fun InAppPlaybackControls(
    uiState: DriveUiState,
    isPlaying: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit
) {
    val positionMs = uiState.playbackPositionMs
    val durationMs = uiState.playbackDurationMs.coerceAtLeast(1L)   // avoid div-by-zero
    val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Episode title
        if (uiState.playbackEpisodeTitle.isNotBlank()) {
            Text(
                text = uiState.playbackEpisodeTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (uiState.playbackPodcastName.isNotBlank()) {
            Text(
                text = uiState.playbackPodcastName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Progress bar (AC1)
        LinearProgressIndicator(
            progress = { progress },
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Elapsed / remaining labels (AC1)
        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "-${formatDuration((durationMs - positionMs).coerceAtLeast(0L))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Pause/Resume + Skip buttons (AC2, AC3) — ≥64dp touch targets per UX-DR9
        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = if (isPlaying) onPause else onResume,
                modifier = androidx.compose.ui.Modifier.heightIn(min = 64.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏸ Pause" else "▶ Resume",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(
                onClick = onSkip,
                modifier = androidx.compose.ui.Modifier.heightIn(min = 64.dp)
            ) {
                Text(
                    text = "⏭ Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Formats a duration in milliseconds to `mm:ss` (e.g. "03:45" or "62:10").
 * Used for elapsed and remaining time labels in [InAppPlaybackControls].
 * [Source: Story 4.11, AC1; Dev Notes — formatDuration() is a display concern]
 */
internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

/** Formats a duration in milliseconds to a human-readable "m:ss" or "h:mm:ss" string. */
private fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
