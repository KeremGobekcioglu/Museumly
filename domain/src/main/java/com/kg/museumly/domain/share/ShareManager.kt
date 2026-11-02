package com.kg.museumly.domain.share


enum class ShareTarget {
    InstagramStories,
    WhatsApp,
    Facebook,
    SystemSheet,
}

/**
 * Outcome of a share attempt. Modelled explicitly rather than as Boolean/exception because the
 * UI reacts differently to each: [Success] closes the sheet, [TargetUnavailable] falls back to
 * the system sheet, [Failed] shows an error in the sheet.
 *
 * There is no "cancelled": ACTION_SEND hands over to another app and reports nothing back.
 */
sealed interface ShareResult {
    data object Success : ShareResult
    data object TargetUnavailable : ShareResult
    data class Failed(val cause: Throwable?) : ShareResult
}

/**
 * Platform handoff for an already-rendered PNG. An interface (not a function in a composable)
 * so the Android specifics (Context, FileProvider, intents) stay out of the UI and the UI can be
 * tested with a fake.
 */
interface ShareManager {
    /** Cheap installed-check so the sheet can hide targets that can't work on this device. */
    fun isAvailable(target: ShareTarget): Boolean

    /** Writes [png] somewhere the target can read it and launches the handoff. */
    suspend fun share(png: ByteArray, target: ShareTarget): ShareResult

    /** Saves [png] to Pictures/Museumly. Needs no permission on API 29+ (our minSdk). */
    suspend fun saveToGallery(png: ByteArray): ShareResult

    suspend fun shareText(text: String, target: ShareTarget): ShareResult
}
