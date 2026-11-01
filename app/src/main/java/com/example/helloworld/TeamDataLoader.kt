package com.example.helloworld

import android.content.Context
import java.io.File

/**
 * Loads all Kill Teams described as data (`assets/teams/<TEAM_ID>.json`, see docs/小队数据JSON格式.md).
 * Uses a pure-Kotlin JSON parser so both Android runtime and JVM unit tests load identically.
 */
object TeamDataLoader {
    private val PRIORITY_ORDER = listOf(
        "IMP-AOD", "CHAOS-PM", "TYR-RAV", "CHAOS-WC",
        "NEC-CAN", "IMP-WS", "IMP-CI", "CHAOS-FELL"
    )
    private data class TeamWeaponRule(val teamId: String, val teamName: String, val rule: RuleDefinition)
    private val abilities = mutableMapOf<String, List<AbilityDefinition>>()
    private val weaponRules = mutableListOf<TeamWeaponRule>()
    private val meleeIds = mutableSetOf<String>()
    private var loadedTeams: List<KillTeamCatalog> = emptyList()
    @Volatile private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val files = orderTeamFiles(context.assets.list("teams").orEmpty().filter { it.endsWith(".json") })
            val teams = files.mapNotNull { file ->
                val text = context.assets.open("teams/$file").bufferedReader().use { it.readText() }
                parseIfActive(file, text)
            }
            loadedTeams = teams
            RosterCatalog.register(teams)
            loaded = true
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val dir = sequenceOf(
                File("src/main/assets/teams"),
                File("app/src/main/assets/teams"),
                File("../app/src/main/assets/teams")
            ).firstOrNull { it.isDirectory } ?: return
            val files = orderTeamFiles(dir.list().orEmpty().filter { it.endsWith(".json") })
            val teams = files.mapNotNull { file ->
                val text = File(dir, file).readText()
                parseIfActive(file, text)
            }
            loadedTeams = teams
            loaded = true
        }
    }

    internal fun allTeams(): List<KillTeamCatalog> {
        ensureLoaded()
        return loadedTeams
    }

    fun abilities(operativeId: String): List<AbilityDefinition>? {
        ensureLoaded()
        return abilities[operativeId]
    }

    fun weaponRule(label: String, teamId: String? = null): RuleDefinition? {
        ensureLoaded()
        val clean = label.trim().removeSuffix("*").trim()
        fun matches(entry: TeamWeaponRule): Boolean =
            entry.rule.name == clean || clean.startsWith(entry.rule.name + " ")
        if (teamId != null) {
            weaponRules.firstOrNull { it.teamId == teamId && matches(it) }?.let { return it.rule }
        }
        val exact = weaponRules.filter { it.rule.name == clean }.ifEmpty { weaponRules.filter(::matches) }
        if (exact.isEmpty()) return null
        if (exact.size == 1 || clean == "护盾") return exact.first().rule
        val distinctByText = exact.distinctBy { it.rule.description }
        if (distinctByText.size == 1) return distinctByText.first().rule
        val combined = distinctByText.joinToString("\n\n") { "【${it.teamName}】${it.rule.description}" }
        return exact.first().rule.copy(description = combined)
    }

    fun isMelee(weaponId: String): Boolean {
        ensureLoaded()
        return weaponId in meleeIds
    }

    private fun orderTeamFiles(files: Iterable<String>): List<String> =
        files.sortedWith(
            compareBy<String> { file ->
                val idx = PRIORITY_ORDER.indexOf(file.removeSuffix(".json"))
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenBy { it }
        )

    private fun parseIfActive(file: String, text: String): KillTeamCatalog? =
        runCatching {
            val root = MiniJson.parseObject(text)
            if (root.bool("draft")) null else parse(root)
        }.onFailure {
            runCatching { android.util.Log.w("TeamDataLoader", "skip $file", it) }
        }.getOrNull()

    internal fun parse(text: String): KillTeamCatalog = parse(MiniJson.parseObject(text))

    private fun parse(root: Map<String, Any?>): KillTeamCatalog {
        val teamId = root.str("teamId")
        val teamName = root.str("name")
        root.strList("archetypes").takeIf { it.isNotEmpty() }?.let { TeamArchetypes.register(teamId, it) }
        root.optStr("compositionHint").takeIf { it.isNotBlank() }?.let { RosterComposition.register(teamId, it) }
        root.objList("weaponRules").forEach { r ->
            val rName = r.str("name")
            weaponRules += TeamWeaponRule(teamId, teamName, RuleDefinition("$teamId:$rName", rName, r.str("text")))
        }
        val operatives = root.objList("operatives").map { o ->
            val id = o.str("id")
            val weapons = o.objList("weapons").map { w ->
                val wid = w.str("id")
                val kind = if (w.optStr("kind") == "melee") WeaponKind.MELEE else WeaponKind.RANGED
                if (kind == WeaponKind.MELEE) {
                    meleeIds += wid
                    if (wid.startsWith("${id}_")) {
                        meleeIds += wid.removePrefix("${id}_")
                    }
                }
                WeaponOption(wid, w.str("name"), w.str("profile"), kind)
            }
            abilities[id] = o.objList("abilities").mapIndexed { i, a ->
                AbilityDefinition(
                    id = a.optStr("id").ifBlank { "$id-$i" },
                    name = a.str("name"),
                    description = a.str("text"),
                    apCost = a.optIntOrNull("ap"),
                    factionRule = a.bool("factionRule")
                )
            }
            val cards = o.strList("cards").map { if (it.startsWith("team_rules/")) it else "team_rules/$it" }
            val dataCard = cards.firstOrNull { !isAbilityCardName(it) } ?: cards.firstOrNull()
            val abilityCards = cards.filter { isAbilityCardName(it) }
            OperativeTemplate(
                id = id,
                name = o.str("name"),
                role = o.optStr("role"),
                apl = o.int("apl"),
                move = o.str("move"),
                save = o.str("save"),
                wounds = o.int("wounds"),
                cardRes = R.drawable.ic_card,
                weapons = weapons,
                defaultWeaponIds = o.strListOrNull("defaultWeaponIds") ?: weapons.map { it.id },
                baseSizeMm = o.optIntOrNull("baseSizeMm"),
                keywords = o.strList("keywords"),
                cardAsset = dataCard,
                abilityAssets = abilityCards
            )
        }
        return KillTeamCatalog(teamId, root.str("name"), root.optStr("subtitle"), operatives)
    }

    private fun isAbilityCardName(name: String): Boolean = "能力卡" in name || "-能力-" in name
}

internal object MiniJson {
    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> = Parser(text).parseValue() as Map<String, Any?>

    private class Parser(private val s: String) {
        private var i = 0

        fun parseValue(): Any? {
            skipWs()
            if (i >= s.length) error("Unexpected EOF")
            return when (val c = s[i]) {
                '{' -> parseObj()
                '[' -> parseArr()
                '"' -> parseStr()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                '-', in '0'..'9' -> parseNum()
                else -> error("Unexpected char '$c' at $i")
            }
        }

        private fun parseObj(): Map<String, Any?> {
            i++ // '{'
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return map }
            while (i < s.length) {
                skipWs()
                val key = parseStr()
                skipWs()
                if (s[i] == ':') i++
                val value = parseValue()
                map[key] = value
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return map }
                    else -> error("Expected ',' or '}' at $i")
                }
            }
            error("Unterminated object")
        }

        private fun parseArr(): List<Any?> {
            i++ // '['
            val list = ArrayList<Any?>()
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return list }
            while (i < s.length) {
                list.add(parseValue())
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return list }
                    else -> error("Expected ',' or ']' at $i")
                }
            }
            error("Unterminated array")
        }

        private fun parseStr(): String {
            if (s[i] != '"') error("Expected '\"' at $i")
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) error("Unterminated escape")
                        when (val esc = s[i++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = s.substring(i, i + 4)
                                i += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            error("Unterminated string")
        }

        private fun parseNum(): Number {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i] in '0'..'9') i++
            var isFloat = false
            if (i < s.length && s[i] == '.') {
                isFloat = true
                i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isFloat = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val raw = s.substring(start, i)
            return if (isFloat) raw.toDouble() else raw.toInt()
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.str(k: String): String = (this[k] as? String) ?: error("Missing string '$k'")
private fun Map<String, Any?>.optStr(k: String): String = (this[k] as? String).orEmpty()
private fun Map<String, Any?>.int(k: String): Int = (this[k] as? Number)?.toInt() ?: error("Missing int '$k'")
private fun Map<String, Any?>.optIntOrNull(k: String): Int? = (this[k] as? Number)?.toInt()
private fun Map<String, Any?>.bool(k: String): Boolean = (this[k] as? Boolean) ?: false
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.objList(k: String): List<Map<String, Any?>> =
    (this[k] as? List<Any?>)?.filterIsInstance<Map<String, Any?>>().orEmpty()
private fun Map<String, Any?>.strList(k: String): List<String> =
    (this[k] as? List<Any?>)?.filterIsInstance<String>().orEmpty()
private fun Map<String, Any?>.strListOrNull(k: String): List<String>? =
    (this[k] as? List<Any?>)?.filterIsInstance<String>()
