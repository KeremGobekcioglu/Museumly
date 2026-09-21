package com.kg.museumly.feature.detail

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kg.museumly.feature.scroll.presentation.components.GalleryGeometry
import com.kg.museumly.feature.scroll.presentation.components.GalleryNotice
import com.kg.museumly.feature.scroll.presentation.components.GalleryPlacard
import com.kg.museumly.feature.scroll.presentation.components.PendantLamp
import com.kg.museumly.feature.scroll.presentation.components.WallColor
import com.kg.museumly.feature.scroll.presentation.components.hangingGeometry
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkWithDetail
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable so a rotation mid-inspection doesn't dump the user
    // back onto the wall.
    var inspecting: Boolean by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = inspecting) {
        inspecting = false
    }

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

            else -> DetailContent(
                data = state.data,
                inspecting = inspecting,
                onInspect = { inspecting = true },
                onExit = { inspecting = false },
            )
        }

        // Hidden while inspecting — InspectOverlay has its own back button,
        // gated on zoom level instead of on inspecting/not.
        AnimatedVisibility(
            visible = !inspecting,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            IconButton(
                onClick = { onBack() },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
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
private fun DetailContent(
    data: ArtworkWithDetail,
    inspecting: Boolean,
    onInspect: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork: Artwork = data.artwork

    var ratio: Float? by remember(artwork.id) {
        mutableStateOf(artwork.aspectRatio)
    }
    val reveal: Animatable<Float, AnimationVector1D> = remember(artwork.id) {
        Animatable(0f)
    }
    val ready: Boolean = ratio != null
    var placeholderKey: MemoryCache.Key? by remember(artwork.id) {
        mutableStateOf(null)
    }

    // Not redundant with the nav fade. The nav fade is timed from the tap;
    // this waits for the aspect ratio. For Met records the ratio arrives
    // after the screen does, and without this the frame is visible at the
    // 0.8 guess and then snaps to the real shape mid-transition.
    LaunchedEffect(ready) {
        if (ready) {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500),
            )
        }
    }

    // The room fades out behind the inspect overlay rather than being
    // removed — nothing competes with the artwork once the user has leaned
    // in, and the room returning on exit is half the pleasure of the
    // transition.
    val roomAlpha: Float by animateFloatAsState(
        targetValue = if (inspecting) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "room",
    )

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
                .graphicsLayer { alpha = reveal.value * roomAlpha },
        ) {
            PendantLamp(geometry = geometry, modifier = Modifier.fillMaxSize())

            ArtworkFrame(
                imageUrl = artwork.imageUrl,
                contentDescription = artwork.title,
                onImageLoaded = { width, height, cacheKey ->
                    placeholderKey = cacheKey
                    if (ratio == null) {
                        ratio = width.toFloat() / height.toFloat()
                    }
                },
                onImageFailed = {
                    if (ratio == null) {
                        ratio = 0.8f
                    }
                },
                onClick = {
                    // Stops a tap on the invisible frame before the room
                    // has revealed.
                    if (ready) {
                        onInspect()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = geometry.frameTop)
                    .size(width = geometry.frameWidth, height = geometry.frameHeight),
            )

            GalleryPlacard(
                title = artwork.title,
                body = artwork.artist,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = geometry.placardTop),
            )
        }

        AnimatedVisibility(
            visible = inspecting,
            enter = fadeIn(animationSpec = tween(durationMillis = 250)),
            exit = fadeOut(animationSpec = tween(durationMillis = 250)),
        ) {
            InspectOverlay(
                imageUrl = data.detail.highResImageUrl ?: artwork.imageUrl,
                placeholderKey = placeholderKey,
                contentDescription = artwork.title,
                onExit = onExit,
            )
        }
    }
}

/**
 * No falloff gradient, no frame edges here — once the user has leaned in,
 * they see the work unaltered.
 *
 * AnimatedVisibility removes this from composition when the exit fade
 * finishes, which releases the zoom state and the high-res image on
 * leaving — every entry starts fresh at fit.
 */
@Composable
private fun InspectOverlay(
    imageUrl: String,
    placeholderKey: MemoryCache.Key?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onExit: () -> Unit
) {
    val context: Context = LocalContext.current
    val request: ImageRequest = remember(imageUrl, placeholderKey) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .placeholderMemoryCacheKey(placeholderKey)
            // The high-res bitmap must never land in the shared memory
            // cache — one 4000px original can evict the whole feed.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .crossfade(300)
            .build()
    }

    val zoomableState: ZoomableState = rememberZoomableState()
    val zoomed: Boolean by remember {
        derivedStateOf { (zoomableState.zoomFraction ?: 0f) > 0.01f }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ZoomableAsyncImage(
            model = request,
            contentDescription = contentDescription,
            state = rememberZoomableImageState(zoomableState),
            modifier = Modifier
                .fillMaxSize()
                .background(WallColor),
        )

        AnimatedVisibility(
            visible = !zoomed,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
        }
    }
}