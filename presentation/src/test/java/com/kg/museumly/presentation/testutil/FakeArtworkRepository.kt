package com.kg.museumly.presentation.testutil

import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.ArtworkWithDetail
import com.kg.museumly.domain.model.Section
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.milliseconds

/**
 * Hand-written ArtworkRepository test double for ScrollViewModelTest. Holds
 * real, inspectable state (a backing StateFlow per section, a call log)
 * instead of stubbed expectations. loadMoreDelayMs/loadMoreThrows let a test
 * put a real suspension point in loadMore() — needed to observe
 * TailState.Loading, which only exists for the duration of a suspended call.
 *
 * loadMoreSections records the section of every loadMore() that started,
 * in call order — including ones later cancelled mid-delay.
 */
class FakeArtworkRepository : ArtworkRepository {

    private val artworksFlows: MutableMap<Section, MutableStateFlow<List<Artwork>>> = HashMap()
    private val favoriteIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    var countValue: Int = 0
    var loadMoreOutcome: LoadOutcome = LoadOutcome.Loaded
    var loadMoreDelayMs: Long = 0
    var loadMoreThrows: Throwable? = null

    val loadMoreSections: MutableList<Section> = ArrayList()

    val loadMoreCallCount: Int
        get() = loadMoreSections.size

    private fun flowFor(section: Section): MutableStateFlow<List<Artwork>> {
        return artworksFlows.getOrPut(section) { MutableStateFlow(emptyList()) }
    }

    fun setArtworks(items: List<Artwork>, section: Section = Section.EUROPEAN) {
        flowFor(section).value = items
    }

    override fun artworks(section: Section): Flow<List<Artwork>> {
        return flowFor(section)
    }

    val artworkWithDetailIds: MutableList<String> = ArrayList()

    override suspend fun artworkWithDetail(id: String): ArtworkWithDetail? {
        artworkWithDetailIds.add(id)
        return null
    }

    override suspend fun byId(id: String): Artwork? {
        return null
    }

    override suspend fun loadMore(section: Section, size: Int): LoadOutcome {
        loadMoreSections.add(section)
        if (loadMoreDelayMs > 0) {
            delay(loadMoreDelayMs.milliseconds)
        }
        val throwable = loadMoreThrows
        if (throwable != null) {
            throw throwable
        }
        return loadMoreOutcome
    }

    override suspend fun count(section: Section): Int {
        return countValue
    }

    override fun getFavorites(section: Section?): Flow<List<Artwork>> {
        return flowOf(emptyList())
    }

    override fun getFavoritesIds(): Flow<Set<String>> {
        return favoriteIds
    }

    var setFavoriteDelayMs: Long = 0

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        delay(setFavoriteDelayMs.milliseconds)
        if (isFavorite) {
            favoriteIds.value = favoriteIds.value + id
        } else {
            favoriteIds.value = favoriteIds.value - id
        }
    }

    override suspend fun getFavoriteById(id: String): Artwork? {
        return null
    }

    override suspend fun favoritesCount(): Int {
        return 0
    }
}
