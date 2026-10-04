package com.kg.museumly.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtworkDao
{
    @Query(" SELECT * FROM artworks WHERE sectionId = :sectionId ORDER BY position ASC")
    fun observeBySection(sectionId: String) : Flow<List<ArtworkEntity>>

    @Query("SELECT * FROM artworks WHERE id = :id")
    suspend fun byId(id : String) : ArtworkEntity?

    @Query("SELECT COUNT(*) FROM artworks WHERE sectionId = :sectionId")
    suspend fun count(sectionId: String) : Int

    /**
     * COALESCE(MAX(position), -1) returns -1 on an empty table,
     * so the first ++position lands on 0. Without it we would get a null and a crash on first run.
     */
    @Query("SELECT COALESCE(MAX(position) , -1) FROM artworks WHERE sectionId = :sectionId")
    suspend fun maxPosition(sectionId : String) : Int

    /**
     * replace : deletes than inserts. duplicate artworks
     * will go to end.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ArtworkEntity>)

    @Query("DELETE FROM artworks")
    suspend fun clear()
}