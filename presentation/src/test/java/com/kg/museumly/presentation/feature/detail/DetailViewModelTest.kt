package com.kg.museumly.presentation.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.kg.museumly.domain.FeedPositionSourceInterface
import com.kg.museumly.presentation.navigation.DetailPage
import com.kg.museumly.presentation.testutil.FakeArtworkRepository
import com.kg.museumly.presentation.testutil.MainDispatcherRule
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the string key DetailViewModel reads from SavedStateHandle. The
 * handle is built from a real DetailPage the same way Navigation builds it,
 * so renaming DetailPage.artworkId without updating the ViewModel fails here
 * instead of crashing at runtime when the detail screen opens.
 *
 * Robolectric-backed because the route is turned into an android.os.Bundle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsTheArtworkIdFromTheRoute() {
        val repository = FakeArtworkRepository()
        val handle = SavedStateHandle(route = DetailPage(artworkId = "met-123"))

        DetailViewModel(
            savedStateHandle = handle,
            repository = repository,
            positionStore = mockk<FeedPositionSourceInterface>(relaxed = true),
        )

        assertEquals(listOf("met-123"), repository.artworkWithDetailIds)
    }
}
