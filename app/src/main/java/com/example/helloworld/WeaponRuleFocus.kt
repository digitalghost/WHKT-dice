package com.example.helloworld

/** Keyword parameters stay visible; aliases only affect matching, never dice logic. */
object WeaponRuleFocus {
    fun definition(keyword: String, teamId: String? = null): RuleDefinition? =
        RuleGlossary.definitionFor(keyword.replace("晕眩", "眩晕"), teamId)
    fun matches(label: String, keywords: List<String>): Boolean {
        val names = label.split('／', '/').mapNotNull { definition(it)?.id }.toSet()
        return keywords.any { keyword ->
            val id = definition(keyword)?.id
            id != null && (id in names || (id == "piercing_crit" && "piercing" in names))
        }
    }
}
