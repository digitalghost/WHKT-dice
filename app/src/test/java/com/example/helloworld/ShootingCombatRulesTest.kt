package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShootingCombatRulesTest {
    private fun attacker(
        wounds: Int = 10,
        attacks: Int = 3,
        hit: Int = 3,
        normalDamage: Int = 3,
        criticalDamage: Int = 5,
        rules: ShootingWeaponRules = ShootingWeaponRules()
    ) = ShootingAttacker(
        battleSideId = "A",
        memberId = "attacker",
        playerName = "甲",
        operativeName = "射手",
        weaponName = "步枪",
        maxWounds = 10,
        currentWounds = wounds,
        attacks = attacks,
        baseHit = hit,
        normalDamage = normalDamage,
        criticalDamage = criticalDamage,
        rules = rules
    )

    private fun defender(wounds: Int = 10, save: Int = 3) = ShootingDefender(
        battleSideId = "B",
        memberId = "defender",
        playerName = "乙",
        operativeName = "目标",
        maxWounds = 10,
        currentWounds = wounds,
        save = save
    )

    private fun retained(
        attacker: ShootingAttacker = attacker(),
        attackValues: List<Int> = listOf(6, 4, 3),
        cover: CoverChoice = CoverChoice.NONE,
        obscured: Boolean = false
    ): ShootingCombatState {
        var state = ShootingCombatRules.start(attacker, defender())
        state = ShootingCombatRules.configure(state, cover, obscured, accurateRetained = 0)
        state = ShootingCombatRules.acceptAttackRoll(state, attackValues)
        if (state.phase == ShootingPhase.ATTACK_REROLL) {
            state = ShootingCombatRules.finishAttackRerolls(state)
        }
        return ShootingCombatRules.finishAttackRetention(state)
    }

    private fun allocatedReady(): ShootingCombatState {
        var state = retained()
        state = ShootingCombatRules.acceptDefenceRoll(state, listOf(6, 4, 2))
        return ShootingCombatRules.finishDefenceRerolls(state)
    }

    @Test
    fun `wounded attacker has worse hit`() {
        val subject = attacker(wounds = 4, hit = 3)
        assertTrue(subject.wounded)
        assertEquals(4, subject.effectiveHit)
    }

    @Test
    fun `defender chooses allocation instead of automatic optimum`() {
        var protectCritical = allocatedReady()
        protectCritical = ShootingCombatRules.allocate(
            protectCritical,
            DefenceAllocation.CRITICAL_TO_CRITICAL
        )
        protectCritical = ShootingCombatRules.allocate(
            protectCritical,
            DefenceAllocation.NORMAL_TO_NORMAL
        )
        protectCritical = ShootingCombatRules.finishAllocation(protectCritical)

        var protectNormals = allocatedReady()
        protectNormals = ShootingCombatRules.allocate(
            protectNormals,
            DefenceAllocation.CRITICAL_TO_NORMAL
        )
        protectNormals = ShootingCombatRules.allocate(
            protectNormals,
            DefenceAllocation.NORMAL_TO_NORMAL
        )
        protectNormals = ShootingCombatRules.finishAllocation(protectNormals)

        assertEquals(3, protectCritical.weaponDamage)
        assertEquals(5, protectNormals.weaponDamage)
    }

    @Test
    fun `two normal saves block one critical`() {
        var state = retained(
            attacker = attacker(attacks = 1),
            attackValues = listOf(6)
        )
        state = ShootingCombatRules.acceptDefenceRoll(state, listOf(4, 3, 1))
        state = ShootingCombatRules.finishDefenceRerolls(state)
        assertTrue(
            ShootingCombatRules.canAllocate(
                state,
                DefenceAllocation.TWO_NORMALS_TO_CRITICAL
            )
        )
        state = ShootingCombatRules.allocate(state, DefenceAllocation.TWO_NORMALS_TO_CRITICAL)
        state = ShootingCombatRules.finishAllocation(state)
        assertEquals(0, state.weaponDamage)
    }

    @Test
    fun `piercing reduces pool before cover die is retained`() {
        val state = retained(
            attacker = attacker(
                attacks = 1,
                rules = ShootingWeaponRules(piercing = 1)
            ),
            attackValues = listOf(4),
            cover = CoverChoice.STANDARD
        )
        assertEquals(2, state.defenceDiceTaken)
        assertEquals(1, state.defencePool.retainedNormals)
        assertEquals(1, state.defenceDiceToRoll)
    }

    @Test
    fun `improved cover may retain two normal saves`() {
        val state = retained(
            attacker = attacker(attacks = 1),
            attackValues = listOf(4),
            cover = CoverChoice.IMPROVED_NORMALS
        )
        assertEquals(2, state.defencePool.retainedNormals)
        assertEquals(1, state.defenceDiceToRoll)
    }

    @Test
    fun `saturate disables cover`() {
        val state = retained(
            attacker = attacker(
                attacks = 1,
                rules = ShootingWeaponRules(saturate = true)
            ),
            attackValues = listOf(4),
            cover = CoverChoice.IMPROVED_CRITICAL
        )
        assertEquals(CoverChoice.NONE, state.coverChoice)
        assertEquals(0, state.defencePool.retainedCriticals)
        assertEquals(3, state.defenceDiceToRoll)
    }

    @Test
    fun `obscured discards a success and downgrades remaining criticals`() {
        val state = retained(
            attacker = attacker(
                rules = ShootingWeaponRules(
                    devastating = 2,
                    piercingCrits = 1
                )
            ),
            attackValues = listOf(6, 6, 4),
            obscured = true
        )
        assertEquals(0, state.attackPool.criticals)
        assertEquals(2, state.attackPool.normals)
        assertEquals(0, state.devastatingDamage)
        assertEquals(0, state.effectivePiercing)
    }

    @Test
    fun `shooting ends before defence roll when no attack succeeds`() {
        val state = retained(
            attacker = attacker(attacks = 2),
            attackValues = listOf(1, 2)
        )
        assertEquals(ShootingPhase.FINISHED, state.phase)
        assertEquals(10, state.defender.currentWounds)
        assertEquals(0, state.weaponDamage)
    }

    @Test
    fun `weapon parser keeps shooting rules separate`() {
        val rules = ShootingRuleParser.parse(
            listOf("精准 1", "关键穿刺 2", "毁灭 3", "集中", "平衡"),
            criticalThreshold = 5
        )
        assertEquals(5, rules.lethalThreshold)
        assertEquals(1, rules.accurate)
        assertEquals(0, rules.piercing)
        assertEquals(2, rules.piercingCrits)
        assertEquals(3, rules.devastating)
        assertTrue(rules.saturate)
        assertEquals(ShootingRerollPolicy.BALANCED, rules.rerollPolicy)
    }

    @Test
    fun `defence die cannot be rerolled twice`() {
        var state = retained(attacker = attacker(attacks = 1), attackValues = listOf(4))
        state = state.copy(defenceExtraRerolls = 2)
        state = ShootingCombatRules.acceptDefenceRoll(state, listOf(1, 2, 3))
        state = ShootingCombatRules.rerollDefence(state, 0, 6)
        assertFalse(ShootingCombatRules.canRerollDefence(state, 0))
        assertTrue(ShootingCombatRules.canRerollDefence(state, 1))
    }

    @Test
    fun `defence cannot reroll without an explicit budget`() {
        var state = retained(attacker = attacker(attacks = 1), attackValues = listOf(4))
        state = ShootingCombatRules.acceptDefenceRoll(state, listOf(1, 2, 3))
        assertFalse(ShootingCombatRules.canRerollDefence(state, 0))
    }

    @Test
    fun `improved normal cover falls back to one retained die after piercing two`() {
        val state = retained(
            attacker = attacker(attacks = 1, rules = ShootingWeaponRules(piercing = 2)),
            attackValues = listOf(4),
            cover = CoverChoice.IMPROVED_NORMALS
        )
        assertEquals(1, state.defenceDiceTaken)
        assertEquals(1, state.defencePool.retainedNormals)
        assertEquals(0, state.defenceDiceToRoll)
    }

    @Test
    fun `accurate retained normal cannot be rended but severe may change it`() {
        val subject = attacker(
            attacks = 2,
            rules = ShootingWeaponRules(accurate = 1, rending = true, severe = true)
        ).copy(accurateRetained = 1)
        var state = ShootingCombatRules.start(subject, defender())
        state = ShootingCombatRules.acceptAttackRoll(state, listOf(6))
        state = ShootingCombatRules.finishAttackRerolls(state)
        state = ShootingCombatRules.finishAttackRetention(
            state,
            ShootingRetainChoices(rending = true)
        )
        assertEquals(1, state.attackPool.criticals)
        assertEquals(1, state.attackPool.normals)

        state = ShootingCombatRules.start(subject, defender())
        state = ShootingCombatRules.acceptAttackRoll(state, listOf(2))
        state = ShootingCombatRules.finishAttackRerolls(state)
        state = ShootingCombatRules.finishAttackRetention(
            state,
            ShootingRetainChoices(severe = true)
        )
        assertEquals(1, state.attackPool.criticals)
        assertEquals(0, state.attackPool.normals)
    }

    @Test
    fun `severe critical triggers devastating and critical piercing but not punishing or rending`() {
        val rules = ShootingWeaponRules(
            severe = true,
            punishing = true,
            rending = true,
            devastating = 2,
            piercingCrits = 2
        )
        var state = ShootingCombatRules.start(attacker(attacks = 3, rules = rules), defender())
        state = ShootingCombatRules.acceptAttackRoll(state, listOf(4, 2, 1))
        state = ShootingCombatRules.finishAttackRerolls(state)
        state = ShootingCombatRules.finishAttackRetention(
            state,
            ShootingRetainChoices(punishing = true, rending = true, severe = true)
        )
        assertEquals(1, state.attackPool.criticals)
        assertEquals(0, state.attackPool.normals)
        assertEquals(2, state.devastatingDamage)
        assertEquals(2, state.effectivePiercing)
    }

    @Test
    fun `piercing and critical piercing use the higher value instead of stacking`() {
        val state = retained(
            attacker = attacker(
                attacks = 1,
                rules = ShootingWeaponRules(piercing = 1, piercingCrits = 2)
            ),
            attackValues = listOf(6)
        )
        assertEquals(2, state.effectivePiercing)
        assertEquals(1, state.defenceDiceTaken)
    }

    @Test
    fun `temporary attack reroll is consumed without consuming balanced`() {
        val subject = attacker(
            attacks = 2,
            rules = ShootingWeaponRules(rerollPolicy = ShootingRerollPolicy.BALANCED)
        )
        var state = ShootingCombatRules.start(subject, defender())
        state = ShootingCombatRules.configure(
            state,
            CoverChoice.NONE,
            obscured = false,
            accurateRetained = 0,
            attackExtraRerolls = 1
        )
        state = ShootingCombatRules.acceptAttackRoll(state, listOf(2, 3))
        state = ShootingCombatRules.rerollAttack(state, 0, 4)
        assertTrue(state.attackPool.balancedUsed)
        assertEquals(0, state.attackPool.extraRerollsUsed)
        state = ShootingCombatRules.rerollAttack(state, 1, 5)
        assertEquals(1, state.attackPool.extraRerollsUsed)
        assertEquals(ShootingPhase.ATTACK_REROLL, state.phase)
        state = ShootingCombatRules.finishAttackRerolls(state)
        assertEquals(ShootingPhase.ATTACK_RETAIN, state.phase)
    }

    @Test
    fun `temporary attack reroll can be granted after seeing dice`() {
        var state = ShootingCombatRules.start(attacker(attacks = 2), defender())
        state = ShootingCombatRules.configure(state, CoverChoice.NONE, false, 0)
        state = ShootingCombatRules.acceptAttackRoll(state, listOf(1, 2))

        assertEquals(ShootingPhase.ATTACK_REROLL, state.phase)
        assertFalse(ShootingCombatRules.canRerollAttack(state, 0))

        state = ShootingCombatRules.setAttackExtraRerolls(state, 1)
        assertTrue(ShootingCombatRules.canRerollAttack(state, 0))
        state = ShootingCombatRules.rerollAttack(state, 0, 4)
        assertEquals(ShootingPhase.ATTACK_REROLL, state.phase)

        state = ShootingCombatRules.setAttackExtraRerolls(state, 2)
        assertTrue(ShootingCombatRules.canRerollAttack(state, 1))
        state = ShootingCombatRules.rerollAttack(state, 1, 5)
        assertEquals(2, state.attackPool.extraRerollsUsed)
        assertEquals(2, state.attackExtraRerolls)
        state = ShootingCombatRules.setAttackExtraRerolls(state, 0)
        assertEquals(2, state.attackExtraRerolls)
    }

    @Test
    fun `temporary defence reroll can be granted after seeing dice`() {
        var state = retained(attackValues = listOf(6, 4, 3))
        state = ShootingCombatRules.acceptDefenceRoll(state, listOf(1, 2, 3))

        assertFalse(ShootingCombatRules.canRerollDefence(state, 0))
        state = ShootingCombatRules.setDefenceExtraRerolls(state, 1)
        assertTrue(ShootingCombatRules.canRerollDefence(state, 0))
        state = ShootingCombatRules.rerollDefence(state, 0, 6)

        assertEquals(1, state.defencePool.extraRerollsUsed)
        assertEquals(1, state.defenceExtraRerolls)
        assertEquals(ShootingPhase.DEFENCE_REROLL, state.phase)
        state = ShootingCombatRules.setDefenceExtraRerolls(state, 0)
        assertEquals(1, state.defenceExtraRerolls)
    }

    @Test
    fun `hot check damages shooter only when roll is below effective hit`() {
        var state = retained(
            attacker = attacker(attacks = 1, hit = 3, rules = ShootingWeaponRules(hot = true)),
            attackValues = listOf(2)
        )
        assertEquals(ShootingPhase.OVERHEAT, state.phase)
        state = ShootingCombatRules.resolveOverheat(state, 2)
        assertEquals(ShootingPhase.FINISHED, state.phase)
        assertEquals(6, state.attacker.currentWounds)
        assertEquals(4, state.overheatDamage)

        var safe = retained(
            attacker = attacker(attacks = 1, hit = 3, rules = ShootingWeaponRules(hot = true)),
            attackValues = listOf(2)
        )
        safe = ShootingCombatRules.resolveOverheat(safe, 3)
        assertEquals(10, safe.attacker.currentWounds)
        assertEquals(0, safe.overheatDamage)
    }

    @Test
    fun `shooting stun aliases apply only when a critical is retained`() {
        assertTrue(ShootingRuleParser.parse(listOf("晕眩")).stun)
        assertTrue(ShootingRuleParser.parse(listOf("眩晕")).stun)
        val critical = retained(
            attacker = attacker(attacks = 1, rules = ShootingWeaponRules(stun = true)),
            attackValues = listOf(6)
        )
        val normal = retained(
            attacker = attacker(attacks = 1, rules = ShootingWeaponRules(stun = true)),
            attackValues = listOf(4)
        )
        assertTrue(critical.defenderStunned)
        assertFalse(normal.defenderStunned)
    }
}
