package com.kg.museumly

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class Museumly : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var imageLoader: ImageLoader
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return imageLoader
    }
}