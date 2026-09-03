package com.kg.museumly.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kg.museumly.data.local.ArtworkDao
import com.kg.museumly.data.local.MuseumDatabase
import com.kg.museumly.data.local.ProviderCursor
import com.kg.museumly.data.local.ProviderCursorDao
import com.kg.museumly.data.local.detail.ArtworkDetailDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /***
     * No fallbackToDestructiveMigration(). While the schema is churning,
     * uninstall the app between changes
     * — that way you notice when you've made a breaking change instead of
     * silently wiping data later in production.
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
}