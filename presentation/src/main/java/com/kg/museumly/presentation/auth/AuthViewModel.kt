package com.kg.museumly.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.domain.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _effects = Channel<AuthEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onGoogleIdTokenResult(result: GoogleIdTokenResult) {
        when (result) {
            is GoogleIdTokenResult.Success ->
                if (!_uiState.value.isLoading) login(result.idToken)
            GoogleIdTokenResult.NoGoogleAccount ->
                _uiState.value = AuthUiState(showNoAccountMessage = true)
            GoogleIdTokenResult.Cancelled ->
                _uiState.value = AuthUiState()
            GoogleIdTokenResult.Failed ->
                _uiState.value = AuthUiState(showError = true)
        }
    }

    private fun login(idToken: String) {
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            if (authRepository.loginWithGoogle(idToken)) {
                _uiState.value = AuthUiState()
                _effects.trySend(AuthEffect.SignedIn)
            } else {
                _uiState.value = AuthUiState(showError = true)
            }
        }
    }
}