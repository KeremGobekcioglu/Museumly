package com.kg.museumly.data.remote.met

import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.testutil.Fixtures
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Protects the cursor-poisoning trap README.md calls out twice: a failed or
 * null fetch must never persist an exhaustion marker, and a failed id-list
 * load must never be cached as if it were a real empty result. Also covers
 * the consecutive-failure threshold (isolated bad object vs. a real outage)
 * and that the width-2 object batch preserves input order despite the
 * network finishing the two calls out of order.
 *
 * Robolectric-backed (rather than isReturnDefaultValues) because
 * MetProvider calls android.util.Log.d directly — Robolectric gives that a
 * real (if fake) implementation instead of masking every unmocked Android
 * SDK call suite-wide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: MetProvider

    private val objectJson: Map<Int, String> = mapOf(
        1001 to Fixtures.read("met/object_1001.json"),
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        provider = MetProvider(retrofit.create(MetApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun objectResponse(objectId: Int, isPublicDomain: Boolean = true): String {
        return """
            {
              "objectID": $objectId,
              "title": "Object $objectId",
              "artistDisplayName": "Test Artist",
              "isPublicDomain": $isPublicDomain,
              "primaryImageSmall": "https://images.metmuseum.org/$objectId-small.jpg",
              "primaryImage": "https://images.metmuseum.org/$objectId-full.jpg"
            }
        """.trimIndent()
    }

    private fun searchResponse(ids: List<Int>): String {
        val idsJson = ids.joinToString(",")
        return """{"total": ${ids.size}, "objectIDs": [$idsJson]}"""
    }

    @Test
    fun `search returning null ids with zero total marks the provider exhausted, not failed`() = runTest {
        server.enqueue(MockResponse().setBody("""{"total": 0, "objectIDs": null}"""))

        val page: PageResult = provider.fetchPage(cursor = null, size = 20)

        assertEquals(PageStatus.EXHAUSTED, page.status)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun `loadIds does not cache after a failed search response`() = runTest {
        // Both the first attempt and the one retry (500 is worth retrying)
        // fail, so the whole page fails with no cached id list.
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val firstPage: PageResult = provider.fetchPage(cursor = null, size = 20)
        assertEquals(PageStatus.FAILED, firstPage.status)

        // A second call must hit /search again — if the failure had been
        // cached as an empty list, this would come back EXHAUSTED instead.
        server.enqueue(MockResponse().setBody(searchResponse(listOf(1001))))
        server.enqueue(MockResponse().setBody(objectResponse(1001)))

        val secondPage: PageResult = provider.fetchPage(cursor = null, size = 20)

        assertEquals(PageStatus.EXHAUSTED, secondPage.status)
        assertEquals(1, secondPage.items.size)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `an isolated object failure is skipped, not fatal to the page`() = runTest {
        server.enqueue(MockResponse().setBody(searchResponse(listOf(2001, 2002, 2003))))
        // 2002 fails once. Per-object fetches never retry (only the id-list
        // load does), so this is exactly one consecutive failure — below the
        // threshold of three — and the walk continues past it.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.contains("objects/2001") -> MockResponse().setBody(objectResponse(2001))
                    path.contains("objects/2002") -> MockResponse().setResponseCode(500)
                    path.contains("objects/2003") -> MockResponse().setBody(objectResponse(2003))
                    else -> MockResponse().setBody(searchResponse(listOf(2001, 2002, 2003)))
                }
            }
        }

        val page: PageResult = provider.fetchPage(cursor = null, size = 20)

        assertEquals(PageStatus.EXHAUSTED, page.status)
        assertEquals(listOf("met:2001", "met:2003"), page.items.map { it.id })
    }

    @Test
    fun `three consecutive object failures stop the page without advancing the cursor`() = runTest {
        server.enqueue(MockResponse().setBody(searchResponse(listOf(3001, 3002, 3003, 3004))))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/search") -> MockResponse().setBody(searchResponse(listOf(3001, 3002, 3003, 3004)))
                    // Every object call fails — three in a row trips the outage threshold.
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val page: PageResult = provider.fetchPage(cursor = null, size = 20)

        assertEquals(PageStatus.FAILED, page.status)
        assertTrue(page.items.isEmpty())
        // The cursor passed in (null = start) must come back unchanged —
        // never advanced past records that were never actually fetched.
        assertEquals(null, page.next)
    }

    @Test
    fun `object batch results preserve input order despite finishing out of order`() = runTest {
        server.enqueue(MockResponse().setBody(searchResponse(listOf(4001, 4002))))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    // 4001 answers slower than 4002, so the network finishes
                    // them in the opposite order to the batch's input order.
                    path.contains("objects/4001") -> MockResponse().setBody(objectResponse(4001))
                        .setBodyDelay(150, TimeUnit.MILLISECONDS)
                    path.contains("objects/4002") -> MockResponse().setBody(objectResponse(4002))
                    else -> MockResponse().setBody(searchResponse(listOf(4001, 4002)))
                }
            }
        }

        val page: PageResult = provider.fetchPage(cursor = null, size = 20)

        assertEquals(listOf("met:4001", "met:4002"), page.items.map { it.id })
    }
}
