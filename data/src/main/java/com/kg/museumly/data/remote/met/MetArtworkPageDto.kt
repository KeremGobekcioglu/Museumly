package com.kg.museumly.data.remote.met


import kotlinx.serialization.Serializable

/**
 * Body of the backend's GET met/artworks. Fields the contract says are never
 * null have no default, so a broken contract fails decoding instead of
 * slipping through as a half-filled item.
 */
@Serializable
data class MetArtworkPageDto(
    val items: List<MetArtworkItemDto>,
    val nextCursor: String,
    val exhausted: Boolean,
)

@Serializable
data class MetArtworkItemDto(
    val sourceId: String,
    val title: String,
    val artist: String? = null,
    val artistBio: String? = null,
    val objectDate: String? = null,
    val beginYear: Int? = null,
    val classification: String? = null,
    val department: String? = null,
    val medium: String? = null,
    val dimensions: String? = null,
    val creditLine: String? = null,
    val culture: String? = null,
    val period: String? = null,
    val imageSmallUrl: String,
    val imageUrl: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
)
