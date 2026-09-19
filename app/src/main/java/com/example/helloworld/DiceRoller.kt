package com.example.helloworld

enum class RollMode {
    ATTACK,
    DEFENCE,
    FREE
}

data class RollConfig(
    var diceCount: Int,
    var threshold: Int,
    var criticalThreshold: Int = 6
)
