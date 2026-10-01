package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamMarksTest {
    private val teamIds = listOf(
        "IMP-AOD", "CHAOS-PM", "TYR-RAV", "CHAOS-WC",
        "CHAOS-FELL", "NEC-CAN", "IMP-WS", "IMP-CI",
    )

    @Test
    fun everySupportedTeamHasItsOwnMark() {
        val marks = teamIds.map { TeamMarks.resourceOrNull(it) }

        assertTrue(marks.all { it != null })
        assertEquals(teamIds.size, marks.toSet().size)
    }

    @Test
    fun unknownTeamDoesNotMasqueradeAsASupportedTeam() {
        assertNull(TeamMarks.resourceOrNull("UNKNOWN"))
    }
}
