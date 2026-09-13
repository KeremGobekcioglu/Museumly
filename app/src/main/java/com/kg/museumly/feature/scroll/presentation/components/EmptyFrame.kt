package com.kg.museumly.feature.scroll.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val FrameLine = Color(0xFFE8E3D9)

/**
 * Hairline only, and deliberately no interior fill — the lamp's cone is drawn
 * underneath, and a filled interior would mask the light inside the frame,
 * which is the one place it needs to land.
 *
 * Size comes entirely from the caller, which holds the geometry.
 */
@Composable
fun EmptyFrame(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.border(width = 1.dp, color = FrameLine.copy(alpha = 0.22f)),
    )
}
