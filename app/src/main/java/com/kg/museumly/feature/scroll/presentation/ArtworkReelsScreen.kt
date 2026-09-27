package com.kg.museumly.feature.scroll.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.kg.museumly.BuildConfig
import com.kg.museumly.feature.scroll.presentation.components.ArtworkPageWithRespectToAspectRatio
import com.kg.museumly.feature.scroll.presentation.components.GalleryLoading
import com.kg.museumly.feature.scroll.presentation.components.GalleryNotice
import com.kg.museumly.model.Section

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
    onSectionSelected: (Section) -> Unit

) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ReelsContent(
            state = state,
            refresh = refresh,
            onPageChanged = onPageChanged,
            onDetailPage = onDetailPage
        )
        SectionPicker(
            selected = state.section,
            onSectionSelected = onSectionSelected,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

private val Section.label: String
    get() = when (this) {
        Section.EGYPT_NEAR_EAST -> "Egypt & Near East"
        Section.GREEK_ROMAN -> "Greek & Roman"
        Section.ISLAMIC -> "Islamic"
        Section.MEDIEVAL -> "Medieval"
        Section.EUROPEAN -> "European"
        Section.ASIA -> "Asia"
        Section.AFRICA_OCEANIA_AMERICAS -> "Africa, Oceania & Americas"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionPicker(
    selected: Section,
    onSectionSelected: (Section) -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    Surface(
        onClick = { sheetOpen = true },
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = 12.dp)
            .widthIn(max = 220.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Change section",
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = Color(0xFF141414),
            contentColor = Color.White
        ) {
            Text(
                text = "Galleries",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            for (section in Section.entries) {
                val isSelected = section == selected
                ListItem(
                    headlineContent = {
                        Text(
                            text = section.label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    trailingContent = {
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected")
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = Color.White,
                        trailingIconColor = Color.White
                    ),
                    modifier = Modifier.clickable {
                        onSectionSelected(section)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { sheetOpen = false }
                    }
                )
            }
        }
    }
}

@Composable
private fun ReelsContent(
    state: ScrollUiState,
    refresh: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onDetailPage: (String) -> Unit
) {

    /**
     * During a switch, if the screen does catch the null gap, the initialPage == null
     * branch returns nothing and you get a plain black frame, not the gallery loading screen.
     * On a cached section it lasts a frame or two. If it bothers you, that return could show
     * GalleryLoading instead, but then cached switches will flash the gallery wall. That's your UI call.
     */

    // A fetch is genuinely in flight and we have nothing to show yet.
    // tail defaults to Loading at construction (before the launched
    // coroutine has had a chance to run), so this covers the very first
    // frame as well as every fetch after that, including a retry.
    if (state.tail == TailState.Loading && state.artworks.isEmpty()) {
        GalleryLoading("Hanging the work")
        return
    }

    // Nothing in Room and nothing loading. Two different situations:
    // the fetch failed, or every provider is genuinely empty. Only the
    // first is retryable, and this early-returns before the LaunchedEffect
    // below, so a button is the only way back.
    if (state.artworks.isEmpty()) {
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
        return
    }

    // Don't build the pager until we know the start position.
    // rememberPagerState reads initialPage exactly once — if it's created
    // against an empty list, the position clamps to 0 and never corrects.
    if (state.initialPage == null) {
        return
    }

    key(state.section) {
        val pagerState = rememberPagerState(
            initialPage = state.initialPage,
            pageCount = { state.artworks.size + 1 /*loading page*/ }
        )

        LaunchedEffect(pagerState.currentPage, state.artworks.size) {
            onPageChanged(pagerState.currentPage)
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (page < state.artworks.size) {
                    val artwork = state.artworks.getOrNull(page)
                    if (artwork != null) {
                        ArtworkPageWithRespectToAspectRatio(
                            artwork = artwork,
                            onDetailPage = onDetailPage
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
                            title = "End of the gallery",
                            body = "You've seen everything here.",
                        )
                    }
                }
            }


            if (pagerState.currentPage < state.artworks.size) {
                PageCounter(pagerState = pagerState, total = state.artworks.size)
            }
        }
    }
}

@Composable
private fun PageCounter(pagerState: PagerState, total: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().safeDrawingPadding().padding(top = 60.dp)) {
        Text(
            text = "${pagerState.currentPage + 1} / $total",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .alpha(0.9f),
        )
    }
}
