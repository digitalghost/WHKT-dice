package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class NotebookTest {
    @Test fun sameRosterCanBeUsedByBothPlayersWithoutSharingBattleState() {
        val team=RosterCatalog.teams.first()
        val template=team.operatives.first()
        val roster=SavedRoster("shared",team.id,"镜像队", mutableListOf(RosterMember("same-member",template.id,"",template.weapons.map { it.id })))
        val battle=BattleRules.create(BattleSetup("镜像对局","","","占领","","","A","B","弹药箱","梯子","主宰","主宰",roster,roster,ScoreType.KEY,ScoreType.KILL))
        assertEquals(battle.sideA.rosterId,battle.sideB.rosterId)
        assertEquals(battle.sideA.operatives,battle.sideB.operatives)
        val changed=battle.copy(sideA=battle.sideA.copy(operatives=battle.sideA.operatives.map { it.copy(currentWounds=1,incapacitated=true) },commandPoints=0,score=BattleScore(key=2)))
        assertEquals(template.wounds,changed.sideB.operatives.first().currentWounds)
        assertFalse(changed.sideB.operatives.first().incapacitated)
        assertEquals(2,changed.sideB.commandPoints)
        assertEquals(0,changed.sideB.score.key)
        assertEquals("梯子",changed.sideB.equipmentNotes)
    }
    @Test fun newBattleRequiresAKeyMission() {
        val team=RosterCatalog.teams.first()
        val roster=SavedRoster("test",team.id,"测试队",mutableListOf())
        val setup=BattleSetup("测试","","","占领","","","A","B","","","主宰","主宰",roster,roster,ScoreType.KEY,ScoreType.KILL)
        listOf("", "   ").forEach { missing ->
            try { BattleRules.create(setup.copy(keyOp=missing));fail("空关键任务不应开局") }
        catch(expected: IllegalArgumentException) { assertEquals("开局前必须选择关键行动",expected.message) }
        }
        assertEquals("占领",BattleRules.create(setup).keyOp)
    }
    @Test fun setupPersistsBothPlayersPrimaryActions() {
        val team=RosterCatalog.teams.first()
        val roster=SavedRoster("test",team.id,"测试队",mutableListOf())
        val setup=BattleSetup(
            "测试","","","占领","","","A","B","","","主宰","侧翼",roster,roster,
            ScoreType.KEY,ScoreType.TACTICAL
        )

        val battle=BattleRules.create(setup)

        assertEquals(ScoreType.KEY,battle.sideA.primaryOp)
        assertEquals(ScoreType.TACTICAL,battle.sideB.primaryOp)
    }
    @Test fun referenceCardsHaveCompleteDetailsAndDistinctNames() {
        assertEquals(21,MissionCards.all.size)
        assertEquals(9,MissionCards.all.count { it.category.contains("关键") })
        assertEquals(12,MissionCards.all.count { it.category.contains("战术") })
        assertEquals(21,MissionCards.all.map { it.name }.toSet().size)
        assertTrue(MissionCards.all.all { it.rule.isNotBlank() && it.scoring.isNotBlank() })
        assertEquals(10,EquipmentCards.universal.size)
        assertEquals(10,EquipmentCards.universal.map { it.name }.toSet().size)
        assertTrue(EquipmentCards.universal.all { it.rules.isNotBlank() && it.count.isNotBlank() })
    }
}
