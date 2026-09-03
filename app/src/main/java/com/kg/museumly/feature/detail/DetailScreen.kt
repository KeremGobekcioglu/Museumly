package com.kg.museumly.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import com.kg.museumly.model.ArtworkWithDetail

@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F3EF)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(color = Color.Black)

            state.notFound || state.data == null -> Text(
                text = "Couldn't find this artwork",
                color = Color.Black,
                style = MaterialTheme.typography.bodyLarge,
            )

            else -> DetailContent(data = state.data)
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun DetailContent(data: ArtworkWithDetail, modifier: Modifier = Modifier) {
    val artwork: Artwork = data.artwork
    val detail: ArtworkDetail = data.detail

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        AsyncImage(
            model = artwork.imageUrl,
            contentDescription = artwork.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )

        Text(
            text = artwork.title,
            color = Color.Black,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (artwork.artist != null) {
            Text(
                text = artwork.artist,
                color = Color.Black.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (artwork.year != null) {
            Text(
                text = artwork.year,
                color = Color.Black.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LabelRow(label = "Medium", value = detail.medium)
        LabelRow(label = "Dimensions", value = detail.dimensions)
        LabelRow(label = "Culture", value = detail.culture)
        LabelRow(label = "Period", value = detail.period)
        LabelRow(label = "Credit", value = detail.creditLine)
    }
}

@Composable
private fun LabelRow(label: String, value: String?) {
    if (value.isNullOrBlank()) {
        return
    }
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = label,
            color = Color.Black.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = Color.Black,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}