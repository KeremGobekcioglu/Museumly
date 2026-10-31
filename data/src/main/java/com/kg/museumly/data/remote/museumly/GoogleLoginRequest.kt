// Studio's in-editor lint flags the plugin-generated serializer of every
// @Serializable class in this library module. Gradle lint and the compiler don't.
@file:Suppress("UnsafeOptInUsageError")
package com.kg.museumly.data.remote.museumly

import kotlinx.serialization.Serializable

@Serializable
data class GoogleLoginRequest(
    val idToken: String
)
