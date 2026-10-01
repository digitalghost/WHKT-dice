package com.example.helloworld

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentLimitTest {
    @Test
    fun fifthEquipmentCannotBeAdded() {
        assertTrue(EquipmentCards.canAdd(0))
        assertTrue(EquipmentCards.canAdd(3))
        assertFalse(EquipmentCards.canAdd(4))
        assertFalse(EquipmentCards.canAdd(5))
    }
}
