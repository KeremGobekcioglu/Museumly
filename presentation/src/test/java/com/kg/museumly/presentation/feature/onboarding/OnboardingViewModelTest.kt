package com.kg.museumly.presentation.feature.onboarding

import com.kg.museumly.presentation.testutil.FakeFeedWarmup
import com.kg.museumly.presentation.testutil.FakeOnboardingSource
import com.kg.museumly.presentation.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun finishWritesTheFlagOnceAndFinishesOnce() = runTest(dispatcher) {
        val source: FakeOnboardingSource = FakeOnboardingSource()
        val viewModel: OnboardingViewModel = OnboardingViewModel(source, FakeFeedWarmup())
        val writesWhenFinished: MutableList<Int> = mutableListOf()
        backgroundScope.launch {
            viewModel.effects.collect { effect: OnboardingEffect ->
                if (effect == OnboardingEffect.Finished) {
                    writesWhenFinished.add(source.completedCalls)
                }
            }
        }

        // runCurrent, not advanceUntilIdle: the latter ignores backgroundScope
        // tasks, so the collector would never see the effect.
        viewModel.finish()
        viewModel.finish()      // Skip tapped during the swipe
        runCurrent()
        viewModel.finish()      // a late call after the first one completed
        runCurrent()

        assertEquals(1, source.completedCalls)
        // One Finished, and the flag was already written when it arrived.
        assertEquals(listOf(1), writesWhenFinished)
    }
}
