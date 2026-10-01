package com.example.helloworld

/** Explicit ownership, not keyword matching: a personal ability may mention a ploy. */
object RuleOrganization {
    val supplementaryTeamCategories = listOf("标识指南", "勘误与问答")
    fun label(category: String) = category
    fun personalAbilities(operativeId: String) = AbilityCatalog.forOperative(operativeId).filterNot { it.factionRule }
}
