package com.kg.museumly.presentation.feature.share.capture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * Composes [content] at its own intrinsic size, ignoring the parent's constraints, records its
 * draw output into [layer], and never draws it on screen. Once this has composed a frame,
 * `layer.toImageBitmap()` returns the captured pixels at the layer's recorded size.
 *
 * The caller pins density (see [ShareCardExportDensity]) before composing this — capture
 * happens at whatever density is in scope, like any other composable.
 */
@Composable
fun CaptureHost(
    layer: GraphicsLayer,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.drawWithContent {
            layer.record {
                this@drawWithContent.drawContent()
            }
            // Deliberately not calling drawContent() here: the content goes into the layer,
            // never onto the screen.
        },
        content = content,
    ) { measurables, _ ->
        val placeable = measurables.first().measure(Constraints())
        // Report the real size: a zero-sized node is skipped in the draw phase, so nothing
        // would ever be recorded into the layer.
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}
