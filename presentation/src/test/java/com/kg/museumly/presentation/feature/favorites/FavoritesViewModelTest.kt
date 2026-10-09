package com.kg.museumly.presentation.feature.favorites

import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Section
import com.kg.museumly.presentation.testutil.FakeArtworkRepository
import com.kg.museumly.presentation.testutil.MainDispatcherRule
import com.kg.museumly.presentation.testutil.sampleArtwork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Protects FavoritesViewModel: the first repository emission fills the list,
 * the sort order is a user choice that must survive later emissions, and
 * switching sections must stop listening to the old section's flow.
 *
 * No Robolectric: the ViewModel touches no Android APIs, only
 * viewModelScope, which MainDispatcherRule covers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeArtworkRepository

    // The repository always delivers newest favorite first.
    private val newestFirst: List<Artwork> = listOf(sampleArtwork("met:3"), sampleArtwork("met:2"), sampleArtwork("met:1"))

    @Before
    fun setUp() {
        repository = FakeArtworkRepository()
    }

    private fun buildViewModel(): FavoritesViewModel {
        return FavoritesViewModel(repository)
    }

    private fun ids(artworks: List<Artwork>): List<String> {
        return artworks.map { it.id }
    }

    @Test
    fun `first emission fills the list and ends loading`() = runTest {
        repository.setFavorites(newestFirst)

        val viewModel: FavoritesViewModel = buildViewModel()
        advanceUntilIdle()

        val state: FavoritesUIState = viewModel.uiState.value
        assertEquals(ids(newestFirst), ids(state.artworks))
        assertFalse(state.isLoading)
    }

    @Test
    fun `sort toggle reverses the list and a second toggle restores it`() = runTest {
        repository.setFavorites(newestFirst)
        val viewModel: FavoritesViewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onIntent(FavoritesIntent.SortToggled)
        assertEquals(listOf("met:1", "met:2", "met:3"), ids(viewModel.uiState.value.artworks))
        assertFalse(viewModel.uiState.value.newestFirst)

        viewModel.onIntent(FavoritesIntent.SortToggled)
        assertEquals(ids(newestFirst), ids(viewModel.uiState.value.artworks))
        assertTrue(viewModel.uiState.value.newestFirst)
    }

    @Test
    fun `oldest first survives a new emission from the repository`() = runTest {
        repository.setFavorites(newestFirst)
        val viewModel: FavoritesViewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.onIntent(FavoritesIntent.SortToggled)

        // A new favorite arrives; the repository still sends it newest first.
        repository.setFavorites(listOf(sampleArtwork("met:4")) + newestFirst)
        advanceUntilIdle()

        assertEquals(listOf("met:1", "met:2", "met:3", "met:4"), ids(viewModel.uiState.value.artworks))
        assertFalse(viewModel.uiState.value.newestFirst)
    }

    @Test
    fun `switching section stops listening to the previous section`() = runTest {
        val european: List<Artwork> = listOf(sampleArtwork("met:eu"))
        repository.setFavorites(european, Section.EUROPEAN)
        val viewModel: FavoritesViewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onIntent(FavoritesIntent.SectionPicked(Section.EUROPEAN))
        advanceUntilIdle()

        // If the "all" collection were still alive, this would overwrite
        // the European list.
        repository.setFavorites(listOf(sampleArtwork("met:other")), section = null)
        advanceUntilIdle()

        val state: FavoritesUIState = viewModel.uiState.value
        assertEquals(ids(european), ids(state.artworks))
        assertEquals(Section.EUROPEAN, state.section)
    }

    @Test
    fun `artwork click sends OpenDetail and leaves the state alone`() = runTest {
        repository.setFavorites(newestFirst)
        val viewModel: FavoritesViewModel = buildViewModel()
        advanceUntilIdle()
        val before: FavoritesUIState = viewModel.uiState.value

        viewModel.onIntent(FavoritesIntent.ArtworkClicked("met:1"))

        assertEquals(FavoritesEffect.OpenDetail("met:1"), viewModel.effects.first())
        assertEquals(before, viewModel.uiState.value)
    }
}
