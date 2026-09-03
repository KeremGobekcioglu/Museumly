package com.kg.museumly.data.local.detail

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artwork_details")
data class ArtworkDetailEntity(
    @PrimaryKey val id: String,
    val medium: String?,
    val dimensions: String?,
    val creditLine: String?,
    val culture: String?,
    val period: String?,
)