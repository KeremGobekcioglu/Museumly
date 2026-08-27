package com.kg.museumly.data.local

import com.kg.museumly.model.Artwork


object ArtworkMapper {

    fun toDomain(entity: ArtworkEntity): Artwork {
        return Artwork(
            id = entity.id,
            title = entity.title,
            artist = entity.artist,
            year = entity.year,
            imageUrl = entity.imageUrl,
            aspectRatio = entity.aspectRatio,
            yearStart = entity.yearStart
        )
    }

    fun toEntity(artwork: Artwork, position: Int): ArtworkEntity {
        return ArtworkEntity(
            id = artwork.id,
            providerId = artwork.providerId,
            title = artwork.title,
            artist = artwork.artist,
            year = artwork.year,
            yearStart = artwork.yearStart,
            imageUrl = artwork.imageUrl,
            aspectRatio = artwork.aspectRatio,
            position = position
        )
    }
}