package com.example.helloworld

/** Data-driven accessor for CHAOS-FELL (loaded from assets/teams/CHAOS-FELL.json via [TeamDataLoader]). */
object FellgorCatalog {
    val team: KillTeamCatalog
        get() = RosterCatalog.team("CHAOS-FELL")

    val abilities: Map<String, List<AbilityDefinition>>
        get() = team.operatives.associate { it.id to AbilityCatalog.forOperative(it.id) }
}
