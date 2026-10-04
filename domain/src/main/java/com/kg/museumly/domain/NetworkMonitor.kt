package com.kg.museumly.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * "Does the device think its default network reaches the internet?"
 * Not a promise that any request will succeed. Used to interpret
 * failures, never to block requests.
 */
interface NetworkMonitor
{
    val isOnline: StateFlow<Boolean>
}