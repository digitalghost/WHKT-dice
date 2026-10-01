package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class WeaponRuleFocusTest {
    @Test fun matchesParameterizedKeywordsAndAliases() {
        assertTrue(WeaponRuleFocus.matches("致命 X+",listOf("致命 5+")))
        assertTrue(WeaponRuleFocus.matches("平衡／均衡",listOf("平衡")))
        assertTrue(WeaponRuleFocus.matches("晕眩",listOf("眩晕")))
        assertTrue(WeaponRuleFocus.matches("重型",listOf("重型（仅限冲刺）")))
    }
    @Test fun criticalPiercingHighlightsCombinedEntryWithoutMatchingOtherRules() {
        assertTrue(WeaponRuleFocus.matches("穿刺 X",listOf("关键穿刺 1")))
        assertFalse(WeaponRuleFocus.matches("致命 X+",listOf("关键穿刺 1")))
        assertFalse(WeaponRuleFocus.matches("穿刺 X",emptyList()))
        assertFalse(WeaponRuleFocus.matches("穿刺 X",listOf("未知规则")))
    }
    @Test fun factionKeywordsHaveDescriptionsAndUnknownsAreNotInvented() {
        assertNotNull(WeaponRuleFocus.definition("中毒"))
        assertNotNull(WeaponRuleFocus.definition("剧毒"))
        assertNull(WeaponRuleFocus.definition("未知规则"))
    }

    @Test fun everyCatalogWeaponKeywordHasAReadableRule() {
        val missing = RosterCatalog.teams
            .flatMap { team -> team.operatives.flatMap { operative -> operative.weapons } }
            .flatMap { weapon -> weapon.stats.keywords.map { keyword -> "${weapon.name}：$keyword" } }
            .filter { entry -> WeaponRuleFocus.definition(entry.substringAfter('：')) == null }
            .distinct()
        assertTrue("缺少武器关键字规则：${missing.joinToString()}",missing.isEmpty())
    }
}
