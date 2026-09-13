package com.kg.museumly.feature.scroll.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val LampBody = Color(0xFF06060A)

/**
 * Fills the whole screen. The dome sits near the top and the cone falls through
 * the rest, so this can't be a small canvas pinned to TopCenter — the cone has
 * to travel past the frame.
 */
@Composable
fun PendantLamp(
    geometry: GalleryGeometry,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val stroke = 1.5.dp.toPx()

        val lampWidth = geometry.lampWidth.toPx()
        val lampHalf = lampWidth / 2f
        val rimY = geometry.rimY.toPx()
        val frameTop = geometry.frameTop.toPx()
        val frameHalf = geometry.frameWidth.toPx() / 2f

        val domeHeight = lampWidth * 0.52f
        val domeTop = rimY - domeHeight
        val neckTop = domeTop - lampWidth * 0.14f

        // Cord runs off the top edge; a visible upper end reads as floating.
        drawLine(
            color = FrameLine.copy(alpha = 0.35f),
            start = Offset(cx, 0f),
            end = Offset(cx, neckTop),
            strokeWidth = stroke,
        )

        // Cone first, so the dome paints over its apex.
        //
        // Each edge is the line through the rim end and the frame's top corner,
        // extended to the bottom of the canvas. That guarantees the light is
        // exactly frame-width when it reaches the frame's top edge — the thing
        // the spread/reach version could only approximate.
        val slope = (frameHalf - lampHalf) / (frameTop - rimY)
        val endY = size.height
        val endHalf = lampHalf + slope * (endY - rimY)

        val cone = Path().apply {
            moveTo(cx - lampHalf, rimY)
            lineTo(cx + lampHalf, rimY)
            lineTo(cx + endHalf, endY)
            lineTo(cx - endHalf, endY)
            close()
        }
        drawPath(
            path = cone,
            brush = Brush.verticalGradient(
                // Three stops, not two: a linear fade reads flat, this
                // approximates real falloff. Fully transparent by the frame's
                // bottom, so the light dies on the work rather than the floor.
                colors = listOf(
                    FrameLine.copy(alpha = 0.11f),
                    FrameLine.copy(alpha = 0.04f),
                    Color.Transparent,
                ),
                startY = rimY,
                endY = geometry.frameBottom.toPx(),
            ),
        )

        val neck = Path().apply {
            moveTo(cx - lampWidth * 0.05f, neckTop)
            lineTo(cx + lampWidth * 0.05f, neckTop)
            lineTo(cx + lampWidth * 0.09f, domeTop)
            lineTo(cx - lampWidth * 0.09f, domeTop)
            close()
        }
        drawPath(path = neck, color = LampBody)
        drawPath(path = neck, color = FrameLine.copy(alpha = 0.30f), style = Stroke(stroke))

        // Half-ellipse: arcTo takes the full ellipse bounds, so the rect is
        // twice the dome height and the 180° sweep draws only the top half.
        val dome = Path().apply {
            arcTo(
                rect = Rect(
                    left = cx - lampHalf,
                    top = domeTop,
                    right = cx + lampHalf,
                    bottom = domeTop + domeHeight * 2f,
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = true,
            )
            close()
        }
        drawPath(path = dome, color = LampBody)
        drawPath(path = dome, color = FrameLine.copy(alpha = 0.30f), style = Stroke(stroke))

        // The lit rim. This is what makes the fixture read as switched on.
        drawLine(
            color = FrameLine.copy(alpha = 0.85f),
            start = Offset(cx - lampHalf, rimY),
            end = Offset(cx + lampHalf, rimY),
            strokeWidth = stroke * 1.5f,
        )
    }
}
