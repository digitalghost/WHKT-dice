package com.example.helloworld

import android.content.Intent
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Flow recording UI. Distances, target legality and faction exceptions remain player adjudications. */
class BattleControls(
    private val activity: AppCompatActivity,
    private val state: () -> BattleRecord,
    private val commit: (BattleRecord) -> Unit,
    private val undo: () -> Unit
) {
    private fun message(text: String) {
        MaterialAlertDialogBuilder(activity).setMessage(text).setPositiveButton("知道了", null).show()
    }
    private fun run(block: () -> Unit) {
        try { block() } catch (e: IllegalArgumentException) { message(e.message ?: "当前步骤不能执行此操作") }
        catch (e: IllegalStateException) { message(e.message ?: "当前步骤不能执行此操作") }
    }
    private fun button(box: LinearLayout, text: String, block: () -> Unit) {
        val density = activity.resources.displayMetrics.density
        val columns = if (activity.resources.configuration.smallestScreenWidthDp >= 600) 3 else 2
        val last = box.getChildAt(box.childCount - 1) as? LinearLayout
        val row = if (last?.tag == "actions" && last.childCount < columns) last else LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; tag = "actions"; box.addView(this)
        }
        row.addView(Button(activity).apply {
            this.text = text
            isAllCaps = false
            textSize = 12f
            minHeight = (52 * density).toInt()
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            background = GrimdarkSkins.button(activity, ConsoleSurface.CELL)
            setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.primary_light))
            setOnClickListener { run(block) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins((3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt())
        })
    }
    private fun choose(title: String, items: List<String>, block: (Int) -> Unit) {
        MaterialAlertDialogBuilder(activity).setTitle(title).setItems(items.toTypedArray()) { _, i -> run { block(i) } }
            .setNegativeButton("取消", null).show()
    }
    private fun form(title: String, fields: List<Pair<String, String>>, block: (List<String>) -> Unit) {
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val inputs = fields.map { (label, value) ->
            box.addView(TextView(activity).apply { text = label })
            EditText(activity).apply { setText(value); inputType = InputType.TYPE_CLASS_TEXT; box.addView(this) }
        }
        val d = MaterialAlertDialogBuilder(activity).setTitle(title)
            .setView(ScrollView(activity).apply { addView(box) }).setPositiveButton("记录", null).setNegativeButton("取消", null).create()
        d.setOnShowListener {
            d.getButton(-1).setOnClickListener { run { block(inputs.map { it.text.toString().trim() }); d.dismiss() } }
        }
        d.show()
    }

    fun panel(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val b = state()
        if (!b.completed) {
            if (b.turningPoint == 0) when (b.progress.setupStep) {
                0 -> button(this, "战前拼骰 · 记录先手与降落区") {
                    choose("战前拼骰胜者决定哪方先手？", listOf(b.sideA.playerName, b.sideB.playerName)) {
                        commit(BattleFlow.deployment(state(), if (it == 0) "A" else "B"))
                    }
                }
                1 -> {
                    addView(TextView(activity).apply { text = "先手方先选降落区；双方公开特工及装备（各最多四项），各得 2CP。装备逐件轮流部署，战术行动按揭示时机保密。" })
                    button(this, "记录降落区 / 装备部署确认") {
                        form("确认部署装备", listOf("A 方降落区" to b.dropZoneA, "B 方降落区" to b.dropZoneB,
                            "装备部署记录（逐件顺序或位置）" to "")) { v ->
                            require(v[0].isNotBlank() && v[1].isNotBlank())
                            commit(BattleFlow.event(state().copy(dropZoneA = v[0], dropZoneB = v[1],
                                progress = state().progress.copy(setupStep = 2)), "双方公开特工/装备并完成装备部署：${v[2]}"))
                        }
                    }
                }
                2 -> {
                    val s = b.progress.nextSide
                    val done = if (s == "A") b.progress.deployedA else b.progress.deployedB
                    button(this, "${b.side(s).playerName} · 确认部署第 ${done + 1} 组（${BattleFlow.groups(b.side(s).operatives.size)[done]} 人）") {
                        commit(BattleFlow.deployGroup(state()))
                    }
                }
                else -> addView(TextView(activity).apply { text = "双方三组部署完成，开始第一转折点拼骰。" })
            }
            if (b.phase == BattlePhase.INITIATIVE) button(this, "先手权卡 · 查看 / 使用") { cards() }
            if (b.phase == BattlePhase.STRATEGY) {
                addView(TextView(activity).apply { text = if (b.progress.planPasses < 2) "当前计划方：${b.side(b.progress.nextSide).playerName}" else "双方连续跳过，可进入交战阶段" })
                button(this, "记录战略计划 / 计谋") {
                    form("战略计划（普通计划可为 0CP）", listOf("名称" to "", "CP 费用" to "1")) {
                        commit(BattleFlow.plan(state(), it[0], it[1].toIntOrNull() ?: error("请输入整数 CP")))
                    }
                }
                button(this, "当前玩家跳过计划") { commit(BattleFlow.passPlan(state())) }
            }
            if (b.phase == BattlePhase.FIREFIGHT) {
                val a = b.progress.active
                if (a == null) {
                    addView(TextView(activity).apply { text = "轮到：${b.side(b.progress.nextSide).playerName}；点击特工的“激活/反应”开始。" })
                    if (!BattleFlow.ready(b, b.progress.nextSide)) button(this, "放弃反应") { commit(BattleFlow.skipReaction(state())) }
                } else {
                    val o = b.side(a.side).operatives.first { it.memberId == a.member }
                    addView(TextView(activity).apply { text = "${o.displayName} · ${if (a.reaction) "反应：一个 1AP 行动，移动至多 2 英寸" else "AP ${a.spent}/${a.limit}"}" })
                    button(this, "记录行动 / 任务行动") { recordAction() }
                    if (!a.reaction) button(this, "调整本次 APL / AP 上限") {
                        form("APL 修正（通常至多 ±1）", listOf("本次 AP 上限" to a.limit.toString(), "规则依据" to "")) {
                            val limit = it[0].toIntOrNull() ?: error("请输入整数")
                            val base = RosterCatalog.operative(RosterCatalog.team(b.side(a.side).teamId), o.operativeId)?.apl ?: a.limit
                            require(limit in (base - 1)..(base + 1) && it[1].isNotBlank()) { "APL 修正最多 ±1，且需记录依据" }
                            commit(BattleFlow.event(state().copy(progress = state().progress.copy(active = a.copy(limit = limit))),
                                "${o.displayName}本次 APL 改为 $limit：${it[1]}"))
                        }
                    }
                    button(this, "结束本次${if (a.reaction) "反应" else "激活"}") { commit(BattleFlow.endActivation(state())) }
                }
                button(this, "交战计谋 / CP 消耗") {
                    choose("使用方", listOf(b.sideA.playerName, b.sideB.playerName)) { index ->
                        val id = if (index == 0) "A" else "B"
                        form("记录交战计谋", listOf("名称" to "", "CP 费用" to "1", "目标 / 规则效果" to "")) { v ->
                            val s = state().side(id)
                            val cost = v[1].toIntOrNull() ?: error("请输入整数")
                            require(v[0].isNotBlank() && cost >= 0 && cost <= s.commandPoints)
                            commit(BattleFlow.event(BattleFlow.side(state(), s.copy(commandPoints = s.commandPoints - cost)),
                                "${s.playerName}使用${v[0]}，消耗 $cost CP；${v[2]}"))
                        }
                    }
                }
            }
            button(this, "目标标识 / 携带标识 / 任务状态") { objectives() }
            button(this, "按击杀等级核对击杀 VP") { syncKillScore() }
        }
        button(this, "撤销上一步（含阶段、CP 和状态）") { undo() }
        button(this, "完整时间线 / 导出对局") {
            val text = report(state())
            MaterialAlertDialogBuilder(activity).setTitle("完整对局记录")
                .setView(ScrollView(activity).apply { addView(TextView(activity).apply { this.text = text; setTextIsSelectable(true); setPadding(24, 16, 24, 16) }) })
                .setPositiveButton("关闭", null).setNeutralButton("导出 / 分享") { _, _ ->
                    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    }, "导出对局"))
                }.show()
        }
    }

    fun activate(side: BattleSide, o: BattleOperativeState) = run {
        val b = state()
        val apl = RosterCatalog.operative(RosterCatalog.team(side.teamId), o.operativeId)?.apl ?: 2
        if (!BattleFlow.ready(b, side.id)) commit(BattleFlow.activate(b, side.id, o.memberId, apl, true))
        else choose("激活时选择命令", listOf("交战", "隐匿")) {
            commit(BattleFlow.activate(state(), side.id, o.memberId, apl, it == 0))
        }
    }

    fun melee(side: BattleSide, actor: BattleOperativeState, target: BattleOperativeState, weapon: BattleWeaponSnapshot) {
        val opponent = state().opponent(side.id)
        val weapons = target.weapons.filter { it.isMelee }
        choose("双方使用攻击骰；选择对方近战武器", weapons.map { "${it.name} · ${it.profile}" } + "无法反击 / 特殊规则") { index ->
            val response = weapons.getOrNull(index)?.name ?: "无法反击 / 特殊规则"
            form("轮流出击 / 格挡后记录（AP 在行动记录中扣除）", listOf(
                "己方攻击骰 / 出击格挡过程" to "", "对方攻击骰 / 出击格挡过程" to "",
                "己方剩余耐伤" to actor.currentWounds.toString(), "对方剩余耐伤" to target.currentWounds.toString()
            )) { v ->
                val aw = v[2].toIntOrNull() ?: error("请输入耐伤整数")
                val tw = v[3].toIntOrNull() ?: error("请输入耐伤整数")
                require(aw in 0..actor.maxWounds && tw in 0..target.maxWounds)
                fun damage(s: BattleSide, id: String, wounds: Int) = s.copy(operatives = s.operatives.map {
                    if (it.memberId == id) it.copy(currentWounds = wounds, incapacitated = wounds == 0, ready = it.ready && wounds > 0) else it
                })
                val b = BattleFlow.side(BattleFlow.side(state(), damage(state().side(side.id), actor.memberId, aw)),
                    damage(state().side(opponent.id), target.memberId, tw))
                commit(BattleFlow.event(b, "近战：${actor.displayName}（${weapon.name}）对${target.displayName}（$response）；己方骰/过程：${v[0]}；对方骰/过程：${v[1]}；耐伤 $aw / $tw"))
            }
        }
    }

    private fun recordAction() {
        val names = listOf("转移", "冲刺", "后撤", "冲锋", "射击", "近战", "拾取标识", "放置标识", "戒备", "任务 / 独特行动")
        choose("选择行动（完成实体行动后记录）", names) { i ->
            form("行动记录", listOf("行动名称" to if (i == 9) "" else names[i],
                "有效 AP 费用（无消耗填 0；反应填 1）" to if (i == 2) "2" else "1",
                "特殊规则依据 / 目标 / 标识" to "")) {
                commit(BattleFlow.action(state(), it[0], it[1].toIntOrNull() ?: error("请输入整数"), it[2]))
            }
        }
    }

    private fun cards() {
        val b = state()
        choose("选择持卡方", listOf("${b.sideA.playerName}：${b.progress.cardsA.map(::cardName)}",
            "${b.sideB.playerName}：${b.progress.cardsB.map(::cardName)}")) { i ->
            val s = if (i == 0) "A" else "B"
            val cards = if (i == 0) b.progress.cardsA else b.progress.cardsB
            if (cards.isEmpty()) message("没有可用先手权卡")
            else choose("败方开始，交替用卡或让过，连续让过后确认最终胜者", cards.map(::cardName)) { c ->
                form("记录修正结果（重投会覆盖此前修正）", listOf("最终点数 / 修正详情" to "")) {
                    require(it[0].isNotBlank())
                    commit(BattleFlow.useCard(state(), s, cards[c], it[0]))
                }
            }
        }
    }
    private fun cardName(i: Int) = if (i == 0) "重投" else "±$i"

    private fun objectives() {
        val b = state()
        val entries = b.progress.objectives.entries.toList()
        choose("目标与任务状态（控制权由双方按 APL 确认）", entries.map { "${it.key}：${it.value}" } + "新增标识 / 状态") { i ->
            form("记录目标、携带者或任务进度", listOf("标识名称" to (entries.getOrNull(i)?.key ?: ""),
                "控制方 / 携带者 / 状态 / 次数" to (entries.getOrNull(i)?.value ?: ""))) { v ->
                require(v.all { it.isNotBlank() })
                commit(BattleFlow.event(state().copy(progress = state().progress.copy(
                    objectives = state().progress.objectives + (v[0] to v[1])
                )), "标识更新：${v[0]} · ${v[1]}"))
            }
        }
    }

    private fun syncKillScore() {
        val b = state()
        require(b.sideA.operatives.size in 5..14 && b.sideB.operatives.size in 5..14) { "官方表覆盖 5–14 人；特殊编成请按阵营规则手动计分" }
        val a = KillScoreCalculator.killLevel(b.sideB.operatives.size, b.sideB.incapacitatedCount)
        val c = KillScoreCalculator.killLevel(b.sideA.operatives.size, b.sideA.incapacitatedCount)
        MaterialAlertDialogBuilder(activity).setTitle("确认击杀 VP：$a : $c")
            .setMessage("此处按当前残废人数核对。忽略击杀的特工、复活或其他例外请手动修正。终局还需给击杀等级较高的一方 +1VP。")
            .setPositiveButton("应用") { _, _ -> run {
                commit(BattleFlow.event(b.copy(sideA = b.sideA.copy(score = b.sideA.score.copy(kill = a)),
                    sideB = b.sideB.copy(score = b.sideB.score.copy(kill = c))), "按击杀等级核对 VP：$a : $c"))
            } }.setNegativeButton("取消", null).show()
    }

    fun finishBattle() {
        val b = state()
        if (b.completed) return
        form("终局核对", listOf("A 方最终击杀等级（0–5）" to minOf(b.sideA.score.kill, 5).toString(),
            "B 方最终击杀等级（0–5）" to minOf(b.sideB.score.kill, 5).toString())) { values ->
            val a = values[0].toIntOrNull() ?: error("请输入整数")
            val c = values[1].toIntOrNull() ?: error("请输入整数")
            require(a in 0..5 && c in 0..5)
            require(b.sideA.primaryOp != null && b.sideB.primaryOp != null) { "请先记录双方主要行动" }
            val final = b.copy(sideA = b.sideA.copy(score = b.sideA.score.copy(kill = KillScoreCalculator.finalScore(a, c))),
                sideB = b.sideB.copy(score = b.sideB.score.copy(kill = KillScoreCalculator.finalScore(c, a))),
                progress = b.progress.copy(killBonusApplied = true, active = null))
            commit(BattleRules.complete(BattleFlow.event(final, "最终击杀等级 $a : $c；较高方获得 1VP；主要行动奖励为一半向上取整")))
        }
    }

    private fun report(b: BattleRecord): String = buildString {
        val time=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault())
        appendLine(b.name); appendLine("${b.mission} · ${b.killzone} · ${b.keyOp}")
        listOf(b.sideA, b.sideB).forEach { s ->
            appendLine("${s.playerName} · ${s.rosterName}：CP ${s.commandPoints} / VP ${if (b.completed) s.totalScore else s.currentScore}")
            if (b.completed) appendLine("主要行动：${s.primaryOp?.label}；奖励 ${s.score.bonus(s.primaryOp)}")
            s.operatives.forEach { appendLine("${it.displayName}：${it.currentWounds}/${it.maxWounds} · ${it.order} · ${if (it.incapacitated) "残废" else if (it.ready) "就绪" else "待机"}") }
        }
        b.progress.objectives.forEach { (k, v) -> appendLine("$k：$v") }
        b.log.forEach { appendLine("${time.format(Date(it.timestamp))} · TP${it.turningPoint} · ${it.phase.label} · ${it.message}") }
    }
}
