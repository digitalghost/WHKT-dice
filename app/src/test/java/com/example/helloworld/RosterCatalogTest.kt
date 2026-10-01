package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterCatalogTest {
    @Test
    fun catalogIdsAreUniqueAndDefaultsExist() {
        assertEquals(RosterCatalog.teams.size, RosterCatalog.teams.map { it.id }.distinct().size)

        RosterCatalog.teams.forEach { team ->
            assertEquals(
                "Duplicate operative id in ${team.id}",
                team.operatives.size,
                team.operatives.map { it.id }.distinct().size
            )
            team.operatives.forEach { operative ->
                val weaponIds = operative.weapons.map { it.id }
                assertEquals(
                    "Duplicate weapon id for ${operative.id}",
                    weaponIds.size,
                    weaponIds.distinct().size
                )
                assertTrue(
                    "Unknown default weapon for ${operative.id}",
                    operative.defaultWeaponIds.all { it in weaponIds }
                )
            }
        }
    }

    @Test
    fun firstReleaseContainsAllPreparedOperativeCards() {
        assertEquals(8, RosterCatalog.teams.size)
        assertEquals(64, RosterCatalog.teams.sumOf { it.operatives.size })
        assertTrue(RosterCatalog.teams.all { it.operatives.isNotEmpty() })
    }

    @Test
    fun everyWeaponProfileParsesIntoUsableDiceSettings() {
        RosterCatalog.teams.flatMap { it.operatives }.flatMap { it.weapons }.forEach { weapon ->
            val stats = weapon.stats
            assertTrue("Invalid attack count for ${weapon.name}", stats.attacks in 1..20)
            assertTrue("Invalid hit value for ${weapon.name}", stats.hit in 2..6)
            assertTrue("Invalid critical value for ${weapon.name}", stats.criticalThreshold in 2..6)
            assertTrue("Invalid damage for ${weapon.name}", stats.normalDamage > 0)
            assertTrue("Invalid critical damage for ${weapon.name}", stats.criticalDamage > 0)
        }
    }

    @Test
    fun lethalThresholdIsIndependentFromTheNormalHitValue() {
        val stats = WeaponProfileParser.parse("4A · 6+ · 3/5 · 致命 5+")

        assertEquals(6, stats.hit)
        assertEquals(5, stats.criticalThreshold)
    }

    @Test
    fun everyWeaponKeywordHasQuickReferenceText() {
        RosterCatalog.teams.flatMap { it.operatives }.flatMap { it.weapons }
            .flatMap { it.stats.keywords }
            .forEach { keyword ->
                assertTrue("Missing glossary entry for $keyword", RuleGlossary.definitionFor(keyword) != null)
            }
    }

    @Test
    fun everyOperativeHasAnAbilityReference() {
        RosterCatalog.teams.flatMap { it.operatives }.forEach { operative ->
            assertTrue(
                "Missing ability reference for ${operative.name}",
                AbilityCatalog.forOperative(operative.id).isNotEmpty()
            )
        }
    }
}
