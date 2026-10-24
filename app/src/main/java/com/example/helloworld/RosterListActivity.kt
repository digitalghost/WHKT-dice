package com.example.helloworld

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saved rosters. Tap a row to edit; ⋯ copies or deletes. */
class RosterListActivity : AppCompatActivity() {
    private lateinit var ui: RecordUi
    private lateinit var kit: CombatKit
    private lateinit var store: RosterStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = RecordUi(this)
        kit = CombatKit(ui)
        store = RosterStore(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::ui.isInitialized) render()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun render() {
        val rosters = store.all()
        val top = kit.topBar("我的小队", "${rosters.size} 支", null, ::finish, {},
            trailing = ui.button("＋ 新建", true) { chooseTeam() }.apply {
                layoutParams = LinearLayout.LayoutParams(-2, ui.dp(48))
            })
        val page = kit.page(this, top)
        if (rosters.isEmpty()) {
            page.body.addView(kit.emptyState("还没有小队", "＋ 新建小队") { chooseTeam() })
        }
        val tablet = resources.configuration.smallestScreenWidthDp >= 600
        rosters.chunked(if (tablet) 2 else 1).forEach { group ->
            page.body.addView(ui.row().apply {
                gravity = android.view.Gravity.TOP
                group.forEachIndexed { index, roster ->
                    addView(rosterRow(roster), LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index == 0 && group.size > 1) marginEnd = ui.dp(8)
                    })
                }
                if (tablet && group.size == 1) addView(View(this@RosterListActivity), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = ui.dp(8) })
            }, ui.wrap(8))
        }
        kit.finishBar(page)
    }

    private fun rosterRow(roster: SavedRoster): View {
        val team = RosterCatalog.team(roster.teamId)
        val updated = if (roster.updatedAt > 0L) SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(roster.updatedAt)) else ""
        val names = roster.members.mapNotNull { m -> RosterCatalog.operative(team, m.operativeId)?.let { m.callsign.ifBlank { it.name } } }
        return kit.listRow(
            title = roster.name,
            subtitle = "${team.name} · ${roster.members.size} 名成员" + if (updated.isBlank()) "" else " · $updated",
            leading = kit.emblem(roster.teamId, 44),
            trailing = kit.iconButton("⋯", "更多：${roster.name}") { more(roster) }.apply {
                layoutParams = LinearLayout.LayoutParams(ui.dp(48), ui.dp(48))
            },
            onClick = { edit(roster) },
            extra = if (names.isEmpty()) null else ui.text(names.joinToString(" · "), 12f, ui.ink).apply { maxLines = 2 }
        )
    }

    private fun more(roster: SavedRoster) {
        kit.actionSheet(roster.name, null, listOf(
            CombatKit.SheetAction("编辑", primary = true) { edit(roster) },
            CombatKit.SheetAction("复制一份") { store.duplicate(roster.rosterId); render() },
            CombatKit.SheetAction("删除") {
                MaterialAlertDialogBuilder(this)
                    .setTitle("删除「${roster.name}」？")
                    .setMessage("删除后无法恢复。已开始的对局不受影响。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除") { _, _ -> store.delete(roster.rosterId); render() }
                    .show()
            }
        ))
    }

    private fun chooseTeam() {
        ui.dialog("选择杀戮小队") { box, sheet ->
            RosterCatalog.teams.forEach { team ->
                box.addView(kit.listRow(
                    title = team.name,
                    subtitle = "${TeamArchetypes.forTeam(team.id).joinToString(" · ")} · ${team.operatives.size} 种成员",
                    leading = kit.emblem(team.id, 44),
                    trailing = kit.chevron(),
                    onClick = {
                        sheet.dismiss()
                        startActivity(Intent(this, RosterActivity::class.java).putExtra(RosterActivity.EXTRA_CREATE_TEAM_ID, team.id))
                    }
                ), ui.wrap(6))
            }
        }
    }

    private fun edit(roster: SavedRoster) {
        startActivity(Intent(this, RosterActivity::class.java).putExtra(RosterActivity.EXTRA_ROSTER_ID, roster.rosterId))
    }
}
