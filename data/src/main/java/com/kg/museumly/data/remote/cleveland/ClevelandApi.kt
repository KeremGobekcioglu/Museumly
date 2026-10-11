package com.kg.museumly.data.remote.cleveland

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ClevelandApi {

    /**
     * cc0 sits in the annotation string. it is just a plain string not a query.
     * i could use QueryName but i never see it is used.
     *
     * created_after and created_before: both ends are inclusive, and only the
     * work's earliest date (creation_date_earliest) is compared. Null leaves
     * the parameter out of the URL.
     */
    @GET("artworks/?cc0")
    suspend fun searchArtworks(
        @Query("has_image") hasImage: Int,
        @Query("skip") skip: Int,
        @Query("limit") limit: Int,
        @Query("department") department: String?,
        @Query("fields") fields: String?,
        @Query("created_after") createdAfter: Int?,
        @Query("created_before") createdBefore: Int?,
    ): ClevelandSearchResponse

    @GET("artworks/{id}")
    suspend fun getArtwork(
        @Path("id") id: Int
    ): ClevelandArtworkResponse
}
