package com.kg.museumly.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.model.ArtworkWithDetail
import com.kg.museumly.navigation.DetailPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ArtworkRepository
) : ViewModel()
{
    private val route: DetailPage = savedStateHandle.toRoute()

    private val _uiState = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load()
    {
        viewModelScope.launch {
            val result: ArtworkWithDetail? = repository.artworkWithDetail(route.artworkId)
            if (result == null) {
                _uiState.value = DetailUiState(
                    data = null,
                    isLoading = false,
                    notFound = true,
                )
            } else {
                _uiState.value = DetailUiState(
                    data = result,
                    isLoading = false,
                    notFound = false,
                )
            }
        }
    }
}