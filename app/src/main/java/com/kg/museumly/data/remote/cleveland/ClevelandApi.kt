package com.kg.museumly.data.remote.cleveland

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ClevelandApi {

    /**
     * cc0 sits in the annotation string. it is just a plain string not a query.
     * i could use QueryName but i never see it is used.
     */
    @GET("artworks/?cc0&orderby=id")
    suspend fun searchArtworks(
        @Query("has_image") hasImage: Int,
        @Query("skip") skip: Int,
        @Query("limit") limit: Int,
        @Query("department") department: String?,
        @Query("fields") fields: String?
    ): ClevelandSearchResponse

    @GET("artworks/{id}")
    suspend fun getArtwork(
        @Path("id") id: Int
    ): ClevelandArtworkResponse
}
