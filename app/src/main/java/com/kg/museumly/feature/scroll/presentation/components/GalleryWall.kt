package com.kg.museumly.feature.scroll.presentation.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WallColor = Color(0xFF0D0C0B)
val FrameLine = Color(0xFFE8E3D9)

/**
 * The gallery wall: warm near-black, with a soft picture-light falling from
 * above centre. Not Color.Black — a flat black reads as "screen off", a warm
 * dark with a light source reads as a room.
 */

//@Composable
//fun GalleryWall(
//    modifier: Modifier = Modifier,
//    // boxscope - box - has align.
//    content: @Composable BoxScope.() -> Unit
//)
//{
//    Box(
//        modifier = modifier
//            .fillMaxSize()
//            .background(WallColor)
//            .drawWithCache {
//                // Centre the light above the middle, where a real picture
//                // light would sit. Radius is deliberately larger than the
//                // width so the falloff is gentle rather than a visible disc.
//                val brush = Brush.radialGradient(
//                    colors = listOf(
//                        FrameLine.copy(0.12f),
//                        Color.Transparent
//                    ),
//                    center = Offset(x = size.width / 2f, y = size.height * 0.15f),
//                    radius = size.width * 1.1f
//                )
//                onDrawBehind { drawRect(brush) }
//            },
//        contentAlignment = Alignment.Center,
//        content = content
//    )
//}

@Composable
fun GalleryWall(
    modifier: Modifier = Modifier,
    // boxscope - box - has align.
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WallColor),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * An empty frame on the wall. Hairline only — a heavy gilt border would
 * compete with the artworks this same rectangle eventually holds.
 *
 * Sized as a fraction of the page rather than a fixed aspect ratio, so that
 * when the feed moves to a uniform frame this converges on the same rectangle
 * the artworks use and nothing has to be rewritten.
 */
@Composable
fun EmptyFrame(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.62f)
            .fillMaxHeight(0.42f)
            .border(width = 1.dp, color = FrameLine.copy(alpha = 0.22f)),
    )
}