package com.example.helloworld

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Shared weapon-keyword links and the focused, weapon-only rules sheet. */
object WeaponRulesUi {
    fun profile(
        ui: RecordUi,
        weaponName: String,
        profile: String,
        keywords: List<String> = WeaponProfileParser.parse(profile).keywords,
        size: Float = 12f,
        color: Int = ui.muted,
        bold: Boolean = false,
        teamId: String? = null
    ): TextView = ui.text(profile, size, color, bold).also {
        linkify(it, ui, weaponName, profile, keywords, teamId)
    }

    fun linkify(
        view: TextView,
        ui: RecordUi,
        weaponName: String,
        value: CharSequence,
        keywords: List<String>,
        teamId: String? = null
    ) {
        val distinct = keywords.map(String::trim).filter(String::isNotBlank).distinct()
        if (distinct.isEmpty()) {
            view.text = value
            return
        }
        val text = SpannableString(value)
        var cursor = 0
        distinct.forEach { keyword ->
            val start = value.indexOf(keyword, cursor).takeIf { it >= 0 }
                ?: value.indexOf(keyword).takeIf { it >= 0 }
                ?: return@forEach
            val end = start + keyword.length
            text.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = show(ui, weaponName, distinct, teamId = teamId)

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ui.accent
                    ds.isUnderlineText = true
                }
            }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            cursor = end
        }
        view.text = text
        view.movementMethod = LinkMovementMethod.getInstance()
        view.highlightColor = Color.TRANSPARENT
        view.linksClickable = true
        view.contentDescription = "$weaponName，点按关键字查看该武器全部关键字规则"
    }

    /** When a core weapon rule takes effect in a fight (timing notes on PDF p92-94). */
    fun timing(keyword: String): String? {
        val k = keyword.replace(" ", "").replace(Regex("^[0-9]+[\"″“”]"), "")
        return when {
            k.startsWith("精准") -> "掷骰前：不掷骰保留为普通成功"
            k.startsWith("平衡") || k.startsWith("均衡") || k.startsWith("无休") || k.startsWith("毫不留情") -> "重投步骤：点攻击骰使用"
            k.startsWith("致命") -> "判定攻击骰：自动生效"
            k.startsWith("重击") || k.startsWith("撕裂") || k.startsWith("严重") -> "保留攻击骰：需要手动应用"
            k.startsWith("毁灭") || k.startsWith("晕眩") || k.startsWith("眩晕") -> "保留暴击后：自动生效"
            k.startsWith("关键穿刺") || k.startsWith("穿刺") || k.startsWith("集中") -> "防御骰之前：自动生效"
            k.startsWith("残暴") -> "抵挡 / 格挡：只能用暴击"
            k.startsWith("震荡") -> "近战结算：首次用暴击出击时"
            k.startsWith("过热") -> "行动结束后：掷 D6 检定"
            k.startsWith("爆炸物") -> "攻击结算后自身丧失行动能力"
            k.startsWith("爆炸") || k.startsWith("洪流") -> "选择目标：逐个目标分别射击"
            k.startsWith("重型") -> "移动过的激活中不能使用"
            k.startsWith("范围") || k.startsWith("追踪") || k.startsWith("安静") -> "选择目标时"
            k.startsWith("有限") -> "整场战斗的使用次数"
            else -> null
        }
    }

    /**
     * Weapon keywords for the current step. Every tag opens the rule text; [active] marks the
     * rules the player can or must use right now.
     */
    fun panel(ui: RecordUi, kit: CombatKit, weaponName: String, keywords: List<String>, teamId: String? = null, active: (String) -> Boolean = { false }): View {
        val distinct = keywords.map(String::trim).filter(String::isNotBlank).distinct()
        val tags = mutableListOf<View>(ui.text(weaponName, 12f, ui.muted, true))
        if (distinct.isEmpty()) tags += ui.text("无特殊规则", 12f, ui.muted)
        distinct.forEach { k ->
            tags += kit.tag(if (active(k)) "● $k" else k, if (active(k)) ui.accent else ui.ink) { show(ui, weaponName, listOf(k), distinct, teamId = teamId) }
                .apply { minHeight = ui.dp(32); gravity = Gravity.CENTER_VERTICAL; textSize = 12f }
        }
        return kit.tagRow(tags)
    }

    /**
     * An optional rule the player applies by tapping. Nothing changes the dice until the
     * player chooses it, and the row states exactly what it will change.
     */
    fun choice(
        ui: RecordUi,
        kit: CombatKit,
        weaponName: String,
        keyword: String,
        effect: String,
        applied: Boolean,
        teamId: String? = null,
        onToggle: () -> Unit
    ): View = ui.row().apply {
        setPadding(ui.dp(12), ui.dp(8), ui.dp(8), ui.dp(8))
        background = ui.bg(if (applied) 0xff2a1d15.toInt() else 0xff1a1a1a.toInt(), if (applied) ui.accent else 0xff343432.toInt())
        addView(ui.column().apply {
            addView(ui.text(keyword + " ⓘ", 15f, ui.accent, true).apply {
                setOnClickListener { show(ui, weaponName, listOf(keyword), teamId = teamId) }
                contentDescription = "查看${keyword}规则"
            }, ui.wrap(2))
            addView(ui.text(effect, 13f, if (applied) ui.ink else 0xffc9c7c2.toInt()), ui.wrap(0))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ui.button(if (applied) "✓ 已应用" else "应用", !applied) { onToggle() }.apply {
            contentDescription = if (applied) "取消应用${keyword}" else "应用${keyword}"
        }, LinearLayout.LayoutParams(ui.dp(96), ui.dp(48)))
    }

    fun show(ui: RecordUi, weaponName: String, keywords: List<String>, all: List<String> = emptyList(), teamId: String? = null) {
        val distinct = keywords.map(String::trim).filter(String::isNotBlank).distinct()
        ui.dialog("$weaponName · 关键字规则") { box, _ ->
            if (distinct.isEmpty()) {
                box.addView(ui.text("这件武器没有特殊关键字。", color = ui.muted), ui.wrap())
                return@dialog
            }
            distinct.forEachIndexed { index, keyword ->
                if (index > 0) box.addView(ui.divider())
                box.addView(ui.text(keyword, 16f, ui.accent, true), ui.wrap(5))
                timing(keyword)?.let { box.addView(ui.text("生效时机：$it", 12f, ui.orange, true), ui.wrap(5)) }
                box.addView(
                    ui.text(
                        WeaponRuleFocus.definition(keyword, teamId)?.description
                            ?: "该关键字尚无独立文字说明，请以对应小队原卡为准。",
                        13f
                    ),
                    ui.wrap(6)
                )
                CoreRules.weaponRule(ui.context, keyword, teamId)?.let { passage ->
                    box.addView(ui.text(CoreRules.pageLabel(passage), 11f, ui.muted, true), ui.wrap(3))
                    box.addView(ui.text(passage.text, 13f, ui.muted).apply {
                        setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
                        background = ui.bg(0xff141414.toInt(), 0xff343432.toInt())
                        setTextIsSelectable(true)
                    }, ui.wrap(10))
                }
            }
            val others = all.map(String::trim).filter { it.isNotBlank() && it !in distinct }.distinct()
            if (others.isNotEmpty()) box.addView(ui.link("查看这件武器的全部规则（共 ${others.size + distinct.size} 条）›") {
                show(ui, weaponName, distinct + others, teamId = teamId)
            }, ui.wrap(6))
        }
    }
}
