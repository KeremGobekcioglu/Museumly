package com.kg.museumly.presentation.feature.share.capture

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

/**
 * PNG-encodes this bitmap. CPU-heavy at 1080x1920, so call it off the main thread.
 *
 * A GraphicsLayer capture can come back as a HARDWARE bitmap, which can't be compressed
 * directly, so it is copied to ARGB_8888 first.
 */
fun ImageBitmap.toPngBytes(): ByteArray {
    var bitmap = asAndroidBitmap()
    if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}
