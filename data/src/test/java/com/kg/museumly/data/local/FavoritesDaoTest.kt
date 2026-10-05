package com.kg.museumly.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kg.museumly.data.local.favorites.FavoriteEntity
import com.kg.museumly.data.local.favorites.FavoritesDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoritesDaoTest {
    private lateinit var database: MuseumDatabase
    private lateinit var artworkDao: ArtworkDao
    private lateinit var favoritesDao: FavoritesDao

    private fun entity(id: String, providerId: String, position: Int, sectionId: String = "european"): ArtworkEntity {
        return ArtworkEntity(
            id = id,
            providerId = providerId,
            sectionId = sectionId,
            title = "Title $id",
            artist = null,
            year = null,
            yearStart = null,
            imageUrl = "https://example.org/$id.jpg",
            aspectRatio = null,
            position = position,
            classification = null,
            department = null,
        )
    }

    private fun idsOf(rows: List<ArtworkEntity>): List<String> {
        val result: MutableList<String> = ArrayList()
        for (row in rows) {
            result.add(row.id)
        }
        return result
    }

    @Before
    fun setUp()
    {
        database = Room.inMemoryDatabaseBuilder(
            context = ApplicationProvider.getApplicationContext(),
            klass = MuseumDatabase::class.java
        ).allowMainThreadQueries().build()
        artworkDao = database.artworkDao()
        favoritesDao = database.favoritesDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun checkThatInsertWorksAndItemShowsUp() = runTest {
        favoritesDao.insert(FavoriteEntity("met:1", 1000L))
        val ids : List<String> = favoritesDao.observeFavoriteIds().first()
        assertEquals(listOf("met:1"), ids)
    }

    @Test
    fun removeDeletesOnlyGivenId() = runTest {
        favoritesDao.insert(FavoriteEntity("met:1", 1000L))
        favoritesDao.insert(FavoriteEntity("met:2", 2000L))
        favoritesDao.remove("met:1")
        assertNull(favoritesDao.byId("met:1"))
        assertNotNull(favoritesDao.byId("met:2"))
        assertEquals(1,favoritesDao.count())
    }

    @Test
    fun checkThatGetFavoritesOnlyReturnFavorites() = runTest {
        artworkDao.insertAll(
            listOf(
                entity("met:1", "met", position = 0),
                entity("met:2", "met", position = 1),
                entity("met:3", "met", position = 2),
            ),
        )
        favoritesDao.insert(FavoriteEntity("met:2", 1000L))
        val favorites = favoritesDao.getFavorites(null).first()
        assertEquals(listOf("met:2"), idsOf(favorites))
    }

    @Test
    fun checkThatSectionFilterWorks() = runTest {
        artworkDao.insertAll(
            listOf(
                entity("met:1", "met", position = 0, sectionId = "european"),
                entity("met:2", "met", position = 0, sectionId = "asia"),
            ),
        )
        favoritesDao.insert(FavoriteEntity("met:1", 1000L))
        favoritesDao.insert(FavoriteEntity("met:2", 2000L))
        val asia: List<ArtworkEntity> = favoritesDao.getFavorites("asia").first()
        val all: List<ArtworkEntity> = favoritesDao.getFavorites(null).first()
        assertEquals(listOf("met:2"), idsOf(asia))
        assertEquals(2, all.size)
    }

    @Test
    fun `getFavorites puts the most recently favorited first`() = runTest {
        artworkDao.insertAll(
            listOf(
                entity("met:1", "met", position = 0),
                entity("met:2", "met", position = 1),
                entity("met:3", "met", position = 2),
            ),
        )
        // Favorited in a different order than the feed positions.
        favoritesDao.insert(FavoriteEntity("met:2", 1000L))
        favoritesDao.insert(FavoriteEntity("met:3", 3000L))
        favoritesDao.insert(FavoriteEntity("met:1", 2000L))

        val favorites: List<ArtworkEntity> = favoritesDao.getFavorites(null).first()

        assertEquals(listOf("met:3", "met:1", "met:2"), idsOf(favorites))
    }

    @Test
    fun `a favorite survives the artwork row being replaced`() = runTest {
        artworkDao.insertAll(listOf(entity("met:1", "met", position = 0)))
        favoritesDao.insert(FavoriteEntity("met:1", 1000L))

        // Same id arrives again: REPLACE deletes the row and writes it at the end.
        artworkDao.insertAll(listOf(entity("met:1", "met", position = 5)))

        val favorites: List<ArtworkEntity> = favoritesDao.getFavorites(null).first()

        assertEquals(listOf("met:1"), idsOf(favorites))
        assertEquals(5, favorites[0].position)
    }

    @Test
    fun `inserting the same favorite twice keeps one row`() = runTest {
        favoritesDao.insert(FavoriteEntity("met:1", 1000L))
        favoritesDao.insert(FavoriteEntity("met:1", 2000L))

        assertEquals(1, favoritesDao.count())
        assertEquals(2000L, favoritesDao.byId("met:1")?.favoritedAt)
    }
}