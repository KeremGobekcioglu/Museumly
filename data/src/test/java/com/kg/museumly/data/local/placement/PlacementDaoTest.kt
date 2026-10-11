package com.kg.museumly.data.local.placement

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kg.museumly.data.local.ArtworkDao
import com.kg.museumly.data.local.ArtworkEntity
import com.kg.museumly.data.local.MuseumDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Protects how positions are numbered per list: each list starts at zero,
 * a duplicate in the same list moves to the end, and placing a work in one
 * list never moves it in another.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlacementDaoTest {

    private lateinit var database: MuseumDatabase
    private lateinit var artworkDao: ArtworkDao
    private lateinit var dao: PlacementDao

    private fun entity(id: String): ArtworkEntity {
        return ArtworkEntity(
            id = id,
            providerId = "met",
            sectionId = "european",
            title = "Title $id",
            artist = null,
            year = null,
            yearStart = null,
            imageUrl = "https://example.org/$id.jpg",
            aspectRatio = null,
            classification = null,
            department = null,
        )
    }

    private fun placement(artworkId: String, position: Int, listKey: String): PlacementEntity {
        return PlacementEntity(artworkId = artworkId, position = position, listKey = listKey)
    }

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MuseumDatabase::class.java,
        ).allowMainThreadQueries().build()
        artworkDao = database.artworkDao()
        dao = database.placementDao()
        // Placements have a foreign key on artworks, so the rows must exist first.
        artworkDao.insertAll(listOf(entity("met:1"), entity("met:2"), entity("met:3")))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `maxPosition returns negative one for an empty list so the first position lands on zero`() = runTest {
        assertEquals(-1, dao.maxPosition("european"))
    }

    @Test
    fun `maxPosition and count are per list, so a new list still starts at zero`() = runTest {
        dao.insertAll(listOf(placement("met:1", 0, "european"), placement("met:2", 1, "european")))

        assertEquals(1, dao.maxPosition("european"))
        assertEquals(2, dao.count("european"))
        assertEquals(-1, dao.maxPosition("asia"))
        assertEquals(0, dao.count("asia"))
    }

    @Test
    fun `a duplicate in the same list moves to the end instead of appearing twice`() = runTest {
        dao.insertAll(listOf(placement("met:1", 0, "european"), placement("met:2", 1, "european")))

        dao.insertAll(listOf(placement("met:1", 2, "european")))

        assertEquals(2, dao.count("european"))
        assertEquals(listOf("met:2", "met:1"), artworkDao.observeByList("european").first().map { it.id })
    }

    @Test
    fun `placing a work in a second list does not move it in the first`() = runTest {
        dao.insertAll(listOf(placement("met:1", 0, "european"), placement("met:2", 1, "european")))

        dao.insertAll(listOf(placement("met:1", 5, "european@1600s")))

        assertEquals(listOf("met:1", "met:2"), artworkDao.observeByList("european").first().map { it.id })
        assertEquals(1, dao.maxPosition("european"))
        assertEquals(listOf("met:1"), artworkDao.observeByList("european@1600s").first().map { it.id })
    }
}
