package com.kg.museumly.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kg.museumly.domain.OnboardingSourceInterface
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch



@HiltViewModel
class StartViewModel @Inject constructor(
    private val onboardingSource: OnboardingSourceInterface
) : ViewModel() {

    // null = not read yet; the splash stays up while it is null.
    private val _onboardingDone: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val onboardingDone: StateFlow<Boolean?> = _onboardingDone.asStateFlow()

    /**
     * Why it reads once instead of observing a Flow: NavHost rebuilds its graph when startDestination changes,
     * and that resets the back stack. If this value were a live Flow, writing done = true at the end of
     * onboarding would change the start destination
     * while the user is on the feed and wipe it. Reading once means the value is fixed for the whole session.
     */
    init {
        viewModelScope.launch {
            val done: Boolean = onboardingSource.hasCompleted()
            _onboardingDone.value = done
        }
    }
}