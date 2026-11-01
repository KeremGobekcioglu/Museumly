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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.presentation.R
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
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = canSwipe,
        ) { page: Int ->
            if (page == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Spacer(modifier = Modifier.height(56.dp))

                    // Not clipped: at 6.5× the work spills over the whole screen.
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.harvesters),
                            contentDescription = stringResource(R.string.onboarding_work_description),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale.value
                                    scaleY = scale.value
                                    transformOrigin = TransformOrigin(0.34f, 0.8f)
                                },
                        )
                    }

                    // Drawn after the work, so the text sits on top of the zoomed image.
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        // Look closer — gradient keeps it readable over the zoomed detail.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = lookCloserAlpha.value }
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_look_closer),
                                fontFamily = LibreCaslon,
                                fontStyle = FontStyle.Italic,
                                fontSize = 32.sp,
                                color = TextPrimary,
                            )
                        }

                        // Promise
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 32.dp)
                                .graphicsLayer {
                                    alpha = promiseAlpha.value
                                    translationY = (1f - promiseAlpha.value) * 8.dp.toPx()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_promise),
                                fontFamily = LibreCaslon,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                textAlign = TextAlign.Center,
                                color = TextPrimary,
                            )
                            Text(
                                text = stringResource(R.string.onboarding_source).uppercase(),
                                fontSize = 12.sp,
                                letterSpacing = 1.6.sp,
                                color = TextMuted,
                            )
                        }

                        // Handoff — hint above the caption.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .graphicsLayer { alpha = handoffAlpha.value },
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_swipe_hint),
                                fontSize = 13.sp,
                                color = TextPrimary,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .background(Color(0xD1141413), RoundedCornerShape(50))
                                    .border(1.dp, TextPrimary.copy(alpha = 0.18f), RoundedCornerShape(50))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.onboarding_work_title),
                                    fontFamily = LibreCaslon,
                                    fontSize = 21.sp,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_work_artist),
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        if (phase != OnboardingPhase.HANDOFF) {
            TextButton(
                onClick = onFinish,
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