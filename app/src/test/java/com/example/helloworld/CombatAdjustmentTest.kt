package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Generic adjustment hooks used for team rules, plus keyword merging (p92). */
class CombatAdjustmentTest {
    private fun shooter(attacks: Int = 3, rules: ShootingWeaponRules = ShootingWeaponRules()) = ShootingAttacker(
        "A", "a", "甲", "射手", "步枪", 10, 10, attacks, 3, 3, 5, rules = rules
    )

    private fun target(save: Int = 3) = ShootingDefender("B", "b", "乙", "目标", 10, 10, save)

    private fun rolled(values: List<Int>, rules: ShootingWeaponRules = ShootingWeaponRules()): ShootingCombatState =
        ShootingCombatRules.acceptAttackRoll(ShootingCombatRules.start(shooter(values.size, rules), target()), values)

    @Test fun `several accurate keywords may count as accurate 2 and lethal uses the best value`() {
        assertEquals(2, ShootingRuleParser.parse(listOf("精准 1", "精准 1")).accurate)
        assertEquals(1, ShootingRuleParser.parse(listOf("精准 1")).accurate)
        assertEquals(5, ShootingRuleParser.parse(listOf("致命 5+")).lethalThreshold)
        assertEquals(4, MeleeRuleParser.parse(listOf("致命 5+"), criticalThreshold = 4).lethalThreshold)
        assertEquals(2, ShootingRuleParser.parse(listOf("穿刺 1", "穿刺 2")).piercing)
    }

    @Test fun `command reroll is chosen after seeing dice, costs 1CP and each die rerolls once`() {
        var s = rolled(listOf(1, 2, 6))
        assertTrue(ShootingCombatRules.canRerollAttackWith(s, 0, RerollSource.COMMAND))
        assertFalse(ShootingCombatRules.canRerollAttackWith(s, 0, RerollSource.WEAPON))
        s = ShootingCombatRules.rerollAttackWith(s, 0, 5, RerollSource.COMMAND)
        assertEquals(1, s.attackCpSpent)
        assertFalse(ShootingCombatRules.canRerollAttackWith(s, 0, RerollSource.OTHER))
        assertTrue(s.log.last().contains("指挥重掷"))
        assertThrows(IllegalArgumentException::class.java) {
            ShootingCombatRules.rerollAttackWith(s, 0, 6, RerollSource.OTHER)
        }
    }

    @Test fun `weapon reroll still follows balanced`() {
        var s = rolled(listOf(1, 2), ShootingWeaponRules(rerollPolicy = ShootingRerollPolicy.BALANCED))
        s = ShootingCombatRules.rerollAttackWith(s, 0, 4, RerollSource.WEAPON)
        assertFalse(ShootingCombatRules.canRerollAttackWith(s, 1, RerollSource.WEAPON))
        assertTrue(ShootingCombatRules.canRerollAttackWith(s, 1, RerollSource.OTHER))
    }

    @Test fun `manual reclassification keeps the face value and the reroll mark`() {
        var s = rolled(listOf(4, 2))
        s = ShootingCombatRules.rerollAttackWith(s, 1, 3, RerollSource.OTHER)
        s = ShootingCombatRules.editAttackDie(s, 1, DieOverride.CRITICAL, note = "埋伏")
        val die = s.attackPool.dice[1]
        assertEquals(3, die.value)
        assertTrue(die.rerolled)
        assertEquals(DieReading.CRITICAL, ShootingCombatRules.readAttackDie(s, die))
        s = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(s))
        assertEquals(1, s.attackPool.criticals)
        assertEquals(1, s.attackPool.normals)
    }

    @Test fun `retained defence success and extra defence dice are supported`() {
        var s = rolled(listOf(4))
        s = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(s))
        assertEquals(3, s.defenceDiceToRoll)
        var more = ShootingCombatRules.configureEnvironment(ShootingCombatRules.start(shooter(1), target()), smoke = false, baseDefenceDice = 4)
        more = ShootingCombatRules.acceptAttackRoll(more, listOf(4))
        more = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(more))
        assertEquals(4, more.defenceDiceToRoll)
        s = ShootingCombatRules.acceptDefenceRoll(s, listOf(1, 1, 1))
        s = ShootingCombatRules.addDefenceRetained(s, critical = false, note = "变色孢子迷彩")
        s = ShootingCombatRules.finishDefenceRerolls(s)
        assertEquals(1, s.defencePool.normals)
    }

    @Test fun `smoke reduces piercing 2 to piercing 1`() {
        var s = ShootingCombatRules.start(shooter(1, ShootingWeaponRules(piercing = 2)), target())
        s = ShootingCombatRules.configureEnvironment(s, smoke = true, baseDefenceDice = 3)
        s = ShootingCombatRules.acceptAttackRoll(s, listOf(4))
        s = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(s))
        assertEquals(1, s.effectivePiercing)
        assertEquals(2, s.defenceDiceTaken)
    }

    @Test fun `recommended allocation is the lowest damage plan`() {
        var s = rolled(listOf(6, 4))
        s = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(s))
        s = ShootingCombatRules.finishDefenceRerolls(ShootingCombatRules.acceptDefenceRoll(s, listOf(6, 2, 2)))
        val plan = ShootingCombatRules.recommendedAllocations(s)
        assertEquals(listOf(DefenceAllocation.CRITICAL_TO_CRITICAL), plan)
        assertEquals(ShootingPhase.ALLOCATE, s.phase)
    }

    @Test fun `damage gate allows per hit changes before wounds are lost`() {
        var s = rolled(listOf(6, 4))
        s = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(s))
        s = ShootingCombatRules.finishDefenceRerolls(ShootingCombatRules.acceptDefenceRoll(s, listOf(1, 1, 1)))
        s = ShootingCombatRules.beginDamage(s)
        assertEquals(ShootingPhase.DAMAGE, s.phase)
        assertEquals(listOf(5, 3), s.pendingHits.map { it.damage })
        s = ShootingCombatRules.adjustHit(s, 0, 4, "恶心韧性")
        s = ShootingCombatRules.applyDamage(s)
        assertEquals(3, s.defender.currentWounds)
        assertEquals(7, s.weaponDamage)
    }

    @Test fun `allocation can be ended early only on purpose`() {
        var s = rolled(listOf(4))
        s = ShootingCombatRules.finishAttackRetention(ShootingCombatRules.finishAttackRerolls(s))
        s = ShootingCombatRules.finishDefenceRerolls(ShootingCombatRules.acceptDefenceRoll(s, listOf(4, 1, 1)))
        assertThrows(IllegalArgumentException::class.java) { ShootingCombatRules.beginDamage(s) }
        assertEquals(ShootingPhase.DAMAGE, ShootingCombatRules.beginDamage(s, force = true).phase)
    }

    private fun fighter(side: MeleeSide, wounds: Int = 10) = MeleeFighter(
        side, if (side == MeleeSide.ATTACKER) "A" else "B", side.name, "p", side.name, "刀", 10, wounds, 3, 3, 3, 5
    )

    private fun meleeRolled(a: List<Int>, d: List<Int>) = MeleeCombatRules.acceptRolls(
        MeleeCombatRules.start(fighter(MeleeSide.ATTACKER), fighter(MeleeSide.DEFENDER), MeleeSide.ATTACKER), a, d
    )

    @Test fun `melee on-demand reroll alternates and edits apply to either side`() {
        var s = meleeRolled(listOf(1, 4, 4), listOf(2, 4, 4))
        s = MeleeCombatRules.rerollWith(s, MeleeSide.ATTACKER, 0, 6, RerollSource.COMMAND)
        assertEquals(1, s.attackerCpSpent)
        assertEquals(MeleeSide.DEFENDER, s.rerollTurn)
        assertFalse(MeleeCombatRules.canRerollWith(s, MeleeSide.ATTACKER, 1, RerollSource.OTHER))
        s = MeleeCombatRules.editDie(s, MeleeSide.ATTACKER, 1, DieOverride.FAIL, note = "腐烂诅咒")
        assertEquals(DieReading.FAIL, MeleeCombatRules.readDie(s, MeleeSide.ATTACKER, s.attackerPool.dice[1]))
    }

    @Test fun `melee discard, turn switch and wound adjustment`() {
        var s = meleeRolled(listOf(4, 4, 1), listOf(4, 6, 1))
        while (s.phase == MeleePhase.REROLL) s = MeleeCombatRules.passReroll(s, s.rerollTurn)
        s = MeleeCombatRules.finishRetention(s)
        assertEquals(MeleeSide.ATTACKER, s.resolveTurn)
        s = MeleeCombatRules.switchTurn(s, "地下恐惧")
        assertEquals(MeleeSide.DEFENDER, s.resolveTurn)
        s = MeleeCombatRules.discardSuccess(s, MeleeSide.ATTACKER, MeleeSuccess.NORMAL, "盾牌")
        assertEquals(1, s.attackerPool.normals)
        s = MeleeCombatRules.adjustWounds(s, MeleeSide.ATTACKER, -10)
        assertEquals(MeleePhase.FINISHED, s.phase)
    }
}
