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
import org.junit.Assert.assertNull
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
 * null fetch must never persist an exhaustion marker, and a failed id-page
 * load must never be cached as if it were a real empty result. Also covers
 * the consecutive-failure threshold (isolated bad object vs. a real outage),
 * that the width-2 object batch preserves input order despite the network
 * finishing the two calls out of order, and the v1.1 migration's incremental
 * id paging: cachedIds grows 500 at a time on demand, and exhaustion is
 * min(total, 10_000) — not "cursor caught up to however many ids happen to
 * be cached so far."
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

    // total defaults to ids.size, matching a search page that returns
    // everything there is. Tests that need to simulate "more pages exist"
    // (a real total bigger than this one page's ids) pass it explicitly.
    private fun searchResponse(ids: List<Int>, total: Int = ids.size): String {
        val idsJson = ids.joinToString(",")
        return """{"total": $total, "objectIDs": [$idsJson]}"""
    }

    private fun RecordedRequest.offset(): Int? =
        requestUrl?.queryParameter("offset")?.toIntOrNull()

    private fun RecordedRequest.limit(): Int? =
        requestUrl?.queryParameter("limit")?.toIntOrNull()

    @Test
    fun `search returning null ids with zero total marks the provider exhausted, not failed`() = runTest {
        server.enqueue(MockResponse().setBody("""{"total": 0, "objectIDs": null}"""))

        val page: PageResult = provider.fetchPage(cursor = null, size = 20)

        assertEquals(PageStatus.EXHAUSTED, page.status)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun `ensureIds does not cache after a failed search response`() = runTest {
        // Both the first attempt and the one retry (500 is worth retrying)
        // fail, so the whole page fails with no ids cached.
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val firstPage: PageResult = provider.fetchPage(cursor = null, size = 20)
        assertEquals(PageStatus.FAILED, firstPage.status)

        // A second call must hit /search again from offset=0 — if the
        // failure had left cachedIds looking "done", this would come back
        // EXHAUSTED instead, or skip straight to hydration with nothing cached.
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
        // 2002 fails once. Per-object fetches never retry (only the id-page
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
                    path.startsWith("/v1.1/search") -> MockResponse().setBody(searchResponse(listOf(3001, 3002, 3003, 3004)))
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

    @Test
    fun `a page needing ids past one 500-window fetches a second id page at the right offset`() = runTest {
        // total=501 forces ensureIds to loop: the first search response can
        // only carry ID_PAGE_LIMIT (500) ids per the real API's cap, so id
        // 500 isn't available until a second /search call at offset=500.
        val firstWindow = (0 until 500).toList()
        // dispatch() runs on MockWebServer's own thread — an AssertionError
        // thrown in there gets swallowed into a broken response instead of
        // failing the test, so just record offsets here and assert on them
        // afterwards, same as the requestCount checks below already do.
        val searchOffsets = mutableListOf<Int?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/v1.1/search") -> {
                        val offset = request.offset() ?: 0
                        searchOffsets.add(offset)
                        if (offset == 0) {
                            MockResponse().setBody(searchResponse(firstWindow, total = 501))
                        } else {
                            MockResponse().setBody(searchResponse(listOf(500), total = 501))
                        }
                    }
                    else -> MockResponse().setBody(objectResponse(request.path!!.substringAfterLast("/").toInt()))
                }
            }
        }

        // Ask for one item starting right at the boundary — id 500 only
        // exists in the second page, so this can't be served without the loop.
        val page: PageResult = provider.fetchPage(cursor = "500", size = 1)

        assertEquals(PageStatus.EXHAUSTED, page.status)
        assertEquals(listOf("met:500"), page.items.map { it.id })
        assertEquals(listOf(0, 500), searchOffsets)
    }

    @Test
    fun `running out of hydrated ids mid-query returns OK with a next cursor, not EXHAUSTED`() = runTest {
        // total says 10 ids exist but this page only returns 3 — a real
        // gap between "what we've cached" and "what the query actually has
        // left." The old exhaustion check (cursor caught up to cachedIds.size)
        // would wrongly call this done; reachable() must not.
        server.enqueue(MockResponse().setBody(searchResponse(listOf(5001, 5002, 5003), total = 10)))
        server.enqueue(MockResponse().setBody(objectResponse(5001)))
        server.enqueue(MockResponse().setBody(objectResponse(5002)))
        server.enqueue(MockResponse().setBody(objectResponse(5003)))

        val page: PageResult = provider.fetchPage(cursor = null, size = 20)

        assertEquals(PageStatus.OK, page.status)
        assertEquals("3", page.next)
        assertEquals(3, page.items.size)
    }

    @Test
    fun `cachedIds stops growing at the 10k ceiling even when total claims more`() = runTest {
        // total is far past SEARCH_CEILING. reachable() = min(total, 10_000),
        // so once the cursor reaches 10_000, the page must report EXHAUSTED
        // rather than issuing another /search call past the ceiling.
        val lastWindow = (9500 until 10_000).toList()
        // See the multi-page test above for why offset/limit are recorded
        // here and asserted after fetchPage returns, not inside dispatch().
        val searchOffsets = mutableListOf<Int?>()
        val searchLimits = mutableListOf<Int?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/v1.1/search") -> {
                        val offset = request.offset() ?: 0
                        val limit = request.limit() ?: 0
                        searchOffsets.add(offset)
                        searchLimits.add(limit)
                        val ids = (offset until minOf(offset + limit, 10_000)).toList()
                        MockResponse().setBody(searchResponse(ids, total = 50_000))
                    }
                    else -> MockResponse().setBody(objectResponse(request.path!!.substringAfterLast("/").toInt()))
                }
            }
        }

        // Cursor already at 9999 — one id left in reach (9999), then done.
        val page: PageResult = provider.fetchPage(cursor = "9999", size = 5)

        assertEquals(PageStatus.EXHAUSTED, page.status)
        assertEquals(listOf("met:9999"), page.items.map { it.id })
        assertEquals((0..9_500 step 500).toList(), searchOffsets)
        assertEquals(List(20) { 500 }, searchLimits)
    }
}