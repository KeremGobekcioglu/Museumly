package com.kg.museumly.presentation.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.domain.OnboardingSourceInterface
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingSource: OnboardingSourceInterface
) : ViewModel() {

    private val _effects: Channel<OnboardingEffect> = Channel(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    private var finishJob: Job? = null

    // Skip and the swipe-up both end here. Runs once: a Skip tap during the
    // swipe, or a second swipe, must not write and navigate twice.
    fun finish() {
        if (finishJob != null) {
            return
        }
        finishJob = viewModelScope.launch {
            onboardingSource.setCompleted()
            _effects.send(OnboardingEffect.Finished)
        }
    }
}