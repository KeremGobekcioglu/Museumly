package com.kg.museumly.data.remote.met

import com.kg.museumly.domain.PageStatus
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MetProviderTest {

    private val server = MockWebServer()
    private lateinit var provider: MetProvider

    @Before
    fun setUp() {
        server.start()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        // No silent retry on a dropped connection, so the network test sees one failure.
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MetBackendApi::class.java)
        provider = MetProvider(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    @Test
    fun feedPage_mapsItemsAndPassesNextCursor() = runTest {
        enqueueJson(fixture("met/backend_feed_page.json"))

        val page = provider.fetchPage(cursor = null, size = 2, department = "11", era = null)

        assertEquals("/met/artworks?department=11&size=2", server.takeRequest().path)
        assertEquals(PageStatus.OK, page.status)
        assertEquals("33987", page.next)
        assertEquals(listOf("met:189655", "met:189656"), page.items.map { it.id })
        // details[i] belongs to items[i]
        assertEquals(listOf("Carved and gilded wood", "Oil on paper"), page.details.map { it.medium })
    }

    @Test
    fun eraPage_sendsYearsAndCursor() = runTest {
        enqueueJson(fixture("met/backend_feed_page.json"))

        provider.fetchPage(cursor = "67626", size = 20, department = "11", era = Era.Y1600)

        assertEquals(
            "/met/artworks?department=11&fromYear=1600&toYear=1699&cursor=67626&size=20",
            server.takeRequest().path,
        )
    }

    @Test
    fun exhausted_nextIsNull() = runTest {
        enqueueJson("""{"items":[],"nextCursor":"999999999","exhausted":true}""")

        val page = provider.fetchPage(cursor = "999999999", size = 20, department = "11", era = null)

        assertEquals(PageStatus.EXHAUSTED, page.status)
        assertNull(page.next)
    }

    @Test
    fun serverError_failedAndKeepsCursor() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val page = provider.fetchPage(cursor = "67626", size = 20, department = "11", era = null)

        assertEquals(PageStatus.FAILED, page.status)
        assertEquals("67626", page.next)
        assertEquals(0, page.items.size)
        assertNotNull(page.failureReason)
    }

    @Test
    fun networkError_failedAndKeepsCursor() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val page = provider.fetchPage(cursor = "67626", size = 20, department = "11", era = null)

        assertEquals(PageStatus.FAILED, page.status)
        assertEquals("67626", page.next)
    }

    @Test
    fun bodyWithoutNextCursor_failed() = runTest {
        enqueueJson("""{"items":[],"exhausted":false}""")

        val page = provider.fetchPage(cursor = "67626", size = 20, department = "11", era = null)

        assertEquals(PageStatus.FAILED, page.status)
    }

    @Test
    fun badDepartment_failsWithoutCallingServer() = runTest {
        val page = provider.fetchPage(cursor = null, size = 20, department = "abc", era = null)

        assertEquals(PageStatus.FAILED, page.status)
        assertEquals(0, server.requestCount)
    }

    // Sections live only in the app; this pins which Met departments each one asks for.
    @Test
    fun departmentsFor_eachSection() {
        assertEquals(listOf("10", "3"), provider.departmentsFor(Section.EGYPT_NEAR_EAST))
        assertEquals(listOf("13"), provider.departmentsFor(Section.GREEK_ROMAN))
        assertEquals(listOf("14"), provider.departmentsFor(Section.ISLAMIC))
        assertEquals(listOf("17"), provider.departmentsFor(Section.MEDIEVAL))
        assertEquals(listOf("11", "12"), provider.departmentsFor(Section.EUROPEAN))
        assertEquals(listOf("6"), provider.departmentsFor(Section.ASIA))
        assertEquals(listOf("5"), provider.departmentsFor(Section.AFRICA_OCEANIA_AMERICAS))
    }
}