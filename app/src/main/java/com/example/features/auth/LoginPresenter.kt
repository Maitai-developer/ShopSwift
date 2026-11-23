package com.example.features.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LoginPresenter : ViewModel(), LoginPresenterContract {

    private val _uiState = MutableStateFlow(LoginUiState())
    override val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    override fun onGoogleSignInClicked(activityContext: Context, onAuthSuccess: () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        val credentialManager = CredentialManager.create(activityContext)
        AuthManager.onGoogleSignInClicked(
            context = activityContext,
            credentialManager = credentialManager,
            scope = viewModelScope,
            onAuthSuccess = {
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                onAuthSuccess()
            },
            onAuthError = { message ->
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            },
            onAuthCancelled = {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Sign-in was cancelled. Tap below to choose an account."
                    )
                }
            }
        )
    }

    override fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
