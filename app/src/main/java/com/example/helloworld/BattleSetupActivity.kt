package com.example.helloworld

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * New battle, step by step: teams → equipment → key op → tactical ops (secret).
 * Primary ops are chosen later, as a strategic plan in the first turning point (p106).
 */
class BattleSetupActivity : AppCompatActivity() {
    private lateinit var ui: RecordUi
    private lateinit var kit: CombatKit
    private lateinit var rosterStore: RosterStore

    private var step = 0
    private var rosterA: SavedRoster? = null
    private var rosterB: SavedRoster? = null
    private var playerA = "玩家 A"
    private var playerB = "玩家 B"
    private var keyOp = ""
    private var equipmentA = ""
    private var equipmentB = ""
    private var tacticalA = ""
    private var tacticalB = ""

    private val steps = listOf("小队", "装备", "关键行动", "战术行动")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = RecordUi(this)
        kit = CombatKit(ui)
        rosterStore = RosterStore(this)
        val rosters = rosterStore.all().filter { it.members.isNotEmpty() }
        savedInstanceState?.let { s ->
            step = s.getInt("step")
            rosterA = s.getString("a")?.let(rosterStore::load)
            rosterB = s.getString("b")?.let(rosterStore::load)
            playerA = s.getString("pa") ?: playerA
            playerB = s.getString("pb") ?: playerB
            keyOp = s.getString("key").orEmpty()
            equipmentA = s.getString("ea").orEmpty(); equipmentB = s.getString("eb").orEmpty()
            tacticalA = s.getString("ta").orEmpty(); tacticalB = s.getString("tb").orEmpty()
        } ?: run {
            rosterA = rosters.firstOrNull()
            rosterB = rosters.getOrNull(1) ?: rosters.firstOrNull()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", step)
        outState.putString("a", rosterA?.rosterId); outState.putString("b", rosterB?.rosterId)
        outState.putString("pa", playerA); outState.putString("pb", playerB)
        outState.putString("key", keyOp)
        outState.putString("ea", equipmentA); outState.putString("eb", equipmentB)
        outState.putString("ta", tacticalA); outState.putString("tb", tacticalB)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        // Rosters may have been edited in another screen.
        rosterA = rosterA?.rosterId?.let(rosterStore::load)
        rosterB = rosterB?.rosterId?.let(rosterStore::load)
        if (::kit.isInitialized) render()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun back() {
        if (step > 0) { step--; render() } else finish()
    }

    private fun stepReady(): Boolean = when (step) {
        0 -> rosterA != null && rosterB != null
        1 -> true
        2 -> keyOp.isNotBlank()
        else -> tacticalA.isNotBlank() && tacticalB.isNotBlank()
    }

    private fun render() {
        val top = kit.topBar("新建对局", "第 ${step + 1} 步 · 共 ${steps.size} 步", null, ::back, {})
        val page = kit.page(this, top, kit.progress(step, steps))
        when (step) {
            0 -> renderTeams(page.body)
            1 -> renderEquipment(page.body)
            2 -> renderKeyOp(page.body)
            else -> renderTactical(page.body)
        }
        val last = step == steps.lastIndex
        val next = kit.primaryButton(if (last) "开始对局" else "下一步 · ${steps[step + 1]}", stepReady()) {
            if (last) startBattle() else { step++; render() }
        }
        page.bar.addView(if (step == 0) next else kit.actionBar(next, kit.secondaryButton("上一步") { back() }))
    }

    // ---------------------------------------------------------------- step 1: teams

    private fun renderTeams(body: LinearLayout) {
        val holder = if (resources.configuration.smallestScreenWidthDp >= 600) ui.row().apply { gravity = Gravity.TOP } else ui.column()
        listOf(true, false).forEach { a ->
            holder.addView(teamCard(a), if (holder.orientation == LinearLayout.HORIZONTAL)
                LinearLayout.LayoutParams(0, -2, 1f).apply { if (a) marginEnd = ui.dp(10) } else ui.wrap(12))
        }
        body.addView(holder)
        if (rosterStore.all().none { it.members.isNotEmpty() }) {
            body.addView(kit.emptyState("还没有可用的小队", "去编成小队") {
                startActivity(Intent(this, RosterListActivity::class.java))
            })
        }
    }

    private fun sideId(a: Boolean) = if (a) BattleRules.SIDE_A else BattleRules.SIDE_B

    private fun teamCard(a: Boolean): View = ui.column().apply {
        val roster = if (a) rosterA else rosterB
        setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        background = ui.bg(kit.sideFill(sideId(a)), kit.sideStroke(sideId(a)))
        val name: EditText = ui.input(if (a) playerA else playerB, if (a) "玩家 A" else "玩家 B").apply {
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(kit.sideColor(sideId(a)))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s?.toString()?.trim().orEmpty().ifBlank { if (a) "玩家 A" else "玩家 B" }
                    if (a) playerA = value else playerB = value
                }
            })
        }
        addView(name, ui.wrap(10))
        if (roster == null) {
            addView(kit.secondaryButton("选择小队") { pickRoster(a) })
        } else {
            val team = RosterCatalog.team(roster.teamId)
            val members = roster.members.mapNotNull { m -> RosterCatalog.operative(team, m.operativeId)?.let { m.callsign.ifBlank { it.name } } }
            addView(kit.listRow(
                title = roster.name,
                subtitle = "${team.name} · ${roster.members.size} 名成员",
                leading = kit.emblem(roster.teamId, 44),
                trailing = ui.text("更换", 13f, ui.accent, true).apply { setPadding(ui.dp(8), 0, ui.dp(8), 0) },
                onClick = { pickRoster(a) },
                extra = ui.text(members.joinToString(" · "), 12f, ui.ink).apply { maxLines = 3 }
            ))
        }
    }

    private fun pickRoster(a: Boolean) {
        val rosters = rosterStore.all().filter { it.members.isNotEmpty() }
        ui.dialog("${if (a) playerA else playerB} 的小队") { box, sheet ->
            if (rosters.isEmpty()) box.addView(ui.text("还没有编成，请先到「我的小队」创建。", 14f, ui.muted, true), ui.wrap())
            rosters.forEach { roster ->
                val team = RosterCatalog.team(roster.teamId)
                val current = (if (a) rosterA else rosterB)?.rosterId == roster.rosterId
                box.addView(kit.listRow(
                    title = roster.name,
                    subtitle = "${team.name} · ${roster.members.size} 名成员",
                    leading = kit.emblem(roster.teamId, 40),
                    trailing = if (current) ui.text("✓", 20f, ui.accent, true) else kit.chevron(),
                    stroke = if (current) ui.accent else 0xff2c2c2a.toInt(),
                    onClick = {
                        if (a) {
                            if (rosterA?.teamId != roster.teamId) { equipmentA = ""; tacticalA = "" }
                            rosterA = roster
                        } else {
                            if (rosterB?.teamId != roster.teamId) { equipmentB = ""; tacticalB = "" }
                            rosterB = roster
                        }
                        sheet.dismiss()
                        render()
                    }
                ), ui.wrap(6))
            }
        }
    }

    // ---------------------------------------------------------------- step 2: equipment

    private fun renderEquipment(body: LinearLayout) {
        listOf(true, false).forEach { a ->
            val roster = (if (a) rosterA else rosterB) ?: return@forEach
            val equipment = (if (a) equipmentA else equipmentB).lines().filter { it.isNotBlank() }
            body.addView(ui.column().apply {
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
                background = ui.bg(kit.sideFill(sideId(a)), kit.sideStroke(sideId(a)))
                addView(ui.row().apply {
                    addView(kit.emblem(roster.teamId, 32))
                    addView(ui.text(if (a) playerA else playerB, 16f, kit.sideColor(sideId(a)), true), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(ui.text("${equipment.size} / ${EquipmentCards.MAX_SELECTIONS}", 16f, ui.ink, true))
                }, ui.wrap(8))
                if (equipment.isEmpty()) addView(ui.text("未选择装备", 13f, ui.muted, true), ui.wrap(8))
                else addView(ui.column().apply {
                    equipment.chunked(2).forEach { pair ->
                        addView(ui.row().apply {
                            pair.forEach { addView(kit.tag(it, ui.ink).apply { textSize = 13f }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) }) }
                        }, ui.wrap(6))
                    }
                }, ui.wrap(4))
                addView(kit.secondaryButton(if (equipment.isEmpty()) "选择装备" else "修改装备") {
                    EquipmentCards.picker(ui, roster.teamId, if (a) equipmentA else equipmentB) {
                        if (a) equipmentA = it else equipmentB = it
                        render()
                    }
                })
            }, ui.wrap(12))
        }
    }

    // ---------------------------------------------------------------- step 3: key op

    private fun renderKeyOp(body: LinearLayout) {
        body.addView(ui.text("双方共用，开局后锁定", 13f, ui.muted, true), ui.wrap(10))
        MissionCards.all.filterNot { it.category.contains("战术") }.forEach { card ->
            val on = card.name == keyOp
            body.addView(kit.listRow(
                title = card.name,
                subtitle = card.scoring,
                trailing = if (on) ui.text("✓", 22f, ui.accent, true).apply { setPadding(ui.dp(8), 0, ui.dp(8), 0) }
                    else ui.text("详情", 13f, ui.muted, true).apply {
                        setPadding(ui.dp(10), ui.dp(12), ui.dp(8), ui.dp(12))
                        setOnClickListener { MissionCards.detail(ui, card) { keyOp = card.name; render() } }
                    },
                titleColor = if (on) ui.accent else ui.ink,
                stroke = if (on) ui.accent else 0xff2c2c2a.toInt(),
                onClick = { keyOp = card.name; render() }
            ), ui.wrap(8))
        }
    }

    // ---------------------------------------------------------------- step 4: tactical (secret)

    private fun renderTactical(body: LinearLayout) {
        body.addView(ui.text("每位玩家单独查看并选择，另一位请回避", 13f, ui.muted, true), ui.wrap(10))
        listOf(true, false).forEach { a ->
            val roster = (if (a) rosterA else rosterB) ?: return@forEach
            val chosen = (if (a) tacticalA else tacticalB).isNotBlank()
            body.addView(kit.listRow(
                title = if (a) playerA else playerB,
                subtitle = "${RosterCatalog.team(roster.teamId).name} · ${TeamArchetypes.forTeam(roster.teamId).joinToString(" · ")}",
                leading = kit.emblem(roster.teamId, 44),
                trailing = ui.text(if (chosen) "✓ 已选择" else "去选择", 14f, if (chosen) ui.accent else ui.ink, true).apply {
                    setPadding(ui.dp(8), 0, ui.dp(8), 0)
                },
                titleColor = kit.sideColor(sideId(a)),
                stroke = if (chosen) kit.sideColor(sideId(a)) else 0xff2c2c2a.toInt(),
                onClick = { pickTactical(a) }
            ), ui.wrap(10))
        }
        body.addView(ui.text("主要行动在第一转折点的战略阶段秘密选择", 12f, ui.muted), ui.wrap(4))
    }

    private fun pickTactical(a: Boolean) {
        val roster = (if (a) rosterA else rosterB) ?: return
        val current = if (a) tacticalA else tacticalB
        SecretSelection.open(ui, if (a) playerA else playerB, "战术行动") { box, sheet ->
            MissionCards.inlineTacticalChoices(ui, box, current, roster.teamId) { card ->
                if (a) tacticalA = card.name else tacticalB = card.name
                sheet.dismiss()
                render()
            }
        }
    }

    private fun startBattle() {
        val a = rosterA ?: return
        val b = rosterB ?: return
        if (!stepReady() || keyOp.isBlank()) return
        val record = BattleRules.create(BattleSetup(
            "$playerA vs $playerB", "认证行动", "", keyOp, "", "", playerA, playerB,
            equipmentA, equipmentB, tacticalA, tacticalB, a, b
        ))
        val store = BattleStore(this)
        store.save(record)
        store.setActive(record.battleId)
        startActivity(Intent(this, BattleActivity::class.java).putExtra(BattleActivity.EXTRA_BATTLE_ID, record.battleId))
        finish()
    }
}
