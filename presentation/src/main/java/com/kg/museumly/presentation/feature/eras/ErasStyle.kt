package com.kg.museumly.presentation.feature.eras

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kg.museumly.presentation.ui.theme.LibreCaslon
import com.kg.museumly.presentation.ui.theme.White60

// The Eras screen's text styles and the sizes that keep its layout still.

/** The hero's number. It labels the work rather than leading it, so 56sp. */
internal val HeroNumberStyle: TextStyle = TextStyle(
    fontFamily = LibreCaslon,
    fontWeight = FontWeight.Normal,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    color = Color.White,
    textAlign = TextAlign.Center,
)

/** "BEFORE", "BC", "AD" beside the hero's number, sitting on its baseline. */
internal val HeroCapsStyle: SpanStyle = SpanStyle(
    fontSize = 13.sp,
    letterSpacing = 2.sp,
    color = White60,
)

/** The caption's title. Its line height is in em so it scales when autoSize shrinks it. */
internal val CaptionTitleStyle: TextStyle = TextStyle(
    fontFamily = LibreCaslon,
    fontWeight = FontWeight.Normal,
    fontSize = 19.sp,
    lineHeight = 1.26.em,
    color = Color.White,
    textAlign = TextAlign.Center,
)

/** "artist · year" under the title. Line height in em, like the title. */
internal val CaptionMetaStyle: TextStyle = TextStyle(
    fontSize = 13.sp,
    lineHeight = 1.38.em,
    color = White60,
    textAlign = TextAlign.Center,
)

internal val EmptyStyle: TextStyle = TextStyle(
    fontFamily = LibreCaslon,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    color = White60,
    textAlign = TextAlign.Center,
)

/** The museum line's height, in sp like the text on it. */
internal val CaptionMuseumLineHeight: TextUnit = 16.sp

/**
 * The caption's fixed height: three title lines, "artist · year" and the
 * museum, with the 4dp gaps between them. Fixed so a long title never
 * pushes the art up.
 *
 * Each line is converted from its own sp size: Android scales large font
 * sizes less than small ones, so converting the sum at once would make the
 * block too short at large font sizes.
 */
internal fun Density.captionHeight(): Dp {
    val titleLine: Dp = CaptionTitleStyle.fontSize.toDp() * CaptionTitleStyle.lineHeight.value
    val metaLine: Dp = CaptionMetaStyle.fontSize.toDp() * CaptionMetaStyle.lineHeight.value
    val museumLine: Dp = CaptionMuseumLineHeight.toDp()
    return titleLine * 3 + 4.dp + metaLine + 4.dp + museumLine
}

/**
 * The height of the fixed pill row under the caption: the pill's full 48dp
 * touch target. In dp, since a touch target doesn't scale with font size.
 * The row keeps it in empty eras too, so the art never jumps.
 */
internal val PillRowHeight: Dp = 48.dp

/**
 * How long the playhead must rest on an era before its card turns. The
 * scrubber's label answers the finger at once, so the card can wait for it
 * to settle and turn once per landing. Above 300 ms, so a slow drag (about
 * one era per 300 ms) waits for the pause instead of stacking turns. Only
 * while the ruler moves: once it stops, the card turns at once.
 */
internal const val ShownEraDwellMillis: Long = 200L

/** How far the works pager slides to show there is a next work. */
internal val NudgeDistance: Dp = 24.dp
