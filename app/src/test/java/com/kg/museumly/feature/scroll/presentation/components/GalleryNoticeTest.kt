package com.kg.museumly.feature.scroll.presentation.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GalleryNotice/GalleryPlacard is the only retry affordance the feed has —
 * it backs both the cold-failure and tail-failure states in
 * ArtworkReelsScreen. Protects that tapping it actually invokes the
 * callback, and that it's inert while a retry is already in flight.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GalleryNoticeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `retry action invokes the callback when tapped`() {
        var clicks = 0
        composeRule.setContent {
            GalleryNotice(
                title = "The gallery didn't open",
                body = "Something went wrong loading the collection.",
                actionLabel = "Retry",
                onAction = { clicks++ },
            )
        }

        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `retry action is disabled while a retry is already busy`() {
        composeRule.setContent {
            GalleryNotice(
                title = "The gallery didn't open",
                body = "Something went wrong loading the collection.",
                actionLabel = "Retry",
                onAction = {},
                isBusy = true,
            )
        }

        composeRule.onNodeWithText("Retry").assertIsNotEnabled()
    }
}
