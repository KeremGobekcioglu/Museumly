package com.kg.museumly.data.remote.cleveland

import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
 * Protects Cleveland's exhaustion signal (empty `data`, and only that — a
 * null/broken response must stay retryable) and its skip/cursor bookkeeping
 * (advances by raw records received, never by records the mapper accepted,
 * or rejected records get re-requested forever).
 *
 * Robolectric-backed (rather than isReturnDefaultValues) because
 * ClevelandProvider calls android.util.Log.d directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClevelandProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: ClevelandProvider

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
        provider = ClevelandProvider(retrofit.create(ClevelandApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun record(id: Int, licenseStatus: String? = "CC0", hasImage: Boolean = true): String {
        val images = if (hasImage) {
            """"images": {"web": {"url": "https://cdn.example.org/$id.jpg", "width": "800", "height": "600"}},"""
        } else {
            ""
        }
        return """
            {
              "id": $id,
              "title": "Record $id",
              "creators": [],
              $images
              "share_license_status": ${licenseStatus?.let { "\"$it\"" } ?: "null"}
            }
        """.trimIndent()
    }

    @Test
    fun `empty data array is the only exhaustion signal`() = runTest {
        server.enqueue(MockResponse().setBody("""{"info": {"total": 0}, "data": []}"""))

        val page: PageResult = provider.fetchPage(cursor = "40", size = 20)

        assertEquals(PageStatus.EXHAUSTED, page.status)
        assertNull(page.next)
    }

    @Test
    fun `a total failure leaves the cursor at the same skip value, not exhausted`() = runTest {
        // Both the first attempt and the one retry (500 is worth retrying) fail.
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val page: PageResult = provider.fetchPage(cursor = "40", size = 20)

        assertEquals(PageStatus.FAILED, page.status)
        assertTrue(page.items.isEmpty())
        // Not exhausted, and skip never advanced — the same page must be
        // retried, never skipped past or marked as the end of the corpus.
        assertEquals("40", page.next)
    }

    @Test
    fun `skip advances by raw dtos size, never by accepted count`() = runTest {
        val body = """
            {"info": {"total": 3}, "data": [
              ${record(id = 101)},
              ${record(id = 102, licenseStatus = "In Copyright")},
              ${record(id = 103)}
            ]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body))

        // size = 2 so the two accepted records (101, 103) already satisfy
        // the page and no second request is issued — isolates this to one
        // batch of 3 raw records.
        val page: PageResult = provider.fetchPage(cursor = "0", size = 2)

        assertEquals(2, page.items.size)
        // 3 raw records consumed, not 2 accepted ones — otherwise record 102
        // would be re-requested and re-rejected on every future page.
        assertEquals("3", page.next)
    }

    @Test
    fun `a transient 503 is retried once and can still succeed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("""{"info": {"total": 1}, "data": [${record(id = 201)}]}"""))

        val page: PageResult = provider.fetchPage(cursor = "0", size = 1)

        assertEquals(PageStatus.OK, page.status)
        assertEquals(1, page.items.size)
        assertEquals(2, server.requestCount)
    }
}
