package com.kg.museumly.presentation.feature.eras.era

import androidx.lifecycle.SavedStateHandle
import com.kg.museumly.domain.EraPositionSourceInterface
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.presentation.feature.scroll.presentation.TailState
import com.kg.museumly.presentation.testutil.FakeArtworkPrefetcher
import com.kg.museumly.presentation.testutil.FakeArtworkRepository
import com.kg.museumly.presentation.testutil.FakeNetworkMonitor
import com.kg.museumly.presentation.testutil.MainDispatcherRule
import com.kg.museumly.presentation.testutil.sampleArtwork
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Protects the short-first-page case: Greek & Roman > Prehistory loads
 * 3 works, the grid reports once while that load still runs, and that
 * report is skipped. The load must still continue once it ends, and stop
 * once the end is out of reach.
 *
 * The fake's count is set by hand, standing in for what Room would hold
 * after each page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EraViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeArtworkRepository
    private lateinit var positionSource: EraPositionSourceInterface

    @Before
    fun setUp() {
        repository = FakeArtworkRepository()
        // A real suspension, so a report can arrive while a load runs.
        repository.loadMoreDelayMs = 1_000

        positionSource = mockk()
        coEvery { positionSource.getFrontier(any(), any()) } returns 0
        coEvery { positionSource.setFrontier(any(), any(), any()) } just Runs
    }

    private fun buildViewModel(): EraViewModel {
        val handle = SavedStateHandle(
            mapOf("eraId" to Era.PREHISTORIC.id, "sectionId" to Section.GREEK_ROMAN.id)
        )
        return EraViewModel(handle, repository, positionSource, FakeArtworkPrefetcher(), FakeNetworkMonitor())
    }

    // uiState is WhileSubscribed: keep a collector, like the screen does.
    private fun TestScope.collect(viewModel: EraViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    /**
     * The first page lands and the grid reports its 3 tiles (plus the tail)
     * while that load is still running.
     */
    private fun TestScope.firstPageReportedDuringLoad(viewModel: EraViewModel) {
        advanceTimeBy(500)
        val works = listOf(sampleArtwork("a"), sampleArtwork("b"), sampleArtwork("c"))
        repository.setArtworks(works, Section.GREEK_ROMAN)
        repository.countValue = 3
        viewModel.onIntent(EraIntent.Scrolled(firstVisible = 0, lastVisible = 3))
    }

    @Test
    fun `a report during the first load still leads to a second load once it ends`() = runTest {
        repository.countValue = 0
        val viewModel: EraViewModel = buildViewModel()
        collect(viewModel)
        firstPageReportedDuringLoad(viewModel)

        // The report was skipped: the first load is still running.
        assertEquals(1, repository.loadMoreCallCount)

        advanceTimeBy(501)
        runCurrent()

        assertEquals(2, repository.loadMoreCallCount)
        assertEquals(Section.GREEK_ROMAN, repository.loadMoreSections[1])
        // A real Loaded always adds works. Without this the fake's count
        // never grows, and runTest's final drain would keep the loop loading.
        repository.countValue = 23
    }

    @Test
    fun `no further load starts once the count is far past the last reported tile`() = runTest {
        repository.countValue = 0
        val viewModel: EraViewModel = buildViewModel()
        collect(viewModel)
        firstPageReportedDuringLoad(viewModel)
        advanceTimeBy(501)
        runCurrent()
        // The second page brings 20 more: 23 - 3 is far past LOAD_AHEAD.
        repository.countValue = 23

        advanceUntilIdle()

        assertEquals(2, repository.loadMoreCallCount)
        assertEquals(TailState.Idle, viewModel.uiState.value.tail)
    }
}
