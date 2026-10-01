package com.example.helloworld

import org.junit.Assert.assertTrue
import org.junit.Test

class RosterCompositionTest {
    @Test
    fun everySupportedTeamHasACompositionHint() {
        val teamIds = listOf(
            "IMP-AOD", "CHAOS-PM", "TYR-RAV", "CHAOS-WC",
            "CHAOS-FELL", "NEC-CAN", "IMP-WS", "IMP-CI",
        )

        teamIds.forEach { assertTrue(it, RosterComposition.hint(it).isNotBlank()) }
    }
}
