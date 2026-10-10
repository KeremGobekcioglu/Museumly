package com.kg.museumly.presentation.feature.favorites


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.kg.museumly.domain.model.Artwork

@Composable
internal fun FavoritesTile(
    artwork: Artwork,
    onClick: () -> Unit,
    onRatioLearned: (Float) -> Unit,
    modifier: Modifier = Modifier
)
{
    var loadedRatio: Float? by remember(artwork.id) { mutableStateOf(null) }
    val ratio: Float = artwork.aspectRatio ?: loadedRatio ?: 1f

    AsyncImage(
        model = artwork.imageUrl,
        contentDescription = artwork.title,
        contentScale = ContentScale.Fit,
        onSuccess = { success: AsyncImagePainter.State.Success ->
            val size : Size = success.painter.intrinsicSize
            if(size.width > 0f && size.height > 0f)
            {
                val learned: Float = size.width / size.height
                loadedRatio = learned
                if (artwork.aspectRatio == null)
                {
                    onRatioLearned(learned)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
    )
}