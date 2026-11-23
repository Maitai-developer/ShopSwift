package com.example.features.auth

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

interface LoginPresenterContract {
    val uiState: StateFlow<LoginUiState>
    fun onGoogleSignInClicked(activityContext: Context, onAuthSuccess: () -> Unit)
    fun clearError()
}
