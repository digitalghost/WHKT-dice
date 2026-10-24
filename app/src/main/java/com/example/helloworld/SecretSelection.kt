package com.example.helloworld

import android.app.Dialog
import android.widget.LinearLayout

object SecretSelection {
    fun open(
        ui: RecordUi,
        playerName: String,
        subject: String,
        buildPrivateContent: (LinearLayout, Dialog) -> Unit
    ) {
        ui.dialog("$playerName · 私密选择", expanded = true) { box, dialog ->
            box.addView(ui.heading("请将设备交给 $playerName", 24f), ui.wrap(8))
            box.addView(ui.text("另一位玩家请回避屏幕", 15f, ui.muted, true), ui.wrap(20))
            box.addView(ui.button("我是 $playerName，继续", true) {
                box.removeAllViews()
                box.addView(ui.text("$playerName · $subject", 18f, ui.accent, true), ui.wrap(12))
                buildPrivateContent(box, dialog)
                ui.refreshDialog()
            }, ui.wrap())
        }
    }

    fun primaryStatus(selected: ScoreType?): String =
        if (selected == null) "未选择" else "已选择（隐藏）"

    fun tacticalStatus(selected: String, revealed: Boolean): String = when {
        selected.isBlank() -> "未选择"
        revealed -> "已公开：$selected"
        else -> "已选择（隐藏）"
    }

    fun tacticalDetailsArePublic(selected: String, revealed: Boolean, battleCompleted: Boolean): Boolean =
        selected.isNotBlank() && (revealed || battleCompleted)
}
