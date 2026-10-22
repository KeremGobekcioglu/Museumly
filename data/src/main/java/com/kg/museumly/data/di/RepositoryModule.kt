package com.kg.museumly.data.di

import com.kg.museumly.data.ArtworkRepositoryImpl
import com.kg.museumly.data.AuthRepositoryImpl
import com.kg.museumly.domain.ArtworkRepository
import com.kg.museumly.domain.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule
{
    @Binds
    @Singleton
    abstract fun bindArtworkRepository(
        impl: ArtworkRepositoryImpl
    ) : ArtworkRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ) : AuthRepository
}