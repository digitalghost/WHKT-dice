package com.example.helloworld

object TeamArchetypes {
    const val INFILTRATION = "渗透"
    const val RECON = "侦察"
    const val SECURITY = "安全保护"
    const val SEEK_AND_DESTROY = "搜索与摧毁"

    /** Archetypes of teams loaded from assets/teams (TeamDataLoader). */
    private val registered = mutableMapOf<String, List<String>>()

    fun register(teamId: String, archetypes: List<String>) {
        registered[teamId] = archetypes
    }

    fun forTeam(teamId: String): List<String> {
        TeamDataLoader.ensureLoaded()
        return registered[teamId].orEmpty()
    }

    fun supports(teamId: String, archetype: String?): Boolean =
        archetype == null || archetype in forTeam(teamId)
}
