package com.example.helloworld

enum class WeaponKind {
    RANGED,
    MELEE
}

object WeaponActions {
    fun legacyKind(id: String): WeaponKind =
        if (TeamDataLoader.isMelee(id)) WeaponKind.MELEE else WeaponKind.RANGED

    fun isMelee(id: String): Boolean = legacyKind(id) == WeaponKind.MELEE

    fun label(kind: WeaponKind): String = if (kind == WeaponKind.MELEE) "交战" else "射击"

    fun label(id: String): String = label(legacyKind(id))
}
