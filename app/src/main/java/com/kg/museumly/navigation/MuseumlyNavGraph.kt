package com.kg.museumly.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.kg.museumly.feature.scroll.presentation.components.WallColor

@Composable
fun MuseumlyNavGraph(navController: NavHostController)
{
    NavHost(
        modifier = Modifier
            .fillMaxSize()
            .background(WallColor),
        startDestination = ScrollPage,
        navController = navController,
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {

        composable<ScrollPage>(
            exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
            popEnterTransition = { EnterTransition.None },
        )
        {
            val viewModel: ScrollViewModel = hiltViewModel()
            val state: ScrollUiState by viewModel.uiState.collectAsStateWithLifecycle()
            ArtworkReelsScreen(
                state = state,
                refresh = viewModel::loadMore,
                onPageChanged = viewModel::onPageChanged,
                onDetailPage = { id -> navController.navigate(DetailPage(id)) }
            )
        }

        composable<DetailPage>(
            // Walking up to the work, not a generic screen swap: slower and
            // paired with a slight scale so entering reads as moving closer,
            // not just a screen appearing. Stepping back on exit mirrors it.
            enterTransition = {
                fadeIn(tween(450)) + scaleIn(initialScale = 0.94f, animationSpec = tween(450))
            },
            popExitTransition = {
                fadeOut(tween(350)) + scaleOut(targetScale = 0.94f, animationSpec = tween(350))
            },
        ) {
            val viewmodel: DetailViewModel = hiltViewModel()
            val state: DetailUiState by viewmodel.state.collectAsStateWithLifecycle()
            DetailScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onInspected = viewmodel::onInspected,
            )
        }
    }
}