// Studio's in-editor lint flags the plugin-generated serializer of every
// @Serializable class in this library module. Gradle lint and the compiler don't.
@file:Suppress("UnsafeOptInUsageError")

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