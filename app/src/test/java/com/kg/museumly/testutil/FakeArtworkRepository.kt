package com.kg.museumly.testutil

import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkWithDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written ArtworkRepository test double for ScrollViewModelTest. Holds
 * real, inspectable state (a backing StateFlow, a call counter) instead of
 * stubbed expectations. loadMoreDelayMs/loadMoreThrows let a test put a real
 * suspension point in loadMore() — needed to observe TailState.Loading,
 * which only exists for the duration of a suspended call.
 */
class FakeArtworkRepository : ArtworkRepository {

    private val artworksFlow = MutableStateFlow<List<Artwork>>(emptyList())

    var countValue: Int = 0
    var loadMoreOutcome: LoadOutcome = LoadOutcome.Loaded
    var loadMoreDelayMs: Long = 0
    var loadMoreThrows: Throwable? = null

    var loadMoreCallCount: Int = 0
        private set

    fun setArtworks(items: List<Artwork>) {
        artworksFlow.value = items
    }

    override fun artworks(): Flow<List<Artwork>> {
        return artworksFlow
    }

    override suspend fun artworkWithDetail(id: String): ArtworkWithDetail? {
        return null
    }

    override suspend fun byId(id: String): Artwork? {
        return null
    }

    override suspend fun loadMore(size: Int): LoadOutcome {
        loadMoreCallCount += 1
        if (loadMoreDelayMs > 0) {
            delay(loadMoreDelayMs)
        }
        val throwable = loadMoreThrows
        if (throwable != null) {
            throw throwable
        }
        return loadMoreOutcome
    }

    override suspend fun count(): Int {
        return countValue
    }
}
