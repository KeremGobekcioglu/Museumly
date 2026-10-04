package com.kg.museumly.feature.scroll.presentation.components

import android.content.ContentValues.TAG
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.kg.museumly.R
import com.kg.museumly.domain.model.Artwork
/**
 * Height of the row the section pill and page counter sit in, below the
 * status bar. Pages reserve it so a tall work can never slide under them.
 */
internal val ReelsTopBarHeight = 60.dp

/**
 * The page's one side margin for text and chips: the section pill, the page
 * counter and the caption all line up on it. The artwork doesn't use it, so
 * a wide work stays full-bleed.
 */
internal val ReelsGutter = 16.dp

/**
 * Libre Caslon Text, bundled (OFL, licence in assets/licenses). Bundled
 * rather than downloadable so the title never renders in a fallback font
 * and then reflows, works offline, and doesn't need Play Services.
 */
private val CaptionSerif: FontFamily = FontFamily(
    Font(
        resId = R.font.libre_caslon_text,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
)

/** Regular weight, a step below titleLarge: a label, not a headline. */
private val CaptionTitleStyle: TextStyle = TextStyle(
    fontFamily = CaptionSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 26.sp,
)

/** The caption's maker line, also used for the failed-image notice. */
private val CaptionSecondaryColor: Color = Color.White.copy(alpha = 0.75f)

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
                // Black on both sides is invisible, so a gutter only shrinks the work.
                // Top and bottom keep a gap to the top bar and the caption.
                .padding(vertical = 16.dp),
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
                    text = "Image unavailable",
                    color = CaptionSecondaryColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        ArtworkCaption(artwork = artwork)
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
            .padding(start = ReelsGutter, end = ReelsGutter, top = 8.dp, bottom = 40.dp),
    ) {
        Text(
            text = artwork.title,
            color = Color.White,
            style = CaptionTitleStyle,
        )
        val line: String? = captionLine(artwork.artist, artwork.year)
        if (line != null) {
            Text(
                text = line,
                color = CaptionSecondaryColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = museumName(artwork.providerId).uppercase(),
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        // No maxLines anywhere in the caption: a cut-off title, artist,
        // museum or department reads as a bug. A long one wraps, and the
        // artwork box gives up the height.
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