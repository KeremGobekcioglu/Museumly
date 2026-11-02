package com.kg.museumly.presentation.feature.share.platform

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ShareModule {
    @Binds
    abstract fun bindShareManager(impl: AndroidShareManager): ShareManager
}
