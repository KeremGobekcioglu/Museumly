package com.kg.museumly.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The index on position matters because every read is ORDER BY position.
 * @PrimaryKey val id: String — a String because it's "met:436535".
 * Met object 436535 and Cleveland object 436535 are different artworks,
 * so an Int would collide once you add providers.
 *
 * val position: Int — SQLite has no inherent row order.
 * Without this, SELECT * returns rows in whatever order the engine feels like,
 * and that order can change after an insert. Your pager needs page 7 to be
 * the same artwork tomorrow. This is the only thing guaranteeing that.
 * 
 * indices = [Index("position")] — because every query is ORDER BY position.
 * Without an index SQLite sorts the whole table each time. Invisible at 20 rows, not at 5,000.
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