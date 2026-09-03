package com.kg.museumly.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.kg.museumly.feature.detail.DetailScreen
import com.kg.museumly.feature.detail.DetailUiState
import com.kg.museumly.feature.detail.DetailViewModel
import com.kg.museumly.feature.scroll.presentation.ArtworkReelsScreen
import com.kg.museumly.feature.scroll.presentation.ScrollUiState
import com.kg.museumly.feature.scroll.presentation.ScrollViewModel

@Composable
fun MuseumlyNavGraph(navController: NavHostController)
{
    NavHost(
        startDestination = ScrollPage,
        navController = navController,
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {

        composable<ScrollPage>{
            val viewModel: ScrollViewModel = hiltViewModel()
            val state: ScrollUiState by viewModel.uiState.collectAsStateWithLifecycle()
            ArtworkReelsScreen(
                state = state,
                refresh = viewModel::loadMore,
                onPageChanged = viewModel::onPageChanged,
                onDetailPage = { id -> navController.navigate(DetailPage(id)) }
            )
        }

        composable<DetailPage>{
            val viewmodel: DetailViewModel = hiltViewModel()
            val state: DetailUiState by viewmodel.state.collectAsStateWithLifecycle()
            DetailScreen(
                state = state,
                onBack = { navController.popBackStack() }
            )
        }
    }
}