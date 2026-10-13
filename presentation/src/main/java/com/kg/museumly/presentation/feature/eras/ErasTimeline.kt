package com.kg.museumly.presentation.feature.eras

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kg.museumly.domain.model.Era
import com.kg.museumly.presentation.feature.eras.components.EraGroup
import com.kg.museumly.presentation.feature.eras.components.EraSlider
import com.kg.museumly.presentation.feature.eras.components.captionHeight
import com.kg.museumly.presentation.feature.eras.components.eraTurn
import com.kg.museumly.presentation.feature.eras.components.groupByEra
import com.kg.museumly.presentation.feature.eras.components.rememberEraLabelAlpha
import com.kg.museumly.presentation.feature.eras.components.rememberShownPage
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Top to bottom: the era's card (art, era, caption, pill), the slider.
 *
 * The card is one piece: it turns as a whole when the finger lifts, or when
 * the playhead rests on an era for ShownEraDwellMillis (200 ms) while the
 * finger still holds the ruler, so the number never runs ahead of the art.
 * While the finger moves, the slider's label names the era under the
 * playhead. Each era opens on its first work.
 *
 * The ruler's PagerState is saved across rotation by rememberPagerState.
 */
@Composable
internal fun ErasTimeline(
    state: ErasUiState,
    onIntent: (ErasIntent) -> Unit,
)
{
    val groups: List<EraGroup> = remember(state.stops) { groupByEra(state.stops) }
    val lastIndex: Int = groups.size - 1
    val rulerState: PagerState = rememberPagerState(initialPage = 0) { groups.size }

    // One tick of haptic feedback per era passed.
    val haptic: HapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(rulerState) {
        var previous: Int = rulerState.currentPage
        snapshotFlow { rulerState.currentPage }.collect { page: Int ->
            if (page != previous) {
                previous = page
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }

    // Read here, in composition: a new shown page turns the card.
    val shownPageState: State<Int> = rememberShownPage(rulerState)
    val shownPage: Int = shownPageState.value
    val shownGroup: EraGroup = groups[shownPage.coerceIn(0, lastIndex)]

    // A card that outlives its era (still turning away, or kept by an
    // interrupted turn) asks this before it nudges.
    val latestShownEra: State<Era> = rememberUpdatedState(shownGroup.era)

    // Never read here: the slider's Canvas reads it in the draw phase.
    val labelAlphaState: State<Float> = rememberEraLabelAlpha(rulerState, shownPage)

    // Eras whose works pager already nudged. Written only from EraWorks'
    // effect, never during composition, so it needs no snapshot state.
    val nudgedEras: MutableSet<Era> = remember { HashSet() }

    // The caption scales with the user's font size.
    val captionBlockHeight: Dp = with(LocalDensity.current) {
        captionHeight()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        AnimatedContent(
            targetState = shownGroup,
            contentKey = { group: EraGroup -> group.era },
            transitionSpec = {
                val forward: Boolean = targetState.era.ordinal > initialState.era.ordinal
                eraTurn(forward)
            },
            label = "eraCard",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { group: EraGroup ->
            EraCard(
                group = group,
                isShownEra = { latestShownEra.value == group.era },
                isLoading = state.isLoading,
                // The error outlives a retry's start; while it runs the empty
                // era says "Arriving…" without the button.
                hasError = state.errorMessage != null && !state.isLoading,
                captionBlockHeight = captionBlockHeight,
                nudgedEras = nudgedEras,
                onIntent = onIntent
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        val scope: CoroutineScope = rememberCoroutineScope()
        EraSlider(
            groups = groups,
            rulerState = rulerState,
            labelAlpha = { labelAlphaState.value },
            onJump = { page: Int ->
                scope.launch {
                    val distance: Int = abs(page - rulerState.currentPage)
                    val duration: Int = (180 + distance * 40).coerceAtMost(650)
                    rulerState.animateScrollToPage(
                        page = page,
                        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                    )
                }
            }
        )
    }
}
