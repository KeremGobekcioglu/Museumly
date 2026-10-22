package com.kg.museumly.domain

interface AuthRepository {
    suspend fun loginWithGoogle(idToken: String) : Boolean
}