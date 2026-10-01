package com.example.helloworld

/** Imported from docs/次元密会-完整规则资料.md, verified data dated 2026-09-23. */
object WarpCovenCatalog {
    val team = KillTeamCatalog("CHAOS-WC", "次元密会", "混沌 · 千子 · 10 种角色", listOf(
        OperativeTemplate("wc_sod", "命运巫师", "巫师", 3, "6\"", "3+", 15, R.drawable.roster_wc_sod,
            listOf(
                WeaponOption("chaos_wc_sod_db_0", "裁决闪电", "4A · 3+ · 4/2 · 灵能、毁灭 2、致命 5+"),
                WeaponOption("chaos_wc_sod_ibp_0", "地狱火爆矢手枪", "4A · 3+ · 3/4 · 范围 8\"、穿刺 1"),
                WeaponOption("chaos_wc_sod_wfp_0", "亚空间炽焰手枪", "4A · 2+ · 3/3 · 范围 6\"、穿刺 1、洪流 1\""),
                WeaponOption("chaos_wc_sod_fs_0", "灵能杖", "4A · 3+ · 4/6 · 灵能、震荡"),
                WeaponOption("chaos_wc_sod_pk_0", "普罗斯佩罗弯刀", "5A · 3+ · 4/6 · 致命 5+")
            ), listOf("chaos_wc_sod_db_0", "chaos_wc_sod_ibp_0", "chaos_wc_sod_fs_0")),
        OperativeTemplate("wc_sot", "时间巫师", "巫师", 3, "6\"", "3+", 15, R.drawable.roster_wc_sot,
            listOf(
                WeaponOption("chaos_wc_sot_fb_0", "奔腾涌流", "4A · 3+ · 3/4 · 灵能、爆炸 2\"、撕裂"),
                WeaponOption("chaos_wc_sot_ibp_0", "地狱火爆矢手枪", "4A · 3+ · 3/4 · 范围 8\"、穿刺 1"),
                WeaponOption("chaos_wc_sot_wfp_0", "亚空间炽焰手枪", "4A · 2+ · 3/3 · 范围 6\"、穿刺 1、洪流 1\""),
                WeaponOption("chaos_wc_sot_fs_0", "灵能杖", "4A · 3+ · 4/6 · 灵能、震荡"),
                WeaponOption("chaos_wc_sot_pk_0", "普罗斯佩罗弯刀", "5A · 3+ · 4/6 · 致命 5+")
            ), listOf("chaos_wc_sot_fb_0", "chaos_wc_sot_ibp_0", "chaos_wc_sot_fs_0")),
        OperativeTemplate("wc_sow", "亚空间炽焰巫师", "巫师", 3, "6\"", "3+", 15, R.drawable.roster_wc_sow,
            listOf(
                WeaponOption("chaos_wc_sow_fstrm_0", "烈焰风暴", "5A · 4+ · 2/3 · 灵能、集中、追踪轻型、洪流 2\""),
                WeaponOption("chaos_wc_sow_ibp_0", "地狱火爆矢手枪", "4A · 3+ · 3/4 · 范围 8\"、穿刺 1"),
                WeaponOption("chaos_wc_sow_mb_0", "焚却理智", "5A · 4+ · 1/1 · 灵能、致命 5+、集中、追踪轻型、焚却理智"),
                WeaponOption("chaos_wc_sow_wfp_0", "亚空间炽焰手枪", "4A · 2+ · 3/3 · 范围 6\"、穿刺 1、洪流 1\""),
                WeaponOption("chaos_wc_sow_fs_0", "灵能杖", "4A · 3+ · 4/6 · 灵能、震荡"),
                WeaponOption("chaos_wc_sow_pk_0", "普罗斯佩罗弯刀", "5A · 3+ · 4/6 · 致命 5+")
            ), listOf("chaos_wc_sow_fstrm_0", "chaos_wc_sow_ibp_0", "chaos_wc_sow_mb_0", "chaos_wc_sow_fs_0")),
        OperativeTemplate("wc_gnr", "红字战士炮手", "炮手", 3, "5\"", "2+", 14, R.drawable.roster_wc_gnr,
            listOf(
                WeaponOption("chaos_wc_gnr_src_0", "灵魂收割者炮（集中）", "5A · 3+ · 4/5 · 穿刺 1"),
                WeaponOption("chaos_wc_gnr_src_1", "灵魂收割者炮（扫射）", "4A · 3+ · 4/5 · 穿刺 1、洪流 1\""),
                WeaponOption("chaos_wc_gnr_wfl_0", "亚空间火焰喷射器", "4A · 2+ · 4/4 · 范围 8\"、集中、穿刺 1、洪流 2\""),
                WeaponOption("chaos_wc_gnr_f_0", "双拳", "3A · 3+ · 3/4")
            ), listOf("chaos_wc_gnr_src_0", "chaos_wc_gnr_src_1", "chaos_wc_gnr_f_0")),
        OperativeTemplate("wc_ib", "红字战士徽记持有者", "专家", 3, "5\"", "2+", 14, R.drawable.roster_wc_ib,
            listOf(
                WeaponOption("chaos_wc_ib_ibg_0", "地狱火爆矢枪", "4A · 3+ · 3/4 · 穿刺 1"),
                WeaponOption("chaos_wc_ib_f_0", "双拳", "3A · 3+ · 3/4")
            ), listOf("chaos_wc_ib_ibg_0", "chaos_wc_ib_f_0")),
        OperativeTemplate("wc_war", "红字战士战士", "战士", 3, "5\"", "2+", 14, R.drawable.roster_wc_war,
            listOf(
                WeaponOption("chaos_wc_war_ibg_0", "地狱火爆矢枪", "4A · 3+ · 3/4 · 穿刺 1"),
                WeaponOption("chaos_wc_war_f_0", "双拳", "3A · 3+ · 3/4")
            ), listOf("chaos_wc_war_ibg_0", "chaos_wc_war_f_0")),
        OperativeTemplate("wc_tzc", "奸角兽勇士", "勇士", 2, "6\"", "5+", 10, R.drawable.roster_wc_tzc,
            listOf(
                WeaponOption("chaos_wc_tzc_ga_0", "巨斧", "4A · 3+ · 4/5 · 残暴、致命 5+"),
                WeaponOption("chaos_wc_tzc_gb_0", "巨剑", "4A · 3+ · 4/5 · 致命 5+、撕裂")
            ), listOf("chaos_wc_tzc_ga_0")),
        OperativeTemplate("wc_tzhb", "奸角兽号手", "专家", 2, "6\"", "5+", 9, R.drawable.roster_wc_tzhb,
            listOf(
                WeaponOption("chaos_wc_tzhb_d_0", "匕首", "4A · 4+ · 3/5")
            ), listOf("chaos_wc_tzhb_d_0")),
        OperativeTemplate("wc_tzib", "奸角兽徽记持有者", "专家", 2, "6\"", "5+", 9, R.drawable.roster_wc_tzib,
            listOf(
                WeaponOption("chaos_wc_tzib_d_0", "匕首", "4A · 4+ · 3/5")
            ), listOf("chaos_wc_tzib_d_0")),
        OperativeTemplate("wc_tzwar", "奸角兽战士", "战士", 2, "6\"", "5+", 9, R.drawable.roster_wc_tzwar,
            listOf(
                WeaponOption("chaos_wc_tzwar_ap_0", "自动手枪", "4A · 4+ · 2/3 · 范围 8\""),
                WeaponOption("chaos_wc_tzwar_cs_0", "链锯剑", "4A · 4+ · 4/5"),
                WeaponOption("chaos_wc_tzwar_tbs_0", "奸角兽之刃和盾牌", "4A · 4+ · 3/4 · 盾牌"),
                WeaponOption("chaos_wc_tzwar_tb_0", "奸角兽双刃", "4A · 4+ · 4/5 · 平衡")
            ), listOf("chaos_wc_tzwar_tb_0"))
    ))
    val meleeWeaponIds = setOf("chaos_wc_sod_fs_0", "chaos_wc_sod_pk_0", "chaos_wc_sot_fs_0", "chaos_wc_sot_pk_0", "chaos_wc_sow_fs_0", "chaos_wc_sow_pk_0", "chaos_wc_gnr_f_0", "chaos_wc_ib_f_0", "chaos_wc_war_f_0", "chaos_wc_tzc_ga_0", "chaos_wc_tzc_gb_0", "chaos_wc_tzhb_d_0", "chaos_wc_tzib_d_0", "chaos_wc_tzwar_cs_0", "chaos_wc_tzwar_tbs_0", "chaos_wc_tzwar_tb_0")
    private val boons = AbilityDefinition("wc_boons", "奸奇恩惠", "每当为战斗选择一名巫师特工时，必须为其选择一种奸奇恩惠，使其在整场战斗中拥有该恩惠。同一种奸奇恩惠每场战斗至多选择一次。", factionRule=true)
    private val astartes = AbilityDefinition("wc_astartes", "阿斯塔特", "在每一名己方次元密会阿斯塔特叛军特工的激活期间，它可以执行两次射击行动或两次近战行动。若两次射击都选择灵魂收割者炮或亚空间火焰喷射器，则第二次行动额外消耗 1AP。每次激活不能选择同一件灵能远程武器超过一次。无论拥有何种命令，每名己方次元密会阿斯塔特叛军特工都可以进行反应。", factionRule=true)
    private val abilities = listOf(
        AbilityDefinition("CHAOS-WC-SOD-PBF", "天命庇护", "选择一名对该特工可见的己方次元密会特工。直到该特工下一次激活开始、该特工残废或另一名己方特工再次执行本行动（以先发生者为准），每当一名特工向被选特工射击时，可以重掷任意己方防御骰。不能在敌方特工控制范围内执行。", 1),
        AbilityDefinition("CHAOS-WC-SOD-RD", "蹂躏命运", "选择一名位于 9\" 内且可见的敌方特工。直到该特工下一次激活开始、该特工残废或另一名己方特工再次执行本行动（以先发生者为准），每当被选敌人射击、近战或反击时，对手必须重掷结果为 6 的攻击骰；确定标识控制权时，将其 APL 视为低 1。后者不是对 APL 属性的修改，因此会与其他修改累加。不能在敌方特工控制范围内执行。", 1),
        AbilityDefinition("CHAOS-WC-SOT-RR", "重构仪式", "选择一名位于 6\" 内且可见的己方次元密会特工，使其恢复最多 2D3 点失去的耐伤。不能在敌方特工控制范围内执行；同一转折点已有己方特工执行过本行动时也不能执行。", 1),
        AbilityDefinition("CHAOS-WC-SOT-TF", "时空波动", "选择一名位于 6\" 内且可见的己方次元密会特工，并将己方时空波动标识放在其控制范围内。该特工下一次激活结束时，若它未残废且仍完全位于标识 6\" 内，将其移出杀戮区并重新放到合法位置；重新放置时，标识必须位于其控制范围内（若无法做到则尽可能接近），随后移除标识。若该特工并非完全位于标识 6\" 内（包括已经残废），只移除标识。不能在敌方控制范围内执行，且己方时空波动标识已经位于杀戮区时不能再次执行。", 1),
        AbilityDefinition("CHAOS-WC-SOW-A-MB", "焚却理智", "在结算攻击骰步骤中，若任意关键成功造成伤害，作为该武器攻击目标的特工获得一枚己方焚却理智标识（若尚未拥有），直到其下一次激活结束、其残废或一名己方特工再次使用该武器（以先发生者为准）。拥有己方焚却理智标识的特工，其武器命中属性恶化 1；不与受创叠加。", null),
        AbilityDefinition("CHAOS-WC-SOW-AL", "点明", "选择一名对该特工可见的敌方特工。直到该特工下一次激活开始、该特工残废或另一名己方特工再次执行本行动（以先发生者为准），目标获得一枚己方点明标识（若尚未拥有）。每当己方次元密会特工向拥有该标识的敌人射击、与其近战或对其反击时，己方特工的武器获得“无休”。不能在敌方控制范围内执行。", 1),
        AbilityDefinition("wc_automata", "奥术自动机兵", "这名特工激活时，将其 APL 降低 1 直到激活结束，除非一名己方次元密会巫师位于它 9\" 内。", null),
        AbilityDefinition("CHAOS-WC-IB-A-IB", "徽记持有者", "确定标识控制权时，将红字战士徽记持有者的 APL 视为高 1。这不是对 APL 属性的修改，因此与其他修改累加。", null),
        AbilityDefinition("CHAOS-WC-WAR-A-SAP", "缓慢而坚定", "每当红字战士战士射击时，若它在本次激活中尚未执行冲锋或转移，或正在进行反应，其远程武器获得“无休”。本规则不阻止它在射击后移动。", null),
        AbilityDefinition("CHAOS-WC-TZC-A-SB", "野性狂暴", "这名特工每次激活首次执行近战后，若未残废，可以立即再执行一次无消耗近战；不必选择同一敌人。本规则优先于行动限制。", null),
        AbilityDefinition("CHAOS-WC-TZHB-BH", "兽群号角", "直到下一战略阶段就绪步骤前，己方次元密会奸角兽特工的移动增加 1\"。不能在敌方控制范围内执行。", 0),
        AbilityDefinition("CHAOS-WC-TZIB-A-HB", "兽群旗帜", "当一枚攻击骰对一名位于该特工 3\" 内、对其可见的己方次元密会奸角兽特工造成 3 点或更多普通伤害时，所造成伤害减 1。", null),
        AbilityDefinition("CHAOS-WC-TZIB-A-IB", "徽记持有者", "确定标识控制权时，将奸角兽徽记持有者的 APL 视为高 1；这不是属性修改，会与其他修改累加。", null),
        AbilityDefinition("CHAOS-WC-TZWAR-A-RH", "神器猎人", "每场战斗限一次，若一名己方次元密会奸角兽战士位于对手领地内，它执行拾取标识、放置标识或任务行动时少消耗 1AP。", null),
        AbilityDefinition("CHAOS-WC-TZWAR-A-SH", "盾牌", "装备奸角兽之刃和盾牌时，该特工豁免为 4+；使用该武器近战或反击时，每个格挡可以格挡两个未结算成功，而不是一个。", null)
    )
    fun forOperative(id: String): List<AbilityDefinition> {
        val short=id.removePrefix("wc_").uppercase()
        val common=when(short) {
            "SOD","SOT","SOW" -> listOf(boons,astartes)
            "GNR","IB","WAR" -> listOf(astartes)+abilities.filter { it.id=="wc_automata" }
            else -> emptyList()
        }
        return common+abilities.filter { it.id.startsWith("CHAOS-WC-$short-") }
    }
}
