package com.kg.museumly.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kg.museumly.data.local.detail.ArtworkDetailDao
import com.kg.museumly.data.local.detail.ArtworkDetailEntity

@Database(
    entities = [ArtworkEntity::class , ProviderCursor::class, ArtworkDetailEntity::class],
    version = 3,
    exportSchema = true
)
abstract class MuseumDatabase : RoomDatabase()
{
    abstract fun artworkDao() : ArtworkDao
    abstract fun cursorDao() : ProviderCursorDao
    abstract fun artworkDetailDao() : ArtworkDetailDao
}