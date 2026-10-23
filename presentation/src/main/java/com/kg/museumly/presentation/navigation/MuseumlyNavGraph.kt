package com.kg.museumly.presentation.navigation

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.presentation.feature.auth.AuthEffect
import com.kg.museumly.presentation.feature.auth.AuthScreen
import com.kg.museumly.presentation.feature.auth.AuthViewModel
import com.kg.museumly.presentation.feature.auth.requestGoogleIdToken
import com.kg.museumly.presentation.feature.detail.DetailScreen
import com.kg.museumly.presentation.feature.detail.DetailUiState
import com.kg.museumly.presentation.feature.detail.DetailViewModel
import com.kg.museumly.presentation.feature.eras.era.EraEffect
import com.kg.museumly.presentation.feature.eras.era.EraScreen
import com.kg.museumly.presentation.feature.eras.era.EraViewModel
import com.kg.museumly.presentation.feature.scroll.presentation.components.WallColor
import kotlinx.coroutines.launch

@Composable
fun MuseumlyNavGraph() {
    val navController: NavHostController = rememberNavController()
    NavHost(
        modifier = Modifier
            .fillMaxSize()
            .background(WallColor),
            startDestination = AuthPage,
        navController = navController,
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {

        composable<MainPage>(
            exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
            popEnterTransition = { EnterTransition.None }
        ) {
            MainShell(
                onOpenDetail = { id: String -> navController.navigate(DetailPage(id)) },
                onOpenEra = { section: Section, era: Era ->
                    navController.navigate(EraPage(eraId = era.id, sectionId = section.id)) {
                        launchSingleTop = true
                    }
                },
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
                setFavorite = viewmodel::setFavorite,
            )
        }

        composable<EraPage> {
            val viewModel: EraViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect: EraEffect ->
                    when (effect) {
                        EraEffect.Back -> {
                            // Pops only EraPage: a second tap during the exit
                            // finds no EraPage and does nothing, instead of
                            // popping MainPage and leaving a blank screen.
                            navController.popBackStack<EraPage>(inclusive = true)
                        }

                        is EraEffect.OpenDetail -> {
                            navController.navigate(DetailPage(effect.artworkId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
            EraScreen(state = state, onIntent = viewModel::onIntent)
        }

        composable<AuthPage> {
            val viewmodel : AuthViewModel = hiltViewModel()
            val state by viewmodel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var requesting by remember { mutableStateOf(false) }

            val openMain: () -> Unit = {
                navController.navigate(MainPage)
                {
                    popUpTo<AuthPage> { inclusive = true}
                }
            }
            LaunchedEffect(Unit) {
                viewmodel.effects.collect {
                    when(it)
                    {
                        AuthEffect.SignedIn -> {
                            openMain()
                        }
                    }
                }
            }
            AuthScreen(
                state = state,
                onGoogleLogin = {
                    if (!requesting && !state.isLoading) {
                        requesting = true
                        scope.launch {
                            try {
                                viewmodel.onGoogleIdTokenResult(requestGoogleIdToken(context))
                            } finally {
                                requesting = false
                            }
                        }
                    }
                },
                onNotNow = openMain
            )
        }
    }
}