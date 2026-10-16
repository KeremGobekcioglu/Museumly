package com.kg.museumly.domain.model

/**
 * Declaration order is timeline order. erasFor() slices with ranges, so
 * reordering entries silently breaks every section's slider.
 *
 * id goes into list keys and cursor keys ("european@1600s"). Renaming one
 * orphans its saved placements and cursors, so treat ids as permanent.
 *
 * Widths are uneven on purpose: ancient art is sparse per year, so BC eras
 * span 500-1000 years; from 1400 on, collections are dense enough for one
 * era per century. Measured on both APIs, 2026-10.
 *
 * The Met only matches a work whose whole date range fits inside the era.
 * A work dated -600..-400 is in neither BC_1000 nor BC_500. This hides about
 * half of Met's Greek & Roman works from the slider. It is expected, not a
 * bug: one wider era wouldn't help, since a Met search serves only 10,000 ids.
 */
enum class Era(val id: String, val begin: Int, val end: Int) {
    // -1_000_000, not -10000: both APIs accept it, and Met Egypt has 41 more works below -10000
    PREHISTORIC("prehistoric", -1_000_000, -3001),
    BC_3000("3000bc", -3000, -2001),
    BC_2000("2000bc", -2000, -1001),
    BC_1000("1000bc", -1000, -501),
    BC_500("500bc", -500, -1),
    AD_0("ad0", 0, 499),
    AD_500("ad500", 500, 999),
    AD_1000("ad1000", 1000, 1199),
    AD_1200("ad1200", 1200, 1399),
    Y1400("1400s", 1400, 1499),
    Y1500("1500s", 1500, 1599),
    Y1600("1600s", 1600, 1699),
    Y1700("1700s", 1700, 1799),
    Y1800("1800s", 1800, 1899),
    Y1900("1900s", 1900, 1999),
    Y2000("2000s", 2000, 2099);

    companion object {
        /**
         * No section: the whole timeline. A section: from its first era with
         * at least ~50 works to its last one before the collection runs dry.
         * Islamic and Medieval are trimmed further to fit their names: the
         * Islamic department's AD_0 works are pre-Islamic, and Medieval's
         * post-1600 works are mostly revival pieces.
         */
        fun erasFor(section: Section?): List<Era> {
            val range: ClosedRange<Era> = when (section) {
                null -> PREHISTORIC..Y2000
                Section.EGYPT_NEAR_EAST -> PREHISTORIC..AD_500
                Section.GREEK_ROMAN -> PREHISTORIC..AD_0
                Section.ISLAMIC -> AD_500..Y1900
                Section.MEDIEVAL -> AD_0..Y1500
                Section.EUROPEAN -> AD_1200..Y1900
                Section.ASIA -> BC_2000..Y2000
                Section.AFRICA_OCEANIA_AMERICAS -> BC_500..Y1900
            }
            val result: MutableList<Era> = ArrayList()
            for (era in entries) {
                if (era in range)
                    result.add(era)
            }
            return result
        }
        /** The era with this id, or null for an unknown one. */
        fun fromId(id: String): Era? {
            for (era in entries) {
                if (era.id == id)
                    return era
            }
            return null
        }

        /**
         * The other direction of erasFor: the sections whose slider holds
         * this era. The era page's picker lists these, and "All" walks over
         * them. Section declaration order, so the walk is the same every time.
         */
        fun sectionsFor(era: Era): List<Section> {
            val result: MutableList<Section> = ArrayList()
            for (section in Section.entries) {
                if (era in erasFor(section))
                    result.add(section)
            }
            return result
        }
    }
}

/**
 * Two tests are worth writing here: ids are unique, and each era begins
 * exactly one year after the previous one ends. A duplicate id would silently
 * merge two lists, and a gap would hide artworks from every slider.
 */
