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
    /** Members already shot in this action (Blast / Torrent), so each target is resolved once. */
    private var shotTargets: Set<String> = emptySet()
    /** Blast secondary targets inherit the primary target's cover and obscuring. */
    private var primaryCover: CoverChoice? = null
    private var primaryObscured = false

    // READY settings: rebuilt into a fresh engine state whenever they change.
    private var cover = CoverChoice.NONE
    private var obscured = false
    private var vantage = 0
    private var smoke = false
    private var ignoreSaturate = false
    private var accurateChoice: Int? = null
    private var modifiers = CombatModifiers()
    private var canUseKasrkinForegrip = false
    private var canUseKasrkinLongScope = false
    private var kasrkinForegrip = false
    private var kasrkinLongScope = false
    private var canUseShriekerToxin = false
    private var shriekerToxinUsedThisTp = false
    private var useShriekerToxin = false
    private var canUseTargetAnalysisOptic = false
    private var targetAnalysisOpticUsedThisTp = false
    private var useTargetAnalysisOptic = false
    private var targetMarkerlights = 0

    private val undoStates = ArrayDeque<ShootingCombatState>()
    private val undoneTrace = mutableListOf<Pair<Int, String>>()
    private var retainChoices = ShootingRetainChoices()
    private var presetNormals = 0
    private var presetCriticals = 0
    private var presetNote = ""
    private var selectedDefence: ShootingSuccess? = null
    private var rolling = false
    private var committed = false
    private var preventIncapacitation = false
    private var triggerFrenzy = false
    private var canTriggerFrenzy = false

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
        primaryCover?.let { cover = it; obscured = primaryObscured }
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
        shotTargets = intent.getStringArrayListExtra(EXTRA_SHOT_TARGETS).orEmpty().toSet()
        primaryCover = intent.getStringExtra(EXTRA_PRIMARY_COVER)?.let { runCatching { CoverChoice.valueOf(it) }.getOrNull() }
        primaryObscured = intent.getBooleanExtra(EXTRA_PRIMARY_OBSCURED, false)
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
        canTriggerFrenzy = defenderSide.teamId == "CHAOS-FELL" && defenderOperative.tokens.none { it.name == "狂暴" }
        triggerFrenzy = canTriggerFrenzy
        val reducePiercingWithoutCrit = defenderSide.teamId == "KT-VOID_DANCER_TROUPE" ||
            defenderOperative.operativeId == "exodite_dragon_masters_01"
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
        val defenderApl = BattleFlow.effectiveApl(
            defenderOperative.copy(apl = defenderTemplate.apl)
        ).coerceAtLeast(1)
        baseDefender = ShootingDefender(
            battleSideId = defenderSide.id,
            memberId = defenderOperative.memberId,
            playerName = defenderSide.playerName,
            operativeName = defenderOperative.displayName,
            maxWounds = defenderOperative.maxWounds,
            currentWounds = defenderOperative.currentWounds,
            save = defenderTemplate.save.filter(Char::isDigit).toIntOrNull() ?: 6,
            reducePiercingWithoutCrit = reducePiercingWithoutCrit,
            apl = defenderApl,
            frenzied = defenderOperative.frenzied
        )
        applyAutomaticCombatModifiers(loaded, attackerSide, attackerOperative, defenderSide, defenderOperative)
        return true
    }

    private fun applyAutomaticCombatModifiers(
        loaded: BattleRecord,
        attackerSide: BattleSide,
        attackerOperative: BattleOperativeState,
        defenderSide: BattleSide,
        defenderOperative: BattleOperativeState
    ) {
        val autoKeywords = mutableListOf<String>()
        val autoSources = mutableListOf<String>()
        var hitDelta = 0
        var normalDelta = 0
        var critDelta = 0

        // 1. CHAOS-PM 剧毒: +1/+1 damage if target has 毒素 token
        if (weapon.keywords.any { it.startsWith("剧毒") } && defenderOperative.tokens.any { it.name == "毒素" }) {
            normalDelta += 1
            critDelta += 1
            autoSources += "剧毒"
        }
        // 2. KT-PATHFINDERS 标记光 cumulative bonuses (2026-08 errata, excluding 融合手雷)
        if (attackerSide.teamId == "KT-PATHFINDERS" && !weapon.name.contains("融合手雷")) {
            val ml = defenderOperative.tokens.filter { it.name.startsWith("标记光") }.sumOf { it.value ?: 1 }
            targetMarkerlights = ml
            val isDrone = AbilityCatalog.forOperative(attackerOperative.operativeId).any { it.name == "无人机" }
            canUseTargetAnalysisOptic = attackerSide.equipmentNotes.contains("目标分析镜") && !isDrone && ml >= 1
            targetAnalysisOpticUsedThisTp = "${attackerSide.id}/交战:目标分析镜" in loaded.progress.usedPlans
            if (ml >= 1) {
                autoKeywords += "集中"
                autoKeywords += "平衡"
                autoSources += "标记光×$ml"
            }
            if (ml >= 2 && weapon.hit > 3) {
                hitDelta -= 1
            }
            if (ml >= 3) {
                obscured = false
            }
            if (ml >= 4) {
                autoKeywords += "追踪轻型"
            }
            if (ml >= 5) {
                autoKeywords += "追踪"
            }
        }
        if (attackerSide.teamId == "KT-KASRKIN") {
            val isDatacardNonPistol = !weapon.name.contains("手枪") && weapon.name !in setOf("破片手雷", "穿甲手雷")
            val isHotshot = weapon.name.contains("高能")
            canUseKasrkinForegrip = attackerSide.equipmentNotes.contains("前握把") && isDatacardNonPistol
            canUseKasrkinLongScope = attackerSide.equipmentNotes.contains("长距离瞄具") && isHotshot
        }
        if (attackerSide.teamId == "KT-VOID_DANCER_TROUPE") {
            canUseShriekerToxin = attackerSide.equipmentNotes.contains("尖啸者毒素弹") &&
                (weapon.name == "星镖手枪" || weapon.name == "尖啸者加农炮（集中）")
            shriekerToxinUsedThisTp = "${attackerSide.id}/交战:尖啸者毒素弹" in loaded.progress.usedPlans
        }
        // 3. Active Strategic Ploys on attacker side
        val activePlans = loaded.progress.usedPlans
            .filter { it.startsWith("${attackerSide.id}/") && !it.startsWith("${attackerSide.id}/交战:") }
            .map { it.substringAfter("/") }
        // 2b. KT-XV26_STEALTH_BATTLESUITS 标记光 / 标记光聚焦
        if (attackerSide.teamId == "KT-XV26_STEALTH_BATTLESUITS") {
            if (defenderOperative.tokens.any { it.name == "标记光" || it.name == "被标记" }) {
                autoKeywords += "严重"
                autoSources += "标记光"
            }
            if (activePlans.any { it.contains("标记光聚焦") } ||
                defenderOperative.tokens.any { it.name == "标记光聚焦" || it.source.contains("标记光聚焦") }
            ) {
                hitDelta -= 1
                autoSources += "标记光聚焦"
            }
        }
        if (attackerSide.teamId == "KT-SANCTIFIERS" &&
            defenderOperative.tokens.any { it.name == "浸湿" } &&
            weapon.keywords.any { it.contains("烈焰") }
        ) {
            autoKeywords += "追踪"
            autoSources += "圣珠（浸湿）"
        }
        if (activePlans.any { it.startsWith("突！突！突！") }) {
            autoKeywords += "重击"
            autoSources += "突！突！突！"
        }
        if (activePlans.any { it.startsWith("叠加火力") }) {
            autoKeywords += "无休"
            autoSources += "叠加火力"
        }
        if (activePlans.any { it.contains("集火目标") }) {
            autoKeywords += "严重"
            autoSources += "熟练技巧：集火目标"
        }
        if (activePlans.any { it.contains("守护者条令") || it.contains("保护者教条") }) {
            autoKeywords += "无休"
            autoSources += "守护者条令"
        }
        if (activePlans.any { it.contains("征服者条令") || it.contains("征服者教条") }) {
            hitDelta += 1
            autoSources += "征服者条令（远程劣化）"
        }
        if (activePlans.any { it.contains("冰雹齐射") || it.contains("肃清扫荡") }) {
            autoKeywords += "无休"
            autoSources += "战略计划"
        }
        if (activePlans.any { it.contains("瞄准！") } && weapon.name != "迫击炮轰炸" && weapon.name != "远程引爆器") {
            autoKeywords += "无休"
            autoSources += "守卫命令：瞄准！"
        }
        if (activePlans.any { it.contains("屏息凝神") }) {
            autoKeywords += "精准 1"
            autoSources += "战略计谋"
        }
        if (activePlans.any { it.contains("精准（暴击改善至5+）") }) {
            autoKeywords += "致命 5+"
            autoSources += "技能点：精准"
        }
        if (activePlans.any { it.contains("毁灭条令") || it.contains("战术条令") || it.contains("情节剧") || it.contains("通俗剧") }) {
            autoKeywords += "平衡"
            autoSources += "战略计谋"
        }
        if (activePlans.any { it.startsWith("他们无所畏惧") || it.startsWith("痛苦之乐") } && attackerOperative.wounded) {
            hitDelta -= 1
            autoSources += "无视受创"
        }
        if (AbilityCatalog.forOperative(defenderOperative.operativeId).any {
                it.description.contains("忽略“集中”") || it.description.contains("无视“集中”") ||
                    it.description.contains("忽略饱和") || it.description.contains("无视饱和")
            }
        ) {
            ignoreSaturate = true
        }
        if (autoKeywords.isNotEmpty() || hitDelta != 0 || normalDelta != 0 || critDelta != 0) {
            modifiers = modifiers.copy(
                hit = hitDelta,
                normalDamage = normalDelta,
                criticalDamage = critDelta,
                extraKeywords = (modifiers.extraKeywords + autoKeywords).distinct(),
                source = autoSources.distinct().joinToString("、")
            )
        }
    }

    /** Vantage (p36) grants Accurate 1/2 only against an engaged target. */
    private fun vantageKeyword(): String? =
        if (vantage > 0 && !targetConcealed) "精准 $vantage" else null

    private fun rebuild() {
        val dynamicKeywords = mutableListOf<String>()
        if (kasrkinForegrip) dynamicKeywords += "精准 1"
        if (kasrkinLongScope) dynamicKeywords += "集中"
        if (useShriekerToxin) dynamicKeywords += "毁灭 1"
        var opticHitBonus = 0
        if (useTargetAnalysisOptic && targetMarkerlights >= 1) {
            val effMl = targetMarkerlights + 1
            if (effMl == 2 && weapon.hit > 3) opticHitBonus = -1
            if (effMl >= 3) obscured = false
            if (effMl >= 4) dynamicKeywords += "追踪轻型"
            if (effMl >= 5) dynamicKeywords += "追踪"
        }
        val keywords = modifiers.keywords(weapon.keywords) + dynamicKeywords + listOfNotNull(vantageKeyword())
        val rawRules = ShootingRuleParser.parse(keywords, weapon.critical)
        val rules = if (ignoreSaturate) rawRules.copy(saturate = false) else rawRules
        val attacks = (weapon.attacks + modifiers.attacks).coerceAtLeast(0)
        val accurate = minOf(attacks, rules.accurate)
        val retained = (accurateChoice ?: accurate).coerceIn(0, accurate)
        val attacker = baseAttacker.copy(
            attacks = attacks,
            baseHit = (weapon.hit + modifiers.hit + opticHitBonus).coerceIn(2, 6),
            normalDamage = (weapon.normalDamage + modifiers.normalDamage).coerceAtLeast(0),
            criticalDamage = (weapon.criticalDamage + modifiers.criticalDamage).coerceAtLeast(0),
            accurateRetained = retained,
            rules = rules,
            presetNormals = presetNormals,
            presetCriticals = presetCriticals,
            presetNote = presetNote
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
                addView(ui.text(a.operativeName, 15f, kit.sideColor(a.battleSideId), true).apply {
                    maxLines = 1
                    contentDescription = "查看${a.operativeName}人物卡"
                    setOnClickListener {
                        val (s, o) = operativeOf(a.battleSideId, a.memberId)
                        BattleReference(ui).operativeCard(s.teamId, o)
                    }
                }, ui.wrap(2))
                addView(ui.text("${a.weaponName} · ${a.attacks}A ${a.effectiveHit}+ ${a.normalDamage}/${a.criticalDamage}", 13f, ui.ink, true), ui.wrap(1))
                addView(ui.text(attackerStats(), 12f, 0xffc9c7c2.toInt(), true), ui.wrap(0))
                if (a.wounded) addView(kit.tag("受创：命中 ${a.baseHit}+ → ${(a.baseHit + 1).coerceAtMost(6)}+（移动 −2″）", ui.orange) {
                    CoreRules.show(ui, "wounds")
                }, ui.wrap(0))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.text("→", 18f, ui.muted, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ui.dp(28), -2))
            addView(ui.column().apply {
                addView(ui.text(d.operativeName, 15f, kit.sideColor(d.battleSideId), true).apply {
                    maxLines = 1; gravity = Gravity.END
                    contentDescription = "查看${d.operativeName}人物卡"
                    setOnClickListener {
                        val (s, o) = operativeOf(d.battleSideId, d.memberId)
                        BattleReference(ui).operativeCard(s.teamId, o)
                    }
                }, ui.wrap(2))
                addView(ui.text(defenderStats() + if (targetConcealed) " · 隐匿" else "", 13f, if (d.currentWounds > 0) ui.ink else kit.danger, true).apply {
                    gravity = Gravity.END
                }, ui.wrap(0))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }, ui.wrap(6))
        val keywords = modifiers.keywords(weapon.keywords) + listOfNotNull(vantageKeyword())
        val tags = mutableListOf<View>()
        keywords.forEach { k ->
            val live = activeKeyword(k)
            val attackerTeamId = battle.side(state.attacker.battleSideId).teamId
            tags += kit.tag(if (live) "● $k" else k, if (live || k in modifiers.extraKeywords || k == vantageKeyword()) ui.accent else ui.ink) {
                WeaponRulesUi.show(ui, weapon.name, listOf(k), keywords, teamId = attackerTeamId)
            }.apply { minHeight = ui.dp(32); gravity = Gravity.CENTER_VERTICAL; textSize = 12f }
        }
        modifiers.summary().filterNot { it.startsWith("+") && it.drop(1) in modifiers.extraKeywords }.forEach {
            tags += kit.tag(it, ui.accent) { if (state.phase == ShootingPhase.READY) openModifiers() }
        }
        if (state.phase == ShootingPhase.READY) tags += kit.tag("＋ 修饰", ui.accent) { openModifiers() }
        if (tags.isNotEmpty()) addView(kit.tagRow(tags))
    }

    private fun operativeOf(sideId: String, memberId: String) =
        battle.side(sideId).let { side -> side to side.operatives.first { it.memberId == memberId } }

    private fun attackerStats(): String = operativeOf(state.attacker.battleSideId, state.attacker.memberId)
        .let { (side, o) -> OperativeStats.line(this, side, o, state.attacker.currentWounds) }

    /** Save shown is the one used for this roll (modifiers applied). */
    private fun defenderStats(): String = operativeOf(state.defender.battleSideId, state.defender.memberId)
        .let { (side, o) -> OperativeStats.line(this, side, o, state.defender.currentWounds) }
        .replace(Regex("豁免 \\d\\+"), "豁免 ${state.defender.save}+")

    /** Weapon rules that take effect in the current step, highlighted in the matchup card. */
    private fun activeKeyword(keyword: String): Boolean {
        val k = keyword.replace(" ", "").replace(Regex("^[0-9]+[\"″“”]"), "")
        val r = state.attacker.rules
        return when (state.phase) {
            ShootingPhase.READY -> k.startsWith("精准") || k.startsWith("集中") || k.startsWith("追踪") || k.startsWith("范围")
            ShootingPhase.ATTACK_REROLL -> k.startsWith("致命") || ((k.startsWith("平衡") || k.startsWith("均衡") ||
                k.startsWith("无休") || k.startsWith("毫不留情")) && ShootingCombatRules.hasAnyAttackReroll(state))
            ShootingPhase.ATTACK_RETAIN -> retainOptions().any { k.startsWith(it.keyword) } ||
                k.startsWith("毁灭") || k.startsWith("晕眩") || k.startsWith("眩晕")
            ShootingPhase.DEFENCE_READY -> (k.startsWith("穿刺") || k.startsWith("关键穿刺")) && state.effectivePiercing > 0 ||
                k.startsWith("集中")
            ShootingPhase.ALLOCATE -> k.startsWith("残暴") && r.brutal
            ShootingPhase.OVERHEAT -> k.startsWith("过热")
            ShootingPhase.FINISHED -> k.startsWith("爆炸") || k.startsWith("洪流")
            else -> false
        }
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
        val inheritedBlast = primaryCover != null
        body.addView(kit.segmented(
            "掩护",
            CoverChoice.entries.map { it.short },
            CoverChoice.entries.indexOf(cover),
            enabled = { !inheritedBlast && (!rules.saturate || it == 0) },
            reason = when {
                inheritedBlast -> "爆炸次要目标：沿用主要目标的掩护与遮挡"
                rules.saturate -> "集中：不能保留掩护豁免"
                else -> null
            },
            info = { CoreRules.show(ui, "shooting") }
        ) { cover = CoverChoice.entries[it]; rebuild(); render() }, ui.wrap(12))
        if (ShootingRuleParser.parse(modifiers.keywords(weapon.keywords), weapon.critical).saturate) {
            body.addView(kit.segmented(
                "迷彩斗篷 / 忽略「集中」",
                listOf("否", "是（允许保留并升级掩护）"),
                if (ignoreSaturate) 1 else 0
            ) { ignoreSaturate = it == 1; rebuild(); render() }, ui.wrap(12))
        }
        body.addView(kit.segmented("遮挡", listOf("否", "是"), if (obscured) 1 else 0,
            enabled = { !inheritedBlast },
            reason = if (inheritedBlast) "爆炸次要目标：沿用主要目标的掩护与遮挡" else null,
            info = { CoreRules.show(ui, "obscured") }) { obscured = it == 1; rebuild(); render() }, ui.wrap(12))
        body.addView(kit.segmented("制高点", listOf("无", "高 ≥2″", "高 ≥4″"), vantage,
            reason = if (targetConcealed && vantage > 0) "目标隐匿：不获得精准" else null,
            info = { CoreRules.show(ui, "vantage") }) { vantage = it; accurateChoice = null; rebuild(); render() }, ui.wrap(12))
        if (rules.piercing == 2 || rules.piercingCrits == 2) {
            body.addView(kit.segmented("目标在烟雾中", listOf("否", "是"), if (smoke) 1 else 0,
                info = { CoreRules.show(ui, "smoke") }) { smoke = it == 1; rebuild(); render() }, ui.wrap(12))
        }
        if (canUseKasrkinForegrip) {
            body.addView(kit.segmented("前握把（目标 ≤3″：精准 1）", listOf("否", "是（≤3″）"), if (kasrkinForegrip) 1 else 0) {
                kasrkinForegrip = it == 1
                if (kasrkinForegrip) kasrkinLongScope = false
                accurateChoice = null
                rebuild(); render()
            }, ui.wrap(12))
        }
        if (canUseKasrkinLongScope) {
            body.addView(kit.segmented("长距离瞄具（目标 >6″：集中）", listOf("否", "是（>6″）"), if (kasrkinLongScope) 1 else 0) {
                kasrkinLongScope = it == 1
                if (kasrkinLongScope) kasrkinForegrip = false
                accurateChoice = null
                rebuild(); render()
            }, ui.wrap(12))
        }
        if (canUseShriekerToxin) {
            body.addView(kit.segmented(
                "尖啸者毒素弹（每转折点限 1 次：毁灭 1）",
                listOf("不使用", "使用"),
                if (useShriekerToxin) 1 else 0,
                enabled = { it == 0 || !shriekerToxinUsedThisTp },
                reason = if (shriekerToxinUsedThisTp) "本转折点已使用过尖啸者毒素弹" else null
            ) { useShriekerToxin = it == 1; rebuild(); render() }, ui.wrap(12))
        }
        if (canUseTargetAnalysisOptic) {
            body.addView(kit.segmented(
                "目标分析镜（每转折点限 1 次：标记光视为 +1）",
                listOf("不使用", "使用"),
                if (useTargetAnalysisOptic) 1 else 0,
                enabled = { it == 0 || !targetAnalysisOpticUsedThisTp },
                reason = if (targetAnalysisOpticUsedThisTp) "本转折点已使用过目标分析镜" else null
            ) { useTargetAnalysisOptic = it == 1; rebuild(); render() }, ui.wrap(12))
        }
        val accurate = minOf(state.attacker.attacks, rules.accurate)
        if (accurate > 0) {
            body.addView(kit.segmented("精准预留", (0..accurate).map { it.toString() }, state.attacker.accurateRetained,
                info = { WeaponRulesUi.show(ui, weapon.name, listOf("精准")) }) {
                accurateChoice = it; rebuild(); render()
            }, ui.wrap(12))
        }
        body.addView(ui.row().apply {
            val preset = presetNormals + presetCriticals
            addView(ui.text(
                if (preset == 0) "其他规则可不掷骰预留成功" else
                    "不掷骰预留 $presetNormals 普通 $presetCriticals 暴击${noteSuffix(presetNote)}",
                12f, if (preset > 0) ui.accent else ui.muted, true
            ), LinearLayout.LayoutParams(0, -2, 1f))
            if (preset > 0) addView(kit.tag("清除", ui.muted) {
                presetNormals = 0; presetCriticals = 0; presetNote = ""; rebuild(); render()
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) })
            addView(kit.tag("＋ 不掷骰预留", ui.accent) { attackPresetSheet() })
        }, ui.wrap(12))
        trays(body, showAttack = true, showDefence = false)
        val retained = state.attacker.accurateRetained + presetNormals + presetCriticals
        bar.addView(kit.primaryButton(
            "掷 ${state.attacker.rolledDiceCount} 枚攻击骰" + if (retained > 0) " · 预留 $retained" else ""
        ) { rollAttack() })
    }

    /** Retaining without rolling happens before the roll and takes dice out of the pool (p15, p92). */
    private fun attackPresetSheet() {
        if (state.phase != ShootingPhase.READY) return
        val input = ui.input(presetNote, "依据（建议填写）：计谋、能力或装备名")
        fun add(critical: Boolean) {
            if (state.attacker.rolledDiceCount <= 0) { note("已经没有可预留的攻击骰"); return }
            if (critical) presetCriticals++ else presetNormals++
            presetNote = input.text.toString().trim()
            rebuild(); render()
        }
        val header = ui.column().apply {
            addView(ui.text("规则允许“不进行掷骰而保留”时使用：预留的骰子直接算作成功，不再掷出，也不能重投。精准请用上方的精准预留。",
                12f, ui.muted), ui.wrap(8))
            addView(input)
        }
        val left = state.attacker.rolledDiceCount
        kit.actionSheet("不掷骰预留攻击骰（剩 $left 枚待掷）", header, listOf(
            CombatKit.SheetAction("预留 1 个普通成功", left > 0) { add(false) },
            CombatKit.SheetAction("预留 1 个暴击", left > 0) { add(true) }
        ))
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
        }, ui.wrap(4))
        bar.addView(kit.primaryButton("确认攻击骰") {
            if (rolling) { note("请等待骰子停止"); return@primaryButton }
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
        val canRerollAny = !die.rerolled && !state.attacker.rules.noReroll
        val actions = mutableListOf<CombatKit.SheetAction>()
        if (state.attacker.rules.rerollPolicy != ShootingRerollPolicy.NONE) {
            actions += CombatKit.SheetAction("重投 · ${weaponRerollName()}",
                ShootingCombatRules.canRerollAttackWith(state, index, RerollSource.WEAPON), primary = true) {
                rerollAttack(index, RerollSource.WEAPON, "")
            }
        }
        actions += CombatKit.SheetAction("重投 · 指挥重掷（1CP，剩 $cpLeft）", canRerollAny && cpLeft > 0) {
            rerollAttack(index, RerollSource.COMMAND, "")
        }
        actions += CombatKit.SheetAction("重投 · 其他规则", canRerollAny) {
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

    /** Defence dice retained without rolling (team rules) come out of the dice still to roll. */
    private fun defencePresetSheet() {
        if (state.phase != ShootingPhase.DEFENCE_READY) return
        val input = ui.input("", "依据（建议填写）：计谋、能力或装备名")
        val left = state.defenceDiceToRoll
        fun add(critical: Boolean) {
            pushUndo()
            state = ShootingCombatRules.addDefenceRetained(state, critical, input.text.toString().trim())
            render()
        }
        val header = ui.column().apply {
            addView(ui.text("掩护豁免已按上方选择自动预留。这里用于其他“不掷骰保留防御骰”的规则。", 12f, ui.muted), ui.wrap(8))
            addView(input)
        }
        kit.actionSheet("不掷骰预留防御骰（剩 $left 枚待掷）", header, listOf(
            CombatKit.SheetAction("预留 1 个普通成功", left > 0) { add(false) },
            CombatKit.SheetAction("预留 1 个暴击", left > 0) { add(true) }
        ))
    }

    private fun rerollAttack(index: Int, source: RerollSource, note: String) {
        val tray = attackTray ?: return
        if (rolling) return
        rolling = true
        sound.play()
        tray.selectOnly(index)
        tray.rerollSelected { values ->
            rolling = false
            if (isFinishing || isDestroyed || state.phase != ShootingPhase.ATTACK_REROLL) return@rerollSelected
            runCatching {
                pushUndo()
                state = ShootingCombatRules.rerollAttackWith(state, index, values.single(), source, note)
                render()
            }
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
            if (isFinishing || isDestroyed || state.phase != ShootingPhase.DEFENCE_REROLL) return@rerollSelected
            runCatching {
                pushUndo()
                state = ShootingCombatRules.rerollDefenceWith(state, index, values.single(), source, note)
                render()
            }
        }
    }

    // ---------------------------------------------------------------- RETAIN

    private data class RetainOption(val key: String, val keyword: String, val effect: String)

    private fun retainOptions(): List<RetainOption> {
        if (state.obscured) return emptyList()
        val preview = ShootingCombatRules.retentionPreview(state)
        val rules = state.attacker.rules
        val canPunish = rules.punishing && preview.naturalCriticals > 0 && preview.failures > 0
        return buildList {
            if (canPunish) add(RetainOption("punishing", "重击", "已保留暴击：把 1 个失败作为普通成功保留"))
            if (rules.rending && preview.naturalCriticals > 0 && (preview.rolledNormals > 0 || canPunish)) add(RetainOption("rending", "撕裂", "已保留暴击：把 1 个掷出的普通成功改为暴击（精准预留的不行）"))
            if (rules.severe && preview.naturalCriticals == 0 && preview.rolledNormals + preview.retainedNormals > 0) add(RetainOption("severe", "严重", "没有暴击：把 1 个普通成功改为暴击；之后不能再用重击、撕裂"))
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
            body.addView(WeaponRulesUi.choice(ui, kit, weapon.name, option.keyword, option.effect, on) {
                retainChoices = when (option.key) {
                    "punishing" -> retainChoices.copy(punishing = !on)
                    "rending" -> retainChoices.copy(rending = !on)
                    else -> retainChoices.copy(severe = !on)
                }
                render()
            }, ui.wrap(6))
        }
        if (state.obscured) body.addView(kit.tag("遮挡：舍弃 1 个成功，暴击改为普通", ui.orange), ui.wrap(8))
        val (normals, criticals) = ShootingCombatRules.previewAttackRetention(state, retainChoices)
        val r = state.attacker.rules
        val effects = buildList {
            add("将保留：$criticals 暴击、$normals 普通")
            if (criticals > 0 && r.devastating > 0) add("毁灭 ${r.devastating}：立即造成 ${criticals * r.devastating} 伤")
            if (criticals > 0 && r.piercingCrits > 0) add("关键穿刺 ${r.piercingCrits} 生效")
            if (criticals > 0 && r.stun) add("晕眩：目标 APL −1")
        }
        body.addView(ui.text(effects.joinToString(" · "), 13f, ui.ink, true), ui.wrap(8))
        bar.addView(ui.text("可选规则只有点“应用”才会生效", 12f, ui.muted, true), ui.wrap(6))
        bar.addView(kit.primaryButton("保留攻击骰") {
            if (rolling) { note("请等待骰子停止"); return@primaryButton }
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
            if (retained > 0) append(" − 不掷骰预留 $retained")
            append(" = 掷 ${state.defenceDiceToRoll} 枚")
        }
        body.addView(ui.text(formula, 15f, ui.ink, true), ui.wrap(6))
        body.addView(ui.row().apply {
            addView(ui.text("掩护：${state.coverChoice.short}" + if (state.attacker.rules.saturate) "（集中：不能保留掩护）" else "",
                12f, ui.muted, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(kit.tag("−1 防御骰", ui.muted) {
                pushUndo()
                state = ShootingCombatRules.adjustDefenceDiceToRoll(state, -1)
                render()
            }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) } })
            addView(kit.tag("+1 防御骰", ui.accent) {
                pushUndo()
                state = ShootingCombatRules.adjustDefenceDiceToRoll(state, 1)
                render()
            }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) } })
            addView(kit.tag("＋ 不掷骰预留", ui.accent) { defencePresetSheet() })
        }, ui.wrap(10))
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
        }, ui.wrap(4))
        bar.addView(kit.primaryButton("确认防御骰") {
            if (rolling) { note("请等待骰子停止"); return@primaryButton }
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
        if (state.attacker.rules.brutal) body.addView(kit.tag("${weapon.name}残暴：只能用防御暴击抵挡", ui.orange) {
            WeaponRulesUi.show(ui, weapon.name, listOf("残暴"))
        }, ui.wrap(8))
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
            if (!rolling) {
                pushUndo()
                state = ShootingCombatRules.beginDamage(state, force = !done)
                render()
            }
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
            note(when {
                state.attacker.rules.brutal && from == ShootingSuccess.NORMAL -> "残暴：只能用暴击抵挡"
                allocation == DefenceAllocation.TWO_NORMALS_TO_CRITICAL -> "需要 2 个普通成功才能抵挡暴击"
                else -> "不能这样分配"
            })
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
            if (rolling) { note("请等待骰子停止"); return@primaryButton }
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

    private fun isMultiTargetKeyword(k: String): Boolean =
        k == "爆炸" || k.startsWith("爆炸 ") || k.startsWith("洪流")

    private fun renderOverheat(body: LinearLayout, bar: LinearLayout) {
        body.addView(ui.text("掷 1 枚 D6：小于 ${state.attacker.effectiveHit} 时，射手受到点数 ×2 的伤害", 14f, ui.ink, true), ui.wrap(8))
        body.addView(kit.infoButton { WeaponRulesUi.show(ui, weapon.name, listOf("过热")) })
        trays(body, showAttack = true, showDefence = false)
        val multi = (modifiers.keywords(weapon.keywords)).any(::isMultiTargetKeyword)
        val rollBtn = kit.primaryButton("掷过热骰") { rollOverheat() }
        if (multi) {
            bar.addView(kit.actionBar(rollBtn, kit.secondaryButton("还有下一目标 · 延后过热") {
                pushUndo()
                state = ShootingCombatRules.skipOverheat(state)
                render()
            }))
        } else {
            bar.addView(rollBtn)
        }
    }

    private fun renderFinished(body: LinearLayout, bar: LinearLayout) {
        val d = state.defender
        val startWounds = baseDefender.currentWounds
        val lethal = d.currentWounds <= 0
        val statusText = when {
            lethal && triggerFrenzy -> "${d.operativeName} 触发【狂暴】 · 保留 1 伤（交战命令）"
            lethal && preventIncapacitation -> "${d.operativeName} 免于残废 · 保留 1 伤"
            lethal -> "${d.operativeName} 残废"
            else -> "${d.operativeName} 耐伤 $startWounds → ${d.currentWounds}"
        }
        body.addView(ui.text(
            statusText,
            22f, if (lethal && !triggerFrenzy && !preventIncapacitation) kit.danger else ui.accent, true
        ), ui.wrap(8))
        if (lethal) {
            val rescueChips = mutableListOf<View>()
            if (canTriggerFrenzy) {
                rescueChips += kit.tag(if (triggerFrenzy) "✓ 触发【狂暴】（1 伤·交战·狂暴标记）" else "触发【狂暴】", if (triggerFrenzy) ui.accent else ui.muted) {
                    triggerFrenzy = !triggerFrenzy
                    if (triggerFrenzy) preventIncapacitation = false
                    render()
                }
            }
            rescueChips += kit.tag(if (preventIncapacitation) "✓ 免于残废（保留 1 伤 · 医疗兵!等）" else "免于残废（保留 1 伤 · 医疗兵!等）",
                if (preventIncapacitation) ui.accent else ui.muted) {
                preventIncapacitation = !preventIncapacitation
                if (preventIncapacitation) triggerFrenzy = false
                render()
            }
            body.addView(kit.tagRow(rescueChips), ui.wrap(8))
        }
        val lines = buildList {
            add("武器伤害 ${state.weaponDamage}")
            if (state.devastatingDamage > 0) {
                val r = state.attacker.rules.devastatingRadius
                add("毁灭 ${state.devastatingDamage}" + if (r > 0) "（含目标 ${r}″ 内可见特工）" else "")
            }
            if (state.defenderStunned) add("晕眩：APL −1")
            if (weapon.keywords.any { it.startsWith("毒素") } && (state.weaponDamage + state.devastatingDamage) > 0) {
                add("毒素：附加毒素标记")
            }
            if (weapon.keywords.any { it == "爆炸物" }) {
                add("爆炸物：结算后射手残废")
            }
            if (state.overheatRoll != null) add("过热 ${state.overheatRoll} · 射手受 ${state.overheatDamage} 伤")
            val cp = state.attackCpSpent + state.defenceCpSpent
            if (cp > 0) add("指挥重掷 ${cp}CP")
        }
        body.addView(kit.tagRow(lines.map { kit.tag(it, ui.ink) }), ui.wrap(8))
        val multi = (modifiers.keywords(weapon.keywords)).any(::isMultiTargetKeyword)
        val done = kit.primaryButton("完成") { if (!rolling) commitResult(nextTarget = false) }
        if (multi) bar.addView(kit.actionBar(done, kit.secondaryButton("射击下一目标") { if (!rolling) commitResult(nextTarget = true) }))
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
        if (committed) { if (nextTarget) chooseNextTarget() else finish(); return }
        if (state.phase != ShootingPhase.FINISHED) return
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
        val defenderTeamId = next.side(state.defender.battleSideId).teamId
        val dealPoison = weapon.keywords.any { it.startsWith("毒素") } &&
            (state.weaponDamage + state.devastatingDamage) > 0 &&
            defenderTeamId != "CHAOS-PM"
        val dealVenom = weapon.keywords.any { it.startsWith("中毒") } &&
            (state.devastatingDamage > 0 || state.pendingHits.any { it.kind == ShootingSuccess.CRITICAL && it.damage > 0 })
        val finalDefenderWounds = if (state.defender.currentWounds <= 0 && (triggerFrenzy || preventIncapacitation)) 1 else state.defender.currentWounds
        update(state.defender.battleSideId, state.defender.memberId) { o ->
            var updated = BattleRules.setWounds(o, finalDefenderWounds)
            if (state.defender.currentWounds <= 0 && triggerFrenzy) {
                updated = updated.copy(
                    order = BattleOperativeState.ORDER_ENGAGE,
                    tokens = updated.tokens.filterNot { it.name == "狂暴" } +
                        OperativeToken(name = "狂暴", duration = TokenDuration.BATTLE, source = "狂暴")
                )
            }
            if (state.defenderStunned && !updated.stunned && !updated.incapacitated) {
                updated = updated.copy(tokens = updated.tokens +
                    OperativeToken(name = OperativeToken.STUNNED, duration = TokenDuration.NEXT_ACTIVATION_END, source = weapon.name))
            }
            if (dealPoison && updated.tokens.none { it.name == "毒素" } && !updated.incapacitated) {
                updated = updated.copy(tokens = updated.tokens +
                    OperativeToken(name = "毒素", duration = TokenDuration.BATTLE, source = weapon.name))
            }
            if (dealVenom && updated.tokens.none { it.name == "中毒" } && !updated.incapacitated) {
                updated = updated.copy(tokens = updated.tokens +
                    OperativeToken(name = "中毒", duration = TokenDuration.BATTLE, source = weapon.name))
            }
            if (next.side(state.attacker.battleSideId).teamId == "KT-SANCTIFIERS" &&
                weapon.keywords.any { it.contains("烈焰") } &&
                updated.tokens.any { it.name == "浸湿" }
            ) {
                updated = updated.copy(tokens = updated.tokens.filterNot { it.name == "浸湿" })
            }
            updated
        }
        if (useShriekerToxin) {
            next = next.copy(progress = next.progress.copy(
                usedPlans = next.progress.usedPlans + "${state.attacker.battleSideId}/交战:尖啸者毒素弹"
            ))
        }
        if (useTargetAnalysisOptic) {
            next = next.copy(progress = next.progress.copy(
                usedPlans = next.progress.usedPlans + "${state.attacker.battleSideId}/交战:目标分析镜"
            ))
        }
        val selfExplode = weapon.keywords.any { it == "爆炸物" } && !nextTarget
        val normKeywords = weapon.keywords.map { it.replace(" ", "").replace("(", "（").replace(")", "）") }
        val heavyTag = when {
            normKeywords.any { it.startsWith("重型（仅限冲刺）") } -> "重型（仅限冲刺）已射击"
            normKeywords.any { it.startsWith("重型（仅限转移）") } -> "重型（仅限转移）已射击"
            normKeywords.any { it == "重型" || it.startsWith("重型") } -> "重型已射击"
            else -> null
        }
        val concealPositionShot = weapon.keywords.any { it.contains("隐匿位置") }
        update(state.attacker.battleSideId, state.attacker.memberId) { o ->
            var updated = if (selfExplode) BattleRules.setWounds(o, 0)
            else if (state.overheatRoll != null) BattleRules.setWounds(o, state.attacker.currentWounds)
            else o
            if (heavyTag != null && updated.tokens.none { it.name == heavyTag }) {
                updated = updated.copy(tokens = updated.tokens +
                    OperativeToken(name = heavyTag, duration = TokenDuration.THIS_ACTIVATION, source = weapon.name))
            }
            if (concealPositionShot && updated.tokens.none { it.name == "隐匿位置已射击" }) {
                updated = updated.copy(tokens = updated.tokens +
                    OperativeToken(name = "隐匿位置已射击", duration = TokenDuration.BATTLE, source = weapon.name))
            }
            updated
        }
        listOf(state.attacker.battleSideId to state.attackCpSpent, state.defender.battleSideId to state.defenceCpSpent)
            .filter { it.second > 0 }
            .forEach { (sideId, cp) ->
                val side = next.side(sideId)
                next = BattleFlow.side(next, side.copy(commandPoints = (side.commandPoints - cp).coerceAtLeast(0)))
            }
        val message = buildString {
            append("射击：${state.attacker.operativeName}（${weapon.name}）→ ${state.defender.operativeName}，")
            intent.getStringExtra(BattleActivity.EXTRA_ACTION_REASON)?.takeIf { it.isNotBlank() }?.let { append("依据：$it，") }
            append("伤害 ${state.weaponDamage + state.devastatingDamage}，耐伤 $finalDefenderWounds/${state.defender.maxWounds}")
            if (state.defender.currentWounds <= 0) {
                when {
                    triggerFrenzy -> append("，触发【狂暴】保留在场")
                    preventIncapacitation -> append("，免于残废（保留 1 伤）")
                    else -> append("，残废")
                }
            }
            if (state.defenderStunned) append("，晕眩")
            if (dealPoison) append("，附加毒素标记")
            if (dealVenom) append("，附加中毒标记")
            if (selfExplode) append("；爆炸物结算完毕，${state.attacker.operativeName} 残废")
            if (state.overheatRoll != null) append("；过热 ${state.overheatRoll}，射手受 ${state.overheatDamage} 伤")
            if (state.attackCpSpent > 0) append("；${state.attacker.playerName} 指挥重掷 ${state.attackCpSpent}CP")
            if (state.defenceCpSpent > 0) append("；${state.defender.playerName} 指挥重掷 ${state.defenceCpSpent}CP")
            if (!modifiers.isEmpty) append("；修饰：${modifiers.summary().joinToString("、")}${noteSuffix(modifiers.source)}")
            append("\n过程：${state.log.joinToString("；")}")
            if (undoneTrace.isNotEmpty()) append("\n已撤回：${undoneTrace.joinToString("；") { it.second }}")
        }
        next = BattleRules.syncKills(BattleFlow.event(next, message))
        battle = store.save(next)
        committed = true
        if (nextTarget) chooseNextTarget() else finish()
    }

    /** Blast / Torrent: shoot further targets as part of the same action, each with its own dice. */
    private fun chooseNextTarget() {
        val kind = MultiTargetRules.kind(modifiers.keywords(weapon.keywords)) ?: run { finish(); return }
        val shot = shotTargets + state.defender.memberId
        val sides = listOf(battle.sideA, battle.sideB)
        val candidates = sides.flatMap { side ->
            side.operatives.map { MultiTargetRules.Candidate(side.id, it.memberId, it.incapacitated) }
        }
        val targets = MultiTargetRules.nextTargets(kind, candidates, state.defender.battleSideId,
            state.attacker.memberId, shot)
        if (targets.isEmpty()) { note("本次行动已没有其他可射击的目标"); finish(); return }
        val primary = shotTargets.isEmpty()
        val hint = when (kind) {
            MultiTargetRules.Kind.TORRENT -> "洪流：选择首要目标 x″ 内、不在己方特工控制范围内的其他有效目标；每个目标只射击一次。"
            MultiTargetRules.Kind.BLAST -> "爆炸：主要目标 x″ 内、对其可见的其他特工都必须被射击（包括己方，无论命令）；掩护与遮挡沿用主要目标。"
        }
        val header = ui.text(hint + "\n已射击：${shot.size} 个目标", 12f, ui.muted)
        kit.actionSheet("下一目标", header, targets.map { t ->
            val side = battle.side(t.sideId)
            val o = side.operatives.first { it.memberId == t.memberId }
            val friendly = t.sideId == state.attacker.battleSideId
            CombatKit.SheetAction("${if (friendly) "己方 · " else ""}${o.displayName} · ${o.order} · 耐伤 ${o.currentWounds}/${o.maxWounds}") {
                startActivity(Intent(intent).apply {
                    putExtra(EXTRA_DEFENDER_SIDE_ID, t.sideId)
                    putExtra(EXTRA_DEFENDER_MEMBER_ID, t.memberId)
                    putExtra(EXTRA_SAME_ACTION, true)
                    putStringArrayListExtra(EXTRA_SHOT_TARGETS, ArrayList(shot))
                    if (kind == MultiTargetRules.Kind.BLAST) {
                        putExtra(EXTRA_PRIMARY_COVER, (if (primary) cover else primaryCover ?: cover).name)
                        putExtra(EXTRA_PRIMARY_OBSCURED, if (primary) obscured else primaryObscured)
                    }
                })
                finish()
            }
        } + CombatKit.SheetAction("结束本次射击") { finish() })
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
        const val EXTRA_SHOT_TARGETS = "shooting_shot_targets"
        const val EXTRA_PRIMARY_COVER = "shooting_primary_cover"
        const val EXTRA_PRIMARY_OBSCURED = "shooting_primary_obscured"
    }
}

/** Weapon snapshots for an operative; falls back to the roster template for legacy battles and includes equipment/faction rule weapons. */
object CombatWeapons {
    val universalEquipmentWeapons: List<Pair<String, WeaponOption>> = listOf(
        "通用装备" to WeaponOption("eq_universal_frag", "破片手雷", "4A · 4+ · 2/4 · 范围 6\"、爆炸 2\"、集中、有限 1", WeaponKind.RANGED),
        "通用装备" to WeaponOption("eq_universal_krak", "穿甲手雷", "4A · 4+ · 4/5 · 范围 6\"、穿刺 1、集中、有限 1", WeaponKind.RANGED)
    )

    private val factionExtraWeapons: Map<String, List<Pair<String, WeaponOption>>> = mapOf(
        "CHAOS-PM" to listOf(
            "阵营装备" to WeaponOption("eq_pm_blight_grenade", "疫病手雷", "4A · 4+ · 2/4 · 范围 6\"、爆炸 2\"、集中、严重、毒素", WeaponKind.RANGED)
        ),
        "KT-BLADES_OF_KHAINE" to listOf(
            "阵营规则·呼啸女妖" to WeaponOption("rule_bok_scream", "杀戮尖叫", "5A · 3+ · 1/2 · 范围 6\"、集中、追踪轻型、眩晕、洪流 1\"", WeaponKind.RANGED)
        ),
        "KT-BROOD_BROTHERS" to listOf(
            "阵营装备" to WeaponOption("eq_bb_cult_knife", "教派匕首", "3A · 4+ · 3/4", WeaponKind.MELEE)
        ),
        "KT-DEATH_KORPS" to listOf(
            "阵营装备" to WeaponOption("eq_dk_hand_axe", "手斧", "3A · 4+ · 3/4", WeaponKind.MELEE)
        ),
        "KT-ELUCIDIAN_STARSTRIDERS" to listOf(
            "阵营规则·私掠者支援资产" to WeaponOption("rule_es_archeotech_beam", "远古科技光束", "4A · 3+ · 6/7 · 重型（仅限转移）、穿刺 2、安静、有限 1", WeaponKind.RANGED),
            "阵营规则·私掠者支援资产" to WeaponOption("rule_es_plasma_array", "等离子阵列", "5A · 4+ · 5/6 · 重型（仅限转移）、致命 5+、穿刺 1、安静、有限 1", WeaponKind.RANGED),
            "阵营规则·私掠者支援资产" to WeaponOption("rule_es_macro_cannon", "宏炮", "5A · 4+ · 4/5 · 重型（仅限转移）、关键穿刺 1、集中、安静、洪流 2\"、有限 1", WeaponKind.RANGED),
            "阵营规则·私掠者支援资产" to WeaponOption("rule_es_guided_shell", "制导飞弹", "5A · 4+ · 3/4 · 爆炸 2\"、重型（仅限转移）、安静、有限 1", WeaponKind.RANGED),
            "阵营规则·私掠者支援资产" to WeaponOption("rule_es_cluster_bombs", "集束炸弹", "5A · 4+ · 2/3 · 爆炸 3\"、重型（仅限转移）、安静、有限 1", WeaponKind.RANGED)
        ),
        "KT-EXODITE_DRAGON_MASTERS" to listOf(
            "阵营规则·石歌者升级" to WeaponOption("rule_exodite_earth_fury", "大地之怒", "4A · 3+ · 4/3 · 毁灭 2、集中", WeaponKind.RANGED)
        ),
        "KT-HEARTHKYN_SALVAGERS" to listOf(
            "阵营装备" to WeaponOption("eq_hs_plasma_knife", "等离子匕首", "3A · 4+ · 3/5 · 致命 5+", WeaponKind.MELEE)
        ),
        "KT-HERNKYN_YAEGRIS" to listOf(
            "阵营装备" to WeaponOption("eq_hy_plasma_knife", "等离子尖刀", "3A · 4+ · 3/5 · 致命 5+", WeaponKind.MELEE)
        ),
        "KT-INQUISITORIAL_AGENTS" to listOf(
            "阵营装备" to WeaponOption("eq_ia_combat_dagger", "格斗匕首", "3A · 4+ · 3/4", WeaponKind.MELEE),
            "阵营装备·寂静修女" to WeaponOption("eq_ia_combat_dagger_sos", "格斗匕首（寂静修女）", "4A · 4+ · 3/4", WeaponKind.MELEE)
        ),
        "KT-KASRKIN" to listOf(
            "阵营装备" to WeaponOption("eq_kasrkin_combat_dagger", "格斗匕首", "3A · 4+ · 3/4", WeaponKind.MELEE)
        ),
        "KT-KOMMANDOS" to listOf(
            "阵营装备" to WeaponOption("eq_kom_choppa", "砍刀", "3A · 3+ · 4/5", WeaponKind.MELEE),
            "阵营装备" to WeaponOption("eq_kom_dynamite", "炸药", "5A · 4+ · 4/5 · 范围 4\"、爆炸 1\"、重型（仅限转移）、集中、有限 1", WeaponKind.RANGED),
            "阵营装备" to WeaponOption("eq_kom_harpoon", "鱼叉", "4A · 4+ · 4/5 · 范围 8\"、致命 5+、眩晕、有限 1", WeaponKind.RANGED)
        ),
        "KT-LEGIONARIES" to listOf(
            "阵营装备" to WeaponOption("eq_leg_baleful_blade", "凶恶利刃", "5A · 3+ · 3/4", WeaponKind.MELEE)
        ),
        "KT-MANDRAKES" to listOf(
            "阵营装备" to WeaponOption("eq_mandrakes_bone_darts", "骨镖", "4A · 3+ · 2/4 · 范围 6\"、撕裂、安静", WeaponKind.RANGED)
        ),
        "KT-PHOBOS_STRIKE_TEAM" to listOf(
            "阵营装备" to WeaponOption("eq_phobos_combat_knife", "格斗刀", "5A · 3+ · 3/4", WeaponKind.MELEE)
        ),
        "KT-SCOUT_SQUAD" to listOf(
            "阵营装备" to WeaponOption("eq_scout_combat_knife", "格斗刀", "3A · 3+ · 4/5", WeaponKind.MELEE)
        ),
        "KT-SPECTRE_SQUAD" to listOf(
            "阵营装备" to WeaponOption("eq_spectre_overwatch_sniper", "守望狙击", "4A · 3+ · 3/3 · 毁灭 2、重型（仅限冲刺）、集中、安静", WeaponKind.RANGED)
        ),
        "KT-TEMPESTUS_AQUILONS" to listOf(
            "阵营装备" to WeaponOption("eq_aquilons_dagger", "风暴军匕首", "3A · 4+ · 3/4", WeaponKind.MELEE)
        ),
        "WYRM" to listOf(
            "阵营装备" to WeaponOption("eq_wyrm_blasting_charge", "爆破炸药", "4A · 4+ · 3/5 · 范围 4\"、爆炸 1\"、集中、有限 1", WeaponKind.RANGED),
            "阵营装备" to WeaponOption("eq_wyrm_cult_blade", "教派利刃", "3A · 4+ · 3/4", WeaponKind.MELEE)
        )
    )

    fun extraOptions(teamId: String): List<Pair<String, WeaponOption>> =
        factionExtraWeapons[teamId].orEmpty() + universalEquipmentWeapons

    private val nonEquippedOperativeIds = setOf(
        "kt_kom_bomb_squig",
        "kt_kom_grot",
        "kt_bb_patriarch",
        "kt_bb_psychic_familiar",
        "exodite_dragon_masters_04",
        "elucidian_starstriders_02",
        "kt_inb_cat_unit",
        "kt_inb_gheistskull",
        "inquisitorial_agents_tome_skull",
        "spectre_squad_12",
        "tempestus_aquilons_07",
        "wrecka_krew_02",
        "gellerpox_infected_07",
        "gellerpox_infected_08",
        "gellerpox_infected_09"
    )

    private fun canEquipExtraWeapons(operative: BattleOperativeState): Boolean {
        if (operative.operativeId in nonEquippedOperativeIds) return false
        val abilities = AbilityCatalog.forOperative(operative.operativeId)
        return abilities.none { a ->
            a.name in setOf("愚蠢", "愚笨", "野兽", "猛兽", "无人机", "轻装上阵") ||
                a.description.contains("不能执行来自装备的行动") ||
                a.description.contains("不可以使用任何装备") ||
                a.description.contains("无法使用任何其数据卡上没有的武器") ||
                a.description.contains("无法使用任何不在其数据卡上的武器")
        }
    }

    private fun applyEquipmentWeaponModifiers(
        teamId: String,
        notes: String,
        weapon: BattleWeaponSnapshot
    ): BattleWeaponSnapshot {
        if (notes.isBlank()) return weapon
        var profile = weapon.profile
        var attacks = weapon.attacks
        var hit = weapon.hit
        var normalDamage = weapon.normalDamage
        var criticalDamage = weapon.criticalDamage
        val keywords = weapon.keywords.toMutableList()

        if (teamId == "KT-KOMMANDOS" && notes.contains("折叠枪托") &&
            weapon.name in setOf("震荡手枪", "短铳")
        ) {
            val idx = keywords.indexOfFirst { it.startsWith("范围") }
            if (idx >= 0) {
                val removed = keywords.removeAt(idx)
                profile = profile.replace(" · $removed", "")
                    .replace("$removed、", "")
                    .replace("、$removed", "")
            }
        }
        if (teamId == "CHAOS-WC" && notes.contains("附魔弹药") && weapon.kind == WeaponKind.RANGED &&
            weapon.name in setOf("地狱火爆矢枪", "地狱火爆矢手枪", "自动手枪") &&
            keywords.none { it.startsWith("毁灭") }
        ) {
            keywords += "毁灭 1"
            profile = if (keywords.size == 1) "$profile · 毁灭 1" else "$profile、毁灭 1"
        }
        if (teamId == "KT-BLOODED" && notes.contains("邪恶之刃") && weapon.kind == WeaponKind.MELEE &&
            weapon.name in setOf("刺刀", "刺刀和护盾", "土制利刃")
        ) {
            val oldDmg = "$normalDamage/$criticalDamage"
            normalDamage += 1
            criticalDamage += 1
            val newDmg = "$normalDamage/$criticalDamage"
            profile = profile.replaceFirst(oldDmg, newDmg)
        }
        if (teamId == "KT-HERNKYN_YAEGRIS" && notes.contains("等离子尖刀") &&
            weapon.name == "等离子尖刀" && weapon.id != "eq_hy_plasma_knife" && "平衡" !in keywords
        ) {
            keywords += "平衡"
            profile = if (keywords.size == 1) "$profile · 平衡" else "$profile、平衡"
        }
        return weapon.copy(
            profile = profile,
            attacks = attacks,
            hit = hit,
            normalDamage = normalDamage,
            criticalDamage = criticalDamage,
            keywords = keywords
        )
    }

    fun of(context: android.content.Context, side: BattleSide, operative: BattleOperativeState): List<BattleWeaponSnapshot> {
        val base = operative.weapons.ifEmpty {
            val member = RosterStore(context).load(side.rosterId)?.members?.firstOrNull { it.id == operative.memberId }
            val template = RosterCatalog.operative(RosterCatalog.team(side.teamId), operative.operativeId)
            template?.weapons.orEmpty()
                .filter { member == null || template?.matchesWeaponId(it.id, member.weaponIds) == true }
                .map(BattleRules::snapshot)
        }
        val withGrenadier = base + BattleRules.grenadierWeapons(operative.operativeId, base)
        val notes = side.equipmentNotes
        val canEquip = canEquipExtraWeapons(operative)
        val autoExtra = extraOptions(side.teamId).mapNotNull { (source, w) ->
            val noteMatches = notes.contains(w.name.substringBefore("（")) ||
                (w.id in setOf("eq_universal_frag", "eq_universal_krak") && notes.contains("爆炸手雷"))
            val selectedInNotes = canEquip && noteMatches && when (w.id) {
                "eq_ia_combat_dagger_sos" -> operative.operativeId == "inquisitorial_agents_sister" || operative.displayName.contains("寂静修女")
                "eq_ia_combat_dagger" -> operative.operativeId != "inquisitorial_agents_sister" && !operative.displayName.contains("寂静修女")
                "eq_bb_cult_knife" -> operative.operativeId !in setOf("kt_bb_patriarch", "kt_bb_psychic_familiar", "kt_bb_magus", "kt_bb_primus")
                else -> true
            }
            val isBuiltInFactionRule = source.startsWith("阵营规则") && when (side.teamId) {
                "KT-BLADES_OF_KHAINE" -> operative.displayName.contains("女妖") || operative.operativeId.contains("banshee") || operative.operativeId in setOf("blades_of_khaine_01", "blades_of_khaine_02")
                "KT-ELUCIDIAN_STARSTRIDERS" -> operative.operativeId != "elucidian_starstriders_02" && canEquip // 除猎犬外均可引导
                "KT-EXODITE_DRAGON_MASTERS" -> operative.displayName.contains("石歌者") || operative.operativeId == "exodite_dragon_masters_03"
                else -> true
            }
            if ((selectedInNotes || isBuiltInFactionRule) && withGrenadier.none { it.name == w.name }) {
                BattleRules.snapshot(w)
            } else null
        }
        return (withGrenadier + autoExtra).map { applyEquipmentWeaponModifiers(side.teamId, notes, it) }
    }
}
