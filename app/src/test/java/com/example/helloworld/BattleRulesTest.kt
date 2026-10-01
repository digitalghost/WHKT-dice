package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleRulesTest {
    @Test
    fun primaryBonusIsHalfRoundedUp() {
        val score = BattleScore(key = 3, kill = 2, tactical = 4)

        assertEquals(11, score.total(ScoreType.KEY))
        assertEquals(11, score.total(ScoreType.TACTICAL))
        assertEquals(21, BattleScore(6, 6, 6).total(ScoreType.KILL))
        assertEquals(0, BattleScore().bonus(ScoreType.KEY))
    }

    @Test
    fun scoresAreCappedPerActionOp() {
        val score = BattleScore(key = 6).change(ScoreType.KEY, 3)
        assertEquals(6, score.key)
        assertEquals(0, score.change(ScoreType.KEY, -20).key)
    }

    @Test
    fun laterTurningPointGivesExtraCpToPlayerWithoutInitiative() {
        val battle = sampleBattle(turningPoint = 1)

        val next = BattleRules.beginTurningPoint(battle, BattleRules.SIDE_A)

        assertEquals(2, next.turningPoint)
        assertEquals(3, next.sideA.commandPoints)
        assertEquals(4, next.sideB.commandPoints)
        assertEquals(BattlePhase.STRATEGY, next.phase)
        assertTrue(next.sideA.operatives.first().ready)
    }

    @Test
    fun fourthFirefightEndsBattle() {
        val battle = sampleBattle(turningPoint = 4).copy(phase = BattlePhase.FIREFIGHT)

        val finished = BattleRules.advancePhase(battle)

        assertTrue(finished.completed)
        assertEquals(BattlePhase.COMPLETE, finished.phase)
    }

    @Test
    fun killLevelUsesOfficialStartingStrengthTable() {
        assertEquals(0, KillScoreCalculator.killLevel(11, 1))
        assertEquals(1, KillScoreCalculator.killLevel(11, 2))
        assertEquals(3, KillScoreCalculator.killLevel(11, 7))
        assertEquals(5, KillScoreCalculator.killLevel(11, 11))
        assertEquals(6, KillScoreCalculator.finalScore(5, 4))
    }

    private fun sampleBattle(turningPoint: Int): BattleRecord {
        val operative = BattleOperativeState(
            memberId = "member",
            operativeId = "operative",
            displayName = "特工",
            maxWounds = 10,
            ready = false
        )
        return BattleRecord(
            name = "测试战斗",
            mission = "测试任务",
            killzone = "测试杀戮区",
            keyOp = "测试关键行动",
            dropZoneA = "A",
            dropZoneB = "B",
            sideA = BattleSide("A", "ra", "甲", "team", "甲方", operatives = listOf(operative)),
            sideB = BattleSide("B", "rb", "乙", "team", "乙方", operatives = listOf(operative)),
            turningPoint = turningPoint,
            phase = BattlePhase.INITIATIVE,
            completed = false
        ).also { assertFalse(it.completed) }
    }
}
