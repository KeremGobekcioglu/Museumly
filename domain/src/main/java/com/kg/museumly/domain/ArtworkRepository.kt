package com.kg.museumly.domain

import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.ArtworkWithDetail
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.domain.model.TimelineStop
import kotlinx.coroutines.flow.Flow

interface ArtworkRepository {

    /**
     * artworks should be served section by section.
     * era == null is the feed; an era is its own list with its own order.
     */
    fun artworks(section: Section, era: Era? = null): Flow<List<Artwork>>
    suspend fun artworkWithDetail(id: String): ArtworkWithDetail?
    suspend fun byId( id: String) : Artwork?

    /**
     * LoadMore takes section becasue it needs to know which
     * department artwork it ll load.
     * With an era, it loads into that era's list, using that era's cursors
     * and turn. The feed's are never touched.
     */
    suspend fun loadMore(section: Section, size: Int = 20, era: Era? = null) : LoadOutcome

    /**
     * now count tracks section by section, and era by era.
     */
    suspend fun count(section: Section, era: Era? = null): Int

    /**
     * name implies.
     */
    fun getFavorites(section: Section?) : Flow<List<Artwork>>

    fun getFavoritesIds() : Flow<Set<String>>

    suspend fun setFavorite(id: String, isFavorite: Boolean)

    suspend fun getFavoriteById(id: String) : Artwork?

    suspend fun favoritesCount() : Int

    suspend fun rememberAspectRatio(id: String, ratio: Float)

    /** The works the timeline slides through, oldest first. Empty until loaded once. */
    fun timelineStops(): Flow<List<TimelineStop>>

    /** Fetches the timeline's works once. Does nothing if they are already stored. */
    suspend fun loadTimeline(): LoadOutcome
}