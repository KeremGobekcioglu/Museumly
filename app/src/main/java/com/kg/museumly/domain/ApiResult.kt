package com.kg.museumly.domain

sealed class ApiResult <out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    /** Retrying won't help: 404, or a 4xx that means the request itself is invalid. */
    data class Rejected(val reason: String) : ApiResult<Nothing>()
    /** Retrying might help: timeout/IO error, 429, 5xx. */
    data class Failed(val cause: Throwable) : ApiResult<Nothing>()
}