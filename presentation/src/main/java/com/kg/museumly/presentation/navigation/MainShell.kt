package com.kg.museumly.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kg.museumly.presentation.feature.favorites.FavoritesEffect
import com.kg.museumly.presentation.feature.favorites.FavoritesScreen
import com.kg.museumly.presentation.feature.favorites.FavoritesUIState
import com.kg.museumly.presentation.feature.favorites.FavoritesViewModel
import com.kg.museumly.presentation.feature.scroll.presentation.ArtworkReelsScreen
import com.kg.museumly.presentation.feature.scroll.presentation.ScrollUiState
import com.kg.museumly.presentation.feature.scroll.presentation.ScrollViewModel
import com.kg.museumly.presentation.feature.scroll.presentation.components.WallColor

private fun switchTab(navController: NavHostController, route: Any)
{
    navController.navigate(route) {
        popUpTo(ScrollPage) { saveState= true }
        launchSingleTop = true
        restoreState = true
    }
}
@Composable
private fun RowScope.MainBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
)
{
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
        )
    }
}

@Composable
fun MainShell(
    onOpenDetail: (String) -> Unit
)
{
    val tabNavController = rememberNavController()
    val backStackEntry : NavBackStackEntry? by tabNavController.currentBackStackEntryAsState()
    val currentDestination : NavDestination? = backStackEntry?.destination

    Scaffold(
        containerColor = WallColor,
        contentWindowInsets = WindowInsets(0,0,0,0),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(56.dp),
            ) {
               /* NavigationBarItem(
                    selected = currentDestination?.hasRoute<ScrollPage>() == true,
                    onClick = {
                        tabNavController.popBackStack(ScrollPage, false)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Museum,
                            contentDescription = "Gallery",
                        )
                    }
                )*/
                MainBarItem(
                    selected = currentDestination?.hasRoute<ScrollPage>() == true,
                    onClick = { switchTab(tabNavController, ScrollPage) },
                    icon = Icons.Outlined.Museum,
                    label = "Gallery"
                )

                MainBarItem(
                    selected = currentDestination?.hasRoute<FavoritesPage>() == true,
                    onClick = { switchTab(tabNavController, FavoritesPage) },
                    icon = Icons.Outlined.FavoriteBorder,
                    label = "Favorites"
                )
               /* NavigationBarItem(
                    selected = currentDestination?.hasRoute<FavoritesPage>() == true,
                    onClick = {
                        tabNavController.navigate(FavoritesPage) { launchSingleTop= true }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorites",
                        )
                    },
                ) */
            }
        },
    ) { innerPadding: PaddingValues ->
        NavHost(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            navController = tabNavController,
            startDestination = ScrollPage,
            enterTransition = { fadeIn(tween(250)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = { fadeOut(tween(180)) },
        )
        {
            composable<ScrollPage>
            {
                val viewModel: ScrollViewModel = hiltViewModel()
                val state: ScrollUiState by viewModel.uiState.collectAsStateWithLifecycle()
                ArtworkReelsScreen(
                    state = state,
                    refresh = viewModel::loadMore,
                    onPageChanged = viewModel::onPageChanged,
                    onDetailPage = onOpenDetail,
                    onSectionSelected = viewModel::selectSection,
                    setFavorite = viewModel::setFavorite
                )
            }

            composable<FavoritesPage>(
                enterTransition = {
                    fadeIn(tween(450)) + scaleIn(initialScale = 0.94f, animationSpec = tween(450))
                },
                popExitTransition = {
                    fadeOut(tween(350)) + scaleOut(targetScale = 0.94f, animationSpec = tween(350))
                },
            )
            {
                val viewmodel : FavoritesViewModel = hiltViewModel()
                val state : FavoritesUIState by viewmodel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    viewmodel.effects.collect { effect: FavoritesEffect ->
                        when(effect)
                        {
                            is FavoritesEffect.OpenDetail -> {
                                onOpenDetail(effect.artworkId)
                            }
                        }
                    }
                }
                FavoritesScreen(
                    state = state,
                    onIntent = viewmodel::onIntent,
                    onBack = { tabNavController.popBackStack() },
                )
            }
        }
    }
}