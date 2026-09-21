package com.kg.museumly.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import com.kg.museumly.feature.scroll.presentation.components.FrameLine

/**
 * Light falling off down the face of the work. The top 40% is untouched —
 * the lamp hits it directly — then it darkens toward the bottom edge.
 * Without this a bright painting on a black wall reads as a backlit screen.
 */
internal val LightFalloff: Brush = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.4f to Color.Transparent,
    1.0f to Color.Black.copy(alpha = 0.22f),
)

/**
 * A real artwork hung under the lamp: the image, the light falling off
 * across it, and a frame edge that knows which way the light comes from.
 *
 * The screen owns the aspect-ratio logic; this only reports what it learns.
 */
@Composable
fun ArtworkFrame(
    imageUrl: String,
    contentDescription: String?,
    onImageLoaded: (width: Int, height: Int, cacheKey: MemoryCache.Key?) -> Unit,
    onImageFailed: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    val pressed: Boolean by interactionSource.collectIsPressedAsState()
    // 1.5% is deliberately tiny — a finger should feel this, not see an
    // animation.
    val lift: Float by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "lift",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                val scale: Float = 1f + 0.015f * lift
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                // A ripple washing over a painting looks like a button, not
                // an object.
                indication = null,
                onClick = onClick,
            )
            .drawWithContent {
                drawContent()
                drawRect(brush = LightFalloff)
                drawFrameEdges(lift = lift)
            },
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val width: Int = state.result.image.width
                val height: Int = state.result.image.height
                if (width > 0 && height > 0) {
                    onImageLoaded(width, height, state.result.memoryCacheKey)
                }
            },
            onError = { _ ->
                onImageFailed()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun DrawScope.drawFrameEdges(lift: Float) {
    val stroke: Float = 1.dp.toPx()
    val half: Float = stroke / 2f
    val width: Float = size.width
    val height: Float = size.height

    // Top edge faces the lamp — brightest line on the frame, catching more
    // light as the work rises toward it under a press.
    drawLine(
        color = FrameLine.copy(alpha = 0.55f + 0.30f * lift),
        start = Offset(0f, half),
        end = Offset(width, half),
        strokeWidth = stroke,
    )

    // Sides catch light at the top and lose it toward the bottom.
    val sideBrush: Brush = Brush.verticalGradient(
        colors = listOf(
            FrameLine.copy(alpha = 0.35f),
            FrameLine.copy(alpha = 0.08f),
        ),
    )
    drawLine(
        brush = sideBrush,
        start = Offset(half, 0f),
        end = Offset(half, height),
        strokeWidth = stroke,
    )
    drawLine(
        brush = sideBrush,
        start = Offset(width - half, 0f),
        end = Offset(width - half, height),
        strokeWidth = stroke,
    )

    // Bottom edge faces away from the light — barely there.
    drawLine(
        color = FrameLine.copy(alpha = 0.06f),
        start = Offset(0f, height - half),
        end = Offset(width, height - half),
        strokeWidth = stroke,
    )
}
