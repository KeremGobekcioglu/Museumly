package com.kg.museumly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "provider_cursors"
)
data class ProviderCursor(
    @PrimaryKey val providerId: String,
    val next: String?
)