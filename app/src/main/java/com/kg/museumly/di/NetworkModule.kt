package com.kg.museumly.di

import com.kg.museumly.data.remote.met.MetApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlin.jvm.java

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule
{
    @Provides
    @Singleton
    fun provideJson(): Json = Json{
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient() : OkHttpClient
    {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BASIC
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * we ll have different retrofit instances for different apis due to base urls.
     * it will change later on. for now, this is fine. after adding second api,
     * it will change.
     */
    @Provides
    @Singleton
    fun provideMetApi(okHttpClient: OkHttpClient, json: Json) : MetApi
    {
        val contentType = "application/json".toMediaType()
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://collectionapi.metmuseum.org/public/collection/v1/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        return retrofit.create(MetApi::class.java)
    }
}