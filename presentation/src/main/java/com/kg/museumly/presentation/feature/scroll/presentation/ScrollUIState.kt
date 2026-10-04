package com.kg.museumly.presentation.feature.scroll.presentation

import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Section

data class ScrollUiState(
    val section: Section? = null,     // ← new
    val artworks: List<Artwork> = emptyList(),
    val initialPage: Int? = null,
    val tail: TailState = TailState.Loading,
    val isOnline: Boolean = true
)

sealed interface TailState {
    data object Idle : TailState
    data object Loading : TailState
    data class Failed(val message: String , val retrying: Boolean = false) : TailState
    data object Exhausted : TailState
}