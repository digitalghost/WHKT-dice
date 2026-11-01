package com.example.helloworld

/**
 * Rule-legal canonical rosters for all 48 Kill Teams, constructed strictly according to each
 * team's official 编成与武器选择 rules (operative counts, leader requirement, specialist/gunner caps,
 * half-slot pairs, and default weapon loadouts).
 */
object CanonicalRosters {
    val memberIdsByTeam: Map<String, List<String>> = mapOf(
        "CHAOS-FELL" to listOf(
            "chaos_fell_ih", "chaos_fell_dk", "chaos_fell_fb", "chaos_fell_gs", "chaos_fell_gh",
            "chaos_fell_hg", "chaos_fell_mng", "chaos_fell_sha", "chaos_fell_tox", "chaos_fell_vnd"
        ),
        "CHAOS-PM" to listOf(
            "pm_champion", "pm_bombardier", "pm_fighter", "pm_heavy_gunner", "pm_icon_bearer", "pm_plaguecaster"
        ),
        "CHAOS-WC" to listOf(
            "wc_sod", "wc_sot", "wc_sow", "wc_gnr", "wc_tzc", "wc_tzhb"
        ),
        "IMP-AOD" to listOf(
            "aod_sergeant", "aod_grenadier", "aod_sniper", "aod_heavy_gunner", "aod_gunner", "aod_assault_warrior"
        ),
        "IMP-CI" to listOf(
            "imp_ci_sup", "imp_ci_abj", "imp_ci_cen", "imp_ci_cre", "imp_ci_den",
            "imp_ci_mor", "imp_ci_rel", "imp_ci_war", "imp_ci_war"
        ),
        "IMP-WS" to listOf(
            "imp_ws_ldr", "imp_ws_wlf", "imp_ws_fb", "imp_ws_fe", "imp_ws_gnr", "imp_ws_rp"
        ),
        "KT-BATTLECLADE" to listOf(
            "battleclade_01", "battleclade_06", "battleclade_02", "battleclade_03", "battleclade_05",
            "battleclade_07", "battleclade_04", "battleclade_04", "battleclade_04", "battleclade_04"
        ),
        "KT-BLADES_OF_KHAINE" to listOf(
            "kt_bok_hb_exarch", "kt_bok_da_warrior", "kt_bok_da_warrior", "kt_bok_da_warrior",
            "kt_bok_hb_warrior", "kt_bok_hb_warrior", "kt_bok_ss_warrior", "kt_bok_ss_warrior"
        ),
        "KT-BLOODED" to listOf(
            "blooded_01", "blooded_02", "blooded_03", "blooded_04", "blooded_05", "blooded_07",
            "blooded_08", "blooded_10", "blooded_11", "blooded_12", "blooded_06", "blooded_09"
        ),
        "KT-BROOD_BROTHERS" to listOf(
            "kt_bb_commander", "kt_bb_agitator", "kt_bb_gunner", "kt_bb_iconward", "kt_bb_knife_fighter",
            "kt_bb_medic", "kt_bb_sapper", "kt_bb_sniper", "kt_bb_veteran", "kt_bb_vox_operator", "kt_bb_patriarch"
        ),
        "KT-CHAOS_CULT" to listOf(
            "chaos_cult_01", "chaos_cult_02", "chaos_cult_02", "chaos_cult_03", "chaos_cult_04",
            "chaos_cult_05", "chaos_cult_05", "chaos_cult_05", "chaos_cult_05", "chaos_cult_05",
            "chaos_cult_05", "chaos_cult_05", "chaos_cult_05", "chaos_cult_05"
        ),
        "KT-CORSAIR_VOIDSCARRED" to listOf(
            "corsair_voidscarred_01", "corsair_voidscarred_02", "corsair_voidscarred_03",
            "corsair_voidscarred_05", "corsair_voidscarred_06", "corsair_voidscarred_07",
            "corsair_voidscarred_08", "corsair_voidscarred_09", "corsair_voidscarred_11"
        ),
        "KT-DEATHWATCH" to listOf(
            "deathwatch_01", "deathwatch_02", "deathwatch_03", "deathwatch_05", "deathwatch_08"
        ),
        "KT-DEATH_KORPS" to listOf(
            "death_korps_01", "death_korps_02", "death_korps_03", "death_korps_04", "death_korps_05",
            "death_korps_06", "death_korps_07", "death_korps_08", "death_korps_10", "death_korps_11",
            "death_korps_09", "death_korps_09", "death_korps_09", "death_korps_09"
        ),
        "KT-ELUCIDIAN_STARSTRIDERS" to listOf(
            "elucidian_starstriders_01", "elucidian_starstriders_02", "elucidian_starstriders_03",
            "elucidian_starstriders_04", "elucidian_starstriders_05", "elucidian_starstriders_06",
            "elucidian_starstriders_07", "elucidian_starstriders_07", "elucidian_starstriders_07", "elucidian_starstriders_07"
        ),
        "KT-EXACTION_SQUAD" to listOf(
            "exaction_squad_01", "exaction_squad_02", "exaction_squad_03", "exaction_squad_04",
            "exaction_squad_05", "exaction_squad_06", "exaction_squad_07", "exaction_squad_08",
            "exaction_squad_09", "exaction_squad_10", "exaction_squad_12"
        ),
        "KT-EXODITE_DRAGON_MASTERS" to listOf(
            "exodite_dragon_masters_01", "exodite_dragon_masters_02", "exodite_dragon_masters_03",
            "exodite_dragon_masters_04", "exodite_dragon_masters_04"
        ),
        "KT-FARSTALKER_KINBAND" to listOf(
            "farstalker_kinband_01", "farstalker_kinband_02", "farstalker_kinband_03", "farstalker_kinband_04",
            "farstalker_kinband_05", "farstalker_kinband_07", "farstalker_kinband_08", "farstalker_kinband_09",
            "farstalker_kinband_10", "farstalker_kinband_11", "farstalker_kinband_06", "farstalker_kinband_06"
        ),
        "KT-GELLERPOX_INFECTED" to listOf(
            "gellerpox_infected_01", "gellerpox_infected_02", "gellerpox_infected_03", "gellerpox_infected_04",
            "gellerpox_infected_06", "gellerpox_infected_06", "gellerpox_infected_06", "gellerpox_infected_06",
            "gellerpox_infected_05", "gellerpox_infected_05", "gellerpox_infected_05"
        ),
        "KT-GOREMONGERS" to listOf(
            "goremongers_01", "goremongers_03", "goremongers_04", "goremongers_05",
            "goremongers_06", "goremongers_07", "goremongers_02", "goremongers_02"
        ),
        "KT-HAND_OF_THE_ARCHON" to listOf(
            "hand_of_the_archon_01", "hand_of_the_archon_03", "hand_of_the_archon_04",
            "hand_of_the_archon_05", "hand_of_the_archon_06", "hand_of_the_archon_07",
            "hand_of_the_archon_08", "hand_of_the_archon_09", "hand_of_the_archon_02"
        ),
        "KT-HEARTHKYN_SALVAGERS" to listOf(
            "hearthkyn_salvagers_01", "hearthkyn_salvagers_02", "hearthkyn_salvagers_03", "hearthkyn_salvagers_04",
            "hearthkyn_salvagers_05", "hearthkyn_salvagers_06", "hearthkyn_salvagers_07", "hearthkyn_salvagers_08",
            "hearthkyn_salvagers_09", "hearthkyn_salvagers_10"
        ),
        "KT-HERNKYN_YAEGRIS" to listOf(
            "hernkyn_yaegris_01", "hernkyn_yaegris_02", "hernkyn_yaegris_03", "hernkyn_yaegris_04",
            "hernkyn_yaegris_05", "hernkyn_yaegris_06", "hernkyn_yaegris_07",
            "hernkyn_yaegris_08", "hernkyn_yaegris_08", "hernkyn_yaegris_08"
        ),
        "KT-HIEROTEK_CIRCLE" to listOf(
            "hierotek_circle_01", "hierotek_circle_04", "hierotek_circle_05", "hierotek_circle_06",
            "hierotek_circle_08", "hierotek_circle_07", "hierotek_circle_09", "hierotek_circle_09"
        ),
        "KT-HUNTER_CLADE" to listOf(
            "hunter_clade_02", "hunter_clade_05", "hunter_clade_05", "hunter_clade_06", "hunter_clade_06",
            "hunter_clade_07", "hunter_clade_13", "hunter_clade_08", "hunter_clade_12", "hunter_clade_14"
        ),
        "KT-IMPERIAL_NAVY_BREACHERS" to listOf(
            "kt_inb_sergeant", "kt_inb_axejack", "kt_inb_endurant", "kt_inb_grenadier",
            "kt_inb_gunner", "kt_inb_hatchcutter", "kt_inb_surveyor", "kt_inb_void_jammer",
            "kt_inb_cat_unit", "kt_inb_gheistskull", "kt_inb_armsman", "kt_inb_armsman", "kt_inb_armsman"
        ),
        "KT-INQUISITORIAL_AGENTS" to listOf(
            "inquisitorial_agents_01", "inquisitorial_agents_tome_skull",
            "inquisitorial_agents_02", "inquisitorial_agents_03", "inquisitorial_agents_04",
            "inquisitorial_agents_05", "inquisitorial_agents_08",
            "inquisitorial_agents_14", "inquisitorial_agents_15", "inquisitorial_agents_17",
            "inquisitorial_agents_16", "inquisitorial_agents_16"
        ),
        "KT-KASRKIN" to listOf(
            "kt_kas_sergeant", "kt_kas_combat_medic", "kt_kas_demo_trooper", "kt_kas_recon_trooper",
            "kt_kas_vox_trooper", "kt_kas_sharpshooter", "kt_kas_gunner", "kt_kas_gunner",
            "kt_kas_trooper", "kt_kas_trooper"
        ),
        "KT-KOMMANDOS" to listOf(
            "kt_kom_boss_nob", "kt_kom_bomb_squig", "kt_kom_grot", "kt_kom_breacha_boy",
            "kt_kom_burna_boy", "kt_kom_comms_boy", "kt_kom_dakka_boy", "kt_kom_rokkit_boy",
            "kt_kom_slasha_boy", "kt_kom_snipa_boy", "kt_kom_boy"
        ),
        "KT-LEGIONARIES" to listOf(
            "legionaries_02", "legionaries_03", "legionaries_04", "legionaries_05", "legionaries_06", "legionaries_09"
        ),
        "KT-MANDRAKES" to listOf(
            "mandrakes_01", "mandrakes_02", "mandrakes_03", "mandrakes_04", "mandrakes_05",
            "mandrakes_06", "mandrakes_06", "mandrakes_06", "mandrakes_06"
        ),
        "KT-MURDERWING" to listOf(
            "murderwing_01", "murderwing_02", "murderwing_03", "murderwing_04", "murderwing_07", "murderwing_09"
        ),
        "KT-NEMESIS_CLAW" to listOf(
            "nemesis_claw_01", "nemesis_claw_02", "nemesis_claw_03", "nemesis_claw_05", "nemesis_claw_06", "nemesis_claw_07"
        ),
        "KT-NOVITIATES" to listOf(
            "novitiates_01", "novitiates_02", "novitiates_03", "novitiates_04", "novitiates_05",
            "novitiates_06", "novitiates_08", "novitiates_09", "novitiates_10", "novitiates_11"
        ),
        "KT-PATHFINDERS" to listOf(
            "kt_pf_shasui", "kt_pf_grenadier", "kt_pf_blooded", "kt_pf_comms", "kt_pf_dronectl",
            "kt_pf_marksman", "kt_pf_medtech", "kt_pf_transpectral", "kt_pf_wexpert", "kt_pf_wexpert",
            "kt_pf_mv1", "kt_pf_mv4"
        ),
        "KT-PHOBOS_STRIKE_TEAM" to listOf(
            "phobos_strike_team_01", "phobos_strike_team_02", "phobos_strike_team_03",
            "phobos_strike_team_04", "phobos_strike_team_09", "phobos_strike_team_10"
        ),
        "KT-RATLINGS" to listOf(
            "ratlings_01", "ratlings_02", "ratlings_05", "ratlings_06", "ratlings_07",
            "ratlings_08", "ratlings_09", "ratlings_11", "ratlings_12", "ratlings_03", "ratlings_03"
        ),
        "KT-SANCTIFIERS" to listOf(
            "sanctifiers_01", "sanctifiers_02", "sanctifiers_03", "sanctifiers_04", "sanctifiers_05",
            "sanctifiers_06", "sanctifiers_07", "sanctifiers_08", "sanctifiers_10", "sanctifiers_11", "sanctifiers_09"
        ),
        "KT-SCOUT_SQUAD" to listOf(
            "kt_ss_sergeant", "kt_ss_heavy_gunner", "kt_ss_hunter", "kt_ss_sniper", "kt_ss_tracker",
            "kt_ss_warrior", "kt_ss_warrior", "kt_ss_warrior", "kt_ss_warrior"
        ),
        "KT-SPECTRE_SQUAD" to listOf(
            "spectre_squad_01", "spectre_squad_12", "spectre_squad_02", "spectre_squad_03",
            "spectre_squad_04", "spectre_squad_05", "spectre_squad_06", "spectre_squad_07",
            "spectre_squad_08", "spectre_squad_09", "spectre_squad_11"
        ),
        "KT-TEMPESTUS_AQUILONS" to listOf(
            "tempestus_aquilons_01", "tempestus_aquilons_07", "tempestus_aquilons_02", "tempestus_aquilons_03",
            "tempestus_aquilons_04", "tempestus_aquilons_05", "tempestus_aquilons_06",
            "tempestus_aquilons_08", "tempestus_aquilons_08", "tempestus_aquilons_08", "tempestus_aquilons_08"
        ),
        "KT-VESPID_STINGWINGS" to listOf(
            "kt_vs_strain_leader", "kt_vs_oversight_drone", "kt_vs_longsting", "kt_vs_shadestrain",
            "kt_vs_skyblast", "kt_vs_swarmguard",
            "kt_vs_warrior", "kt_vs_warrior", "kt_vs_warrior", "kt_vs_warrior", "kt_vs_warrior"
        ),
        "KT-VOID_DANCER_TROUPE" to listOf(
            "void_dancer_troupe_01", "void_dancer_troupe_02", "void_dancer_troupe_04",
            "void_dancer_troupe_03", "void_dancer_troupe_03", "void_dancer_troupe_03",
            "void_dancer_troupe_03", "void_dancer_troupe_03"
        ),
        "KT-WRECKA_KREW" to listOf(
            "wrecka_krew_01", "wrecka_krew_02", "wrecka_krew_02", "wrecka_krew_03",
            "wrecka_krew_04", "wrecka_krew_05", "wrecka_krew_06", "wrecka_krew_07"
        ),
        "KT-XV26_STEALTH_BATTLESUITS" to listOf(
            "kt_xv26_shasvre", "kt_xv26_mv75", "kt_xv26_mv15",
            "kt_xv26_designator", "kt_xv26_liberator", "kt_xv26_lodestar", "kt_xv26_neutraliser"
        ),
        "NEC-CAN" to listOf(
            "nec_can_geo", "nec_can_tc", "nec_can_tc", "nec_can_acc", "nec_can_rea",
            "nec_can_war", "nec_can_war", "nec_can_war"
        ),
        "TYR-RAV" to listOf(
            "rav_prime", "rav_felltalon", "rav_tremorscythe", "rav_venomspitter", "rav_wrecker"
        ),
        "WYRM" to listOf(
            "wyrm_leader", "wyrm_kelermorph", "wyrm_locus", "wyrm_gunner", "wyrm_gunner",
            "wyrm_heavy_gunner", "wyrm_heavy_gunner", "wyrm_icon_bearer",
            "wyrm_warrior", "wyrm_warrior", "wyrm_warrior", "wyrm_warrior"
        )
    )

    fun forTeam(teamId: String): SavedRoster {
        val team = RosterCatalog.team(teamId)
        val operativeIds = memberIdsByTeam[teamId] ?: team.operatives.map { it.id }
        val counts = mutableMapOf<String, Int>()
        val totalCounts = operativeIds.groupingBy { it }.eachCount()
        val members = operativeIds.mapIndexed { idx, opId ->
            val template = requireNotNull(RosterCatalog.operative(team, opId)) {
                "Missing operative $opId in team $teamId"
            }
            val n = (counts[opId] ?: 0) + 1
            counts[opId] = n
            val callsign = if ((totalCounts[opId] ?: 1) > 1) "${template.name} $n" else template.name
            val weaponIds = when {
                teamId == "KT-KASRKIN" && opId == "kt_kas_gunner" -> when (n) {
                    1 -> listOf("kt_kas_gun_plasma_std", "kt_kas_gun_plasma_sc", "kt_kas_gun_gun_butt")
                    2 -> listOf("kt_kas_gun_meltagun", "kt_kas_gun_gun_butt")
                    3 -> listOf("kt_kas_gun_volley_focused", "kt_kas_gun_volley_sweeping", "kt_kas_gun_gun_butt")
                    else -> listOf("kt_kas_gun_gl_frag", "kt_kas_gun_gl_krak", "kt_kas_gun_gun_butt")
                }
                teamId == "KT-VOID_DANCER_TROUPE" && opId == "void_dancer_troupe_03" -> when (n) {
                    1 -> listOf("void_dancer_troupe_03_w01", "void_dancer_troupe_03_w07")
                    2 -> listOf("void_dancer_troupe_03_w02", "void_dancer_troupe_03_w06")
                    3 -> listOf("void_dancer_troupe_03_w03", "void_dancer_troupe_03_w05")
                    4 -> listOf("void_dancer_troupe_03_w03", "void_dancer_troupe_03_w04")
                    else -> listOf("void_dancer_troupe_03_w03", "void_dancer_troupe_03_w07")
                }
                teamId == "KT-ELUCIDIAN_STARSTRIDERS" && opId == "elucidian_starstriders_07" && n == 4 ->
                    listOf("elucidian_starstriders_07_w02", "elucidian_starstriders_07_w03", "elucidian_starstriders_07_w04")
                teamId == "NEC-CAN" && opId == "nec_can_tc" && n == 2 ->
                    listOf("nec_can_tc_tgr_01", "nec_can_tc_tgr_02", "nec_can_tc_c_01")
                else -> template.defaultWeaponIds.ifEmpty { template.weapons.map { it.id } }
            }
            RosterMember(
                id = "${teamId.lowercase()}_m${idx + 1}",
                operativeId = opId,
                callsign = callsign,
                weaponIds = weaponIds
            )
        }.toMutableList()
        return SavedRoster(
            rosterId = "canonical_${teamId.lowercase()}",
            teamId = teamId,
            name = "${team.name}标准编成",
            members = members,
            updatedAt = 1730000000000L
        )
    }

    fun all(): List<SavedRoster> = RosterCatalog.teams.map { forTeam(it.id) }
}
