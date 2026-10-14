package com.kg.museumly.presentation.feature.eras.era

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.domain.ArtworkPrefetcher
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.EraPositionSourceInterface
import com.kg.museumly.domain.LoadOutcome
import com.kg.museumly.domain.NetworkMonitor
import com.kg.museumly.domain.completeRounds
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.domain.walkRounds
import com.kg.museumly.presentation.feature.scroll.presentation.TailState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class EraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ArtworkRepository,
    private val eraPositionSource: EraPositionSourceInterface,
    private val prefetcher: ArtworkPrefetcher,
    private val networkMonitor: NetworkMonitor
): ViewModel()
{

    // keys = EraPage.eraId / EraPage.sectionId, filled in by Navigation. Read by
    // name so this ViewModel doesn't depend on the route class.
    private val eraId: String = checkNotNull(savedStateHandle["eraId"])
    private val sectionId: String = checkNotNull(savedStateHandle["sectionId"])

    // Placeholders: use your real id lookups.
    private val era: Era = checkNotNull(Era.fromId(eraId))
    private val startSection: Section = checkNotNull(Section.fromId(sectionId))
    private val sections: List<Section> = Era.sectionsFor(era)

    /** A restored index, tagged with the selection it belongs to (null = All). */
    private class RestoredIndex(val selection: Section?, val index: Int)

    // null = All. Starts on the timeline stop's section.
    private val selected = MutableStateFlow<Section?>(startSection)
    private val restored = MutableStateFlow<RestoredIndex?>(null)
    private val tail = MutableStateFlow<TailState>(TailState.Loading)

    // Sections found exhausted. allCount() fills it from the cursor rows,
    // loadBatch() keeps it current.
    private val exhausted = MutableStateFlow<Set<Section>>(emptySet())
    private val _effect = Channel<EraEffect>(Channel.BUFFERED)
    val effects = _effect.receiveAsFlow()

    /**
     * The list for the current selection, tagged with that selection, so the
     * state never pairs a new selection with the old list.
     */

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<Pair<Section?, List<Artwork>>> = selected
        .flatMapLatest { current: Section? ->
            val list : Flow<List<Artwork>>
            if (current == null)
            {
                list = allArtworks()
            }
            else
            {
                list = repository.artworks(current,era)
            }
            list.map { artworks: List<Artwork> ->
                Pair(current,artworks)
            }
        }

    val uiState : StateFlow<EraUiState> = combine(
        content,
        restored,
        tail,
        networkMonitor.isOnline
    ) {
        contentValue: Pair<Section?, List<Artwork>>, restoredValue: RestoredIndex?,
            tailState: TailState, online: Boolean ->
        val shown : Section? = contentValue.first
        var matchedIndex: Int? = null
        if(restoredValue != null && restoredValue.selection == shown)
        {
            matchedIndex = restoredValue.index
        }
        EraUiState(
            era = era,
            sections = sections,
            selected = shown,
            artworks = contentValue.second,
            initialIndex = matchedIndex,
            tail = tailState,
            isOnline = online,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EraUiState(era = era, sections = sections, selected = startSection)
    )

    private companion object {
        const val PAGE_SIZE: Int = 20
        const val MIN_RETRY_VISIBLE_MS: Long = 600
        // A section's load can come back short (a source gave fewer works).
        // A few tries fill it up; past that, the next batch tries again.
        const val MAX_TRIES_PER_SECTION: Int = 3
        // Start loading when this many tiles or fewer are left below the
        // last visible one: at 20 works, that's when tile 15 shows.
        const val LOAD_AHEAD: Int = 5
        // Images started below the screen: about two rows.
        const val PREFETCH_AHEAD: Int = 4
    }

    // Declared above init on purpose: init calls enter() right away, and a
    // field declared below init would be reset to null after enter() set it.
    private var enterJob: Job? = null
    private var loadJob: Job? = null

    init {
        enter(startSection)
        viewModelScope.launch {
            // Back online with a failed tail: retry, like the feed.
            networkMonitor.isOnline
                .drop(1)
                .collect { online: Boolean ->
                    if (online && tail.value is TailState.Failed) {
                        loadMore()
                    }
                }
        }
    }
    /**
     * Restores where this selection was left, clamped to what Room has,
     * then loads if the list is empty.
     */
    private fun enter(target: Section?)
    {
        enterJob?.cancel()
        enterJob = viewModelScope.launch {
            // Count first: DataStore survives a Room wipe, so the saved
            // index can point past the end.
            val existing: Int = countFor(target)
            val stored: Int = eraPositionSource.getFrontier(era, target)
            var start: Int = stored
            if (start > existing - 1) {
                start = existing - 1
            }
            if (start < 0) {
                start = 0
            }
            restored.value = RestoredIndex(target, start)

            if (existing == 0) {
                loadMore()
            } else {
                tail.value = TailState.Idle
            }
        }
    }
    private suspend fun countFor(target: Section?): Int {
        if (target == null) {
            return allCount()                   // piece 4
        }
        return repository.count(target, era)
    }

    private fun loadMore()
    {
        if(loadJob?.isActive == true)
        {
            return
        }
        // Captured now: a switch cancels this job instead of letting it
        // finish into the new selection's tail.
        val target: Section? = selected.value
        loadJob = viewModelScope.launch {
            val previous: TailState = tail.value
            val isRetry : Boolean = previous is TailState.Failed
            if (previous is TailState.Failed) {
                tail.value = previous.copy(retrying = true)
            } else {
                tail.value = TailState.Loading
            }
            val next: TailState
            if(isRetry)
            {
                next = coroutineScope {
                    val minimum : Job = launch {
                        delay(MIN_RETRY_VISIBLE_MS.milliseconds)
                    }
                    val result: TailState = runLoad(target)
                    minimum.join()
                    result
                }
            }
            else
            {
                next = runLoad(target)
            }
            tail.value = next
        }
    }

    private suspend fun runLoad(target: Section?): TailState {
        try {
            val outcome: LoadOutcome
            if (target == null) {
                outcome = loadBatch()               // piece 4
            } else {
                outcome = repository.loadMore(target, PAGE_SIZE, era)
            }
            val next: TailState = when (outcome) {
                LoadOutcome.Loaded -> TailState.Idle
                LoadOutcome.Exhausted -> TailState.Exhausted
                is LoadOutcome.Failed -> TailState.Failed(outcome.reason)
            }
            return next
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return TailState.Failed(e.message ?: "Couldn't load more artworks")
        }
    }

    /**
     * All: every section's list for this era, walked into complete rounds.
     * Re-walks when any list grows or a section runs out.
     */
    private fun allArtworks() : Flow<List<Artwork>>
    {
        val perSection: MutableList<Flow<List<Artwork>>> = ArrayList()
        // walk in sections and add their flow. later we ll collect.
        for (section in sections) {
            perSection.add(repository.artworks(section, era))
        }
        val lists: Flow<List<List<Artwork>>> = combine(perSection)
        { arrays: Array<List<Artwork>> ->
            val result: MutableList<List<Artwork>> = ArrayList()
            for(list in arrays)
            {
                result.add(list)
            }
            result
        }
        return combine(lists,exhausted)
        {
            current: List<List<Artwork>>, done: Set<Section> ->
                val flags : MutableList<Boolean> = ArrayList()
                for(section in sections)
                {
                    flags.add(section in done)
                }
            walkRounds(current,flags)
        }
    }
    /**
     * Length of the All walk as it stands, without loading any lists.
     * Also fills `exhausted` from the cursor rows, so after a restart the
     * walk isn't held back by a section that has already run out.
     */
    private suspend fun allCount() : Int
    {
        // Step 1: which sections have already run out. Cursor rows only,
        // no network. Done first, because the walk's length depends on it.
        val done: MutableSet<Section> = HashSet()
        for (section in sections) {
            if (repository.isExhausted(section, era)) {
                done.add(section)
            }
        }
        exhausted.value = done

        // Step 2: how many works each section's list holds.
        val sizes: MutableList<Int> = ArrayList()
        val flags: MutableList<Boolean> = ArrayList()
        for (section in sections) {
            sizes.add(repository.count(section, era))
            flags.add(section in done)
        }
        // Step 3: the walk shows `rounds` rounds. Each list adds one work
        // per round it has, so it adds min(its size, rounds) in total.
        val rounds: Int = completeRounds(sizes, flags)
        var length: Int = 0
        for (size in sizes) {
            if (size < rounds) {
                length += size
            } else {
                length += rounds
            }
        }
        return length
    }

    /**
     * Brings every live section up to the same target, so All gains about
     * PAGE_SIZE works, all in complete rounds. A section already at the
     * target (browsed on its own earlier) costs no network.
     */
    private suspend fun loadBatch(): LoadOutcome
    {
        // Step 1: where each section stands now.
        val sizes: MutableList<Int> = ArrayList()
        val flags: MutableList<Boolean> = ArrayList()
        val live: MutableList<Section> = ArrayList()
        for (section in sections) {
            sizes.add(repository.count(section, era))
            val done: Boolean = section in exhausted.value
            flags.add(done)
            if (!done) {
                live.add(section)
            }
        }
        // Step 2: nothing left that can grow.
        if (live.isEmpty()) {
            return LoadOutcome.Exhausted
        }

        // Step 3: the target. PAGE_SIZE more works split over the live
        // sections, rounded up: 4 sections -> 5 each, 3 -> 7, 1 -> 20.
        val perSection: Int = (PAGE_SIZE + live.size - 1) / live.size
        val target: Int = completeRounds(sizes, flags) + perSection

        // Step 4: bring each live section up to the target. Sections run
        // one after another; each loadMore takes the repository's lock itself.
        val failures: MutableList<String> = ArrayList()
        for (section in live)
        {
            var tries: Int = 0
            while (tries < MAX_TRIES_PER_SECTION)
            {
                val have: Int = repository.count(section, era)
                if (have >= target) {
                    break                       // already there: no network
                }
                tries += 1
                val outcome: LoadOutcome = repository.loadMore(section, target - have, era)
                if (outcome is LoadOutcome.Failed) {
                    failures.add(outcome.reason)
                    break                       // holds the batch back; retry later
                }
                if (outcome == LoadOutcome.Exhausted) {
                    exhausted.value = exhausted.value + section
                    break                       // the walk re-runs and stops waiting for it
                }
                // Loaded: may still be short, so loop and count again.
            }
        }
        // Step 5: report. A failure wins, so the tail shows Retry.
        if (failures.isNotEmpty()) {
            return LoadOutcome.Failed(failures.joinToString("; "))
        }
        // Every section ran out during this batch: say so now instead of
        // waiting for the next trigger to find out.
        if (exhausted.value.size == sections.size) {
            return LoadOutcome.Exhausted
        }
        return LoadOutcome.Loaded
    }

    fun onIntent(intent: EraIntent)
    {
        when(intent)
        {
            is EraIntent.SectionPicked -> selectSection(intent.section)
            is EraIntent.Scrolled -> onScrolled(intent.firstVisible, intent.lastVisible)
            is EraIntent.ArtworkClicked -> sendEffect(EraEffect.OpenDetail(intent.artworkId))
            EraIntent.RetryClicked -> loadMore()
            EraIntent.BackClicked -> sendEffect(EraEffect.Back)
        }
    }

    /**
     * The mid-switch check is new. The feed gets this for free because
     * its pager is rebuilt under key(section).
     * Here, a late Scrolled from the old grid could otherwise save the wrong list’s frontier,
     * or start a load for the new list based on the old list’s size.
     * The Exhausted check is new. The feed’s pager rarely reaches its end.
     * A grid does, and every scroll there would set Loading and then Exhausted again.
     * The frontier uses firstVisible, and the trigger uses lastVisible. That’s why the intent carries both.
     */

    /** Same order as the feed's selectSection. */
    private fun selectSection(target: Section?) {
        // Step 1: same choice, nothing to do.
        if (target == selected.value) {
            return
        }
        // Step 2: a load for the old selection is the old selection's business.
        loadJob?.cancel()
        // Step 3: the grid must not build until the new index is known.
        restored.value = null
        tail.value = TailState.Loading
        // Step 4: switch the list, then restore its position and load if empty.
        selected.value = target
        enter(target)
    }

    private fun onScrolled(firstVisible: Int, lastVisible: Int) {
        val state: EraUiState = uiState.value

        // Step 1: mid-switch, the grid still shows the old list while
        // `selected` is already the new one. Ignore it until they agree.
        if (state.selected != selected.value) {
            return
        }

        // Step 2: remember the furthest row reached. The store ignores
        // anything lower than what it has.
        viewModelScope.launch {
            eraPositionSource.setFrontier(era, state.selected, firstVisible)
        }

        // Step 3: start images for the visible tiles and two rows below.
        // Visible ones are included on purpose: the prefetcher cancels
        // anything missing from the latest list (the feed's offset-0 reason).
        val urls: MutableList<String> = ArrayList()
        for (i in firstVisible..lastVisible + PREFETCH_AHEAD) {
            val artwork: Artwork? = state.artworks.getOrNull(i)
            if (artwork != null) {
                urls.add(artwork.imageUrl)
            }
        }
        prefetcher.prefetch(urls)

        // Step 4: near the end, load more. Not while Failed (Retry or
        // reconnect handles that, like the feed), and not once Exhausted,
        // or every scroll at the bottom would flash Loading for nothing.
        val left: Int = state.artworks.size - lastVisible
        val current: TailState = tail.value
        if (left <= LOAD_AHEAD && current !is TailState.Failed && current !is TailState.Exhausted) {
            loadMore()
        }
    }

    private fun sendEffect(effect: EraEffect) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }
}