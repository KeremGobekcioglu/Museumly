package com.kg.museumly.data.remote.met

import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.domain.model.Section
import com.kg.museumly.testutil.Fixtures
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
        val client = OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
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

    private fun RecordedRequest.departmentId(): Int? =
        requestUrl?.queryParameter("departmentId")?.toIntOrNull()

    private fun RecordedRequest.objectId(): Int =
        path!!.substringAfterLast("/").toInt()

    @Test
    fun `search returning null ids with zero total marks the provider exhausted, not failed`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"total": 0, "objectIDs": null}"""))

            val page: PageResult = provider.fetchPage(cursor = null, size = 20, department = "11")

            assertEquals(PageStatus.EXHAUSTED, page.status)
            assertTrue(page.items.isEmpty())
        }

    @Test
    fun `ensureIds does not cache after a failed search response`() = runTest {
        // Both the first attempt and the one retry (500 is worth retrying)
        // fail, so the whole page fails with no ids cached.
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val firstPage: PageResult = provider.fetchPage(cursor = null, size = 20, department = "11")
        assertEquals(PageStatus.FAILED, firstPage.status)

        // A second call must hit /search again from offset=0 — if the
        // failure had left cachedIds looking "done", this would come back
        // EXHAUSTED instead, or skip straight to hydration with nothing cached.
        server.enqueue(MockResponse().setBody(searchResponse(listOf(1001))))
        server.enqueue(MockResponse().setBody(objectResponse(1001)))

        val secondPage: PageResult = provider.fetchPage(cursor = null, size = 20, department = "11")

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

        val page: PageResult = provider.fetchPage(cursor = null, size = 20, department = "11")

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
                    path.startsWith("/v1.1/search") -> MockResponse().setBody(
                        searchResponse(
                            listOf(
                                3001,
                                3002,
                                3003,
                                3004
                            )
                        )
                    )
                    // Every object call fails — three in a row trips the outage threshold.
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val page: PageResult = provider.fetchPage(cursor = null, size = 20, department = "11")

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

        val page: PageResult = provider.fetchPage(cursor = null, size = 20, department = "11")

        assertEquals(listOf("met:4001", "met:4002"), page.items.map { it.id })
    }

    @Test
    fun `a page needing ids past one 500-window fetches a second id page at the right offset`() =
        runTest {
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

                        else -> MockResponse().setBody(
                            objectResponse(
                                request.path!!.substringAfterLast(
                                    "/"
                                ).toInt()
                            )
                        )
                    }
                }
            }

            // Ask for one item starting right at the boundary — id 500 only
            // exists in the second page, so this can't be served without the loop.
            val page: PageResult = provider.fetchPage(cursor = "500", size = 1, department = "11")

            assertEquals(PageStatus.EXHAUSTED, page.status)
            assertEquals(listOf("met:500"), page.items.map { it.id })
            assertEquals(listOf(0, 500), searchOffsets)
        }

    @Test
    fun `running out of hydrated ids mid-query returns OK with a next cursor, not EXHAUSTED`() =
        runTest {
            // total says 10 ids exist but this page only returns 3 — a real
            // gap between "what we've cached" and "what the query actually has
            // left." The old exhaustion check (cursor caught up to cachedIds.size)
            // would wrongly call this done; reachable() must not.
            // size = 3 so the page ends exactly at the cached ids. With a larger
            // size the provider asks for the next search page, which isn't queued,
            // and waits out OkHttp's 10 s read timeout.
            server.enqueue(
                MockResponse().setBody(
                    searchResponse(
                        listOf(5001, 5002, 5003),
                        total = 10
                    )
                )
            )
            server.enqueue(MockResponse().setBody(objectResponse(5001)))
            server.enqueue(MockResponse().setBody(objectResponse(5002)))
            server.enqueue(MockResponse().setBody(objectResponse(5003)))

            val page: PageResult = provider.fetchPage(cursor = null, size = 3, department = "11")

            assertEquals(PageStatus.OK, page.status)
            assertEquals("3", page.next)
            assertEquals(3, page.items.size)
            // 1 search + 3 objects. A 5th request means it went looking for more ids.
            assertEquals(4, server.requestCount)
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

                    else -> MockResponse().setBody(
                        objectResponse(
                            request.path!!.substringAfterLast(
                                "/"
                            ).toInt()
                        )
                    )
                }
            }
        }

        // Cursor already at 9999 — one id left in reach (9999), then done.
        val page: PageResult = provider.fetchPage(cursor = "9999", size = 5, department = "11")

        assertEquals(PageStatus.EXHAUSTED, page.status)
        assertEquals(listOf("met:9999"), page.items.map { it.id })
        assertEquals((0..9_500 step 500).toList(), searchOffsets)
        assertEquals(List(20) { 500 }, searchLimits)
    }

    @Test
    fun `a non-numeric department fails the page without calling the API`() = runTest {
        // The Met ignores a bad departmentId and searches the whole
        // collection, so a malformed value must never reach the network.
        val page: PageResult =
            provider.fetchPage(cursor = "12", size = 20, department = "European Paintings")

        assertEquals(PageStatus.FAILED, page.status)
        assertEquals("12", page.next)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the department is sent to search as departmentId`() = runTest {
        val searchDepartments = mutableListOf<Int?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return if (path.startsWith("/v1.1/search")) {
                    searchDepartments.add(request.departmentId())
                    MockResponse().setBody(searchResponse(listOf(1001)))
                } else {
                    MockResponse().setBody(objectResponse(request.objectId()))
                }
            }
        }

        provider.fetchPage(cursor = null, size = 20, department = "6")

        assertEquals(listOf<Int?>(6), searchDepartments)
    }

    @Test
    fun `each department keeps its own id cache`() = runTest {
        val searchDepartments = mutableListOf<Int?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return if (path.startsWith("/v1.1/search")) {
                    val department = request.departmentId()
                    searchDepartments.add(department)
                    val ids = if (department == 11) listOf(7001, 7002) else listOf(8001, 8002)
                    MockResponse().setBody(searchResponse(ids))
                } else {
                    MockResponse().setBody(objectResponse(request.objectId()))
                }
            }
        }

        val european: PageResult = provider.fetchPage(cursor = null, size = 1, department = "11")
        val modern: PageResult = provider.fetchPage(cursor = null, size = 1, department = "12")
        // Department 11 again, from its own cursor — must come from its own
        // cache, not department 12's list and not a fresh search.
        val europeanNext: PageResult =
            provider.fetchPage(cursor = european.next, size = 1, department = "11")

        assertEquals(listOf("met:7001"), european.items.map { it.id })
        assertEquals(listOf("met:8001"), modern.items.map { it.id })
        assertEquals(listOf("met:7002"), europeanNext.items.map { it.id })
        assertEquals(listOf<Int?>(11, 12), searchDepartments)
    }

    @Test
    fun `unparseable objects are rejected, so three in a row are not an outage`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/v1.1/search") -> MockResponse().setBody(
                        searchResponse(
                            listOf(
                                9001,
                                9002,
                                9003,
                                9004
                            )
                        )
                    )

                    path.contains("objects/9004") -> MockResponse().setBody(objectResponse(9004))
                    // 200 with a body the converter can't decode -> SerializationException.
                    else -> MockResponse().setBody("{not json")
                }
            }
        }

        val page: PageResult = provider.fetchPage(cursor = null, size = 20, department = "11")

        assertEquals(PageStatus.EXHAUSTED, page.status)
        assertEquals(listOf("met:9004"), page.items.map { it.id })
    }

    @Test
    fun `an outage after some successes rewinds the cursor to the first failure of the streak`() =
        runTest {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: ""
                    return when {
                        path.startsWith("/v1.1/search") -> MockResponse().setBody(
                            searchResponse(
                                listOf(6001, 6002, 6003, 6004, 6005)
                            )
                        )

                        path.contains("objects/6001") -> MockResponse().setBody(objectResponse(6001))
                        // 6002, 6003, 6004 fail in a row -> outage on 6004.
                        else -> MockResponse().setResponseCode(500)
                    }
                }
            }

            val page: PageResult = provider.fetchPage(cursor = null, size = 20, department = "11")

            // 6001 was delivered, so this is a normal page — but the cursor must
            // point at 6002 (index 1), not at 6004, or 6002 and 6003 are lost.
            assertEquals(PageStatus.OK, page.status)
            assertEquals(listOf("met:6001"), page.items.map { it.id })
            assertEquals("1", page.next)
        }

    @Test
    fun `a 403 below the streak threshold stops the page and rewinds to the first blocked id`() =
        runTest {
            // Recorded on MockWebServer's thread, asserted afterwards — see the
            // multi-page test above for why.
            val requestedObjects: MutableList<Int> =
                java.util.Collections.synchronizedList(mutableListOf())
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: ""
                    if (path.startsWith("/v1.1/search")) {
                        return MockResponse().setBody(searchResponse((1101..1108).toList()))
                    }
                    val objectId = request.objectId()
                    requestedObjects.add(objectId)
                    return when (objectId) {
                        // First batch: ok, ok, 403, 403. The streak is 2, under the
                        // threshold, so only the block check can stop the walk here.
                        1103, 1104 -> MockResponse().setResponseCode(403)
                        else -> MockResponse().setBody(objectResponse(objectId))
                    }
                }
            }

            val page: PageResult = provider.fetchPage(cursor = null, size = 20, department = "11")

            assertEquals(PageStatus.OK, page.status)
            assertEquals(listOf("met:1101", "met:1102"), page.items.map { it.id })
            // Cursor on 1103 (index 2), so the blocked ids are retried, not skipped.
            assertEquals("2", page.next)
            // No second batch went out into the block.
            assertEquals(listOf(1101, 1102, 1103, 1104), requestedObjects.sorted())

            // While cooling down, the next page fails without touching the network.
            val requestsBefore: Int = server.requestCount
            val cooling: PageResult =
                provider.fetchPage(cursor = page.next, size = 20, department = "11")
            assertEquals(PageStatus.FAILED, cooling.status)
            assertEquals("2", cooling.next)
            assertEquals(requestsBefore, server.requestCount)
        }

    @Test
    fun `every section maps to numeric Met department ids`() {
        // fetchPage parses the department back with toIntOrNull; a
        // non-numeric entry here would fail that department on every page.
        for (section in Section.entries) {
            val departments: List<String> = provider.departmentsFor(section)
            assertTrue("$section has no Met departments", departments.isNotEmpty())
            for (department in departments) {
                assertTrue(
                    "$section maps to non-numeric '$department'",
                    department.toIntOrNull() != null
                )
            }
        }
    }
}
