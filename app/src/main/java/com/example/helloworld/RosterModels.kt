package com.example.helloworld

import androidx.annotation.DrawableRes

data class WeaponOption(
    val id: String,
    val name: String,
    val profile: String,
    val declaredKind: WeaponKind? = null
) {
    val stats: WeaponStats
        get() = WeaponProfileParser.parse(profile)
    val kind: WeaponKind
        get() = declaredKind ?: WeaponActions.legacyKind(id)
}

data class WeaponStats(
    val attacks: Int,
    val hit: Int,
    val normalDamage: Int,
    val criticalDamage: Int,
    val keywords: List<String>
) {
    val criticalThreshold: Int
        get() = keywords.firstNotNullOfOrNull { keyword ->
            Regex("致命\\s*(\\d)\\+").find(keyword)?.groupValues?.get(1)?.toIntOrNull()
        } ?: 6
}

object WeaponProfileParser {
    fun parse(profile: String): WeaponStats {
        val parts = profile.split('·').map(String::trim).filter(String::isNotBlank)
        val damage = parts.getOrNull(2).orEmpty().split('/').map(String::trim)
        val keywordText = parts.drop(3).joinToString("、")
        return WeaponStats(
            attacks = parts.getOrNull(0)?.filter(Char::isDigit)?.toIntOrNull() ?: 1,
            hit = parts.getOrNull(1)?.filter(Char::isDigit)?.toIntOrNull() ?: 6,
            normalDamage = damage.getOrNull(0)?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
            criticalDamage = damage.getOrNull(1)?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
            keywords = keywordText.split('、', ',').map(String::trim).filter(String::isNotBlank)
        )
    }
}

data class AbilityDefinition(
    val id: String,
    val name: String,
    val description: String,
    val apCost: Int? = null,
    val factionRule: Boolean = false
)

data class OperativeCardSource(
    val label: String,
    val asset: String? = null,
    @DrawableRes val resource: Int? = null
)

data class OperativeTemplate(
    val id: String,
    val name: String,
    val role: String,
    val apl: Int,
    val move: String,
    val save: String,
    val wounds: Int,
    @DrawableRes val cardRes: Int,
    val weapons: List<WeaponOption>,
    val defaultWeaponIds: List<String>,
    val baseSizeMm: Int? = null,
    val keywords: List<String> = emptyList(),
    /** Datacard image in assets for teams loaded from JSON; [cardRes] is then a placeholder. */
    val cardAsset: String? = null,
    val abilityAssets: List<String> = emptyList()
) {
    /** Primary card asset for portrait fallback (datacard asset, or first ability card when no datacard). */
    val primaryCardAsset: String?
        get() = cardAsset ?: abilityAssets.firstOrNull()?.let {
            if (it.startsWith("team_rules/")) it else "team_rules/$it"
        }

    fun matchesWeaponId(weaponId: String, selectedIds: Collection<String>): Boolean {
        if (weaponId in selectedIds || weaponId.removePrefix("${id}_") in selectedIds) return true
        val target = weapons.firstOrNull { it.id == weaponId } ?: return false
        if ('（' !in target.name) return false
        val baseName = target.name.substringBefore('（').trim()
        return weapons.any { w ->
            w.kind == target.kind &&
                '（' in w.name &&
                w.name.substringBefore('（').trim() == baseName &&
                (w.id in selectedIds || w.id.removePrefix("${id}_") in selectedIds)
        }
    }

    fun cardSources(): List<OperativeCardSource> {
        fun normalizeAsset(path: String): String =
            if (path.startsWith("team_rules/")) path else "team_rules/$path"
        fun isAbilityName(name: String): Boolean = "能力卡" in name || "-能力-" in name

        val raw = mutableListOf<Pair<String?, Int?>>()
        if (!cardAsset.isNullOrBlank()) {
            raw += normalizeAsset(cardAsset) to null
        } else if (cardRes != 0) {
            raw += null to cardRes
        }
        abilityAssets.forEach { asset ->
            if (asset.isNotBlank()) {
                val normalized = normalizeAsset(asset)
                if (raw.none { it.first == normalized }) {
                    raw += normalized to null
                }
            }
        }
        val abilityFlags = raw.mapIndexed { index, (asset, _) ->
            if (asset != null) isAbilityName(asset) else index > 0
        }
        val abilityCount = abilityFlags.count { it }
        var abilityIndex = 0
        return raw.mapIndexed { index, (asset, resId) ->
            val label = if (abilityFlags[index]) {
                abilityIndex += 1
                if (abilityCount > 1) "能力卡 $abilityIndex" else "能力卡"
            } else {
                "数据卡"
            }
            OperativeCardSource(label = label, asset = asset, resource = resId)
        }
    }
}

data class KillTeamCatalog(
    val id: String,
    val name: String,
    val subtitle: String,
    val operatives: List<OperativeTemplate>
)

data class RosterMember(
    val id: String,
    val operativeId: String,
    val callsign: String,
    val weaponIds: List<String>,
    val customAvatarUri: String? = null
)

data class SavedRoster(
    val rosterId: String,
    val teamId: String,
    val name: String,
    val members: MutableList<RosterMember>,
    val updatedAt: Long = 0L
)

object RosterCatalog {
    @Volatile private var registeredTeams: List<KillTeamCatalog> = emptyList()

    /** All Kill Teams loaded uniformly from assets/teams/<TEAM_ID>.json via [TeamDataLoader]. */
    val teams: List<KillTeamCatalog>
        get() = registeredTeams.ifEmpty { TeamDataLoader.allTeams() }

    fun register(loaded: List<KillTeamCatalog>) {
        registeredTeams = loaded
    }

    fun team(teamId: String): KillTeamCatalog = teams.firstOrNull { it.id == teamId } ?: teams.first()

    /**
     * Looks in [team] first, then in every other team: rules that let a kill team field
     * operatives from other catalogues (e.g. 审判庭征用命令) keep the original operative id.
     */
    fun operative(team: KillTeamCatalog, operativeId: String): OperativeTemplate? =
        team.operatives.firstOrNull { it.id == operativeId }
            ?: teams.asSequence().flatMap { it.operatives.asSequence() }.firstOrNull { it.id == operativeId }

    /** The team an operative template originally belongs to. */
    fun teamOf(operativeId: String): KillTeamCatalog? = teams.firstOrNull { t -> t.operatives.any { it.id == operativeId } }
}
