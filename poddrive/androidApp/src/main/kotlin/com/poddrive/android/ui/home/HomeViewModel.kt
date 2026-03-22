package com.poddrive.android.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poddrive.android.PodDriveApplication
import com.poddrive.domain.model.Podcast
import com.poddrive.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PodDriveApplication.from(application)

    sealed class UiState {
        data object Loading : UiState()
        data class Success(val podcasts: List<Podcast>) : UiState()
        data class Error(
            val message: String,
            val cachedPodcasts: List<Podcast> = emptyList()
        ) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Syncs the podcast library from Spotify and updates [uiState].
     * On failure, attempts to show cached data with an error banner.
     */
    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            when (val result = app.syncPodcastLibraryUseCase.execute()) {
                is Result.Success -> _uiState.value = UiState.Success(result.value)
                is Result.Failure -> {
                    // Show cached podcasts (if any) alongside the error
                    val cached = withContext(Dispatchers.IO) { app.podcastRepository.getAllWithUnheardCount() }
                    _uiState.value = UiState.Error(
                        message = "Could not refresh library. ${result.error}",
                        cachedPodcasts = cached
                    )
                }
            }
        }
    }
}
