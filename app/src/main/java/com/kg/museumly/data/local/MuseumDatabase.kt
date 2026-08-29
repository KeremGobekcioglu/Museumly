package com.kg.museumly.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ArtworkEntity::class , ProviderCursor::class],
    version = 2,
    exportSchema = true
)
abstract class MuseumDatabase : RoomDatabase()
{
    abstract fun artworkDao() : ArtworkDao
    abstract fun cursorDao() : ProviderCursorDao
}