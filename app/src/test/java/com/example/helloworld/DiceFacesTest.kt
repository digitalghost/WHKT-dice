package com.example.helloworld

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceFacesTest {
    /** Tray results must be fair: each face within 16.7% ± 1% over 60,000 throws. */
    @Test fun trayFacesAreUniform() {
        val random = Random(20261108)
        val counts = IntArray(7)
        repeat(60_000) { counts[DiceFaces.draw(random)]++ }
        (1..6).forEach { face ->
            val share = counts[face] / 60_000.0
            assertTrue("face $face share $share", share in 0.157..0.177)
        }
        assertTrue(counts[0] == 0)
    }
}
