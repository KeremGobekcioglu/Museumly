package com.kg.museumly.data.remote.met

import com.kg.museumly.domain.ArtworkProvider
import com.kg.museumly.domain.PageResult
import com.kg.museumly.domain.PageStatus
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.ArtworkDetail
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class MetProvider @Inject constructor(
    private val api: MetBackendApi
) : ArtworkProvider {

    override val id = "met"

    override suspend fun fetchPage(
        cursor: String?,
        size: Int,
        department: String,
        era: Era?
    ): PageResult {
        val departmentId: Int = department.toIntOrNull()
            ?: return failed(cursor, "bad Met department '$department'")

        val page: MetArtworkPageDto = try {
            api.artworks(
                department = departmentId,
                fromYear = era?.begin,
                toYear = era?.end,
                cursor = cursor,
                size = size,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No network, 4xx/5xx, or a body that breaks the contract: nothing usable
            // this time. The repository keeps the saved cursor and asks again later.
            return failed(cursor, "Met backend: ${e.message ?: e::class.simpleName}")
        }

        val items: List<Artwork> = page.items.map { MetBackendMapper.toDomain(it) }
        val details: List<ArtworkDetail> = page.items.map { MetBackendMapper.toDetail(it) }

        // The last page can still carry items; EXHAUSTED only says nothing comes after.
        if (page.exhausted) {
            return PageResult(items, details, next = null, status = PageStatus.EXHAUSTED)
        }
        return PageResult(items, details, next = page.nextCursor, status = PageStatus.OK)
    }

    private fun failed(cursor: String?, reason: String): PageResult {
        return PageResult(emptyList(), emptyList(), cursor, PageStatus.FAILED, reason)
    }

    private fun departmentIdsFor(section: Section): List<Int>
    {
        return when (section) {
            Section.EGYPT_NEAR_EAST -> listOf(10, 3)
            Section.GREEK_ROMAN -> listOf(13)
            Section.ISLAMIC -> listOf(14)
            Section.MEDIEVAL -> listOf(17)
            Section.EUROPEAN -> listOf(11, 12)
            Section.ASIA -> listOf(6)
            Section.AFRICA_OCEANIA_AMERICAS -> listOf(5)
        }
    }

    override fun departmentsFor(section: Section): List<String>
    {
        val result: MutableList<String> = ArrayList()
        for (id in departmentIdsFor(section)) {
            result.add(id.toString())
        }
        return result
    }
}