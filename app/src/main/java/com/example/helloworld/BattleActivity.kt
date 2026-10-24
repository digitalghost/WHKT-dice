package com.example.helloworld

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rule-guided battle screen: the app runs the turn sequence (initiative, ready, strategy,
 * firefight, end of battle) and the bookkeeping (CP, kill grade, tokens). Every step can be
 * undone with a visible trace, and every value can be changed by hand for team rules.
 */
class BattleActivity : AppCompatActivity() {
    private lateinit var ui: RecordUi
    private lateinit var kit: CombatKit
    private lateinit var store: BattleStore
    private lateinit var battle: BattleRecord
    private lateinit var root: LinearLayout
    private var scroll: ScrollView? = null
    private var actionBar: View? = null
    private var selectedSide = BattleRules.SIDE_A
    private val viewState by lazy { getSharedPreferences("battle_view_state", MODE_PRIVATE) }

    // Initiative roll-off inputs (not persisted until the turning point starts).
    private var rollA: Int? = null
    private var rollB: Int? = null
    private var deploymentInitiative: String? = null

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = RecordUi(this)
        kit = CombatKit(ui)
        store = BattleStore(this)
        val loaded = intent.getStringExtra(EXTRA_BATTLE_ID)?.let(store::load) ?: store.active()
        if (loaded == null) { finish(); return }
        battle = loaded
        selectedSide = savedInstanceState?.getString("side")
            ?: viewState.getString("${battle.battleId}_side", BattleRules.SIDE_A) ?: BattleRules.SIDE_A
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::battle.isInitialized) {
            store.load(battle.battleId)?.let { battle = it }
            render()
        }
    }

    override fun onPause() {
        if (::battle.isInitialized) viewState.edit().putString("${battle.battleId}_side", selectedSide).apply()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("side", selectedSide)
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    // ---------------------------------------------------------------- persistence

    /** Save a record whose log was already written by the rules (BattleFlow / BattleRules). */
    /** While a live-editing sheet is open the screen behind it is not rebuilt; it refreshes once on close. */
    private var holdRender = false

    private fun refresh() { if (!holdRender) render() }

    private fun commit(next: BattleRecord) {
        if (battle.completed) return
        battle = store.save(next)
        refresh()
    }

    /** Save with a single manual log line (manual adjustments are marked as such). */
    private fun save(next: BattleRecord, message: String, manual: Boolean = true) {
        if (battle.completed) return
        battle = store.save(next.copy(log = next.log + BattleLogEntry(
            turningPoint = next.turningPoint, phase = next.phase, message = message, manual = manual
        )))
        refresh()
    }

    private fun attempt(block: () -> BattleRecord): Boolean = runCatching(block)
        .onSuccess { commit(it) }
        .onFailure { note(it.message ?: "无法执行") }
        .isSuccess

    private fun undo() {
        val label = store.undoPreview(battle.battleId)
        val restored = store.undo(battle.battleId) ?: run { note("没有可撤回的记录"); return }
        battle = restored
        render()
        note("已撤回：${label ?: "上一步"}")
    }

    private fun note(message: String) {
        snack(message, Snackbar.LENGTH_SHORT).show()
    }

    /** Notices sit above the bottom action bar so they never cover the next action. */
    private fun snack(message: String, duration: Int): Snackbar =
        Snackbar.make(root, message, duration).also { bar -> actionBar?.takeIf { it.visibility == View.VISIBLE }?.let { bar.anchorView = it } }

    private fun noteWithUndo(message: String) {
        snack(message, NOTICE_MS).setAction("撤回") { undo() }.show()
    }

    // ---------------------------------------------------------------- helpers

    private fun template(side: BattleSide, operative: BattleOperativeState) =
        RosterCatalog.operative(RosterCatalog.team(side.teamId), operative.operativeId)

    private fun aplOf(side: BattleSide, operative: BattleOperativeState): Int =
        template(side, operative)?.apl ?: operative.apl

    private fun withApl(side: BattleSide, operative: BattleOperativeState) = operative.copy(apl = aplOf(side, operative))

    private fun updateOperative(sideId: String, memberId: String, change: (BattleOperativeState) -> BattleOperativeState): BattleRecord {
        val side = battle.side(sideId)
        return BattleFlow.side(battle, side.copy(operatives = side.operatives.map { if (it.memberId == memberId) change(it) else it }))
    }

    private fun sourceInput(hint: String = "来源（选填）：计谋、能力或装备名"): EditText = ui.input("", hint)

    private fun timestamp(value: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))

    // ---------------------------------------------------------------- layout

    private fun render() {
        val previousY = scroll?.scrollY ?: 0
        root = ui.column().apply { setBackgroundColor(ui.base) }
        setContentView(root)
        val tp = if (battle.phase == BattlePhase.INITIATIVE) battle.turningPoint + 1 else battle.turningPoint
        val title = if (battle.completed) "对局总结" else "TP ${tp.coerceIn(1, BattleRules.TURNING_POINT_COUNT)} · ${battle.phase.label}"
        root.addView(kit.topBar(
            title = title,
            subtitle = battle.keyOp.ifBlank { battle.name },
            undoLabel = if (battle.completed) null else store.undoPreview(battle.battleId),
            onBack = ::finish,
            onUndo = ::undo,
            trailing = ui.row().apply {
                addView(kit.diceButton(ui) { diceSheet() })
                addView(kit.iconButton("⋯", "更多") { more() }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            }
        ))
        if (!battle.completed) root.addView(phaseStrip(), LinearLayout.LayoutParams(-1, -2))
        root.addView(scoreboard(), LinearLayout.LayoutParams(-1, -2))
        root.addView(quickAccess(), LinearLayout.LayoutParams(-1, -2))
        val body = ui.column(12)
        val sv = ScrollView(this).apply { addView(body) }
        scroll = sv
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = ui.column().apply {
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))
            setBackgroundColor(0xff141414.toInt())
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        actionBar = bar

        if (battle.completed) {
            renderSummary(body, bar)
        } else when (battle.phase) {
            BattlePhase.INITIATIVE -> renderInitiative(body, bar)
            BattlePhase.STRATEGY -> renderStrategy(body, bar)
            BattlePhase.FIREFIGHT -> renderFirefight(body, bar)
            BattlePhase.COMPLETE -> renderSummary(body, bar)
        }
        if (bar.childCount == 0) bar.visibility = View.GONE
        sv.post { sv.scrollTo(0, previousY) }
    }

    /** TP dots plus the three phases of the current turning point. */
    private fun phaseStrip(): View = ui.row().apply {
        setPadding(ui.dp(16), ui.dp(2), ui.dp(16), ui.dp(8))
        val tp = battle.turningPoint
        addView(ui.text((1..BattleRules.TURNING_POINT_COUNT).joinToString(" ") { if (it <= tp) "●" else "○" }, 12f, ui.accent, true),
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(12) })
        val phases = listOf(BattlePhase.INITIATIVE to "先手·就绪", BattlePhase.STRATEGY to "战略", BattlePhase.FIREFIGHT to "交战")
        val current = phases.indexOfFirst { it.first == battle.phase }
        phases.forEachIndexed { index, (_, label) ->
            val color = when {
                index == current -> ui.accent
                index < current -> ui.ink
                else -> 0xff5c5a56.toInt()
            }
            addView(ui.text((if (index < current) "✓ " else if (index == current) "● " else "") + label, 12f, color, index == current))
            if (index < phases.lastIndex) addView(ui.text("  ─  ", 12f, 0xff4a4844.toInt()))
        }
    }

    /** Both sides at a glance: CP with quick ±, VP (tap for breakdown). */
    private fun scoreboard(): View = ui.column().apply {
        setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(6))
        val sides = listOf(battle.sideA, battle.sideB)
        val holder = if (isTablet) ui.row() else ui.column()
        sides.forEach { side ->
            val turn = !battle.completed && battle.phase != BattlePhase.INITIATIVE &&
                (battle.progress.active?.side ?: battle.progress.nextSide) == side.id
            holder.addView(ui.row().apply {
                minimumHeight = ui.dp(48)
                setPadding(ui.dp(8), ui.dp(2), ui.dp(4), ui.dp(2))
                background = ui.bg(kit.sideFill(side.id), if (turn) kit.sideColor(side.id) else kit.sideStroke(side.id))
                addView(ui.portrait(ui.teamArt(side.teamId), 30))
                addView(ui.column().apply {
                    addView(ui.text(side.playerName + if (battle.initiativeSideId == side.id) " · 先手" else "", 13f, kit.sideColor(side.id), true).apply { maxLines = 1 }, ui.wrap(0))
                    addView(ui.text(RosterCatalog.team(side.teamId).name, 11f, ui.muted, true).apply { maxLines = 1 }, ui.wrap(0))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (!battle.completed) {
                    addView(ui.button("−") { changeCp(side.id, -1) }.apply { contentDescription = "${side.playerName} CP 减 1" },
                        LinearLayout.LayoutParams(ui.dp(40), ui.dp(44)))
                }
                addView(ui.text("${side.commandPoints}CP", 15f, ui.ink, true).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(ui.dp(50), -2))
                if (!battle.completed) {
                    addView(ui.button("+") { changeCp(side.id, 1) }.apply { contentDescription = "${side.playerName} CP 加 1" },
                        LinearLayout.LayoutParams(ui.dp(40), ui.dp(44)))
                }
                addView(ui.text("${if (battle.completed) side.totalScore else side.currentScore}VP ›", 16f, ui.accent, true).apply {
                    gravity = Gravity.CENTER
                    minHeight = ui.dp(44)
                    setPadding(ui.dp(10), 0, ui.dp(6), 0)
                    contentDescription = "${side.playerName} 比分"
                    setOnClickListener { scoreSheet(side.id) }
                })
            }, if (isTablet) LinearLayout.LayoutParams(0, -2, 1f).apply { if (side.id == BattleRules.SIDE_A) marginEnd = ui.dp(8) }
            else ui.wrap(4))
        }
        addView(holder)
    }

    /** Reference cards players open all game long; kept one tap away instead of under ⋯. */
    /**
     * Reference cards players open all game long; kept one tap away instead of under ⋯.
     * Tablet: one row. Phone: the two team cards on the first row, the rest below, all visible.
     */
    private fun quickAccess(): View {
        fun entry(label: String, color: Int, action: () -> Unit) = kit.tag(label, color, action).apply {
            minHeight = ui.dp(40); gravity = Gravity.CENTER; textSize = 13f; maxLines = 1
        }
        val teams = listOf(battle.sideA, battle.sideB).map { side ->
            entry("${side.playerName} · 小队资料", kit.sideColor(side.id)) { teamInfo(side.id) }
        }
        val tools = listOf(
            entry("关键行动 · ${battle.keyOp.ifBlank { "未记录" }}", ui.ink) { tasks() },
            entry("规则速查", ui.ink) { rulesIndex() },
            entry("记录 · ${battle.log.count { !it.undone }}", ui.ink) { journal() }
        )
        fun rowOf(items: List<View>) = ui.row().apply {
            items.forEachIndexed { i, v ->
                addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i < items.lastIndex) marginEnd = ui.dp(6) })
            }
        }
        return ui.column().apply {
            setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(8))
            if (isTablet) addView(rowOf(teams + tools))
            else {
                addView(rowOf(teams), ui.wrap(6))
                addView(rowOf(tools))
            }
        }
    }

    /** Generic dice; results can be written to the battle log so the roll stays on record. */
    private fun diceSheet() = DiceSheet(ui, onRecord = if (battle.completed) null else { line ->
        save(battle, line, manual = false)
    })

    private fun changeCp(sideId: String, delta: Int) {
        val side = battle.side(sideId)
        val points = (side.commandPoints + delta).coerceAtLeast(0)
        if (points == side.commandPoints) return
        save(BattleFlow.side(battle, side.copy(commandPoints = points)), "${side.playerName}：CP ${side.commandPoints} → $points")
    }

    // ---------------------------------------------------------------- initiative

    private fun renderInitiative(body: LinearLayout, bar: LinearLayout) {
        val next = battle.turningPoint + 1
        body.addView(ui.row().apply {
            addView(ui.text("第 $next 转折点 · 先手权拼骰", 18f, ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(kit.infoButton { CoreRules.show(ui, "initiative") })
        }, ui.wrap(10))
        // Same animated tray as combat: one tray per player, both thrown together.
        initiativeTrays = listOf(battle.sideA, battle.sideB).associate { side ->
            side.id to DiceTrayView(this).apply {
                background = ui.bg(0xff101010.toInt(), kit.sideStroke(side.id))
                setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
                contentDescription = "${side.playerName} 先手骰"
                (if (side.id == BattleRules.SIDE_A) rollA else rollB)?.let { restoreResults(listOf(it)) }
            }
        }
        body.addView(ui.row().apply {
            initiativeTrays.values.forEachIndexed { i, tray ->
                addView(tray, LinearLayout.LayoutParams(0, ui.dp(if (isTablet) 180 else 130), 1f).apply { if (i == 0) marginEnd = ui.dp(8) })
            }
        }, ui.wrap(10))
        listOf(battle.sideA, battle.sideB).forEach { side ->
            val current = if (side.id == BattleRules.SIDE_A) rollA else rollB
            body.addView(kit.segmented(side.playerName, (1..6).map { it.toString() }, (current ?: 0) - 1) { index ->
                if (side.id == BattleRules.SIDE_A) rollA = index + 1 else rollB = index + 1
                render()
            }, ui.wrap(10))
            val cards = if (side.id == BattleRules.SIDE_A) battle.progress.cardsA else battle.progress.cardsB
            if (cards.isNotEmpty()) body.addView(kit.tagRow(cards.map { card ->
                kit.tag(if (card == 0) "重投卡 · 使用" else "±$card 卡 · 使用", kit.sideColor(side.id)) { useCard(side.id, card) }
            }), ui.wrap(10))
        }
        val a = rollA
        val b = rollB
        if (a == null || b == null) {
            bar.addView(kit.primaryButton("掷先手骰") { rollInitiative() })
            return
        }
        var winner = BattleFlow.initiativeWinner(battle, a, b)
        if (a == b) {
            if (winner == null) {
                body.addView(kit.segmented("平手 · 部署时谁有先手", listOf(battle.sideA.playerName, battle.sideB.playerName),
                    listOf(BattleRules.SIDE_A, BattleRules.SIDE_B).indexOf(deploymentInitiative)) { index ->
                    deploymentInitiative = if (index == 0) BattleRules.SIDE_A else BattleRules.SIDE_B
                    render()
                }, ui.wrap(10))
                winner = deploymentInitiative?.let(BattleFlow::other)
            }
        }
        if (winner == null) {
            bar.addView(kit.primaryButton("选择部署先手方后继续", enabled = false) {})
            return
        }
        val winnerSide = battle.side(winner)
        body.addView(ui.text("胜者：${winnerSide.playerName}" + if (a == b) "（平手规则）" else "", 16f, kit.sideColor(winner), true), ui.wrap(4))
        bar.addView(ui.text("${winnerSide.playerName}决定谁拥有先手权", 12f, ui.muted, true), ui.wrap(6))
        bar.addView(ui.row().apply {
            listOf(battle.sideA, battle.sideB).forEach { side ->
                addView(kit.primaryButton("${side.playerName} 先手") { startTurningPoint(winner, side.id, a, b) },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { if (side.id == BattleRules.SIDE_A) marginEnd = ui.dp(8) })
            }
        })
    }

    private var initiativeTrays: Map<String, DiceTrayView> = emptyMap()
    private var initiativeRolling = false

    private fun rollInitiative() {
        if (initiativeRolling) return
        val trayA = initiativeTrays[BattleRules.SIDE_A] ?: return
        val trayB = initiativeTrays[BattleRules.SIDE_B] ?: return
        initiativeRolling = true
        var a: Int? = null
        var b: Int? = null
        fun done() {
            if (a == null || b == null) return
            initiativeRolling = false
            rollA = a; rollB = b
            render()
        }
        DiceSoundPlayer(this).play()
        trayA.roll(1) { a = it.single(); done() }
        trayB.roll(1) { b = it.single(); done() }
    }

    private fun startTurningPoint(winner: String, chosen: String, a: Int, b: Int) {
        var next = battle
        if (next.turningPoint == 0 && deploymentInitiative != null) {
            next = next.copy(progress = next.progress.copy(deploymentInitiative = deploymentInitiative.orEmpty()))
        }
        next = BattleFlow.event(next, "先手拼骰：${battle.sideA.playerName} $a，${battle.sideB.playerName} $b")
        val before = battle
        val ok = attempt { BattleFlow.initiative(next, winner, chosen) }
        if (!ok) return
        rollA = null; rollB = null
        selectedSide = chosen
        val gainA = battle.sideA.commandPoints - before.sideA.commandPoints
        val gainB = battle.sideB.commandPoints - before.sideB.commandPoints
        render()
        noteWithUndo("第 ${battle.turningPoint} 转折点：特工已就绪，${battle.sideA.playerName} +${gainA}CP，${battle.sideB.playerName} +${gainB}CP")
    }

    private fun useCard(sideId: String, card: Int) {
        val input = sourceInput("结果（选填）：例如 4 → 6")
        kit.actionSheet(if (card == 0) "使用重投先手权卡" else "使用 ±$card 先手权卡", input, listOf(
            CombatKit.SheetAction("使用", primary = true) {
                attempt { BattleFlow.useCard(battle, sideId, card, input.text.toString().ifBlank { "修改拼骰结果" }) }
            }
        ))
    }

    // ---------------------------------------------------------------- strategy

    private fun renderStrategy(body: LinearLayout, bar: LinearLayout) {
        val turn = battle.side(battle.progress.nextSide)
        val done = battle.progress.planPasses >= 2
        val needsPrimary = BattleFlow.needsPrimary(battle, turn.id)
        body.addView(ui.row().apply {
            addView(ui.text(when {
                done -> "双方已连续跳过"
                needsPrimary -> "轮到 ${turn.playerName}：秘密选择主要行动"
                else -> "轮到 ${turn.playerName}：使用战略计划或跳过"
            }, 17f, if (done) ui.ink else kit.sideColor(turn.id), true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(kit.infoButton { CoreRules.show(ui, "turn_sequence") })
        }, ui.wrap(10))
        if (battle.turningPoint == 1) {
            body.addView(ui.row().apply {
                listOf(battle.sideA, battle.sideB).forEach { side ->
                    val chosen = side.primaryOp != null
                    addView(kit.tag("${side.playerName} 主要行动 · ${if (chosen) "已选（隐藏）" else "未选"}",
                        if (chosen) kit.sideColor(side.id) else ui.muted), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) })
                }
            }, ui.wrap(10))
        }
        val used = battle.progress.usedPlans.map { it.substringBefore("/") to it.substringAfter("/") }
            .filter { it.second != BattleFlow.PRIMARY_PLAN }
        if (used.isNotEmpty()) {
            body.addView(kit.tagRow(listOf(ui.text("已使用", 12f, ui.muted, true)) + used.map { (side, name) -> kit.tag(name, kit.sideColor(side)) }), ui.wrap(10))
        }
        body.addView(kit.tagRow(listOf(battle.sideA, battle.sideB).map { side ->
            kit.tag("${side.playerName} 的计谋", kit.sideColor(side.id)) { BattleReference(ui).ploys(side.teamId) }
        }), ui.wrap(12))
        renderOperatives(body)
        when {
            needsPrimary -> bar.addView(kit.primaryButton("${turn.playerName} · 选择主要行动") { primarySheet() })
            done && BattleFlow.primariesReady(battle) -> bar.addView(kit.primaryButton("进入交战阶段") { enterFirefight() })
            else -> bar.addView(kit.actionBar(
                kit.primaryButton("跳过") { attempt { BattleFlow.passPlan(battle) } },
                kit.secondaryButton("使用计谋") { ploySheet() },
                kit.secondaryButton("进入交战", BattleFlow.primariesReady(battle)) { enterFirefight() }
            ))
        }
    }

    /** Primary op is chosen in private; the public screen only shows "已选（隐藏）". */
    private fun primarySheet() {
        val side = battle.side(battle.progress.nextSide)
        SecretSelection.open(ui, side.playerName, "主要行动") { box, sheet ->
            box.addView(ui.text("战斗结束时揭示，该行动的 VP 额外增加一半（向上取整）", 13f, ui.muted, true), ui.wrap(12))
            ScoreType.entries.forEach { type ->
                val detail = when (type) {
                    ScoreType.KEY -> battle.keyOp.ifBlank { "关键行动" }
                    ScoreType.KILL -> "击杀等级"
                    ScoreType.TACTICAL -> side.tacticalOpNotes.ifBlank { "战术行动" }
                }
                box.addView(kit.listRow(type.label, detail, trailing = kit.chevron(), onClick = {
                    sheet.dismiss()
                    if (attempt { BattleFlow.choosePrimary(battle, type) }) noteWithUndo("${side.playerName} 已选择主要行动")
                }), ui.wrap(8))
            }
        }
    }

    private fun enterFirefight() {
        if (attempt { BattleRules.advancePhase(battle) }) {
            selectedSide = battle.progress.nextSide
            render()
        }
    }

    private fun ploySheet() {
        val side = battle.side(battle.progress.nextSide)
        var cost = 1
        val name = ui.input("", "计谋名称")
        val costView = ui.column()
        fun renderCost() {
            costView.removeAllViews()
            costView.addView(numberRow("CP", cost, 0, 9) { cost = it; renderCost() })
        }
        renderCost()
        val header = ui.column().apply {
            addView(name, ui.wrap(8))
            addView(costView)
        }
        kit.actionSheet("${side.playerName} · 战略计谋（剩 ${side.commandPoints}CP）", header, listOf(
            CombatKit.SheetAction("使用", primary = true) {
                val title = name.text.toString().ifBlank { "战略计谋" }
                if (BattleFlow.planUsed(battle, title)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("本转折点已用过「$title」")
                        .setMessage("每种战略计划每个转折点最多使用一次。有规则允许时可以继续记录。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("仍然记录") { _, _ -> attempt { BattleFlow.plan(battle, title, cost, allowRepeat = true) } }
                        .show()
                } else {
                    attempt { BattleFlow.plan(battle, title, cost) }
                }
            }
        ))
    }

    // ---------------------------------------------------------------- firefight

    private fun renderFirefight(body: LinearLayout, bar: LinearLayout) {
        val active = battle.progress.active
        if (active != null) {
            renderActivation(body, bar, active)
            renderOperatives(body)
            return
        }
        val turnId = battle.progress.nextSide
        val turn = battle.side(turnId)
        val turnReady = BattleFlow.ready(battle, turnId)
        val otherReady = BattleFlow.ready(battle, BattleFlow.other(turnId))
        val guidance = when {
            !turnReady && !otherReady -> "所有特工都已待机"
            turnReady -> "轮到 ${turn.playerName}：点一名就绪特工激活"
            else -> "${turn.playerName} 已无就绪特工：可用交战命令的特工反应，或放弃"
        }
        body.addView(ui.text(guidance, 15f, if (turnReady || otherReady) kit.sideColor(turnId) else ui.ink, true), ui.wrap(10))
        renderOperatives(body)
        when {
            !turnReady && !otherReady -> bar.addView(kit.primaryButton(
                if (battle.turningPoint >= BattleRules.TURNING_POINT_COUNT) "结束战斗" else "结束第 ${battle.turningPoint} 转折点"
            ) { endTurningPoint() })
            !turnReady -> bar.addView(kit.actionBar(
                kit.primaryButton("放弃反应") { attempt { BattleFlow.skipReaction(battle) } },
                kit.secondaryButton("结束转折点") { confirmEndTurningPoint() }
            ))
            else -> bar.addView(kit.actionBar(
                kit.secondaryButton("结束转折点") { confirmEndTurningPoint() }
            ))
        }
    }

    private fun confirmEndTurningPoint() {
        MaterialAlertDialogBuilder(this)
            .setTitle("仍有就绪特工")
            .setMessage("确定提前结束本转折点？之后可以撤回。")
            .setNegativeButton("取消", null)
            .setPositiveButton("结束") { _, _ -> endTurningPoint() }
            .show()
    }

    private fun endTurningPoint() {
        val last = battle.turningPoint >= BattleRules.TURNING_POINT_COUNT
        if (attempt { BattleRules.advancePhase(battle) }) {
            noteWithUndo(if (last) "战斗结束：${BattleRules.resultLabel(battle)}" else "第 ${battle.turningPoint} 转折点结束，等待先手权拼骰")
        }
    }

    private val actions = listOf(
        "转移" to 1, "冲刺" to 1, "冲锋" to 1, "后撤" to 2,
        "拾取标识" to 1, "放置标识" to 1, "戒备" to 1
    )

    private fun renderActivation(body: LinearLayout, bar: LinearLayout, active: Activation) {
        val side = battle.side(active.side)
        val operative = side.operatives.first { it.memberId == active.member }
        body.addView(ui.column().apply {
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(12))
            background = ui.bg(kit.sideFill(side.id), kit.sideColor(side.id))
            addView(ui.row().apply {
                addView(ui.column().apply {
                    addView(ui.text(if (active.reaction) "反应中" else "激活中", 11f, ui.muted, true), ui.wrap(1))
                    addView(ui.text(operative.displayName, 18f, kit.sideColor(side.id), true), ui.wrap(1))
                    addView(ui.text("${operative.order}命令" + if (operative.stunned) " · 晕眩" else "", 13f, ui.ink, true), ui.wrap(0))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.text(if (active.reaction) "1 行动" else "${active.spent}/${active.limit} AP", 24f, ui.ink, true).apply {
                    setOnClickListener { apSheet() }
                    contentDescription = "调整 AP"
                })
            }, ui.wrap(10))
            if (active.actions.isNotEmpty()) addView(kit.tagRow(active.actions.map { kit.tag(it, ui.ink) }), ui.wrap(10))
            val ranged = CombatWeapons.of(this@BattleActivity, side, operative).filterNot { it.isMelee }
            val melee = CombatWeapons.of(this@BattleActivity, side, operative).filter { it.isMelee }
            val buttons = mutableListOf<View>()
            if (ranged.isNotEmpty()) buttons += actionButton("射击", 1, true) { chooseWeapon(side, operative, ranged) }
            if (melee.isNotEmpty()) buttons += actionButton("近战", 1, true) { chooseWeapon(side, operative, melee) }
            actions.forEach { (name, cost) -> buttons += actionButton(name, cost, false) { recordAction(name, cost) } }
            buttons += actionButton("其他…", null, false) { customAction() }
            buttons.chunked(if (isTablet) 5 else 3).forEach { group ->
                val columns = if (isTablet) 5 else 3
                addView(ui.row().apply {
                    (0 until columns).forEach { i ->
                        val view = group.getOrNull(i) ?: View(context)
                        addView(view, LinearLayout.LayoutParams(0, ui.dp(52), 1f).apply { if (i < columns - 1) marginEnd = ui.dp(6) })
                    }
                }, ui.wrap(6))
            }
        }, ui.wrap(12))
        bar.addView(kit.actionBar(
            kit.primaryButton(if (active.reaction) "结束反应" else "结束激活") {
                val name = operative.displayName
                if (attempt { BattleFlow.endActivation(battle) }) {
                    selectedSide = battle.progress.nextSide
                    render()
                    noteWithUndo("$name 已待机")
                }
            },
            kit.secondaryButton("AP 调整") { apSheet() }
        ))
    }

    private fun actionButton(name: String, cost: Int?, primary: Boolean, run: () -> Unit) =
        ui.button(if (cost == null) name else "$name · ${cost}AP", primary, run).apply { textSize = 13f }

    /** Records an action; when the core rules refuse it, offers a logged team-rule exception. */
    private fun recordAction(name: String, cost: Int) {
        val result = runCatching { BattleFlow.action(battle, name, cost, "") }
        result.onSuccess { commit(it); return }
        exceptionSheet(result.exceptionOrNull()?.message.orEmpty()) { reason ->
            var next = battle
            val active = next.progress.active
            if (active != null && !active.reaction && active.spent + cost > active.limit) {
                next = BattleFlow.adjustActivation(next, active.spent + cost - active.limit, 0, reason)
            }
            attempt { BattleFlow.action(next, name, cost, reason) }
        }
    }

    private fun exceptionSheet(message: String, onConfirm: (String) -> Unit) {
        val input = sourceInput("依据：规则、计谋或能力名")
        val header = ui.column().apply {
            addView(ui.text(message, 14f, ui.orange, true), ui.wrap(8))
            addView(input)
        }
        kit.actionSheet("核心规则不允许", header, listOf(
            CombatKit.SheetAction("按规则例外记录", primary = true) { onConfirm(input.text.toString().ifBlank { "规则例外" }) }
        ))
    }

    private fun customAction() {
        var cost = 1
        val name = ui.input("", "行动名称")
        val costView = ui.column()
        fun renderCost() { costView.removeAllViews(); costView.addView(numberRow("AP", cost, 0, 4) { cost = it; renderCost() }) }
        renderCost()
        kit.actionSheet("其他行动", ui.column().apply { addView(name, ui.wrap(8)); addView(costView) }, listOf(
            CombatKit.SheetAction("记录", primary = true) { recordAction(name.text.toString().ifBlank { "行动" }, cost) }
        ))
    }

    private fun apSheet() {
        val active = battle.progress.active ?: return
        val input = sourceInput()
        fun adjust(limit: Int, spent: Int) = attempt { BattleFlow.adjustActivation(battle, limit, spent, input.text.toString()) }
        kit.actionSheet("AP ${active.spent}/${active.limit}", input, listOf(
            CombatKit.SheetAction("APL +1") { adjust(1, 0) },
            CombatKit.SheetAction("APL −1") { adjust(-1, 0) },
            CombatKit.SheetAction("返还 1AP（免费行动）") { adjust(0, -1) },
            CombatKit.SheetAction("额外消耗 1AP") { adjust(0, 1) }
        ))
    }

    private fun chooseWeapon(side: BattleSide, operative: BattleOperativeState, weapons: List<BattleWeaponSnapshot>) {
        if (weapons.size == 1) { chooseTarget(side, operative, weapons.single()); return }
        kit.actionSheet("选择武器", null, weapons.map { w ->
            CombatKit.SheetAction("${w.name} · ${w.profile}") { chooseTarget(side, operative, w) }
        })
    }

    private fun chooseTarget(side: BattleSide, operative: BattleOperativeState, weapon: BattleWeaponSnapshot) {
        val opponent = battle.opponent(side.id)
        val targets = opponent.operatives.filter { !it.incapacitated && !it.offField }
        if (targets.isEmpty()) { note("对方没有在场特工"); return }
        kit.actionSheet(if (weapon.isMelee) "控制范围内的敌方特工" else "有效目标", null, targets.map { t ->
            CombatKit.SheetAction("${t.displayName} · ${t.order} · 耐伤 ${t.currentWounds}/${t.maxWounds}") {
                validateThen(side, operative, weapon) { reason ->
                    if (weapon.isMelee) chooseRetaliationWeapon(side, operative, weapon, opponent, t, reason)
                    else launchShooting(side, operative, weapon, opponent, t, reason)
                }
            }
        })
    }

    /** Dry-run the action against the core rules before opening the resolution screen. */
    private fun validateThen(side: BattleSide, operative: BattleOperativeState, weapon: BattleWeaponSnapshot, launch: (String) -> Unit) {
        val active = battle.progress.active
        if (active == null || active.side != side.id || active.member != operative.memberId) { launch(""); return }
        val name = if (weapon.isMelee) "近战" else "射击"
        val silent = weapon.keywords.any { it.replace(" ", "").startsWith("安静") }
        val reason = if (silent && !weapon.isMelee) "安静" else ""
        val result = runCatching { BattleFlow.action(battle, name, 1, reason) }
        if (result.isSuccess) launch(reason)
        else exceptionSheet(result.exceptionOrNull()?.message.orEmpty()) { launch(it) }
    }

    private fun launchShooting(
        side: BattleSide, operative: BattleOperativeState, weapon: BattleWeaponSnapshot,
        opponent: BattleSide, target: BattleOperativeState, reason: String
    ) {
        startActivity(Intent(this, ShootingCombatActivity::class.java).apply {
            putExtra(ShootingCombatActivity.EXTRA_BATTLE_ID, battle.battleId)
            putExtra(ShootingCombatActivity.EXTRA_ATTACKER_SIDE_ID, side.id)
            putExtra(ShootingCombatActivity.EXTRA_ATTACKER_MEMBER_ID, operative.memberId)
            putExtra(ShootingCombatActivity.EXTRA_ATTACKER_WEAPON_ID, weapon.id)
            putExtra(ShootingCombatActivity.EXTRA_DEFENDER_SIDE_ID, opponent.id)
            putExtra(ShootingCombatActivity.EXTRA_DEFENDER_MEMBER_ID, target.memberId)
            putExtra(EXTRA_ACTION_REASON, reason)
        })
    }

    private fun chooseRetaliationWeapon(
        side: BattleSide, operative: BattleOperativeState, weapon: BattleWeaponSnapshot,
        opponent: BattleSide, target: BattleOperativeState, reason: String
    ) {
        val retaliation = CombatWeapons.of(this, opponent, target).filter { it.isMelee }
        fun launch(choice: BattleWeaponSnapshot?) {
            startActivity(Intent(this, MeleeCombatActivity::class.java).apply {
                putExtra(MeleeCombatActivity.EXTRA_BATTLE_ID, battle.battleId)
                putExtra(MeleeCombatActivity.EXTRA_ATTACKER_SIDE_ID, side.id)
                putExtra(MeleeCombatActivity.EXTRA_ATTACKER_MEMBER_ID, operative.memberId)
                putExtra(MeleeCombatActivity.EXTRA_ATTACKER_WEAPON_ID, weapon.id)
                putExtra(MeleeCombatActivity.EXTRA_DEFENDER_SIDE_ID, opponent.id)
                putExtra(MeleeCombatActivity.EXTRA_DEFENDER_MEMBER_ID, target.memberId)
                putExtra(MeleeCombatActivity.EXTRA_DEFENDER_WEAPON_ID, choice?.id)
                putExtra(MeleeCombatActivity.EXTRA_CAN_RETALIATE, choice != null)
                putExtra(EXTRA_ACTION_REASON, reason)
            })
        }
        if (retaliation.size == 1) { launch(retaliation.single()); return }
        kit.actionSheet("${target.displayName} 用哪件武器", null,
            retaliation.map { w -> CombatKit.SheetAction("${w.name} · ${w.profile}") { launch(w) } } +
                CombatKit.SheetAction("无法反击（规则效果）") { launch(null) })
    }

    // ---------------------------------------------------------------- operatives

    private fun renderOperatives(body: LinearLayout) {
        if (isTablet) {
            body.addView(ui.row().apply {
                gravity = Gravity.TOP
                listOf(battle.sideA, battle.sideB).forEach { side ->
                    addView(sideColumn(side), LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (side.id == BattleRules.SIDE_A) marginEnd = ui.dp(10)
                    })
                }
            })
        } else {
            body.addView(kit.segmented("", listOf(battle.sideA.playerName, battle.sideB.playerName),
                if (selectedSide == BattleRules.SIDE_A) 0 else 1) { index ->
                selectedSide = if (index == 0) BattleRules.SIDE_A else BattleRules.SIDE_B
                render()
            }, ui.wrap(8))
            body.addView(sideColumn(battle.side(selectedSide)))
        }
    }

    private fun sideColumn(side: BattleSide): View = ui.column().apply {
        val inPlay = side.operatives.count { !it.incapacitated }
        addView(ui.row().apply {
            addView(ui.text("${RosterCatalog.team(side.teamId).name} · 在场 $inPlay/${side.operatives.size}", 14f, ui.ink, true),
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(kit.tag("计数器${if (side.counters.isEmpty()) "" else " ${side.counters.size}"}", ui.muted) { countersSheet(side.id) })
        }, ui.wrap(6))
        if (side.counters.isNotEmpty()) addView(kit.tagRow(side.counters.map { c ->
            kit.tag("${c.name} ${c.value}", kit.sideColor(side.id)) { countersSheet(side.id) }
        }), ui.wrap(6))
        val rosters = RosterStore(this@BattleActivity).load(side.rosterId)
        side.operatives.forEach { operative ->
            addView(operativeCard(side, operative, rosters?.members?.firstOrNull { it.id == operative.memberId }?.customAvatarUri), ui.wrap(6))
        }
    }

    private fun canActivateNow(side: BattleSide, operative: BattleOperativeState): Boolean =
        !battle.completed && battle.phase == BattlePhase.FIREFIGHT && battle.progress.active == null &&
            battle.progress.nextSide == side.id && !operative.incapacitated &&
            (operative.ready || (!BattleFlow.ready(battle, side.id) && BattleFlow.ready(battle, BattleFlow.other(side.id)) &&
                operative.order == BattleOperativeState.ORDER_ENGAGE && BattleFlow.key(side.id, operative.memberId) !in battle.progress.reacted))

    private fun operativeCard(side: BattleSide, operative: BattleOperativeState, avatar: String?): View = ui.row().apply {
        val active = battle.progress.active?.let { it.side == side.id && it.member == operative.memberId } == true
        val highlight = active || canActivateNow(side, operative)
        minimumHeight = ui.dp(64)
        setPadding(ui.dp(8), ui.dp(6), ui.dp(6), ui.dp(6))
        background = ui.bg(
            if (operative.ready && !operative.incapacitated) 0xff1d1d1d.toInt() else 0xff141414.toInt(),
            if (highlight) kit.sideColor(side.id) else 0xff2c2c2a.toInt()
        )
        alpha = when {
            operative.incapacitated -> .45f
            operative.offField -> .6f
            else -> 1f
        }
        val t = template(side, operative)
        if (t != null) addView(ui.memberPortrait(avatar, t.cardRes, 44, "${operative.displayName} · 棋子照片"))
        addView(ui.column().apply {
            addView(ui.text(operative.displayName, 15f, if (operative.ready && !operative.incapacitated) ui.ink else ui.muted, true).apply { maxLines = 1 }, ui.wrap(2))
            val info = buildList {
                add("APL ${aplOf(side, operative)}")
                t?.save?.let { add("豁免 $it") }
                if (operative.incapacitated) add("残废") else if (!operative.ready) add("待机")
            }.joinToString(" · ")
            addView(ui.text(info, 12f, if (operative.incapacitated) kit.danger else 0xffc9c7c2.toInt(), true), ui.wrap(2))
            val tags = buildList {
                if (operative.wounded && !operative.incapacitated) add(kit.tag("受创", ui.orange))
                operative.tokens.take(3).forEach { add(kit.tag(it.label, if (it.name == OperativeToken.STUNNED) ui.orange else kit.sideColor(side.id))) }
                if (operative.tokens.size > 3) add(kit.tag("+${operative.tokens.size - 3}", ui.muted))
                if (operative.excludedFromKill) add(kit.tag("不计击杀", ui.muted))
            }
            if (tags.isNotEmpty()) addView(kit.tagRow(tags), ui.wrap(0))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(context).apply {
            setImageResource(OrderTokens.resource(operative.order, operative.ready))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "${operative.order}命令，${OrderTokens.faceLabel(operative.ready)}"
        }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply { marginEnd = ui.dp(6) })
        addView(ui.column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(ui.text("${operative.currentWounds}/${operative.maxWounds}", 15f,
                when { operative.incapacitated -> kit.danger; operative.wounded -> ui.orange; else -> ui.ink }, true).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2))
            if (!battle.completed) addView(ui.row().apply {
                addView(ui.button("−") { changeWounds(side.id, operative.memberId, -1) }.apply {
                    contentDescription = "${operative.displayName} 耐伤减 1"
                }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(44)))
                addView(ui.button("+") { changeWounds(side.id, operative.memberId, 1) }.apply {
                    contentDescription = "${operative.displayName} 耐伤加 1"
                }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(44)))
            })
        }, LinearLayout.LayoutParams(ui.dp(84), -2))
        setOnClickListener { operativeSheet(side.id, operative.memberId) }
        contentDescription = "${operative.displayName}，耐伤 ${operative.currentWounds}/${operative.maxWounds}"
    }

    private fun changeWounds(sideId: String, memberId: String, delta: Int) {
        val operative = battle.side(sideId).operatives.first { it.memberId == memberId }
        val updated = BattleRules.setWounds(operative, operative.currentWounds + delta)
        if (updated.currentWounds == operative.currentWounds && updated.incapacitated == operative.incapacitated) return
        save(updateOperative(sideId, memberId) { updated },
            "${operative.displayName}：耐伤 ${operative.currentWounds} → ${updated.currentWounds}${if (updated.incapacitated && !operative.incapacitated) "，残废" else ""}")
        if (updated.incapacitated && !operative.incapacitated) incapacitationNotice(sideId, memberId)
    }

    /** Default is incapacitated; the notice offers undo or a team-rule override. */
    private fun incapacitationNotice(sideId: String, memberId: String) {
        val name = battle.side(sideId).operatives.first { it.memberId == memberId }.displayName
        snack("$name 已残废", NOTICE_MS).setAction("选项") {
            kit.actionSheet("$name 已残废", null, listOf(
                CombatKit.SheetAction("撤回") { undo() },
                CombatKit.SheetAction("被规则阻止 · 保留 1 耐伤") {
                    save(updateOperative(sideId, memberId) { it.copy(currentWounds = 1, incapacitated = false) },
                        "$name：被规则阻止残废，保留 1 耐伤")
                },
                CombatKit.SheetAction("不计入击杀") {
                    save(updateOperative(sideId, memberId) { it.copy(excludedFromKill = true) }, "$name：不计入击杀")
                }
            ))
        }.show()
    }

    private fun operativeSheet(sideId: String, memberId: String) {
        val side = battle.side(sideId)
        val operative = side.operatives.first { it.memberId == memberId }
        val apl = BattleFlow.effectiveApl(withApl(side, operative))
        val actions = mutableListOf<CombatKit.SheetAction>()
        val firefight = !battle.completed && battle.phase == BattlePhase.FIREFIGHT && battle.progress.active == null
        if (firefight && canActivateNow(side, operative)) {
            if (operative.ready) {
                actions += CombatKit.SheetAction("激活 · 交战命令", primary = true) { activate(sideId, memberId, apl, engage = true) }
                actions += CombatKit.SheetAction("激活 · 隐匿命令", primary = true) { activate(sideId, memberId, apl, engage = false) }
            } else {
                actions += CombatKit.SheetAction("反应", primary = true) { activate(sideId, memberId, apl, engage = true) }
            }
        }
        if (firefight && !operative.incapacitated) {
            actions += CombatKit.SheetAction("额外激活（规则干预）…") { forcedActivation(sideId, memberId, apl) }
        }
        if (!battle.completed) {
            actions += CombatKit.SheetAction("伤害 / 治疗…") { woundSheet(sideId, memberId) }
            actions += CombatKit.SheetAction("状态标记…") { tokenSheet(sideId, memberId) }
            val other = OrderTokens.oppositeOrder(operative.order)
            actions += CombatKit.SheetAction("命令改为$other") {
                save(updateOperative(sideId, memberId) { it.copy(order = other) }, "${operative.displayName}：命令改为$other")
            }
            actions += CombatKit.SheetAction(if (operative.ready) "改为待机" else "改为就绪") {
                save(updateOperative(sideId, memberId) { it.copy(ready = !it.ready) },
                    "${operative.displayName}：改为${if (operative.ready) "待机" else "就绪"}")
            }
            actions += CombatKit.SheetAction(if (operative.incapacitated) "恢复在场" else "标记残废") {
                save(updateOperative(sideId, memberId) {
                    if (it.incapacitated) it.copy(incapacitated = false, currentWounds = it.currentWounds.coerceAtLeast(1))
                    else it.copy(incapacitated = true, ready = false)
                }, "${operative.displayName}：${if (operative.incapacitated) "恢复在场" else "标记残废"}")
            }
            actions += CombatKit.SheetAction(if (operative.excludedFromKill) "计入击杀" else "不计入击杀") {
                save(updateOperative(sideId, memberId) { it.copy(excludedFromKill = !it.excludedFromKill) },
                    "${operative.displayName}：${if (operative.excludedFromKill) "计入击杀" else "不计入击杀"}")
            }
        }
        actions += CombatKit.SheetAction("人物卡") { BattleReference(ui).operativeCard(side.teamId, operative) }
        val header = ui.column().apply {
            addView(ui.text("APL $apl · ${operative.order} · ${OrderTokens.faceLabel(operative.ready)} · 耐伤 ${operative.currentWounds}/${operative.maxWounds}",
                13f, ui.muted, true), ui.wrap(6))
            CombatWeapons.of(this@BattleActivity, side, operative).forEach { w ->
                addView(ui.text(w.name, 13f, ui.ink, true), ui.wrap(1))
                addView(WeaponRulesUi.profile(ui, w.name, w.profile, w.keywords, 13f, 0xffc9c7c2.toInt(), bold = true), ui.wrap(6))
            }
        }
        kit.actionSheet(operative.displayName, header, actions)
    }

    private fun activate(sideId: String, memberId: String, apl: Int, engage: Boolean) {
        if (attempt { BattleFlow.activate(battle, sideId, memberId, apl, engage) }) {
            selectedSide = sideId
            render()
        }
    }

    private fun forcedActivation(sideId: String, memberId: String, apl: Int) {
        val input = sourceInput("依据：规则、计谋或能力名")
        kit.actionSheet("额外激活", input, listOf(
            CombatKit.SheetAction("交战命令", primary = true) {
                attempt { BattleFlow.activate(battle, sideId, memberId, apl, true, force = true, reason = input.text.toString()) }
            },
            CombatKit.SheetAction("隐匿命令") {
                attempt { BattleFlow.activate(battle, sideId, memberId, apl, false, force = true, reason = input.text.toString()) }
            }
        ))
    }

    private fun woundSheet(sideId: String, memberId: String) {
        val operative = battle.side(sideId).operatives.first { it.memberId == memberId }
        val input = sourceInput()
        fun change(delta: Int) {
            val updated = BattleRules.setWounds(operative, operative.currentWounds + delta)
            val reason = input.text.toString()
            save(updateOperative(sideId, memberId) { updated },
                "${operative.displayName}：${if (delta < 0) "受到 ${-delta} 伤" else "回复 $delta"}，耐伤 ${updated.currentWounds}/${updated.maxWounds}${noteSuffix(reason)}")
            if (updated.incapacitated && !operative.incapacitated) incapacitationNotice(sideId, memberId)
        }
        kit.actionSheet("${operative.displayName} 耐伤 ${operative.currentWounds}/${operative.maxWounds}", input, listOf(
            CombatKit.SheetAction("受到 1 伤") { change(-1) },
            CombatKit.SheetAction("受到 2 伤") { change(-2) },
            CombatKit.SheetAction("受到 3 伤") { change(-3) },
            CombatKit.SheetAction("回复 1") { change(1) },
            CombatKit.SheetAction("回复 2") { change(2) },
            CombatKit.SheetAction("回复 3") { change(3) }
        ))
    }

    private fun tokenSheet(sideId: String, memberId: String) {
        val operative = battle.side(sideId).operatives.first { it.memberId == memberId }
        val name = ui.input("", "标记名称，例如：中毒、圣勉")
        val value = ui.input("", "数值（选填）", numeric = true)
        val header = ui.column().apply {
            if (operative.tokens.isNotEmpty()) {
                addView(ui.text("点按移除", 12f, ui.muted, true), ui.wrap(4))
                addView(kit.tagRow(operative.tokens.map { token ->
                    kit.tag("${token.label} · ${token.duration.label} ✕", kit.sideColor(sideId)) {
                        save(updateOperative(sideId, memberId) { o -> o.copy(tokens = o.tokens.filterNot { it.id == token.id }) },
                            "${operative.displayName}：移除${token.label}")
                    }
                }), ui.wrap(10))
            }
            addView(ui.row().apply {
                addView(kit.tag("离场 / 地下", ui.ink) { name.setText(OperativeToken.OFF_FIELD) })
                addView(View(context), LinearLayout.LayoutParams(ui.dp(8), 1))
                addView(kit.tag("晕眩", ui.ink) { name.setText(OperativeToken.STUNNED) })
            }, ui.wrap(8))
            addView(name, ui.wrap(6))
            addView(value, ui.wrap(8))
            addView(ui.text("持续到", 12f, ui.muted, true), ui.wrap(0))
        }
        kit.actionSheet("状态标记 · ${operative.displayName}", header, TokenDuration.entries.map { duration ->
            CombatKit.SheetAction(duration.label) {
                val title = name.text.toString().trim().ifBlank { "标记" }
                val token = OperativeToken(name = title, value = value.text.toString().toIntOrNull(), duration = duration)
                save(updateOperative(sideId, memberId) { it.copy(tokens = it.tokens + token) },
                    "${operative.displayName}：获得${token.label}（${duration.label}）")
            }
        })
    }

    private fun countersSheet(sideId: String) {
        val side = battle.side(sideId)
        val name = ui.input("", "新计数器名称，例如：节点")
        val reset = ui.input("", "每转折点重置为（选填）", numeric = true)
        val header = ui.column().apply {
            side.counters.forEach { c ->
                addView(numberRow(c.name, c.value, -99, 99) { v ->
                    saveSide(side.copy(counters = side.counters.map { if (it.id == c.id) it.copy(value = v) else it }),
                        "${side.playerName}：${c.name} ${c.value} → $v")
                    ui.refreshDialog()
                })
            }
            if (side.counters.isNotEmpty()) addView(ui.divider())
            addView(name, ui.wrap(6))
            addView(reset, ui.wrap(6))
        }
        kit.actionSheet("${side.playerName} · 小队计数器", header, buildList {
            add(CombatKit.SheetAction("添加计数器", primary = true) {
                val title = name.text.toString().trim()
                if (title.isNotBlank()) {
                    val counter = TeamCounter(name = title, value = reset.text.toString().toIntOrNull() ?: 0,
                        resetEachTurningPoint = reset.text.toString().toIntOrNull())
                    saveSide(battle.side(sideId).let { it.copy(counters = it.counters + counter) }, "${side.playerName}：添加计数器 $title")
                }
            })
            side.counters.forEach { c ->
                add(CombatKit.SheetAction("删除「${c.name}」") {
                    saveSide(battle.side(sideId).let { s -> s.copy(counters = s.counters.filterNot { it.id == c.id }) }, "${side.playerName}：删除计数器 ${c.name}")
                })
            }
        })
    }

    private fun saveSide(side: BattleSide, message: String) = save(BattleFlow.side(battle, side), message)

    private fun numberRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit): View = ui.row().apply {
        addView(ui.text(label, 14f, ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        addView(ui.button("−") { if (value > min) onChange(value - 1) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        addView(ui.text(value.toString(), 18f, ui.accent, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ui.dp(48), -2))
        addView(ui.button("+") { if (value < max) onChange(value + 1) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
    }

    // ---------------------------------------------------------------- score

    /** VP editor. Rows update in place, so the sheet stays open and steady while ± is tapped. */
    private fun scoreSheet(sideId: String) {
        holdRender = true
        ui.dialog("${battle.side(sideId).playerName} · 胜利点", onClose = { holdRender = false; render() }) { box, _ ->
            val content = ui.column()
            fun fill() {
                content.removeAllViews()
                val side = battle.side(sideId)
                ScoreType.entries.forEach { type ->
                    content.addView(numberRow(type.label, side.score.value(type), 0, BattleScore.MAX_SCORE_PER_OP) { v ->
                        val current = battle.side(sideId)
                        saveSide(current.copy(score = current.score.change(type, v - current.score.value(type))),
                            "${current.playerName}：${type.label} ${current.score.value(type)} → $v VP")
                        fill()
                    }, ui.wrap(4))
                }
                val level = BattleRules.killLevelAgainst(battle.opponent(sideId))
                content.addView(ui.text("击杀等级 $level · 每提升一级自动 +1VP", 12f, ui.muted), ui.wrap(8))
                val primary = if (battle.completed) side.primaryOp?.label ?: "未选择" else SecretSelection.primaryStatus(side.primaryOp)
                content.addView(ui.text("主要行动：$primary" + if (battle.completed) " · 奖励 ${side.score.bonus(side.primaryOp)}VP" else "", 12f, ui.muted), ui.wrap(8))
            }
            fill()
            box.addView(content)
            box.addView(ui.link("小队资料 · 装备与行动 ›") { teamInfo(sideId) }, ui.wrap())
        }
    }

    // ---------------------------------------------------------------- summary

    private fun renderSummary(body: LinearLayout, bar: LinearLayout) {
        body.addView(ui.text(BattleRules.resultLabel(battle), 28f, ui.accent, true).apply { gravity = Gravity.CENTER }, ui.wrap(4))
        body.addView(ui.text("${battle.sideA.totalScore} : ${battle.sideB.totalScore}", 22f, ui.ink, true).apply { gravity = Gravity.CENTER }, ui.wrap(14))
        val holder = if (isTablet) ui.row().apply { gravity = Gravity.TOP } else ui.column()
        listOf(battle.sideA, battle.sideB).forEach { side ->
            holder.addView(ui.column().apply {
                setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                background = ui.bg(kit.sideFill(side.id), kit.sideStroke(side.id))
                addView(ui.text("${side.playerName} · ${RosterCatalog.team(side.teamId).name}", 15f, kit.sideColor(side.id), true), ui.wrap(8))
                listOf(
                    "关键行动" to side.score.key,
                    "击杀行动（等级 ${BattleRules.killLevelAgainst(battle.opponent(side.id))}）" to side.score.kill,
                    "战术行动 · ${side.tacticalOpNotes.ifBlank { "—" }}" to side.score.tactical,
                    "主要行动奖励 · ${side.primaryOp?.label ?: "未选择"}" to side.score.bonus(side.primaryOp)
                ).forEach { (label, value) ->
                    addView(ui.row().apply {
                        addView(ui.text(label, 13f, ui.ink), LinearLayout.LayoutParams(0, -2, 1f))
                        addView(ui.text("$value", 15f, ui.ink, true))
                    }, ui.wrap(4))
                }
                addView(ui.divider())
                addView(ui.row().apply {
                    addView(ui.text("合计", 14f, ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(ui.text("${side.totalScore} VP", 18f, ui.accent, true))
                })
                addView(ui.text("在场 ${side.operatives.count { !it.incapacitated }}/${side.operatives.size}", 12f, ui.muted), ui.wrap(0))
            }, if (isTablet) LinearLayout.LayoutParams(0, -2, 1f).apply { if (side.id == BattleRules.SIDE_A) marginEnd = ui.dp(10) } else ui.wrap(10))
        }
        body.addView(holder, ui.wrap(12))
        val highlights = battle.log.filter { !it.undone && (it.message.contains("残废") || it.message.contains("击杀等级") || it.message.startsWith("第 ")) }
        if (highlights.isNotEmpty()) {
            body.addView(ui.text("关键事件", 12f, ui.muted, true), ui.wrap(4))
            highlights.forEach { body.addView(ui.text("TP${it.turningPoint} · ${it.message.substringBefore("\n")}", 12f, ui.muted), ui.wrap(3)) }
        }
        bar.addView(kit.actionBar(kit.primaryButton("完整记录") { journal() }, kit.secondaryButton("导出") { export() }))
    }

    // ---------------------------------------------------------------- menu

    private fun more() {
        kit.actionSheet("对局", null, buildList {
            add(CombatKit.SheetAction("完整记录", stack = true) { journal() })
            add(CombatKit.SheetAction("规则原文速查", stack = true) { rulesIndex() })
            add(CombatKit.SheetAction("关键行动 · ${battle.keyOp.ifBlank { "未记录" }}", stack = true) { tasks() })
            add(CombatKit.SheetAction("${battle.sideA.playerName} 小队资料", stack = true) { teamInfo(battle.sideA.id) })
            add(CombatKit.SheetAction("${battle.sideB.playerName} 小队资料", stack = true) { teamInfo(battle.sideB.id) })
            add(CombatKit.SheetAction("导出记录") { export() })
            if (!battle.completed) {
                add(CombatKit.SheetAction("修改名称") { rename() })
                add(CombatKit.SheetAction("提前结束对局") {
                    MaterialAlertDialogBuilder(this@BattleActivity)
                        .setTitle("提前结束对局？")
                        .setMessage("将结算击杀等级奖励并揭示主要行动。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("结束") { _, _ -> attempt { BattleRules.complete(battle) } }
                        .show()
                })
            }
        })
    }

    private fun rulesIndex() {
        val topics = listOf(
            "turn_sequence", "initiative", "shooting", "melee", "obscured", "vantage",
            "wounds", "ploys", "smoke", "movable_cover", "kill_op", "weapon_rules"
        )
        kit.actionSheet("规则原文", null, topics.mapNotNull { id ->
            CoreRules.topic(this, id)?.let { p -> CombatKit.SheetAction("${p.title} · p${p.pages.joinToString("-")}", stack = true) { CoreRules.show(ui, id) } }
        })
    }

    private var journalFilter = BattleLogFormat.Filter.ALL

    /** Battle log grouped by turning point, with category tags and coloured names, weapons, VP and CP. */
    private fun journal() {
        ui.dialog("完整记录", expanded = true) { box, sheet ->
            if (!battle.completed) {
                val input = ui.input("", "备注")
                box.addView(ui.row().apply {
                    addView(input, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(ui.button("保存", true) {
                        if (input.text.isNotBlank()) { sheet.dismiss(); save(battle, input.text.toString()) }
                    }, LinearLayout.LayoutParams(ui.dp(72), -2))
                }, ui.wrap(12))
            }
            val lexicon = BattleLogFormat.lexicon(battle)
            val entries = battle.log.map { it to BattleLogFormat.kind(it.message, it.manual) }
            val list = ui.column()
            val filters = ui.row()
            fun fill() {
                filters.removeAllViews()
                BattleLogFormat.Filter.values().forEach { filter ->
                    val count = entries.count { (e, k) -> !e.undone && filter.accepts(k) }
                    val on = filter == journalFilter
                    filters.addView(kit.tag("${filter.label} $count", if (on) ui.accent else ui.muted) {
                        journalFilter = filter; fill(); ui.refreshDialog()
                    }.apply {
                        minHeight = ui.dp(36); gravity = Gravity.CENTER_VERTICAL; textSize = 12f
                        if (on) background = ui.bg(0xff3a2418.toInt(), ui.accent)
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) })
                }
                list.removeAllViews()
                // "全部" keeps undone entries struck through; the narrower filters show only what stands.
                val shown = entries.filter { (e, k) -> journalFilter == BattleLogFormat.Filter.ALL || (!e.undone && journalFilter.accepts(k)) }
                if (shown.isEmpty()) list.addView(ui.text(if (battle.log.isEmpty()) "暂无记录" else "这一类暂无记录", color = ui.muted))
                shown.asReversed().groupBy { it.first.turningPoint }.forEach { (tp, group) ->
                    list.addView(ui.text(if (tp <= 0) "战前设置" else "第 $tp 转折点", 13f, ui.accent, true).apply {
                        setPadding(0, ui.dp(10), 0, ui.dp(4))
                    }, ui.wrap(4))
                    group.forEach { (entry, kind) -> list.addView(journalEntry(entry, kind, lexicon), ui.wrap(6)) }
                }
            }
            box.addView(android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(filters)
            }, ui.wrap(8))
            box.addView(list, ui.wrap())
            fill()
        }
    }

    private fun journalEntry(entry: BattleLogEntry, kind: BattleLogFormat.Kind, lexicon: Map<String, BattleLogFormat.Role>): View {
        val (head, steps) = BattleLogFormat.split(entry.message)
        val kindColor = when (kind) {
            BattleLogFormat.Kind.SHOOT, BattleLogFormat.Kind.MELEE -> ui.accent
            BattleLogFormat.Kind.DICE -> kit.steel
            BattleLogFormat.Kind.INJURY -> kit.danger
            BattleLogFormat.Kind.SCORE -> kit.gold
            BattleLogFormat.Kind.CP -> LOG_CP
            BattleLogFormat.Kind.NOTE -> ui.ink
            else -> ui.muted
        }
        return ui.column().apply {
            setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
            background = ui.bg(0xff1d1d1c.toInt(), if (kind == BattleLogFormat.Kind.INJURY && !entry.undone) 0xff5a2624.toInt() else 0xff2c2c2a.toInt())
            if (entry.undone) alpha = .5f
            addView(ui.row().apply {
                addView(kit.tag(if (entry.undone) "已撤回" else kind.label, kindColor), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(8) })
                addView(ui.text(timestamp(entry.timestamp), 11f, 0xff77756f.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
            }, ui.wrap(4))
            addView(ui.text("", 14f, ui.ink).apply { text = styled(head, lexicon, entry.undone) }, ui.wrap(0))
            if (steps.isNotEmpty()) {
                val detail = ui.column().apply { visibility = View.GONE; setPadding(ui.dp(8), ui.dp(4), 0, 0) }
                steps.forEachIndexed { i, step ->
                    detail.addView(ui.text("", 12f, ui.muted).apply { text = styled("${i + 1}. $step", lexicon, entry.undone) }, ui.wrap(2))
                }
                addView(ui.text("结算过程 · ${steps.size} 步 ›", 12f, ui.muted, true).apply {
                    minHeight = ui.dp(36); gravity = Gravity.CENTER_VERTICAL
                    setOnClickListener {
                        val open = detail.visibility != View.VISIBLE
                        detail.visibility = if (open) View.VISIBLE else View.GONE
                        text = if (open) "结算过程 · 收起" else "结算过程 · ${steps.size} 步 ›"
                        ui.refreshDialog()
                    }
                })
                addView(detail)
            }
        }
    }

    private fun styled(text: String, lexicon: Map<String, BattleLogFormat.Role>, struck: Boolean): CharSequence =
        android.text.SpannableString(text).apply {
            BattleLogFormat.spans(text, lexicon).forEach { span ->
                val color = when (span.role) {
                    BattleLogFormat.Role.SIDE_A -> kit.sideColor(BattleRules.SIDE_A)
                    BattleLogFormat.Role.SIDE_B -> kit.sideColor(BattleRules.SIDE_B)
                    BattleLogFormat.Role.WEAPON -> LOG_WEAPON
                    BattleLogFormat.Role.SCORE -> kit.gold
                    BattleLogFormat.Role.CP -> LOG_CP
                    BattleLogFormat.Role.DANGER -> kit.danger
                }
                setSpan(android.text.style.ForegroundColorSpan(color), span.start, span.end, 0)
                setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), span.start, span.end, 0)
            }
            if (struck) setSpan(android.text.style.StrikethroughSpan(), 0, length, 0)
        }

    private fun export() {
        val report = buildString {
            appendLine(battle.name)
            appendLine("关键行动：${battle.keyOp} · TP ${battle.turningPoint}")
            if (battle.completed) appendLine("结果：${BattleRules.resultLabel(battle)} ${battle.sideA.totalScore}:${battle.sideB.totalScore}")
            listOf(battle.sideA, battle.sideB).forEach { s ->
                appendLine("${s.playerName} · ${s.rosterName} · ${if (battle.completed) s.totalScore else s.currentScore} VP / ${s.commandPoints} CP")
                appendLine("装备：${s.equipmentNotes}")
                if (battle.completed) appendLine("战术：${s.tacticalOpNotes}；主要行动：${s.primaryOp?.label}")
                s.operatives.forEach { appendLine("${it.displayName} · ${it.currentWounds}/${it.maxWounds} · ${if (it.incapacitated) "已残废" else "在场"}") }
            }
            battle.log.forEach { appendLine("${timestamp(it.timestamp)} · TP${it.turningPoint} · ${if (it.undone) "[已撤回] " else ""}${it.message}") }
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "导出对局记录"))
    }

    private fun rename() {
        ui.dialog("名称") { fields, edit ->
            val name = ui.input(battle.name, "对局名称")
            val a = ui.input(battle.sideA.playerName, "玩家 A")
            val b = ui.input(battle.sideB.playerName, "玩家 B")
            fields.addView(name, ui.wrap()); fields.addView(a, ui.wrap()); fields.addView(b, ui.wrap())
            fields.addView(ui.button("保存", true) {
                edit.dismiss()
                save(battle.copy(
                    name = name.text.toString().ifBlank { battle.name },
                    sideA = battle.sideA.copy(playerName = a.text.toString().ifBlank { "玩家 A" }),
                    sideB = battle.sideB.copy(playerName = b.text.toString().ifBlank { "玩家 B" })
                ), "更新对局名称")
            })
        }
    }

    private fun tasks() {
        val card = MissionCards.all.firstOrNull { it.name == battle.keyOp }
        if (card != null) MissionCards.detail(ui, card)
        else ui.note(if (battle.keyOp.isBlank()) "这份旧对局未记录关键行动。" else "当前关键行动：${battle.keyOp}")
    }

    private fun teamInfo(sideId: String) {
        val side = battle.side(sideId)
        BattleReference(ui).faction(side.teamId) { box ->
            box.addView(ui.text("当前装备", 18f, ui.accent, true), ui.wrap(8))
            EquipmentCards.selected(ui, box, side.teamId, side.equipmentNotes)
            val tacticalSection = ui.column()
            fun renderTacticalSection() {
                tacticalSection.removeAllViews()
                val tactical = battle.side(sideId)
                val tacticalPublic = SecretSelection.tacticalDetailsArePublic(tactical.tacticalOpNotes, tactical.tacticalOpRevealed, battle.completed)
                tacticalSection.addView(ui.link("战术行动 · ${SecretSelection.tacticalStatus(tactical.tacticalOpNotes, tacticalPublic)} ›") {
                    if (tacticalPublic && tactical.tacticalOpNotes.isNotBlank()) {
                        MissionCards.all.firstOrNull { it.name == tactical.tacticalOpNotes }?.let { MissionCards.detail(ui, it) }
                    } else chooseTactical(sideId, ::renderTacticalSection)
                }, ui.wrap())
            }
            renderTacticalSection()
            box.addView(tacticalSection, ui.wrap())
            val primaryStatus = if (battle.completed) side.primaryOp?.label ?: "未选择" else SecretSelection.primaryStatus(side.primaryOp)
            box.addView(ui.link("主要行动 · $primaryStatus ›") { viewPrimary(sideId) }, ui.wrap())
        }
    }

    private fun chooseTactical(sideId: String, onChanged: (() -> Unit)? = null) {
        val current = battle.side(sideId)
        SecretSelection.open(ui, current.playerName, "战术行动") { box, sheet ->
            val archetypes = TeamArchetypes.forTeam(current.teamId)
            box.addView(ui.text("${RosterCatalog.team(current.teamId).name} · 小队原型", 16f, ui.accent, true), ui.wrap(4))
            box.addView(ui.text(archetypes.joinToString("  ·  "), 15f, bold = true), ui.wrap(12))
            if (current.tacticalOpNotes.isNotBlank()) {
                val card = MissionCards.all.firstOrNull { it.name == current.tacticalOpNotes }
                box.addView(ui.text("当前选择：${current.tacticalOpNotes}", 17f, ui.accent, true), ui.wrap(4))
                if (card != null) MissionCards.contents(ui, box, card)
                box.addView(ui.button("按卡片时机公开战术行动", true) {
                    ui.dialog("确认公开？") { confirm, confirmDialog ->
                        confirm.addView(ui.text("公开后，双方都会在公共界面看到「${current.tacticalOpNotes}」。", 14f), ui.wrap(16))
                        confirm.addView(ui.button("确认公开", true) {
                            confirmDialog.dismiss()
                            sheet.dismiss()
                            save(battle.side(sideId).copy(tacticalOpRevealed = true).let { BattleFlow.side(battle, it) },
                                "${current.playerName}公开战术行动：${current.tacticalOpNotes}", manual = false)
                            onChanged?.invoke()
                            ui.refreshDialog()
                        }, ui.wrap())
                    }
                }, ui.wrap(12))
            } else {
                box.addView(ui.text("尚未选择战术行动。", 12f, ui.muted), ui.wrap(12))
            }
            if (!battle.completed && current.tacticalOpNotes.isBlank()) box.addView(ui.button("为旧对局补选战术行动") {
                MissionCards.picker(ui, tactical = true, selected = current.tacticalOpNotes, teamId = current.teamId) { card ->
                    sheet.dismiss()
                    save(BattleFlow.side(battle, battle.side(sideId).copy(tacticalOpNotes = card.name, tacticalOpRevealed = false)),
                        "${current.playerName}已私密选择战术行动", manual = false)
                    onChanged?.invoke()
                    ui.refreshDialog()
                }
            }, ui.wrap())
        }
    }

    private fun viewPrimary(sideId: String) {
        val current = battle.side(sideId)
        if (battle.completed) {
            ui.dialog("主要行动") { box, _ ->
                box.addView(ui.text(current.primaryOp?.label ?: "未选择", 20f, ui.accent, true), ui.wrap(8))
            }
            return
        }
        SecretSelection.open(ui, current.playerName, "查看主要行动") { box, _ ->
            box.addView(ui.text(current.primaryOp?.label ?: "未选择", 20f, ui.accent, true), ui.wrap(8))
            box.addView(ui.text("战斗结束时揭示", 12f, ui.muted), ui.wrap())
        }
    }

    companion object {
        private val LOG_WEAPON = 0xff9fcf7a.toInt()
        private val LOG_CP = 0xffb7a3e0.toInt()
        const val EXTRA_BATTLE_ID = "battle_id"
        private const val NOTICE_MS = 8000
        /** Reason recorded with the shoot/fight action when the core rules needed an exception. */
        const val EXTRA_ACTION_REASON = "battle_action_reason"
    }
}
