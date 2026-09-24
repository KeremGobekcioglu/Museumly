package com.kg.museumly.data.remote.met

import android.util.Log
import com.kg.museumly.data.remote.worthRetrying
import com.kg.museumly.domain.ApiResult
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * right now we can only get paintings if it is our query.
 */
@Singleton
class MetProvider @Inject constructor(
    private val api: MetApi
): ArtworkProvider {

    private companion object {
        val RETRY_DELAY: Duration = 500.milliseconds
        const val ID_PAGE_LIMIT = 500      // v1.1 max per request
        const val SEARCH_CEILING = 10_000  // v1.1: offset + limit may not exceed this
    }

    override val id = "met"
    private val cachedIds = mutableListOf<Int>()
    private var total: Int? = null         // null until the first ID page arrives
    private val idsMutex = Mutex()

    /** How many IDs this query can ever give us: the real total, capped by the v1.1 ceiling. */
    private fun reachable(): Int?
    {
        return total?.let { minOf(it, SEARCH_CEILING) }
    }

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

    /**
     * Grows cachedIds until it covers indices [0, upTo), one 500-ID page at a time.
     * Index into cachedIds == search offset, so the cursor stays a plain index.
     */
    private suspend fun ensureIds(upTo: Int): ApiResult<Unit> {
        idsMutex.withLock {
            return try {
               while (cachedIds.size < upTo)
               {
                   val cap: Int? = reachable()
                   if (cap != null && cachedIds.size >= cap) break // query fully consumed
                   /**
                    * 500 or what we got left. lets say we have cached 9700 ids, so right side
                    * will be 300. limit will be 300.
                    * if we get below 9500 ids, lets say 9000 , right side will be 1000 so
                    * we ll get 500 again.
                    */
                   val limit = min(ID_PAGE_LIMIT, SEARCH_CEILING - cachedIds.size)
                   val response = api.search(11, offset = cachedIds.size, limit = limit)
                   total = response.total
                   val page: List<Int>? = response.objectIDs
                   if(page == null)
                   {
                       // Same rule as before: null with total=0 is a real empty result.
                       if(response.total == 0) break
                       return ApiResult.Rejected("Met search returned no object IDs despite total=${response.total}")
                   }
                   else if (page.isEmpty()) break // safety: never loop on an empty page
                   cachedIds.addAll(page)
               }
                ApiResult.Success(Unit)
            }
            catch (e: CancellationException) {
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

    /** Your old retry-once rule, moved from the loadIds() call site to here. */
    private suspend fun ensureIdsWithRetry(upTo: Int): ApiResult<Unit> {
        val first = ensureIds(upTo)
        if (first is ApiResult.Failed && first.worthRetrying()) {
            Log.d("METPROVIDER", "retrying id page after transient failure: ${first.cause.message}")
            delay(RETRY_DELAY)
            return ensureIds(upTo)
        }
        return first
    }

    override suspend fun fetchPage(
        cursor: String?,
        size: Int
    ): PageResult {
        Log.d("METPROVIDER", "fetchPage cursor=$cursor")
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
        while (items.size < size)
        {
            // The cursor walked past the IDs we have so far. Fetch the
            // next ID page from v1.1 before hydrating anything else.
            if( i >= cachedIds.size)
            {
                val idsOutcome = ensureIdsWithRetry( i + 1)
                if(idsOutcome is ApiResult.Rejected)
                {
                    failureReason = idsOutcome.reason
                    break
                }
                if (idsOutcome is ApiResult.Failed) {
                    failureReason = idsOutcome.cause.message ?: "Failed to load Met IDs"
                    break
                }
                // Fetched fine but nothing new arrived: end of the query,
                // or we hit the 10k ceiling.
                if( i>= cachedIds.size)
                {
                    break
                }
            }
            Log.d("METPROVIDER", "cachedIds size=${cachedIds.size}")

            // toList() copies the two IDs. A plain subList is a view over
            // cachedIds, and if cachedIds grows while we're suspended below,
            // reading that view throws ConcurrentModificationException.
            val batch = cachedIds.subList(i, minOf(i + 2, cachedIds.size)).toList()
            // map launches every async{} immediately (List.map is eager, not lazy),
            // so all requests are in flight before awaitAll() blocks on them.
            val results = coroutineScope {
                batch.map { objectId -> async { fetchArtwork(objectId) } }.awaitAll()
            }

            // batch and results are the same length, and results[k] is the
            // outcome for batch[k] — awaitAll() preserved that order — so we
            // walk both by the same index instead of pairing them up first.
            for(index in batch.indices)
            {
                val objectId = batch[index]
                val result = results[index]

                when(result)
                {
                    is ApiResult.Success -> {
                        consecutiveFailures = 0
                        items.add(result.value.first)
                        details.add((result.value.second))
                        i++
                    }
                    is ApiResult.Rejected ->
                    {
                        consecutiveFailures = 0
                        Log.d("METPROVIDER", "skipping object $objectId: ${result.reason}")
                        i++
                    }
                    is ApiResult.Failed ->
                    {
                        consecutiveFailures++
                        Log.d("METPROVIDER", "object $objectId failed (consecutive=$consecutiveFailures): ${result.cause.message}")
                        if(consecutiveFailures >= consecutiveFailureThreshold)
                        {
                            failureReason = result.cause.message ?: "Object $objectId failed"
                            break
                        }
                        i++
                    }
                }
                if(items.size >= size) break
            }
            if(failureReason != null)
            {
                break
            }

        }
        if(items.isEmpty() && failureReason != null)
        {
            return PageResult(emptyList(),emptyList(),cursor, PageStatus.FAILED, failureReason)
        }
        var status = PageStatus.OK
        var next: String? = null
        val cap: Int? = reachable()
        if(cap != null && i >= cap)
        {
            status = PageStatus.EXHAUSTED
        }
        else
        {
            next = i.toString()
        }
        return PageResult(items,details,next,status)
    }

}