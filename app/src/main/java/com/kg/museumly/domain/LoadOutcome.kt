package com.kg.museumly.domain

sealed class LoadOutcome {
    data object Loaded : LoadOutcome()
    data object Exhausted : LoadOutcome()
    data class Failed(val reason: String, val kind: ErrorKind = ErrorKind.UNKNOWN) : LoadOutcome()
}
