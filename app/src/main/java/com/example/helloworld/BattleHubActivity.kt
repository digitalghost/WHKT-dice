package com.example.helloworld

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home: continue the current battle, start a new one, or open rosters / free dice. */
class BattleHubActivity : AppCompatActivity() {
    private lateinit var ui: RecordUi
    private lateinit var kit: CombatKit

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = RecordUi(this)
        kit = CombatKit(ui)
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

    private fun teamName(teamId: String) = RosterCatalog.teams.firstOrNull { it.id == teamId }?.name ?: teamId

    private fun render() {
        val store = BattleStore(this)
        val top = ui.row().apply {
            setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(8))
            addView(ui.heading("KILL TEAM", 28f).apply { setTextColor(ui.accent) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.text("杀戮小队 · 三版", 13f, ui.muted, true))
        }
        val page = kit.page(this, top)
        val body = page.body

        val active = store.active()
        if (active != null) body.addView(activeCard(active), ui.wrap(12))
        body.addView(newBattleCard(primary = active == null), LinearLayout.LayoutParams(-1, ui.dp(if (active == null) (if (isTablet) 240 else 200) else 150)).apply { bottomMargin = ui.dp(12) })
        body.addView(ui.row().apply {
            addView(shortcut("我的小队", "编成成员与武器") { startActivity(Intent(this@BattleHubActivity, RosterListActivity::class.java)) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8) })
            addView(shortcut("自由投掷", "单独掷骰") { startActivity(Intent(this@BattleHubActivity, MainActivity::class.java)) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8) })
            addView(shortcut("行动卡", "关键 / 战术行动") { MissionCards.picker(ui, false, "", readOnly = true) {} },
                LinearLayout.LayoutParams(0, -2, 1f))
        }, ui.wrap(20))

        val battles = store.all().filter { it.battleId != active?.battleId }
        body.addView(kit.section("对局记录", ui.text("${store.all().size} 场", 12f, ui.muted, true)), ui.wrap(6))
        if (store.all().isEmpty()) body.addView(ui.text("还没有对局", 14f, ui.muted, true), ui.wrap())
        battles.take(30).forEach { battle -> body.addView(historyRow(battle), ui.wrap(8)) }
        kit.finishBar(page)
    }

    private fun activeCard(battle: BattleRecord): View = ui.column().apply {
        setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(16))
        background = ui.bg(0xff211914.toInt(), ui.accent)
        val tp = if (battle.phase == BattlePhase.INITIATIVE) battle.turningPoint + 1 else battle.turningPoint
        addView(ui.text("进行中 · 第 ${tp.coerceIn(1, 4)} 转折点 · ${battle.phase.label}", 13f, ui.accent, true), ui.wrap(10))
        addView(ui.row().apply {
            addView(side(battle.sideA), LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.text("${battle.sideA.currentScore} : ${battle.sideB.currentScore}", 26f, ui.ink, true).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ui.dp(96), -2))
            addView(side(battle.sideB, end = true), LinearLayout.LayoutParams(0, -2, 1f))
        }, ui.wrap(14))
        addView(kit.primaryButton("继续对局") { open(battle) })
    }

    private fun side(s: BattleSide, end: Boolean = false): View = ui.row().apply {
        gravity = if (end) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER_VERTICAL
        val text = ui.column().apply {
            addView(ui.text(s.playerName, 15f, kit.sideColor(s.id), true).apply { if (end) gravity = Gravity.END }, ui.wrap(1))
            addView(ui.text(teamName(s.teamId), 12f, ui.muted, true).apply { if (end) gravity = Gravity.END }, ui.wrap(0))
        }
        val emblem = kit.emblem(s.teamId, 36)
        if (end) { addView(text); addView(emblem.apply { (layoutParams as LinearLayout.LayoutParams).apply { marginEnd = 0; marginStart = ui.dp(8) } }) }
        else { addView(emblem); addView(text) }
    }

    private fun newBattleCard(primary: Boolean): View = FrameLayout(this).apply {
        addView(ImageView(this@BattleHubActivity).apply {
            setImageResource(R.drawable.kt_battlefield)
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
        addView(View(this@BattleHubActivity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x33101010, 0xf00f0f0f.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        addView(ui.column(16).apply {
            addView(ui.text("新的对局", 22f, ui.ink, true), ui.wrap(4))
            addView(ui.text("选小队 · 选装备 · 选行动", 13f, ui.muted, true), ui.wrap(12))
            val start = { startActivity(Intent(this@BattleHubActivity, BattleSetupActivity::class.java)) }
            addView(if (primary) kit.primaryButton("＋ 新建对局") { start() } else kit.secondaryButton("＋ 新建对局") { start() })
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun shortcut(title: String, subtitle: String, action: () -> Unit): View = ui.column().apply {
        setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        minimumHeight = ui.dp(76)
        background = ui.bg(0xff1d1d1d.toInt(), 0xff2c2c2a.toInt())
        addView(ui.text(title, 15f, ui.ink, true), ui.wrap(3))
        addView(ui.text(subtitle, 11f, ui.muted).apply { maxLines = 1 }, ui.wrap(0))
        isClickable = true
        foreground = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x22ee7540), null, ui.bg(ui.ink)
        )
        setOnClickListener { action() }
    }

    private fun historyRow(battle: BattleRecord): View {
        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(battle.updatedAt))
        val status = if (battle.completed) BattleRules.resultLabel(battle) else "进行中 · TP ${battle.turningPoint.coerceAtLeast(1)}"
        val score = if (battle.completed) "${battle.sideA.totalScore} : ${battle.sideB.totalScore}"
            else "${battle.sideA.currentScore} : ${battle.sideB.currentScore}"
        val trailing = ui.column().apply {
            gravity = Gravity.END
            addView(ui.text(score, 18f, ui.ink, true).apply { gravity = Gravity.END }, ui.wrap(2))
            addView(ui.text(date, 11f, ui.muted).apply { gravity = Gravity.END }, ui.wrap(0))
        }
        return kit.listRow(
            title = "${teamName(battle.sideA.teamId)}  vs  ${teamName(battle.sideB.teamId)}",
            subtitle = "${battle.sideA.playerName} vs ${battle.sideB.playerName} · $status",
            trailing = trailing,
            onClick = { open(battle) }
        ).apply {
            setOnLongClickListener { confirmDelete(battle); true }
            contentDescription = "打开对局：${teamName(battle.sideA.teamId)}对阵${teamName(battle.sideB.teamId)}，$status，长按删除"
        }
    }

    private fun confirmDelete(battle: BattleRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除这场对局？")
            .setMessage("${teamName(battle.sideA.teamId)} vs ${teamName(battle.sideB.teamId)}")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> BattleStore(this).delete(battle.battleId); render() }
            .show()
    }

    private fun open(battle: BattleRecord) {
        if (!battle.completed) BattleStore(this).setActive(battle.battleId)
        startActivity(Intent(this, BattleActivity::class.java).putExtra(BattleActivity.EXTRA_BATTLE_ID, battle.battleId))
    }
}
