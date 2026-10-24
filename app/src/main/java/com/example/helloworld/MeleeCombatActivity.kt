package com.example.helloworld

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.ArrayDeque

/**
 * Melee resolution (p17-18): both sides roll attack dice, then alternate strikes and parries.
 * Each step can be undone with a visible trace and adjusted manually for team rules.
 */
class MeleeCombatActivity : AppCompatActivity() {
    private lateinit var ui: RecordUi
    private lateinit var kit: CombatKit
    private lateinit var store: BattleStore
    private lateinit var battle: BattleRecord
    private lateinit var state: MeleeCombatState
    private lateinit var root: LinearLayout
    private lateinit var sound: DiceSoundPlayer
    private var combatScroll: ScrollView? = null
    private var actionBar: View? = null
    private var attackerTray: DiceTrayView? = null
    private var defenderTray: DiceTrayView? = null

    private lateinit var attackerWeapon: BattleWeaponSnapshot
    private lateinit var defenderWeapon: BattleWeaponSnapshot
    private lateinit var attackerBase: MeleeFighter
    private lateinit var defenderBase: MeleeFighter
    private var defenderCanFight = true

    private val setup = mutableMapOf(
        MeleeSide.ATTACKER to MeleeSetup(),
        MeleeSide.DEFENDER to MeleeSetup()
    )

    private val undoStates = ArrayDeque<MeleeCombatState>()
    private val undoneTrace = mutableListOf<Pair<Int, String>>()
    private val retainChoices = mutableMapOf(
        MeleeSide.ATTACKER to MeleeRetainChoices(true, true, true),
        MeleeSide.DEFENDER to MeleeRetainChoices(true, true, true)
    )
    private var rolling = false
    private var committed = false
    private var attackerRollResult: List<Int>? = null
    private var defenderRollResult: List<Int>? = null

    private data class MeleeSetup(
        val assists: Int = 0,
        val accurate: Int? = null,
        val modifiers: CombatModifiers = CombatModifiers()
    )

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = RecordUi(this)
        kit = CombatKit(ui)
        store = BattleStore(this)
        sound = DiceSoundPlayer(this)
        val loaded = intent.getStringExtra(EXTRA_BATTLE_ID)?.let(store::load)
        if (loaded == null || loaded.completed) {
            finish()
            return
        }
        battle = loaded
        if (!loadFighters()) {
            finish()
            return
        }
        rebuild()
        setupBackNavigation()
        render()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun loadFighters(): Boolean {
        val attackerSideId = intent.getStringExtra(EXTRA_ATTACKER_SIDE_ID) ?: return false
        val attackerMemberId = intent.getStringExtra(EXTRA_ATTACKER_MEMBER_ID) ?: return false
        val attackerWeaponId = intent.getStringExtra(EXTRA_ATTACKER_WEAPON_ID) ?: return false
        val defenderSideId = intent.getStringExtra(EXTRA_DEFENDER_SIDE_ID) ?: return false
        val defenderMemberId = intent.getStringExtra(EXTRA_DEFENDER_MEMBER_ID) ?: return false
        val defenderWeaponId = intent.getStringExtra(EXTRA_DEFENDER_WEAPON_ID)
        val attackerSide = battle.side(attackerSideId)
        val defenderSide = battle.side(defenderSideId)
        val attacker = attackerSide.operatives.firstOrNull { it.memberId == attackerMemberId } ?: return false
        val defender = defenderSide.operatives.firstOrNull { it.memberId == defenderMemberId } ?: return false
        attackerWeapon = CombatWeapons.of(this, attackerSide, attacker).firstOrNull { it.id == attackerWeaponId } ?: return false
        val chosen = defenderWeaponId?.let { id -> CombatWeapons.of(this, defenderSide, defender).firstOrNull { it.id == id } }
        defenderCanFight = intent.getBooleanExtra(EXTRA_CAN_RETALIATE, chosen != null) && chosen != null
        defenderWeapon = chosen ?: BattleWeaponSnapshot("no-retaliation", "无法反击", "0A", 0, 6, 6, 0, 0, emptyList())
        attackerBase = fighter(MeleeSide.ATTACKER, attackerSide, attacker, attackerWeapon, true)
        defenderBase = fighter(MeleeSide.DEFENDER, defenderSide, defender, defenderWeapon, defenderCanFight)
        return true
    }

    private fun fighter(
        slot: MeleeSide,
        side: BattleSide,
        operative: BattleOperativeState,
        weapon: BattleWeaponSnapshot,
        canFight: Boolean
    ) = MeleeFighter(
        side = slot,
        battleSideId = side.id,
        memberId = operative.memberId,
        playerName = side.playerName,
        operativeName = operative.displayName,
        weaponName = weapon.name,
        maxWounds = operative.maxWounds,
        currentWounds = operative.currentWounds,
        attacks = if (canFight) weapon.attacks else 0,
        baseHit = weapon.hit,
        normalDamage = weapon.normalDamage,
        criticalDamage = weapon.criticalDamage,
        canFight = canFight
    )

    private fun weaponOf(side: MeleeSide) = if (side == MeleeSide.ATTACKER) attackerWeapon else defenderWeapon

    private fun built(side: MeleeSide): MeleeFighter {
        val base = if (side == MeleeSide.ATTACKER) attackerBase else defenderBase
        val weapon = weaponOf(side)
        val s = setup.getValue(side)
        val rules = MeleeRuleParser.parse(s.modifiers.keywords(weapon.keywords), weapon.critical)
        val attacks = if (base.canFight) (weapon.attacks + s.modifiers.attacks).coerceAtLeast(0) else 0
        val accurate = minOf(attacks, rules.accurate)
        return base.copy(
            attacks = attacks,
            baseHit = (weapon.hit + s.modifiers.hit).coerceIn(2, 6),
            normalDamage = (weapon.normalDamage + s.modifiers.normalDamage).coerceAtLeast(0),
            criticalDamage = (weapon.criticalDamage + s.modifiers.criticalDamage).coerceAtLeast(0),
            assists = s.assists,
            accurateRetained = (s.accurate ?: accurate).coerceIn(0, accurate),
            rules = rules
        )
    }

    private fun rebuild() {
        val initiative = if (battle.initiativeSideId == defenderBase.battleSideId) MeleeSide.DEFENDER else MeleeSide.ATTACKER
        state = MeleeCombatRules.start(built(MeleeSide.ATTACKER), built(MeleeSide.DEFENDER), initiative)
    }

    // ---------------------------------------------------------------- layout

    private fun render() {
        val previousScrollY = combatScroll?.scrollY ?: 0
        root = ui.column().apply { setBackgroundColor(ui.base) }
        setContentView(root)
        root.addView(kit.topBar(
            title = "近战 · ${phaseLabel()}",
            subtitle = "${state.attacker.operativeName} ⚔ ${state.defender.operativeName}",
            undoLabel = undoLabel(),
            onBack = ::requestClose,
            onUndo = ::undoLast,
            trailing = kit.diceButton(ui) { DiceSheet(ui) }
        ))
        root.addView(ui.divider())
        val body = ui.column(12)
        val scroll = ScrollView(this).apply { addView(body) }
        combatScroll = scroll
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = ui.column().apply {
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))
            setBackgroundColor(0xff141414.toInt())
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        actionBar = bar
        when (state.phase) {
            MeleePhase.READY -> renderReady(body, bar)
            MeleePhase.REROLL -> renderReroll(body, bar)
            MeleePhase.RETAIN -> renderRetain(body, bar)
            MeleePhase.RESOLVE -> renderResolve(body, bar)
            MeleePhase.FINISHED -> renderFinished(body, bar)
        }
        body.addView(ui.divider())
        body.addView(kit.logView(state.log, undoneTrace), ui.wrap(16))
        scroll.post { scroll.scrollTo(0, previousScrollY) }
    }

    private fun phaseLabel(): String = when (state.phase) {
        MeleePhase.READY -> "准备"
        MeleePhase.REROLL -> "重投"
        MeleePhase.RETAIN -> "保留"
        MeleePhase.RESOLVE -> "结算"
        MeleePhase.FINISHED -> "结果"
    }

    /** Name, weapon line and wounds for one fighter; [active] marks whose turn it is. */
    private fun fighterHeader(side: MeleeSide, active: Boolean, woundsEditable: Boolean = false): View = ui.row().apply {
        val f = state.fighter(side)
        val color = kit.sideColor(f.battleSideId)
        addView(ui.text(if (active) "▶" else "", 14f, color, true), LinearLayout.LayoutParams(ui.dp(18), -2))
        addView(ui.column().apply {
            addView(ui.text(f.operativeName, 15f, color, true).apply { maxLines = 1 }, ui.wrap(1))
            addView(ui.text(
                if (!f.canFight) "无法反击" else
                    "${f.weaponName} · ${f.attacks}A ${f.effectiveHit}+ ${f.normalDamage}/${f.criticalDamage}" +
                        (if (f.assists > 0) " · 协助 ${f.assists}" else "") + (if (f.wounded) " · 受创" else ""),
                13f, if (f.wounded) ui.orange else 0xffc9c7c2.toInt(), true
            ).apply { maxLines = 1 }, ui.wrap(0))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ui.text("耐伤 ${f.currentWounds}/${f.maxWounds}", 16f, if (f.currentWounds > 0) ui.ink else kit.danger, true).apply {
            gravity = Gravity.CENTER
            minHeight = ui.dp(44)
            setPadding(ui.dp(8), 0, ui.dp(8), 0)
            if (woundsEditable) {
                background = ui.bg(0xff242424.toInt())
                contentDescription = "调整${f.operativeName}耐伤"
                setOnClickListener { woundSheet(side) }
            }
        })
    }

    // ---------------------------------------------------------------- READY

    private fun renderReady(body: LinearLayout, bar: LinearLayout) {
        listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).forEach { side ->
            val f = state.fighter(side)
            val s = setup.getValue(side)
            body.addView(ui.column().apply {
                setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                background = ui.bg(kit.sideFill(f.battleSideId), kit.sideStroke(f.battleSideId))
                addView(fighterHeader(side, false), ui.wrap(6))
                if (f.canFight) {
                    val weapon = weaponOf(side)
                    val keywords = s.modifiers.keywords(weapon.keywords)
                    val tags = keywords.map { k ->
                        kit.tag(k, if (k in s.modifiers.extraKeywords) ui.accent else ui.ink) { WeaponRulesUi.show(ui, weapon.name, keywords) }
                    } + s.modifiers.summary().filterNot { it.startsWith("+") && it.drop(1) in s.modifiers.extraKeywords }
                        .map { kit.tag(it, ui.accent) { openModifiers(side) } } +
                        kit.tag("＋ 修饰", ui.accent) { openModifiers(side) }
                    addView(kit.tagRow(tags), ui.wrap(8))
                    addView(kit.segmented("协助", listOf("0", "1", "2", "3"), s.assists.coerceAtMost(3),
                        info = { CoreRules.show(ui, "melee") }) { value ->
                        setup[side] = s.copy(assists = value); rebuild(); render()
                    }, ui.wrap(6))
                    val accurate = minOf(f.attacks, f.rules.accurate)
                    if (accurate > 0) addView(kit.segmented("精准预留", (0..accurate).map { it.toString() }, f.accurateRetained) { value ->
                        setup[side] = s.copy(accurate = value); rebuild(); render()
                    }, ui.wrap(0))
                }
            }, ui.wrap(10))
        }
        trays(body)
        bar.addView(kit.primaryButton("双方掷骰") { rollBoth() })
    }

    private fun openModifiers(side: MeleeSide) {
        val weapon = weaponOf(side)
        kit.modifierSheet(ui, "修饰 · ${state.fighter(side).operativeName}", weapon.keywords, setup.getValue(side).modifiers, shooting = false) {
            setup[side] = setup.getValue(side).copy(modifiers = it, accurate = null)
            rebuild()
            render()
        }
    }

    private fun trays(body: LinearLayout) {
        val holder = if (isTablet) ui.row().apply { gravity = Gravity.TOP } else ui.column()
        val rolled = state.phase != MeleePhase.READY
        val height = ui.dp(if (isTablet) (if (rolled) 150 else 200) else (if (rolled) 84 else 130))
        attackerTray = tray(MeleeSide.ATTACKER)
        defenderTray = tray(MeleeSide.DEFENDER)
        if (isTablet) {
            holder.addView(attackerTray, LinearLayout.LayoutParams(0, height, 1f).apply { marginEnd = ui.dp(8) })
            holder.addView(defenderTray, LinearLayout.LayoutParams(0, height, 1f))
        } else {
            holder.addView(attackerTray, LinearLayout.LayoutParams(-1, height).apply { bottomMargin = ui.dp(6) })
            holder.addView(defenderTray, LinearLayout.LayoutParams(-1, height))
        }
        body.addView(holder, ui.wrap(8))
    }

    private fun tray(side: MeleeSide) = DiceTrayView(this).apply {
        val f = state.fighter(side)
        background = ui.bg(0xff101010.toInt(), kit.sideStroke(f.battleSideId))
        contentDescription = "${f.operativeName}攻击骰"
        setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
        restoreResults(state.pool(side).dice.map { it.value })
    }

    private fun trayOf(side: MeleeSide) = if (side == MeleeSide.ATTACKER) attackerTray else defenderTray

    private fun diceChips(side: MeleeSide, onTap: ((Int) -> Unit)?): View {
        val f = state.fighter(side)
        val pool = state.pool(side)
        val chips = pool.dice.map { die ->
            DieChip(die.value, reading = MeleeCombatRules.readDie(state, side, die), rerolled = die.rerolled, edited = die.override != null)
        } + List(f.accurateRetained + pool.retainedNormals) { DieChip(null, reading = DieReading.NORMAL) } +
            List(pool.retainedCriticals) { DieChip(null, reading = DieReading.CRITICAL) }
        return kit.diceRow(chips, kit.sideColor(f.battleSideId), onTap?.let { tap -> { index: Int -> if (index < pool.dice.size) tap(index) } })
    }

    // ---------------------------------------------------------------- REROLL

    private fun renderReroll(body: LinearLayout, bar: LinearLayout) {
        trays(body)
        listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).forEach { side ->
            trayOf(side)?.setOnDieTapListener { dieSheet(side, it) }
            body.addView(fighterHeader(side, state.rerollTurn == side), ui.wrap(4))
            body.addView(diceChips(side) { dieSheet(side, it) }, ui.wrap(4))
            body.addView(ui.row().apply {
                val pool = state.pool(side)
                addView(ui.text(kit.tally(pool.dice.map { MeleeCombatRules.readDie(state, side, it) },
                    state.fighter(side).accurateRetained + pool.retainedNormals, pool.retainedCriticals), 12f, ui.muted, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(kit.tag("＋ 预留", ui.accent) { retainedSheet(side) })
            }, ui.wrap(12))
        }
        val turn = state.fighter(state.rerollTurn)
        bar.addView(ui.text("轮到 ${turn.operativeName}：点骰子重投，或跳过", 12f, ui.muted, true), ui.wrap(6))
        bar.addView(kit.actionBar(
            kit.primaryButton("跳过") { pass() },
            kit.secondaryButton("双方结束重投") { endRerolls() }
        ))
    }

    private fun sourceInput(): EditText = ui.input("", "来源（选填）：计谋、能力或装备名")

    private fun dieSheet(side: MeleeSide, index: Int) {
        if (rolling || state.phase != MeleePhase.REROLL) return
        val f = state.fighter(side)
        val die = state.pool(side).dice[index]
        val input = sourceInput()
        val myTurn = state.rerollTurn == side
        val spent = if (side == MeleeSide.ATTACKER) state.attackerCpSpent else state.defenderCpSpent
        val cpLeft = battle.side(f.battleSideId).commandPoints - spent
        val actions = mutableListOf<CombatKit.SheetAction>()
        if (f.rules.rerollPolicy != MeleeRerollPolicy.NONE) {
            actions += CombatKit.SheetAction("重投 · ${rerollName(f.rules.rerollPolicy)}",
                MeleeCombatRules.canRerollWith(state, side, index, RerollSource.WEAPON), primary = true) {
                reroll(side, index, RerollSource.WEAPON, "")
            }
        }
        actions += CombatKit.SheetAction("重投 · 指挥重掷（1CP，剩 $cpLeft）", myTurn && !die.rerolled && cpLeft > 0) {
            reroll(side, index, RerollSource.COMMAND, "")
        }
        actions += CombatKit.SheetAction("重投 · 其他规则", myTurn && !die.rerolled) {
            reroll(side, index, RerollSource.OTHER, input.text.toString())
        }
        val reading = MeleeCombatRules.readDie(state, side, die)
        DieOverride.entries.filter { it.name != reading.name }.forEach { o ->
            actions += CombatKit.SheetAction(if (o == DieOverride.DISCARDED) "舍弃" else "改判为${o.label}") { edit(side, index, o, null, input.text.toString()) }
        }
        if (die.override != null) actions += CombatKit.SheetAction("恢复按点数判定") { edit(side, index, null, null, input.text.toString()) }
        actions += CombatKit.SheetAction("改点数…") { pickFace { edit(side, index, die.override, it, input.text.toString()) } }
        val title = "${f.operativeName} · 骰子 ${die.value}" + when {
            die.rerolled -> " · 已重投"
            !myTurn -> " · 未轮到重投"
            else -> ""
        }
        kit.actionSheet(title, input, actions)
    }

    private fun rerollName(policy: MeleeRerollPolicy) = when (policy) {
        MeleeRerollPolicy.BALANCED -> "平衡"
        MeleeRerollPolicy.CEASELESS -> "无休"
        MeleeRerollPolicy.RELENTLESS -> "毫不留情"
        MeleeRerollPolicy.NONE -> "武器规则"
    }

    private fun pickFace(onPick: (Int) -> Unit) {
        ui.dialog("选择点数") { box, sheet ->
            box.addView(ui.row().apply {
                (1..6).forEach { value ->
                    addView(ui.button(value.toString(), true) { sheet.dismiss(); onPick(value) },
                        LinearLayout.LayoutParams(0, ui.dp(52), 1f).apply { marginEnd = ui.dp(4) })
                }
            })
        }
    }

    private fun edit(side: MeleeSide, index: Int, override: DieOverride?, value: Int?, note: String) {
        pushUndo()
        state = MeleeCombatRules.editDie(state, side, index, override, value, note)
        render()
    }

    private fun retainedSheet(side: MeleeSide) {
        val input = sourceInput()
        fun add(critical: Boolean) {
            pushUndo()
            state = MeleeCombatRules.addRetained(state, side, critical, input.text.toString())
            render()
        }
        kit.actionSheet("预留成功（不掷骰）", input, listOf(
            CombatKit.SheetAction("预留 1 个普通成功") { add(false) },
            CombatKit.SheetAction("预留 1 个暴击") { add(true) }
        ))
    }

    private fun reroll(side: MeleeSide, index: Int, source: RerollSource, note: String) {
        val tray = trayOf(side) ?: return
        if (rolling) return
        rolling = true
        sound.play()
        tray.selectOnly(index)
        tray.rerollSelected { values ->
            rolling = false
            if (isFinishing || isDestroyed) return@rerollSelected
            pushUndo()
            state = MeleeCombatRules.rerollWith(state, side, index, values.single(), source, note)
            render()
        }
    }

    private fun pass() {
        if (rolling) return
        pushUndo()
        state = MeleeCombatRules.passReroll(state, state.rerollTurn)
        autoRetain()
        render()
    }

    private fun endRerolls() {
        if (rolling) return
        pushUndo()
        while (state.phase == MeleePhase.REROLL) state = MeleeCombatRules.passReroll(state, state.rerollTurn)
        autoRetain()
        render()
    }

    // ---------------------------------------------------------------- RETAIN

    private data class RetainOption(val side: MeleeSide, val key: String, val label: String)

    private fun retainOptions(): List<RetainOption> = listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).flatMap { side ->
        val f = state.fighter(side)
        val p = MeleeCombatRules.retentionPreview(state, side)
        val canPunish = f.rules.punishing && p.naturalCriticals > 0 && p.failures > 0
        buildList {
            if (canPunish) add(RetainOption(side, "punishing", "重击：1 失败 → 普通"))
            if (f.rules.rending && p.naturalCriticals > 0 && (p.rolledNormals > 0 || canPunish)) add(RetainOption(side, "rending", "撕裂：1 普通 → 暴击"))
            if (f.rules.severe && p.naturalCriticals == 0 && p.rolledNormals + p.retainedNormals > 0) add(RetainOption(side, "severe", "严重：1 普通 → 暴击"))
        }
    }

    private fun autoRetain() {
        if (state.phase == MeleePhase.RETAIN && retainOptions().isEmpty()) {
            state = MeleeCombatRules.finishRetention(state)
        }
    }

    private fun renderRetain(body: LinearLayout, bar: LinearLayout) {
        listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).forEach { side ->
            body.addView(fighterHeader(side, false), ui.wrap(4))
            body.addView(diceChips(side, null), ui.wrap(8))
            retainOptions().filter { it.side == side }.forEach { option ->
                val choices = retainChoices.getValue(side)
                val on = when (option.key) {
                    "punishing" -> choices.punishing
                    "rending" -> choices.rending
                    else -> choices.severe
                }
                body.addView(kit.segmented(option.label, listOf("不用", "使用"), if (on) 1 else 0) { pick ->
                    retainChoices[side] = when (option.key) {
                        "punishing" -> choices.copy(punishing = pick == 1)
                        "rending" -> choices.copy(rending = pick == 1)
                        else -> choices.copy(severe = pick == 1)
                    }
                    render()
                }, ui.wrap(8))
            }
        }
        bar.addView(kit.primaryButton("开始结算") {
            pushUndo()
            state = MeleeCombatRules.finishRetention(state, retainChoices.getValue(MeleeSide.ATTACKER), retainChoices.getValue(MeleeSide.DEFENDER))
            render()
        })
    }

    // ---------------------------------------------------------------- RESOLVE

    private fun successChips(side: MeleeSide): List<DieChip> {
        val f = state.fighter(side)
        val pool = state.pool(side)
        val active = state.phase == MeleePhase.RESOLVE && state.resolveTurn == side
        return List(pool.criticals) { DieChip(null, face = "暴${f.criticalDamage}", reading = DieReading.CRITICAL, dim = !active) } +
            List(pool.normals) { DieChip(null, face = "普${f.normalDamage}", reading = DieReading.NORMAL, dim = !active) }
    }

    private fun renderResolve(body: LinearLayout, bar: LinearLayout) {
        listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).forEach { side ->
            val pool = state.pool(side)
            body.addView(ui.column().apply {
                setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                val active = state.resolveTurn == side
                background = ui.bg(if (active) kit.sideFill(state.fighter(side).battleSideId) else 0xff161616.toInt(),
                    if (active) kit.sideColor(state.fighter(side).battleSideId) else 0xff2a2a2a.toInt())
                addView(fighterHeader(side, active, woundsEditable = true), ui.wrap(8))
                addView(kit.diceRow(successChips(side), kit.sideColor(state.fighter(side).battleSideId), { index ->
                    val success = if (index < pool.criticals) MeleeSuccess.CRITICAL else MeleeSuccess.NORMAL
                    successSheet(side, success)
                }, size = 52), ui.wrap(0))
            }, ui.wrap(10))
        }
        if (state.fighter(state.resolveTurn.opponent()).rules.brutal) {
            body.addView(kit.tag("残暴：只能用暴击格挡", ui.orange) { WeaponRulesUi.show(ui, weaponOf(state.resolveTurn.opponent()).name, listOf("残暴")) }, ui.wrap(8))
        }
        val turn = state.fighter(state.resolveTurn)
        bar.addView(ui.row().apply {
            addView(ui.text("轮到 ${turn.operativeName}：点自己的成功骰出击或格挡", 13f, kit.sideColor(turn.battleSideId), true),
                LinearLayout.LayoutParams(0, -2, 1f))
            if (state.pool(state.resolveTurn.opponent()).successes > 0) addView(kit.tag("改由对方结算", ui.muted) {
                val input = sourceInput()
                kit.actionSheet("改由对方结算（规则效果）", input, listOf(CombatKit.SheetAction("确认", primary = true) {
                    pushUndo()
                    state = MeleeCombatRules.switchTurn(state, input.text.toString())
                    render()
                }))
            })
        })
    }

    private fun successSheet(owner: MeleeSide, success: MeleeSuccess) {
        val f = state.fighter(owner)
        val opponentPool = state.pool(owner.opponent())
        val myTurn = state.resolveTurn == owner
        val kind = if (success == MeleeSuccess.CRITICAL) "暴击" else "普通成功"
        val input = sourceInput()
        val actions = mutableListOf<CombatKit.SheetAction>()
        if (myTurn) {
            val damage = if (success == MeleeSuccess.CRITICAL) f.criticalDamage else f.normalDamage
            actions += CombatKit.SheetAction("出击 · 造成 $damage 伤", primary = true) { resolve(owner, success, MeleeUse.STRIKE, null) }
            val canParry = MeleeCombatRules.canParryWith(state, owner, success)
            if (opponentPool.successes == 0) {
                actions += CombatKit.SheetAction("格挡（对手已无成功）", canParry) { resolve(owner, success, MeleeUse.PARRY, null) }
            } else {
                actions += CombatKit.SheetAction("格挡对手 1 个普通成功", canParry && opponentPool.normals > 0) {
                    resolve(owner, success, MeleeUse.PARRY, MeleeSuccess.NORMAL)
                }
                if (success == MeleeSuccess.CRITICAL) actions += CombatKit.SheetAction("格挡对手 1 个暴击", canParry && opponentPool.criticals > 0) {
                    resolve(owner, success, MeleeUse.PARRY, MeleeSuccess.CRITICAL)
                }
            }
        }
        actions += CombatKit.SheetAction("舍弃这个$kind（规则效果）") {
            pushUndo()
            state = MeleeCombatRules.discardSuccess(state, owner, success, input.text.toString())
            render()
        }
        kit.actionSheet("${f.operativeName} · $kind", input, actions)
    }

    private fun resolve(side: MeleeSide, success: MeleeSuccess, use: MeleeUse, target: MeleeSuccess?) {
        pushUndo()
        state = MeleeCombatRules.resolve(state, side, success, use, target)
        render()
    }

    private fun woundSheet(side: MeleeSide) {
        val f = state.fighter(side)
        val input = sourceInput()
        fun change(delta: Int) {
            pushUndo()
            state = MeleeCombatRules.adjustWounds(state, side, delta, input.text.toString())
            render()
        }
        kit.actionSheet("${f.operativeName} 耐伤 ${f.currentWounds}/${f.maxWounds}", input, listOf(
            CombatKit.SheetAction("回复 1（减伤或治疗）") { change(1) },
            CombatKit.SheetAction("回复 2") { change(2) },
            CombatKit.SheetAction("额外受到 1 伤") { change(-1) },
            CombatKit.SheetAction("额外受到 2 伤") { change(-2) }
        ))
    }

    // ---------------------------------------------------------------- FINISHED

    private fun renderFinished(body: LinearLayout, bar: LinearLayout) {
        listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).forEach { side ->
            val f = state.fighter(side)
            val start = (if (side == MeleeSide.ATTACKER) attackerBase else defenderBase).currentWounds
            body.addView(ui.row().apply {
                minimumHeight = ui.dp(52)
                addView(ui.text(f.operativeName, 16f, kit.sideColor(f.battleSideId), true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.text(if (f.currentWounds <= 0) "残废" else "耐伤 $start → ${f.currentWounds}", 18f,
                    if (f.currentWounds <= 0) kit.danger else ui.ink, true).apply {
                    setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
                    background = ui.bg(0xff242424.toInt())
                    setOnClickListener { woundSheet(side) }
                })
            }, ui.wrap(6))
        }
        val tags = buildList {
            if (state.attackerStunned) add("${state.attacker.operativeName} 晕眩")
            if (state.defenderStunned) add("${state.defender.operativeName} 晕眩")
            val cp = state.attackerCpSpent + state.defenderCpSpent
            if (cp > 0) add("指挥重掷 ${cp}CP")
        }
        if (tags.isNotEmpty()) body.addView(kit.tagRow(tags.map { kit.tag(it, ui.ink) }), ui.wrap(8))
        bar.addView(kit.primaryButton("完成") { commitResult() })
    }

    // ---------------------------------------------------------------- rolls

    private fun rollBoth() {
        if (rolling || state.phase != MeleePhase.READY) return
        val a = attackerTray ?: return
        val d = defenderTray ?: return
        rolling = true
        attackerRollResult = null
        defenderRollResult = null
        sound.play()
        a.roll(state.attacker.rolledDiceCount) { values -> attackerRollResult = values; finishRollIfReady() }
        d.roll(state.defender.rolledDiceCount) { values -> defenderRollResult = values; finishRollIfReady() }
    }

    private fun finishRollIfReady() {
        if (!rolling || isFinishing || isDestroyed) return
        val attackerValues = attackerRollResult ?: return
        val defenderValues = defenderRollResult ?: return
        rolling = false
        pushUndo()
        state = MeleeCombatRules.acceptRolls(state, attackerValues, defenderValues)
        autoRetain()
        render()
    }

    // ---------------------------------------------------------------- undo

    private fun pushUndo() {
        undoStates.addLast(state)
    }

    private fun undoLabel(): String? {
        val previous = undoStates.peekLast() ?: return null
        return state.log.drop(previous.log.size).lastOrNull() ?: "上一步"
    }

    private fun undoLast() {
        if (rolling) { note("请等待骰子停止"); return }
        val previous = undoStates.pollLast() ?: return
        undoneTrace += state.log.drop(previous.log.size).map { previous.log.size to it }
        state = previous
        render()
    }

    // ---------------------------------------------------------------- commit

    private fun commitResult() {
        if (committed || state.phase != MeleePhase.FINISHED) return
        val latest = store.load(battle.battleId)
        if (latest == null || latest.completed) {
            note("对局已不存在或已经结束，无法写回")
            return
        }
        var next: BattleRecord = latest
        val active = next.progress.active
        if (active?.side == state.attacker.battleSideId && active.member == state.attacker.memberId) {
            runCatching { BattleFlow.action(next, "近战", 1, intent.getStringExtra(BattleActivity.EXTRA_ACTION_REASON).orEmpty()) }
                .onSuccess { next = it }
        }
        fun update(f: MeleeFighter, stunned: Boolean, source: String) {
            val side = next.side(f.battleSideId)
            next = BattleFlow.side(next, side.copy(operatives = side.operatives.map { o ->
                if (o.memberId != f.memberId) o else {
                    val wounded = BattleRules.setWounds(o, f.currentWounds)
                    if (stunned && !wounded.stunned && !wounded.incapacitated) wounded.copy(tokens = wounded.tokens +
                        OperativeToken(name = OperativeToken.STUNNED, duration = TokenDuration.NEXT_ACTIVATION_END, source = source))
                    else wounded
                }
            }))
        }
        update(state.attacker, state.attackerStunned, defenderWeapon.name)
        update(state.defender, state.defenderStunned, attackerWeapon.name)
        listOf(state.attacker.battleSideId to state.attackerCpSpent, state.defender.battleSideId to state.defenderCpSpent)
            .filter { it.second > 0 }
            .forEach { (sideId, cp) ->
                val side = next.side(sideId)
                next = BattleFlow.side(next, side.copy(commandPoints = (side.commandPoints - cp).coerceAtLeast(0)))
            }
        val summary = buildString {
            append("近战：${state.attacker.operativeName}（${attackerWeapon.name}）⚔ ${state.defender.operativeName}（${defenderWeapon.name}），")
            append("耐伤 ${state.attacker.currentWounds}/${state.attacker.maxWounds} 与 ${state.defender.currentWounds}/${state.defender.maxWounds}")
            if (state.attacker.currentWounds <= 0) append("，${state.attacker.operativeName}残废")
            if (state.defender.currentWounds <= 0) append("，${state.defender.operativeName}残废")
            if (state.attackerStunned) append("，${state.attacker.operativeName}晕眩")
            if (state.defenderStunned) append("，${state.defender.operativeName}晕眩")
            if (state.attackerCpSpent > 0) append("；${state.attacker.playerName}指挥重掷 ${state.attackerCpSpent}CP")
            if (state.defenderCpSpent > 0) append("；${state.defender.playerName}指挥重掷 ${state.defenderCpSpent}CP")
            listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).forEach { side ->
                val m = setup.getValue(side).modifiers
                if (!m.isEmpty) append("；${state.fighter(side).operativeName}修饰：${m.summary().joinToString("、")}${noteSuffix(m.source)}")
            }
            append("\n过程：${state.log.joinToString("；")}")
            if (undoneTrace.isNotEmpty()) append("\n已撤回：${undoneTrace.joinToString("；") { it.second }}")
        }
        next = BattleFlow.event(next, summary)
        battle = store.save(next)
        committed = true
        finish()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = requestClose()
        })
    }

    private fun requestClose() {
        if (rolling) {
            note("请等待骰子停止")
            return
        }
        if (state.phase == MeleePhase.READY || committed) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("放弃本次近战？")
            .setMessage("尚未写回的骰子与耐伤会被丢弃。")
            .setNegativeButton("继续结算", null)
            .setPositiveButton("放弃") { _, _ -> finish() }
            .show()
    }

    private fun note(message: String) {
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).also { s -> actionBar?.let { s.anchorView = it } }.show()
    }

    override fun onDestroy() {
        rolling = false
        if (::sound.isInitialized) sound.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BATTLE_ID = "melee_battle_id"
        const val EXTRA_ATTACKER_SIDE_ID = "melee_attacker_side_id"
        const val EXTRA_ATTACKER_MEMBER_ID = "melee_attacker_member_id"
        const val EXTRA_ATTACKER_WEAPON_ID = "melee_attacker_weapon_id"
        const val EXTRA_DEFENDER_SIDE_ID = "melee_defender_side_id"
        const val EXTRA_DEFENDER_MEMBER_ID = "melee_defender_member_id"
        const val EXTRA_DEFENDER_WEAPON_ID = "melee_defender_weapon_id"
        const val EXTRA_CAN_RETALIATE = "melee_can_retaliate"
    }
}
