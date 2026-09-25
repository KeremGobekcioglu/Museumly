package com.kg.museumly.data.remote.met

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MetApi {

    /**
     * later isOnView can be added to see which artworks are
     * actually showed in the museum now.
     */
    @GET("v1.1/search")
    suspend fun search(
        @Query("departmentId") departmentId: Int,
        //@Query("isHighlight") isHighlight: Boolean = true,
        @Query("hasImages") hasImages: Boolean = true,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
        //@Query("isOnView") isOnView: Boolean = true
    ) : MetSearchDto

    @GET("v1/objects/{objectId}")
    suspend fun getObject(
        @Path("objectId") objectId: Int
    ) : MetObjectDto

    @GET("v1/departments")
    suspend fun getDepartments() : MetDepartmentsDto
}