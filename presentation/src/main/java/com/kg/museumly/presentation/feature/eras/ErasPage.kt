package com.kg.museumly.presentation.feature.eras

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.TimelineStop
import com.kg.museumly.presentation.feature.detail.ArtworkFrame
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import com.kg.museumly.presentation.feature.scroll.presentation.components.TopBarChipOutline
import com.kg.museumly.presentation.feature.scroll.presentation.components.museumName
import com.kg.museumly.presentation.ui.theme.White40
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Top to bottom: the art, the era, the caption, the pill, the scrubber.
 *
 * The hero follows the finger: it reads the ruler's currentPage and rolls
 * through every era live. The art, caption and pill follow the era the
 * playhead rests on for ShownEraDwellMillis (100 ms), so a slow drag flips
 * through every era and a fling lands on its destination without loading
 * the eras in between. Each era opens on its first work.
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

    // The era the playhead rests on. A new page cancels the wait, so eras
    // passed in a fast scrub never reach the art.
    var shownPage: Int by remember { mutableIntStateOf(rulerState.currentPage) }
    LaunchedEffect(rulerState) {
        snapshotFlow { rulerState.currentPage }.collectLatest { page: Int ->
            delay(ShownEraDwellMillis)
            shownPage = page
        }
    }
    val shownGroup: EraGroup = groups[shownPage.coerceIn(0, lastIndex)]

    // The global index (into state.stops) of the work on screen. The slot
    // inside AnimatedContent reports it; the caption and the pill read it.
    var visibleIndex: Int by remember { mutableIntStateOf(-1) }

    // Reads how far the works pager is between two works. The slot on screen
    // hands it over; the caption reads it in the draw phase, so following the
    // finger never recomposes.
    var worksOffset: () -> Float by remember { mutableStateOf<() -> Float>({ 0f }) }

    // Eras whose works pager already nudged. Written only from the slot's
    // effect, never during composition, so it needs no snapshot state.
    val nudgedEras: MutableSet<Era> = remember { HashSet() }

    // Fall back to the era's first work until the slot reports one of this
    // era's own works. -1 when the era has none.
    var moreIndex: Int = shownGroup.firstIndex
    val eraEnd: Int = shownGroup.firstIndex + shownGroup.stops.size
    if (visibleIndex >= shownGroup.firstIndex && visibleIndex < eraEnd) {
        moreIndex = visibleIndex
    }
    // The pill's click reads this, so it always opens the work on screen
    // without the pill recomposing on every swipe.
    val latestMoreIndex: State<Int> = rememberUpdatedState(moreIndex)
    // The caption scales with the user's font size.
    val captionBlockHeight: Dp = with(LocalDensity.current) {
        captionHeight()
    }

    // Frozen per work, so a caption fading out keeps its own stop.
    var captionTarget: CaptionTarget? = null
    val visibleStop: TimelineStop? = state.stops.getOrNull(moreIndex)
    if (visibleStop != null) {
        captionTarget = CaptionTarget(stop = visibleStop)
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
                // A flipbook: quick enough to keep up with a scrub.
                fadeIn(tween(150)).togetherWith(fadeOut(tween(100)))
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
                onVisibleIndex = { index: Int -> visibleIndex = index },
                onWorksOffset = { reader: () -> Float -> worksOffset = reader }
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedContent(
                targetState = captionTarget,
                contentKey = { target: CaptionTarget? -> target?.stop?.artwork?.id },
                transitionSpec = {
                    if (initialState?.stop?.era == targetState?.stop?.era) {
                        return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None
                    }
                    fadeIn(tween(150)).togetherWith(fadeOut(tween(100)))
                },
                label = "eraCaption",
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(captionBlockHeight)
                    .padding(horizontal = ReelsGutter)
            ) { target: CaptionTarget? ->
                if (target != null) {
                    // Fades out as the image slides away and in as the next
                    // one slides in: invisible at the halfway point, where
                    // the text swaps. Driven by the finger, so it can't
                    // lead or lag.
                    EraCaption(
                        stop = target.stop,
                        modifier = Modifier.graphicsLayer {
                            val fraction: Float = abs(worksOffset())
                            alpha = (1f - 2.5f * fraction).coerceIn(0f, 1f)
                        }
                    )
                }
            }

            // The pill sits in its own fixed row, so it never moves with the
            // caption. Its 48dp touch target adds about 10dp above the drawn
            // pill, so the 6dp spacer shows as a 16dp gap. The row keeps its
            // height in empty eras too, so the art never jumps.
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PillRowHeight)
            ) {
                // Keyed on the era, so a swipe within an era leaves it alone.
                AnimatedContent(
                    targetState = shownGroup,
                    contentKey = { group: EraGroup -> group.era },
                    transitionSpec = {
                        fadeIn(tween(150)).togetherWith(fadeOut(tween(100)))
                    },
                    label = "eraPill",
                    contentAlignment = Alignment.Center
                ) { group: EraGroup ->
                    if (group.stops.isNotEmpty()) {
                        OutlinedPill(
                            label = "More from " + eraSpan(group.era),
                            onClick = {
                                onIntent(ErasIntent.MoreClicked(latestMoreIndex.value))
                            }
                        )
                    }
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
    onWorksOffset: (() -> Float) -> Unit,
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

    // Hands the screen a reader of this pager's offset, for the caption's fade.
    LaunchedEffect(worksState) {
        onWorksOffset { worksState.currentPageOffsetFraction }
    }

    // The caption follows currentPage, which flips at the swipe's halfway
    // point, so it changes together with the image, not after the snap.
    LaunchedEffect(worksState, group.firstIndex) {
        snapshotFlow { worksState.currentPage }.collect { page: Int ->
            val index: Int = group.firstIndex + page
            onVisibleIndex(index)
        }
    }

    // Prefetch waits for the works pager to settle: when the era is shown
    // (page 0), when its first work arrives, and after each swipe. Never
    // mid-swipe.
    LaunchedEffect(worksState, group.firstIndex) {
        snapshotFlow { worksState.settledPage }.collect { page: Int ->
            val index: Int = group.firstIndex + page
            onIntent(ErasIntent.StopChanged(index))
        }
    }

    // Once per era: slide toward the next work and spring back, so the user
    // sees there is more to swipe. Waits until the era is held, not just
    // passed in a scrub, and skips it if the user is already swiping.
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
                .weight(1f),
            flingBehavior = PagerDefaults.flingBehavior(
                state = worksState,
                snapAnimationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
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
 * The work hangs at the bottom of its wall in the app's frame, like Detail,
 * so the art, the era and the caption read as one group, like a label under
 * its object. Until its ratio is known it shows bare and fitted; once the
 * image reports its size, the frame replaces it and sits exactly on the
 * image edges.
 */
@Composable
private fun EraArtwork(
    artwork: Artwork,
    onClick: () -> Unit,
)
{
    var ratio: Float? by remember(artwork.id) { mutableStateOf(artwork.aspectRatio) }

    // The wall. The works pager's swipe area stays full width around it.
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp)
    ) {
        val knownRatio: Float? = ratio
        if (knownRatio != null) {
            ArtworkFrame(
                imageUrl = artwork.imageUrl,
                contentDescription = artwork.title,
                onImageLoaded = { width, height, _ ->
                    ratio = width.toFloat() / height.toFloat()
                },
                onImageFailed = { },
                onClick = onClick,
                modifier = Modifier.aspectRatio(knownRatio)
            )
        } else {
            AsyncImage(
                model = artwork.imageUrl,
                contentDescription = artwork.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                onSuccess = { state ->
                    val width: Int = state.result.image.width
                    val height: Int = state.result.image.height
                    if (width > 0 && height > 0) {
                        ratio = width.toFloat() / height.toFloat()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick)
            )
        }
    }
}

/** The work the caption shows. */
private class CaptionTarget(val stop: TimelineStop)

/**
 * Title in Libre Caslon, then "artist · year", then the museum in small
 * spaced caps, all centered. The title gets three lines and "artist · year"
 * one; a long one first shrinks to fit (title down to 15sp, "artist · year"
 * down to 11sp) and only then is ellipsized. The museum is never cut: it
 * wraps.
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
        BasicText(
            text = stop.artwork.title,
            style = CaptionTitleStyle,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 19.sp)
        )
        val meta: String? = artistAndYear(stop.artwork)
        if (meta != null) {
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = meta,
                style = CaptionMetaStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 13.sp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = museumName(stop.artwork.providerId).uppercase(),
            color = White40,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            lineHeight = CaptionMuseumLineHeight,
            textAlign = TextAlign.Center
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
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}
