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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.TimelineStop
import com.kg.museumly.presentation.R
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import com.kg.museumly.presentation.feature.scroll.presentation.components.TopBarChipOutline
import com.kg.museumly.presentation.feature.scroll.presentation.components.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Libre Caslon Text, the same bundled font as the feed caption. The feed's
 * FontFamily is private to ArtworkPage.kt, so this declares its own.
 */
private val ErasSerif: FontFamily = FontFamily(
    Font(
        resId = R.font.libre_caslon_text,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
)

/** The hero's number. One number only, so 80sp fits "1400s" and "3000" with their caps. */
private val HeroNumberStyle: TextStyle = TextStyle(
    fontFamily = ErasSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 80.sp,
    lineHeight = 84.sp,
    color = Color.White,
    textAlign = TextAlign.Center,
)

/** "BEFORE", "BC", "AD" beside the hero's number, sitting on its baseline. */
private val HeroCapsStyle: SpanStyle = SpanStyle(
    fontSize = 15.sp,
    letterSpacing = 2.sp,
    color = Color.White.copy(alpha = 0.6f),
)

private val CaptionTitleStyle: TextStyle = TextStyle(
    fontFamily = ErasSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 19.sp,
    lineHeight = 24.sp,
    color = Color.White,
    textAlign = TextAlign.Center,
)

private val EmptyStyle: TextStyle = TextStyle(
    fontFamily = ErasSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    color = Color.White.copy(alpha = 0.6f),
    textAlign = TextAlign.Center,
)

private val AnchorLabelStyle: TextStyle = TextStyle(
    fontSize = 10.sp,
    letterSpacing = 0.5.sp,
    color = Color.White.copy(alpha = 0.6f),
)

private val White60: Color = Color.White.copy(alpha = 0.6f)
private val White40: Color = Color.White.copy(alpha = 0.4f)

/**
 * The caption's fixed height: two title lines, "artist · year" and the
 * section. Fixed so a long title never pushes the art up.
 */
private val CaptionHeight: Dp = 92.dp
/** The pill's row keeps its height in empty eras too, so the art never jumps. */
private val PillRowHeight: Dp = 40.dp

/** How far the works pager slides to show there is a next work. */
private val NudgeDistance: Dp = 24.dp

/** One invisible ruler page per era. It is the drag engine; the Canvas draws the scrubber. */
private val TickPageWidth: Dp = 64.dp
private val ScrubberHeight: Dp = 64.dp
/** The track's inset from both edges. */
private val TrackInset: Dp = 24.dp
private val TrackY: Dp = 22.dp
private val TrackStroke: Dp = 1.5.dp
private val SegmentGap: Dp = 2.dp
private val PlayheadWidth: Dp = 2.dp
private val PlayheadHeight: Dp = 20.dp
private val PlayheadHalo: Dp = 18.dp
private val AnchorLabelTop: Dp = 42.dp

/**
 * One era on the ruler and the works the timeline holds for it.
 * firstIndex is the index of the era's first work in state.stops, or -1
 * when the era has none yet.
 */
private class EraGroup(
    val era: Era,
    val firstIndex: Int,
    val stops: List<TimelineStop>
)

/**
 * The eras the timeline shows: every era except the 2000s, oldest first.
 * The repository decides which works each era holds; this decides which
 * eras get a place on the ruler.
 */
private fun timelineEras(): List<Era> {
    val result: MutableList<Era> = ArrayList()
    for (era in Era.erasFor(null)) {
        if (era != Era.Y2000) {
            result.add(era)
        }
    }
    return result
}

/**
 * One group for every timeline era, in order, including eras with no works.
 * Stops arrive era by era, so the ruler keeps a fixed place for each era
 * instead of growing under the user's finger.
 */
private fun groupByEra(stops: List<TimelineStop>): List<EraGroup> {
    val groups: MutableList<EraGroup> = ArrayList()
    for (era in timelineEras()) {
        var firstIndex: Int = -1
        val eraStops: MutableList<TimelineStop> = ArrayList()
        for (index in stops.indices) {
            val stop: TimelineStop = stops[index]
            if (stop.era == era) {
                if (firstIndex == -1) {
                    firstIndex = index
                }
                eraStops.add(stop)
            }
        }
        groups.add(EraGroup(era, firstIndex, eraStops))
    }
    return groups
}

fun eraLabel(era: Era): String {
    return when (era) {
        Era.PREHISTORIC -> "Prehistory"
        Era.BC_3000 -> "3000 BC"
        Era.BC_2000 -> "2000 BC"
        Era.BC_1000 -> "1000 BC"
        Era.BC_500 -> "500 BC"
        Era.AD_0 -> "0 – 500"
        Era.AD_500 -> "500 – 1000"
        Era.AD_1000 -> "1000 – 1200"
        Era.AD_1200 -> "1200 – 1400"
        Era.Y1400 -> "1400s"
        Era.Y1500 -> "1500s"
        Era.Y1600 -> "1600s"
        Era.Y1700 -> "1700s"
        Era.Y1800 -> "1800s"
        Era.Y1900 -> "1900s"
        Era.Y2000 -> "2000s"
    }
}

/**
 * The hero's words: one serif number with small caps beside it.
 * "BEFORE 3000 BC", "3000 BC", "AD 1", "AD 500", "1000", "1400s".
 * The full range lives in the pill instead.
 */
private class HeroParts(
    val prefix: String?,
    val number: String,
    val suffix: String?
)

private fun heroParts(era: Era): HeroParts {
    return when (era) {
        Era.PREHISTORIC -> HeroParts("BEFORE", "3000", "BC")
        Era.BC_3000 -> HeroParts(null, "3000", "BC")
        Era.BC_2000 -> HeroParts(null, "2000", "BC")
        Era.BC_1000 -> HeroParts(null, "1000", "BC")
        Era.BC_500 -> HeroParts(null, "500", "BC")
        Era.AD_0 -> HeroParts("AD", "1", null)
        Era.AD_500 -> HeroParts("AD", "500", null)
        Era.AD_1000 -> HeroParts(null, "1000", null)
        Era.AD_1200 -> HeroParts(null, "1200", null)
        Era.Y1400 -> HeroParts(null, "1400s", null)
        Era.Y1500 -> HeroParts(null, "1500s", null)
        Era.Y1600 -> HeroParts(null, "1600s", null)
        Era.Y1700 -> HeroParts(null, "1700s", null)
        Era.Y1800 -> HeroParts(null, "1800s", null)
        Era.Y1900 -> HeroParts(null, "1900s", null)
        Era.Y2000 -> HeroParts(null, "2000s", null)
    }
}

/**
 * The hero's text. Caps on one side only get an invisible twin on the
 * other, so the number itself stays centered over the caption.
 */
private fun heroText(parts: HeroParts): AnnotatedString {
    val ghost: SpanStyle = HeroCapsStyle.copy(color = Color.Transparent)
    return buildAnnotatedString {
        if (parts.prefix != null) {
            withStyle(HeroCapsStyle) { append(parts.prefix + " ") }
        } else if (parts.suffix != null) {
            withStyle(ghost) { append(parts.suffix + " ") }
        }
        append(parts.number)
        if (parts.suffix != null) {
            withStyle(HeroCapsStyle) { append(" " + parts.suffix) }
        } else if (parts.prefix != null) {
            withStyle(ghost) { append(" " + parts.prefix) }
        }
    }
}

/** What a screen reader says for the hero: the visible words, without the twin. */
private fun heroDescription(parts: HeroParts): String {
    return listOfNotNull(parts.prefix, parts.number, parts.suffix).joinToString(" ")
}

/**
 * The era's years, from Era.begin and Era.end:
 * "1600 – 1699", "3000 – 2001 BC", "500 – 1 BC", "AD 1 – 499".
 * PREHISTORIC has no real start, so it reads "Prehistory".
 */
private fun eraSpan(era: Era): String {
    if (era == Era.PREHISTORIC) {
        return "Prehistory"
    }
    if (era.end < 0) {
        return (-era.begin).toString() + " – " + (-era.end).toString() + " BC"
    }
    if (era.begin == 0) {
        // There is no year 0; the era starts at AD 1.
        return "AD 1 – " + era.end.toString()
    }
    return era.begin.toString() + " – " + era.end.toString()
}

/** "Artist · ca. 1665", skipping whichever part is missing. Null when both are. */
private fun artistAndYear(artwork: Artwork): String? {
    val parts: MutableList<String> = ArrayList()
    val artist: String? = artwork.artist
    if (artist != null && artist.isNotBlank()) {
        parts.add(artist)
    }
    val year: String? = artwork.year
    if (year != null && year.isNotBlank()) {
        parts.add(year)
    }
    if (parts.isEmpty()) {
        return null
    }
    return parts.joinToString(" · ")
}

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

/**
 * The scrubber. The ruler's HorizontalPager is the invisible drag engine:
 * one empty 64dp page per era, so fling, snap, settledPage and haptics work
 * as before. reverseLayout makes dragging right move forward in time.
 *
 * A Canvas on top draws what the user sees, and takes no input:
 *  - a hairline from 24dp to width - 24dp, one segment per era, brighter
 *    the more works the era holds, so it lights up as eras arrive;
 *  - the playhead, a white slit with a soft halo, read in the draw phase;
 *  - four anchor labels.
 * A tap anywhere on the scrubber jumps to the nearest era.
 */
@Composable
private fun EraScrubber(
    groups: List<EraGroup>,
    rulerState: PagerState,
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
        for ((era, text) in ScrubberAnchors) {
            val index: Int = groups.indexOfFirst { group: EraGroup -> group.era == era }
            if (index >= 0) {
                result.add(Pair(index, textMeasurer.measure(text, AnchorLabelStyle)))
            }
        }
        result
    }

    val currentOnJump: (Int) -> Unit by rememberUpdatedState(onJump)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(ScrubberHeight)
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
        val sidePadding: Dp = (maxWidth - TickPageWidth) / 2
        HorizontalPager(
            state = rulerState,
            pageSize = PageSize.Fixed(TickPageWidth),
            contentPadding = PaddingValues(horizontal = sidePadding),
            reverseLayout = true,
            flingBehavior = PagerDefaults.flingBehavior(
                state = rulerState,
                pagerSnapDistance = PagerSnapDistance.atMost(groups.size)
            ),
            modifier = Modifier.fillMaxSize()
        ) { _: Int ->
            // Draws nothing; the Canvas below is the scrubber.
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
        }
    }
}

/** The scrubber's four labels: where the timeline's big turns are. */
private val ScrubberAnchors: List<Pair<Era, String>> = listOf(
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
