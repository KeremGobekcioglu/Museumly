package com.kg.museumly

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kg.museumly.presentation.navigation.MuseumlyNavGraph
import com.kg.museumly.presentation.navigation.StartViewModel
import com.kg.museumly.presentation.ui.theme.MuseumlyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startViewModel: StartViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash: SplashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition {
            return@setKeepOnScreenCondition startViewModel.onboardingDone.value == null
        }
        // The app is always black, so the system bars use light icons on a
        // transparent bar regardless of the system light/dark setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val onboardingDone: Boolean? by startViewModel.onboardingDone.collectAsStateWithLifecycle()
            MuseumlyTheme {
                val done: Boolean? = onboardingDone
                if (done != null) {
                    MuseumlyNavGraph(onboardingDone = done)
                }
            }
        }
    }
}
