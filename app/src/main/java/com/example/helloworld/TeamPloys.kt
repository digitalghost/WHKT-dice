package com.example.helloworld

import android.content.Context
import java.io.File
import org.json.JSONObject

/** A team's ploy, offered as a one-tap preset when the player records using it. */
data class TeamPloy(val name: String, val summary: String, val body: String, val card: String?)

/**
 * Strategy and firefight ploys of a team. Text comes from `rules/<team>.json`; when a team has
 * no text yet, the names still come from its card images so the presets are never empty.
 */
object TeamPloys {
    const val STRATEGY = "战略计谋"
    const val FIREFIGHT = "交战计谋"
    const val FACTION_RULE = "阵营完整规则"
    const val FACTION_EQUIPMENT = "阵营装备"

    /** Pure-JVM loader for unit tests and non-Context callers. */
    @Suppress("UNCHECKED_CAST")
    fun of(teamId: String, category: String): List<TeamPloy> {
        val dir = sequenceOf(
            File("src/main/assets/rules"),
            File("app/src/main/assets/rules"),
            File("../app/src/main/assets/rules")
        ).firstOrNull { it.isDirectory } ?: return emptyList()
        val file = File(dir, "$teamId.json").takeIf { it.isFile } ?: return emptyList()
        val root = MiniJson.parseObject(file.readText())
        val entries = (root["entries"] as? List<Map<String, Any?>>).orEmpty()
        return entries
            .filter { (it["category"] as? String) == category }
            .map { e ->
                val name = displayName((e["title"] as? String).orEmpty())
                val body = cleanBody((e["body"] as? String).orEmpty())
                val cards = (e["cards"] as? List<String>).orEmpty()
                TeamPloy(name, summary(body), body, cards.firstOrNull())
            }
    }

    fun of(context: Context, teamId: String, category: String): List<TeamPloy> {
        val cardCategory = if (category == FACTION_RULE) "阵营规则" else category
        val cards = CardAssets.list(context, "team_rules")
            .filter { it.startsWith("$teamId--") && (it.contains("-$category-") || it.contains("-$cardCategory-")) }
            .sorted()
        val fromText = runCatching {
            val array = JSONObject(context.assets.open("rules/$teamId.json").bufferedReader().use { it.readText() })
                .getJSONArray("entries")
            (0 until array.length()).map(array::getJSONObject)
                .filter { it.getString("category") == category }
                .map { e ->
                    val name = displayName(e.getString("title"))
                    val body = cleanBody(e.getString("body"))
                    val linked = e.optJSONArray("cards")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
                    TeamPloy(name, summary(body), body, linked.firstOrNull() ?: cards.firstOrNull { key(it.cardTitle(cardCategory)) == key(name) })
                }
        }.getOrDefault(emptyList())
        val known = fromText.map { key(it.name) }.toSet()
        if (fromText.isNotEmpty()) return fromText
        val fromCards = cards.map { it.cardTitle(cardCategory) }
            .distinctBy { key(it) }
            .filter { key(it) !in known }
            .map { title -> TeamPloy(title, "", "", cards.first { it.cardTitle(cardCategory) == title }) }
        return fromText + fromCards
    }

    /** Strategy-phase gambits (0CP faction rules & operative strategic gambits). */
    fun strategicGambits(
        context: Context,
        teamId: String,
        activeOperativeIds: Set<String>? = null,
        equipmentNotes: String = ""
    ): List<TeamPloy> {
        val equipGambits = if (equipmentNotes.isBlank()) emptyList() else {
            of(context, teamId, FACTION_EQUIPMENT).filter { eq ->
                equipmentNotes.contains(eq.name) && ("战略计划" in eq.body || "战略计谋" in eq.body || "战略阶段" in eq.body)
            }
        }
        return filterStrategicGambits(teamId, of(context, teamId, FACTION_RULE), activeOperativeIds, equipGambits)
    }

    fun strategicGambits(teamId: String, activeOperativeIds: Set<String>? = null): List<TeamPloy> =
        filterStrategicGambits(teamId, of(teamId, FACTION_RULE), activeOperativeIds, emptyList())

    private fun filterStrategicGambits(
        teamId: String,
        factionRules: List<TeamPloy>,
        activeOperativeIds: Set<String>?,
        equipGambits: List<TeamPloy>
    ): List<TeamPloy> {
        val gambitTitles = setOf(
            "熟练技巧", "守卫命令", "剧目", "隧道", "方尖碑节点矩阵", "重生规程", "活体金属",
            "信仰之举", "浴血者", "国教布道", "全方位扰频器", "标记审判", "元素风暴", "变异", "教义条令", "教条命令"
        )
        val fromFaction = factionRules.filter { p ->
            p.name in gambitTitles ||
                "战略计划" in p.body || "战略计谋" in p.body || "战略阶段" in p.body || "就绪步骤" in p.body
        }
        val opGambits = RosterCatalog.team(teamId).operatives
            .filter { activeOperativeIds == null || it.id in activeOperativeIds }
            .flatMap { op ->
                AbilityCatalog.forOperative(op.id).filter { a ->
                    !a.factionRule && a.apCost == null &&
                        ("战略计划" in a.description || "战略阶段" in a.description || "就绪步骤" in a.description)
                }.map { a ->
                    TeamPloy("${a.name}（${op.name}）", summary(a.description), a.description, op.cardAsset?.removePrefix("team_rules/"))
                }
            }.distinctBy { it.name }
        return (fromFaction + opGambits + equipGambits).distinctBy { it.name }
    }

    /** Firefight-phase 0CP active faction rules (Aspect Techniques, Scarlet Stimms, SIA, etc.). */
    fun firefightRules(context: Context, teamId: String): List<TeamPloy> =
        filterFirefightRules(of(context, teamId, FACTION_RULE))

    fun firefightRules(teamId: String): List<TeamPloy> =
        filterFirefightRules(of(teamId, FACTION_RULE))

    private fun filterFirefightRules(factionRules: List<TeamPloy>): List<TeamPloy> {
        val activeNames = setOf(
            "支派技艺", "猩红活剂", "特制弹药", "突破扫荡", "暗影通道", "小巧灵敏", "私掠者支援资产", "技能点"
        )
        return factionRules.filter { it.name in activeNames }
    }

    fun subOptions(ploy: TeamPloy): List<String> = subOptions(ploy.name)

    /** Quick sub-option choices for ploys/gambits that ask the player to pick a mode/option. */
    fun subOptions(name: String): List<String> = when (displayName(name)) {
        "战团战术" -> listOf("1. 猛攻（近战撕裂）", "2. 决斗家（普骰挡暴击）", "3. 坚决（忽略APL修正与震荡）", "4. 隐匿（额外掩护豁免或升暴击）", "5. 机动（后撤减1AP/控围冲锋）", "6. 强健（射击防御5+暴击）", "7. 神射手（未移动爆矢精准1与严重）", "8. 攻城战专家（远程集中/近战防协助）")
        "战斗条令", "调整条令" -> listOf("毁灭条令（6\"外射击平衡）", "战术条令（6\"内射击平衡）", "突击条令（近战/反击平衡）")
        "教义条令", "教条命令" -> listOf("守护者条令（远程无休/近战劣化）", "征服者条令（近战无休/远程劣化）", "壁垒条令（3+普伤减1/移动-1\"）", "进攻者条令（移动+1\"/豁免恶化）", "平衡条令（无优化/无劣化）")
        "支派技艺" -> listOf("狂嚎女妖：灾难", "狂嚎女妖：泪雨", "狂嚎女妖：特技", "狂嚎女妖：失能尖啸", "狂嚎女妖：杀戮尖叫", "凶暴复仇者：切刀飓风", "凶暴复仇者：千刃之死", "凶暴复仇者：复仇者的守望", "凶暴复仇者：灵活且坚固", "凶暴复仇者：炽热亡火", "突击战蝎：耐心追踪，突然出手", "突击战蝎：突击并消匿", "突击战蝎：战蝎之眼", "突击战蝎：无情突击", "突击战蝎：伴影之裔")
        "基因炼金术" -> listOf("神经强化（命中改善）", "肾上腺激增（冲刺/APL）", "甲壳硬化（减伤）")
        "恶魔之力" -> listOf("奸奇（4+聚能护盾）", "科恩（近战无休）", "纳垢（全武器剧毒）", "色孽（移动+1\"）")
        "混沌印记", "混沌护身符" -> listOf("恐虐", "纳垢", "色孽", "奸奇", "无分")
        "剧目" -> listOf("史诗（近战残废敌方→近战平衡）", "情节剧（射击残废敌方→远程平衡）")
        "熟练技巧", "技能点" -> listOf("集火目标（远程严重）", "迅速出击（转移+1\"）", "沉着冷静（首次3+普伤减1）", "为了卡迪亚！（近战+1A/首次出击+1伤）")
        "以太守护" -> listOf("微光屏护（5+ 暴击豁免）", "预知偏折（重掷 1 枚防御骰）")
        "兵法", "兵法（老兵探路者）" -> listOf("蒙特’卡（移动 +1\"）", "考雍（隐匿免费标记光）")
        "守卫命令" -> listOf("瞄准！（远程无休）", "上刺刀！（近战无休）", "坚守阵地！（掩护重掷指定防御骰）", "冲！冲！冲！（转移+1\"）")
        "国教布道" -> listOf("神圣狂热", "救赎之火", "坚定信念")
        "侦查前锋", "前线侦察" -> listOf("战术移动", "分散注意", "重新部署", "转移", "绊索警报", "饵雷陷阱", "制定计划", "指定目标", "窥察")
        "特制弹药" -> listOf("爆炸 1\"（洪流武器除外）", "毁灭 1", "致命 5+", "关键穿刺 1", "撕裂", "严重")
        "任务战术" -> listOf("针对隐匿命令（获得平衡）", "针对交战命令（获得平衡）")
        else -> emptyList()
    }

    /** "以太守护（Aetherial Warding）" → "以太守护". */
    fun displayName(title: String): String = title.substringBefore('（').substringBefore('(').trim()

    internal fun cleanBody(body: String): String =
        body.removePrefix("计谋费用由玩家按采用的规则确认（资料接口未单列费用）。").trim()

    /** First rule sentence or ~60 characters (skipping leading lore paragraph/line when present). */
    internal fun summary(body: String): String {
        val ruleMarkers = listOf(
            "每当", "在", "选择", "如果", "当", "使用", "己方", "敌方", "您", "可",
            "1AP", "2AP", "0AP", "•", "-", "1.", "战略", "作为", "将", "直到", "本计谋", "本规则"
        )
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val ruleSource = when {
            lines.size >= 2 && ruleMarkers.none { lines.first().startsWith(it) } &&
                !("己方" in lines.first() || "敌方" in lines.first() || "1AP" in lines.first() || "您可以" in lines.first()) ->
                lines.drop(1).joinToString(" ")
            else -> {
                val paragraphs = body.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
                if (paragraphs.size >= 2) paragraphs.drop(1).joinToString(" ") else body
            }
        }
        val flat = ruleSource.replace(Regex("\\s+"), " ").trim()
        val end = flat.indexOfAny(charArrayOf('。', '；')).takeIf { it in 8..72 }
        return if (end != null) flat.substring(0, end + 1) else if (flat.length > 64) flat.take(62) + "…" else flat
    }

    private fun String.cardTitle(category: String) =
        substringAfter("-$category-")
            .removeSuffix(".png")
            .removeSuffix(".webp")
            .replace(Regex("-\\d+$"), "")

    private fun key(name: String) = name.filter { it.isLetterOrDigit() }
}
