package com.poddrive.android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poddrive.android.PodDriveApplication
import com.poddrive.domain.model.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 * Delegates "Disconnect Spotify" action to [RevokeSpotifyAccessUseCase] via [PodDriveApplication].
 *
 * State transitions: Idle → Revoking → Revoked | Error
 * On [UiState.Revoked], the caller (SettingsScreen) invokes onRevoked() to reset
 * the top-level auth state.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PodDriveApplication.from(application)

    sealed class UiState {
        data object Idle : UiState()
        data object Revoking : UiState()
        data object Revoked : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Clears all stored Spotify credentials and transitions state to [UiState.Revoked].
     * Called when the user confirms "Disconnect Spotify".
     */
    fun revokeAccess() {
        viewModelScope.launch {
            _uiState.value = UiState.Revoking
            when (app.revokeSpotifyAccessUseCase.execute()) {
                is Result.Success -> _uiState.value = UiState.Revoked
                is Result.Failure -> _uiState.value =
                    UiState.Error("Failed to disconnect Spotify. Please try again.")
            }
        }
    }
}
