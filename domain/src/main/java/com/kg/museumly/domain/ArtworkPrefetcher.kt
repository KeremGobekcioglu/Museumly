package com.kg.museumly.domain

interface ArtworkPrefetcher {
    fun prefetch(urls: List<String>)
}