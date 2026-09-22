package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Test

class RollLogicTest {

    @Test
    fun `classifies critical normal failed dice without double counting`() {
        assertEquals(
            RollSummary(criticals = 1, normals = 2, failures = 2),
            RollLogic.classify(listOf(6, 5, 3, 2, 1), threshold = 3)
        )
    }

    @Test
    fun `lethal lowers critical threshold`() {
        assertEquals(
            RollSummary(criticals = 2, normals = 1, failures = 1),
            RollLogic.classify(listOf(6, 5, 4, 1), threshold = 3, criticalThreshold = 5)
        )
    }

    @Test
    fun `critical threshold never turns failed hit into critical`() {
        assertEquals(
            RollSummary(criticals = 1, normals = 0, failures = 2),
            RollLogic.classify(
                values = listOf(5, 4, 1),
                threshold = 5,
                criticalThreshold = 4
            )
        )
    }

    @Test
    fun `defence dice still require the save threshold before a critical`() {
        assertEquals(
            RollSummary(criticals = 1, normals = 1, failures = 2),
            RollLogic.classify(listOf(6, 4, 2, 1), threshold = 4)
        )
    }

    @Test
    fun `retained normal successes are added without classifying a die`() {
        assertEquals(
            RollSummary(criticals = 0, normals = 2, failures = 1),
            RollLogic.classify(
                values = listOf(4, 2),
                threshold = 3,
                retainedNormalSuccesses = 1
            )
        )
    }

    @Test
    fun `reroll replaces only selected dice`() {
        assertEquals(
            listOf(3, 5, 4, 6),
            RollLogic.mergeRerollResults(
                current = listOf(3, 1, 2, 6),
                indices = listOf(1, 2),
                newValues = listOf(5, 4)
            )
        )
    }

    @Test
    fun `shooting resolution preserves attack success types`() {
        assertEquals(
            ShootingResolution(
                unblockedCriticals = 1,
                unblockedNormals = 0,
                blockedCriticals = 1,
                blockedNormals = 1
            ),
            RollLogic.resolveShooting(
                attack = RollSummary(criticals = 2, normals = 1, failures = 0),
                defence = RollSummary(criticals = 1, normals = 2, failures = 0)
            )
        )
    }

    @Test
    fun `two normal saves do not turn one remaining critical into two normals`() {
        assertEquals(
            ShootingResolution(
                unblockedCriticals = 1,
                unblockedNormals = 0,
                blockedCriticals = 0,
                blockedNormals = 2
            ),
            RollLogic.resolveShooting(
                attack = RollSummary(criticals = 1, normals = 2, failures = 0),
                defence = RollSummary(criticals = 0, normals = 2, failures = 0)
            )
        )
    }

    @Test
    fun `otherwise unused normal save pairs can block a critical`() {
        assertEquals(
            ShootingResolution(
                unblockedCriticals = 0,
                unblockedNormals = 0,
                blockedCriticals = 1,
                blockedNormals = 0
            ),
            RollLogic.resolveShooting(
                attack = RollSummary(criticals = 1, normals = 0, failures = 0),
                defence = RollSummary(criticals = 0, normals = 2, failures = 0)
            )
        )
    }

    @Test
    fun `unopposed critical remains one critical`() {
        assertEquals(
            ShootingResolution(
                unblockedCriticals = 1,
                unblockedNormals = 0,
                blockedCriticals = 0,
                blockedNormals = 0
            ),
            RollLogic.resolveShooting(
                attack = RollSummary(criticals = 1, normals = 0, failures = 0),
                defence = RollSummary(criticals = 0, normals = 0, failures = 0)
            )
        )
    }

    @Test
    fun `shooting resolution can block all attacks`() {
        assertEquals(
            ShootingResolution(
                unblockedCriticals = 0,
                unblockedNormals = 0,
                blockedCriticals = 1,
                blockedNormals = 2
            ),
            RollLogic.resolveShooting(
                attack = RollSummary(criticals = 1, normals = 2, failures = 1),
                defence = RollSummary(criticals = 2, normals = 1, failures = 0)
            )
        )
    }
}
