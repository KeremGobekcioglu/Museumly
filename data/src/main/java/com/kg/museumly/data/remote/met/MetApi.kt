package com.kg.museumly.data.remote.met

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MetApi {

    /**
     * later isOnView can be added to see which artworks are
     * actually showed in the museum now.
     *
     * dateBegin and dateEnd: send both or neither. The Met ignores a single
     * one without an error and returns the unfiltered result (checked
     * 2026-10-10). With both, a work matches only if its whole date span
     * fits inside the range. Null leaves the parameter out of the URL.
     */
    @GET("v1.1/search")
    suspend fun search(
        @Query("departmentId") departmentId: Int,
        //@Query("isHighlight") isHighlight: Boolean = true,
        @Query("hasImages") hasImages: Boolean = true,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("dateBegin") dateBegin: Int?,
        @Query("dateEnd") dateEnd: Int?,
        //@Query("isOnView") isOnView: Boolean = true
    ) : MetSearchDto

    @GET("v1/objects/{objectId}")
    suspend fun getObject(
        @Path("objectId") objectId: Int
    ) : MetObjectDto

    @GET("v1/departments")
    suspend fun getDepartments() : MetDepartmentsDto
}