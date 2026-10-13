package com.kg.museumly.presentation.feature.eras

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * The ruler at one moment: the page under the playhead, where it will land,
 * and whether a finger holds it.
 */
private data class RulerMoment(val page: Int, val target: Int, val dragging: Boolean)

/**
 * The era the card shows, as a page of the ruler. Only the collector here
 * writes it.
 */
@Composable
internal fun rememberShownPage(rulerState: PagerState): State<Int> {
    // The era the card shows. While a finger holds the ruler, a page must
    // stay for ShownEraDwellMillis, and a new page cancels the wait, so eras
    // passed in a scrub never reach the card. Once the finger lifts, the
    // ruler already knows where it will land, so the card turns there at once.
    val isDragged: Boolean by rulerState.interactionSource.collectIsDraggedAsState()
    val shownPage: MutableIntState = remember { mutableIntStateOf(rulerState.currentPage) }
    LaunchedEffect(rulerState) {
        snapshotFlow { RulerMoment(rulerState.currentPage, rulerState.targetPage, isDragged) }
            .collectLatest { moment: RulerMoment ->
                if (moment.dragging) {
                    delay(ShownEraDwellMillis)
                    shownPage.intValue = moment.page
                    return@collectLatest
                }
                // Finger up (a release, a fling or a tap-jump): turn to where the
                // ruler will land, while the playhead is still travelling there.
                shownPage.intValue = moment.target
            }
    }
    return shownPage
}

/**
 * The scrubber's era label alpha. It returns a value, so it has no recompose
 * scope of its own: its reads of the ruler land in the caller's, as before.
 */
@Composable
internal fun rememberEraLabelAlpha(rulerState: PagerState, shownPage: Int): State<Float> {
    // The label shows while the finger moves and until the card has turned.
    var labelTarget: Float = 0f
    if (rulerState.isScrollInProgress || rulerState.currentPage != shownPage) {
        labelTarget = 1f
    }
    val labelAlpha: State<Float> = animateFloatAsState(
        targetValue = labelTarget,
        animationSpec = tween(250),
        label = "eraLabelAlpha"
    )
    return labelAlpha
}

/**
 * The card turns like a page of time: the old era drifts up and fades, the
 * new one rises a little from below and fades in just after, so the two
 * barely overlap and no frame goes black. Back in time runs the other way.
 */
internal fun eraTurn(forward: Boolean): ContentTransform {
    val sign: Int = if (forward) 1 else -1
    val enter: EnterTransition =
        slideInVertically(tween(320, delayMillis = 60, easing = FastOutSlowInEasing)) { height: Int ->
            sign * height / 16
        } + fadeIn(tween(240, delayMillis = 120))
    val exit: ExitTransition =
        slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { height: Int ->
            -sign * height / 16
        } + fadeOut(tween(160))
    return enter.togetherWith(exit)
}
