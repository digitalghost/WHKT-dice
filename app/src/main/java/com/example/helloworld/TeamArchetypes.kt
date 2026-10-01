package com.example.helloworld

object TeamArchetypes {
    const val INFILTRATION = "渗透"
    const val RECON = "侦察"
    const val SECURITY = "安全保护"
    const val SEEK_AND_DESTROY = "搜索与摧毁"

    private val byTeamId = mapOf(
        "IMP-AOD" to listOf(SECURITY, SEEK_AND_DESTROY),
        "CHAOS-PM" to listOf(SECURITY, SEEK_AND_DESTROY),
        "TYR-RAV" to listOf(INFILTRATION, SEEK_AND_DESTROY),
        "CHAOS-WC" to listOf(RECON, SECURITY),
        "NEC-CAN" to listOf(RECON, SECURITY),
        "IMP-WS" to listOf(SEEK_AND_DESTROY, RECON),
        "IMP-CI" to listOf(SECURITY, SEEK_AND_DESTROY),
        "CHAOS-FELL" to listOf(RECON, SEEK_AND_DESTROY)
    )

    fun forTeam(teamId: String): List<String> = byTeamId[teamId].orEmpty()

    fun supports(teamId: String, archetype: String?): Boolean =
        archetype == null || archetype in forTeam(teamId)
}
