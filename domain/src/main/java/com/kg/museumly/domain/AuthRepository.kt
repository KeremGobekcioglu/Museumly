package com.kg.museumly.domain

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val isSignedIn: Flow<Boolean>
    suspend fun loginWithGoogle(idToken: String) : Boolean
    suspend fun signOut()
}