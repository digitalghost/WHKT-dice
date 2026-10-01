package com.example.helloworld

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A notebook, not a rules referee: no mandatory phases, target selection or AP gates. */
class BattleActivity : AppCompatActivity() {
    private lateinit var ui: RecordUi
    private lateinit var store: BattleStore
    private lateinit var battle: BattleRecord
    private var selectedSide="A"
    private var battleScroll: ScrollView?=null
    private val scrollPositions=mutableMapOf<String,Int>()
    private var renderedSide="A"
    private var pendingScroll: Int?=null
    private val scoreTabViews=mutableMapOf<String,android.widget.TextView>()
    private val viewState by lazy { getSharedPreferences("battle_view_state", MODE_PRIVATE) }
    private fun capturePosition() { battleScroll?.let { scrollPositions[renderedSide]=pendingScroll ?: it.scrollY } }
    override fun onPause() {
        capturePosition()
        if(::battle.isInitialized) viewState.edit().apply {
            putString("${battle.battleId}_side",selectedSide)
            scrollPositions.forEach { (side,y) -> putInt("${battle.battleId}_scroll_$side",y) }
        }.apply()
        super.onPause()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);ui=RecordUi(this);store=BattleStore(this)
        val loaded=intent.getStringExtra(EXTRA_BATTLE_ID)?.let(store::load) ?: store.active()
        if(loaded==null) { finish();return }
        battle=loaded
        selectedSide=savedInstanceState?.getString("side") ?: viewState.getString("${battle.battleId}_side","A") ?: "A"
        listOf("A","B").forEach { side -> scrollPositions[side]=savedInstanceState?.getInt("scroll_$side") ?: viewState.getInt("${battle.battleId}_scroll_$side",0) }
        render()
    }
    override fun onResume() { super.onResume();if(::battle.isInitialized) { store.load(battle.battleId)?.let { battle=it };render() } }
    override fun onSaveInstanceState(outState: Bundle) { capturePosition();outState.putString("side",selectedSide);scrollPositions.forEach { (side,y) -> outState.putInt("scroll_$side",y) };super.onSaveInstanceState(outState) }
 private fun save(next: BattleRecord,message: String,renderAfter: Boolean=true) {
 if(battle.completed) return
 battle=store.save(next.copy(log=next.log+BattleLogEntry(turningPoint=next.turningPoint,phase=next.phase,message=message)))
 if(renderAfter) render()
 }
 private fun sideSave(side: BattleSide,message: String,renderAfter: Boolean=true) =
     save(if(side.id==battle.sideA.id) battle.copy(sideA=side) else battle.copy(sideB=side),message,renderAfter)

 private fun timestamp(value: Long): String =
     SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date(value))
    private fun render() {
        capturePosition()
        scoreTabViews.clear()
        renderedSide=selectedSide
        val restoreY=scrollPositions[selectedSide] ?: 0
        val root=ui.column().apply { setBackgroundColor(ui.base) }
        root.addView(ui.row().apply {
            setPadding(ui.dp(12),ui.dp(2),ui.dp(12),0)
            addView(ui.link("‹ 返回") { finish() })
            addView(ui.heading(if(battle.completed) "对局记录" else "对局 · TP ${battle.turningPoint.coerceAtLeast(1)}",25f),ui.weight())
            if(!battle.completed) addView(ui.link("撤销") { undo() })
            addView(ui.link("更多") { more() })
        })
        root.addView(ui.divider())
        val content=ui.column(12)
        val scroll=ScrollView(this).apply { addView(content) }
        battleScroll=scroll
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        content.addView(ui.row().apply {
            addView(ui.link(if(battle.completed) "已结束" else "TP ${battle.turningPoint.coerceAtLeast(1)} / 4  ▾") { rounds() })
            addView(ui.link(if(battle.keyOp.isBlank()) "未记录关键行动" else "${battle.keyOp}  ›") { tasks() },ui.weight())
            addView(ui.link("记录") { journal() })
        },ui.wrap(8))
        val tablet=resources.configuration.smallestScreenWidthDp>=600
        if(tablet) {
            content.addView(ui.row().apply {
                gravity=android.view.Gravity.TOP
                addView(sidePanel(battle.sideA),ui.weight());addView(sidePanel(battle.sideB),ui.weight())
            })
        } else {
            content.addView(ui.row().apply {
                listOf(battle.sideA,battle.sideB).forEach { side ->
                    val scoreTab=compactButton("${side.playerName}  ${if(battle.completed) side.totalScore else side.currentScore} VP",12f,selectedSide==side.id) { selectedSide=side.id;render() }
                    scoreTabViews[side.id]=scoreTab
                    addView(scoreTab,ui.weight())
                }
            },ui.wrap(10))
            content.addView(sidePanel(battle.side(selectedSide)))
        }
        pendingScroll=restoreY
        scroll.post { if(battleScroll===scroll) { scroll.scrollTo(0,restoreY);pendingScroll=null } }
    }
    private fun inlineAction(title: String,size: Float=14f,bold: Boolean=true,block: ()->Unit)=
        ui.text(title,size,ui.accent,bold).apply {
            gravity=android.view.Gravity.CENTER_VERTICAL
            minHeight=ui.dp(44)
            setPadding(ui.dp(2),ui.dp(7),ui.dp(2),ui.dp(7))
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33ee7540),null,null
            )
            setOnClickListener { block() }
        }
    private fun compactButton(title: String,size: Float=12f,primary: Boolean=false,block: ()->Unit)=
        ui.text(title,size,ui.ink,true).apply {
            gravity=android.view.Gravity.CENTER
            minHeight=ui.dp(44)
            setPadding(ui.dp(7),ui.dp(5),ui.dp(7),ui.dp(5))
            background=android.graphics.drawable.InsetDrawable(
                android.graphics.drawable.StateListDrawable().apply {
                    addState(
                        intArrayOf(android.R.attr.state_pressed),
                        ui.bg(if(primary) 0xff973916.toInt() else 0xff36302b.toInt())
                    )
                    addState(intArrayOf(),ui.bg(if(primary) ui.orange else 0xff242424.toInt()))
                },
                ui.dp(7),ui.dp(8),ui.dp(7),ui.dp(8)
            )
            setOnClickListener { block() }
        }
    private fun compactIconButton(icon: Int,description: String,block: ()->Unit)=
        androidx.appcompat.widget.AppCompatImageButton(this).apply {
            setImageResource(icon)
            scaleType=android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.dp(13),ui.dp(13),ui.dp(13),ui.dp(13))
            background=android.graphics.drawable.InsetDrawable(
                android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed),ui.bg(0xff36302b.toInt()))
                    addState(intArrayOf(),ui.bg(0xff242424.toInt()))
                },
                ui.dp(7),ui.dp(8),ui.dp(7),ui.dp(8)
            )
            contentDescription=description
            setOnClickListener { block() }
        }
    private fun counter(title: String, value: Int, max: Int=Int.MAX_VALUE, accessibilityTitle: String=title, change: (Int)->Int): LinearLayout {
        var currentValue=value
        val valueText=ui.text(value.toString(),20f,ui.accent,true).apply {
            gravity=android.view.Gravity.CENTER
        }
        lateinit var minusButton: android.view.View
        lateinit var plusButton: android.view.View

        fun sync() {
            valueText.text=currentValue.toString()
            fun updateButton(button: android.view.View, enabled: Boolean) {
                button.isEnabled=!battle.completed && enabled
                button.alpha=if(button.isEnabled) 1f else .35f
            }
            updateButton(minusButton,currentValue>0)
            updateButton(plusButton,currentValue<max)
        }

        fun adjust(label: String,delta: Int)=compactButton(label,18f) {
            currentValue=change(delta).coerceIn(0,max)
            sync()
        }.apply {
            contentDescription="$accessibilityTitle${if(delta<0) "减少" else "增加"}1"
        }

        return ui.column().apply {
            addView(ui.text(title,11f,ui.muted,true),ui.wrap(1))
            addView(ui.row().apply {
                minusButton=adjust("−",-1)
                plusButton=adjust("＋",1)
                addView(minusButton,LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
                addView(valueText,ui.weight())
                addView(plusButton,LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
                sync()
            })
        }
    }

    /**
     * Wounds change often during a battle, so updating them must not rebuild the whole
     * battle screen. Rebuilding here makes the window flash and also needlessly recreates
     * every operative card. This counter persists the updated battle record, then only
     * refreshes its own number and enabled states.
     */
    private fun operativeWoundCounter(sideId: String, operative: BattleOperativeState): LinearLayout {
        val title="耐伤 / ${operative.maxWounds}"
        val valueText=ui.text(operative.currentWounds.toString(),20f,ui.accent,true).apply {
            gravity=android.view.Gravity.CENTER
        }
        lateinit var minusButton: android.view.View
        lateinit var plusButton: android.view.View

        fun sync(value: Int) {
            valueText.text=value.toString()
            fun updateButton(button: android.view.View, enabled: Boolean) {
                button.isEnabled=!battle.completed && enabled
                button.alpha=if(button.isEnabled) 1f else .35f
            }
            updateButton(minusButton,value>0)
            updateButton(plusButton,value<operative.maxWounds)
        }

        fun adjust(label: String,delta: Int)=compactButton(label,18f) {
            val current=battle.side(sideId)
            val member=current.operatives.firstOrNull { it.memberId==operative.memberId }
                ?: return@compactButton
            val wounds=(member.currentWounds+delta).coerceIn(0,member.maxWounds)
            if(wounds==member.currentWounds) return@compactButton
            sideSave(
                current.copy(operatives=current.operatives.map {
                    if(it.memberId==member.memberId) it.copy(currentWounds=wounds) else it
                }),
                "${member.displayName}：耐伤 $wounds/${member.maxWounds}",
                renderAfter=false
            )
            sync(wounds)
        }.apply {
            contentDescription="${operative.displayName} · $title${if(delta<0) "减少" else "增加"}1"
        }

        return ui.column().apply {
            addView(ui.text(title,11f,ui.muted,true),ui.wrap(1))
            addView(ui.row().apply {
                minusButton=adjust("−",-1)
                plusButton=adjust("＋",1)
                addView(minusButton,LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
                addView(valueText,ui.weight())
                addView(plusButton,LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
                sync(operative.currentWounds)
            })
        }
    }
    private fun operativeToken(
        side: BattleSide,
        operative: BattleOperativeState,
        onChanged: (BattleOperativeState) -> Unit
    ): android.view.View = OrderTokenSwitch(this).apply {
        bind(operative.order,operative.ready)
        isEnabled=!battle.completed && !operative.incapacitated
        alpha=if(operative.incapacitated) .5f else 1f
        contentDescription="${operative.displayName}，$contentDescription"
        onFaceFlipped = {
            val current=battle.side(side.id)
            val member=current.operatives.first { it.memberId==operative.memberId }
            val updated=member.copy(ready=!member.ready)
            sideSave(
                current.copy(operatives=current.operatives.map { if(it.memberId==member.memberId) updated else it }),
                "${member.displayName}：${member.order}标记变为${if(updated.ready) "亮面" else "暗面"}，当前${if(updated.ready) "就绪" else "待机"}",
                renderAfter=false
            )
            bind(updated.order,updated.ready)
            contentDescription="${updated.displayName}，${updated.order}命令，${OrderTokens.faceLabel(updated.ready)}"
            onChanged(updated)
        }
        onOrderSelected = { nextOrder ->
            val current=battle.side(side.id)
            val member=current.operatives.first { it.memberId==operative.memberId }
            val updated=member.copy(order=nextOrder)
            sideSave(
                current.copy(operatives=current.operatives.map { if(it.memberId==member.memberId) updated else it }),
                "${member.displayName}：命令切换为$nextOrder；${nextOrder}标记当前${if(updated.ready) "亮面 · 就绪" else "暗面 · 待机"}",
                renderAfter=false
            )
            bind(updated.order,updated.ready)
            contentDescription="${updated.displayName}，${updated.order}命令，${OrderTokens.faceLabel(updated.ready)}"
            onChanged(updated)
        }
    }
    private fun revealedTacticalCard(side: BattleSide): LinearLayout? {
        if (!SecretSelection.tacticalDetailsArePublic(
                side.tacticalOpNotes,
                side.tacticalOpRevealed,
                battle.completed
            )) return null

        val card=MissionCards.all.firstOrNull { it.name==side.tacticalOpNotes }
        return ui.card().apply {
            addView(ui.text("已公开战术行动",11f,ui.muted,true),ui.wrap(2))
            addView(ui.text(side.tacticalOpNotes,20f,ui.accent,true),ui.wrap(8))
            if(card!=null) MissionCards.cardDetails(ui,this,card)
            else addView(ui.text("未找到对应的战术行动细则。",12f,ui.muted),ui.wrap())
        }
    }

    private fun sidePanel(side: BattleSide): LinearLayout = ui.column().apply {
        val isLeft = side.id == battle.sideA.id
        setPadding(ui.dp(10),ui.dp(10),ui.dp(10),ui.dp(10))
        background=ui.bg(
            if(isLeft) 0xff211914.toInt() else 0xff141c22.toInt(),
            if(isLeft) 0xff6c3d24.toInt() else 0xff35566a.toInt()
        )
        val teamName=RosterCatalog.team(side.teamId).name
        lateinit var scoreTotalView: android.widget.TextView
        addView(ui.row().apply {
            addView(ui.portrait(ui.teamArt(side.teamId),48))
            addView(ui.column().apply {
                addView(ui.text(teamName,20f,bold=true),ui.wrap(2))
                addView(ui.text("${side.playerName} · ${side.rosterName}",11f,ui.muted),ui.wrap(2))
            },ui.weight())
            scoreTotalView=ui.text("${if(battle.completed) side.totalScore else side.currentScore} VP",22f,ui.accent,true)
            addView(scoreTotalView)
        },ui.wrap(4))
        val counters=listOf(counter("CP",side.commandPoints,accessibilityTitle="${side.playerName} · CP") { delta ->
            val current=battle.side(side.id)
            val points=(current.commandPoints+delta).coerceAtLeast(0)
            sideSave(current.copy(commandPoints=points),"${current.playerName}：CP $points",renderAfter=false)
            points
        }) + ScoreType.entries.map { type ->
            counter(type.label+" VP",side.score.value(type),BattleScore.MAX_SCORE_PER_OP) { delta ->
                val current=battle.side(side.id)
                val updatedScore=current.score.change(type,delta)
                val updatedSide=current.copy(score=updatedScore)
                sideSave(updatedSide,"${current.playerName}：${type.label} ${updatedScore.value(type)} VP",renderAfter=false)
                scoreTotalView.text="${updatedSide.currentScore} VP"
                scoreTabViews[updatedSide.id]?.text="${updatedSide.playerName}  ${updatedSide.currentScore} VP"
                updatedScore.value(type)
            }
        }
        counters.chunked(2).forEach { pair -> addView(ui.row().apply { pair.forEach { addView(it,ui.weight()) } },ui.wrap(4)) }
        addView(ui.row().apply {
            addView(ui.link("小队资料 · 装备与战术 ›") { teamInfo(side.id) },ui.weight())
            addView(ui.text("${side.operatives.count { !it.incapacitated }}/${side.operatives.size} 在场",11f,ui.muted))
        })
        revealedTacticalCard(side)?.let { addView(it,ui.wrap(12)) }
        addView(ui.divider())
        side.operatives.forEachIndexed { index,operative ->
            val memberRow=ui.row()
            val reactionSlot=android.widget.FrameLayout(this@BattleActivity)
            val template=RosterCatalog.operative(RosterCatalog.team(side.teamId),operative.operativeId)
            val savedMember=RosterStore(this@BattleActivity).load(side.rosterId)?.members?.firstOrNull { it.id==operative.memberId }
            if(template!=null) memberRow.addView(ui.memberPortrait(savedMember?.customAvatarUri,template.cardRes,40))
            memberRow.apply {
                addView(ui.text("%02d".format(index+1),12f,ui.accent,true).apply {
                    gravity=android.view.Gravity.CENTER_VERTICAL
                },LinearLayout.LayoutParams(ui.dp(28),ui.dp(44)))
                val nameView=inlineAction("${operative.displayName} ›",16f,true) { BattleReference(ui).operativeCard(side.teamId,operative) }.apply {
                    contentDescription="${operative.displayName}，查看人物卡"
                }
                addView(nameView,ui.weight())
                fun updateActivationVisual(updated: BattleOperativeState) {
                    nameView.setTextColor(if(updated.ready) ui.accent else ui.muted)
                    memberRow.background=if(updated.ready) null else ui.bg(0xff1a1918.toInt(),0xff3d3833.toInt())
                    reactionSlot.removeAllViews()
                    val reactionKey="${side.id}/${updated.memberId}"
                    val reacted=reactionKey in battle.progress.reacted
                    val canReact=!updated.ready && !updated.incapacitated &&
                        (updated.order==BattleOperativeState.ORDER_ENGAGE || reacted)
                    reactionSlot.visibility=if(canReact) android.view.View.VISIBLE else android.view.View.GONE
                    if(canReact) reactionSlot.addView(androidx.appcompat.widget.AppCompatImageButton(this@BattleActivity).apply {
                        setImageResource(if(reacted) R.drawable.ic_reaction_done else R.drawable.ic_reaction)
                        scaleType=android.widget.ImageView.ScaleType.CENTER_INSIDE
                        setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8))
                        background=ui.bg(if(reacted) 0xff172019.toInt() else 0xff29201b.toInt())
                        contentDescription=if(reacted) "${updated.displayName}本回合已反应" else "记录${updated.displayName}执行反应"
                        isEnabled=!reacted && !battle.completed
                        alpha=if(reacted) .72f else 1f
                        if(!reacted) setOnClickListener {
                            val next=battle.copy(progress=battle.progress.copy(reacted=battle.progress.reacted+reactionKey))
                            save(next,"${updated.displayName}：执行反应，本回合反应已使用",renderAfter=false)
                            updateActivationVisual(updated)
                        }
                    },android.widget.FrameLayout.LayoutParams(ui.dp(40),ui.dp(40)))
                }
                addView(operativeToken(side,operative,::updateActivationVisual),LinearLayout.LayoutParams(ui.dp(92),ui.dp(48)).apply {
                    marginEnd=ui.dp(4)
                })
                addView(reactionSlot,LinearLayout.LayoutParams(ui.dp(40),ui.dp(44)).apply { marginEnd=ui.dp(4) })
                addView(inlineAction(if(operative.incapacitated) "已残废" else "在场",11f,true) {
                    val current=battle.side(side.id)
                    val member=current.operatives.first { it.memberId==operative.memberId }
                    sideSave(current.copy(operatives=current.operatives.map { if(it.memberId==member.memberId) it.copy(incapacitated=!it.incapacitated) else it }),"${operative.displayName}：${if(member.incapacitated) "恢复在场" else "标记残废"}")
                }.apply { isEnabled=!battle.completed })
                updateActivationVisual(operative)
            }
            addView(memberRow,ui.wrap(3))
            addView(operativeWoundCounter(side.id,operative),ui.wrap(4))
            val weapons=weaponSnapshots(side,operative)
            weapons.forEach { weapon ->
                addView(ui.row().apply {
                    addView(ui.column().apply {
                        addView(ui.text(weapon.name,15f,bold=true),ui.wrap(2))
                        addView(WeaponRulesUi.profile(ui,weapon.name,weapon.profile,weapon.keywords,11f,ui.muted),ui.wrap(2))
                    },ui.weight())
                    if(!battle.completed) {
                        val action=WeaponActions.label(weapon.id)
                        val icon=if(WeaponActions.isMelee(weapon.id)) R.drawable.ic_weapon_melee else R.drawable.ic_weapon_ranged
                        addView(compactIconButton(icon,"${operative.displayName} · ${weapon.name} · $action") {
                            roll(battle.side(side.id),operative,weapon)
                        },LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
                    }
                },ui.wrap(4))
            }
            if(weapons.isEmpty()) addView(ui.text("未记录武器",11f,ui.muted),ui.wrap())
            addView(ui.divider())
        }
    }
    private fun weaponSnapshots(side: BattleSide,operative: BattleOperativeState): List<BattleWeaponSnapshot> = operative.weapons.ifEmpty {
        val member=RosterStore(this).load(side.rosterId)?.members?.firstOrNull { it.id==operative.memberId }
        RosterCatalog.operative(RosterCatalog.team(side.teamId),operative.operativeId)?.weapons.orEmpty()
            .filter { member==null || it.id in member.weaponIds }.map { w ->
                val stats=w.stats
                BattleWeaponSnapshot(w.id,w.name,w.profile,stats.attacks,stats.hit,stats.criticalThreshold,stats.normalDamage,stats.criticalDamage,stats.keywords)
            }
    }
 private fun undo(skipRoundConfirmation: Boolean=false,afterUndo: (() -> Unit)?=null) {
        if(battle.completed) return
        val isRoundChange=BattleRoundTransitions.isRoundChange(battle.log.lastOrNull())
        if(isRoundChange && !skipRoundConfirmation) {
            ui.dialog("撤回当前回合切换？") { box,dialog ->
                box.addView(ui.text("将撤回当前回合切换，并恢复切换前的回合以及两队成员的就绪／待机状态。",14f),ui.wrap(16))
 box.addView(ui.button("确认撤回",true) { dialog.dismiss();undo(skipRoundConfirmation=true,afterUndo=afterUndo) },ui.wrap())
            }
            return
        }
        val restored=store.undo(battle.battleId)
 if(restored!=null) { battle=restored;afterUndo?.invoke();render() }
        else ui.note("没有可撤销的记录。")
    }
    private fun roll(side: BattleSide,operative: BattleOperativeState,weapon: BattleWeaponSnapshot) {
        save(battle,"${side.playerName} · ${operative.displayName}使用${weapon.name}")
        startActivity(Intent(this,MainActivity::class.java).apply {
            putExtra(EXTRA_BATTLE_ID,battle.battleId)
            putExtra("battle_roll_context","${side.playerName} · ${operative.displayName} · ${weapon.name}")
            putExtra(MainActivity.EXTRA_ROSTER_NAME,"${battle.name} · ${side.playerName}")
            putExtra(MainActivity.EXTRA_MEMBER_NAME,operative.displayName);putExtra(MainActivity.EXTRA_WEAPON_NAME,weapon.name)
            putExtra(MainActivity.EXTRA_ATTACK_DICE,weapon.attacks);putExtra(MainActivity.EXTRA_HIT,weapon.hit)
            putExtra(MainActivity.EXTRA_CRITICAL,weapon.critical);putExtra(MainActivity.EXTRA_NORMAL_DAMAGE,weapon.normalDamage)
            putExtra(MainActivity.EXTRA_CRITICAL_DAMAGE,weapon.criticalDamage)
            putStringArrayListExtra(MainActivity.EXTRA_WEAPON_KEYWORDS,ArrayList(weapon.keywords))
        })
    }
    private fun teamInfo(sideId: String) {
        val side=battle.side(sideId)
        BattleReference(ui).faction(side.teamId) { box ->
            box.addView(ui.text("当前装备",18f,ui.accent,true),ui.wrap(8))
            EquipmentCards.selected(ui,box,side.teamId,side.equipmentNotes)
            box.addView(ui.text("装备已在对局开始时锁定，进行中不可变更。",12f,ui.muted),ui.wrap(12))
            val tacticalSection=ui.column()
            fun renderTacticalSection() {
                tacticalSection.removeAllViews()
                val tactical=battle.side(sideId)
                val tacticalPublic=SecretSelection.tacticalDetailsArePublic(
                    tactical.tacticalOpNotes,
                    tactical.tacticalOpRevealed,
                    battle.completed
                )
                tacticalSection.addView(ui.link("战术行动 · ${SecretSelection.tacticalStatus(tactical.tacticalOpNotes,tacticalPublic)} ›") {
                    if(tacticalPublic && tactical.tacticalOpNotes.isNotBlank()) {
                        MissionCards.all.firstOrNull { it.name==tactical.tacticalOpNotes }?.let { MissionCards.detail(ui,it) }
                    } else chooseTactical(sideId,::renderTacticalSection)
                },ui.wrap())
            }
            renderTacticalSection()
            box.addView(tacticalSection,ui.wrap())
            val current=battle.side(sideId)
            val primaryStatus=if(battle.completed) current.primaryOp?.label ?: "未选择"
                else SecretSelection.primaryStatus(current.primaryOp)
            box.addView(ui.link("主要行动 · $primaryStatus ›") { viewPrimary(sideId) },ui.wrap())
        }
    }
    private fun chooseTactical(sideId: String,onChanged: (() -> Unit)?=null) {
        val current=battle.side(sideId)
        SecretSelection.open(ui,current.playerName,"战术行动") { box,sheet ->
            val archetypes=TeamArchetypes.forTeam(current.teamId)
            box.addView(ui.text("${RosterCatalog.team(current.teamId).name} · 小队原型",16f,ui.accent,true),ui.wrap(4))
            box.addView(ui.text(archetypes.joinToString("  ·  "),15f,bold=true),ui.wrap(12))
            if(current.tacticalOpNotes.isNotBlank()) {
                val card=MissionCards.all.firstOrNull { it.name==current.tacticalOpNotes }
                box.addView(ui.text("当前选择：${current.tacticalOpNotes}",17f,ui.accent,true),ui.wrap(4))
                if(card!=null) MissionCards.contents(ui,box,card)
                box.addView(ui.button("按卡片时机公开战术行动",true) {
                    ui.dialog("确认公开？") { confirm,confirmDialog ->
                        confirm.addView(ui.text("公开后，双方都会在公共界面看到「${current.tacticalOpNotes}」。",14f),ui.wrap(16))
                        confirm.addView(ui.button("确认公开",true) {
                            sideSave(
                                battle.side(sideId).copy(tacticalOpRevealed=true),
                                "${current.playerName}公开战术行动：${current.tacticalOpNotes}"
                            )
                            confirmDialog.dismiss()
                            sheet.dismiss()
                            onChanged?.invoke()
                            ui.refreshDialog()
                        },ui.wrap())
                    }
                },ui.wrap(12))
            } else {
                box.addView(ui.text("尚未选择战术行动。",12f,ui.muted),ui.wrap(12))
            }
            if(!battle.completed && current.tacticalOpNotes.isBlank()) box.addView(ui.button("为旧对局补选战术行动") {
                MissionCards.picker(
                    ui,
                    tactical=true,
                    selected=current.tacticalOpNotes,
                    teamId=current.teamId
                ) { card ->
                        sideSave(
                            battle.side(sideId).copy(tacticalOpNotes=card.name,tacticalOpRevealed=false),
                            "${current.playerName}已私密选择战术行动",
                            renderAfter=false
                        )
                        sheet.dismiss()
                        onChanged?.invoke()
                        ui.refreshDialog()
                }
            },ui.wrap())
        }
    }
    private fun viewPrimary(sideId: String) {
        val current=battle.side(sideId)
        if(battle.completed) {
            ui.dialog("主要行动") { box,_ ->
                box.addView(ui.text(current.primaryOp?.label ?: "未选择",20f,ui.accent,true),ui.wrap(8))
                box.addView(ui.text("战斗结束后，主要行动已公开。",12f,ui.muted),ui.wrap())
            }
            return
        }
        SecretSelection.open(ui,current.playerName,"查看主要行动") { box,_ ->
            box.addView(ui.text(current.primaryOp?.label ?: "未选择",20f,ui.accent,true),ui.wrap(8))
            box.addView(ui.text("主要行动会在战斗结束时由双方同时揭示。",12f,ui.muted),ui.wrap())
        }
    }
    private fun tasks() {
        val card=MissionCards.all.firstOrNull { it.name==battle.keyOp }
        if(card!=null) MissionCards.detail(ui,card)
        else ui.note(if(battle.keyOp.isBlank()) "这份旧对局未记录关键行动。" else "当前关键行动：${battle.keyOp}")
    }
    private fun rounds() {
        if(battle.completed) return
        ui.dialog("回合标记") { box,sheet ->
            val current=battle.turningPoint.coerceIn(1,BattleRules.TURNING_POINT_COUNT)
            box.addView(ui.text("当前：第 $current 回合",20f,ui.accent,true),ui.wrap(6))
            box.addView(ui.text("回合只能按顺序推进。推进后会将两队在场成员的标记翻回亮面（就绪），但不改变隐匿／交战命令。",13f,ui.muted),ui.wrap(16))
            if(current<BattleRules.TURNING_POINT_COUNT) {
                val nextTp=current+1
                box.addView(ui.button("进入第 $nextTp 回合",true) {
                    ui.dialog("确认进入第 $nextTp 回合？") { confirm,confirmDialog ->
                        confirm.addView(ui.text("请先确认第 $current 回合的任务得分、目标控制、效果持续时间和其他回合末结算均已完成。",14f),ui.wrap(16))
                        confirm.addView(ui.button("已完成结算，继续",true) {
                            val next=BattleRoundTransitions.advance(battle)
                            save(next,"进入第 $nextTp 回合；两队在场成员的命令标记已翻为亮面 · 就绪")
                            confirmDialog.dismiss();sheet.dismiss()
                        },ui.wrap())
                    }
                },ui.wrap())
            } else {
                box.addView(ui.text("已是第 4 回合，不能再向后推进。",13f,ui.muted),ui.wrap())
            }
            if(BattleRoundTransitions.isRoundChange(battle.log.lastOrNull())) {
                box.addView(ui.button("撤回当前回合切换") { undo(afterUndo={ sheet.dismiss() }) },ui.wrap())
            }
        }
    }
    private fun journal() {
        ui.dialog("对局记录") { box,sheet ->
            if(!battle.completed) {
                val input=ui.input("","伤害、目标状态或其他备注");input.setSingleLine(false);input.minLines=2
                box.addView(input,ui.wrap())
                box.addView(ui.button("保存备注",true) { if(input.text.isNotBlank()) { save(battle,input.text.toString());sheet.dismiss() } },ui.wrap(20))
            }
            if(battle.log.isEmpty()) box.addView(ui.text("暂时没有记录。",color=ui.muted))
            battle.log.reversed().forEach { entry -> box.addView(ui.text("${timestamp(entry.timestamp)} · TP${entry.turningPoint} · ${entry.message}",13f,ui.muted),ui.wrap()) }
        }
    }
    private fun more() {
        ui.dialog("对局选项") { box,sheet ->
            box.addView(ui.button("修改对局名 / 玩家名") {
                ui.dialog("名称") { fields,edit ->
                    val name=ui.input(battle.name,"对局名称");val a=ui.input(battle.sideA.playerName,"玩家 A");val b=ui.input(battle.sideB.playerName,"玩家 B")
                    fields.addView(name,ui.wrap());fields.addView(a,ui.wrap());fields.addView(b,ui.wrap())
                    if(!battle.completed) fields.addView(ui.button("保存",true) { save(battle.copy(name=name.text.toString().ifBlank{battle.name},sideA=battle.sideA.copy(playerName=a.text.toString().ifBlank{"玩家 A"}),sideB=battle.sideB.copy(playerName=b.text.toString().ifBlank{"玩家 B"})),"更新对局名称");edit.dismiss();sheet.dismiss() })
                }
            },ui.wrap())
            box.addView(ui.button("撤销上一次记录") {
                undo();sheet.dismiss()
            },ui.wrap())
            // Keep navigation inside the existing dialog host. Dismissing the root
            // sheet and immediately creating another dialog races its OnDismiss
            // callback, which can clear the newly-created page and leave a black,
            // non-dismissible window behind.
            box.addView(ui.button("完整记录") { journal() },ui.wrap())
            box.addView(ui.button("导出记录") {
                val report=buildString {
                    appendLine(battle.name);appendLine("任务：${battle.keyOp} · 回合 ${battle.turningPoint}")
                    listOf(battle.sideA,battle.sideB).forEach { s ->
                        appendLine("${s.playerName} · ${s.rosterName} · ${if(battle.completed) s.totalScore else s.currentScore} VP / ${s.commandPoints} CP")
                        appendLine("装备：${s.equipmentNotes}")
                        if(battle.completed) appendLine("战术：${s.tacticalOpNotes}；主要行动：${s.primaryOp?.label}")
                        s.operatives.forEach { appendLine("${it.displayName} · ${it.currentWounds}/${it.maxWounds} · ${if(it.incapacitated) "已残废" else "在场"} · ${it.weapons.joinToString { w->w.name }}") }
                    }
                    battle.log.forEach { appendLine("${timestamp(it.timestamp)} · TP${it.turningPoint} · ${it.message}") }
                }
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"导出对局记录"))
            },ui.wrap())
            box.addView(ui.button("行动流程速查") { MissionCards.original(ui) },ui.wrap())
            if(!battle.completed) box.addView(ui.button("结束并保存对局") {
                ui.dialog("确认结束？") { confirm,finish ->
                    confirm.addView(ui.text("按当前手动记录的比分结束，不自动判断击杀或任务得分。已选择的主要行动会加上半数 VP（向上取整）。\n\n${battle.sideA.playerName}：${battle.sideA.totalScore} VP\n${battle.sideB.playerName}：${battle.sideB.totalScore} VP"),ui.wrap())
                    confirm.addView(ui.button("确认结束",true) { save(battle.copy(completed=true,phase=BattlePhase.COMPLETE),"对局结束");finish.dismiss();sheet.dismiss() })
                }
            })
        }
    }
    companion object { const val EXTRA_BATTLE_ID="battle_id" }
}
