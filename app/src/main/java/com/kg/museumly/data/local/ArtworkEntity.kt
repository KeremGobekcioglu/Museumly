package com.kg.museumly.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The index on position matters because every read is ORDER BY position.
 */
@Entity(
    tableName = "artworks",
    indices = [Index("position"), Index("providerId")]
)
data class ArtworkEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val title: String,
    val artist: String?,
    val year: String?,
    val yearStart: Int?,
    val imageUrl: String,
    val aspectRatio: Float,
    val position: Int
)