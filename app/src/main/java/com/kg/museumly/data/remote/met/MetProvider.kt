package com.kg.museumly.data.remote.met

import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.PageResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * right now we can only get paintings if it is our query.
 */
@Singleton
class MetProvider @Inject constructor(
    private val api: MetApi
): ArtworkProvider {

    override val id = "met"
    private var cachedIds: List<Int>? = null
    private val idsMutex = Mutex()

    private suspend fun loadIds(): List<Int>
    {
        idsMutex.withLock {
            var ids = cachedIds
            if(ids == null)
            {
                val response = api.search("paintings")
            }
        }
    }

    override suspend fun fetchPage(
        cursor: String?,
        size: Int
    ): PageResult {
        TODO("Not yet implemented")
    }

}