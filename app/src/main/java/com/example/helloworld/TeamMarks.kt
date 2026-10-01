package com.example.helloworld

/** Team emblems extracted from the original PDF card watermark vectors. */
object TeamMarks {
    private val resources = mapOf(
        "IMP-AOD" to R.drawable.team_mark_imp_aod,
        "CHAOS-PM" to R.drawable.team_mark_chaos_pm,
        "TYR-RAV" to R.drawable.team_mark_tyr_rav,
        "CHAOS-WC" to R.drawable.team_mark_chaos_wc,
        "CHAOS-FELL" to R.drawable.team_mark_chaos_fell,
        "NEC-CAN" to R.drawable.team_mark_nec_can,
        "IMP-WS" to R.drawable.team_mark_imp_ws,
        "IMP-CI" to R.drawable.team_mark_imp_ci,
    )

    fun resource(teamId: String): Int = resources[teamId] ?: R.drawable.team_mark_imp_aod

    fun resourceOrNull(teamId: String): Int? = resources[teamId]
}
