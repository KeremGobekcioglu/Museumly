package com.kg.museumly.presentation.feature.eras.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Era
import com.kg.museumly.presentation.feature.detail.ArtworkFrame
import com.kg.museumly.presentation.feature.eras.ErasIntent
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * The works pager: one era's works and their dots, the part of the card
 * that holds the art. Reads only its own group, never the screen's state:
 * while the old era fades out, its works pager must keep showing the old
 * era.
 */
@Composable
internal fun EraWorks(
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
    // the latest count. Each era gets its own card, so its own PagerState,
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
        // A works pager still composed after its era left the screen must
        // not spend that era's nudge.
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
