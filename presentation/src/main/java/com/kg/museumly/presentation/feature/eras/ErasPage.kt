package com.kg.museumly.presentation.feature.eras

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import com.kg.museumly.presentation.BuildConfig
import com.kg.museumly.presentation.feature.detail.ArtworkFrame
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryLoading
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryNotice
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
 * Which screen ErasScreen shows. Every mode but Timeline is the cold state:
 * no era has a single work yet, so there is nothing to scrub through and
 * the gallery screens take the whole page, as on the feed. Once one era has
 * works, the timeline stays, and an empty era shows EmptyEra in its slot.
 */
private enum class ErasMode { Waiting, Loading, Failed, Empty, Timeline }

private fun ErasUiState.mode(): ErasMode {
    if (stops.isNotEmpty()) {
        return ErasMode.Timeline
    }
    // Room hasn't answered yet. Showing GalleryLoading here would flash it
    // on every visit with a cached timeline.
    if (!stopsReady) {
        return ErasMode.Waiting
    }
    // Checked before isLoading: a retry keeps the placard up, spinner on.
    if (errorMessage != null) {
        return ErasMode.Failed
    }
    if (isLoading) {
        return ErasMode.Loading
    }
    return ErasMode.Empty
}

private fun failedTitle(isOnline: Boolean): String {
    if (!isOnline) {
        return "No connection"
    }
    return "The timeline didn't open"
}

private fun failedBody(isOnline: Boolean): String {
    if (!isOnline) {
        return "We'll pick this back up once you're back online."
    }
    return "Something went wrong loading the collection."
}

/**
 * Crossfades between modes like the feed's ReelsContent. contentKey = mode()
 * means only a change of screen animates, and the screen fading out keeps
 * the state it last had.
 */
@Composable
fun ErasScreen(
    state: ErasUiState,
    onIntent: (ErasIntent) -> Unit,
)
{
    AnimatedContent(
        targetState = state,
        contentKey = { it.mode() },
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "eras-mode",
    ) { slotState: ErasUiState ->
        when (slotState.mode()) {
            ErasMode.Waiting -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
            ErasMode.Loading -> GalleryLoading("Hanging the timeline")
            ErasMode.Failed -> GalleryNotice(
                title = failedTitle(slotState.isOnline),
                body = failedBody(slotState.isOnline),
                actionLabel = if (slotState.isOnline) "Retry" else "Try anyway",
                onAction = { onIntent(ErasIntent.RetryClicked) },
                isBusy = slotState.isRetrying,
                debugDetail = if (BuildConfig.DEBUG) slotState.errorMessage else null,
            )
            ErasMode.Empty -> GalleryNotice(
                title = "Nothing on the walls",
                body = "No works came back for the timeline.",
                actionLabel = "Retry",
                onAction = { onIntent(ErasIntent.RetryClicked) },
            )
            ErasMode.Timeline -> ErasTimeline(state = slotState, onIntent = onIntent)
        }
    }
}

/**
 * Top to bottom: the era's card (art, era, caption, pill), the scrubber.
 *
 * The card is one piece: it turns as a whole when the finger lifts, or when
 * the playhead rests on an era for ShownEraDwellMillis (200 ms) while the
 * finger still holds the ruler, so the number never runs ahead of the art.
 * While the finger moves, the scrubber's label names the era under the
 * playhead. Each era opens on its first work.
 *
 * The ruler's PagerState is saved across rotation by rememberPagerState.
 */
@Composable
private fun ErasTimeline(
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

    // The era the card shows. While a finger holds the ruler, a page must
    // stay for ShownEraDwellMillis, and a new page cancels the wait, so eras
    // passed in a scrub never reach the card. Once the finger lifts, the
    // ruler already knows where it will land, so the card turns there at once.
    val isDragged: Boolean by rulerState.interactionSource.collectIsDraggedAsState()
    var shownPage: Int by remember { mutableIntStateOf(rulerState.currentPage) }
    LaunchedEffect(rulerState) {
        snapshotFlow { RulerMoment(rulerState.currentPage, rulerState.targetPage, isDragged) }
            .collectLatest { moment: RulerMoment ->
                if (moment.dragging) {
                    delay(ShownEraDwellMillis)
                    shownPage = moment.page
                    return@collectLatest
                }
                // Finger up (a release, a fling or a tap-jump): turn to where the
                // ruler will land, while the playhead is still travelling there.
                shownPage = moment.target
            }
    }
    val shownGroup: EraGroup = groups[shownPage.coerceIn(0, lastIndex)]

    // A card that outlives its era (still turning away, or kept by an
    // interrupted turn) asks this before it nudges.
    val latestShownEra: State<Era> = rememberUpdatedState(shownGroup.era)

    // The label shows while the finger moves and until the card has turned.
    var labelTarget: Float = 0f
    if (rulerState.isScrollInProgress || rulerState.currentPage != shownPage) {
        labelTarget = 1f
    }
    val labelAlpha: Float by animateFloatAsState(
        targetValue = labelTarget,
        animationSpec = tween(250),
        label = "eraLabelAlpha"
    )

    // Eras whose works pager already nudged. Written only from the slot's
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
                // The error outlives a retry's start; while it runs the slot
                // says "Arriving…" without the button.
                hasError = state.errorMessage != null && !state.isLoading,
                captionBlockHeight = captionBlockHeight,
                nudgedEras = nudgedEras,
                onIntent = onIntent
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        val scope: CoroutineScope = rememberCoroutineScope()
        EraScrubber(
            groups = groups,
            rulerState = rulerState,
            labelAlpha = { labelAlpha },
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

/**
 * One era as a single piece: its works, the era, the caption, the pill.
 * Reads only its own group, so while it turns away it keeps showing its own
 * era, work and caption.
 */
@Composable
private fun EraCard(
    group: EraGroup,
    isShownEra: () -> Boolean,
    isLoading: Boolean,
    hasError: Boolean,
    captionBlockHeight: Dp,
    nudgedEras: MutableSet<Era>,
    onIntent: (ErasIntent) -> Unit,
)
{
    // The work on screen, as a page of this era's works pager.
    var page: Int by remember { mutableIntStateOf(0) }

    // Reads how far the works pager is between two works, for the caption's
    // fade. Read in the draw phase, so following the finger never recomposes.
    var worksOffset: () -> Float by remember { mutableStateOf<() -> Float>({ 0f }) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            EraSlot(
                group = group,
                isShownEra = isShownEra,
                isLoading = isLoading,
                hasError = hasError,
                nudgedEras = nudgedEras,
                onIntent = onIntent,
                onVisibleIndex = { index: Int -> page = index - group.firstIndex },
                onWorksOffset = { reader: () -> Float -> worksOffset = reader }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        EraHero(
            era = group.era,
            hasWorks = group.stops.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReelsGutter)
        )

        Spacer(modifier = Modifier.height(8.dp))
        // Frozen per work, so a caption fading out keeps its own stop.
        var captionTarget: CaptionTarget? = null
        val visibleStop: TimelineStop? = group.stops.getOrNull(page)
        if (visibleStop != null) {
            captionTarget = CaptionTarget(stop = visibleStop)
        }
        AnimatedContent(
            targetState = captionTarget,
            contentKey = { target: CaptionTarget? -> target?.stop?.artwork?.id },
            transitionSpec = {
                EnterTransition.None togetherWith ExitTransition.None
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
            if (group.stops.isNotEmpty()) {
                OutlinedPill(
                    label = "More from " + eraSpan(group.era),
                    // Reads page on click, so it opens the work on screen
                    // without the pill recomposing on every swipe.
                    onClick = {
                        onIntent(ErasIntent.MoreClicked(group.firstIndex + page))
                    }
                )
            }
        }
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
    isShownEra: () -> Boolean,
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
        // A slot still composed after its era left the screen must not
        // spend that era's nudge.
        if (!isShownEra()) {
            return@LaunchedEffect
        }
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
 * The ruler at one moment: the page under the playhead, where it will land,
 * and whether a finger holds it.
 */
private data class RulerMoment(val page: Int, val target: Int, val dragging: Boolean)

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
 * The hero: the card's era. It turns with the card, so it has no motion of
 * its own. Dims to 40% while the era has no works, and brightens if they
 * arrive while the card is on screen.
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
    val parts: HeroParts = heroParts(era)
    Text(
        text = heroText(parts),
        style = HeroNumberStyle,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clearAndSetSemantics { contentDescription = heroDescription(parts) }
    )
}

/**
 * The card turns like a page of time: the old era drifts up and fades, the
 * new one rises a little from below and fades in just after, so the two
 * barely overlap and no frame goes black. Back in time runs the other way.
 */
private fun eraTurn(forward: Boolean): ContentTransform {
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
