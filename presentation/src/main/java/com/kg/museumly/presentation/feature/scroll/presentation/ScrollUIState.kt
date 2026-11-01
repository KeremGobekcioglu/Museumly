package com.kg.museumly.presentation.feature.scroll.presentation

import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Section

data class ScrollUiState(
    val section: Section? = null,     // ← new
    val artworks: List<Artwork> = emptyList(),
    val initialPage: Int? = null,
    val tail: TailState = TailState.Loading,
    val isOnline: Boolean = true,
    val favoriteIds: Set<String> = emptySet(),
    val showSignInSheet: Boolean = false,
    // REVERT-HASLOADED (1/3): delete this field, with the other two markers, to undo the loader-flash fix.
    // False only for the ViewModel's initial value: nothing has been read yet.
    // Without it, "not read yet" looks the same as "empty and loading".
    val hasLoaded: Boolean = true
)

sealed interface TailState {
    data object Idle : TailState
    data object Loading : TailState
    data class Failed(val message: String , val retrying: Boolean = false) : TailState
    data object Exhausted : TailState
}