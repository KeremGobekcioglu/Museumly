package com.kg.museumly.domain

import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section

interface ArtworkProvider {
    // providers need to have an id. so we can differentiate them.
    val id: String

    /**
     * pages should be fetched by department. because users need
     * some sort of filtering. everyone has different taste.
     *
     * era == null is the feed: no date filter. With an era, only works from
     * that era's years. The cursor belongs to that one query, so a feed
     * cursor must never be used for an era page or the other way round.
     */
    suspend fun fetchPage(cursor: String?, size: Int, department: String, era: Era?) : PageResult

    /**
     * Different providers will have different departments for sections.
     * This functions purpose is unify,combine providers departments using our sections.
     * These will create one shared logic across providers.
     */
    fun departmentsFor(section: Section): List<String>

}