package com.kg.museumly.presentation.feature.share.capture

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Card size in dp. 9:16, which is what Instagram Stories expects. */
val ShareCardWidth = 675.dp
val ShareCardHeight = 1200.dp

private const val ShareCardExportWidthPx = 1080

/**
 * Density used for the offscreen capture: 675dp * 1.6 = 1080px, 1200dp * 1.6 = 1920px.
 *
 * Pinned so the exported PNG is the same size on every device — otherwise a 3x phone exports
 * 2025x3600 and a 1.5x tablet 1013x1800. fontScale is pinned to 1f so a user's accessibility
 * text size cannot reflow the export.
 */
val ShareCardExportDensity = Density(
    density = ShareCardExportWidthPx / ShareCardWidth.value,
    fontScale = 1f,
)
