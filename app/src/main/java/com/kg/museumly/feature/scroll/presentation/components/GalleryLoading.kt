package com.kg.museumly.feature.scroll.presentation.components


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// pearl_girl.webp is 800x947 — the frame is shaped to its real ratio so
// ContentScale.Fit has nothing to letterbox; a generic fixed-box frame left
// visible gaps on the sides where the placeholder image didn't reach.
private const val PEARL_GIRL_ASPECT_RATIO: Float = 800f / 947f

/**
 * The loading counterpart to GalleryNotice. Same wall, same lamp — the frame
 * is shaped to the placeholder painting's own ratio via hangingGeometry
 * rather than a generic empty-frame box, and the placard carries no body or
 * action.
 */
@Composable
fun GalleryLoading(
    title: String = "Art is worth the wait.",
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WallColor),
    ) {
        val geometry = hangingGeometry(
            width = maxWidth,
            height = maxHeight,
            aspectRatio = PEARL_GIRL_ASPECT_RATIO,
        )

        PendantLamp(geometry = geometry, modifier = Modifier.fillMaxSize())

        PaintingFrame(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = geometry.frameTop)
                .size(width = geometry.frameWidth, height = geometry.frameHeight),
        )

        GalleryPlacard(
            title = title,
            body = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = geometry.placardTop),
        )
    }
}