package com.kg.museumly.presentation.testutil

import com.kg.museumly.domain.OnboardingSourceInterface

class FakeOnboardingSource : OnboardingSourceInterface {

    var completedCalls: Int = 0

    override suspend fun hasCompleted(): Boolean {
        return completedCalls > 0
    }

    override suspend fun setCompleted() {
        completedCalls = completedCalls + 1
    }
}
