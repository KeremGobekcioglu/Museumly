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

    if (state.isInitialLoad && state.artworks.isEmpty()) {
        // first attempt still running
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    if (state.artworks.isEmpty()) {
        // tried, got nothing — network down or the provider is empty
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(
                text = "Something went wrong",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
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
        pageCount = { state.artworks.size }
    )

    LaunchedEffect(pagerState.currentPage, state.artworks.size) {
        onPageChanged(pagerState.currentPage)
        if (state.artworks.size - pagerState.currentPage <= 5) {
            refresh()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val artwork = state.artworks.getOrNull(page)
                if (artwork != null) {
                    ArtworkPageWithRespectToAspectRatio(artwork = artwork)
                }
        }

        PageCounter(pagerState = pagerState, total = state.artworks.size)
    }
}

@Composable
internal fun ArtworkCaption(artwork: Artwork, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                ),
            )
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = artwork.title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${artwork.artist} · ${artwork.year}",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
