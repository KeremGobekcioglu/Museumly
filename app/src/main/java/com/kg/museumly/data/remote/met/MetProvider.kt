package com.kg.museumly.data.remote.met

import android.util.Log
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.PageResult
import com.kg.museumly.model.Artwork
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

    /**
     * Cursor is a plain index into the ID list. Null means start at 0,
     * and anything unparseable falls back to 0 too — worst case the user
     * sees a few artworks again.
     */
    private fun parseCursor(cursor: String?) : Int
    {
        if(cursor == null)
            return 0
        val parsed = cursor.toIntOrNull() ?: return 0
        return parsed
    }
    /**
     * One ID in, one artwork or null out.
     *
     * Returns null for: a network failure, a 404, a parse error, or a
     * record the mapper rejects (not public domain, no image).
     * A single bad artwork must never kill a whole page.
     */
    private suspend fun fetchArtwork(objectId: Int): Artwork?
    {
        return try {
            val dto = api.getObject(objectId)
            /**
             * toDomain eliminates poor candidates. check the code.
             *
             */
            MetMapper.toDomain(dto)
        }
        catch (e: Exception)
        {
            Log.d("MET PROVIDER" , "FETCH ARTWORK ERROR = ${e.message}")
            null
        }
    }
    private suspend fun loadIds(): List<Int> {
        idsMutex.withLock {
            val existing: List<Int>? = cachedIds
            if (existing != null) {
                return existing
            }

            try {
                // european paintings
                val response = api.search(departmentId = 11)
                val fetched: List<Int>? = response.objectIDs
                if (fetched == null) {
                    Log.d("METPROVIDER", "load ids = ids == null.")
                    return emptyList()
                }
                Log.d("METPROVIDER", "total=${response.total}, ids=${fetched.size}")
                cachedIds = fetched
                return fetched
            } catch (e: Exception) {
                Log.d("METPROVIDER", "LOAD IDS THROW = ${e.message}")
                return emptyList()
            }
        }
    }

    override suspend fun fetchPage(
        cursor: String?,
        size: Int
    ): PageResult {
        Log.d("METPROVIDER", "fetchPage cursor=$cursor")
        val allIds = loadIds()
        Log.d("METPROVIDER", "allIds size=${allIds.size}")
        var i = parseCursor(cursor)
        val items : MutableList<Artwork> = ArrayList()

        // Walk IDs until we have `size` good ones or run out.
        // Rejected records are skipped permanently — they'd fail
        // identically next time.
        while(i < allIds.size && items.size < size)
        {
            val artwork = fetchArtwork(allIds[i])
            if(artwork != null)
            {
                items.add(artwork)
            }
            i++
        }

        var next: String? = null
        if(i < allIds.size)
        {
            next = i.toString()
        }
        return PageResult(items, next)
    }

}