package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Generic tools for team rules that the core flow could not record before. */
class RuleToolsTest {
    private fun op(id: String, wounds: Int = 10) = BattleOperativeState(id, "aod_captain", id, wounds, apl = 2)

    private fun battle() = BattleRecord(
        name = "t", mission = "m", killzone = "k", keyOp = "o", dropZoneA = "北", dropZoneB = "南",
        sideA = BattleSide("A", "r", "甲队", "IMP-AOD", "甲", operatives = listOf(op("a1"), op("a2"), op("a3"))),
        sideB = BattleSide("B", "r", "乙队", "IMP-AOD", "乙", operatives = listOf(op("b1"), op("b2")))
    ).copy(phase = BattlePhase.FIREFIGHT, turningPoint = 1)

    @Test fun `skipping an activation passes the turn and needs a reason`() {
        val b = battle()
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.skipActivation(b, "") }
        val skipped = BattleFlow.skipActivation(b, "恶兆")
        assertEquals("B", skipped.progress.nextSide)
        assertTrue(skipped.log.last().message.contains("恶兆"))
    }

    @Test fun `keep turn lets the same player activate again`() {
        var b = BattleFlow.activate(battle(), "A", "a1", 2, true)
        b = BattleFlow.keepTurn(b, "控制律令")
        b = BattleFlow.endActivation(b)
        assertEquals("A", b.progress.nextSide)
        b = BattleFlow.activate(b, "A", "a2", 2, true)
        assertEquals("a2", b.progress.active?.member)
    }

    @Test fun `joint activation keeps two operatives open and resumes the parked one`() {
        var b = BattleFlow.activate(battle(), "A", "a1", 2, true)
        b = BattleFlow.action(b, "转移", 1, "")
        b = BattleFlow.jointActivate(b, "a2", 2, true, "变种兽群")
        assertEquals("a2", b.progress.active?.member)
        b = BattleFlow.action(b, "冲刺", 1, "")
        b = BattleFlow.switchJoint(b, "a1")
        assertEquals(listOf("转移"), b.progress.active?.actions)
        b = BattleFlow.endActivation(b)
        assertEquals("a2", b.progress.active?.member)
        assertEquals("A", b.progress.nextSide)
        b = BattleFlow.endActivation(b)
        assertEquals(null, b.progress.active)
        assertEquals("B", b.progress.nextSide)
        assertFalse(b.sideA.operatives.first { it.memberId == "a1" }.ready)
        assertFalse(b.sideA.operatives.first { it.memberId == "a2" }.ready)
    }

    @Test fun `blocked token and forced next activation are enforced`() {
        var b = battle()
        val side = b.sideA
        b = BattleFlow.side(b, side.copy(operatives = side.operatives.map {
            if (it.memberId == "a1") it.copy(tokens = listOf(OperativeToken(name = OperativeToken.BLOCKED, duration = TokenDuration.NEXT_ACTIVATION_END))) else it
        }))
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.activate(b, "A", "a1", 2, true) }
        b = BattleFlow.forceNextActivation(b, "A", "a3", "阴魂不散的注视")
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.activate(b, "A", "a2", 2, true) }
        b = BattleFlow.activate(b, "A", "a3", 2, true)
        assertEquals("", b.progress.mustActivate)
    }

    @Test fun `a reaction can take a second action when a rule is given`() {
        var b = battle()
        b = BattleFlow.side(b, b.sideA.copy(operatives = b.sideA.operatives.map { it.copy(ready = false, order = BattleOperativeState.ORDER_ENGAGE) }))
        b = BattleFlow.activate(b, "A", "a1", 2, true)
        assertTrue(b.progress.active!!.reaction)
        b = BattleFlow.action(b, "射击", 1, "")
        assertThrows(IllegalArgumentException::class.java) { BattleFlow.action(b, "转移", 1, "") }
        b = BattleFlow.action(b, "转移", 1, "亚空间残响")
        assertEquals(listOf("射击", "转移"), b.progress.active?.actions)
    }

    @Test fun `operatives can be added and change datacard`() {
        val team = RosterCatalog.team("IMP-AOD")
        val sniper = team.operatives.first { it.id == "aod_eliminator" || it.name.contains("狙击") }
        var b = BattleFlow.addOperative(battle(), "A", sniper, ready = false, countsForKill = false, reason = "变异害虫")
        val added = b.sideA.operatives.last()
        assertEquals(sniper.wounds, added.maxWounds)
        assertTrue(added.excludedFromKill)
        assertFalse(added.ready)
        // a1 lost 4 wounds; becomes a sniper keeping the lost wounds
        b = BattleFlow.side(b, b.sideA.copy(operatives = b.sideA.operatives.map { if (it.memberId == "a1") it.copy(currentWounds = 6) else it }))
        b = BattleFlow.replaceDatacard(b, "A", "a1", sniper, keepLost = true, reason = "变异")
        val changed = b.sideA.operatives.first { it.memberId == "a1" }
        assertEquals(sniper.id, changed.operativeId)
        assertEquals(sniper.wounds - 4, changed.currentWounds)
        assertTrue(changed.weapons.isNotEmpty())
    }

    @Test fun `granted weapons join the operative and can be removed`() {
        var b = BattleFlow.grantWeapon(battle(), "A", "a1",
            WeaponOption("support_macro", "宏炮", "4A · 2+ · 4/5 · 爆炸 2\"", WeaponKind.RANGED), "私掠者支援资产")
        val w = b.sideA.operatives.first { it.memberId == "a1" }.weapons.last()
        assertEquals("宏炮", w.name)
        assertEquals(2, w.hit)
        assertFalse(w.isMelee)
        assertThrows(IllegalArgumentException::class.java) {
            BattleFlow.grantWeapon(b, "A", "a1", WeaponOption("support_macro", "宏炮", "4A · 2+ · 4/5"), "x")
        }
        b = BattleFlow.removeWeapon(b, "A", "a1", w.id)
        assertTrue(b.sideA.operatives.first { it.memberId == "a1" }.weapons.none { it.id == w.id })
    }
}
