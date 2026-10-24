package com.example.helloworld

/** Where a reroll came from. Only WEAPON is checked against the weapon's own rule. */
enum class RerollSource(val label: String) {
    WEAPON("武器规则"),
    COMMAND("指挥重掷"),
    OTHER("其他规则")
}

/** A manual reclassification of one die. The rolled value is kept unchanged (FAQ 1.2). */
enum class DieOverride(val label: String) {
    FAIL("失败"),
    NORMAL("普通成功"),
    CRITICAL("暴击"),
    DISCARDED("舍弃")
}

/** How a die currently reads, shared by chips, previews and engines. */
enum class DieReading { FAIL, NORMAL, CRITICAL, DISCARDED }

object DieClassifier {
    fun read(value: Int, override: DieOverride?, hit: Int, criticalThreshold: Int): DieReading = when (override) {
        DieOverride.FAIL -> DieReading.FAIL
        DieOverride.NORMAL -> DieReading.NORMAL
        DieOverride.CRITICAL -> DieReading.CRITICAL
        DieOverride.DISCARDED -> DieReading.DISCARDED
        null -> when {
            value == 1 || value < hit -> DieReading.FAIL
            value >= criticalThreshold -> DieReading.CRITICAL
            else -> DieReading.NORMAL
        }
    }
}

/**
 * Keyword helpers shared by both parsers.
 * Rules p92: identical rules don't stack; for rules with x, pick one value
 * (the attacker always benefits most from the highest, except Lethal where lower is better).
 * Accurate is special: several Accurate x may instead count as a single Accurate 2.
 */
object KeywordValues {
    private val number = Regex("(\\d+)")

    fun normalize(keywords: List<String>): List<String> = keywords.map { it.replace(" ", "") }

    fun values(normalized: List<String>, prefix: String): List<Int> = normalized
        .filter { it.startsWith(prefix) }
        .mapNotNull { number.find(it.removePrefix(prefix))?.groupValues?.getOrNull(1)?.toIntOrNull() }

    fun max(normalized: List<String>, prefix: String): Int = values(normalized, prefix).maxOrNull() ?: 0

    fun accurate(normalized: List<String>): Int {
        val all = values(normalized, "精准")
        return when {
            all.isEmpty() -> 0
            all.size == 1 -> all.single()
            else -> maxOf(all.max(), 2)
        }
    }

    fun lethal(normalized: List<String>, criticalThreshold: Int): Int =
        (values(normalized, "致命") + criticalThreshold).min().coerceIn(2, 6)
}
