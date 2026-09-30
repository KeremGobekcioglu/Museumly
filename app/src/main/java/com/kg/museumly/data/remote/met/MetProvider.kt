package com.kg.museumly.data.remote.met

import android.util.Log
import com.kg.museumly.data.remote.worthRetrying
import com.kg.museumly.domain.ApiResult
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import com.kg.museumly.model.Section
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
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

    /**
     * Different departments so a map is needed with a int key and list value.
     */
    private val cachedIds: MutableMap<Int, MutableList<Int>> = HashMap()

    /**
     * Different departments, so a map of totals. Int key -> department id,
     * Int value -> total.
     */
    private val totals: MutableMap<Int,Int> = HashMap()        // null until the first ID page arrives
    private val idsMutex = Mutex()

    /** How many IDs this query can ever give us: the real total, capped by the v1.1 ceiling. */
    private fun reachable(departmentId: Int): Int?
    {
        val total: Int = totals[departmentId] ?: return null
        return minOf(total, SEARCH_CEILING)
    }

    /**
     * met api returns object ids for search. it returns objectIds for departments.
     * This function does if a department is asked before, return its existing id list.
     * ıf it is not asked before, create and put the list in the map and return it.
     * Same object every time, so ensureIds fills the list fetchPage reads.
     */
    private fun idsFor(departmentId: Int): MutableList<Int>
    {
        var ids: MutableList<Int>? = cachedIds[departmentId]
        if (ids == null) {
            ids = ArrayList()
            cachedIds[departmentId] = ids
        }
        return ids
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
                e.code() in 500..599 || e.code() == 429 || e.code() == 403 -> ApiResult.Failed(e)
                else -> ApiResult.Rejected("HTTP ${e.code()} for object $objectId")
            }
        }
        catch (e: IOException) {
            ApiResult.Failed(e)
        }
        catch (e: SerializationException) {
            ApiResult.Rejected("unparseable object $objectId , message: ${e.message}")
        }
        // KNOWN RISK (left as-is for now): anything reaching here isn't a network
        // error (IOException/HTTP are caught above) — most likely the mapper choking
        // on an odd record. That's permanent, but labelled Failed, so 3 in a row form
        // a streak → rewind → same records → department stuck FAILED forever.
        //
        // Fix when needed: return Rejected here and use Log.e.
        // Consequence: a mapper bug that hits EVERY record would then skip through the
        // whole department to EXHAUSTED — clear app data after fixing mapper bugs.
        catch (e: Exception) {
            Log.d("MET PROVIDER", "unexpected error for $objectId: ${e.message}")
            ApiResult.Failed(e)
        }
    }

    /**
     * Grows cachedIds until it covers indices [0, upTo), one 500-ID page at a time.
     * Index into cachedIds == search offset, so the cursor stays a plain index.
     */
    private suspend fun ensureIds(upTo: Int, departmentId: Int): ApiResult<Unit> {
        idsMutex.withLock {
            return try {
                /**
                 * idsFor checks cachedIds map, returns a list checking the department id key.
                 * we update this list s reference so we dont touch cachedIds directly.
                 */
                val ids = idsFor(departmentId)
               while (ids.size < upTo)
               {
                   val cap: Int? = reachable(departmentId)
                   if (cap != null && ids.size >= cap) break // query fully consumed
                   /**
                    * 500 or what we got left. lets say we have cached 9700 ids, so right side
                    * will be 300. limit will be 300.
                    * if we get below 9500 ids, lets say 9000 , right side will be 1000 so
                    * we ll get 500 again.
                    */
                   val limit = min(ID_PAGE_LIMIT, SEARCH_CEILING - ids.size)
                   // get 500 for first.
                   val response = api.search(departmentId, offset = ids.size, limit = limit)
                   totals[departmentId] = response.total
                   val page: List<Int>? = response.objectIDs
                   if(page == null)
                   {
                       if(response.total == 0) break
                       return ApiResult.Rejected("Met search returned no object IDs despite total=${response.total}")
                   }
                   else if (page.isEmpty()) break // safety: never loop on an empty page
                   /**
                    * This line updates cachedIds list. manipulates the reference.
                    */
                   ids.addAll(page)
               }
                ApiResult.Success(Unit)
            }
            catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() in 500..599 || e.code() == 429 || e.code() == 403) ApiResult.Failed(e)
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
    private suspend fun ensureIdsWithRetry(upTo: Int, departmentId: Int): ApiResult<Unit> {
        val first = ensureIds(upTo, departmentId)
        if (first is ApiResult.Failed && first.worthRetrying()) {
            Log.d("METPROVIDER", "retrying id page after transient failure: ${first.cause.message}")
            delay(RETRY_DELAY)
            return ensureIds(upTo, departmentId)
        }
        return first
    }

    override suspend fun fetchPage(
        cursor: String?,
        size: Int,
        department: String
    ): PageResult {
        Log.d("METPROVIDER", "fetchPage department=$department cursor=$cursor")
        // ← new: the "6" the repository handed back becomes 6 again
        val departmentId: Int = department.toIntOrNull()
            ?: return PageResult(emptyList(), emptyList(), cursor,
                PageStatus.FAILED,
                "bad Met department '$department'"
            )
        val ids = idsFor(departmentId)
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
            // The cursor walked past the IDs we have so far. Usually
            // i == ids.size (the cache ran out), but it can also be further
            // ahead: the cursor lives in Room and survives process death,
            // while cachedIds is memory-only and comes back empty (e.g.
            // cursor 600, ids.size 0). upTo = i + 1 makes ensureIds refetch
            // every ID page up to i, not just the next one.
            if( i >= ids.size)
            {
                val idsOutcome = ensureIdsWithRetry( i + 1, departmentId)
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
                if( i>= ids.size)
                {
                    break
                }
            }
            Log.d("METPROVIDER", "cachedIds size=${ids.size}")

            // toList() copies the batch's IDs. A plain subList is a view over
            // cachedIds, and if cachedIds grows while we're suspended below,
            // reading that view throws ConcurrentModificationException.
            // Batch of 4 stays under OkHttp's default maxRequestsPerHost (5).
            val batch = ids.subList(i, minOf(i + 4, ids.size)).toList()
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

                        // Streak of N failures: rewind i to the first one so the next page retries
                        // all of them (failures 1..N-1 did i++, failure N didn't).
                        // Without this, a brief outage skips those IDs forever — no backfill.
                        //
                        // If a department ever gets stuck FAILED at the same cursor forever:
                        // N broken records in a row, labelled Failed instead of Rejected.
                        // Find which exception they throw in fetchArtwork and map it to Rejected.

                        // If a department sticks at the same cursor: see KNOWN RISK on fetchArtwork's catch-all.
                        if(consecutiveFailures >= consecutiveFailureThreshold)
                        {
                            failureReason = result.cause.message ?: "Object $objectId failed"
                            i -= consecutiveFailures - 1 // back to the first failure of the streak.
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
        val cap: Int? = reachable(departmentId)
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

    private fun departmentIdsFor(section: Section): List<Int>
    {
        return when (section) {
            Section.EGYPT_NEAR_EAST -> listOf(10, 3)
            Section.GREEK_ROMAN -> listOf(13)
            Section.ISLAMIC -> listOf(14)
            Section.MEDIEVAL -> listOf(17)
            Section.EUROPEAN -> listOf(11, 12)
            Section.ASIA -> listOf(6)
            Section.AFRICA_OCEANIA_AMERICAS -> listOf(5)
        }
    }

    override fun departmentsFor(section: Section): List<String>
    {
        val result: MutableList<String> = ArrayList()
        for (id in departmentIdsFor(section)) {
            result.add(id.toString())
        }
        return result
    }
}