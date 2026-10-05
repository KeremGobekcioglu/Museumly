package com.kg.museumly.data.local.favorites

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kg.museumly.data.local.ArtworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao
{
    @Query("SELECT artworkId FROM favorites")
    fun observeFavoriteIds() : Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE artworkId = :artworkId")
    suspend fun remove(artworkId: String)

    /**
     * GET FAVORITE ARTWORKS. FILTERING.
     */
    @Query("""
        SELECT artworks.* FROM artworks
         INNER JOIN favorites ON favorites.artworkId = artworks.id
         WHERE (:sectionId IS NULL OR artworks.sectionId = :sectionId)
         ORDER BY favorites.favoritedAt DESC
    """)
    fun getFavorites(sectionId: String?) : Flow<List<ArtworkEntity>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count() : Int

    @Query("SELECT * FROM favorites WHERE artworkId = :id")
    suspend fun byId(id : String) : FavoriteEntity?
}