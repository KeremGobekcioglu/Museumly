package com.kg.museumly.data

import android.util.Log
import com.kg.museumly.data.local.ArtworkDao
import com.kg.museumly.data.local.ArtworkDetailMapper
import com.kg.museumly.data.local.ArtworkEntity
import com.kg.museumly.data.local.ArtworkMapper
import com.kg.museumly.data.local.ProviderCursor
import com.kg.museumly.data.local.ProviderCursorDao
import com.kg.museumly.data.local.detail.ArtworkDetailDao
import com.kg.museumly.data.local.detail.ArtworkDetailEntity
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.PageResult
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import com.kg.museumly.model.ArtworkWithDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkRepositoryImpl @Inject constructor(
    private val artworkDao: ArtworkDao,
    private val artworkDetailDao: ArtworkDetailDao,
    private val cursorDao: ProviderCursorDao,
    private val providers: Set<@JvmSuppressWildcards ArtworkProvider>,
    private val seedSource: SeedSource
) : ArtworkRepository
{
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
     * provider turns. it wraps.
     */
    private var turn: Int = 0

    /**
     * Returns Flow, so the screen subscribes once and gets every future version automatically.
     */
    override fun artworks(): Flow<List<Artwork>> {
        return artworkDao.observeAll().map {
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
    private suspend fun insert(items: List<Artwork>, details: List<ArtworkDetail>) {
        var position: Int = artworkDao.maxPosition()
        val entities : MutableList<ArtworkEntity> = ArrayList()
        val detailEntities: MutableList<ArtworkDetailEntity> = ArrayList()
        for (i in items.indices) {
            position += 1
            val artwork: Artwork = items[i]
            val detail: ArtworkDetail = details[i]
            entities.add(ArtworkMapper.toEntity(artwork, position))
            detailEntities.add(
                ArtworkDetailEntity(
                    id = artwork.id,
                    medium = detail.medium,
                    dimensions = detail.dimensions,
                    creditLine = detail.creditLine,
                    culture = detail.culture,
                    period = detail.period,
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

    override suspend fun loadMore(size: Int) {
        mutex.withLock {
            val ordered : List<ArtworkProvider> = providers.sortedBy { it.id }
            for(attempt in ordered.indices)
            {
                val index : Int = (turn + attempt) % ordered.size
                val provider: ArtworkProvider = ordered[index]
                Log.d("ARTWORKREPOSITORYIMPL" , "LOAD MORE.")
                val saved: ProviderCursor? = cursorDao.get(provider.id)
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
                val page: PageResult = provider.fetchPage(cursor,size)
                Log.d("REPO", "returned ${page.items.size} items, next=${page.next}")
                // If page.next is null, this writes the exhaustion marker, and the
                // continue check will skip this provider from now on.
                cursorDao.put(ProviderCursor(provider.id,page.next))
                Log.d("ARTWORKREPOSITORYIMPL" , "CURSOR = $cursor")
                if(page.items.isNotEmpty())
                {
                    insert(page.items , page.details)
                    //update turn
                    turn = (index + 1) % ordered.size
                    return@withLock
                }
            }
        }
    }

    override suspend fun count(): Int {
        return artworkDao.count()
    }

}