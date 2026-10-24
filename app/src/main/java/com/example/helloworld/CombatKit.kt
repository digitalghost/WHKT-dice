package com.example.helloworld

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** Verbatim core-rule passages (tools/extract_core_rules.py). Never summarised. */
object CoreRules {
    data class Passage(val title: String, val pages: List<Int>, val text: String)

    private var topics: Map<String, Passage>? = null
    private var weapons: Map<String, Passage>? = null

    private fun load(context: Context) {
        if (topics != null) return
        val root = JSONObject(context.assets.open("core_rules.json").bufferedReader().use { it.readText() })
        fun pages(o: JSONObject) = o.getJSONArray("pages").let { a -> List(a.length()) { a.getInt(it) } }
        val t = root.getJSONArray("topics")
        topics = List(t.length()) { t.getJSONObject(it) }.associate { o ->
            o.getString("id") to Passage(o.getString("title"), pages(o), o.getString("text"))
        }
        val w = root.getJSONArray("weaponRules")
        weapons = List(w.length()) { w.getJSONObject(it) }.associate { o ->
            o.getString("keyword") to Passage(o.getString("keyword"), pages(o), o.getString("text"))
        }
    }

    fun topic(context: Context, id: String): Passage? { load(context); return topics?.get(id) }

    /** "穿刺 1" / "关键穿刺1" / "致命 5+" / "眩晕" -> original weapon-rule entry. */
    fun weaponRule(context: Context, keyword: String): Passage? {
        load(context)
        val base = keyword.replace(" ", "").replace(Regex("[0-9+\"″“”]"), "")
            .replace("眩晕", "晕眩").replace("均衡", "平衡").removePrefix("关键").removeSuffix("轻型")
        return weapons?.get(base)
    }

    fun pageLabel(p: Passage) = "原文 · PDF 第 ${p.pages.joinToString("、")} 页"

    fun show(ui: RecordUi, id: String) {
        val passage = topic(ui.context, id) ?: return
        ui.dialog(passage.title, expanded = true) { box, _ ->
            box.addView(ui.text(pageLabel(passage), 11f, ui.muted, true), ui.wrap(8))
            box.addView(ui.text(passage.text, 14f).apply { setTextIsSelectable(true) }, ui.wrap())
        }
    }
}

enum class DieRole { ATTACK, DEFENCE }

/** One die as shown in a chip row. value == null means a retained success without a face. */
data class DieChip(
    val value: Int?,
    val face: String? = null,
    val reading: DieReading,
    val rerolled: Boolean = false,
    val edited: Boolean = false,
    val highlight: Boolean = false,
    val dim: Boolean = false
)

/** Shared compact controls for the combat and battle screens. */
class CombatKit(private val ui: RecordUi) {
    val gold = 0xffe2b347.toInt()
    val steel = 0xff6aa0c4.toInt()
    val danger = 0xffd34b45.toInt()
    private val line = 0xff343432.toInt()

    fun sideColor(sideId: String) = if (sideId == BattleRules.SIDE_A) ui.accent else steel
    fun sideFill(sideId: String) = if (sideId == BattleRules.SIDE_A) 0xff211914.toInt() else 0xff141c22.toInt()
    fun sideStroke(sideId: String) = if (sideId == BattleRules.SIDE_A) 0xff6c3d24.toInt() else 0xff35566a.toInt()

    fun topBar(
        title: String,
        subtitle: String,
        undoLabel: String?,
        onBack: () -> Unit,
        onUndo: () -> Unit,
        trailing: View? = null
    ): View = ui.row().apply {
        setPadding(ui.dp(4), ui.dp(4), ui.dp(8), ui.dp(4))
        minimumHeight = ui.dp(56)
        addView(iconButton("‹", "返回", onBack), LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        addView(ui.column().apply {
            addView(ui.text(title, 18f, ui.ink, true).apply { maxLines = 1 }, ui.wrap(1))
            if (subtitle.isNotBlank()) addView(ui.text(subtitle, 12f, ui.muted, true).apply { maxLines = 1 }, ui.wrap(0))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { addView(it) }
        if (undoLabel != null) {
            addView(ui.text("↶ 撤回", 13f, ui.accent, true).apply {
                gravity = Gravity.CENTER
                minHeight = ui.dp(44)
                setPadding(ui.dp(12), 0, ui.dp(12), 0)
                background = ui.bg(0xff242424.toInt())
                contentDescription = "撤回：$undoLabel"
                setOnClickListener { onUndo() }
                setOnLongClickListener { ui.note("下一步撤回：$undoLabel"); true }
            }, LinearLayout.LayoutParams(-2, ui.dp(44)).apply { marginStart = ui.dp(6) })
        }
    }

    fun iconButton(symbol: String, description: String, action: () -> Unit) = ui.text(symbol, 24f, ui.ink, true).apply {
        gravity = Gravity.CENTER
        contentDescription = description
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33ee7540), null, ui.bg(ui.ink)
        )
        setOnClickListener { action() }
    }

    /** A labelled row of always-visible choices. Disabled options show why via [reason]. */
    fun segmented(
        label: String,
        options: List<String>,
        selected: Int,
        enabled: (Int) -> Boolean = { true },
        reason: String? = null,
        info: (() -> Unit)? = null,
        onSelect: (Int) -> Unit
    ): View = ui.column().apply {
        addView(ui.row().apply {
            addView(ui.text(label, 12f, ui.muted, true), LinearLayout.LayoutParams(-2, -2))
            if (reason != null) addView(tag(reason, ui.orange), LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(6) })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            if (info != null) addView(infoButton(info))
        }, ui.wrap(4))
        addView(ui.row().apply {
            options.forEachIndexed { index, option ->
                val on = index == selected
                val usable = enabled(index)
                addView(ui.text(option, 14f, if (on) Color.BLACK else ui.ink, true).apply {
                    gravity = Gravity.CENTER
                    minHeight = ui.dp(44)
                    setPadding(ui.dp(4), 0, ui.dp(4), 0)
                    background = ui.bg(if (on) ui.accent else 0xff222222.toInt(), if (on) ui.accent else line)
                    alpha = if (usable) 1f else .3f
                    isEnabled = usable
                    contentDescription = "$label：$option${if (on) "，已选" else ""}"
                    setOnClickListener { if (!on) onSelect(index) }
                }, LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply {
                    if (index < options.lastIndex) marginEnd = ui.dp(4)
                })
            }
        })
    }

    fun infoButton(action: () -> Unit) = ui.text("ⓘ", 16f, ui.muted).apply {
        gravity = Gravity.CENTER
        minWidth = ui.dp(36)
        minHeight = ui.dp(32)
        contentDescription = "规则原文"
        setOnClickListener { action() }
    }

    fun tag(text: String, color: Int = ui.muted, onClick: (() -> Unit)? = null) = ui.text(text, 11f, color, true).apply {
        setPadding(ui.dp(7), ui.dp(3), ui.dp(7), ui.dp(3))
        background = GradientDrawable().apply {
            cornerRadius = ui.dp(10).toFloat()
            setStroke(ui.dp(1), color)
            setColor(Color.TRANSPARENT)
        }
        onClick?.let { action -> setOnClickListener { action() } }
    }

    fun tagRow(tags: List<View>): View = HorizontalScrollView(ui.context).apply {
        isHorizontalScrollBarEnabled = false
        addView(ui.row().apply {
            tags.forEach { addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) }) }
        })
    }

    /** Big readable dice; colour = reading, corner marks = rerolled / edited / retained. */
    fun diceRow(
        dice: List<DieChip>,
        sideColor: Int,
        onTap: ((Int) -> Unit)? = null,
        size: Int = 48
    ): View = HorizontalScrollView(ui.context).apply {
        isHorizontalScrollBarEnabled = false
        addView(ui.row().apply {
            dice.forEachIndexed { index, die -> addView(dieView(die, sideColor, onTap?.let { { it(index) } }, size),
                LinearLayout.LayoutParams(ui.dp(size), ui.dp(size)).apply { marginEnd = ui.dp(6) }) }
            if (dice.isEmpty()) addView(ui.text("—", 14f, ui.muted))
        })
    }

    private fun dieView(die: DieChip, sideColor: Int, onTap: (() -> Unit)?, size: Int): View {
        val border = when (die.reading) {
            DieReading.CRITICAL -> gold
            DieReading.NORMAL -> sideColor
            DieReading.FAIL, DieReading.DISCARDED -> 0xff555553.toInt()
        }
        val face = die.face ?: die.value?.toString() ?: "◆"
        val marks = buildString {
            if (die.rerolled) append("⟳")
            if (die.edited) append("✎")
        }
        return android.widget.FrameLayout(ui.context).apply {
            background = GradientDrawable().apply {
                cornerRadius = ui.dp(8).toFloat()
                setColor(if (die.highlight) 0xff3a2a1c.toInt() else 0xff1b1b1b.toInt())
                setStroke(ui.dp(if (die.reading == DieReading.CRITICAL || die.highlight) 3 else 2), if (die.highlight) Color.WHITE else border)
            }
            alpha = when {
                die.dim -> .35f
                die.reading == DieReading.FAIL || die.reading == DieReading.DISCARDED -> .5f
                else -> 1f
            }
            addView(ui.text(face, if (face.length > 1) 15f else if (size >= 44) 22f else 16f, if (die.reading == DieReading.CRITICAL) gold else ui.ink, true).apply {
                gravity = Gravity.CENTER
                if (die.reading == DieReading.DISCARDED) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }, android.widget.FrameLayout.LayoutParams(-1, -1))
            if (marks.isNotEmpty()) addView(ui.text(marks, 10f, ui.muted, true).apply {
                setPadding(0, ui.dp(1), ui.dp(3), 0)
            }, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
            contentDescription = "骰子 $face，${readingLabel(die.reading)}${if (die.rerolled) "，已重投" else ""}"
            if (onTap != null) {
                isClickable = true
                setOnClickListener { onTap() }
            }
        }
    }

    fun readingLabel(reading: DieReading) = when (reading) {
        DieReading.CRITICAL -> "暴击"
        DieReading.NORMAL -> "普通成功"
        DieReading.FAIL -> "失败"
        DieReading.DISCARDED -> "已舍弃"
    }

    fun tally(readings: List<DieReading>, extraNormals: Int = 0, extraCriticals: Int = 0): String {
        val c = readings.count { it == DieReading.CRITICAL } + extraCriticals
        val n = readings.count { it == DieReading.NORMAL } + extraNormals
        val f = readings.count { it == DieReading.FAIL }
        return "暴击 $c · 普通 $n · 失败 $f"
    }

    /**
     * Log with undone entries struck through, newest first. Each undone entry carries the log
     * size at the moment it was reverted, so it is shown where it happened.
     */
    fun logView(entries: List<String>, undone: List<Pair<Int, String>>, collapsedCount: Int = 4): View = ui.column().apply {
        val all = mutableListOf<Pair<String, Boolean>>()
        entries.forEachIndexed { index, text ->
            undone.filter { it.first == index }.forEach { all += it.second to true }
            all += text to false
        }
        undone.filter { it.first >= entries.size }.forEach { all += it.second to true }
        var expanded = false
        val body = ui.column()
        fun fill() {
            body.removeAllViews()
            val shown = if (expanded) all.asReversed() else all.asReversed().take(collapsedCount)
            shown.forEach { (text, struck) -> body.addView(logLine(text, struck), ui.wrap(3)) }
        }
        val header = ui.text("过程记录 · ${entries.size}", 12f, ui.muted, true).apply {
            minHeight = ui.dp(36)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { expanded = !expanded; text = if (expanded) "过程记录 · 收起" else "过程记录 · ${entries.size}"; fill() }
        }
        addView(header, ui.wrap(2))
        addView(body)
        fill()
    }

    fun logLine(text: String, struck: Boolean): TextView {
        val view = ui.text("", 12f, if (struck) 0xff77756f.toInt() else ui.muted)
        view.text = if (struck) SpannableString(text).apply { setSpan(StrikethroughSpan(), 0, text.length, 0) } else text
        return view
    }

    /** A bottom-sheet style list of actions; disabled entries stay visible with a reason. */
    /** [stack] opens the next page above this sheet, so closing it returns here instead of to the screen. */
    data class SheetAction(val label: String, val enabled: Boolean = true, val primary: Boolean = false, val stack: Boolean = false, val run: () -> Unit)

    fun actionSheet(title: String, header: View? = null, actions: List<SheetAction>) {
        ui.dialog(title) { box, sheet ->
            header?.let { box.addView(it, ui.wrap(10)) }
            actions.forEach { action ->
                box.addView(ui.button(action.label, action.primary) {
                    if (action.enabled && action.stack) action.run()
                    else if (action.enabled) {
                        sheet.dismiss()
                        // Run after the dialog host's dismiss callback, otherwise a sheet opened
                        // by the action is cleared by that callback and leaves a stuck window.
                        android.os.Handler(android.os.Looper.getMainLooper()).post { action.run() }
                    }
                }.apply {
                    isEnabled = action.enabled
                    alpha = if (action.enabled) 1f else .35f
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                }, ui.wrap(2))
            }
        }
    }

    fun primaryButton(title: String, enabled: Boolean = true, action: () -> Unit) = ui.button(title, true, action).apply {
        textSize = 15f
        minHeight = ui.dp(56)
        isEnabled = enabled
        alpha = if (enabled) 1f else .35f
    }

    fun secondaryButton(title: String, enabled: Boolean = true, action: () -> Unit) = ui.button(title, false, action).apply {
        minHeight = ui.dp(52)
        isEnabled = enabled
        alpha = if (enabled) 1f else .35f
    }

    fun actionBar(primary: View, vararg secondary: View): View = ui.row().apply {
        secondary.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8) }) }
        addView(primary, LinearLayout.LayoutParams(0, -2, if (secondary.isEmpty()) 1f else 1.6f))
    }

    // ------------------------------------------------------------ shared page building blocks

    /** Section title in bold; optional trailing view (count, link) on the right. */
    fun section(title: String, trailing: View? = null): View = ui.row().apply {
        minimumHeight = ui.dp(36)
        addView(ui.text(title, 16f, ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { addView(it) }
    }

    /** "APL 3" style chips: label muted, value bold. */
    fun stats(vararg pairs: Pair<String, String>): View = ui.row().apply {
        pairs.forEach { (label, value) ->
            addView(ui.row().apply {
                addView(ui.text(label, 11f, ui.muted, true), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(3) })
                addView(ui.text(value, 14f, ui.ink, true))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(12) })
        }
    }

    /** A tappable list row: leading view, bold title, subtitle, trailing view. */
    fun listRow(
        title: String,
        subtitle: String? = null,
        leading: View? = null,
        trailing: View? = null,
        titleColor: Int = ui.ink,
        stroke: Int = 0xff2c2c2a.toInt(),
        onClick: (() -> Unit)? = null,
        extra: View? = null
    ): View = ui.row().apply {
        minimumHeight = ui.dp(64)
        setPadding(ui.dp(12), ui.dp(10), ui.dp(8), ui.dp(10))
        background = ui.bg(0xff1a1a1a.toInt(), stroke)
        leading?.let { addView(it) }
        addView(ui.column().apply {
            addView(ui.text(title, 16f, titleColor, true).apply { maxLines = 1 }, ui.wrap(if (subtitle == null) 0 else 3))
            subtitle?.let { addView(ui.text(it, 12f, ui.muted).apply { maxLines = 2 }, ui.wrap(0)) }
            extra?.let { addView(it, ui.wrap(0).apply { topMargin = ui.dp(6) }) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { addView(it) }
        if (onClick != null) {
            isClickable = true
            foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22ee7540), null, ui.bg(ui.ink)
            )
            setOnClickListener { onClick() }
        }
    }

    fun emblem(teamId: String, size: Int = 40): View = ui.portrait(ui.teamArt(teamId), size)

    fun chevron(): View = ui.text("›", 22f, ui.muted, true).apply { setPadding(ui.dp(6), 0, ui.dp(4), 0) }

    /** Step indicator for wizards: filled dots for done steps, label for the current one. */
    fun progress(step: Int, labels: List<String>): View = ui.row().apply {
        setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(10))
        labels.forEachIndexed { index, label ->
            val color = when {
                index == step -> ui.accent
                index < step -> ui.ink
                else -> 0xff5c5a56.toInt()
            }
            addView(ui.text("${if (index < step) "✓" else "${index + 1}"} $label", 13f, color, index <= step).apply { maxLines = 1 })
            if (index < labels.lastIndex) addView(View(context).apply { setBackgroundColor(0xff3a3936.toInt()) },
                LinearLayout.LayoutParams(0, ui.dp(1), 1f).apply { marginStart = ui.dp(6); marginEnd = ui.dp(6) })
        }
    }

    fun emptyState(title: String, action: String? = null, onAction: (() -> Unit)? = null): View = ui.column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(ui.dp(16), ui.dp(40), ui.dp(16), ui.dp(40))
        addView(ui.text(title, 16f, ui.muted, true).apply { gravity = Gravity.CENTER }, ui.wrap(16))
        if (action != null && onAction != null) addView(primaryButton(action) { onAction() }, LinearLayout.LayoutParams(ui.dp(220), -2))
    }

    /** Page scaffold: top bar, scrolling body, fixed bottom action bar (hidden when empty). */
    class Page(val root: LinearLayout, val body: LinearLayout, val bar: LinearLayout, val scroll: android.widget.ScrollView)

    fun page(activity: android.app.Activity, top: View, header: View? = null): Page {
        val root = ui.column().apply { setBackgroundColor(ui.base) }
        root.addView(top)
        header?.let { root.addView(it) }
        root.addView(View(activity).apply { setBackgroundColor(0xff2a2a28.toInt()) }, LinearLayout.LayoutParams(-1, ui.dp(1)))
        val body = ui.column(12)
        val scroll = android.widget.ScrollView(activity).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = ui.column().apply {
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))
            setBackgroundColor(0xff141414.toInt())
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        activity.setContentView(root)
        return Page(root, body, bar, scroll)
    }

    fun finishBar(page: Page) {
        if (page.bar.childCount == 0) page.bar.visibility = View.GONE
    }

    fun stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit): View = ui.row().apply {
        addView(ui.text(label, 13f, ui.ink), LinearLayout.LayoutParams(0, -2, 1f))
        addView(ui.button("−") { if (value > min) onChange(value - 1) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        addView(ui.text(if (value > 0) "+$value" else value.toString(), 16f, if (value == 0) ui.muted else ui.accent, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ui.dp(44), -2))
        addView(ui.button("+") { if (value < max) onChange(value + 1) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
    }
}

/**
 * Temporary changes for one resolution, used to apply team rules without team-specific code.
 * Keyword additions reuse the standard weapon-rule parser, so the engine resolves them normally.
 */
data class CombatModifiers(
    val extraKeywords: List<String> = emptyList(),
    val removedKeywords: Set<String> = emptySet(),
    val attacks: Int = 0,
    val hit: Int = 0,
    val normalDamage: Int = 0,
    val criticalDamage: Int = 0,
    val save: Int = 0,
    val defenceDice: Int = 0,
    val source: String = ""
) {
    val isEmpty: Boolean get() = this.copy(source = "") == CombatModifiers()

    fun keywords(base: List<String>): List<String> = base.filterNot { it in removedKeywords } + extraKeywords

    fun summary(): List<String> = buildList {
        extraKeywords.forEach { add("+$it") }
        removedKeywords.forEach { add("−$it") }
        fun signed(label: String, v: Int) { if (v != 0) add("$label${if (v > 0) "+" else ""}$v") }
        signed("攻击", attacks)
        // Hit and save: a lower target number is better, so show "改善/恶化".
        if (hit != 0) add(if (hit < 0) "命中改善${-hit}" else "命中恶化$hit")
        signed("普通伤害", normalDamage)
        signed("暴击伤害", criticalDamage)
        if (save != 0) add(if (save < 0) "豁免改善${-save}" else "豁免恶化$save")
        signed("防御骰", defenceDice)
    }

    companion object {
        /** Standard weapon rules a team rule can grant (p92-95). */
        val grantable = listOf(
            "精准 1", "精准 2", "平衡", "无休", "毫不留情", "致命 5+", "致命 4+", "重击", "撕裂", "严重",
            "毁灭 1", "毁灭 2", "毁灭 3", "穿刺 1", "穿刺 2", "关键穿刺 1", "关键穿刺 2", "集中", "晕眩",
            "残暴", "震荡"
        )
    }
}

/** Sheet editing [CombatModifiers]; [shooting] adds save / defence-dice rows. */
fun CombatKit.modifierSheet(
    ui: RecordUi,
    title: String,
    weaponKeywords: List<String>,
    current: CombatModifiers,
    shooting: Boolean,
    onApply: (CombatModifiers) -> Unit
) {
    var draft = current
    ui.dialog(title) { box, sheet ->
        val content = ui.column()
        fun render() {
            content.removeAllViews()
            val source = ui.input(draft.source, "来源（选填）：计谋、能力或装备名")
            source.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { draft = draft.copy(source = s?.toString().orEmpty()) }
            })
            content.addView(source, ui.wrap(10))
            if (weaponKeywords.isNotEmpty()) {
                content.addView(ui.text("武器自带（点按停用）", 12f, ui.muted, true), ui.wrap(4))
                content.addView(flow(ui.context, weaponKeywords.map { k ->
                    val off = k in draft.removedKeywords
                    tag(if (off) "✕ $k" else k, if (off) ui.muted else ui.ink) {
                        draft = draft.copy(removedKeywords = if (off) draft.removedKeywords - k else draft.removedKeywords + k)
                        render()
                    }
                }), ui.wrap(10))
            }
            content.addView(ui.text("获得武器规则", 12f, ui.muted, true), ui.wrap(4))
            content.addView(flow(ui.context, CombatModifiers.grantable.map { k ->
                val on = k in draft.extraKeywords
                tag(if (on) "✓ $k" else k, if (on) ui.accent else ui.muted) {
                    draft = draft.copy(extraKeywords = if (on) draft.extraKeywords - k else draft.extraKeywords + k)
                    render()
                }
            }), ui.wrap(10))
            content.addView(ui.text("属性", 12f, ui.muted, true), ui.wrap(2))
            content.addView(stepper("攻击骰数量", draft.attacks, -6, 6) { draft = draft.copy(attacks = it); render() })
            content.addView(stepper("命中（+ 为恶化）", draft.hit, -3, 3) { draft = draft.copy(hit = it); render() })
            content.addView(stepper("普通伤害", draft.normalDamage, -6, 6) { draft = draft.copy(normalDamage = it); render() })
            content.addView(stepper("暴击伤害", draft.criticalDamage, -6, 6) { draft = draft.copy(criticalDamage = it); render() })
            if (shooting) {
                content.addView(stepper("目标豁免（+ 为恶化）", draft.save, -3, 3) { draft = draft.copy(save = it); render() })
                content.addView(stepper("防御骰数量", draft.defenceDice, -3, 3) { draft = draft.copy(defenceDice = it); render() })
            }
            ui.refreshDialog()
        }
        render()
        box.addView(content, ui.wrap(10))
        box.addView(ui.row().apply {
            addView(ui.button("清空") { draft = CombatModifiers(); render() }, ui.weight())
            addView(ui.button("应用", true) { sheet.dismiss(); onApply(draft) }, LinearLayout.LayoutParams(0, -2, 1.6f))
        })
    }
}

private fun flow(context: Context, tags: List<View>): View {
    // Rows of three keep the tags large enough to tap on a phone held over the table.
    val density = context.resources.displayMetrics.density
    val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    tags.chunked(3).forEach { group ->
        column.addView(LinearLayout(context).apply {
            group.forEach {
                (it as? TextView)?.apply {
                    minHeight = (40 * density).toInt()
                    gravity = Gravity.CENTER
                    textSize = 13f
                }
                addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = (8 * density).toInt(); bottomMargin = (8 * density).toInt()
                })
            }
            repeat(3 - group.size) { addView(View(context), LinearLayout.LayoutParams(0, 0, 1f)) }
        })
    }
    return column
}
