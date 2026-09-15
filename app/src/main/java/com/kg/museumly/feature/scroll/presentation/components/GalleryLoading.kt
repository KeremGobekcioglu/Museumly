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

/**
 * The loading counterpart to GalleryNotice. Same wall, same lamp, same frame
 * at the same geometry — the only difference is that the canvas inside the
 * frame is being painted, and the placard carries no body or action.
 *
 * Keeping the geometry shared means the frame doesn't move between loading
 * and error: the work is either arriving or it isn't, and the wall is the
 * same wall either way.
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
        val geometry = galleryGeometry(width = maxWidth, height = maxHeight)

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