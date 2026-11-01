package com.example.helloworld

data class RuleDefinition(
    val id: String,
    val name: String,
    val description: String
)

object RuleGlossary {
    private val rules = listOf(
        rule("torrent_0", "洪流 0\"", "“洪流 0\"”意味着不能选择次要目标，但在所有其他规则的判断中，此武器仍拥有洪流规则，例如“拥挤堡垒”规则（见核心规则的杀戮区：伏库斯）。"),
        rule("mindburn", "焚却理智", "关键成功造成伤害后，为作为攻击目标的特工放置焚却理智标识。其武器命中恶化 1，不与受创叠加；持续到其下次激活结束、其残废或另一名己方特工再次使用此武器，以先发生者为准。"),
        rule("shield", "盾牌", "装备奸角兽之刃和盾牌时，特工豁免为 4+；使用该武器近战或反击时，每个格挡可以格挡两个未结算成功。"),
        rule("silent", "安静", "处于隐匿命令时也可以使用这件远程武器射击。"),
        rule("blast", "爆炸", "完成对主要目标的射击后，分别射击其指定距离内、且对主要目标可见的其他特工。"),
        rule("brutal", "残暴", "对手只能使用暴击来格挡这件近战武器的攻击。"),
        rule("piercing_crit", "关键穿刺", "只有保留至少一个暴击时才生效；防御方少掷指定数量的防御骰。"),
        rule("piercing", "穿刺", "防御方少掷指定数量的防御骰。"),
        rule("range", "范围", "只有位于指定距离内的特工才能成为有效目标。"),
        rule("hot", "过热", "使用后掷一枚 D6；若结果低于命中属性，使用者承受该点数两倍的伤害。同一行动多次使用只检定一次。"),
        rule("torrent", "洪流", "除主要目标外，还可以分别射击其指定距离内、且不在其他友方特工控制范围内的有效目标。"),
        rule("devastating", "毁灭", "每个保留的暴击立即造成指定伤害，之后该暴击仍继续正常结算。"),
        rule("saturate", "集中", "防御方不能自动保留掩护豁免。"),
        rule("accurate", "精准", "最多将指定数量的攻击骰不经投掷直接保留为普通成功。"),
        rule("balanced", "均衡", "可以重投一枚攻击骰。"),
        rule("rending", "撕裂", "若已经保留暴击，可以把一个普通成功改为暴击。"),
        rule("ceaseless", "无休", "选择一个骰面，重投任意数量显示该点数的攻击骰。"),
        rule("severe", "严重", "若尚未保留暴击，把一个普通成功改为暴击；可触发毁灭与关键穿刺，不能触发重击与撕裂。"),
        rule("limited", "有限", "整场战斗只能使用指定次数；同一行动内多次射击只计一次。"),
        rule("stun", "眩晕", "若保留了暴击，目标 APL 减 1，持续到其下一次激活结束。"),
        rule("shock", "震荡", "每次结算第一次用暴击出击时，舍弃对手一个未结算普通成功；没有普通成功则舍弃一个暴击。"),
        rule("lethal", "致命", "攻击骰达到标注的点数时就视为暴击，而不是通常的 6。"),
        rule("punishing", "重击", "若已经保留暴击，可以把一个失败改为普通成功。"),
        rule("heavy", "重型", "本次激活中移动过便不能使用，使用后也不能再移动；规则注明的特定移动除外。"),
        rule("seek", "追踪", "选择目标时忽略规则注明的掩护地形；不会移除目标已经获得的掩护豁免。"),
        rule("relentless", "毫不留情", "可以重投任意攻击骰。"),
        rule("poison_pm", "毒素", "瘟疫战士规则：这件武器以任意成功造成伤害后，目标获得毒素标识；带标识的特工激活时受到 1 点伤害。"),
        rule("toxic_pm", "剧毒", "瘟疫战士规则：若目标在行动开始时已有己方毒素标识，这件武器的普通和暴击伤害都加 1。"),
        rule("poison_rav", "中毒", "蛇虫规则：这件武器以暴击造成伤害后，目标获得毒素标识；带标识的特工激活时受到 D3 点伤害。"),
        rule("psychic", "灵能", "这是一件灵能武器。该标签本身不改变骰子，其他规则可能引用它。"),
        rule("crush", "粉碎", "每次出击时双方掷骰对抗；若使用者获胜，按点数差额追加伤害，最多追加 3 点。对耐伤 9 或更低的目标，使用者结果加 1。"),
        rule("explosive_squig", "爆炸物", "特种兵（炸弹史奎格）规则：每当此特工使用此武器执行射击行动时，在该行动所有攻击结算完毕后，此特工丧失行动能力。")
    )

    private val aliases = linkedMapOf(
        "洪流 0\"" to "洪流 0\"",
        "洪流0\"" to "洪流 0\"",
        "焚却理智" to "焚却理智",
        "盾牌" to "盾牌",
        "关键穿刺" to "关键穿刺",
        "穿刺" to "穿刺",
        "致命" to "致命",
        "范围" to "范围",
        "距离" to "范围",
        "爆炸物" to "爆炸物",
        "爆炸" to "爆炸",
        "洪流" to "洪流",
        "毁灭" to "毁灭",
        "精准" to "精准",
        "有限" to "有限",
        "追踪" to "追踪",
        "均衡" to "均衡",
        "平衡" to "均衡",
        "毫不留情" to "毫不留情",
        "无休" to "无休",
        "重型" to "重型",
        "安静" to "安静",
        "集中" to "集中",
        "残暴" to "残暴",
        "过热" to "过热",
        "撕裂" to "撕裂",
        "震荡" to "震荡",
        "眩晕" to "眩晕",
        "晕眩" to "眩晕",
        "严重" to "严重",
        "重击" to "重击",
        "毒素" to "毒素",
        "剧毒" to "剧毒",
        "中毒" to "中毒",
        "灵能" to "灵能",
        "粉碎" to "粉碎"
    )

    private val coreRuleNames = setOf(
        "洪流 0\"", "安静", "爆炸", "残暴", "关键穿刺", "穿刺", "范围", "过热",
        "毫不留情", "洪流", "毁灭", "集中", "精准", "均衡", "撕裂", "无休",
        "严重", "有限", "眩晕", "震荡", "致命", "重击", "重型", "追踪"
    )

    fun definitionFor(label: String, teamId: String? = null): RuleDefinition? {
        val trimmed = label.trim()
        val withoutDistancePrefix = trimmed.replace(Regex("^[0-9]+\\s*[\"″“”]\\s*"), "")
        val canonical = aliases.entries.firstOrNull {
            trimmed.startsWith(it.key) || withoutDistancePrefix.startsWith(it.key)
        }?.value
        if (canonical != null && canonical in coreRuleNames) {
            val core = rules.firstOrNull { it.name == canonical }
            if (core != null) {
                val teamOverride = if (trimmed.removeSuffix("*").trim() == canonical) {
                    TeamDataLoader.weaponRule(canonical, teamId)
                } else null
                return if (teamOverride != null) {
                    core.copy(description = "${core.description}\n\n${teamOverride.description}")
                } else core
            }
        }
        TeamDataLoader.weaponRule(trimmed, teamId)?.let { return it }
        return canonical?.let { c -> rules.firstOrNull { it.name == c } }
    }

    fun all(): List<RuleDefinition> = rules

    private fun rule(id: String, name: String, description: String) = RuleDefinition(id, name, description)
}

object AbilityCatalog {
    fun forOperative(operativeId: String): List<AbilityDefinition> =
        TeamDataLoader.abilities(operativeId).orEmpty()
}
