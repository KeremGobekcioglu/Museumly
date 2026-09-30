package com.kg.museumly.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The (sectionId, position) index matters because every read filters by section and orders by position.
 * @PrimaryKey val id: String — a String because it's "met:436535".
 * Met object 436535 and Cleveland object 436535 are different artworks,
 * so an Int would collide once you add providers.
 *
 * val position: Int — SQLite has no inherent row order.
 * Without this, SELECT * returns rows in whatever order the engine feels like,
 * and that order can change after an insert. Your pager needs page 7 to be
 * the same artwork tomorrow. This is the only thing guaranteeing that.
 *
 * indices = [Index("sectionId", "position")] — every query is
 * WHERE sectionId = ? ORDER BY position. The composite index answers both at once:
 * SQLite jumps to that section's rows, already sorted by position. Column order
 * matters, since (position, sectionId) couldn't do the WHERE lookup.
 * Without it SQLite scans and sorts the whole table each time. Invisible at 20 rows, not at 5,000.
 */
@Entity(
    tableName = "artworks",
    indices = [Index("sectionId", "position"), Index("providerId")]
)
data class ArtworkEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val sectionId: String,          // ← new: Section.id, e.g. "asia"
    val title: String,
    val artist: String?,
    val year: String?,
    val yearStart: Int?,
    val imageUrl: String,
    val aspectRatio: Float?,
    val position: Int, // now counts within its section
    val classification: String?,
    val department: String?
)