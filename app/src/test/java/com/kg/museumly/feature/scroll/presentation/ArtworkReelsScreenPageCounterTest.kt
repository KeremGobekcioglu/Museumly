package com.kg.museumly.feature.scroll.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kg.museumly.domain.model.Section
import com.kg.museumly.testutil.sampleArtwork
import org.junit.Assert.assertEquals
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
                onSectionSelected = {},
            )
        }

        composeRule.onNodeWithText("You've walked the whole gallery").assertIsDisplayed()
        composeRule.onNodeWithText("${artworks.size} / ${artworks.size}").assertDoesNotExist()
    }

    @Test
    fun `section picker stays reachable on the empty loading state and reports a selection`() {
        // No artworks and tail Loading is ReelsContent's early-return path —
        // the picker is drawn outside it, so it must still be there.
        val state = ScrollUiState(section = Section.EUROPEAN, artworks = emptyList(), tail = TailState.Loading)
        var selected: Section? = null

        composeRule.setContent {
            ArtworkReelsScreen(
                state = state,
                refresh = {},
                onPageChanged = {},
                onDetailPage = {},
                onSectionSelected = { selected = it },
            )
        }

        composeRule.onNodeWithText("European").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Asia").performClick()
        composeRule.waitForIdle()

        assertEquals(Section.ASIA, selected)
    }
}
