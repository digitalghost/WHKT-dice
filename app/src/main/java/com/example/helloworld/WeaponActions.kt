package com.example.helloworld

enum class WeaponKind {
    RANGED,
    MELEE
}

object WeaponActions {
    fun legacyKind(id: String): WeaponKind = if (
        id in FellgorCatalog.meleeWeaponIds || id in ImportedTeamCatalog.meleeWeaponIds || id in WarpCovenCatalog.meleeWeaponIds || id in setOf(
        "power_fist", "chainsword", "power_weapon", "thunder_hammer", "fists",
        "plague_sword", "flail", "plague_knife", "staff", "talons", "toxic_talons", "crushing_claws"
        )
    ) WeaponKind.MELEE else WeaponKind.RANGED

    fun isMelee(id: String): Boolean = legacyKind(id) == WeaponKind.MELEE

    fun label(kind: WeaponKind): String = if (kind == WeaponKind.MELEE) "交战" else "射击"

    fun label(id: String): String = label(legacyKind(id))
}
