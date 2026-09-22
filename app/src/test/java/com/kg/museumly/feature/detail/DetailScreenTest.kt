package com.kg.museumly.feature.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DetailScreen takes state + callbacks with no injected ViewModel, so its
 * not-found branch can be tested with a plain state object. Protects that a
 * missing/removed artwork shows the notice instead of silently rendering
 * nothing or crashing on a null ArtworkWithDetail.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `not found state shows the notice instead of the artwork`() {
        composeRule.setContent {
            DetailScreen(
                state = DetailUiState(data = null, isLoading = false, notFound = true),
                onBack = {},
                onInspected = {},
            )
        }

        composeRule.onNodeWithText("Couldn't find this artwork").assertIsDisplayed()
    }
}
