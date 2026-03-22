package com.poddrive.android.ui.onboarding

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.poddrive.android.PodDriveApplication
import com.poddrive.domain.model.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Base64

sealed class OnboardingUiState {
    data object Idle : OnboardingUiState()
    data object LaunchingBrowser : OnboardingUiState()
    data object ExchangingTokens : OnboardingUiState()
    data object Authenticated : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
}

/**
 * ViewModel for the onboarding OAuth flow.
 * Delegates all business logic to [AuthenticateWithSpotifyUseCase] via [PodDriveApplication].
 *
 * [SavedStateHandle] is used to persist [pendingCodeVerifier] and [pendingState] across
 * process death — Android can kill the process while the user is in the Custom Tab, and
 * the verifier must survive to complete the PKCE exchange on redirect.
 *
 * State transitions: Idle → LaunchingBrowser → (user redirects back) → ExchangingTokens →
 *   Authenticated | Error
 */
class OnboardingViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val app = PodDriveApplication.from(application)

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    // Persisted in SavedStateHandle so they survive process death while the browser is open.
    private var pendingCodeVerifier: String?
        get() = savedStateHandle["pendingCodeVerifier"]
        set(value) { savedStateHandle["pendingCodeVerifier"] = value }

    private var pendingState: String?
        get() = savedStateHandle["pendingState"]
        set(value) { savedStateHandle["pendingState"] = value }

    /**
     * Returns the Spotify authorisation URL and stores the code_verifier and CSRF state
     * for later validation. The caller (OnboardingScreen) opens this URL in Chrome Custom Tabs.
     */
    fun buildAuthorizationUrl(): String {
        val verifier = app.authManager.generateCodeVerifier()
        val challenge = app.authManager.generateCodeChallenge(verifier)
        val state = generateSecureState()
        pendingCodeVerifier = verifier
        pendingState = state
        _uiState.value = OnboardingUiState.LaunchingBrowser
        return app.authManager.buildAuthorizationUrl(challenge, state)
    }

    /**
     * Called from [MainActivity.onNewIntent] (and [MainActivity.onCreate] for redirect cold-starts)
     * when the OAuth redirect URI arrives.
     *
     * Validates the CSRF [state] parameter before proceeding with token exchange (P1-1).
     */
    fun handleOAuthRedirect(intent: Intent) {
        // Only process poddrive://callback intents
        val uri: Uri = intent.data ?: return
        if (uri.scheme != "poddrive" || uri.host != "callback") return

        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        val returnedState = uri.getQueryParameter("state")
        val verifier = pendingCodeVerifier
        val expectedState = pendingState

        when {
            error != null -> {
                clearPending()
                _uiState.value = OnboardingUiState.Error("Spotify denied access: $error")
            }
            returnedState == null || returnedState != expectedState -> {
                clearPending()
                _uiState.value = OnboardingUiState.Error(
                    "OAuth state mismatch — possible CSRF attack. Please try again."
                )
            }
            code == null || verifier == null -> {
                clearPending()
                _uiState.value = OnboardingUiState.Error("OAuth redirect missing required parameters.")
            }
            else -> exchangeTokens(code, verifier)
        }
    }

    /** Called by [OnboardingScreen] when [ActivityNotFoundException] is caught launching the browser. */
    fun reportBrowserMissing() {
        clearPending()
        _uiState.value = OnboardingUiState.Error(
            "No browser found. Please install a browser app and try again."
        )
    }

    fun resetError() {
        _uiState.value = OnboardingUiState.Idle
    }

    /**
     * Resets the UI state to [OnboardingUiState.Idle], returning the user to the welcome screen.
     * Called after a successful Spotify access revocation (Story 1.5) to clear in-memory auth state.
     */
    fun resetToIdle() {
        clearPending()
        _uiState.value = OnboardingUiState.Idle
    }

    private fun exchangeTokens(code: String, verifier: String) {
        clearPending()
        _uiState.value = OnboardingUiState.ExchangingTokens
        viewModelScope.launch {
            when (val result = app.authenticateWithSpotifyUseCase.execute(code, verifier)) {
                is Result.Success -> _uiState.value = OnboardingUiState.Authenticated
                is Result.Failure -> _uiState.value =
                    OnboardingUiState.Error(result.error.toString())
            }
        }
    }

    private fun clearPending() {
        pendingCodeVerifier = null
        pendingState = null
    }

    /**
     * Generates a cryptographically secure CSRF state value using [SecureRandom].
     * 16 random bytes → 22-char Base64url string (~128 bits entropy).
     */
    private fun generateSecureState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
