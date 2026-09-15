package com.kg.museumly.feature.scroll.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kg.museumly.R

// Pigments, not a tint. Ochre at the bottom, muted red-brown through the
// middle, warm cream at the wet edge — the palette of an underpainting.
private val PaintDeep = Color(0xFF6B4A22)
private val PaintMid = Color(0xFF8A4B3A)
private val PaintEdge = Color(0xFFD9C08A)

/**
 * The frame with paint arriving on the canvas: a warm wash rising from the
 * bottom with a soft wet edge, easing as it climbs, never reaching the top.
 *
 * It never completes on purpose. A reveal that fills to 100% and snaps back
 * reads as a progress bar that lied; one that slows, holds, and dissolves
 * reads as work still in progress — which is what's true.
 */


@Composable
fun PaintingFrame(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "paint-in")
// keyframes, not tween: the hold near the top and the fade-out are part of
// one cycle, so progress and alpha have to stay in step. Two separate
// animations would drift apart over long waits.
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                0f at 0 using LinearEasing
                0.55f at 1800            // steady rise
                0.72f at 3000            // slows as it climbs
                0.76f at 3600            // holds
                0.80f at 4200            // still holding while alpha fades
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                0f at 0
                1f at 700                // wash appears
                1f at 3600               // stays while it rises and holds
                0f at 4200               // dissolves, then the cycle restarts
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier.border(width = 1.dp, color = FrameLine.copy(alpha = 0.22f)),
    ) {
        Image(
            painter = painterResource(R.drawable.pearl_girl),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                // Offscreen is required: DstIn has to blend against this
                // layer's own pixels, not against whatever is already on
                // screen behind it.
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    this.alpha = alpha
                }
                .drawWithContent {
                    drawContent()
                    val bottom = size.height * progress
                    // Painted from the top down, so the wet edge is the
                    // bottom of the revealed region. Gradient runs opaque
                    // (keep) into transparent (erase).
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = bottom - size.height * 0.12f,
                            endY = bottom,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}