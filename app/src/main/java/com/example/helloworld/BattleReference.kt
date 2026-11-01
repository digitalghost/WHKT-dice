package com.example.helloworld

import android.widget.LinearLayout

/** Team references are shared; member pages contain only that operative's rules. */
class BattleReference(private val ui: RecordUi) {
    private fun files(teamId: String)=CardAssets.list(ui.context, "team_rules").filter { it.startsWith("$teamId--") }.sorted()

    private data class ReferenceCard(val title: String,val asset: String)

    private fun referenceCards(teamId: String,category: String): List<ReferenceCard> {
        val entriesByCard = runCatching {
            TeamRuleBook(ui, teamId).entries.flatMap { e -> e.cards.map { c -> c to e.title } }.toMap()
        }.getOrDefault(emptyMap())
        val teamCards=files(teamId).filter { file ->
            file.contains("-$category-") || (category == "阵营规则" && file.contains("-其他-"))
        }.map { file ->
            val rawLabel = (if (file.contains("-$category-")) file.substringAfter("-$category-") else file.substringAfter("-其他-"))
                .removeSuffix(".png")
            val resolvedTitle = entriesByCard[file]
                ?: if (rawLabel == "名称攻击命中伤害") "武器型阵营装备" else rawLabel
            ReferenceCard(resolvedTitle,"team_rules/$file")
        }
        if(teamCards.isNotEmpty() || category!="阵营装备") return teamCards
        return CardAssets.list(ui.context, "equipment")
            .filter { it.startsWith("$teamId--") }
            .sorted()
            .map { file -> ReferenceCard(file.substringAfter("--").removeSuffix(".png"),"equipment/$file") }
    }

    private fun inlineCategory(box: LinearLayout,teamId: String,category: String) {
        val cards=referenceCards(teamId,category)
        if(cards.isEmpty()) {
            box.addView(ui.text("暂无收录内容。",color=ui.muted),ui.wrap())
            return
        }
        ui.tiles(box,cards) { tile,card ->
            tile.addView(ui.text(card.title,17f,ui.accent,true),ui.wrap())
            ui.preview(tile,card.title,card.asset,expandable=true)
        }
        box.addView(ui.text("原卡可能包含旧数值；以采用的勘误版本为准。",12f,ui.muted),ui.wrap())
    }

    fun faction(teamId: String, includeComposition: Boolean=true, loadout: ((LinearLayout)->Unit)?=null) {
        ui.reference("${RosterCatalog.team(teamId).name} · 小队规则") { box,_ ->
            val rules=ui.column()
        val strategy=ui.column()
        val firefight=ui.column()
        val factionEquipment=ui.column()
        val composition=ui.column()
        val battleSetup=ui.column()
            fun jump(section: LinearLayout) { (box.parent as? android.widget.ScrollView)?.smoothScrollTo(0,section.top) }
            box.addView(ui.row().apply {
                if(includeComposition) addView(ui.link("编成") { jump(composition) },ui.weight())
                addView(ui.link("规则") { jump(rules) },ui.weight())
                addView(ui.link("战略") { jump(strategy) },ui.weight())
                addView(ui.link("战术") { jump(firefight) },ui.weight())
                addView(ui.link("装备") { jump(factionEquipment) },ui.weight())
            },ui.wrap())
            if(includeComposition) {
                composition.addView(ui.text("编成指南",18f,ui.accent,true),ui.wrap())
                inlineCategory(composition,teamId,"小队选择")
                box.addView(composition,ui.wrap())
                box.addView(ui.divider())
            }
            rules.addView(ui.text("小队特殊规则",18f,ui.accent,true),ui.wrap())
            factionContents(rules,teamId)
            TeamRuleBook(ui,teamId).categories(rules)
            box.addView(rules,ui.wrap())
            box.addView(ui.divider())
            strategy.addView(ui.text("战略计谋",18f,ui.accent,true),ui.wrap())
            inlineCategory(strategy,teamId,"战略计谋")
            box.addView(strategy,ui.wrap())
            box.addView(ui.divider())
            firefight.addView(ui.text("战术计谋（交战计谋）",18f,ui.accent,true),ui.wrap())
            inlineCategory(firefight,teamId,"交战计谋")
            box.addView(firefight,ui.wrap())
            box.addView(ui.divider())
            factionEquipment.addView(ui.text("小队专属装备",18f,ui.accent,true),ui.wrap())
            inlineCategory(factionEquipment,teamId,"阵营装备")
            box.addView(factionEquipment,ui.wrap())
            if(loadout!=null) {
                box.addView(ui.divider())
                battleSetup.addView(ui.text("本局配置",18f,ui.accent,true),ui.wrap())
                loadout(battleSetup)
                box.addView(battleSetup,ui.wrap())
            }
        }
    }

    fun ploys(teamId: String) {
        ui.reference("${RosterCatalog.team(teamId).name} · 小队计谋") { box,_ ->
            ployLinks(box,teamId)
        }
    }

    private fun ployLinks(box: LinearLayout,teamId: String) {
        listOf("战略计谋","交战计谋").forEach { category ->
            box.addView(ui.text(RuleOrganization.label(category),18f,ui.accent,true),ui.wrap(12))
            inlineCategory(box,teamId,category)
        }
    }

    private fun factionRules(teamId: String) {
        ui.reference("${RosterCatalog.team(teamId).name} · 小队特殊规则") { box,_ -> factionContents(box,teamId) }
    }
    private fun factionContents(box: LinearLayout,teamId: String) {
        inlineCategory(box,teamId,"阵营规则")
    }
    fun operative(teamId: String,state: BattleOperativeState) =
        member(teamId,state.operativeId,state.displayName)

    fun operativeCard(teamId: String, state: BattleOperativeState) {
        val template = RosterCatalog.operative(RosterCatalog.team(teamId), state.operativeId) ?: return
        ReferenceOverlay.showCards(
            context = ui.context,
            tag = "battle_operative_card_overlay",
            description = "${state.displayName}人物卡",
            cards = template.cardSources()
        )
    }

    fun member(teamId: String,operativeId: String,displayName: String) {
        val template=RosterCatalog.operative(RosterCatalog.team(teamId),operativeId)
        ui.reference("$displayName · 成员规则") { box,_ ->
            if(template!=null) box.addView(ui.text("${template.name} · APL ${template.apl} · 移动 ${template.move} · 豁免 ${template.save} · 耐伤 ${template.wounds}",12f,ui.muted),ui.wrap())
            template?.baseSizeMm?.let { box.addView(ui.text("底座 ${it}mm",12f,ui.muted),ui.wrap()) }
            if(!template?.keywords.isNullOrEmpty()) box.addView(ui.text(template!!.keywords.joinToString(" · "),11f,ui.muted),ui.wrap())
            box.addView(ui.text("专属能力与行动",16f,ui.accent,true),ui.wrap())
            // Ravener entries include the latest ambush erratum. Warpcoven shared
            // chapters must not expose other operative types' abilities here.
            val current=if(operativeId.startsWith("rav_")) TeamRuleBook(ui,teamId).operativeEntry(operativeId) else null
            val personal=RuleOrganization.personalAbilities(operativeId)
            if(current!=null) box.addView(ui.text(current.body),ui.wrap())
            else if(personal.isEmpty()) box.addView(ui.text(
                "此成员没有额外的专属能力。",
                color=ui.muted
            ),ui.wrap())
                else if(ui.isTablet) ui.tiles(box, personal) { tile, ability ->
                    tile.addView(ui.text(ability.name+(ability.apCost?.let { " · ${it}AP" } ?: ""),15f,ui.accent,true),ui.wrap())
                    tile.addView(ui.text(ability.description),ui.wrap())
                } else personal.forEach { ability ->
                box.addView(ui.text(ability.name+(ability.apCost?.let { " · ${it}AP" } ?: ""),15f,ui.accent,true),ui.wrap(6))
                box.addView(ui.text(ability.description),ui.wrap())
            }
            val cardSources = template?.cardSources().orEmpty()
            val dataCard = cardSources.firstOrNull { it.label == "数据卡" } ?: cardSources.firstOrNull()
            val abilityCards = cardSources.filter { it !== dataCard }
            if(template!=null && dataCard!=null && ui.isTablet) {
                box.addView(ui.text("成员数据原卡 · 原卡可能含旧数值", 12f, ui.muted), ui.wrap())
                ui.preview(box, template.name, asset = dataCard.asset, resource = dataCard.resource.takeIf { dataCard.asset == null })
            }
            if(template!=null && dataCard!=null && !ui.isTablet) box.addView(ui.link("成员数据原卡 ›") {
                ui.reference(template.name) { card,_ ->
                    card.addView(ui.text("双指缩放 · 拖动查看 · 原卡可能含旧数值",11f,ui.muted),ui.wrap())
                    card.addView(ZoomReference(ui.context,asset=dataCard.asset,resource=dataCard.resource.takeIf { dataCard.asset == null }),LinearLayout.LayoutParams(-1,ui.dp(400)))
                }
            },ui.wrap())
            box.addView(ui.divider())
            if(abilityCards.isNotEmpty() && ui.isTablet) {
                ui.tiles(box,abilityCards) { tile,src ->
                    ui.preview(tile,"$displayName · ${src.label}",asset=src.asset,resource=src.resource.takeIf { src.asset == null })
                }
            } else abilityCards.forEachIndexed { index,src ->
                val label = if(abilityCards.size > 1) "成员能力原卡 ${index+1} ›" else "成员能力原卡 ›"
                box.addView(ui.link(label) {
                    ui.reference("$displayName · ${src.label}") { card,_ ->
                        card.addView(ui.text("双指缩放 · 拖动查看 · 原卡可能含旧数值",11f,ui.muted),ui.wrap())
                        card.addView(ZoomReference(ui.context,asset=src.asset,resource=src.resource.takeIf { src.asset == null }),LinearLayout.LayoutParams(-1,ui.dp(400)))
                    }
                },ui.wrap())
            }
            box.addView(ui.text("小队共享资料",12f,ui.muted),ui.wrap(4))
            box.addView(ui.link("小队计谋 ›") { ploys(teamId) },ui.wrap(2))
            box.addView(ui.link("阵营与通用规则 ›") { factionRules(teamId) },ui.wrap(2))
        }
    }

    private fun original(title: String,file: String) {
        ui.reference(title) { box,_ ->
            box.addView(ui.text("双指缩放 · 拖动查看 · 双击复位",11f,ui.muted),ui.wrap())
            box.addView(ZoomReference(ui.context,"team_rules/$file"),LinearLayout.LayoutParams(-1,ui.dp(400)))
        }
    }
}
