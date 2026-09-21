package com.kg.museumly.feature.scroll.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every position on a gallery screen, derived once from the viewport.
 *
 * The point is that the lamp and the frame share one coordinate system: the
 * light cone is aimed at the frame's top corners rather than guessed with a
 * spread/reach pair that had to be re-tuned by eye whenever the frame moved.
 * Nothing here measures against anything else on screen, so a two-line placard
 * and a three-line placard produce identical frames.
 *
 * lightEnd is where the cone's gradient reaches transparent. It defaults to
 * the frame's bottom edge (light dies on the work, the loading/error look);
 * the detail screen pushes it past the frame so the wall and placard below
 * the work are faintly lit.
 */
@Immutable
data class GalleryGeometry(
    val frameWidth: Dp,
    val frameHeight: Dp,
    val frameTop: Dp,
    val lampWidth: Dp,
    val rimY: Dp,
    val placardTop: Dp,
    val lightEnd: Dp = frameTop + frameHeight,
) {
    val frameBottom: Dp get() = frameTop + frameHeight
}

/**
 * Fixed empty frame for the loading and error screens.
 */
fun galleryGeometry(width: Dp, height: Dp): GalleryGeometry = GalleryGeometry(
    frameWidth = width * 0.62f,
    frameHeight = height * 0.48f,
    frameTop = height * 0.30f,
    lampWidth = width * 0.34f,
    rimY = height * 0.17f,
    placardTop = height * 0.81f,
)

private const val MAX_FRAME_WIDTH: Float = 0.80f
private const val MAX_FRAME_HEIGHT: Float = 0.50f
private const val CENTRE_LINE: Float = 0.52f
private const val RIM_Y: Float = 0.17f
private const val LAMP_MAX_WIDTH: Float = 0.34f
private const val LAMP_TO_FRAME: Float = 0.55f
private const val MIN_RATIO: Float = 0.25f
private const val MAX_RATIO: Float = 4f

/**
 * A frame shaped to a real artwork, hung on a fixed centre line.
 *
 * The frame is the largest rectangle of the artwork's ratio that fits inside
 * the max box — ContentScale.Fit, done once here so the frame, the image and
 * the lamp all agree on one rectangle.
 *
 * Clearance below the lamp is guaranteed by construction: frameHeight can't
 * exceed MAX_FRAME_HEIGHT, so frameTop can't rise above CENTRE_LINE minus half
 * of it (27%), and the rim sits at 17%.
 *
 * The lamp is capped at LAMP_TO_FRAME of the frame width. The cone's edges run
 * from the rim ends through the frame's top corners; if the lamp were wider
 * than the frame, the edges would converge and cross below it. Keeping the
 * lamp narrower keeps the slope positive, so the cone can't draw a bowtie.
 */
fun hangingGeometry(width: Dp, height: Dp, aspectRatio: Float): GalleryGeometry {
    var ratio: Float = aspectRatio
    if (ratio < MIN_RATIO) {
        ratio = MIN_RATIO
    }
    if (ratio > MAX_RATIO) {
        ratio = MAX_RATIO
    }

    val maxWidth: Dp = width * MAX_FRAME_WIDTH
    val maxHeight: Dp = height * MAX_FRAME_HEIGHT
    val boxRatio: Float = maxWidth / maxHeight

    val frameWidth: Dp
    val frameHeight: Dp
    if (ratio > boxRatio) {
        frameWidth = maxWidth
        frameHeight = maxWidth / ratio
    } else {
        frameHeight = maxHeight
        frameWidth = maxHeight * ratio
    }

    val frameTop: Dp = height * CENTRE_LINE - frameHeight / 2
    val frameBottom: Dp = frameTop + frameHeight

    var lampWidth: Dp = width * LAMP_MAX_WIDTH
    val lampCap: Dp = frameWidth * LAMP_TO_FRAME
    if (lampWidth > lampCap) {
        lampWidth = lampCap
    }

    return GalleryGeometry(
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        frameTop = frameTop,
        lampWidth = lampWidth,
        rimY = height * RIM_Y,
        placardTop = frameBottom + 24.dp,
        lightEnd = frameBottom + 64.dp,
    )
}