package com.kg.museumly.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kg.museumly.data.local.placement.PlacementDao
import com.kg.museumly.data.local.placement.PlacementEntity
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
 * Protects the facts the rest of the app relies on Room for: list order
 * survives round-tripping through SQLite (placement position, not insertion
 * order, is the source of truth — see PlacementEntity's doc comment), the
 * "provider:id" primary key keeps Met and Cleveland ids from colliding, and
 * re-inserting an artwork never disturbs its placements (IGNORE, not
 * REPLACE — see ArtworkDao.insertAll).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkDaoTest {

    private lateinit var database: MuseumDatabase
    private lateinit var dao: ArtworkDao
    private lateinit var placementDao: PlacementDao

    private fun entity(id: String, providerId: String, sectionId: String = "european"): ArtworkEntity {
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
            classification = null,
            department = null,
        )
    }

    private fun placement(artworkId: String, position: Int, listKey: String = "european"): PlacementEntity {
        return PlacementEntity(artworkId = artworkId, position = position, listKey = listKey)
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MuseumDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.artworkDao()
        placementDao = database.placementDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeByList returns rows ordered by placement position, not insertion order`() = runTest {
        dao.insertAll(listOf(entity("met:1", "met"), entity("met:2", "met"), entity("met:3", "met")))
        placementDao.insertAll(listOf(placement("met:3", 2), placement("met:1", 0), placement("met:2", 1)))

        val rows: List<ArtworkEntity> = dao.observeByList("european").first()

        assertEquals(listOf("met:1", "met:2", "met:3"), rows.map { it.id })
    }

    @Test
    fun `composite provider-prefixed ids do not collide between providers`() = runTest {
        // Met and Cleveland both use six-digit numeric ids, so the raw
        // number "123" exists in both corpora — only the "provider:" prefix
        // keeps them from overwriting each other.
        dao.insertAll(listOf(entity("met:123", "met"), entity("cleveland:123", "cleveland")))

        assertEquals("met", dao.byId("met:123")?.providerId)
        assertEquals("cleveland", dao.byId("cleveland:123")?.providerId)
    }

    @Test
    fun `observeByList only sees its own list`() = runTest {
        dao.insertAll(listOf(entity("met:1", "met"), entity("met:2", "met"), entity("met:3", "met")))
        placementDao.insertAll(
            listOf(
                placement("met:1", 0, listKey = "european"),
                placement("met:2", 0, listKey = "asia"),
                placement("met:3", 1, listKey = "asia"),
            ),
        )

        val asia: List<ArtworkEntity> = dao.observeByList("asia").first()

        assertEquals(listOf("met:2", "met:3"), asia.map { it.id })
    }

    @Test
    fun `re-inserting an artwork keeps its placements`() = runTest {
        // With REPLACE, the second insert would delete the row first, and the
        // cascade would take its placement with it.
        dao.insertAll(listOf(entity("met:1", "met")))
        placementDao.insertAll(listOf(placement("met:1", 0)))

        dao.insertAll(listOf(entity("met:1", "met")))

        assertEquals(listOf("met:1"), dao.observeByList("european").first().map { it.id })
    }

    @Test
    fun `re-inserting an artwork keeps a measured aspect ratio`() = runTest {
        dao.insertAll(listOf(entity("met:1", "met")))
        dao.setAspectRatio("met:1", 1.5f)

        // A fresh copy from the API still has a null ratio.
        dao.insertAll(listOf(entity("met:1", "met")))

        assertEquals(1.5f, dao.byId("met:1")?.aspectRatio)
    }

    @Test
    fun `clear removes every placement, so list counts drop to zero`() = runTest {
        dao.insertAll(listOf(entity("met:1", "met"), entity("met:2", "met")))
        placementDao.insertAll(listOf(placement("met:1", 0), placement("met:2", 0, listKey = "european@1600s")))

        dao.clear()

        assertEquals(0, placementDao.count("european"))
        assertEquals(0, placementDao.count("european@1600s"))
    }
}
