package com.kg.museumly.feature.scroll.presentation

import com.kg.museumly.model.Artwork

data class ScrollUiState(
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