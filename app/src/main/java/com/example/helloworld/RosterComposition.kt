package com.example.helloworld

/** Concise, decision-focused summaries shown while a player builds a roster. */
object RosterComposition {
    private val registered = mutableMapOf<String, String>()

    fun register(teamId: String, hint: String) {
        registered[teamId] = hint
    }

    fun hint(teamId: String): String {
        TeamDataLoader.ensureLoaded()
        return registered[teamId] ?: "请按照下方编成规则原卡选择成员。"
    }
}
