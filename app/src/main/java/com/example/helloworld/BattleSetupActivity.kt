package com.example.helloworld

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

class BattleSetupActivity : AppCompatActivity() {
    private lateinit var ui: RecordUi
    private lateinit var rosterStore: RosterStore
    private var rosterA: SavedRoster? = null
    private var rosterB: SavedRoster? = null
    private var keyOp = ""
    private var equipmentA = ""
    private var equipmentB = ""
    private var primaryA: ScoreType? = null
    private var primaryB: ScoreType? = null
    private var tacticalA = ""
    private var tacticalB = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui=RecordUi(this);rosterStore=RosterStore(this)
        val rosters=rosterStore.all().filter { it.members.isNotEmpty() }
        rosterA=savedInstanceState?.getString("a")?.let(rosterStore::load) ?: rosters.firstOrNull()
        rosterB=savedInstanceState?.getString("b")?.let(rosterStore::load) ?: rosters.getOrNull(1) ?: rosters.firstOrNull()
        keyOp=savedInstanceState?.getString("key").orEmpty()
        equipmentA=savedInstanceState?.getString("ea").orEmpty();equipmentB=savedInstanceState?.getString("eb").orEmpty()
        primaryA=savedInstanceState?.getString("pa")?.let { runCatching { ScoreType.valueOf(it) }.getOrNull() }
        primaryB=savedInstanceState?.getString("pb")?.let { runCatching { ScoreType.valueOf(it) }.getOrNull() }
        tacticalA=savedInstanceState?.getString("ta").orEmpty()
        tacticalB=savedInstanceState?.getString("tb").orEmpty()
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("a",rosterA?.rosterId);outState.putString("b",rosterB?.rosterId)
        outState.putString("key",keyOp);outState.putString("ea",equipmentA);outState.putString("eb",equipmentB)
        outState.putString("pa",primaryA?.name);outState.putString("pb",primaryB?.name)
        outState.putString("ta",tacticalA);outState.putString("tb",tacticalB)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        val content=ui.column(16).apply { setBackgroundColor(ui.base) }
        setContentView(ScrollView(this).apply { setBackgroundColor(ui.base);isFillViewport=true;addView(content) })
        content.addView(ui.row().apply {
            addView(ui.link("‹ 返回") { finish() })
            addView(ui.heading("新建对局",27f),ui.weight())
        },ui.wrap(4))
        content.addView(ui.text("选择双方小队、私密主要行动和共用关键行动后开始对局。",13f,ui.muted),ui.wrap(16))
        listOf(true,false).forEach { a ->
            val roster=if(a) rosterA else rosterB
            val equipment=if(a) equipmentA else equipmentB
            val primary=if(a) primaryA else primaryB
            val tactical=if(a) tacticalA else tacticalB
            content.addView(ui.text(if(a) "玩家 A" else "玩家 B",18f,ui.accent,true),ui.wrap(10))
            content.addView(ui.row().apply {
                if(roster!=null) addView(ui.portrait(ui.teamArt(roster.teamId),64))
                addView(ui.column().apply {
                    addView(ui.text(roster?.name ?: "尚未选择队伍",17f,bold=true),ui.wrap(4))
                    addView(ui.text(roster?.let { "${RosterCatalog.team(it.teamId).name} · ${it.members.size} 名角色" } ?: "选择一份已保存的编成",12f,ui.muted))
                },ui.weight())
                addView(ui.link("选择 ›") { pickRoster(a) })
            },ui.wrap(6))
            if(roster!=null) content.addView(ui.row().apply {
                addView(ui.link("角色与武器") { rosterDetails(roster) },ui.weight())
                addView(ui.link(if(equipment.isBlank()) "＋ 装备" else "装备 ${equipment.lines().size}/4") {
                    EquipmentCards.picker(ui,roster.teamId,equipment) { if(a) equipmentA=it else equipmentB=it;render() }
                })
            })
            if(equipment.isNotBlank()) content.addView(ui.text(equipment.replace("\n"," · "),12f,ui.muted),ui.wrap())
            content.addView(ui.row().apply {
                addView(ui.text("主要行动 · 私密必选",15f,bold=true),ui.weight())
                addView(ui.link("${SecretSelection.primaryStatus(primary)} ›") { pickPrimary(a) })
            },ui.wrap(8))
            content.addView(ui.text("另一位玩家回避后选择；具体内容不会显示在公共界面。",12f,ui.muted),ui.wrap(8))
            content.addView(ui.row().apply {
                addView(ui.text("战术行动 · 私密必选",15f,bold=true),ui.weight())
                addView(ui.link("${SecretSelection.tacticalStatus(tactical,false)} ›") { pickTactical(a) })
            },ui.wrap(8))
            roster?.let {
                content.addView(
                    ui.text("${RosterCatalog.team(it.teamId).name}小队原型：${TeamArchetypes.forTeam(it.teamId).joinToString(" · ")}",12f,ui.muted),
                    ui.wrap(8)
                )
            }
            content.addView(ui.divider())
        }
        content.addView(ui.row().apply {
            addView(ui.text("关键行动 · 双方共用",15f,bold=true),ui.weight())
            addView(ui.link(if(keyOp.isBlank()) "请选择 ›" else "$keyOp ›") {
                MissionCards.picker(ui,false,keyOp) { keyOp=it.name;render() }
            })
        },ui.wrap(12))
        content.addView(ui.text("关键行动由双方共同决定，开局后锁定。",12f,ui.muted),ui.wrap())
        content.addView(ui.button("开始对局  →",true) { startBattle() }.apply {
            isEnabled=rosterA!=null && rosterB!=null && primaryA!=null && primaryB!=null &&
                tacticalA.isNotBlank() && tacticalB.isNotBlank() && keyOp.isNotBlank()
            alpha=if(isEnabled) 1f else .4f
        },ui.wrap())
        content.addView(ui.text("支持双方使用同一编成；状态与装备独立记录。",11f,ui.muted))
    }
    private fun pickPrimary(a: Boolean) {
        val current=if(a) primaryA else primaryB
        val playerName=if(a) "玩家 A" else "玩家 B"
        val teamId=(if(a) rosterA else rosterB)?.teamId
        SecretSelection.open(ui,playerName,"主要行动") { box,sheet ->
            box.addView(ui.text("从关键行动、击杀行动或战术行动中选择一项。战斗结束时双方同时揭示。",14f),ui.wrap(8))
            if(teamId!=null) {
                box.addView(ui.text("${RosterCatalog.team(teamId).name} · 小队原型",15f,ui.accent,true),ui.wrap(4))
                box.addView(ui.text(TeamArchetypes.forTeam(teamId).joinToString("  ·  "),14f,bold=true),ui.wrap(12))
            }
            box.addView(ui.text("结算时，该行动获得的 VP 会额外增加一半（向上取整）。",12f,ui.muted),ui.wrap(12))
            ScoreType.entries.forEach { type ->
                box.addView(ui.button(if(type==current) "${type.label}  ✓" else type.label,type==current) {
                    if(a) primaryA=type else primaryB=type
                    sheet.dismiss();render()
                },ui.wrap())
            }
        }
    }
    private fun pickTactical(a: Boolean) {
        val roster=if(a) rosterA else rosterB
        if(roster==null) {
            ui.note("请先为该玩家选择小队。")
            return
        }
        val current=if(a) tacticalA else tacticalB
        val playerName=if(a) "玩家 A" else "玩家 B"
        SecretSelection.open(ui,playerName,"战术行动") { box,sheet ->
            val archetypes=TeamArchetypes.forTeam(roster.teamId)
            box.addView(ui.text("${RosterCatalog.team(roster.teamId).name} · 小队原型",16f,ui.accent,true),ui.wrap(4))
            box.addView(ui.text(archetypes.joinToString("  ·  "),15f,bold=true),ui.wrap(8))
            if(current.isNotBlank()) box.addView(ui.text("当前选择：$current",13f,ui.muted),ui.wrap(12))
            MissionCards.inlineTacticalChoices(ui,box,current,roster.teamId) { card ->
                if(a) tacticalA=card.name else tacticalB=card.name
                sheet.dismiss()
                render()
            }
        }
    }
    private fun pickRoster(a: Boolean) {
        ui.dialog("选择${if(a) "A" else "B"}方小队") { box,sheet ->
            val rosters=rosterStore.all().filter { it.members.isNotEmpty() }
            if(rosters.isEmpty()) box.addView(ui.text("还没有编成。请返回「小队」创建角色与武器配置。"),ui.wrap())
            rosters.forEach { roster ->
                box.addView(ui.button("${roster.name} · ${RosterCatalog.team(roster.teamId).name} · ${roster.members.size} 人") {
                    rosterDetails(roster) {
                        if(a) {
                            if(rosterA?.teamId != roster.teamId) { equipmentA="";tacticalA="" }
                            rosterA=roster
                        } else {
                            if(rosterB?.teamId != roster.teamId) { equipmentB="";tacticalB="" }
                            rosterB=roster
                        }
                        sheet.dismiss();render()
                    }
                },ui.wrap())
            }
        }
    }
    private fun rosterDetails(roster: SavedRoster,choose: (() -> Unit)? = null) {
        ui.dialog(roster.name) { box,sheet ->
            if(choose!=null) box.addView(ui.button("使用这份编成",true) { choose();sheet.dismiss() },ui.wrap(16))
            roster.members.forEach { member ->
                val template=RosterCatalog.operative(RosterCatalog.team(roster.teamId),member.operativeId)
                box.addView(ui.row().apply {
                    if(template!=null) addView(ui.memberPortrait(member.customAvatarUri,template.cardRes,44))
                    addView(ui.text(member.callsign.ifBlank { template?.name ?: member.operativeId },18f,ui.accent,true),ui.weight())
                },ui.wrap(4))
            template?.weapons?.filter { it.id in member.weaponIds }?.forEach { weapon ->
                box.addView(ui.text(weapon.name,14f,ui.ink,true),ui.wrap(2))
                box.addView(WeaponRulesUi.profile(ui,weapon.name,weapon.profile,weapon.stats.keywords),ui.wrap(8))
            }
            }
            if(choose!=null) box.addView(ui.button("使用这份编成",true) { choose();sheet.dismiss() })
        }
    }
    private fun startBattle() {
        val a=rosterA;val b=rosterB
        if(a==null || b==null) { ui.note("请先为双方选择小队。");return }
        val aPrimary=primaryA;val bPrimary=primaryB
        if(aPrimary==null || bPrimary==null) { ui.note("请先为双方选择主要行动。");return }
        if(tacticalA.isBlank() || tacticalB.isBlank()) { ui.note("请先为双方私密选择战术行动。");return }
        if(keyOp.isBlank()) { ui.note("请先选择本局关键行动，开局后不可更换。");return }
        val record=BattleRules.create(BattleSetup("玩家 A vs 玩家 B","自由记录","",keyOp,"","","玩家 A","玩家 B",equipmentA,equipmentB,tacticalA,tacticalB,a,b,aPrimary,bPrimary))
            .copy(turningPoint=1,phase=BattlePhase.FIREFIGHT,log=emptyList())
        val store=BattleStore(this);store.save(record);store.setActive(record.battleId)
        startActivity(Intent(this,BattleActivity::class.java).putExtra(BattleActivity.EXTRA_BATTLE_ID,record.battleId));finish()
    }
}
