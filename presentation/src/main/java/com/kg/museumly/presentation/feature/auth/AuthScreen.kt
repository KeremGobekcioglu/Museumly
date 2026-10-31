package com.kg.museumly.presentation.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.presentation.R
import com.kg.museumly.presentation.feature.auth.components.AuthActions
import com.kg.museumly.presentation.ui.theme.LibreCaslon

@Composable
fun AuthScreen(
    state: AuthUiState,
    onGoogleLogin: () -> Unit,
    onNotNow: () -> Unit,

)
{
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(top = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthArtwork(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
        )

        AuthTitle(modifier = Modifier.padding(top = 72.dp))

        AuthActions(
            isLoading = state.isLoading,
            onContinueWithGoogle = onGoogleLogin,
            onNotNow = onNotNow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = 32.dp)
        )
        AuthMessage(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(start = 32.dp, end = 32.dp, top = 8.dp)
        )
    }
}

@Composable
private fun AuthArtwork(
    modifier: Modifier = Modifier
)
{
    val fade : Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        fade.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = LinearOutSlowInEasing)
        )
    }

    Column(
        modifier = modifier.graphicsLayer {
            alpha = fade.value
        },
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        Image(
            painter = painterResource(R.drawable.harvesters),
            contentDescription = stringResource(R.string.auth_artwork_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
        )
        Text(
            text = stringResource(R.string.auth_artwork_caption),
            fontFamily = LibreCaslon,
            fontSize = 12.sp,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
            color = Color(0xFF9A958C),
            modifier = Modifier.padding(top = 12.dp, start = 28.dp, end = 28.dp)
        )
    }
}

@Composable
private fun AuthTitle(
    modifier: Modifier = Modifier
)
{
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.auth_title),
            fontFamily = LibreCaslon,
            fontSize = 40.sp,
            letterSpacing = 0.5.sp,
            color = Color(0xFFF4EFE6)
        )
        Text(
            text = stringResource(R.string.auth_tagline),
            fontFamily = LibreCaslon,
            fontSize = 14.sp,
            letterSpacing = 0.3.sp,
            color = Color(0xFF9A958C)
        )
    }
}

@Composable
private fun AuthMessage(
    state: AuthUiState,
    modifier: Modifier = Modifier
) {
    val messageRes: Int? = if (state.showNoAccountMessage) {
        R.string.auth_no_account
    } else if (state.showError) {
        R.string.auth_error
    } else {
        null
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (messageRes != null) {
            Text(
                text = stringResource(messageRes),
                fontFamily = LibreCaslon,
                fontSize = 15.sp,
                color = Color(0xFFF2B8A8),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                }
            )
        }
    }
}