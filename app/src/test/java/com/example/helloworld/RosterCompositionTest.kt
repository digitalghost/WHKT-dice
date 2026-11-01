package com.example.helloworld

import org.junit.Assert.assertTrue
import org.junit.Test

class RosterCompositionTest {
    @Test
    fun everySupportedTeamHasACompositionHint() {
        val teamIds = RosterCatalog.teams.map { it.id }
        assertTrue(teamIds.size >= 48)
        teamIds.forEach {
            val hint = RosterComposition.hint(it)
            assertTrue("$it missing custom compositionHint", hint.isNotBlank() && hint != "请按照下方编成规则原卡选择成员。")
        }
    }
}
