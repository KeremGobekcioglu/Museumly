package com.kg.museumly.domain.share

/**
 * Values the share handoff needs that live in app resources, which `:data` can't see.
 * Blank means "not configured": the feature that needs it is hidden or left off.
 *
 * @param facebookAppId registered Meta app id; Instagram rejects Story intents without one.
 * @param landingUrl link sticker on the story and link in shared text.
 */
data class ShareConfig(
    val facebookAppId: String,
    val landingUrl: String,
)
