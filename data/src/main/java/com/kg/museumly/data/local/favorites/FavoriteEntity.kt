package com.kg.museumly.data.local.favorites

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites"
)
data class FavoriteEntity(
    @PrimaryKey val artworkId: String,
    val favoritedAt: Long
)