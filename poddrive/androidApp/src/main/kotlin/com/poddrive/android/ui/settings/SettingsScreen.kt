package com.poddrive.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Settings screen with "Disconnect Spotify" action (Story 1.5, FR3).
 *
 * Renders a "Disconnect Spotify" button that calls [SettingsViewModel.revokeAccess].
 * On [SettingsViewModel.UiState.Revoked], invokes [onRevoked] to reset the top-level
 * auth state back to the onboarding screen.
 *
 * This screen is displayed in place of the authenticated placeholder from Stories 1.3/1.4
 * until a full home screen is built in Epic 2+.
 */
@Composable
fun SettingsScreen(
    onRevoked: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate back to onboarding as soon as Revoked state is emitted.
    LaunchedEffect(uiState) {
        if (uiState is SettingsViewModel.UiState.Revoked) {
            onRevoked()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PodDrive",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connected to Spotify",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(48.dp))

        when (uiState) {
            is SettingsViewModel.UiState.Idle -> {
                Button(
                    onClick = { viewModel.revokeAccess() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect Spotify")
                }
            }

            is SettingsViewModel.UiState.Revoking -> {
                Text("Disconnecting…")
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            is SettingsViewModel.UiState.Revoked -> {
                // Handled by LaunchedEffect above — no UI needed here
            }

            is SettingsViewModel.UiState.Error -> {
                Text(
                    text = (uiState as SettingsViewModel.UiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.revokeAccess() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { /* stay on screen — user did not disconnect */ }) {
                    Text("Cancel")
                }
            }
        }
    }
}
