package com.example.helloworld

import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.LinearLayout

data class EquipmentCard(val name: String, val count: String, val rules: String, val asset: String? = null, val currentText: Boolean = false)

object EquipmentCards {
    const val MAX_SELECTIONS = 4

    fun canAdd(selectedCount: Int): Boolean = selectedCount < MAX_SELECTIONS

    val universal = listOf(
        EquipmentCard("轻型掩体", "2 件 · 地面部署", "轻型地形。必须完全部署在自己的领地内、杀戮区地面上，与其他装备地形、访问点和可穿越地形至少相距 2 英寸。支架属于不重要和暴露地形。"),
        EquipmentCard("重型掩体", "1 件 · 地面部署", "重型地形。必须完全部署在自己的降落区 4 英寸内、杀戮区地面上，与其他装备地形、访问点和可穿越地形至少相距 2 英寸。"),
        EquipmentCard("可移动掩体", "1 件 · 地面部署", "轻型、保护、可移动地形；支架属于不重要和暴露地形。完全部署在自己的领地内、杀戮区地面上，与其他装备地形、访问点和可穿越地形至少相距 2 英寸。\n\n保护：特工受到此地形提供的掩护时，防御属性加 1，最高提升至 2+。\n\n可移动：只有当特工与模型接触且由护盾（忽略支架）提供阻碍时，此模型才提供掩护。掩体内侧接触的特工可在战斗中执行「与掩体移动」（1AP）：视为转移行动，限制与转移一致。特工最多移动其移动属性减 2 英寸的距离；不能攀爬、坠落或跳跃，也不能同时使用可移除特工并重新部署的规则（例如飞行或暗影通道）。移动结束后将掩体放置于移动后特工底盘接触处；必须与其他装备地形、访问点和可穿越地形至少相距 2 英寸。若无空间放置，不能执行此行动。"),
        EquipmentCard("铁丝网", "1 件 · 地面部署", "暴露、阻碍地形。完全部署在自己的领地内、杀戮区地面上，与其他装备地形、访问点和可穿越地形至少相距 2 英寸。\n\n阻碍：特工翻越位于其 1 英寸内的本地形时，视为额外移动 1 英寸。"),
        EquipmentCard("弹药箱", "1 件 · 支援", "完全部署在自己的领地内。友方特工控制该装备标识时，可执行「补给弹药」（0AP）：直到下一个转折点开始，使用其数据卡上的远程武器射击时，可以重投 1 个骰子。位于敌方特工控制范围内时不能执行。弹药箱在每个转折点只能被使用 1 次。"),
        EquipmentCard("通讯设备", "1 件 · 支援", "完全部署在自己的领地内。当友方特工控制该友方装备标识时，其「支援」类能力的作用范围增加 3 英寸。"),
        EquipmentCard("梯子", "2 件 · 地形", "不重要和暴露地形。必须完全部署在自己的领地内，直立倚靠在高度大于等于 2 英寸的地形上，与其他装备地形至少相距 2 英寸，与访问点和可穿越地形至少相距 1 英寸。\n\n每个行动 1 次，当特工攀爬梯子依靠的地形时，只要这次攀爬的全过程中梯子完全位于该特工的控制范围内，就将本次攀爬的垂直距离视为 1 英寸。"),
        EquipmentCard("地雷", "1 件 · 陷阱", "必须完全部署在自己的领地内，与其他标识、访问点和可穿越地形至少相距 2 英寸。敌我不分，只要地雷进入某个特工的控制范围就会爆炸，对该特工造成 D3+3 点伤害。此处按用户提供速查表原文转录；触发对象及范围若有争议，请核对装备正式规则。"),
        EquipmentCard("通用手雷", "2 枚 · 自由搭配", "烟雾弹和震撼弹共 2 枚，自由搭配。投掷属于特殊行动，不需要交战命令。\n\n投掷烟雾弹（1AP）：把标识放置在距执行特工 6 英寸内的可见位置（包括制高点）。形成从标识边缘出发水平 1 英寸、垂直无限（不能向标识下方）的烟雾区域。完全在区域内的特工受到 2 英寸外射击时处于被遮挡状态，反之亦然。射击完全位于区域内的敌方特工时，武器的穿刺 2 变为穿刺 1，关键穿刺 2 变为关键穿刺 1。在下一转折点战略阶段的就绪阶段，为上一转折点放置的烟雾标识投 D3，在该数目的特工激活后或本转折点结束时（以先到为准）移除。\n\n投掷震撼弹（1AP）：选择 6 英寸内、对投掷特工可见的敌方特工。为包含该特工在内的 1 英寸内所有敌方特工分别投 D6，3+ 时减少其 1 点 APL，直到其下一次激活结束。"),
        EquipmentCard("爆炸手雷", "2 枚 · 自由搭配", "破片手雷和穿甲手雷共 2 枚，自由搭配。投掷属于射击行动，需要交战命令。\n\n破片手雷：攻击 4，命中 4+，伤害 2/4，范围 6 英寸，爆炸 2 英寸、集中。\n\n穿甲手雷：攻击 4，命中 4+，伤害 4/5，范围 6 英寸，穿刺 1、集中。")
    )
    private fun cards(ui: RecordUi, teamId: String): List<EquipmentCard> {
        ImportedTeamCatalog.equipment[teamId]?.let { return it+universal }
        val faction=ui.context.assets.list("equipment").orEmpty().filter { it.startsWith("$teamId--") }.map { file ->
            EquipmentCard(file.substringAfter("--").substringBeforeLast('.'),"阵营装备 · ${RosterCatalog.team(teamId).name}","完整效果请查看下方原卡，可双指放大。", "equipment/$file")
        }
        return faction+universal
    }
    private fun originalLink(ui: RecordUi, box: LinearLayout, card: EquipmentCard) {
        if(card.asset!=null) box.addView(ui.link("查看原卡 ›") {
            ui.reference(card.name+" · 原卡") { original,_ -> ui.preview(original,card.name,card.asset) }
        },ui.wrap())
    }
    fun selected(ui: RecordUi,box: LinearLayout,teamId: String,saved: String) {
        val names=saved.lines().filter { it.isNotBlank() }
        if(names.isEmpty()) {
            box.addView(ui.text("尚未选择装备。",12f,ui.muted),ui.wrap())
            return
        }
        val available=cards(ui,teamId).associateBy { it.name }
        ui.tiles(box,names) { tile,name ->
            val card=available[name]
            tile.addView(ui.text(name,17f,ui.accent,true),ui.wrap(4))
            if(card==null) {
                tile.addView(ui.text("旧记录中保存的装备；当前规则库没有对应卡片。",12f,ui.muted),ui.wrap())
            } else {
                tile.addView(ui.text(card.count,12f,ui.muted),ui.wrap())
                if(card.asset!=null) ui.preview(tile,card.name,card.asset,expandable=false)
                else tile.addView(ui.text(card.rules),ui.wrap())
            }
        }
    }
    fun picker(ui: RecordUi,teamId: String,saved: String,readOnly: Boolean=false,commit: (String)->Unit) {
        val selected=saved.lines().filter { it.isNotBlank() }.toMutableSet()
        ui.reference("装备 · ${RosterCatalog.team(teamId).name}",
            closeLabel=if(readOnly) "关闭" else "保存并关闭",
            onClose=if(readOnly) null else ({
                val value=selected.joinToString("\n")
                if(value != saved) commit(value)
            })
        ) { box,_ ->
            val count=ui.text("已选 ${selected.size} / $MAX_SELECTIONS 项",14f,ui.accent,true)
            box.addView(ui.row().apply {
                addView(count,ui.weight())
            },ui.wrap())
            box.addView(ui.text("最多选择 4 件装备；选满后请先移除一件，才能选择其他装备。",12f,ui.muted),ui.wrap(12))
            if(!ui.isTablet) box.addView(ui.text("点击装备查看完整效果与原卡。",12f,ui.muted),ui.wrap(12))
            val cards=cards(ui,teamId)
            val equipmentToggles=mutableListOf<Pair<String,android.widget.TextView>>()
            fun refreshEquipmentToggles() {
                equipmentToggles.forEach { (name,button) ->
                    val selectedNow=name in selected
                    val enabled=selectedNow || canAdd(selected.size)
                    button.isEnabled=enabled
                    button.alpha=if(enabled) 1f else .45f
                    button.text=when {
                        selectedNow -> "移除"
                        enabled -> "加入装备"
                        else -> "已达上限"
                    }
                }
            }
            if(cards.none { it.asset!=null }) box.addView(ui.text("暂无此阵营原卡，下方为通用装备。",12f,ui.muted),ui.wrap())
            if(ui.isTablet) {
                cards.groupBy { if(it.asset != null) "阵营装备" else "通用装备" }.forEach { (group, items) ->
                    box.addView(ui.text(group, 17f, ui.accent, true), ui.wrap(12))
                    ui.tiles(box, items) { tile, card ->
                        val title = ui.text("${if(card.name in selected) "✓ " else ""}${card.name}", 17f, ui.accent, true)
                        tile.addView(title, ui.wrap())
                        tile.addView(ui.text(card.count, 12f, ui.muted), ui.wrap())
                        if(!readOnly) {
                            val toggle = ui.button(if(card.name in selected) "移除" else "加入装备") {}
                            toggle.setOnClickListener {
                                if(card.name in selected) selected.remove(card.name)
                                else if(canAdd(selected.size)) selected.add(card.name)
                                title.text = "${if(card.name in selected) "✓ " else ""}${card.name}"
                                count.text = "已选 ${selected.size} / $MAX_SELECTIONS 项"
                                refreshEquipmentToggles()
                            }
                            equipmentToggles += card.name to toggle
                            tile.addView(toggle, ui.wrap())
                        }
                        if(card.currentText) {
                            tile.addView(ui.text(card.rules),ui.wrap())
                            originalLink(ui,tile,card)
                        } else if(card.asset != null) ui.preview(tile, card.name, card.asset)
                        else tile.addView(ui.text(card.rules), ui.wrap())
                    }
                }
                refreshEquipmentToggles()
                box.addView(ui.link("查看通用装备原卡 ›") {
                    ui.reference("通用装备原卡") { original, _ -> ui.preview(original, "通用装备原卡", "equipment-reference-1.jpg") }
                }, ui.wrap())
            } else {
            var previousGroup=""
            cards.forEach { card ->
                val group=if(card.asset!=null) "FACTION / 阵营装备" else "UNIVERSAL / 通用装备"
                if(group!=previousGroup) { box.addView(ui.text(group,12f,ui.accent,true),ui.wrap(10));previousGroup=group }
                val title=ui.text("${if(card.name in selected) "✓ " else ""}${card.name}",16f,ui.ink,true)
                val tile=ui.row().apply {
                    minimumHeight=ui.dp(68);setPadding(0,ui.dp(6),0,ui.dp(6))
                    if(card.asset!=null) addView(ImageView(ui.context).apply {
                        ui.context.assets.open(card.asset).use { setImageBitmap(BitmapFactory.decodeStream(it)) }
                        scaleType=ImageView.ScaleType.CENTER_CROP
                        importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },LinearLayout.LayoutParams(ui.dp(48),ui.dp(58)).apply { marginEnd=ui.dp(12) })
                    addView(ui.column().apply {
                        addView(title,ui.wrap(4));addView(ui.text(card.count,11f,ui.muted))
                    },ui.weight())
                    addView(ui.text("›",22f,ui.accent))
                    contentDescription="查看装备卡：${card.name}"
                }
                    tile.setOnClickListener {
                        ui.dialog(card.name) { details,detail ->
                            val canSelect=card.name in selected || canAdd(selected.size)
                            if(!readOnly && !canSelect) {
                                details.addView(ui.text("已选择 4 件装备。请先移除一件，才能选择此装备。",12f,ui.accent,true),ui.wrap(12))
                            }
                            details.addView(ui.row().apply {
                                addView(ui.text(card.count,12f,ui.accent),ui.weight())
                                if(!readOnly) addView(ui.button(if(!canSelect) "已达上限" else if(card.name in selected) "移除" else "加入装备",true) {
                                    if(card.name in selected) selected.remove(card.name)
                                    else if(canAdd(selected.size)) selected.add(card.name)
                                    else return@button
                                    title.text="${if(card.name in selected) "✓ " else ""}${card.name}"
                                    count.text="已选 ${selected.size} / $MAX_SELECTIONS 项";detail.dismiss()
                                }.apply { isEnabled=canSelect;alpha=if(canSelect) 1f else .45f })
                            },ui.wrap(10))
                        if(card.currentText) {
                            details.addView(ui.text(card.rules),ui.wrap())
                            originalLink(ui,details,card)
                        } else if(card.asset!=null) {
                            details.addView(ui.text("双指缩放 · 拖动查看 · 双击复位",11f,ui.muted),ui.wrap())
                            details.addView(ZoomReference(ui.context,card.asset),LinearLayout.LayoutParams(-1,ui.dp(380)))
                        } else {
                            details.addView(ui.text(card.rules),ui.wrap())
                            details.addView(ui.link("查看 PDF 完整原页  ›") {
                                ui.dialog("通用装备 · PDF 第 1 页") { original,_ ->
                                    original.addView(ZoomReference(ui.context,"equipment-reference-1.jpg"),LinearLayout.LayoutParams(-1,ui.dp(420)))
                                }
                            })
                        }
                    }
                }
                box.addView(tile);box.addView(ui.divider())
            }
            }
            selected.filter { name->cards.none { it.name==name } }.forEach { note ->
                box.addView(ui.link("旧记录：$note · ${if(readOnly) "已保存" else "移除"}") {
                    if(!readOnly) { selected.remove(note);count.text="已选 ${selected.size} / $MAX_SELECTIONS 项" }
                },ui.wrap())
            }
        }
    }
}
