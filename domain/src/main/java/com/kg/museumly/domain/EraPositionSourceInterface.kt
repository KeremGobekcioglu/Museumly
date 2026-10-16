package com.kg.museumly.domain

import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section

/**
 * Scroll position of the era page, one per list: a section in an era, or
 * "All" in an era (section == null). Separate from
 * FeedPositionSourceInterface on purpose, so the era page has no way to
 * move the feed's position.
 */
interface EraPositionSourceInterface {

    /** Furthest first-visible grid index reached in this list; 0 if never visited. */
    suspend fun getFrontier(era: Era, section: Section?): Int

    /** Forward only, like the feed's: an index below the stored one is ignored. */
    suspend fun setFrontier(era: Era, section: Section?, index: Int)
}