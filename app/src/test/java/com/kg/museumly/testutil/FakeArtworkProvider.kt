package com.kg.museumly.testutil

import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.PageResult
import com.kg.museumly.model.Section

/**
 * Hand-written ArtworkProvider test double. Each fetchPage() call consumes
 * the next queued PageResult (FIFO); callCount is a plain field so a test
 * can assert a provider was skipped entirely (an already-exhausted
 * provider) or called exactly once, without a mocking-library verify.
 *
 * departments defaults to one department ("all") for every section, so a
 * test that doesn't care about departments sees one source per provider and
 * cursor keys like "met:all". requestedDepartments records what fetchPage
 * was actually asked for, in call order.
 */
class FakeArtworkProvider(
    override val id: String,
    private val departments: (Section) -> List<String> = { listOf("all") },
) : ArtworkProvider {

    private val queuedPages: ArrayDeque<PageResult> = ArrayDeque()

    var callCount: Int = 0
        private set

    val requestedDepartments: MutableList<String> = ArrayList()

    fun enqueue(page: PageResult) {
        queuedPages.addLast(page)
    }

    override suspend fun fetchPage(cursor: String?, size: Int, department: String): PageResult {
        callCount += 1
        requestedDepartments.add(department)
        val page = queuedPages.removeFirstOrNull()
        checkNotNull(page) {
            "FakeArtworkProvider(\"$id\"): fetchPage called with no queued response (call #$callCount)"
        }
        return page
    }

    override fun departmentsFor(section: Section): List<String> {
        return departments(section)
    }
}
