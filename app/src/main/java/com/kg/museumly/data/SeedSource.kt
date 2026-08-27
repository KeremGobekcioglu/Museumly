package com.kg.museumly.data

import com.kg.museumly.model.Artwork
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedSource @Inject constructor() {

    fun artworks(): List<Artwork> {
        return sampleArtworks
    }
}