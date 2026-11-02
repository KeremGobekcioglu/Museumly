package com.kg.museumly.data.share

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.kg.museumly.domain.share.ShareConfig
import com.kg.museumly.domain.share.ShareManager
import com.kg.museumly.domain.share.ShareResult
import com.kg.museumly.domain.share.ShareTarget
import com.kg.museumly.domain.share.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import android.hardware.display.DisplayManager
import android.util.Size
import android.view.Display
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.IOException
private const val SHARE_FILE_PREFIX = "museumly"
private const val INSTAGRAM_PACKAGE = "com.instagram.android"
private const val WHATSAPP_PACKAGE = "com.whatsapp"
private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
private const val FACEBOOK_PACKAGE = "com.facebook.katana"

/** Exported PNGs older than this are pruned on the next share. */
private const val SHARE_CACHE_TTL_MS = 10 * 60 * 1000L
private const val APP_FOLDER_NAME = "Museumly"

/**
 * Singleton because [lastGrantedUri] must outlive a single share: the next share revokes the
 * previous package-level grant.
 */
@Singleton
class AndroidShareManager @Inject constructor(
    @ApplicationContext private val context: Context,
    config: ShareConfig,
) : ShareManager {

    @Volatile
    private var lastGrantedUri: Uri? = null

    // Blank means "not configured": the feature that needs it is hidden instead of
    // launching an intent that would open an empty composer.
    private val facebookAppId: String = config.facebookAppId
    private val landingUrl: String = config.landingUrl

    override fun isAvailable(target: ShareTarget): Boolean = when (target) {
        // Instagram rejects Story intents that don't name a registered Meta app.
        ShareTarget.InstagramStories -> facebookAppId.isNotBlank() && isInstalled(INSTAGRAM_PACKAGE)
        ShareTarget.WhatsApp -> isInstalled(WHATSAPP_PACKAGE) || isInstalled(WHATSAPP_BUSINESS_PACKAGE)
        // Plain ACTION_SEND: unlike Stories, this needs no Meta app id.
        ShareTarget.Facebook -> isInstalled(FACEBOOK_PACKAGE)
        ShareTarget.SystemSheet -> true
    }

    override suspend fun share(png: ByteArray, target: ShareTarget): ShareResult =
        withContext(Dispatchers.IO) {
            try {
                if (!isAvailable(target)) return@withContext ShareResult.TargetUnavailable

                val uri = writeToShareCache(png)
                val pkg = resolvePackage(target)

                val intent = when (target) {
                    ShareTarget.InstagramStories -> storiesIntent(uri)
                    ShareTarget.SystemSheet ->
                        Intent.createChooser(sendIntent(uri, null), null)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ShareTarget.WhatsApp, ShareTarget.Facebook -> sendIntent(uri, pkg)
                }

                if (intent.resolveActivity(context.packageManager) == null) {
                    ShareResult.TargetUnavailable
                } else {
                    grantTo(pkg, uri)
                    context.startActivity(intent)
                    ShareResult.Success
                }
            } catch (e: Exception) {
                ShareResult.Failed(e)
            }
        }

    // ACTION_SEND puts the payload in the extras bundle under EXTRA_STREAM. Every generic
    // "share to me" receiver (WhatsApp, the system chooser) reads from this extra by convention.
    // Compare storiesIntent below, which reads a different slot.
    private fun sendIntent(uri: Uri, pkg: String?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            pkg?.let { setPackage(it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // We hold an application Context, not an Activity. An Activity already belongs to a
            // task, so startActivity() can push onto it implicitly. A bare Context belongs to no
            // task, and without this flag startActivity() throws at the moment it runs.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    // ADD_TO_STORY is a bespoke action Meta's app declares. Its convention reads the payload from
    // the intent's primary *data* field (setDataAndType), NOT from EXTRA_STREAM. Put the image in
    // the wrong slot and Instagram still launches (resolveActivity passes, no exception) but the
    // story composer finds nothing and opens a blank canvas. See grantTo() for the second way to
    // get a silently-empty Story.
    private fun storiesIntent(uri: Uri): Intent =
        Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/png")
            putExtra("source_application", facebookAppId)
            // A tappable link sticker on the story. Left off when no landing page is configured.
            landingUrl.takeIf { it.isNotBlank() }?.let { putExtra("content_url", it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * The intent flag alone grants read access only to the activity the system launches, for
     * that activity's lifetime. Instagram's story composer often reads the URI from another
     * process or after the receiving activity finished, by which point the flag-based grant is
     * gone and the canvas comes up empty. A package-level grant persists until revoked or reboot.
     *
     * Package grants don't expire on their own, so [lastGrantedUri] tracks the previous one and
     * revokes it before issuing a new one.
     */
    private fun grantTo(pkg: String?, uri: Uri) {
        lastGrantedUri?.let {
            context.revokeUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        lastGrantedUri = null

        // SystemSheet has no fixed package: the chooser grants the picked app access itself,
        // via the intent flag already set in sendIntent.
        if (pkg == null) return

        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        lastGrantedUri = uri
    }

    private fun writeToShareCache(png: ByteArray): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach {
            if (now - it.lastModified() > SHARE_CACHE_TTL_MS) it.delete()
        }
        // Unique name per share: reusing one could replace a file another app is mid-read on.
        val file = File(dir, "${SHARE_FILE_PREFIX}_$now.png")
        file.writeBytes(png)
        // Must match the <provider android:authorities> in the manifest.
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun resolvePackage(target: ShareTarget): String? = when (target) {
        ShareTarget.InstagramStories -> INSTAGRAM_PACKAGE
        ShareTarget.WhatsApp ->
            if (isInstalled(WHATSAPP_PACKAGE)) WHATSAPP_PACKAGE else WHATSAPP_BUSINESS_PACKAGE
        ShareTarget.Facebook -> FACEBOOK_PACKAGE
        ShareTarget.SystemSheet -> null
    }

    // On API 30+, getPackageInfo() throws NameNotFoundException for any package outside the
    // app's <queries>, even if it is installed. The manifest declares every package checked
    // here; without that entry this would report "not installed" for an app that is.
    private fun isInstalled(pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    override suspend fun saveToGallery(png: ByteArray): ShareResult =
        withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${APP_FOLDER_NAME}_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/$APP_FOLDER_NAME")
                    // Pending hides the row from other apps until the bytes are fully written.
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val itemUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext ShareResult.Failed(IllegalStateException("MediaStore insert returned null"))
                try {
                    context.contentResolver.openOutputStream(itemUri)?.use { it.write(png) }
                        ?: throw IllegalStateException("Could not open output stream")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, values, null, null)
                    ShareResult.Success
                } catch (e: Exception) {
                    context.contentResolver.delete(itemUri, null, null) // don't leave a half-written row
                    ShareResult.Failed(e)
                }
            } catch (e: Exception) {
                ShareResult.Failed(e)
            }
        }

    override suspend fun shareText(text: String, target: ShareTarget): ShareResult =
        withContext(Dispatchers.IO) {
            try {
                val base = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    resolvePackage(target)?.let { setPackage(it) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val intent =
                    if (target == ShareTarget.SystemSheet) {
                        Intent.createChooser(base, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    } else {
                        base
                    }
                if (intent.resolveActivity(context.packageManager) == null) {
                    ShareResult.TargetUnavailable
                } else {
                    context.startActivity(intent)
                    ShareResult.Success
                }
            } catch (e: Exception) {
                ShareResult.Failed(e)
            }
        }

    override fun canSetWallpaper(): Boolean {
        val wallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)
        return wallpaperManager.isWallpaperSupported && wallpaperManager.isSetWallpaperAllowed
    }

    override suspend fun setWallpaper(imageUrl: String, target: WallpaperTarget): ShareResult {
        if(!canSetWallpaper())
        {
            return ShareResult.TargetUnavailable
        }
        val size: Size = screenSize()
        val source: Bitmap? = loadBitmap(imageUrl,size)
        if (source == null) {
            return ShareResult.Failed(IllegalStateException("Could not load image for wallpaper"))
        }
        return withContext(Dispatchers.IO)
        {
            try {
                val wallpaper: Bitmap = fitOnBlack(source,size)
                val flags: Int = when (target) {
                    WallpaperTarget.Home -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.Lock -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.Both -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                val id: Int = WallpaperManager.getInstance(context).setBitmap(wallpaper, null, true, flags)
                if (id == 0) {
                    return@withContext ShareResult.Failed(IllegalStateException("setBitmap returned 0"))
                }
                return@withContext ShareResult.Success
            }
            catch (e: IOException)
            {
                return@withContext ShareResult.Failed(e)
            }
            catch (e: SecurityException)
            {
                return@withContext ShareResult.Failed(e)
            }
        }
    }

    private fun screenSize() : Size
    {
        val displayManager: DisplayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display: Display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val mode: Display.Mode = display.mode
        val width: Int = mode.physicalWidth
        val height: Int = mode.physicalHeight
        // Always portrait, whichever way the display reports itself.
        return Size(minOf(width, height), maxOf(width, height))
    }
    private suspend fun loadBitmap(imageUrl: String, size: Size) : Bitmap?
    {
        val request: ImageRequest = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(size.width, size.height)
            .allowHardware(false)
            .build()
        val result: ImageResult = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            return result.image.toBitmap()
        }
        return null
    }
    private fun fitOnBlack(source: Bitmap, size: Size): Bitmap {
        val canvasWidth: Int = size.width
        val canvasHeight: Int = size.height
        val output: Bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val scale: Float = minOf(
            canvasWidth.toFloat() / source.width,
            canvasHeight.toFloat() / source.height,
        )
        val drawnWidth: Float = source.width * scale
        val drawnHeight: Float = source.height * scale
        val left: Float = (canvasWidth - drawnWidth) / 2f
        val top: Float = (canvasHeight - drawnHeight) / 2f
        val destination: RectF = RectF(left, top, left + drawnWidth, top + drawnHeight)

        val paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, destination, paint)
        return output
    }
}
