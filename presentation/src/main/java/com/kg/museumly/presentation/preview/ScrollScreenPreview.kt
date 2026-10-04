package com.kg.museumly.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.presentation.feature.scroll.presentation.ArtworkReelsScreen
import com.kg.museumly.presentation.feature.scroll.presentation.ScrollUiState
import com.kg.museumly.presentation.feature.scroll.presentation.TailState
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Section

/**
 * The same three orientations as the detail previews, as a feed. The
 * aspect-ratio page sizes the image slot before decode, so these show
 * whether wide and tall works still leave room for the caption and pill.
 */
private val previewArtworks = listOf(
    Artwork(
        id = "met:wide",
        title = "The Harvesters",
        artist = "Pieter Bruegel the Elder",
        year = "1565",
        imageUrl = WIDE_URL,
        aspectRatio = 1.36f,
        department = "European Paintings",
    ),
    Artwork(
        id = "met:tall",
        title = "Quail and Millet",
        artist = "Preview Artist",
        year = "1800",
        imageUrl = TALL_URL,
        aspectRatio = 0.48f,
        department = "Asian Art",
    ),
    Artwork(
        id = "cleveland:portrait",
        title = "Girl with a Pearl Earring",
        artist = "Johannes Vermeer",
        year = "1665",
        imageUrl = PORTRAIT_URL,
        aspectRatio = 0.88f,
        department = "European Painting and Sculpture",
    ),
    // Worst case for the caption. The title is a real Cleveland work (it was
    // cut off on device); the artist and department are long on purpose, so
    // every caption line is stressed at once.
    Artwork(
        id = "cleveland:long",
        title = "Red-Figure Lekythos (Oil Vessel): Athena Slaying Giant (body); Satyr between Maenads (shoulder)",
        artist = "Attributed to the Berlin Painter, workshop of the Kleophrades Painter",
        year = "c. 490 BCE",
        imageUrl = TALL_URL,
        aspectRatio = 0.48f,
        department = "Arts of Africa, Oceania, and the Americas",
    ),
)

private const val PREVIEW_ERROR = "UnknownHostException: collectionapi.metmuseum.org"

@Composable
private fun ReelsPreview(state: ScrollUiState) = WithPreviewImages {
    ArtworkReelsScreen(
        state = state,
        refresh = {},
        onPageChanged = {},
        onDetailPage = {},
        onSectionSelected = {},
    )
}

private fun pagerState(page: Int, tail: TailState = TailState.Idle) = ScrollUiState(
    section = Section.EUROPEAN,
    artworks = previewArtworks,
    initialPage = page,
    tail = tail,
)

// --- Pager: one per orientation ---

@Preview(name = "Pager · Wide", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ReelsWidePreview() = ReelsPreview(pagerState(page = 0))

@Preview(name = "Pager · Tall", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ReelsTallPreview() = ReelsPreview(pagerState(page = 1))

@Preview(name = "Pager · Portrait", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ReelsPortraitPreview() = ReelsPreview(pagerState(page = 2))

@Preview(name = "Pager · Long caption", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ReelsLongCaptionPreview() = ReelsPreview(pagerState(page = 3))

// The same page on a small phone, where every line wraps sooner.
@Preview(name = "Pager · Long caption, small phone", showBackground = true, device = "spec:width=360dp,height=740dp")
@Composable
private fun ReelsLongCaptionSmallPreview() = ReelsPreview(pagerState(page = 3))

// --- Tail page: the slot after the last artwork ---

@Preview(name = "Tail · Loading", showBackground = true)
@Composable
private fun ReelsTailLoadingPreview() =
    ReelsPreview(pagerState(page = previewArtworks.size, tail = TailState.Loading))

@Preview(name = "Tail · Failed", showBackground = true)
@Composable
private fun ReelsTailFailedPreview() =
    ReelsPreview(pagerState(page = previewArtworks.size, tail = TailState.Failed(PREVIEW_ERROR)))

@Preview(name = "Tail · Exhausted", showBackground = true)
@Composable
private fun ReelsTailExhaustedPreview() =
    ReelsPreview(pagerState(page = previewArtworks.size, tail = TailState.Exhausted))

// --- Whole-screen states: nothing in Room yet ---

@Preview(name = "Loading", showBackground = true)
@Composable
private fun ReelsLoadingPreview() = ReelsPreview(ScrollUiState())

@Preview(name = "Failed · Online", showBackground = true)
@Composable
private fun ReelsFailedOnlinePreview() = ReelsPreview(
    ScrollUiState(section = Section.EUROPEAN, tail = TailState.Failed(PREVIEW_ERROR)),
)

@Preview(name = "Failed · Offline, retrying", showBackground = true)
@Composable
private fun ReelsFailedOfflinePreview() = ReelsPreview(
    ScrollUiState(
        section = Section.EUROPEAN,
        tail = TailState.Failed(PREVIEW_ERROR, retrying = true),
        isOnline = false,
    ),
)

@Preview(name = "Empty", showBackground = true)
@Composable
private fun ReelsEmptyPreview() = ReelsPreview(
    ScrollUiState(section = Section.EUROPEAN, tail = TailState.Exhausted),
)
