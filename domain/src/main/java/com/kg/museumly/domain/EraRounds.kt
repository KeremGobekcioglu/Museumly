package com.kg.museumly.domain

/**
 * How many complete rounds "All" can show: the length of the shortest live
 * (not exhausted) list. When every list is exhausted, the longest one, so
 * every work is shown.
 *
 * sizes and exhausted are parallel: index i is the same section in both.
 */
fun completeRounds(sizes: List<Int>, exhausted: List<Boolean>): Int {
    require(sizes.size == exhausted.size) { "sizes and exhausted must be parallel" }
    var anyLive: Boolean = false
    var shortestLive: Int = Int.MAX_VALUE
    var longest: Int = 0
    for (i in sizes.indices) {
        if (sizes[i] > longest) {
            longest = sizes[i]
        }
        if (!exhausted[i]) {
            anyLive = true
            if (sizes[i] < shortestLive) {
                shortestLive = sizes[i]
            }
        }
    }
    if (anyLive) {
        return shortestLive
    }
    return longest
}
/**
 * The "All" list of an era page: one work from each section in turn,
 * complete rounds only.
 *
 * Append-only: lists only grow, and a list running out only lifts a limit,
 * so a shown work never moves. The grid never jumps and a saved index stays
 * valid.
 *
 * lists must come in the same order every call (sectionsFor's order).
 */

fun <T> walkRounds(lists: List<List<T>>, exhausted: List<Boolean>): List<T> {
    val sizes: MutableList<Int> = ArrayList()
    for (list in lists) {
        sizes.add(list.size)
    }
    val rounds: Int = completeRounds(sizes, exhausted)
    val result: MutableList<T> = ArrayList()
    for (round in 0 until rounds) {
        for (list in lists) {
            if (round < list.size) {
                result.add(list[round])
            }
        }
    }
    return result
}