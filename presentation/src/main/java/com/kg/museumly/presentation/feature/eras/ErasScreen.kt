package com.kg.museumly.presentation.feature.eras

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.kg.museumly.presentation.BuildConfig
import com.kg.museumly.presentation.auth.AuthViewModel
import com.kg.museumly.presentation.auth.requestGoogleIdToken
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryLoading
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Which screen ErasScreen shows. Every mode but Timeline is the cold state:
 * no era has a single work yet, so there is nothing to scrub through and
 * the gallery screens take the whole page, as on the feed. Once one era has
 * works, the timeline stays, and an empty era shows EmptyEra in its card.
 */
private enum class ErasMode { Waiting, Loading, Failed, Empty, Timeline }

private fun ErasUiState.mode(): ErasMode {
    if (stops.isNotEmpty()) {
        return ErasMode.Timeline
    }
    // Room hasn't answered yet. Showing GalleryLoading here would flash it
    // on every visit with a cached timeline.
    if (!stopsReady) {
        return ErasMode.Waiting
    }
    // Checked before isLoading: a retry keeps the placard up, spinner on.
    if (errorMessage != null) {
        return ErasMode.Failed
    }
    if (isLoading) {
        return ErasMode.Loading
    }
    return ErasMode.Empty
}

private fun failedTitle(isOnline: Boolean): String {
    if (!isOnline) {
        return "No connection"
    }
    return "The timeline didn't open"
}

private fun failedBody(isOnline: Boolean): String {
    if (!isOnline) {
        return "We'll pick this back up once you're back online."
    }
    return "Something went wrong loading the collection."
}

/**
 * Crossfades between modes like the feed's ReelsContent. contentKey = mode()
 * means only a change of screen animates, and the screen fading out keeps
 * the state it last had.
 */
@Composable
fun ErasScreen(
    state: ErasUiState,
    onIntent: (ErasIntent) -> Unit
)
{
    // TEMP: debug Google sign-in trigger, remove once real sign-in UI exists.
    val context: Context = LocalContext.current
    val scope: CoroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state,
            contentKey = { it.mode() },
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "eras-mode",
        ) { slotState: ErasUiState ->
            when (slotState.mode()) {
                ErasMode.Waiting -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
                ErasMode.Loading -> GalleryLoading("Hanging the timeline")
                ErasMode.Failed -> GalleryNotice(
                    title = failedTitle(slotState.isOnline),
                    body = failedBody(slotState.isOnline),
                    actionLabel = if (slotState.isOnline) "Retry" else "Try anyway",
                    onAction = { onIntent(ErasIntent.RetryClicked) },
                    isBusy = slotState.isRetrying,
                    debugDetail = if (BuildConfig.DEBUG) slotState.errorMessage else null,
                )
                ErasMode.Empty -> GalleryNotice(
                    title = "Nothing on the walls",
                    body = "No works came back for the timeline.",
                    actionLabel = "Retry",
                    onAction = { onIntent(ErasIntent.RetryClicked) },
                )
                ErasMode.Timeline -> ErasTimeline(state = slotState, onIntent = onIntent)
            }
        }
    }
}
