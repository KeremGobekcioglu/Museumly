package com.kg.museumly.presentation.auth

data class AuthUiState(
    val isLoading: Boolean = false,
    val showNoAccountMessage: Boolean = false,
    val showError: Boolean = false,
)

sealed interface AuthEffect {
    data object SignedIn : AuthEffect
}