package com.kg.museumly.presentation.feature.share

import android.content.Context
import com.kg.museumly.domain.share.ShareConfig
import com.kg.museumly.presentation.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Feeds `:data`'s share handoff the two values that live in this module's string resources. */
@Module
@InstallIn(SingletonComponent::class)
object ShareConfigModule {
    @Provides
    fun provideShareConfig(@ApplicationContext context: Context): ShareConfig = ShareConfig(
        facebookAppId = context.getString(R.string.share_facebook_app_id),
        landingUrl = context.getString(R.string.share_landing_url),
    )
}
