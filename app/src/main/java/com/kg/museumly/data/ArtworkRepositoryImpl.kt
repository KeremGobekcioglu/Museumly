package com.kg.museumly.data

import android.util.Log
import androidx.room.withTransaction
import com.kg.museumly.data.local.ArtworkDao
import com.kg.museumly.data.local.ArtworkDetailMapper
import com.kg.museumly.data.local.ArtworkEntity
import com.kg.museumly.data.local.ArtworkMapper
import com.kg.museumly.data.local.MuseumDatabase
import com.kg.museumly.data.local.ProviderCursor
import com.kg.museumly.data.local.ProviderCursorDao
import com.kg.museumly.data.local.ProviderTurnSource
import com.kg.museumly.data.local.detail.ArtworkDetailDao
import com.kg.museumly.data.local.detail.ArtworkDetailEntity
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import com.kg.museumly.model.ArtworkWithDetail
import com.kg.museumly.model.Section
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * One department of one provider. The repository rotates over these
 * exactly the way it used to rotate over providers.
 * key is what the cursor is stored under: "cleveland:Chinese Art", "met:6".
 */
private class Source(val provider: ArtworkProvider, val department: String)
{
    val key = provider.id + ":" + department
}

@Singleton
class ArtworkRepositoryImpl @Inject constructor(
    private val database: MuseumDatabase,
    private val artworkDao: ArtworkDao,
    private val artworkDetailDao: ArtworkDetailDao,
    private val cursorDao: ProviderCursorDao,
    private val providers: Set<@JvmSuppressWildcards ArtworkProvider>,
    private val seedSource: SeedSource,
    private val turnSource: ProviderTurnSource
) : ArtworkRepository
{
    private companion object {
        // callTimeout (NetworkModule) bounds a single HTTP call. fetchPage
        // can be several of those in a row — Met especially, one call per
        // object — so a provider that's merely slow, never erroring, never
        // tripping a single call's timeout, would otherwise have no ceiling
        // at all. This is that ceiling, generous relative to the 10s
        // per-call one since it has to cover the whole page.
        const val PROVIDER_FETCH_TIMEOUT_MS: Long = 20_000
    }

    /**
     * Departments differ by provider. SO they need
     * their own naming logic. see departmentsFor, it s overriden for both.
     * Eventually, user and domain/presenttion packages have one unified name for
     * each department.
     * This function create sources, for example:
     * if user wants to see european artworks, providers can have multiple
     * european department and this function combines them, european paintings, scultpure for
     * one provider, european paintings for other provider and returns 3 source that is
     * usable. later on, loadMore will take turn on these sources.
     */
    private fun sourcesFor(section: Section) : List<Source>
    {
        val sources : MutableList<Source> = ArrayList()
        val ordered : List<ArtworkProvider> = providers.sortedBy { it.id }
        for(provider in ordered)
        {
            for(department in provider.departmentsFor(section))
            {
                sources.add(Source(provider,department))
            }
        }
        return sources
    }

    /**
     * Only one writer at a time.
     *
     * insert() reads maxPosition(), then writes. Without this lock, two
     * writers can both read 19, and both write positions 20..39. Duplicate
     * positions break ORDER BY, so the feed order becomes random.
     *
     * SQLite already handles two writes at once. It does not handle the
     * math we do in between them. That is what this protects.
     *
     * Mutex, not synchronized, because these functions suspend.
     */
    private val mutex = Mutex()

    /**
     * Returns Flow, so the screen subscribes once and gets every future version automatically.
     */
    override fun artworks(section: Section): Flow<List<Artwork>> {
        return artworkDao.observeBySection(section.id).map {
            rows: List<ArtworkEntity> ->
                val result : MutableList<Artwork> = ArrayList()
                for(row in rows)
                {
                    result.add(ArtworkMapper.toDomain(row))
                }
            result
        }
    }

    override suspend fun artworkWithDetail(id: String): ArtworkWithDetail? {
        Log.d("REPOSITORY ARTWORKWITHDETAIL", "ARTWORK ID : $id")
        val artworkEntity = artworkDao.byId(id) ?: return null
        val artworkDetailEntity = artworkDetailDao.getById(id) ?: return null
        Log.d("REPOSITORY ARTWORKWITHDETAIL", "STILL NOT NULL")
        return ArtworkWithDetail(ArtworkMapper.toDomain(artworkEntity), ArtworkDetailMapper.toDomain(artworkDetailEntity))
    }

    override suspend fun byId(id: String): Artwork? {
        val entity : ArtworkEntity? = artworkDao.byId(id)
        if(entity == null)
            return null
        return ArtworkMapper.toDomain(entity)
    }

    /**
     * Writes artworks to the database, numbering them after the last one.
     *
     * The caller must lock the mutex first. This function does not lock it.
     * Kotlin's Mutex cannot be locked twice by the same caller — it would
     * freeze forever. This is private so all callers stay in this file.
     *
     * One insertAll for the whole list, not one per artwork, so Room
     * notifies the screen once instead of twenty times.
     */
    private suspend fun insert(items: List<Artwork>, details: List<ArtworkDetail>, section: Section) {
        var position: Int = artworkDao.maxPosition(section.id)
        val entities : MutableList<ArtworkEntity> = ArrayList()
        val detailEntities: MutableList<ArtworkDetailEntity> = ArrayList()
        for (i in items.indices) {
            position += 1
            val artwork: Artwork = items[i]
            val detail: ArtworkDetail = details[i]
            entities.add(ArtworkMapper.toEntity(artwork, position, section.id))
            detailEntities.add(
                ArtworkDetailEntity(
                    id = artwork.id,
                    medium = detail.medium,
                    dimensions = detail.dimensions,
                    creditLine = detail.creditLine,
                    culture = detail.culture,
                    period = detail.period,
                    highResImageUrl = detail.highResImageUrl,
                    artistBio = detail.artistBio,
                    description = detail.description,
                    didYouKnow = detail.didYouKnow,
                )
            )
        }
        artworkDao.insertAll(entities)
        artworkDetailDao.insertAll(detailEntities)
    }

//    override suspend fun seedIfEmpty() {
//        mutex.withLock {
//            val existing: Int = artworkDao.count()
//            if(existing > 0)
//                return@withLock
//            insert(seedSource.artworks())
//        }
//    }

    override suspend fun loadMore(section: Section, size: Int): LoadOutcome
    {
        mutex.withLock {
            val sources = sourcesFor(section)
            if(sources.isEmpty())
            {
                return LoadOutcome.Exhausted
            }
            val failures: MutableList<String> = ArrayList()
            val turn : Int = turnSource.getTurn(section = section)
            for(attempt in sources.indices)
            {
                val index : Int = (turn + attempt) % sources.size
                val source: Source = sources[index]
                Log.d("ARTWORKREPOSITORYIMPL" , "LOAD MORE.")
                val saved: ProviderCursor? = cursorDao.get(source.key)
                // we need to check exhaustion for providers
                if(saved != null && saved.next == null)
                {
                    // try next provider, this is finished.
                    Log.d("REPO", "exhausted, skipping")
                    continue
                }

                var cursor: String? = null
                if(saved != null)
                    cursor = saved.next
                // if saved is null, it means we are at 0, at the beginning.
                // The provider does everything: rebuilds its ID list if needed, walks
                // from `cursor`, hydrates each artwork, drops the unusable ones.
                Log.d("REPO", "calling fetchPage cursor=$cursor")
                val started: Long = System.nanoTime()
                val page: PageResult? = withTimeoutOrNull(PROVIDER_FETCH_TIMEOUT_MS.milliseconds)
                {
                    source.provider.fetchPage(cursor,size,source.department)
                }
                val ms: Long = (System.nanoTime() - started) / 1_000_000
                if (page == null)
                {
                    Log.d("REPO", "${source.key} timed out after ${ms}ms")
                    failures.add("${source.key}: timed out")
                    continue
                }
                Log.d("REPO", "${source.key} took ${ms}ms, ${page.items.size} items, next=${page.next}")
                if (page.status == PageStatus.FAILED) {
                    val reason: String = page.failureReason ?: "failed"
                    Log.d("REPO", "${source.key} failed: $reason")
                    failures.add("${source.key}: $reason")
                    continue
                }
                // If page.next is null, this writes the exhaustion marker, and the
                // continue check will skip this provider from now on.
                //
                // One transaction because a crash between the writes leaves damage
                // nothing ever repairs:
                //   cursor moved, artworks not inserted -> that range is never
                //     requested again; the feed just silently lacks those works
                //   artworks inserted, details not -> those pages open a blank
                //     detail screen forever, since nothing refetches what's already
                //     in Room
                //
                // Empty items with status OK is normal (the mapper rejected the whole
                // batch) and the cursor still has to move — those IDs would be
                // rejected again next time.
                database.withTransaction {
                    if (page.items.isNotEmpty()) {
                        insert(page.items, page.details, section)
                    }
                    cursorDao.put(ProviderCursor(source.key, page.next))
                }
                // DataStore, not Room, so it can't join the transaction. Only advance
                // the turn when the provider actually delivered something.
                if (page.items.isNotEmpty()) {
                    turnSource.setTurn((index + 1) % sources.size, section)
                    return LoadOutcome.Loaded
                }
            }
            if (failures.isNotEmpty()) {
                return LoadOutcome.Failed(failures.joinToString("; "))
            }
            return LoadOutcome.Exhausted
        }
    }

    override suspend fun count(section: Section): Int {
        return artworkDao.count(section.id)
    }

}