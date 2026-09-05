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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.museumly.model.Artwork

private const val TAG = "MuseumlyImages"

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
    onDetailPage: (String) -> Unit
) {

    // A fetch is genuinely in flight and we have nothing to show yet.
    // isInitialLoad covers the very first frame, before the launched
    // coroutine has had a chance to flip isLoadingMore to true; isLoadingMore
    // covers every fetch after that, including a retry after a failure.
    if ((state.isInitialLoad || state.isLoadingMore) && state.artworks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Nothing in Room and nothing loading. Two different situations:
    // the fetch failed, or every provider is genuinely empty. Only the
    // first is retryable, and this early-returns before the LaunchedEffect
    // below, so a button is the only way back.
    if (state.artworks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    TextButton(onClick = refresh) {
                        Text("Retry", color = Color.White)
                    }
                } else {
                    Text(
                        text = "Nothing here yet",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
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
        if (pagerState.currentPage < state.artworks.size &&
            state.artworks.size - pagerState.currentPage <= 5
        ) {
            refresh()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if(page < state.artworks.size)
            {
                val artwork = state.artworks.getOrNull(page)
                if (artwork != null) {
                    ArtworkPageWithRespectToAspectRatio(artwork = artwork, onDetailPage = onDetailPage)
                }
            }
            else
            {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.isLoadingMore -> CircularProgressIndicator(color = Color.White)
                        state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                            TextButton(onClick = refresh) {
                                Text("Retry", color = Color.White)
                            }
                        }
                        else -> Text(
                            text = "You're all caught up",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
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
