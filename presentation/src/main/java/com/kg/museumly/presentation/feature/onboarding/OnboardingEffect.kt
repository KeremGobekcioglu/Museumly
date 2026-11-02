package com.kg.museumly.presentation.feature.onboarding

sealed interface OnboardingEffect {
    data object Finished : OnboardingEffect
}