package com.example.helloworld

import kotlin.random.Random

/**
 * Generic dice expression from the core rules (p23): xD6 or xD3 (+ modifier), optionally
 * judged against a target "y+". D3 = D6 halved, rounded up. Each die may be rerolled once.
 */
data class DiceExpression(val count: Int = 1, val sides: Int = 6, val modifier: Int = 0, val target: Int? = null) {
    val label: String
        get() = buildString {
            if (count > 1) append(count)
            append("D$sides")
            if (modifier > 0) append("+$modifier") else if (modifier < 0) append(modifier)
            target?.let { append(" · ${it}+") }
        }

    companion object {
        /** Expressions that appear most often in core and team rules. */
        val presets = listOf(
            DiceExpression(1, 6), DiceExpression(1, 3), DiceExpression(2, 6), DiceExpression(2, 3),
            DiceExpression(1, 3, 3), DiceExpression(1, 3, 1), DiceExpression(1, 6, target = 3), DiceExpression(1, 6, target = 4)
        )
    }
}

/** One rolled die: the raw D6 face and the value it counts as (D3 halves it). */
data class RolledDie(val face: Int, val sides: Int, val rerolled: Boolean = false) {
    val value: Int get() = if (sides == 3) (face + 1) / 2 else face
}

data class DiceRoll(val expression: DiceExpression, val dice: List<RolledDie>) {
    val total: Int get() = dice.sumOf { it.value } + expression.modifier
    /** With a target each die is judged on its own (1 always fails); otherwise null. */
    val successes: Int? get() = expression.target?.let { t -> dice.count { it.face != 1 && it.value >= t } }

    fun describe(): String = buildString {
        append("${expression.label}：")
        append(dice.joinToString(" ") { if (it.rerolled) "${it.value}⟳" else "${it.value}" })
        val s = successes
        if (s != null) append(" → 成功 $s")
        else {
            if (expression.modifier != 0 || dice.size > 1) append(" → 合计 $total")
        }
    }
}

object DiceRolls {
    fun roll(expression: DiceExpression, random: Random = Random.Default): DiceRoll =
        DiceRoll(expression, List(expression.count.coerceIn(1, 20)) { RolledDie(random.nextInt(1, 7), expression.sides) })

    /** Reroll one die. Core rules: never more than once, and the new result must be kept. */
    fun reroll(roll: DiceRoll, index: Int, random: Random = Random.Default): DiceRoll {
        val die = roll.dice[index]
        require(!die.rerolled) { "每枚骰子最多重掷一次" }
        return roll.copy(dice = roll.dice.mapIndexed { i, d -> if (i == index) RolledDie(random.nextInt(1, 7), d.sides, true) else d })
    }

    /** Apply an animated reroll result to die [index]. */
    fun withReroll(roll: DiceRoll, index: Int, face: Int): DiceRoll {
        require(!roll.dice[index].rerolled) { "每枚骰子最多重掷一次" }
        return roll.copy(dice = roll.dice.mapIndexed { i, d -> if (i == index) RolledDie(face, d.sides, true) else d })
    }
}
