package com.example.helloworld

import android.widget.LinearLayout
import org.json.JSONObject

data class TeamRuleEntry(
    val id: String,
    val category: String,
    val title: String,
    val body: String,
    val cards: List<String>
)

class TeamRuleBook(private val ui: RecordUi, private val teamId: String) {
    val available: Boolean
        get() = teamId in setOf("CHAOS-WC", "TYR-RAV") ||
            ImportedTeamCatalog.teams.any { it.id == teamId }

    val entries: List<TeamRuleEntry> by lazy {
        if (!available) emptyList()
        else {
            val array = JSONObject(
                ui.context.assets.open("rules/$teamId.json").bufferedReader().use { it.readText() }
            ).getJSONArray("entries")
            (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                val cards = entry.getJSONArray("cards")
                TeamRuleEntry(
                    entry.getString("id"),
                    entry.getString("category"),
                    entry.getString("title"),
                    entry.getString("body"),
                    (0 until cards.length()).map { cards.getString(it) }
                )
            }
        }
    }

    fun categories(box: LinearLayout) {
        if (!available) return
        val categories = RuleOrganization.supplementaryTeamCategories
            .filter { category -> entries.any { it.category == category } }
        if (categories.isEmpty()) return

        box.addView(ui.divider())
        box.addView(ui.text("补充资料", 18f, ui.accent, true), ui.wrap())
        box.addView(
            ui.text("刊物、标识指南和勘误按实际收录情况显示。", 12f, ui.muted),
            ui.wrap(8)
        )
        categories.forEach { category ->
            box.addView(ui.link("${RuleOrganization.label(category)} ›") { index(category) }, ui.wrap(2))
        }
    }

    fun contents(box: LinearLayout, category: String, expanded: Boolean = ui.isTablet) {
        val matching = entries.filter { it.category == category }
        if (matching.isEmpty()) {
            box.addView(ui.text("暂无收录内容。", color = ui.muted), ui.wrap())
            return
        }

        if (expanded) {
            val present = ui.context.assets.list("team_rules").orEmpty().toSet()
            ui.tiles(box, matching) { tile, entry ->
                tile.addView(ui.text(entry.title.substringBefore('（'), 17f, ui.accent, true), ui.wrap())
                entry.cards.filter { it in present }.forEach { file ->
                    ui.preview(tile, entry.title, "team_rules/$file")
                }
                tile.addView(
                    ui.text(
                        entry.body.removePrefix(
                            "计谋费用由玩家按采用的规则确认（资料接口未单列费用）。\n\n"
                        )
                    ),
                    ui.wrap()
                )
            }
            if (category.endsWith("计谋")) {
                box.addView(ui.text("计谋费用以采用的规则为准。", 12f, ui.muted), ui.wrap())
            }
            box.addView(
                ui.text("文字已更新勘误；原卡可能保留旧版数值。点按图片可放大。", 12f, ui.muted),
                ui.wrap()
            )
        } else {
            matching.forEach { entry ->
                box.addView(ui.link("${entry.title.substringBefore('（')} ›") { show(entry) }, ui.wrap(2))
            }
        }
    }

    fun index(category: String) {
        ui.reference("${RosterCatalog.team(teamId).name} · ${RuleOrganization.label(category)}") { box, _ ->
            contents(box, category, expanded = true)
        }
    }

    fun show(entry: TeamRuleEntry) {
        ui.reference(entry.title.substringBefore('（')) { box, _ ->
            box.addView(ui.text(entry.body), ui.wrap())
            box.addView(
                ui.text(
                    "资料核对：${if (ImportedTeamCatalog.teams.any { it.id == teamId }) "2026-09-24" else "2026-09-23"}。",
                    11f,
                    ui.muted
                ),
                ui.wrap()
            )
            val present = ui.context.assets.list("team_rules").orEmpty().toSet()
            entry.cards.filter { it in present }.forEach { file ->
                ui.preview(box, entry.title, "team_rules/$file")
            }
        }
    }

    fun operativeEntry(id: String): TeamRuleEntry? {
        val entryId = when (id) {
            "wc_sod" -> "6.1"
            "wc_sot" -> "6.2"
            "wc_sow" -> "6.3"
            "wc_gnr", "wc_ib", "wc_war" -> "6.4"
            "wc_tzc", "wc_tzhb", "wc_tzib", "wc_tzwar" -> "6.5"
            "rav_prime" -> "6.1"
            "rav_felltalon" -> "6.2"
            "rav_tremorscythe" -> "6.3"
            "rav_venomspitter" -> "6.4"
            "rav_warrior" -> "6.5"
            "rav_wrecker" -> "6.6"
            else -> return null
        }
        return entries.firstOrNull { it.id == entryId }
    }
}
