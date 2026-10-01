package com.example.helloworld

/** Visual and interaction state for the official two-sided Kill Team order token. */
object OrderTokens {
    fun resource(order: String, ready: Boolean): Int = when {
        order == BattleOperativeState.ORDER_ENGAGE && ready -> R.drawable.order_engage_ready
        order == BattleOperativeState.ORDER_ENGAGE -> R.drawable.order_engage_spent
        ready -> R.drawable.order_conceal_ready
        else -> R.drawable.order_conceal_spent
    }

    fun oppositeOrder(order: String): String =
        if (order == BattleOperativeState.ORDER_CONCEAL) {
            BattleOperativeState.ORDER_ENGAGE
        } else {
            BattleOperativeState.ORDER_CONCEAL
        }

    fun orderForSide(left: Boolean): String =
        if(left) BattleOperativeState.ORDER_CONCEAL else BattleOperativeState.ORDER_ENGAGE

    fun faceLabel(ready: Boolean): String = if (ready) "亮面 · 就绪" else "暗面 · 待机"
}
