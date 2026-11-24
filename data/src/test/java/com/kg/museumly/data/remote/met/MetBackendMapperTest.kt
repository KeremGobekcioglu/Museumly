package com.kg.museumly.data.remote.met

import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.ArtworkDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class MetBackendMapperTest {

    private fun item(width: Int? = null, height: Int? = null) = MetArtworkItemDto(
        sourceId = "189656",
        title = "Allegory of Smell",
        artist = "Joseph Marie Vien",
        artistBio = "French, Montpellier 1716–1809 Paris",
        objectDate = "1775–81",
        beginYear = 1775,
        classification = "Paintings",
        department = "European Paintings",
        medium = "Oil on paper",
        dimensions = "Overall: 12 1/2 × 8 3/4 in. (31.8 × 22.2 cm)",
        creditLine = "Gift of J. Pierpont Morgan, 1906",
        culture = null,
        period = null,
        imageSmallUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP-44694-001.jpg",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/original/DP-44694-001.jpg",
        imageWidth = width,
        imageHeight = height,
    )

    @Test
    fun toDomain_mapsEveryField() {
        val expected = Artwork(
            id = "met:189656",
            title = "Allegory of Smell",
            artist = "Joseph Marie Vien",
            year = "1775–81",
            imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP-44694-001.jpg",
            aspectRatio = null,
            yearStart = 1775,
            classification = "Paintings",
            department = "European Paintings",
        )
        assertEquals(expected, MetBackendMapper.toDomain(item()))
    }

    @Test
    fun toDomain_ratioIsWidthOverHeight() {
        assertEquals(0.75f, MetBackendMapper.toDomain(item(width = 300, height = 400)).aspectRatio)
    }

    @Test
    fun toDetail_mapsEveryField() {
        val expected = ArtworkDetail(
            medium = "Oil on paper",
            dimensions = "Overall: 12 1/2 × 8 3/4 in. (31.8 × 22.2 cm)",
            creditLine = "Gift of J. Pierpont Morgan, 1906",
            culture = null,
            period = null,
            highResImageUrl = "https://images.metmuseum.org/CRDImages/ep/original/DP-44694-001.jpg",
            artistBio = "French, Montpellier 1716–1809 Paris",
            description = null,
            didYouKnow = null,
        )
        assertEquals(expected, MetBackendMapper.toDetail(item()))
    }
}