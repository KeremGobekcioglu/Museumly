package com.kg.museumly.data.remote.met

import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Protects MetMapper's gatekeeping (public-domain + has-image filtering)
 * and the two Met-specific facts the rest of the app depends on: aspectRatio
 * is always null (the Met gives no pixel dimensions, so the UI must fall
 * back to containerRatio), and highResImageUrl comes from primaryImage.
 */
class MetMapperTest {

    private fun dto(
        objectID: Int = 1001,
        title: String = "Water Lilies",
        artistDisplayName: String? = "Claude Monet",
        isPublicDomain: Boolean = true,
        primaryImageSmall: String? = "https://images.metmuseum.org/1001-small.jpg",
        primaryImage: String? = "https://images.metmuseum.org/1001-full.jpg",
    ): MetObjectDto {
        return MetObjectDto(
            objectID = objectID,
            title = title,
            artistDisplayName = artistDisplayName,
            isPublicDomain = isPublicDomain,
            primaryImageSmall = primaryImageSmall,
            primaryImage = primaryImage,
        )
    }

    @Test
    fun `non public domain records are rejected`() {
        val artwork: Artwork? = MetMapper.toDomain(dto(isPublicDomain = false))

        assertNull(artwork)
    }

    @Test
    fun `missing primary image small is rejected`() {
        val artwork: Artwork? = MetMapper.toDomain(dto(primaryImageSmall = null))

        assertNull(artwork)
    }

    @Test
    fun `blank primary image small is rejected`() {
        val artwork: Artwork? = MetMapper.toDomain(dto(primaryImageSmall = "   "))

        assertNull(artwork)
    }

    @Test
    fun `aspect ratio is always null`() {
        // The Met's API never returns pixel dimensions for the list
        // thumbnail — see README.md "aspectRatio is always null" — so this
        // must stay hardcoded null, not silently start returning a guess.
        val artwork: Artwork? = MetMapper.toDomain(dto())

        assertNull(artwork?.aspectRatio)
    }

    @Test
    fun `blank title falls back to Untitled`() {
        val artwork: Artwork? = MetMapper.toDomain(dto(title = ""))

        assertEquals("Untitled", artwork?.title)
    }

    @Test
    fun `blank artist display name maps to null not an empty string`() {
        // README's open questions flag this exact case: a blank (not null)
        // artistDisplayName would otherwise render as " - 1550" in the caption.
        val artwork: Artwork? = MetMapper.toDomain(dto(artistDisplayName = "  "))

        assertNull(artwork?.artist)
    }

    @Test
    fun `detail high res image url is primaryImage`() {
        val detail: ArtworkDetail = MetMapper.toDetail(dto(primaryImage = "https://images.metmuseum.org/1001-full.jpg"))

        assertEquals("https://images.metmuseum.org/1001-full.jpg", detail.highResImageUrl)
    }

    @Test
    fun `blank primaryImage maps to null high res url`() {
        val detail: ArtworkDetail = MetMapper.toDetail(dto(primaryImage = ""))

        assertNull(detail.highResImageUrl)
    }
}
