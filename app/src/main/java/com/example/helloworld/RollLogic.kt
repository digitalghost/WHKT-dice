package com.example.helloworld

data class RollSummary(
    val criticals: Int,
    val normals: Int,
    val failures: Int
)

data class ShootingResolution(
    val unblockedCriticals: Int,
    val unblockedNormals: Int,
    val blockedCriticals: Int,
    val blockedNormals: Int
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
                value == 1 || value < threshold -> failures++
                value >= criticalThreshold -> criticals++
                else -> normals++
            }
        }
        return RollSummary(criticals, normals, failures)
    }

    fun resolveShooting(
        attack: RollSummary,
        defence: RollSummary
    ): ShootingResolution {
        val attackCriticals = attack.criticals.coerceAtLeast(0)
        val attackNormals = attack.normals.coerceAtLeast(0)
        val defenceCriticals = defence.criticals.coerceAtLeast(0)
        val defenceNormals = defence.normals.coerceAtLeast(0)

        // Preserve success types throughout resolution. A critical is never converted into
        // two normal successes: normal saves first cancel normal attacks one-for-one, and
        // only otherwise-unused pairs of normal saves can cancel one critical attack.
        val criticalsBlockedByCriticalSaves = minOf(defenceCriticals, attackCriticals)
        val criticalSavesLeft = defenceCriticals - criticalsBlockedByCriticalSaves
        val normalsBlockedByCriticalSaves = minOf(criticalSavesLeft, attackNormals)

        val normalsAfterCriticalSaves = attackNormals - normalsBlockedByCriticalSaves
        val normalsBlockedByNormalSaves = minOf(defenceNormals, normalsAfterCriticalSaves)
        val normalSavesLeft = defenceNormals - normalsBlockedByNormalSaves

        val criticalsAfterCriticalSaves = attackCriticals - criticalsBlockedByCriticalSaves
        val criticalsBlockedByNormalSaves = minOf(normalSavesLeft / 2, criticalsAfterCriticalSaves)

        val blockedCriticals =
            criticalsBlockedByCriticalSaves + criticalsBlockedByNormalSaves
        val blockedNormals = normalsBlockedByCriticalSaves + normalsBlockedByNormalSaves

        return ShootingResolution(
            unblockedCriticals = attackCriticals - blockedCriticals,
            unblockedNormals = attackNormals - blockedNormals,
            blockedCriticals = blockedCriticals,
            blockedNormals = blockedNormals
        )
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
