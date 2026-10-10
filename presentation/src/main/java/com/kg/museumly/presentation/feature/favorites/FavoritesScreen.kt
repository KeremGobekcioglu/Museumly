package com.kg.museumly.presentation.feature.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Section
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryNotice
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import com.kg.museumly.presentation.feature.scroll.presentation.components.SectionPicker
import com.kg.museumly.presentation.feature.scroll.presentation.components.TopBarChipOutline
import com.kg.museumly.presentation.feature.scroll.presentation.components.WallColor

@Composable
fun FavoritesScreen(
    state: FavoritesUIState,
    onIntent: (FavoritesIntent) -> Unit,
    onBack: () -> Unit,
)
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WallColor)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReelsGutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionPicker(
                allLabel = "All Galleries",
                selected = state.section,
                onSectionSelected = {
                    picked: Section? -> onIntent(FavoritesIntent.SectionPicked(picked))
                },
                // Lets a long section name shrink and wrap instead of
                // pushing into the sort chip.
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
            )
            SortChip(
                newestFirst = state.newestFirst,
                onClick = { onIntent(FavoritesIntent.SortToggled) }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (state.isLoading)
            {

            }
            else if(state.artworks.isEmpty())
            {
                FavoritesEmpty(
                    section = state.section,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.Center))
            }
            else
            {
                FavoritesGrid(state.artworks, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun FavoritesGrid(
    artworks: List<Artwork>,
    onIntent: (FavoritesIntent) -> Unit
)
{
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columnCount(artworks.size)),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalItemSpacing = 4.dp
    ) {
        itemsIndexed(
            items = artworks,
            key = { index: Int, artwork: Artwork ->
                artwork.id
            },
        ){ index, artwork ->
            FavoritesTile(
                artwork = artwork,
                onClick = { onIntent(FavoritesIntent.ArtworkClicked(artwork.id)) },
                onRatioLearned = { ratio: Float ->
                    onIntent(FavoritesIntent.RatioLearned(artwork.id, ratio))
                },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun FavoritesEmpty(
    section: Section?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
)
{
    val message: String = if (section == null) "No favorites yet" else "No favorites in this section"
    GalleryNotice(
        title = message,
        body = "",
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
private fun SortChip(
    newestFirst: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
{
    val label = if(newestFirst) "Newest First" else "Oldest First"
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        border = BorderStroke(1.dp, TopBarChipOutline),
        contentColor = Color.White,
        shape = CircleShape
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
private fun columnCount(favoriteCount: Int): Int
{
    if (favoriteCount <= 1)
    {
        return 1
    }
    if (favoriteCount <= 6)
    {
        return 2
    }
    return 3
}