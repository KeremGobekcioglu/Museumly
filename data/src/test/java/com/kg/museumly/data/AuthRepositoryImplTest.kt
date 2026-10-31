package com.kg.museumly.data

import com.kg.museumly.data.remote.museumly.MuseumlyApi
import com.kg.museumly.domain.AuthTokenStoreInterface
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Protects isSignedIn: a stored token only counts as signed in while its exp
 * is in the future. Uses exp = 1 (long past) and the year 2100 so no clock is needed.
 */
class AuthRepositoryImplTest {

    private class FakeTokenStore(stored: String?) : AuthTokenStoreInterface {
        override val token: Flow<String?> = flowOf(stored)
        override suspend fun save(token: String) = Unit
        override suspend fun clear() = Unit
    }

    private fun b64(text: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
    }

    private fun tokenWithExp(exp: Long): String {
        return b64("""{"alg":"none"}""") + "." + b64("""{"exp":$exp}""") + ".sig"
    }

    private fun repoWith(stored: String?): AuthRepositoryImpl {
        return AuthRepositoryImpl(mockk<MuseumlyApi>(), FakeTokenStore(stored))
    }

    @Test
    fun expiredToken_isNotSignedIn() = runTest {
        assertFalse(repoWith(tokenWithExp(1L)).isSignedIn.first())
    }

    @Test
    fun futureToken_isSignedIn() = runTest {
        assertTrue(repoWith(tokenWithExp(4102444800L)).isSignedIn.first())
    }
}