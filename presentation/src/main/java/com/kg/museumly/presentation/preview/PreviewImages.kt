package com.kg.museumly.presentation.preview

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import coil3.asImage
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.SuccessResult
import com.kg.museumly.presentation.R
import kotlin.collections.get

internal const val WIDE_URL = "preview://wide"
internal const val TALL_URL = "preview://tall"
internal const val PORTRAIT_URL = "preview://portrait"

/**
 * A static Compose Preview snapshot is one synchronous frame — Coil's real
 * AsyncImage decode is asynchronous even for a local drawable, so without
 * this the frame/lamp/placard render but the image slot stays empty.
 * LocalAsyncImagePreviewHandler intercepts the request while
 * LocalInspectionMode is true and resolves it synchronously instead.
 */
@Composable
internal fun WithPreviewImages(content: @Composable () -> Unit) {
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
