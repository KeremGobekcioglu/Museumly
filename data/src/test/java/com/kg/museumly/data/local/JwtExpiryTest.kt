package com.kg.museumly.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * Protects readJwtExpiry's contract: anything that is not a readable JWT with a
 * numeric exp yields null, which AuthRepositoryImpl treats as "not signed in".
 */
class JwtExpiryTest {

    private fun b64(text: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
    }

    private fun tokenWithPayload(payloadJson: String): String {
        return b64("""{"alg":"none"}""") + "." + b64(payloadJson) + ".sig"
    }

    @Test
    fun garbage_returnsNull() {
        assertNull(readJwtExpiry("not-a-jwt"))
    }

    @Test
    fun twoParts_returnsNull() {
        assertNull(readJwtExpiry(b64("""{"alg":"none"}""") + "." + b64("""{"exp":1}""")))
    }

    @Test
    fun payloadWithoutExp_returnsNull() {
        assertNull(readJwtExpiry(tokenWithPayload("""{"sub":"user"}""")))
    }

    @Test
    fun payloadWithExp_returnsIt() {
        assertEquals(4102444800L, readJwtExpiry(tokenWithPayload("""{"exp":4102444800}""")))
    }
}