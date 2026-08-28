package com.kg.museumly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.kg.museumly.feature.scroll.presentation.ScrollViewModel
import com.kg.museumly.model.Artwork

private const val TAG = "MuseumlyImages"

/**
 * Phase 0 spike: the World Wonders question, answered up front. Each page reserves
 * exactly the artwork's true aspect ratio before the image decodes, inside a fixed
 * screen-sized page (Reels-style vertical snap via VerticalPager's real fling/snap).
 */
@Composable
fun ArtworkReelsScreen(viewModel: ScrollViewModel = hiltViewModel()) {
    val artworks: List<Artwork> by viewModel.artworks.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { artworks.size })

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (artworks.isEmpty()) {
            return@Box
        }
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            /**
             * val artwork = artworks.getOrNull(page)
             *     if (artwork != null) {
             *         ArtworkPage(artwork = artwork)
             *     }
             */
            ArtworkPage(artwork = artworks[page])
        }

        PageCounter(pagerState = pagerState, total = artworks.size)
    }
}

@Composable
private fun ArtworkPage(artwork: Artwork, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerRatio = maxWidth / maxHeight
            val aspectRatio = artwork.aspectRatio ?: containerRatio
            val (imageWidth, imageHeight) = if (aspectRatio > containerRatio) {
                maxWidth to maxWidth / aspectRatio
            } else {
                maxHeight * aspectRatio to maxHeight
            }

            var loadFailed by remember(artwork.id) { mutableStateOf(false) }

            AsyncImage(
                model = artwork.imageUrl,
                contentDescription = artwork.title,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) {
                        Log.e(TAG, "Failed to load ${artwork.id}: ${artwork.imageUrl}", state.result.throwable)
                        loadFailed = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(imageWidth)
                    .height(imageHeight),
            )

            if (loadFailed) {
                Text(
                    text = "Couldn't load this image",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        ArtworkCaption(artwork = artwork, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ArtworkCaption(artwork: Artwork, modifier: Modifier = Modifier) {
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
