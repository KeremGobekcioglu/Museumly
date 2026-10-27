package com.kg.museumly.data.di

import com.kg.museumly.data.local.SignInPromptSource
import com.kg.museumly.domain.SignInPromptSourceInterface
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SignInPromptSourceModule {
    @Binds
    @Singleton
    abstract fun bindSignInPromptSource(
        impl: SignInPromptSource
    ) : SignInPromptSourceInterface
}