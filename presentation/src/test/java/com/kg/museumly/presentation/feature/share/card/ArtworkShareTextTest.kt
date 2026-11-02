package com.kg.museumly.presentation.feature.share.card

import com.kg.museumly.presentation.testutil.sampleArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArtworkShareTextTest {

    @Test
    fun `text lists title, maker, museum and the app name`() {
        val artwork = sampleArtwork(id = "met:1", title = "The Harvesters")

        val text = artworkShareText(artwork, appName = "Museumly", landingUrl = "")

        assertEquals("The Harvesters\nTest Artist · 1900\nThe Met\n\nMuseumly", text)
    }

    @Test
    fun `link is appended only when a landing page is configured`() {
        val artwork = sampleArtwork(id = "met:1")

        val withLink = artworkShareText(artwork, appName = "Museumly", landingUrl = "https://museumly.app")
        val withoutLink = artworkShareText(artwork, appName = "Museumly", landingUrl = "  ")

        assertEquals("https://museumly.app", withLink.lines().last())
        assertFalse(withoutLink.contains("http"))
    }

    @Test
    fun `maker line is skipped when the work has no artist or year`() {
        val artwork = sampleArtwork(id = "cleveland:2", title = "Untitled")
            .copy(artist = null, year = null)

        val text = artworkShareText(artwork, appName = "Museumly", landingUrl = "")

        assertEquals("Untitled\nCleveland Museum of Art\n\nMuseumly", text)
    }
}
