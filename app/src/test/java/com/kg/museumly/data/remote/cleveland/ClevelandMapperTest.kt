package com.kg.museumly.data.remote.cleveland

import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Protects ClevelandMapper's two jobs: turning quoted-string image
 * dimensions into a safe aspect ratio (never Infinity/NaN), and acting as
 * the CC0/has-image gatekeeper described in README.md's "Mapper as
 * gatekeeper" section. Every rejection here must return null silently,
 * never throw — a bad record must not kill a whole page.
 */
class ClevelandMapperTest {

    private fun dto(
        id: Int? = 3001,
        creators: List<ClevelandCreatorDto> = emptyList(),
        images: ClevelandImagesDto? = ClevelandImagesDto(
            web = ClevelandImageDto(url = "https://cdn.example.org/web.jpg", width = "956", height = "1200"),
            print = ClevelandImageDto(url = "https://cdn.example.org/print.jpg", width = "3000", height = "4000"),
        ),
        shareLicenseStatus: String? = "CC0",
        description: String? = null,
        didYouKnow: String? = null,
    ): ClevelandArtworkDto {
        return ClevelandArtworkDto(
            id = id,
            title = "Test Artwork",
            creators = creators,
            images = images,
            shareLicenseStatus = shareLicenseStatus,
            description = description,
            didYouKnow = didYouKnow,
        )
    }

    @Test
    fun `quoted width and height strings parse to a fraction`() {
        val web = ClevelandImageDto(url = "https://cdn.example.org/web.jpg", width = "956", height = "1200")

        val artwork: Artwork? = ClevelandMapper.toArtwork(dto(images = ClevelandImagesDto(web = web)))

        assertEquals(956f / 1200f, artwork?.aspectRatio)
    }

    @Test
    fun `zero height returns null instead of infinity`() {
        // Guards width / 0f, which is Float.POSITIVE_INFINITY with no
        // exception — README calls this out as a silent blank-page bug.
        val web = ClevelandImageDto(url = "https://cdn.example.org/web.jpg", width = "956", height = "0")

        val artwork: Artwork? = ClevelandMapper.toArtwork(dto(images = ClevelandImagesDto(web = web)))

        assertNull(artwork?.aspectRatio)
    }

    @Test
    fun `non numeric or missing height returns null`() {
        val nonNumeric = ClevelandImageDto(url = "https://cdn.example.org/web.jpg", width = "956", height = "n/a")
        val missing = ClevelandImageDto(url = "https://cdn.example.org/web.jpg", width = "956", height = null)

        val fromNonNumeric: Artwork? = ClevelandMapper.toArtwork(dto(images = ClevelandImagesDto(web = nonNumeric)))
        val fromMissing: Artwork? = ClevelandMapper.toArtwork(dto(images = ClevelandImagesDto(web = missing)))

        assertNull(fromNonNumeric?.aspectRatio)
        assertNull(fromMissing?.aspectRatio)
    }

    @Test
    fun `empty creators list maps to null artist without rejecting the record`() {
        val artwork: Artwork? = ClevelandMapper.toArtwork(dto(creators = emptyList()))

        assertEquals(null, artwork?.artist)
        assertEquals("cleveland:3001", artwork?.id)
    }

    @Test
    fun `creator description is trimmed at the first parenthesis`() {
        val creator = ClevelandCreatorDto(description = "Song Xu (Chinese, 1525-c. 1606)", role = "artist")

        val artwork: Artwork? = ClevelandMapper.toArtwork(dto(creators = listOf(creator)))

        assertEquals("Song Xu", artwork?.artist)
    }

    @Test
    fun `non cc0 license status rejects the record`() {
        val artwork: Artwork? = ClevelandMapper.toArtwork(dto(shareLicenseStatus = "In Copyright"))

        assertNull(artwork)
    }

    @Test
    fun `missing web image rejects the record`() {
        val artwork: Artwork? = ClevelandMapper.toArtwork(dto(images = ClevelandImagesDto(web = null)))

        assertNull(artwork)
    }

    @Test
    fun `null id rejects the record`() {
        val artwork: Artwork? = ClevelandMapper.toArtwork(dto(id = null))

        assertNull(artwork)
    }

    @Test
    fun `detail high res image url is the print url`() {
        // Cleveland's `full` size is a multi-hundred-MB TIFF Android can't
        // decode — the DTO doesn't even expose it (see ClevelandImagesDto),
        // so print is the only high-res option that exists.
        val detail: ArtworkDetail = ClevelandMapper.toDetail(dto())

        assertEquals("https://cdn.example.org/print.jpg", detail.highResImageUrl)
    }

    @Test
    fun `artist bio is the bracketed part of the creator description`() {
        val creator = ClevelandCreatorDto(description = "Vincent van Gogh (Dutch, 1853–1890)", role = "artist")

        val detail: ArtworkDetail = ClevelandMapper.toDetail(dto(creators = listOf(creator)))

        assertEquals("Dutch, 1853–1890", detail.artistBio)
    }

    @Test
    fun `creator description without brackets gives a null artist bio`() {
        val creator = ClevelandCreatorDto(description = "Unknown maker", role = "artist")

        val detail: ArtworkDetail = ClevelandMapper.toDetail(dto(creators = listOf(creator)))

        assertNull(detail.artistBio)
    }

    @Test
    fun `brackets with no name before them give a null artist bio`() {
        // Same rule cleanArtistName uses: a bracket at index 0 is not "name (bio)".
        val creator = ClevelandCreatorDto(description = "(Dutch, 1853–1890)", role = "artist")

        val detail: ArtworkDetail = ClevelandMapper.toDetail(dto(creators = listOf(creator)))

        assertNull(detail.artistBio)
    }

    @Test
    fun `description and did you know pass through the cleaner`() {
        val detail: ArtworkDetail = ClevelandMapper.toDetail(
            dto(
                description = "  Marks &amp; <em>stola</em>.  ",
                didYouKnow = "Similar armors are displayed nearby.",
            )
        )

        assertEquals("Marks & <em>stola</em>.", detail.description)
        assertNull(detail.didYouKnow)
    }
}
