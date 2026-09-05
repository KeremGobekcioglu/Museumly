package com.kg.museumly.domain

interface ArtworkProvider {
    // providers need to have an id. so we can differentiate them.
    val id: String
    suspend fun fetchPage(cursor: String?, size: Int) : PageResult
}