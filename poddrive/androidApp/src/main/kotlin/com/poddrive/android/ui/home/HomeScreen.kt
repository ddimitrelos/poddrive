package com.poddrive.android.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poddrive.android.ui.drive.DriveSessionViewModel
import com.poddrive.android.ui.settings.SettingsScreen
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRevoked: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    driveViewModel: DriveSessionViewModel = viewModel()  // Story 3.3
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()
    val selectedPodcast by viewModel.selectedPodcast.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

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
            0 -> ShowsTabContent(
                uiState = uiState,
                paddingValues = paddingValues,
                selectedFilter = selectedFilter,
                onFilterSelected = viewModel::setFilter,
                onLongPress = { viewModel.showTierSheet(it) },
                driveViewModel = driveViewModel
            )
            1 -> DiscoverPlaceholder(paddingValues = paddingValues)
            2 -> SettingsScreen(onRevoked = onRevoked)
        }

        // P-8: ModalBottomSheet inside Scaffold for correct system-inset context.
        // P-1: capture podcast as local val at composition time so lambdas hold a stable,
        //      non-nullable reference rather than re-reading the nullable State at tap time.
        if (selectedPodcast != null) {
            val podcast = selectedPodcast!!  // safe: inside null-guard, captured as stable val
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissTierSheet() },
                sheetState = sheetState
            ) {
                TierSheetContent(
                    podcast = podcast,
                    onSetLoved = { viewModel.setTier(podcast, EpisodeTier.LOVED) },
                    onSetLiked = { viewModel.setTier(podcast, EpisodeTier.LIKED) },
                    onRemoveTier = { viewModel.setTier(podcast, EpisodeTier.NONE) }
                )
            }
        }
    }
}

/** Bottom sheet content for tier selection (UX-DR9 — bottom sheet, not dialog). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TierSheetContent(
    podcast: Podcast,
    onSetLoved: () -> Unit,
    onSetLiked: () -> Unit,
    onRemoveTier: () -> Unit
) {
    // P-8: navigationBarsPadding() ensures content clears gesture navigation bar on all devices
    Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(
            text = podcast.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        ListItem(
            headlineContent = { Text("Set Loved") },
            leadingContent = {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFF87171))
            },
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onClick = onSetLoved
            )
        )
        ListItem(
            headlineContent = { Text("Set Liked") },
            leadingContent = {
                Icon(Icons.Default.ThumbUp, contentDescription = null, tint = Color(0xFF60A5FA))
            },
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onClick = onSetLiked
            )
        )
        ListItem(
            headlineContent = { Text("Remove Tier") },
            leadingContent = {
                Icon(Icons.Default.Delete, contentDescription = null)
            },
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onClick = onRemoveTier
            )
        )
    }
}

@Composable
private fun ShowsTabContent(
    uiState: HomeViewModel.UiState,
    paddingValues: PaddingValues,
    selectedFilter: EpisodeTier,
    onFilterSelected: (EpisodeTier) -> Unit,
    onLongPress: (Podcast) -> Unit,
    driveViewModel: DriveSessionViewModel
) {
    when (uiState) {
        is HomeViewModel.UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        is HomeViewModel.UiState.Success -> {
            val filteredPodcasts = when (selectedFilter) {
                EpisodeTier.NONE -> uiState.podcasts
                else             -> uiState.podcasts.filter { it.tier == selectedFilter }
            }
            PodcastList(
                podcasts = filteredPodcasts,
                paddingValues = paddingValues,
                showFilter = true,
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
                onLongPress = onLongPress,
                isStale = uiState.isStale,
                driveViewModel = driveViewModel
            )
        }
        is HomeViewModel.UiState.Error -> {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                // P-13: no filter row in Error state (spec anti-pattern); show all cached podcasts
                if (uiState.cachedPodcasts.isNotEmpty()) {
                    PodcastList(
                        podcasts = uiState.cachedPodcasts,
                        paddingValues = PaddingValues(0.dp),
                        showFilter = false,
                        selectedFilter = EpisodeTier.NONE,
                        onFilterSelected = {},
                        onLongPress = onLongPress,
                        isStale = false,   // spec anti-pattern: no staleness banner in Error state
                        driveViewModel = driveViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastList(
    podcasts: List<Podcast>,
    paddingValues: PaddingValues,
    showFilter: Boolean,  // P-13: false in Error state per spec anti-pattern
    selectedFilter: EpisodeTier,
    onFilterSelected: (EpisodeTier) -> Unit,
    onLongPress: (Podcast) -> Unit,
    driveViewModel: DriveSessionViewModel,
    isStale: Boolean = false   // FR32: shows staleness banner when cache is >24 h old
) {
    LazyColumn(contentPadding = paddingValues) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "My Shows",
                    style = MaterialTheme.typography.headlineSmall
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(48.dp)
                        .heightIn(min = 3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                // Story 2.6 AC4 (UX-DR7): idle-state prompt shown until drive sessions are active.
                Text(
                    text = "Connect your car to start your first session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        // Story 3.3: Manual drive session trigger — FR13, UX-DR9 (≥64dp touch target)
        item {
            Button(
                onClick = { driveViewModel.startManualDriveSession() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Start Drive")
            }
        }
        if (isStale) {
            item { StalenessBanner() }
        }
        if (showFilter) {
            item {
                TierFilterTabRow(selected = selectedFilter, onSelect = onFilterSelected)
            }
        }
        if (podcasts.isEmpty() && selectedFilter != EpisodeTier.NONE) {
            item {
                val tierLabel = if (selectedFilter == EpisodeTier.LOVED) "Loved" else "Liked"
                // P-11: fillParentMaxWidth + padding instead of fillParentMaxSize to avoid
                //       overlapping the header and filter-row items above this one.
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No $tierLabel shows yet — long-press a show to tag it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(podcasts, key = { it.id }) { podcast ->
                ShowTile(podcast = podcast, onLongPress = { onLongPress(podcast) })
                HorizontalDivider()
            }
        }
    }
}

/**
 * Single podcast row. Long-press opens the tier bottom sheet. (UX-DR6, UX-DR9)
 * Touch target ≥44dp enforced via `heightIn(min = 72.dp)`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShowTile(
    podcast: Podcast,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    val avatarBackground = when (podcast.tier) {
        EpisodeTier.LOVED -> Color(0x33F87171)   // coral 20%
        EpisodeTier.LIKED -> Color(0x3360A5FA)   // blue 20%
        EpisodeTier.NONE  -> MaterialTheme.colorScheme.surfaceVariant
    }
    val avatarTextColor = when (podcast.tier) {
        EpisodeTier.LOVED -> Color(0xFFF87171)
        EpisodeTier.LIKED -> Color(0xFF60A5FA)
        EpisodeTier.NONE  -> MaterialTheme.colorScheme.onSurface
    }

    // P-14: Reverted to spec-prescribed Row layout — removed unspecified LOVED-only left strip.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)  // ≥44dp touch target (UX-DR9)
            .combinedClickable(
                onClick = {},  // future: navigate to episode list
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Artwork placeholder (52dp rounded square) — Coil/AsyncImage added in a future story
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.small)
                .background(avatarBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = podcast.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = avatarTextColor
            )
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

/**
 * FULL variant `TierBadge` — icon + label pill. Used on `ShowTile` in management mode.
 * Never colour-only: always has icon AND text (UX-DR11).
 * [Source: ux-design-specification.md#TierBadge, UX-DR5]
 */
@Composable
fun TierBadge(tier: EpisodeTier, modifier: Modifier = Modifier) {
    val (backgroundColor, iconVector, label) = when (tier) {
        EpisodeTier.LOVED -> Triple(Color(0x21F87171), Icons.Default.Favorite, "Loved")
        EpisodeTier.LIKED -> Triple(Color(0x2160A5FA), Icons.Default.ThumbUp, "Liked")
        // P-18: restored error() guard — silent return masked misuse at call sites
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

/**
 * ICON_ONLY variant `TierBadge` — coloured dot with icon. Used in drive mode (Story 3.x+).
 * Distinct from [TierBadge] FULL variant: no label text, smaller footprint.
 * Never colour-only: icon provides the non-colour signal (UX-DR5, UX-DR11).
 * [Source: ux-design-specification.md#TierBadge, UX-DR5]
 */
@Composable
fun TierBadgeIconOnly(tier: EpisodeTier, modifier: Modifier = Modifier) {
    if (tier == EpisodeTier.NONE) return
    val (tintColor, iconVector, description) = when (tier) {
        EpisodeTier.LOVED -> Triple(Color(0xFFF87171), Icons.Default.Favorite, "Loved")
        EpisodeTier.LIKED -> Triple(Color(0xFF60A5FA), Icons.Default.ThumbUp, "Liked")
        EpisodeTier.NONE -> return
    }
    // P-17: 24dp container / 16dp icon — legible and accessible as a decorative indicator.
    //       Not a tap target (contentDescription intentionally set for a11y labels in drive mode).
    Box(
        modifier = modifier
            .size(24.dp)
            .background(tintColor.copy(alpha = 0.2f), shape = MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = tintColor
        )
    }
}

/**
 * Three-pill filter row: All / Loved / Liked. Placed above the podcast list in Shows tab. (UX-DR6)
 * Never colour-only: label text accompanies every icon (UX-DR11).
 */
@Composable
fun TierFilterTabRow(
    selected: EpisodeTier,
    onSelect: (EpisodeTier) -> Unit,
    modifier: Modifier = Modifier
) {
    // P-4: selectableGroup() announces this Row as a tab list to accessibility services.
    Row(
        modifier = modifier
            .selectableGroup()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // P-4: semantics { role = Role.Tab } overrides FilterChip's default Role.Checkbox.
        FilterChip(
            selected = selected == EpisodeTier.NONE,
            onClick = { onSelect(EpisodeTier.NONE) },
            label = { Text("All") },
            modifier = Modifier.semantics { role = Role.Tab }
        )
        FilterChip(
            selected = selected == EpisodeTier.LOVED,
            onClick = { onSelect(EpisodeTier.LOVED) },
            leadingIcon = {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text("Loved") },
            modifier = Modifier.semantics { role = Role.Tab }
        )
        FilterChip(
            selected = selected == EpisodeTier.LIKED,
            onClick = { onSelect(EpisodeTier.LIKED) },
            leadingIcon = {
                Icon(
                    Icons.Default.ThumbUp,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text("Liked") },
            modifier = Modifier.semantics { role = Role.Tab }
        )
    }
}

/**
 * Non-intrusive one-line caption shown when the library cache is >24 h old. (FR32)
 * Uses tertiary text color per UX design system (color-text-tertiary #6B7280).
 * Renders above the filter tabs so it is always visible regardless of tier selection.
 */
@Composable
private fun StalenessBanner() {
    Text(
        text = "Last synced over 24 hours ago",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun DiscoverPlaceholder(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentAlignment = Alignment.Center
    ) { Text("Discover — Coming in a future story") }
}
