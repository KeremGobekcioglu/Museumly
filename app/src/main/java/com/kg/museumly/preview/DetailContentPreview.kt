package com.kg.museumly.preview

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import coil3.asImage
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.SuccessResult
import com.kg.museumly.R
import com.kg.museumly.feature.detail.DetailScreen
import com.kg.museumly.feature.detail.DetailUiState
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail
import com.kg.museumly.model.ArtworkWithDetail

/**
 * The three orientations the fixed frame has to survive: wide, tall (a
 * hanging-scroll extreme), and roughly portrait — the case the frame's
 * fixed shape was actually designed around. Local drawables, not network,
 * so these render without a device.
 */
private fun previewArtwork(
    id: String,
    title: String,
    imageUrl: String,
    aspectRatio: Float,
): ArtworkWithDetail {
    val artwork = Artwork(
        id = id,
        title = title,
        artist = "Preview Artist",
        year = "1900",
        imageUrl = imageUrl,
        aspectRatio = aspectRatio,
    )
    val detail = ArtworkDetail(
        medium = null,
        dimensions = null,
        creditLine = null,
        culture = null,
        period = null,
        highResImageUrl = null,
        artistBio = null,
        description = null,
        didYouKnow = null,
    )
    return ArtworkWithDetail(artwork = artwork, detail = detail)
}

private const val WIDE_URL = "preview://wide"
private const val TALL_URL = "preview://tall"
private const val PORTRAIT_URL = "preview://portrait"

/**
 * A static Compose Preview snapshot is one synchronous frame — Coil's real
 * AsyncImage decode is asynchronous even for a local drawable, so without
 * this the frame/lamp/placard render but the image slot stays empty.
 * LocalAsyncImagePreviewHandler intercepts the request while
 * LocalInspectionMode is true and resolves it synchronously instead.
 */
@Composable
private fun WithPreviewImages(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val drawableForUrl = mapOf(
        WIDE_URL to R.drawable.preview_wide,
        TALL_URL to R.drawable.preview_tall,
        PORTRAIT_URL to R.drawable.preview_portrait,
    )
    val handler = remember {
        AsyncImagePreviewHandler { _, request ->
            val drawableRes = drawableForUrl[request.data as? String]
                ?: return@AsyncImagePreviewHandler AsyncImagePainter.State.Empty
            val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)
            AsyncImagePainter.State.Success(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                result = SuccessResult(
                    image = bitmap.asImage(),
                    request = request,
                ),
            )
        }
    }
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides handler) {
        content()
    }
}

@Preview(name = "Wide", showBackground = true)
@Composable
private fun DetailScreenWidePreview() = WithPreviewImages {
    DetailScreen(
        state = DetailUiState(
            data = previewArtwork("preview:wide", "The Harvesters", WIDE_URL, aspectRatio = 1.36f),
            isLoading = false,
        ),
        onBack = {},
        onInspected = {},
    )
}

@Preview(name = "Tall", showBackground = true)
@Composable
private fun DetailScreenTallPreview() = WithPreviewImages {
    DetailScreen(
        state = DetailUiState(
            data = previewArtwork("preview:tall", "Quail and Millet", TALL_URL, aspectRatio = 0.48f),
            isLoading = false,
        ),
        onBack = {},
        onInspected = {},
    )
}

@Preview(name = "Portrait", showBackground = true)
@Composable
private fun DetailScreenPortraitPreview() = WithPreviewImages {
    DetailScreen(
        state = DetailUiState(
            data = previewArtwork("preview:portrait", "Girl with a Pearl Earring", PORTRAIT_URL, aspectRatio = 0.88f),
            isLoading = false,
        ),
        onBack = {},
        onInspected = {},
    )
}
