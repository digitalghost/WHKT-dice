package com.example.helloworld

import java.util.UUID

enum class BattlePhase(val label: String) {
    INITIATIVE("决定先手权"),
    STRATEGY("战略阶段"),
    FIREFIGHT("交战阶段"),
    COMPLETE("战斗结束")
}

enum class ScoreType(val label: String) {
    KEY("关键行动"),
    KILL("击杀行动"),
    TACTICAL("战术行动")
}

data class BattleScore(
    val key: Int = 0,
    val kill: Int = 0,
    val tactical: Int = 0
) {
    fun value(type: ScoreType): Int = when (type) {
        ScoreType.KEY -> key
        ScoreType.KILL -> kill
        ScoreType.TACTICAL -> tactical
    }

    fun change(type: ScoreType, delta: Int): BattleScore {
        val next = (value(type) + delta).coerceIn(0, MAX_SCORE_PER_OP)
        return when (type) {
            ScoreType.KEY -> copy(key = next)
            ScoreType.KILL -> copy(kill = next)
            ScoreType.TACTICAL -> copy(tactical = next)
        }
    }

    fun bonus(primary: ScoreType?): Int = ((primary?.let(::value) ?: 0) + 1) / 2

    fun total(primary: ScoreType?): Int = key + kill + tactical + bonus(primary)

    companion object {
        const val MAX_SCORE_PER_OP = 6
    }
}

/** When a status token ends. Expiry is automatic and logged; MANUAL/BATTLE need a tap. */
enum class TokenDuration(val label: String) {
    THIS_ACTIVATION("本次激活"),
    NEXT_ACTIVATION_START("下次激活开始"),
    NEXT_ACTIVATION_END("下次激活结束"),
    TURNING_POINT_END("本转折点结束"),
    NEXT_READY("下一就绪步骤"),
    BATTLE("整场"),
    MANUAL("手动移除");

    companion object {
        val UNTIL_REMOVED: TokenDuration get() = MANUAL
    }
}

data class OperativeToken(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val value: Int? = null,
    val duration: TokenDuration = TokenDuration.MANUAL,
    val source: String = ""
) {
    val label: String get() = if (value == null) name else "$name $value"

    companion object {
        const val OFF_FIELD = "离场"
        const val STUNNED = "晕眩"
        /** Rules that stop an operative from being activated (e.g. 磨难耳语). */
        const val BLOCKED = "不能激活"
    }
}

data class BattleOperativeState(
    val memberId: String,
    val operativeId: String,
    val displayName: String,
    val maxWounds: Int,
    val currentWounds: Int = maxWounds,
    val order: String = ORDER_CONCEAL,
    val ready: Boolean = true,
    val incapacitated: Boolean = false,
    val weapons: List<BattleWeaponSnapshot> = emptyList(),
    val apl: Int = 2,
    val tokens: List<OperativeToken> = emptyList(),
    val excludedFromKill: Boolean = false
) {
    val wounded: Boolean get() = currentWounds > 0 && currentWounds * 2 < maxWounds
    val offField: Boolean get() = tokens.any { it.name == OperativeToken.OFF_FIELD }
    val stunned: Boolean get() = tokens.any { it.name == OperativeToken.STUNNED }
    val frenzied: Boolean get() = tokens.any { it.name == "狂暴" }

    companion object {
        const val ORDER_CONCEAL = "隐匿"
        const val ORDER_ENGAGE = "交战"
    }
}

data class BattleWeaponSnapshot(
    val id: String,
    val name: String,
    val profile: String,
    val attacks: Int,
    val hit: Int,
    val critical: Int,
    val normalDamage: Int,
    val criticalDamage: Int,
    val keywords: List<String>,
    val kind: WeaponKind = WeaponActions.legacyKind(id)
) {
    val isMelee: Boolean get() = kind == WeaponKind.MELEE
}

data class BattleSide(
    val id: String,
    val rosterId: String,
    val rosterName: String,
    val teamId: String,
    val playerName: String,
    val commandPoints: Int = 2,
    val score: BattleScore = BattleScore(),
    val primaryOp: ScoreType? = null,
    val equipmentNotes: String = "",
    val tacticalOpNotes: String = "",
    val operatives: List<BattleOperativeState> = emptyList(),
    val tacticalOpRevealed: Boolean = false,
    val counters: List<TeamCounter> = emptyList()
) {
    val currentScore: Int get() = score.total(null)
    val totalScore: Int get() = score.total(primaryOp)
    val incapacitatedCount: Int get() = operatives.count { it.incapacitated }
    val inFieldCount: Int get() = operatives.count { !it.incapacitated && !it.offField }
    val killStartingCount: Int get() = operatives.count { !it.excludedFromKill }
    val killIncapacitatedCount: Int get() = operatives.count {
        (it.incapacitated || it.tokens.any { t -> t.name == "狂暴" }) && !it.excludedFromKill
    }
}

/** A named team resource (nodes, tunnels, free rerolls...). No team-specific code reads it. */
data class TeamCounter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val value: Int = 0,
    val resetEachTurningPoint: Int? = null
)

data class BattleLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val turningPoint: Int,
    val phase: BattlePhase,
    val message: String,
    val undone: Boolean = false,
    val manual: Boolean = false
)

data class BattleRecord(
    val battleId: String = UUID.randomUUID().toString(),
    val name: String,
    val progress: BattleProgress = BattleProgress(),
    val mission: String,
    val killzone: String,
    val keyOp: String,
    val dropZoneA: String,
    val dropZoneB: String,
    val sideA: BattleSide,
    val sideB: BattleSide,
    val turningPoint: Int = 0,
    val phase: BattlePhase = BattlePhase.INITIATIVE,
    val initiativeSideId: String? = null,
    val notes: String = "",
    val log: List<BattleLogEntry> = emptyList(),
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun side(id: String): BattleSide = if (sideA.id == id) sideA else sideB
    fun opponent(id: String): BattleSide = if (sideA.id == id) sideB else sideA
}

data class BattleSetup(
    val name: String,
    val mission: String,
    val killzone: String,
    val keyOp: String,
    val dropZoneA: String,
    val dropZoneB: String,
    val playerA: String,
    val playerB: String,
    val equipmentA: String,
    val equipmentB: String,
    val tacticalA: String,
    val tacticalB: String,
    val rosterA: SavedRoster,
    val rosterB: SavedRoster,
    val primaryA: ScoreType? = null,
    val primaryB: ScoreType? = null
)

object BattleRules {
    const val SIDE_A = "A"
    const val SIDE_B = "B"
    const val TURNING_POINT_COUNT = 4

    fun create(setup: BattleSetup): BattleRecord {
        require(setup.keyOp.isNotBlank()) { "开局前必须选择关键行动" }
        require(setup.tacticalA.isNotBlank() && setup.tacticalB.isNotBlank()) { "开局前双方必须私密选择战术行动" }
        val now = System.currentTimeMillis()
        return BattleRecord(
            name = setup.name,
            mission = setup.mission,
            killzone = setup.killzone,
            keyOp = setup.keyOp,
            dropZoneA = setup.dropZoneA,
            dropZoneB = setup.dropZoneB,
            sideA = createSide(SIDE_A, setup.playerA, setup.rosterA, setup.equipmentA, setup.tacticalA, setup.primaryA),
            sideB = createSide(SIDE_B, setup.playerB, setup.rosterB, setup.equipmentB, setup.tacticalB, setup.primaryB),
            log = listOf(
                BattleLogEntry(
                    timestamp = now,
                    turningPoint = 0,
                    phase = BattlePhase.INITIATIVE,
                    message = "完成战前设置，双方各获得 2CP"
                )
            ),
            createdAt = now,
            updatedAt = now
        )
    }

    fun beginTurningPoint(battle: BattleRecord, initiativeSideId: String): BattleRecord {
        require(!battle.completed)
        require(battle.phase == BattlePhase.INITIATIVE) { "只能在先手权步骤开始转折点" }
        require(initiativeSideId == SIDE_A || initiativeSideId == SIDE_B)
        val nextTurningPoint = if (battle.turningPoint == 0) 1 else battle.turningPoint + 1
        require(nextTurningPoint <= TURNING_POINT_COUNT)

        fun readySide(side: BattleSide): BattleSide {
            val cpGain = if (nextTurningPoint > 1 && side.id != initiativeSideId) 2 else 1
            return side.copy(
                commandPoints = side.commandPoints + cpGain,
                operatives = side.operatives.map { operative ->
                    val kept = operative.copy(tokens = operative.tokens.filterNot { it.duration == TokenDuration.NEXT_READY })
                    if (kept.incapacitated) kept else kept.copy(ready = true)
                },
                counters = side.counters.map { c ->
                    when {
                        c.resetEachTurningPoint != null -> c.copy(value = c.resetEachTurningPoint)
                        side.teamId == "KT-SPECTRE_SQUAD" && c.name == "野战点数" && nextTurningPoint > 1 -> c.copy(value = c.value + 1)
                        else -> c
                    }
                }
            )
        }

        return battle.copy(
            turningPoint = nextTurningPoint,
            progress = battle.progress.copy(active = null, reacted = emptySet(), nextSide = initiativeSideId, planPasses = 0, usedPlans = emptySet()),
            phase = BattlePhase.STRATEGY,
            initiativeSideId = initiativeSideId,
            sideA = readySide(battle.sideA),
            sideB = readySide(battle.sideB),
            log = battle.log + BattleLogEntry(
                turningPoint = nextTurningPoint,
                phase = BattlePhase.STRATEGY,
                message = "第 ${nextTurningPoint} 转折点开始，${battle.side(initiativeSideId).playerName} 拥有先手权"
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun advancePhase(battle: BattleRecord): BattleRecord = when (battle.phase) {
        BattlePhase.STRATEGY -> {
            require(BattleFlow.primariesReady(battle)) { "双方需要先秘密选择主要行动" }
            battle.copy(
            phase = BattlePhase.FIREFIGHT,
            progress = battle.progress.copy(nextSide = battle.initiativeSideId ?: battle.progress.nextSide, active = null),
            log = battle.log + BattleLogEntry(
                turningPoint = battle.turningPoint,
                phase = BattlePhase.FIREFIGHT,
                message = "进入交战阶段"
            ),
            updatedAt = System.currentTimeMillis()
        )
        }

        BattlePhase.FIREFIGHT -> if (battle.turningPoint >= TURNING_POINT_COUNT) {
            complete(expireTurningPointTokens(battle))
        } else {
            expireTurningPointTokens(battle).copy(
                phase = BattlePhase.INITIATIVE,
                log = battle.log + BattleLogEntry(
                    turningPoint = battle.turningPoint,
                    phase = BattlePhase.INITIATIVE,
                    message = "第 ${battle.turningPoint} 转折点结束，等待下一次先手权拼骰"
                ),
                updatedAt = System.currentTimeMillis()
            )
        }

        else -> battle
    }

    /**
     * End of battle (p107): the side with the higher kill grade gains 1VP, then primary-op bonus
     * is revealed via totalScore. Most VP wins; equal VP is a draw.
     */
    fun complete(battle: BattleRecord): BattleRecord {
        val burrowNotes = mutableListOf<String>()
        fun incapacitateBurrowed(side: BattleSide): BattleSide = side.copy(
            operatives = side.operatives.map { o ->
                if (!o.incapacitated && o.tokens.any { it.name == OperativeToken.OFF_FIELD && it.source == "钻地" }) {
                    burrowNotes += "${o.displayName} 战斗结束时仍处于钻地地下状态，视作丧失行动能力"
                    setWounds(o, 0)
                } else o
            }
        )
        val afterBurrow = battle.copy(
            sideA = incapacitateBurrowed(battle.sideA),
            sideB = incapacitateBurrowed(battle.sideB)
        )
        val synced = syncKills(afterBurrow)
        val levelA = killLevelAgainst(synced.sideB)
        val levelB = killLevelAgainst(synced.sideA)
        val bonusA = !synced.progress.killBonusApplied && levelA > levelB
        val bonusB = !synced.progress.killBonusApplied && levelB > levelA
        val sideA = if (bonusA) synced.sideA.copy(score = synced.sideA.score.change(ScoreType.KILL, 1)) else synced.sideA
        val sideB = if (bonusB) synced.sideB.copy(score = synced.sideB.score.change(ScoreType.KILL, 1)) else synced.sideB
        val ended = synced.copy(sideA = sideA, sideB = sideB, progress = synced.progress.copy(killBonusApplied = true))
        val notes = buildList {
            addAll(burrowNotes)
            if (bonusA) add("${sideA.playerName} 击杀等级 $levelA 高于对手 $levelB，+1VP")
            if (bonusB) add("${sideB.playerName} 击杀等级 $levelB 高于对手 $levelA，+1VP")
            add("战斗结束：${sideA.playerName} ${sideA.totalScore} - ${sideB.totalScore} ${sideB.playerName}，${resultLabel(ended.copy(completed = true))}")
        }
        return ended.copy(
            phase = BattlePhase.COMPLETE,
            completed = true,
            log = ended.log + notes.map {
                BattleLogEntry(turningPoint = battle.turningPoint, phase = BattlePhase.COMPLETE, message = it)
            },
            updatedAt = System.currentTimeMillis()
        )
    }

    fun resultLabel(battle: BattleRecord): String {
        val a = battle.sideA.totalScore
        val b = battle.sideB.totalScore
        return when {
            a > b -> "${battle.sideA.playerName}获胜"
            b > a -> "${battle.sideB.playerName}获胜"
            else -> "平局"
        }
    }

    /** Kill grade this side's opponent has reached against [victims]. */
    fun killLevelAgainst(victims: BattleSide): Int {
        val startingCount = if (victims.teamId == "KT-HIEROTEK_CIRCLE") 5 else victims.killStartingCount
        return KillScoreCalculator.killLevel(startingCount, victims.killIncapacitatedCount)
    }

    /**
     * Each new kill grade reached gains 1VP (p107). Tracked via [BattleProgress.killLevelA]/[BattleProgress.killLevelB]
     * so manual VP edits are preserved and each grade is only awarded once.
     */
    fun syncKills(battle: BattleRecord): BattleRecord {
        val levelA = killLevelAgainst(battle.sideB)
        val levelB = killLevelAgainst(battle.sideA)
        val reanimatingB = battle.sideB.teamId in setOf("KT-HIEROTEK_CIRCLE", "NEC-CAN")
        val reanimatingA = battle.sideA.teamId in setOf("KT-HIEROTEK_CIRCLE", "NEC-CAN")
        val deltaA = if (reanimatingB) levelA - battle.progress.killLevelA else (levelA - battle.progress.killLevelA).coerceAtLeast(0)
        val deltaB = if (reanimatingA) levelB - battle.progress.killLevelB else (levelB - battle.progress.killLevelB).coerceAtLeast(0)
        if (deltaA == 0 && deltaB == 0) return battle
        return battle.copy(
            sideA = battle.sideA.copy(score = battle.sideA.score.change(ScoreType.KILL, deltaA)),
            sideB = battle.sideB.copy(score = battle.sideB.score.change(ScoreType.KILL, deltaB)),
            progress = battle.progress.copy(
                killLevelA = if (reanimatingB) levelA else maxOf(levelA, battle.progress.killLevelA),
                killLevelB = if (reanimatingA) levelB else maxOf(levelB, battle.progress.killLevelB)
            ),
            log = battle.log + listOfNotNull(
                when {
                    deltaA > 0 -> BattleLogEntry(turningPoint = battle.turningPoint, phase = battle.phase,
                        message = "${battle.sideA.playerName} 击杀等级提升至 $levelA，击杀行动 +${deltaA}VP")
                    deltaA < 0 -> BattleLogEntry(turningPoint = battle.turningPoint, phase = battle.phase,
                        message = "${battle.sideB.playerName} 重生规程生效，${battle.sideA.playerName} 击杀等级调整为 $levelA，击杀行动 ${deltaA}VP")
                    else -> null
                },
                when {
                    deltaB > 0 -> BattleLogEntry(turningPoint = battle.turningPoint, phase = battle.phase,
                        message = "${battle.sideB.playerName} 击杀等级提升至 $levelB，击杀行动 +${deltaB}VP")
                    deltaB < 0 -> BattleLogEntry(turningPoint = battle.turningPoint, phase = battle.phase,
                        message = "${battle.sideA.playerName} 重生规程生效，${battle.sideB.playerName} 击杀等级调整为 $levelB，击杀行动 ${deltaB}VP")
                    else -> null
                }
            )
        )
    }

    /**
     * Explicitly recalculates Kill Grade and Kill Op VP when an operative is saved from incapacitation
     * (医疗兵！, 被规则阻止残废) or reanimated (重生规程, 恢复在场).
     */
    fun recalculateKills(battle: BattleRecord): BattleRecord {
        val levelA = killLevelAgainst(battle.sideB)
        val levelB = killLevelAgainst(battle.sideA)
        val deltaA = levelA - battle.progress.killLevelA
        val deltaB = levelB - battle.progress.killLevelB
        if (deltaA == 0 && deltaB == 0) return battle
        return battle.copy(
            sideA = battle.sideA.copy(score = battle.sideA.score.change(ScoreType.KILL, deltaA)),
            sideB = battle.sideB.copy(score = battle.sideB.score.change(ScoreType.KILL, deltaB)),
            progress = battle.progress.copy(
                killLevelA = levelA,
                killLevelB = levelB
            ),
            log = battle.log + listOfNotNull(
                when {
                    deltaA > 0 -> BattleLogEntry(turningPoint = battle.turningPoint, phase = battle.phase,
                        message = "${battle.sideA.playerName} 击杀等级提升至 $levelA，击杀行动 +${deltaA}VP")
                    deltaA < 0 -> BattleLogEntry(turningPoint = battle.turningPoint, phase = battle.phase,
                        message = "${battle.sideB.playerName} 特工免于残废/重生，${battle.sideA.playerName} 击杀等级调整为 $levelA，击杀行动 ${deltaA}VP")
                    else -> null
                },
                when {
                    deltaB > 0 -> BattleLogEntry(turningPoint = battle.turningPoint, phase = battle.phase,
                        message = "${battle.sideB.playerName} 击杀等级提升至 $levelB，击杀行动 +${deltaB}VP")
                    deltaB < 0 -> BattleLogEntry(turningPoint = battle.turningPoint, phase = battle.phase,
                        message = "${battle.sideA.playerName} 特工免于残废/重生，${battle.sideB.playerName} 击杀等级调整为 $levelB，击杀行动 ${deltaB}VP")
                    else -> null
                }
            )
        )
    }

    private val expendableOperativeIds = setOf(
        "kt_inb_cat_unit",
        "kt_inb_gheistskull",
        "inquisitorial_agents_tome_skull",
        "kt_kom_bomb_squig",
        "spectre_squad_12",
        "wrecka_krew_02",
        "kt_xv26_mv15",
        "kt_xv26_mv75",
        "nec_can_war",
        "gellerpox_infected_07",
        "gellerpox_infected_08",
        "gellerpox_infected_09"
    )

    /** True when an operative's rules state it is ignored for the opponent's Kill Op (消耗品/无人机/变异害虫). */
    fun isExpendableForKillOp(operativeId: String): Boolean {
        if (operativeId in expendableOperativeIds) return true
        return AbilityCatalog.forOperative(operativeId).any { a ->
            a.name.contains("消耗品") ||
                a.description.contains("在对手的击杀/灭敌行动中被忽略") ||
                a.description.contains("在您对手的杀戮/消灭任务中") ||
                a.description.contains("在对手的杀戮／消灭任务中忽略")
        }
    }

    /** Built-in Frag & Krak Grenades granted by a Grenadier operative's ability (掷弹兵 / 手雷专家). */
    fun grenadierWeapons(operativeId: String, existing: List<BattleWeaponSnapshot>): List<BattleWeaponSnapshot> {
        val grenadierAbility = AbilityCatalog.forOperative(operativeId).firstOrNull {
            it.name == "掷弹兵" || it.name == "手雷专家"
        } ?: return emptyList()
        val improvedHit = grenadierAbility.description.contains("命中属性") &&
            (grenadierAbility.description.contains("提升 1") ||
                grenadierAbility.description.contains("改善 1") ||
                grenadierAbility.description.contains("增加 1"))
        val hit = if (improvedHit) 3 else 4
        val out = mutableListOf<BattleWeaponSnapshot>()
        if (existing.none { it.name.contains("破片手雷") || it.name.contains("疫病手雷") }) {
            out += snapshot(WeaponOption("${operativeId}_frag_grenade", "破片手雷",
                "4A · ${hit}+ · 2/4 · 范围 6\"、爆炸 2\"、集中", WeaponKind.RANGED))
        }
        if (existing.none { it.name.contains("穿甲手雷") }) {
            out += snapshot(WeaponOption("${operativeId}_krak_grenade", "穿甲手雷",
                "4A · ${hit}+ · 4/5 · 范围 6\"、穿刺 1、集中", WeaponKind.RANGED))
        }
        return out
    }

    /** Wounds at 0 incapacitate (p21); raising wounds above 0 again revives the token. */
    fun snapshot(weapon: WeaponOption): BattleWeaponSnapshot {
        val stats = weapon.stats
        return BattleWeaponSnapshot(weapon.id, weapon.name, weapon.profile, stats.attacks, stats.hit,
            stats.criticalThreshold, stats.normalDamage, stats.criticalDamage, stats.keywords, weapon.kind)
    }

    /** A fresh battle operative from a team template (rules that add operatives mid-battle). */
    fun fromTemplate(template: OperativeTemplate, memberId: String, name: String = template.name): BattleOperativeState {
        val baseWeapons = template.weapons.filter { it.id in template.defaultWeaponIds }.ifEmpty { template.weapons }.map(::snapshot)
        return BattleOperativeState(
            memberId = memberId,
            operativeId = template.id,
            displayName = name,
            maxWounds = template.wounds,
            apl = template.apl,
            weapons = baseWeapons + grenadierWeapons(template.id, baseWeapons),
            ready = false,
            excludedFromKill = isExpendableForKillOp(template.id)
        )
    }

    fun setWounds(operative: BattleOperativeState, wounds: Int): BattleOperativeState {
        val value = wounds.coerceIn(0, operative.maxWounds)
        return when {
            value <= 0 -> operative.copy(currentWounds = 0, incapacitated = true, ready = false)
            operative.incapacitated && operative.currentWounds <= 0 -> operative.copy(currentWounds = value, incapacitated = false)
            else -> operative.copy(currentWounds = value)
        }
    }

    private fun expireTurningPointTokens(battle: BattleRecord): BattleRecord {
        fun clean(side: BattleSide) = side.copy(operatives = side.operatives.map { o ->
            o.copy(tokens = o.tokens.filterNot { it.duration == TokenDuration.TURNING_POINT_END || it.duration == TokenDuration.THIS_ACTIVATION })
        })
        return battle.copy(sideA = clean(battle.sideA), sideB = clean(battle.sideB))
    }

    fun defaultCounters(teamId: String): List<TeamCounter> = when (teamId) {
        "TYR-RAV" -> listOf(TeamCounter(name = "隧道标识", value = 0))
        "NEC-CAN" -> listOf(TeamCounter(name = "方尖碑节点", value = 3))
        "KT-NOVITIATES" -> listOf(TeamCounter(name = "信仰点数", value = 0))
        "KT-BLOODED" -> listOf(TeamCounter(name = "浴血指示物", value = 0))
        "KT-WRECKA_KREW" -> listOf(TeamCounter(name = "破坏点数", value = 0))
        "KT-SPECTRE_SQUAD" -> listOf(TeamCounter(name = "野战点数", value = 1))
        "KT-VESPID_STINGWINGS" -> listOf(TeamCounter(name = "交融点数", value = 0))
        "KT-HAND_OF_THE_ARCHON" -> listOf(TeamCounter(name = "痛苦指示物", value = 0))
        "KT-ELUCIDIAN_STARSTRIDERS" -> listOf(TeamCounter(name = "贸易许可（剩余）", value = 4))
        "KT-HERNKYN_YAEGRIS" -> listOf(TeamCounter(name = "机敏点数", value = 0, resetEachTurningPoint = 0))
        else -> emptyList()
    }

    private fun defaultLoadoutIds(operative: OperativeTemplate, occurrenceIndex: Int): Set<String> {
        return when (operative.id) {
            "kt_kas_gunner" -> when (occurrenceIndex % 4) {
                0 -> setOf("kt_kas_gun_plasma_std", "kt_kas_gun_plasma_sc", "kt_kas_gun_gun_butt")
                1 -> setOf("kt_kas_gun_meltagun", "kt_kas_gun_gun_butt")
                2 -> setOf("kt_kas_gun_volley_focused", "kt_kas_gun_volley_sweeping", "kt_kas_gun_gun_butt")
                else -> setOf("kt_kas_gun_gl_frag", "kt_kas_gun_gl_krak", "kt_kas_gun_gun_butt")
            }
            "void_dancer_troupe_03" -> when (occurrenceIndex) {
                0 -> setOf("void_dancer_troupe_03_w01", "void_dancer_troupe_03_w07")
                1 -> setOf("void_dancer_troupe_03_w02", "void_dancer_troupe_03_w06")
                2 -> setOf("void_dancer_troupe_03_w03", "void_dancer_troupe_03_w05")
                3 -> setOf("void_dancer_troupe_03_w03", "void_dancer_troupe_03_w04")
                else -> setOf("void_dancer_troupe_03_w03", "void_dancer_troupe_03_w07")
            }
            "nec_can_tc" -> if (occurrenceIndex == 0) {
                setOf("nec_can_tc_tdi_01", "nec_can_tc_c_01")
            } else {
                setOf("nec_can_tc_tgr_01", "nec_can_tc_tgr_02", "nec_can_tc_c_01")
            }
            "elucidian_starstriders_07" -> if (occurrenceIndex == 3) {
                setOf("elucidian_starstriders_07_w02", "elucidian_starstriders_07_w03", "elucidian_starstriders_07_w04")
            } else {
                operative.defaultWeaponIds.toSet()
            }
            else -> operative.defaultWeaponIds.toSet()
        }
    }

    private fun createSide(
        id: String,
        playerName: String,
        roster: SavedRoster,
        equipment: String,
        tactical: String,
        primary: ScoreType?
    ): BattleSide {
        val team = RosterCatalog.team(roster.teamId)
        val seenByOpId = mutableMapOf<String, Int>()
        return BattleSide(
            id = id,
            rosterId = roster.rosterId,
            rosterName = roster.name,
            teamId = roster.teamId,
            playerName = playerName.ifBlank { if (id == SIDE_A) "玩家 A" else "玩家 B" },
            primaryOp = primary,
            equipmentNotes = equipment,
            tacticalOpNotes = tactical,
            counters = defaultCounters(roster.teamId),
            operatives = roster.members.mapNotNull { member ->
                val operative = RosterCatalog.operative(team, member.operativeId) ?: return@mapNotNull null
                val occ = seenByOpId.getOrDefault(operative.id, 0).also { seenByOpId[operative.id] = it + 1 }
                val chosenIds = member.weaponIds.ifEmpty {
                    defaultLoadoutIds(operative, occ)
                }
                val baseWeapons = operative.weapons
                    .filter { operative.matchesWeaponId(it.id, chosenIds) }
                    .ifEmpty { operative.weapons }
                    .map(::snapshot)
                val initialOrder = if (operative.id == "kt_kom_bomb_squig") {
                    BattleOperativeState.ORDER_ENGAGE
                } else {
                    BattleOperativeState.ORDER_CONCEAL
                }
                BattleOperativeState(
                    memberId = member.id,
                    operativeId = operative.id,
                    displayName = member.callsign.ifBlank { operative.name },
                    maxWounds = operative.wounds,
                    order = initialOrder,
                    apl = operative.apl,
                    weapons = baseWeapons + grenadierWeapons(operative.id, baseWeapons),
                    excludedFromKill = isExpendableForKillOp(operative.id)
                )
            }
        )
    }
}

object KillScoreCalculator {
    private val thresholds = mapOf(
        1 to listOf(1, 1, 1, 1, 1),
        2 to listOf(1, 1, 2, 2, 2),
        3 to listOf(1, 2, 2, 3, 3),
        4 to listOf(1, 2, 3, 4, 4),
        5 to listOf(1, 2, 3, 4, 5),
        6 to listOf(1, 2, 4, 5, 6),
        7 to listOf(1, 3, 4, 6, 7),
        8 to listOf(2, 3, 5, 6, 8),
        9 to listOf(2, 4, 5, 7, 9),
        10 to listOf(2, 4, 6, 8, 10),
        11 to listOf(2, 4, 7, 9, 11),
        12 to listOf(2, 5, 7, 10, 12),
        13 to listOf(3, 5, 8, 10, 13),
        14 to listOf(3, 6, 8, 11, 14)
    )

    fun killLevel(enemyStartingOperatives: Int, enemyIncapacitated: Int): Int {
        if (enemyStartingOperatives <= 0 || enemyIncapacitated <= 0) return 0
        val table = thresholds[enemyStartingOperatives.coerceIn(1, 14)] ?: return 0
        return table.count { enemyIncapacitated >= it }
    }

    fun finalScore(ownKillLevel: Int, opponentKillLevel: Int): Int =
        (ownKillLevel + if (ownKillLevel > opponentKillLevel) 1 else 0)
            .coerceIn(0, BattleScore.MAX_SCORE_PER_OP)
}
