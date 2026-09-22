package com.kg.museumly.testutil

import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.PageResult

/**
 * Hand-written ArtworkProvider test double. Each fetchPage() call consumes
 * the next queued PageResult (FIFO); callCount is a plain field so a test
 * can assert a provider was skipped entirely (an already-exhausted
 * provider) or called exactly once, without a mocking-library verify.
 */
class FakeArtworkProvider(override val id: String) : ArtworkProvider {

    private val queuedPages: ArrayDeque<PageResult> = ArrayDeque()

    var callCount: Int = 0
        private set

    fun enqueue(page: PageResult) {
        queuedPages.addLast(page)
    }

    override suspend fun fetchPage(cursor: String?, size: Int): PageResult {
        callCount += 1
        val page = queuedPages.removeFirstOrNull()
        checkNotNull(page) {
            "FakeArtworkProvider(\"$id\"): fetchPage called with no queued response (call #$callCount)"
        }
        return page
    }
}
