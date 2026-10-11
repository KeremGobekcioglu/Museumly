package com.kg.museumly.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kg.museumly.data.local.detail.ArtworkDetailDao
import com.kg.museumly.data.local.detail.ArtworkDetailEntity
import com.kg.museumly.data.local.favorites.FavoriteEntity
import com.kg.museumly.data.local.favorites.FavoritesDao
import com.kg.museumly.data.local.placement.PlacementDao
import com.kg.museumly.data.local.placement.PlacementEntity

@Database(
    entities = [ArtworkEntity::class , ProviderCursor::class, ArtworkDetailEntity::class, FavoriteEntity::class, PlacementEntity::class],
    version = 8,
    exportSchema = true
)
abstract class MuseumDatabase : RoomDatabase()
{
    abstract fun artworkDao() : ArtworkDao
    abstract fun cursorDao() : ProviderCursorDao
    abstract fun artworkDetailDao() : ArtworkDetailDao

    abstract fun favoritesDao() : FavoritesDao

    abstract fun placementDao() : PlacementDao
}