package com.poddrive.android.ui.drive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poddrive.domain.model.DriveSessionState
import com.poddrive.domain.model.Episode
import com.poddrive.domain.model.EpisodeTier

/**
 * Full-screen drive session UI.
 *
 * States handled:
 * - Loading: 3 skeleton EpisodeTile rows (UX-DR10, AC4 of Story 4.2)
 * - Briefing/Playing: scrollable EpisodeTile queue (UX-DR4, AC3 of Story 4.2)
 * - Error: error panel with Retry button (UX-DR10, AC3 of Story 4.5)
 * - Ended: session complete message
 *
 * Back gesture is suppressed during an active session (UX-DR3).
 * Bottom navigation bar is absent (no Scaffold bottomBar).
 *
 * [Stories 4.2, 4.5]
 */
@Composable
fun DriveSessionScreen(
    viewModel: DriveSessionViewModel = viewModel()
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // UX-DR3: suppress back gesture during active drive session
    BackHandler(enabled = sessionState !is DriveSessionState.Ended) { /* no-op */ }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Session status header
            DriveSessionHeader(state = sessionState)

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            when {
                // Error state: show error panel with Retry (Story 4.5, AC3)
                sessionState is DriveSessionState.Error -> {
                    ErrorPanel(onRetry = viewModel::retryPlayback)
                }

                // Ended state
                sessionState is DriveSessionState.Ended -> {
                    SessionEndedPanel()
                }

                // Loading / Activating: show 3 skeleton rows (Story 4.2, AC4)
                isLoading || sessionState is DriveSessionState.Activating -> {
                    EpisodeQueueSkeleton()
                }

                // Briefing or Playing: show episode queue (Story 4.2, AC3)
                else -> {
                    EpisodeQueueList(
                        queue = queue,
                        currentEpisode = (sessionState as? DriveSessionState.Playing)?.episode,
                        onConfirmBriefing = viewModel::confirmBriefing,
                        isBriefing = sessionState is DriveSessionState.Briefing
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun DriveSessionHeader(state: DriveSessionState) {
    val label = when (state) {
        is DriveSessionState.Activating -> "Starting drive session…"
        is DriveSessionState.Briefing -> "Ready — tap to play"
        is DriveSessionState.Playing -> "Now playing"
        is DriveSessionState.Paused -> "Paused"
        is DriveSessionState.Error -> "Playback error"
        is DriveSessionState.Ended -> "Session ended"
        else -> "PodDrive"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

/**
 * Scrollable list of EpisodeTile rows showing the ordered queue (AC3, Story 4.2).
 * If in Briefing state, shows a "Start Playback" button at the top.
 */
@Composable
private fun EpisodeQueueList(
    queue: List<Episode>,
    currentEpisode: Episode?,
    onConfirmBriefing: () -> Unit,
    isBriefing: Boolean
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isBriefing && queue.isNotEmpty()) {
            item {
                // UX-DR9: ≥64dp touch target for drive-mode buttons
                Button(
                    onClick = onConfirmBriefing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                ) {
                    Text("Start Playback")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (queue.isEmpty()) {
            item {
                Text(
                    text = "No episodes in queue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(queue) { episode ->
                EpisodeTile(
                    episode = episode,
                    isPlaying = episode == currentEpisode
                )
            }
        }
    }
}

/**
 * Single episode row with title, tier badge, and playing indicator.
 * [Stories 4.2, 3.6]
 */
@Composable
private fun EpisodeTile(episode: Episode, isPlaying: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPlaying)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        tonalElevation = if (isPlaying) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)  // UX-DR9: ≥64dp drive-mode touch targets
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tier badge icon
            TierBadge(tier = episode.tier)
            Spacer(modifier = Modifier.width(12.dp))
            // Episode title
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** LOVED/LIKED icon badge. NONE tier episodes are not shown in the queue. */
@Composable
private fun TierBadge(tier: EpisodeTier) {
    val icon = when (tier) {
        EpisodeTier.LOVED -> Icons.Default.Favorite
        EpisodeTier.LIKED -> Icons.Default.ThumbUp
        EpisodeTier.NONE -> return  // excluded from queue; badge not shown
    }
    val tint = when (tier) {
        EpisodeTier.LOVED -> MaterialTheme.colorScheme.error
        EpisodeTier.LIKED -> MaterialTheme.colorScheme.primary
        EpisodeTier.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(
        imageVector = icon,
        contentDescription = tier.name,
        tint = tint,
        modifier = Modifier.size(20.dp)
    )
}

/**
 * 3 skeleton placeholder rows shown while BuildEpisodeQueueUseCase is running (AC4, Story 4.2).
 */
@Composable
private fun EpisodeQueueSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                // Shimmer placeholder — simple solid fill for MVP
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}

/**
 * Error panel with Retry button (AC3 of Story 4.5, UX-DR10).
 * Retry button meets ≥64dp drive-mode touch target (UX-DR9).
 */
@Composable
private fun ErrorPanel(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Could not connect.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),  // UX-DR9: ≥64dp touch target
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun SessionEndedPanel() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Drive session ended.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
