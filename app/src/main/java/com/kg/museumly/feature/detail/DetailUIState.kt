package com.kg.museumly.feature.detail

import com.kg.museumly.model.ArtworkWithDetail

data class DetailUiState(
    val data: ArtworkWithDetail? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
)