package com.kg.museumly.presentation.feature.eras

import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.TimelineStop

/**
 * One era on the ruler and the works the timeline holds for it.
 * firstIndex is the index of the era's first work in state.stops, or -1
 * when the era has none yet.
 */
internal class EraGroup(
    val era: Era,
    val firstIndex: Int,
    val stops: List<TimelineStop>
)

/**
 * The eras the timeline shows: every era except the 2000s, oldest first.
 * The repository decides which works each era holds; this decides which
 * eras get a place on the ruler.
 */
internal fun timelineEras(): List<Era> {
    val result: MutableList<Era> = ArrayList()
    for (era in Era.erasFor(null)) {
        if (era != Era.Y2000) {
            result.add(era)
        }
    }
    return result
}

/**
 * One group for every timeline era, in order, including eras with no works.
 * Stops arrive era by era, so the ruler keeps a fixed place for each era
 * instead of growing under the user's finger.
 */
internal fun groupByEra(stops: List<TimelineStop>): List<EraGroup> {
    val groups: MutableList<EraGroup> = ArrayList()
    for (era in timelineEras()) {
        var firstIndex: Int = -1
        val eraStops: MutableList<TimelineStop> = ArrayList()
        for (index in stops.indices) {
            val stop: TimelineStop = stops[index]
            if (stop.era == era) {
                if (firstIndex == -1) {
                    firstIndex = index
                }
                eraStops.add(stop)
            }
        }
        groups.add(EraGroup(era, firstIndex, eraStops))
    }
    return groups
}
