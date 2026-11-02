package com.kg.museumly.data.di

import com.kg.museumly.data.share.AndroidShareManager
import com.kg.museumly.domain.share.ShareManager
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
