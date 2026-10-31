package com.kg.museumly.data.remote.museumly

import com.kg.museumly.domain.AuthTokenStoreInterface
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val authTokenStore: AuthTokenStoreInterface
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original : Request = chain.request()
        if(original.url.encodedPath.startsWith("/auth/"))
        {
            return chain.proceed(original)
        }

        val token: String? = runBlocking {
            authTokenStore.token.first()
        }
        if(token == null)
            return chain.proceed(original)

        val authorized: Request = original.newBuilder()
            .header("Authorization" , "Bearer $token")
            .build()
        val response: Response = chain.proceed(authorized)
        if(response.code == 401)
        {
            runBlocking {
                val current: String? = authTokenStore.token.first()
                if(current == token)
                {
                    authTokenStore.clear()
                }
            }
        }
        return response
    }

}