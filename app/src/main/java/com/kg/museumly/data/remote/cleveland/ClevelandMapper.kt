package com.kg.museumly.data.remote.cleveland

import com.kg.museumly.data.local.detail.ArtworkDetailEntity
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail


object ClevelandMapper {

    private const val PROVIDER_ID: String = "cleveland"
    private const val LICENSE_CC0: String = "CC0"
    private const val UNTITLED: String = "Untitled"

    fun toArtworks(dtos: List<ClevelandArtworkDto>): List<Artwork> {
        val artworks: MutableList<Artwork> = mutableListOf()
        for (dto in dtos) {
            val artwork: Artwork? = toArtwork(dto)
            if (artwork != null) {
                artworks.add(artwork)
            }
        }
        return artworks
    }

    fun toArtwork(dto: ClevelandArtworkDto): Artwork? {
        val id: Int = dto.id ?: return null

        val license: String? = dto.shareLicenseStatus
        if (license != null && license != LICENSE_CC0) {
            return null
        }

        val web: ClevelandImageDto = dto.images?.web ?: return null
        val imageUrl: String = web.url ?: return null
        if (imageUrl.isBlank()) {
            return null
        }

        val title: String = if (dto.title.isNullOrBlank()) UNTITLED else dto.title

        return Artwork(
            id = "$PROVIDER_ID:$id",
            title = title,
            artist = artistOf(dto.creators),
            year = dto.creationDate,
            imageUrl = imageUrl,
            aspectRatio = aspectRatioOf(web),
            yearStart = dto.creationDateEarliest,
            classification = dto.type,
            department = dto.department
        )
    }

    private fun artistOf(creators: List<ClevelandCreatorDto>): String? {
        val description: String = firstCreatorDescription(creators) ?: return null
        return cleanArtistName(description)
    }

    private fun firstCreatorDescription(creators: List<ClevelandCreatorDto>): String? {
        for (creator in creators) {
            val description: String? = creator.description
            if (!description.isNullOrBlank()) {
                return description
            }
        }
        return null
    }

    /**
     * cleveland api returns artist name and some year exp with it.
     */
    private fun cleanArtistName(description: String): String {
        val parenIndex: Int = description.indexOf('(')
        if (parenIndex <= 0) {
            return description.trim()
        }
        return description.substring(0, parenIndex).trim()
    }

    /**
     * The other half of cleanArtistName: "Vincent van Gogh (Dutch, 1853–1890)"
     * gives "Dutch, 1853–1890", the line a wall label prints under the name.
     * creators[].biography is a multi-page essay, not this — never use it.
     */
    private fun artistBioOf(creators: List<ClevelandCreatorDto>): String? {
        val description: String = firstCreatorDescription(creators) ?: return null
        val open: Int = description.indexOf('(')
        val close: Int = description.lastIndexOf(')')
        if (open <= 0 || close <= open) {
            return null
        }
        val inside: String = description.substring(open + 1, close)
        val decoded: String = ClevelandTextCleaner.decodeEntities(inside).trim()
        if (decoded.isEmpty()) {
            return null
        }
        return decoded
    }

    private fun aspectRatioOf(image: ClevelandImageDto): Float? {
        val widthText: String = image.width ?: return null
        val heightText: String = image.height ?: return null

        val width: Float = widthText.toFloatOrNull() ?: return null
        val height: Float = heightText.toFloatOrNull() ?: return null

        if (width <= 0f || height <= 0f) {
            return null
        }

        return width / height
    }

    // ClevelandMapper
    fun toDetail(dto: ClevelandArtworkDto): ArtworkDetail {
        val cultureJoined: String? = if (dto.culture.isEmpty()) null else dto.culture.joinToString(", ")
        return ArtworkDetail(
            medium = dto.technique,
            dimensions = dto.measurements,
            creditLine = dto.creditline,
            culture = cultureJoined,
            period = dto.creationDate,
            highResImageUrl = dto.images?.print?.url?.takeIf { it.isNotBlank() },
            artistBio = artistBioOf(dto.creators),
            description = ClevelandTextCleaner.wallText(dto.description),
            didYouKnow = ClevelandTextCleaner.wallText(dto.didYouKnow),
        )
    }

    // same null check with toArtworks.
    fun toArtworksWithDetail(dtos: List<ClevelandArtworkDto>): List<Pair<Artwork, ArtworkDetail>> {
        val results: MutableList<Pair<Artwork, ArtworkDetail>> = mutableListOf()
        for (dto in dtos) {
            val artwork: Artwork? = toArtwork(dto)
            if (artwork != null) {
                results.add(Pair(artwork, toDetail(dto)))
            }
        }
        return results
    }
}