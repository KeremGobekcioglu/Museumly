package com.kg.museumly.presentation.feature.favorites

import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.ArtworkWithDetail
import com.kg.museumly.domain.model.Section

data class FavoritesUIState(
    val artworks: List<Artwork> = emptyList(),
    val isLoading: Boolean = true,
    val section: Section? = null,
    val newestFirst: Boolean = true
)

sealed interface FavoritesIntent
{
    data object SortToggled: FavoritesIntent
    data class ArtworkClicked(val artworkId: String) : FavoritesIntent
    data class SectionPicked(val section: Section? = null) : FavoritesIntent
}

sealed interface FavoritesEffect
{
    data class OpenDetail(val artworkId: String) : FavoritesEffect
}