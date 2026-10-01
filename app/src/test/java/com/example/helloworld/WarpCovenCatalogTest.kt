package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class WarpCovenCatalogTest {
    @Test fun completeCatalogAndLegalDefaultWizardWeapons() {
        val team = RosterCatalog.team("CHAOS-WC")
        assertEquals(10, team.operatives.size)
        assertEquals(32, team.operatives.sumOf { it.weapons.size })
        team.operatives.take(3).forEach { wizard ->
            assertEquals(15, wizard.wounds)
            assertTrue(wizard.defaultWeaponIds.any { it.endsWith("_fs_0") })
            assertTrue(wizard.defaultWeaponIds.any { it.endsWith("_ibp_0") })
            assertFalse(wizard.defaultWeaponIds.any { it.endsWith("_wfp_0") || it.endsWith("_pk_0") })
        }
    }

    @Test fun factionAbilitiesRespectOperativeTypes() {
        assertTrue(AbilityCatalog.forOperative("wc_sod").any { it.id == "wc_boons" })
        assertTrue(AbilityCatalog.forOperative("wc_war").any { it.id == "wc_automata" })
        WarpCovenCatalog.team.operatives.filter { it.id.startsWith("wc_tz") }.forEach {
            assertFalse(AbilityCatalog.forOperative(it.id).any { a -> a.id == "wc_astartes" })
        }
        assertNotNull(RuleGlossary.definitionFor("焚却理智"))
        assertNotNull(RuleGlossary.definitionFor("盾牌"))
    }
}
