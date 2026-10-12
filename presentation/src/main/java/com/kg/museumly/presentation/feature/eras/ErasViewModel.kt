package com.kg.museumly.presentation.feature.eras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.domain.ArtworkPrefetcher
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.domain.model.TimelineStop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ErasViewModel @Inject constructor(
    private val repository: ArtworkRepository,
    private val prefetcher: ArtworkPrefetcher
) : ViewModel()
{
    private val _uiState = MutableStateFlow(ErasUiState())
    val state = _uiState.asStateFlow()

    private val _effects = Channel<ErasEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.timelineStops().collect {
                stops: List<TimelineStop> ->
                    _uiState.update { it.copy(stops = stops) }
            }
        }
        // Eras already filled are skipped inside loadTimeline, so opening the
        // screen again costs no network.
        load()
    }

    private fun load() {
        if(loadJob?.isActive == true) {
            return
        }
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            var message : String? = null
            try {
                val outcome : LoadOutcome = repository.loadTimeline()
                if(outcome is LoadOutcome.Failed)
                {
                    message = outcome.reason
                }
            }
            catch (e: CancellationException)
            {
                throw e
            }
            catch (e: Exception)
            {
                message = e.message ?: "Couldn't load the timeline"
            }
            _uiState.update { it.copy(isLoading = false, errorMessage = message) }
        }
    }

    fun onIntent(intent: ErasIntent)
    {
        when(intent)
        {
            is ErasIntent.ArtworkClicked -> {
                _effects.trySend(ErasEffect.OpenDetail(intent.artworkId))
            }
            is ErasIntent.MoreClicked -> {
                val stop: TimelineStop = _uiState.value.stops.getOrNull(intent.index) ?: return
                _effects.trySend(ErasEffect.OpenEra(stop.section, stop.era))
            }
            is ErasIntent.RetryClicked -> {
                load()
            }
            is ErasIntent.StopChanged -> {
                prefetchAround(intent.index)
            }
        }
    }

    /** Keeps the images on both sides of the slider warm, so sliding rarely shows a gap. */
    private fun prefetchAround(index: Int) {
        val stops: List<TimelineStop> = _uiState.value.stops
        val urls: MutableList<String> = ArrayList()
        for (offset in -2..2) {
            val stop: TimelineStop? = stops.getOrNull(index + offset)
            if (stop != null) {
                urls.add(stop.artwork.imageUrl)
            }
        }
        prefetcher.prefetch(urls)
    }
}