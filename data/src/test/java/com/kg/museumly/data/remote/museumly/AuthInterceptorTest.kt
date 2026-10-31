package com.kg.museumly.data.remote.museumly

import com.kg.museumly.domain.AuthTokenStoreInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakeAuthTokenStore(initial: String?) : AuthTokenStoreInterface
{
    val state: MutableStateFlow<String?> = MutableStateFlow(initial)
    override val token: Flow<String?> = state
    override suspend fun save(token: String) {
        state.value = token
    }

    override suspend fun clear() {
        state.value = null
    }
}

class AuthInterceptorTest
{
    private lateinit var server: MockWebServer

    @Before
    fun setUp()
    {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown()
    {
        server.shutdown()
    }

    private fun call(store: FakeAuthTokenStore, path: String)
    {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .build()
        val request: Request = Request.Builder()
            .url(server.url(path))
            .build()

        val response: Response = client.newCall(request).execute()
        response.close()
    }

    @Test
    fun storedTokenIsSentAsBearerHeader()
    {
        val store = FakeAuthTokenStore("abc")
        server.enqueue(MockResponse().setResponseCode(200))

        call(store,"/me")
        val recorded : RecordedRequest = server.takeRequest()
        assertEquals("Bearer abc", recorded.getHeader("Authorization"))
    }

    @Test
    fun noTokenMeansNoHeader() {
        val store: FakeAuthTokenStore = FakeAuthTokenStore(null)
        server.enqueue(MockResponse().setResponseCode(200))

        call(store, "/me")

        val recorded: RecordedRequest = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun authPathGetsNoHeaderAndItsUnauthorizedKeepsTheToken() {
        val store: FakeAuthTokenStore = FakeAuthTokenStore("abc")
        server.enqueue(MockResponse().setResponseCode(401))

        call(store, "/auth/google")

        val recorded: RecordedRequest = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertEquals("abc", store.state.value)
    }

    @Test
    fun unauthorizedOnProtectedPathClearsTheToken() {
        val store: FakeAuthTokenStore = FakeAuthTokenStore("abc")
        server.enqueue(MockResponse().setResponseCode(401))

        call(store, "/me")

        assertNull(store.state.value)
    }

    @Test
    fun unauthorizedForAnOldTokenKeepsTheNewOne() {
        val store: FakeAuthTokenStore = FakeAuthTokenStore("old")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // The user signs in again while this request is still in flight.
                store.state.value = "new"
                return MockResponse().setResponseCode(401)
            }
        }

        call(store, "/me")

        assertEquals("new", store.state.value)
    }
}