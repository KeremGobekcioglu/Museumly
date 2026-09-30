package com.kg.museumly.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Protects the two facts the rest of the app relies on Room for: feed order
 * survives round-tripping through SQLite (position, not insertion order,
 * is the source of truth — see ArtworkEntity's doc comment), and the
 * composite "provider:id" primary key actually prevents Met and Cleveland
 * object ids from colliding, since both use overlapping numeric ranges.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkDaoTest {

    private lateinit var database: MuseumDatabase
    private lateinit var dao: ArtworkDao

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

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MuseumDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.artworkDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertAll then observeBySection returns rows ordered by position`() = runTest {
        dao.insertAll(listOf(entity("met:3", "met", position = 2), entity("met:1", "met", position = 0), entity("met:2", "met", position = 1)))

        val rows: List<ArtworkEntity> = dao.observeBySection("european").first()

        assertEquals(listOf("met:1", "met:2", "met:3"), rows.map { it.id })
    }

    @Test
    fun `composite provider-prefixed ids do not collide between providers`() {
        // Met and Cleveland both use six-digit numeric ids, so the raw
        // number "123" exists in both corpora — only the "provider:" prefix
        // keeps them from overwriting each other.
        runTest {
            dao.insertAll(
                listOf(
                    entity("met:123", "met", position = 0),
                    entity("cleveland:123", "cleveland", position = 1),
                ),
            )

            val metRow: ArtworkEntity? = dao.byId("met:123")
            val clevelandRow: ArtworkEntity? = dao.byId("cleveland:123")

            assertEquals("met", metRow?.providerId)
            assertEquals("cleveland", clevelandRow?.providerId)
            assertEquals(2, dao.count("european"))
        }
    }

    @Test
    fun `maxPosition returns negative one on an empty table so the first position lands on zero`() = runTest {
        val max: Int = dao.maxPosition("european")

        assertEquals(-1, max)
    }

    @Test
    fun `observeBySection and count only see their own section`() = runTest {
        dao.insertAll(
            listOf(
                entity("met:1", "met", position = 0, sectionId = "european"),
                entity("met:2", "met", position = 0, sectionId = "asia"),
                entity("met:3", "met", position = 1, sectionId = "asia"),
            ),
        )

        val asia: List<ArtworkEntity> = dao.observeBySection("asia").first()

        assertEquals(listOf("met:2", "met:3"), asia.map { it.id })
        assertEquals(1, dao.count("european"))
        assertEquals(2, dao.count("asia"))
    }

    @Test
    fun `maxPosition is per section, so a new section still starts at zero`() = runTest {
        dao.insertAll(listOf(entity("met:1", "met", position = 0), entity("met:2", "met", position = 1)))

        assertEquals(1, dao.maxPosition("european"))
        assertEquals(-1, dao.maxPosition("asia"))
    }
}
