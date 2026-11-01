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
 * Welcome / onboarding screen dispatcher.
 *
 * Handles all [OnboardingUiState] values:
 *  - Idle, LaunchingBrowser, ExchangingTokens, Error → OAuth flow (Stories 1.x)
 *  - CheckingLibrary → shows a spinner while the library skip-check runs (Story 2.6 AC5)
 *  - AddFirstShow → delegates to [AddFirstShowScreen] (Story 2.6 AC1–AC3)
 *  - Authenticated → calls [onAuthenticated] to navigate to HomeScreen
 *
 * Note: If Spotify is not installed, the Custom Tab browser still works because
 * Spotify OAuth uses a standard web flow. The "Spotify not installed" check (AC4 of Story 1.3)
 * is relevant only if the app later attempts to deep-link into the Spotify app.
 */
@Composable
fun OnboardingScreen(
    onAuthenticated: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // P2-1: Use LaunchedEffect to avoid calling onAuthenticated() during composition.
    LaunchedEffect(uiState) {
        if (uiState is OnboardingUiState.Authenticated) {
            onAuthenticated()
        }
    }

    // AddFirstShow is a full-screen step — render outside the centred OAuth column.
    if (uiState is OnboardingUiState.AddFirstShow) {
        AddFirstShowScreen(
            state = uiState as OnboardingUiState.AddFirstShow,
            onQueryChanged = viewModel::onSearchQueryChanged,
            onShowSelected = viewModel::onShowSelected,
            onTierSelected = { tier, andProceed -> viewModel.onTierSelected(tier, andProceed) },
            onDone = viewModel::proceedToHome,
            onSkip = viewModel::proceedToHome
        )
        return
    }

    // OAuth / checking-library column layout
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

            is OnboardingUiState.CheckingLibrary -> {
                // Story 2.6 AC5: checking whether the user already has followed shows.
                Text("Setting up your library…")
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

            is OnboardingUiState.AddFirstShow -> {
                // handled by early-return branch above — unreachable here
            }
        }
    }
}
