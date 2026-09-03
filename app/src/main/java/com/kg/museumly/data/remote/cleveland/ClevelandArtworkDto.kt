package com.kg.museumly.data.remote.cleveland


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClevelandArtworkDto(
    val id: Int? = null,
    val title: String? = null,
    val creators: List<ClevelandCreatorDto> = emptyList(),
    @SerialName("creation_date") val creationDate: String? = null,
    @SerialName("creation_date_earliest") val creationDateEarliest: Int? = null,
    val department: String? = null,
    val type: String? = null,
    val images: ClevelandImagesDto? = null,
    @SerialName("share_license_status") val shareLicenseStatus: String? = null,
    val tombstone: String? = null,
    val description: String? = null,
    @SerialName("did_you_know") val didYouKnow: String? = null,
    val technique: String? = null,
    val measurements: String? = null,
    val culture: List<String> = emptyList(),
    val url: String? = null,
    @SerialName("creditline") val creditline: String? = null,
)

@Serializable
data class ClevelandCreatorDto(
    val description: String? = null,
    val role: String? = null
)

@Serializable
data class ClevelandImagesDto(
    val web: ClevelandImageDto? = null
)

@Serializable
data class ClevelandImageDto(
    val url: String? = null,
    val width: String? = null,
    val height: String? = null
)