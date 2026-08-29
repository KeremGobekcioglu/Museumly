package com.kg.museumly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kg.museumly.feature.scroll.presentation.ScrollUiState
import com.kg.museumly.feature.scroll.presentation.ScrollViewModel
import com.kg.museumly.ui.theme.MuseumlyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuseumlyTheme {
                val viewModel: ScrollViewModel = hiltViewModel()
                val state: ScrollUiState by viewModel.uiState.collectAsStateWithLifecycle()
                ArtworkReelsScreen(state , viewModel::loadMore, viewModel::onPageChanged)
            }
        }
    }
}
