package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FellgorCatalogTest {
    @Test fun fellgorIsAvailableWithAllReviewedOperativesAndProfiles() {
        val team=RosterCatalog.team("CHAOS-FELL")

        assertEquals("恶角兽掠夺者",team.name)
        assertEquals(11,team.operatives.size)
        assertEquals(25,team.operatives.sumOf { it.weapons.size })
        assertTrue(team.operatives.all { it.id in FellgorCatalog.abilities })
    }

    @Test fun correctedManglerProfileAndWeaponKindsArePreserved() {
        val mangler=RosterCatalog.operative(FellgorCatalog.team,"chaos_fell_mng")!!

        assertEquals(4,mangler.weapons.single().stats.hit)
        assertTrue(WeaponActions.isMelee("chaos_fell_mng_vc_0"))
        assertTrue(!WeaponActions.isMelee("chaos_fell_sha_tc_0"))
    }

    @Test fun everyFellgorOperativeExposesPersonalAbilitiesAlongsideFrenzy() {
        FellgorCatalog.team.operatives.forEach { op ->
            val personal = RuleOrganization.personalAbilities(op.id)
            assertTrue("Expected personal abilities for ${op.name}", personal.isNotEmpty())
            assertTrue(personal.none { it.factionRule })
            assertEquals("恶角兽掠夺者", op.keywords.firstOrNull())
        }
        val shamanPersonal = RuleOrganization.personalAbilities("chaos_fell_sha").map { it.name }
        assertEquals(listOf("暴怒再生", "黑暗之衣"), shamanPersonal)
    }
}
