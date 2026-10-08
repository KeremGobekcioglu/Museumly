package com.kg.museumly.presentation.feature.favorites


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Section
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repo: ArtworkRepository
) : ViewModel()
{
    private val _uiState = MutableStateFlow(FavoritesUIState())
    val uiState : StateFlow<FavoritesUIState> = _uiState.asStateFlow()

    private val _effects = Channel<FavoritesEffect>(Channel.BUFFERED)
    val effects : Flow<FavoritesEffect> = _effects.receiveAsFlow()
    private var loadJob: Job? = null
    init {
        loadFavorites(_uiState.value.section)
    }

    private fun loadFavorites(section: Section?)
    {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repo.getFavorites(section).collect { artworks: List<Artwork> ->
                _uiState.update {
                    val ordered : List<Artwork> = if(it.newestFirst) artworks else artworks.reversed()
                    it.copy(artworks = ordered, isLoading = false)
                }
            }
        }
    }

    fun onIntent(intent: FavoritesIntent)
    {
        when(intent)
        {
            is FavoritesIntent.ArtworkClicked -> {
                _effects.trySend(FavoritesEffect.OpenDetail(intent.artworkId))
            }
            is FavoritesIntent.SectionPicked -> {
                _uiState.update { it.copy(section = intent.section) }
                loadFavorites(intent.section)
            }
            FavoritesIntent.SortToggled -> {
                _uiState.update { it.copy(
                    artworks = it.artworks.reversed(),
                    newestFirst = !it.newestFirst
                ) }
            }
        }
    }
}