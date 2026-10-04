package com.kg.museumly.data.remote.cleveland

import kotlinx.serialization.Serializable

@Serializable
data class ClevelandSearchResponse(
    val info: ClevelandInfo,
    val data: List<ClevelandArtworkDto>
)

@Serializable
data class ClevelandInfo(
    val total: Int
)

@Serializable
data class ClevelandArtworkResponse(
    val data: ClevelandArtworkDto
)