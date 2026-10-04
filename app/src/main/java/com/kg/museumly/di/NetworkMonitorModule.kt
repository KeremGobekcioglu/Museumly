package com.kg.museumly.di

import com.kg.museumly.data.NetworkMonitorImpl
import com.kg.museumly.domain.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkMonitorModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor( impl: NetworkMonitorImpl ) : NetworkMonitor
}