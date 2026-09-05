package com.kg.museumly.domain

import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail

enum class PageStatus {
    /** Records returned, more may exist. */
    OK,
    /** Provider genuinely reached the end of its corpus. */
    EXHAUSTED,
    /** The call failed. Says nothing about whether more exists. */
    FAILED
}


/**
 * caller needs to know when a provider is exhausted. next == null.
 */
data class PageResult(
    val items: List<Artwork>,
    val details: List<ArtworkDetail>,
    val next: String?,
    val status: PageStatus
)