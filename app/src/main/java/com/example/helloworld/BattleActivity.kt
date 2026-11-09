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
            val prevIncap = (battle.sideA.operatives.map { BattleRules.SIDE_A to it } +
                battle.sideB.operatives.map { BattleRules.SIDE_B to it })
                .filter { it.second.incapacitated }
                .map { "${it.first}/${it.second.memberId}" }
                .toSet()
            store.load(battle.battleId)?.let { loaded ->
                battle = loaded
                val newlyIncap = (loaded.sideA.operatives.map { BattleRules.SIDE_A to it } +
                    loaded.sideB.operatives.map { BattleRules.SIDE_B to it })
                    .firstOrNull { it.second.incapacitated && "${it.first}/${it.second.memberId}" !in prevIncap }
                render()
                if (newlyIncap != null) {
                    incapacitationNotice(newlyIncap.first, newlyIncap.second.memberId)
                }
                return
            }
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

    private fun statLine(side: BattleSide, operative: BattleOperativeState) = OperativeStats.line(this, side, operative)

    /**
     * Generic rule tools on one operative: weapons not on its datacard, a different datacard,
     * or an action it takes under the opponent's control. All need the rule name and are logged.
     */
    private fun operativeRuleSheet(sideId: String, memberId: String) {
        val side = battle.side(sideId)
        val o = side.operatives.first { it.memberId == memberId }
        val input = sourceInput("规则依据（选填，点上方预设或留空默认“规则效果”）")
        fun reason(): String = input.text.toString().trim().ifBlank { "规则效果" }
        val granted = o.weapons.filter { it.id.startsWith("granted-") }
        val tmpl = template(side, o)
        val rulePresets = buildList {
            when (side.teamId) {
                "KT-CHAOS_CULT" -> add("变异")
                "KT-INQUISITORIAL_AGENTS" -> add("军火库")
                "KT-BROOD_BROTHERS", "CHAOS-WC" -> add("心灵控制")
                "KT-HIEROTEK_CIRCLE" -> add("机械魔宠")
                "KT-EXODITE_DRAGON_MASTERS" -> add("氏族之刃升级")
            }
            addAll(listOf("变异", "军火库", "心灵控制", "阵营装备", "特工能力", "规则效果"))
        }.distinct()
        kit.actionSheet("${o.displayName} · 规则效果", ui.column().apply {
            addView(ui.text("用于切换数据卡武器配置，或处理数据卡之外的小队规则：借用武器、换卡、被对方临时控制。", 12f, ui.muted), ui.wrap(6))
            addView(kit.tagRow(rulePresets.map { p ->
                kit.tag(p, ui.accent) {
                    input.setText(p)
                    input.setSelection(input.text.length)
                }
            }), ui.wrap(6))
            addView(input)
        }, buildList {
            if (tmpl != null && tmpl.weapons.size > 1) {
                add(CombatKit.SheetAction("切换/勾选本特工数据卡武器配置（共 ${tmpl.weapons.size} 件）…", primary = true) {
                    datacardWeaponsSheet(sideId, memberId)
                })
            }
            add(CombatKit.SheetAction("获得一件武器（借用其他特工的武器）…") {
                borrowWeaponSheet(sideId, memberId, reason())
            })
            add(CombatKit.SheetAction("获得一件武器（手填武器档案）…") {
                customWeaponSheet(sideId, memberId, reason())
            })
            granted.forEach { w ->
                add(CombatKit.SheetAction("移除规则授予的「${w.name}」") { attempt { BattleFlow.removeWeapon(battle, sideId, memberId, w.id) } })
            }
            add(CombatKit.SheetAction("更换为本队另一张数据卡（保留已损失耐伤）…") {
                datacardSheet(sideId, memberId, reason(), keepLost = true)
            })
            add(CombatKit.SheetAction("更换为本队另一张数据卡（耐伤回满）…") {
                datacardSheet(sideId, memberId, reason(), keepLost = false)
            })
            if (battle.phase == BattlePhase.FIREFIGHT && !o.incapacitated) {
                val controller = battle.opponent(sideId)
                add(CombatKit.SheetAction("由 ${controller.playerName} 临时控制，执行一个免费行动…") {
                    controlledActionSheet(sideId, memberId, reason())
                })
            }
        })
    }

    /** A rule adds an operative mid-battle (变异害虫、召唤、增援…). */
    private fun addOperativeSheet(sideId: String) {
        val side = battle.side(sideId)
        val team = RosterCatalog.team(side.teamId)
        val defaultReason = when (side.teamId) {
            "KT-GELLERPOX_INFECTED" -> "变异害虫"
            "NEC-CAN" -> "无尽巨虫"
            "KT-CHAOS_CULT" -> "创生"
            else -> "规则增援"
        }
        val input = sourceInput("规则依据（选填，留空默认「$defaultReason」）")
        var ready = false
        var counts = false
        val options = ui.column()
        fun renderOptions() {
            options.removeAllViews()
            options.addView(kit.segmented("状态", listOf("待机", "就绪"), if (ready) 1 else 0) { ready = it == 1; renderOptions() }, ui.wrap(6))
            options.addView(kit.segmented("计入击杀行动", listOf("不计入", "计入"), if (counts) 1 else 0) { counts = it == 1; renderOptions() })
        }
        renderOptions()
        val presets = listOf(defaultReason, "变异害虫", "无尽巨虫", "创生", "战术召唤", "增援").distinct()
        kit.actionSheet("${side.playerName} · 由规则新增特工", ui.column().apply {
            addView(kit.tagRow(presets.map { p ->
                kit.tag(p, ui.accent) {
                    input.setText(p)
                    input.setSelection(input.text.length)
                }
            }), ui.wrap(6))
            addView(input, ui.wrap(8)); addView(options)
        }, team.operatives.map { t ->
            CombatKit.SheetAction("${t.name} · APL ${t.apl} · 耐伤 ${t.wounds}") {
                val reason = input.text.toString().trim().ifBlank { defaultReason }
                attempt { BattleFlow.addOperative(battle, sideId, t, ready, counts, reason) }
            }
        })
    }

    /** Toggle any weapon from the operative's own datacard (e.g. switching firearm modes or melee options). */
    private fun datacardWeaponsSheet(sideId: String, memberId: String) {
        holdRender = true
        val side = battle.side(sideId)
        val o = side.operatives.first { it.memberId == memberId }
        val tmpl = template(side, o) ?: return
        ui.dialog("${o.displayName} · 数据卡武器配置", onClose = { holdRender = false; render() }) { box, _ ->
            val list = ui.column()
            fun fill() {
                list.removeAllViews()
                val curSide = battle.side(sideId)
                val curOp = curSide.operatives.first { it.memberId == memberId }
                val activeWeapons = if (curOp.weapons.isNotEmpty()) curOp.weapons else CombatWeapons.of(this, curSide, curOp)
                val granted = curOp.weapons.filter { it.id.startsWith("granted-") }
                list.addView(ui.text("点按勾选或取消该特工数据卡上的武器档案（至少保留 1 件）：", 12f, ui.muted), ui.wrap(8))
                tmpl.weapons.forEach { w ->
                    val on = activeWeapons.any { it.id == w.id || it.name == w.name }
                    list.addView(ui.row().apply {
                        setPadding(ui.dp(10), ui.dp(8), ui.dp(8), ui.dp(8))
                        background = ui.bg(if (on) 0xff2a1d15.toInt() else 0xff1a1a1a.toInt(), if (on) ui.accent else 0xff343432.toInt())
                        addView(ui.column().apply {
                            addView(ui.text((if (on) "✓ " else "") + w.name, 14f, if (on) ui.accent else ui.ink, true), ui.wrap(2))
                            addView(ui.text(w.profile, 12f, 0xffc9c7c2.toInt()), ui.wrap(0))
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(ui.button(if (on) "已装备" else "装备", !on) {
                            val currentBase = tmpl.weapons.filter { tw -> activeWeapons.any { it.id == tw.id || it.name == tw.name } }
                            val nextBase = if (on) {
                                if (currentBase.size <= 1) { note("至少需要保留 1 件武器"); return@button }
                                currentBase.filterNot { it.id == w.id }
                            } else {
                                currentBase + w
                            }
                            val nextSnapshots = nextBase.map(BattleRules::snapshot) + granted
                            save(updateOperative(sideId, memberId) { it.copy(weapons = nextSnapshots) },
                                "${curOp.displayName}：更新武器配置为 ${nextBase.joinToString("、") { it.name }}")
                            fill()
                            ui.refreshDialog()
                        }, LinearLayout.LayoutParams(ui.dp(84), ui.dp(42)))
                    }, ui.wrap(6))
                }
            }
            fill()
            box.addView(list)
        }
    }

    /** Weapons of every datacard in this team, plus universal/faction equipment & faction rule weapons, to borrow. */
    private fun borrowWeaponSheet(sideId: String, memberId: String, reason: String) {
        val side = battle.side(sideId)
        val team = RosterCatalog.team(side.teamId)
        val extra = CombatWeapons.extraOptions(side.teamId).map { (source, w) -> source to w }
        val teamOptions = team.operatives.flatMap { t -> t.weapons.map { t.name to it } }
        val options = (extra + teamOptions).distinctBy { it.second.name + it.second.profile }
        kit.actionSheet("选择要获得的武器 · ${team.name}", null, options.map { (source, w) ->
            CombatKit.SheetAction("${w.name} · ${w.profile}（$source）") {
                attempt { BattleFlow.grantWeapon(battle, sideId, memberId, w, reason) }
            }
        })
    }

    private fun customWeaponSheet(sideId: String, memberId: String, reason: String) {
        val name = ui.input("", "武器名称")
        val profile = ui.input("", "档案：4A · 3+ · 3/4 · 范围 8″、穿刺 1")
        var melee = false
        val kind = ui.column()
        fun renderKind() { kind.removeAllViews(); kind.addView(kit.segmented("类型", listOf("远程", "近战"), if (melee) 1 else 0) { melee = it == 1; renderKind() }) }
        renderKind()
        kit.actionSheet("手填武器档案", ui.column().apply {
            addView(name, ui.wrap(6)); addView(profile, ui.wrap(6)); addView(kind)
            addView(ui.text("格式：攻击A 命中+ 普通/暴击 武器规则，可用空格或 · 分隔，例如 4A 2+ 4/5 爆炸 2″", 12f, ui.muted), ui.wrap(0))
        }, listOf(CombatKit.SheetAction("加入", primary = true) {
            val title = name.text.toString().trim()
            val text = normalizeProfile(profile.text.toString())
            val stats = WeaponProfileParser.parse(text)
            if (title.isBlank() || stats.normalDamage <= 0) { note("请填写名称和有效档案，例如 4A · 3+ · 3/4"); return@SheetAction }
            val id = "custom_" + title.hashCode().toUInt().toString(16)
            attempt { BattleFlow.grantWeapon(battle, sideId, memberId, WeaponOption(id, title, text, if (melee) WeaponKind.MELEE else WeaponKind.RANGED), reason) }
        }))
    }

    /** "4A 2+ 4/5 爆炸 2″" → "4A · 2+ · 4/5 · 爆炸 2″"; already dotted profiles are kept. */
    private fun normalizeProfile(raw: String): String {
        val text = raw.trim()
        if ('·' in text) return text
        val parts = text.split(Regex("\\s+"), limit = 4)
        return parts.joinToString(" · ")
    }

    private fun datacardSheet(sideId: String, memberId: String, reason: String, keepLost: Boolean) {
        val side = battle.side(sideId)
        val current = side.operatives.first { it.memberId == memberId }.operativeId
        val team = RosterCatalog.team(side.teamId)
        kit.actionSheet("更换为哪张数据卡", null, team.operatives.filter { it.id != current }.map { t ->
            CombatKit.SheetAction("${t.name} · 耐伤 ${t.wounds}") { attempt { BattleFlow.replaceDatacard(battle, sideId, memberId, t, keepLost, reason) } }
        })
    }

    /** The opponent controls this operative for one free action (e.g. 心灵控制); nothing else changes. */
    private fun controlledActionSheet(sideId: String, memberId: String, reason: String) {
        val o = battle.side(sideId).operatives.first { it.memberId == memberId }
        val controller = battle.opponent(sideId)
        val other = OrderTokens.oppositeOrder(o.order)
        val action = ui.input("", "行动名称，例如：射击、转移")
        kit.actionSheet("${controller.playerName} 控制 ${o.displayName}", ui.column().apply {
            addView(ui.text("记录一个免费行动，不消耗 AP、不改变激活顺序。射击 / 近战结果请在结算后用耐伤调整记录。", 12f, ui.muted), ui.wrap(8))
            addView(action)
        }, listOf(
            CombatKit.SheetAction("记录行动", primary = true) {
                val name = action.text.toString().trim().ifBlank { "免费行动" }
                save(battle, "${controller.playerName}临时控制 ${o.displayName}：$name（免费，依据：$reason）")
            },
            CombatKit.SheetAction("命令改为$other") {
                save(updateOperative(sideId, memberId) { it.copy(order = other) },
                    "${controller.playerName}临时控制 ${o.displayName}：命令改为$other（依据：$reason）")
            }
        ))
    }

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
            undoLabel = store.undoPreview(battle.battleId),
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
        val tp = (if (battle.phase == BattlePhase.INITIATIVE) battle.turningPoint + 1 else battle.turningPoint)
            .coerceIn(1, BattleRules.TURNING_POINT_COUNT)
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
                    addView(ui.button("−") { spendCpSheet(side.id) }.apply { contentDescription = "${side.playerName} 消耗 CP：使用交战计谋或其他原因" },
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
            entry("流程速查图", ui.ink) { MissionCards.original(ui) },
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

    /**
     * Spending CP needs a reason. In the firefight phase the usual one is a firefight ploy, so the
     * team's ploys are offered first; strategy phase points to the strategic ploy sheet.
     */
    private fun spendCpSheet(sideId: String) {
        val side = battle.side(sideId)
        if (battle.phase == BattlePhase.STRATEGY && battle.progress.nextSide == sideId) { ploySheet(); return }
        ui.dialog("${side.playerName} · 消耗 CP / 交战规则（剩 ${side.commandPoints}CP）", expanded = battle.phase == BattlePhase.FIREFIGHT) { box, sheet ->
            fun close(then: () -> Unit) {
                sheet.dismiss()
                android.os.Handler(android.os.Looper.getMainLooper()).post(then)
            }
            if (battle.phase == BattlePhase.FIREFIGHT) {
                var cost = 1
                val name = ui.input("", "交战计谋或阵营规则名称（点下方预制项自动填入，也可自己填写）")
                val subOptionsBox = ui.column()
                val costView = ui.column()
                fun renderCost() {
                    costView.removeAllViews()
                    costView.addView(numberRow("CP", cost, 0, 9) { cost = it; renderCost() })
                }
                fun renderSubOptions(ploy: TeamPloy) {
                    subOptionsBox.removeAllViews()
                    val subs = TeamPloys.subOptions(ploy)
                    if (subs.isNotEmpty()) {
                        subOptionsBox.addView(ui.text("点选「${ploy.name}」子项：", 12f, ui.accent, true), ui.wrap(4))
                        subOptionsBox.addView(kit.tagRow(subs.map { sub ->
                            kit.tag(sub, ui.ink) {
                                val full = "${ploy.name}（$sub）"
                                name.setText(full)
                                name.setSelection(name.text.length)
                            }
                        }), ui.wrap(6))
                    }
                    ui.refreshDialog()
                }
                renderCost()
                box.addView(name, ui.wrap(6))
                box.addView(subOptionsBox)
                box.addView(costView, ui.wrap(8))
                box.addView(ui.button("使用交战计谋 / 规则", true) {
                    val title = name.text.toString().trim()
                    if (title.isBlank()) { note("请选择或填写计谋名称"); return@button }
                    close {
                        if (BattleFlow.firefightPloyUsed(battle, sideId, title)) {
                            MaterialAlertDialogBuilder(this)
                                .setTitle("本转折点已用过「$title」")
                                .setMessage("除指挥重掷外，同一计谋每个转折点只能使用一次。有规则允许时可以继续记录。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("仍然记录") { _, _ -> attempt { BattleFlow.firefightPloy(battle, sideId, title, cost, allowRepeat = true) } }
                                .show()
                        } else attempt { BattleFlow.firefightPloy(battle, sideId, title, cost) }
                    }
                }, ui.wrap(8))
                box.addView(ui.text("指挥重掷请在掷骰界面点骰子使用，会自动扣 CP。", 12f, ui.muted), ui.wrap(8))
                ployPresets(box, side.teamId, TeamPloys.FIREFIGHT, TeamPloys.of(this, side.teamId, TeamPloys.FIREFIGHT), used = { BattleFlow.firefightPloyUsed(battle, sideId, it) }) { picked ->
                    cost = 1
                    renderCost()
                    name.setText(picked.name); name.setSelection(name.text.length)
                    renderSubOptions(picked)
                    (box.parent as? ScrollView)?.post { (box.parent as? ScrollView)?.smoothScrollTo(0, 0) }
                }
                val ffRules = TeamPloys.firefightRules(this, side.teamId)
                if (ffRules.isNotEmpty()) {
                    ployPresets(box, side.teamId, "交战期阵营规则（0CP）", ffRules, used = { BattleFlow.firefightPloyUsed(battle, sideId, it) }) { picked ->
                        cost = 0
                        renderCost()
                        name.setText(picked.name); name.setSelection(name.text.length)
                        renderSubOptions(picked)
                        (box.parent as? ScrollView)?.post { (box.parent as? ScrollView)?.smoothScrollTo(0, 0) }
                    }
                }
                box.addView(ui.divider())
            }
            val reason = ui.input("", "其他原因（选填）：例如更正记录")
            box.addView(reason, ui.wrap(6))
            box.addView(ui.button("仅 −1CP（不是计谋）", false) {
                close { changeCp(sideId, -1, reason.text.toString().trim()) }
            }, ui.wrap())
        }
    }

    private fun changeCp(sideId: String, delta: Int, reason: String = "") {
        val side = battle.side(sideId)
        val points = (side.commandPoints + delta).coerceAtLeast(0)
        if (points == side.commandPoints) return
        save(BattleFlow.side(battle, side.copy(commandPoints = points)), "${side.playerName}：CP ${side.commandPoints} → $points${noteSuffix(reason)}")
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
        body.addView(View(this), LinearLayout.LayoutParams(-1, ui.dp(48)))
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
            .filter { it.second != BattleFlow.PRIMARY_PLAN && !it.second.startsWith("交战:") }
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
        ui.dialog("${side.playerName} · 战略计划 / 战略计谋（剩 ${side.commandPoints}CP）", expanded = true) { box, sheet ->
            var cost = 1
            val name = ui.input("", "计谋或战略计划名称（点下方预制项自动填入，也可自己填写）")
            val subOptionsBox = ui.column()
            val costView = ui.column()
            fun renderCost() {
                costView.removeAllViews()
                costView.addView(numberRow("CP", cost, 0, 9) { cost = it; renderCost() })
            }
            fun renderSubOptions(ploy: TeamPloy) {
                subOptionsBox.removeAllViews()
                val subs = TeamPloys.subOptions(ploy)
                if (subs.isNotEmpty()) {
                    subOptionsBox.addView(ui.text("点选「${ploy.name}」子项：", 12f, ui.accent, true), ui.wrap(4))
                    subOptionsBox.addView(kit.tagRow(subs.map { sub ->
                        kit.tag(sub, ui.ink) {
                            val full = "${ploy.name}（$sub）"
                            name.setText(full)
                            name.setSelection(name.text.length)
                        }
                    }), ui.wrap(6))
                }
                ui.refreshDialog()
            }
            renderCost()
            box.addView(name, ui.wrap(6))
            box.addView(subOptionsBox)
            box.addView(costView, ui.wrap(8))
            box.addView(ui.button("使用", true) {
                val title = name.text.toString().trim().ifBlank { "战略计谋" }
                sheet.dismiss()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
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
            }, ui.wrap(10))
            ployPresets(box, side.teamId, TeamPloys.STRATEGY, TeamPloys.of(this, side.teamId, TeamPloys.STRATEGY), used = { BattleFlow.planUsed(battle, it) }) { picked ->
                cost = 1
                renderCost()
                name.setText(picked.name)
                name.setSelection(name.text.length)
                renderSubOptions(picked)
                (box.parent as? ScrollView)?.post { (box.parent as? ScrollView)?.smoothScrollTo(0, 0) }
            }
            val gambits = TeamPloys.strategicGambits(
                this,
                side.teamId,
                activeOperativeIds = side.operatives.filter { !it.incapacitated }.map { it.operativeId }.toSet(),
                equipmentNotes = side.equipmentNotes
            )
            if (gambits.isNotEmpty()) {
                ployPresets(box, side.teamId, "战略计划 / 阵营规则（0CP）", gambits, used = { BattleFlow.planUsed(battle, it) }) { picked ->
                    cost = 0
                    renderCost()
                    name.setText(picked.name)
                    name.setSelection(name.text.length)
                    renderSubOptions(picked)
                    (box.parent as? ScrollView)?.post { (box.parent as? ScrollView)?.smoothScrollTo(0, 0) }
                }
            }
        }
    }

    /**
     * Preset ploys of a team as tappable rows: tap the name to fill it in, "规则" opens the full
     * text. Each row shows a one-line summary so players see what the ploy does at a glance.
     */
    private fun ployPresets(
        box: LinearLayout,
        teamId: String,
        category: String,
        ploys: List<TeamPloy>,
        used: (String) -> Boolean,
        onPick: (TeamPloy) -> Unit
    ) {
        box.addView(ui.text("${RosterCatalog.team(teamId).name} 的$category", 13f, ui.accent, true), ui.wrap(6))
        if (ploys.isEmpty()) {
            box.addView(ui.text("暂无收录，可直接填写名称。", 12f, ui.muted), ui.wrap(8))
            return
        }
        ploys.forEach { ploy ->
            val already = used(ploy.name)
            box.addView(ui.row().apply {
                setPadding(ui.dp(10), ui.dp(6), ui.dp(4), ui.dp(6))
                background = ui.bg(0xff1a1a1a.toInt(), 0xff343432.toInt())
                isClickable = true
                setOnClickListener { onPick(ploy) }
                contentDescription = "选择计谋${ploy.name}"
                addView(ui.column().apply {
                    addView(ui.text(ploy.name + if (already) " · 本转折点已用" else "", 15f, if (already) ui.muted else ui.ink, true), ui.wrap(2))
                    if (ploy.summary.isNotBlank()) addView(ui.text(ploy.summary, 12f, 0xffc9c7c2.toInt()), ui.wrap(0))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.link("规则") { showPloy(ploy) }.apply { contentDescription = "查看${ploy.name}规则" },
                    LinearLayout.LayoutParams(ui.dp(64), ui.dp(44)))
            }, ui.wrap(6))
        }
    }

    private fun showPloy(ploy: TeamPloy) {
        ui.reference(ploy.name) { box, _ ->
            if (ploy.body.isNotBlank()) box.addView(ui.text(ploy.body), ui.wrap())
            else box.addView(ui.text("暂无文字版，请查看原卡。", 12f, ui.muted), ui.wrap())
            ploy.card?.let { ui.preview(box, ploy.name, "team_rules/$it") }
        }
    }

    // ---------------------------------------------------------------- firefight

    private fun renderFirefight(body: LinearLayout, bar: LinearLayout) {
        if (battle.progress.ploys.isNotEmpty()) {
            body.addView(kit.tagRow(
                listOf(ui.text("本转折点生效战略", 12f, ui.muted, true)) +
                    battle.progress.ploys.map { p ->
                        val sideId = p.substringBefore("：", "")
                        val planName = p.substringAfter("：", p)
                        val sideName = if (sideId == BattleRules.SIDE_A || sideId == BattleRules.SIDE_B) battle.side(sideId).playerName else sideId
                        val sideColor = when (sideId) {
                            BattleRules.SIDE_A, battle.sideA.playerName -> kit.sideColor(BattleRules.SIDE_A)
                            BattleRules.SIDE_B, battle.sideB.playerName -> kit.sideColor(BattleRules.SIDE_B)
                            else -> ui.accent
                        }
                        kit.tag("$sideName：$planName", sideColor)
                    }
            ), ui.wrap(8))
        }
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
        mustActivateNote()?.let { body.addView(it, ui.wrap(10)) }
        renderOperatives(body)
        val isLastTp = battle.turningPoint >= BattleRules.TURNING_POINT_COUNT
        val endLabel = if (isLastTp) "结束战斗" else "结束第 ${battle.turningPoint} 转折点"
        val earlyEndLabel = if (isLastTp) "提前结束战斗" else "结束转折点"
        when {
            !turnReady && !otherReady -> bar.addView(kit.primaryButton(endLabel) { endTurningPoint() })
            !turnReady -> bar.addView(kit.actionBar(
                kit.primaryButton("放弃反应") { attempt { BattleFlow.skipReaction(battle) } },
                kit.secondaryButton(earlyEndLabel) { confirmEndTurningPoint() }
            ))
            else -> bar.addView(kit.actionBar(
                kit.secondaryButton("规则：顺序…") { turnOrderSheet() },
                kit.secondaryButton(earlyEndLabel) { confirmEndTurningPoint() }
            ))
        }
    }

    private fun mustActivateNote(): View? {
        val must = battle.progress.mustActivate.takeIf { it.isNotBlank() } ?: return null
        val side = battle.side(must.substringBefore("/"))
        val o = side.operatives.firstOrNull { it.memberId == must.substringAfter("/") } ?: return null
        return kit.tag("规则要求：${side.playerName}下一次激活 ${o.displayName} · 点此取消", ui.orange) {
            attempt { BattleFlow.forceNextActivation(battle, side.id, null, "") }
        }
    }

    /** Rule tools that change who activates next (恶兆、耐心埋伏、阴魂不散的注视…). */
    private fun turnOrderSheet() {
        val turn = battle.side(battle.progress.nextSide)
        val input = sourceInput("规则依据（选填，点上方预设或留空默认“规则效果”）")
        fun reason(): String = input.text.toString().trim().ifBlank { "规则效果" }
        val presets = listOf("耐心埋伏", "恶兆", "收买", "阴魂不散的注视", "诱饵", "传讯员", "规则效果")
        kit.actionSheet("激活顺序 · 规则效果", ui.column().apply {
            addView(ui.text("这些操作都会写入记录，并附上依据。", 12f, ui.muted), ui.wrap(6))
            addView(kit.tagRow(presets.map { p ->
                kit.tag(p, ui.accent) {
                    input.setText(p)
                    input.setSelection(input.text.length)
                }
            }), ui.wrap(6))
            addView(input)
        }, listOf(
            CombatKit.SheetAction("${turn.playerName} 跳过本次激活（回合交给对方）", primary = true) {
                attempt { BattleFlow.skipActivation(battle, reason()) }
            },
            CombatKit.SheetAction("指定某方下一次必须激活的特工…") {
                val r = reason()
                val choices = listOf(battle.sideA, battle.sideB).flatMap { s -> s.operatives.filter { it.ready && !it.incapacitated }.map { s to it } }
                kit.actionSheet("下一次必须激活", null, choices.map { (s, o) ->
                    CombatKit.SheetAction("${s.playerName} · ${o.displayName}") { attempt { BattleFlow.forceNextActivation(battle, s.id, o.memberId, r) } }
                })
            }
        ))
    }

    private fun confirmEndTurningPoint() {
        val isLastTp = battle.turningPoint >= BattleRules.TURNING_POINT_COUNT
        MaterialAlertDialogBuilder(this)
            .setTitle("仍有就绪特工")
            .setMessage(if (isLastTp) "确定提前结束战斗并进入对局总结？之后可以撤回。" else "确定提前结束本转折点？之后可以撤回。")
            .setNegativeButton("取消", null)
            .setPositiveButton("结束") { _, _ -> endTurningPoint() }
            .show()
    }

    private fun endTurningPoint() {
        val last = battle.turningPoint >= BattleRules.TURNING_POINT_COUNT
        if (attempt { BattleRules.advancePhase(battle) }) {
            if (last) noteWithUndo("战斗结束：${BattleRules.resultLabel(battle)}")
            else note("第 ${battle.turningPoint} 转折点结束，等待先手权拼骰")
        }
    }

    private val actions = listOf(
        "转移" to 1, "冲刺" to 1, "冲锋" to 1, "后撤" to 2,
        "拾取标识" to 1, "放置标识" to 1, "任务行动" to 1, "戒备" to 1
    )

    private fun renderActivation(body: LinearLayout, bar: LinearLayout, active: Activation) {
        val side = battle.side(active.side)
        val operative = side.operatives.first { it.memberId == active.member }
        body.addView(ui.column().apply {
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(12))
            background = ui.bg(kit.sideFill(side.id), if (operative.incapacitated) kit.danger else kit.sideColor(side.id))
            addView(ui.row().apply {
                addView(ui.column().apply {
                    addView(ui.text(if (operative.incapacitated) "已残废 · 请结束激活" else if (active.reaction) "反应中" else "激活中",
                        11f, if (operative.incapacitated) kit.danger else ui.muted, true), ui.wrap(1))
                    addView(ui.text(operative.displayName, 18f, if (operative.incapacitated) kit.danger else kit.sideColor(side.id), true), ui.wrap(1))
                    addView(ui.text("${operative.order}命令" + if (operative.incapacitated) " · 已残废" else if (operative.stunned) " · 晕眩" else "",
                        13f, if (operative.incapacitated) kit.danger else ui.ink, true), ui.wrap(0))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.text(if (active.reaction) "1 行动" else "${active.spent}/${active.limit} AP", 24f, ui.ink, true).apply {
                    setOnClickListener { apSheet() }
                    contentDescription = "调整 AP"
                })
            }, ui.wrap(10))
            if (operative.incapacitated) {
                addView(kit.tag("该特工在本次激活中残废，通常应立即结束激活；若有死前行动规则，点击下方行动可按规则例外记录", kit.danger), ui.wrap(10))
            }
            if (active.actions.isNotEmpty()) addView(kit.tagRow(active.actions.map { kit.tag(it, ui.ink) }), ui.wrap(10))
            if (battle.progress.parked.isNotEmpty()) addView(kit.tagRow(listOf(ui.text("联合激活中", 12f, ui.muted, true)) +
                battle.progress.parked.map { p ->
                    val o = side.operatives.first { it.memberId == p.member }
                    kit.tag("切换到 ${o.displayName} · ${p.spent}/${p.limit} AP", kit.sideColor(side.id)) {
                        attempt { BattleFlow.switchJoint(battle, p.member) }
                    }
                }), ui.wrap(10))
            if (active.keepTurn) addView(kit.tag("结束后由 ${side.playerName} 继续激活", ui.orange), ui.wrap(10))
            val ranged = CombatWeapons.of(this@BattleActivity, side, operative).filterNot { it.isMelee }
            val melee = CombatWeapons.of(this@BattleActivity, side, operative).filter { it.isMelee }
            val uniqueActions = AbilityCatalog.forOperative(operative.operativeId).filter { it.apCost != null } +
                equipmentActionsFor(side, operative)
            val buttons = mutableListOf<View>()
            if (ranged.isNotEmpty()) buttons += actionButton("射击", 1, true) { chooseWeapon(side, operative, ranged) }
            if (melee.isNotEmpty()) buttons += actionButton("近战", 1, true) { chooseWeapon(side, operative, melee) }
            actions.forEach { (name, cost) -> buttons += actionButton(name, cost, false) { recordAction(name, cost) } }
            if (uniqueActions.isNotEmpty()) {
                buttons += actionButton("独特行动…", null, false) { uniqueActionSheet(side, operative, uniqueActions) }
            }
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
            kit.secondaryButton("AP · 规则…") { apSheet() }
        ))
    }

    private fun equipmentActionsFor(side: BattleSide, operative: BattleOperativeState): List<AbilityDefinition> {
        val notes = side.equipmentNotes
        if (notes.isBlank()) return emptyList()
        val abilities = AbilityCatalog.forOperative(operative.operativeId)
        if (abilities.any {
                it.name in setOf("愚蠢", "愚笨", "野兽", "猛兽", "无人机", "轻装上阵") ||
                    it.description.contains("不能执行来自装备的行动") ||
                    it.description.contains("无法使用任何不在其数据卡上的武器或执行独特行动")
            }
        ) {
            return emptyList()
        }
        val list = mutableListOf<AbilityDefinition>()
        if (notes.contains("通用手雷") || notes.contains("烟雾手雷") || notes.contains("烟雾弹")) {
            list += AbilityDefinition("eq_smoke_grenade", "烟雾手雷", "通用装备独特行动：把标识放置在距执行特工 6\" 内的可见位置（包括制高点），形成水平 1\"、垂直无限的烟雾区域（烟雾弹和震撼弹共 2 枚/场）。", apCost = 1)
        }
        if (notes.contains("通用手雷") || notes.contains("震荡手雷") || notes.contains("震撼弹")) {
            list += AbilityDefinition("eq_stun_grenade", "震荡手雷", "通用装备独特行动：选择 6\" 内可见敌方特工，为包含该特工在内的 1\" 内所有敌方特工分别掷 D6，3+ 时减少其 1 点 APL，直到其下一次激活结束（烟雾弹和震撼弹共 2 枚/场）。", apCost = 1)
        }
        if (notes.contains("可移动掩体")) {
            list += AbilityDefinition("eq_portable_barricade", "与掩体移动", "通用装备独特行动：掩体内侧接触的特工执行（视为转移行动，最多移动其移动属性 −2\" 的距离，移动结束后将掩体放置于移动后底盘接触处）。", apCost = 1)
        }
        if (notes.contains("弹药箱")) {
            list += AbilityDefinition("eq_ammo_cache", "补给弹药", "通用装备独特行动（0AP）：友方特工控制弹药箱标识且不在敌方控制范围内时执行，直到下一转折点开始使用数据卡远程武器射击可重投 1 个骰子（每转折点限 1 次）。", apCost = 0)
        }
        if (notes.contains("破拆炸药")) {
            list += AbilityDefinition("eq_breaching_charge", "爆破突入", "通用装备独特行动：条件与限制与正常转移行动相同，本次移动中可穿过最多 1\" 厚的地形特征并留下一个宽 1\" 的缺口（每场战斗限 1 次）。", apCost = 1)
        }
        if (side.teamId == "IMP-CI" && notes.contains("灭灵手雷")) {
            list += AbilityDefinition("eq_null_grenade", "灭灵手雷（震荡手雷）", "洁天使隐伏者阵营装备独特行动：执行震荡手雷行动，首名特工震荡测试结果为 3+ 时额外受到等同于结果半数（向上取整）伤害；若拥有灵能者关键词则受到等同于结果数值的伤害（最多 2 次/场）。", apCost = 1)
        }
        if (side.teamId == "KT-PATHFINDERS" && notes.contains("光子手雷")) {
            list += AbilityDefinition("eq_photon_grenade", "光子手雷", "探路者阵营装备独特行动：对 6\" 内可见敌方特工及其 1\" 内其他特工掷 D6，3+ 使其直到下一次激活结束前移动属性减少 2\"（最多 2 次/场）。", apCost = 1)
        }
        if (side.teamId == "CHAOS-PM" && notes.contains("瘟疫铃")) {
            list += AbilityDefinition("eq_plague_bells", "敲响瘟疫铃", "瘟疫战士阵营装备独特行动：距此特工 6\" 内友方瘟疫战士本转折点移动属性 +1\"，敌方特工移动属性 −1\"（每转折点限 1 次）。", apCost = 1)
        }
        if (side.teamId == "KT-VOID_DANCER_TROUPE" && notes.contains("尖啸者面具")) {
            list += AbilityDefinition("eq_shrieker_mask", "震耳呼嚎", "虚空舞者阵营装备独特行动：选择 6\" 内可见敌方特工，其直到下一次激活结束 APL −1 且不能执行反应（每转折点限 1 次）。", apCost = 1)
        }
        if (side.teamId == "KT-MURDERWING" && notes.contains("刃鳍")) {
            list += AbilityDefinition("eq_mw_blade_fins", "凌空斩击", "杀戮之翼阵营装备独特行动：推进行动（在后撤或转移期间执行），对推进区域内一名敌方特工造成 2D3+1 点伤害。", apCost = 1)
        }
        if (side.teamId == "KT-MURDERWING" && notes.contains("扩声器")) {
            list += AbilityDefinition("eq_mw_vox_caster", "震耳呼嚎（扩声器）", "杀戮之翼阵营装备独特行动：2\" 内每名敌方特工进行眩晕测试（每转折点限 1 次，拥有隐匿命令时无法执行）。", apCost = 1)
        }
        if (side.teamId == "KT-SANCTIFIERS" && notes.contains("圣珠") &&
            operative.operativeId !in setOf("sanctifiers_02", "sanctifiers_04", "sanctifiers_07")
        ) {
            list += AbilityDefinition(
                "eq_sanctifiers_rosary",
                "圣珠",
                "净化者阵营装备独特行动（每个转折点限一次）：选择位于这名特工 6\" 内且可见的一名敌方特工。那名特工和位于其 1\" 内的每一名敌方特工进行浸湿测试（掷 D6：3+ 获得浸湿指示物；己方净化者使用拥有“烈焰”武器规则的武器射击浸湿目标时获得“追踪”武器规则，射击后移除浸湿指示物；不能在敌方特工控制范围内执行）。",
                apCost = 1
            )
        }
        return list
    }

    private fun uniqueActionSheet(side: BattleSide, operative: BattleOperativeState, uniqueActions: List<AbilityDefinition>) {
        ui.dialog("${operative.displayName} · 独特行动", expanded = true) { box, sheet ->
            uniqueActions.forEach { ua ->
                val cost = ua.apCost ?: 1
                box.addView(ui.column().apply {
                    setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
                    background = ui.bg(0xff1a1a1a.toInt(), 0xff343432.toInt())
                    addView(ui.row().apply {
                        addView(ui.text("${ua.name} · ${cost}AP", 15f, ui.accent, true), LinearLayout.LayoutParams(0, -2, 1f))
                        addView(ui.button("执行", true) {
                            if (ua.name.startsWith("时间分裂") && battle.turningPoint <= 1) {
                                note("时间分裂不能在第 1 转折点使用")
                                return@button
                            }
                            sheet.dismiss()
                            executeUniqueAction(side, operative, ua, cost)
                        }, LinearLayout.LayoutParams(ui.dp(76), ui.dp(40)))
                    }, ui.wrap(4))
                    addView(ui.text(ua.description, 12f, 0xffc9c7c2.toInt()), ui.wrap(0))
                }, ui.wrap(8))
            }
        }
    }

    private fun executeUniqueAction(side: BattleSide, operative: BattleOperativeState, ua: AbilityDefinition, cost: Int) {
        if (ua.id == "eq_mw_vox_caster" && operative.order == BattleOperativeState.ORDER_CONCEAL) {
            note("震耳呼嚎（扩声器）：拥有隐匿命令时无法执行该行动")
            return
        }
        when {
            ua.id == "eq_sanctifiers_rosary" -> {
                if ("${side.id}/交战:圣珠" in battle.progress.usedPlans) {
                    note("圣珠：每个转折点限一次，本转折点已使用")
                    return
                }
                val opponent = battle.opponent(side.id)
                val enemies = opponent.operatives.filter { !it.incapacitated && !it.offField }
                if (enemies.isEmpty()) { recordAction(ua.name, cost); return }
                kit.actionSheet("圣珠 · 选择 6\" 内可见敌方目标（D6 3+ 浸湿）", null, enemies.map { target ->
                    CombatKit.SheetAction(target.displayName, primary = true) {
                        val res = runCatching { BattleFlow.action(battle, ua.name, cost, "") }.getOrNull()
                        if (res == null) { recordAction(ua.name, cost); return@SheetAction }
                        val roll = (1..6).random()
                        val soaked = roll >= 3
                        val updatedOpp = res.side(opponent.id).let { oppSide ->
                            if (!soaked) oppSide else oppSide.copy(operatives = oppSide.operatives.map { o ->
                                if (o.memberId != target.memberId || o.tokens.any { it.name == "浸湿" }) o
                                else o.copy(tokens = o.tokens + OperativeToken(name = "浸湿", duration = TokenDuration.UNTIL_REMOVED, source = "圣珠"))
                            })
                        }
                        val withPlan = BattleFlow.side(res, updatedOpp).let { b ->
                            b.copy(progress = b.progress.copy(usedPlans = b.progress.usedPlans + "${side.id}/交战:圣珠"))
                        }
                        val msg = if (soaked) {
                            "${operative.displayName}对${target.displayName}投掷圣珠：D6 掷出 $roll（≥3 成功），${target.displayName}获得浸湿指示物"
                        } else {
                            "${operative.displayName}对${target.displayName}投掷圣珠：D6 掷出 $roll（<3 未浸湿）"
                        }
                        commit(BattleFlow.event(withPlan, msg))
                    }
                })
            }
            ua.name.startsWith("标记光聚焦") -> {
                val opponent = battle.opponent(side.id)
                val enemies = opponent.operatives.filter { !it.incapacitated && !it.offField }
                if (enemies.isEmpty()) { recordAction(ua.name, cost); return }
                kit.actionSheet("标记光聚焦 · 选择可见敌方目标（本转折点被射击命中+1）", null, enemies.map { target ->
                    CombatKit.SheetAction(target.displayName, primary = true) {
                        val res = runCatching { BattleFlow.action(battle, ua.name, cost, "") }.getOrNull()
                        if (res == null) { recordAction(ua.name, cost); return@SheetAction }
                        val updatedOpp = res.side(opponent.id).let { oppSide ->
                            oppSide.copy(operatives = oppSide.operatives.map { o ->
                                if (o.memberId != target.memberId) o else {
                                    o.copy(tokens = o.tokens.filterNot { it.name == "标记光聚焦" } +
                                        OperativeToken(name = "标记光聚焦", duration = TokenDuration.TURNING_POINT_END, source = "${operative.displayName}（标记光聚焦）"))
                                }
                            })
                        }
                        commit(BattleFlow.event(BattleFlow.side(res, updatedOpp), "${operative.displayName}对${target.displayName}执行标记光聚焦（本转折点被射击命中提升 1 点）"))
                    }
                })
            }
            ua.name.startsWith("标记光") -> {
                val opponent = battle.opponent(side.id)
                val enemies = opponent.operatives.filter { !it.incapacitated && !it.offField }
                if (enemies.isEmpty()) { recordAction(ua.name, cost); return }
                val isHighIntensityDrone = AbilityCatalog.forOperative(operative.operativeId).any { it.name == "高强度标记光" }
                val step = if (isHighIntensityDrone) 2 else 1
                kit.actionSheet("标记光 · 选择可见敌方目标（最多 4 枚，配合目标分析镜可达第 5 档）", null, enemies.map { target ->
                    val curVal = target.tokens.filter { it.name.startsWith("标记光") }.sumOf { it.value ?: 1 }.coerceAtMost(4)
                    val nextVal = (curVal + step).coerceAtMost(4)
                    CombatKit.SheetAction("${target.displayName}（当前标记光：$curVal → $nextVal）", primary = true) {
                        val res = runCatching { BattleFlow.action(battle, ua.name, cost, "") }
                        val afterAction = res.getOrNull()
                        if (afterAction == null) {
                            recordAction(ua.name, cost)
                            return@SheetAction
                        }
                        val updatedOpp = afterAction.side(opponent.id).let { oppSide ->
                            oppSide.copy(operatives = oppSide.operatives.map { o ->
                                if (o.memberId != target.memberId) o else {
                                    o.copy(tokens = o.tokens.filterNot { it.name.startsWith("标记光") } +
                                        OperativeToken(name = "标记光", value = nextVal, duration = TokenDuration.UNTIL_REMOVED, source = operative.displayName))
                                }
                            })
                        }
                        commit(BattleFlow.event(BattleFlow.side(afterAction, updatedOpp), "${operative.displayName}对${target.displayName}放置标记光（累计 $nextVal）"))
                    }
                })
            }
            ua.name.startsWith("钻地") -> {
                val res = runCatching { BattleFlow.action(battle, ua.name, cost, "") }.getOrNull()
                if (res == null) { recordAction(ua.name, cost); return }
                val curSide = res.side(side.id)
                val curOp = curSide.operatives.first { it.memberId == operative.memberId }
                val willGoUnderground = !curOp.offField
                val nextSide = curSide.copy(operatives = curSide.operatives.map { o ->
                    if (o.memberId != operative.memberId) o else {
                        if (willGoUnderground) {
                            o.copy(tokens = o.tokens + OperativeToken(name = OperativeToken.OFF_FIELD, duration = TokenDuration.UNTIL_REMOVED, source = "钻地"))
                        } else {
                            o.copy(tokens = o.tokens.filterNot { it.name == OperativeToken.OFF_FIELD || it.name == "钻出（移动−2″）" } +
                                OperativeToken(name = "钻出（移动−2″）", duration = TokenDuration.THIS_ACTIVATION, source = "钻地"))
                        }
                    }
                })
                commit(BattleFlow.event(BattleFlow.side(res, nextSide), "${operative.displayName}：${if (willGoUnderground) "钻入地下（离场）" else "钻出地面（返场，直到那次激活或反应结束前移动属性减少 2\"）"}"))
            }
            ua.name.startsWith("暴怒再生") -> {
                val targets = side.operatives.filter { !it.incapacitated && !it.frenzied && !it.offField && it.memberId != operative.memberId }
                if (targets.isEmpty()) { recordAction(ua.name, cost); return }
                kit.actionSheet("暴怒再生 · 选择 6\" 内可见的其他非狂暴友方特工", null, targets.flatMap { target ->
                    listOf(
                        CombatKit.SheetAction("${target.displayName} · 2D3 治疗（常规）", primary = true) {
                            val res = runCatching { BattleFlow.action(battle, ua.name, cost, "") }.getOrNull()
                            if (res == null) { recordAction(ua.name, cost); return@SheetAction }
                            val d3a = (1..3).random()
                            val d3b = (1..3).random()
                            val heal = d3a + d3b
                            val curSide = res.side(side.id)
                            val curOp = curSide.operatives.first { it.memberId == target.memberId }
                            val newWounds = (curOp.currentWounds + heal).coerceAtMost(curOp.maxWounds)
                            val nextSide = curSide.copy(operatives = curSide.operatives.map { o ->
                                if (o.memberId != target.memberId) o else BattleRules.setWounds(o, newWounds)
                            })
                            commit(BattleFlow.event(BattleFlow.side(res, nextSide), "${operative.displayName}对${target.displayName}执行暴怒再生：2D3 掷出 $d3a+$d3b=$heal，耐伤 ${curOp.currentWounds} → $newWounds"))
                        },
                        CombatKit.SheetAction("${target.displayName} · 固定回复 6 点（本场战斗中曾通过近战或反击使敌方特工残废）") {
                            val res = runCatching { BattleFlow.action(battle, ua.name, cost, "") }.getOrNull()
                            if (res == null) { recordAction(ua.name, cost); return@SheetAction }
                            val heal = 6
                            val curSide = res.side(side.id)
                            val curOp = curSide.operatives.first { it.memberId == target.memberId }
                            val newWounds = (curOp.currentWounds + heal).coerceAtMost(curOp.maxWounds)
                            val nextSide = curSide.copy(operatives = curSide.operatives.map { o ->
                                if (o.memberId != target.memberId) o else BattleRules.setWounds(o, newWounds)
                            })
                            commit(BattleFlow.event(BattleFlow.side(res, nextSide), "${operative.displayName}对${target.displayName}执行暴怒再生（曾在近战/反击中使敌方残废）：固定回复 6 点，耐伤 ${curOp.currentWounds} → $newWounds"))
                        }
                    )
                })
            }
            ua.name.startsWith("植入命令") -> {
                val friends = side.operatives.filter { !it.incapacitated && !it.offField && it.memberId != operative.memberId }
                if (friends.isEmpty()) { recordAction(ua.name, cost); return }
                kit.actionSheet("植入命令 · 选择友方教团序列特工立即执行 1AP 免费行动", null, friends.map { target ->
                    CombatKit.SheetAction(target.displayName, primary = true) {
                        val res = runCatching { BattleFlow.action(battle, ua.name, cost, "") }.getOrNull()
                        if (res == null) { recordAction(ua.name, cost); return@SheetAction }
                        commit(BattleFlow.event(res, "${operative.displayName}对${target.displayName}执行「${ua.name}」：${target.displayName}立即执行 1 项免费 1AP 行动"))
                    }
                })
            }
            ua.name.startsWith("重生 1/2AP") -> {
                val fallen = side.operatives.filter { it.incapacitated && it.memberId != operative.memberId }
                if (fallen.isEmpty()) { recordAction(ua.name, cost); return }
                kit.actionSheet("重生 1/2AP · 选择待重生的友方特工", null, fallen.flatMap { target ->
                    listOf(
                        CombatKit.SheetAction("${target.displayName} · 1AP（掷 D6，3+ 成功重生并剩余 1 点耐伤）", primary = true) {
                            val res = runCatching { BattleFlow.action(battle, "重生（1AP）", 1, "") }.getOrNull()
                            if (res == null) { recordAction("重生（1AP）", 1); return@SheetAction }
                            val roll = (1..6).random()
                            if (roll >= 3) {
                                val heal = 1
                                val curSide = res.side(side.id)
                                val ready = !expendedThisTurningPoint(side.id, target)
                                val nextSide = curSide.copy(operatives = curSide.operatives.map { o ->
                                    if (o.memberId != target.memberId) o else o.copy(incapacitated = false, currentWounds = heal, ready = ready)
                                })
                                commit(BattleRules.recalculateKills(BattleFlow.event(BattleFlow.side(res, nextSide), "${operative.displayName}对${target.displayName}执行重生（1AP）：掷出 $roll（≥3 成功），以 1 点耐伤${if (ready) "就绪" else "待机"}重生")))
                            } else {
                                commit(BattleFlow.event(res, "${operative.displayName}对${target.displayName}执行重生（1AP）：掷出 $roll（<3 未能重生）"))
                            }
                        },
                        CombatKit.SheetAction("${target.displayName} · 2AP（自动成功重生并剩余 1 点耐伤）") {
                            val res = runCatching { BattleFlow.action(battle, "重生（2AP）", 2, "") }.getOrNull()
                            if (res == null) { recordAction("重生（2AP）", 2); return@SheetAction }
                            val heal = 1
                            val curSide = res.side(side.id)
                            val ready = !expendedThisTurningPoint(side.id, target)
                            val nextSide = curSide.copy(operatives = curSide.operatives.map { o ->
                                if (o.memberId != target.memberId) o else o.copy(incapacitated = false, currentWounds = heal, ready = ready)
                            })
                            commit(BattleRules.recalculateKills(BattleFlow.event(BattleFlow.side(res, nextSide), "${operative.displayName}对${target.displayName}执行重生（2AP）：自动成功，以 1 点耐伤${if (ready) "就绪" else "待机"}重生")))
                        }
                    )
                })
            }
            ua.name.startsWith("点燃怒火") || ua.name.startsWith("搞定它") ||
                ua.name.startsWith("发送信号") || ua.name.startsWith("传讯") || ua.name.startsWith("空中视野") -> {
                val friends = side.operatives.filter { !it.incapacitated && !it.offField && it.memberId != operative.memberId }
                if (friends.isEmpty()) { recordAction(ua.name, cost); return }
                kit.actionSheet("${ua.name} · 选择其他友方目标（获得 +1 APL）", null, friends.map { target ->
                    CombatKit.SheetAction(target.displayName, primary = true) {
                        val res = runCatching { BattleFlow.action(battle, ua.name, cost, "") }.getOrNull()
                        if (res == null) { recordAction(ua.name, cost); return@SheetAction }
                        val curSide = res.side(side.id)
                        val nextSide = curSide.copy(operatives = curSide.operatives.map { o ->
                            if (o.memberId != target.memberId) o else {
                                o.copy(tokens = o.tokens + OperativeToken(name = "APL", value = 1, duration = TokenDuration.NEXT_ACTIVATION_END, source = ua.name))
                            }
                        })
                        var updated = BattleFlow.side(res, nextSide)
                        if (updated.progress.active?.side == side.id && updated.progress.active?.member == target.memberId) {
                            updated = BattleFlow.adjustActivation(updated, 1, 0, ua.name)
                        } else if (updated.progress.parked.any { it.side == side.id && it.member == target.memberId }) {
                            updated = updated.copy(progress = updated.progress.copy(
                                parked = updated.progress.parked.map { p ->
                                    if (p.side == side.id && p.member == target.memberId) p.copy(limit = (p.limit + 1).coerceIn(0, 4)) else p
                                }
                            ))
                        }
                        commit(BattleFlow.event(updated, "${operative.displayName}对${target.displayName}执行「${ua.name}」：${target.displayName} APL +1"))
                    }
                })
            }
            ua.id == "eq_stun_grenade" || ua.name == "震荡手雷" -> {
                val opponent = battle.opponent(side.id)
                val enemies = opponent.operatives.filter { !it.incapacitated && !it.offField }
                if (enemies.isEmpty()) { recordAction(ua.name, cost); return }
                kit.actionSheet("震荡手雷 · 选择 6\" 内可见敌方目标（掷 D6，3+ 使其 −1 APL）", null, enemies.map { target ->
                    CombatKit.SheetAction(target.displayName, primary = true) {
                        val res = runCatching { BattleFlow.action(battle, ua.name, cost, "") }.getOrNull()
                        if (res == null) { recordAction(ua.name, cost); return@SheetAction }
                        val roll = (1..6).random()
                        if (roll >= 3) {
                            val oppSide = res.side(opponent.id)
                            val nextOpp = oppSide.copy(operatives = oppSide.operatives.map { o ->
                                if (o.memberId != target.memberId) o else {
                                    o.copy(tokens = o.tokens + OperativeToken(name = "APL", value = -1, duration = TokenDuration.NEXT_ACTIVATION_END, source = "震荡手雷"))
                                }
                            })
                            commit(BattleFlow.event(BattleFlow.side(res, nextOpp), "${operative.displayName}对${target.displayName}投掷震荡手雷：D6 掷出 $roll（≥3 成功），${target.displayName} APL −1"))
                        } else {
                            commit(BattleFlow.event(res, "${operative.displayName}对${target.displayName}投掷震荡手雷：D6 掷出 $roll（<3 未生效）"))
                        }
                    }
                })
            }
            ua.name.startsWith("系统干扰") && side.teamId == "KT-XV26_STEALTH_BATTLESUITS" -> {
                val effCost = if (operative.order == BattleOperativeState.ORDER_CONCEAL) cost + 1 else cost
                val opponent = battle.opponent(side.id)
                val enemies = opponent.operatives.filter { !it.incapacitated && !it.offField }
                if (enemies.isEmpty()) { recordAction(ua.name, effCost); return }
                kit.actionSheet("${ua.name} · 选择敌方目标（获得 −1 APL）", null, enemies.map { target ->
                    CombatKit.SheetAction(target.displayName, primary = true) {
                        val res = runCatching { BattleFlow.action(battle, ua.name, effCost, "") }.getOrNull()
                        if (res == null) { recordAction(ua.name, effCost); return@SheetAction }
                        val oppSide = res.side(opponent.id)
                        val nextOpp = oppSide.copy(operatives = oppSide.operatives.map { o ->
                            if (o.memberId != target.memberId) o else {
                                o.copy(tokens = o.tokens + OperativeToken(name = "APL", value = -1, duration = TokenDuration.NEXT_ACTIVATION_END, source = ua.name))
                            }
                        })
                        commit(BattleFlow.event(BattleFlow.side(res, nextOpp), "${operative.displayName}对${target.displayName}执行「${ua.name}」：${target.displayName} APL −1"))
                    }
                })
            }
            else -> recordAction(ua.name, cost)
        }
    }

    /**
     * 重生 1/2AP: an operative that already activated this turning point before it was
     * incapacitated comes back expended (incapacitation clears [BattleOperativeState.ready],
     * so the activation is read from this turning point's log).
     */
    private fun expendedThisTurningPoint(sideId: String, target: BattleOperativeState): Boolean {
        val prefix = "${battle.side(sideId).playerName}：${target.displayName}"
        return battle.log.any {
            !it.undone && it.turningPoint == battle.turningPoint && it.message.startsWith(prefix) &&
                (it.message.startsWith("${prefix}激活 · ") || it.message.startsWith("${prefix}额外激活") || it.message.startsWith("${prefix}联合激活"))
        }
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
                next = BattleFlow.adjustActivation(next, active.spent + cost - active.limit, 0, "$name；依据：$reason")
            }
            attempt { BattleFlow.action(next, name, cost, reason) }
        }
    }

    /** The rule the player names here is written into the log entry of the action itself. */
    private fun exceptionSheet(message: String, onConfirm: (String) -> Unit) {
        ui.dialog("核心规则不允许") { box, sheet ->
            val input = sourceInput("必填：允许这样做的规则、计谋或能力名")
            box.addView(ui.text(message, 14f, ui.orange, true), ui.wrap(8))
            box.addView(ui.text("点选下方常见规则依据，或手动填写：", 12f, ui.muted), ui.wrap(6))
            val activeSideId = battle.progress.active?.side ?: battle.progress.nextSide
            val teamId = battle.side(activeSideId).teamId
            val teamPresets = when (teamId) {
                "CHAOS-PM", "IMP-AOD", "KT-NEMESIS_CLAW", "KT-LEGIONARIES", "CHAOS-WC", "KT-ANGELS_OF_DEATH", "KT-MURDERWING" -> listOf("阿斯塔特")
                "KT-DEATHWATCH" -> listOf("阿斯塔特老兵", "阿斯塔特")
                "KT-PHOBOS_STRIKE_TEAM" -> listOf("猎手阿斯塔特")
                "KT-SCOUT_SQUAD" -> listOf("前线侦察", "阿斯塔特")
                "TYR-RAV" -> listOf("掠食本能", "活体闪电")
                "KT-PATHFINDERS" -> listOf("全息移动", "标记光5+")
                "KT-KASRKIN" -> listOf("迅速开火")
                "KT-KOMMANDOS" -> listOf("割喉者", "搞定它！", "WAAAGH!")
                "KT-HIEROTEK_CIRCLE" -> listOf("无情进军", "星际科技大师")
                "CHAOS-FELL" -> listOf("狂暴", "冷酷无情")
                else -> emptyList()
            }
            val presets = (teamPresets + listOf("阿斯塔特", "笨重", "计谋允许", "特工能力允许", "死前行动")).distinct()
            box.addView(kit.tagRow(presets.map { p ->
                kit.tag(p, ui.accent) {
                    input.setText(p)
                    input.setSelection(input.text.length)
                }
            }), ui.wrap(8))
            box.addView(input, ui.wrap(10))
            val confirm = ui.button("按规则例外记录", true) {
                val reason = input.text.toString().trim()
                if (reason.isNotBlank()) {
                    sheet.dismiss()
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onConfirm(reason) }
                }
            }.apply { isEnabled = false; alpha = .35f }
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    val ok = !s.isNullOrBlank()
                    confirm.isEnabled = ok; confirm.alpha = if (ok) 1f else .35f
                }
            })
            box.addView(confirm, ui.wrap(2))
        }
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
        val side = battle.side(active.side)
        kit.actionSheet("AP ${active.spent}/${active.limit} · 规则效果", input, listOf(
            CombatKit.SheetAction("APL +1") { adjust(1, 0) },
            CombatKit.SheetAction("APL −1") { adjust(-1, 0) },
            CombatKit.SheetAction("返还 1AP（免费行动）") { adjust(0, -1) },
            CombatKit.SheetAction("额外消耗 1AP") { adjust(0, 1) },
            CombatKit.SheetAction(if (active.keepTurn) "已设：结束后由同一玩家继续激活" else "结束后由同一玩家继续激活（连续激活）", !active.keepTurn) {
                attempt { BattleFlow.keepTurn(battle, input.text.toString()) }
            },
            CombatKit.SheetAction("联合激活另一名己方特工…") {
                val reason = input.text.toString().trim()
                val choices = side.operatives.filter { it.ready && !it.incapacitated && it.memberId != active.member && battle.progress.parked.none { p -> p.member == it.memberId } }
                if (choices.isEmpty()) { note("没有可联合激活的就绪特工"); return@SheetAction }
                kit.actionSheet("联合激活（两名特工同时处于激活中，可来回切换）", null, choices.flatMap { o ->
                    val apl = BattleFlow.effectiveApl(withApl(side, o))
                    listOf(
                        CombatKit.SheetAction("${o.displayName} · 交战") { attempt { BattleFlow.jointActivate(battle, o.memberId, apl, true, reason) } },
                        CombatKit.SheetAction("${o.displayName} · 隐匿") { attempt { BattleFlow.jointActivate(battle, o.memberId, apl, false, reason) } }
                    )
                })
            }
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
            CombatKit.SheetAction("${t.displayName} · ${t.order} · ${statLine(opponent, t)}") {
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
        if (!weapon.isMelee) {
            val heavyKeyword = weapon.keywords.firstOrNull { it.replace(" ", "").startsWith("重型") }
            if (heavyKeyword != null) {
                val norm = heavyKeyword.replace(" ", "")
                val forbiddenMoves = when {
                    norm.contains("仅限冲刺") -> active.actions.filter { it in setOf("转移", "冲锋", "后撤") }
                    norm.contains("仅限转移") -> active.actions.filter { it in setOf("冲刺", "冲锋", "后撤") }
                    else -> active.actions.filter { it in setOf("转移", "冲刺", "冲锋", "后撤") }
                }
                if (forbiddenMoves.isNotEmpty()) {
                    exceptionSheet("这件武器带有「$heavyKeyword」，本激活已执行过「${forbiddenMoves.joinToString("、")}」，核心规则下不能使用该武器射击") { launch(it) }
                    return
                }
            }
            val concealPositionKeyword = weapon.keywords.any { it.contains("隐匿位置") }
            if (concealPositionKeyword) {
                if (operative.tokens.any { it.name == "隐匿位置已射击" }) {
                    exceptionSheet("这件武器带有「隐匿位置」规则（每场战斗只能使用一次），该特工本场战斗已使用过隐匿位置武器") { launch(it) }
                    return
                }
                if (active.actions.any { it in setOf("转移", "冲刺", "冲锋", "后撤") }) {
                    exceptionSheet("这件武器带有「隐匿位置」规则，特工在移动过的同一激活中不能使用该武器射击") { launch(it) }
                    return
                }
            }
        }
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
        if (retaliation.isEmpty()) { launch(null); return }
        if (retaliation.size == 1) { launch(retaliation.single()); return }
        kit.actionSheet("防御方 ${target.displayName} 选择反击用的近战武器", ui.text("由被攻击方玩家选择；进入近战后在准备步骤仍可更换。", 12f, ui.muted),
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
        val inPlay = side.inFieldCount
        val offFieldCount = side.operatives.count { !it.incapacitated && it.offField }
        val statusSuffix = if (offFieldCount > 0) "（离场 $offFieldCount）" else ""
        addView(ui.row().apply {
            addView(ui.text("${RosterCatalog.team(side.teamId).name} · 在场 $inPlay/${side.operatives.size}$statusSuffix", 14f, ui.ink, true),
                LinearLayout.LayoutParams(0, -2, 1f))
            if (!battle.completed) addView(kit.tag("＋ 特工", ui.muted) { addOperativeSheet(side.id) },
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) })
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
        if (t != null) addView(ui.memberPortrait(avatar, t.cardRes, 44, "${operative.displayName} · 棋子照片", fallbackAsset = t.primaryCardAsset))
        addView(ui.column().apply {
            addView(ui.text(operative.displayName, 15f, if (operative.ready && !operative.incapacitated) ui.ink else ui.muted, true).apply { maxLines = 1 }, ui.wrap(2))
            val info = buildList {
                add(statLine(side, operative).substringBeforeLast(" · 耐伤"))
                if (operative.incapacitated) add("残废") else if (!operative.ready) add("待机")
            }.joinToString(" · ")
            addView(ui.text(info, 12f, if (operative.incapacitated) kit.danger else 0xffc9c7c2.toInt(), true), ui.wrap(2))
            val tags = buildList {
                if (operative.wounded && !operative.incapacitated) add(kit.tag("受创 · 移动−2″ 命中+1", ui.orange) { CoreRules.show(ui, "wounds") })
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
        val side = battle.side(sideId)
        val operative = side.operatives.firstOrNull { it.memberId == memberId } ?: return
        val name = operative.displayName
        snack("$name 已残废", NOTICE_MS).setAction("选项") {
            val actions = mutableListOf<CombatKit.SheetAction>()
            if (side.teamId == "CHAOS-FELL" && !operative.frenzied) {
                actions += CombatKit.SheetAction("⚡ 触发【狂暴】（保留在场并获得狂暴标记）", primary = true) {
                    val updated = BattleRules.syncKills(updateOperative(sideId, memberId) { o ->
                        o.copy(
                            currentWounds = 1,
                            incapacitated = false,
                            order = BattleOperativeState.ORDER_ENGAGE,
                            tokens = o.tokens.filterNot { it.name == "狂暴" } +
                                OperativeToken(name = "狂暴", duration = TokenDuration.UNTIL_REMOVED, source = "狂暴")
                        )
                    })
                    save(updated, "$name：触发【狂暴】，保留在场并获得狂暴标记")
                }
            }
            actions += CombatKit.SheetAction("撤回") { undo() }
            actions += CombatKit.SheetAction("被规则阻止 · 保留 1 耐伤（医疗兵!/死里逃生等）") {
                val updated = BattleRules.recalculateKills(updateOperative(sideId, memberId) { it.copy(currentWounds = 1, incapacitated = false) })
                save(updated, "$name：被规则阻止残废，保留 1 耐伤")
            }
            actions += CombatKit.SheetAction("不计入击杀") {
                val updated = BattleRules.recalculateKills(updateOperative(sideId, memberId) { it.copy(excludedFromKill = true) })
                save(updated, "$name：不计入击杀")
            }
            kit.actionSheet("$name 已残废", null, actions)
        }.show()
    }

    private fun operativeSheet(sideId: String, memberId: String) {
        val side = battle.side(sideId)
        val operative = side.operatives.first { it.memberId == memberId }
        val apl = BattleFlow.effectiveApl(withApl(side, operative))
        val actions = mutableListOf<CombatKit.SheetAction>()
        val firefight = !battle.completed && battle.phase == BattlePhase.FIREFIGHT && battle.progress.active == null
        val canReactWindow = firefight && battle.progress.nextSide == side.id && !operative.incapacitated &&
            !operative.ready && !BattleFlow.ready(battle, side.id) && BattleFlow.ready(battle, BattleFlow.other(side.id)) &&
            BattleFlow.key(side.id, operative.memberId) !in battle.progress.reacted
        if (firefight && canActivateNow(side, operative)) {
            if (operative.ready) {
                actions += CombatKit.SheetAction("激活 · 交战命令", primary = true) { activate(sideId, memberId, apl, engage = true) }
                actions += CombatKit.SheetAction("激活 · 隐匿命令", primary = true) { activate(sideId, memberId, apl, engage = false) }
            } else {
                actions += CombatKit.SheetAction("反应", primary = true) { activate(sideId, memberId, apl, engage = true) }
            }
        } else if (canReactWindow && operative.order == BattleOperativeState.ORDER_CONCEAL) {
            actions += CombatKit.SheetAction("反应（隐匿命令 · 规则允许）…", primary = true) {
                exceptionSheet("核心规则下只有交战命令的待机特工能执行反应。若有阵营规则（如阿斯塔特）允许隐匿命令反应，请填写规则依据：") { reason ->
                    if (attempt { BattleFlow.activate(battle, sideId, memberId, apl, engage = false, reason = reason) }) {
                        selectedSide = sideId
                        render()
                    }
                }
            }
        }
        if (firefight && !operative.incapacitated) {
            actions += CombatKit.SheetAction("额外激活（规则干预）…") { forcedActivation(sideId, memberId, apl) }
        }
        if (!battle.completed) {
            actions += CombatKit.SheetAction("伤害 / 治疗…") { woundSheet(sideId, memberId) }
            actions += CombatKit.SheetAction("状态标记…") { tokenSheet(sideId, memberId) }
            actions += CombatKit.SheetAction("规则：武器 / 数据卡 / 控制…") { operativeRuleSheet(sideId, memberId) }
            val other = OrderTokens.oppositeOrder(operative.order)
            actions += CombatKit.SheetAction("命令改为$other") {
                save(updateOperative(sideId, memberId) { it.copy(order = other) }, "${operative.displayName}：命令改为$other")
            }
            actions += CombatKit.SheetAction(if (operative.ready) "改为待机" else "改为就绪") {
                save(updateOperative(sideId, memberId) { it.copy(ready = !it.ready) },
                    "${operative.displayName}：改为${if (operative.ready) "待机" else "就绪"}")
            }
            actions += CombatKit.SheetAction(if (operative.incapacitated) "恢复在场（医疗兵!/重生规程等）" else "标记残废") {
                val wasIncap = operative.incapacitated
                val updated = BattleRules.recalculateKills(updateOperative(sideId, memberId) {
                    if (it.incapacitated) it.copy(incapacitated = false, currentWounds = it.currentWounds.coerceAtLeast(1))
                    else it.copy(incapacitated = true, currentWounds = 0, ready = false)
                })
                save(updated, "${operative.displayName}：${if (wasIncap) "恢复在场" else "标记残废"}")
            }
            if (operative.incapacitated && side.teamId == "CHAOS-FELL" && !operative.frenzied) {
                actions += CombatKit.SheetAction("⚡ 触发【狂暴】（恢复在场并获得狂暴标记）") {
                    val updated = BattleRules.recalculateKills(updateOperative(sideId, memberId) { o ->
                        o.copy(
                            incapacitated = false,
                            currentWounds = 1,
                            order = BattleOperativeState.ORDER_ENGAGE,
                            tokens = o.tokens.filterNot { it.name == "狂暴" } +
                                OperativeToken(name = "狂暴", duration = TokenDuration.UNTIL_REMOVED, source = "狂暴")
                        )
                    })
                    save(updated, "${operative.displayName}：触发【狂暴】，恢复在场并获得狂暴标记")
                }
            }
            actions += CombatKit.SheetAction(if (operative.excludedFromKill) "计入击杀" else "不计入击杀") {
                val updated = BattleRules.recalculateKills(updateOperative(sideId, memberId) { it.copy(excludedFromKill = !it.excludedFromKill) })
                save(updated, "${operative.displayName}：${if (operative.excludedFromKill) "计入击杀" else "不计入击杀"}")
            }
        }
        actions += CombatKit.SheetAction("人物卡") { BattleReference(ui).operativeCard(side.teamId, operative) }
        actions += CombatKit.SheetAction("成员规则 · 能力与关键字") { BattleReference(ui).operative(side.teamId, operative) }
        val header = ui.column().apply {
            addView(ui.text("${statLine(side, operative)} · ${operative.order} · ${OrderTokens.faceLabel(operative.ready)}",
                13f, ui.muted, true), ui.wrap(6))
            CombatWeapons.of(this@BattleActivity, side, operative).forEach { w ->
                addView(ui.text(w.name, 13f, ui.ink, true), ui.wrap(1))
                addView(WeaponRulesUi.profile(ui, w.name, w.profile, w.keywords, 13f, 0xffc9c7c2.toInt(), bold = true, teamId = side.teamId), ui.wrap(6))
            }
        }
        kit.actionSheet(operative.displayName, header, actions)
    }

    private fun activate(sideId: String, memberId: String, apl: Int, engage: Boolean) {
        if (attempt { BattleFlow.activate(battle, sideId, memberId, apl, engage) }) {
            selectedSide = sideId
            render()
            scroll?.post { scroll?.smoothScrollTo(0, 0) }
        }
    }

    private fun forcedActivation(sideId: String, memberId: String, apl: Int) {
        val input = sourceInput("依据（选填）：规则、计谋或能力名")
        val presets = listOf("阴魂不散的注视", "机仆网络", "传讯员", "守望", "规则干预")
        val header = ui.column().apply {
            addView(kit.tagRow(presets.map { p ->
                kit.tag(p, ui.accent) {
                    input.setText(p)
                    input.setSelection(input.text.length)
                }
            }), ui.wrap(6))
            addView(input)
        }
        fun doForced(engage: Boolean) {
            if (attempt { BattleFlow.activate(battle, sideId, memberId, apl, engage, force = true, reason = input.text.toString()) }) {
                selectedSide = sideId
                render()
                scroll?.post { scroll?.smoothScrollTo(0, 0) }
            }
        }
        kit.actionSheet("额外激活", header, listOf(
            CombatKit.SheetAction("交战命令", primary = true) { doForced(true) },
            CombatKit.SheetAction("隐匿命令") { doForced(false) }
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
        val name = ui.input("", "标记名称，例如：毒素、标记光、圣勉")
        val value = ui.input("", "数值（选填）", numeric = true)
        val header = ui.column().apply {
            if (operative.tokens.isNotEmpty()) {
                addView(ui.text("点按移除", 12f, ui.muted, true), ui.wrap(4))
                addView(kit.tagRow(operative.tokens.map { token ->
                    kit.tag("${token.label} · ${token.duration.label} ✕", kit.sideColor(sideId)) {
                        save(BattleRules.syncKills(updateOperative(sideId, memberId) { o -> o.copy(tokens = o.tokens.filterNot { it.id == token.id }) }),
                            "${operative.displayName}：移除${token.label}")
                    }
                }), ui.wrap(10))
            }
            val presets = listOf(
                "离场 / 地下" to (OperativeToken.OFF_FIELD to ""),
                "晕眩" to (OperativeToken.STUNNED to ""),
                "毒素" to ("毒素" to ""),
                "中毒" to ("中毒" to ""),
                "标记光 +1" to ("标记光" to "1"),
                "标记光 +2" to ("标记光" to "2"),
                "狂暴" to ("狂暴" to ""),
                "APL +1" to ("APL" to "1"),
                "APL −1" to ("APL" to "-1"),
                "圣勉" to ("圣勉" to ""),
                "烈焰" to ("烈焰" to ""),
                "浸湿" to ("浸湿" to ""),
                "仇恨" to ("仇恨" to ""),
                "痛苦" to ("痛苦" to ""),
                "半载" to ("半载" to ""),
                "满载" to ("满载" to ""),
                "猜疑" to ("猜疑" to ""),
                "虚无" to ("虚无" to "1"),
                "速度 4+" to ("速度 4+" to ""),
                "速度 7+" to ("速度 7+" to ""),
                "恐虐" to ("恐虐" to ""),
                "纳垢" to ("纳垢" to ""),
                "色孽" to ("色孽" to ""),
                "奸奇" to ("奸奇" to ""),
                "无分" to ("无分" to ""),
                "守护护甲 2+" to ("守护护甲 2+" to "")
            )
            addView(kit.tagRow(presets.map { (label, pair) ->
                kit.tag(label, ui.ink) {
                    name.setText(pair.first)
                    name.setSelection(name.text.length)
                    value.setText(pair.second)
                }
            }), ui.wrap(8))
            addView(name, ui.wrap(6))
            addView(value, ui.wrap(8))
            addView(ui.text("点选下方持续时间以添加标记", 12f, ui.muted, true), ui.wrap(0))
        }
        kit.actionSheet("状态标记 · ${operative.displayName}", header, TokenDuration.entries.map { duration ->
            CombatKit.SheetAction(duration.label) {
                val title = name.text.toString().trim()
                if (title.isBlank()) {
                    note("请先点选上方预设标记或填写标记名称")
                    return@SheetAction
                }
                val token = OperativeToken(name = title, value = value.text.toString().toIntOrNull(), duration = duration)
                save(BattleRules.syncKills(updateOperative(sideId, memberId) { it.copy(tokens = it.tokens + token) }),
                    "${operative.displayName}：获得${token.label}（${duration.label}）")
            }
        })
    }

    /**
     * Team counters (e.g. nodes, rally points). Rows re-read the saved side on every tap and
     * redraw in place, like the VP sheet, so ± always changes the value shown.
     */
    private fun countersSheet(sideId: String) {
        holdRender = true
        ui.dialog("${battle.side(sideId).playerName} · 小队计数器", onClose = { holdRender = false; render() }) { box, _ ->
            val rows = ui.column()
            fun fill() {
                rows.removeAllViews()
                val side = battle.side(sideId)
                if (side.counters.isEmpty()) rows.addView(ui.text("还没有计数器。用于记录小队规则里需要累计的数值，例如标识、节点、资源点。", 12f, ui.muted), ui.wrap(8))
                side.counters.forEach { c ->
                    rows.addView(numberRow(c.name + (c.resetEachTurningPoint?.let { " · 每转折点重置为 $it" } ?: ""), c.value, -99, 99) { v ->
                        val current = battle.side(sideId)
                        val before = current.counters.firstOrNull { it.id == c.id }?.value ?: return@numberRow
                        saveSide(current.copy(counters = current.counters.map { if (it.id == c.id) it.copy(value = v) else it }),
                            "${current.playerName}：${c.name} $before → $v")
                        fill(); ui.refreshDialog()
                    }, ui.wrap(4))
                    rows.addView(ui.link("删除「${c.name}」") {
                        val current = battle.side(sideId)
                        saveSide(current.copy(counters = current.counters.filterNot { it.id == c.id }), "${current.playerName}：删除计数器 ${c.name}")
                        fill(); ui.refreshDialog()
                    }, ui.wrap(8))
                }
            }
            fill()
            box.addView(rows, ui.wrap(8))
            box.addView(ui.divider())
            val name = ui.input("", "新计数器名称，例如：节点")
            val reset = ui.input("", "每转折点重置为（选填）", numeric = true)
            box.addView(name, ui.wrap(6))
            box.addView(reset, ui.wrap(6))
            box.addView(ui.button("添加计数器", true) {
                val title = name.text.toString().trim()
                if (title.isNotBlank()) {
                    val start = reset.text.toString().toIntOrNull()
                    val current = battle.side(sideId)
                    saveSide(current.copy(counters = current.counters + TeamCounter(name = title, value = start ?: 0, resetEachTurningPoint = start)),
                        "${current.playerName}：添加计数器 $title")
                    name.setText(""); reset.setText("")
                    fill(); ui.refreshDialog()
                }
            }, ui.wrap())
        }
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
                val inField = side.operatives.count { !it.incapacitated && !it.offField }
                val offField = side.operatives.count { !it.incapacitated && it.offField }
                addView(ui.text("在场 $inField/${side.operatives.size}" + if (offField > 0) "（离场 $offField）" else "", 12f, ui.muted), ui.wrap(0))
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
