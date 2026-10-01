package com.example.helloworld

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
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
        bold: Boolean = false
    ): TextView = ui.text(profile, size, color, bold).also {
        linkify(it, ui, weaponName, profile, keywords)
    }

    fun linkify(
        view: TextView,
        ui: RecordUi,
        weaponName: String,
        value: CharSequence,
        keywords: List<String>
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
                override fun onClick(widget: View) = show(ui, weaponName, distinct)

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

    fun show(ui: RecordUi, weaponName: String, keywords: List<String>) {
        val distinct = keywords.map(String::trim).filter(String::isNotBlank).distinct()
        ui.dialog("$weaponName · 关键字规则") { box, _ ->
            if (distinct.isEmpty()) {
                box.addView(ui.text("这件武器没有特殊关键字。", color = ui.muted), ui.wrap())
                return@dialog
            }
            distinct.forEachIndexed { index, keyword ->
                if (index > 0) box.addView(ui.divider())
                box.addView(ui.text(keyword, 16f, ui.accent, true), ui.wrap(5))
                box.addView(
                    ui.text(
                        WeaponRuleFocus.definition(keyword)?.description
                            ?: "该关键字尚无独立文字说明，请以对应小队原卡为准。",
                        13f
                    ),
                    ui.wrap(10)
                )
            }
        }
    }
}
