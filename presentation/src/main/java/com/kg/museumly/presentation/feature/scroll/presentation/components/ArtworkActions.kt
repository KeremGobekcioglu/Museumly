package com.kg.museumly.presentation.feature.scroll.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The page's actions: favorite, then share. Share is left out while
 * [onShareClick] is null, so the button doesn't show before it does anything.
 */
@Composable
fun ArtworkActions(
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onShareClick: (() -> Unit)?,
    /**
     * if vertical is true, icons are aligned vertically.
     * else horizontally
     */
    verticalState: Boolean,
    modifier: Modifier = Modifier
) {
    if (verticalState) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            FavoriteAndShareButtons(isFavorite, onFavoriteClick, onShareClick)
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            FavoriteAndShareButtons(isFavorite, onFavoriteClick, onShareClick)
        }
    }
}

@Composable
private fun FavoriteAndShareButtons(
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onShareClick: (() -> Unit)?,
) {
    IconButton(onClick = onFavoriteClick) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = Color.White,
        )
    }
    if (onShareClick != null) {
        IconButton(onClick = onShareClick) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                tint = Color.White,
            )
        }
    }
}