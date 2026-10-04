package com.kg.museumly.data.di

import com.kg.museumly.data.local.FeedPositionSource
import com.kg.museumly.domain.FeedPositionSourceInterface
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FeedPositionSourceModule {

    @Binds
    @Singleton
    abstract fun bindFeedPositionSource(
        impl: FeedPositionSource
    ) : FeedPositionSourceInterface
}