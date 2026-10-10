package com.kg.museumly.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
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
            MainBottomBar(
                isSelected = { tab -> currentDestination?.hasRoute(tab.route::class) == true },
                onTabClick = { tab -> switchTab(tabNavController, tab.route) },
            )
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
                exitTransition = {
                    fadeOut(tween(350)) + scaleOut(targetScale = 0.94f, animationSpec = tween(350))
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

            composable<ErasPage>(

            )
            {

            }
        }
    }
}