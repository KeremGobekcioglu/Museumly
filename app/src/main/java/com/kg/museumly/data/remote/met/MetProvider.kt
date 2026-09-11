package com.kg.museumly.data.remote.met

import android.util.Log
import com.kg.museumly.domain.ApiResult
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

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
     * Consecutive Failed (transient) results, in a row, before we stop
     * skipping and treat it as an outage instead of one flaky object.
     */
    private val consecutiveFailureThreshold = 3

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
    private suspend fun fetchArtwork(objectId: Int): ApiResult<Pair<Artwork, ArtworkDetail>>
    {
        return try {
            val dto = api.getObject(objectId)
            /**
             * toDomain eliminates poor candidates. check the code.
             *
             */
            val artwork = MetMapper.toDomain(dto) ?:
                return ApiResult.Rejected("mapper rejected object $objectId")
            ApiResult.Success(Pair(artwork, MetMapper.toDetail(dto)))
        }
        catch (e: CancellationException)
        {
            throw e
        }
        catch (e: HttpException)
        {
            when {
                e.code() == 404 -> ApiResult.Rejected("404 for object $objectId")
                e.code() in 500..599 || e.code() == 429 -> ApiResult.Failed(e)
                else -> ApiResult.Rejected("HTTP ${e.code()} for object $objectId")
            }
        }
        catch (e: IOException) {
            ApiResult.Failed(e)
        } catch (e: Exception) {
            Log.d("MET PROVIDER", "unexpected error for $objectId: ${e.message}")
            ApiResult.Failed(e)
        }
    }
    private suspend fun loadIds(): ApiResult<List<Int>> {
        idsMutex.withLock {
            val existing: List<Int>? = cachedIds
            if (existing != null) {
                return ApiResult.Success(existing)
            }

            return try {
                // european paintings
                val response = api.search(departmentId = 11)
                val fetched: List<Int>? = response.objectIDs
                if (fetched == null) {
                    if (response.total == 0) {
                        // The Met returns objectIDs: null, not [], when nothing
                        // matches. That's a real empty result, not a failure —
                        // treat it as exhausted, not FAILED-forever.
                        Log.d("METPROVIDER", "no objects match (total=0)")
                        cachedIds = emptyList()
                        return ApiResult.Success(emptyList())
                    }
                    Log.d("METPROVIDER", "objectIDs null but total=${response.total}")
                    return ApiResult.Rejected("Met search returned no object IDs despite total=${response.total}")
                }
                Log.d("METPROVIDER", "total=${response.total}, ids=${fetched.size}")
                cachedIds = fetched
                ApiResult.Success(fetched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() in 500..599 || e.code() == 429) ApiResult.Failed(e)
                else ApiResult.Rejected("HTTP ${e.code()} loading Met ID list")
            } catch (e: IOException) {
                ApiResult.Failed(e)
            } catch (e: Exception) {
                Log.d("METPROVIDER", "LOAD IDS THROW = ${e.message}")
                ApiResult.Failed(e)
            }
        }
    }

    override suspend fun fetchPage(
        cursor: String?,
        size: Int
    ): PageResult {
        Log.d("METPROVIDER", "fetchPage cursor=$cursor")
        var idsOutcome = loadIds()
        if (idsOutcome is ApiResult.Failed) {
            Log.d("METPROVIDER", "retrying id list load after transient failure: ${idsOutcome.cause.message}")
            idsOutcome = loadIds()
        }
        val allIds = when (idsOutcome) {
            is ApiResult.Success -> idsOutcome.value
            is ApiResult.Rejected -> return PageResult(emptyList(), emptyList(), cursor, PageStatus.FAILED, idsOutcome.reason)
            is ApiResult.Failed -> return PageResult(emptyList(), emptyList(), cursor, PageStatus.FAILED, idsOutcome.cause.message ?: "Failed to load Met catalog")
        }
        Log.d("METPROVIDER", "allIds size=${allIds.size}")
        var i = parseCursor(cursor)
        val items : MutableList<Artwork> = ArrayList()
        val details: MutableList<ArtworkDetail> = ArrayList()
        var failureReason: String? = null
        var consecutiveFailures = 0
        // Walk IDs until we have `size` good ones or run out.
        // Rejected records are skipped permanently — they'd fail
        // identically next time. An isolated Failed record is also
        // skipped: stalling the whole page on one flaky object would
        // mean the user never sees the perfectly good ones right after
        // it. Only when failures stack up consecutively — a real
        // outage, not one bad object — do we stop and leave the cursor
        // pointing at the failing record so the next page retries it.
        while(i < allIds.size && items.size < size)
        {
            val objectId = allIds[i]
            var resultPair = fetchArtwork(objectId)
            if(resultPair is ApiResult.Failed)
            {
                Log.d("METPROVIDER", "retrying object $objectId after transient failure: ${resultPair.cause.message}")
                resultPair = fetchArtwork(objectId)
            }
            when(resultPair)
            {
                is ApiResult.Success -> {
                    consecutiveFailures = 0
                    items.add(resultPair.value.first)
                    details.add(resultPair.value.second)
                    i++
                }
                is ApiResult.Rejected -> {
                    consecutiveFailures = 0
                    Log.d("METPROVIDER", "skipping object $objectId: ${resultPair.reason}")
                    i++
                }
                is ApiResult.Failed -> {
                    consecutiveFailures++
                    Log.d("METPROVIDER", "object $objectId failed after retry (consecutive=$consecutiveFailures): ${resultPair.cause.message}")
                    if (consecutiveFailures >= consecutiveFailureThreshold) {
                        failureReason = resultPair.cause.message ?: "Object $objectId failed after retry"
                        break
                    }
                    i++
                }
            }
        }
        if (items.isEmpty() && failureReason != null) {
            return PageResult(emptyList(), emptyList(), cursor, PageStatus.FAILED, failureReason)
        }
        var status = PageStatus.OK
        var next: String? = null
        if(i < allIds.size)
        {
            next = i.toString()
        }
        else {
            status = PageStatus.EXHAUSTED
        }
        return PageResult(items, details, next, status)
    }

}