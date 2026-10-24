package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleLogFormatTest {
    private val lexicon = mapOf(
        "玩家 A" to BattleLogFormat.Role.SIDE_A,
        "恶角兽铁角头领" to BattleLogFormat.Role.SIDE_A,
        "时间巫师" to BattleLogFormat.Role.SIDE_B,
        "腐化手枪" to BattleLogFormat.Role.WEAPON,
        "手枪" to BattleLogFormat.Role.WEAPON
    )

    @Test fun classifiesCommonMessages() {
        assertEquals(BattleLogFormat.Kind.SHOOT, BattleLogFormat.kind("射击：A（枪）→ B，伤害 3\n过程：x", false))
        assertEquals(BattleLogFormat.Kind.SCORE, BattleLogFormat.kind("玩家 A：关键行动 2 VP", false))
        assertEquals(BattleLogFormat.Kind.CP, BattleLogFormat.kind("玩家 B：CP 3 → 2", false))
        assertEquals(BattleLogFormat.Kind.UNDO, BattleLogFormat.kind("↶ 撤回：转移", false))
        assertEquals(BattleLogFormat.Kind.NOTE, BattleLogFormat.kind("对手忘了掩护", true))
        assertEquals(BattleLogFormat.Kind.TURN, BattleLogFormat.kind("第 2 转折点开始，玩家 A 拥有先手权", false))
    }

    @Test fun splitsProcessSteps() {
        val (head, steps) = BattleLogFormat.split("射击：A → B\n过程：攻击骰：2、5；严重改暴击；射击结算完成")
        assertEquals("射击：A → B", head)
        assertEquals(listOf("攻击骰：2、5", "严重改暴击", "射击结算完成"), steps)
    }

    @Test fun longerTermsWinAndNumbersAreTagged() {
        val text = "射击：恶角兽铁角头领（腐化手枪）→ 时间巫师，伤害 3；玩家 A：2 VP，CP 3 → 2"
        val spans = BattleLogFormat.spans(text, lexicon)
        fun at(term: String) = spans.single { text.substring(it.start, it.end) == term }.role
        assertEquals(BattleLogFormat.Role.WEAPON, at("腐化手枪"))
        assertTrue(spans.none { text.substring(it.start, it.end) == "手枪" })
        assertEquals(BattleLogFormat.Role.SIDE_B, at("时间巫师"))
        assertEquals(BattleLogFormat.Role.DANGER, at("伤害 3"))
        assertEquals(BattleLogFormat.Role.SCORE, at("2 VP"))
        assertEquals(BattleLogFormat.Role.CP, at("CP 3 → 2"))
    }
}
