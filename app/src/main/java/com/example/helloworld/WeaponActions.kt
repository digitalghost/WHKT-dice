package com.example.helloworld

object WeaponActions {
    fun isMelee(id: String): Boolean = id in FellgorCatalog.meleeWeaponIds || id in ImportedTeamCatalog.meleeWeaponIds || id in WarpCovenCatalog.meleeWeaponIds || id in setOf(
        "power_fist", "chainsword", "power_weapon", "thunder_hammer", "fists",
        "plague_sword", "flail", "plague_knife", "staff", "talons", "toxic_talons", "crushing_claws"
    )
    fun label(id: String) = if(isMelee(id)) "交战" else "射击"
}
