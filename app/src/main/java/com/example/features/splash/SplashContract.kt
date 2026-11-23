package com.example.features.splash

import kotlinx.coroutines.flow.StateFlow

data class SplashUiState(
    val isCheckingSession: Boolean = true,
    val statusLabel: String = "Preparing your storefront..."
)

interface SplashPresenterContract {
    val uiState: StateFlow<SplashUiState>
    fun startSessionCheck(onFinished: () -> Unit)
}
