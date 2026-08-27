package com.kg.museumly.feature.scroll.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.model.Artwork
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val repository: ArtworkRepository
) : ViewModel()
{
    val artworks: StateFlow<List<Artwork>> = repository.artworks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
        }
    }
}