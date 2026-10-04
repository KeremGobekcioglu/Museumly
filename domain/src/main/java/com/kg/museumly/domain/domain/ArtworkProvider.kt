package com.kg.museumly.domain.domain

import com.kg.museumly.domain.model.Section

interface ArtworkProvider {
    // providers need to have an id. so we can differentiate them.
    val id: String

    /**
     * pages should be fetched by department. because users need
     * some sort of filtering. everyone has different taste.
     */
    suspend fun fetchPage(cursor: String?, size: Int, department: String) : PageResult

    /**
     * Different providers will have different departments for sections.
     * This functions purpose is unify,combine providers departments using our sections.
     * These will create one shared logic across providers.
     */
    fun departmentsFor(section: Section): List<String>

}