package com.kg.museumly.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.feature.detail.DetailScreen
import com.kg.museumly.feature.detail.DetailUiState
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import com.kg.museumly.model.ArtworkWithDetail

/**
 * The three orientations the fixed frame has to survive: wide, tall (a
 * hanging-scroll extreme), and roughly portrait — the case the frame's
 * fixed shape was actually designed around. Local drawables, not network,
 * so these render without a device.
 */
private fun previewArtwork(
    id: String,
    title: String,
    imageUrl: String,
    aspectRatio: Float,
): ArtworkWithDetail {
    val artwork = Artwork(
        id = id,
        title = title,
        artist = "Preview Artist",
        year = "1900",
        imageUrl = imageUrl,
        aspectRatio = aspectRatio,
    )
    val detail = ArtworkDetail(
        medium = null,
        dimensions = null,
        creditLine = null,
        culture = null,
        period = null,
        highResImageUrl = null,
        artistBio = null,
        description = null,
        didYouKnow = null,
    )
    return ArtworkWithDetail(artwork = artwork, detail = detail)
}

@Preview(name = "Wide", showBackground = true)
@Composable
private fun DetailScreenWidePreview() = WithPreviewImages {
    DetailScreen(
        state = DetailUiState(
            data = previewArtwork("preview:wide", "The Harvesters", WIDE_URL, aspectRatio = 1.36f),
            isLoading = false,
        ),
        onBack = {},
        onInspected = {},
    )
}

@Preview(name = "Tall", showBackground = true)
@Composable
private fun DetailScreenTallPreview() = WithPreviewImages {
    DetailScreen(
        state = DetailUiState(
            data = previewArtwork("preview:tall", "Quail and Millet", TALL_URL, aspectRatio = 0.48f),
            isLoading = false,
        ),
        onBack = {},
        onInspected = {},
    )
}

@Preview(name = "Portrait", showBackground = true)
@Composable
private fun DetailScreenPortraitPreview() = WithPreviewImages {
    DetailScreen(
        state = DetailUiState(
            data = previewArtwork("preview:portrait", "Girl with a Pearl Earring", PORTRAIT_URL, aspectRatio = 0.88f),
            isLoading = false,
        ),
        onBack = {},
        onInspected = {},
    )
}
