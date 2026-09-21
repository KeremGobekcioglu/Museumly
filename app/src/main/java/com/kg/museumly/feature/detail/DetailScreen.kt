package com.kg.museumly.feature.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kg.museumly.feature.scroll.presentation.components.FrameLine
import com.kg.museumly.feature.scroll.presentation.components.GalleryGeometry
import com.kg.museumly.feature.scroll.presentation.components.GalleryNotice
import com.kg.museumly.feature.scroll.presentation.components.GalleryPlacard
import com.kg.museumly.feature.scroll.presentation.components.PendantLamp
import com.kg.museumly.feature.scroll.presentation.components.WallColor
import com.kg.museumly.feature.scroll.presentation.components.hangingGeometry
import com.kg.museumly.model.Artwork
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
            .background(WallColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Room-backed, so this resolves in milliseconds — a spinner here
            // would only flicker. Just the wall until it does.
            state.isLoading -> {}

            state.notFound || state.data == null -> GalleryNotice(
                title = "Couldn't find this artwork",
                body = "It may have been removed from the collection.",
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

/**
 * The room, with the real artwork where GalleryLoading's PaintingFrame was.
 * No scroll, no zoom yet — this step is only about the room. Zoom comes back
 * behind a tap into a full-screen inspect mode; the catalogue text (medium,
 * dimensions, credit) comes back in a bottom sheet. Both are next steps.
 */
@Composable
private fun DetailContent(data: ArtworkWithDetail, modifier: Modifier = Modifier) {
    val artwork: Artwork = data.artwork

    var ratio: Float? by remember(artwork.id) {
        mutableStateOf(artwork.aspectRatio)
    }
    val reveal: Animatable<Float, AnimationVector1D> = remember(artwork.id) {
        Animatable(0f)
    }
    val ready: Boolean = ratio != null

    // Cleveland already knows its ratio on the first frame (real metadata),
    // so this fires immediately. Met starts null and only flips to ready
    // once onSuccess below reports the decoded shape — the frame is drawn
    // at its final size underneath the fade, so there's nothing to resize
    // once it's visible.
    LaunchedEffect(ready) {
        if (ready) {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 250),
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WallColor),
    ) {
        val geometry: GalleryGeometry = hangingGeometry(
            width = maxWidth,
            height = maxHeight,
            aspectRatio = ratio ?: 0.8f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = reveal.value },
        ) {
            PendantLamp(geometry = geometry, modifier = Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = geometry.frameTop)
                    .size(width = geometry.frameWidth, height = geometry.frameHeight)
                    .border(width = 1.dp, color = FrameLine.copy(alpha = 0.22f)),
            ) {
                AsyncImage(
                    model = artwork.imageUrl,
                    contentDescription = artwork.title,
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        if (ratio == null) {
                            val width: Int = state.result.image.width
                            val height: Int = state.result.image.height
                            if (width > 0 && height > 0) {
                                ratio = width.toFloat() / height.toFloat()
                            }
                        }
                    },
                    onError = { _ ->
                        if (ratio == null) {
                            ratio = 0.8f
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            GalleryPlacard(
                title = artwork.title,
                body = artwork.artist,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = geometry.placardTop),
            )
        }
    }
}