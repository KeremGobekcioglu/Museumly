package com.kg.museumly.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.presentation.R
import com.kg.museumly.presentation.feature.share.SheetColor
import com.kg.museumly.presentation.feature.share.ShareBottomSheetContent
import com.kg.museumly.presentation.feature.share.capture.ScaledShareCard
import com.kg.museumly.presentation.feature.share.card.ArtworkShareCard
import com.kg.museumly.presentation.feature.share.rememberShareOptions

/**
 * The three orientations the card has to survive (wide, tall, roughly portrait), then the
 * worst case for the label and the case with no maker line. Local drawables, not network,
 * so these render without a device.
 *
 * The card is a fixed 675 x 1200dp, so the full-size previews use that as their canvas.
 * fontScale stays 1f, as in the export, so what shows here is what gets shared.
 */
private fun shareArtwork(
    id: String,
    title: String,
    imageUrl: String,
    aspectRatio: Float,
    artist: String? = "Preview Artist",
    year: String? = "1900",
) = Artwork(
    id = id,
    title = title,
    artist = artist,
    year = year,
    imageUrl = imageUrl,
    aspectRatio = aspectRatio,
)

private val WideWork = shareArtwork(
    id = "met:wide",
    title = "The Harvesters",
    imageUrl = WIDE_URL,
    aspectRatio = 1.36f,
    artist = "Pieter Bruegel the Elder",
    year = "1565",
)

private val TallWork = shareArtwork(
    id = "met:tall",
    title = "Quail and Millet",
    imageUrl = TALL_URL,
    aspectRatio = 0.48f,
)

private val PortraitWork = shareArtwork(
    id = "cleveland:portrait",
    title = "Girl with a Pearl Earring",
    imageUrl = PORTRAIT_URL,
    aspectRatio = 0.88f,
    artist = "Johannes Vermeer",
    year = "1665",
)

// Same worst case as the feed previews: a real Cleveland title that was cut off on device,
// with a long artist, so the image has to give up the most height.
private val LongCaptionWork = shareArtwork(
    id = "cleveland:long",
    title = "Red-Figure Lekythos (Oil Vessel): Athena Slaying Giant (body); Satyr between Maenads (shoulder)",
    imageUrl = TALL_URL,
    aspectRatio = 0.48f,
    artist = "Attributed to the Berlin Painter, workshop of the Kleophrades Painter",
    year = "c. 490 BCE",
)

private val NoMakerWork = shareArtwork(
    id = "met:unknown",
    title = "Untitled",
    imageUrl = PORTRAIT_URL,
    aspectRatio = 0.88f,
    artist = null,
    year = null,
)

@Composable
private fun CardPreview(artwork: Artwork) = WithPreviewImages {
    ArtworkShareCard(artwork = artwork, onImageLoaded = {})
}

// --- The card at its real size, one per case ---

@Preview(name = "Card · Wide", widthDp = 675, heightDp = 1200)
@Composable
private fun ShareCardWidePreview() = CardPreview(WideWork)

@Preview(name = "Card · Tall", widthDp = 675, heightDp = 1200)
@Composable
private fun ShareCardTallPreview() = CardPreview(TallWork)

@Preview(name = "Card · Portrait", widthDp = 675, heightDp = 1200)
@Composable
private fun ShareCardPortraitPreview() = CardPreview(PortraitWork)

@Preview(name = "Card · Long caption", widthDp = 675, heightDp = 1200)
@Composable
private fun ShareCardLongCaptionPreview() = CardPreview(LongCaptionWork)

@Preview(name = "Card · No artist or year", widthDp = 675, heightDp = 1200)
@Composable
private fun ShareCardNoMakerPreview() = CardPreview(NoMakerWork)

// --- The card as the sheet shows it: scaled down to 480dp tall ---

@Preview(name = "Card · Scaled, as in the sheet", showBackground = true, backgroundColor = 0xFF181715)
@Composable
private fun ShareCardScaledPreview() = WithPreviewImages {
    ScaledShareCard { ArtworkShareCard(artwork = PortraitWork, onImageLoaded = {}) }
}

// --- The sheet's body (ModalBottomSheet itself can't render in a static preview) ---

@Composable
private fun SheetPreview(
    enabled: Boolean = true,
    statusMessage: String? = null,
    isError: Boolean = false,
    showWallpaper: Boolean = true,
) = WithPreviewImages {
    Box(Modifier.fillMaxWidth().background(SheetColor)) {
        ShareBottomSheetContent(
            preview = { ScaledShareCard { ArtworkShareCard(artwork = PortraitWork, onImageLoaded = {}) } },
            enabled = enabled,
            statusMessage = statusMessage,
            isError = isError,
            options = rememberShareOptions(),
            onOptionClick = {},
            onShareTextClick = {},
            onSaveImageClick = {},
            showWallpaper = showWallpaper,
            wallpaperEnabled = enabled,
            onSetWallpaperClick = {},
            onDismiss = {},
        )
    }
}

private const val SheetDevice = "spec:width=411dp,height=891dp"

@Preview(name = "Sheet · Ready", device = SheetDevice)
@Composable
private fun ShareSheetReadyPreview() = SheetPreview()

@Preview(name = "Sheet · Preparing", device = SheetDevice)
@Composable
private fun ShareSheetPreparingPreview() = SheetPreview(
    enabled = false,
    statusMessage = stringResource(R.string.share_preparing),
)

@Preview(name = "Sheet · Image failed", device = SheetDevice)
@Composable
private fun ShareSheetImageFailedPreview() = SheetPreview(
    enabled = false,
    statusMessage = stringResource(R.string.share_error_image),
    isError = true,
)

@Preview(name = "Sheet · Share error", device = SheetDevice)
@Composable
private fun ShareSheetErrorPreview() = SheetPreview(
    statusMessage = stringResource(R.string.share_error_generic),
    isError = true,
)
