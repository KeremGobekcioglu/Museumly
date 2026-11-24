package com.kg.museumly.data.remote.met

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The Museumly backend's Met endpoint. Null parameters are left out of the
 * URL: no cursor = from the start, no years = the feed. Years: both or neither.
 * The cursor is opaque: stored and sent back as-is, never parsed.
 */
interface MetBackendApi {

    @GET("met/artworks")
    suspend fun artworks(
        @Query("department") department: Int,
        @Query("fromYear") fromYear: Int?,
        @Query("toYear") toYear: Int?,
        @Query("cursor") cursor: String?,
        @Query("size") size: Int,
    ): MetArtworkPageDto
}