package com.kg.museumly.di

import com.kg.museumly.data.remote.CoilArtworkPrefetcher
import com.kg.museumly.domain.ArtworkPrefetcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PrefetchModule {
    @Binds
    @Singleton
    abstract fun bindArtworkPrefetcher(impl: CoilArtworkPrefetcher): ArtworkPrefetcher
}