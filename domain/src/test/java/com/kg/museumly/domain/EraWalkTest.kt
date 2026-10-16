package com.kg.museumly.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EraWalkTest {

    @Test
    fun shownWorksNeverMove_whenListsGrowOrRunOut() {
        val european: MutableList<String> = mutableListOf("E0", "E1", "E2")
        val islamic: MutableList<String> = mutableListOf("I0")
        val exhausted: MutableList<Boolean> = mutableListOf(false, false)

        val before: List<String> = walkRounds(listOf(european, islamic), exhausted)

        islamic.add("I1")
        val afterGrow: List<String> = walkRounds(listOf(european, islamic), exhausted)

        exhausted[1] = true
        val afterRunOut: List<String> = walkRounds(listOf(european, islamic), exhausted)

        assertEquals(before, afterGrow.subList(0, before.size))
        assertEquals(afterGrow, afterRunOut.subList(0, afterGrow.size))
        assertEquals(listOf("E0", "I0", "E1", "I1", "E2"), afterRunOut)
    }
}
