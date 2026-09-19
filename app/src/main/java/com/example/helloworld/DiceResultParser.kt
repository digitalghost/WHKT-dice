package com.example.helloworld

object DiceResultParser {
    private val numberPattern = Regex("-?\\d+")

    fun parse(json: String): List<Int> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        return numberPattern.findAll(trimmed).map { it.value.toInt() }.toList()
    }
}
