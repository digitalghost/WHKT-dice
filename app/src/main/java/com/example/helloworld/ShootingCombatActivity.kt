package com.example.helloworld

import android.content.Intent
import android.content.res.Configuration
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
 * Shooting resolution (p15-16). The engine enforces the core rules; every step can be undone
 * (with a visible trace) and adjusted manually for team rules.
 */
class ShootingCombatActivity : AppCompatActivity() {
    private lateinit var ui: RecordUi
    private lateinit var kit: CombatKit
    private lateinit var store: BattleStore
    private lateinit var battle: BattleRecord
    private lateinit var state: ShootingCombatState
    private lateinit var root: LinearLayout
    private var attackTray: DiceTrayView? = null
    private var defenceTray: DiceTrayView? = null
    private lateinit var sound: DiceSoundPlayer
    private var combatScroll: ScrollView? = null
    private var actionBar: View? = null

    private lateinit var weapon: BattleWeaponSnapshot
    private lateinit var baseAttacker: ShootingAttacker
    private lateinit var baseDefender: ShootingDefender
    private var targetConcealed = false

    // READY settings: rebuilt into a fresh engine state whenever they change.
    private var cover = CoverChoice.NONE
    private var obscured = false
    private var vantage = 0
    private var smoke = false
    private var accurateChoice: Int? = null
    private var modifiers = CombatModifiers()

    private val undoStates = ArrayDeque<ShootingCombatState>()
    private val undoneTrace = mutableListOf<Pair<Int, String>>()
    private var retainChoices = ShootingRetainChoices(punishing = true, rending = true, severe = true)
    private var selectedDefence: ShootingSuccess? = null
    private var rolling = false
    private var committed = false

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = RecordUi(this)
        kit = CombatKit(ui)
        store = BattleStore(this)
        sound = DiceSoundPlayer(this)
        val loaded = intent.getStringExtra(EXTRA_BATTLE_ID)?.let(store::load)
        if (loaded == null || loaded.completed || !loadBase(loaded)) {
            finish()
            return
        }
        battle = loaded
        rebuild()
        setupBackNavigation()
        render()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun loadBase(loaded: BattleRecord): Boolean {
        val attackerSideId = intent.getStringExtra(EXTRA_ATTACKER_SIDE_ID) ?: return false
        val attackerMemberId = intent.getStringExtra(EXTRA_ATTACKER_MEMBER_ID) ?: return false
        val attackerWeaponId = intent.getStringExtra(EXTRA_ATTACKER_WEAPON_ID) ?: return false
        val defenderSideId = intent.getStringExtra(EXTRA_DEFENDER_SIDE_ID) ?: return false
        val defenderMemberId = intent.getStringExtra(EXTRA_DEFENDER_MEMBER_ID) ?: return false
        val attackerSide = loaded.side(attackerSideId)
        val defenderSide = loaded.side(defenderSideId)
        val attackerOperative = attackerSide.operatives.firstOrNull { it.memberId == attackerMemberId } ?: return false
        val defenderOperative = defenderSide.operatives.firstOrNull { it.memberId == defenderMemberId } ?: return false
        weapon = CombatWeapons.of(this, attackerSide, attackerOperative)
            .firstOrNull { it.id == attackerWeaponId && !it.isMelee } ?: return false
        val defenderTemplate = RosterCatalog.operative(
            RosterCatalog.team(defenderSide.teamId),
            defenderOperative.operativeId
        ) ?: return false
        targetConcealed = defenderOperative.order == BattleOperativeState.ORDER_CONCEAL
        baseAttacker = ShootingAttacker(
            battleSideId = attackerSide.id,
            memberId = attackerOperative.memberId,
            playerName = attackerSide.playerName,
            operativeName = attackerOperative.displayName,
            weaponName = weapon.name,
            maxWounds = attackerOperative.maxWounds,
            currentWounds = attackerOperative.currentWounds,
            attacks = weapon.attacks,
            baseHit = weapon.hit,
            normalDamage = weapon.normalDamage,
            criticalDamage = weapon.criticalDamage
        )
        baseDefender = ShootingDefender(
            battleSideId = defenderSide.id,
            memberId = defenderOperative.memberId,
            playerName = defenderSide.playerName,
            operativeName = defenderOperative.displayName,
            maxWounds = defenderOperative.maxWounds,
            currentWounds = defenderOperative.currentWounds,
            save = defenderTemplate.save.filter(Char::isDigit).toIntOrNull() ?: 6
        )
        return true
    }

    /** Vantage (p36) grants Accurate 1/2 only against an engaged target. */
    private fun vantageKeyword(): String? =
        if (vantage > 0 && !targetConcealed) "精准 $vantage" else null

    private fun rebuild() {
        val keywords = modifiers.keywords(weapon.keywords) + listOfNotNull(vantageKeyword())
        val rules = ShootingRuleParser.parse(keywords, weapon.critical)
        val attacks = (weapon.attacks + modifiers.attacks).coerceAtLeast(0)
        val accurate = minOf(attacks, rules.accurate)
        val retained = (accurateChoice ?: accurate).coerceIn(0, accurate)
        val attacker = baseAttacker.copy(
            attacks = attacks,
            baseHit = (weapon.hit + modifiers.hit).coerceIn(2, 6),
            normalDamage = (weapon.normalDamage + modifiers.normalDamage).coerceAtLeast(0),
            criticalDamage = (weapon.criticalDamage + modifiers.criticalDamage).coerceAtLeast(0),
            accurateRetained = retained,
            rules = rules
        )
        val defender = baseDefender.copy(save = (baseDefender.save + modifiers.save).coerceIn(2, 6))
        if (rules.saturate) cover = CoverChoice.NONE
        var next = ShootingCombatRules.start(attacker, defender)
        next = ShootingCombatRules.configureEnvironment(next, smoke, 3 + modifiers.defenceDice)
        state = ShootingCombatRules.configure(next, cover, obscured, retained)
    }

    // ---------------------------------------------------------------- layout

    private fun render() {
        val previousScrollY = combatScroll?.scrollY ?: 0
        root = ui.column().apply { setBackgroundColor(ui.base) }
        setContentView(root)
        root.addView(kit.topBar(
            title = "射击 · ${phaseLabel()}",
            subtitle = "${state.attacker.operativeName} → ${state.defender.operativeName}",
            undoLabel = undoLabel(),
            onBack = ::requestClose,
            onUndo = ::undoLast,
            trailing = kit.diceButton(ui) { DiceSheet(ui) }
        ))
        root.addView(ui.divider().apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, 0) })
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

        body.addView(matchup(), ui.wrap(10))
        when (state.phase) {
            ShootingPhase.READY -> renderReady(body, bar)
            ShootingPhase.ATTACK_REROLL -> renderAttackDice(body, bar)
            ShootingPhase.ATTACK_RETAIN -> renderRetain(body, bar)
            ShootingPhase.DEFENCE_READY -> renderDefenceReady(body, bar)
            ShootingPhase.DEFENCE_REROLL -> renderDefenceDice(body, bar)
            ShootingPhase.ALLOCATE -> renderAllocation(body, bar)
            ShootingPhase.DAMAGE -> renderDamage(body, bar)
            ShootingPhase.OVERHEAT -> renderOverheat(body, bar)
            ShootingPhase.FINISHED -> renderFinished(body, bar)
        }
        body.addView(ui.divider())
        body.addView(kit.logView(state.log, undoneTrace), ui.wrap(16))
        scroll.post { scroll.scrollTo(0, previousScrollY) }
    }

    private fun phaseLabel(): String = when (state.phase) {
        ShootingPhase.READY -> "准备"
        ShootingPhase.ATTACK_REROLL -> "攻击骰"
        ShootingPhase.ATTACK_RETAIN -> "保留攻击骰"
        ShootingPhase.DEFENCE_READY -> "防御骰"
        ShootingPhase.DEFENCE_REROLL -> "防御骰"
        ShootingPhase.ALLOCATE -> "分配防御"
        ShootingPhase.DAMAGE -> "伤害"
        ShootingPhase.OVERHEAT -> "过热"
        ShootingPhase.FINISHED -> "结果"
    }

    private fun matchup(): View = ui.column().apply {
        setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
        background = ui.bg(0xff191919.toInt(), 0xff343432.toInt())
        val a = state.attacker
        val d = state.defender
        addView(ui.row().apply {
            gravity = Gravity.TOP
            addView(ui.column().apply {
                addView(ui.text(a.operativeName, 15f, kit.sideColor(a.battleSideId), true).apply { maxLines = 1 }, ui.wrap(2))
                addView(ui.text("${a.weaponName} · ${a.attacks}A ${a.effectiveHit}+ ${a.normalDamage}/${a.criticalDamage}" +
                    if (a.wounded) " · 受创" else "", 13f, if (a.wounded) ui.orange else ui.ink, true), ui.wrap(0))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.text("→", 18f, ui.muted, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ui.dp(28), -2))
            addView(ui.column().apply {
                addView(ui.text(d.operativeName, 15f, kit.sideColor(d.battleSideId), true).apply {
                    maxLines = 1; gravity = Gravity.END
                }, ui.wrap(2))
                addView(ui.text("豁免 ${d.save}+ · 耐伤 ${d.currentWounds}/${d.maxWounds}" +
                    if (targetConcealed) " · 隐匿" else "", 13f, if (d.currentWounds > 0) ui.ink else kit.danger, true).apply {
                    gravity = Gravity.END
                }, ui.wrap(0))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }, ui.wrap(6))
        val keywords = modifiers.keywords(weapon.keywords) + listOfNotNull(vantageKeyword())
        val tags = mutableListOf<View>()
        keywords.forEach { k ->
            tags += kit.tag(k, if (k in modifiers.extraKeywords || k == vantageKeyword()) ui.accent else ui.ink) {
                WeaponRulesUi.show(ui, weapon.name, keywords)
            }
        }
        modifiers.summary().filterNot { it.startsWith("+") && it.drop(1) in modifiers.extraKeywords }.forEach {
            tags += kit.tag(it, ui.accent) { if (state.phase == ShootingPhase.READY) openModifiers() }
        }
        if (state.phase == ShootingPhase.READY) tags += kit.tag("＋ 修饰", ui.accent) { openModifiers() }
        if (tags.isNotEmpty()) addView(kit.tagRow(tags))
    }

    private fun trays(body: LinearLayout, showAttack: Boolean, showDefence: Boolean) {
        attackTray = null
        defenceTray = null
        val both = isTablet
        val holder = if (both) ui.row().apply { gravity = Gravity.TOP } else ui.column()
        val rolled = state.phase != ShootingPhase.READY && state.phase != ShootingPhase.DEFENCE_READY &&
            state.phase != ShootingPhase.OVERHEAT
        val height = ui.dp(if (both) (if (rolled) 150 else 220) else (if (rolled) 96 else 170))
        if (showAttack || both) {
            attackTray = tray(state.attackPool.dice.map { it.value }, state.attacker.battleSideId, "攻击骰")
            holder.addView(attackTray, if (both) LinearLayout.LayoutParams(0, height, 1f).apply { marginEnd = ui.dp(8) }
                else LinearLayout.LayoutParams(-1, height))
        }
        if (showDefence || both) {
            defenceTray = tray(state.defencePool.dice.map { it.value }, state.defender.battleSideId, "防御骰")
            holder.addView(defenceTray, if (both) LinearLayout.LayoutParams(0, height, 1f) else LinearLayout.LayoutParams(-1, height))
        }
        body.addView(holder, ui.wrap(8))
    }

    private fun tray(values: List<Int>, sideId: String, description: String) = DiceTrayView(this).apply {
        background = ui.bg(0xff101010.toInt(), kit.sideStroke(sideId))
        contentDescription = description
        setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
        restoreResults(values)
    }

    private fun attackChips(interactive: Boolean): View {
        val chips = state.attackPool.dice.map { die ->
            DieChip(die.value, reading = ShootingCombatRules.readAttackDie(state, die), rerolled = die.rerolled, edited = die.override != null)
        } + List(state.attacker.accurateRetained + state.attackPool.retainedNormals) { DieChip(null, reading = DieReading.NORMAL) } +
            List(state.attackPool.retainedCriticals) { DieChip(null, reading = DieReading.CRITICAL) }
        return kit.diceRow(chips, kit.sideColor(state.attacker.battleSideId),
            if (interactive) { index -> if (index < state.attackPool.dice.size) attackDieSheet(index) } else null)
    }

    private fun defenceChips(interactive: Boolean): View {
        val chips = state.defencePool.dice.map { die ->
            DieChip(die.value, reading = ShootingCombatRules.readDefenceDie(state, die), rerolled = die.rerolled, edited = die.override != null)
        } + List(state.defencePool.retainedNormals) { DieChip(null, reading = DieReading.NORMAL) } +
            List(state.defencePool.retainedCriticals) { DieChip(null, reading = DieReading.CRITICAL) }
        return kit.diceRow(chips, kit.sideColor(state.defender.battleSideId),
            if (interactive) { index -> if (index < state.defencePool.dice.size) defenceDieSheet(index) } else null)
    }

    // ---------------------------------------------------------------- READY

    private fun renderReady(body: LinearLayout, bar: LinearLayout) {
        val rules = state.attacker.rules
        body.addView(kit.segmented(
            "掩护",
            CoverChoice.entries.map { it.short },
            CoverChoice.entries.indexOf(cover),
            enabled = { !rules.saturate || it == 0 },
            reason = if (rules.saturate) "集中：不能保留掩护豁免" else null,
            info = { CoreRules.show(ui, "shooting") }
        ) { cover = CoverChoice.entries[it]; rebuild(); render() }, ui.wrap(12))
        body.addView(kit.segmented("遮挡", listOf("否", "是"), if (obscured) 1 else 0,
            info = { CoreRules.show(ui, "obscured") }) { obscured = it == 1; rebuild(); render() }, ui.wrap(12))
        body.addView(kit.segmented("制高点", listOf("无", "高 ≥2″", "高 ≥4″"), vantage,
            reason = if (targetConcealed && vantage > 0) "目标隐匿：不获得精准" else null,
            info = { CoreRules.show(ui, "vantage") }) { vantage = it; accurateChoice = null; rebuild(); render() }, ui.wrap(12))
        if (rules.piercing == 2 || rules.piercingCrits == 2) {
            body.addView(kit.segmented("目标在烟雾中", listOf("否", "是"), if (smoke) 1 else 0,
                info = { CoreRules.show(ui, "smoke") }) { smoke = it == 1; rebuild(); render() }, ui.wrap(12))
        }
        val accurate = minOf(state.attacker.attacks, rules.accurate)
        if (accurate > 0) {
            body.addView(kit.segmented("精准预留", (0..accurate).map { it.toString() }, state.attacker.accurateRetained,
                info = { WeaponRulesUi.show(ui, weapon.name, listOf("精准")) }) {
                accurateChoice = it; rebuild(); render()
            }, ui.wrap(12))
        }
        trays(body, showAttack = true, showDefence = false)
        val retained = state.attacker.accurateRetained
        bar.addView(kit.primaryButton(
            "掷 ${state.attacker.rolledDiceCount} 枚攻击骰" + if (retained > 0) " · 预留 $retained" else ""
        ) { rollAttack() })
    }

    private fun openModifiers() {
        kit.modifierSheet(ui, "修饰 · ${weapon.name}", weapon.keywords, modifiers, shooting = true) {
            modifiers = it
            accurateChoice = null
            rebuild()
            render()
        }
    }

    // ---------------------------------------------------------------- ATTACK

    private fun renderAttackDice(body: LinearLayout, bar: LinearLayout) {
        trays(body, showAttack = true, showDefence = false)
        attackTray?.setOnDieTapListener { attackDieSheet(it) }
        body.addView(attackChips(interactive = true), ui.wrap(6))
        body.addView(ui.row().apply {
            addView(ui.text(kit.tally(state.attackPool.dice.map { ShootingCombatRules.readAttackDie(state, it) },
                state.attacker.accurateRetained + state.attackPool.retainedNormals, state.attackPool.retainedCriticals), 13f, ui.ink, true),
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(kit.tag("＋ 预留", ui.accent) { addRetainedSheet(defence = false) })
        }, ui.wrap(4))
        bar.addView(kit.primaryButton("确认攻击骰") {
            pushUndo()
            state = ShootingCombatRules.finishAttackRerolls(state)
            autoRetain()
            render()
        })
    }

    private fun weaponRerollName(): String = when (state.attacker.rules.rerollPolicy) {
        ShootingRerollPolicy.BALANCED -> "平衡"
        ShootingRerollPolicy.CEASELESS -> "无休"
        ShootingRerollPolicy.RELENTLESS -> "毫不留情"
        ShootingRerollPolicy.NONE -> "武器规则"
    }

    private fun sourceInput(): EditText = ui.input("", "来源（选填）：计谋、能力或装备名")

    private fun attackDieSheet(index: Int) {
        if (rolling || state.phase != ShootingPhase.ATTACK_REROLL) return
        val die = state.attackPool.dice[index]
        val input = sourceInput()
        val cpLeft = battle.side(state.attacker.battleSideId).commandPoints - state.attackCpSpent
        val actions = mutableListOf<CombatKit.SheetAction>()
        if (state.attacker.rules.rerollPolicy != ShootingRerollPolicy.NONE) {
            actions += CombatKit.SheetAction("重投 · ${weaponRerollName()}",
                ShootingCombatRules.canRerollAttackWith(state, index, RerollSource.WEAPON), primary = true) {
                rerollAttack(index, RerollSource.WEAPON, "")
            }
        }
        actions += CombatKit.SheetAction("重投 · 指挥重掷（1CP，剩 $cpLeft）", !die.rerolled && cpLeft > 0) {
            rerollAttack(index, RerollSource.COMMAND, "")
        }
        actions += CombatKit.SheetAction("重投 · 其他规则", !die.rerolled) {
            rerollAttack(index, RerollSource.OTHER, input.text.toString())
        }
        actions += editActions(die.override, ShootingCombatRules.readAttackDie(state, die), input) { override, value ->
            pushUndo()
            state = ShootingCombatRules.editAttackDie(state, index, override, value, input.text.toString())
            render()
        }
        kit.actionSheet("攻击骰 ${die.value}${if (die.rerolled) " · 已重投" else ""}", input, actions)
    }

    private fun defenceDieSheet(index: Int) {
        if (rolling || state.phase != ShootingPhase.DEFENCE_REROLL) return
        val die = state.defencePool.dice[index]
        val input = sourceInput()
        val cpLeft = battle.side(state.defender.battleSideId).commandPoints - state.defenceCpSpent
        val actions = mutableListOf(
            CombatKit.SheetAction("重投 · 指挥重掷（1CP，剩 $cpLeft）", !die.rerolled && cpLeft > 0, primary = true) {
                rerollDefence(index, RerollSource.COMMAND, "")
            },
            CombatKit.SheetAction("重投 · 其他规则", !die.rerolled) {
                rerollDefence(index, RerollSource.OTHER, input.text.toString())
            }
        )
        actions += editActions(die.override, ShootingCombatRules.readDefenceDie(state, die), input) { override, value ->
            pushUndo()
            state = ShootingCombatRules.editDefenceDie(state, index, override, value, input.text.toString())
            render()
        }
        kit.actionSheet("防御骰 ${die.value}${if (die.rerolled) " · 已重投" else ""}", input, actions)
    }

    private fun editActions(
        current: DieOverride?,
        reading: DieReading,
        input: EditText,
        apply: (DieOverride?, Int?) -> Unit
    ): List<CombatKit.SheetAction> = buildList {
        DieOverride.entries.forEach { o ->
            if (o.name != reading.name) add(CombatKit.SheetAction(if (o == DieOverride.DISCARDED) "舍弃" else "改判为${o.label}") { apply(o, null) })
        }
        if (current != null) add(CombatKit.SheetAction("恢复按点数判定") { apply(null, null) })
        add(CombatKit.SheetAction("改点数…") { pickFace { value -> apply(current, value) } })
    }

    private fun pickFace(onPick: (Int) -> Unit) {
        ui.dialog("改为点数") { box, sheet ->
            box.addView(ui.row().apply {
                (1..6).forEach { value ->
                    addView(ui.button(value.toString(), true) { sheet.dismiss(); onPick(value) },
                        LinearLayout.LayoutParams(0, ui.dp(52), 1f).apply { marginEnd = ui.dp(4) })
                }
            })
        }
    }

    private fun addRetainedSheet(defence: Boolean) {
        val input = sourceInput()
        kit.actionSheet("预留成功（不掷骰）", input, listOf(
            CombatKit.SheetAction("预留 1 个普通成功") { addRetained(defence, false, input.text.toString()) },
            CombatKit.SheetAction("预留 1 个暴击") { addRetained(defence, true, input.text.toString()) }
        ))
    }

    private fun addRetained(defence: Boolean, critical: Boolean, note: String) {
        pushUndo()
        state = if (defence) ShootingCombatRules.addDefenceRetained(state, critical, note)
        else ShootingCombatRules.addAttackRetained(state, critical, note)
        render()
    }

    private fun rerollAttack(index: Int, source: RerollSource, note: String) {
        val tray = attackTray ?: return
        if (rolling) return
        rolling = true
        sound.play()
        tray.selectOnly(index)
        tray.rerollSelected { values ->
            rolling = false
            if (isFinishing || isDestroyed) return@rerollSelected
            pushUndo()
            state = ShootingCombatRules.rerollAttackWith(state, index, values.single(), source, note)
            render()
        }
    }

    private fun rerollDefence(index: Int, source: RerollSource, note: String) {
        val tray = defenceTray ?: return
        if (rolling) return
        rolling = true
        sound.play()
        tray.selectOnly(index)
        tray.rerollSelected { values ->
            rolling = false
            if (isFinishing || isDestroyed) return@rerollSelected
            pushUndo()
            state = ShootingCombatRules.rerollDefenceWith(state, index, values.single(), source, note)
            render()
        }
    }

    // ---------------------------------------------------------------- RETAIN

    private data class RetainOption(val key: String, val label: String)

    private fun retainOptions(): List<RetainOption> {
        val preview = ShootingCombatRules.retentionPreview(state)
        val rules = state.attacker.rules
        return buildList {
            if (rules.punishing && preview.naturalCriticals > 0 && preview.failures > 0) add(RetainOption("punishing", "重击：1 失败 → 普通"))
            if (rules.rending && preview.naturalCriticals > 0 && preview.rolledNormals > 0) add(RetainOption("rending", "撕裂：1 普通 → 暴击"))
            if (rules.severe && preview.naturalCriticals == 0 && preview.rolledNormals + preview.retainedNormals > 0) add(RetainOption("severe", "严重：1 普通 → 暴击"))
        }
    }

    /** Skip the retain step when no optional rule can trigger. */
    private fun autoRetain() {
        if (state.phase == ShootingPhase.ATTACK_RETAIN && retainOptions().isEmpty()) {
            state = ShootingCombatRules.finishAttackRetention(state)
        }
    }

    private fun renderRetain(body: LinearLayout, bar: LinearLayout) {
        body.addView(attackChips(interactive = false), ui.wrap(10))
        retainOptions().forEach { option ->
            val on = when (option.key) {
                "punishing" -> retainChoices.punishing
                "rending" -> retainChoices.rending
                else -> retainChoices.severe
            }
            body.addView(kit.segmented(option.label, listOf("不用", "使用"), if (on) 1 else 0,
                info = { WeaponRulesUi.show(ui, weapon.name, listOf(option.label.substringBefore("："))) }) { pick ->
                retainChoices = when (option.key) {
                    "punishing" -> retainChoices.copy(punishing = pick == 1)
                    "rending" -> retainChoices.copy(rending = pick == 1)
                    else -> retainChoices.copy(severe = pick == 1)
                }
                render()
            }, ui.wrap(10))
        }
        if (state.obscured) body.addView(kit.tag("遮挡：舍弃 1 个成功，暴击改为普通", ui.orange), ui.wrap(8))
        bar.addView(kit.primaryButton("保留攻击骰") {
            pushUndo()
            state = ShootingCombatRules.finishAttackRetention(state, retainChoices)
            render()
        })
    }

    // ---------------------------------------------------------------- DEFENCE

    private fun renderDefenceReady(body: LinearLayout, bar: LinearLayout) {
        body.addView(successSummary(), ui.wrap(10))
        val retained = state.defencePool.retainedNormals + state.defencePool.retainedCriticals
        val formula = buildString {
            append("防御骰 ${state.baseDefenceDice}")
            if (state.effectivePiercing > 0) append(" − 穿刺 ${state.effectivePiercing}")
            if (retained > 0) append(" − 掩护预留 $retained")
            append(" = 掷 ${state.defenceDiceToRoll} 枚")
        }
        body.addView(ui.text(formula, 15f, ui.ink, true), ui.wrap(10))
        trays(body, showAttack = false, showDefence = true)
        bar.addView(kit.primaryButton(
            if (state.defenceDiceToRoll > 0) "掷 ${state.defenceDiceToRoll} 枚防御骰" else "继续"
        ) { rollDefence() })
    }

    private fun successSummary(): View = ui.row().apply {
        addView(ui.text("攻击成功", 12f, ui.muted, true), LinearLayout.LayoutParams(ui.dp(72), -2))
        addView(kit.diceRow(
            List(state.attackPool.criticals) { DieChip(null, face = "暴", reading = DieReading.CRITICAL) } +
                List(state.attackPool.normals) { DieChip(null, face = "普", reading = DieReading.NORMAL) },
            kit.sideColor(state.attacker.battleSideId), size = 40
        ))
    }

    private fun renderDefenceDice(body: LinearLayout, bar: LinearLayout) {
        body.addView(successSummary(), ui.wrap(10))
        trays(body, showAttack = false, showDefence = true)
        defenceTray?.setOnDieTapListener { defenceDieSheet(it) }
        body.addView(defenceChips(interactive = true), ui.wrap(6))
        body.addView(ui.row().apply {
            addView(ui.text(kit.tally(state.defencePool.dice.map { ShootingCombatRules.readDefenceDie(state, it) },
                state.defencePool.retainedNormals, state.defencePool.retainedCriticals), 13f, ui.ink, true),
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(kit.tag("＋ 预留", ui.accent) { addRetainedSheet(defence = true) })
        }, ui.wrap(4))
        bar.addView(kit.primaryButton("确认防御骰") {
            pushUndo()
            state = ShootingCombatRules.finishDefenceRerolls(state)
            selectedDefence = null
            if (!ShootingCombatRules.hasLegalAllocation(state)) state = ShootingCombatRules.beginDamage(state)
            render()
        })
    }

    // ---------------------------------------------------------------- ALLOCATE

    private fun renderAllocation(body: LinearLayout, bar: LinearLayout) {
        val attack = state.attackPool
        val defence = state.defencePool
        val attackChips = List(attack.criticals) { DieChip(null, face = "暴${state.attacker.criticalDamage}", reading = DieReading.CRITICAL) } +
            List(attack.normals) { DieChip(null, face = "普${state.attacker.normalDamage}", reading = DieReading.NORMAL) }
        val defenceChips = List(defence.criticals) {
            DieChip(null, face = "暴", reading = DieReading.CRITICAL, highlight = selectedDefence == ShootingSuccess.CRITICAL)
        } + List(defence.normals) {
            DieChip(null, face = "普", reading = DieReading.NORMAL, highlight = selectedDefence == ShootingSuccess.NORMAL)
        }
        body.addView(ui.text("① 点防御成功  ② 点要抵挡的攻击", 13f, ui.muted, true), ui.wrap(8))
        body.addView(ui.text(state.defender.operativeName, 12f, kit.sideColor(state.defender.battleSideId), true), ui.wrap(4))
        body.addView(kit.diceRow(defenceChips, kit.sideColor(state.defender.battleSideId), { index ->
            selectedDefence = if (index < defence.criticals) ShootingSuccess.CRITICAL else ShootingSuccess.NORMAL
            render()
        }), ui.wrap(12))
        body.addView(ui.text(state.attacker.operativeName, 12f, kit.sideColor(state.attacker.battleSideId), true), ui.wrap(4))
        body.addView(kit.diceRow(attackChips, kit.sideColor(state.attacker.battleSideId), { index ->
            val target = if (index < attack.criticals) ShootingSuccess.CRITICAL else ShootingSuccess.NORMAL
            allocateSelected(target)
        }), ui.wrap(12))
        body.addView(ui.text("当前承伤 ${ShootingCombatRules.remainingDamage(state)}", 18f, ui.accent, true), ui.wrap(8))
        val plan = ShootingCombatRules.recommendedAllocations(state)
        if (plan.isNotEmpty()) {
            var preview = state
            plan.forEach { preview = ShootingCombatRules.allocate(preview, it) }
            body.addView(kit.tag("推荐分配 · 承伤 ${ShootingCombatRules.remainingDamage(preview)} · 套用", ui.accent) {
                pushUndo()
                plan.forEach { state = ShootingCombatRules.allocate(state, it) }
                selectedDefence = null
                render()
            }.apply { minHeight = ui.dp(40); gravity = Gravity.CENTER_VERTICAL }, ui.wrap(8))
        }
        val done = !ShootingCombatRules.hasLegalAllocation(state)
        val toDamage = {
            pushUndo()
            state = ShootingCombatRules.beginDamage(state, force = !done)
            render()
        }
        bar.addView(if (done) kit.primaryButton("结算伤害") { toDamage() }
            else kit.secondaryButton("提前结束分配（规则效果）") { toDamage() })
    }

    private fun allocateSelected(target: ShootingSuccess) {
        val from = selectedDefence ?: run { note("先点一个防御成功"); return }
        val allocation = when {
            from == ShootingSuccess.NORMAL && target == ShootingSuccess.NORMAL -> DefenceAllocation.NORMAL_TO_NORMAL
            from == ShootingSuccess.NORMAL -> DefenceAllocation.TWO_NORMALS_TO_CRITICAL
            target == ShootingSuccess.NORMAL -> DefenceAllocation.CRITICAL_TO_NORMAL
            else -> DefenceAllocation.CRITICAL_TO_CRITICAL
        }
        if (!ShootingCombatRules.canAllocate(state, allocation)) {
            note(if (allocation == DefenceAllocation.TWO_NORMALS_TO_CRITICAL) "需要 2 个普通成功才能抵挡暴击" else "不能这样分配")
            return
        }
        pushUndo()
        state = ShootingCombatRules.allocate(state, allocation)
        selectedDefence = null
        render()
    }

    // ---------------------------------------------------------------- DAMAGE

    private fun renderDamage(body: LinearLayout, bar: LinearLayout) {
        state.pendingHits.forEachIndexed { index, hit ->
            val crit = hit.kind == ShootingSuccess.CRITICAL
            body.addView(ui.row().apply {
                minimumHeight = ui.dp(52)
                setPadding(ui.dp(12), 0, ui.dp(12), 0)
                background = ui.bg(0xff1b1b1b.toInt(), if (crit) kit.gold else kit.sideStroke(state.attacker.battleSideId))
                addView(ui.text(if (crit) "暴击" else "普通", 14f, if (crit) kit.gold else ui.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
                if (hit.damage != hit.baseDamage) addView(ui.text("${hit.baseDamage} → ", 13f, ui.muted))
                addView(ui.text("${hit.damage} 伤", 18f, ui.accent, true))
                addView(ui.text("  ›", 18f, ui.muted))
                setOnClickListener { hitSheet(index) }
            }, ui.wrap(6))
        }
        val total = state.pendingHits.sumOf { it.damage }
        val after = (state.defender.currentWounds - total).coerceAtLeast(0)
        body.addView(ui.text("${state.defender.operativeName} 耐伤 ${state.defender.currentWounds} → $after" +
            if (after == 0) " · 残废" else "", 16f, if (after == 0) kit.danger else ui.ink, true), ui.wrap(6))
        bar.addView(kit.primaryButton("造成 $total 点伤害") {
            pushUndo()
            state = ShootingCombatRules.applyDamage(state)
            render()
        })
    }

    private fun hitSheet(index: Int) {
        val hit = state.pendingHits[index]
        val input = sourceInput()
        fun set(value: Int) {
            pushUndo()
            state = ShootingCombatRules.adjustHit(state, index, value, input.text.toString())
            render()
        }
        kit.actionSheet("调整这次伤害（当前 ${hit.damage}）", input, buildList {
            add(CombatKit.SheetAction("−1") { set(hit.damage - 1) })
            add(CombatKit.SheetAction("−2") { set(hit.damage - 2) })
            add(CombatKit.SheetAction("+1") { set(hit.damage + 1) })
            if (hit.kind == ShootingSuccess.CRITICAL) add(CombatKit.SheetAction("按普通伤害（${state.attacker.normalDamage}）") { set(state.attacker.normalDamage) })
            add(CombatKit.SheetAction("忽略（0）") { set(0) })
            add(CombatKit.SheetAction("改为指定值…") { pickFace { set(it) } })
            if (hit.damage != hit.baseDamage) add(CombatKit.SheetAction("恢复 ${hit.baseDamage}") { set(hit.baseDamage) })
        })
    }

    // ---------------------------------------------------------------- OVERHEAT / RESULT

    private fun renderOverheat(body: LinearLayout, bar: LinearLayout) {
        body.addView(ui.text("掷 1 枚 D6：小于 ${state.attacker.effectiveHit} 时，射手受到点数 ×2 的伤害", 14f, ui.ink, true), ui.wrap(8))
        body.addView(kit.infoButton { WeaponRulesUi.show(ui, weapon.name, listOf("过热")) })
        trays(body, showAttack = true, showDefence = false)
        bar.addView(kit.primaryButton("掷过热骰") { rollOverheat() })
    }

    private fun renderFinished(body: LinearLayout, bar: LinearLayout) {
        val d = state.defender
        val startWounds = baseDefender.currentWounds
        body.addView(ui.text(
            if (d.currentWounds <= 0) "${d.operativeName} 残废" else "${d.operativeName} 耐伤 $startWounds → ${d.currentWounds}",
            22f, if (d.currentWounds <= 0) kit.danger else ui.accent, true
        ), ui.wrap(8))
        val lines = buildList {
            add("武器伤害 ${state.weaponDamage}")
            if (state.devastatingDamage > 0) add("毁灭 ${state.devastatingDamage}")
            if (state.defenderStunned) add("晕眩：APL −1")
            if (state.overheatRoll != null) add("过热 ${state.overheatRoll} · 射手受 ${state.overheatDamage} 伤")
            val cp = state.attackCpSpent + state.defenceCpSpent
            if (cp > 0) add("指挥重掷 ${cp}CP")
        }
        body.addView(kit.tagRow(lines.map { kit.tag(it, ui.ink) }), ui.wrap(8))
        val multi = (modifiers.keywords(weapon.keywords)).any { it.startsWith("爆炸") || it.startsWith("洪流") }
        val done = kit.primaryButton("完成") { commitResult(nextTarget = false) }
        if (multi) bar.addView(kit.actionBar(done, kit.secondaryButton("射击下一目标") { commitResult(nextTarget = true) }))
        else bar.addView(done)
    }

    // ---------------------------------------------------------------- rolls

    private fun rollAttack() {
        if (rolling || state.phase != ShootingPhase.READY) return
        val tray = attackTray ?: return
        rolling = true
        sound.play()
        tray.roll(state.attacker.rolledDiceCount) { values ->
            rolling = false
            if (isFinishing || isDestroyed) return@roll
            pushUndo()
            state = ShootingCombatRules.acceptAttackRoll(state, values)
            autoRetain()
            render()
        }
    }

    private fun rollDefence() {
        if (rolling || state.phase != ShootingPhase.DEFENCE_READY) return
        val tray = defenceTray ?: return
        rolling = true
        if (state.defenceDiceToRoll > 0) sound.play()
        tray.roll(state.defenceDiceToRoll) { values ->
            rolling = false
            if (isFinishing || isDestroyed) return@roll
            pushUndo()
            state = ShootingCombatRules.acceptDefenceRoll(state, values)
            render()
        }
    }

    private fun rollOverheat() {
        if (rolling || state.phase != ShootingPhase.OVERHEAT) return
        val tray = attackTray ?: return
        rolling = true
        sound.play()
        tray.roll(1) { values ->
            rolling = false
            if (isFinishing || isDestroyed) return@roll
            pushUndo()
            state = ShootingCombatRules.resolveOverheat(state, values.single())
            render()
        }
    }

    // ---------------------------------------------------------------- undo

    private fun pushUndo() {
        undoStates.addLast(state)
    }

    private fun undoLabel(): String? {
        val previous = undoStates.peekLast() ?: return null
        return state.log.drop(previous.log.size).lastOrNull() ?: "上一步"
    }

    /** Undo keeps a struck-through trace of what was reverted (dice already seen stay visible). */
    private fun undoLast() {
        if (rolling) { note("请等待骰子停止"); return }
        val previous = undoStates.pollLast() ?: return
        undoneTrace += state.log.drop(previous.log.size).map { previous.log.size to it }
        state = previous
        selectedDefence = null
        render()
    }

    // ---------------------------------------------------------------- commit

    private fun commitResult(nextTarget: Boolean) {
        if (committed || state.phase != ShootingPhase.FINISHED) return
        val latest = store.load(battle.battleId)
        if (latest == null || latest.completed) {
            note("对局已不存在或已经结束，无法写回")
            return
        }
        var next: BattleRecord = latest
        val sameAction = intent.getBooleanExtra(EXTRA_SAME_ACTION, false)
        val active = next.progress.active
        if (!sameAction && active?.side == state.attacker.battleSideId && active.member == state.attacker.memberId) {
            runCatching { BattleFlow.action(next, "射击", 1, intent.getStringExtra(BattleActivity.EXTRA_ACTION_REASON).orEmpty()) }
                .onSuccess { next = it }
        }
        fun update(sideId: String, memberId: String, change: (BattleOperativeState) -> BattleOperativeState) {
            val side = next.side(sideId)
            next = BattleFlow.side(next, side.copy(operatives = side.operatives.map { if (it.memberId == memberId) change(it) else it }))
        }
        update(state.defender.battleSideId, state.defender.memberId) { o ->
            val wounded = BattleRules.setWounds(o, state.defender.currentWounds)
            if (state.defenderStunned && !wounded.stunned && !wounded.incapacitated) wounded.copy(tokens = wounded.tokens +
                OperativeToken(name = OperativeToken.STUNNED, duration = TokenDuration.NEXT_ACTIVATION_END, source = weapon.name))
            else wounded
        }
        if (state.overheatRoll != null) update(state.attacker.battleSideId, state.attacker.memberId) {
            BattleRules.setWounds(it, state.attacker.currentWounds)
        }
        listOf(state.attacker.battleSideId to state.attackCpSpent, state.defender.battleSideId to state.defenceCpSpent)
            .filter { it.second > 0 }
            .forEach { (sideId, cp) ->
                val side = next.side(sideId)
                next = BattleFlow.side(next, side.copy(commandPoints = (side.commandPoints - cp).coerceAtLeast(0)))
            }
        val message = buildString {
            append("射击：${state.attacker.operativeName}（${weapon.name}）→ ${state.defender.operativeName}，")
            append("伤害 ${state.weaponDamage + state.devastatingDamage}，耐伤 ${state.defender.currentWounds}/${state.defender.maxWounds}")
            if (state.defender.currentWounds <= 0) append("，残废")
            if (state.defenderStunned) append("，晕眩")
            if (state.overheatRoll != null) append("；过热 ${state.overheatRoll}，射手受 ${state.overheatDamage} 伤")
            if (state.attackCpSpent > 0) append("；${state.attacker.playerName}指挥重掷 ${state.attackCpSpent}CP")
            if (state.defenceCpSpent > 0) append("；${state.defender.playerName}指挥重掷 ${state.defenceCpSpent}CP")
            if (!modifiers.isEmpty) append("；修饰：${modifiers.summary().joinToString("、")}${noteSuffix(modifiers.source)}")
            append("\n过程：${state.log.joinToString("；")}")
            if (undoneTrace.isNotEmpty()) append("\n已撤回：${undoneTrace.joinToString("；") { it.second }}")
        }
        next = BattleFlow.event(next, message)
        battle = store.save(next)
        committed = true
        if (nextTarget) chooseNextTarget() else finish()
    }

    /** Blast / Torrent: shoot further targets as part of the same action, each with its own dice. */
    private fun chooseNextTarget() {
        val opponent = battle.side(state.defender.battleSideId)
        val targets = opponent.operatives.filter { !it.incapacitated && it.memberId != state.defender.memberId }
        if (targets.isEmpty()) { finish(); return }
        MaterialAlertDialogBuilder(this)
            .setTitle("下一目标")
            .setItems(targets.map { "${it.displayName} · 耐伤 ${it.currentWounds}/${it.maxWounds}" }.toTypedArray()) { _, which ->
                startActivity(Intent(intent).apply {
                    putExtra(EXTRA_DEFENDER_MEMBER_ID, targets[which].memberId)
                    putExtra(EXTRA_SAME_ACTION, true)
                })
                finish()
            }
            .setOnCancelListener { finish() }
            .setNegativeButton("结束") { _, _ -> finish() }
            .show()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = requestClose()
        })
    }

    private fun requestClose() {
        if (state.phase == ShootingPhase.READY || committed) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("放弃本次射击？")
            .setMessage("尚未写回的骰子与伤害会被丢弃。")
            .setNegativeButton("继续结算", null)
            .setPositiveButton("放弃") { _, _ -> finish() }
            .show()
    }

    private fun note(message: String) {
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).also { s -> actionBar?.let { s.anchorView = it } }.show()
    }

    override fun onDestroy() {
        if (::sound.isInitialized) sound.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BATTLE_ID = "shooting_battle_id"
        const val EXTRA_ATTACKER_SIDE_ID = "shooting_attacker_side_id"
        const val EXTRA_ATTACKER_MEMBER_ID = "shooting_attacker_member_id"
        const val EXTRA_ATTACKER_WEAPON_ID = "shooting_attacker_weapon_id"
        const val EXTRA_DEFENDER_SIDE_ID = "shooting_defender_side_id"
        const val EXTRA_DEFENDER_MEMBER_ID = "shooting_defender_member_id"
        const val EXTRA_SAME_ACTION = "shooting_same_action"
    }
}

/** Weapon snapshots for an operative; falls back to the roster template for legacy battles. */
object CombatWeapons {
    fun of(context: android.content.Context, side: BattleSide, operative: BattleOperativeState): List<BattleWeaponSnapshot> =
        operative.weapons.ifEmpty {
            val member = RosterStore(context).load(side.rosterId)?.members?.firstOrNull { it.id == operative.memberId }
            RosterCatalog.operative(RosterCatalog.team(side.teamId), operative.operativeId)?.weapons.orEmpty()
                .filter { member == null || it.id in member.weaponIds }
                .map { w ->
                    val stats = w.stats
                    BattleWeaponSnapshot(w.id, w.name, w.profile, stats.attacks, stats.hit, stats.criticalThreshold,
                        stats.normalDamage, stats.criticalDamage, stats.keywords, w.kind)
                }
        }
}
