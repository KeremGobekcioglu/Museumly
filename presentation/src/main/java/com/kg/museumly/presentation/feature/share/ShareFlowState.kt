package com.kg.museumly.presentation.feature.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Everything the share flow needs for one thing to share. The host knows nothing about
 * artworks: to share something new, build another [ShareContent].
 *
 * @param text plain-text version, for "Share as text".
 * @param card the card to render. The host composes it twice — once scaled as the sheet's
 * preview, once offscreen for the export — so it must be a pure function of its inputs.
 * It must call `onImageLoaded(true)` when everything it needs is drawn (or immediately if it
 * has nothing async), and `onImageLoaded(false)` if it failed. The host keeps the share options
 * disabled until it hears `true`, so an export can never capture a half-loaded card.
 */
class ShareContent(
    val text: String,
    val card: @Composable (onImageLoaded: (Boolean) -> Unit) -> Unit,
)

@Stable
class ShareFlowState internal constructor() {
    var content by mutableStateOf<ShareContent?>(null)
        private set

    fun open(content: ShareContent) {
        this.content = content
    }

    fun dismiss() {
        content = null
    }
}

@Composable
fun rememberShareFlowState(): ShareFlowState = remember { ShareFlowState() }
