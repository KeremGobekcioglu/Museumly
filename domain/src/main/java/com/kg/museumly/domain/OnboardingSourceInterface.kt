package com.kg.museumly.domain

interface OnboardingSourceInterface {
    suspend fun hasCompleted(): Boolean
    suspend fun setCompleted()
}