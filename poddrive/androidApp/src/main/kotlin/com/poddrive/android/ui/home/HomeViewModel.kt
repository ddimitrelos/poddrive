package com.poddrive.android.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poddrive.android.PodDriveApplication
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast
import com.poddrive.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PodDriveApplication.from(application)

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val podcasts: List<Podcast>,
            /** True when library cache is >24h old (FR32, Story 2.5). */
            val isStale: Boolean = false
        ) : UiState()
        data class Error(
            val message: String,
            val cachedPodcasts: List<Podcast> = emptyList()
        ) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Podcast currently targeted by the tier bottom sheet. Null = sheet dismissed. */
    private val _selectedPodcast = MutableStateFlow<Podcast?>(null)
    val selectedPodcast: StateFlow<Podcast?> = _selectedPodcast.asStateFlow()

    /** Active tier filter. NONE = "All" (default). Resets to NONE on app restart (ephemeral). */
    private val _selectedFilter = MutableStateFlow(EpisodeTier.NONE)
    val selectedFilter: StateFlow<EpisodeTier> = _selectedFilter.asStateFlow()

    fun setFilter(filter: EpisodeTier) {
        _selectedFilter.value = filter
    }

    private var loadLibraryJob: Job? = null
    private var setTierJob: Job? = null  // P-5: tracked to prevent untracked races

    /**
     * Syncs the podcast library from Spotify and updates [uiState].
     * Cancels any in-progress sync before starting a new one.
     * On failure, attempts to show cached data with an error banner.
     */
    fun loadLibrary() {
        loadLibraryJob?.cancel()
        loadLibraryJob = viewModelScope.launch {
            ensureActive()  // P-6: bail if cancelled before first suspension point
            _uiState.value = UiState.Loading
            when (val result = app.syncPodcastLibraryUseCase.execute()) {
                is Result.Success -> {
                    ensureActive()  // P-6: don't overwrite state if a newer job has started
                    _uiState.value = UiState.Success(result.value)
                }
                is Result.Failure -> {
                    val cached = withContext(Dispatchers.IO) { app.podcastRepository.getAllWithUnheardCount() }
                    ensureActive()  // P-6
                    _uiState.value = UiState.Error(
                        message = "Could not refresh library. ${result.error}",
                        cachedPodcasts = cached
                    )
                }
            }
        }
    }

    /** Opens the tier bottom sheet for [podcast]. */
    fun showTierSheet(podcast: Podcast) {
        _selectedPodcast.value = podcast
    }

    /** Dismisses the tier bottom sheet without making changes. */
    fun dismissTierSheet() {
        _selectedPodcast.value = null
    }

    /**
     * Sets the tier for [podcast] to [tier] (pass [EpisodeTier.NONE] to remove).
     * Dismisses the sheet, persists the change, then refreshes the list from the DB.
     */
    fun setTier(podcast: Podcast, tier: EpisodeTier) {
        _selectedPodcast.value = null  // dismiss sheet immediately (optimistic UX)
        setTierJob?.cancel()           // P-5: cancel any in-flight tier write
        setTierJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                app.setEpisodeTierUseCase.execute(podcast.id, tier)
            }
            when (result) {
                is Result.Success -> {
                    // P-2: wrap DB reload in try/catch — unhandled throw would freeze the UI
                    val updated = try {
                        withContext(Dispatchers.IO) { app.podcastRepository.getAllWithUnheardCount() }
                    } catch (e: Exception) {
                        val current = when (val s = _uiState.value) {
                            is UiState.Success -> s.podcasts
                            is UiState.Error   -> s.cachedPodcasts
                            else               -> emptyList()
                        }
                        _uiState.value = UiState.Error(
                            message = "Tier saved but could not reload library. ${e.message}",
                            cachedPodcasts = current
                        )
                        return@launch
                    }
                    _uiState.value = UiState.Success(updated)
                }
                is Result.Failure -> {
                    // P-7: preserve whatever list was visible (Success or Error cached)
                    val current = when (val s = _uiState.value) {
                        is UiState.Success -> s.podcasts
                        is UiState.Error   -> s.cachedPodcasts
                        else               -> emptyList()
                    }
                    _uiState.value = UiState.Error(
                        message = "Could not update tier. ${result.error}",
                        cachedPodcasts = current
                    )
                }
            }
        }
    }
}
