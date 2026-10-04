package com.kg.museumly.testutil

import com.kg.museumly.domain.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {

    private val onlineFlow = MutableStateFlow(initiallyOnline)

    override val isOnline: StateFlow<Boolean> = onlineFlow

    fun setOnline(online: Boolean) {
        onlineFlow.value = online
    }
}
