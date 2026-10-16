package com.kg.museumly.presentation.feature.eras

import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.domain.model.TimelineStop

data class ErasUiState(
    val stops: List<TimelineStop> = emptyList(),
    // False until Room's first emission. Until then an empty stops list
    // means "not read yet", not "nothing cached".
    val stopsReady: Boolean = false,
    val isLoading: Boolean = true,
    // Kept through a retry, so the failure placard stays up with its spinner.
    val errorMessage: String? = null,
    val isRetrying: Boolean = false,
    val isOnline: Boolean = true
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