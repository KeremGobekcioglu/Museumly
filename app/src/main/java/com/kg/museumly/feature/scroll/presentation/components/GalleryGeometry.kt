package com.kg.museumly.feature.scroll.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * Every position on the notice screen, derived once from the viewport.
 *
 * The point is that the lamp and the frame share one coordinate system: the
 * light cone is aimed at the frame's top corners rather than guessed with a
 * spread/reach pair that had to be re-tuned by eye whenever the frame moved.
 * Nothing here measures against anything else on screen, so a two-line placard
 * and a three-line placard produce identical frames.
 */
@Immutable
data class GalleryGeometry(
    val frameWidth: Dp,
    val frameHeight: Dp,
    val frameTop: Dp,
    val lampWidth: Dp,
    val rimY: Dp,
    val placardTop: Dp,
) {
    val frameBottom: Dp get() = frameTop + frameHeight
}

fun galleryGeometry(width: Dp, height: Dp): GalleryGeometry = GalleryGeometry(
    frameWidth = width * 0.62f,
    frameHeight = height * 0.48f,
    frameTop = height * 0.30f,
    lampWidth = width * 0.34f,
    rimY = height * 0.17f,
    placardTop = height * 0.81f,
)
