package com.kg.museumly.presentation.feature.eras

import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.domain.model.TimelineStop

data class ErasUiState(
    val stops: List<TimelineStop> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

sealed interface ErasIntent {
    data class StopChanged(val index: Int) : ErasIntent
    data class ArtworkClicked(val artworkId: String) : ErasIntent
    data class MoreClicked(val index: Int) : ErasIntent
    data object RetryClicked : ErasIntent
}

sealed interface ErasEffect {
    data class OpenDetail(val artworkId: String) : ErasEffect
    data class OpenEra(val section: Section, val era: Era) : ErasEffect
}