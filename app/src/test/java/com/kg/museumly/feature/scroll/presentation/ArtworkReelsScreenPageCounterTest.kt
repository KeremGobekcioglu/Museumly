package com.kg.museumly.feature.scroll.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kg.museumly.testutil.sampleArtwork
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sentinel page (pageCount = artworks.size + 1, see README's "The
 * sentinel page") renders loading/failure/exhaustion, not an artwork — the
 * "N / total" counter belongs to a real artwork page and must not show on
 * it. Sets initialPage straight to the sentinel index so the pager never
 * composes a real artwork page (and its AsyncImage) at all.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ArtworkReelsScreenPageCounterTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `page counter is hidden on the sentinel page`() {
        val artworks = listOf(sampleArtwork("met:1"), sampleArtwork("met:2"))
        val state = ScrollUiState(
            artworks = artworks,
            initialPage = artworks.size,
            tail = TailState.Exhausted,
        )

        composeRule.setContent {
            ArtworkReelsScreen(
                state = state,
                refresh = {},
                onPageChanged = {},
                onDetailPage = {},
            )
        }

        composeRule.onNodeWithText("End of the gallery").assertIsDisplayed()
        composeRule.onNodeWithText("${artworks.size} / ${artworks.size}").assertDoesNotExist()
    }
}
