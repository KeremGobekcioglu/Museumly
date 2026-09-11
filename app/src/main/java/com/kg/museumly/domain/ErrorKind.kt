package com.kg.museumly.domain

/**
 * The only distinction that changes what the user can do about it:
 * check their connection, or nothing — every other cause (server error,
 * bad response, parsing bug) collapses into the same generic bucket.
 */
enum class ErrorKind {
    NETWORK,
    UNKNOWN
}