package com.kg.museumly.presentation.feature.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Section
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryNotice
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
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = artworks,
            key = { index: Int, artwork: Artwork ->
                artwork.id
            },
        ){ index, artwork ->
            FavoritesTile(
                artwork,
                onClick = { onIntent(FavoritesIntent.ArtworkClicked(artwork.id))}
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