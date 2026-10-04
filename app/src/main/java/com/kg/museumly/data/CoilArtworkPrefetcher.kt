package com.kg.museumly.data

import android.content.Context
import android.net.ConnectivityManager
import coil3.ImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import com.kg.museumly.domain.ArtworkPrefetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts downloading the next pages' images before the user swipes to them.
 */
@Singleton
class CoilArtworkPrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader
) : ArtworkPrefetcher {

    private companion object {
        // How many pages ahead to prefetch when saving data.
        const val LIMITED_PREFETCH_DEPTH = 1
    }

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    // Images are never shown bigger than the screen,
    // so we don't decode them bigger than that either.
    private val screenWidthPx: Int = context.resources.displayMetrics.widthPixels
    private val screenHeightPx: Int = context.resources.displayMetrics.heightPixels

    // Downloads started by the previous call, by URL.
    // A Disposable lets us check or cancel a download.
    // Only touched from the main thread, so no lock.
    private var pending: Map<String, Disposable> = emptyMap()

    override fun prefetch(urls: List<String>) {
        // Step 1: decide which URLs to download.
        // Data Saver only matters on mobile data, so check both.
        val dataSaverOn: Boolean = connectivityManager.restrictBackgroundStatus ==
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        val limited: Boolean = dataSaverOn && connectivityManager.isActiveNetworkMetered
        // urls starts with the nearest page, so take(1) keeps the next one.
        val targets: List<String> = if (limited) urls.take(LIMITED_PREFETCH_DEPTH) else urls

        // Step 2: keep downloads that are still running, start the missing ones.
        val next: MutableMap<String, Disposable> = HashMap()
        for (url in targets) {
            val existing: Disposable? = pending[url]
            if (existing != null && !existing.isDisposed) {
                // Already downloading. Don't restart it.
                next[url] = existing
            } else {
                // Not started, or already finished. A finished one is
                // served from cache, so starting it again costs nothing.
                next[url] = imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(screenWidthPx, screenHeightPx)
                        .build()
                )
            }
        }

        // Step 3: cancel downloads for pages the user already scrolled past.
        for ((url, disposable) in pending) {
            if (url !in next) {
                disposable.dispose()
            }
        }

        // Step 4: remember these downloads for the next call.
        pending = next
    }
}