package com.example.helloworld

import java.io.File

// Data-driven accessor for NEC-CAN, IMP-WS, IMP-CI (loaded from assets/teams and assets/rules).
object ImportedTeamCatalog {
    private val teamIds = listOf("NEC-CAN", "IMP-WS", "IMP-CI")

    val teams: List<KillTeamCatalog>
        get() = teamIds.map { RosterCatalog.team(it) }

    val abilities: Map<String, List<AbilityDefinition>>
        get() = teams.flatMap { it.operatives }.associate { it.id to AbilityCatalog.forOperative(it.id) }

    val abilityCards: Map<String, List<String>>
        get() = teams.flatMap { it.operatives }.associate { it.id to it.abilityAssets }

    val equipment: Map<String, List<EquipmentCard>> by lazy {
        val rulesDir = sequenceOf(
            File("src/main/assets/rules"),
            File("app/src/main/assets/rules"),
            File("../app/src/main/assets/rules")
        ).firstOrNull { it.isDirectory }
        teamIds.associateWith { id ->
            val file = rulesDir?.let { File(it, "$id.json") }
            if (file != null && file.exists()) {
                val root = MiniJson.parseObject(file.readText())
                @Suppress("UNCHECKED_CAST")
                val entries = (root["entries"] as? List<Map<String, Any?>>).orEmpty()
                entries.filter { it["category"] == "阵营装备" }.map { e ->
                    @Suppress("UNCHECKED_CAST")
                    val cards = (e["cards"] as? List<String>).orEmpty()
                    EquipmentCard(
                        name = e["title"] as? String ?: "",
                        count = "阵营装备",
                        rules = e["body"] as? String ?: "",
                        asset = cards.firstOrNull()?.let { "team_rules/$it" },
                        currentText = true
                    )
                }
            } else emptyList()
        }
    }

    fun teamArt(id: String): Int? = TeamMarks.resourceOrNull(id)
}
