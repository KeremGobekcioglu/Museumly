package com.kg.museumly.data.local

import com.kg.museumly.data.local.detail.ArtworkDetailEntity
import com.kg.museumly.model.ArtworkDetail

object ArtworkDetailMapper {
    fun toDomain(entity: ArtworkDetailEntity): ArtworkDetail {
        return ArtworkDetail(
            medium = entity.medium,
            dimensions = entity.dimensions,
            creditLine = entity.creditLine,
            culture = entity.culture,
            period = entity.period,
        )
    }
}