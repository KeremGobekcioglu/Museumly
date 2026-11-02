package com.kg.museumly.presentation.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.presentation.R
import com.kg.museumly.presentation.navigation.MainBottomBarHeight
import com.kg.museumly.presentation.feature.scroll.presentation.components.ArtworkActions
import com.kg.museumly.presentation.feature.scroll.presentation.components.ArtworkCaption
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsTopBarHeight
import com.kg.museumly.presentation.feature.scroll.presentation.components.museumName
import com.kg.museumly.presentation.ui.theme.LibreCaslon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class OnboardingPhase { LOOK_CLOSER, REVEAL, HANDOFF }

private const val START_SCALE: Float = 6.5f
private val PullBackEasing: Easing = CubicBezierEasing(0.22f, 0.61f, 0.2f, 1f)
private val TextPrimary: Color = Color(0xFFF4EFE6)
private val TextMuted: Color = Color(0xFF9A958C)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var phase: OnboardingPhase by remember { mutableStateOf(OnboardingPhase.LOOK_CLOSER) }
    var canSwipe: Boolean by remember { mutableStateOf(false) }
    val scale: Animatable<Float, AnimationVector1D> = remember { Animatable(START_SCALE) }
    val lookCloserAlpha: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
    val promiseAlpha: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
    val handoffAlpha: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
    val pagerState: PagerState = rememberPagerState(pageCount = { 2 })
    val nudgePx: Float = with(LocalDensity.current) { 28.dp.toPx() }

    LaunchedEffect(Unit) {
        // 1 · Look closer: the line comes and goes while the pull-back runs.
        launch {
            lookCloserAlpha.animateTo(1f, tween(durationMillis = 600, delayMillis = 300))
            delay(1800)
            lookCloserAlpha.animateTo(0f, tween(durationMillis = 800))
        }
        scale.animateTo(1f, tween(durationMillis = 3400, delayMillis = 600, easing = PullBackEasing))

        // 2 · Reveal: the whole work and the promise.
        phase = OnboardingPhase.REVEAL
        promiseAlpha.animateTo(1f, tween(durationMillis = 700))
        delay(1600)

        // 3 · Handoff: promise out, caption and hint in, one nudge, then the swipe is theirs.
        promiseAlpha.animateTo(0f, tween(durationMillis = 400))
        phase = OnboardingPhase.HANDOFF
        handoffAlpha.animateTo(1f, tween(durationMillis = 500))
        delay(300)
        pagerState.animateScrollBy(nudgePx, tween(durationMillis = 350, easing = FastOutSlowInEasing))
        pagerState.animateScrollBy(-nudgePx, tween(durationMillis = 450, easing = FastOutSlowInEasing))
        canSwipe = true
    }

    // Settling on the black page is the swipe-up that ends onboarding.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page: Int ->
            if (page == 1) {
                onFinish()
            }
        }
    }
    OnboardingContent(
        pagerState = pagerState,
        scale = { scale.value },
        lookCloserAlpha = { lookCloserAlpha.value },
        promiseAlpha = { promiseAlpha.value },
        handoffAlpha = { handoffAlpha.value },
        canSwipe = canSwipe,
        showSkip = phase != OnboardingPhase.HANDOFF,
        onSkip = onFinish,
    )
}

/**
 * The screen with its animation values passed in, so a preview can show any
 * moment of the timeline. Values are lambdas: they are read at draw time.
 */
@Composable
internal fun OnboardingContent(
    pagerState: PagerState,
    scale: () -> Float,
    lookCloserAlpha: () -> Float,
    promiseAlpha: () -> Float,
    handoffAlpha: () -> Float,
    canSwipe: Boolean,
    showSkip: Boolean,
    onSkip: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = canSwipe,
        ) { page: Int ->
            if (page == 0) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Same top as a feed page: status bar + the pill's row.
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
                    Spacer(modifier = Modifier.height(ReelsTopBarHeight))

                    // Same artwork box as a feed page. Not clipped: at 6.5x the work spills over the screen.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // Sides only: a little air round the work; top and bottom as the feed.
                            .padding(horizontal = 4.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val painter: Painter = painterResource(R.drawable.harvesters)
                        val ratio: Float = painter.intrinsicSize.width / painter.intrinsicSize.height
                        Image(
                            painter = painter,
                            contentDescription = stringResource(R.string.onboarding_work_description),
                            contentScale = ContentScale.Fit,
                            // Bounds = the painting itself, so transformOrigin points at paint, not letterbox.
                            modifier = Modifier
                                .aspectRatio(ratio)
                                .graphicsLayer {
                                    scaleX = scale()
                                    scaleY = scale()
                                    transformOrigin = TransformOrigin(0.18f, 0.72f)
                                },
                        )
                    }

                    // Same label slot as a feed page. Its height is the caption (the tallest
                    // layer), so the artwork box is identical in every phase.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Full width, behind the gutter: keeps "Look closer." readable
                            // over the zoomed detail. Alpha is read at draw time.
                            .drawBehind {
                                drawRect(
                                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                                    alpha = lookCloserAlpha() * 0.85f,
                                )
                            }
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            )
                            // The feed's caption sits above the bar; ArtworkCaption adds the rest.
                            .padding(bottom = MainBottomBarHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Swipe hint: sits just above the caption without taking height from the
                        // slot, so the title stays where the feed puts it.
                        Text(
                            text = stringResource(R.string.onboarding_swipe_hint),
                            fontSize = 13.sp,
                            color = TextPrimary,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    val gap: Int = 8.dp.roundToPx()
                                    layout(placeable.width, 0) { placeable.place(0, -placeable.height - gap) }
                                }
                                .graphicsLayer { alpha = handoffAlpha() }
                                .background(Color(0xD1141413), RoundedCornerShape(50))
                                .border(1.dp, TextPrimary.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )

                        // Look closer
                        Text(
                            text = stringResource(R.string.onboarding_look_closer),
                            fontFamily = LibreCaslon,
                            fontStyle = FontStyle.Italic,
                            fontSize = 32.sp,
                            color = TextPrimary,
                            modifier = Modifier.graphicsLayer { alpha = lookCloserAlpha() },
                        )

                        // Promise
                        Column(
                            // The slot no longer pads, so the promise brings the gutter.
                            modifier = Modifier.padding(horizontal = ReelsGutter).graphicsLayer {
                                alpha = promiseAlpha()
                                translationY = (1f - promiseAlpha()) * 8.dp.toPx()
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_promise),
                                fontFamily = LibreCaslon,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                textAlign = TextAlign.Center,
                                color = TextPrimary,
                            )
                        }

                        // Handoff: the feed's own caption.
                        ArtworkCaption(
                            title = stringResource(R.string.onboarding_work_title),
                            makerLine = stringResource(R.string.onboarding_work_artist),
                            museum = museumName("met"),
                            department = stringResource(R.string.onboarding_work_department),
                            // The feed's real actions, invisible: the row keeps exactly the feed's height.
                            actions = {
                                Box(modifier = Modifier.alpha(0f).clearAndSetSemantics {}) {
                                    ArtworkActions(
                                        isFavorite = false,
                                        onFavoriteClick = {},
                                        // Non-null so the stack has the feed's two buttons.
                                        onShareClick = {},
                                        verticalState = true,
                                    )
                                }
                            },
                            modifier = Modifier.graphicsLayer { alpha = handoffAlpha() },
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        if (showSkip) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 8.dp),
            ) {
                Text(text = stringResource(R.string.onboarding_skip), fontSize = 14.sp, color = TextMuted)
            }
        }
    }
}
