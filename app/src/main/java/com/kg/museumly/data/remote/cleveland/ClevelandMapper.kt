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
        for (creator in creators) {
            val description: String? = creator.description
            if (!description.isNullOrBlank()) {
                return cleanArtistName(description)
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
        )
    }
}