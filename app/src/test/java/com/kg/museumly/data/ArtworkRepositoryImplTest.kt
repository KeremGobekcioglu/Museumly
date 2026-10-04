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
import com.kg.museumly.domain.model.Section
import com.kg.museumly.testutil.FakeArtworkProvider
import com.kg.museumly.testutil.sampleArtwork
import com.kg.museumly.testutil.sampleDetail
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.first
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
        coEvery { turnSource.getTurn(any()) } returns 0
        coEvery { turnSource.setTurn(any(), any()) } just Runs
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

        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

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
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals(LoadOutcome.Loaded, outcome)
        // met is index 1 in the sorted [cleveland, met] list, so the next
        // turn should be (1 + 1) % 2 = 0, not 2 or unchanged.
        coVerify(exactly = 1) { turnSource.setTurn(0 , Section.EUROPEAN) }
    }

    @Test
    fun `turn does not advance when a page comes back OK but empty`() = runTest {
        // The mapper rejected the whole batch — status is OK (not FAILED),
        // but there's nothing to show, so this must not count as progress.
        val emptyOkPage = PageResult(emptyList(), emptyList(), next = "5", status = PageStatus.OK)
        val cleveland = provider("cleveland", emptyOkPage)
        val met = provider("met", emptyOkPage)

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals(LoadOutcome.Exhausted, outcome)
        coVerify(exactly = 0) { turnSource.setTurn(any() , any()) }
    }

    @Test
    fun `all-provider failure joins every failure reason, not just the last one`() = runTest {
        val clevelandFailed = PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "cleveland down")
        val metFailed = PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "met down")
        val cleveland = provider("cleveland", clevelandFailed)
        val met = provider("met", metFailed)

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertTrue(outcome is LoadOutcome.Failed)
        val reason = (outcome as LoadOutcome.Failed).reason
        assertTrue("expected both reasons, got: $reason", reason.contains("cleveland down") && reason.contains("met down"))
    }

    @Test
    fun `an already-exhausted provider is skipped and never fetched`() = runTest {
        cursorDao.put(ProviderCursor(providerId = "cleveland:all", next = null))
        val metPage = PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK)
        val cleveland = provider("cleveland", metPage)
        val met = provider("met", metPage)

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals(LoadOutcome.Loaded, outcome)
        assertEquals(0, cleveland.callCount)
    }

    @Test
    fun `a failed fetch never persists the exhaustion marker`() = runTest {
        val failed = PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "down")
        val met = provider("met", failed)

        val repository = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20)

        // ProviderCursor(id, null) is the exhaustion marker. A FAILED page
        // must never write it, or the provider is skipped forever.
        assertNull(cursorDao.get("met:all"))
    }

    @Test
    fun `insert and cursor write are one transaction, so a failed insert leaves no cursor behind`() = runTest {
        val page = PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK)
        val met = provider("met", page)
        val throwingDao = ThrowingInsertArtworkDao(artworkDao)

        val repository = repository(setOf(met), artworkDaoOverride = throwingDao)

        try {
            repository.loadMore(Section.EUROPEAN, size = 20)
            fail("expected insertAll's exception to propagate")
        } catch (e: IllegalStateException) {
            // expected — insertAll() throws by design in this test double.
        }

        // If insert() and cursorDao.put() weren't in one transaction, the
        // cursor write (which runs after insert in source order) could
        // still have landed even though the insert above threw.
        assertNull(cursorDao.get("met:all"))
        assertEquals(0, artworkDao.count(Section.EUROPEAN.id))
    }

    @Test
    fun `sources alternate providers, each walking its own departments in order`() = runTest {
        // sources interleave per round: [cleveland:a, met:11, cleveland:b, met:11]; turn = 2 lands on cleveland:b.
        coEvery { turnSource.getTurn(Section.EUROPEAN) } returns 2
        val page = PageResult(listOf(sampleArtwork("cleveland:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK)
        val cleveland = FakeArtworkProvider("cleveland") { listOf("a", "b") }
        cleveland.enqueue(page)
        val met = FakeArtworkProvider("met") { listOf("11") }

        val repository = repository(linkedSetOf(met, cleveland))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals(LoadOutcome.Loaded, outcome)
        assertEquals(listOf("b"), cleveland.requestedDepartments)
        assertEquals(0, met.callCount)
        // cleveland:b is index 2 of 4, so the next turn is 3 (met:11).
        coVerify(exactly = 1) { turnSource.setTurn(3, Section.EUROPEAN) }
    }

    @Test
    fun `museums get equal turns even when one has fewer departments`() = runTest {
        rememberTurns()
        val cleveland = FakeArtworkProvider("cleveland") { listOf("a", "b") }
        val met = FakeArtworkProvider("met") { listOf("11") }
        repeat(2) {
            cleveland.enqueue(PageResult(listOf(sampleArtwork("cleveland:$it")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
            met.enqueue(PageResult(listOf(sampleArtwork("met:$it")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
        }

        val repository = repository(setOf(cleveland, met))
        repeat(4) { repository.loadMore(Section.EUROPEAN, size = 20) }

        // Met repeats its one department instead of Cleveland getting 2 of every 3 pages.
        assertEquals(listOf("a", "b"), cleveland.requestedDepartments)
        assertEquals(listOf("11", "11"), met.requestedDepartments)
    }

    @Test
    fun `an exhausted department gets no slot, so it can't pass its turn to the other museum`() = runTest {
        // Live list is [cleveland:b, met:11]. If cleveland:a still held a
        // slot, the list would be [cleveland:a, met:11, cleveland:b, met:11]
        // and met:11 at index 1 would hand the turn to 2 instead of 0.
        cursorDao.put(ProviderCursor(providerId = "cleveland:a", next = null))
        coEvery { turnSource.getTurn(Section.EUROPEAN) } returns 1
        val cleveland = FakeArtworkProvider("cleveland") { listOf("a", "b") }
        val met = FakeArtworkProvider("met") { listOf("11") }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals(LoadOutcome.Loaded, outcome)
        assertEquals(listOf("11"), met.requestedDepartments)
        coVerify(exactly = 1) { turnSource.setTurn(0, Section.EUROPEAN) }
    }

    @Test
    fun `a department listed twice is fetched only once per call`() = runTest {
        // sources = [cleveland:a, met:11, cleveland:b, met:11]; every fetch fails.
        val failed = PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "down")
        val cleveland = FakeArtworkProvider("cleveland") { listOf("a", "b") }
        cleveland.enqueue(failed)
        cleveland.enqueue(failed)
        val met = FakeArtworkProvider("met") { listOf("11") }
        met.enqueue(failed)

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertTrue(outcome is LoadOutcome.Failed)
        // A failing department must cost one timeout per load, not one per slot.
        assertEquals(1, met.callCount)
        assertEquals(listOf("a", "b"), cleveland.requestedDepartments)
    }

    @Test
    fun `each section keeps its own turn`() = runTest {
        rememberTurns()
        val cleveland = FakeArtworkProvider("cleveland") { section -> listOf(section.id) }
        val met = FakeArtworkProvider("met") { section -> listOf(section.id) }
        cleveland.enqueue(PageResult(listOf(sampleArtwork("cleveland:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
        cleveland.enqueue(PageResult(listOf(sampleArtwork("cleveland:2")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(cleveland, met))
        repository.loadMore(Section.EUROPEAN, size = 20)
        repository.loadMore(Section.ASIA, size = 20)

        // European moved its turn to met; Asia still starts at its own 0 (cleveland).
        assertEquals(listOf(Section.EUROPEAN.id, Section.ASIA.id), cleveland.requestedDepartments)
        assertEquals(0, met.callCount)
        coVerify(exactly = 1) { turnSource.setTurn(1, Section.EUROPEAN) }
        coVerify(exactly = 1) { turnSource.setTurn(1, Section.ASIA) }
    }

    /**
     * Makes turnSource behave like the real per-section store, so a test can
     * run several loadMore() calls and see the rotation carry over.
     */
    private fun rememberTurns() {
        val turns: MutableMap<Section, Int> = HashMap()
        coEvery { turnSource.getTurn(any()) } answers { turns[firstArg<Section>()] ?: 0 }
        coEvery { turnSource.setTurn(any(), any()) } answers { turns[secondArg<Section>()] = firstArg<Int>() }
    }

    @Test
    fun `an exhausted department skips only itself, not its provider's other departments`() = runTest {
        cursorDao.put(ProviderCursor(providerId = "met:11", next = null))
        val met = FakeArtworkProvider("met") { listOf("11", "12") }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(met))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals(LoadOutcome.Loaded, outcome)
        assertEquals(listOf("12"), met.requestedDepartments)
    }

    @Test
    fun `cursor is stored under provider-colon-department, one row per department`() = runTest {
        val met = FakeArtworkProvider("met") { listOf("11") }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "7", status = PageStatus.OK))

        val repository = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals("7", cursorDao.get("met:11")?.next)
        // The old per-provider key must not be written anymore.
        assertNull(cursorDao.get("met"))
    }

    @Test
    fun `a section no provider covers is exhausted without fetching anything`() = runTest {
        val met = FakeArtworkProvider("met") { emptyList() }

        val repository = repository(setOf(met))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals(LoadOutcome.Exhausted, outcome)
        assertEquals(0, met.callCount)
    }

    @Test
    fun `positions count within a section, so each section starts at zero`() = runTest {
        val met = FakeArtworkProvider("met") { section -> listOf(section.id) }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1"), sampleArtwork("met:2")), listOf(sampleDetail(), sampleDetail()), next = "2", status = PageStatus.OK))
        met.enqueue(PageResult(listOf(sampleArtwork("met:3")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20)
        repository.loadMore(Section.ASIA, size = 20)

        val european = artworkDao.observeBySection(Section.EUROPEAN.id).first()
        val asia = artworkDao.observeBySection(Section.ASIA.id).first()
        assertEquals(listOf(0, 1), european.map { it.position })
        assertEquals(listOf(0), asia.map { it.position })
        assertEquals(listOf("met:3"), asia.map { it.id })
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
        coEvery { turnSource.getTurn(Section.EUROPEAN) } returns 1
        val met = provider("met", PageResult(emptyList(), emptyList(), next = null, status = PageStatus.FAILED, failureReason = "met down"))
        val cleveland = provider("cleveland", PageResult(listOf(sampleArtwork("cleveland:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

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
