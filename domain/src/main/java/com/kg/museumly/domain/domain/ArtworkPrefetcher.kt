package com.kg.museumly.domain.domain

interface ArtworkPrefetcher {
    fun prefetch(urls: List<String>)
}