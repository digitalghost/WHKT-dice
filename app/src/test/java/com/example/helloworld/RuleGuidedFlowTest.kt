package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule-guided flow: initiative ties, CP, kill grade, end of battle, tokens, undo trace. */
class RuleGuidedFlowTest {
    private fun op(id: String, wounds: Int = 10, apl: Int = 2) = BattleOperativeState(id, "op", id, wounds, apl = apl)

    private fun battle(countA: Int = 5, countB: Int = 5) = BattleRecord(
        name = "测试", mission = "", killzone = "", keyOp = "", dropZoneA = "", dropZoneB = "",
        sideA = BattleSide("A", "ra", "A", "IMP-AOD", "甲", operatives = List(countA) { op("a$it") }),
        sideB = BattleSide("B", "rb", "B", "IMP-AOD", "乙", operatives = List(countB) { op("b$it") })
    )

    private fun incapacitate(b: BattleRecord, sideId: String, count: Int): BattleRecord {
        val side = b.side(sideId)
        return BattleFlow.side(b, side.copy(operatives = side.operatives.mapIndexed { i, o ->
            if (i < count) BattleRules.setWounds(o, 0) else o
        }))
    }

    @Test fun `initiative tie goes to the player without initiative`() {
        val b = battle().copy(turningPoint = 2, initiativeSideId = "A")
        assertEquals("B", BattleFlow.initiativeWinner(b, 4, 4))
        assertEquals("A", BattleFlow.initiativeWinner(b, 5, 4))
    }

    @Test fun `first turning point tie goes to the player without deployment initiative`() {
        val unknown = battle()
        assertNull(BattleFlow.initiativeWinner(unknown, 3, 3))
        val known = unknown.copy(progress = BattleProgress(deploymentInitiative = "A"))
        assertEquals("B", BattleFlow.initiativeWinner(known, 3, 3))
    }

    @Test fun `new battle starts at initiative and first turning point gives 1CP each`() {
        val b = battle()
        assertEquals(BattlePhase.INITIATIVE, b.phase)
        val tp1 = BattleFlow.initiative(b, "A", "A")
        assertEquals(1, tp1.turningPoint)
        assertEquals(BattlePhase.STRATEGY, tp1.phase)
        assertEquals(3, tp1.sideA.commandPoints)
        assertEquals(3, tp1.sideB.commandPoints)
    }

    @Test fun `later turning points give 2CP to the player without initiative and ready operatives`() {
        var b = battle().copy(turningPoint = 1, phase = BattlePhase.FIREFIGHT, initiativeSideId = "A")
        b = b.copy(sideA = b.sideA.copy(operatives = b.sideA.operatives.map { it.copy(ready = false) }))
        b = BattleRules.advancePhase(b)
        assertEquals(BattlePhase.INITIATIVE, b.phase)
        b = BattleFlow.initiative(b, "B", "B")
        assertEquals(2, b.turningPoint)
        assertEquals(4, b.sideA.commandPoints)
        assertEquals(3, b.sideB.commandPoints)
        assertTrue(b.sideA.operatives.all { it.ready })
    }

    @Test fun `firefight starts with the initiative player`() {
        val b = BattleRules.advancePhase(battle().copy(turningPoint = 2, phase = BattlePhase.STRATEGY, initiativeSideId = "B"))
        assertEquals(BattlePhase.FIREFIGHT, b.phase)
        assertEquals("B", b.progress.nextSide)
    }

    @Test fun `wounds at zero incapacitate and raising them revives`() {
        val o = op("x")
        val down = BattleRules.setWounds(o, 0)
        assertTrue(down.incapacitated)
        assertFalse(down.ready)
        assertFalse(BattleRules.setWounds(down, 2).incapacitated)
    }

    @Test fun `each new kill grade scores once and is never removed`() {
        var b = incapacitate(battle(), "B", 2)
        b = BattleRules.syncKills(b)
        assertEquals(2, b.sideA.score.kill)
        assertEquals(2, BattleRules.syncKills(b).sideA.score.kill)
        val revived = b.copy(sideB = b.sideB.copy(operatives = b.sideB.operatives.map { BattleRules.setWounds(it, 5) }))
        assertEquals(2, BattleRules.syncKills(revived).sideA.score.kill)
    }

    @Test fun `operatives excluded from kill op do not count`() {
        var b = battle()
        b = b.copy(sideB = b.sideB.copy(operatives = b.sideB.operatives.mapIndexed { i, o ->
            if (i == 0) BattleRules.setWounds(o, 0).copy(excludedFromKill = true) else o
        }))
        assertEquals(0, BattleRules.syncKills(b).sideA.score.kill)
    }

    @Test fun `end of battle adds 1VP for the higher kill grade and decides the result`() {
        var b = battle().copy(turningPoint = 4, phase = BattlePhase.FIREFIGHT)
        b = BattleRules.syncKills(incapacitate(b, "B", 3))
        b = BattleRules.advancePhase(b)
        assertTrue(b.completed)
        assertEquals(4, b.sideA.score.kill)
        assertEquals(0, b.sideB.score.kill)
        assertEquals("甲获胜", BattleRules.resultLabel(b))
        assertEquals(b, b.copy())
        assertTrue(b.progress.killBonusApplied)
    }

    @Test fun `equal VP is a draw`() {
        assertEquals("平局", BattleRules.resultLabel(BattleRules.complete(battle())))
    }

    @Test fun `stun lowers APL and total APL change is capped at one`() {
        val stunned = op("x", apl = 3).copy(tokens = listOf(OperativeToken(name = OperativeToken.STUNNED)))
        assertEquals(2, BattleFlow.effectiveApl(stunned))
        assertEquals(2, BattleFlow.effectiveApl(stunned, manualDelta = -1))
        assertEquals(4, BattleFlow.effectiveApl(stunned, manualDelta = 2))
        assertEquals(4, BattleFlow.effectiveApl(op("y", apl = 3), manualDelta = 3))
    }

    @Test fun `tokens expire at the end of the next activation and turning point`() {
        var b = battle().copy(turningPoint = 1, phase = BattlePhase.FIREFIGHT)
        b = BattleFlow.side(b, b.sideA.copy(operatives = b.sideA.operatives.mapIndexed { i, o ->
            if (i == 0) o.copy(tokens = listOf(
                OperativeToken(name = OperativeToken.STUNNED, duration = TokenDuration.NEXT_ACTIVATION_END),
                OperativeToken(name = "圣勉", duration = TokenDuration.TURNING_POINT_END),
                OperativeToken(name = OperativeToken.OFF_FIELD, duration = TokenDuration.MANUAL)
            )) else o
        }))
        b = BattleFlow.activate(b, "A", "a0", 2, true)
        b = BattleFlow.endActivation(b)
        assertEquals(listOf("圣勉", OperativeToken.OFF_FIELD), b.sideA.operatives[0].tokens.map { it.name })
        b = BattleRules.advancePhase(b)
        assertEquals(listOf(OperativeToken.OFF_FIELD), b.sideA.operatives[0].tokens.map { it.name })
        assertTrue(b.sideA.operatives[0].offField)
    }

    @Test fun `forced activation bypasses turn order for team rules and is logged`() {
        val b = battle().copy(turningPoint = 1, phase = BattlePhase.FIREFIGHT, progress = BattleProgress(nextSide = "A"))
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.activate(b, "B", "b0", 2, true) }
        val forced = BattleFlow.activate(b, "B", "b0", 2, true, force = true, reason = "地底伏击")
        assertEquals("B", forced.progress.active?.side)
        assertTrue(forced.log.last().message.contains("地底伏击"))
    }

    @Test fun `duplicate strategic ploy is refused unless a rule allows it`() {
        val b = battle().copy(turningPoint = 2, phase = BattlePhase.STRATEGY)
        val used = BattleFlow.passPlan(BattleFlow.plan(b, "计谋", 1))
        val back = used.copy(progress = used.progress.copy(nextSide = "A", planPasses = 0))
        assertTrue(BattleFlow.planUsed(back, "计谋"))
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.plan(back, "计谋", 0) }
        val repeated = BattleFlow.plan(back, "计谋", 0, allowRepeat = true)
        assertEquals(1, repeated.sideA.commandPoints)
    }

    @Test fun `undo keeps a struck trace of reverted entries`() {
        val before = BattleFlow.event(battle(), "开局")
        val after = BattleFlow.event(before, "甲：耐伤 10 → 7")
        assertEquals("甲：耐伤 10 → 7", UndoTrace.preview(after, before))
        val restored = UndoTrace.restore(after, before)
        assertEquals(3, restored.log.size)
        assertTrue(restored.log[1].undone)
        assertEquals("${UndoTrace.PREFIX}甲：耐伤 10 → 7", restored.log[2].message)
        assertFalse(restored.log[2].undone)
        assertNull(UndoTrace.preview(restored, before))
    }

    @Test fun `primary op is a secret strategic plan in the first turning point`() {
        var b = BattleFlow.initiative(battle(), "A", "A")
        assertTrue(BattleFlow.needsPrimary(b, "A"))
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.passPlan(b) }
        assertThrows(IllegalArgumentException::class.java) { BattleRules.advancePhase(b) }
        b = BattleFlow.choosePrimary(b, ScoreType.KILL)
        assertEquals(ScoreType.KILL, b.sideA.primaryOp)
        assertEquals("B", b.progress.nextSide)
        assertFalse(b.log.last().message.contains("击杀"))
        assertThrows(IllegalArgumentException::class.java) { BattleRules.advancePhase(b) }
        b = BattleFlow.choosePrimary(b, ScoreType.KEY)
        assertTrue(BattleFlow.primariesReady(b))
        assertEquals(BattlePhase.FIREFIGHT, BattleRules.advancePhase(b).phase)
    }
}
