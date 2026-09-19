package com.example.helloworld

data class RollSummary(
    val criticals: Int,
    val normals: Int,
    val failures: Int
)

object RollLogic {

    fun classify(
        values: List<Int>,
        threshold: Int,
        criticalThreshold: Int = 6,
        retainedNormalSuccesses: Int = 0
    ): RollSummary {
        var criticals = 0
        var normals = retainedNormalSuccesses
        var failures = 0

        values.forEach { value ->
            when {
                value == 1 -> failures++
                value >= criticalThreshold -> criticals++
                value >= threshold -> normals++
                else -> failures++
            }
        }
        return RollSummary(criticals, normals, failures)
    }

    fun mergeRerollResults(
        current: List<Int>,
        indices: List<Int>,
        newValues: List<Int>
    ): List<Int> {
        if (indices.size != newValues.size) return current
        val merged = current.toMutableList()
        indices.forEachIndexed { resultIndex, originalIndex ->
            if (originalIndex !in merged.indices) return current
            merged[originalIndex] = newValues[resultIndex]
        }
        return merged
    }
}
