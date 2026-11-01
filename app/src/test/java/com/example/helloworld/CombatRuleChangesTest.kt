package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule fixes checked against the v11.5 core rules (p14, p15, p28, p92-94). */
class CombatRuleChangesTest {
    private fun shooter(attacks: Int = 4, rules: ShootingWeaponRules = ShootingWeaponRules(), normals: Int = 0, crits: Int = 0) =
        ShootingAttacker("A", "s", "甲", "射手", "枪", 10, 10, attacks, 3, 3, 4, rules = rules,
            presetNormals = normals, presetCriticals = crits, presetNote = "埋伏")

    private fun target() = ShootingDefender("B", "t", "乙", "目标", 10, 10, 3)

    @Test fun `shooting presets are retained before the roll and leave fewer dice to roll`() {
        val start = ShootingCombatRules.start(shooter(normals = 1, crits = 1), target())
        assertEquals(2, start.attacker.rolledDiceCount)
        val rolled = ShootingCombatRules.acceptAttackRoll(start, listOf(1, 1))
        assertTrue(rolled.log.any { it.contains("不掷骰预留") && it.contains("埋伏") })
        val kept = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(rolled))
        assertEquals(1, kept.attackPool.normals)
        assertEquals(1, kept.attackPool.criticals)
    }

    @Test fun `defence presets come out of the dice still to roll`() {
        var s = ShootingCombatRules.acceptAttackRoll(ShootingCombatRules.start(shooter(attacks = 1), target()), listOf(4))
        s = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(s))
        s = ShootingCombatRules.addDefenceRetained(s, critical = true, note = "规则")
        assertEquals(2, s.defenceDiceToRoll)
        assertThrows(IllegalArgumentException::class.java) {
            ShootingCombatRules.addDefenceRetained(ShootingCombatRules.acceptDefenceRoll(s, listOf(2, 2)), critical = false)
        }
    }

    @Test fun `brutal shooting can only be blocked with critical saves`() {
        var s = ShootingCombatRules.start(shooter(attacks = 2, rules = ShootingRuleParser.parse(listOf("残暴"))), target())
        s = ShootingCombatRules.acceptAttackRoll(s, listOf(6, 4))
        s = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(s))
        s = ShootingCombatRules.finishDefenceRerolls(ShootingCombatRules.acceptDefenceRoll(s, listOf(6, 4, 4)))
        assertFalse(ShootingCombatRules.canAllocate(s, DefenceAllocation.NORMAL_TO_NORMAL))
        assertFalse(ShootingCombatRules.canAllocate(s, DefenceAllocation.TWO_NORMALS_TO_CRITICAL))
        assertTrue(ShootingCombatRules.canAllocate(s, DefenceAllocation.CRITICAL_TO_CRITICAL))
    }

    @Test fun `optional retain rules only apply when chosen`() {
        var s = ShootingCombatRules.start(shooter(attacks = 3, rules = ShootingWeaponRules(severe = true)), target())
        s = ShootingCombatRules.finishAttackRerolls(ShootingCombatRules.acceptAttackRoll(s, listOf(4, 4, 1)))
        assertEquals(2 to 0, ShootingCombatRules.previewAttackRetention(s, ShootingRetainChoices()))
        assertEquals(1 to 1, ShootingCombatRules.previewAttackRetention(s, ShootingRetainChoices(severe = true)))
        assertEquals(0, ShootingCombatRules.finishAttackRetention(s).attackPool.criticals)
    }

    @Test fun `melee presets reduce rolled dice and enter the pool`() {
        val a = MeleeFighter(MeleeSide.ATTACKER, "A", "a", "甲", "甲", "刀", 10, 10, 3, 3, 3, 4, presetCriticals = 1)
        val d = MeleeFighter(MeleeSide.DEFENDER, "B", "d", "乙", "乙", "刀", 10, 10, 3, 3, 3, 4)
        val start = MeleeCombatRules.start(a, d, MeleeSide.ATTACKER)
        assertEquals(2, start.attacker.rolledDiceCount)
        val rolled = MeleeCombatRules.acceptRolls(start, listOf(1, 1), listOf(1, 1, 1))
        assertEquals(1, MeleeCombatRules.previewRetention(rolled, MeleeSide.ATTACKER, MeleeRetainChoices()).criticals)
    }

    @Test fun `torrent skips targets already shot and blast also offers friendly operatives`() {
        val all = listOf(
            MultiTargetRules.Candidate("B", "b1", false),
            MultiTargetRules.Candidate("B", "b2", false),
            MultiTargetRules.Candidate("B", "b3", true),
            MultiTargetRules.Candidate("A", "shooter", false),
            MultiTargetRules.Candidate("A", "a2", false)
        )
        val torrent = MultiTargetRules.nextTargets(MultiTargetRules.Kind.TORRENT, all, "B", "shooter", setOf("b1"))
        assertEquals(listOf("b2"), torrent.map { it.memberId })
        val blast = MultiTargetRules.nextTargets(MultiTargetRules.Kind.BLAST, all, "B", "shooter", setOf("b1"))
        assertEquals(listOf("b2", "a2"), blast.map { it.memberId })
        assertEquals(MultiTargetRules.Kind.BLAST, MultiTargetRules.kind(listOf("爆炸 2\"")))
        assertEquals(MultiTargetRules.Kind.TORRENT, MultiTargetRules.kind(listOf("洪流 1\"")))
    }

    private fun firefight(): BattleRecord {
        val b = BattleRecord(
            name = "t", mission = "m", killzone = "k", keyOp = "o", dropZoneA = "北", dropZoneB = "南",
            sideA = BattleSide("A", "r", "小队 A", "IMP-AOD", "甲", commandPoints = 2,
                operatives = listOf(BattleOperativeState("m", "aod_captain", "队长", 15))),
            sideB = BattleSide("B", "r", "小队 B", "IMP-AOD", "乙",
                operatives = listOf(BattleOperativeState("m2", "aod_captain", "队长", 15)))
        )
        return b.copy(phase = BattlePhase.FIREFIGHT, turningPoint = 1)
    }

    @Test fun `firefight ploy spends cp once per turning point except command reroll`() {
        val b = firefight()
        val used = BattleFlow.firefightPloy(b, "A", "超人体格", 1)
        assertEquals(1, used.sideA.commandPoints)
        assertTrue(BattleFlow.firefightPloyUsed(used, "A", "超人体格"))
        assertTrue(used.log.last().message.contains("交战计谋 超人体格"))
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.firefightPloy(used, "A", "超人体格", 1) }
        val twice = BattleFlow.firefightPloy(BattleFlow.firefightPloy(b, "A", "指挥重掷", 1), "A", "指挥重掷", 1)
        assertEquals(0, twice.sideA.commandPoints)
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.firefightPloy(twice, "A", "指挥重掷", 1) }
    }

    @Test fun `charge after reposition is refused by core rules p14 and the rule exception is logged`() {
        val b = BattleFlow.activate(firefight(), "A", "m", 3, true)
        val moved = BattleFlow.action(b, "转移", 1, "")
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.action(moved, "冲锋", 1, "") }
        val charged = BattleFlow.action(moved, "冲锋", 1, "狂暴")
        assertTrue(charged.log.last().message.contains("依据：狂暴"))
        // Charging first and then fighting is the normal way to get into control range.
        val direct = BattleFlow.action(BattleFlow.action(b, "冲锋", 1, ""), "近战", 1, "")
        assertEquals(listOf("冲锋", "近战"), direct.progress.active?.actions)
    }
}
