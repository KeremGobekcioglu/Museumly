package com.kg.museumly.data

import android.util.Log
import com.kg.museumly.data.local.AuthTokenStore
import com.kg.museumly.data.remote.museumly.GoogleLoginRequest
import com.kg.museumly.data.remote.museumly.LoginResponse
import com.kg.museumly.data.remote.museumly.MuseumlyApi
import com.kg.museumly.domain.AuthRepository
import com.kg.museumly.domain.AuthTokenStoreInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject


private const val TAG: String = "AuthRepository"


class AuthRepositoryImpl @Inject constructor(
    private val museumlyApi: MuseumlyApi,
    private val authTokenStore: AuthTokenStoreInterface
) : AuthRepository
{
    /**
     * The flow runs like this:
     * - save(token) writes the file, so token emits the new string and isSignedIn emits true.
     * - clear() removes the key, so token emits null and isSignedIn emits false.
     * - The nav graph, the favorites prompt and profile all collect this one flow. Nobody sets a boolean by hand.
     */
    override val isSignedIn: Flow<Boolean> = authTokenStore.token.map {
        token: String? -> token != null
    }
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
        authTokenStore.save(response.accessToken)
        Log.d(TAG, "Got access token: ${response.accessToken}")
        return true
    }

    override suspend fun signOut() {
        authTokenStore.clear()
    }
}