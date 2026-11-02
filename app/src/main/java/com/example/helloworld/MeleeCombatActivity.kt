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
        MeleeSide.ATTACKER to MeleeRetainChoices(),
        MeleeSide.DEFENDER to MeleeRetainChoices()
    )
    private var rolling = false
    private var committed = false
    private var attackerRollResult: List<Int>? = null
    private var defenderRollResult: List<Int>? = null
    private val frenzySides = mutableSetOf<MeleeSide>()
    private val preventIncapSides = mutableSetOf<MeleeSide>()
    private var detonationSide: MeleeSide? = null

    private data class MeleeSetup(
        val assists: Int = 0,
        val accurate: Int? = null,
        val modifiers: CombatModifiers = CombatModifiers(),
        val presetNormals: Int = 0,
        val presetCriticals: Int = 0,
        val presetNote: String = ""
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
        defenderOptions = CombatWeapons.of(this, defenderSide, defender).filter { it.isMelee }
        attackerBase = fighter(MeleeSide.ATTACKER, attackerSide, attacker, attackerWeapon, true)
        setup[MeleeSide.ATTACKER] = MeleeSetup(modifiers = defaultMeleeModifiers(attackerSide, defender, attackerWeapon))
        setDefenderWeapon(if (intent.getBooleanExtra(EXTRA_CAN_RETALIATE, chosen != null)) chosen else null)
        return true
    }

    private var defenderOptions: List<BattleWeaponSnapshot> = emptyList()

    private fun defaultMeleeModifiers(
        ownSide: BattleSide,
        opponent: BattleOperativeState,
        weapon: BattleWeaponSnapshot
    ): CombatModifiers {
        var mod = CombatModifiers()
        val sources = mutableListOf<String>()
        val extraKeywords = mutableListOf<String>()
        if (weapon.keywords.any { it.contains("剧毒") } && opponent.tokens.any { it.name.contains("毒素") }) {
            mod = mod.copy(normalDamage = mod.normalDamage + 1, criticalDamage = mod.criticalDamage + 1)
            sources += "剧毒（目标有毒素标识）"
        }
        val activePloys = battle.progress.usedPlans
            .filter { it.startsWith("${ownSide.id}/") && !it.startsWith("${ownSide.id}/交战:") }
            .map { it.substringAfter("/") }
        if (ownSide.teamId == "KT-KOMMANDOS" && activePloys.any { it.contains("WAAAGH", ignoreCase = true) }) {
            extraKeywords += "均衡"
            sources += "WAAAGH!"
        }
        if (activePloys.any { it.contains("上刺刀") || it.contains("刺刀准备") || it.contains("科恩") || it.contains("征服者条令") || it.contains("征服者教条") }) {
            extraKeywords += "无休"
            sources += "战略计划/计谋"
        }
        if (activePloys.any { it.contains("守护者条令") || it.contains("保护者教条") }) {
            mod = mod.copy(hit = mod.hit + 1)
            sources += "守护者条令（近战劣化）"
        }
        if (ownSide.teamId == "CHAOS-FELL" && activePloys.any { it.contains("暴躁脾气") }) {
            extraKeywords += "均衡"
            sources += "暴躁脾气"
        }
        if (ownSide.teamId == "IMP-AOD" && activePloys.any { it.contains("突击条令") }) {
            extraKeywords += "均衡"
            sources += "突击条令"
        }
        if (ownSide.teamId == "KT-VOID_DANCER_TROUPE" && activePloys.any { it.contains("史诗") }) {
            extraKeywords += "均衡"
            sources += "史诗"
        }
        if (ownSide.teamId == "CHAOS-FELL" && activePloys.any { it.contains("狂热猛攻") }) {
            extraKeywords += "严重"
            sources += "狂热猛攻"
        }
        return mod.copy(
            extraKeywords = (mod.extraKeywords + extraKeywords).distinct(),
            source = sources.joinToString("、")
        )
    }

    /** The defender picks its own melee weapon (p17); null means it cannot fight back. */
    private fun setDefenderWeapon(choice: BattleWeaponSnapshot?) {
        val side = battle.side(intent.getStringExtra(EXTRA_DEFENDER_SIDE_ID) ?: return)
        val operative = side.operatives.firstOrNull { it.memberId == intent.getStringExtra(EXTRA_DEFENDER_MEMBER_ID) } ?: return
        val attackerSide = battle.side(intent.getStringExtra(EXTRA_ATTACKER_SIDE_ID) ?: return)
        val attacker = attackerSide.operatives.firstOrNull { it.memberId == intent.getStringExtra(EXTRA_ATTACKER_MEMBER_ID) } ?: return
        defenderCanFight = choice != null
        defenderWeapon = choice ?: BattleWeaponSnapshot("no-retaliation", "无法反击", "0A", 0, 6, 6, 0, 0, emptyList())
        defenderBase = fighter(MeleeSide.DEFENDER, side, operative, defenderWeapon, defenderCanFight)
        setup[MeleeSide.DEFENDER] = MeleeSetup(
            modifiers = if (defenderCanFight) defaultMeleeModifiers(side, attacker, defenderWeapon) else CombatModifiers()
        )
    }

    private fun defenderWeaponSheet() {
        if (state.phase != MeleePhase.READY) return
        kit.actionSheet("${state.defender.operativeName} 用哪件近战武器", null,
            defenderOptions.map { w ->
                CombatKit.SheetAction((if (w.id == defenderWeapon.id) "✓ " else "") + "${w.name} · ${w.profile}", primary = w.id == defenderWeapon.id) {
                    setDefenderWeapon(w); rebuild(); render()
                }
            } + CombatKit.SheetAction("无法反击（规则效果）") { setDefenderWeapon(null); rebuild(); render() })
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
        canFight = canFight,
        frenzied = operative.frenzied,
        frenzyNormalStrikesTaken = operative.tokens.firstOrNull { it.name == "狂暴已受普攻" }?.value ?: 0
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
            rules = rules,
            presetNormals = s.presetNormals,
            presetCriticals = s.presetCriticals,
            presetNote = s.presetNote
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
            addView(ui.text(f.operativeName, 15f, color, true).apply {
                maxLines = 1
                contentDescription = "查看${f.operativeName}人物卡"
                setOnClickListener {
                    val s = battle.side(f.battleSideId)
                    s.operatives.firstOrNull { it.memberId == f.memberId }?.let { o ->
                        BattleReference(ui).operativeCard(s.teamId, o)
                    }
                }
            }, ui.wrap(1))
            addView(ui.text(
                if (!f.canFight) "无法反击" else
                    "${f.weaponName} · ${f.attacks}A ${f.effectiveHit}+ ${f.normalDamage}/${f.criticalDamage}" +
                        (if (f.assists > 0) " · 协助 ${f.assists}" else ""),
                13f, 0xffc9c7c2.toInt(), true
            ).apply { maxLines = 1 }, ui.wrap(0))
            battle.side(f.battleSideId).let { s -> s.operatives.firstOrNull { it.memberId == f.memberId }?.let { o ->
                addView(ui.text(OperativeStats.line(this@MeleeCombatActivity, s, o, f.currentWounds).substringBeforeLast(" · 耐伤"),
                    12f, ui.muted, true).apply { maxLines = 1 }, ui.wrap(0))
            } }
            if (f.wounded) addView(kit.tag("受创：命中 ${f.baseHit}+ → ${(f.baseHit + 1).coerceAtMost(6)}+（移动 −2″）", ui.orange) {
                CoreRules.show(ui, "wounds")
            }, ui.wrap(0))
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

    /** Places Attacker & Defender blocks side-by-side in 2 columns on Tablet, stacked on Phone. */
    private fun sideBySideFighters(body: LinearLayout, buildSide: (LinearLayout, MeleeSide) -> Unit) {
        if (isTablet) {
            body.addView(ui.row().apply {
                gravity = Gravity.TOP
                listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).forEach { side ->
                    addView(ui.column().also { buildSide(it, side) }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (side == MeleeSide.ATTACKER) marginEnd = ui.dp(8)
                    })
                }
            }, ui.wrap(10))
        } else {
            listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).forEach { side -> buildSide(body, side) }
        }
    }

    // ---------------------------------------------------------------- READY

    private fun renderReady(body: LinearLayout, bar: LinearLayout) {
        sideBySideFighters(body) { container, side ->
            val f = state.fighter(side)
            val s = setup.getValue(side)
            container.addView(ui.column().apply {
                setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                background = ui.bg(kit.sideFill(f.battleSideId), kit.sideStroke(f.battleSideId))
                addView(fighterHeader(side, false), ui.wrap(6))
                if (side == MeleeSide.DEFENDER) addView(ui.row().apply {
                    addView(ui.text(if (defenderOptions.isEmpty()) "没有近战武器，无法反击" else "防御方武器：${defenderWeapon.name}（共 ${defenderOptions.size} 件可选）",
                        12f, ui.muted, true), LinearLayout.LayoutParams(0, -2, 1f))
                    if (defenderOptions.isNotEmpty()) addView(kit.tag("更换武器", ui.accent) { defenderWeaponSheet() }
                        .apply { minHeight = ui.dp(36); gravity = Gravity.CENTER_VERTICAL })
                }, ui.wrap(6))
                if (f.canFight) {
                    val weapon = weaponOf(side)
                    val sideTeamId = battle.side(f.battleSideId).teamId
                    val keywords = s.modifiers.keywords(weapon.keywords)
                    val tags = keywords.map { k ->
                        kit.tag(k, if (k in s.modifiers.extraKeywords) ui.accent else ui.ink) { WeaponRulesUi.show(ui, weapon.name, keywords, teamId = sideTeamId) }
                    } + s.modifiers.summary().filterNot { it.startsWith("+") && it.drop(1) in s.modifiers.extraKeywords }
                        .map { kit.tag(it, ui.accent) { openModifiers(side) } } +
                        kit.tag("＋ 修饰", ui.accent) { openModifiers(side) }
                    addView(kit.tagRow(tags), ui.wrap(8))
                    addView(kit.segmented("协助", listOf("0", "1", "2", "3"), s.assists.coerceAtMost(3),
                        info = { CoreRules.show(ui, "melee") }) { value ->
                        setup[side] = s.copy(assists = value); rebuild(); render()
                    }, ui.wrap(6))
                    val accurate = minOf(f.attacks, f.rules.accurate)
                    if (accurate > 0) addView(kit.segmented("精准预留", (0..accurate).map { it.toString() }, f.accurateRetained,
                        info = { WeaponRulesUi.show(ui, weapon.name, listOf("精准")) }) { value ->
                        setup[side] = s.copy(accurate = value); rebuild(); render()
                    }, ui.wrap(6))
                    addView(ui.row().apply {
                        val preset = s.presetNormals + s.presetCriticals
                        addView(ui.text(
                            if (preset == 0) "掷 ${f.rolledDiceCount} 枚" else
                                "掷 ${f.rolledDiceCount} 枚 · 不掷骰预留 ${s.presetNormals} 普通 ${s.presetCriticals} 暴击${noteSuffix(s.presetNote)}",
                            12f, if (preset > 0) ui.accent else ui.muted, true
                        ), LinearLayout.LayoutParams(0, -2, 1f))
                        if (preset > 0) addView(kit.tag("清除预留", ui.muted) {
                            setup[side] = s.copy(presetNormals = 0, presetCriticals = 0, presetNote = ""); rebuild(); render()
                        }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) })
                        addView(kit.tag("＋ 不掷骰预留", ui.accent) { retainedSheet(side) })
                    }, ui.wrap(0))
                }
            }, ui.wrap(if (isTablet) 0 else 10))
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
        sideBySideFighters(body) { container, side ->
            trayOf(side)?.setOnDieTapListener { dieSheet(side, it) }
            container.addView(fighterHeader(side, state.rerollTurn == side), ui.wrap(4))
            container.addView(diceChips(side) { dieSheet(side, it) }, ui.wrap(4))
            container.addView(ui.row().apply {
                val pool = state.pool(side)
                addView(ui.text(kit.tally(pool.dice.map { MeleeCombatRules.readDie(state, side, it) },
                    state.fighter(side).accurateRetained + pool.retainedNormals, pool.retainedCriticals), 12f, ui.muted, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
            }, ui.wrap(4))
            container.addView(weaponPanel(side) { k ->
                val n = k.replace(" ", "")
                n.startsWith("致命") || ((n.startsWith("平衡") || n.startsWith("均衡") || n.startsWith("无休") || n.startsWith("毫不留情")) &&
                    MeleeCombatRules.hasAnyReroll(state, side))
            }, ui.wrap(if (isTablet) 0 else 12))
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

    /** Retaining without rolling happens before the roll and takes dice out of the pool (p15, p92). */
    private fun retainedSheet(side: MeleeSide) {
        if (state.phase != MeleePhase.READY) return
        val f = state.fighter(side)
        val input = ui.input(setup.getValue(side).presetNote, "依据（建议填写）：计谋、能力或装备名")
        fun add(critical: Boolean) {
            val s = setup.getValue(side)
            if (state.fighter(side).rolledDiceCount <= 0) { note("已经没有可预留的攻击骰"); return }
            setup[side] = if (critical) s.copy(presetCriticals = s.presetCriticals + 1, presetNote = input.text.toString().trim())
            else s.copy(presetNormals = s.presetNormals + 1, presetNote = input.text.toString().trim())
            rebuild(); render()
        }
        val header = ui.column().apply {
            addView(ui.text("规则允许“不进行掷骰而保留”时使用：预留的骰子直接算作成功，不再掷出，也不能重投。精准请用上方的精准预留。",
                12f, ui.muted), ui.wrap(8))
            addView(input)
        }
        kit.actionSheet("${f.operativeName} · 不掷骰预留（剩 ${f.rolledDiceCount} 枚待掷）", header, listOf(
            CombatKit.SheetAction("预留 1 个普通成功", f.rolledDiceCount > 0) { add(false) },
            CombatKit.SheetAction("预留 1 个暴击", f.rolledDiceCount > 0) { add(true) }
        ))
    }

    /** Weapon keywords of [side]; each tag opens its rule text. */
    private fun weaponPanel(side: MeleeSide, active: (String) -> Boolean = { false }): View {
        val f = state.fighter(side)
        if (!f.canFight) return ui.text("无法反击", 12f, ui.muted)
        val weapon = weaponOf(side)
        val sideTeamId = battle.side(f.battleSideId).teamId
        return WeaponRulesUi.panel(ui, kit, weapon.name, setup.getValue(side).modifiers.keywords(weapon.keywords), sideTeamId, active)
    }

    private fun reroll(side: MeleeSide, index: Int, source: RerollSource, note: String) {
        val tray = trayOf(side) ?: return
        if (rolling || state.phase != MeleePhase.REROLL) return
        if (!MeleeCombatRules.canRerollWith(state, side, index, source)) return
        rolling = true
        sound.play()
        tray.selectOnly(index)
        tray.rerollSelected { values ->
            rolling = false
            if (isFinishing || isDestroyed || state.phase != MeleePhase.REROLL) return@rerollSelected
            if (!MeleeCombatRules.canRerollWith(state, side, index, source)) return@rerollSelected
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

    private data class RetainOption(val side: MeleeSide, val key: String, val keyword: String, val effect: String)

    private fun retainOptions(): List<RetainOption> = listOf(MeleeSide.ATTACKER, MeleeSide.DEFENDER).flatMap { side ->
        val f = state.fighter(side)
        val p = MeleeCombatRules.retentionPreview(state, side)
        val canPunish = f.rules.punishing && p.naturalCriticals > 0 && p.failures > 0
        buildList {
            if (canPunish) add(RetainOption(side, "punishing", "重击", "已保留暴击：把 1 个失败作为普通成功保留"))
            if (f.rules.rending && p.naturalCriticals > 0 && (p.rolledNormals > 0 || canPunish)) add(RetainOption(side, "rending", "撕裂", "已保留暴击：把 1 个掷出的普通成功改为暴击（精准预留的不行）"))
            if (f.rules.severe && p.naturalCriticals == 0 && p.rolledNormals + p.retainedNormals > 0) add(RetainOption(side, "severe", "严重", "没有暴击：把 1 个普通成功改为暴击；之后不能再用重击、撕裂"))
        }
    }

    private fun autoRetain() {
        if (state.phase == MeleePhase.RETAIN && retainOptions().isEmpty()) {
            state = MeleeCombatRules.finishRetention(state)
        }
    }

    private fun renderRetain(body: LinearLayout, bar: LinearLayout) {
        sideBySideFighters(body) { container, side ->
            container.addView(fighterHeader(side, false), ui.wrap(4))
            container.addView(diceChips(side, null), ui.wrap(8))
            val f = state.fighter(side)
            val options = retainOptions().filter { it.side == side }
            container.addView(weaponPanel(side) { k -> options.any { k.startsWith(it.keyword) } }, ui.wrap(6))
            options.forEach { option ->
                val choices = retainChoices.getValue(side)
                val on = when (option.key) {
                    "punishing" -> choices.punishing
                    "rending" -> choices.rending
                    else -> choices.severe
                }
                container.addView(WeaponRulesUi.choice(ui, kit, weaponOf(side).name, option.keyword, option.effect, on) {
                    retainChoices[side] = when (option.key) {
                        "punishing" -> choices.copy(punishing = !on)
                        "rending" -> choices.copy(rending = !on)
                        else -> choices.copy(severe = !on)
                    }
                    render()
                }, ui.wrap(6))
            }
            val result = MeleeCombatRules.previewRetention(state, side, retainChoices.getValue(side))
            val effects = buildList {
                add("将保留：${result.criticals} 暴击、${result.normals} 普通")
                if (result.criticals > 0 && f.rules.devastating > 0) add("毁灭 ${f.rules.devastating}：立即造成 ${result.criticals * f.rules.devastating} 伤")
                if (result.criticals > 0 && f.rules.stun) add("晕眩：对手 APL −1")
            }
            container.addView(ui.text(effects.joinToString(" · "), 13f, ui.ink, true), ui.wrap(if (isTablet) 0 else 14))
        }
        bar.addView(ui.text("可选规则只有点“应用”才会生效", 12f, ui.muted, true), ui.wrap(6))
        bar.addView(kit.primaryButton("开始结算") {
            if (rolling || state.phase != MeleePhase.RETAIN) return@primaryButton
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
        sideBySideFighters(body) { container, side ->
            val pool = state.pool(side)
            container.addView(ui.column().apply {
                setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
                val active = state.resolveTurn == side
                background = ui.bg(if (active) kit.sideFill(state.fighter(side).battleSideId) else 0xff161616.toInt(),
                    if (active) kit.sideColor(state.fighter(side).battleSideId) else 0xff2a2a2a.toInt())
                addView(fighterHeader(side, active, woundsEditable = true), ui.wrap(6))
                addView(weaponPanel(side) { k ->
                    val n = k.replace(" ", "")
                    (n.startsWith("残暴")) || (n.startsWith("震荡") && !shockUsed(side) && pool.criticals > 0)
                }, ui.wrap(8))
                addView(kit.diceRow(successChips(side), kit.sideColor(state.fighter(side).battleSideId), { index ->
                    val success = if (index < pool.criticals) MeleeSuccess.CRITICAL else MeleeSuccess.NORMAL
                    successSheet(side, success)
                }, size = 52), ui.wrap(0))
            }, ui.wrap(if (isTablet) 0 else 10))
        }
        if (state.fighter(state.resolveTurn.opponent()).rules.brutal) {
            body.addView(kit.tag("对方武器残暴：${state.fighter(state.resolveTurn).operativeName}只能用暴击格挡", ui.orange) {
                WeaponRulesUi.show(ui, weaponOf(state.resolveTurn.opponent()).name, listOf("残暴"))
            }, ui.wrap(8))
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
            val w = weaponOf(owner)
            val isDetonationWeapon = w.keywords.any { it.startsWith("爆破") }
            val damage = if (success == MeleeSuccess.CRITICAL) f.criticalDamage else f.normalDamage
            val shock = success == MeleeSuccess.CRITICAL && f.rules.shock && !shockUsed(owner) && opponentPool.successes > 0
            val shockText = if (!shock) "" else
                " + 震荡：舍弃对手 1 个${if (opponentPool.normals > 0) "普通成功" else "暴击"}"
            if (!isDetonationWeapon) {
                actions += CombatKit.SheetAction("出击 · 造成 $damage 伤$shockText", primary = true) {
                    resolve(owner, success, MeleeUse.STRIKE, null, 0, input.text.toString())
                }
                if (w.keywords.any { it.startsWith("粉碎") }) {
                    val opponent = state.fighter(owner.opponent())
                    actions += CombatKit.SheetAction("出击 · 自动掷【粉碎】对抗骰（最多 +3 伤）") {
                        val ownRoll = (1..6).random()
                        val oppRoll = (1..6).random()
                        val bonusMod = if (opponent.maxWounds <= 9) 1 else 0
                        val ownTotal = ownRoll + bonusMod
                        val crushBonus = (ownTotal - oppRoll).coerceIn(0, 3)
                        val crushNote = "粉碎对抗：己方掷 $ownRoll${if (bonusMod > 0) "+1(耐伤≤9)" else ""} vs 对方 $oppRoll，追加 $crushBonus 伤"
                        resolve(owner, success, MeleeUse.STRIKE, null, crushBonus, crushNote)
                    }
                }
                if (damage > 1) {
                    actions += CombatKit.SheetAction("出击（伤害 −1：造成 ${damage - 1} 伤 · 强化甲壳/奥术长袍/沉着冷静等）") {
                        resolve(owner, success, MeleeUse.STRIKE, null, -1, input.text.toString().ifBlank { "规则减伤 1" })
                    }
                }
                actions += CombatKit.SheetAction("出击（伤害 +1：造成 ${damage + 1} 伤 · 为了卡迪亚!/粉碎等）") {
                    resolve(owner, success, MeleeUse.STRIKE, null, 1, input.text.toString().ifBlank { "规则增伤 1" })
                }
            } else {
                val label = if (success == MeleeSuccess.CRITICAL) "2D6+6" else "D6+6"
                actions += CombatKit.SheetAction("出击（爆破 $label 伤，分别为目标与自身掷骰并结束近战）", primary = true) {
                    fun rollDetonation(): Int = if (success == MeleeSuccess.CRITICAL) {
                        kotlin.random.Random.nextInt(1, 7) + kotlin.random.Random.nextInt(1, 7) + 6
                    } else {
                        kotlin.random.Random.nextInt(1, 7) + 6
                    }
                    val targetRoll = rollDetonation()
                    val selfRoll = rollDetonation()
                    pushUndo()
                    detonationSide = owner
                    val afterStrike = MeleeCombatRules.resolve(
                        state, owner, success, MeleeUse.STRIKE, null,
                        targetRoll - damage, "爆破（目标 $label = $targetRoll）"
                    )
                    val selfAfter = MeleeCombatRules.adjustWounds(afterStrike, owner, -selfRoll, "爆破波及自身（$label = $selfRoll）")
                    state = if (selfAfter.phase == MeleePhase.RESOLVE) {
                        selfAfter.copy(
                            phase = MeleePhase.FINISHED,
                            finishReason = "爆破结算后行动立即结束",
                            log = selfAfter.log + "爆破结算后行动立即结束"
                        )
                    } else selfAfter
                    render()
                }
            }
            val ownSide = battle.side(f.battleSideId)
            val ownOp = ownSide.operatives.firstOrNull { it.memberId == f.memberId }
            val ownAbilities = ownOp?.let { AbilityCatalog.forOperative(it.operativeId) }.orEmpty()
            val ownMods = setup.getValue(owner).modifiers
            val hasDoubleParry = w.name == "简易护盾" ||
                w.keywords.any { it in setOf("盾牌", "护盾", "镇压", "挥打", "缠绕") } ||
                ownMods.extraKeywords.any { it.contains("缠绕") } ||
                battle.progress.usedPlans.any { it.startsWith("${ownSide.id}/") && it.contains("织网缠绕") }
            val hasSurvivalInstincts = ownSide.teamId == "KT-RATLINGS" &&
                ownOp?.operativeId !in setOf("ratlings_03", "ratlings_04") &&
                (owner == MeleeSide.DEFENDER || battle.progress.usedPlans.any { it.startsWith("${ownSide.id}/") && it.contains("生存本能") })
            val hasCritDoubleParry = hasDoubleParry || hasSurvivalInstincts
            val critDoubleParryLabel = if (!hasDoubleParry && hasSurvivalInstincts) "生存本能" else "护盾/镇压"
            val canParry = MeleeCombatRules.canParryWith(state, owner, success)
            val opponentBrutal = state.fighter(owner.opponent()).rules.brutal
            if (!canParry && opponentBrutal && success == MeleeSuccess.NORMAL) {
                actions += CombatKit.SheetAction("格挡不可用：对方武器残暴，只能用暴击格挡", false) {}
            }
            if (opponentPool.successes == 0) {
                actions += CombatKit.SheetAction("格挡（对手已无成功）", canParry) { resolve(owner, success, MeleeUse.PARRY, null) }
            } else {
                actions += CombatKit.SheetAction("格挡对手 1 个普通成功", canParry && opponentPool.normals > 0) {
                    resolve(owner, success, MeleeUse.PARRY, MeleeSuccess.NORMAL)
                }
                val canParryCritWithNormal = hasSurvivalInstincts || ownAbilities.any {
                    it.name.contains("生存本能") || it.name.contains("决斗家") ||
                        it.description.contains("普通成功可以格挡暴击") || it.description.contains("普通成功格挡一个暴击")
                } || battle.progress.usedPlans.any {
                    it.startsWith("${ownSide.id}/") && (it.contains("决斗家") || it.contains("生存本能"))
                }
                if (success == MeleeSuccess.NORMAL && !opponentBrutal && opponentPool.criticals > 0 && canParryCritWithNormal) {
                    actions += CombatKit.SheetAction("格挡对手 1 个暴击（生存本能/决斗家）") {
                        pushUndo()
                        state = MeleeCombatRules.parryCriticalWithNormal(state, owner, input.text.toString().ifBlank { "生存本能/决斗家" })
                        render()
                    }
                }
                if (hasDoubleParry && canParry && opponentPool.normals >= 2) {
                    actions += CombatKit.SheetAction("格挡对手 2 个普通成功（护盾/镇压）") {
                        pushUndo()
                        val afterFirst = MeleeCombatRules.resolve(state, owner, success, MeleeUse.PARRY, MeleeSuccess.NORMAL)
                        state = if (afterFirst.phase == MeleePhase.RESOLVE && afterFirst.pool(owner.opponent()).normals > 0) {
                            MeleeCombatRules.discardSuccess(afterFirst, owner.opponent(), MeleeSuccess.NORMAL, "护盾/镇压额外格挡")
                        } else afterFirst
                        render()
                    }
                }
                if (success == MeleeSuccess.CRITICAL) {
                    actions += CombatKit.SheetAction("格挡对手 1 个暴击", canParry && opponentPool.criticals > 0) {
                        resolve(owner, success, MeleeUse.PARRY, MeleeSuccess.CRITICAL)
                    }
                    if (!hasDoubleParry && hasSurvivalInstincts && canParry && opponentPool.normals >= 2) {
                        actions += CombatKit.SheetAction("格挡对手 2 个普通成功（$critDoubleParryLabel）") {
                            pushUndo()
                            val afterFirst = MeleeCombatRules.resolve(state, owner, success, MeleeUse.PARRY, MeleeSuccess.NORMAL)
                            state = if (afterFirst.phase == MeleePhase.RESOLVE && afterFirst.pool(owner.opponent()).normals > 0) {
                                MeleeCombatRules.discardSuccess(afterFirst, owner.opponent(), MeleeSuccess.NORMAL, "${critDoubleParryLabel}额外格挡")
                            } else afterFirst
                            render()
                        }
                    }
                    if (hasCritDoubleParry && canParry && opponentPool.criticals >= 1 && opponentPool.normals >= 1) {
                        actions += CombatKit.SheetAction("格挡对手 1 个暴击 + 1 个普通成功（$critDoubleParryLabel）") {
                            pushUndo()
                            val afterFirst = MeleeCombatRules.resolve(state, owner, success, MeleeUse.PARRY, MeleeSuccess.CRITICAL)
                            state = if (afterFirst.phase == MeleePhase.RESOLVE && afterFirst.pool(owner.opponent()).normals > 0) {
                                MeleeCombatRules.discardSuccess(afterFirst, owner.opponent(), MeleeSuccess.NORMAL, "${critDoubleParryLabel}额外格挡")
                            } else afterFirst
                            render()
                        }
                    }
                    if (hasCritDoubleParry && canParry && opponentPool.criticals >= 2) {
                        actions += CombatKit.SheetAction("格挡对手 2 个暴击（$critDoubleParryLabel）") {
                            pushUndo()
                            val afterFirst = MeleeCombatRules.resolve(state, owner, success, MeleeUse.PARRY, MeleeSuccess.CRITICAL)
                            state = if (afterFirst.phase == MeleePhase.RESOLVE && afterFirst.pool(owner.opponent()).criticals > 0) {
                                MeleeCombatRules.discardSuccess(afterFirst, owner.opponent(), MeleeSuccess.CRITICAL, "${critDoubleParryLabel}额外格挡")
                            } else afterFirst
                            render()
                        }
                    }
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

    private fun shockUsed(side: MeleeSide) = if (side == MeleeSide.ATTACKER) state.attackerShockUsed else state.defenderShockUsed

    private fun resolve(
        side: MeleeSide,
        success: MeleeSuccess,
        use: MeleeUse,
        target: MeleeSuccess?,
        damageDelta: Int = 0,
        extraNote: String = ""
    ) {
        pushUndo()
        state = MeleeCombatRules.resolve(state, side, success, use, target, damageDelta, extraNote)
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
            val statusText = when {
                f.currentWounds > 0 -> "耐伤 $start → ${f.currentWounds}"
                side in frenzySides -> "触发【狂暴】（保留在场）"
                side in preventIncapSides -> "免于残废（保留 1 伤）"
                else -> "残废"
            }
            body.addView(ui.row().apply {
                minimumHeight = ui.dp(52)
                addView(ui.text(f.operativeName, 16f, kit.sideColor(f.battleSideId), true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.text(statusText, 18f,
                    if (f.currentWounds <= 0 && side !in frenzySides && side !in preventIncapSides) kit.danger else ui.ink, true).apply {
                    setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
                    background = ui.bg(0xff242424.toInt())
                    setOnClickListener { woundSheet(side) }
                })
            }, ui.wrap(6))
            if (f.currentWounds <= 0) {
                val bSide = battle.side(f.battleSideId)
                val op = bSide.operatives.firstOrNull { it.memberId == f.memberId }
                val canFrenzy = bSide.teamId == "CHAOS-FELL" && op != null && !op.frenzied
                body.addView(ui.row().apply {
                    if (canFrenzy) {
                        val on = side in frenzySides
                        addView(kit.tag(
                            if (on) "✓ 已触发【狂暴】（保留在场）" else "⚡ 触发【狂暴】（保留在场并获狂暴标记）",
                            if (on) ui.accent else ui.orange
                        ) {
                            if (on) frenzySides.remove(side) else { frenzySides.add(side); preventIncapSides.remove(side) }
                            render()
                        }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) })
                    }
                    val savedOn = side in preventIncapSides
                    addView(kit.tag(
                        if (savedOn) "✓ 免于残废（保留 1 伤）" else "💉 免于残废（保留 1 伤 · 医疗兵!/死里逃生等）",
                        if (savedOn) ui.accent else ui.ink
                    ) {
                        if (savedOn) preventIncapSides.remove(side) else { preventIncapSides.add(side); frenzySides.remove(side) }
                        render()
                    })
                }, ui.wrap(6))
            }
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
        fun update(
            slot: MeleeSide,
            f: MeleeFighter,
            stunned: Boolean,
            opponentWeapon: BattleWeaponSnapshot,
            startWounds: Int,
            critStrikesTaken: Int,
            normalStrikesTaken: Int
        ) {
            val side = next.side(f.battleSideId)
            val damageTaken = (startWounds - f.currentWounds).coerceAtLeast(0)
            val dealPoison = damageTaken > 0 && opponentWeapon.keywords.any { it.startsWith("毒素") } && side.teamId != "CHAOS-PM"
            val dealVenom = critStrikesTaken > 0 && opponentWeapon.keywords.any { it.startsWith("中毒") }
            next = BattleFlow.side(next, side.copy(operatives = side.operatives.map { o ->
                if (o.memberId != f.memberId) o else {
                    var updated = when {
                        slot in frenzySides && f.currentWounds <= 0 -> o.copy(
                            currentWounds = 1,
                            incapacitated = false,
                            order = "交战",
                            tokens = (o.tokens.filterNot { it.name == "狂暴" || it.name == "狂暴已受普攻" } +
                                OperativeToken(name = "狂暴", duration = TokenDuration.UNTIL_REMOVED, source = "狂暴（近战免于残废）")).toMutableList()
                        )
                        slot in preventIncapSides && f.currentWounds <= 0 ->
                            BattleRules.setWounds(o, 1).copy(incapacitated = false)
                        else -> BattleRules.setWounds(o, f.currentWounds)
                    }
                    if (o.frenzied && !updated.incapacitated && normalStrikesTaken > 0) {
                        updated = updated.copy(
                            tokens = (updated.tokens.filterNot { it.name == "狂暴已受普攻" } +
                                OperativeToken(
                                    name = "狂暴已受普攻",
                                    value = normalStrikesTaken,
                                    duration = TokenDuration.UNTIL_REMOVED,
                                    source = "狂暴"
                                )).toMutableList()
                        )
                    }
                    if (stunned && !updated.stunned && !updated.incapacitated) {
                        updated = updated.copy(tokens = (updated.tokens +
                            OperativeToken(name = OperativeToken.STUNNED, duration = TokenDuration.NEXT_ACTIVATION_END, source = opponentWeapon.name)).toMutableList())
                    }
                    if (dealPoison && !updated.incapacitated && updated.tokens.none { it.name == "毒素" }) {
                        updated = updated.copy(tokens = (updated.tokens +
                            OperativeToken(name = "毒素", duration = TokenDuration.BATTLE, source = opponentWeapon.name)).toMutableList())
                    }
                    if (dealVenom && !updated.incapacitated && updated.tokens.none { it.name == "中毒" }) {
                        updated = updated.copy(tokens = (updated.tokens +
                            OperativeToken(name = "中毒", duration = TokenDuration.BATTLE, source = opponentWeapon.name)).toMutableList())
                    }
                    updated
                }
            }))
        }
        update(MeleeSide.ATTACKER, state.attacker, state.attackerStunned, defenderWeapon, attackerBase.currentWounds, state.attackerCritStrikesTaken, state.attackerNormalStrikesTaken)
        update(MeleeSide.DEFENDER, state.defender, state.defenderStunned, attackerWeapon, defenderBase.currentWounds, state.defenderCritStrikesTaken, state.defenderNormalStrikesTaken)
        detonationSide?.let { dSide ->
            val wielder = state.fighter(dSide)
            val bSide = next.side(wielder.battleSideId)
            if (bSide.teamId == "KT-WRECKA_KREW") {
                val incaps = listOf(MeleeSide.ATTACKER to state.attacker, MeleeSide.DEFENDER to state.defender).count { (s, fighter) ->
                    fighter.currentWounds <= 0 && s !in frenzySides && s !in preventIncapSides
                }
                val wreckaGain = 1 + incaps
                next = BattleFlow.side(next, bSide.copy(counters = bSide.counters.map { c ->
                    if (c.name == "破坏点数") c.copy(value = c.value + wreckaGain) else c
                }))
            }
        }
        listOf(state.attacker.battleSideId to state.attackerCpSpent, state.defender.battleSideId to state.defenderCpSpent)
            .filter { it.second > 0 }
            .forEach { (sideId, cp) ->
                val side = next.side(sideId)
                next = BattleFlow.side(next, side.copy(commandPoints = (side.commandPoints - cp).coerceAtLeast(0)))
            }
        next = BattleRules.syncKills(next)
        val summary = buildString {
            append("近战：${state.attacker.operativeName}（${attackerWeapon.name}）⚔ ${state.defender.operativeName}（${defenderWeapon.name}），")
            intent.getStringExtra(BattleActivity.EXTRA_ACTION_REASON)?.takeIf { it.isNotBlank() }?.let { append("依据：$it，") }
            append("耐伤 ${state.attacker.currentWounds}/${state.attacker.maxWounds} 与 ${state.defender.currentWounds}/${state.defender.maxWounds}")
            when {
                MeleeSide.ATTACKER in frenzySides && state.attacker.currentWounds <= 0 -> append("，${state.attacker.operativeName}触发【狂暴】")
                MeleeSide.ATTACKER in preventIncapSides && state.attacker.currentWounds <= 0 -> append("，${state.attacker.operativeName}免于残废（保留1伤）")
                state.attacker.currentWounds <= 0 -> append("，${state.attacker.operativeName}残废")
            }
            when {
                MeleeSide.DEFENDER in frenzySides && state.defender.currentWounds <= 0 -> append("，${state.defender.operativeName}触发【狂暴】")
                MeleeSide.DEFENDER in preventIncapSides && state.defender.currentWounds <= 0 -> append("，${state.defender.operativeName}免于残废（保留1伤）")
                state.defender.currentWounds <= 0 -> append("，${state.defender.operativeName}残废")
            }
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
