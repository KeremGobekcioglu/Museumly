package com.kg.museumly.data

import android.util.Log
import com.kg.museumly.data.remote.museumly.GoogleLoginRequest
import com.kg.museumly.data.remote.museumly.LoginResponse
import com.kg.museumly.data.remote.museumly.MuseumlyApi
import com.kg.museumly.domain.AuthRepository
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject


private const val TAG: String = "AuthRepository"


class AuthRepositoryImpl @Inject constructor(
    private val museumlyApi: MuseumlyApi
) : AuthRepository
{
    override suspend fun loginWithGoogle(idToken: String): Boolean {
        val response : LoginResponse = try {
            museumlyApi.loginWithGoogle(GoogleLoginRequest(idToken))
        } catch (e: HttpException) {
            Log.e(TAG, "Backend rejected login: ${e.code()}")
            return false
        } catch (e: IOException) {
            Log.e(TAG, "Network error during login", e)
            return false
        }
        Log.d(TAG, "Got access token: ${response.accessToken}")
        return true
    }

}