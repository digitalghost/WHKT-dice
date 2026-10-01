package com.example.helloworld

data class RuleDefinition(
    val id: String,
    val name: String,
    val description: String
)

object RuleGlossary {
    private val rules = listOf(
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
        rule("crush", "粉碎", "每次出击时双方掷骰对抗；若使用者获胜，按点数差额追加伤害，最多追加 3 点。对耐伤 9 或更低的目标，使用者结果加 1。")
    )

    private val aliases = linkedMapOf(
        "焚却理智" to "焚却理智",
        "盾牌" to "盾牌",
        "关键穿刺" to "关键穿刺",
        "穿刺" to "穿刺",
        "致命" to "致命",
        "范围" to "范围",
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

    fun definitionFor(label: String): RuleDefinition? {
        FellgorCatalog.weaponRules.firstOrNull { label.trim().startsWith(it.name) }?.let { return it }
        ImportedTeamCatalog.weaponRules.firstOrNull { label.trim() == it.name }?.let { return it }
        val canonical = aliases.entries.firstOrNull { label.trim().startsWith(it.key) }?.value ?: return null
        return rules.firstOrNull { it.name == canonical }
    }

    fun all(): List<RuleDefinition> = rules

    private fun rule(id: String, name: String, description: String) = RuleDefinition(id, name, description)
}

object AbilityCatalog {
    private val aodAstartes = ability(
        "aod_astartes", "阿斯塔特",
        "每次激活可执行两次射击或两次战斗。两次射击时至少一次必须使用爆矢武器；若两次都使用爆矢狙击步枪或重型爆矢枪，第二次额外消耗 1AP。无论命令为何都能反制。",
        faction = true
    )
    private val pmAstartes = ability(
        "pm_astartes", "阿斯塔特",
        "每次激活可执行两次射击或两次战斗。两次射击时至少一次须使用爆矢手枪、爆矢枪或灵能武器；同一件灵能远程武器每次激活不能选两次。无论命令为何都能反制。",
        faction = true
    )
    private val resilient = ability(
        "pm_resilient", "恼人韧性",
        "每当一枚攻击骰对该特工造成 3 点或更多伤害时掷 D6；结果为 4+ 时，所受伤害减少 1。",
        faction = true
    )
    private val burrow = ability(
        "rav_burrow", "钻地",
        "部署时前两名特工正常设置，其余可置于地下。地下特工只能执行钻地行动：从己方隧道标识出现并在本次激活或反制中移动减 2 英寸；或站在隧道上时移出战场进入地下。携带标识或不在地下/隧道上时不能执行。",
        ap = 1,
        faction = true
    )
    private val predatory = ability(
        "rav_predatory", "掠食本能",
        "每次激活可执行两次战斗。无论命令为何都能反制；反制前可更改命令，若不执行任务行动还可免费执行一次钻地。",
        faction = true
    )
    private val tunnel = ability(
        "rav_tunnel", "隧道",
        "部署结束时在己方边缘放置 0 号隧道标识；前四个转折点可依次在上一枚 5 英寸内放置下一枚。相邻编号标识之间构成己方隧道，钻地及部分能力会引用它。",
        faction = true
    )

    private val unique = mapOf(
        "aod_captain" to listOf(
            ability("aod_heroic", "英雄领袖", "每个转折点一次：可为该特工免费使用一项交战计谋（指挥重投除外），或在满足位置条件时使用战斗条令/调整条令；同一计谋本转折点已用过则不能再次使用。"),
            ability("aod_halo", "钢铁光环", "每场战斗一次，一枚攻击骰对该特工造成普通伤害时，可忽略那次伤害。")
        ),
        "aod_assault_sergeant" to listOf(
            ability("aod_chapter_veteran", "战团老兵", "选择部署特工步骤结束时，为该特工额外选择一项本场战斗有效的战团战术。"),
            ability("aod_assault_doctrine", "条令战争", "每场战斗各一次：该特工在杀戮区时，选择毁灭条令或战术条令的战斗条令计谋可花费 0CP。")
        ),
        "aod_sergeant" to listOf(
            ability("aod_chapter_veteran", "战团老兵", "选择部署特工步骤结束时，为该特工额外选择一项本场战斗有效的战团战术。"),
            ability("aod_tactical_doctrine", "条令战争", "每场战斗各一次：该特工在杀戮区时，选择毁灭条令或战术条令的战斗条令计谋可花费 0CP。")
        ),
        "aod_sniper" to listOf(
            ability("aod_camo", "迷彩斗篷", "敌人射击该特工时忽略“集中”。该特工拥有隐匿战团战术；若队伍也选择了该战术，可同时使用其两个选项。"),
            ability("aod_optics", "瞄准", "直到该特工下次激活开始，其射击目标不能处于遮蔽状态。处于敌方控制范围内时不能执行。", ap = 1),
            ability("aod_stealth", "隐匿", "敌人射击该特工时，若能保留掩护豁免，可额外保留一枚，或将一枚保留为暴击。不能与制高地带来的强化掩护叠加。")
        ),
        "aod_grenadier" to listOf(
            ability("aod_grenadier", "掷弹兵", "可使用破片和穿甲手雷，不占用队伍装备的有限次数；使用这些手雷时命中属性改善 1。")
        ),
        "pm_champion" to listOf(
            ability("pm_blessing", "慈父赐福", "7 英寸内带有己方毒素标识的敌人损失耐伤时，该特工恢复等量已损耐伤；每个转折点最多恢复 3 点，且该特工必须仍在场。")
        ),
        "pm_plaguecaster" to listOf(
            ability("pm_miasma", "有毒瘴气", "灵能。选择 7 英寸内可见的敌方特工或该特工的有效目标：目标获得己方毒素标识；若已有标识，则改为造成 3 点伤害。处于敌方控制范围内时不能执行。", ap = 1),
            ability("pm_vitality", "腐烂活力", "灵能。选择 3 英寸内可见的友方特工并掷 2D6：总和为 7 时恢复最多 7 点耐伤，否则按较高的一枚 D6 恢复。每个转折点一次，处于敌方控制范围内时不能执行。", ap = 1)
        ),
        "pm_bombardier" to listOf(
            ability("pm_grenadier", "掷弹兵", "可使用疫病与穿甲手雷，不占用队伍装备的有限次数；使用时命中属性改善 1，疫病手雷同时获得“剧毒”。")
        ),
        "pm_fighter" to listOf(
            ability("pm_flail", "连枷", "对 2 英寸内其他每名可见特工分别造成 D3+2 伤害；若敌人对应的 D3 为 3，还获得己方毒素标识。按阿斯塔特限制视为战斗行动，隐匿命令下不能执行。", ap = 1)
        ),
        "pm_icon_bearer" to listOf(
            ability("pm_icon_bearer", "持徽手", "判断标识控制权时，该特工的 APL 视为加 1；这不是属性修改，可与其他效果叠加。"),
            ability("pm_icon_contagion", "传染圣像", "该特工位于对手领地内时，“传染”战略计谋花费 0CP。")
        ),
        "pm_warrior" to listOf(
            ability("pm_fortitude", "排斥韧性", "敌人射击该特工时，其防御骰结果 5+ 视为暴击。")
        ),
        "rav_prime" to listOf(
            ability("rav_crest", "神经掠食冠", "争夺标识时，若敌方特工在 3 英寸内，其 APL 总和视为少 1。3 英寸内的敌人拾取标识或执行任务行动额外花费 1AP，且不能重投攻击或防御骰。"),
            ability("rav_synapse", "突触链接", "战略策略：该特工仍在场时掷 D6。若结果至少为当前转折点两倍，获得 1CP；若低于转折点数，该特工受到等于结果的伤害；其余无事发生。")
        ),
        "rav_felltalon" to listOf(
            ability("rav_toxic_lunge", "剧毒突刺", "选择 2 英寸内可见敌人；若处于地下，也可选择隧道上的敌人。造成 D3+2 伤害并令其获得毒素标识。地下时也能执行。", ap = 1)
        ),
        "rav_tremorscythe" to listOf(
            ability("rav_hunter", "超感猎手", "若该特工在同一次激活或反制中执行过钻地，即使是隐匿命令也能执行冲锋。"),
            ability("rav_ambush", "地下伏击", "每个转折点一次：敌人在移动超过 2 英寸后停于己方隧道 2 英寸内，若该特工在地下且可行动，可立即中断并激活或反制；必须先对该敌人战斗或射击。")
        ),
        "rav_venomspitter" to listOf(
            ability("rav_distend", "扩张背囊", "直到用毒液飞弹射击、再次执行本行动或钻地为止，毒液飞弹所有档案获得致命 5+、攻击骰加 1，并移除范围 8 英寸。", ap = 1)
        ),
        "rav_warrior" to listOf(
            ability("rav_instinct", "本能行为", "射击、战斗或反击受伤的敌人，或本转折点执行过撤退的敌人时，该特工的武器获得致命 5+。")
        ),
        "rav_wrecker" to listOf(
            ability("rav_carapace", "强化甲壳", "普通或暴击伤害为 4 点及以上的攻击骰对该特工造成的伤害减少 1。")
        )
    )

    fun forOperative(operativeId: String): List<AbilityDefinition> {
        FellgorCatalog.abilities[operativeId]?.let { return it }
        ImportedTeamCatalog.abilities[operativeId]?.let { return it }
        if (operativeId.startsWith("wc_")) return WarpCovenCatalog.forOperative(operativeId)
        val common = when {
            operativeId.startsWith("aod_") -> listOf(aodAstartes)
            operativeId.startsWith("pm_") -> listOf(pmAstartes, resilient)
            operativeId.startsWith("rav_") -> listOf(burrow, predatory, tunnel)
            else -> emptyList()
        }
        return common + unique[operativeId].orEmpty()
    }

    private fun ability(
        id: String,
        name: String,
        description: String,
        ap: Int? = null,
        faction: Boolean = false
    ) = AbilityDefinition(id, name, description, ap, faction)
}
