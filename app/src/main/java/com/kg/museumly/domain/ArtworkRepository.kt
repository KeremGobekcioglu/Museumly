package com.kg.museumly.domain

import com.kg.museumly.model.Artwork
import kotlinx.coroutines.flow.Flow

interface ArtworkRepository {

    fun artworks(): Flow<List<Artwork>>

    suspend fun byId( id: String) : Artwork?

    //suspend fun insert(items: List<Artwork>)

    suspend fun seedIfEmpty()
}