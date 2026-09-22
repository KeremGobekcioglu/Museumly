package com.kg.museumly.data.remote

import com.kg.museumly.domain.ApiResult
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * worthRetrying() is what decides whether a provider gets a second attempt
 * (see MetProvider/ClevelandProvider's single-retry-after-500ms). Getting
 * this wrong either retries a request that will fail identically (404) or
 * gives up on a transient server hiccup (429/5xx) after one try.
 */
class ApiResultHelperTest {

    private fun httpException(code: Int): HttpException {
        val body = "".toResponseBody(null)
        val response: Response<Any> = Response.error(code, body)
        return HttpException(response)
    }

    @Test
    fun `429 is worth retrying`() {
        val failed = ApiResult.Failed(httpException(429))

        assertTrue(failed.worthRetrying())
    }

    @Test
    fun `5xx is worth retrying`() {
        val failed = ApiResult.Failed(httpException(503))

        assertTrue(failed.worthRetrying())
    }

    @Test
    fun `404 is not worth retrying`() {
        val failed = ApiResult.Failed(httpException(404))

        assertFalse(failed.worthRetrying())
    }

    @Test
    fun `other 4xx is not worth retrying`() {
        val failed = ApiResult.Failed(httpException(400))

        assertFalse(failed.worthRetrying())
    }

    @Test
    fun `a non http cause is not worth retrying`() {
        // Offline/IOException failures fail again identically on retry — only
        // the server-side codes above are worth a second attempt.
        val failed = ApiResult.Failed(IOException("no route to host"))

        assertFalse(failed.worthRetrying())
    }
}
