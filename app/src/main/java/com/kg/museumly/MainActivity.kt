package com.kg.museumly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.kg.museumly.navigation.MuseumlyNavGraph
import com.kg.museumly.ui.theme.MuseumlyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuseumlyTheme {
                val navController = rememberNavController()
                MuseumlyNavGraph(navController)
            }
        }
    }
}
