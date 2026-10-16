package com.kg.museumly.presentation.feature.eras.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.kg.museumly.domain.model.Artwork
import com.kg.museumly.domain.model.Era

// The words the Eras screen shows: era names, the hero, ranges, captions.

fun eraLabel(era: Era): String {
    return when (era) {
        Era.PREHISTORIC -> "Prehistory"
        Era.BC_3000 -> "3000 BC"
        Era.BC_2000 -> "2000 BC"
        Era.BC_1000 -> "1000 BC"
        Era.BC_500 -> "500 BC"
        Era.AD_0 -> "0 – 500"
        Era.AD_500 -> "500 – 1000"
        Era.AD_1000 -> "1000 – 1200"
        Era.AD_1200 -> "1200 – 1400"
        Era.Y1400 -> "1400s"
        Era.Y1500 -> "1500s"
        Era.Y1600 -> "1600s"
        Era.Y1700 -> "1700s"
        Era.Y1800 -> "1800s"
        Era.Y1900 -> "1900s"
        Era.Y2000 -> "2000s"
    }
}

/**
 * The hero's words: one serif number with small caps beside it.
 * "BEFORE 3000 BC", "3000 BC", "AD 1", "AD 500", "1000", "1400s".
 * The full range lives in the pill instead.
 */
internal class HeroParts(
    val prefix: String?,
    val number: String,
    val suffix: String?
)

internal fun heroParts(era: Era): HeroParts {
    return when (era) {
        Era.PREHISTORIC -> HeroParts("BEFORE", "3000", "BC")
        Era.BC_3000 -> HeroParts(null, "3000", "BC")
        Era.BC_2000 -> HeroParts(null, "2000", "BC")
        Era.BC_1000 -> HeroParts(null, "1000", "BC")
        Era.BC_500 -> HeroParts(null, "500", "BC")
        Era.AD_0 -> HeroParts("AD", "1", null)
        Era.AD_500 -> HeroParts("AD", "500", null)
        Era.AD_1000 -> HeroParts(null, "1000", null)
        Era.AD_1200 -> HeroParts(null, "1200", null)
        Era.Y1400 -> HeroParts(null, "1400s", null)
        Era.Y1500 -> HeroParts(null, "1500s", null)
        Era.Y1600 -> HeroParts(null, "1600s", null)
        Era.Y1700 -> HeroParts(null, "1700s", null)
        Era.Y1800 -> HeroParts(null, "1800s", null)
        Era.Y1900 -> HeroParts(null, "1900s", null)
        Era.Y2000 -> HeroParts(null, "2000s", null)
    }
}

/**
 * The hero's text. Caps on one side only get an invisible twin on the
 * other, so the number itself stays centered over the caption.
 */
internal fun heroText(parts: HeroParts): AnnotatedString {
    val ghost: SpanStyle = HeroCapsStyle.copy(color = Color.Transparent)
    return buildAnnotatedString {
        if (parts.prefix != null) {
            withStyle(HeroCapsStyle) { append(parts.prefix + " ") }
        } else if (parts.suffix != null) {
            withStyle(ghost) { append(parts.suffix + " ") }
        }
        append(parts.number)
        if (parts.suffix != null) {
            withStyle(HeroCapsStyle) { append(" " + parts.suffix) }
        } else if (parts.prefix != null) {
            withStyle(ghost) { append(" " + parts.prefix) }
        }
    }
}

/** What a screen reader says for the hero: the visible words, without the twin. */
internal fun heroDescription(parts: HeroParts): String {
    return listOfNotNull(parts.prefix, parts.number, parts.suffix).joinToString(" ")
}

/**
 * The era's years, from Era.begin and Era.end:
 * "1600 – 1699", "3000 – 2001 BC", "500 – 1 BC", "AD 1 – 499".
 * PREHISTORIC has no real start, so it reads "Prehistory".
 */
internal fun eraSpan(era: Era): String {
    if (era == Era.PREHISTORIC) {
        return "Prehistory"
    }
    if (era.end < 0) {
        return (-era.begin).toString() + " – " + (-era.end).toString() + " BC"
    }
    if (era.begin == 0) {
        // There is no year 0; the era starts at AD 1.
        return "AD 1 – " + era.end.toString()
    }
    return era.begin.toString() + " – " + era.end.toString()
}

/** "Artist · ca. 1665", skipping whichever part is missing. Null when both are. */
internal fun artistAndYear(artwork: Artwork): String? {
    val parts: MutableList<String> = ArrayList()
    val artist: String? = artwork.artist
    if (artist != null && artist.isNotBlank()) {
        parts.add(artist)
    }
    val year: String? = artwork.year
    if (year != null && year.isNotBlank()) {
        parts.add(year)
    }
    if (parts.isEmpty()) {
        return null
    }
    return parts.joinToString(" · ")
}
