package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class BattleFlowTest {
    private fun battle() = BattleRecord(
        name = "测试", mission = "认证行动", killzone = "测试", keyOp = "测试", dropZoneA = "北", dropZoneB = "南",
        sideA = BattleSide("A", "same-roster", "小队 A", "IMP-AOD", "甲",
            operatives = listOf(BattleOperativeState("same-member", "aod_captain", "甲队长", 15))),
        sideB = BattleSide("B", "same-roster", "小队 B", "IMP-AOD", "乙",
            operatives = listOf(BattleOperativeState("same-member", "aod_captain", "乙队长", 15)))
    )

    @Test fun deploymentAlternatesAndUsesBalancedGroups() {
        assertEquals(listOf(4, 3, 3), BattleFlow.groups(10))
        assertEquals(listOf(3, 2, 2), BattleFlow.groups(7))
        var b = BattleFlow.deployment(battle(), "B")
        assertEquals(listOf(0), b.progress.cardsA)
        b = b.copy(progress = b.progress.copy(setupStep = 2))
        repeat(6) { b = BattleFlow.deployGroup(b) }
        assertEquals(3, b.progress.setupStep)
        assertEquals(3, b.progress.deployedA)
        assertEquals(3, b.progress.deployedB)
    }

    @Test fun initiativeCardGoesToLoserEvenWhenWinnerGivesAwayInitiative() {
        val b = battle().copy(progress = BattleProgress(setupStep = 3))
        val next = BattleFlow.initiative(b, "A", "B")
        assertEquals("B", next.initiativeSideId)
        assertEquals(listOf(1), next.progress.cardsB)
        assertEquals(3, next.sideA.commandPoints)
        assertEquals(3, next.sideB.commandPoints)
        assertThrows(IllegalArgumentException::class.java) { BattleRules.beginTurningPoint(next, "A") }
    }

    @Test fun initiativePersistsBetweenTurningPoints() {
        val b = battle().copy(turningPoint = 2, phase = BattlePhase.FIREFIGHT, initiativeSideId = "B")
        val next = BattleRules.advancePhase(b)
        assertEquals("B", next.initiativeSideId)
        val started = BattleFlow.initiative(next, "B", "A")
        assertEquals(3, started.sideA.commandPoints)
        assertEquals(4, started.sideB.commandPoints)
    }

    @Test fun cardCanOnlyBeSpentOnce() {
        val b = battle().copy(progress = BattleProgress(cardsA = listOf(0, 2)))
        val spent = BattleFlow.useCard(b, "A", 2, "4 → 6")
        assertEquals(listOf(0), spent.progress.cardsA)
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.useCard(spent, "A", 2, "6 → 8") }
    }

    @Test fun strategyAlternatesAndRejectsDuplicateOrUnaffordablePlan() {
        val b = battle().copy(turningPoint = 2, phase = BattlePhase.STRATEGY)
        val first = BattleFlow.plan(b, "计划", 1)
        assertEquals(1, first.sideA.commandPoints)
        val passed = BattleFlow.passPlan(first)
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.plan(passed, "计划", 1) }
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.plan(passed, "其他", 5) }
        assertEquals(2, BattleFlow.passPlan(passed).progress.planPasses)
    }

    @Test fun firstTurningPointCannotPassBeforeSelectingPrimary() {
        val b = battle().copy(turningPoint = 1, phase = BattlePhase.STRATEGY)
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.passPlan(b) }
    }

    @Test fun activationTracksApAndOrderAndAlternatesPlayers() {
        val b = battle().copy(turningPoint = 1, phase = BattlePhase.FIREFIGHT)
        val activated = BattleFlow.activate(b, "A", "same-member", 3, false)
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.action(activated, "射击", 1, "") }
        val moved = BattleFlow.action(activated, "转移", 1, "")
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.action(moved, "转移", 1, "") }
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.action(moved, "任务", 3, "") }
        val finished = BattleFlow.endActivation(moved)
        assertEquals("B", finished.progress.nextSide)
        assertFalse(finished.sideA.operatives.first().ready)
        assertTrue(finished.sideB.operatives.first().ready)
    }

    @Test fun reactionIsOneOneApActionOncePerTurningPoint() {
        val b = battle().copy(turningPoint = 2, phase = BattlePhase.FIREFIGHT)
        val standby = b.copy(sideA = b.sideA.copy(operatives = b.sideA.operatives.map { it.copy(ready = false, order = "交战") }))
        val reaction = BattleFlow.activate(standby, "A", "same-member", 3, true)
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.action(reaction, "戒备", 1, "") }
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.action(reaction, "后撤", 2, "") }
        val shot = BattleFlow.action(reaction, "射击", 1, "")
        assertEquals(0, shot.progress.active?.spent)
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.action(shot, "射击", 1, "阿斯塔特") }
        val ended = BattleFlow.endActivation(shot)
        assertTrue("A/same-member" in ended.progress.reacted)
        assertFalse("B/same-member" in ended.progress.reacted)
    }
}
