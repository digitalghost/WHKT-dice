package com.example.helloworld

enum class MeleeSide {
    ATTACKER,
    DEFENDER;

    fun opponent(): MeleeSide = if (this == ATTACKER) DEFENDER else ATTACKER
}

enum class MeleePhase {
    READY,
    REROLL,
    RETAIN,
    RESOLVE,
    FINISHED
}

enum class MeleeSuccess {
    NORMAL,
    CRITICAL
}

enum class MeleeUse {
    STRIKE,
    PARRY
}

enum class MeleeRerollPolicy {
    NONE,
    BALANCED,
    CEASELESS,
    RELENTLESS
}

data class MeleeWeaponRules(
    val lethalThreshold: Int = 6,
    val accurate: Int = 0,
    val rerollPolicy: MeleeRerollPolicy = MeleeRerollPolicy.NONE,
    val punishing: Boolean = false,
    val rending: Boolean = false,
    val severe: Boolean = false,
    val devastating: Int = 0,
    val brutal: Boolean = false,
    val shock: Boolean = false,
    val stun: Boolean = false
)

data class MeleeFighter(
    val side: MeleeSide,
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
    val assists: Int = 0,
    val canFight: Boolean = true,
    val accurateRetained: Int = 0,
    val extraRerolls: Int = 0,
    val rules: MeleeWeaponRules = MeleeWeaponRules()
) {
    val wounded: Boolean
        get() = currentWounds > 0 && currentWounds * 2 < maxWounds

    val effectiveHit: Int
        get() = (baseHit + (if (wounded) 1 else 0) - assists).coerceIn(2, 6)

    val rolledDiceCount: Int
        get() = if (canFight) (attacks - accurateRetained).coerceAtLeast(0) else 0
}

data class MeleeDie(
    val id: Int,
    val value: Int,
    val rerolled: Boolean = false,
    val override: DieOverride? = null
)

data class MeleeDicePool(
    val dice: List<MeleeDie> = emptyList(),
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

data class MeleeRetainChoices(
    val punishing: Boolean = false,
    val rending: Boolean = false,
    val severe: Boolean = false
)

data class MeleeCombatState(
    val attacker: MeleeFighter,
    val defender: MeleeFighter,
    val initiative: MeleeSide,
    val phase: MeleePhase = MeleePhase.READY,
    val attackerPool: MeleeDicePool = MeleeDicePool(),
    val defenderPool: MeleeDicePool = MeleeDicePool(),
    val rerollTurn: MeleeSide = initiative,
    val consecutiveRerollPasses: Int = 0,
    val resolveTurn: MeleeSide = MeleeSide.ATTACKER,
    val attackerShockUsed: Boolean = false,
    val defenderShockUsed: Boolean = false,
    val attackerStunned: Boolean = false,
    val defenderStunned: Boolean = false,
    val attackerCpSpent: Int = 0,
    val defenderCpSpent: Int = 0,
    val finishReason: String? = null,
    val log: List<String> = emptyList()
) {
    fun fighter(side: MeleeSide): MeleeFighter =
        if (side == MeleeSide.ATTACKER) attacker else defender

    fun pool(side: MeleeSide): MeleeDicePool =
        if (side == MeleeSide.ATTACKER) attackerPool else defenderPool
}

data class MeleeRetentionPreview(
    val naturalCriticals: Int,
    val rolledNormals: Int,
    val failures: Int,
    val retainedNormals: Int
)

object MeleeRuleParser {
    fun parse(keywords: List<String>, criticalThreshold: Int = 6): MeleeWeaponRules {
        val normalized = KeywordValues.normalize(keywords)
        val reroll = when {
            normalized.any { it.startsWith("毫不留情") } -> MeleeRerollPolicy.RELENTLESS
            normalized.any { it.startsWith("无休") } -> MeleeRerollPolicy.CEASELESS
            normalized.any { it.startsWith("平衡") || it.startsWith("均衡") } -> MeleeRerollPolicy.BALANCED
            else -> MeleeRerollPolicy.NONE
        }
        return MeleeWeaponRules(
            lethalThreshold = KeywordValues.lethal(normalized, criticalThreshold),
            accurate = KeywordValues.accurate(normalized),
            rerollPolicy = reroll,
            punishing = normalized.any { it.startsWith("重击") },
            rending = normalized.any { it.startsWith("撕裂") },
            severe = normalized.any { it.startsWith("严重") },
            devastating = KeywordValues.max(normalized, "毁灭"),
            brutal = normalized.any { it.startsWith("残暴") },
            shock = normalized.any { it.startsWith("震荡") },
            stun = normalized.any { it.startsWith("晕眩") || it.startsWith("眩晕") }
        )
    }
}

object MeleeCombatRules {
    fun start(
        attacker: MeleeFighter,
        defender: MeleeFighter,
        initiative: MeleeSide
    ): MeleeCombatState = MeleeCombatState(
        attacker = attacker.copy(
            currentWounds = attacker.currentWounds.coerceIn(0, attacker.maxWounds),
            accurateRetained = attacker.accurateRetained.coerceIn(
                0,
                minOf(attacker.attacks, attacker.rules.accurate)
            ),
            extraRerolls = attacker.extraRerolls.coerceIn(0, attacker.attacks)
        ),
        defender = defender.copy(
            currentWounds = defender.currentWounds.coerceIn(0, defender.maxWounds),
            accurateRetained = defender.accurateRetained.coerceIn(
                0,
                minOf(defender.attacks, defender.rules.accurate)
            ),
            extraRerolls = defender.extraRerolls.coerceIn(0, defender.attacks)
        ),
        initiative = initiative,
        rerollTurn = initiative
    )

    fun acceptRolls(
        state: MeleeCombatState,
        attackerValues: List<Int>,
        defenderValues: List<Int>
    ): MeleeCombatState {
        require(state.phase == MeleePhase.READY)
        require(attackerValues.size == state.attacker.rolledDiceCount)
        require(defenderValues.size == state.defender.rolledDiceCount)
        require((attackerValues + defenderValues).all { it in 1..6 })

        val attackerPool = MeleeDicePool(attackerValues.mapIndexed { index, value -> MeleeDie(index, value) })
        val defenderPool = MeleeDicePool(defenderValues.mapIndexed { index, value -> MeleeDie(index, value) })
        val rolled = state.copy(
            attackerPool = attackerPool,
            defenderPool = defenderPool,
            log = state.log + listOf(
                "${state.attacker.operativeName}掷骰：${attackerValues.joinToString("、").ifBlank { "无" }}",
                "${state.defender.operativeName}掷骰：${defenderValues.joinToString("、").ifBlank { "无" }}"
            )
        )
        return rolled.copy(
            phase = if (attackerPool.dice.isEmpty() && defenderPool.dice.isEmpty()) {
                MeleePhase.RETAIN
            } else {
                MeleePhase.REROLL
            },
            rerollTurn = state.initiative
        )
    }

    fun setExtraRerolls(
        state: MeleeCombatState,
        side: MeleeSide,
        value: Int
    ): MeleeCombatState {
        require(state.phase == MeleePhase.REROLL)
        val fighter = state.fighter(side)
        val pool = state.pool(side)
        val adjusted = value.coerceIn(pool.extraRerollsUsed, pool.dice.size)
        return if (side == MeleeSide.ATTACKER) {
            state.copy(attacker = fighter.copy(extraRerolls = adjusted))
        } else {
            state.copy(defender = fighter.copy(extraRerolls = adjusted))
        }
    }

    fun canReroll(state: MeleeCombatState, side: MeleeSide, dieIndex: Int): Boolean {
        if (state.phase != MeleePhase.REROLL || state.rerollTurn != side) return false
        val pool = state.pool(side)
        val die = pool.dice.getOrNull(dieIndex) ?: return false
        if (die.rerolled) return false
        return canUseWeaponReroll(state, side, dieIndex) ||
            pool.extraRerollsUsed < state.fighter(side).extraRerolls
    }

    fun canUseWeaponReroll(state: MeleeCombatState, side: MeleeSide, dieIndex: Int): Boolean {
        if (state.phase != MeleePhase.REROLL || state.rerollTurn != side) return false
        val fighter = state.fighter(side)
        val pool = state.pool(side)
        val die = pool.dice.getOrNull(dieIndex) ?: return false
        if (die.rerolled) return false
        return when (fighter.rules.rerollPolicy) {
            MeleeRerollPolicy.NONE -> false
            MeleeRerollPolicy.BALANCED -> !pool.balancedUsed
            MeleeRerollPolicy.CEASELESS -> pool.ceaselessFace == null || pool.ceaselessFace == die.value
            MeleeRerollPolicy.RELENTLESS -> true
        }
    }

    fun hasAnyReroll(state: MeleeCombatState, side: MeleeSide): Boolean {
        val eligible = if (state.phase == MeleePhase.REROLL && state.rerollTurn == side) {
            state
        } else {
            state.copy(phase = MeleePhase.REROLL, rerollTurn = side)
        }
        return eligible.pool(side).dice.indices.any { canReroll(eligible, side, it) }
    }

    fun reroll(
        state: MeleeCombatState,
        side: MeleeSide,
        dieIndex: Int,
        newValue: Int
    ): MeleeCombatState {
        require(canReroll(state, side, dieIndex)) { "这枚骰子不能重投" }
        val source = if (canUseWeaponReroll(state, side, dieIndex)) RerollSource.WEAPON else RerollSource.OTHER
        return applyReroll(state, side, dieIndex, newValue, source, budgeted = true)
    }

    fun canRerollWith(state: MeleeCombatState, side: MeleeSide, dieIndex: Int, source: RerollSource): Boolean {
        if (state.phase != MeleePhase.REROLL || state.rerollTurn != side) return false
        val die = state.pool(side).dice.getOrNull(dieIndex) ?: return false
        if (die.rerolled) return false
        return source != RerollSource.WEAPON || canUseWeaponReroll(state, side, dieIndex)
    }

    /** On-demand reroll: the source is chosen after seeing the dice; each die still rerolls once. */
    fun rerollWith(
        state: MeleeCombatState,
        side: MeleeSide,
        dieIndex: Int,
        newValue: Int,
        source: RerollSource,
        note: String = ""
    ): MeleeCombatState {
        require(canRerollWith(state, side, dieIndex, source)) { "这枚骰子不能重投" }
        return applyReroll(state, side, dieIndex, newValue, source, budgeted = false, note = note)
    }

    private fun applyReroll(
        state: MeleeCombatState,
        side: MeleeSide,
        dieIndex: Int,
        newValue: Int,
        source: RerollSource,
        budgeted: Boolean,
        note: String = ""
    ): MeleeCombatState {
        require(newValue in 1..6)
        val oldPool = state.pool(side)
        val oldValue = oldPool.dice[dieIndex].value
        val weapon = source == RerollSource.WEAPON
        val policy = state.fighter(side).rules.rerollPolicy
        val nextPool = oldPool.copy(
            dice = oldPool.dice.mapIndexed { index, die ->
                if (index == dieIndex) die.copy(value = newValue, rerolled = true, override = null) else die
            },
            balancedUsed = oldPool.balancedUsed || (weapon && policy == MeleeRerollPolicy.BALANCED),
            ceaselessFace = if (weapon && policy == MeleeRerollPolicy.CEASELESS) {
                oldPool.ceaselessFace ?: oldValue
            } else {
                oldPool.ceaselessFace
            },
            extraRerollsUsed = oldPool.extraRerollsUsed + if (weapon) 0 else 1
        )
        val fighter = state.fighter(side)
        val grown = if (!weapon && !budgeted) fighter.copy(extraRerolls = fighter.extraRerolls + 1) else fighter
        val cp = if (source == RerollSource.COMMAND) 1 else 0
        val withFighter = if (side == MeleeSide.ATTACKER) {
            state.copy(attacker = grown, attackerCpSpent = state.attackerCpSpent + cp)
        } else {
            state.copy(defender = grown, defenderCpSpent = state.defenderCpSpent + cp)
        }
        return withFighter.withPool(side, nextPool).copy(
            rerollTurn = side.opponent(),
            consecutiveRerollPasses = 0,
            log = state.log + rerollLine(fighter.operativeName, oldValue, newValue, source, note)
        )
    }

    /** Manual die change for team rules. Allowed for either side during the reroll step. */
    fun editDie(
        state: MeleeCombatState,
        side: MeleeSide,
        dieIndex: Int,
        override: DieOverride?,
        value: Int? = null,
        note: String = ""
    ): MeleeCombatState {
        require(state.phase == MeleePhase.REROLL)
        val pool = state.pool(side)
        val die = pool.dice[dieIndex]
        val updated = die.copy(value = value ?: die.value, override = override)
        return state.withPool(side, pool.copy(dice = pool.dice.mapIndexed { i, d -> if (i == dieIndex) updated else d }))
            .copy(log = state.log + dieEditLine(state.fighter(side).operativeName, die.value, updated.value, override, note))
    }

    fun addRetained(state: MeleeCombatState, side: MeleeSide, critical: Boolean, note: String = ""): MeleeCombatState {
        require(state.phase == MeleePhase.REROLL)
        val pool = state.pool(side)
        val next = if (critical) pool.copy(retainedCriticals = pool.retainedCriticals + 1)
        else pool.copy(retainedNormals = pool.retainedNormals + 1)
        return state.withPool(side, next).copy(
            log = state.log + "${state.fighter(side).operativeName}预留 1 个${if (critical) "暴击" else "普通成功"}${noteSuffix(note)}"
        )
    }

    fun readDie(state: MeleeCombatState, side: MeleeSide, die: MeleeDie): DieReading {
        val fighter = state.fighter(side)
        return DieClassifier.read(die.value, die.override, fighter.effectiveHit, fighter.rules.lethalThreshold)
    }

    fun passReroll(state: MeleeCombatState, side: MeleeSide): MeleeCombatState {
        require(state.phase == MeleePhase.REROLL && state.rerollTurn == side)
        val passes = state.consecutiveRerollPasses + 1
        return state.copy(
            phase = if (passes >= 2) MeleePhase.RETAIN else MeleePhase.REROLL,
            rerollTurn = side.opponent(),
            consecutiveRerollPasses = passes,
            log = state.log + "${state.fighter(side).operativeName}跳过重投"
        )
    }

    fun retentionPreview(state: MeleeCombatState, side: MeleeSide): MeleeRetentionPreview {
        val fighter = state.fighter(side)
        val pool = state.pool(side)
        val readings = pool.dice.map { readDie(state, side, it) }
        return MeleeRetentionPreview(
            naturalCriticals = readings.count { it == DieReading.CRITICAL } + pool.retainedCriticals,
            rolledNormals = readings.count { it == DieReading.NORMAL },
            failures = readings.count { it == DieReading.FAIL },
            retainedNormals = fighter.accurateRetained + pool.retainedNormals
        )
    }

    fun finishRetention(
        state: MeleeCombatState,
        attackerChoices: MeleeRetainChoices = MeleeRetainChoices(),
        defenderChoices: MeleeRetainChoices = MeleeRetainChoices()
    ): MeleeCombatState {
        require(state.phase == MeleePhase.RETAIN)
        val (attackerPool, attackerNotes) = classify(
            state.attacker,
            state.attackerPool,
            attackerChoices
        )
        val (defenderPool, defenderNotes) = classify(
            state.defender,
            state.defenderPool,
            defenderChoices
        )

        val damageToDefender = attackerPool.criticals * state.attacker.rules.devastating
        val damageToAttacker = defenderPool.criticals * state.defender.rules.devastating
        val attacker = state.attacker.copy(
            currentWounds = (state.attacker.currentWounds - damageToAttacker).coerceAtLeast(0)
        )
        val defender = state.defender.copy(
            currentWounds = (state.defender.currentWounds - damageToDefender).coerceAtLeast(0)
        )
        val attackerStunned = defenderPool.criticals > 0 && state.defender.rules.stun
        val defenderStunned = attackerPool.criticals > 0 && state.attacker.rules.stun
        val ruleNotes = buildList {
            addAll(attackerNotes.map { "${attacker.operativeName}：$it" })
            addAll(defenderNotes.map { "${defender.operativeName}：$it" })
            if (damageToDefender > 0) add("${attacker.weaponName}的毁灭造成 $damageToDefender 点额外伤害")
            if (damageToAttacker > 0) add("${defender.weaponName}的毁灭造成 $damageToAttacker 点额外伤害")
            if (defenderStunned) add("${defender.operativeName}受到晕眩：APL -1，直到其下一次激活结束")
            if (attackerStunned) add("${attacker.operativeName}受到晕眩：APL -1，直到其下一次激活结束")
        }
        var next = state.copy(
            attacker = attacker,
            defender = defender,
            attackerPool = attackerPool,
            defenderPool = defenderPool,
            attackerStunned = attackerStunned,
            defenderStunned = defenderStunned,
            phase = MeleePhase.RESOLVE,
            resolveTurn = MeleeSide.ATTACKER,
            log = state.log + ruleNotes
        )
        next = normalizeResolution(next)
        return when {
            attacker.currentWounds <= 0 || defender.currentWounds <= 0 -> finish(
                next,
                "毁灭伤害使特工残废，近战立即结束"
            )
            attackerPool.successes == 0 && defenderPool.successes == 0 -> finish(
                next,
                "双方均无未结算成功，近战结束"
            )
            else -> next
        }
    }

    fun canParryWith(state: MeleeCombatState, side: MeleeSide, success: MeleeSuccess): Boolean {
        if (state.phase != MeleePhase.RESOLVE || state.resolveTurn != side) return false
        if (state.pool(side).count(success) <= 0) return false
        if (state.fighter(side.opponent()).rules.brutal && success != MeleeSuccess.CRITICAL) return false
        val opponentPool = state.pool(side.opponent())
        return when (success) {
            MeleeSuccess.NORMAL -> opponentPool.normals > 0 || opponentPool.successes == 0
            MeleeSuccess.CRITICAL -> true
        }
    }

    fun resolve(
        state: MeleeCombatState,
        side: MeleeSide,
        success: MeleeSuccess,
        use: MeleeUse,
        parryTarget: MeleeSuccess? = null
    ): MeleeCombatState {
        require(state.phase == MeleePhase.RESOLVE && state.resolveTurn == side)
        require(state.pool(side).count(success) > 0) { "没有可结算的成功骰" }
        var ownPool = state.pool(side).remove(success)
        var opponentPool = state.pool(side.opponent())
        var attacker = state.attacker
        var defender = state.defender
        var attackerShockUsed = state.attackerShockUsed
        var defenderShockUsed = state.defenderShockUsed
        val notes = mutableListOf<String>()
        val fighter = state.fighter(side)
        val opponent = state.fighter(side.opponent())

        when (use) {
            MeleeUse.STRIKE -> {
                val damage = if (success == MeleeSuccess.CRITICAL) fighter.criticalDamage else fighter.normalDamage
                if (side == MeleeSide.ATTACKER) {
                    defender = defender.copy(currentWounds = (defender.currentWounds - damage).coerceAtLeast(0))
                } else {
                    attacker = attacker.copy(currentWounds = (attacker.currentWounds - damage).coerceAtLeast(0))
                }
                notes += "${fighter.operativeName}以${success.label()}出击，${opponent.operativeName}受到 $damage 点伤害"

                val shockUnused = if (side == MeleeSide.ATTACKER) !attackerShockUsed else !defenderShockUsed
                if (success == MeleeSuccess.CRITICAL && fighter.rules.shock && shockUnused) {
                    val removed = when {
                        opponentPool.normals > 0 -> MeleeSuccess.NORMAL
                        opponentPool.criticals > 0 -> MeleeSuccess.CRITICAL
                        else -> null
                    }
                    if (removed != null) {
                        opponentPool = opponentPool.remove(removed)
                        notes += "震荡额外舍弃${opponent.operativeName}的${removed.label()}"
                    }
                    if (side == MeleeSide.ATTACKER) attackerShockUsed = true else defenderShockUsed = true
                }
            }

            MeleeUse.PARRY -> {
                require(canParryWith(state, side, success)) { "该成功不能用于格挡" }
                if (opponentPool.successes == 0) {
                    require(parryTarget == null)
                    notes += "${fighter.operativeName}以${success.label()}进行空格挡"
                } else {
                    val target = requireNotNull(parryTarget) { "请选择要格挡的成功" }
                    require(opponentPool.count(target) > 0) { "对手没有这种成功" }
                    require(success == MeleeSuccess.CRITICAL || target == MeleeSuccess.NORMAL) {
                        "普通成功只能格挡普通成功"
                    }
                    opponentPool = opponentPool.remove(target)
                    notes += "${fighter.operativeName}以${success.label()}格挡${opponent.operativeName}的${target.label()}"
                }
            }
        }

        var next = state.copy(
            attacker = attacker,
            defender = defender,
            attackerShockUsed = attackerShockUsed,
            defenderShockUsed = defenderShockUsed,
            log = state.log + notes
        ).withPool(side, ownPool).withPool(side.opponent(), opponentPool)

        if (attacker.currentWounds <= 0 || defender.currentWounds <= 0) {
            return finish(next, "有特工残废，近战立即结束")
        }
        next = next.copy(resolveTurn = side.opponent())
        return normalizeResolution(next)
    }

    /** Manual wound change (damage reduction, extra damage, healing) during or after resolution. */
    fun adjustWounds(state: MeleeCombatState, side: MeleeSide, delta: Int, note: String = ""): MeleeCombatState {
        require(state.phase == MeleePhase.RESOLVE || state.phase == MeleePhase.FINISHED)
        val fighter = state.fighter(side)
        val wounds = (fighter.currentWounds + delta).coerceIn(0, fighter.maxWounds)
        val updated = fighter.copy(currentWounds = wounds)
        var next = (if (side == MeleeSide.ATTACKER) state.copy(attacker = updated) else state.copy(defender = updated))
            .copy(log = state.log + "${fighter.operativeName}耐伤 ${fighter.currentWounds} → $wounds${noteSuffix(note)}")
        if (next.phase == MeleePhase.RESOLVE && wounds <= 0) next = finish(next, "有特工残废，近战立即结束")
        return next
    }

    /** Discard one unresolved success of [owner] (e.g. a parry that blocks two successes). */
    fun discardSuccess(state: MeleeCombatState, owner: MeleeSide, success: MeleeSuccess, note: String = ""): MeleeCombatState {
        require(state.phase == MeleePhase.RESOLVE)
        require(state.pool(owner).count(success) > 0) { "没有这种未结算成功" }
        val next = state.withPool(owner, state.pool(owner).remove(success)).copy(
            log = state.log + "舍弃${state.fighter(owner).operativeName}的 1 个${success.label()}${noteSuffix(note)}"
        )
        return normalizeResolution(next)
    }

    /** Hand the next resolution to the other fighter (team rules that change the order). */
    fun switchTurn(state: MeleeCombatState, note: String = ""): MeleeCombatState {
        require(state.phase == MeleePhase.RESOLVE)
        val target = state.resolveTurn.opponent()
        require(state.pool(target).successes > 0) { "对方没有未结算成功" }
        return state.copy(
            resolveTurn = target,
            log = state.log + "改由${state.fighter(target).operativeName}结算${noteSuffix(note)}"
        )
    }

    private fun classify(
        fighter: MeleeFighter,
        pool: MeleeDicePool,
        choices: MeleeRetainChoices
    ): Pair<MeleeDicePool, List<String>> {
        val preview = MeleeCombatState(
            attacker = fighter,
            defender = fighter,
            initiative = fighter.side,
            attackerPool = pool,
            defenderPool = pool
        ).let { retentionPreview(it, fighter.side) }
        var criticals = preview.naturalCriticals
        var rolledNormals = preview.rolledNormals
        var retainedNormals = preview.retainedNormals
        var failures = preview.failures
        val notes = mutableListOf<String>()

        if (preview.naturalCriticals > 0) {
            if (fighter.rules.punishing && choices.punishing && failures > 0) {
                failures -= 1
                rolledNormals += 1
                notes += "重击将一个失败保留为普通成功"
            }
            if (fighter.rules.rending && choices.rending && rolledNormals > 0) {
                rolledNormals -= 1
                criticals += 1
                notes += "撕裂将一个普通成功改为暴击；精准预留未用于该转换"
            }
        } else if (fighter.rules.severe && choices.severe && rolledNormals + retainedNormals > 0) {
            if (rolledNormals > 0) rolledNormals -= 1 else retainedNormals -= 1
            criticals += 1
            notes += "严重将一个已保留的普通成功改为暴击"
        }

        return pool.copy(
            normals = rolledNormals + retainedNormals,
            criticals = criticals
        ) to notes
    }

    private fun normalizeResolution(state: MeleeCombatState): MeleeCombatState {
        if (state.phase != MeleePhase.RESOLVE) return state
        val attackerSuccesses = state.attackerPool.successes
        val defenderSuccesses = state.defenderPool.successes
        return when {
            attackerSuccesses == 0 && defenderSuccesses == 0 -> finish(
                state,
                "双方成功骰均已结算，近战结束"
            )
            state.resolveTurn == MeleeSide.ATTACKER && attackerSuccesses == 0 ->
                state.copy(resolveTurn = MeleeSide.DEFENDER)
            state.resolveTurn == MeleeSide.DEFENDER && defenderSuccesses == 0 ->
                state.copy(resolveTurn = MeleeSide.ATTACKER)
            else -> state
        }
    }

    private fun finish(state: MeleeCombatState, reason: String): MeleeCombatState = state.copy(
        phase = MeleePhase.FINISHED,
        finishReason = reason,
        log = state.log + reason
    )

    private fun MeleeCombatState.withPool(side: MeleeSide, pool: MeleeDicePool): MeleeCombatState =
        if (side == MeleeSide.ATTACKER) copy(attackerPool = pool) else copy(defenderPool = pool)

    private fun MeleeDicePool.count(success: MeleeSuccess): Int = when (success) {
        MeleeSuccess.NORMAL -> normals
        MeleeSuccess.CRITICAL -> criticals
    }

    private fun MeleeDicePool.remove(success: MeleeSuccess): MeleeDicePool = when (success) {
        MeleeSuccess.NORMAL -> copy(normals = (normals - 1).coerceAtLeast(0))
        MeleeSuccess.CRITICAL -> copy(criticals = (criticals - 1).coerceAtLeast(0))
    }

    private fun MeleeSuccess.label(): String = if (this == MeleeSuccess.CRITICAL) "暴击成功" else "普通成功"
}
