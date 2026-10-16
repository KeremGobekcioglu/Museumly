package com.kg.museumly.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per artwork, whatever lists it appears in. Which lists it is in,
 * and at what position, live in PlacementEntity.
 *
 * @PrimaryKey val id: String — a String because it's "met:436535".
 * Met object 436535 and Cleveland object 436535 are different artworks,
 * so an Int would collide once you add providers.
 *
 * sectionId — the section the work's department maps to. It is used only
 * for the favorites section filter, not for list order. A work's department
 * never changes, so it gets the same sectionId whichever list saved it first.
 */
@Entity(
    tableName = "artworks",
    indices = [Index("sectionId"), Index("providerId")]
)
data class ArtworkEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val sectionId: String,          // Section.id, e.g. "asia"
    val title: String,
    val artist: String?,
    val year: String?,
    val yearStart: Int?,
    val imageUrl: String,
    val aspectRatio: Float?,
    val classification: String?,
    val department: String?
)