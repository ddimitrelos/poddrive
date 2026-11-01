package com.poddrive.android.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poddrive.api.SpotifyShow
import com.poddrive.domain.model.EpisodeTier

/**
 * Full-screen "Add first show" onboarding step (Story 2.6, UX-DR7).
 *
 * Renders search → results → tier selection flow.
 * Called from [OnboardingScreen] when state is [OnboardingUiState.AddFirstShow].
 *
 * @param state       Current UI state slice for this step.
 * @param onQueryChanged  Called on every keystroke (ViewModel applies 300ms debounce).
 * @param onShowSelected  User tapped a search result row.
 * @param onTierSelected  User chose LOVED or LIKED; [andProceed]=true means "Done", false="Add more".
 * @param onDone          User tapped "Done" without selecting a new tier — proceeds to HomeScreen.
 * @param onSkip          User tapped "Skip" — proceeds to HomeScreen without saving anything.
 */
@Composable
fun AddFirstShowScreen(
    state: OnboardingUiState.AddFirstShow,
    onQueryChanged: (String) -> Unit,
    onShowSelected: (SpotifyShow) -> Unit,
    onTierSelected: (EpisodeTier, Boolean) -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Add your first show",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Search for a podcast to tag before your first drive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // ── Tier selection panel (shown when a show has been tapped) ──────────
        if (state.selectedShow != null) {
            TierSelectionPanel(
                show = state.selectedShow,
                onTierSelected = onTierSelected,
                onDone = onDone
            )
        } else {
            // ── Search field ─────────────────────────────────────────────────
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    onQueryChanged(q)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search podcasts") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            // ── Error message ────────────────────────────────────────────────
            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
            }

            // ── Searching spinner ────────────────────────────────────────────
            if (state.isSearching) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
            }

            // ── Results list ─────────────────────────────────────────────────
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.searchResults, key = { it.id }) { show ->
                    ListItem(
                        headlineContent = { Text(show.name) },
                        supportingContent = {
                            Text(
                                text = "${show.totalEpisodes} episodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { onShowSelected(show) }
                    )
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Skip link ────────────────────────────────────────────────────────
        TextButton(onClick = onSkip) {
            Text("Skip for now")
        }
    }
}

/**
 * Panel displayed after the user selects a show — prompts for LOVED or LIKED tier.
 * "Done" proceeds to HomeScreen; "Add more" returns to search without proceeding.
 */
@Composable
private fun TierSelectionPanel(
    show: SpotifyShow,
    onTierSelected: (EpisodeTier, Boolean) -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = show.name,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Set priority tier",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onTierSelected(EpisodeTier.LOVED, true) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("LOVED")
            }
            OutlinedButton(
                onClick = { onTierSelected(EpisodeTier.LIKED, true) },
                modifier = Modifier.weight(1f)
            ) {
                Text("LIKED")
            }
        }
        Spacer(Modifier.height(8.dp))

        // "Add more" — saves tier but stays on the screen to add another show
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onTierSelected(EpisodeTier.LOVED, false) },
                modifier = Modifier.weight(1f)
            ) {
                Text("LOVED + add more")
            }
            OutlinedButton(
                onClick = { onTierSelected(EpisodeTier.LIKED, false) },
                modifier = Modifier.weight(1f)
            ) {
                Text("LIKED + add more")
            }
        }
        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onDone) {
            Text("Done")
        }
    }
}
