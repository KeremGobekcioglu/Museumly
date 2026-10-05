package com.kg.museumly.presentation.feature.scroll.presentation


import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.presentation.testutil.sampleArtwork
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The only favorites logic in the UI: which heart a page shows
 * (favoriteIds.contains(artwork.id)) and what a tap sends
 * (that page's id, and the opposite of its current value).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ArtworkReelsScreenFavoriteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val artworks: List<Artwork> = listOf(sampleArtwork("met:1"), sampleArtwork("met:2"))

    private var tappedId: String? = null
    private var tappedValue: Boolean? = null

    private fun show(favoriteIds: Set<String>) {
        val state = ScrollUiState(
            artworks = artworks,
            initialPage = 0,
            tail = TailState.Idle,
            favoriteIds = favoriteIds,
        )
        composeRule.setContent {
            ArtworkReelsScreen(
                state = state,
                refresh = {},
                onPageChanged = {},
                onDetailPage = {},
                onSectionSelected = {},
                setFavorite = { id: String, value: Boolean ->
                    tappedId = id
                    tappedValue = value
                },
            )
        }
    }

    @Test
    fun `tapping the heart on a page that is not a favorite asks to add that page's artwork`() {
        // The neighbour is the favorite, not the page on screen.
        show(favoriteIds = setOf("met:2"))

        composeRule.onNodeWithContentDescription("Add to favorites").performClick()
        composeRule.waitForIdle()

        assertEquals("met:1", tappedId)
        assertEquals(true, tappedValue)
    }

    @Test
    fun `tapping the heart on a favorite page asks to remove that page's artwork`() {
        show(favoriteIds = setOf("met:1"))

        composeRule.onNodeWithContentDescription("Remove from favorites").performClick()
        composeRule.waitForIdle()

        assertEquals("met:1", tappedId)
        assertEquals(false, tappedValue)
    }
}