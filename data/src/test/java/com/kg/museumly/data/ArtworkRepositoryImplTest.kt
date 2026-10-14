package com.kg.museumly.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kg.museumly.data.local.ArtworkDao
import com.kg.museumly.data.local.ArtworkEntity
import com.kg.museumly.data.local.MuseumDatabase
import com.kg.museumly.data.local.ProviderCursor
import com.kg.museumly.data.local.ProviderCursorDao
import com.kg.museumly.data.local.ProviderTurnSource
import com.kg.museumly.data.local.favorites.FavoriteEntity
import com.kg.museumly.data.local.favorites.FavoritesDao
import com.kg.museumly.data.local.placement.PlacementDao
import com.kg.museumly.data.testutil.FakeArtworkProvider
import com.kg.museumly.data.testutil.sampleArtwork
import com.kg.museumly.data.testutil.sampleDetail
import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

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

    private lateinit var favoritesDao: FavoritesDao
    private lateinit var placementDao: PlacementDao

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
        favoritesDao = database.favoritesDao()
        placementDao = database.placementDao()
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
            favoritesDao = favoritesDao,
            placementDao = placementDao
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
        coVerify(exactly = 1) { turnSource.setTurn(0, Section.EUROPEAN.id) }
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
        assertEquals(0, placementDao.count(Section.EUROPEAN.id))
        assertNull(artworkDao.byId("met:1"))
    }

    @Test
    fun `sources alternate providers, each walking its own departments in order`() = runTest {
        // sources interleave per round: [cleveland:a, met:11, cleveland:b, met:11]; turn = 2 lands on cleveland:b.
        coEvery { turnSource.getTurn(Section.EUROPEAN.id) } returns 2
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
        coVerify(exactly = 1) { turnSource.setTurn(3, Section.EUROPEAN.id) }
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
        coEvery { turnSource.getTurn(Section.EUROPEAN.id) } returns 1
        val cleveland = FakeArtworkProvider("cleveland") { listOf("a", "b") }
        val met = FakeArtworkProvider("met") { listOf("11") }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(cleveland, met))
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals(LoadOutcome.Loaded, outcome)
        assertEquals(listOf("11"), met.requestedDepartments)
        coVerify(exactly = 1) { turnSource.setTurn(0, Section.EUROPEAN.id) }
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
        coVerify(exactly = 1) { turnSource.setTurn(1, Section.EUROPEAN.id) }
        coVerify(exactly = 1) { turnSource.setTurn(1, Section.ASIA.id) }
    }

    /**
     * Makes turnSource behave like the real per-list store, so a test can
     * run several loadMore() calls and see the rotation carry over.
     * Returns the backing map, keyed by list key, so a test can read the turns.
     */
    private fun rememberTurns(): MutableMap<String, Int> {
        val turns: MutableMap<String, Int> = HashMap()
        coEvery { turnSource.getTurn(any()) } answers { turns[firstArg<String>()] ?: 0 }
        coEvery { turnSource.setTurn(any(), any()) } answers { turns[secondArg<String>()] = firstArg<Int>() }
        return turns
    }

    @Test
    fun `an era load leaves the feed's count, cursor row and turn unchanged`() = runTest {
        val turns: MutableMap<String, Int> = rememberTurns()
        val cleveland = FakeArtworkProvider("cleveland") { listOf("a") }
        val met = FakeArtworkProvider("met") { listOf("11") }
        cleveland.enqueue(PageResult(listOf(sampleArtwork("cleveland:1")), listOf(sampleDetail()), next = "2", status = PageStatus.OK))
        cleveland.enqueue(PageResult(listOf(sampleArtwork("cleveland:2")), listOf(sampleDetail()), next = "9", status = PageStatus.OK))

        val repository = repository(setOf(cleveland, met))
        repository.loadMore(Section.EUROPEAN, size = 20)
        repository.loadMore(Section.EUROPEAN, size = 20, era = Era.Y1600)

        // The feed is exactly as the first load left it.
        assertEquals(1, repository.count(Section.EUROPEAN))
        assertEquals("2", cursorDao.get("cleveland:a")?.next)
        assertEquals(1, turns[Section.EUROPEAN.id])
        coVerify(exactly = 1) { turnSource.setTurn(any(), Section.EUROPEAN.id) }

        // The era started from its own turn 0 (cleveland) and kept its own books.
        assertEquals(1, repository.count(Section.EUROPEAN, Era.Y1600))
        assertEquals("9", cursorDao.get("cleveland:a@1600s")?.next)
        assertEquals(1, turns["european@1600s"])
    }

    @Test
    fun `exhausting a department in one era does not remove it from the feed's rotation`() = runTest {
        val met = FakeArtworkProvider("met") { listOf("11") }
        // The era runs dry on its first page.
        met.enqueue(PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = null, status = PageStatus.OK))
        met.enqueue(PageResult(listOf(sampleArtwork("met:2")), listOf(sampleDetail()), next = "2", status = PageStatus.OK))

        val repository = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20, era = Era.Y1600)
        val feedOutcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20)
        val eraOutcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20, era = Era.Y1600)

        assertNotNull(cursorDao.get("met:11@1600s"))
        assertNull(cursorDao.get("met:11@1600s")?.next)
        // The feed still fetched met:11.
        assertEquals(LoadOutcome.Loaded, feedOutcome)
        assertEquals(listOf("11", "11"), met.requestedDepartments)
        assertEquals("2", cursorDao.get("met:11")?.next)
        // The era itself is done and fetches nothing more.
        assertEquals(LoadOutcome.Exhausted, eraOutcome)
        assertEquals(2, met.callCount)
    }

    @Test
    fun `two eras of one section have separate cursors and positions`() = runTest {
        val met = FakeArtworkProvider("met") { listOf("11") }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1"), sampleArtwork("met:2")), listOf(sampleDetail(), sampleDetail()), next = "2", status = PageStatus.OK))
        met.enqueue(PageResult(listOf(sampleArtwork("met:3")), listOf(sampleDetail()), next = "5", status = PageStatus.OK))

        val repository = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20, era = Era.Y1600)
        repository.loadMore(Section.EUROPEAN, size = 20, era = Era.Y1700)

        assertEquals("2", cursorDao.get("met:11@1600s")?.next)
        assertEquals("5", cursorDao.get("met:11@1700s")?.next)
        assertNull(cursorDao.get("met:11"))

        // Each era is its own list, numbered from zero.
        assertEquals(listOf("met:1", "met:2"), repository.artworks(Section.EUROPEAN, Era.Y1600).first().map { it.id })
        assertEquals(listOf("met:3"), repository.artworks(Section.EUROPEAN, Era.Y1700).first().map { it.id })
        assertEquals(1, placementDao.maxPosition("european@1600s"))
        assertEquals(0, placementDao.maxPosition("european@1700s"))
        assertEquals(0, repository.count(Section.EUROPEAN))
    }

    @Test
    fun `loadMore hands its era to the provider, and the feed hands null`() = runTest {
        val met = FakeArtworkProvider("met") { listOf("11") }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
        met.enqueue(PageResult(listOf(sampleArtwork("met:2")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20, era = Era.Y1600)
        repository.loadMore(Section.EUROPEAN, size = 20)

        assertEquals(listOf<Era?>(Era.Y1600, null), met.requestedEras)
    }

    @Test
    fun `loadTimeline fetches only eras whose list holds fewer than three works`() = runTest {
        // Only European has a department, so only the table's European eras
        // (1400s, 1600s, 1800s, 1900s) can reach the provider at all.
        val met = FakeArtworkProvider("met") { section ->
            if (section == Section.EUROPEAN) listOf("11") else emptyList()
        }
        met.enqueue(
            PageResult(
                listOf(sampleArtwork("met:1"), sampleArtwork("met:2"), sampleArtwork("met:3")),
                listOf(sampleDetail(), sampleDetail(), sampleDetail()),
                next = "3",
                status = PageStatus.OK
            )
        )
        met.enqueue(PageResult(listOf(sampleArtwork("met:4")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
        met.enqueue(PageResult(listOf(sampleArtwork("met:5")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
        met.enqueue(PageResult(listOf(sampleArtwork("met:6")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(met))
        // 1600s now holds three works.
        repository.loadMore(Section.EUROPEAN, size = 3, era = Era.Y1600)

        val outcome: LoadOutcome = repository.loadTimeline()

        assertEquals(LoadOutcome.Loaded, outcome)
        // After the setup call, 1600s is never asked for again; the other
        // European eras, still empty, each get one fetch.
        assertEquals(listOf<Era?>(Era.Y1600, Era.Y1400, Era.Y1800, Era.Y1900), met.requestedEras)
    }

    @Test
    fun `timelineStops gives at most three stops per era, in era order, with the table's section`() = runTest {
        // One department per section, so no two sections share a cursor.
        val met = FakeArtworkProvider("met") { section -> listOf(section.id) }
        met.enqueue(
            PageResult(
                listOf(sampleArtwork("met:e1"), sampleArtwork("met:e2"), sampleArtwork("met:e3"), sampleArtwork("met:e4")),
                listOf(sampleDetail(), sampleDetail(), sampleDetail(), sampleDetail()),
                next = "4",
                status = PageStatus.OK
            )
        )
        met.enqueue(
            PageResult(
                listOf(sampleArtwork("met:a1"), sampleArtwork("met:a2")),
                listOf(sampleDetail(), sampleDetail()),
                next = "2",
                status = PageStatus.OK
            )
        )
        met.enqueue(PageResult(listOf(sampleArtwork("met:p1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
        met.enqueue(PageResult(listOf(sampleArtwork("met:x1")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20, era = Era.Y1600)
        repository.loadMore(Section.ASIA, size = 20, era = Era.Y1700)
        repository.loadMore(Section.EGYPT_NEAR_EAST, size = 20, era = Era.PREHISTORIC)
        // The table gives 1700s to Asia, so European's 1700s list must not appear.
        repository.loadMore(Section.EUROPEAN, size = 20, era = Era.Y1700)

        val stops = repository.timelineStops().first()

        val actual: List<String> = stops.map { it.artwork.id + "|" + it.section + "|" + it.era }
        assertEquals(
            listOf(
                "met:p1|EGYPT_NEAR_EAST|PREHISTORIC",
                "met:e1|EUROPEAN|Y1600",
                "met:e2|EUROPEAN|Y1600",
                "met:e3|EUROPEAN|Y1600",
                "met:a1|ASIA|Y1700",
                "met:a2|ASIA|Y1700"
            ),
            actual
        )
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
    fun `each section is its own list, so each starts at position zero`() = runTest {
        val met = FakeArtworkProvider("met") { section -> listOf(section.id) }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1"), sampleArtwork("met:2")), listOf(sampleDetail(), sampleDetail()), next = "2", status = PageStatus.OK))
        met.enqueue(PageResult(listOf(sampleArtwork("met:3")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))

        val repository = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20)
        repository.loadMore(Section.ASIA, size = 20)

        val european = artworkDao.observeByList(Section.EUROPEAN.id).first()
        val asia = artworkDao.observeByList(Section.ASIA.id).first()
        assertEquals(listOf("met:1", "met:2"), european.map { it.id })
        assertEquals(listOf("met:3"), asia.map { it.id })
        assertEquals(1, placementDao.maxPosition(Section.EUROPEAN.id))
        assertEquals(0, placementDao.maxPosition(Section.ASIA.id))
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
        coEvery { turnSource.getTurn(Section.EUROPEAN.id) } returns 1
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

    @Test
    fun testThatFavoriteArtworkSurvivesLoadMOREDeliveringItself() = runTest {
        val met: FakeArtworkProvider = FakeArtworkProvider("met") { listOf("11") }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1"), sampleArtwork("met:2")), listOf(sampleDetail(), sampleDetail()), next = "2", status = PageStatus.OK))
        // The museum's list shifted: met:1 comes back on the next page.
        met.enqueue(PageResult(listOf(sampleArtwork("met:1")), listOf(sampleDetail()), next = "3", status = PageStatus.OK))
        val repository = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN,20)
        repository.setFavorite("met:1", true)
        repository.loadMore(Section.EUROPEAN,20)

        // The duplicate moves to the end of the feed instead of appearing twice.
        val feed = artworkDao.observeByList(Section.EUROPEAN.id).first()
        assertEquals(listOf("met:2", "met:1"), feed.map { it.id })
        assertTrue(repository.getFavoritesIds().first().contains("met:1"))
    }

    @Test
    fun `setFavorite true adds the id and false removes only that id`() = runTest {
        val repository: ArtworkRepositoryImpl = repository(emptySet())

        repository.setFavorite("met:1", true)
        repository.setFavorite("met:2", true)
        assertEquals(setOf("met:1", "met:2"), repository.getFavoritesIds().first())

        repository.setFavorite("met:1", false)
        assertEquals(setOf("met:2"), repository.getFavoritesIds().first())
    }

    @Test
    fun setFavoriteHASnothingToDoWithRemoteFetching() = runBlocking {
        val slow = GatedArtworkProvider("met")
        val repository: ArtworkRepositoryImpl = repository(setOf(slow))

        // A real thread, so loadMore really runs next to the test.
        val load: Job = launch(Dispatchers.Default) {
            repository.loadMore(Section.EUROPEAN, size = 20)
        }
        // loadMore now holds the mutex and is stuck inside fetchPage.
        slow.entered.await()

        // If setFavorite took the mutex it would hang here; this turns the hang into null.
        val finished: Unit? = withTimeoutOrNull(2_000.milliseconds) {
            repository.setFavorite("met:1", true)
        }
        assertNotNull("setFavorite waited for the load to finish", finished)
        assertTrue(load.isActive)
        assertTrue(repository.getFavoritesIds().first().contains("met:1"))

        slow.gate.complete(Unit)
        load.join()
    }

    @Test
    fun `getFavorites returns only favorited works, newest favorite first`() = runTest {
        val met: FakeArtworkProvider = FakeArtworkProvider("met") { listOf("11") }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1"), sampleArtwork("met:2"), sampleArtwork("met:3")), listOf(sampleDetail(), sampleDetail(), sampleDetail()), next = "3", status = PageStatus.OK))
        val repository: ArtworkRepositoryImpl = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20)

        // Fixed timestamps instead of setFavorite: it reads the system clock,
        // and two calls in the same millisecond have no defined order.
        favoritesDao.insert(FavoriteEntity("met:1", favoritedAt = 100))
        favoritesDao.insert(FavoriteEntity("met:3", favoritedAt = 200))

        val favorites: List<String> = repository.getFavorites(null).first().map { it.id }
        assertEquals(listOf("met:3", "met:1"), favorites)
    }

    @Test
    fun `getFavorites with a section returns only that section, null returns every section`() = runTest {
        val met: FakeArtworkProvider = FakeArtworkProvider("met") { section -> listOf(section.id) }
        met.enqueue(PageResult(listOf(sampleArtwork("met:eu")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
        met.enqueue(PageResult(listOf(sampleArtwork("met:asia")), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
        val repository: ArtworkRepositoryImpl = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20)
        repository.loadMore(Section.ASIA, size = 20)

        repository.setFavorite("met:eu", true)
        repository.setFavorite("met:asia", true)

        val european: List<String> = repository.getFavorites(Section.EUROPEAN).first().map { it.id }
        val all: Set<String> = repository.getFavorites(null).first().map { it.id }.toSet()
        assertEquals(listOf("met:eu"), european)
        assertEquals(setOf("met:eu", "met:asia"), all)
    }

    @Test
    fun `rememberAspectRatio fills an empty ratio and never overwrites an existing one`() = runTest {
        val met: FakeArtworkProvider = FakeArtworkProvider("met") { listOf("11") }
        // met:empty is what MetMapper produces; met:known stands in for a
        // provider that supplied real dimensions.
        met.enqueue(PageResult(listOf(sampleArtwork("met:empty").copy(aspectRatio = null), sampleArtwork("met:known").copy(aspectRatio = 0.5f)), listOf(sampleDetail(), sampleDetail()), next = "2", status = PageStatus.OK))
        val repository: ArtworkRepositoryImpl = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20)

        repository.rememberAspectRatio("met:empty", 1.5f)
        repository.rememberAspectRatio("met:empty", 2f)
        repository.rememberAspectRatio("met:known", 2f)

        assertEquals(1.5f, artworkDao.byId("met:empty")?.aspectRatio)
        assertEquals(0.5f, artworkDao.byId("met:known")?.aspectRatio)
    }

    @Test
    fun `a measured aspect ratio survives the same artwork arriving again`() = runTest {
        val met: FakeArtworkProvider = FakeArtworkProvider("met") { listOf("11") }
        met.enqueue(PageResult(listOf(sampleArtwork("met:1").copy(aspectRatio = null)), listOf(sampleDetail()), next = "1", status = PageStatus.OK))
        // The museum's list shifted: met:1 comes back, still without a ratio.
        met.enqueue(PageResult(listOf(sampleArtwork("met:1").copy(aspectRatio = null)), listOf(sampleDetail()), next = "2", status = PageStatus.OK))
        val repository: ArtworkRepositoryImpl = repository(setOf(met))
        repository.loadMore(Section.EUROPEAN, size = 20)
        repository.rememberAspectRatio("met:1", 1.5f)

        repository.loadMore(Section.EUROPEAN, size = 20)

        // With REPLACE the second insert rewrote the row with a null ratio.
        assertEquals(1.5f, artworkDao.byId("met:1")?.aspectRatio)
    }

    @Test
    fun `isExhausted is false before any load`() = runTest {
        val met = FakeArtworkProvider("met")
        val cleveland = FakeArtworkProvider("cleveland")
        val repository: ArtworkRepositoryImpl = repository(setOf(met, cleveland))

        // No cursor rows yet: a department never fetched is live.
        assertFalse(repository.isExhausted(Section.EUROPEAN, Era.Y1600))
        assertEquals(0, met.callCount)
        assertEquals(0, cleveland.callCount)
    }

    @Test
    fun `isExhausted is true once every source has returned next = null`() = runTest {
        val end = PageResult(emptyList(), emptyList(), next = null, status = PageStatus.EXHAUSTED)
        val met: FakeArtworkProvider = provider("met", end)
        val cleveland: FakeArtworkProvider = provider("cleveland", end)
        val repository: ArtworkRepositoryImpl = repository(setOf(met, cleveland))

        // Empty pages deliver nothing, so one load tries both sources and
        // writes both exhaustion markers.
        val outcome: LoadOutcome = repository.loadMore(Section.EUROPEAN, size = 20, era = Era.Y1600)

        assertEquals(LoadOutcome.Exhausted, outcome)
        assertTrue(repository.isExhausted(Section.EUROPEAN, Era.Y1600))
        // Exhaustion is per scope: the era ran out, the feed didn't.
        assertFalse(repository.isExhausted(Section.EUROPEAN))
    }

    /** A page of three works with ids unique to this prefix. */
    private fun threeWorks(prefix: String): PageResult {
        val items = listOf(sampleArtwork("met:$prefix-1"), sampleArtwork("met:$prefix-2"), sampleArtwork("met:$prefix-3"))
        val details = listOf(sampleDetail(), sampleDetail(), sampleDetail())
        return PageResult(items, details, next = "3", status = PageStatus.OK)
    }

    @Test
    fun `loadTimeline never fetches for the 2000s`() = runTest {
        val met = FakeArtworkProvider("met")
        // One page per timeline era, plus one spare: a 2000s fetch would get
        // the spare and show up in requestedEras instead of crashing the fake.
        for (i in 0..Era.entries.size) {
            met.enqueue(threeWorks("p$i"))
        }
        val repository: ArtworkRepositoryImpl = repository(setOf(met))

        repository.loadTimeline()

        assertFalse(met.requestedEras.contains(Era.Y2000))
        // Every other era was filled, so the 2000s wasn't skipped by accident.
        assertEquals(Era.entries.size - 1, met.requestedEras.size)
    }

    @Test
    fun `timelineStops has no 2000s stop even when the 2000s list holds works`() = runTest {
        val met = FakeArtworkProvider("met")
        met.enqueue(threeWorks("y2000"))
        met.enqueue(threeWorks("y1700"))
        val repository: ArtworkRepositoryImpl = repository(setOf(met))
        // The era page can fill asia@2000s; the timeline must still leave it out.
        repository.loadMore(Section.ASIA, size = 3, era = Era.Y2000)
        repository.loadMore(Section.ASIA, size = 3, era = Era.Y1700)

        val stops = repository.timelineStops().first()

        for (stop in stops) {
            assertNotEquals(Era.Y2000, stop.era)
        }
        // The 1700s works came through, so the flow itself works.
        assertEquals(3, stops.size)
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

/**
 * A provider whose fetchPage waits until the test opens the gate.
 * Lets a test hold loadMore in the middle of its network call.
 */
private class GatedArtworkProvider(override val id: String) : ArtworkProvider {
    val entered: CompletableDeferred<Unit> = CompletableDeferred()
    val gate: CompletableDeferred<Unit> = CompletableDeferred()

    override fun departmentsFor(section: Section): List<String> {
        return listOf("all")
    }

    override suspend fun fetchPage(cursor: String?, size: Int, department: String, era: Era?): PageResult {
        entered.complete(Unit)
        gate.await()
        return PageResult(emptyList(), emptyList(), cursor, PageStatus.FAILED, "gate opened")
    }
}