package com.kg.museumly.data.remote.museumly

import retrofit2.http.Body
import retrofit2.http.POST

interface MuseumlyApi {
    @POST("/auth/google")
    suspend fun loginWithGoogle(@Body loginRequest: GoogleLoginRequest): LoginResponse
}