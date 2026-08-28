package com.kg.museumly.data.remote.met

import com.kg.museumly.model.Artwork

object MetMapper
{
    fun toDomain(dto: MetObjectDto) : Artwork?
    {
        if(!dto.isPublicDomain)
            return null

        val imageUrl = dto.primaryImageSmall
        if(imageUrl.isNullOrBlank())
            return null

        val title = dto.title.ifBlank { "Untitled" }

        var artist: String? = dto.artistDisplayName
        if (artist != null && artist.isBlank()) {
            artist = null
        }

        var year: String? = dto.objectDate
        if (year != null && year.isBlank()) {
            year = null
        }
        var classification: String? = dto.classification
        if (classification != null && classification.isBlank()) {
            classification = null
        }
        var department: String? = dto.department
        if (department != null && department.isBlank()) {
            department = null
        }
        return Artwork(
            id = "met:" + dto.objectID,
            title = title,
            artist = artist,
            yearStart = dto.objectBeginDate,
            imageUrl = imageUrl,
            aspectRatio = null,
            classification = classification,
            year = year,
            department = department
        )
    }
}