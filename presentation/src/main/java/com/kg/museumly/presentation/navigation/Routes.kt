package com.kg.museumly.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
object ScrollPage

// artworkId is read by name in DetailViewModel via SavedStateHandle — keep
// in sync. DetailViewModelTest fails if they drift.
@Serializable
data class DetailPage(val artworkId: String)