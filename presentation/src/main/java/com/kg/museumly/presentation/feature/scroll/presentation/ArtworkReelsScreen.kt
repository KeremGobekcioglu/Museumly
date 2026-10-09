package com.kg.museumly.presentation.feature.scroll.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kg.museumly.presentation.BuildConfig
import com.kg.museumly.presentation.feature.scroll.presentation.components.ArtworkPageWithRestrainedBox
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryLoading
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryNotice
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import com.kg.museumly.presentation.feature.scroll.presentation.components.SectionPicker
import com.kg.museumly.domain.model.Section
import com.kg.museumly.presentation.feature.scroll.presentation.components.TopBarChipOutline
import com.kg.museumly.presentation.feature.scroll.presentation.components.label

private const val TAG = "MuseumlyImages"

private fun failedTitle(isOnline: Boolean, isTail: Boolean): String {
    if (!isOnline) {
        return if (isTail) "Lost the connection" else "No connection"
    }
    return if (isTail) "Couldn't reach the next room" else "The gallery didn't open"
}

private fun failedBody(isOnline: Boolean, isTail: Boolean): String {
    if (!isOnline) {
        return if (isTail) "We'll fetch the next works once you're back online."
        else "We'll pick this back up once you're back online."
    }
    return if (isTail) "The collection isn't responding right now."
    else "Something went wrong loading the collection."
}

/**
 * Which top-level screen ReelsContent shows. The transition animates on
 * this, not on ScrollUiState, so new artworks or page changes don't
 * restart the fade or rebuild the pager.
 */
private enum class ReelsMode { Loading, Notice, Waiting, Pager }

private fun ScrollUiState.mode(): ReelsMode = when {
    // A fetch is in flight and there's nothing to show yet. tail defaults to
    // Loading at construction, so this covers the very first frame as well
    // as every fetch after it, including a retry.
    tail == TailState.Loading && artworks.isEmpty() -> ReelsMode.Loading
    // Nothing in Room and nothing loading: failed, or genuinely empty.
    artworks.isEmpty() -> ReelsMode.Notice
    // Don't build the pager until we know the start position.
    initialPage == null -> ReelsMode.Waiting
    else -> ReelsMode.Pager
}

/**
 * Phase 0 spike: the World Wonders question, answered up front. Each page reserves
 * exactly the artwork's true aspect ratio before the image decodes, inside a fixed
 * screen-sized page (Reels-style vertical snap via VerticalPager's real fling/snap).
 */
@Composable
fun ArtworkReelsScreen(
    state: ScrollUiState,
    refresh: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onDetailPage: (String) -> Unit,
    onSectionSelected: (Section) -> Unit,
    setFavorite: (String, Boolean) -> Unit,
    onDebugFavorites: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ReelsContent(
            state = state,
            refresh = refresh,
            onPageChanged = onPageChanged,
            onDetailPage = onDetailPage,
            setFavorite = setFavorite
        )
        // Same 300 ms as the gallery crossfade, so the pill and the
        // artwork appear together instead of the pill popping in on its own.
        AnimatedVisibility(
            visible = state.section != null,
            enter = fadeIn(tween(300)),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            state.section?.let { section ->
                SectionPicker(
                    selected = section,
                    onSectionSelected = onSectionSelected
                )
            }
        }

        // Temporary entry point until the bottom bar exists.
        if (BuildConfig.DEBUG) {
            SmallFloatingActionButton(
                onClick = onDebugFavorites,
                containerColor = Color.White.copy(alpha = 0.15f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = ReelsGutter)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Debug: open favorites")
            }
        }
    }
}


/**
 * Switching screens with a plain if/return is a hard cut: loading screen on
 * one frame, full pager on the next. AnimatedContent crossfades instead.
 * contentKey = mode() means only a change of screen animates, and the
 * screen fading out keeps the state it last had instead of the newest one.
 *
 * Waiting (initialPage == null, mid section switch) is an empty,
 * transparent box, so the wall shows through. Showing GalleryLoading there
 * would flash the loading screen on every cached switch.
 */
@Composable
private fun ReelsContent(
    state: ScrollUiState,
    refresh: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onDetailPage: (String) -> Unit,
    setFavorite: (String, Boolean) -> Unit
) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.mode() },        // only animate when the mode changes
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "reels-mode",
    ) { slotState: ScrollUiState ->
        when (slotState.mode()) {
            ReelsMode.Loading -> GalleryLoading("Hanging the work")
            ReelsMode.Notice -> ReelsNotice(state = slotState, refresh = refresh)
            ReelsMode.Waiting -> Box(modifier = Modifier.fillMaxSize())
            ReelsMode.Pager -> ReelsPager(
                state = slotState,
                refresh = refresh,
                onPageChanged = onPageChanged,
                onDetailPage = onDetailPage,
                setFavorite = setFavorite
            )
        }
    }
}

// Nothing in Room and nothing loading. Two different situations:
// the fetch failed, or every provider is genuinely empty. Only the
// first is retryable, and the pager (with its LaunchedEffect) isn't
// built here, so a button is the only way back.
@Composable
private fun ReelsNotice(state: ScrollUiState, refresh: () -> Unit) {
    val tail = state.tail
    if (tail is TailState.Failed) {
        GalleryNotice(
            title = failedTitle(state.isOnline, false),
            body = failedBody(state.isOnline, false),
            actionLabel = if (state.isOnline) "Retry" else "Try anyway",
            onAction = refresh,
            isBusy = tail.retrying,
            debugDetail = if (BuildConfig.DEBUG) tail.message else null,
        )
    } else {
        GalleryNotice(
            title = "Nothing on the walls",
            body = "No works came back from the collection.",
            actionLabel = "Retry",
            onAction = refresh,
        )
    }
}

@Composable
private fun ReelsPager(
    state: ScrollUiState,
    refresh: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onDetailPage: (String) -> Unit,
    setFavorite: (String, Boolean) -> Unit
) {
    key(state.section) {
        val pagerState = rememberPagerState(
            // rememberPagerState reads initialPage exactly once, which is why
            // mode() only picks Pager once it's known. initialPage is always set
            // here; ?: 0 only satisfies the nullable type.
            initialPage = state.initialPage ?: 0,
            pageCount = { state.artworks.size + 1 /*loading page*/ }
        )

        LaunchedEffect(pagerState.currentPage, state.artworks.size) {
            onPageChanged(pagerState.currentPage)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (page < state.artworks.size) {
                    val artwork = state.artworks.getOrNull(page)
                    if (artwork != null) {
                        ArtworkPageWithRestrainedBox(
                            artwork = artwork,
                            onDetailPage = onDetailPage,
                            isFavorite = state.favoriteIds.contains(artwork.id),
                            setFavorite = setFavorite
                        )
                    }
                } else {
                    when (val tail = state.tail) {
                        // Idle here means a load just finished right as the user
                        // landed on this page and the next one hasn't been
                        // triggered yet — visually indistinguishable from Loading.
                        TailState.Loading, TailState.Idle -> GalleryLoading("Art is worth the wait.")
                        is TailState.Failed -> GalleryNotice(
                            title = failedTitle(state.isOnline, true),
                            body = failedBody(state.isOnline, true),
                            actionLabel = if (state.isOnline) "Retry" else "Try anyway",
                            onAction = refresh,
                            isBusy = tail.retrying,
                            debugDetail = if (BuildConfig.DEBUG) tail.message else null,
                        )

                        TailState.Exhausted -> GalleryNotice(
                            title = "You've walked the whole gallery",
                            body = "That's every work we have in ${state.section?.label ?: "this gallery"}. " +
                                "Thank you for taking the time to look. " +
                                "Another gallery is waiting in the menu above.",
                        )
                    }
                }
            }


            if (pagerState.currentPage < state.artworks.size) {
                PageCounter(
                    pagerState = pagerState,
                    total = state.artworks.size,
                    isComplete = state.tail == TailState.Exhausted,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

/**
 * The pill's partner on the right of the top bar: same height, same
 * hairline outline. Until the section is exhausted the loaded count isn't the
 * section's size — it grows with every batch — so the total stays ∞ until
 * it's real. Tabular figures keep it from shifting sideways at 9 → 10.
 */
@Composable
private fun PageCounter(
    pagerState: PagerState,
    total: Int,
    isComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    val totalLabel: String = if (isComplete) total.toString() else "∞"
    Text(
        text = "${pagerState.currentPage + 1} / $totalLabel",
        color = Color.White,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(end = ReelsGutter, top = 12.dp)
            // The pill is a clickable Surface, so Material pads it to a 48dp
            // touch target and centres it. The same here keeps the two chips
            // on one line.
            .minimumInteractiveComponentSize()
            .border(1.dp, TopBarChipOutline, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
