package com.kg.museumly.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kg.museumly.data.local.ArtworkDao
import com.kg.museumly.data.local.ArtworkEntity
import com.kg.museumly.data.local.MuseumDatabase
import com.kg.museumly.data.local.ProviderCursor
import com.kg.museumly.data.local.ProviderCursorDao
import com.kg.museumly.data.local.ProviderTurnSource
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.testutil.FakeArtworkProvider
import com.kg.museumly.testutil.sampleArtwork
import com.kg.museumly.testutil.sampleDetail
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Protects the repository's round-robin and the cursor-poisoning trap
 * README.md describes: a failed provider must never write
 * ProviderCursor(id, null), rotation must be deterministic despite
 * Dagger's multibinding Set having no iteration order, and turn only
 * advances past a provider that actually delivered something.
 *
 * Uses a real in-memory Room database (Robolectric), not fakes, because
 * database.withTransaction {} is the thing under test in the atomicity
 * case below — a fake repository has no transaction to break. Providers are
 * FakeArtworkProvider (a real ArtworkProvider interface, easy to hold queued
 * state for). turnSource stays MockK: ProviderTurnSource is a final class
 * whose constructor needs a real android.content.Context, so a fake subclass
 * would still need a real or mocked Context just to compile — a mock avoids
 * that entirely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkRepositoryImplTest {

    private lateinit var database: MuseumDatabase
    private lateinit var artworkDao: ArtworkDao
    private lateinit var cursorDao: ProviderCursorDao
    private lateinit var turnSource: ProviderTurnSource

    private fun provider(id: String, page: PageResult): FakeArtworkProvider {
        val fake = FakeArtworkProvider(id)
        fake.enqueue(page)
        return fake
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MuseumDatabase::class.java,
        ).allowMainThreadQueries().build()
        artworkDao = database.artworkDao()
        cursorDao = database.cursorDao()

        turnSource = mockk()
        coEvery { turnSource.getTurn() } returns 0
        coEvery { turnSource.setTurn(any()) } just Runs
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(providers: Set<ArtworkProvider>, artworkDaoOverride: ArtworkDao = artworkDao): ArtworkRepositoryImpl {
        return ArtworkRepositoryImpl(
            database = database,
            artworkDao = artworkDaoOverride,
            artworkDetailDao = database.artworkDetailDao(),
            cursorDao = cursorDao,
            providers = providers,
            // Real instance, not a double — SeedSource() takes no
            // dependencies and nothing in loadMore() calls it
            // (seedIfEmpty is commented out), so there's nothing to fake.
            seedSource = SeedSource(),
            turnSource = turnSource,
        )
    }

    @Test
    fun `providers are tried in sorted-by-id order regardless of set iteration order`() = runTest {
        val metPage = PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK)
        val met = provider("met", metPage)
        val cleveland = provider("cleveland", metPage.copy(items = listOf(sampleArtwork("cleveland:1"))))

        // Inserted met-first into the Set — sortedBy(id) must still try
        // cleveland first, since @IntoSet gives no ordering guarantee.
        val repository = repository(linkedSetOf(met, cleveland))

        val outcome: LoadOutcome = repository.loadMore(size = 20)

        assertEquals(LoadOutcome.Loaded, outcome)
        assertEquals(1, cleveland.callCount)
        assertEquals(0, met.callCount)
    }

    @Test
    fun `turn advances only on success`() = runTest {
        val clevelandFailed = PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "cleveland down")
        val metSucceeded = PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK)
        val cleveland = provider("cleveland", clevelandFailed)
        val met = provider("met", metSucceeded)

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(size = 20)

        assertEquals(LoadOutcome.Loaded, outcome)
        // met is index 1 in the sorted [cleveland, met] list, so the next
        // turn should be (1 + 1) % 2 = 0, not 2 or unchanged.
        coVerify(exactly = 1) { turnSource.setTurn(0) }
    }

    @Test
    fun `turn does not advance when a page comes back OK but empty`() = runTest {
        // The mapper rejected the whole batch — status is OK (not FAILED),
        // but there's nothing to show, so this must not count as progress.
        val emptyOkPage = PageResult(emptyList(), emptyList(), next = "5", status = PageStatus.OK)
        val cleveland = provider("cleveland", emptyOkPage)
        val met = provider("met", emptyOkPage)

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(size = 20)

        assertEquals(LoadOutcome.Exhausted, outcome)
        coVerify(exactly = 0) { turnSource.setTurn(any()) }
    }

    @Test
    fun `all-provider failure joins every failure reason, not just the last one`() = runTest {
        val clevelandFailed = PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "cleveland down")
        val metFailed = PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "met down")
        val cleveland = provider("cleveland", clevelandFailed)
        val met = provider("met", metFailed)

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(size = 20)

        assertTrue(outcome is LoadOutcome.Failed)
        val reason = (outcome as LoadOutcome.Failed).reason
        assertTrue("expected both reasons, got: $reason", reason.contains("cleveland down") && reason.contains("met down"))
    }

    @Test
    fun `an already-exhausted provider is skipped and never fetched`() = runTest {
        cursorDao.put(ProviderCursor(providerId = "cleveland", next = null))
        val metPage = PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK)
        val cleveland = provider("cleveland", metPage)
        val met = provider("met", metPage)

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(size = 20)

        assertEquals(LoadOutcome.Loaded, outcome)
        assertEquals(0, cleveland.callCount)
    }

    @Test
    fun `a failed fetch never persists the exhaustion marker`() = runTest {
        val failed = PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "down")
        val met = provider("met", failed)

        val repository = repository(setOf(met))
        repository.loadMore(size = 20)

        // ProviderCursor(id, null) is the exhaustion marker. A FAILED page
        // must never write it, or the provider is skipped forever.
        assertNull(cursorDao.get("met"))
    }

    @Test
    fun `insert and cursor write are one transaction, so a failed insert leaves no cursor behind`() = runTest {
        val page = PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK)
        val met = provider("met", page)
        val throwingDao = ThrowingInsertArtworkDao(artworkDao)

        val repository = repository(setOf(met), artworkDaoOverride = throwingDao)

        try {
            repository.loadMore(size = 20)
            fail("expected insertAll's exception to propagate")
        } catch (e: IllegalStateException) {
            // expected — insertAll() throws by design in this test double.
        }

        // If insert() and cursorDao.put() weren't in one transaction, the
        // cursor write (which runs after insert in source order) could
        // still have landed even though the insert above threw.
        assertNull(cursorDao.get("met"))
        assertEquals(0, artworkDao.count())
    }

    @Test
    @Ignore(
        "KNOWN BUG (README \"Deferred, in order\" item 5, \"Mixed-provider " +
            "failure is silent\"): when one provider fails and another then " +
            "succeeds within the same loadMore() call, the outcome is the " +
            "bare LoadOutcome.Loaded singleton — the failure is computed " +
            "into `failures` but never survives the early return on success " +
            "(ArtworkRepositoryImpl.kt, inside the round-robin loop). " +
            "Un-ignore once loadMore() surfaces that failure somehow (e.g. a " +
            "field on LoadOutcome.Loaded, or a separate signal) instead of " +
            "returning the bare Loaded object.",
    )
    fun `a provider failure is not silently lost when a later provider succeeds in the same call`() = runTest {
        // sorted = [cleveland, met]; turn = 1 starts the round at met, so
        // met fails first and cleveland succeeds second, in that order.
        coEvery { turnSource.getTurn() } returns 1
        val met = provider("met", PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "met down"))
        val cleveland = provider("cleveland", PageResult(listOf(sampleArtwork("cleveland:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(size = 20)

        // Correct behavior: met's failure must be observable somehow, even
        // though cleveland's success means the user still sees new
        // artworks. As written today `outcome` is exactly the bare Loaded
        // singleton, so this fails.
        assertNotEquals(LoadOutcome.Loaded, outcome)
    }
}

/**
 * Delegates every ArtworkDao call to a real Room-backed DAO except
 * insertAll, which always throws — used only to prove that a mid-transaction
 * failure rolls back the cursor write too (see the atomicity test above).
 */
private class ThrowingInsertArtworkDao(private val delegate: ArtworkDao) : ArtworkDao by delegate {
    override suspend fun insertAll(items: List<ArtworkEntity>) {
        throw IllegalStateException("insert failed on purpose")
    }
}
