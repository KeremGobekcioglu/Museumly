package com.kg.museumly.data.remote.met

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MetApi {

    /**
     * later isOnView can be added to see which artworks are
     * actually showed in the museum now.
     */
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("isHighlight") isHighlight : Boolean = true,
        @Query("hasImages") hasImages: Boolean = true
    ) : MetSearchDto

    @GET("objects/{objectId}")
    suspend fun getObject(
        @Path("objectId") objectId: Int
    ) : MetObjectDto
}