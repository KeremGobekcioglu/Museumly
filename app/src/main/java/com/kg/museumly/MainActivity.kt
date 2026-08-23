package com.kg.museumly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kg.museumly.ui.theme.MuseumlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuseumlyTheme {
                ArtworkReelsScreen(artworks = sampleArtworks)
            }
        }
    }
}
