package com.example.helloworld

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

class BattleHubActivity : AppCompatActivity() {
    private lateinit var ui: RecordUi
    private fun teamName(teamId: String): String =
        RosterCatalog.teams.firstOrNull { it.id == teamId }?.name ?: teamId

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);ui=RecordUi(this);render() }
    override fun onResume() { super.onResume();if(::ui.isInitialized) render() }
    private fun render() {
        val store=BattleStore(this)
        val tablet=resources.configuration.smallestScreenWidthDp>=600
        fun actionSize()=if(tablet) LinearLayout.LayoutParams(ui.dp(156),ui.dp(44)).apply { marginEnd=ui.dp(8) } else ui.weight()
        val box=ui.column(16).apply { setBackgroundColor(ui.base) }
        setContentView(ScrollView(this).apply { setBackgroundColor(ui.base);isFillViewport=true;addView(box) })
        box.addView(ui.row().apply {
            addView(ui.heading("KILL TEAM  /",27f).apply { setTextColor(ui.accent) },ui.weight())
            addView(ui.text("桌边记录台",14f,ui.muted))
        },ui.wrap(12))
        store.active()?.let { active ->
            box.addView(ui.row().apply {
                addView(ui.column().apply {
                    addView(ui.text("进行中 · TP ${active.turningPoint.coerceAtLeast(1)}",11f,ui.accent,true),ui.wrap(4))
                    addView(ui.text(active.name,18f,bold=true),ui.wrap(4))
                    addView(ui.text("${teamName(active.sideA.teamId)}  ${active.sideA.currentScore} : ${active.sideB.currentScore}  ${teamName(active.sideB.teamId)}",13f,ui.accent,true),ui.wrap(3))
                    addView(ui.text("${active.sideA.playerName}  vs  ${active.sideB.playerName}",11f,ui.muted))
                },ui.weight())
                addView(ui.button("继续对局  →",true) { open(active) })
            },ui.wrap(12))
        }
        box.addView(FrameLayout(this).apply {
            addView(ImageView(this@BattleHubActivity).apply {
                setImageResource(R.drawable.kt_battlefield);scaleType=ImageView.ScaleType.CENTER_CROP
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },FrameLayout.LayoutParams(-1,-1))
            addView(View(this@BattleHubActivity).apply {
                background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x22101010,0xee0f0f0f.toInt()))
            },FrameLayout.LayoutParams(-1,-1))
            addView(ui.column(16).apply {
                addView(ui.heading("准备下一场对局",26f),ui.wrap(3))
                addView(ui.text("下一场对局，从这里开始。",15f))
            },FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        },LinearLayout.LayoutParams(-1,ui.dp(if(resources.configuration.smallestScreenWidthDp>=600) 210 else 158)).apply { bottomMargin=ui.dp(12) })
        box.addView(ui.row().apply {
            addView(ui.button("＋ 新建对局",true) { startActivity(Intent(this@BattleHubActivity,BattleSetupActivity::class.java)) },actionSize())
            addView(ui.button("小队与武器") { startActivity(Intent(this@BattleHubActivity,RosterListActivity::class.java)) },actionSize())
            addView(ui.button("自由投掷") { startActivity(Intent(this@BattleHubActivity,MainActivity::class.java)) },actionSize())
        },ui.wrap(16))
        box.addView(ui.divider())
        box.addView(ui.row().apply {
            addView(ui.heading("对局记录",23f),ui.weight())
                addView(ui.link("行动图鉴") { MissionCards.picker(ui,false,"",readOnly=true) {} })
        },ui.wrap(4))
        if(store.all().isEmpty()) box.addView(ui.text("还没有对局。创建后会自动保存。",color=ui.muted),ui.wrap())
        store.all().take(20).forEach { battle ->
            box.addView(ui.row().apply {
                setPadding(0,ui.dp(8),0,ui.dp(8));minimumHeight=ui.dp(60)
                addView(ui.text(if(battle.completed) "END" else "LIVE",12f,if(battle.completed) ui.muted else ui.accent,true),LinearLayout.LayoutParams(ui.dp(44),-2))
                addView(ui.column().apply {
                    addView(ui.text(battle.name,15f,bold=true),ui.wrap(4))
                    addView(ui.text("${teamName(battle.sideA.teamId)}  vs  ${teamName(battle.sideB.teamId)}",13f,ui.accent,true),ui.wrap(3))
                    addView(ui.text("${battle.sideA.playerName}  vs  ${battle.sideB.playerName}",11f,ui.muted))
                },ui.weight())
                addView(ui.text("›",24f,ui.accent))
                contentDescription="打开对局：${battle.name}，${battle.sideA.playerName}的${teamName(battle.sideA.teamId)}对阵${battle.sideB.playerName}的${teamName(battle.sideB.teamId)}";setOnClickListener { open(battle) }
            })
            box.addView(ui.divider())
        }
    }
    private fun open(battle: BattleRecord) {
        if(!battle.completed) BattleStore(this).setActive(battle.battleId)
        startActivity(Intent(this,BattleActivity::class.java).putExtra(BattleActivity.EXTRA_BATTLE_ID,battle.battleId))
    }
}
