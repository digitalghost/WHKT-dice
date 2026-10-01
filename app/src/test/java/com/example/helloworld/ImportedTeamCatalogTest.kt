package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class ImportedTeamCatalogTest {
    @Test fun preparedTeamsExposeEveryOperativeAndWeaponProfile() {
        val expected=mapOf("NEC-CAN" to (5 to 15), "IMP-WS" to (8 to 21), "IMP-CI" to (8 to 19))
        assertEquals(expected.keys,ImportedTeamCatalog.teams.map { it.id }.toSet())
        expected.forEach { (id,counts) ->
            val team=RosterCatalog.team(id)
            assertEquals(id,team.id)
            assertEquals(counts.first,team.operatives.size)
            assertEquals(counts.second,team.operatives.sumOf { it.weapons.size })
            assertEquals(4,ImportedTeamCatalog.equipment.getValue(id).size)
            assertTrue(ImportedTeamCatalog.equipment.getValue(id).all { it.currentText && it.rules.isNotBlank() && it.asset!=null })
            assertTrue(team.operatives.all { it.baseSizeMm!=null && it.keywords.isNotEmpty() })
        }
    }
    @Test fun currentStatsAndDistinctWeaponModesReachTheDicePicker() {
        val geomancer=RosterCatalog.team("NEC-CAN").operatives.first { it.id=="nec_can_geo" }
        assertEquals(14,geomancer.wounds)
        assertEquals(3,geomancer.weapons.size)
        assertEquals(listOf("射击","射击","交战"),geomancer.weapons.map { WeaponActions.label(it.id) })
        assertEquals(geomancer.weapons.map { it.id },geomancer.defaultWeaponIds)
        val quake=geomancer.weapons.first { it.name.contains("撼动") }
        assertEquals(5,quake.stats.attacks)
        assertEquals(1,quake.stats.normalDamage)
        assertEquals(2,quake.stats.criticalDamage)
        val crawler=RosterCatalog.team("NEC-CAN").operatives.first { it.id=="nec_can_tc" }
        assertEquals(18,crawler.wounds)
        val wolf=RosterCatalog.team("IMP-WS").operatives.first { it.id=="imp_ws_wlf" }
        assertTrue(wolf.weapons.all { WeaponActions.isMelee(it.id) })
    }
    @Test fun personalAbilitiesAndSpecialWeaponRulesKeepTheirOwnMeaning() {
        val shield=RuleGlossary.definitionFor("护盾")!!
        assertTrue(shield.description.contains("两枚"))
        assertFalse(shield.description.contains("豁免为 4+"))
        assertTrue(RuleGlossary.definitionFor("洪流 0\"")!!.description.contains("不能选择次要目标"))
        assertTrue(RuleGlossary.definitionFor("反灵能者")!!.description.contains("灵能者"))
        assertTrue(RuleGlossary.definitionFor("维度放逐")!!.description.contains("NEMESIS"))
        assertEquals(RuleGlossary.definitionFor("眩晕"),RuleGlossary.definitionFor("晕眩"))
        ImportedTeamCatalog.teams.flatMap { it.operatives }.forEach { operative ->
            val abilities=AbilityCatalog.forOperative(operative.id)
            assertTrue(operative.id,abilities.isNotEmpty())
            assertTrue(RuleOrganization.personalAbilities(operative.id).none { it.factionRule })
            assertTrue(operative.id,ImportedTeamCatalog.abilityCards.containsKey(operative.id))
        }
    }
}
