package com.poddrive.android.ui.onboarding

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Welcome / onboarding screen.
 * Shows "Connect Spotify" button → opens Chrome Custom Tab for PKCE OAuth.
 * Displays inline error with retry on failure (AC5).
 * Notifies [onAuthenticated] when tokens are successfully stored (AC3).
 *
 * Note: If Spotify is not installed, the Custom Tab browser still works because
 * Spotify OAuth uses a standard web flow. The "Spotify not installed" check (AC4)
 * is relevant only if the app later attempts to deep-link into the Spotify app.
 * At this stage, we gracefully handle ActivityNotFoundException from the browser intent.
 */
@Composable
fun OnboardingScreen(
    onAuthenticated: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // P2-1: Use LaunchedEffect to avoid calling onAuthenticated() during composition,
    // which would fire on every recomposition while state remains Authenticated.
    LaunchedEffect(uiState) {
        if (uiState is OnboardingUiState.Authenticated) {
            onAuthenticated()
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
            text = "Your podcast co-pilot",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(48.dp))

        when (uiState) {
            is OnboardingUiState.Idle -> {
                Button(
                    onClick = {
                        val authUrl = viewModel.buildAuthorizationUrl()
                        try {
                            CustomTabsIntent.Builder().build()
                                .launchUrl(context, authUrl.toUri())
                        } catch (e: ActivityNotFoundException) {
                            // P1-4: Report the error instead of swallowing it silently (AC4).
                            viewModel.reportBrowserMissing()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Spotify")
                }
            }

            is OnboardingUiState.LaunchingBrowser -> {
                Text("Opening Spotify login…")
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            is OnboardingUiState.ExchangingTokens -> {
                Text("Connecting…")
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            is OnboardingUiState.Error -> {
                Text(
                    text = (uiState as OnboardingUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.resetError() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.resetError() }) {
                    Text("Cancel")
                }
            }

            is OnboardingUiState.Authenticated -> {
                // handled via LaunchedEffect above — won't render content here
            }
        }
    }
}
