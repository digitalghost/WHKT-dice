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
            box.addView(ui.heading("请将设备交给 $playerName", 22f), ui.wrap(8))
            box.addView(
                ui.text("另一位玩家请回避屏幕。确认只有当前玩家能看到后，再进入${subject}选择。", 15f),
                ui.wrap(12)
            )
            box.addView(
                ui.text("完成选择后，具体内容会立即隐藏；公共界面只显示选择状态。", 12f, ui.muted),
                ui.wrap(20)
            )
            box.addView(ui.button("我已独自查看，继续", true) {
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
