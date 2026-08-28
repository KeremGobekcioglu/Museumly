package com.kg.museumly.model

/**
 * even though we do have different dtos and apis, our model will be main.
 */
data class Artwork(
    val id: String,
    val title: String,
    val artist: String?,
    val year: String?,
    val imageUrl: String,
    val aspectRatio: Float?,
    val yearStart: Int? = null,
    val classification: String? = null,
    val department: String? = null
) {
    val providerId: String
        get() = id.substringBefore(':')
}