package com.example.helloworld

enum class RollMode {
    ATTACK,
    DEFENCE
}

data class RollConfig(
    var diceCount: Int,
    var threshold: Int,
    var criticalThreshold: Int = 6
)
