package com.kg.museumly.domain

import com.kg.museumly.model.Section

interface FeedPositionSourceInterface {

    /** Always returns a valid section; EUROPEAN on first launch. */
    suspend fun getLastSection(): Section
    suspend fun setLastSection(section: Section)

    /** Highest page index reached in [section]; 0 if never visited. */
    suspend fun getFrontier(section: Section) : Int

    /**
     * Highest page index the user has ever reached. Not "where they left
     * off" — if they swiped to 30 then browsed back to 5 and closed, we
     * want 30. Going backwards is free; going forwards is the thing worth
     * remembering. A lower [position] than the stored one is ignored.
     */
    suspend fun setFrontier(section: Section, position: Int)

    /**
     * Whether the user has completed a tap/pinch into inspect mode this
     * session. One-way within the session: once true, InspectHint stops
     * showing until the app is relaunched.
     */
    fun hasInspected(): Boolean
    fun setInspected()
}
