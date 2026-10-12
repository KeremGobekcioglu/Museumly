package com.kg.museumly.presentation.feature.eras

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.TimelineStop
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import com.kg.museumly.presentation.feature.scroll.presentation.components.TopBarChipOutline
import com.kg.museumly.presentation.feature.scroll.presentation.components.label
import com.kg.museumly.presentation.ui.theme.White40
import com.kg.museumly.presentation.ui.theme.White60
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Top to bottom: the art, the era, the caption, the pill, the scrubber.
 *
 * Two indices into the era groups, both from the ruler:
 *  - dragging is the ruler's currentPage. It follows the finger, and only
 *    the hero and the scrubber read it, so sliding stays cheap.
 *  - settled is the ruler's settledPage. The works, caption and pill read
 *    it, so the art changes only once the ruler stops.
 *
 * The ruler's PagerState is saved across rotation by rememberPagerState.
 */
@Composable
fun ErasScreen(
    state: ErasUiState,
    onIntent: (ErasIntent) -> Unit,
)
{
    val groups: List<EraGroup> = remember(state.stops) { groupByEra(state.stops) }
    val lastIndex: Int = groups.size - 1
    val rulerState: PagerState = rememberPagerState(initialPage = 0) { groups.size }

    var settled: Int by remember { mutableIntStateOf(rulerState.settledPage) }
    LaunchedEffect(rulerState) {
        snapshotFlow { rulerState.settledPage }.collect { page: Int ->
            settled = page
        }
    }

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

    val draggingGroup: EraGroup = groups[rulerState.currentPage.coerceIn(0, lastIndex)]
    val settledGroup: EraGroup = groups[settled.coerceIn(0, lastIndex)]

    // The global index (into state.stops) of the work on screen. The slot
    // inside AnimatedContent reports it; the caption and the pill read it.
    var visibleIndex: Int by remember { mutableIntStateOf(-1) }

    // Eras whose works pager already nudged. Written only from the slot's
    // effect, never during composition, so it needs no snapshot state.
    val nudgedEras: MutableSet<Era> = remember { HashSet() }

    // Fall back to the era's first work until the slot reports one of this
    // era's own works. -1 when the era has none.
    var moreIndex: Int = settledGroup.firstIndex
    val eraEnd: Int = settledGroup.firstIndex + settledGroup.stops.size
    if (visibleIndex >= settledGroup.firstIndex && visibleIndex < eraEnd) {
        moreIndex = visibleIndex
    }
    val visibleStop: TimelineStop? = state.stops.getOrNull(moreIndex)

    // While scrubbing only the art and the era stay on screen.
    val detailsAlpha: Float by animateFloatAsState(
        targetValue = if (rulerState.isScrollInProgress) 0f else 1f,
        animationSpec = tween(120),
        label = "detailsAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        AnimatedContent(
            targetState = settledGroup,
            contentKey = { group: EraGroup -> group.era },
            transitionSpec = {
                // The work rises from the line it stands on, just above the era.
                (fadeIn(tween(300)) + scaleIn(
                    animationSpec = tween(300),
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                )).togetherWith(fadeOut(tween(200)))
            },
            label = "eraWorks",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { group: EraGroup ->
            EraSlot(
                group = group,
                isLoading = state.isLoading,
                hasError = state.errorMessage != null,
                nudgedEras = nudgedEras,
                onIntent = onIntent,
                onVisibleIndex = { index: Int -> visibleIndex = index }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        EraHero(
            era = draggingGroup.era,
            hasWorks = draggingGroup.stops.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReelsGutter)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = detailsAlpha }
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedContent(
                targetState = visibleStop,
                contentKey = { stop: TimelineStop? -> stop?.artwork?.id },
                transitionSpec = {
                    fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
                },
                label = "eraCaption",
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CaptionHeight)
                    .padding(horizontal = ReelsGutter)
            ) { stop: TimelineStop? ->
                if (stop != null) {
                    EraCaption(stop = stop)
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PillRowHeight)
            ) {
                if (moreIndex >= 0) {
                    OutlinedPill(
                        label = "More from " + eraSpan(settledGroup.era) + " →",
                        onClick = { onIntent(ErasIntent.MoreClicked(moreIndex)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        val scope: CoroutineScope = rememberCoroutineScope()
        EraScrubber(
            groups = groups,
            rulerState = rulerState,
            onJump = { page: Int ->
                scope.launch {
                    rulerState.animateScrollToPage(page)
                }
            }
        )
    }
}

/**
 * One era's works and their dots, the part that rises in when the ruler
 * stops. Reads only its own group, never the screen's state: while the old
 * era fades out, its slot must keep showing the old era.
 */
@Composable
private fun EraSlot(
    group: EraGroup,
    isLoading: Boolean,
    hasError: Boolean,
    nudgedEras: MutableSet<Era>,
    onIntent: (ErasIntent) -> Unit,
    onVisibleIndex: (Int) -> Unit,
)
{
    if (group.stops.isEmpty()) {
        EmptyEra(isLoading = isLoading, hasError = hasError, onIntent = onIntent)
        return
    }

    // Works can still arrive while the era is on screen, so the pager reads
    // the latest count. Each era gets its own slot, so its own PagerState,
    // and always opens on its first work.
    val pageCount: Int by rememberUpdatedState(group.stops.size)
    val worksState: PagerState = rememberPagerState(initialPage = 0) { pageCount }

    // Runs when the era settles (page 0), when its first work arrives, and
    // whenever the works pager settles on another page. Never mid-swipe.
    LaunchedEffect(worksState, group.firstIndex) {
        snapshotFlow { worksState.settledPage }.collect { page: Int ->
            val index: Int = group.firstIndex + page
            onVisibleIndex(index)
            onIntent(ErasIntent.StopChanged(index))
        }
    }

    // Once per era: slide toward the next work and spring back, so the user
    // sees there is more to swipe. Waits for the rise-in to finish, and skips
    // it if the user is already swiping.
    val hasMore: Boolean = group.stops.size > 1
    val nudgePx: Float = with(LocalDensity.current) { NudgeDistance.toPx() }
    LaunchedEffect(group.era, hasMore) {
        if (!hasMore || group.era in nudgedEras) {
            return@LaunchedEffect
        }
        delay(650)
        if (worksState.isScrollInProgress || worksState.currentPage != 0) {
            return@LaunchedEffect
        }
        nudgedEras.add(group.era)
        worksState.animateScrollBy(nudgePx, tween(240, easing = FastOutSlowInEasing))
        worksState.animateScrollBy(
            -nudgePx,
            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        HorizontalPager(
            state = worksState,
            pageSpacing = 16.dp,
            key = { page: Int -> group.stops[page].artwork.id },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page: Int ->
            val artwork: Artwork = group.stops[page].artwork
            EraArtwork(
                artwork = artwork,
                onClick = { onIntent(ErasIntent.ArtworkClicked(artwork.id)) }
            )
        }
        // The dots' row keeps its height with one work too, so the art
        // stands on the same line in every era.
        Spacer(modifier = Modifier.height(12.dp))
        WorkDots(count = group.stops.size, worksState = worksState)
    }
}

/**
 * One dot per work, only when there is more than one. The active dot is
 * white, the others 30%; the alpha follows the swipe in the draw phase.
 */
@Composable
private fun WorkDots(
    count: Int,
    worksState: PagerState,
)
{
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(6.dp)
    ) {
        if (count > 1) {
            for (index in 0 until count) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer {
                            val position: Float = worksState.currentPage + worksState.currentPageOffsetFraction
                            val closeness: Float = 1f - abs(index - position).coerceAtMost(1f)
                            alpha = 0.3f + 0.7f * closeness
                        }
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun EmptyEra(
    isLoading: Boolean,
    hasError: Boolean,
    onIntent: (ErasIntent) -> Unit,
)
{
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ReelsGutter),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val message: String
            if (isLoading) {
                message = "Arriving…"
            } else {
                message = "Nothing from this era yet"
            }
            Text(text = message, style = EmptyStyle)
            if (hasError) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedPill(
                    label = "Try again",
                    onClick = { onIntent(ErasIntent.RetryClicked) }
                )
            }
        }
    }
}

/**
 * Bare image on black, no frame and no favorite button. Fitted to the
 * bottom center, so portraits, landscapes and squares all stand on the same
 * line just above the era.
 */
@Composable
private fun EraArtwork(
    artwork: Artwork,
    onClick: () -> Unit,
)
{
    AsyncImage(
        model = artwork.imageUrl,
        contentDescription = artwork.title,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
    )
}

/**
 * Title in Libre Caslon, then "artist · year", then the section in small
 * spaced caps, all centered. The title gets two lines and the rest one;
 * past that they are ellipsized, so the caption's height never changes.
 */
@Composable
private fun EraCaption(
    stop: TimelineStop,
    modifier: Modifier = Modifier,
)
{
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stop.artwork.title,
            style = CaptionTitleStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val meta: String? = artistAndYear(stop.artwork)
        if (meta != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = meta,
                color = White60,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stop.section.label.uppercase(),
            color = White40,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The hero: the era under the scrubber's playhead. Follows the ruler live.
 * Going forward in time it slides up and fades; going back it slides down.
 * Dims to 40% while the era under the playhead has no works.
 */
@Composable
private fun EraHero(
    era: Era,
    hasWorks: Boolean,
    modifier: Modifier = Modifier,
)
{
    val alpha: Float by animateFloatAsState(
        targetValue = if (hasWorks) 1f else 0.4f,
        animationSpec = tween(200),
        label = "heroAlpha"
    )
    AnimatedContent(
        targetState = era,
        transitionSpec = {
            val forward: Boolean = targetState.ordinal > initialState.ordinal
            heroTransform(forward)
        },
        contentAlignment = Alignment.Center,
        label = "eraHero",
        modifier = modifier.graphicsLayer { this.alpha = alpha }
    ) { shown: Era ->
        val parts: HeroParts = heroParts(shown)
        Text(
            text = heroText(parts),
            style = HeroNumberStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = heroDescription(parts) }
        )
    }
}

private fun heroTransform(forward: Boolean): ContentTransform {
    val sign: Int = if (forward) 1 else -1
    val enter = slideInVertically(tween(180)) { height: Int -> sign * height / 3 } + fadeIn(tween(180))
    val exit = slideOutVertically(tween(150)) { height: Int -> -sign * height / 3 } + fadeOut(tween(120))
    return enter.togetherWith(exit)
}


/** The app's outlined pill: transparent, 1dp white at 30%, white label. */
@Composable
private fun OutlinedPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
{
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        border = BorderStroke(1.dp, TopBarChipOutline),
        contentColor = Color.White,
        shape = CircleShape
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
