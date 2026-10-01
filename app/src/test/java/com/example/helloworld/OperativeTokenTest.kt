package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperativeTokenTest {
    private fun operative() = BattleOperativeState("member", "operative", "测试特工", 10)

    @Test
    fun orderIsOneMutuallyExclusiveValueAndReadinessIsIndependent() {
        val concealed = operative()
        val engaged = concealed.copy(order = BattleOperativeState.ORDER_ENGAGE)
        val spent = engaged.copy(ready = false)

        assertEquals(BattleOperativeState.ORDER_CONCEAL, concealed.order)
        assertEquals(BattleOperativeState.ORDER_ENGAGE, engaged.order)
        assertTrue(engaged.ready)
        assertFalse(spent.ready)
        assertEquals(BattleOperativeState.ORDER_ENGAGE, spent.order)
    }

    @Test
    fun eachOrderHasASeparateReadyAndSpentFace() {
        val concealReady=OrderTokens.resource(BattleOperativeState.ORDER_CONCEAL,true)
        val concealSpent=OrderTokens.resource(BattleOperativeState.ORDER_CONCEAL,false)
        val engageReady=OrderTokens.resource(BattleOperativeState.ORDER_ENGAGE,true)
        val engageSpent=OrderTokens.resource(BattleOperativeState.ORDER_ENGAGE,false)

        assertEquals(4,setOf(concealReady,concealSpent,engageReady,engageSpent).size)
        assertEquals(BattleOperativeState.ORDER_ENGAGE,OrderTokens.oppositeOrder(BattleOperativeState.ORDER_CONCEAL))
        assertEquals(BattleOperativeState.ORDER_CONCEAL,OrderTokens.oppositeOrder(BattleOperativeState.ORDER_ENGAGE))
        assertEquals(BattleOperativeState.ORDER_CONCEAL,OrderTokens.orderForSide(true))
        assertEquals(BattleOperativeState.ORDER_ENGAGE,OrderTokens.orderForSide(false))
        assertEquals("亮面 · 就绪",OrderTokens.faceLabel(true))
        assertEquals("暗面 · 待机",OrderTokens.faceLabel(false))
    }
}
