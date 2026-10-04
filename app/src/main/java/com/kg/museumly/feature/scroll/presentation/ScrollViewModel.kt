package com.kg.museumly.feature.scroll.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.domain.domain.ArtworkPrefetcher
import com.kg.museumly.domain.domain.ArtworkRepository
import com.kg.museumly.domain.domain.FeedPositionSourceInterface
import com.kg.museumly.domain.domain.LoadOutcome
import com.kg.museumly.domain.domain.NetworkMonitor
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Section
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

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
    private val positionStore: FeedPositionSourceInterface,
    private val prefetcher: ArtworkPrefetcher,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private companion object {
        const val MIN_RETRY_VISIBLE_MS: Long = 600
    }

    private var loadJob: Job? = null
    private val tail = MutableStateFlow<TailState>(TailState.Loading)
    private val initialPage = MutableStateFlow<Pair<Section, Int>?>(null)
    private val section = MutableStateFlow<Section?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val feed: Flow<Pair<Section, List<Artwork>>> = section
        .filterNotNull()
        .flatMapLatest { current: Section ->
            repository.artworks(current).map { list: List<Artwork> ->
                Pair(current, list)
            }
        }
    val uiState: StateFlow<ScrollUiState> = combine(
        feed,
        initialPage,
        tail,
        networkMonitor.isOnline
    ) { feedValue: Pair<Section, List<Artwork>>, page: Pair<Section, Int>?, tailState: TailState, online: Boolean ->
        val feedSection: Section = feedValue.first
        var matchedPage: Int? = null
        if (page != null && page.first == feedSection) {
            matchedPage = page.second
        }
        ScrollUiState(
            section = feedSection,
            artworks = feedValue.second,
            initialPage = matchedPage,
            tail = tailState,
            isOnline = online
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScrollUiState()
    )

    init {
        viewModelScope.launch {
            val restoredSection = positionStore.getLastSection()
            // Defensive: nothing should set section before this. The section pill
            // only appears once section is set, so the user can't tap first.
            if (section.value == null) {
                section.value = restoredSection
                enter(restoredSection)
            }
        }
        viewModelScope.launch {
            // auto retry
            networkMonitor.isOnline
                .drop(1)
                .collect { online: Boolean ->
                    if (online && tail.value is TailState.Failed) {
                        Log.d("VM", "back online, retrying")
                        loadMore()
                    }
                }
        }
    }

    private var enterJob: Job? = null

    private fun enter(target: Section) {
        enterJob?.cancel()
        enterJob = viewModelScope.launch {
            // Count first: the saved frontier can outlive Room's rows
            // (a schema bump wipes Room but keeps DataStore).
            // Ask the database directly. A Flow's first emission can't tell
            // "empty because loading" from "empty because empty" — a count query can.
            val existing: Int = repository.count(target)
            val stored: Int = positionStore.getFrontier(target)
            var start: Int = stored - 2
            if (start > existing - 1) {
                start = existing - 1        // never past the last artwork
            }
            if (start < 0) {
                start = 0                   // also covers existing == 0
            }
            initialPage.value = Pair(target, start)

            // Start the landing page's image now, before the pager is even
            // composed. By the time AsyncImage asks for it, it's in the memory
            // cache, so the page draws with its image instead of empty.
            val landing: Artwork? = repository.artworks(target).first().getOrNull(start)
            if (landing != null) {
                prefetch(listOf(landing.imageUrl))
            }

            if (existing == 0) {
                loadMore()
            } else {
                // Cache already has data and nothing is pending. Without this,
                // tail stays stuck at its Loading default and the tail
                // placeholder page would spin forever with no fetch in flight.
                tail.value = TailState.Idle
            }

        }
    }

    fun selectSection(target: Section) {
        if (target == section.value) {
            return
        }

        loadJob?.cancel()              // an Asia load in flight is Asia's business now
        initialPage.value = null       // pager must not build until the new page is known
        tail.value = TailState.Loading
        section.value = target
        enter(target)
        viewModelScope.launch {
            positionStore.setLastSection(target)
        }
    }

    fun loadMore() {
        Log.d("VM", "loadMore called, active=${loadJob?.isActive}")
        if (loadJob?.isActive == true) {
            Log.d("VM", "skipped, already loading")
            return
        }
        val target: Section = section.value ?: return

        loadJob = viewModelScope.launch {

            /**
             * previous is saved in a val first so both checks use the same value.
             * Reading tail.value twice could in principle give different answers.
             * isRetry is saved separately because after the if, tail.value has already changed,
             * so you couldn't check it again later.
             *
             */

            val previous: TailState = tail.value
            val isRetry: Boolean = previous is TailState.Failed

            if (previous is TailState.Failed) {
                tail.value = previous.copy(retrying = true)
            } else {
                tail.value = TailState.Loading
            }
            val next: TailState
            if (isRetry) {
                /**
                 * result will come after at lest MIN_RETRY_VISIBLE_MS SECONDS
                 * IF RETRY İS CALLED. at least.
                 */
                next = coroutineScope {
                    val minimumTimeShouldSpentBeforeVisible: Job =
                        launch { delay(MIN_RETRY_VISIBLE_MS.milliseconds) }

                    val result: TailState = runLoad(target)
                    /**
                     * minimum.join() suspends until the timer finishes.
                     * If it already finished, this returns immediately.
                     */
                    minimumTimeShouldSpentBeforeVisible.join()
                    result
                }
            } else {
                next = runLoad(target)
            }
            tail.value = next
        }
    }

    private suspend fun runLoad(target: Section): TailState {
        return try {
            when (val outcome = repository.loadMore(target)) {
                LoadOutcome.Loaded -> TailState.Idle
                LoadOutcome.Exhausted -> TailState.Exhausted
                is LoadOutcome.Failed -> TailState.Failed(outcome.reason)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TailState.Failed(e.message ?: "Couldn't load more artworks")
        }
    }

    fun onPageChanged(page: Int) {
        val shown = uiState.value.section ?: return
        viewModelScope.launch {
            positionStore.setFrontier(shown, page)
        }

        val artworks = uiState.value.artworks
        // Offset 0 keeps the current page in the list. The prefetcher cancels
        // anything not in the latest list, and enter() may have just started
        // this page's image — dropping it would throw that head start away.
        val urls = (0..2).mapNotNull { offset ->
            artworks.getOrNull(page + offset)?.imageUrl
        }
        prefetch(urls)
        // <= (not <) so landing directly on the tail placeholder page — e.g. a
        // fast fling that skips past the "within 5 of the end" pages — still
        // triggers a load instead of leaving tail stuck at Idle with nothing
        // ever fetching.

        // A failed tail is retried by its Retry button or by reconnecting, not by
        // swiping. Swipe-retries fire about once a second and keep hitting a server
        // that may be rate-limiting us.
        if (artworks.size - page <= 5 && tail.value !is TailState.Failed) {
            loadMore()
        }
    }

    fun prefetch(urls: List<String>) = prefetcher.prefetch(urls)
}