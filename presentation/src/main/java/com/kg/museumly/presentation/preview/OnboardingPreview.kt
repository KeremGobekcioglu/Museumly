package com.kg.museumly.presentation.preview

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.presentation.feature.onboarding.OnboardingContent
import com.kg.museumly.presentation.ui.theme.MuseumlyTheme

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 411, heightDp = 891)
@Composable
private fun OnboardingHandoffPreview() {
    MuseumlyTheme {
        OnboardingContent(
            pagerState = rememberPagerState(pageCount = { 2 }),
            scale = { 1f },
            lookCloserAlpha = { 0f },
            promiseAlpha = { 0f },
            handoffAlpha = { 1f },
            canSwipe = true,
            showSkip = false,
            onSkip = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 411, heightDp = 891)
@Composable
private fun OnboardingRevealPreview() {
    MuseumlyTheme {
        OnboardingContent(
            pagerState = rememberPagerState(pageCount = { 2 }),
            scale = { 1f },
            lookCloserAlpha = { 0f },
            promiseAlpha = { 1f },
            handoffAlpha = { 0f },
            canSwipe = false,
            showSkip = true,
            onSkip = {},
        )
    }
}
