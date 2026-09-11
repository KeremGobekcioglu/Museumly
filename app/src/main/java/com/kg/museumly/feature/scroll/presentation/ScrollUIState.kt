package com.kg.museumly.feature.scroll.presentation

import com.kg.museumly.domain.ErrorKind
import com.kg.museumly.model.Artwork

data class ScrollUiState(
    val artworks: List<Artwork> = emptyList(),
    val initialPage: Int? = null,
    val tail: TailState = TailState.Loading
)

sealed interface TailState {
    data object Idle : TailState
    object Loading : TailState
    data class Failed(val message: String, val kind: ErrorKind = ErrorKind.UNKNOWN) : TailState
    object Exhausted : TailState
}