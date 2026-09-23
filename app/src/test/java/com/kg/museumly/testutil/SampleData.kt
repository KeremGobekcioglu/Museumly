package com.kg.museumly.testutil

import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail

fun sampleArtwork(id: String, title: String = "Sample $id"): Artwork {
    return Artwork(
        id = id,
        title = title,
        artist = "Test Artist",
        year = "1900",
        imageUrl = "https://example.org/$id.jpg",
        aspectRatio = 1f,
    )
}

fun sampleDetail(): ArtworkDetail {
    return ArtworkDetail(
        medium = null,
        dimensions = null,
        creditLine = null,
        culture = null,
        period = null,
        highResImageUrl = null,
        artistBio = null,
        description = null,
        didYouKnow = null,
    )
}
