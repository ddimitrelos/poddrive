package com.poddrive.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.poddrive.android.ui.drive.DriveSessionScreen
import com.poddrive.android.ui.drive.DriveSessionViewModel
import com.poddrive.android.ui.home.HomeScreen
import com.poddrive.android.ui.onboarding.OnboardingScreen
import com.poddrive.android.ui.onboarding.OnboardingUiState
import com.poddrive.android.ui.onboarding.OnboardingViewModel
import com.poddrive.android.ui.theme.PodDriveTheme
import com.poddrive.domain.model.DriveSessionState

class MainActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()
    private val driveViewModel: DriveSessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // P2-7: Only process intents with the poddrive://callback scheme to avoid
        // accidental processing of non-OAuth cold-start intents.
        intent?.takeIf { it.data?.scheme == "poddrive" }
            ?.let { onboardingViewModel.handleOAuthRedirect(it) }

        setContent {
            PodDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by onboardingViewModel.uiState.collectAsState()
                    val driveState by driveViewModel.sessionState.collectAsState()
                    val isDriveActive = driveState !is DriveSessionState.Idle &&
                            driveState !is DriveSessionState.Ended

                    if (uiState is OnboardingUiState.Authenticated) {
                        if (isDriveActive) {
                            // UX-DR3: Suppress back gesture during active drive session.
                            // BackHandler is scoped here (not hoisted above the auth check) so it
                            // never fires on OnboardingScreen (P2-2 code review fix).
                            BackHandler(enabled = true) { /* no-op */ }
                            DriveSessionScreen(viewModel = driveViewModel)
                        } else {
                            HomeScreen(
                                onRevoked = { onboardingViewModel.resetToIdle() },
                                driveViewModel = driveViewModel
                            )
                        }
                    } else {
                        OnboardingScreen(
                            onAuthenticated = {
                                // Navigation handled reactively via uiState above
                            },
                            viewModel = onboardingViewModel
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when the OAuth redirect (poddrive://callback) arrives.
     * launchMode="singleTop" ensures this activity is reused rather than recreated (AC4).
     * Scheme guard matches onCreate — prevents non-OAuth intents from reaching the auth handler.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.takeIf { it.data?.scheme == "poddrive" }
            ?.let { onboardingViewModel.handleOAuthRedirect(it) }
    }
}
