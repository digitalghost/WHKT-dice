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
        val updated = BattleRules.syncKills(battle).copy(updatedAt = System.currentTimeMillis())
        val battles = all().toMutableList()
        val index = battles.indexOfFirst { it.battleId == updated.battleId }
        if (index >= 0) {
            val key = "undo_${updated.battleId}"
            val history = JSONArray(preferences.getString(key, "[]"))
            history.put(encodeBattle(battles[index]))
            val kept = JSONArray()
            for (i in maxOf(0, history.length() - UNDO_LIMIT) until history.length()) kept.put(history.getJSONObject(i))
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

    /** What the next undo would revert, for the undo button label. */
    fun undoPreview(battleId: String): String? {
        val history = JSONArray(preferences.getString("undo_$battleId", "[]"))
        if (history.length() == 0) return null
        val current = load(battleId) ?: return null
        return UndoTrace.preview(current, decodeBattle(history.getJSONObject(history.length() - 1)))
    }

    /**
     * Restores the previous snapshot but keeps a trace: entries written since then stay in the
     * log marked as undone, followed by an undo entry. Dice already rolled are never hidden.
     */
    fun undo(battleId: String): BattleRecord? {
        val key = "undo_$battleId"
        val history = JSONArray(preferences.getString(key, "[]"))
        if (history.length() == 0) return null
        val snapshot = decodeBattle(history.getJSONObject(history.length() - 1))
        val previous = UndoTrace.restore(load(battleId), snapshot)
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

    /** Old saves scored kill VP by hand: treat grades already reached as counted. */
    private fun decodeBattle(root: JSONObject): BattleRecord {
        val record = decodeBattleFields(root)
        val progress = root.optJSONObject("progress")
        if (progress != null && progress.has("killLevelA")) return record
        return record.copy(progress = record.progress.copy(
            killLevelA = BattleRules.killLevelAgainst(record.sideB),
            killLevelB = BattleRules.killLevelAgainst(record.sideA)
        ))
    }

    private fun decodeBattleFields(root: JSONObject) = BattleRecord(
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
            .put("counters", JSONArray().apply { side.counters.forEach { c ->
                put(JSONObject().put("id", c.id).put("name", c.name).put("value", c.value)
                    .put("reset", c.resetEachTurningPoint ?: JSONObject.NULL))
            } })
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
            tacticalOpRevealed = root.optBoolean("tacticalOpRevealed", false),
            counters = root.optJSONArray("counters").toObjectList { c ->
                TeamCounter(
                    id = c.optString("id"),
                    name = c.optString("name"),
                    value = c.optInt("value"),
                    resetEachTurningPoint = if (c.isNull("reset")) null else c.optInt("reset")
                )
            }
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
        .put("apl", operative.apl)
        .put("excludedFromKill", operative.excludedFromKill)
        .put("tokens", JSONArray().apply { operative.tokens.forEach { t ->
            put(JSONObject().put("id", t.id).put("name", t.name).put("value", t.value ?: JSONObject.NULL)
                .put("duration", t.duration.name).put("source", t.source))
        } })

    private fun decodeOperative(root: JSONObject) = BattleOperativeState(
        memberId = root.getString("memberId"),
        operativeId = root.getString("operativeId"),
        displayName = root.optString("displayName"),
        maxWounds = root.optInt("maxWounds"),
        currentWounds = root.optInt("currentWounds"),
        order = root.optString("order", BattleOperativeState.ORDER_CONCEAL),
        ready = root.optBoolean("ready", true),
        incapacitated = root.optBoolean("incapacitated"),
        weapons = root.optJSONArray("weapons").toObjectList(::decodeWeapon),
        apl = root.optInt("apl", 2),
        excludedFromKill = root.optBoolean("excludedFromKill"),
        tokens = root.optJSONArray("tokens").toObjectList { t ->
            OperativeToken(
                id = t.optString("id"),
                name = t.optString("name"),
                value = if (t.isNull("value")) null else t.optInt("value"),
                duration = enumValueOrDefault(t.optString("duration"), TokenDuration.MANUAL),
                source = t.optString("source")
            )
        }
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
        .put("kind", weapon.kind.name)
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
        keywords = root.optJSONArray("keywords").toStringList(),
        kind = runCatching { WeaponKind.valueOf(root.optString("kind")) }
            .getOrElse { WeaponActions.legacyKind(root.getString("id")) }
    )

    private fun encodeLog(entry: BattleLogEntry) = JSONObject()
        .put("id", entry.id)
        .put("timestamp", entry.timestamp)
        .put("turningPoint", entry.turningPoint)
        .put("phase", entry.phase.name)
        .put("message", entry.message)
        .put("undone", entry.undone)
        .put("manual", entry.manual)

    private fun decodeLog(root: JSONObject) = BattleLogEntry(
        id = root.getString("id"),
        timestamp = root.optLong("timestamp"),
        turningPoint = root.optInt("turningPoint"),
        phase = enumValueOrDefault(root.optString("phase"), BattlePhase.INITIATIVE),
        message = root.optString("message"),
        undone = root.optBoolean("undone"),
        manual = root.optBoolean("manual")
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
        private const val UNDO_LIMIT = 100
        const val UNDO_PREFIX = UndoTrace.PREFIX
    }
}

/**
 * Undo restores the previous snapshot but never hides what happened: log entries written since
 * that snapshot are kept, marked as undone, followed by an "↶ 撤回" entry.
 */
object UndoTrace {
    const val PREFIX = "↶ 撤回："

    private fun since(current: BattleRecord, snapshot: BattleRecord): List<BattleLogEntry> {
        val keptIds = snapshot.log.map { it.id }.toSet()
        return current.log.filter { it.id !in keptIds }
    }

    fun preview(current: BattleRecord, snapshot: BattleRecord): String? =
        since(current, snapshot).firstOrNull { !it.undone && !it.message.startsWith(PREFIX) }?.message?.substringBefore("\n")

    fun restore(current: BattleRecord?, snapshot: BattleRecord): BattleRecord {
        if (current == null) return snapshot
        val trace = since(current, snapshot)
        val label = preview(current, snapshot) ?: "上一步"
        return snapshot.copy(log = snapshot.log +
            trace.map { if (it.message.startsWith(PREFIX)) it else it.copy(undone = true) } +
            BattleLogEntry(turningPoint = current.turningPoint, phase = current.phase, message = "$PREFIX$label", manual = true))
    }
}
