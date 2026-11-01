package com.poddrive.android.ui.onboarding

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.poddrive.android.PodDriveApplication
import com.poddrive.api.SpotifyShow
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast
import com.poddrive.domain.model.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Base64

sealed class OnboardingUiState {
    data object Idle : OnboardingUiState()
    data object LaunchingBrowser : OnboardingUiState()
    data object ExchangingTokens : OnboardingUiState()
    /** Checking whether the user already has followed Spotify podcasts (skip-guard for AC5). */
    data object CheckingLibrary : OnboardingUiState()
    /**
     * "Add first show" onboarding step — shown when the user has no followed Spotify podcasts.
     * [searchResults] is populated after a debounced query. [selectedShow] is the show the user
     * tapped and is about to tier. [isSearching] shows a progress indicator during the API call.
     * [error] carries a transient error message (search failure, save failure).
     */
    data class AddFirstShow(
        val searchResults: List<SpotifyShow> = emptyList(),
        val isSearching: Boolean = false,
        val selectedShow: SpotifyShow? = null,
        val error: String? = null
    ) : OnboardingUiState()
    data object Authenticated : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
}

/**
 * ViewModel for the onboarding OAuth flow and the "Add first show" step (Story 2.6).
 * Delegates all business logic to use cases via [PodDriveApplication].
 *
 * State machine:
 *   Idle → LaunchingBrowser → (redirect) → ExchangingTokens →
 *     CheckingLibrary → Authenticated (has shows)
 *     CheckingLibrary → AddFirstShow  (no shows — Story 2.6)
 *       AddFirstShow → Authenticated  (Done / Skip)
 *   Any → Error
 *
 * [SavedStateHandle] persists [pendingCodeVerifier] and [pendingState] across process death
 * while the user is in the Custom Tab browser.
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

    /** Cancellable job for the 300ms debounced search (Story 2.6 — AC1). */
    private var searchJob: Job? = null

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

    // ─── Story 2.6 — Add First Show ─────────────────────────────────────────

    /**
     * Debounced search: cancels any pending search, waits 300ms, then calls
     * [SearchSpotifyShowsUseCase]. Blank queries clear results immediately (no network call).
     * (AC1 — 300ms debounce, artwork/name/episode count in results)
     */
    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            val current = _uiState.value
            if (current is OnboardingUiState.AddFirstShow) {
                _uiState.value = current.copy(searchResults = emptyList(), isSearching = false, error = null)
            }
            return
        }
        val current = _uiState.value
        if (current is OnboardingUiState.AddFirstShow) {
            _uiState.value = current.copy(isSearching = true, error = null)
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            ensureActive()
            val result = withContext(Dispatchers.IO) {
                app.searchSpotifyShowsUseCase.execute(query)
            }
            ensureActive()
            val state = _uiState.value
            if (state is OnboardingUiState.AddFirstShow) {
                when (result) {
                    is Result.Success -> _uiState.value = state.copy(
                        searchResults = result.value,
                        isSearching = false,
                        error = null
                    )
                    is Result.Failure -> _uiState.value = state.copy(
                        isSearching = false,
                        error = "Search failed. ${result.error}"
                    )
                }
            }
        }
    }

    /**
     * User tapped a search result — set it as the selected show awaiting tier assignment.
     * (AC2)
     */
    fun onShowSelected(show: SpotifyShow) {
        val current = _uiState.value
        if (current is OnboardingUiState.AddFirstShow) {
            _uiState.value = current.copy(selectedShow = show, error = null)
        }
    }

    /**
     * User set a tier for the currently selected show. Saves the show to the local DB
     * with the chosen tier. Then:
     *  - if [andProceed] == true  → "Done" path → navigate to HomeScreen (emit Authenticated)
     *  - if [andProceed] == false → "Add more" path → clear selection, stay in AddFirstShow
     * Save errors are surfaced via [OnboardingUiState.AddFirstShow.error] rather than silently
     * discarded. A concurrent [proceedToHome] call cannot clobber the post-save transition
     * because we snapshot [_uiState] after the IO block. (AC2, AC3)
     */
    fun onTierSelected(tier: EpisodeTier, andProceed: Boolean) {
        val current = _uiState.value as? OnboardingUiState.AddFirstShow ?: return
        val show = current.selectedShow ?: return
        viewModelScope.launch {
            val podcast = Podcast(
                id = show.uri,
                name = show.name,
                description = show.description,
                imageUrl = show.images.maxByOrNull { it.width ?: 0 }?.url ?: "",
                episodeCount = show.totalEpisodes,
                unheardEpisodeCount = 0,
                tier = tier,
                lastSyncedAt = com.poddrive.currentTimeMillis()
            )
            val saveError: String? = withContext(Dispatchers.IO) {
                try {
                    app.podcastRepository.upsertAll(listOf(podcast))
                    app.setEpisodeTierUseCase.execute(podcast.id, tier)
                    null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { "Failed to save show. Please try again." }
            }
            if (saveError != null) {
                val s = _uiState.value
                if (s is OnboardingUiState.AddFirstShow) {
                    _uiState.value = s.copy(error = saveError)
                }
                return@launch
            }
            // Re-snapshot state after IO: guards against a concurrent proceedToHome() call
            // that may have already moved the state machine forward.
            val stateNow = _uiState.value
            if (andProceed) {
                _uiState.value = OnboardingUiState.Authenticated
            } else if (stateNow is OnboardingUiState.AddFirstShow) {
                _uiState.value = stateNow.copy(selectedShow = null, error = null)
            }
        }
    }

    /**
     * "Add more" action — clears the selected show and returns to the search view
     * WITHOUT saving any tier. (AC3)
     */
    fun onAddMore() {
        val current = _uiState.value as? OnboardingUiState.AddFirstShow ?: return
        _uiState.value = current.copy(selectedShow = null, error = null)
    }

    /**
     * User tapped "Done" without selecting a tier, or the "Skip" link.
     * Proceeds directly to HomeScreen. (AC3, AC5)
     */
    fun proceedToHome() {
        _uiState.value = OnboardingUiState.Authenticated
    }

    // ─── Private ────────────────────────────────────────────────────────────

    private fun exchangeTokens(code: String, verifier: String) {
        clearPending()
        _uiState.value = OnboardingUiState.ExchangingTokens
        viewModelScope.launch {
            when (val result = app.authenticateWithSpotifyUseCase.execute(code, verifier)) {
                is Result.Success -> checkLibraryAndProceed()
                is Result.Failure -> _uiState.value =
                    OnboardingUiState.Error(result.error.toString())
            }
        }
    }

    /**
     * After a successful token exchange, check if the user already has Spotify followed shows.
     * If yes → skip the "Add first show" step. If no (empty library) → show it.
     * Network/auth failures emit [OnboardingUiState.Error] rather than silently routing the
     * user into the onboarding flow. (AC5)
     */
    private fun checkLibraryAndProceed() {
        _uiState.value = OnboardingUiState.CheckingLibrary
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                app.libraryApiClient.fetchFollowedShows()
            }
            _uiState.value = when {
                result is Result.Success && result.value.isNotEmpty() ->
                    OnboardingUiState.Authenticated
                result is Result.Success ->
                    // Authenticated but no followed shows — show the onboarding step.
                    OnboardingUiState.AddFirstShow()
                else ->
                    // Network error, 401, etc. Surface to the user rather than silently
                    // forcing them into the "Add first show" flow.
                    OnboardingUiState.Error("Library error: $result")
            }
        }
    }

    private fun clearPending() {
        pendingCodeVerifier = null
        pendingState = null
    }

    private fun generateSecureState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        /** Debounce delay for Spotify show search input (AC1). */
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
