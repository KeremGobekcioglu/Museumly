package com.kg.museumly.presentation.feature.eras

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
import kotlin.math.abs

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

private val EraNameStyle: TextStyle = TextStyle(
    fontFamily = ErasSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 64.sp,
    lineHeight = 70.sp,
    color = Color.White,
)

private val CaptionTitleStyle: TextStyle = TextStyle(
    fontFamily = ErasSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    color = Color.White,
)

private val EmptyStyle: TextStyle = TextStyle(
    fontFamily = ErasSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    color = Color.White.copy(alpha = 0.6f),
    textAlign = TextAlign.Center,
)

private val White60: Color = Color.White.copy(alpha = 0.6f)
private val White50: Color = Color.White.copy(alpha = 0.5f)
private val White40: Color = Color.White.copy(alpha = 0.4f)

/** One ruler page per era; the center marker sits over the middle one. */
private val TickPageWidth: Dp = 64.dp
private val RulerHeight: Dp = 72.dp
/** Where the tick starts inside a ruler page; the center marker is laid out around it. */
private val TickTop: Dp = 24.dp
private val TickHeight: Dp = 12.dp
private val MarkerHeight: Dp = 28.dp

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

/** The ruler's label under each tick: short enough for a 64dp page at 10sp. */
private fun eraShortLabel(era: Era): String {
    return when (era) {
        Era.PREHISTORIC -> "Prehistory"
        Era.BC_3000 -> "3000 BC"
        Era.BC_2000 -> "2000 BC"
        Era.BC_1000 -> "1000 BC"
        Era.BC_500 -> "500 BC"
        Era.AD_0 -> "0"
        Era.AD_500 -> "500"
        Era.AD_1000 -> "1000"
        Era.AD_1200 -> "1200"
        Era.Y1400 -> "1400"
        Era.Y1500 -> "1500"
        Era.Y1600 -> "1600"
        Era.Y1700 -> "1700"
        Era.Y1800 -> "1800"
        Era.Y1900 -> "1900"
        Era.Y2000 -> "2000"
    }
}

/**
 * The era's years, from Era.begin and Era.end:
 * "1600 – 1699", "3000 – 2001 BC", "500 – 1 BC", "0 – 499".
 * PREHISTORIC has no real start, so it reads "before 3000 BC".
 */
private fun eraSpan(era: Era): String {
    if (era == Era.PREHISTORIC) {
        return "before " + yearText(era.end + 1)
    }
    if (era.end < 0) {
        return (-era.begin).toString() + " – " + (-era.end).toString() + " BC"
    }
    return yearText(era.begin) + " – " + yearText(era.end)
}

private fun yearText(year: Int): String {
    if (year < 0) {
        return (-year).toString() + " BC"
    }
    return year.toString()
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
 * Two indices into the era groups, both from the ruler:
 *  - dragging is the ruler's currentPage. It follows the finger, and only
 *    the era name and the tick alphas read it, so sliding stays cheap.
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
    // inside AnimatedContent reports it; the pill reads it.
    var visibleIndex: Int by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        AnimatedContent(
            targetState = settledGroup,
            contentKey = { group: EraGroup -> group.era },
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(initialScale = 0.94f, animationSpec = tween(300)))
                    .togetherWith(fadeOut(tween(200)))
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
                onIntent = onIntent,
                onVisibleIndex = { index: Int -> visibleIndex = index }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        EraName(
            era = draggingGroup.era,
            modifier = Modifier.padding(horizontal = ReelsGutter)
        )

        if (settledGroup.stops.isNotEmpty()) {
            // Fall back to the era's first work until the slot reports one
            // of this era's own works.
            var moreIndex: Int = settledGroup.firstIndex
            val eraEnd: Int = settledGroup.firstIndex + settledGroup.stops.size
            if (visibleIndex >= settledGroup.firstIndex && visibleIndex < eraEnd) {
                moreIndex = visibleIndex
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedPill(
                label = "More from " + eraLabel(settledGroup.era) + " →",
                onClick = { onIntent(ErasIntent.MoreClicked(moreIndex)) },
                modifier = Modifier.padding(horizontal = ReelsGutter)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        val scope: CoroutineScope = rememberCoroutineScope()
        EraRuler(
            groups = groups,
            rulerState = rulerState,
            onTickClick = { page: Int ->
                scope.launch {
                    rulerState.animateScrollToPage(page)
                }
            }
        )
    }
}

/**
 * One era's works and caption, the part that fades in when the ruler stops.
 * Reads only its own group, never the screen's state: while the old era
 * fades out, its slot must keep showing the old era.
 */
@Composable
private fun EraSlot(
    group: EraGroup,
    isLoading: Boolean,
    hasError: Boolean,
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

    val current: TimelineStop = group.stops[worksState.currentPage.coerceIn(0, group.stops.size - 1)]

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = worksState,
            // The next work peeks in at the edge, so the pager reads as swipeable.
            contentPadding = PaddingValues(horizontal = 40.dp),
            pageSpacing = 12.dp,
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
        if (group.stops.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = (worksState.currentPage + 1).toString() + " / " + group.stops.size,
                color = White60,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        EraCaption(
            stop = current,
            modifier = Modifier.padding(horizontal = ReelsGutter)
        )
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

/** Bare image on black, fitted and centered, no frame and no favorite button. */
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
        alignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
    )
}

/**
 * Title in Libre Caslon, then "artist · year", then the section in small
 * spaced caps. Everything wraps; nothing is ellipsized.
 */
@Composable
private fun EraCaption(
    stop: TimelineStop,
    modifier: Modifier = Modifier,
)
{
    Column(modifier = modifier) {
        Text(
            text = stop.artwork.title,
            style = CaptionTitleStyle
        )
        val meta: String? = artistAndYear(stop.artwork)
        if (meta != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = meta,
                color = White60,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stop.section.label.uppercase(),
            color = White40,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp
        )
    }
}

/**
 * The hero: the era under the ruler's marker, and its years under it.
 * Follows the ruler live; a 150 ms crossfade makes fast sliding read as
 * flipping through eras instead of flickering.
 */
@Composable
private fun EraName(
    era: Era,
    modifier: Modifier = Modifier,
)
{
    AnimatedContent(
        targetState = era,
        transitionSpec = {
            fadeIn(tween(150)).togetherWith(fadeOut(tween(150)))
        },
        label = "eraName",
        modifier = modifier
    ) { shown: Era ->
        Column {
            Text(
                text = eraLabel(shown),
                style = EraNameStyle
            )
            Text(
                text = eraSpan(shown),
                color = White50,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * The era ruler: one 64dp page per era, padded so the first and last era
 * can sit under the center marker. History moves under the fixed marker.
 * A strong fling can travel the whole ruler; ticks fade with distance from
 * the center, and both ends fade into black to show there is more.
 */
@Composable
private fun EraRuler(
    groups: List<EraGroup>,
    rulerState: PagerState,
    onTickClick: (Int) -> Unit,
)
{
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(RulerHeight)
    ) {
        val sidePadding: Dp = (maxWidth - TickPageWidth) / 2
        HorizontalPager(
            state = rulerState,
            pageSize = PageSize.Fixed(TickPageWidth),
            contentPadding = PaddingValues(horizontal = sidePadding),
            flingBehavior = PagerDefaults.flingBehavior(
                state = rulerState,
                pagerSnapDistance = PagerSnapDistance.atMost(groups.size)
            ),
            modifier = Modifier.fillMaxSize()
        ) { page: Int ->
            val group: EraGroup = groups[page]
            val hasWorks: Boolean = group.stops.isNotEmpty()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Read here, in the draw phase, so dragging redraws
                        // the ticks without recomposing them.
                        val position: Float = rulerState.currentPage + rulerState.currentPageOffsetFraction
                        alpha = tickAlpha(abs(page - position), hasWorks)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTickClick(page) }
                    )
            ) {
                Spacer(modifier = Modifier.height(TickTop))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(TickHeight)
                        .background(Color.White)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = eraShortLabel(group.era),
                    color = Color.White,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Both ends fade into black: there is more history past the edge.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black,
                        0.22f to Color.Transparent,
                        0.78f to Color.Transparent,
                        1f to Color.Black
                    )
                )
        )

        // The fixed marker, centered on the tick row. History moves under it.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = TickTop - (MarkerHeight - TickHeight) / 2)
                .width(2.dp)
                .height(MarkerHeight)
                .background(Color.White)
        )
    }
}

/** 1.0 at the center, 0.25 three eras away, never below 0.15. Halved for an era with no works yet. */
private fun tickAlpha(distance: Float, hasWorks: Boolean): Float {
    var alpha: Float = 1f - distance * 0.25f
    if (alpha < 0.15f) {
        alpha = 0.15f
    }
    if (!hasWorks) {
        alpha = alpha * 0.5f
    }
    return alpha
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
