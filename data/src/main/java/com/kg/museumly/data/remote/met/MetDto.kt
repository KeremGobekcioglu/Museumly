package com.kg.museumly.data.remote.met

import kotlinx.serialization.Serializable


/***
 * Every field except objectID has a default. A record missing title should degrade to unusable,
 * not crash the page. objectIDs is nullable because the Met returns null, not [], when nothing matches.
 */

@Serializable
data class MetSearchDto(
    val total: Int = 0,
    val objectIDs: List<Int>? = null
)

@Serializable
data class MetObjectDto(
    val objectID: Int,
    val title: String = "",
    val artistDisplayName: String? = null,
    val artistDisplayBio: String? = null,
    val objectDate: String? = null,
    val objectBeginDate: Int? = null,
    val primaryImageSmall: String? = null,
    val primaryImage: String? = null,
    val isPublicDomain: Boolean = false,
    val classification: String? = null,
    val department: String? = null,
    val medium: String? = null,
    val dimensions: String? = null,
    val creditLine: String? = null,
    val culture: String? = null,
    val period: String? = null,
)


@Serializable
data class MetDepartmentsDto(
    val departments: List<MetDepartmentDto> = emptyList()
)

@Serializable
data class MetDepartmentDto(
    val departmentId: Int,
    val displayName: String = ""
)