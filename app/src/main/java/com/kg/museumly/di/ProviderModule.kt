package com.kg.museumly.di

import com.kg.museumly.data.remote.met.MetProvider
import com.kg.museumly.domain.ArtworkProvider
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    @Binds
    @IntoSet
    abstract fun bindMetProvider(provider: MetProvider): ArtworkProvider
}