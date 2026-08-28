package com.kg.museumly.domain

import com.kg.museumly.model.Artwork

/**
 * caller needs to know when a provider is exhausted. next == null.
 */
data class PageResult(
    val items: List<Artwork>,
    val next: String?
)

interface ArtworkProvider {
    // providers need to have an id. so we can differentiate them.
    val id: String
    suspend fun fetchPage(cursor: String?, size: Int) : PageResult
}