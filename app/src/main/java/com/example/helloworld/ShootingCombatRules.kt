package com.example.helloworld

enum class ShootingPhase {
    READY,
    ATTACK_REROLL,
    ATTACK_RETAIN,
    DEFENCE_READY,
    DEFENCE_REROLL,
    ALLOCATE,
    DAMAGE,
    OVERHEAT,
    FINISHED
}

enum class CoverChoice(val label: String, val short: String) {
    NONE("无掩护", "无"),
    STANDARD("普通掩护：预留 1 普通成功", "普通"),
    IMPROVED_CRITICAL("强化掩护：预留 1 暴击", "强化·1暴击"),
    IMPROVED_NORMALS("强化掩护：预留 2 普通成功", "强化·2普通")
}

enum class ShootingSuccess {
    NORMAL,
    CRITICAL
}

enum class DefenceAllocation {
    NORMAL_TO_NORMAL,
    TWO_NORMALS_TO_CRITICAL,
    CRITICAL_TO_NORMAL,
    CRITICAL_TO_CRITICAL
}

enum class ShootingRerollPolicy {
    NONE,
    BALANCED,
    CEASELESS,
    RELENTLESS
}

data class ShootingWeaponRules(
    val lethalThreshold: Int = 6,
    val accurate: Int = 0,
    val rerollPolicy: ShootingRerollPolicy = ShootingRerollPolicy.NONE,
    val punishing: Boolean = false,
    val rending: Boolean = false,
    val severe: Boolean = false,
    val devastating: Int = 0,
    val piercing: Int = 0,
    val piercingCrits: Int = 0,
    val saturate: Boolean = false,
    val hot: Boolean = false,
    val stun: Boolean = false
)

data class ShootingAttacker(
    val battleSideId: String,
    val memberId: String,
    val playerName: String,
    val operativeName: String,
    val weaponName: String,
    val maxWounds: Int,
    val currentWounds: Int,
    val attacks: Int,
    val baseHit: Int,
    val normalDamage: Int,
    val criticalDamage: Int,
    val accurateRetained: Int = 0,
    val rules: ShootingWeaponRules = ShootingWeaponRules()
) {
    val wounded: Boolean
        get() = currentWounds > 0 && currentWounds * 2 < maxWounds

    val effectiveHit: Int
        get() = (baseHit + if (wounded) 1 else 0).coerceIn(2, 6)

    val rolledDiceCount: Int
        get() = (attacks - accurateRetained).coerceAtLeast(0)
}

data class ShootingDefender(
    val battleSideId: String,
    val memberId: String,
    val playerName: String,
    val operativeName: String,
    val maxWounds: Int,
    val currentWounds: Int,
    val save: Int
)

data class ShootingDie(
    val id: Int,
    val value: Int,
    val rerolled: Boolean = false,
    val override: DieOverride? = null
)

data class ShootingDicePool(
    val dice: List<ShootingDie> = emptyList(),
    val normals: Int = 0,
    val criticals: Int = 0,
    val retainedNormals: Int = 0,
    val retainedCriticals: Int = 0,
    val balancedUsed: Boolean = false,
    val ceaselessFace: Int? = null,
    val extraRerollsUsed: Int = 0
) {
    val successes: Int get() = normals + criticals
}

data class ShootingRetainChoices(
    val punishing: Boolean = false,
    val rending: Boolean = false,
    val severe: Boolean = false
)

data class ShootingRetentionPreview(
    val naturalCriticals: Int,
    val rolledNormals: Int,
    val failures: Int,
    val retainedNormals: Int
)

/** One unblocked attack waiting at the damage gate. */
data class PendingHit(
    val kind: ShootingSuccess,
    val baseDamage: Int,
    val damage: Int,
    val note: String? = null
)

data class ShootingCombatState(
    val attacker: ShootingAttacker,
    val defender: ShootingDefender,
    val phase: ShootingPhase = ShootingPhase.READY,
    val coverChoice: CoverChoice = CoverChoice.NONE,
    val obscured: Boolean = false,
    val smoke: Boolean = false,
    val baseDefenceDice: Int = 3,
    val attackPool: ShootingDicePool = ShootingDicePool(),
    val defencePool: ShootingDicePool = ShootingDicePool(),
    val defenceDiceTaken: Int = 3,
    val defenceDiceToRoll: Int = 3,
    val effectivePiercing: Int = 0,
    val attackExtraRerolls: Int = 0,
    val defenceExtraRerolls: Int = 0,
    val attackCpSpent: Int = 0,
    val defenceCpSpent: Int = 0,
    val devastatingDamage: Int = 0,
    val pendingHits: List<PendingHit> = emptyList(),
    val weaponDamage: Int = 0,
    val overheatRoll: Int? = null,
    val overheatDamage: Int = 0,
    val defenderStunned: Boolean = false,
    val finishReason: String? = null,
    val log: List<String> = emptyList()
)

object ShootingRuleParser {
    fun parse(keywords: List<String>, criticalThreshold: Int = 6): ShootingWeaponRules {
        val normalized = KeywordValues.normalize(keywords)
        val rerollPolicy = when {
            normalized.any { it.startsWith("毫不留情") } -> ShootingRerollPolicy.RELENTLESS
            normalized.any { it.startsWith("无休") } -> ShootingRerollPolicy.CEASELESS
            normalized.any { it.startsWith("平衡") || it.startsWith("均衡") } -> ShootingRerollPolicy.BALANCED
            else -> ShootingRerollPolicy.NONE
        }
        return ShootingWeaponRules(
            lethalThreshold = KeywordValues.lethal(normalized, criticalThreshold),
            accurate = KeywordValues.accurate(normalized),
            rerollPolicy = rerollPolicy,
            punishing = normalized.any { it.startsWith("重击") },
            rending = normalized.any { it.startsWith("撕裂") },
            severe = normalized.any { it.startsWith("严重") },
            devastating = KeywordValues.max(normalized, "毁灭"),
            piercing = KeywordValues.max(normalized, "穿刺"),
            piercingCrits = KeywordValues.max(normalized, "关键穿刺"),
            saturate = normalized.any { it.startsWith("集中") },
            hot = normalized.any { it.startsWith("过热") },
            stun = normalized.any { it.startsWith("晕眩") || it.startsWith("眩晕") }
        )
    }
}

object ShootingCombatRules {
    fun start(attacker: ShootingAttacker, defender: ShootingDefender): ShootingCombatState =
        ShootingCombatState(
            attacker = attacker.copy(
                currentWounds = attacker.currentWounds.coerceIn(0, attacker.maxWounds),
                accurateRetained = attacker.accurateRetained.coerceIn(
                    0,
                    minOf(attacker.attacks, attacker.rules.accurate)
                )
            ),
            defender = defender.copy(
                currentWounds = defender.currentWounds.coerceIn(0, defender.maxWounds),
                save = defender.save.coerceIn(2, 6)
            )
        )

    fun configure(
        state: ShootingCombatState,
        coverChoice: CoverChoice,
        obscured: Boolean,
        accurateRetained: Int,
        attackExtraRerolls: Int = state.attackExtraRerolls,
        defenceExtraRerolls: Int = state.defenceExtraRerolls
    ): ShootingCombatState {
        require(state.phase == ShootingPhase.READY)
        val sanitizedCover = if (state.attacker.rules.saturate) CoverChoice.NONE else coverChoice
        return state.copy(
            attacker = state.attacker.copy(
                accurateRetained = accurateRetained.coerceIn(
                    0,
                    minOf(state.attacker.attacks, state.attacker.rules.accurate)
                )
            ),
            coverChoice = sanitizedCover,
            obscured = obscured,
            attackExtraRerolls = attackExtraRerolls.coerceIn(0, state.attacker.attacks),
            defenceExtraRerolls = defenceExtraRerolls.coerceIn(0, state.baseDefenceDice.coerceAtLeast(3))
        )
    }

    /** Smoke (p103) and manual defence-dice changes from team rules. */
    fun configureEnvironment(
        state: ShootingCombatState,
        smoke: Boolean,
        baseDefenceDice: Int
    ): ShootingCombatState {
        require(state.phase == ShootingPhase.READY)
        return state.copy(smoke = smoke, baseDefenceDice = baseDefenceDice.coerceAtLeast(0))
    }

    fun setAttackExtraRerolls(state: ShootingCombatState, value: Int): ShootingCombatState {
        require(state.phase == ShootingPhase.READY || state.phase == ShootingPhase.ATTACK_REROLL)
        val minimum = state.attackPool.extraRerollsUsed
        val maximum = if (state.phase == ShootingPhase.READY) {
            state.attacker.rolledDiceCount
        } else {
            state.attackPool.dice.size
        }
        return state.copy(attackExtraRerolls = value.coerceIn(minimum, maximum))
    }

    fun setDefenceExtraRerolls(state: ShootingCombatState, value: Int): ShootingCombatState {
        require(state.phase == ShootingPhase.READY || state.phase == ShootingPhase.DEFENCE_REROLL)
        val minimum = state.defencePool.extraRerollsUsed
        val maximum = if (state.phase == ShootingPhase.READY) {
            state.baseDefenceDice.coerceAtLeast(3)
        } else {
            state.defencePool.dice.size
        }
        return state.copy(defenceExtraRerolls = value.coerceIn(minimum, maximum))
    }

    fun acceptAttackRoll(state: ShootingCombatState, values: List<Int>): ShootingCombatState {
        require(state.phase == ShootingPhase.READY)
        require(values.size == state.attacker.rolledDiceCount)
        require(values.all { it in 1..6 })
        val pool = ShootingDicePool(values.mapIndexed { index, value -> ShootingDie(index, value) })
        val rolled = state.copy(
            attackPool = pool,
            log = state.log + "${state.attacker.operativeName}攻击骰：${values.joinToString("、").ifBlank { "无" }}"
        )
        return rolled.copy(
            phase = if (pool.dice.isEmpty()) ShootingPhase.ATTACK_RETAIN else ShootingPhase.ATTACK_REROLL
        )
    }

    fun canRerollAttack(state: ShootingCombatState, dieIndex: Int): Boolean {
        if (state.phase != ShootingPhase.ATTACK_REROLL) return false
        val die = state.attackPool.dice.getOrNull(dieIndex) ?: return false
        if (die.rerolled) return false
        return canUseWeaponReroll(state, dieIndex) ||
            state.attackPool.extraRerollsUsed < state.attackExtraRerolls
    }

    /** Whether any reroll source could apply. Each die can still be rerolled only once. */
    fun canRerollAttackWith(state: ShootingCombatState, dieIndex: Int, source: RerollSource): Boolean {
        if (state.phase != ShootingPhase.ATTACK_REROLL) return false
        val die = state.attackPool.dice.getOrNull(dieIndex) ?: return false
        if (die.rerolled) return false
        return source != RerollSource.WEAPON || canUseWeaponReroll(state, dieIndex)
    }

    fun canUseWeaponReroll(state: ShootingCombatState, dieIndex: Int): Boolean {
        if (state.phase != ShootingPhase.ATTACK_REROLL) return false
        val die = state.attackPool.dice.getOrNull(dieIndex) ?: return false
        if (die.rerolled) return false
        return when (state.attacker.rules.rerollPolicy) {
            ShootingRerollPolicy.NONE -> false
            ShootingRerollPolicy.BALANCED -> !state.attackPool.balancedUsed
            ShootingRerollPolicy.CEASELESS ->
                state.attackPool.ceaselessFace == null || state.attackPool.ceaselessFace == die.value
            ShootingRerollPolicy.RELENTLESS -> true
        }
    }

    fun hasAnyAttackReroll(state: ShootingCombatState): Boolean =
        state.attackPool.dice.indices.any { canRerollAttack(state.copy(phase = ShootingPhase.ATTACK_REROLL), it) }

    fun rerollAttack(state: ShootingCombatState, dieIndex: Int, newValue: Int): ShootingCombatState {
        require(canRerollAttack(state, dieIndex))
        val source = if (canUseWeaponReroll(state, dieIndex)) RerollSource.WEAPON else RerollSource.OTHER
        return applyAttackReroll(state, dieIndex, newValue, source, budgeted = true)
    }

    fun rerollAttackWith(
        state: ShootingCombatState,
        dieIndex: Int,
        newValue: Int,
        source: RerollSource,
        note: String = ""
    ): ShootingCombatState {
        require(canRerollAttackWith(state, dieIndex, source)) { "这枚骰子不能重投" }
        return applyAttackReroll(state, dieIndex, newValue, source, budgeted = false, note = note)
    }

    private fun applyAttackReroll(
        state: ShootingCombatState,
        dieIndex: Int,
        newValue: Int,
        source: RerollSource,
        budgeted: Boolean,
        note: String = ""
    ): ShootingCombatState {
        require(newValue in 1..6)
        val oldValue = state.attackPool.dice[dieIndex].value
        val weapon = source == RerollSource.WEAPON
        val policy = state.attacker.rules.rerollPolicy
        val extra = !weapon
        val pool = state.attackPool.copy(
            dice = state.attackPool.dice.mapIndexed { index, die ->
                if (index == dieIndex) die.copy(value = newValue, rerolled = true, override = null) else die
            },
            balancedUsed = state.attackPool.balancedUsed || (weapon && policy == ShootingRerollPolicy.BALANCED),
            ceaselessFace = if (weapon && policy == ShootingRerollPolicy.CEASELESS) {
                state.attackPool.ceaselessFace ?: oldValue
            } else {
                state.attackPool.ceaselessFace
            },
            extraRerollsUsed = state.attackPool.extraRerollsUsed + if (extra) 1 else 0
        )
        return state.copy(
            attackPool = pool,
            attackExtraRerolls = if (extra && !budgeted) state.attackExtraRerolls + 1 else state.attackExtraRerolls,
            attackCpSpent = state.attackCpSpent + if (source == RerollSource.COMMAND) 1 else 0,
            log = state.log + rerollLine(state.attacker.operativeName, oldValue, newValue, source, note)
        )
    }

    /** Manual reclassification or face change for team rules; value change keeps the reroll mark. */
    fun editAttackDie(
        state: ShootingCombatState,
        dieIndex: Int,
        override: DieOverride?,
        value: Int? = null,
        note: String = ""
    ): ShootingCombatState {
        require(state.phase == ShootingPhase.ATTACK_REROLL)
        val die = state.attackPool.dice[dieIndex]
        val updated = die.copy(value = value ?: die.value, override = override)
        return state.copy(
            attackPool = state.attackPool.copy(dice = state.attackPool.dice.mapIndexed { i, d -> if (i == dieIndex) updated else d }),
            log = state.log + editLine(state.attacker.operativeName, die, updated, note)
        )
    }

    fun addAttackRetained(state: ShootingCombatState, critical: Boolean, note: String = ""): ShootingCombatState {
        require(state.phase == ShootingPhase.ATTACK_REROLL)
        val pool = state.attackPool
        return state.copy(
            attackPool = if (critical) pool.copy(retainedCriticals = pool.retainedCriticals + 1)
            else pool.copy(retainedNormals = pool.retainedNormals + 1),
            log = state.log + "${state.attacker.operativeName}预留 1 个${if (critical) "暴击" else "普通成功"}${noteSuffix(note)}"
        )
    }

    fun readAttackDie(state: ShootingCombatState, die: ShootingDie): DieReading =
        DieClassifier.read(die.value, die.override, state.attacker.effectiveHit, state.attacker.rules.lethalThreshold)

    fun readDefenceDie(state: ShootingCombatState, die: ShootingDie): DieReading =
        DieClassifier.read(die.value, die.override, state.defender.save, 6)

    fun finishAttackRerolls(state: ShootingCombatState): ShootingCombatState {
        require(state.phase == ShootingPhase.ATTACK_REROLL)
        return state.copy(
            phase = ShootingPhase.ATTACK_RETAIN,
            log = state.log + "攻击方结束重投"
        )
    }

    fun retentionPreview(state: ShootingCombatState): ShootingRetentionPreview {
        val readings = state.attackPool.dice.map { readAttackDie(state, it) }
        return ShootingRetentionPreview(
            naturalCriticals = readings.count { it == DieReading.CRITICAL } + state.attackPool.retainedCriticals,
            rolledNormals = readings.count { it == DieReading.NORMAL },
            failures = readings.count { it == DieReading.FAIL },
            retainedNormals = state.attacker.accurateRetained + state.attackPool.retainedNormals
        )
    }

    fun finishAttackRetention(
        state: ShootingCombatState,
        choices: ShootingRetainChoices = ShootingRetainChoices()
    ): ShootingCombatState {
        require(state.phase == ShootingPhase.ATTACK_RETAIN)
        val preview = retentionPreview(state)
        var criticals = preview.naturalCriticals
        var rolledNormals = preview.rolledNormals
        var retainedNormals = preview.retainedNormals
        var failures = preview.failures
        val notes = mutableListOf<String>()

        if (state.attacker.rules.punishing && choices.punishing && preview.naturalCriticals > 0 && failures > 0) {
            failures--
            rolledNormals++
            notes += "重击将 1 个失败作为普通成功保留"
        }
        if (state.attacker.rules.rending && choices.rending && preview.naturalCriticals > 0 && rolledNormals > 0) {
            rolledNormals--
            criticals++
            notes += "撕裂将 1 个普通成功改为暴击"
        }
        if (state.attacker.rules.severe && choices.severe && preview.naturalCriticals == 0 && rolledNormals + retainedNormals > 0) {
            if (rolledNormals > 0) rolledNormals-- else retainedNormals--
            criticals++
            notes += "严重将 1 个普通成功改为暴击"
        }

        var normals = rolledNormals + retainedNormals
        if (state.obscured && normals + criticals > 0) {
            normals = (normals + criticals - 1).coerceAtLeast(0)
            criticals = 0
            notes += "遮挡舍弃 1 个成功，并将剩余暴击改为普通成功"
        }

        val devastatingDamage = criticals * state.attacker.rules.devastating
        val defenderAfterDevastating = state.defender.copy(
            currentWounds = (state.defender.currentWounds - devastatingDamage).coerceAtLeast(0)
        )
        val defenderStunned = criticals > 0 && state.attacker.rules.stun
        fun smoked(value: Int) = if (state.smoke && value == 2) 1 else value
        val effectivePiercing = maxOf(
            smoked(state.attacker.rules.piercing),
            if (criticals > 0) smoked(state.attacker.rules.piercingCrits) else 0
        ).coerceIn(0, state.baseDefenceDice)
        val defenceDiceTaken = (state.baseDefenceDice - effectivePiercing).coerceAtLeast(0)
        val retained = coverRetention(state.coverChoice, defenceDiceTaken)
        val defenceDiceToRoll = (defenceDiceTaken - retained.first - retained.second).coerceAtLeast(0)

        var next = state.copy(
            defender = defenderAfterDevastating,
            phase = ShootingPhase.DEFENCE_READY,
            attackPool = state.attackPool.copy(normals = normals, criticals = criticals),
            defencePool = ShootingDicePool(
                retainedNormals = retained.first,
                retainedCriticals = retained.second
            ),
            defenceDiceTaken = defenceDiceTaken,
            defenceDiceToRoll = defenceDiceToRoll,
            effectivePiercing = effectivePiercing,
            devastatingDamage = devastatingDamage,
            defenderStunned = defenderStunned,
            log = state.log + notes + buildList {
                add("攻击成功：$normals 普通、$criticals 暴击")
                if (effectivePiercing > 0) add("穿刺使防御骰减少 $effectivePiercing" + if (state.smoke) "（烟雾）" else "")
                if (retained.first > 0 || retained.second > 0) {
                    add("掩护预留 ${retained.first} 普通、${retained.second} 暴击")
                }
                if (devastatingDamage > 0) add("毁灭造成 $devastatingDamage 点伤害")
                if (defenderStunned) add("晕眩使目标 APL -1，直到其下一次激活结束")
            }
        )
        if (defenderAfterDevastating.currentWounds <= 0) {
            next = finish(next, "毁灭伤害使目标残废，射击立即结束")
        } else if (normals + criticals == 0) {
            next = finish(next, "没有保留攻击成功，射击结束")
        }
        return next
    }

    fun acceptDefenceRoll(state: ShootingCombatState, values: List<Int>): ShootingCombatState {
        require(state.phase == ShootingPhase.DEFENCE_READY)
        require(values.size == state.defenceDiceToRoll)
        require(values.all { it in 1..6 })
        val pool = state.defencePool.copy(
            dice = values.mapIndexed { index, value -> ShootingDie(index, value) }
        )
        return state.copy(
            phase = ShootingPhase.DEFENCE_REROLL,
            defencePool = pool,
            log = state.log + "${state.defender.operativeName}防御骰：${values.joinToString("、").ifBlank { "无" }}"
        )
    }

    fun canRerollDefence(state: ShootingCombatState, dieIndex: Int): Boolean =
        state.phase == ShootingPhase.DEFENCE_REROLL &&
            state.defencePool.dice.getOrNull(dieIndex)?.rerolled == false &&
            state.defencePool.extraRerollsUsed < state.defenceExtraRerolls

    fun canRerollDefenceWith(state: ShootingCombatState, dieIndex: Int): Boolean =
        state.phase == ShootingPhase.DEFENCE_REROLL &&
            state.defencePool.dice.getOrNull(dieIndex)?.rerolled == false

    fun rerollDefence(state: ShootingCombatState, dieIndex: Int, newValue: Int): ShootingCombatState {
        require(canRerollDefence(state, dieIndex))
        return applyDefenceReroll(state, dieIndex, newValue, RerollSource.OTHER, budgeted = true)
    }

    fun rerollDefenceWith(
        state: ShootingCombatState,
        dieIndex: Int,
        newValue: Int,
        source: RerollSource,
        note: String = ""
    ): ShootingCombatState {
        require(source != RerollSource.WEAPON) { "防御骰没有武器重投" }
        require(canRerollDefenceWith(state, dieIndex)) { "这枚骰子不能重投" }
        return applyDefenceReroll(state, dieIndex, newValue, source, budgeted = false, note = note)
    }

    private fun applyDefenceReroll(
        state: ShootingCombatState,
        dieIndex: Int,
        newValue: Int,
        source: RerollSource,
        budgeted: Boolean,
        note: String = ""
    ): ShootingCombatState {
        require(newValue in 1..6)
        val oldValue = state.defencePool.dice[dieIndex].value
        return state.copy(
            defencePool = state.defencePool.copy(
                dice = state.defencePool.dice.mapIndexed { index, die ->
                    if (index == dieIndex) die.copy(value = newValue, rerolled = true, override = null) else die
                },
                extraRerollsUsed = state.defencePool.extraRerollsUsed + 1
            ),
            defenceExtraRerolls = if (budgeted) state.defenceExtraRerolls else state.defenceExtraRerolls + 1,
            defenceCpSpent = state.defenceCpSpent + if (source == RerollSource.COMMAND) 1 else 0,
            log = state.log + rerollLine(state.defender.operativeName, oldValue, newValue, source, note)
        )
    }

    fun editDefenceDie(
        state: ShootingCombatState,
        dieIndex: Int,
        override: DieOverride?,
        value: Int? = null,
        note: String = ""
    ): ShootingCombatState {
        require(state.phase == ShootingPhase.DEFENCE_REROLL)
        val die = state.defencePool.dice[dieIndex]
        val updated = die.copy(value = value ?: die.value, override = override)
        return state.copy(
            defencePool = state.defencePool.copy(dice = state.defencePool.dice.mapIndexed { i, d -> if (i == dieIndex) updated else d }),
            log = state.log + editLine(state.defender.operativeName, die, updated, note)
        )
    }

    fun addDefenceRetained(state: ShootingCombatState, critical: Boolean, note: String = ""): ShootingCombatState {
        require(state.phase == ShootingPhase.DEFENCE_REROLL)
        val pool = state.defencePool
        return state.copy(
            defencePool = if (critical) pool.copy(retainedCriticals = pool.retainedCriticals + 1)
            else pool.copy(retainedNormals = pool.retainedNormals + 1),
            log = state.log + "${state.defender.operativeName}预留 1 个${if (critical) "暴击" else "普通成功"}${noteSuffix(note)}"
        )
    }

    fun finishDefenceRerolls(state: ShootingCombatState): ShootingCombatState {
        require(state.phase == ShootingPhase.DEFENCE_REROLL)
        val readings = state.defencePool.dice.map { readDefenceDie(state, it) }
        val criticals = readings.count { it == DieReading.CRITICAL } + state.defencePool.retainedCriticals
        val normals = readings.count { it == DieReading.NORMAL } + state.defencePool.retainedNormals
        return state.copy(
            phase = ShootingPhase.ALLOCATE,
            defencePool = state.defencePool.copy(normals = normals, criticals = criticals),
            log = state.log + "防御成功：$normals 普通、$criticals 暴击"
        )
    }

    fun canAllocate(state: ShootingCombatState, allocation: DefenceAllocation): Boolean {
        if (state.phase != ShootingPhase.ALLOCATE) return false
        return when (allocation) {
            DefenceAllocation.NORMAL_TO_NORMAL ->
                state.defencePool.normals >= 1 && state.attackPool.normals >= 1
            DefenceAllocation.TWO_NORMALS_TO_CRITICAL ->
                state.defencePool.normals >= 2 && state.attackPool.criticals >= 1
            DefenceAllocation.CRITICAL_TO_NORMAL ->
                state.defencePool.criticals >= 1 && state.attackPool.normals >= 1
            DefenceAllocation.CRITICAL_TO_CRITICAL ->
                state.defencePool.criticals >= 1 && state.attackPool.criticals >= 1
        }
    }

    fun allocate(state: ShootingCombatState, allocation: DefenceAllocation): ShootingCombatState {
        require(canAllocate(state, allocation))
        val attack = state.attackPool
        val defence = state.defencePool
        return when (allocation) {
            DefenceAllocation.NORMAL_TO_NORMAL -> state.copy(
                attackPool = attack.copy(normals = attack.normals - 1),
                defencePool = defence.copy(normals = defence.normals - 1),
                log = state.log + "1 个普通防御成功抵挡 1 个普通攻击成功"
            )
            DefenceAllocation.TWO_NORMALS_TO_CRITICAL -> state.copy(
                attackPool = attack.copy(criticals = attack.criticals - 1),
                defencePool = defence.copy(normals = defence.normals - 2),
                log = state.log + "2 个普通防御成功抵挡 1 个暴击攻击成功"
            )
            DefenceAllocation.CRITICAL_TO_NORMAL -> state.copy(
                attackPool = attack.copy(normals = attack.normals - 1),
                defencePool = defence.copy(criticals = defence.criticals - 1),
                log = state.log + "1 个暴击防御成功抵挡 1 个普通攻击成功"
            )
            DefenceAllocation.CRITICAL_TO_CRITICAL -> state.copy(
                attackPool = attack.copy(criticals = attack.criticals - 1),
                defencePool = defence.copy(criticals = defence.criticals - 1),
                log = state.log + "1 个暴击防御成功抵挡 1 个暴击攻击成功"
            )
        }
    }

    fun hasLegalAllocation(state: ShootingCombatState): Boolean =
        DefenceAllocation.entries.any { canAllocate(state, it) }

    /**
     * Lowest-damage plan as a suggestion only (rules don't force the defender to use it).
     * Brute force is fine: pools are tiny.
     */
    fun recommendedAllocations(state: ShootingCombatState): List<DefenceAllocation> {
        if (state.phase != ShootingPhase.ALLOCATE) return emptyList()
        var best: Pair<Int, List<DefenceAllocation>>? = null
        fun search(current: ShootingCombatState, path: List<DefenceAllocation>) {
            val options = DefenceAllocation.entries.filter { canAllocate(current, it) }
            if (options.isEmpty()) {
                val damage = remainingDamage(current)
                if (best == null || damage < best!!.first || (damage == best!!.first && path.size < best!!.second.size)) {
                    best = damage to path
                }
                return
            }
            options.forEach { search(allocate(current, it).copy(log = emptyList()), path + it) }
        }
        search(state.copy(log = emptyList()), emptyList())
        return best?.second.orEmpty()
    }

    fun remainingDamage(state: ShootingCombatState): Int =
        state.attackPool.normals * state.attacker.normalDamage +
            state.attackPool.criticals * state.attacker.criticalDamage

    /** Move unblocked attacks to the damage gate. [force] ends allocation early (team rules). */
    fun beginDamage(state: ShootingCombatState, force: Boolean = false): ShootingCombatState {
        require(state.phase == ShootingPhase.ALLOCATE)
        require(force || !hasLegalAllocation(state)) { "仍有必须分配的防御成功" }
        val hits = List(state.attackPool.criticals) {
            PendingHit(ShootingSuccess.CRITICAL, state.attacker.criticalDamage, state.attacker.criticalDamage)
        } + List(state.attackPool.normals) {
            PendingHit(ShootingSuccess.NORMAL, state.attacker.normalDamage, state.attacker.normalDamage)
        }
        val next = state.copy(
            phase = ShootingPhase.DAMAGE,
            pendingHits = hits,
            log = state.log + if (force && hasLegalAllocation(state)) listOf("防御方结束分配（规则干预）") else emptyList()
        )
        return if (hits.isEmpty()) applyDamage(next) else next
    }

    fun adjustHit(state: ShootingCombatState, index: Int, damage: Int, note: String): ShootingCombatState {
        require(state.phase == ShootingPhase.DAMAGE)
        val hit = state.pendingHits[index]
        val updated = hit.copy(damage = damage.coerceAtLeast(0), note = note.ifBlank { null })
        val kind = if (hit.kind == ShootingSuccess.CRITICAL) "暴击" else "普通"
        return state.copy(
            pendingHits = state.pendingHits.mapIndexed { i, h -> if (i == index) updated else h },
            log = state.log + "${kind}攻击伤害 ${hit.damage} → ${updated.damage}${noteSuffix(note)}"
        )
    }

    fun applyDamage(state: ShootingCombatState): ShootingCombatState {
        require(state.phase == ShootingPhase.DAMAGE)
        val weaponDamage = state.pendingHits.sumOf { it.damage }
        val defender = state.defender.copy(
            currentWounds = (state.defender.currentWounds - weaponDamage).coerceAtLeast(0)
        )
        val reason = if (defender.currentWounds <= 0) "未被抵挡的攻击使目标残废" else "射击结算完成"
        return finish(
            state.copy(
                defender = defender,
                weaponDamage = weaponDamage,
                log = state.log + "未抵挡攻击造成 $weaponDamage 点伤害"
            ),
            reason
        )
    }

    fun finishAllocation(state: ShootingCombatState): ShootingCombatState {
        val gated = beginDamage(state)
        return if (gated.phase == ShootingPhase.DAMAGE) applyDamage(gated) else gated
    }

    fun resolveOverheat(state: ShootingCombatState, roll: Int): ShootingCombatState {
        require(state.phase == ShootingPhase.OVERHEAT)
        require(roll in 1..6)
        val damage = if (roll < state.attacker.effectiveHit) roll * 2 else 0
        val attacker = state.attacker.copy(
            currentWounds = (state.attacker.currentWounds - damage).coerceAtLeast(0)
        )
        val result = if (damage > 0) {
            "过热掷出 $roll，${state.attacker.operativeName}受到 $damage 点伤害" +
                if (attacker.currentWounds <= 0) "并残废" else ""
        } else {
            "过热掷出 $roll，未造成伤害"
        }
        return state.copy(
            attacker = attacker,
            phase = ShootingPhase.FINISHED,
            overheatRoll = roll,
            overheatDamage = damage,
            log = state.log + result
        )
    }

    private fun coverRetention(choice: CoverChoice, defenceDiceTaken: Int): Pair<Int, Int> = when (choice) {
        CoverChoice.NONE -> 0 to 0
        CoverChoice.STANDARD -> if (defenceDiceTaken >= 1) 1 to 0 else 0 to 0
        CoverChoice.IMPROVED_CRITICAL -> if (defenceDiceTaken >= 1) 0 to 1 else 0 to 0
        CoverChoice.IMPROVED_NORMALS -> minOf(2, defenceDiceTaken) to 0
    }

    private fun finish(state: ShootingCombatState, reason: String): ShootingCombatState {
        val hot = state.attacker.rules.hot && state.overheatRoll == null
        return state.copy(
            phase = if (hot) ShootingPhase.OVERHEAT else ShootingPhase.FINISHED,
            finishReason = reason,
            log = state.log + (reason + if (hot) "；等待过热检定" else "")
        )
    }
}

internal fun noteSuffix(note: String): String = if (note.isBlank()) "" else "（$note）"

internal fun rerollLine(name: String, old: Int, new: Int, source: RerollSource, note: String): String {
    val label = when (source) {
        RerollSource.COMMAND -> "指挥重掷，1CP"
        else -> note.ifBlank { source.label }
    }
    return "${name}重投 $old → $new（$label）"
}

internal fun editLine(name: String, before: ShootingDie, after: ShootingDie, note: String): String =
    dieEditLine(name, before.value, after.value, after.override, note)

internal fun dieEditLine(name: String, oldValue: Int, newValue: Int, override: DieOverride?, note: String): String {
    val parts = buildList {
        if (oldValue != newValue) add("点数 $oldValue → $newValue")
        add(override?.let { "改判为${it.label}" } ?: "恢复按点数判定")
    }
    return "${name}的骰子（$oldValue）${parts.joinToString("，")}${noteSuffix(note)}"
}
