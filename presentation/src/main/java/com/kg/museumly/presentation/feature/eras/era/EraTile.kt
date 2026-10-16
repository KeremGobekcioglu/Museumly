package com.kg.museumly.presentation.feature.eras.era

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.presentation.feature.detail.ArtworkFrame

/** Box for a work whose ratio isn't known yet (a new Met work). Never resized. */
private const val UnknownRatioBox: Float = 3f / 4f

/**
 * One work on the era wall.
 *
 * Known ratio (Cleveland, or a Met work measured earlier): the tile takes
 * the work's own shape, framed.
 *
 * Unknown ratio: a fixed 3:4 box that never changes size, so nothing below
 * it ever moves. Inside it, the image shows bare and fitted until it
 * reports its size; then the frame replaces it at the true shape, centred,
 * exactly where the bare image was.
 *
 * The tile never saves what it learns. A save would re-emit the list with
 * the ratio filled in, and the tile would switch from the box to its own
 * shape: the jump this box exists to avoid.
 */
@Composable
internal fun EraTile(
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
{
    var measured: Float? by remember(artwork.id) {
        mutableStateOf(null)
    }
    val knownRatio : Float? = artwork.aspectRatio
    if(knownRatio != null)
    {
        ArtworkFrame(
            imageUrl = artwork.imageUrl,
            contentDescription = artwork.title,
            // Ignored on purpose: correcting a known shape would resize the tile.
            /**
             * width, height, _ ->
             *    ratio = width.toFloat() / height.toFloat()
             */
            onImageLoaded = {_,_,_ ->},
            /**
             * onImageFailed = {
             *      if (ratio == null) {
             *        ratio = 0.8f
             *         }
             *       }
             */
            onImageFailed = {

            },
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(knownRatio)
        )
    }
    else
    {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(UnknownRatioBox)
        )
        {
            val ratio: Float? = measured
            if(ratio != null)
            {
                // aspectRatio inside the box picks the largest rect of this
                // shape that fits: the same rect Fit gave the bare image.
                ArtworkFrame(
                    imageUrl = artwork.imageUrl,
                    contentDescription = artwork.title,
                    onImageLoaded = { _, _, _ -> },
                    onImageFailed = { },
                    onClick = onClick,
                    modifier = Modifier.aspectRatio(ratio)
                )
            }
            else
            {
                AsyncImage(
                    model = artwork.imageUrl,
                    contentDescription = artwork.title,
                    contentScale = ContentScale.Fit,
                    onSuccess = { state  ->
                        val width: Int = state.result.image.width
                        val height: Int = state.result.image.height
                        if (width > 0 && height > 0) {
                            measured = width.toFloat() / height.toFloat()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onClick)
                )
            }
        }
    }
}