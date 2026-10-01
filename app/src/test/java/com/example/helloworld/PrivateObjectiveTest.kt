package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateObjectiveTest {
    @Test fun everyTacticalArchetypeHasExactlyThreeCards() {
        val archetypes=setOf(
            TeamArchetypes.INFILTRATION,
            TeamArchetypes.RECON,
            TeamArchetypes.SECURITY,
            TeamArchetypes.SEEK_AND_DESTROY
        )
        val tacticalCards=MissionCards.all.filter { it.archetype!=null }

        assertEquals(12,tacticalCards.size)
        archetypes.forEach { archetype ->
            assertEquals(archetype,3,tacticalCards.count { it.archetype==archetype })
        }
    }

    @Test fun everyTeamHasTwoTacticalArchetypesAndEligibleCards() {
        RosterCatalog.teams.forEach { team ->
            val archetypes=TeamArchetypes.forTeam(team.id)
            assertEquals(team.id,2,archetypes.size)
            val eligible=MissionCards.tacticalForTeam(team.id)
            assertEquals(team.id,6,eligible.size)
            assertTrue(eligible.all { card ->
                card.archetype?.let { TeamArchetypes.supports(team.id,it) } == true
            })
        }
    }

    @Test fun hiddenStatusesNeverExposeSelectionsBeforeReveal() {
        ScoreType.entries.forEach { selected ->
            val status=SecretSelection.primaryStatus(selected)
            assertEquals("已选择（隐藏）",status)
            assertFalse(status.contains(selected.label))
        }
        assertEquals("已选择（隐藏）",SecretSelection.tacticalStatus("主宰",false))
        assertEquals("已公开：主宰",SecretSelection.tacticalStatus("主宰",true))
        assertFalse(SecretSelection.tacticalDetailsArePublic("主宰",false,false))
        assertTrue(SecretSelection.tacticalDetailsArePublic("主宰",true,false))
        assertTrue(SecretSelection.tacticalDetailsArePublic("主宰",false,true))
        assertFalse(SecretSelection.tacticalDetailsArePublic("",true,false))
    }

    @Test fun visibleMissionTerminologyUsesOfficialActionLabels() {
        assertTrue(MissionCards.all.filter { it.archetype==null }.all { it.displayCategory.contains("关键行动") })
        assertTrue(MissionCards.all.filter { it.archetype!=null }.all { it.displayCategory.contains("战术行动") })
        assertFalse(MissionCards.all.any { it.displayCategory.contains("目标") || it.displayCategory.contains("安全防护") })
    }
}
