package com.kg.museumly.data.di

import android.content.Context
import androidx.room.Room
import com.kg.museumly.data.local.ArtworkDao
import com.kg.museumly.data.local.MuseumDatabase
import com.kg.museumly.data.local.ProviderCursorDao
import com.kg.museumly.data.local.detail.ArtworkDetailDao
import com.kg.museumly.data.local.favorites.FavoritesDao
import com.kg.museumly.data.local.placement.PlacementDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * fallbackToDestructiveMigration(true): while the schema is churning,
     * a version bump drops and recreates every table instead of needing a
     * migration. Every table is wiped, favorites included. Replace this with
     * real migrations before release.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context) : MuseumDatabase
    {
        return Room.databaseBuilder(
                context,
                MuseumDatabase::class.java,
                "museum_db"
            ).fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideArtworkDao(db: MuseumDatabase) : ArtworkDao
    {
        return db.artworkDao()
    }

    @Provides
    fun provideCursorDao(db: MuseumDatabase) : ProviderCursorDao
    {
        return db.cursorDao()
    }

    @Provides
    fun provideArtworkDetailDao(db: MuseumDatabase) : ArtworkDetailDao
    {
        return db.artworkDetailDao()
    }

    @Provides
    fun provideFavoritesDao(db: MuseumDatabase) : FavoritesDao
    {
        return db.favoritesDao()
    }

    @Provides
    fun providePlacementDao(db: MuseumDatabase) : PlacementDao
    {
        return db.placementDao()
    }
}