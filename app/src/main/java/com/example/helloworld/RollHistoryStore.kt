package com.example.helloworld

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RollSideHistory(
    val diceCount: Int,
    val threshold: Int,
    val criticalThreshold: Int,
    val retainedNormals: Int = 0,
    val stages: List<List<Int>>
) {
    val latestValues: List<Int>
        get() = stages.lastOrNull().orEmpty()

    val rerollCount: Int
        get() = (stages.size - 1).coerceAtLeast(0)
}

data class RollHistoryEntry(
    val id: Long,
    val timestamp: Long,
    val attack: RollSideHistory?,
    val defence: RollSideHistory?
) {
    private val legacySide: RollSideHistory
        get() = attack ?: requireNotNull(defence)

    val isPaired: Boolean
        get() = attack != null && defence != null

    val rerollCount: Int
        get() = (attack?.rerollCount ?: 0) + (defence?.rerollCount ?: 0)

    // Compatibility accessors keep legacy single-roll rendering and restore paths readable.
    val mode: RollMode
        get() = if (attack != null) RollMode.ATTACK else RollMode.DEFENCE
    val diceCount: Int
        get() = legacySide.diceCount
    val threshold: Int
        get() = legacySide.threshold
    val criticalThreshold: Int
        get() = legacySide.criticalThreshold
    val latestValues: List<Int>
        get() = legacySide.latestValues
}

class RollHistoryStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "dice_history"
        private const val HISTORY_KEY = "entries"
        private const val MAX_ENTRIES = 100
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun entries(): List<RollHistoryEntry> =
        readEntries().sortedByDescending { it.timestamp }

    fun addPair(
        attackConfig: RollConfig,
        attackStages: List<List<Int>>,
        defenceConfig: RollConfig,
        defenceStages: List<List<Int>>,
        defenceRetainedNormals: Int
    ): Long {
        val id = System.currentTimeMillis()
        val entry = RollHistoryEntry(
            id = id,
            timestamp = id,
            attack = RollSideHistory(
                diceCount = attackConfig.diceCount,
                threshold = attackConfig.threshold,
                criticalThreshold = attackConfig.criticalThreshold,
                stages = attackStages.map { it.toList() }
            ),
            defence = RollSideHistory(
                diceCount = defenceConfig.diceCount,
                threshold = defenceConfig.threshold,
                criticalThreshold = defenceConfig.criticalThreshold,
                retainedNormals = defenceRetainedNormals,
                stages = defenceStages.map { it.toList() }
            )
        )
        val updated = listOf(entry) + readEntries().filterNot { it.id == id }
        writeEntries(updated.take(MAX_ENTRIES))
        return id
    }

    fun appendReroll(id: Long, mode: RollMode, values: List<Int>) {
        val updated = readEntries().map { entry ->
            if (entry.id != id) {
                entry
            } else if (mode == RollMode.ATTACK && entry.attack != null) {
                entry.copy(attack = entry.attack.copy(stages = entry.attack.stages + listOf(values)))
            } else if (mode == RollMode.DEFENCE && entry.defence != null) {
                entry.copy(defence = entry.defence.copy(stages = entry.defence.stages + listOf(values)))
            } else {
                entry
            }
        }
        writeEntries(updated)
    }

    fun delete(id: Long) {
        writeEntries(readEntries().filterNot { it.id == id })
    }

    fun clear() {
        preferences.edit().remove(HISTORY_KEY).apply()
    }

    private fun readEntries(): List<RollHistoryEntry> = runCatching {
        val array = JSONArray(preferences.getString(HISTORY_KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val attack = if (item.has("attack")) {
                    readSide(item.optJSONObject("attack"))
                } else {
                    readLegacySide(item, RollMode.ATTACK)
                }
                val defence = if (item.has("defence")) {
                    readSide(item.optJSONObject("defence"))
                } else {
                    readLegacySide(item, RollMode.DEFENCE)
                }
                if (attack != null || defence != null) {
                    add(
                        RollHistoryEntry(
                            id = item.optLong("id", item.optLong("timestamp")),
                            timestamp = item.optLong("timestamp", item.optLong("id")),
                            attack = attack,
                            defence = defence
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun readLegacySide(item: JSONObject, expectedMode: RollMode): RollSideHistory? {
        val mode = RollMode.entries.firstOrNull { it.name == item.optString("mode") } ?: return null
        if (mode != expectedMode) return null
        return RollSideHistory(
            diceCount = item.optInt("diceCount", 1),
            threshold = item.optInt("threshold", 3),
            criticalThreshold = item.optInt("criticalThreshold", 6),
            stages = readStages(item.optJSONArray("stages"))
        )
    }

    private fun readSide(item: JSONObject?): RollSideHistory? {
        item ?: return null
        return RollSideHistory(
            diceCount = item.optInt("diceCount", 1),
            threshold = item.optInt("threshold", 3),
            criticalThreshold = item.optInt("criticalThreshold", 6),
            retainedNormals = item.optInt("retainedNormals", 0),
            stages = readStages(item.optJSONArray("stages"))
        )
    }

    private fun readStages(stagesJson: JSONArray?): List<List<Int>> = buildList {
        if (stagesJson == null) return@buildList
        for (stageIndex in 0 until stagesJson.length()) {
            val valuesJson = stagesJson.optJSONArray(stageIndex) ?: continue
            add(buildList {
                for (valueIndex in 0 until valuesJson.length()) {
                    add(valuesJson.optInt(valueIndex))
                }
            })
        }
    }

    private fun writeEntries(entries: List<RollHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("timestamp", entry.timestamp)
                    .put("attack", entry.attack?.let(::writeSide) ?: JSONObject.NULL)
                    .put("defence", entry.defence?.let(::writeSide) ?: JSONObject.NULL)
            )
        }
        preferences.edit().putString(HISTORY_KEY, array.toString()).apply()
    }

    private fun writeSide(side: RollSideHistory): JSONObject = JSONObject()
        .put("diceCount", side.diceCount)
        .put("threshold", side.threshold)
        .put("criticalThreshold", side.criticalThreshold)
        .put("retainedNormals", side.retainedNormals)
        .put("stages", JSONArray().apply {
            side.stages.forEach { values ->
                put(JSONArray().apply { values.forEach(::put) })
            }
        })
}
