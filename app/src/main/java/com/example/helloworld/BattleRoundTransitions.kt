package com.example.helloworld

/** Sequential TP changes used by the lightweight battle notebook UI. */
object BattleRoundTransitions {
    fun advance(battle: BattleRecord): BattleRecord {
        require(!battle.completed) { "已结束的对局不能切换回合" }
        require(battle.turningPoint in 1 until BattleRules.TURNING_POINT_COUNT) {
            "回合只能从 TP1 依次推进到 TP4"
        }

        fun readySide(side: BattleSide)=side.copy(
            operatives=side.operatives.map { operative ->
                if(operative.incapacitated) operative else operative.copy(ready=true)
            }
        )

        return battle.copy(
            turningPoint=battle.turningPoint+1,
            phase=BattlePhase.FIREFIGHT,
            sideA=readySide(battle.sideA),
            sideB=readySide(battle.sideB),
            progress=battle.progress.copy(
                active=null,
                reacted=emptySet(),
                planPasses=0,
                usedPlans=emptySet()
            )
        )
    }

    fun isRoundChange(entry: BattleLogEntry?): Boolean =
        entry?.message?.startsWith("进入第 ")==true
}
