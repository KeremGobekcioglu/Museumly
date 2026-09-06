package com.kg.museumly.feature.scroll.presentation

import android.content.ContentValues.TAG
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.kg.museumly.model.Artwork

@Composable
internal fun ArtworkPageWithoutAspectRatio(
    artwork: Artwork,
    modifier: Modifier = Modifier,
    onDetailPage: (String) -> Unit,
)  {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        Text(
            text = artwork.providerId,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        )
        if (loadFailed) {
            Text(
                text = "Couldn't load this image",
                color = Color.Red,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        ArtworkCaption(artwork = artwork, modifier = Modifier.align(Alignment.BottomStart))
    }
}
@Composable
internal fun ArtworkPageWithRestrainedBox(
    artwork: Artwork,
    modifier: Modifier = Modifier,
    onDetailPage: (String) -> Unit,
)  {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                .fillMaxWidth(0.6f)      // <-- force it smaller than the screen
                .fillMaxHeight(0.4f)
                .border(2.dp, Color.Red) // <-- shows you exactly where the box is
                .background(Color(0xFF222222)), // <-- shows empty space vs image
            contentScale = ContentScale.Fit
        )

        Text(
            text = artwork.providerId,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        )

        if (loadFailed) {
            Text(
                text = "Couldn't load this image",
                color = Color.Red,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        ArtworkCaption(artwork = artwork, modifier = Modifier.align(Alignment.BottomStart))
    }
}


@Composable
internal fun ArtworkPageWithRespectToAspectRatio(
    artwork: Artwork,
    modifier: Modifier = Modifier,
    onDetailPage: (String) -> Unit,
) {
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
            Text(
                text = artwork.providerId,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
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
                    .height(imageHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDetailPage(artwork.id) }
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
        val line: String? = captionLine(artwork.artist, artwork.year)
        if (line != null) {
            Text(
                text = line,
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun captionLine(artist: String?, year: String?): String? {
    if (artist != null && year != null) {
        return "$artist · $year"
    }
    if (artist != null) {
        return artist
    }
    if (year != null) {
        return year
    }
    return null
}