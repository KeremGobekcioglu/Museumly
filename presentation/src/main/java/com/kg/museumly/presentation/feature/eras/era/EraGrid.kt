package com.kg.museumly.presentation.feature.eras.era

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The era wall: two columns, append-only.
 *
 * Built only once the restored index is known, and the caller wraps it in
 * key(selected): rememberLazyStaggeredGridState reads initialIndex once,
 * the same trap as rememberPagerState.
 */

@Composable
internal fun EraGrid(
    artworks: List<Artwork>,
    initialIndex: Int,
    onIntent: (EraIntent) -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
)
{
    val gridState = rememberLazyStaggeredGridState(
        initialFirstVisibleItemIndex = initialIndex
    )
    // The effect below outlives recompositions; this keeps it calling the
    // latest onIntent instead of the one it started with.
    val currentOnIntent: (EraIntent) -> Unit by rememberUpdatedState(onIntent)

    // Reports what's on screen. Read in a snapshotFlow, not in composition,
    // so scrolling never recomposes the screen.
    LaunchedEffect(gridState) {
        snapshotFlow {
            val visible: List<LazyStaggeredGridItemInfo> = gridState.layoutInfo.visibleItemsInfo
            var last: Int = -1
            for (item in visible) {
                if (item.index > last) {
                    last = item.index
                }
            }
            Pair(gridState.firstVisibleItemIndex, last)
        }
            .distinctUntilChanged()
            .collect { range : Pair<Int,Int> ->
                if(range.second >= 0)
                {
                    currentOnIntent(EraIntent.Scrolled(range.first, range.second))
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        contentPadding = PaddingValues(start = ReelsGutter, end = ReelsGutter, bottom = 16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = artworks,
            key = { artwork: Artwork -> artwork.id}
        )
        {
            artwork: Artwork ->
                EraTile(
                    artwork = artwork,
                    onClick = { onIntent(EraIntent.ArtworkClicked(artwork.id))},
                    modifier = Modifier.animateItem()
                )
        }
        // The tail: loading, retry or the end. Spans both columns.
        item(key = "tail", span = StaggeredGridItemSpan.FullLine) {
            footer()
        }
    }
}