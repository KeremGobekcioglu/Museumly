package com.kg.museumly.presentation.feature.profile.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

class CouponStripShape(
    private val cornerRadius: Dp,
    private val notchRadius: Dp,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val corner: Float = with(density) { cornerRadius.toPx() }
        val notch: Float = with(density) { notchRadius.toPx() }
        val centerX: Float = size.width / 2f

        val body: Path = Path()
        body.addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                cornerRadius = CornerRadius(corner, corner),
            )
        )

        val notches: Path = Path()
        notches.addOval(Rect(center = Offset(centerX, 0f), radius = notch))
        notches.addOval(Rect(center = Offset(centerX, size.height), radius = notch))

        val strip: Path = Path()
        strip.op(body, notches, PathOperation.Difference)
        return Outline.Generic(strip)
    }
}