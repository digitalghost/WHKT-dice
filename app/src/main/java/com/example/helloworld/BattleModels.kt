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

data class BattleOperativeState(
    val memberId: String,
    val operativeId: String,
    val displayName: String,
    val maxWounds: Int,
    val currentWounds: Int = maxWounds,
    val order: String = ORDER_CONCEAL,
    val ready: Boolean = true,
    val incapacitated: Boolean = false,
    val weapons: List<BattleWeaponSnapshot> = emptyList()
) {
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
    val keywords: List<String>
) {
    val isMelee: Boolean get() = WeaponActions.isMelee(id)
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
    val tacticalOpRevealed: Boolean = false
) {
    val currentScore: Int get() = score.total(null)
    val totalScore: Int get() = score.total(primaryOp)
    val incapacitatedCount: Int get() = operatives.count { it.incapacitated }
}

data class BattleLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val turningPoint: Int,
    val phase: BattlePhase,
    val message: String
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
        require(setup.primaryA != null && setup.primaryB != null) { "开局前双方必须私密选择主要行动" }
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
                    if (operative.incapacitated) operative else operative.copy(ready = true)
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
        BattlePhase.STRATEGY -> battle.copy(
            phase = BattlePhase.FIREFIGHT,
            log = battle.log + BattleLogEntry(
                turningPoint = battle.turningPoint,
                phase = BattlePhase.FIREFIGHT,
                message = "进入交战阶段"
            ),
            updatedAt = System.currentTimeMillis()
        )

        BattlePhase.FIREFIGHT -> if (battle.turningPoint >= TURNING_POINT_COUNT) {
            complete(battle)
        } else {
            battle.copy(
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

    fun complete(battle: BattleRecord): BattleRecord = battle.copy(
        phase = BattlePhase.COMPLETE,
        completed = true,
        log = battle.log + BattleLogEntry(
            turningPoint = battle.turningPoint,
            phase = BattlePhase.COMPLETE,
            message = "战斗结束：${battle.sideA.totalScore} - ${battle.sideB.totalScore}"
        ),
        updatedAt = System.currentTimeMillis()
    )

    private fun createSide(
        id: String,
        playerName: String,
        roster: SavedRoster,
        equipment: String,
        tactical: String,
        primary: ScoreType?
    ): BattleSide {
        val team = RosterCatalog.team(roster.teamId)
        return BattleSide(
            id = id,
            rosterId = roster.rosterId,
            rosterName = roster.name,
            teamId = roster.teamId,
            playerName = playerName.ifBlank { if (id == SIDE_A) "玩家 A" else "玩家 B" },
            primaryOp = primary,
            equipmentNotes = equipment,
            tacticalOpNotes = tactical,
            operatives = roster.members.mapNotNull { member ->
                val operative = RosterCatalog.operative(team, member.operativeId) ?: return@mapNotNull null
                BattleOperativeState(
                    memberId = member.id,
                    operativeId = operative.id,
                    displayName = member.callsign.ifBlank { operative.name },
                    maxWounds = operative.wounds,
                    weapons = operative.weapons
                        .filter { it.id in member.weaponIds }
                        .ifEmpty { operative.weapons }
                        .map { weapon ->
                            val stats = weapon.stats
                            BattleWeaponSnapshot(
                                id = weapon.id,
                                name = weapon.name,
                                profile = weapon.profile,
                                attacks = stats.attacks,
                                hit = stats.hit,
                                critical = stats.criticalThreshold,
                                normalDamage = stats.normalDamage,
                                criticalDamage = stats.criticalDamage,
                                keywords = stats.keywords
                            )
                        }
                )
            }
        )
    }
}

object KillScoreCalculator {
    private val thresholds = mapOf(
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
        val table = thresholds[enemyStartingOperatives] ?: return 0
        return table.count { enemyIncapacitated >= it }
    }

    fun finalScore(ownKillLevel: Int, opponentKillLevel: Int): Int =
        (ownKillLevel + if (ownKillLevel > opponentKillLevel) 1 else 0)
            .coerceIn(0, BattleScore.MAX_SCORE_PER_OP)
}
