package com.kg.museumly.data.local.placement

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.kg.museumly.data.local.ArtworkEntity

/**
 * One artwork's spot in one list. The same artwork can sit in "asia" and
 * "asia@1600s" at once, each with its own position.
 *
 * listKey — section.id for a feed ("asia"), "<section or all>@<era id>"
 * for an era list ("asia@1600s", "all@1600s").
 *
 * position — SQLite has no inherent row order. Without this, SELECT returns
 * rows in whatever order the engine feels like, and that order can change
 * after an insert. The pager needs page 7 to be the same artwork tomorrow.
 *
 * primaryKeys = [listKey, artworkId] — an artwork sits in a list only once.
 *
 * Index("listKey", "position") — every read is WHERE listKey = ? ORDER BY
 * position. The composite index answers both at once: SQLite jumps to that
 * list's rows, already sorted. Column order matters, since (position, listKey)
 * couldn't do the WHERE lookup.
 *
 * Index("artworkId") — without it Room warns that the foreign key column is
 * unindexed, and every cascade delete would scan the whole table.
 *
 * onDelete = CASCADE — deleting an artwork removes its placements, so
 * ArtworkDao.clear() can't leave orphans. This is also why ArtworkDao
 * inserts with IGNORE rather than REPLACE.
 *
 * Foreign keys are enforced, so an artwork row must exist before its
 * placement is inserted.
 */
@Entity(
    tableName = "placements",
    primaryKeys = ["listKey", "artworkId"],
    indices = [Index("listKey", "position"), Index("artworkId")],
    foreignKeys = [
        ForeignKey(
            entity = ArtworkEntity::class,
            parentColumns = ["id"],
            childColumns = ["artworkId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlacementEntity(
    val artworkId: String,
    val position: Int,
    val listKey: String
)