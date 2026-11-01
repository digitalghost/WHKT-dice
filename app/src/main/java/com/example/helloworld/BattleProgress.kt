package com.example.helloworld

import org.json.JSONArray
import org.json.JSONObject

data class Activation(
    val side: String, val member: String, val limit: Int, val spent: Int = 0,
    val reaction: Boolean = false, val actions: List<String> = emptyList(),
    /** Ending this activation keeps the turn with the same player (rules that chain activations). */
    val keepTurn: Boolean = false
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
    /** Other activations open at the same time (joint activation); resumed when the active one ends. */
    val parked: List<Activation> = emptyList(),
    /** "side/member" the player must activate next (rules that force the next activation). */
    val mustActivate: String = "",
    val reacted: Set<String> = emptySet(),
    val planPasses: Int = 0,
    val usedPlans: Set<String> = emptySet(),
    val objectives: Map<String, String> = emptyMap(),
    val killBonusApplied: Boolean = false,
    val killLevelA: Int = 0,
    val killLevelB: Int = 0
) {
    val ploys: List<String>
        get() = usedPlans
            .filter { !it.endsWith("/${BattleFlow.PRIMARY_PLAN}") && !it.substringAfter("/").startsWith("交战:") }
            .map { "${it.substringBefore("/")}：${it.substringAfter("/")}" }
}

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

    /** Base APL with modifiers capped at ±1 in total (p21); stun and APL tokens are included. */
    fun effectiveApl(o: BattleOperativeState, manualDelta: Int = 0): Int {
        val tokenDelta = o.tokens.filter { it.name == "APL" }.sumOf { it.value ?: 0 }
        val delta = (manualDelta + tokenDelta - if (o.stunned) 1 else 0).coerceIn(-1, 1)
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
        require(b.turningPoint > 1 || (!name.startsWith("转移位置") && !name.startsWith("嘘——！") && !name.startsWith("侦察扫描")) || allowRepeat) {
            "${name}：不能在第一个转折点使用该计谋"
        }
        val id = key(s.id, name)
        require(allowRepeat || id !in b.progress.usedPlans) { "本转折点已使用该战略计划" }
        return event(side(b, s.copy(commandPoints = s.commandPoints - cost)).copy(
            progress = b.progress.copy(nextSide = other(s.id), planPasses = 0, usedPlans = b.progress.usedPlans + id)
        ), "${s.playerName}：$name（消耗 $cost CP）")
    }

    /** Key for firefight ploys in [BattleProgress.usedPlans], separate from strategic plans. */
    fun firefightKey(sideId: String, name: String) = key(sideId, "交战:$name")

    fun firefightPloyUsed(b: BattleRecord, sideId: String, name: String): Boolean =
        firefightKey(sideId, name) in b.progress.usedPlans

    /**
     * Firefight ploy (p28): spends CP during the firefight phase. Each ploy once per turning
     * point per player, except Command Re-roll, unless a rule allows otherwise.
     */
    fun firefightPloy(b: BattleRecord, sideId: String, name: String, cost: Int, allowRepeat: Boolean = false): BattleRecord {
        require(b.phase == BattlePhase.FIREFIGHT) { "交战计谋只能在交战阶段使用" }
        val s = b.side(sideId)
        val title = name.trim()
        require(title.isNotBlank() && cost >= 0) { "请输入计谋名称" }
        require(s.commandPoints >= cost) { "CP 不足：剩 ${s.commandPoints}CP" }
        val repeatable = title == COMMAND_REROLL
        require(repeatable || allowRepeat || !firefightPloyUsed(b, sideId, title)) { "本转折点已使用过「$title」" }
        return event(side(b, s.copy(commandPoints = s.commandPoints - cost)).copy(
            progress = b.progress.copy(usedPlans = if (repeatable) b.progress.usedPlans else b.progress.usedPlans + firefightKey(sideId, title))
        ), "${s.playerName}：交战计谋 $title（消耗 $cost CP，CP ${s.commandPoints} → ${s.commandPoints - cost}）")
    }

    const val COMMAND_REROLL = "指挥重掷"

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
        )), "${b.side(b.progress.nextSide).playerName} 跳过战略计划")
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
                require(ready(b, other(s)) && !o.ready &&
                    (o.order == BattleOperativeState.ORDER_ENGAGE || reason.isNotBlank()) &&
                    key(s, member) !in b.progress.reacted) { "反应需要待机、交战命令（隐匿命令反应需填写规则依据）且本转折点未反应；对方仍有就绪特工" }
            } else require(o.ready) { "该特工已经待机" }
            require(o.tokens.none { it.name == OperativeToken.BLOCKED }) { "${o.displayName}带有「${OperativeToken.BLOCKED}」标记，不能被激活" }
            val must = b.progress.mustActivate
            require(must.isBlank() || must.substringBefore("/") != s || must == key(s, member)) {
                "规则要求先激活 ${b.side(s).operatives.firstOrNull { key(s, it.memberId) == must }?.displayName ?: "指定特工"}"
            }
        }
        val forcedEngage = o.operativeId == "kt_kom_bomb_squig" || o.tokens.any { it.name == "狂暴" }
        val effectiveEngage = engage || forcedEngage
        val hasPoison = !reaction && o.tokens.any { it.name == "毒素" }
        val hasEnvenom = !reaction && o.tokens.any { it.name == "中毒" }
        val envenomDmg = if (hasEnvenom) (1..3).random() else 0
        val totalDot = (if (hasPoison) 1 else 0) + envenomDmg
        val expired = o.tokens.filter { it.duration == TokenDuration.NEXT_ACTIVATION_START }
        val updated = side(b, b.side(s).copy(operatives = b.side(s).operatives.map {
            if (it.memberId != member) it else {
                val kept = it.copy(tokens = it.tokens - expired.toSet())
                val ordered = if (reaction) kept else kept.copy(order = if (effectiveEngage) BattleOperativeState.ORDER_ENGAGE else BattleOperativeState.ORDER_CONCEAL)
                if (totalDot > 0) BattleRules.setWounds(ordered, ordered.currentWounds - totalDot) else ordered
            }
        }))
        val label = when {
            force -> "额外激活（${reason.ifBlank { "规则干预" }}）"
            reaction -> "反应" + if (reason.isNotBlank()) "（依据：$reason）" else ""
            else -> "激活 · ${if (effectiveEngage) "交战" else "隐匿"} · APL $apl"
        }
        val must = if (b.progress.mustActivate == key(s, member)) "" else b.progress.mustActivate
        var next = event(updated.copy(progress = b.progress.copy(mustActivate = must, active = Activation(s, member, apl, reaction = reaction))),
            "${b.side(s).playerName}：${o.displayName}$label")
        if (expired.isNotEmpty()) next = event(next, "${o.displayName}：${expired.joinToString("、") { it.label }} 到期移除")
        if (hasPoison) {
            val afterWounds = (o.currentWounds - 1).coerceAtLeast(0)
            next = BattleRules.syncKills(event(next, "【毒素】${o.displayName} 激活时受到 1 点毒素伤害（耐伤 ${o.currentWounds} → $afterWounds）"))
        }
        if (hasEnvenom) {
            val baseWounds = (o.currentWounds - if (hasPoison) 1 else 0).coerceAtLeast(0)
            val afterWounds = (baseWounds - envenomDmg).coerceAtLeast(0)
            next = BattleRules.syncKills(event(next, "【中毒】${o.displayName} 激活时受到 D3=$envenomDmg 点中毒伤害（耐伤 $baseWounds → $afterWounds）"))
        }
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
        val ruleOverride = exception.isNotBlank() && exception != "安静"
        val concealAllowed = exception.isNotBlank()
        require(!o.incapacitated || ruleOverride) { "特工已残废，请结束激活（死前行动等例外请填写规则依据）" }
        if (a.reaction) {
            require((a.actions.isEmpty() && cost == 1 && name != "戒备") || ruleOverride) {
                "反应只能执行一个当前费用为 1AP 的非戒备行动，实际不消耗 AP；规则允许时请填写依据"
            }
        } else {
            require(a.spent + cost <= a.limit) { "剩余 AP 不足；可先记录 APL 修正" }
            require(name !in a.actions || ruleOverride) { "重复行动需要填写阵营或特殊规则依据" }
        }
        val moved = a.actions.filter { it in listOf("转移", "冲刺", "后撤") }
        val allMoves = a.actions.filter { it in listOf("转移", "冲刺", "冲锋", "后撤") }
        require(name != "冲锋" || moved.isEmpty() || ruleOverride) {
            "核心规则 p14：同一激活中执行过${moved.joinToString("、")}后不能冲锋。冲锋本身可移动「移动属性 +2″」并进入敌方控制范围，接敌请直接用冲锋代替转移。"
        }
        val movementConflict = (name in listOf("转移", "冲刺", "后撤") && "冲锋" in a.actions) ||
            (name == "转移" && "后撤" in a.actions) || (name == "后撤" && "转移" in a.actions)
        require(!movementConflict || ruleOverride) { "核心规则 p13-14：这两个移动行动不能在同一激活中执行，请填写特殊规则依据" }
        val heavyConflict = when {
            name !in listOf("转移", "冲刺", "冲锋", "后撤") -> false
            o.tokens.any { it.name == "重型已射击" } -> true
            o.tokens.any { it.name == "重型（仅限冲刺）已射击" } && name != "冲刺" -> true
            o.tokens.any { it.name == "重型（仅限转移）已射击" } && name != "转移" -> true
            else -> false
        }
        require(!heavyConflict || ruleOverride) { "重型武器限制：本次激活已使用重型武器射击，不能执行该移动行动（特殊规则允许请填写依据）" }
        require(o.order != "隐匿" || name !in listOf("射击", "冲锋") || concealAllowed) { "隐匿命令不能射击或冲锋；安静等例外请填写依据" }
        val next = a.copy(spent = a.spent + if (a.reaction) 0 else cost, actions = a.actions + name)
        var out = event(b.copy(progress = b.progress.copy(active = next)),
            "${o.displayName}：$name（${if (a.reaction) "反应，0" else cost.toString()} AP）${if (exception.isBlank()) "" else "；依据：$exception"}")
        if (name in listOf("转移", "冲刺", "冲锋", "后撤") && allMoves.isEmpty()) {
            val mlCount = o.tokens.filter { it.name.startsWith("标记光") }.sumOf { it.value ?: 1 }
            if (mlCount > 0) {
                val newMl = mlCount - 1
                val curSide = out.side(a.side)
                val updatedSide = curSide.copy(operatives = curSide.operatives.map { op ->
                    if (op.memberId != o.memberId) op else {
                        val rest = op.tokens.filterNot { it.name.startsWith("标记光") }
                        val nextTokens = if (newMl > 0) rest + OperativeToken(name = "标记光", value = newMl, duration = TokenDuration.UNTIL_REMOVED, source = "标记光") else rest
                        op.copy(tokens = nextTokens)
                    }
                })
                out = event(side(out, updatedSide), "${o.displayName}执行移动行动，移除 1 枚标记光指示物（剩余 $newMl）")
            }
        }
        return out
    }

    fun endActivation(b: BattleRecord): BattleRecord {
        val a = requireNotNull(b.progress.active)
        val s = b.side(a.side)
        val operative = s.operatives.first { it.memberId == a.member }
        val expired = operative.tokens.filter {
            it.duration == TokenDuration.THIS_ACTIVATION || it.duration == TokenDuration.NEXT_ACTIVATION_END
        }
        val hadFrenzy = operative.tokens.any { it.name == "狂暴" }
        var next = side(b, s.copy(operatives = s.operatives.map {
            if (it.memberId == a.member) {
                val base = it.copy(ready = false, tokens = it.tokens - expired.toSet())
                if (hadFrenzy) base.copy(currentWounds = 0, incapacitated = true, tokens = base.tokens.filterNot { t -> t.name == "狂暴" }) else base
            } else it
        }))
        if (expired.isNotEmpty()) next = event(next, "${operative.displayName}：${expired.joinToString("、") { it.label }} 到期移除")
        if (hadFrenzy) next = BattleRules.syncKills(event(next, "【狂暴】${operative.displayName} ${if (a.reaction) "反应" else "激活"}结束，正式残废退场"))
        fun clean(side: BattleSide) = side.copy(operatives = side.operatives.map { o ->
            o.copy(tokens = o.tokens.filterNot { it.duration == TokenDuration.THIS_ACTIVATION })
        })
        next = next.copy(sideA = clean(next.sideA), sideB = clean(next.sideB))
        // A parked joint activation resumes; the turn passes only when no activation is left open.
        val resumed = b.progress.parked.firstOrNull()
        return event(next.copy(progress = b.progress.copy(
            active = resumed, parked = b.progress.parked.drop(1),
            nextSide = if (resumed != null || a.keepTurn) a.side else other(a.side),
            reacted = if (a.reaction) b.progress.reacted + key(a.side, a.member) else b.progress.reacted
        )), "${s.operatives.first { it.memberId == a.member }.displayName}结束${if (a.reaction) "反应" else "激活"}" +
            when {
                resumed != null -> "，继续 ${b.side(resumed.side).operatives.first { it.memberId == resumed.member }.displayName} 的联合激活"
                a.keepTurn -> "，按规则由同一玩家继续激活"
                else -> ""
            })
    }

    /**
     * The player whose turn it is passes it to the opponent without activating (rules such as
     * 恶兆 or 耐心埋伏, or only unactivatable operatives left).
     */
    fun skipActivation(b: BattleRecord, reason: String): BattleRecord {
        require(b.phase == BattlePhase.FIREFIGHT && b.progress.active == null) { "请先结束当前激活" }
        require(reason.isNotBlank()) { "请填写允许跳过的规则" }
        val s = b.progress.nextSide
        return event(b.copy(progress = b.progress.copy(nextSide = other(s))), "${b.side(s).playerName}跳过本次激活（依据：$reason）")
    }

    /** The next activation of [sideId] must be [memberId] (e.g. 阴魂不散的注视); blank clears it. */
    fun forceNextActivation(b: BattleRecord, sideId: String, memberId: String?, reason: String): BattleRecord {
        val target = memberId?.let { key(sideId, it) }.orEmpty()
        val name = memberId?.let { m -> b.side(sideId).operatives.first { it.memberId == m }.displayName }
        return event(b.copy(progress = b.progress.copy(mustActivate = target)),
            if (name == null) "取消指定激活" else "${b.side(sideId).playerName}下一次必须激活 $name（依据：${reason.ifBlank { "规则效果" }}）")
    }

    /** Ending the current activation keeps the turn with this player (rules that chain two activations). */
    fun keepTurn(b: BattleRecord, reason: String): BattleRecord {
        val a = requireNotNull(b.progress.active) { "尚未激活特工" }
        return event(b.copy(progress = b.progress.copy(active = a.copy(keepTurn = true))),
            "本次激活结束后由同一玩家继续激活（依据：${reason.ifBlank { "规则效果" }}）")
    }

    /**
     * Joint activation: a second operative of the same side activates while the first stays open.
     * The panel switches between them; each keeps its own AP and actions.
     */
    fun jointActivate(b: BattleRecord, memberId: String, apl: Int, engage: Boolean, reason: String): BattleRecord {
        val a = requireNotNull(b.progress.active) { "请先激活第一名特工" }
        require(memberId != a.member && b.progress.parked.none { it.member == memberId }) { "该特工已在激活中" }
        val o = b.side(a.side).operatives.first { it.memberId == memberId }
        require(!o.incapacitated) { "残废特工不能激活" }
        val side = b.side(a.side)
        val updated = side(b, side.copy(operatives = side.operatives.map {
            if (it.memberId == memberId) it.copy(order = if (engage) BattleOperativeState.ORDER_ENGAGE else BattleOperativeState.ORDER_CONCEAL) else it
        }))
        return event(updated.copy(progress = b.progress.copy(parked = b.progress.parked + a, active = Activation(a.side, memberId, apl))),
            "${side.playerName}：${o.displayName}联合激活 · ${if (engage) "交战" else "隐匿"} · APL $apl（依据：${reason.ifBlank { "规则效果" }}）")
    }

    private fun withOperative(b: BattleRecord, sideId: String, memberId: String, change: (BattleOperativeState) -> BattleOperativeState): BattleRecord {
        val s = b.side(sideId)
        return side(b, s.copy(operatives = s.operatives.map { if (it.memberId == memberId) change(it) else it }))
    }

    /** A rule adds an operative to a side mid-battle (e.g. 变异害虫, summons); it starts on standby unless set ready. */
    fun addOperative(b: BattleRecord, sideId: String, template: OperativeTemplate, ready: Boolean, countsForKill: Boolean, reason: String): BattleRecord {
        require(reason.isNotBlank()) { "请填写新增特工的规则依据" }
        val s = b.side(sideId)
        val same = s.operatives.count { it.operativeId == template.id }
        val o = BattleRules.fromTemplate(template, "added-" + java.util.UUID.randomUUID(), if (same == 0) template.name else "${template.name} ${same + 1}")
            .copy(ready = ready, excludedFromKill = !countsForKill)
        return event(side(b, s.copy(operatives = s.operatives + o)), "${s.playerName}：新增特工 ${o.displayName}（依据：$reason）")
    }

    /**
     * A rule turns an operative into another datacard (e.g. 变异). Identity, order, readiness and
     * tokens stay; wounds lost carry over when [keepLost] is set, otherwise it starts at full wounds.
     */
    fun replaceDatacard(b: BattleRecord, sideId: String, memberId: String, template: OperativeTemplate, keepLost: Boolean, reason: String): BattleRecord {
        require(reason.isNotBlank()) { "请填写更换数据卡的规则依据" }
        val before = b.side(sideId).operatives.first { it.memberId == memberId }
        val fresh = BattleRules.fromTemplate(template, memberId)
        val lost = before.maxWounds - before.currentWounds
        val wounds = if (keepLost) (template.wounds - lost).coerceAtLeast(1) else template.wounds
        val oldBaseName = templateNameOf(b, sideId, before.operativeId)
        val newDisplayName = if (oldBaseName.isNotBlank() && before.displayName.startsWith(oldBaseName)) {
            template.name + before.displayName.removePrefix(oldBaseName)
        } else {
            template.name
        }
        var next = withOperative(b, sideId, memberId) {
            it.copy(operativeId = template.id, maxWounds = template.wounds, currentWounds = wounds, apl = template.apl,
                weapons = fresh.weapons, displayName = newDisplayName)
        }
        val active = next.progress.active
        if (active != null && active.side == sideId && active.member == memberId && !active.reaction) {
            val aplDiff = template.apl - before.apl
            if (aplDiff != 0) {
                next = next.copy(progress = next.progress.copy(active = active.copy(limit = (active.limit + aplDiff).coerceAtLeast(1))))
            }
        }
        return event(next, "${before.displayName}：更换数据卡为 ${template.name}，耐伤 $wounds/${template.wounds}（依据：$reason）")
    }

    private fun templateNameOf(b: BattleRecord, sideId: String, operativeId: String) =
        RosterCatalog.operative(RosterCatalog.team(b.side(sideId).teamId), operativeId)?.name ?: ""

    /**
     * A rule grants a weapon that is not on the operative's datacard (equipment, borrowed weapons,
     * support assets). It is added to this operative's weapons for the rest of the battle.
     */
    fun grantWeapon(b: BattleRecord, sideId: String, memberId: String, weapon: WeaponOption, reason: String): BattleRecord {
        require(reason.isNotBlank()) { "请填写授予武器的规则依据" }
        WeaponProfileParser.parse(weapon.profile)
        val o = b.side(sideId).operatives.first { it.memberId == memberId }
        val id = "granted-${weapon.id}"
        require(o.weapons.none { it.id == id }) { "${o.displayName}已经拥有${weapon.name}" }
        val snap = BattleRules.snapshot(weapon).copy(id = id)
        return event(withOperative(b, sideId, memberId) { it.copy(weapons = it.weapons + snap) },
            "${o.displayName}：获得武器 ${weapon.name}（${weapon.profile}）（依据：$reason）")
    }

    fun removeWeapon(b: BattleRecord, sideId: String, memberId: String, weaponId: String): BattleRecord {
        val o = b.side(sideId).operatives.first { it.memberId == memberId }
        val w = o.weapons.first { it.id == weaponId }
        return event(withOperative(b, sideId, memberId) { it.copy(weapons = it.weapons - w) }, "${o.displayName}：移除武器 ${w.name}")
    }

    /** Switch which of the jointly activated operatives the panel records actions for. */
    fun switchJoint(b: BattleRecord, memberId: String): BattleRecord {
        val a = requireNotNull(b.progress.active)
        val target = b.progress.parked.firstOrNull { it.member == memberId } ?: return b
        return b.copy(progress = b.progress.copy(active = target, parked = b.progress.parked - target + a))
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
        .put("active", p.active?.let(::encodeActivation))
        .put("parked", JSONArray().apply { p.parked.forEach { put(encodeActivation(it)) } })
        .put("mustActivate", p.mustActivate)

    private fun encodeActivation(a: Activation) = JSONObject().put("side", a.side).put("member", a.member)
        .put("limit", a.limit).put("spent", a.spent).put("reaction", a.reaction).put("actions", JSONArray(a.actions))
        .put("keepTurn", a.keepTurn)

    private fun decodeActivation(a: JSONObject): Activation {
        val actions = a.optJSONArray("actions") ?: JSONArray()
        return Activation(a.getString("side"), a.getString("member"), a.getInt("limit"), a.optInt("spent"),
            a.optBoolean("reaction"), List(actions.length()) { actions.getString(it) }, a.optBoolean("keepTurn"))
    }

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
            active = j.optJSONObject("active")?.let(::decodeActivation),
            parked = j.optJSONArray("parked")?.let { a -> List(a.length()) { decodeActivation(a.getJSONObject(it)) } }.orEmpty(),
            mustActivate = j.optString("mustActivate")
        )
    }
}
