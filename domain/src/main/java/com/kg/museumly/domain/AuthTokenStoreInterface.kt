package com.kg.museumly.domain

import kotlinx.coroutines.flow.Flow

interface AuthTokenStoreInterface
{
    val token: Flow<String?>
    suspend fun save(token: String)
    suspend fun clear()
}