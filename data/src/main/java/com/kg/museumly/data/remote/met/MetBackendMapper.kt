package com.kg.museumly.data.remote.met

import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.ArtworkDetail

/**
 * Backend item → domain. Never rejects: the backend serves only ACCEPTED
 * works, and the importer already stored blanks as null.
 */
object MetBackendMapper {

    fun toDomain(dto: MetArtworkItemDto): Artwork {
        return Artwork(
            // sourceId is the Met Object ID; the page cursor is a different number.
            id = "met:" + dto.sourceId,
            title = dto.title,
            artist = dto.artist,
            year = dto.objectDate,
            imageUrl = dto.imageSmallUrl,
            aspectRatio = aspectRatio(dto.imageWidth, dto.imageHeight),
            yearStart = dto.beginYear,
            classification = dto.classification,
            department = dto.department,
        )
    }

    fun toDetail(dto: MetArtworkItemDto): ArtworkDetail {
        return ArtworkDetail(
            medium = dto.medium,
            dimensions = dto.dimensions,
            creditLine = dto.creditLine,
            culture = dto.culture,
            period = dto.period,
            highResImageUrl = dto.imageUrl,
            artistBio = dto.artistBio,
            // The Met has no wall text. Left empty on purpose.
            description = null,
            didYouKnow = null,
        )
    }

    // The server stores both or neither, both positive (CHECK constraint).
    private fun aspectRatio(width: Int?, height: Int?): Float? {
        if (width == null || height == null) return null
        return width.toFloat() / height
    }
}