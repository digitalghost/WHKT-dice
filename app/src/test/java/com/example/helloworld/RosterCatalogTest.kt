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
        assertEquals(48, RosterCatalog.teams.size)
        assertTrue(RosterCatalog.teams.sumOf { it.operatives.size } >= 450)
        assertTrue(RosterCatalog.teams.all { it.operatives.isNotEmpty() })
        RosterCatalog.teams.forEach { team ->
            assertTrue("Blank composition hint for ${team.id}", RosterComposition.hint(team.id).isNotBlank())
            assertTrue("Missing tactical archetypes for ${team.id}", TeamArchetypes.forTeam(team.id).size >= 2)
        }
    }

    @Test
    fun everyWeaponProfileParsesIntoUsableDiceSettings() {
        RosterCatalog.teams.flatMap { it.operatives }.flatMap { it.weapons }.forEach { weapon ->
            val stats = weapon.stats
            assertTrue("Invalid attack count for ${weapon.name}", stats.attacks in 1..20)
            assertTrue("Invalid hit value for ${weapon.name}", stats.hit in 2..6)
            assertTrue("Invalid critical value for ${weapon.name}", stats.criticalThreshold in 2..6)
            assertTrue("Invalid damage for ${weapon.name}", stats.normalDamage >= 0)
            assertTrue("Invalid critical damage for ${weapon.name}", stats.criticalDamage >= 0)
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
        assertEquals("毁灭", RuleGlossary.definitionFor("1\" 毁灭 1")?.name)
        assertEquals("范围", RuleGlossary.definitionFor("距离 8\"")?.name)
        assertEquals(1, KeywordValues.max(KeywordValues.normalize(listOf("1\" 毁灭 1")), "毁灭"))
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

    @Test
    fun multiCardOperativesExposeAllCardSourcesWithoutDuplicates() {
        val geo = RosterCatalog.operative(RosterCatalog.team("NEC-CAN"), "nec_can_geo")!!
        assertEquals(listOf("数据卡", "能力卡 1", "能力卡 2"), geo.cardSources().map { it.label })

        val felltalon = RosterCatalog.operative(RosterCatalog.team("TYR-RAV"), "rav_felltalon")!!
        assertEquals(listOf("数据卡", "能力卡"), felltalon.cardSources().map { it.label })

        val shaman = RosterCatalog.operative(RosterCatalog.team("CHAOS-FELL"), "chaos_fell_sha")!!
        assertEquals(listOf("数据卡", "能力卡"), shaman.cardSources().map { it.label })

        // Statless operative whose cards in JSON are all ability cards (e.g. 海军 C.A.T.单位)
        val catUnit = OperativeTemplate(
            id = "cat_test",
            name = "海军 C.A.T.单位",
            role = "侦测支援设备",
            apl = 0,
            move = "6\"",
            save = "4+",
            wounds = 5,
            cardRes = R.drawable.kt_battlefield,
            weapons = emptyList(),
            defaultWeaponIds = emptyList(),
            cardAsset = "team_rules/KT-IMPERIAL_NAVY_BREACHERS--01-05-特工能力-海军-能力卡1.png",
            abilityAssets = listOf(
                "team_rules/KT-IMPERIAL_NAVY_BREACHERS--01-05-特工能力-海军-能力卡1.png",
                "team_rules/KT-IMPERIAL_NAVY_BREACHERS--01-06-特工能力-海军-能力卡2.png"
            )
        )
        assertEquals(listOf("能力卡 1", "能力卡 2"), catUnit.cardSources().map { it.label })
    }

    @Test
    fun allBuiltInOperativesHaveBaseSizeKeywordsAndValidCardAssets() {
        val assetsRoot = java.io.File("src/main/assets")
        RosterCatalog.teams.forEach { team ->
            team.operatives.forEach { op ->
                assertTrue("Missing baseSizeMm for ${op.id}", (op.baseSizeMm ?: 0) > 0)
                assertTrue("Missing keywords for ${op.id}", op.keywords.isNotEmpty())
                val sources = op.cardSources()
                assertTrue("Empty cardSources for ${op.id}", sources.isNotEmpty())
                sources.forEach { src ->
                    if (src.asset != null) {
                        assertEquals("Asset card must not set resource fallback that shadows ZoomReference", null, src.resource)
                        val png = java.io.File(assetsRoot, src.asset)
                        val webp = java.io.File(assetsRoot, src.asset.removeSuffix(".png") + ".webp")
                        assertTrue("Missing card asset ${src.asset} for ${op.id}", png.exists() || webp.exists())
                    } else {
                        assertTrue("Invalid card resource for ${op.id}", (src.resource ?: 0) != 0)
                    }
                }
            }
        }
        val assaultSgt = RosterCatalog.operative(RosterCatalog.team("IMP-AOD"), "aod_assault_sergeant")!!
        assertTrue(assaultSgt.weapons.any { it.name == "等离子手枪（标准）" })
        assertTrue(assaultSgt.weapons.any { it.name == "等离子手枪（过载）" })
        assertEquals(listOf("数据卡", "能力卡"), assaultSgt.cardSources().map { it.label })

        val sniper = RosterCatalog.operative(RosterCatalog.team("IMP-AOD"), "aod_sniper")!!
        assertEquals(
            listOf("爆矢手枪", "爆矢狙击步枪（处刑者）", "爆矢狙击步枪（超级破片）", "爆矢狙击步枪（致死）", "双拳"),
            sniper.weapons.map { it.name }
        )
        assertEquals(listOf("数据卡", "能力卡"), sniper.cardSources().map { it.label })
    }

    @Test
    fun allJsonTeamsAndRulesHaveCleanFilesAndValidMultiCardSources() {
        val assetsRoot = java.io.File("src/main/assets")
        val teamFiles = java.io.File(assetsRoot, "teams").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        val ruleFiles = java.io.File(assetsRoot, "rules").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertEquals(48, teamFiles.size)
        assertEquals(48, ruleFiles.size)

        val assetFiles = java.io.File(assetsRoot, "team_rules").listFiles().orEmpty()
        assetFiles.forEach { f ->
            assertTrue("Control character in asset filename ${f.name}", f.name.none { it.code < 32 })
        }

        val cardsBlockRegex = Regex(""""id"\s*:\s*"([^"]+)"[\s\S]*?"cards"\s*:\s*\[([^\]]*)]""")
        val quotedStringRegex = Regex(""""([^"]+)"""")

        teamFiles.forEach { file ->
            val text = file.readText()
            assertTrue("Control character found in ${file.name}", text.none { it.code < 32 && it != '\n' && it != '\r' && it != '\t' })
            val matches = cardsBlockRegex.findAll(text).toList()
            assertTrue("No operatives with cards found in ${file.name}", matches.isNotEmpty())
            matches.forEach { m ->
                val opId = m.groupValues[1]
                val cards = quotedStringRegex.findAll(m.groupValues[2]).map { "team_rules/${it.groupValues[1]}" }.toList()
                assertTrue("Empty cards list for $opId in ${file.name}", cards.isNotEmpty())
                val op = OperativeTemplate(
                    id = opId,
                    name = opId,
                    role = "",
                    apl = 2,
                    move = "6\"",
                    save = "4+",
                    wounds = 8,
                    cardRes = R.drawable.ic_card,
                    weapons = emptyList(),
                    defaultWeaponIds = emptyList(),
                    cardAsset = cards.firstOrNull { it.contains("数据卡") } ?: cards.firstOrNull(),
                    abilityAssets = cards.filter { it.contains("能力卡") }
                )
                val sources = op.cardSources()
                assertEquals("All cards in JSON must be exposed by cardSources() for $opId", cards.distinct().size, sources.size)
                assertTrue("Missing primaryCardAsset for $opId", !op.primaryCardAsset.isNullOrBlank())
                sources.forEach { src ->
                    assertTrue("JSON operative card must have asset for $opId", !src.asset.isNullOrBlank())
                    assertEquals("JSON operative card must have null resource so asset is not shadowed", null, src.resource)
                    val png = java.io.File(assetsRoot, src.asset!!)
                    val webp = java.io.File(assetsRoot, src.asset!!.removeSuffix(".png") + ".webp")
                    assertTrue("Missing asset file ${src.asset} for $opId", png.exists() || webp.exists())
                }
                if (opId == "kt_inb_cat_unit") {
                    assertEquals(listOf("能力卡 1", "能力卡 2"), sources.map { it.label })
                }
            }
        }

        ruleFiles.forEach { file ->
            val text = file.readText()
            assertTrue("Control character found in rules/${file.name}", text.none { it.code < 32 && it != '\n' && it != '\r' && it != '\t' })
        }
    }
}
