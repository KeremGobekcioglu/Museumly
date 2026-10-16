package com.kg.museumly.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtworkDao
{
    /**
     * artworks.*, not *: a bare * also returns the placement columns, which
     * don't belong to ArtworkEntity. The query reads both tables, so the Flow
     * re-emits when either one changes.
     */
    @Query("""
        SELECT artworks.* FROM artworks
        INNER JOIN placements ON placements.artworkId = artworks.id
        WHERE placements.listKey = :listKey
        ORDER BY placements.position ASC
    """)
    fun observeByList(listKey: String) : Flow<List<ArtworkEntity>>

    @Query("SELECT * FROM artworks WHERE id = :id")
    suspend fun byId(id : String) : ArtworkEntity?

    /**
     * ignore, not replace. Replace deletes the old row first, and the cascade
     * on placements would then delete this work from every list it is in.
     * Ignore also keeps an aspectRatio measured earlier, which a fresh copy
     * from the API (still null) would otherwise overwrite.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ArtworkEntity>)

    @Query("UPDATE artworks SET aspectRatio = :ratio WHERE id = :id AND aspectRatio IS NULL")
    suspend fun setAspectRatio(id: String, ratio: Float)

    @Query("DELETE FROM artworks")
    suspend fun clear()
}