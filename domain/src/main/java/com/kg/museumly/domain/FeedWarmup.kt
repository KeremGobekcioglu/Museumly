package com.kg.museumly.domain

/**
 * Loads the first page of the feed ahead of time, while onboarding is on screen,
 * so a first-time user doesn't land on a loading page.
 */
interface FeedWarmup {
    /** Starts the load. Does nothing if it was already started. */
    fun start()

    /** Returns once a started warm-up has finished. Returns at once if none is running. */
    suspend fun awaitIfRunning()
}