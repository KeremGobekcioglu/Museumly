package com.kg.museumly.presentation.feature.eras.era

import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.presentation.feature.scroll.presentation.TailState

data class EraUiState(
    val era: Era,
    // Era.sectionsFor(era): what the picker lists, besides "All".
    val sections: List<Section>,
    // null = All, matching getFavorites(null).
    val selected: Section?,
    // The list for `selected`: one section's list, or the All walk.
    val artworks: List<Artwork> = emptyList(),
    // Restored, clamped position. null until known; the grid waits for it.
    val initialIndex: Int? = null,
    val tail: TailState = TailState.Loading,
    val isOnline: Boolean = true,
)

sealed interface EraIntent
{
    // null = All.
    data class SectionPicked(val section: Section?) : EraIntent
    // From the grid: frontier uses firstVisible, the load trigger uses lastVisible.
    data class Scrolled(val firstVisible: Int, val lastVisible: Int) : EraIntent
    data class ArtworkClicked(val artworkId: String) : EraIntent
    data object RetryClicked : EraIntent
    data object BackClicked : EraIntent
}

sealed interface EraEffect
{
    data class OpenDetail(val artworkId: String) : EraEffect
    data object Back : EraEffect
}