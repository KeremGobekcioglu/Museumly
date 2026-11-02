package com.kg.museumly.presentation.feature.share.card

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.presentation.R
import com.kg.museumly.presentation.feature.scroll.presentation.components.WallColor
import com.kg.museumly.presentation.feature.scroll.presentation.components.captionLine
import com.kg.museumly.presentation.feature.scroll.presentation.components.museumName
import com.kg.museumly.presentation.feature.share.ShareContent
import com.kg.museumly.presentation.feature.share.capture.ShareCardHeight
import com.kg.museumly.presentation.feature.share.capture.ShareCardWidth
import com.kg.museumly.presentation.ui.theme.LibreCaslon

/** Builds what the share flow needs for [artwork]. Call it from a click handler. */
fun artworkShareContent(context: Context, artwork: Artwork): ShareContent =
    ShareContent(
        text = artworkShareText(
            artwork = artwork,
            appName = context.getString(R.string.share_card_app_name),
            landingUrl = context.getString(R.string.share_landing_url),
        ),
        card = { onImageLoaded -> ArtworkShareCard(artwork, onImageLoaded) },
    )

/** Plain-text share. The link is left out while no landing page is configured. */
internal fun artworkShareText(artwork: Artwork, appName: String, landingUrl: String): String =
    buildString {
        appendLine(artwork.title)
        captionLine(artwork.artist, artwork.year)?.let { appendLine(it) }
        appendLine(museumName(artwork.providerId))
        appendLine()
        append(appName)
        if (landingUrl.isNotBlank()) {
            appendLine()
            append(landingUrl)
        }
    }

private val CardTitleStyle = TextStyle(
    fontFamily = LibreCaslon,
    fontWeight = FontWeight.Normal,
    fontSize = 46.sp,
    lineHeight = 56.sp,
)

/**
 * The 9:16 card: the work on the wall, with its label underneath, like the feed. Fixed size
 * ([ShareCardWidth] x [ShareCardHeight]); the image takes whatever height the label leaves, so
 * a long title wraps instead of being cut off.
 */
@Composable
fun ArtworkShareCard(
    artwork: Artwork,
    onImageLoaded: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Two things differ from how the feed loads images, both because this is an export:
    // - crossfade(false): the app-wide loader fades images in. A capture taken right after
    //   "loaded" would otherwise record a half-transparent frame.
    // - allowHardware(false): software bitmaps always capture correctly.
    val request = remember(artwork.imageUrl) {
        ImageRequest.Builder(context)
            .data(artwork.imageUrl)
            .crossfade(false)
            .allowHardware(false)
            .build()
    }

    Column(
        modifier = modifier
            .size(ShareCardWidth, ShareCardHeight)
            .background(WallColor)
            .padding(horizontal = 56.dp, vertical = 72.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = artwork.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> onImageLoaded(true)
                        is AsyncImagePainter.State.Error -> onImageLoaded(false)
                        else -> Unit
                    }
                },
            )
        }

        Spacer(Modifier.height(48.dp))

        Text(
            text = artwork.title,
            color = Color.White,
            style = CardTitleStyle,
        )
        captionLine(artwork.artist, artwork.year)?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 28.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = museumName(artwork.providerId).uppercase(),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 20.sp,
                letterSpacing = 2.sp,
            )
            Text(
                text = "MUSEUMLY",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 20.sp,
                letterSpacing = 4.sp,
            )
        }
    }
}
