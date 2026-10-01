package com.example.helloworld

/** Reviewed from docs/恶角兽掠夺者-完整规则资料.md (2026-09-23). */
object FellgorCatalog {
    private fun weapon(id: String, name: String, profile: String) = WeaponOption(id, name, profile)

    private val frenzy = AbilityDefinition(
        id = "chaos_fell_frenzy",
        name = "狂暴",
        description = "己方恶角兽掠夺者特工首次将被残废时，改为获得狂暴指示物、弃掉剩余攻击骰，并把隐匿命令改为交战命令。拥有狂暴指示物期间，该特工受创、不能拥有隐匿命令，控制标识时 APL 视为 1，并按狂暴规则列出的时机残废。",
        factionRule = true
    )

    val team = KillTeamCatalog(
        id = "CHAOS-FELL",
        name = "恶角兽掠夺者",
        subtitle = "混沌 · 侦察 / 搜索并歼灭 · 11 种成员",
        operatives = listOf(
            OperativeTemplate(
                "chaos_fell_ih", "恶角兽铁角头领", "领袖", 2, "6\"", "5+", 11,
                R.drawable.roster_chaos_fell_ih,
                listOf(
                    weapon("chaos_fell_ih_cp_0", "腐化手枪", "4A · 4+ · 3/5 · 范围 8\"、撕裂"),
                    weapon("chaos_fell_ih_pp_0", "等离子手枪（标准）", "4A · 4+ · 3/5 · 范围 8\"、穿刺 1"),
                    weapon("chaos_fell_ih_pp_1", "等离子手枪（过载）", "4A · 4+ · 4/5 · 范围 8\"、过热、致命 5+、穿刺 1"),
                    weapon("chaos_fell_ih_bl_0", "钝器", "4A · 3+ · 4/4 · 残暴"),
                    weapon("chaos_fell_ih_cc_0", "腐化链锯剑", "4A · 3+ · 4/5")
                ),
                listOf("chaos_fell_ih_cp_0", "chaos_fell_ih_cc_0"),
                baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "LEADER", "IRONHORN")
            ),
            OperativeTemplate(
                "chaos_fell_dk", "恶角兽死亡丧钟", "持徽与防御支援", 2, "6\"", "4+", 10,
                R.drawable.roster_chaos_fell_dk,
                listOf(
                    weapon("chaos_fell_dk_ap_0", "自动手枪", "4A · 4+ · 2/3 · 范围 8\""),
                    weapon("chaos_fell_dk_bl_0", "钝器", "4A · 3+ · 4/4 · 残暴")
                ),
                listOf("chaos_fell_dk_ap_0", "chaos_fell_dk_bl_0"),
                baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "DEATHKNELL")
            ),
            OperativeTemplate(
                "chaos_fell_fb", "恶角兽诡变者", "机动近战", 2, "6\"", "5+", 10,
                R.drawable.roster_chaos_fell_fb,
                listOf(weapon("chaos_fell_fb_3c_0", "三连砍刀", "4A · 3+ · 4/5 · 无休")),
                listOf("chaos_fell_fb_3c_0"), baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "FLUXBRAY")
            ),
            OperativeTemplate(
                "chaos_fell_gs", "恶角兽疤面", "近射联动", 2, "6\"", "5+", 10,
                R.drawable.roster_chaos_fell_gs,
                listOf(
                    weapon("chaos_fell_gs_ap_0", "自动手枪", "4A · 4+ · 2/3 · 范围 8\""),
                    weapon("chaos_fell_gs_bf_0", "义肢拳", "4A · 3+ · 4/5 · 残暴")
                ),
                listOf("chaos_fell_gs_ap_0", "chaos_fell_gs_bf_0"), baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "GNARLSCAR")
            ),
            OperativeTemplate(
                "chaos_fell_gh", "恶角兽血角", "近战决斗", 2, "6\"", "5+", 10,
                R.drawable.roster_chaos_fell_gh,
                listOf(
                    weapon("chaos_fell_gh_ap_0", "自动手枪", "4A · 4+ · 2/3 · 范围 8\""),
                    weapon("chaos_fell_gh_sc_0", "勈颅刀", "4A · 3+ · 4/5 · 致命 5+、夺颅者")
                ),
                listOf("chaos_fell_gh_ap_0", "chaos_fell_gh_sc_0"), baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "GOREHORN")
            ),
            OperativeTemplate(
                "chaos_fell_hg", "恶角兽激兽师", "支援与控制", 2, "6\"", "5+", 10,
                R.drawable.roster_chaos_fell_hg,
                listOf(
                    weapon("chaos_fell_hg_ap_0", "自动手枪", "4A · 4+ · 2/3 · 范围 8\""),
                    weapon("chaos_fell_hg_cwr_0", "裂刺鞭（远程）", "4A · 2+ · 2/3 · 范围 3\"、致命 4+、晕眩"),
                    weapon("chaos_fell_hg_cwm_0", "裂刺鞭（近战）", "4A · 3+ · 2/3 · 致命 4+、震荡")
                ),
                listOf("chaos_fell_hg_ap_0", "chaos_fell_hg_cwr_0", "chaos_fell_hg_cwm_0"), baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "HERD-GOAD")
            ),
            OperativeTemplate(
                "chaos_fell_mng", "恶角兽撕裂者", "连续近战", 2, "6\"", "5+", 10,
                R.drawable.roster_chaos_fell_mng,
                listOf(weapon("chaos_fell_mng_vc_0", "恶毒利爪", "4A · 4+ · 4/6 · 无休、触觉猎手")),
                listOf("chaos_fell_mng_vc_0"), baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "MANGLER")
            ),
            OperativeTemplate(
                "chaos_fell_sha", "恶角兽萨满", "灵能、恢复与隐蔽", 2, "6\"", "5+", 10,
                R.drawable.roster_chaos_fell_sha,
                listOf(
                    weapon("chaos_fell_sha_ap_0", "自动手枪", "4A · 4+ · 2/3 · 范围 8\""),
                    weapon("chaos_fell_sha_tc_0", "科技诅咒", "4A · 3+ · 1/3 · 灵能、撕裂、集中、追踪轻型"),
                    weapon("chaos_fell_sha_bs_0", "兽群法杖", "4A · 3+ · 3/5 · 震荡")
                ),
                listOf("chaos_fell_sha_ap_0", "chaos_fell_sha_tc_0", "chaos_fell_sha_bs_0"), baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "SHAMAN")
            ),
            OperativeTemplate(
                "chaos_fell_tox", "恶角兽毒角", "韧性与手雷", 2, "6\"", "5+", 10,
                R.drawable.roster_chaos_fell_tox,
                listOf(
                    weapon("chaos_fell_tox_ap_0", "自动手枪", "4A · 4+ · 2/3 · 范围 8\""),
                    weapon("chaos_fell_tox_cl_0", "砍刀", "4A · 3+ · 4/5")
                ),
                listOf("chaos_fell_tox_ap_0", "chaos_fell_tox_cl_0"), baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "TOXHORN")
            ),
            OperativeTemplate(
                "chaos_fell_vnd", "恶角兽破坏者", "重击与范围伤害", 2, "6\"", "5+", 10,
                R.drawable.roster_chaos_fell_vnd,
                listOf(weapon("chaos_fell_vnd_mc_0", "碎人锤", "4A · 4+ · 5/5 · 残暴、恶毒打击")),
                listOf("chaos_fell_vnd_mc_0"), baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "VANDAL")
            ),
            OperativeTemplate(
                "chaos_fell_war", "恶角兽战士", "基础战士", 2, "6\"", "5+", 10,
                R.drawable.roster_chaos_fell_war,
                listOf(
                    weapon("chaos_fell_war_ap_0", "自动手枪", "4A · 4+ · 2/3 · 范围 8\""),
                    weapon("chaos_fell_war_bl_0", "钝器", "4A · 3+ · 4/4 · 残暴"),
                    weapon("chaos_fell_war_cl_0", "砍刀", "4A · 3+ · 4/5")
                ),
                listOf("chaos_fell_war_ap_0", "chaos_fell_war_cl_0"), baseSizeMm = 32,
                keywords = listOf("FELLGOR RAVAGER", "CHAOS", "WARRIOR")
            )
        )
    )

    val abilities = team.operatives.associate { it.id to listOf(frenzy) }

    val meleeWeaponIds = setOf(
        "chaos_fell_ih_bl_0", "chaos_fell_ih_cc_0", "chaos_fell_dk_bl_0",
        "chaos_fell_fb_3c_0", "chaos_fell_gs_bf_0", "chaos_fell_gh_sc_0",
        "chaos_fell_hg_cwm_0", "chaos_fell_mng_vc_0", "chaos_fell_sha_bs_0",
        "chaos_fell_tox_cl_0", "chaos_fell_vnd_mc_0", "chaos_fell_war_bl_0",
        "chaos_fell_war_cl_0"
    )

    val abilityCards = mapOf(
        "chaos_fell_sha" to listOf("CHAOS-FELL--09-特工-恶角兽萨满-能力卡.png")
    )

    val weaponRules = listOf(
        RuleDefinition("chaos_fell_headtaker", "夺颅者", "这名特工使用勈颅刀使敌方特工残废时，按夺颅者规则掷 D3，并选择恢复耐伤或强化该武器的暴击伤害。"),
        RuleDefinition("chaos_fell_tactual_hunter", "触觉猎手", "对处于待机状态的特工近战时，首次以暴击出击后，可以在对手之前立即再以一个成功出击。"),
        RuleDefinition("chaos_fell_vicious_blows", "恶毒打击", "这名特工使用碎人锤近战时，该武器拥有“无休”武器规则。")
    )
}
