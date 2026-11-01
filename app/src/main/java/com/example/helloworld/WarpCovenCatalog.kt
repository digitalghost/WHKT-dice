package com.example.helloworld

/** Data-driven accessor for CHAOS-WC (loaded from assets/teams/CHAOS-WC.json via [TeamDataLoader]). */
object WarpCovenCatalog {
    val team: KillTeamCatalog
        get() = RosterCatalog.team("CHAOS-WC")

    fun forOperative(id: String): List<AbilityDefinition> =
        AbilityCatalog.forOperative(id)
}
