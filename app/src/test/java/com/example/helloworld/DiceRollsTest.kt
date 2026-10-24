package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceRollsTest {
    @Test fun d3IsHalfD6RoundedUp() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3), (1..6).map { RolledDie(it, 3).value })
    }

    @Test fun totalAddsDiceAndModifier() {
        val roll = DiceRoll(DiceExpression(2, 3, 3), listOf(RolledDie(5, 3), RolledDie(2, 3)))
        assertEquals(3 + 1 + 3, roll.total)
        assertEquals("2D3+3", roll.expression.label)
    }

    @Test fun targetCountsSuccessesAndOneAlwaysFails() {
        val roll = DiceRoll(DiceExpression(3, 6, target = 4), listOf(RolledDie(4, 6), RolledDie(1, 6), RolledDie(6, 6)))
        assertEquals(2, roll.successes)
    }

    @Test fun eachDieRerollsOnlyOnce() {
        val r = Random(7)
        var roll = DiceRolls.roll(DiceExpression(2, 6), r)
        roll = DiceRolls.reroll(roll, 0, r)
        assertTrue(roll.dice[0].rerolled)
        assertThrows(IllegalArgumentException::class.java) { DiceRolls.reroll(roll, 0, r) }
    }
}
