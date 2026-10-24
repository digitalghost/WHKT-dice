package com.example.helloworld

import org.json.JSONArray
import org.json.JSONObject

data class Activation(
    val side: String, val member: String, val limit: Int, val spent: Int = 0,
    val reaction: Boolean = false, val actions: List<String> = emptyList()
)

data class BattleProgress(
    val setupStep: Int = 0,
    val deploymentInitiative: String = "",
    val deployedA: Int = 0,
    val deployedB: Int = 0,
    val nextSide: String = "A",
    val cardsA: List<Int> = emptyList(),
    val cardsB: List<Int> = emptyList(),
    val active: Activation? = null,
    val reacted: Set<String> = emptySet(),
    val planPasses: Int = 0,
    val usedPlans: Set<String> = emptySet(),
    val objectives: Map<String, String> = emptyMap(),
    val killBonusApplied: Boolean = false,
    val killLevelA: Int = 0,
    val killLevelB: Int = 0
)

object BattleFlow {
    fun event(b: BattleRecord, message: String): BattleRecord = b.copy(
        log = b.log + BattleLogEntry(turningPoint = b.turningPoint, phase = b.phase, message = message)
    )

    fun side(b: BattleRecord, s: BattleSide): BattleRecord =
        if (s.id == "A") b.copy(sideA = s) else b.copy(sideB = s)

    fun other(id: String) = if (id == "A") "B" else "A"
    fun key(s: String, m: String) = "$s/$m"
    fun groups(count: Int): List<Int> = List(3) { count / 3 + if (it < count % 3) 1 else 0 }
    fun ready(b: BattleRecord, s: String) = b.side(s).operatives.any { it.ready && !it.incapacitated }

    fun deployment(b: BattleRecord, initiative: String): BattleRecord {
        require(b.turningPoint == 0 && b.progress.setupStep == 0)
        return event(b.copy(progress = b.progress.copy(
            deploymentInitiative = initiative, setupStep = 1, nextSide = initiative,
            cardsA = if (initiative == "B") listOf(0) else emptyList(),
            cardsB = if (initiative == "A") listOf(0) else emptyList()
        )), "战前先手：${b.side(initiative).playerName}；对方获得重投先手权卡。先手方选择降落区。")
    }

    fun deployGroup(b: BattleRecord): BattleRecord {
        val p = b.progress
        require(p.setupStep == 2)
        val s = p.nextSide
        val done = if (s == "A") p.deployedA else p.deployedB
        require(done < 3)
        val a = p.deployedA + if (s == "A") 1 else 0
        val c = p.deployedB + if (s == "B") 1 else 0
        return event(b.copy(progress = p.copy(
            deployedA = a, deployedB = c, nextSide = other(s), setupStep = if (a == 3 && c == 3) 3 else 2
        )), "${b.side(s).playerName}部署第 ${done + 1} 组：${groups(b.side(s).operatives.size)[done]} 名特工，隐匿命令")
    }

    /**
     * Roll-off winner (p8, p106). Ties: the player without initiative wins; in the first
     * turning point the player who did not have deployment initiative wins. Returns null when
     * that cannot be decided from the record (deployment initiative unknown).
     */
    fun initiativeWinner(b: BattleRecord, rollA: Int, rollB: Int): String? = when {
        rollA > rollB -> "A"
        rollB > rollA -> "B"
        b.turningPoint == 0 -> b.progress.deploymentInitiative.takeIf { it == "A" || it == "B" }?.let(::other)
        else -> b.initiativeSideId?.let(::other)
    }

    /** Base APL with modifiers capped at ±1 in total (p21); stun is one of those modifiers. */
    fun effectiveApl(o: BattleOperativeState, manualDelta: Int = 0): Int {
        val delta = (manualDelta - if (o.stunned) 1 else 0).coerceIn(-1, 1)
        return (o.apl + delta).coerceAtLeast(0)
    }

    fun initiative(b: BattleRecord, winner: String, chosen: String): BattleRecord {
        val next = BattleRules.beginTurningPoint(b, chosen)
        val loser = other(winner)
        val card = next.turningPoint
        val p = next.progress
        val updated = if (card < 4) p.copy(
            cardsA = p.cardsA + if (loser == "A") listOf(card) else emptyList(),
            cardsB = p.cardsB + if (loser == "B") listOf(card) else emptyList()
        ) else p
        return event(next.copy(progress = updated), "拼骰胜方：${b.side(winner).playerName}；选择${b.side(chosen).playerName}先手" +
            if (card < 4) "；败方获得 ±$card 卡" else "；第四转折点不发卡")
    }

    fun useCard(b: BattleRecord, s: String, card: Int, result: String): BattleRecord {
        require(b.phase == BattlePhase.INITIATIVE)
        val p = b.progress
        val cards = (if (s == "A") p.cardsA else p.cardsB).toMutableList()
        require(cards.remove(card)) { "没有这张先手权卡" }
        return event(b.copy(progress = if (s == "A") p.copy(cardsA = cards) else p.copy(cardsB = cards)),
            "${b.side(s).playerName}使用${if (card == 0) "重投" else "±$card"}先手权卡；$result")
    }

    fun planUsed(b: BattleRecord, name: String): Boolean = key(b.progress.nextSide, name) in b.progress.usedPlans

    fun plan(b: BattleRecord, name: String, cost: Int, allowRepeat: Boolean = false): BattleRecord {
        require(b.phase == BattlePhase.STRATEGY && b.progress.planPasses < 2)
        val s = b.side(b.progress.nextSide)
        require(name.isNotBlank() && cost >= 0 && s.commandPoints >= cost) { "请输入计划名称并确认 CP 足够" }
        val id = key(s.id, name)
        require(allowRepeat || id !in b.progress.usedPlans) { "本转折点已使用该战略计划" }
        return event(side(b, s.copy(commandPoints = s.commandPoints - cost)).copy(
            progress = b.progress.copy(nextSide = other(s.id), planPasses = 0, usedPlans = b.progress.usedPlans + id)
        ), "${s.playerName}：$name（消耗 $cost CP）")
    }

    /**
     * Primary op (p106): in the first turning point each player secretly chooses it as a
     * strategic plan. The log never names the choice; it is revealed at the end of the battle.
     */
    fun needsPrimary(b: BattleRecord, sideId: String): Boolean =
        b.phase == BattlePhase.STRATEGY && b.turningPoint == 1 && b.side(sideId).primaryOp == null

    fun choosePrimary(b: BattleRecord, choice: ScoreType): BattleRecord {
        require(b.phase == BattlePhase.STRATEGY && b.turningPoint == 1) { "主要行动在第一转折点战略阶段选择" }
        val s = b.side(b.progress.nextSide)
        require(s.primaryOp == null) { "已经选择过主要行动" }
        return event(side(b, s.copy(primaryOp = choice)).copy(
            progress = b.progress.copy(nextSide = other(s.id), planPasses = 0, usedPlans = b.progress.usedPlans + key(s.id, PRIMARY_PLAN))
        ), "${s.playerName}：秘密选择主要行动")
    }

    const val PRIMARY_PLAN = "主要行动"

    fun primariesReady(b: BattleRecord): Boolean =
        b.turningPoint != 1 || (b.sideA.primaryOp != null && b.sideB.primaryOp != null)

    fun passPlan(b: BattleRecord): BattleRecord {
        require(b.phase == BattlePhase.STRATEGY && b.progress.planPasses < 2)
        require(b.turningPoint != 1 || b.side(b.progress.nextSide).primaryOp != null) { "第一转折点请先秘密选择己方主要行动" }
        return event(b.copy(progress = b.progress.copy(
            nextSide = other(b.progress.nextSide), planPasses = b.progress.planPasses + 1
        )), "${b.side(b.progress.nextSide).playerName}跳过战略计划")
    }

    /**
     * [force] is the manual escape hatch for team rules (extra/interrupting activations,
     * activating an expended operative): it skips turn and readiness checks and is logged.
     */
    fun activate(
        b: BattleRecord, s: String, member: String, apl: Int, engage: Boolean,
        force: Boolean = false, reason: String = ""
    ): BattleRecord {
        require(b.phase == BattlePhase.FIREFIGHT && b.progress.active == null) { "请先结束当前激活" }
        val o = b.side(s).operatives.first { it.memberId == member }
        require(!o.incapacitated) { "残废特工不能激活" }
        val reaction = !force && !ready(b, s)
        if (!force) {
            require(s == b.progress.nextSide) { "尚未轮到该玩家" }
            if (reaction) {
                require(ready(b, other(s)) && o.order == BattleOperativeState.ORDER_ENGAGE &&
                    key(s, member) !in b.progress.reacted) { "反应需要待机、交战命令且本转折点未反应；对方仍有就绪特工" }
            } else require(o.ready) { "该特工已经待机" }
        }
        val expired = o.tokens.filter { it.duration == TokenDuration.NEXT_ACTIVATION_START }
        val updated = side(b, b.side(s).copy(operatives = b.side(s).operatives.map {
            if (it.memberId != member) it else {
                val kept = it.copy(tokens = it.tokens - expired.toSet())
                if (reaction) kept else kept.copy(order = if (engage) BattleOperativeState.ORDER_ENGAGE else BattleOperativeState.ORDER_CONCEAL)
            }
        }))
        val label = when {
            force -> "额外激活（${reason.ifBlank { "规则干预" }}）"
            reaction -> "反应"
            else -> "激活 · ${if (engage) "交战" else "隐匿"} · APL $apl"
        }
        var next = event(updated.copy(progress = b.progress.copy(active = Activation(s, member, apl, reaction = reaction))),
            "${b.side(s).playerName}：${o.displayName}$label")
        if (expired.isNotEmpty()) next = event(next, "${o.displayName}：${expired.joinToString("、") { it.label }} 到期移除")
        return next
    }

    /** Manual AP / APL correction during an activation (team rules, free actions). */
    fun adjustActivation(b: BattleRecord, limitDelta: Int, spentDelta: Int, reason: String): BattleRecord {
        val a = requireNotNull(b.progress.active)
        val next = a.copy(limit = (a.limit + limitDelta).coerceAtLeast(0), spent = (a.spent + spentDelta).coerceAtLeast(0))
        return event(b.copy(progress = b.progress.copy(active = next)),
            "AP 调整：已用 ${next.spent}/${next.limit}${if (reason.isBlank()) "" else "（$reason）"}")
    }

    fun action(b: BattleRecord, name: String, cost: Int, exception: String): BattleRecord {
        val a = requireNotNull(b.progress.active) { "尚未激活特工" }
        require(name.isNotBlank() && cost >= 0)
        val o = b.side(a.side).operatives.first { it.memberId == a.member }
        require(!o.incapacitated) { "特工已残废，请结束激活" }
        if (a.reaction) {
            require(a.actions.isEmpty() && cost == 1 && name != "戒备") { "反应只能执行一个当前费用为 1AP 的非戒备行动，实际不消耗 AP" }
        } else {
            require(a.spent + cost <= a.limit) { "剩余 AP 不足；可先记录 APL 修正" }
            require(name !in a.actions || exception.isNotBlank()) { "重复行动需要填写阵营或特殊规则依据" }
        }
        val movementConflict = (name == "冲锋" && a.actions.any { it in listOf("转移", "冲刺", "后撤") }) ||
            (name in listOf("转移", "冲刺", "后撤") && "冲锋" in a.actions) ||
            (name == "转移" && "后撤" in a.actions) || (name == "后撤" && "转移" in a.actions)
        require(!movementConflict || exception.isNotBlank()) { "移动行动冲突，请填写特殊规则依据" }
        require(o.order != "隐匿" || name !in listOf("射击", "冲锋") || exception.isNotBlank()) { "隐匿命令不能射击或冲锋；安静等例外请填写依据" }
        val next = a.copy(spent = a.spent + if (a.reaction) 0 else cost, actions = a.actions + name)
        return event(b.copy(progress = b.progress.copy(active = next)),
            "${o.displayName}：$name（${if (a.reaction) "反应，0" else cost.toString()} AP）${if (exception.isBlank()) "" else "；依据：$exception"}")
    }

    fun endActivation(b: BattleRecord): BattleRecord {
        val a = requireNotNull(b.progress.active)
        val s = b.side(a.side)
        val operative = s.operatives.first { it.memberId == a.member }
        val expired = operative.tokens.filter {
            it.duration == TokenDuration.THIS_ACTIVATION || it.duration == TokenDuration.NEXT_ACTIVATION_END
        }
        var next = side(b, s.copy(operatives = s.operatives.map {
            if (it.memberId == a.member) it.copy(ready = false, tokens = it.tokens - expired.toSet()) else it
        }))
        if (expired.isNotEmpty()) next = event(next, "${operative.displayName}：${expired.joinToString("、") { it.label }} 到期移除")
        fun clean(side: BattleSide) = side.copy(operatives = side.operatives.map { o ->
            o.copy(tokens = o.tokens.filterNot { it.duration == TokenDuration.THIS_ACTIVATION })
        })
        next = next.copy(sideA = clean(next.sideA), sideB = clean(next.sideB))
        return event(next.copy(progress = b.progress.copy(
            active = null, nextSide = other(a.side),
            reacted = if (a.reaction) b.progress.reacted + key(a.side, a.member) else b.progress.reacted
        )), "${s.operatives.first { it.memberId == a.member }.displayName}结束${if (a.reaction) "反应" else "激活"}")
    }

    fun skipReaction(b: BattleRecord): BattleRecord {
        require(b.phase == BattlePhase.FIREFIGHT && b.progress.active == null && !ready(b, b.progress.nextSide))
        return event(b.copy(progress = b.progress.copy(nextSide = other(b.progress.nextSide))),
            "${b.side(b.progress.nextSide).playerName}放弃反应")
    }
}

object BattleProgressCodec {
    fun encode(p: BattleProgress): JSONObject = JSONObject()
        .put("setup", p.setupStep).put("deploymentInitiative", p.deploymentInitiative)
        .put("deployedA", p.deployedA).put("deployedB", p.deployedB).put("nextSide", p.nextSide)
        .put("cardsA", JSONArray(p.cardsA)).put("cardsB", JSONArray(p.cardsB))
        .put("reacted", JSONArray(p.reacted.toList())).put("passes", p.planPasses)
        .put("plans", JSONArray(p.usedPlans.toList())).put("objectives", JSONObject(p.objectives))
        .put("killBonusApplied", p.killBonusApplied)
        .put("killLevelA", p.killLevelA).put("killLevelB", p.killLevelB)
        .put("active", p.active?.let { a -> JSONObject().put("side", a.side).put("member", a.member)
            .put("limit", a.limit).put("spent", a.spent).put("reaction", a.reaction).put("actions", JSONArray(a.actions)) })

    fun decode(j: JSONObject?): BattleProgress {
        if (j == null) return BattleProgress()
        fun ints(key: String): List<Int> { val a = j.optJSONArray(key) ?: return emptyList(); return List(a.length()) { a.getInt(it) } }
        fun strings(key: String): Set<String> { val a = j.optJSONArray(key) ?: return emptySet(); return List(a.length()) { a.getString(it) }.toSet() }
        val objectives = j.optJSONObject("objectives") ?: JSONObject()
        return BattleProgress(
            setupStep = j.optInt("setup"), deploymentInitiative = j.optString("deploymentInitiative"),
            deployedA = j.optInt("deployedA"), deployedB = j.optInt("deployedB"), nextSide = j.optString("nextSide", "A"),
            cardsA = ints("cardsA"), cardsB = ints("cardsB"), reacted = strings("reacted"),
            planPasses = j.optInt("passes"), usedPlans = strings("plans"),
            objectives = objectives.keys().asSequence().associateWith { objectives.getString(it) },
            killBonusApplied = j.optBoolean("killBonusApplied"),
            killLevelA = j.optInt("killLevelA"), killLevelB = j.optInt("killLevelB"),
            active = j.optJSONObject("active")?.let { a ->
                val actions = a.optJSONArray("actions") ?: JSONArray()
                Activation(a.getString("side"), a.getString("member"), a.getInt("limit"), a.optInt("spent"),
                    a.optBoolean("reaction"), List(actions.length()) { actions.getString(it) })
            }
        )
    }
}
