package com.kg.museumly.feature.scroll.presentation

import app.cash.turbine.test
import com.kg.museumly.data.local.FeedPositionSource
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.testutil.FakeArtworkPrefetcher
import com.kg.museumly.testutil.FakeArtworkRepository
import com.kg.museumly.testutil.FakeNetworkMonitor
import com.kg.museumly.testutil.MainDispatcherRule
import com.kg.museumly.testutil.sampleArtwork
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
 * while one is already in flight.
 *
 * repository/prefetcher/networkMonitor are hand-written fakes (real
 * ArtworkRepository/ArtworkPrefetcher/NetworkMonitor interfaces, trivial to
 * hold state for). positionStore stays MockK: FeedPositionSource is a final
 * class whose constructor needs a real android.content.Context, so a fake
 * subclass would still need a real or mocked Context just to compile — a
 * mock avoids that entirely.
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
    private lateinit var positionStore: FeedPositionSource
    private lateinit var prefetcher: FakeArtworkPrefetcher
    private lateinit var networkMonitor: FakeNetworkMonitor

    @Before
    fun setUp() {
        repository = FakeArtworkRepository()

        positionStore = mockk()
        coEvery { positionStore.getFrontier() } returns 0
        coEvery { positionStore.setFrontier(any()) } just Runs

        prefetcher = FakeArtworkPrefetcher()
        networkMonitor = FakeNetworkMonitor(initiallyOnline = true)
    }

    private fun buildViewModel(): ScrollViewModel {
        return ScrollViewModel(repository, positionStore, prefetcher, networkMonitor)
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
        coEvery { positionStore.getFrontier() } returns 100
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
}
