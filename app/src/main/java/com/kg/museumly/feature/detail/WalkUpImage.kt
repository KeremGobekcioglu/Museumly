package com.kg.museumly.feature.detail

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import androidx.compose.ui.util.lerp

/**
 * The painting in transit between its frame and full screen.
 *
 * Laid out once at its final (fit) size and moved with graphicsLayer, so
 * nothing re-measures per frame and Coil only resolves one request size.
 * Starts shrunk and offset to sit exactly over the frame, ends at identity
 * — exactly where the zoomable image sits at fit, so the handover is seamless.
 */

@Composable
internal fun WalkUpImage(
    imageUrl: String,
    placeholderKey: MemoryCache.Key?,
    progress: Float,
    startScale: Float,
    startOffsetY: Dp,
    fitWidth: Dp,
    fitHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request : ImageRequest = remember(
        imageUrl, placeholderKey
    ) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .placeholderMemoryCacheKey(placeholderKey)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(fitWidth, fitHeight)
            .graphicsLayer
            {
                val scale: Float = lerp(startScale , 1f, progress)
                scaleX = scale
                scaleY = scale
                translationY = lerp(startOffsetY.toPx() , 0f, progress)
            }
            .drawWithContent {
                drawContent()
                // The lamp's falloff leaves as the work leaves the lamp.
                drawRect(brush = LightFalloff , alpha = 1f - progress)
            }
    )
}