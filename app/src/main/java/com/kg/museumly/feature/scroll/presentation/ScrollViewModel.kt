package com.kg.museumly.feature.scroll.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.data.local.FeedPositionSource
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.model.Artwork
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/***
 * stateIn converts a cold Flow into a hot StateFlow. Cold means each collector
 * triggers its own Room query; hot means one query, shared. It also gives the Flow a current value,
 * which Compose needs — a screen has to render something on first composition,
 * before Room has answered.
 *
 * WhileSubscribed(5_000) means the underlying Room query stays alive for five seconds
 * after the last collector goes away. That's the rotation window:
 * the screen is destroyed and recreated, the new collector reattaches to
 * the still-running query, and no re-query happens. Without it you'd tear down
 * and restart the query on every rotation. With SharingStarted.Eagerly
 * you'd instead never stop observing, burning a query even while the app is backgrounded.
 *
 * initialValue = emptyList() is what the screen sees for the first frame or two.
 * Watch for whether that empty frame is visible as a flash. If it is, the
 * fix is a nullable initial value so you can distinguish "not loaded yet"
 * from "genuinely nothing" — a spinner is usually the wrong answer for a gallery.
 *
 * The init block calls seedIfEmpty() in viewModelScope. It's in the
 * ViewModel rather than the repository's constructor because viewModelScope
 * cancels properly when the screen dies. Do it in an init on a @Singleton
 * and you need your own scope, and cancellation gets murky.
 */
@HiltViewModel
class ScrollViewModel @Inject constructor(
    private val repository: ArtworkRepository,
    private val positionStore: FeedPositionSource
) : ViewModel()
{
    private val isLoadingMore = MutableStateFlow(false)
    private val isInitialLoad = MutableStateFlow(true)
    private var loadJob: Job? = null
    private val error = MutableStateFlow<String?>(null)


    private val initialPage = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ScrollUiState> = combine(
        repository.artworks(),
        isLoadingMore,
        isInitialLoad,
        initialPage,
        error
    ){
            artworks: List<Artwork>, loading: Boolean, inital: Boolean, page: Int?, err: String? ->
        ScrollUiState(
            artworks= artworks,
            isLoadingMore = loading,
            isInitialLoad = inital,
            initialPage = page,
            error = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScrollUiState()
    )

    init {
        viewModelScope.launch {
            val stored: Int = positionStore.getFrontier()
            var start: Int = stored - 2
            if (start < 0) {
                start = 0
            }
            initialPage.value = start
        }

        viewModelScope.launch {
            // Ask the database directly. A Flow's first emission can't tell
            // "empty because loading" from "empty because empty" — a count query can.
            val existing: Int = repository.count()
            if (existing == 0) {
                loadMore()
            }
        }
    }
    fun loadMore()
    {
        Log.d("VM", "loadMore called, active=${loadJob?.isActive}")
        if (loadJob?.isActive == true) {
            Log.d("VM", "skipped, already loading")
            return
        }
        loadJob = viewModelScope.launch {
            isLoadingMore.value = true
            error.value = null
            try {
                val outcome = repository.loadMore()
                if (outcome == LoadOutcome.FAILED) {
                    error.value = "Couldn't load more artworks"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Couldn't load more artworks"
            } finally {
                isLoadingMore.value = false
                isInitialLoad.value = false
            }
        }
    }

    fun onPageChanged(page: Int) {
        viewModelScope.launch {
            positionStore.setFrontier(page)
        }
    }
}