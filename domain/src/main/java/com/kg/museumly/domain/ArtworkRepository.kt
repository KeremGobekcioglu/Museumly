package com.kg.museumly.domain

import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.ArtworkWithDetail
import com.kg.museumly.domain.model.Section
import kotlinx.coroutines.flow.Flow

interface ArtworkRepository {

    /**
     * artworks should be served section by section.
     */
    fun artworks(section: Section): Flow<List<Artwork>>
    suspend fun artworkWithDetail(id: String): ArtworkWithDetail?
    suspend fun byId( id: String) : Artwork?

    /**
     * LoadMore takes section becasue it needs to know which
     * department artwork it ll load.
     */
    suspend fun loadMore(section: Section, size: Int = 20) : LoadOutcome

    /**
     * now count tracks section by section.
     */
    suspend fun count(section: Section): Int

    /**
     * name implies.
     */
    fun getFavorites(section: Section?) : Flow<List<Artwork>>

    fun getFavoritesIds() : Flow<Set<String>>

    suspend fun setFavorite(id: String, isFavorite: Boolean)

    suspend fun getFavoriteById(id: String) : Artwork?

    suspend fun favoritesCount() : Int

    suspend fun rememberAspectRatio(id: String, ratio: Float)
}