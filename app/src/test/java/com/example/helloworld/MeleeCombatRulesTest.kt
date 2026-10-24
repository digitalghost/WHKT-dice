package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeleeCombatRulesTest {
    private fun fighter(
        side: MeleeSide,
        name: String,
        wounds: Int = 10,
        attacks: Int = 3,
        hit: Int = 3,
        normalDamage: Int = 3,
        criticalDamage: Int = 5,
        assists: Int = 0,
        canFight: Boolean = true,
        accurateRetained: Int = 0,
        extraRerolls: Int = 0,
        rules: MeleeWeaponRules = MeleeWeaponRules()
    ) = MeleeFighter(
        side = side,
        battleSideId = if (side == MeleeSide.ATTACKER) "A" else "B",
        memberId = name,
        playerName = name,
        operativeName = name,
        weaponName = "$name 武器",
        maxWounds = 10,
        currentWounds = wounds,
        attacks = attacks,
        baseHit = hit,
        normalDamage = normalDamage,
        criticalDamage = criticalDamage,
        assists = assists,
        canFight = canFight,
        accurateRetained = accurateRetained,
        extraRerolls = extraRerolls,
        rules = rules
    )

    private fun rolled(
        attacker: MeleeFighter = fighter(MeleeSide.ATTACKER, "甲"),
        defender: MeleeFighter = fighter(MeleeSide.DEFENDER, "乙"),
        attackerValues: List<Int> = listOf(6, 4, 2),
        defenderValues: List<Int> = listOf(6, 3, 1)
    ): MeleeCombatState = MeleeCombatRules.acceptRolls(
        MeleeCombatRules.start(attacker, defender, MeleeSide.ATTACKER),
        attackerValues,
        defenderValues
    )

    private fun rerollsFinished(state: MeleeCombatState): MeleeCombatState {
        if (state.phase != MeleePhase.REROLL) return state
        val afterFirstPass = MeleeCombatRules.passReroll(state, state.rerollTurn)
        return MeleeCombatRules.passReroll(afterFirstPass, afterFirstPass.rerollTurn)
    }

    private fun retained(state: MeleeCombatState): MeleeCombatState =
        MeleeCombatRules.finishRetention(
            rerollsFinished(state),
            MeleeRetainChoices(punishing = true, rending = true, severe = true),
            MeleeRetainChoices(punishing = true, rending = true, severe = true)
        )

    @Test
    fun `wounded and assists modify hit but one still always fails`() {
        val subject = fighter(MeleeSide.ATTACKER, "甲", wounds = 4, hit = 4, assists = 2)
        assertTrue(subject.wounded)
        assertEquals(3, subject.effectiveHit)
        val state = retained(rolled(attacker = subject, attackerValues = listOf(1, 2, 3)))
        assertEquals(1, state.attackerPool.normals)
        assertEquals(0, state.attackerPool.criticals)
    }

    @Test
    fun `lethal only turns an already successful result into a critical`() {
        val subject = fighter(
            MeleeSide.ATTACKER,
            "甲",
            hit = 5,
            rules = MeleeWeaponRules(lethalThreshold = 4)
        )
        val state = retained(rolled(attacker = subject, attackerValues = listOf(4, 5, 6)))
        assertEquals(0, state.attackerPool.normals)
        assertEquals(2, state.attackerPool.criticals)
    }

    @Test
    fun `balanced rerolls one die and simultaneous rerolls alternate from initiative`() {
        val attacker = fighter(
            MeleeSide.ATTACKER,
            "甲",
            rules = MeleeWeaponRules(rerollPolicy = MeleeRerollPolicy.BALANCED)
        )
        val defender = fighter(
            MeleeSide.DEFENDER,
            "乙",
            rules = MeleeWeaponRules(rerollPolicy = MeleeRerollPolicy.RELENTLESS)
        )
        var state = MeleeCombatRules.acceptRolls(
            MeleeCombatRules.start(attacker, defender, MeleeSide.DEFENDER),
            listOf(1, 2, 3),
            listOf(1, 2, 3)
        )
        assertEquals(MeleeSide.DEFENDER, state.rerollTurn)
        state = MeleeCombatRules.reroll(state, MeleeSide.DEFENDER, 0, 6)
        assertEquals(MeleeSide.ATTACKER, state.rerollTurn)
        state = MeleeCombatRules.reroll(state, MeleeSide.ATTACKER, 0, 6)
        assertFalse(MeleeCombatRules.hasAnyReroll(state, MeleeSide.ATTACKER))
        assertEquals(MeleeSide.DEFENDER, state.rerollTurn)
    }

    @Test
    fun `normal parry cannot remove critical while critical parry can`() {
        var state = retained(rolled())
        assertEquals(1, state.attackerPool.criticals)
        assertEquals(1, state.defenderPool.criticals)
        state = MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.CRITICAL,
            MeleeUse.PARRY,
            MeleeSuccess.CRITICAL
        )
        assertEquals(0, state.defenderPool.criticals)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normal parry rejects critical target`() {
        val state = retained(rolled())
        MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.NORMAL,
            MeleeUse.PARRY,
            MeleeSuccess.CRITICAL
        )
    }

    @Test
    fun `brutal requires opponent to spend a critical to parry`() {
        val attacker = fighter(
            MeleeSide.ATTACKER,
            "甲",
            rules = MeleeWeaponRules(brutal = true)
        )
        var state = retained(rolled(attacker = attacker))
        state = MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.NORMAL,
            MeleeUse.STRIKE
        )
        assertEquals(MeleeSide.DEFENDER, state.resolveTurn)
        assertFalse(MeleeCombatRules.canParryWith(state, MeleeSide.DEFENDER, MeleeSuccess.NORMAL))
        assertTrue(MeleeCombatRules.canParryWith(state, MeleeSide.DEFENDER, MeleeSuccess.CRITICAL))
    }

    @Test
    fun `shock removes one opposing normal on first critical strike only`() {
        val attacker = fighter(
            MeleeSide.ATTACKER,
            "甲",
            attacks = 4,
            rules = MeleeWeaponRules(shock = true)
        )
        val defender = fighter(MeleeSide.DEFENDER, "乙", attacks = 4)
        var state = retained(
            rolled(
                attacker,
                defender,
                attackerValues = listOf(6, 6, 4, 4),
                defenderValues = listOf(6, 5, 4, 3)
            )
        )
        state = MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.CRITICAL,
            MeleeUse.STRIKE
        )
        assertEquals(2, state.defenderPool.normals)
        assertTrue(state.attackerShockUsed)
    }

    @Test
    fun `empty parry is legal and consumes the success`() {
        val attacker = fighter(MeleeSide.ATTACKER, "甲", attacks = 1)
        val defender = fighter(MeleeSide.DEFENDER, "乙", attacks = 0, canFight = false)
        var state = retained(rolled(attacker, defender, listOf(4), emptyList()))
        assertTrue(MeleeCombatRules.canParryWith(state, MeleeSide.ATTACKER, MeleeSuccess.NORMAL))
        state = MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.NORMAL,
            MeleeUse.PARRY
        )
        assertEquals(MeleePhase.FINISHED, state.phase)
        assertEquals(10, state.defender.currentWounds)
    }

    @Test
    fun `a killing strike immediately ends combat`() {
        val defender = fighter(MeleeSide.DEFENDER, "乙", wounds = 4)
        var state = retained(rolled(defender = defender))
        state = MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.CRITICAL,
            MeleeUse.STRIKE
        )
        assertEquals(MeleePhase.FINISHED, state.phase)
        assertEquals(0, state.defender.currentWounds)
    }

    @Test
    fun `severe may convert an accurate retained normal but cannot trigger rending`() {
        val attacker = fighter(
            MeleeSide.ATTACKER,
            "甲",
            attacks = 2,
            accurateRetained = 1,
            rules = MeleeWeaponRules(accurate = 1, severe = true, rending = true)
        )
        val state = MeleeCombatRules.finishRetention(
            rerollsFinished(rolled(attacker = attacker, attackerValues = listOf(2))),
            MeleeRetainChoices(rending = true, severe = true)
        )
        assertEquals(1, state.attackerPool.criticals)
        assertEquals(0, state.attackerPool.normals)
    }

    @Test
    fun `devastating damage is applied for both pools before resolution`() {
        val attacker = fighter(
            MeleeSide.ATTACKER,
            "甲",
            attacks = 2,
            rules = MeleeWeaponRules(devastating = 2)
        )
        val defender = fighter(
            MeleeSide.DEFENDER,
            "乙",
            attacks = 2,
            rules = MeleeWeaponRules(devastating = 1)
        )
        val state = retained(rolled(attacker, defender, listOf(6, 4), listOf(6, 3)))

        assertEquals(9, state.attacker.currentWounds)
        assertEquals(8, state.defender.currentWounds)
        assertEquals(MeleePhase.RESOLVE, state.phase)
    }

    @Test
    fun `stun aliases are parsed and retained critical marks the target`() {
        assertTrue(MeleeRuleParser.parse(listOf("晕眩")).stun)
        assertTrue(MeleeRuleParser.parse(listOf("眩晕")).stun)

        val attacker = fighter(
            MeleeSide.ATTACKER,
            "甲",
            rules = MeleeWeaponRules(stun = true)
        )
        val state = retained(rolled(attacker = attacker))
        assertTrue(state.defenderStunned)
        assertFalse(state.attackerStunned)
    }

    @Test
    fun `ceaseless locks rerolls to the first selected face`() {
        val attacker = fighter(
            MeleeSide.ATTACKER,
            "甲",
            rules = MeleeWeaponRules(rerollPolicy = MeleeRerollPolicy.CEASELESS)
        )
        var state = rolled(attacker = attacker, attackerValues = listOf(2, 2, 3))
        state = MeleeCombatRules.reroll(state, MeleeSide.ATTACKER, 0, 6)
        state = MeleeCombatRules.passReroll(state, MeleeSide.DEFENDER)

        assertTrue(MeleeCombatRules.canReroll(state, MeleeSide.ATTACKER, 1))
        assertFalse(MeleeCombatRules.canReroll(state, MeleeSide.ATTACKER, 2))
    }

    @Test
    fun `rending cannot upgrade an accurate retained success by itself`() {
        val attacker = fighter(
            MeleeSide.ATTACKER,
            "甲",
            attacks = 2,
            accurateRetained = 1,
            rules = MeleeWeaponRules(accurate = 1, rending = true)
        )
        val state = MeleeCombatRules.finishRetention(
            rerollsFinished(rolled(attacker = attacker, attackerValues = listOf(6))),
            MeleeRetainChoices(rending = true)
        )

        assertEquals(1, state.attackerPool.criticals)
        assertEquals(1, state.attackerPool.normals)
    }

    @Test
    fun `side with remaining successes keeps resolving after opponent pool is empty`() {
        val attacker = fighter(MeleeSide.ATTACKER, "甲", attacks = 2, normalDamage = 2)
        val defender = fighter(MeleeSide.DEFENDER, "乙", attacks = 1)
        var state = retained(rolled(attacker, defender, listOf(4, 4), listOf(1)))

        assertEquals(MeleeSide.ATTACKER, state.resolveTurn)
        state = MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.NORMAL,
            MeleeUse.STRIKE
        )
        assertEquals(MeleeSide.ATTACKER, state.resolveTurn)
        state = MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.NORMAL,
            MeleeUse.STRIKE
        )

        assertEquals(MeleePhase.FINISHED, state.phase)
        assertEquals(6, state.defender.currentWounds)
    }

    @Test
    fun `balanced alias is parsed`() {
        assertEquals(
            MeleeRerollPolicy.BALANCED,
            MeleeRuleParser.parse(listOf("均衡")).rerollPolicy
        )
    }

    @Test
    fun `temporary rerolls are budgeted and still alternate by initiative`() {
        val attacker = fighter(MeleeSide.ATTACKER, "甲", extraRerolls = 2)
        val defender = fighter(MeleeSide.DEFENDER, "乙", extraRerolls = 1)
        var state = rolled(
            attacker,
            defender,
            attackerValues = listOf(1, 2, 3),
            defenderValues = listOf(1, 2, 3)
        )
        assertEquals(MeleeSide.ATTACKER, state.rerollTurn)
        state = MeleeCombatRules.reroll(state, MeleeSide.ATTACKER, 0, 6)
        assertEquals(1, state.attackerPool.extraRerollsUsed)
        assertEquals(MeleeSide.DEFENDER, state.rerollTurn)
        state = MeleeCombatRules.reroll(state, MeleeSide.DEFENDER, 0, 6)
        assertEquals(1, state.defenderPool.extraRerollsUsed)
        assertEquals(MeleeSide.ATTACKER, state.rerollTurn)
        state = MeleeCombatRules.reroll(state, MeleeSide.ATTACKER, 1, 5)
        assertEquals(2, state.attackerPool.extraRerollsUsed)
        assertEquals(MeleeSide.DEFENDER, state.rerollTurn)
        assertFalse(MeleeCombatRules.hasAnyReroll(state, MeleeSide.ATTACKER))
        assertFalse(MeleeCombatRules.hasAnyReroll(state, MeleeSide.DEFENDER))
    }

    @Test
    fun `temporary rerolls can be granted after both players see dice`() {
        var state = rolled(
            attackerValues = listOf(1, 2, 3),
            defenderValues = listOf(1, 2, 3)
        )

        assertEquals(MeleePhase.REROLL, state.phase)
        assertFalse(MeleeCombatRules.canReroll(state, MeleeSide.ATTACKER, 0))

        state = MeleeCombatRules.setExtraRerolls(state, MeleeSide.ATTACKER, 1)
        assertTrue(MeleeCombatRules.canReroll(state, MeleeSide.ATTACKER, 0))
        state = MeleeCombatRules.reroll(state, MeleeSide.ATTACKER, 0, 6)
        assertEquals(MeleeSide.DEFENDER, state.rerollTurn)

        state = MeleeCombatRules.setExtraRerolls(state, MeleeSide.DEFENDER, 1)
        assertTrue(MeleeCombatRules.canReroll(state, MeleeSide.DEFENDER, 0))
        state = MeleeCombatRules.reroll(state, MeleeSide.DEFENDER, 0, 5)

        state = MeleeCombatRules.setExtraRerolls(state, MeleeSide.ATTACKER, 0)
        assertEquals(1, state.attacker.extraRerolls)
        assertEquals(1, state.attackerPool.extraRerollsUsed)
        assertEquals(1, state.defenderPool.extraRerollsUsed)
    }

    @Test
    fun `two consecutive reroll passes end reroll step`() {
        val attacker = fighter(MeleeSide.ATTACKER, "甲", extraRerolls = 1)
        val defender = fighter(MeleeSide.DEFENDER, "乙", extraRerolls = 1)
        var state = rolled(attacker, defender)
        state = MeleeCombatRules.passReroll(state, MeleeSide.ATTACKER)
        assertEquals(MeleePhase.REROLL, state.phase)
        state = MeleeCombatRules.passReroll(state, MeleeSide.DEFENDER)
        assertEquals(MeleePhase.RETAIN, state.phase)
    }

    @Test
    fun `active fighter resolves first even when defender has reroll initiative`() {
        var state = MeleeCombatRules.acceptRolls(
            MeleeCombatRules.start(
                fighter(MeleeSide.ATTACKER, "甲"),
                fighter(MeleeSide.DEFENDER, "乙"),
                MeleeSide.DEFENDER
            ),
            listOf(4, 2, 1),
            listOf(4, 2, 1)
        )
        state = retained(state)
        assertEquals(MeleeSide.ATTACKER, state.resolveTurn)
    }

    @Test
    fun `fighter that cannot retaliate contributes no dice and attacker resolves all`() {
        val defender = fighter(MeleeSide.DEFENDER, "乙", canFight = false, attacks = 0)
        var state = rolled(
            defender = defender,
            attackerValues = listOf(4, 4, 2),
            defenderValues = emptyList()
        )
        state = retained(state)
        assertEquals(0, state.defenderPool.successes)
        assertEquals(MeleeSide.ATTACKER, state.resolveTurn)
        state = MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.NORMAL,
            MeleeUse.PARRY
        )
        assertEquals(MeleeSide.ATTACKER, state.resolveTurn)
        assertEquals(1, state.attackerPool.normals)
    }

    @Test
    fun `shock discards an opposing critical when no normal remains`() {
        val attacker = fighter(
            MeleeSide.ATTACKER,
            "甲",
            attacks = 2,
            rules = MeleeWeaponRules(shock = true)
        )
        val defender = fighter(MeleeSide.DEFENDER, "乙", attacks = 2)
        var state = retained(
            rolled(
                attacker,
                defender,
                attackerValues = listOf(6, 4),
                defenderValues = listOf(6, 2)
            )
        )
        state = MeleeCombatRules.resolve(
            state,
            MeleeSide.ATTACKER,
            MeleeSuccess.CRITICAL,
            MeleeUse.STRIKE
        )
        assertEquals(0, state.defenderPool.criticals)
    }
}
