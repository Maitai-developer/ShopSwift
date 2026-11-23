package com.example.features.splash

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.AuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SplashPresenter(
    private val appContext: Context
) : ViewModel(), SplashPresenterContract {

    private val _uiState = MutableStateFlow(SplashUiState())
    override val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    private var hasStarted = false

    override fun startSessionCheck(onFinished: () -> Unit) {
        if (hasStarted) return
        hasStarted = true

        viewModelScope.launch {
            delay(1150L)
            val credentialManager = CredentialManager.create(appContext)
            AuthManager.attemptAutoSignIn(
                context = appContext,
                credentialManager = credentialManager,
                scope = viewModelScope,
                onAuthSuccess = {
                    _uiState.update { it.copy(isCheckingSession = false, statusLabel = "Welcome back") }
                    onFinished()
                },
                onUnauthenticated = {
                    _uiState.update { it.copy(isCheckingSession = false, statusLabel = "Ready") }
                    onFinished()
                }
            )
        }
    }
}
