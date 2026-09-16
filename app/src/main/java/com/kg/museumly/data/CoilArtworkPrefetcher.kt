package com.kg.museumly.data

import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.kg.museumly.domain.ArtworkPrefetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoilArtworkPrefetcher  @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader
) : ArtworkPrefetcher {
    override fun prefetch(urls: List<String>) {
        urls.forEach {
            url ->
                imageLoader.enqueue(
                    ImageRequest.Builder(context).data(url).build()
                )
        }
    }

}