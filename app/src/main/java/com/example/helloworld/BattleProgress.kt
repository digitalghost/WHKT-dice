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
    val killBonusApplied: Boolean = false
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

    fun initiative(b: BattleRecord, winner: String, chosen: String): BattleRecord {
        require(b.turningPoint > 0 || b.progress.setupStep == 3) { "请先完成部署" }
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

    fun plan(b: BattleRecord, name: String, cost: Int): BattleRecord {
        require(b.phase == BattlePhase.STRATEGY && b.progress.planPasses < 2)
        val s = b.side(b.progress.nextSide)
        require(name.isNotBlank() && cost >= 0 && s.commandPoints >= cost) { "请输入计划名称并确认 CP 足够" }
        val id = key(s.id, name)
        require(id !in b.progress.usedPlans) { "本转折点已使用该战略计划" }
        return event(side(b, s.copy(commandPoints = s.commandPoints - cost)).copy(
            progress = b.progress.copy(nextSide = other(s.id), planPasses = 0, usedPlans = b.progress.usedPlans + id)
        ), "${s.playerName}：$name（消耗 $cost CP）")
    }

    fun passPlan(b: BattleRecord): BattleRecord {
        require(b.phase == BattlePhase.STRATEGY && b.progress.planPasses < 2)
        require(b.turningPoint != 1 || b.side(b.progress.nextSide).primaryOp != null) { "第一转折点请先秘密选择己方主要行动" }
        return event(b.copy(progress = b.progress.copy(
            nextSide = other(b.progress.nextSide), planPasses = b.progress.planPasses + 1
        )), "${b.side(b.progress.nextSide).playerName}跳过战略计划")
    }

    fun activate(b: BattleRecord, s: String, member: String, apl: Int, engage: Boolean): BattleRecord {
        require(b.phase == BattlePhase.FIREFIGHT && b.progress.active == null) { "请先结束当前激活" }
        require(s == b.progress.nextSide) { "尚未轮到该玩家" }
        val o = b.side(s).operatives.first { it.memberId == member }
        require(!o.incapacitated)
        val reaction = !ready(b, s)
        if (reaction) {
            require(ready(b, other(s)) && o.order == BattleOperativeState.ORDER_ENGAGE &&
                key(s, member) !in b.progress.reacted) { "反应需要待机、交战命令且本转折点未反应；对方仍有就绪特工" }
        } else require(o.ready) { "该特工已经待机" }
        val updated = side(b, b.side(s).copy(operatives = b.side(s).operatives.map {
            if (it.memberId == member && !reaction) it.copy(order = if (engage) "交战" else "隐匿") else it
        }))
        return event(updated.copy(progress = b.progress.copy(active = Activation(s, member, apl, reaction = reaction))),
            "${b.side(s).playerName}：${o.displayName}开始${if (reaction) "反应" else "激活"}")
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
        val next = side(b, s.copy(operatives = s.operatives.map { if (it.memberId == a.member) it.copy(ready = false) else it }))
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
            active = j.optJSONObject("active")?.let { a ->
                val actions = a.optJSONArray("actions") ?: JSONArray()
                Activation(a.getString("side"), a.getString("member"), a.getInt("limit"), a.optInt("spent"),
                    a.optBoolean("reaction"), List(actions.length()) { actions.getString(it) })
            }
        )
    }
}
