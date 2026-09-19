package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Test

class RollLogicTest {

    @Test
    fun `classifies critical normal and failed dice without double counting`() {
        assertEquals(
            RollSummary(criticals = 1, normals = 2, failures = 2),
            RollLogic.classify(listOf(6, 5, 3, 2, 1), threshold = 3)
        )
    }

    @Test
    fun `lethal lowers the critical threshold`() {
        assertEquals(
            RollSummary(criticals = 2, normals = 1, failures = 1),
            RollLogic.classify(listOf(6, 5, 4, 1), threshold = 3, criticalThreshold = 5)
        )
    }

    @Test
    fun `retained cover adds one normal save`() {
        assertEquals(
            RollSummary(criticals = 0, normals = 2, failures = 1),
            RollLogic.classify(listOf(4, 2), threshold = 3, retainedNormalSuccesses = 1)
        )
    }

    @Test
    fun `reroll results replace only selected dice`() {
        assertEquals(
            listOf(3, 5, 4, 6),
            RollLogic.mergeRerollResults(listOf(3, 1, 2, 6), listOf(1, 2), listOf(5, 4))
        )
    }
}
