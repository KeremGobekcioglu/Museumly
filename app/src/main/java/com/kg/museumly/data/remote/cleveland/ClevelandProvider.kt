package com.kg.museumly.data.remote.cleveland

import android.util.Log
import com.kg.museumly.domain.ApiResult
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.ErrorKind
import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.data.remote.toErrorKind
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

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
    private suspend fun fetchDtos(skip: Int, limit: Int) : ApiResult<List<ClevelandArtworkDto>>
    {
        return try {
            val response = api.searchArtworks(
                hasImage = 1,
                skip = skip,
                limit = limit,
                department = null,
                fields = null
            )
            ApiResult.Success(response.data)
        }
        catch (e: CancellationException)
        {
            throw e
        }
        catch (e: HttpException) {
            when {
                e.code() == 404 -> ApiResult.Rejected("404 for skip=$skip limit=$limit")
                e.code() in 500..599 || e.code() == 429 -> ApiResult.Failed(e)
                else -> ApiResult.Rejected("HTTP ${e.code()} for skip=$skip limit=$limit")
            }
        }
        catch (e: IOException) {
            ApiResult.Failed(e)
        } catch (e: Exception) {
            Log.d("CLEVELANDPROVIDER", "unexpected error for skip=$skip: ${e.message}")
            ApiResult.Failed(e)
        }
    }

    override suspend fun fetchPage(
        cursor: String?,
        size: Int
    ): PageResult {
        // we do get some items and skip them to not get again( move cursor)
        var skip : Int = parseCursor(cursor)
        val items: MutableList<Artwork> = ArrayList()
        val details: MutableList<ArtworkDetail> = ArrayList()
        // are we done, did we hit end
        var exhausted = false
        var failed = false
        var failureReason: String? = null
        var failureKind: ErrorKind = ErrorKind.UNKNOWN
        // it is not skip, because we dont know if we accept the data or not.
        while(items.size < size)
        {
            // first pass, need is 20. if 13 of items rejected, need will be 13.
            val need = size - items.size
            var outcome = fetchDtos(skip, need)
            if (outcome is ApiResult.Failed) {
                Log.d("CLEVELANDPROVIDER", "retrying skip=$skip after transient failure: ${outcome.cause.message}")
                outcome = fetchDtos(skip, need)
            }
            val dtos = when(outcome)
            {
                is ApiResult.Success -> outcome.value
                is ApiResult.Rejected -> {
                    Log.d("CLEVELANDPROVIDER", "giving up: ${outcome.reason}")
                    failed = true
                    failureReason = outcome.reason
                    failureKind = ErrorKind.UNKNOWN
                    null
                }
                is ApiResult.Failed -> {
                    Log.d("CLEVELANDPROVIDER", "giving up on skip=$skip after retry: ${outcome.cause.message}")
                    failed = true
                    failureReason = outcome.cause.message ?: "Cleveland request failed"
                    failureKind = outcome.toErrorKind()
                    null
                }
            }
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
            //items.addAll(ClevelandMapper.toArtworks(dtos))
            val pairs = ClevelandMapper.toArtworksWithDetail(dtos)
            for (pair in pairs) {
                items.add(pair.first)
                details.add(pair.second)
            }
        }

        var status = PageStatus.OK
        if (exhausted) {
            status = PageStatus.EXHAUSTED
        } else if (failed && items.isEmpty()) {
            status = PageStatus.FAILED
        }

        var next: String? = null
        if(!exhausted)
        {
            next = skip.toString()
        }
        return PageResult(items,details,next,status,failureReason,failureKind)
    }
}