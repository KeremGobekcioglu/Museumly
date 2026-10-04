package com.kg.museumly.testutil

import com.kg.museumly.domain.ArtworkPrefetcher

class FakeArtworkPrefetcher : ArtworkPrefetcher {

    val prefetchedBatches: MutableList<List<String>> = mutableListOf()

    override fun prefetch(urls: List<String>) {
        prefetchedBatches.add(urls)
    }
}
