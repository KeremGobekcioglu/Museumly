package com.kg.museumly.feature.detail

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.kg.museumly.feature.scroll.presentation.components.FrameLine
import com.kg.museumly.feature.scroll.presentation.components.GalleryGeometry
import com.kg.museumly.feature.scroll.presentation.components.GalleryNotice
import com.kg.museumly.feature.scroll.presentation.components.GalleryPlacard
import com.kg.museumly.feature.scroll.presentation.components.PendantLamp
import com.kg.museumly.feature.scroll.presentation.components.WallColor
import com.kg.museumly.feature.scroll.presentation.components.hangingGeometry
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkWithDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onInspected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable so a rotation mid-inspection doesn't dump the user
    // back onto the wall.
    var inspecting: Boolean by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WallColor)
            .navigationBarsPadding(),
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
                showInspectHint = state.showInspectHint,
                onInspect = {
                    inspecting = true
                    onInspected()
                },
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
    showInspectHint: Boolean,
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

    val context = LocalContext.current
    val highResUrl : String? = data.detail.highResImageUrl
    var highResReady: Boolean by remember(artwork.id) {
        mutableStateOf(false)
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

    // download high resolution image before user clicks the image.
    LaunchedEffect(highResUrl) {
        if(highResUrl == null) return@LaunchedEffect
        val request : ImageRequest = ImageRequest.Builder(context)
            .data(highResUrl)
            .memoryCachePolicy(CachePolicy.DISABLED)
            // Only the download matters here. A tiny decode size keeps this
            // from decoding a 4000px bitmap nobody is looking at.
            .size(256)
            .build()
        val result : ImageResult = SingletonImageLoader.get(context).execute(request)
        if(result is SuccessResult)
            highResReady = true
    }
    val progress by animateFloatAsState(
        targetValue = if (inspecting) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "walk-up"
    )
    // i think derived state of should be use here.
    val arrived = inspecting && progress >= 1f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WallColor)
            .safeDrawingPadding(),
    ) {
        val geometry: GalleryGeometry = hangingGeometry(
            width = maxWidth,
            height = maxHeight,
            aspectRatio = ratio ?: 0.8f,
        )

        val currentRatio = ratio ?: 0.8f
        val fitWidth: Dp
        val fitHeight: Dp
        if(currentRatio > maxWidth / maxHeight)
        {
            fitWidth = maxWidth
            fitHeight = maxWidth / currentRatio
        }
        else
        {
            fitHeight = maxHeight
            fitWidth = maxHeight * currentRatio
        }

        val startScale : Float = geometry.frameWidth / fitWidth
        val frameCenterY : Dp = geometry.frameTop + geometry.frameHeight / 2
        val startOffsetY : Dp = frameCenterY - maxHeight / 2
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = reveal.value * (1f - progress) },
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
                    .size(width = geometry.frameWidth, height = geometry.frameHeight)
                    .graphicsLayer {
                        alpha = if (progress > 0f) 0f else 1f
                    }
            )

            GalleryPlacard(
                title = artwork.title,
                body = artwork.artist,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = geometry.placardTop),
            )

            // Teaches once, then leaves for good — see FeedPositionSource.
            // hasInspected. A permanent "tap here" label is clutter; one
            // that disappears after it's learned is onboarding.
            if (showInspectHint) {
                InspectHint(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 40.dp),
                )
            }
        }

        // image is not ful size yet.
        if( progress > 0f && !arrived)
        {
            WalkUpImage(
                imageUrl = artwork.imageUrl,
                placeholderKey = placeholderKey,
                progress = progress,
                startScale = startScale,
                startOffsetY = startOffsetY,
                fitWidth = fitWidth,
                fitHeight = fitHeight,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // Composed as soon as inspecting starts, not at `arrived`, on purpose:
        // the zoomable image uses the 300ms flight to resolve its first frame,
        // so it's already showing an image when the flight lands. Composing it
        // at `arrived` risks a blank frame at landing. It does receive touches
        // while invisible during the flight; with no tap handler, they do nothing.
        if(inspecting)
        {
            InspectOverlay(
                lowResUrl = artwork.imageUrl,
                highResUrl = data.detail.highResImageUrl,
                highResReady = highResReady,
                placeholderKey = placeholderKey,
                contentDescription = artwork.title,
                onExit = onExit,
                modifier = Modifier.graphicsLayer{
                    alpha = if (arrived) 1f else 0f
                }
            )
        }
    }
}

/**
 * Small caps, wide spacing — reads like museum signage, not an app tooltip.
 * The slow breathing alpha is what makes it noticeable without shouting.
 */
@Composable
private fun InspectHint(modifier: Modifier = Modifier) {
    val transition: InfiniteTransition = rememberInfiniteTransition(label = "hint")
    val alpha: Float by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hint-alpha",
    )
    Text(
        text = "TAP THE PAINTING TO STEP CLOSER",
        color = FrameLine,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.sp,
        modifier = modifier.graphicsLayer { this.alpha = alpha },
    )
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
    lowResUrl: String,
    highResUrl: String?,
    highResReady: Boolean,
    placeholderKey: MemoryCache.Key?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onExit: () -> Unit
) {
    val context: Context = LocalContext.current
    val useHighRes = highResReady && highResUrl != null
    val model = if (useHighRes) highResUrl else lowResUrl
    Log.d("DETAIL SCREEN, INSPECT OVERLAY" , "HIGH RESOLUTION IMAGE : $highResReady")
    val request: ImageRequest = remember(model, placeholderKey) {
        val builder = ImageRequest.Builder(context)
            .data(model)
            .placeholderMemoryCacheKey(placeholderKey)
            .crossfade(300)
            if(useHighRes)
            {
                // The high-res bitmap must never land in the shared memory
                // cache — one 4000px original can evict the whole feed.
                builder.memoryCachePolicy(CachePolicy.DISABLED)
            }
            builder.build()
    }

    val zoomableState: ZoomableState = rememberZoomableState()
    val zoomed: Boolean by remember {
        derivedStateOf { (zoomableState.zoomFraction ?: 0f) > 0.01f }
    }

    val scope = rememberCoroutineScope()

    var controlsVisible: Boolean by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1500.milliseconds)
        controlsVisible = false
    }

    // Every way out goes through here. If zoomed, zoom back to fit first, so
// the flying image picks up from exactly where the zoomable image is
    fun stepBack()
    {
        scope.launch {
            if(zoomed)
            {
                zoomableState.resetZoom()
            }
            onExit()
        }
    }

    BackHandler {
        stepBack()
    }

    Box(modifier = modifier.fillMaxSize()) {
        ZoomableAsyncImage(
            model = request,
            contentDescription = contentDescription,
            state = rememberZoomableImageState(zoomableState),
            modifier = Modifier
                .fillMaxSize()
                .background(WallColor),
            onClick = { _ ->
                controlsVisible = !controlsVisible
            },
        )

        AnimatedVisibility(
            visible = controlsVisible && !zoomed,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            IconButton(
                onClick = { stepBack() },
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