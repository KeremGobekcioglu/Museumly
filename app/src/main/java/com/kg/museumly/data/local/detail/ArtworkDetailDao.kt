package com.kg.museumly.data.local.detail

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArtworkDetailDao {
    @Query("SELECT * FROM artwork_details WHERE id = :id")
    suspend fun getById(id: String): ArtworkDetailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(detail: ArtworkDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(details: List<ArtworkDetailEntity>)
}