package com.kg.museumly.presentation.feature.profile.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.NumberFormat

// ---- Shape ----

/**
 * A rounded rectangle with a half-circle bitten out of each side.
 * The notches are measured from the BOTTOM, because the stub has a fixed
 * height while the top part grows with its content.
 */
class TicketShape(
    private val cornerRadius: Dp,
    private val notchRadius: Dp,
    private val notchCenterFromBottom: Dp,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val corner: Float = with(density) { cornerRadius.toPx() }
        val notch: Float = with(density) { notchRadius.toPx() }
        val notchY: Float = size.height - with(density) { notchCenterFromBottom.toPx() }

        val body: Path = Path()
        body.addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                cornerRadius = CornerRadius(corner, corner),
            )
        )

        val notches: Path = Path()
        notches.addOval(Rect(center = Offset(0f, notchY), radius = notch))
        notches.addOval(Rect(center = Offset(size.width, notchY), radius = notch))

        val ticket: Path = Path()
        ticket.op(body, notches, PathOperation.Difference)
        return Outline.Generic(ticket)
    }
}

@Composable
internal fun DashedLines()
{
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(PerforationHeight)
    ) {
        val inset: Float = 20.dp.toPx()
        val y: Float = size.height / 2f
        val dash: Float = 6.dp.toPx()
        val gap: Float = 5.dp.toPx()
        val lineCount = 2
        val spacing: Float = 10.dp.toPx()
        val totalHeight: Float = spacing * (lineCount - 1)
        val startY: Float = (size.height - totalHeight) / 2f

        repeat(lineCount) {
                i -> val y = startY + i *spacing
            drawLine(
                color = TicketInk.copy(alpha = 0.26f),
                start = Offset(inset,y),
                end = Offset(size.width - inset, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash,gap))
            )
        }

    }
}