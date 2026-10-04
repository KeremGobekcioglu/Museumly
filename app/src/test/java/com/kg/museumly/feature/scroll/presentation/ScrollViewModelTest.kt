package com.kg.museumly.feature.scroll.presentation

import app.cash.turbine.test
import com.kg.museumly.domain.domain.FeedPositionSourceInterface
import com.kg.museumly.domain.domain.LoadOutcome
import com.kg.museumly.testutil.FakeArtworkPrefetcher
import com.kg.museumly.testutil.FakeArtworkRepository
import com.kg.museumly.testutil.FakeNetworkMonitor
import com.kg.museumly.testutil.MainDispatcherRule
import com.kg.museumly.domain.model.Section
import com.kg.museumly.testutil.sampleArtwork
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Protects ScrollViewModel's state machine: the initial load must happen in
 * init (not a LaunchedEffect — README explains why), TailState must move
 * Idle -> Loading -> Idle/Failed/Exhausted and never get stuck, and the
 * overlap guard must stop a second loadMore from starting a second fetch
 * while one is already in flight. Also covers the last-viewed section: the
 * feed opens on whatever getLastSection() returns, nothing loads before that
 * read finishes, and only a user's selectSection() writes it back.
 *
 * repository/prefetcher/networkMonitor are hand-written fakes (real
 * ArtworkRepository/ArtworkPrefetcher/NetworkMonitor interfaces, trivial to
 * hold state for). positionStore is a MockK mock of the
 * FeedPositionSourceInterface interface, because the tests verify exact
 * setFrontier/setLastSection calls with coVerify.
 *
 * Robolectric-backed (rather than isReturnDefaultValues) because
 * ScrollViewModel calls android.util.Log.d directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeArtworkRepository
    private lateinit var positionStore: FeedPositionSourceInterface
    private lateinit var prefetcher: FakeArtworkPrefetcher
    private lateinit var networkMonitor: FakeNetworkMonitor

    @Before
    fun setUp() {
        repository = FakeArtworkRepository()

        positionStore = mockk()
        coEvery { positionStore.getFrontier(any()) } returns 0
        coEvery { positionStore.setFrontier(any(), any()) } just Runs
        // First launch: nothing stored, so the feed opens on EUROPEAN.
        coEvery { positionStore.getLastSection() } returns Section.EUROPEAN
        coEvery { positionStore.setLastSection(any()) } just Runs

        prefetcher = FakeArtworkPrefetcher()
        networkMonitor = FakeNetworkMonitor(initiallyOnline = true)
    }

    private fun buildViewModel(): ScrollViewModel {
        return ScrollViewModel(repository, positionStore, prefetcher, networkMonitor)
    }

    // uiState is WhileSubscribed — keep a collector alive for the whole test
    // so .value reflects settled state and onPageChanged reads the real section.
    private fun TestScope.collect(viewModel: ScrollViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun `initial load happens in init when the repository is empty`() = runTest {
        repository.countValue = 0

        buildViewModel()
        advanceUntilIdle()

        assertEquals(1, repository.loadMoreCallCount)
    }

    @Test
    fun `existing data skips the initial load and tail settles at idle`() = runTest {
        repository.countValue = 5

        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(0, repository.loadMoreCallCount)
        // uiState is WhileSubscribed(5_000) — its combine() only runs while
        // something is collecting, so .value only reflects settled state
        // once a collector exists. Turbine provides that collector.
        viewModel.uiState.test {
            assertEquals(TailState.Idle, awaitItem().tail)
        }
    }

    @Test
    fun `loadMore transitions tail from idle to loading to idle on success`() = runTest {
        repository.countValue = 5
        repository.loadMoreDelayMs = 100
        repository.loadMoreOutcome = LoadOutcome.Loaded

        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(TailState.Idle, awaitItem().tail)

            viewModel.loadMore()
            assertEquals(TailState.Loading, awaitItem().tail)

            advanceUntilIdle()
            assertEquals(TailState.Idle, awaitItem().tail)
        }
    }

    @Test
    fun `loadMore transitions tail to Failed when the repository throws`() = runTest {
        repository.countValue = 5
        repository.loadMoreDelayMs = 100
        repository.loadMoreThrows = IllegalStateException("boom")

        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(TailState.Idle, awaitItem().tail)

            viewModel.loadMore()
            assertEquals(TailState.Loading, awaitItem().tail)

            advanceUntilIdle()
            val failed = awaitItem().tail
            assertTrue(failed is TailState.Failed)
        }
    }

    @Test
    fun `a second loadMore call while one is active does not start another fetch`() = runTest {
        repository.countValue = 5
        repository.loadMoreDelayMs = 200

        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        // loadJob is still active (mid-delay) — this call must be dropped,
        // not queued, per the Job?.isActive overlap guard.
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(1, repository.loadMoreCallCount)
    }

    @Test
    @Ignore(
        "KNOWN BUG: ScrollViewModel's frontier restore (init block) only " +
            "clamps the lower bound (start < 0 -> 0). It never clamps to the " +
            "artwork count actually available, so a stored frontier larger " +
            "than what's in Room hands the pager an initialPage past the end " +
            "of the list. Un-ignore once ScrollViewModel.kt's init block also " +
            "clamps start to (existing count - 1).",
    )
    fun `frontier restore clamps to the existing artwork count, not just to zero`() = runTest {
        val artworks = listOf(sampleArtwork("met:1"))
        coEvery { positionStore.getFrontier(any()) } returns 100
        repository.countValue = 1
        repository.setArtworks(artworks)

        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val initialPage = awaitItem().initialPage
            assertTrue(
                "initialPage ($initialPage) must not exceed the last real index (${artworks.size - 1})",
                initialPage != null && initialPage <= artworks.size - 1,
            )
        }
    }

    @Test
    fun `selecting a section loads that section`() = runTest {
        repository.countValue = 0

        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.selectSection(Section.ASIA)
        advanceUntilIdle()

        assertEquals(listOf(Section.EUROPEAN, Section.ASIA), repository.loadMoreSections)
    }

    @Test
    fun `selecting the section already on screen does nothing`() = runTest {
        repository.countValue = 0

        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.selectSection(Section.EUROPEAN)
        advanceUntilIdle()

        assertEquals(listOf(Section.EUROPEAN), repository.loadMoreSections)
        coVerify(exactly = 0) { positionStore.setLastSection(any()) }
    }

    @Test
    fun `switching sections cancels the old section's in-flight load`() = runTest {
        repository.countValue = 0
        repository.loadMoreDelayMs = 200

        val viewModel = buildViewModel()
        runCurrent()
        // European's load is mid-delay. Without the cancel in selectSection,
        // the overlap guard would drop Asia's load as "already loading".
        viewModel.selectSection(Section.ASIA)
        advanceUntilIdle()

        assertEquals(listOf(Section.EUROPEAN, Section.ASIA), repository.loadMoreSections)
    }

    @Test
    fun `initialPage is only exposed alongside the section it was computed for`() = runTest {
        repository.countValue = 20
        coEvery { positionStore.getFrontier(Section.EUROPEAN) } returns 12
        coEvery { positionStore.getFrontier(Section.ASIA) } returns 0

        val viewModel = buildViewModel()
        collect(viewModel)
        advanceUntilIdle()
        assertEquals(Section.EUROPEAN, viewModel.uiState.value.section)
        assertEquals(10, viewModel.uiState.value.initialPage)

        viewModel.selectSection(Section.ASIA)
        advanceUntilIdle()

        // Never European's 10 on Asia's list.
        assertEquals(Section.ASIA, viewModel.uiState.value.section)
        assertEquals(0, viewModel.uiState.value.initialPage)
    }

    @Test
    fun `initialPage is clamped to the last artwork when the frontier is past the list`() = runTest {
        repository.countValue = 5
        coEvery { positionStore.getFrontier(Section.EUROPEAN) } returns 12

        val viewModel = buildViewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.initialPage)
    }

    @Test
    fun `frontier is saved against the section on screen`() = runTest {
        repository.countValue = 5

        val viewModel = buildViewModel()
        collect(viewModel)
        advanceUntilIdle()
        viewModel.selectSection(Section.ASIA)
        advanceUntilIdle()

        viewModel.onPageChanged(3)
        advanceUntilIdle()

        coVerify(exactly = 1) { positionStore.setFrontier(Section.ASIA, 3) }
        coVerify(exactly = 0) { positionStore.setFrontier(Section.EUROPEAN, any()) }
    }

    @Test
    fun `launch opens the last viewed section`() = runTest {
        coEvery { positionStore.getLastSection() } returns Section.ASIA
        repository.countValue = 0

        val viewModel = buildViewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertEquals(Section.ASIA, viewModel.uiState.value.section)
        // No European load first — the guessed default never reaches the repository.
        assertEquals(listOf(Section.ASIA), repository.loadMoreSections)
    }

    @Test
    fun `launch does not write back the section it just restored`() = runTest {
        buildViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { positionStore.setLastSection(any()) }
    }

    @Test
    fun `selecting a section saves it as the last viewed`() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.selectSection(Section.ASIA)
        advanceUntilIdle()

        coVerify(exactly = 1) { positionStore.setLastSection(Section.ASIA) }
    }

    @Test
    fun `nothing loads or shows until the saved section is read`() = runTest {
        val saved = CompletableDeferred<Section>()
        coEvery { positionStore.getLastSection() } coAnswers { saved.await() }
        repository.countValue = 0

        val viewModel = buildViewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.section)
        viewModel.loadMore()
        viewModel.onPageChanged(0)
        advanceUntilIdle()
        assertEquals(0, repository.loadMoreCallCount)
        coVerify(exactly = 0) { positionStore.setFrontier(any(), any()) }

        saved.complete(Section.ASIA)
        advanceUntilIdle()

        assertEquals(Section.ASIA, viewModel.uiState.value.section)
        assertEquals(listOf(Section.ASIA), repository.loadMoreSections)
    }

    @Test
    fun `entering a section prefetches the image of the page the pager opens on`() = runTest {
        // Frontier 3 opens the pager two pages back, on index 1.
        coEvery { positionStore.getFrontier(Section.EUROPEAN) } returns 3
        val artworks = listOf(sampleArtwork("met:1"), sampleArtwork("met:2"), sampleArtwork("met:3"))
        repository.setArtworks(artworks)
        repository.countValue = artworks.size

        buildViewModel()
        advanceUntilIdle()

        assertEquals(listOf(artworks[1].imageUrl), prefetcher.prefetchedBatches.first())
    }
}
