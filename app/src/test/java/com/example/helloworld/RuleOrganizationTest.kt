package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class RuleOrganizationTest {
    @Test fun memberRulesNeverContainSharedFactionRules() {
        RosterCatalog.teams.flatMap { it.operatives }.forEach { operative ->
            assertTrue(operative.id, RuleOrganization.personalAbilities(operative.id).none { it.factionRule })
        }
    }
    @Test fun personalAbilitiesThatAffectTeamPloysArePreserved() {
        assertTrue(RuleOrganization.personalAbilities("aod_assault_sergeant").any { it.name == "条令战争" })
        assertTrue(RuleOrganization.personalAbilities("aod_captain").any { it.name == "英雄领袖" })
    }
    @Test fun memberAbilitiesAreNotMixedAcrossWarpcovenTypes() {
        val warrior = RuleOrganization.personalAbilities("wc_tzwar")
        assertTrue(warrior.any { it.name == "神器猎人" })
        assertFalse(warrior.any { it.name == "兽群号角" || it.name == "野性狂暴" })
    }
    @Test fun teamSupplementDoesNotMixInMemberOrPloyIndexes() {
        assertFalse("角色完整规则" in RuleOrganization.supplementaryTeamCategories)
        assertFalse("战略计谋" in RuleOrganization.supplementaryTeamCategories)
        assertEquals("交战计谋", RuleOrganization.label("交战计谋"))
    }
}
