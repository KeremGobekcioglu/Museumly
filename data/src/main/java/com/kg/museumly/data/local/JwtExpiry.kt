package com.kg.museumly.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/**
 * Reads the exp claim (seconds since epoch) from a JWT without verifying it.
 * Returns null when the token is not a readable JWT or has no exp.
 */
fun readJwtExpiry(token: String): Long? {
    val parts: List<String> = token.split('.')
    if (parts.size != 3) {
        return null
    }
    try {
        val payloadBytes: ByteArray = Base64.getUrlDecoder().decode(parts[1])
        val payloadText: String = String(payloadBytes, Charsets.UTF_8)
        val payload: JsonObject = Json.parseToJsonElement(payloadText).jsonObject
        val exp: JsonElement? = payload["exp"]
        if (exp !is JsonPrimitive) {
            return null
        }
        return exp.longOrNull
    } catch (e: IllegalArgumentException) {
        return null
    }
}