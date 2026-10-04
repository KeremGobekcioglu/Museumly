package com.kg.museumly.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Each provider's rotation position is independent (README: "interleaving
 * cannot corrupt either"), which only holds if cursor rows are truly keyed
 * by providerId and a write to one never touches the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderCursorDaoTest {

    private lateinit var database: MuseumDatabase
    private lateinit var dao: ProviderCursorDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MuseumDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.cursorDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `cursor rows are keyed by provider id, independent of each other`() = runTest {
        dao.put(ProviderCursor(providerId = "met", next = "20"))
        dao.put(ProviderCursor(providerId = "cleveland", next = null))

        val met: ProviderCursor? = dao.get("met")
        val cleveland: ProviderCursor? = dao.get("cleveland")

        assertEquals("20", met?.next)
        assertNull(cleveland?.next)
    }

    @Test
    fun `put with REPLACE overwrites the previous cursor for the same provider`() = runTest {
        dao.put(ProviderCursor(providerId = "met", next = "20"))
        dao.put(ProviderCursor(providerId = "met", next = "40"))

        val met: ProviderCursor? = dao.get("met")

        assertEquals("40", met?.next)
    }
}
