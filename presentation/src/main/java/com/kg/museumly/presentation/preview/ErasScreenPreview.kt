package com.kg.museumly.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.domain.model.TimelineStop
import com.kg.museumly.presentation.feature.eras.ErasScreen
import com.kg.museumly.presentation.feature.eras.ErasUiState

/**
 * Three fake works for every era, except two eras left empty and one with a
 * single work, so the ruler shows dimmed ticks and the counter can hide.
 */
private val emptyEras: Set<Era> = setOf(Era.BC_3000, Era.Y1500)
private val singleWorkEra: Era = Era.AD_1000

private val urls: List<String> = listOf(PORTRAIT_URL, WIDE_URL, TALL_URL)

private class FakeWork(val title: String, val artist: String?, val year: String)

private val fakeWorks: List<FakeWork> = listOf(
    FakeWork("Young Woman with a Water Pitcher", "Johannes Vermeer", "ca. 1662"),
    FakeWork("Portrait of Lisa Gherardini, wife of Francesco del Giocondo", "Leonardo da Vinci", "1503–1506"),
    FakeWork("Seated Scribe", null, "ca. 2600–2350 BC"),
    FakeWork("The Harvesters", "Pieter Bruegel the Elder", "1565"),
    FakeWork("Amphora with Herakles and the Nemean Lion", "Attributed to the Painter of London B 76", "ca. 560 BC"),
    FakeWork("Wheat Field with Cypresses", "Vincent van Gogh", "1889"),
)

private fun fakeStops(): List<TimelineStop> {
    val stops: MutableList<TimelineStop> = ArrayList()
    var counter: Int = 0
    for (era in Era.erasFor(null)) {
        if (era in emptyEras) {
            continue
        }
        var count: Int = 3
        if (era == singleWorkEra) {
            count = 1
        }
        for (index in 0 until count) {
            val work: FakeWork = fakeWorks[counter % fakeWorks.size]
            val artwork: Artwork = Artwork(
                id = "preview:" + era.id + ":" + index,
                title = work.title,
                artist = work.artist,
                year = work.year,
                imageUrl = urls[index % urls.size],
                aspectRatio = null,
            )
            stops.add(TimelineStop(artwork, Section.EUROPEAN, era))
            counter += 1
        }
    }
    return stops
}

@Preview(name = "Eras", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ErasScreenPreview() {
    WithPreviewImages {
        ErasScreen(
            state = ErasUiState(stops = fakeStops(), isLoading = false),
            onIntent = {},
        )
    }
}

// Cold states: nothing in Room, so the gallery screens take the whole page.

@Preview(name = "Eras · loading", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ErasLoadingPreview() {
    ErasScreen(
        state = ErasUiState(stopsReady = true, isLoading = true),
        onIntent = {},
    )
}

@Preview(name = "Eras · failed", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ErasFailedPreview() {
    ErasScreen(
        state = ErasUiState(stopsReady = true, isLoading = false, errorMessage = "HTTP 503"),
        onIntent = {},
    )
}

@Preview(name = "Eras · failed, retrying", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ErasRetryingPreview() {
    ErasScreen(
        state = ErasUiState(
            stopsReady = true,
            isLoading = true,
            isRetrying = true,
            errorMessage = "HTTP 503",
        ),
        onIntent = {},
    )
}

@Preview(name = "Eras · offline", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ErasOfflinePreview() {
    ErasScreen(
        state = ErasUiState(
            stopsReady = true,
            isLoading = false,
            errorMessage = "Unable to resolve host",
            isOnline = false,
        ),
        onIntent = {},
    )
}

@Preview(name = "Eras · empty", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ErasEmptyPreview() {
    ErasScreen(
        state = ErasUiState(stopsReady = true, isLoading = false),
        onIntent = {},
    )
}

// Partial states: some eras have works, so the timeline stays and the empty
// era the ruler opens on (3000 BC) shows EmptyEra in its slot.

@Preview(name = "Eras · partial, still loading", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ErasPartialLoadingPreview() {
    WithPreviewImages {
        ErasScreen(
            state = ErasUiState(stops = fakeStops(), stopsReady = true, isLoading = true),
            onIntent = {},
        )
    }
}

@Preview(name = "Eras · partial, failed", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun ErasPartialFailedPreview() {
    WithPreviewImages {
        ErasScreen(
            state = ErasUiState(
                stops = fakeStops(),
                stopsReady = true,
                isLoading = false,
                errorMessage = "HTTP 503",
            ),
            onIntent = {},
        )
    }
}
