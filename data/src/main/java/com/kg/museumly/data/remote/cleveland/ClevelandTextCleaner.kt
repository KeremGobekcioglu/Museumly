package com.kg.museumly.data.remote.cleveland

/**
 * Cleveland's wall texts arrive as light HTML written for their website and
 * galleries. They are cleaned here, once, so the entity only ever holds text
 * the detail screen can show as it is. <em> and <br> are kept on purpose —
 * WallText renders them.
 */
internal object ClevelandTextCleaner {

    // Over this a wall text swamps the screen. A text cut off with an
    // ellipsis is worse than none, so a long one is dropped whole.
    private const val MAX_WALL_TEXT_LENGTH: Int = 3000

    // Texts written for the physical gallery ("similar armors are displayed
    // nearby") read as nonsense in the app. Deliberately broad: dropping a
    // good text costs little, showing a broken one costs more.
    private val GALLERY_PHRASES: List<String> = listOf(
        "nearby",
//        "on view",
//        "on display",
        "this gallery",
        "next gallery",
        "adjacent gallery",
        "across the gallery",
        "in this room",
        "displayed here",
    )

    // Cross-references to other Cleveland works ("CMA 1954.603"). They point
    // outside the app, so only the visible text is kept.
    private val ANCHOR: Regex = Regex(
        "<a\\b[^>]*>(.*?)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // Only ever wraps link text; meaningless once the link is gone.
    private val UNDERLINE: Regex = Regex("</?u\\s*>", RegexOption.IGNORE_CASE)

    private val NAMED_ENTITIES: Map<String, String> = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "ndash" to "–",
        "mdash" to "—",
        "lsquo" to "‘",
        "rsquo" to "’",
        "ldquo" to "“",
        "rdquo" to "”",
        "hellip" to "…",
    )

    fun wallText(raw: String?): String? {
        if (raw == null) {
            return null
        }
        val decoded: String = decodeEntities(raw)
        if (mentionsGallery(decoded)) {
            return null
        }
//        if (decoded.length > MAX_WALL_TEXT_LENGTH) {
//            return null
//        }
        var text: String = ANCHOR.replace(decoded) { match -> match.groupValues[1] }
        text = UNDERLINE.replace(text, "")
        return blankToNull(text)
    }

    fun decodeEntities(text: String): String {
        val result: StringBuilder = StringBuilder(text.length)
        var index: Int = 0
        while (index < text.length) {
            val current: Char = text[index]
            if (current != '&') {
                result.append(current)
                index += 1
                continue
            }
            val end: Int = text.indexOf(';', index)
            // Real entities are short; a far-off ';' means this '&' is text.
            if (end == -1 || end - index > 10) {
                result.append(current)
                index += 1
                continue
            }
            val decoded: String? = decodeEntity(text.substring(index + 1, end))
            if (decoded == null) {
                result.append(current)
                index += 1
                continue
            }
            result.append(decoded)
            index = end + 1
        }
        return result.toString()
    }

    private fun decodeEntity(name: String): String? {
        if (name.startsWith("#x") || name.startsWith("#X")) {
            val code: Int = name.substring(2).toIntOrNull(16) ?: return null
            return codePointToString(code)
        }
        if (name.startsWith("#")) {
            val code: Int = name.substring(1).toIntOrNull() ?: return null
            return codePointToString(code)
        }
        return NAMED_ENTITIES[name]
    }

    private fun codePointToString(code: Int): String? {
        if (!Character.isValidCodePoint(code)) {
            return null
        }
        return String(Character.toChars(code))
    }

    private fun mentionsGallery(text: String): Boolean {
        val lower: String = text.lowercase()
        for (phrase in GALLERY_PHRASES) {
            if (lower.contains(phrase)) {
                return true
            }
        }
        return false
    }

    private fun blankToNull(text: String): String? {
        val trimmed: String = text.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return trimmed
    }
}
