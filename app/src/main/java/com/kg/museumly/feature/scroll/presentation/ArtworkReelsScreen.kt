package com.kg.museumly.feature.scroll.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kg.museumly.BuildConfig
import com.kg.museumly.feature.scroll.presentation.components.ArtworkPageWithRespectToAspectRatio
import com.kg.museumly.feature.scroll.presentation.components.GalleryLoading
import com.kg.museumly.feature.scroll.presentation.components.GalleryNotice

private const val TAG = "MuseumlyImages"

private fun failedTitle(isOnline: Boolean, isTail: Boolean): String {
    if (!isOnline) {
        return if (isTail) "Lost the connection" else "No connection"
    }
    return if (isTail) "Couldn't reach the next room" else "The gallery didn't open"
}

private fun failedBody(isOnline: Boolean, isTail: Boolean): String {
    if (!isOnline) {
        return if (isTail) "We'll fetch the next works once you're back online."
        else "We'll pick this back up once you're back online."
    }
    return if (isTail) "The collection isn't responding right now."
    else "Something went wrong loading the collection."
}
/**
 * Phase 0 spike: the World Wonders question, answered up front. Each page reserves
 * exactly the artwork's true aspect ratio before the image decodes, inside a fixed
 * screen-sized page (Reels-style vertical snap via VerticalPager's real fling/snap).
 */
@Composable
fun ArtworkReelsScreen(
    state: ScrollUiState,
    refresh: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onDetailPage: (String) -> Unit,
) {

    // A fetch is genuinely in flight and we have nothing to show yet.
    // tail defaults to Loading at construction (before the launched
    // coroutine has had a chance to run), so this covers the very first
    // frame as well as every fetch after that, including a retry.
    if (state.tail == TailState.Loading && state.artworks.isEmpty()) {
        GalleryLoading("Hanging the work")
        return
    }

    // Nothing in Room and nothing loading. Two different situations:
    // the fetch failed, or every provider is genuinely empty. Only the
    // first is retryable, and this early-returns before the LaunchedEffect
    // below, so a button is the only way back.
    if (state.artworks.isEmpty()) {
        val tail = state.tail
        if (tail is TailState.Failed) {
            GalleryNotice(
                title = failedTitle(state.isOnline, false),
                body = failedBody(state.isOnline, false),
                actionLabel = if (state.isOnline) "Retry" else "Try anyway",
                onAction = refresh,
                isBusy = tail.retrying,
                debugDetail = if (BuildConfig.DEBUG) tail.message else null,
            )
        } else {
            GalleryNotice(
                title = "Nothing on the walls",
                body = "No works came back from the collection.",
                actionLabel = "Retry",
                onAction = refresh,
            )
        }
        return
    }

    // Don't build the pager until we know the start position.
    // rememberPagerState reads initialPage exactly once — if it's created
    // against an empty list, the position clamps to 0 and never corrects.
    if (state.initialPage == null) {
        return
    }

    val pagerState = rememberPagerState(
        initialPage = state.initialPage,
        pageCount = { state.artworks.size + 1 /*loading page*/ }
    )

    LaunchedEffect(pagerState.currentPage, state.artworks.size) {
        onPageChanged(pagerState.currentPage)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page < state.artworks.size) {
                val artwork = state.artworks.getOrNull(page)
                if (artwork != null) {
                    ArtworkPageWithRespectToAspectRatio(
                        artwork = artwork,
                        onDetailPage = onDetailPage
                    )
                }
            } else {
                when (val tail = state.tail) {
                    // Idle here means a load just finished right as the user
                    // landed on this page and the next one hasn't been
                    // triggered yet — visually indistinguishable from Loading.
                    TailState.Loading, TailState.Idle -> GalleryLoading("Art is worth the wait.")
                    is TailState.Failed -> GalleryNotice(
                        title = failedTitle(state.isOnline, true),
                        body = failedBody(state.isOnline, true),
                        actionLabel = if (state.isOnline) "Retry" else "Try anyway",
                        onAction = refresh,
                        isBusy = tail.retrying,
                        debugDetail = if (BuildConfig.DEBUG) tail.message else null,
                    )

                    TailState.Exhausted -> GalleryNotice(
                        title = "End of the gallery",
                        body = "You've seen everything here.",
                    )
                }
            }
        }


        if (pagerState.currentPage < state.artworks.size) {
            PageCounter(pagerState = pagerState, total = state.artworks.size)
        }
    }
}
@Composable
private fun PageCounter(pagerState: PagerState, total: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().safeDrawingPadding().padding(top = 12.dp)) {
        Text(
            text = "${pagerState.currentPage + 1} / $total",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .alpha(0.9f),
        )
    }
}
