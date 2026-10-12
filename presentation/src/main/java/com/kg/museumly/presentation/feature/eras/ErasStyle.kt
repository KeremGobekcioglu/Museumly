package com.kg.museumly.presentation.feature.eras

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

internal val CaptionTitleStyle: TextStyle = TextStyle(
    fontFamily = LibreCaslon,
    fontWeight = FontWeight.Normal,
    fontSize = 19.sp,
    lineHeight = 24.sp,
    color = Color.White,
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

/**
 * The caption's fixed height: two title lines, "artist · year" and the
 * section. Fixed so a long title never pushes the art up.
 */
internal val CaptionHeight: Dp = 92.dp
/** The pill's row keeps its height in empty eras too, so the art never jumps. */
internal val PillRowHeight: Dp = 32.dp

/** How far the works pager slides to show there is a next work. */
internal val NudgeDistance: Dp = 24.dp
