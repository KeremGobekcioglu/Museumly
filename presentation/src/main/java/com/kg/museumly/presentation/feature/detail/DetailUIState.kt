package com.kg.museumly.presentation.feature.detail

import com.kg.museumly.domain.model.ArtworkWithDetail

data class DetailUiState(
    val data: ArtworkWithDetail? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val showInspectHint: Boolean = false,
    val isFavorite: Boolean = false
)