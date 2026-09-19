package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Test

class DiceResultParserTest {

    @Test
    fun `parses normal array`() {
        assertEquals(listOf(3, 1, 6, 4), DiceResultParser.parse("[3, 1, 6, 4]"))
    }

    @Test
    fun `parses empty array`() {
        assertEquals(emptyList<Int>(), DiceResultParser.parse("[]"))
    }

    @Test
    fun `tolerates whitespace and newlines`() {
        assertEquals(listOf(2, 5), DiceResultParser.parse(" [ 2,\n 5 ] "))
    }

    @Test
    fun `non-array input returns empty`() {
        assertEquals(emptyList<Int>(), DiceResultParser.parse("garbage"))
        assertEquals(emptyList<Int>(), DiceResultParser.parse("{\"value\":3}"))
    }
}
