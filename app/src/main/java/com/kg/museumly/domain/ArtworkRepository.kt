package com.kg.museumly.domain

import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkWithDetail
import kotlinx.coroutines.flow.Flow

interface ArtworkRepository {

    fun artworks(): Flow<List<Artwork>>
    suspend fun artworkWithDetail(id: String): ArtworkWithDetail?
    suspend fun byId( id: String) : Artwork?
    suspend fun loadMore(size: Int = 20) : LoadOutcome

    suspend fun count(): Int
}