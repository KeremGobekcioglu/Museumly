package com.kg.museumly.feature.scroll.presentation.components

import android.content.ContentValues.TAG
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            text = sourceLabel(artwork),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
/**
 * Height of the row the section pill and page counter sit in, below the
 * status bar. Pages reserve it so a tall work can never slide under them.
 */
internal val ReelsTopBarHeight = 60.dp

/**
 * Three stacked regions — top bar, artwork, caption — instead of layers.
 * The artwork box takes whatever the other two leave, so a tall work or a
 * two-line title can't overlap the text, and the box is the same on every
 * page apart from that.
 */
@Composable
internal fun ArtworkPageWithRestrainedBox(
    artwork: Artwork,
    modifier: Modifier = Modifier,
    onDetailPage: (String) -> Unit,
)  {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
        Spacer(modifier = Modifier.height(ReelsTopBarHeight))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // Wall on both sides, so a wide work reads as hung, not as a
                // full-bleed photo.
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
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
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDetailPage(artwork.id) },
                contentScale = ContentScale.Fit
            )

            if (loadFailed) {
                Text(
                    text = "Couldn't load this image",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        ArtworkCaption(artwork = artwork)
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
                text = sourceLabel(artwork),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .widthIn(max = maxWidth * 0.4f)
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

/**
 * Sits on the wall below the work, not over it, so it needs no scrim.
 * Read top to bottom like a museum label: title, maker, then the
 * collection credit as the quietest line.
 */
@Composable
private fun ArtworkCaption(artwork: Artwork, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
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
        Text(
            text = museumName(artwork.providerId).uppercase(),
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        // No maxLines on the credit: a cut-off museum or department name
        // reads as a bug. A long one wraps, and the artwork box gives up the
        // height.
        artwork.department?.let { department ->
            Text(
                text = department.uppercase(),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

private fun museumName(providerId: String): String = when (providerId) {
    "met" -> "The Met"
    "cleveland" -> "Cleveland Museum of Art"
    else -> providerId
}

/**
 * "cleveland: Indian and Southeast Asian Art", "met: Asian Art".
 * department is the museum's own field on the record, so for the Met it's the
 * display name, not the numeric id in the cursor key. Just the provider when
 * the record has no department.
 */
private fun sourceLabel(artwork: Artwork): String {
    val department: String = artwork.department ?: return artwork.providerId
    return "${artwork.providerId}: $department"
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