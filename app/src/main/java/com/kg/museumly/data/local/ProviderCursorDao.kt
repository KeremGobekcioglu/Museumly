package com.kg.museumly.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProviderCursorDao {
    @Query("SELECT * FROM provider_cursors WHERE providerId = :id")
    suspend fun get(id : String) : ProviderCursor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(cursor: ProviderCursor)
}