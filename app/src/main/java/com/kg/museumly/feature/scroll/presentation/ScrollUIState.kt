package com.kg.museumly.feature.scroll.presentation

import com.kg.museumly.model.Artwork

data class ScrollUiState(
    val artworks: List<Artwork> = emptyList(),
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val initialPage: Int? = null,
    val isInitialLoad: Boolean = true,
)