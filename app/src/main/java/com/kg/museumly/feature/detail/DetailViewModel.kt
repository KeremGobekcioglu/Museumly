package com.kg.museumly.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.FeedPositionSourceInterface
import com.kg.museumly.domain.model.ArtworkWithDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ArtworkRepository,
    private val positionStore: FeedPositionSourceInterface,
) : ViewModel()
{
    // key = DetailPage.artworkId, filled in by Navigation. Read by name so
    // this ViewModel doesn't depend on the route class.
    private val artworkId: String = checkNotNull(savedStateHandle["artworkId"])

    private val _uiState = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load()
    {
        viewModelScope.launch {
            val result: ArtworkWithDetail? = repository.artworkWithDetail(artworkId)
            val hasInspected: Boolean = positionStore.hasInspected()
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
                    showInspectHint = !hasInspected,
                )
            }
        }
    }

    /**
     * Called wherever the screen enters inspect mode. Idempotent: once the
     * hint is off, repeat calls (a pinch fires this every frame of the
     * gesture) are no-ops.
     */
    fun onInspected()
    {
        if (!_uiState.value.showInspectHint) {
            return
        }
        _uiState.value = _uiState.value.copy(showInspectHint = false)
        positionStore.setInspected()
    }
}