package com.kg.museumly.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.presentation.feature.favorites.FavoritesScreen
import com.kg.museumly.presentation.feature.favorites.FavoritesUIState

/**
 * The grid switches column count with the favorite count (2 up to 6, then 3),
 * so these sit on both sides of that threshold. Tiles cycle through the three
 * orientations so the staggered columns end at uneven heights.
 */
private val orientations = listOf(
    WIDE_URL to 1.36f,
    TALL_URL to 0.48f,
    PORTRAIT_URL to 0.88f,
)

private fun favorites(count: Int): List<Artwork> = List(count) { index ->
    val (url, ratio) = orientations[index % orientations.size]
    Artwork(
        id = "preview:$index",
        title = "Favorite ${index + 1}",
        artist = "Preview Artist",
        year = "1800",
        imageUrl = url,
        aspectRatio = ratio,
        department = "European Paintings",
    )
}

@Composable
private fun FavoritesPreview(count: Int) = WithPreviewImages {
    FavoritesScreen(
        state = FavoritesUIState(artworks = favorites(count), isLoading = false),
        onIntent = {},
        onBack = {},
    )
}

@Preview(name = "Favorites · 3", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun Favorites3Preview() = FavoritesPreview(count = 3)

@Preview(name = "Favorites · 6", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun Favorites6Preview() = FavoritesPreview(count = 6)

@Preview(name = "Favorites · 9", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun Favorites9Preview() = FavoritesPreview(count = 9)

@Preview(name = "Favorites · 15", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun Favorites15Preview() = FavoritesPreview(count = 15)
