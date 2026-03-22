package com.poddrive.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poddrive.android.ui.settings.SettingsScreen
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast

@Composable
fun HomeScreen(
    onRevoked: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()

    // Trigger library load when Shows tab is first displayed
    LaunchedEffect(Unit) { viewModel.loadLibrary() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Shows") },
                    label = { Text("Shows") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Discover") },
                    label = { Text("Discover") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> ShowsTabContent(uiState = uiState, paddingValues = paddingValues)
            1 -> DiscoverPlaceholder(paddingValues = paddingValues)
            2 -> SettingsScreen(onRevoked = onRevoked)
        }
    }
}

@Composable
private fun ShowsTabContent(
    uiState: HomeViewModel.UiState,
    paddingValues: PaddingValues
) {
    when (uiState) {
        is HomeViewModel.UiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        is HomeViewModel.UiState.Success -> {
            PodcastList(podcasts = uiState.podcasts, paddingValues = paddingValues)
        }
        is HomeViewModel.UiState.Error -> {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                if (uiState.cachedPodcasts.isNotEmpty()) {
                    PodcastList(podcasts = uiState.cachedPodcasts, paddingValues = PaddingValues(0.dp))
                }
            }
        }
    }
}

@Composable
private fun PodcastList(podcasts: List<Podcast>, paddingValues: PaddingValues) {
    LazyColumn(contentPadding = paddingValues) {
        items(podcasts, key = { it.id }) { podcast ->
            ShowTile(podcast = podcast)
            HorizontalDivider()
        }
    }
}

@Composable
fun ShowTile(podcast: Podcast, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)  // ≥44dp touch target (UX-DR9)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Artwork: 52dp rounded square (UX-DR6)
        // Placeholder Box — Coil is NOT yet a dependency (added in a future story)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(text = podcast.name.take(1), style = MaterialTheme.typography.titleMedium)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = podcast.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                text = if (podcast.unheardEpisodeCount > 0) "${podcast.unheardEpisodeCount} new" else "Up to date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (podcast.tier != EpisodeTier.NONE) {
            TierBadge(tier = podcast.tier)
        }
    }
}

@Composable
fun TierBadge(tier: EpisodeTier, modifier: Modifier = Modifier) {
    val (backgroundColor, iconVector, label) = when (tier) {
        EpisodeTier.LOVED -> Triple(Color(0x21F87171), Icons.Default.Favorite, "Loved")
        EpisodeTier.LIKED -> Triple(Color(0x2160A5FA), Icons.Default.ThumbUp, "Liked")
        EpisodeTier.NONE -> error("TierBadge must not be called with EpisodeTier.NONE — guard at call site")
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = "$label podcast",  // accessibility: never colour-only (UX-DR11)
                modifier = Modifier.size(12.dp),
                tint = if (tier == EpisodeTier.LOVED) Color(0xFFF87171) else Color(0xFF60A5FA)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (tier == EpisodeTier.LOVED) Color(0xFFF87171) else Color(0xFF60A5FA)
            )
        }
    }
}

@Composable
private fun DiscoverPlaceholder(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentAlignment = Alignment.Center
    ) { Text("Discover — Coming in a future story") }
}
