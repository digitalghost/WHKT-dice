package com.example.helloworld

import androidx.annotation.DrawableRes

data class WeaponOption(
    val id: String,
    val name: String,
    val profile: String
) {
    val stats: WeaponStats
        get() = WeaponProfileParser.parse(profile)
}

data class WeaponStats(
    val attacks: Int,
    val hit: Int,
    val normalDamage: Int,
    val criticalDamage: Int,
    val keywords: List<String>
) {
    val criticalThreshold: Int
        get() = keywords.firstNotNullOfOrNull { keyword ->
            Regex("致命\\s*(\\d)\\+").find(keyword)?.groupValues?.get(1)?.toIntOrNull()
        } ?: 6
}

object WeaponProfileParser {
    fun parse(profile: String): WeaponStats {
        val parts = profile.split('·').map(String::trim).filter(String::isNotBlank)
        val damage = parts.getOrNull(2).orEmpty().split('/').map(String::trim)
        val keywordText = parts.drop(3).joinToString("、")
        return WeaponStats(
            attacks = parts.getOrNull(0)?.filter(Char::isDigit)?.toIntOrNull() ?: 1,
            hit = parts.getOrNull(1)?.filter(Char::isDigit)?.toIntOrNull() ?: 6,
            normalDamage = damage.getOrNull(0)?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
            criticalDamage = damage.getOrNull(1)?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
            keywords = keywordText.split('、', ',').map(String::trim).filter(String::isNotBlank)
        )
    }
}

data class AbilityDefinition(
    val id: String,
    val name: String,
    val description: String,
    val apCost: Int? = null,
    val factionRule: Boolean = false
)

data class OperativeTemplate(
    val id: String,
    val name: String,
    val role: String,
    val apl: Int,
    val move: String,
    val save: String,
    val wounds: Int,
    @DrawableRes val cardRes: Int,
    val weapons: List<WeaponOption>,
    val defaultWeaponIds: List<String>,
    val baseSizeMm: Int? = null,
    val keywords: List<String> = emptyList()
)

data class KillTeamCatalog(
    val id: String,
    val name: String,
    val subtitle: String,
    val operatives: List<OperativeTemplate>
)

data class RosterMember(
    val id: String,
    val operativeId: String,
    val callsign: String,
    val weaponIds: List<String>,
    val customAvatarUri: String? = null
)

data class SavedRoster(
    val rosterId: String,
    val teamId: String,
    val name: String,
    val members: MutableList<RosterMember>,
    val updatedAt: Long = 0L
)

object RosterCatalog {
    private fun w(id: String, name: String, profile: String) = WeaponOption(id, name, profile)

    val teams: List<KillTeamCatalog> = listOf(
        KillTeamCatalog(
            id = "IMP-AOD",
            name = "死亡天使",
            subtitle = "帝国 · 阿斯塔特修会 · 9 种成员",
            operatives = listOf(
                OperativeTemplate(
                    "aod_captain", "星际战士连长", "领袖", 3, "6\"", "3+", 15,
                    R.drawable.roster_aod_captain,
                    listOf(
                        w("plasma_standard", "等离子手枪（标准）", "4A · 3+ · 3/5 · 范围 8\"、穿刺 1"),
                        w("plasma_super", "等离子手枪（过载）", "4A · 3+ · 4/5 · 过热、致命 5+、穿刺 1"),
                        w("power_fist", "动力拳套", "5A · 3+ · 5/7 · 残暴")
                    ),
                    listOf("plasma_standard", "power_fist")
                ),
                OperativeTemplate(
                    "aod_assault_sergeant", "突击仲裁者军士", "领袖", 3, "6\"", "3+", 15,
                    R.drawable.roster_aod_assault_sergeant,
                    listOf(
                        w("hand_flamer", "喷火手枪", "4A · 2+ · 3/3 · 范围 6\"、集中、洪流 1\""),
                        w("heavy_pistol", "重型爆矢手枪", "4A · 3+ · 3/4 · 范围 8\"、关键穿刺 1"),
                        w("plasma_pistol", "等离子手枪", "4A · 3+ · 3/5 · 范围 8\"、穿刺 1"),
                        w("chainsword", "链锯剑", "5A · 3+ · 4/5"),
                        w("power_fist", "动力拳套", "5A · 4+ · 5/7 · 残暴"),
                        w("power_weapon", "动力武器", "5A · 3+ · 4/6 · 致命 5+"),
                        w("thunder_hammer", "雷霆锤", "5A · 4+ · 5/6 · 震荡、眩晕")
                    ),
                    listOf("heavy_pistol", "chainsword")
                ),
                OperativeTemplate(
                    "aod_sergeant", "仲裁者军士", "领袖", 3, "6\"", "3+", 15,
                    R.drawable.roster_aod_sergeant,
                    listOf(
                        w("auto_bolt", "自动爆矢步枪", "4A · 3+ · 3/4 · 洪流 1\""),
                        w("bolt_rifle", "爆矢步枪", "4A · 3+ · 3/4 · 关键穿刺 1"),
                        w("stalker", "追猎者爆矢步枪", "4A · 3+ · 3/5 · 重型、致命 5+"),
                        w("bolt_pistol", "爆矢手枪", "4A · 3+ · 3/4 · 范围 8\""),
                        w("chainsword", "链锯剑", "4A · 3+ · 4/5"),
                        w("power_fist", "动力拳套", "4A · 4+ · 5/7 · 残暴"),
                        w("power_weapon", "动力武器", "4A · 3+ · 4/6 · 致命 5+"),
                        w("thunder_hammer", "雷霆锤", "4A · 4+ · 5/6 · 震荡、眩晕")
                    ),
                    listOf("bolt_rifle", "bolt_pistol", "chainsword")
                ),
                OperativeTemplate(
                    "aod_grenadier", "突击仲裁者掷弹兵", "专家", 3, "6\"", "3+", 14,
                    R.drawable.roster_aod_grenadier,
                    listOf(
                        w("heavy_pistol", "重型爆矢手枪", "4A · 3+ · 3/4 · 范围 8\"、关键穿刺 1"),
                        w("chainsword", "链锯剑", "5A · 3+ · 4/5"),
                        w("frag", "破片手雷", "4A · 3+ · 2/4 · 范围 6\"、爆炸 2\"、集中"),
                        w("krak", "穿甲手雷", "4A · 3+ · 4/5 · 范围 6\"、穿刺 1、集中")
                    ),
                    listOf("heavy_pistol", "chainsword", "frag", "krak")
                ),
                OperativeTemplate(
                    "aod_assault_warrior", "突击仲裁者战士", "战士", 3, "6\"", "3+", 14,
                    R.drawable.roster_aod_assault_warrior,
                    listOf(
                        w("heavy_pistol", "重型爆矢手枪", "4A · 3+ · 3/4 · 范围 8\"、关键穿刺 1"),
                        w("chainsword", "链锯剑", "5A · 3+ · 4/5")
                    ),
                    listOf("heavy_pistol", "chainsword")
                ),
                OperativeTemplate(
                    "aod_sniper", "歼灭者狙击手", "狙击手", 3, "7\"", "3+", 12,
                    R.drawable.roster_aod_sniper,
                    listOf(
                        w("bolt_pistol", "爆矢手枪", "4A · 3+ · 3/4 · 范围 8\""),
                        w("sniper_mobile", "爆矢狙击步枪（机动）", "4A · 2+ · 3/4"),
                        w("sniper_suppressed", "爆矢狙击步枪（压制）", "4A · 2+ · 2/4 · 爆炸 1\"、安静"),
                        w("sniper_mw", "爆矢狙击步枪（精准）", "4A · 2+ · 3/3 · 毁灭 3、穿刺 1、安静"),
                        w("fists", "双拳", "4A · 3+ · 3/4")
                    ),
                    listOf("bolt_pistol", "sniper_mobile", "fists")
                ),
                OperativeTemplate(
                    "aod_heavy_gunner", "重装仲裁者炮手", "重炮手", 3, "5\"", "3+", 18,
                    R.drawable.roster_aod_heavy_gunner,
                    listOf(
                        w("bolt_pistol", "爆矢手枪", "4A · 3+ · 3/4 · 范围 8\""),
                        w("heavy_focused", "重型爆矢枪（集中）", "5A · 3+ · 4/5 · 关键穿刺 1"),
                        w("heavy_sweeping", "重型爆矢枪（扫射）", "4A · 3+ · 4/5 · 关键穿刺 1、洪流 1\""),
                        w("fists", "双拳", "4A · 3+ · 3/4")
                    ),
                    listOf("bolt_pistol", "heavy_focused", "fists")
                ),
                OperativeTemplate(
                    "aod_gunner", "仲裁者炮手", "炮手", 3, "6\"", "3+", 14,
                    R.drawable.roster_aod_gunner,
                    listOf(
                        w("auto_bolt", "自动爆矢步枪", "4A · 3+ · 3/4 · 洪流 1\""),
                        w("bolt_rifle", "爆矢步枪", "4A · 3+ · 3/4 · 关键穿刺 1"),
                        w("stalker", "追猎者爆矢步枪", "4A · 3+ · 3/5 · 重型、致命 5+"),
                        w("launcher_frag", "附加榴弹发射器（破片）", "4A · 3+ · 2/4 · 爆炸 2\""),
                        w("launcher_krak", "附加榴弹发射器（穿甲）", "4A · 3+ · 4/5 · 穿刺 1"),
                        w("fists", "双拳", "4A · 3+ · 3/4")
                    ),
                    listOf("bolt_rifle", "launcher_frag", "launcher_krak", "fists")
                ),
                OperativeTemplate(
                    "aod_warrior", "仲裁者战士", "战士", 3, "6\"", "3+", 14,
                    R.drawable.roster_aod_warrior,
                    listOf(
                        w("auto_bolt", "自动爆矢步枪", "4A · 3+ · 3/4 · 洪流 1\""),
                        w("bolt_rifle", "爆矢步枪", "4A · 3+ · 3/4 · 关键穿刺 1"),
                        w("stalker", "追猎者爆矢步枪", "4A · 3+ · 3/5 · 重型、致命 5+"),
                        w("fists", "双拳", "4A · 3+ · 3/4")
                    ),
                    listOf("bolt_rifle", "fists")
                )
            )
        ),
        KillTeamCatalog(
            id = "CHAOS-PM",
            name = "瘟疫战士",
            subtitle = "混沌 · 阿斯塔特叛军 · 7 种成员",
            operatives = listOf(
                OperativeTemplate(
                    "pm_champion", "瘟疫战士勇士", "领袖", 3, "5\"", "3+", 15,
                    R.drawable.roster_pm_champion,
                    listOf(
                        w("plasma_standard", "等离子手枪（标准）", "4A · 3+ · 3/5 · 范围 8\"、穿刺 1"),
                        w("plasma_super", "等离子手枪（过载）", "4A · 3+ · 4/5 · 过热、致命 5+、穿刺 1"),
                        w("plague_sword", "瘟疫之剑", "5A · 3+ · 4/5 · 严重、毒素、剧毒")
                    ),
                    listOf("plasma_standard", "plague_sword")
                ),
                OperativeTemplate(
                    "pm_bombardier", "瘟疫战士掷弹兵", "专家", 3, "5\"", "3+", 14,
                    R.drawable.roster_pm_bombardier,
                    listOf(
                        w("boltgun", "爆矢枪", "4A · 3+ · 3/4"),
                        w("blight_grenade", "疫病手雷", "4A · 3+ · 2/4 · 爆炸 2\"、严重、毒素"),
                        w("krak", "穿甲手雷", "4A · 3+ · 4/5 · 范围 6\"、穿刺 1、集中"),
                        w("fists", "双拳", "4A · 3+ · 3/4")
                    ),
                    listOf("boltgun", "blight_grenade", "krak", "fists")
                ),
                OperativeTemplate(
                    "pm_fighter", "瘟疫战士斗士", "斗士", 3, "5\"", "3+", 14,
                    R.drawable.roster_pm_fighter,
                    listOf(
                        w("bolt_pistol", "爆矢手枪", "4A · 3+ · 3/4 · 范围 8\""),
                        w("flail", "腐化连枷", "5A · 3+ · 4/5 · 残暴、严重、震荡、毒素")
                    ),
                    listOf("bolt_pistol", "flail")
                ),
                OperativeTemplate(
                    "pm_heavy_gunner", "瘟疫战士重炮手", "重炮手", 3, "5\"", "3+", 14,
                    R.drawable.roster_pm_heavy_gunner,
                    listOf(
                        w("bolt_pistol", "爆矢手枪", "4A · 3+ · 3/4 · 范围 8\""),
                        w("plague_spewer", "瘟疫喷吐炮", "5A · 2+ · 3/3 · 集中、严重、洪流 2\"、毒素"),
                        w("fists", "双拳", "4A · 3+ · 3/4")
                    ),
                    listOf("bolt_pistol", "plague_spewer", "fists")
                ),
                OperativeTemplate(
                    "pm_icon_bearer", "瘟疫战士持徽手", "持徽手", 3, "5\"", "3+", 14,
                    R.drawable.roster_pm_icon_bearer,
                    listOf(
                        w("bolt_pistol", "爆矢手枪", "4A · 3+ · 3/4 · 范围 8\""),
                        w("plague_knife", "瘟疫匕首", "5A · 3+ · 3/4 · 严重、毒素")
                    ),
                    listOf("bolt_pistol", "plague_knife")
                ),
                OperativeTemplate(
                    "pm_plaguecaster", "恶瘟投放者", "灵能者", 3, "5\"", "3+", 14,
                    R.drawable.roster_pm_plaguecaster,
                    listOf(
                        w("entropy", "熵能瘟疫", "4A · 3+ · 3/7 · 灵能、范围 7\"、严重、毒素"),
                        w("plague_wind", "瘟疫之风", "6A · 3+ · 2/3 · 灵能、洪流 1\"、毒素"),
                        w("staff", "腐化法杖", "4A · 3+ · 3/4")
                    ),
                    listOf("entropy", "plague_wind", "staff")
                ),
                OperativeTemplate(
                    "pm_warrior", "瘟疫战士士兵", "战士", 3, "5\"", "3+", 14,
                    R.drawable.roster_pm_warrior,
                    listOf(
                        w("boltgun", "爆矢枪", "4A · 3+ · 3/4 · 剧毒"),
                        w("plague_knife", "瘟疫匕首", "4A · 3+ · 3/4 · 严重、毒素"),
                        w("bolt_pistol", "爆矢手枪（PDF 勘误）", "4A · 3+ · 3/4 · 范围 8\"")
                    ),
                    listOf("boltgun", "plague_knife")
                )
            )
        ),
        KillTeamCatalog(
            id = "TYR-RAV",
            name = "蛇虫",
            subtitle = "大吞噬者 · 泰伦虫族 · 6 种成员",
            operatives = listOf(
                OperativeTemplate(
                    "rav_prime", "蛇虫王虫", "领袖", 3, "7\"", "5+", 19,
                    R.drawable.roster_rav_prime,
                    listOf(
                        w("tail_blade", "尾刃", "4A · 3+ · 3/4 · 范围 3\"、撕裂、安静"),
                        w("talons", "镰爪与撕裂爪", "5A · 3+ · 4/5 · 撕裂")
                    ),
                    listOf("tail_blade", "talons")
                ),
                OperativeTemplate(
                    "rav_felltalon", "毒爪蛇虫", "专家", 3, "7\"", "5+", 18,
                    R.drawable.roster_rav_felltalon,
                    listOf(
                        w("pincer_tail", "螯尾", "4A · 3+ · 3/4 · 范围 3\"、安静"),
                        w("toxic_grubs", "毒蛆", "4A · 3+ · 3/4 · 范围 6\"、安静、中毒"),
                        w("toxic_talons", "毒素镰爪", "5A · 3+ · 4/5 · 致命 5+、震荡、中毒")
                    ),
                    listOf("pincer_tail", "toxic_grubs", "toxic_talons")
                ),
                OperativeTemplate(
                    "rav_tremorscythe", "颤镰蛇虫", "专家", 3, "7\"", "5+", 18,
                    R.drawable.roster_rav_tremorscythe,
                    listOf(
                        w("pincer_tail", "螯尾", "4A · 3+ · 3/4 · 范围 3\"、安静"),
                        w("talons", "镰爪与撕裂爪", "5A · 3+ · 4/5 · 撕裂")
                    ),
                    listOf("pincer_tail", "talons")
                ),
                OperativeTemplate(
                    "rav_venomspitter", "喷毒蛇虫", "炮手", 3, "7\"", "5+", 18,
                    R.drawable.roster_rav_venomspitter,
                    listOf(
                        w("pincer_tail", "螯尾", "4A · 3+ · 3/4 · 范围 3\"、安静"),
                        w("venom_blast", "毒液喷吐（爆炸）", "4A · 3+ · 3/5 · 范围 8\"、爆炸 2\"、中毒"),
                        w("venom_pierce", "毒液喷吐（穿刺）", "4A · 3+ · 4/5 · 范围 8\"、穿刺 1、中毒"),
                        w("talons", "镰爪", "5A · 3+ · 4/5")
                    ),
                    listOf("pincer_tail", "venom_blast", "talons")
                ),
                OperativeTemplate(
                    "rav_warrior", "蛇虫武士", "战士", 3, "7\"", "5+", 18,
                    R.drawable.roster_rav_warrior,
                    listOf(
                        w("pincer_tail", "螯尾", "4A · 3+ · 3/4 · 范围 3\"、安静"),
                        w("talons", "镰爪", "5A · 3+ · 4/5")
                    ),
                    listOf("pincer_tail", "talons")
                ),
                OperativeTemplate(
                    "rav_wrecker", "破袭蛇虫", "专家", 3, "7\"", "4+", 18,
                    R.drawable.roster_rav_wrecker,
                    listOf(
                        w("bio_missile", "生体飞弹", "4A · 3+ · 3/4 · 范围 3\"、穿刺 1、安静"),
                        w("crushing_claws", "镰爪与粉碎爪", "5A · 3+ · 4/5 · 粉碎")
                    ),
                    listOf("bio_missile", "crushing_claws")
                )
            )
        )
    ) + WarpCovenCatalog.team + ImportedTeamCatalog.teams + FellgorCatalog.team

    fun team(teamId: String): KillTeamCatalog = teams.firstOrNull { it.id == teamId } ?: teams.first()

    fun operative(team: KillTeamCatalog, operativeId: String): OperativeTemplate? =
        team.operatives.firstOrNull { it.id == operativeId }
}
