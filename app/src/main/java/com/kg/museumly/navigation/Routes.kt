package com.kg.museumly.navigation

import kotlinx.serialization.Serializable

@Serializable
object ScrollPage

@Serializable
data class DetailPage(val artworkId: String)