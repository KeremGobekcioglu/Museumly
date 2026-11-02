package com.kg.museumly.presentation.feature.share.capture

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Lays [content] out at its own intrinsic size (ignoring the parent's constraints) and then
 * visually scales the result down to [previewHeight] with a graphicsLayer transform.
 *
 * This is a *scale*, not a *shrink*: the card's layout never sees tighter constraints than it
 * asks for, so its fixed dp/sp children can't clip. fontScale is pinned to 1f, as in the
 * export, so the preview matches what gets shared even with a large system text size.
 */
@Composable
fun ScaledShareCard(
    modifier: Modifier = Modifier,
    previewHeight: Dp = 480.dp,
    content: @Composable () -> Unit,
) {
    val scale = previewHeight / ShareCardHeight
    val density = LocalDensity.current
    val pinned = remember(density.density) { Density(density.density, fontScale = 1f) }

    CompositionLocalProvider(LocalDensity provides pinned) {
        Layout(modifier = modifier.clip(RoundedCornerShape(12.dp)), content = content) { measurables, _ ->
            val placeable = measurables.first().measure(Constraints())
            val scaledW = (placeable.width * scale).roundToInt()
            val scaledH = (placeable.height * scale).roundToInt()

            // Report the SCALED size so the parent centers a real ~270x480 element.
            layout(scaledW, scaledH) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}
