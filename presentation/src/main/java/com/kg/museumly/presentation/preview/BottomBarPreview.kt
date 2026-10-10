package com.kg.museumly.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.presentation.navigation.ErasPage
import com.kg.museumly.presentation.navigation.FavoritesPage
import com.kg.museumly.presentation.navigation.MainBottomBar
import com.kg.museumly.presentation.navigation.ScrollPage

@Preview(name = "Bottom bar · Gallery", widthDp = 411)
@Composable
private fun BottomBarGalleryPreview() = MainBottomBar(
    isSelected = { tab -> tab.route == ScrollPage },
    onTabClick = {},
)

@Preview(name = "Bottom bar · Favorites", widthDp = 411)
@Composable
private fun BottomBarFavoritesPreview() = MainBottomBar(
    isSelected = { tab -> tab.route == FavoritesPage },
    onTabClick = {},
)

@Preview(name = "Bottom bar · Eras", widthDp = 411)
@Composable
private fun BottomBarErasPreview() = MainBottomBar(
    isSelected = { tab -> tab.route == ErasPage },
    onTabClick = {},
)