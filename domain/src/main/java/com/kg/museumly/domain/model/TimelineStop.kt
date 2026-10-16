package com.kg.museumly.domain.model

/** One stop on the timeline: a highlight work, and where "more like this" lives. */
data class TimelineStop(
    val artwork: Artwork,
    val section: Section,
    val era: Era
)