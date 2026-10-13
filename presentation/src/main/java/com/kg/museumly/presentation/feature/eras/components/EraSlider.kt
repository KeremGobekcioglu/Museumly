package com.kg.museumly.presentation.feature.eras.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.domain.model.Era
import com.kg.museumly.presentation.ui.theme.White60
import kotlin.math.roundToInt

private val SliderHeight: Dp = 64.dp
/** The track's inset from both edges. */
private val TrackInset: Dp = 24.dp
private val TrackY: Dp = 22.dp
private val TrackStroke: Dp = 1.5.dp
private val SegmentGap: Dp = 2.dp
private val PlayheadWidth: Dp = 2.dp
private val PlayheadHeight: Dp = 20.dp
private val PlayheadHalo: Dp = 18.dp
private val AnchorLabelTop: Dp = 42.dp

private val AnchorLabelStyle: TextStyle = TextStyle(
    fontSize = 10.sp,
    letterSpacing = 0.5.sp,
    color = White60,
)

/** The label above the playhead while the finger moves. */
private val EraLabelGap: Dp = 4.dp
private val EraLabelStyle: TextStyle = TextStyle(
    fontSize = 11.sp,
    letterSpacing = 0.5.sp,
    color = Color.White,
)

/**
 * The slider. The ruler's HorizontalPager is the invisible drag engine:
 * one empty page per era, exactly one track step wide, so the playhead
 * stays under the thumb 1:1. Fling, snap, settledPage and haptics work as
 * before. reverseLayout makes dragging right move forward in time.
 *
 * A Canvas on top draws what the user sees, and takes no input:
 *  - a hairline from 24dp to width - 24dp, one segment per era, brighter
 *    the more works the era holds, so it lights up as eras arrive;
 *  - the playhead, a white slit with a soft halo, read in the draw phase;
 *  - four anchor labels.
 * A tap anywhere on the slider jumps to the nearest era.
 */
@Composable
internal fun EraSlider(
    groups: List<EraGroup>,
    rulerState: PagerState,
    labelAlpha: () -> Float,
    onJump: (Int) -> Unit,
)
{
    val lastIndex: Int = groups.size - 1

    // Animated per era, so a segment fades up when its works arrive.
    // groups.size is fixed, so the loop always calls the same states.
    val segmentAlphas: MutableList<State<Float>> = ArrayList()
    for (group in groups) {
        segmentAlphas.add(
            animateFloatAsState(
                targetValue = segmentAlpha(group.stops.size),
                animationSpec = tween(400),
                label = "segmentAlpha"
            )
        )
    }

    val textMeasurer: TextMeasurer = rememberTextMeasurer()
    val anchors: List<Pair<Int, TextLayoutResult>> = remember(groups.size, textMeasurer) {
        val result: MutableList<Pair<Int, TextLayoutResult>> = ArrayList()
        for ((era, text) in SliderAnchors) {
            val index: Int = groups.indexOfFirst { group: EraGroup -> group.era == era }
            if (index >= 0) {
                result.add(Pair(index, textMeasurer.measure(text, AnchorLabelStyle)))
            }
        }
        result
    }

    // One label per era, measured once: the hero's words, e.g. "AD 500".
    val eraLabels: List<TextLayoutResult> = remember(groups.size, textMeasurer) {
        val result: MutableList<TextLayoutResult> = ArrayList()
        for (group in groups) {
            val words: String = heroDescription(heroParts(group.era))
            result.add(textMeasurer.measure(words, EraLabelStyle))
        }
        result
    }

    val currentOnJump: (Int) -> Unit by rememberUpdatedState(onJump)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .pointerInput(lastIndex) {
                detectTapGestures { tap: Offset ->
                    val start: Float = TrackInset.toPx()
                    val length: Float = size.width - 2 * start
                    if (length > 0f && lastIndex > 0) {
                        val fraction: Float = ((tap.x - start) / length).coerceIn(0f, 1f)
                        currentOnJump((fraction * lastIndex).roundToInt())
                    }
                }
            }
    ) {
        // One page per track step, so the drag moves the playhead 1:1. A lone
        // era has no step; its page spans the track.
        var step: Dp = maxWidth - TrackInset * 2
        if (lastIndex > 0) {
            step = step / lastIndex
        }
        val sidePadding: Dp = (maxWidth - step) / 2
        HorizontalPager(
            state = rulerState,
            pageSize = PageSize.Fixed(step),
            contentPadding = PaddingValues(horizontal = sidePadding),
            reverseLayout = true,
            flingBehavior = PagerDefaults.flingBehavior(
                state = rulerState,
                pagerSnapDistance = PagerSnapDistance.atMost(groups.size)
            ),
            modifier = Modifier.fillMaxSize()
        ) { _: Int ->
            // Draws nothing; the Canvas below is the slider.
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val start: Float = TrackInset.toPx()
            val end: Float = size.width - start
            val length: Float = end - start
            val y: Float = TrackY.toPx()
            if (length <= 0f || lastIndex <= 0) {
                return@Canvas
            }
            val step: Float = length / lastIndex
            val gap: Float = SegmentGap.toPx() / 2

            // One segment per era, centered on the era's point on the track.
            for (index in 0..lastIndex) {
                val center: Float = start + index * step
                val left: Float = (center - step / 2).coerceAtLeast(start) + (if (index > 0) gap else 0f)
                val right: Float = (center + step / 2).coerceAtMost(end) - (if (index < lastIndex) gap else 0f)
                drawLine(
                    color = Color.White.copy(alpha = segmentAlphas[index].value),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = TrackStroke.toPx()
                )
            }

            for ((index, layout) in anchors) {
                val x: Float = start + index * step - layout.size.width / 2f
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x.coerceIn(0f, size.width - layout.size.width),
                        AnchorLabelTop.toPx()
                    )
                )
            }

            // Read here, in the draw phase, so dragging redraws the playhead
            // without recomposing the screen.
            val position: Float = (rulerState.currentPage + rulerState.currentPageOffsetFraction)
                .coerceIn(0f, lastIndex.toFloat())
            val x: Float = start + position / lastIndex * length
            val halo: Float = PlayheadHalo.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(x, y),
                    radius = halo
                ),
                radius = halo,
                center = Offset(x, y)
            )
            val slitWidth: Float = PlayheadWidth.toPx()
            val slitHeight: Float = PlayheadHeight.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x - slitWidth / 2, y - slitHeight / 2),
                size = Size(slitWidth, slitHeight),
                cornerRadius = CornerRadius(slitWidth / 2)
            )

            // The era under the playhead, riding above it. Read here, in the
            // draw phase, like the playhead. Drawn above the slider's top
            // edge, into the spacer over it.
            val alpha: Float = labelAlpha()
            if (alpha > 0f) {
                val page: Int = rulerState.currentPage.coerceIn(0, lastIndex)
                val label: TextLayoutResult = eraLabels[page]
                val labelX: Float = (x - label.size.width / 2f)
                    .coerceIn(0f, size.width - label.size.width)
                val labelY: Float = y - slitHeight / 2 - EraLabelGap.toPx() - label.size.height
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(labelX, labelY),
                    alpha = alpha
                )
            }
        }
    }
}

/** The slider's four labels: where the timeline's big turns are. */
private val SliderAnchors: List<Pair<Era, String>> = listOf(
    Pair(Era.BC_3000, "3000 BC"),
    Pair(Era.AD_0, "AD 1"),
    Pair(Era.Y1400, "1400s"),
    Pair(Era.Y1900, "1900s"),
)

/** A segment's brightness from its era's work count: 0 → 0.12, 1 → 0.35, 2 → 0.6, 3+ → 0.85. */
private fun segmentAlpha(count: Int): Float {
    return when {
        count <= 0 -> 0.12f
        count == 1 -> 0.35f
        count == 2 -> 0.6f
        else -> 0.85f
    }
}
