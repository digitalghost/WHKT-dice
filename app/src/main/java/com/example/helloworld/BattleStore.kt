package com.example.helloworld

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BattleStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun all(): List<BattleRecord> = decodeList(preferences.getString(KEY_BATTLES, null))
        .sortedByDescending { it.updatedAt }

    fun load(battleId: String): BattleRecord? = all().firstOrNull { it.battleId == battleId }

    fun save(battle: BattleRecord): BattleRecord {
        val updated = battle.copy(updatedAt = System.currentTimeMillis())
        val battles = all().toMutableList()
        val index = battles.indexOfFirst { it.battleId == updated.battleId }
        if (index >= 0) {
            val key = "undo_${updated.battleId}"
            val history = JSONArray(preferences.getString(key, "[]"))
            history.put(encodeBattle(battles[index]))
            val kept = JSONArray()
            for (i in maxOf(0, history.length() - 30) until history.length()) kept.put(history.getJSONObject(i))
            preferences.edit().putString(key, kept.toString()).apply()
        }
        if (index >= 0) battles[index] = updated else battles.add(updated)
        preferences.edit()
            .putString(KEY_BATTLES, encodeList(battles).toString())
            .apply()
        if (updated.completed) clearActiveIf(updated.battleId) else setActive(updated.battleId)
        return updated
    }

    fun active(): BattleRecord? = preferences.getString(KEY_ACTIVE_BATTLE, null)
        ?.let(::load)
        ?.takeUnless { it.completed }

    fun undo(battleId: String): BattleRecord? {
        val key = "undo_$battleId"
        val history = JSONArray(preferences.getString(key, "[]"))
        if (history.length() == 0) return null
        val previous = decodeBattle(history.getJSONObject(history.length() - 1))
        history.remove(history.length() - 1)
        preferences.edit().putString(key, history.toString())
            .putString(KEY_BATTLES, encodeList(all().map { if (it.battleId == battleId) previous else it }).toString())
            .putString(KEY_ACTIVE_BATTLE, battleId).apply()
        return previous
    }

    fun setActive(battleId: String) {
        require(load(battleId) != null)
        preferences.edit().putString(KEY_ACTIVE_BATTLE, battleId).apply()
    }

    fun delete(battleId: String) {
        val remaining = all().filterNot { it.battleId == battleId }
        preferences.edit().putString(KEY_BATTLES, encodeList(remaining).toString()).apply()
        clearActiveIf(battleId)
    }

    private fun clearActiveIf(battleId: String) {
        if (preferences.getString(KEY_ACTIVE_BATTLE, null) == battleId) {
            preferences.edit().remove(KEY_ACTIVE_BATTLE).apply()
        }
    }

    private fun decodeList(source: String?): List<BattleRecord> {
        if (source.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(source)
            List(array.length()) { index -> decodeBattle(array.getJSONObject(index)) }
        }.getOrElse { throw IllegalStateException("对局存档无法读取，已保留原始数据", it) }
    }

    private fun encodeList(battles: List<BattleRecord>) = JSONArray().apply {
        battles.forEach { put(encodeBattle(it)) }
    }

    private fun encodeBattle(battle: BattleRecord) = JSONObject()
        .put("battleId", battle.battleId)
        .put("progress", BattleProgressCodec.encode(battle.progress))
        .put("name", battle.name)
        .put("mission", battle.mission)
        .put("killzone", battle.killzone)
        .put("keyOp", battle.keyOp)
        .put("dropZoneA", battle.dropZoneA)
        .put("dropZoneB", battle.dropZoneB)
        .put("sideA", encodeSide(battle.sideA))
        .put("sideB", encodeSide(battle.sideB))
        .put("turningPoint", battle.turningPoint)
        .put("phase", battle.phase.name)
        .put("initiativeSideId", battle.initiativeSideId.orEmpty())
        .put("notes", battle.notes)
        .put("log", JSONArray().apply { battle.log.forEach { put(encodeLog(it)) } })
        .put("completed", battle.completed)
        .put("createdAt", battle.createdAt)
        .put("updatedAt", battle.updatedAt)

    private fun decodeBattle(root: JSONObject) = BattleRecord(
        battleId = root.getString("battleId"),
        progress = BattleProgressCodec.decode(root.optJSONObject("progress")),
        name = root.optString("name", "杀戮小队对局"),
        mission = root.optString("mission"),
        killzone = root.optString("killzone"),
        keyOp = root.optString("keyOp"),
        dropZoneA = root.optString("dropZoneA"),
        dropZoneB = root.optString("dropZoneB"),
        sideA = decodeSide(root.getJSONObject("sideA")),
        sideB = decodeSide(root.getJSONObject("sideB")),
        turningPoint = root.optInt("turningPoint"),
        phase = enumValueOrDefault(root.optString("phase"), BattlePhase.INITIATIVE),
        initiativeSideId = root.optString("initiativeSideId").takeIf(String::isNotBlank),
        notes = root.optString("notes"),
        log = root.optJSONArray("log").toObjectList(::decodeLog),
        completed = root.optBoolean("completed"),
        createdAt = root.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun encodeSide(side: BattleSide) = JSONObject()
        .put("id", side.id)
        .put("rosterId", side.rosterId)
        .put("rosterName", side.rosterName)
        .put("teamId", side.teamId)
        .put("playerName", side.playerName)
        .put("commandPoints", side.commandPoints)
        .put("score", JSONObject()
            .put("key", side.score.key)
            .put("kill", side.score.kill)
            .put("tactical", side.score.tactical))
            .put("primaryOp", side.primaryOp?.name.orEmpty())
            .put("equipmentNotes", side.equipmentNotes)
            .put("tacticalOpNotes", side.tacticalOpNotes)
            .put("tacticalOpRevealed", side.tacticalOpRevealed)
            .put("operatives", JSONArray().apply { side.operatives.forEach { put(encodeOperative(it)) } })

    private fun decodeSide(root: JSONObject): BattleSide {
        val score = root.optJSONObject("score") ?: JSONObject()
        return BattleSide(
            id = root.getString("id"),
            rosterId = root.optString("rosterId"),
            rosterName = root.optString("rosterName"),
            teamId = root.optString("teamId"),
            playerName = root.optString("playerName"),
            commandPoints = root.optInt("commandPoints", 2),
            score = BattleScore(
                key = score.optInt("key"),
                kill = score.optInt("kill"),
                tactical = score.optInt("tactical")
            ),
            primaryOp = root.optString("primaryOp").takeIf(String::isNotBlank)
                ?.let { enumValueOrDefault<ScoreType>(it, ScoreType.KEY) },
            equipmentNotes = root.optString("equipmentNotes"),
            tacticalOpNotes = root.optString("tacticalOpNotes"),
            operatives = root.optJSONArray("operatives").toObjectList(::decodeOperative),
            tacticalOpRevealed = root.optBoolean("tacticalOpRevealed", false)
        )
    }

    private fun encodeOperative(operative: BattleOperativeState) = JSONObject()
        .put("memberId", operative.memberId)
        .put("operativeId", operative.operativeId)
        .put("displayName", operative.displayName)
        .put("maxWounds", operative.maxWounds)
        .put("currentWounds", operative.currentWounds)
        .put("order", operative.order)
        .put("ready", operative.ready)
        .put("incapacitated", operative.incapacitated)
        .put("weapons", JSONArray().apply { operative.weapons.forEach { put(encodeWeapon(it)) } })

    private fun decodeOperative(root: JSONObject) = BattleOperativeState(
        memberId = root.getString("memberId"),
        operativeId = root.getString("operativeId"),
        displayName = root.optString("displayName"),
        maxWounds = root.optInt("maxWounds"),
        currentWounds = root.optInt("currentWounds"),
        order = root.optString("order", BattleOperativeState.ORDER_CONCEAL),
        ready = root.optBoolean("ready", true),
        incapacitated = root.optBoolean("incapacitated"),
        weapons = root.optJSONArray("weapons").toObjectList(::decodeWeapon)
    )

    private fun encodeWeapon(weapon: BattleWeaponSnapshot) = JSONObject()
        .put("id", weapon.id)
        .put("name", weapon.name)
        .put("profile", weapon.profile)
        .put("attacks", weapon.attacks)
        .put("hit", weapon.hit)
        .put("critical", weapon.critical)
        .put("normalDamage", weapon.normalDamage)
        .put("criticalDamage", weapon.criticalDamage)
        .put("keywords", JSONArray(weapon.keywords))

    private fun decodeWeapon(root: JSONObject) = BattleWeaponSnapshot(
        id = root.getString("id"),
        name = root.optString("name"),
        profile = root.optString("profile"),
        attacks = root.optInt("attacks", 1),
        hit = root.optInt("hit", 6),
        critical = root.optInt("critical", 6),
        normalDamage = root.optInt("normalDamage"),
        criticalDamage = root.optInt("criticalDamage"),
        keywords = root.optJSONArray("keywords").toStringList()
    )

    private fun encodeLog(entry: BattleLogEntry) = JSONObject()
        .put("id", entry.id)
        .put("timestamp", entry.timestamp)
        .put("turningPoint", entry.turningPoint)
        .put("phase", entry.phase.name)
        .put("message", entry.message)

    private fun decodeLog(root: JSONObject) = BattleLogEntry(
        id = root.getString("id"),
        timestamp = root.optLong("timestamp"),
        turningPoint = root.optInt("turningPoint"),
        phase = enumValueOrDefault(root.optString("phase"), BattlePhase.INITIATIVE),
        message = root.optString("message")
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return List(length()) { index -> mapper(getJSONObject(index)) }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { index -> getString(index) }
    }

    companion object {
        private const val PREFERENCES = "battle_records"
        private const val KEY_BATTLES = "battles_v1"
        private const val KEY_ACTIVE_BATTLE = "active_battle_id"
    }
}
