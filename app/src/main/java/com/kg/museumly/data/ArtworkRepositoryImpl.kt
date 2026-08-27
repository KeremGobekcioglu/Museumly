package com.kg.museumly.data

import com.kg.museumly.data.local.ArtworkDao
import com.kg.museumly.data.local.ArtworkEntity
import com.kg.museumly.data.local.ArtworkMapper
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.model.Artwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkRepositoryImpl @Inject constructor(
    private val artworkDao: ArtworkDao,
    private val seedSource: SeedSource
) : ArtworkRepository
{
    private val mutex = Mutex()
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

    override suspend fun byId(id: String): Artwork? {
        val entity : ArtworkEntity? = artworkDao.byId(id)
        if(entity == null)
            return null
        return ArtworkMapper.toDomain(entity)
    }

    private suspend fun insert(items: List<Artwork>) {
        var position: Int = artworkDao.maxPosition()
        val entities : MutableList<ArtworkEntity> = ArrayList()

        for(artwork in items)
        {
            position += 1
            entities.add(ArtworkMapper.toEntity(artwork,position))
        }
        artworkDao.insertAll(entities)
    }

    // Debug only: append a batch while the screen is live, to watch what
    // a mid-fling Room emission does to the pager.
    override suspend fun appendBatch(items: List<Artwork>) {
        mutex.withLock {
            insert(items)
        }
    }

    override suspend fun seedIfEmpty() {
        mutex.withLock {
            val existing: Int = artworkDao.count()
            if(existing > 0)
                return@withLock
            insert(seedSource.artworks())
        }
    }

}