package com.kg.museumly.data.remote.cleveland

import android.util.Log
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.PageResult
import com.kg.museumly.model.Artwork
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClevelandProvider @Inject constructor(
    private val api: ClevelandApi
) : ArtworkProvider
{
    override val id: String = "cleveland"
    private fun parseCursor(cursor: String?) : Int
    {
        if(cursor == null)
            return 0
        val parsed = cursor.toIntOrNull() ?: return 0
        return parsed
    }
    private suspend fun fetchDtos(skip: Int, limit: Int) : List<ClevelandArtworkDto>?
    {
        return try {
            val response = api.searchArtworks(
                hasImage = 1,
                skip = skip,
                limit = limit,
                department = null,
                fields = null
            )
            response.data
        }
        catch (e: Exception)
        {
            Log.d("CLEVELANDPROVIDER","EXCEPTION FETCH DTOS = ${e.message}")
            null
        }
    }

    override suspend fun fetchPage(
        cursor: String?,
        size: Int
    ): PageResult {
        // we do get some items and skip them to not get again( move cursor)
        var skip : Int = parseCursor(cursor)
        val items: MutableList<Artwork> = ArrayList()
        // are we done, did we hit end
        var exhausted = false
        // it is not skip, because we dont know if we accept the data or not.
        while(items.size < size)
        {
            // first pass, need is 20. if 13 of items rejected, need will be 13.
            val need = size - items.size
            val dtos = fetchDtos(skip,need)
            // call failed.
            if (dtos == null) {
                break
            }
            // we got the end.
            if (dtos.isEmpty()) {
                exhausted = true
                break
            }
            // we got dtos.size elements , so we move.
            /**
             * skip tracks consumption, items tracks acceptance.
             */
            skip+=dtos.size
            items.addAll(ClevelandMapper.toArtworks(dtos))
        }
        var next: String? = null
        if(!exhausted)
        {
            next = skip.toString()
        }
        return PageResult(items,next)
    }
}