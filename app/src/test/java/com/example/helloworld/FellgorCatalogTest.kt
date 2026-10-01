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
}
