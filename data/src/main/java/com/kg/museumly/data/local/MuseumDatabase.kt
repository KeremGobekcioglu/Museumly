package com.kg.museumly.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kg.museumly.data.local.detail.ArtworkDetailDao
import com.kg.museumly.data.local.detail.ArtworkDetailEntity
import com.kg.museumly.data.local.favorites.FavoriteEntity
import com.kg.museumly.data.local.favorites.FavoritesDao

@Database(
    entities = [ArtworkEntity::class , ProviderCursor::class, ArtworkDetailEntity::class, FavoriteEntity::class],
    version = 7,
    exportSchema = true
)
abstract class MuseumDatabase : RoomDatabase()
{
    abstract fun artworkDao() : ArtworkDao
    abstract fun cursorDao() : ProviderCursorDao
    abstract fun artworkDetailDao() : ArtworkDetailDao

    abstract fun favoritesDao() : FavoritesDao
}