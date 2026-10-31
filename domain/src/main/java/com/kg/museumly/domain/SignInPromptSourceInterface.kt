package com.kg.museumly.domain

/**
 * Whether the sign-in sheet has already been shown. It is asked once, at the
 * first favorite, and the answer survives restarts.
 */
interface SignInPromptSourceInterface {

    suspend fun hasAsked(): Boolean

    suspend fun setAsked()
}