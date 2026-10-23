package com.kg.museumly.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.presentation.feature.auth.AuthScreen
import com.kg.museumly.presentation.feature.auth.AuthUiState

@Preview(name = "Auth · Idle", widthDp = 411, heightDp = 891)
@Composable
private fun AuthScreenIdlePreview() = AuthScreen(
    state = AuthUiState(),
    onGoogleLogin = {},
    onNotNow = {},
)

@Preview(name = "Auth · Loading", widthDp = 411, heightDp = 891)
@Composable
private fun AuthScreenLoadingPreview() = AuthScreen(
    state = AuthUiState(isLoading = true),
    onGoogleLogin = {},
    onNotNow = {},
)
@Preview(name = "Auth · No Google account", widthDp = 411, heightDp = 891)
@Composable
private fun AuthScreenNoAccountPreview() = AuthScreen(
    state = AuthUiState(showNoAccountMessage = true),
    onGoogleLogin = {},
    onNotNow = {},
)

@Preview(name = "Auth · Error", widthDp = 411, heightDp = 891)
@Composable
private fun AuthScreenErrorPreview() = AuthScreen(
    state = AuthUiState(showError = true),
    onGoogleLogin = {},
    onNotNow = {},
)

@Preview(name = "Auth · Short phone", widthDp = 360, heightDp = 560)
@Composable
private fun AuthScreenShortPreview() = AuthScreen(
    state = AuthUiState(showNoAccountMessage = true),
    onGoogleLogin = {},
    onNotNow = {},
)
