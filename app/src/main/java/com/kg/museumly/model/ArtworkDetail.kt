package com.kg.museumly.model

// domain/ArtworkDetail.kt
data class ArtworkDetail(
    val medium: String?,
    val dimensions: String?,
    val creditLine: String?,
    val culture: String?,
    val period: String?,
)

// domain/ArtworkWithDetail.kt
data class ArtworkWithDetail(
    val artwork: Artwork,
    val detail: ArtworkDetail,
)