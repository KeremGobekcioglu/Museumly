package com.kg.museumly.presentation.testutil

import com.kg.museumly.domain.FeedWarmup

class FakeFeedWarmup : FeedWarmup {

    var startCalls: Int = 0
    var awaitCalls: Int = 0

    override fun start() {
        startCalls++
    }

    override suspend fun awaitIfRunning() {
        awaitCalls++
    }
}