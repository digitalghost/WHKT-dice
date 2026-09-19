package com.example.helloworld

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RollHistoryEntry(
    val id: Long,
    val timestamp: Long,
    val mode: RollMode,
    val diceCount: Int,
    val threshold: Int,
    val criticalThreshold: Int,
    val diceTheme: DiceTheme,
    val stages: List<List<Int>>
) {
    val latestValues: List<Int> get() = stages.lastOrNull().orEmpty()
    val rerollCount: Int get() = (stages.size - 1).coerceAtLeast(0)
}

class RollHistoryStore(context: Context) {
    companion object {
        private const val PREFS_NAME = "dice_history"
        private const val HISTORY_KEY = "entries"
        private const val MAX_ENTRIES = 100
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun entries(): List<RollHistoryEntry> = readEntries().sortedByDescending { it.timestamp }

    fun add(
        mode: RollMode,
        config: RollConfig,
        diceTheme: DiceTheme,
        values: List<Int>
    ): Long {
        val id = System.currentTimeMillis()
        val updated = listOf(
            RollHistoryEntry(
                id = id,
                timestamp = id,
                mode = mode,
                diceCount = config.diceCount,
                threshold = config.threshold,
                criticalThreshold = config.criticalThreshold,
                diceTheme = diceTheme,
                stages = listOf(values)
            )
        ) + readEntries().filterNot { it.id == id }
        writeEntries(updated.take(MAX_ENTRIES))
        return id
    }

    fun appendReroll(id: Long, values: List<Int>) {
        val updated = readEntries().map { entry ->
            if (entry.id == id) entry.copy(stages = entry.stages + listOf(values)) else entry
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
                val stagesJson = item.getJSONArray("stages")
                val stages = buildList {
                    for (stageIndex in 0 until stagesJson.length()) {
                        val valuesJson = stagesJson.getJSONArray(stageIndex)
                        add(buildList {
                            for (valueIndex in 0 until valuesJson.length()) {
                                add(valuesJson.getInt(valueIndex))
                            }
                        })
                    }
                }
                add(
                    RollHistoryEntry(
                        id = item.getLong("id"),
                        timestamp = item.getLong("timestamp"),
                        mode = RollMode.valueOf(item.getString("mode")),
                        diceCount = item.getInt("diceCount"),
                        threshold = item.getInt("threshold"),
                        criticalThreshold = item.getInt("criticalThreshold"),
                        diceTheme = runCatching {
                            DiceTheme.valueOf(item.getString("diceTheme"))
                        }.getOrDefault(DiceTheme.ANGELS_OF_DEATH),
                        stages = stages
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun writeEntries(entries: List<RollHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val stages = JSONArray()
            entry.stages.forEach { values ->
                stages.put(JSONArray().apply { values.forEach(::put) })
            }
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("timestamp", entry.timestamp)
                    .put("mode", entry.mode.name)
                    .put("diceCount", entry.diceCount)
                    .put("threshold", entry.threshold)
                    .put("criticalThreshold", entry.criticalThreshold)
                    .put("diceTheme", entry.diceTheme.name)
                    .put("stages", stages)
            )
        }
        preferences.edit().putString(HISTORY_KEY, array.toString()).apply()
    }
}
