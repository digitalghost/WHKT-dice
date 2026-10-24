package com.example.helloworld

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * Global dice roller for any rule that asks for a random number (过热, D3 damage, team rules).
 * Pick an expression, roll, tap a die to reroll it once, then optionally write the result to
 * the battle log. [onRecord] is null where there is no battle to write to.
 */
class DiceSheet(private val ui: RecordUi, private val onRecord: ((String) -> Unit)? = null) {
    private val kit = CombatKit(ui)
    private val sound = DiceSoundPlayer(ui.context)

    fun open(initial: DiceExpression = last) {
        var expression = initial
        var roll: DiceRoll? = null
        var purpose = ""
        var rolling = false
        ui.dialog("掷骰", expanded = false) { box, sheet ->
            // One persistent tray: every roll and reroll is the same 3D animation used in combat.
            val tray = DiceTrayView(ui.context).apply {
                background = ui.bg(0xff101010.toInt(), 0xff343432.toInt())
                setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
                contentDescription = "骰盘"
            }
            box.addView(tray, LinearLayout.LayoutParams(-1, ui.dp(190)).apply { bottomMargin = ui.dp(8) })
            val content = ui.column()
            lateinit var fill: () -> Unit
            fun rerollDie(index: Int) {
                val current = roll ?: return
                if (rolling) return
                if (current.dice[index].rerolled) { ui.note("每枚骰子最多重掷一次"); return }
                rolling = true
                sound.play()
                tray.selectOnly(index)
                tray.rerollSelected { faces ->
                    rolling = false
                    roll = DiceRolls.withReroll(current, index, faces.single())
                    fill()
                }
            }
            fill = {
                content.removeAllViews()
                val current = roll
                if (current == null) {
                    content.addView(ui.text(expression.label, 30f, ui.accent, true).apply { gravity = Gravity.CENTER }, ui.wrap(12))
                } else {
                    val chips = current.dice.map { d ->
                        val reading = when {
                            current.expression.target == null -> DieReading.NORMAL
                            d.face != 1 && d.value >= current.expression.target -> DieReading.CRITICAL
                            else -> DieReading.FAIL
                        }
                        DieChip(d.value, face = if (d.sides == 3) "${d.value}" else null, reading = reading, rerolled = d.rerolled)
                    }
                    content.addView(kit.diceRow(chips, ui.ink, { i -> rerollDie(i) }, size = 52), ui.wrap(8))
                    val headline = current.successes?.let { "成功 $it / ${current.dice.size}" } ?: "合计 ${current.total}"
                    content.addView(ui.text(headline, 30f, ui.accent, true).apply { gravity = Gravity.CENTER }, ui.wrap(2))
                    content.addView(ui.text(current.describe() + "\n点骰子可重掷一次（不能保留原结果）", 12f, ui.muted).apply {
                        gravity = Gravity.CENTER
                    }, ui.wrap(12))
                }
                content.addView(kit.primaryButton(if (current == null) "掷 ${expression.label}" else "再掷一次") {
                    if (!rolling) {
                    rolling = true
                    last = expression
                    val rolledExpression = expression
                    sound.play()
                    tray.roll(rolledExpression.count) { faces ->
                        rolling = false
                        roll = DiceRoll(rolledExpression, faces.map { RolledDie(it, rolledExpression.sides) })
                        fill()
                    }
                    }
                }, ui.wrap(8))
                content.addView(ui.divider())
                content.addView(ui.text("常用", 12f, ui.muted, true), ui.wrap(4))
                content.addView(kit.tagRow(DiceExpression.presets.map { preset ->
                    val on = preset == expression
                    kit.tag(preset.label, if (on) ui.accent else ui.ink) { expression = preset; roll = null; tray.clearResults(); fill() }.apply {
                        minHeight = ui.dp(40); gravity = Gravity.CENTER; textSize = 14f
                        if (on) background = ui.bg(0xff3a2418.toInt(), ui.accent)
                    }
                }), ui.wrap(10))
                content.addView(kit.segmented("骰子", listOf("D6", "D3"), if (expression.sides == 6) 0 else 1) {
                    expression = expression.copy(sides = if (it == 0) 6 else 3, target = expression.target?.coerceAtMost(if (it == 0) 6 else 3)); roll = null; tray.clearResults(); fill()
                }, ui.wrap(6))
                content.addView(countRow(expression.count) { expression = expression.copy(count = it); roll = null; tray.clearResults(); fill() })
                content.addView(kit.stepper("加值", expression.modifier, -3, 6) { expression = expression.copy(modifier = it); roll = null; tray.clearResults(); fill() })
                val targets = listOf("无") + (2..expression.sides).map { "$it+" }
                content.addView(kit.segmented("目标值", targets, expression.target?.minus(1) ?: 0) {
                    expression = expression.copy(target = if (it == 0) null else it + 1); roll = null; tray.clearResults(); fill()
                }, ui.wrap(12))

                ui.refreshDialog()
            }
            fill()
            box.addView(content)
        }
    }

    /** Dice count: a plain number, unlike the signed modifier stepper. */
    private fun countRow(value: Int, onChange: (Int) -> Unit): View = ui.row().apply {
        addView(ui.text("数量", 13f, ui.ink), LinearLayout.LayoutParams(0, -2, 1f))
        addView(ui.button("−") { if (value > 1) onChange(value - 1) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        addView(ui.text("$value", 16f, ui.accent, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ui.dp(44), -2))
        addView(ui.button("+") { if (value < 10) onChange(value + 1) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
    }

    companion object {
        /** Last expression used, so the sheet opens where the player left it. */
        var last = DiceExpression()
    }
}

/** Small "🎲 掷骰" button for top bars. */
fun CombatKit.diceButton(ui: RecordUi, sheet: () -> DiceSheet): View =
    ui.text("掷骰", 13f, ui.ink, true).apply {
        gravity = Gravity.CENTER
        minHeight = ui.dp(44)
        setPadding(ui.dp(12), 0, ui.dp(12), 0)
        background = ui.bg(0xff242424.toInt())
        contentDescription = "通用掷骰"
        setOnClickListener { sheet().open() }
        layoutParams = LinearLayout.LayoutParams(-2, ui.dp(44)).apply { marginEnd = ui.dp(6) }
    }
