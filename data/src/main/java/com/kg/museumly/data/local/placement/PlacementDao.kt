package com.kg.museumly.data.local.placement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlacementDao
{
    @Query("SELECT COUNT(*) FROM placements WHERE listKey = :listKey")
    suspend fun count(listKey: String) : Int

    /**
     * COALESCE(MAX(position), -1) returns -1 for an empty list,
     * so the first ++position lands on 0.
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM placements WHERE listKey = :listKey")
    suspend fun maxPosition(listKey: String) : Int

    /**
     * replace: the primary key is (listKey, artworkId), so a work already in
     * this list is deleted and inserted again with its new position.
     * Duplicates go to the end, as before.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlacementEntity>)
}