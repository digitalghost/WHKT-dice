package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleRoundTransitionsTest {
    private fun side(id: String, order: String, ready: Boolean, incapacitated: Boolean=false)=BattleSide(
        id=id,
        rosterId="roster-$id",
        rosterName="roster",
        teamId="IMP-AOD",
        playerName="player-$id",
        operatives=listOf(BattleOperativeState("member-$id","operative-$id","operative-$id",10,
            order=order,ready=ready,incapacitated=incapacitated))
    )

    private fun battle(turningPoint: Int=1)=BattleRecord(
        name="test",
        mission="mission",
        killzone="killzone",
        keyOp="key",
        dropZoneA="A",
        dropZoneB="B",
        sideA=side("A",BattleOperativeState.ORDER_ENGAGE,false),
        sideB=side("B",BattleOperativeState.ORDER_CONCEAL,false),
        turningPoint=turningPoint,
        phase=BattlePhase.FIREFIGHT,
        progress=BattleProgress(
            active=Activation("A","member-A",3),
            reacted=setOf("A/member-A"),
            planPasses=2,
            usedPlans=setOf("A/plan")
        )
    )

    @Test fun advanceIsSequentialAndResetsOnlyPerTurnActivationState() {
        val next=BattleRoundTransitions.advance(battle())

        assertEquals(2,next.turningPoint)
        assertEquals(BattleOperativeState.ORDER_ENGAGE,next.sideA.operatives.first().order)
        assertEquals(BattleOperativeState.ORDER_CONCEAL,next.sideB.operatives.first().order)
        assertTrue(next.sideA.operatives.first().ready)
        assertTrue(next.sideB.operatives.first().ready)
        assertTrue(next.progress.reacted.isEmpty())
        assertTrue(next.progress.usedPlans.isEmpty())
        assertEquals(0,next.progress.planPasses)
        assertEquals(null,next.progress.active)
    }

    @Test fun cannotSkipOrAdvancePastFourthTurningPoint() {
        assertThrows(IllegalArgumentException::class.java) { BattleRoundTransitions.advance(battle(0)) }
        assertThrows(IllegalArgumentException::class.java) { BattleRoundTransitions.advance(battle(4)) }
    }

    @Test fun roundChangeLogsAreRecognizedForConfirmationOnly() {
        assertTrue(BattleRoundTransitions.isRoundChange(BattleLogEntry(turningPoint=2,phase=BattlePhase.FIREFIGHT,message="进入第 2 回合；就绪")))
        assertFalse(BattleRoundTransitions.isRoundChange(BattleLogEntry(turningPoint=2,phase=BattlePhase.FIREFIGHT,message="更新耐伤")))
    }
}
