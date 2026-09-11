package com.kg.museumly.feature.scroll.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.io.path.Path
import kotlin.io.path.moveTo
private val LampBody = Color(0xFF06060A)
@Composable
fun PendantLamp(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val stroke = 1.5.dp.toPx()

        // Dome is sized off width, not height — the canvas is now tall enough
        // for the cone to fall through, and a height-based dome would stretch.
        val domeHeight = w * 0.52f
        val domeTop = h * 0.10f
        val neckTop = domeTop - w * 0.14f
        val rimY = domeTop + domeHeight

        // 1. Cord, from the top edge down to the neck.
        drawLine(
            color = FrameLine.copy(alpha = 0.35f),
            start = Offset(cx, 0f),
            end = Offset(cx, neckTop),
            strokeWidth = stroke,
        )

        // 2. Cone FIRST, so the dome paints over its apex.
        val spread = w * 1.2f
        val reach = h - rimY
        val cone = Path().apply {
            moveTo(w * 0.08f, rimY)
            lineTo(w * 0.92f, rimY)
            lineTo(cx + spread, rimY + reach)
            lineTo(cx - spread, rimY + reach)
            close()
        }
        drawPath(
            path = cone,
            brush = Brush.verticalGradient(
                colors = listOf(FrameLine.copy(alpha = 0.10f), Color.Transparent),
                startY = rimY,
                endY = rimY + reach,
            ),
        )

        // 3. Neck.
        val neck = Path().apply {
            moveTo(cx - w * 0.05f, neckTop)
            lineTo(cx + w * 0.05f, neckTop)
            lineTo(cx + w * 0.09f, domeTop)
            lineTo(cx - w * 0.09f, domeTop)
            close()
        }
        drawPath(path = neck, color = LampBody)
        drawPath(path = neck, color = FrameLine.copy(alpha = 0.30f), style = Stroke(stroke))

        // 4. Dome.
        val dome = Path().apply {
            arcTo(
                rect = Rect(0f, domeTop, w, domeTop + domeHeight * 2f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = true,
            )
            close()
        }
        drawPath(path = dome, color = LampBody)
        drawPath(path = dome, color = FrameLine.copy(alpha = 0.30f), style = Stroke(stroke))

        // 5. Lit rim.
        drawLine(
            color = FrameLine.copy(alpha = 0.85f),
            start = Offset(0f, rimY),
            end = Offset(w, rimY),
            strokeWidth = stroke * 1.5f,
        )
    }
}