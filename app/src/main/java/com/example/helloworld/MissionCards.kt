package com.example.helloworld

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

data class MissionCard(val name: String, val category: String, val reveal: String, val rule: String, val scoring: String) {
    val displayCategory: String
        get() = category
            .replace("关键目标", "关键行动")
            .replace("战术目标", "战术行动")
            .replace("安全防护", TeamArchetypes.SECURITY)
            .replace("侦查", TeamArchetypes.RECON)

    val archetype: String?
        get() = if (category.contains("战术")) {
            category.substringBefore(" · ")
                .replace("安全防护", TeamArchetypes.SECURITY)
                .replace("侦查", TeamArchetypes.RECON)
        } else null
}

/** Transcribed from the user's community reference, not a replacement for official errata. */
object MissionCards {
    const val COMMON = "单个任务最多 6VP。控制目标：1 英寸内且可见，不必完全位于范围内；比较双方特工的 APL 总和。处于敌方控制范围内的特工不能执行任务动作。通常从第 2 转折点开始任务动作和计分，具体卡片优先。争夺与控制不同：范围内有特工即可能争夺，APL 较低的一方也仍在争夺。"
    val all = listOf(
        MissionCard("占领", "关键目标 2025", "", "占领（1AP）：对被己方控制的目标标识执行。占领后可离开；目标可被对手重新占领。", "每占领 1 个目标，获得 1VP；友方控制目标数大于对手，获得 1VP。"),
        MissionCard("掠夺", "关键目标 2025", "", "掠夺（1AP）：目标被己方控制，且本转折点未被掠夺。", "每掠夺 1 个目标标识，获得 1VP；每转折点最多 2VP。"),
        MissionCard("传输情报", "关键目标 2025", "", "传输情报（1AP）：对被己方控制的目标标识执行，传输效果持续到本转折点结束。", "控制任意有效传输目标，获得 1VP；控制有效目标数大于对手，获得 1VP。"),
        MissionCard("宝球", "关键目标 2025", "", "中央目标标识拥有宝球。踢球（1AP）：控制有宝球的目标时执行。若宝球在中央，将其移至任一玩家的目标；若在玩家目标，将其移回中央。", "己方每控制一个没有宝球的目标标识，获得 1VP。"),
        MissionCard("宣告主权", "关键目标 2025", "", "第 2 及后续转折点的战略阶段：先手方开始，每位玩家选择 1 个目标，并声明本回合结束时由己方控制，或未被争夺。每位玩家对每个目标只能选择 1 次。原图备注：第二条英文版为“争夺”，中文版为“控制”，请与对手确认采用版本。", "完成声明，获得 1VP；友方控制目标数大于对手，获得 1VP。"),
        MissionCard("能量电池", "关键目标 2025", "", "第 2 及后续转折点，可对控制的目标执行拾取标识。第 2 回合额外消耗 2AP，第 3 回合额外消耗 1AP，第 4 回合无额外消耗。第 2、3 回合拾取的 AP 不能免费也不能减少。携带电池时，不能移动并重新部署到 6 英寸外（不能飞，只能走）。", "友方控制目标数大于对手，获得 1VP；战斗结束时，友方每携带 1 个电池，获得 1VP。"),
        MissionCard("下载", "关键目标 2025", "", "第 3 及后续转折点开始，可对中央或对手的目标执行下载（1AP）：目标由己方控制，且尚未下载。", "友方控制未下载的目标数大于对手，获得 1VP；第 3 回合每次下载获得 1VP，第 4 回合每次下载获得 2VP。"),
        MissionCard("数据", "关键目标 2025", "", "编译数据（1AP）：对当前转折点未被编译且由己方控制的目标执行，增加 1 点数据。传输数据（1AP）：第 4 回合开始可执行，移除目标上的全部数据。", "第 2、3 转折点结束，友方执行编译数据次数大于对手，获得 1VP；传输数据时，每点数据获得 1VP。"),
        MissionCard("重启", "关键目标 2025", "", "第 2 及后续战略计划时，双方秘密选择 1 个对应目标标识的数字，同时揭示。若相同，该目标变为钝化；若不同，没被选择的数字所对应目标变为钝化。重启动作（2AP）：重新激活钝化目标。", "每控制 1 个目标标识，获得 1VP；钝化目标不计分。"),
        MissionCard("侧翼", "侦查 · 战术目标 2026", "作为战略计划公布。", "从降落区底面 1 条线象限，将杀戮区从中间分为两个侧翼。完全位于一个侧翼内，且同时完全位于对手领地内的特工争夺该侧翼；比较这些特工的 APL 总和，较大者控制。", "每个被己方控制的侧翼获得 1VP。第 4 转折点结算时，若己方在第 3 转折点结束也控制了同一侧翼，则该侧翼获得 2VP。每转折点最多 2VP。"),
        MissionCard("插旗", "安全防护 · 战术目标 2026", "友方首次执行任务时。", "旗帜须位于中立杀戮区边缘 5 英寸外（地图左右两侧边缘），且完全位于对手领地内。插旗（1AP）：放置旗帜标识。旗帜可被敌方拾取；只能插旗一次。", "旗帜完全位于敌方领地且被己方控制，获得 1VP；若没有敌方特工争夺旗帜，再获得 1VP。携带旗帜不计分，需放置。"),
        MissionCard("主宰", "搜索与摧毁 · 战术目标 2026", "敌方首次被己方击杀时。", "每当己方特工击倒敌方特工，该己方特工获得 1 个主宰标记。", "在第 3、4 转折点结束，可移除未被击杀的己方特工身上的主宰标记。每移除 1 个获得 1VP；每转折点最多 3VP。"),
        MissionCard("放置装置", "渗透 · 战术目标 2026", "友方特工首次执行任务时。", "放置装置（1AP）：控制一个目标标识时，对其放置装置；不能对已有装置的目标执行。", "敌方目标有装置，获得 1VP；每有 1 个敌方争夺的其他目标（中场的）有装置，获得 1VP。每转折点最多 2VP。"),
        MissionCard("回收", "侦查 · 战术目标 2026", "首次通过此目标得分时。", "搜索（1AP）：控制尚未被己方搜索过的目标时执行，立即获得 1 个回收标记。己方特工可对己方回收标记执行拾取标识。第 1 转折点或已携带 1 枚标记时不能执行搜索。", "每次搜索获得 1VP；战斗结束时，己方每携带 1 个回收标记，获得 1VP。"),
        MissionCard("殉道者", "安全防护 · 战术目标 2026", "首次获得殉道者标记时。", "己方特工在争夺目标标识时被残废，该目标获得 1 个己方殉道者标记。每个特工只在第一次残废时产生标记，复活后不再产生。", "在己方争夺的目标上移除殉道者标记，每个获得 1VP；若己方同时控制该目标，则获得 2VP。每转折点最多 2VP。"),
        MissionCard("扫荡清理", "搜索与摧毁 · 战术目标 2026", "产生扫荡标记或执行清理时。", "敌方特工在争夺目标时被击杀，该目标获得 1 个己方扫荡标记（若尚未拥有），至下一战略阶段。清理（1AP）：对控制的目标标识执行。", "控制带有扫荡标记的目标，获得 1VP；若该目标又被清理，则获得 2VP。每转折点最多 2VP。"),
        MissionCard("追踪敌军", "渗透 · 战术目标 2026", "首次通过此目标得分时。", "敌方特工满足全部条件即被追踪：不在控制范围内；在隐蔽的友方特工 6 英寸内；是该友方特工的有效目标；该友方特工不是被追踪敌方的有效目标。请对照原图并与对手确认可见性、有效目标判定。", "有 1 名敌方被追踪，获得 1VP，第 4 转折点则获得 2VP；有 2 名及以上敌方被追踪，获得 2VP。每转折点最多 2VP。"),
        MissionCard("敌情", "侦查 · 战术目标 2026", "友方特工首次执行任务时。", "选择 1 名可见、距离活跃特工 6 英寸外的敌方就绪特工（即使争控判类的不算活跃特工）；不在交战状态及被控制范围内。执行侦查（1AP），使该敌方特工受到监控。原图个别措辞较简略，争议请以任务原卡为准。", "转折点结束，每有 1 名受到己方监控且对任意己方可见的敌方特工，获得 1VP；每转折点最多 2VP。"),
        MissionCard("特使", "安全防护 · 战术目标 2026", "首次选择特使时。", "作为战略计划，选择 1 名特工作为特使。不能重复选择上一转折点选择的特工，也不能选择在计分条件中无效的特工。", "特使完全位于敌方领地内且不在敌方控制范围内，获得 1VP；若该特使在本转折点未受到任何伤害，则获得 2VP。"),
        MissionCard("击溃", "搜索与摧毁 · 战术目标 2026", "首次通过此目标得分时。", "此任务没有额外任务动作。", "己方在敌方降落区 6 英寸内击杀敌方特工，获得 1VP；若击杀特工耐伤 ≥12，则获得 2VP。每转折点最多 2VP。"),
        MissionCard("窃取情报", "渗透 · 战术目标 2026", "敌方首次被己方击杀时。", "每当敌方被击杀，在其控制范围内放置 1 个己方情报标记。友方可拾取此标记；每名特工最多携带 2 个情报标记，或 1 个情报标记加 1 个其他标记。", "若有任意友方特工携带己方情报标记，获得 1VP；战斗结束时，友方携带的每个情报标记获得 1VP。")
    )
    fun tacticalForTeam(teamId: String): List<MissionCard> = all.filter { card ->
        card.archetype?.let { TeamArchetypes.supports(teamId, it) } == true
    }

    fun inlineTacticalChoices(
        ui: RecordUi,
        box: LinearLayout,
        selected: String,
        teamId: String,
        choose: (MissionCard) -> Unit
    ) {
        val archetypes = TeamArchetypes.forTeam(teamId)
        val cards = tacticalForTeam(teamId)
        box.addView(
            ui.text(
                "该小队有 ${archetypes.size} 个原型，每个原型各 3 张战术行动，共 ${cards.size} 张；阅读完整内容后直接选择。",
                12f,
                ui.muted
            ),
            ui.wrap(12)
        )

        if (!ui.isTablet) {
            archetypes.forEach { archetype ->
                val group = cards.filter { it.archetype == archetype }
                box.addView(ui.text("$archetype · ${group.size} 张", 17f, ui.accent, true), ui.wrap(8))
                group.forEach { card ->
                    box.addView(ui.card().apply {
                        addView(
                            ui.text(
                                "${if (selected == card.name) "✓ " else ""}${card.name}",
                                18f,
                                ui.accent,
                                true
                            ),
                            ui.wrap(4)
                        )
                        addView(ui.text(card.displayCategory, 12f, ui.muted, true), ui.wrap(8))
                        addView(ui.button("查看细则并选择 ›") {
                            detail(ui, card) { choose(card) }
                        }, ui.wrap(4))
                    }, ui.wrap(10))
                }
            }
            return
        }

        archetypes.forEach { archetype ->
            val group = cards.filter { it.archetype == archetype }
            box.addView(ui.text("$archetype · ${group.size} 张", 17f, ui.accent, true), ui.wrap(8))
            group.forEach { card ->
                box.addView(ui.card().apply {
                    addView(
                        ui.text("${if (selected == card.name) "✓ " else ""}${card.name}", 19f, ui.accent, true),
                        ui.wrap(4)
                    )
                    addView(ui.text(card.displayCategory, 12f, ui.muted, true), ui.wrap(10))
                    if (card.reveal.isNotBlank()) {
                        addView(ui.text("公开时机\n${card.reveal}", 14f), ui.wrap(12))
                    }
                    addView(ui.text("行动规则 / 任务行动\n${card.rule}", 14f), ui.wrap(12))
                    addView(ui.text("VP 结算\n${card.scoring}", 14f), ui.wrap(12))
                    addView(
                        ui.button(
                            if (selected == card.name) "已选择「${card.name}」" else "选择「${card.name}」",
                            true
                        ) { choose(card) },
                        ui.wrap(4)
                    )
                }, ui.wrap(12))
            }
        }

        box.addView(
            ui.text("通用说明\n${COMMON.replaceFirst("单个任务", "单个行动")}", 12f, ui.muted),
            ui.wrap(12)
        )
        box.addView(
            ui.text("来源：风来西行动速查 vol.1.29；非官方原卡，歧义请查原卡。", 11f, ui.muted),
            ui.wrap()
        )
    }

    fun picker(
        ui: RecordUi,
        tactical: Boolean,
        selected: String,
        teamId: String? = null,
        readOnly: Boolean = false,
        choose: (MissionCard) -> Unit
    ) {
        ui.reference(if (tactical) "战术行动" else "关键行动") { box, sheet ->
            val teamArchetypes = teamId?.let(TeamArchetypes::forTeam).orEmpty()
            val cards = all.filter { card ->
                card.category.contains("战术") == tactical &&
                    (!tactical || teamId == null || TeamArchetypes.supports(teamId, card.archetype))
            }
            if (tactical && teamId != null) {
                val team = RosterCatalog.team(teamId)
                box.addView(ui.text("${team.name} · 小队原型", 18f, ui.accent, true), ui.wrap(4))
                box.addView(ui.text(teamArchetypes.joinToString("  ·  "), 16f, bold = true), ui.wrap(8))
                box.addView(
                    ui.text("下方仅显示与本小队原型相符的战术行动。", 12f, ui.muted),
                    ui.wrap(16)
                )
            }
            if(ui.isTablet) {
                ui.tiles(box, cards) { tile, card ->
                    tile.addView(ui.text("${if(selected == card.name) "✓ " else ""}${card.name}", 18f, ui.accent, true), ui.wrap())
                    tile.addView(ui.text(card.displayCategory, 12f, ui.muted, true), ui.wrap())
                    if(card.reveal.isNotBlank()) tile.addView(ui.text("公开时机\n${card.reveal}"), ui.wrap())
                    tile.addView(ui.text(card.rule), ui.wrap())
                    tile.addView(ui.text("VP 结算", 14f, ui.accent, true), ui.wrap())
                    tile.addView(ui.text(card.scoring), ui.wrap())
                    if(!readOnly) tile.addView(ui.button(if(selected == card.name) "已选择" else "选择「${card.name}」", true) {
                        choose(card); sheet.dismiss()
                    }, ui.wrap())
                }
                box.addView(ui.text("通用说明\n${COMMON.replaceFirst("单个任务", "单个行动")}", 12f, ui.muted), ui.wrap())
                box.addView(ui.text("来源：风来西行动速查 vol.1.29；非官方原卡，歧义请查原卡。", 11f, ui.muted), ui.wrap())
            } else {
                cards.forEach { card ->
                    box.addView(ui.card().apply {
                        addView(ui.text("${if (selected == card.name) "✓ " else ""}${card.name}", 17f, ui.accent, true), ui.wrap(4))
                        addView(ui.text(card.displayCategory, 12f, ui.muted, true), ui.wrap(8))
                        addView(ui.button(if(readOnly) "查看详情 ›" else "查看并选择 ›") {
                            if (readOnly) detail(ui, card) else detail(ui, card) { choose(card); sheet.dismiss() }
                        }, ui.wrap())
                    }, ui.wrap(8))
                }
            }
            box.addView(ui.button("查看完整行动流程速查图") { original(ui) })
        }
    }
    fun detail(ui: RecordUi, card: MissionCard, choose: (() -> Unit)? = null) {
        ui.reference(card.name) { box, sheet ->
            contents(ui,box,card)
            if (choose != null) box.addView(ui.button("选择「${card.name}」", true) { choose(); sheet.dismiss() })
        }
    }
    fun cardDetails(ui: RecordUi, box: LinearLayout, card: MissionCard) {
        box.addView(ui.text(card.displayCategory, 16f, ui.accent, true), ui.wrap())
        if (card.reveal.isNotBlank()) box.addView(ui.text("公开时机\n${card.reveal}"), ui.wrap(16))
        box.addView(ui.text("行动规则 / 任务行动\n${card.rule}"), ui.wrap(16))
        box.addView(ui.text("VP 结算\n${card.scoring}"), ui.wrap(16))
    }

    fun contents(ui: RecordUi, box: LinearLayout, card: MissionCard) {
        cardDetails(ui,box,card)
        box.addView(ui.text("通用说明\n${COMMON.replaceFirst("单个任务", "单个行动")}", 12f, ui.muted), ui.wrap(16))
        box.addView(ui.text("来源：用户提供的风来西行动速查 vol.1.29；关键行动为 2025，战术行动为 2026。非官方原卡，歧义请查原卡。", 11f, ui.muted), ui.wrap())
        box.addView(ui.button("查看完整行动流程速查图 · 可缩放") { original(ui) }, ui.wrap())
    }
    /** Push the reference image inside RecordUi's single window instead of opening a second layer. */
    fun original(ui: RecordUi) {
        ui.reference("行动流程速查图") { box, _ ->
            box.addView(ui.text("双指缩放 · 拖动查看 · 双击复位", 12f, ui.muted), ui.wrap())
            box.addView(
                ZoomReference(ui.context),
                LinearLayout.LayoutParams(-1, (ui.context.resources.displayMetrics.heightPixels * .72f).toInt())
            )
        }
    }
    fun original(context: Context) {
        val activity=context as? Activity ?: return
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        val overlayTag="mission_reference_overlay"
        root.findViewWithTag<View>(overlayTag)?.let(root::removeView)
        val density=context.resources.displayMetrics.density
        fun dp(value: Int)=(value*density).toInt()

        lateinit var overlay: FrameLayout
        fun close() { root.removeView(overlay) }
        overlay=FrameLayout(context).apply {
            tag=overlayTag
            setBackgroundColor(android.graphics.Color.rgb(15,15,15))
            isClickable=true
            isFocusableInTouchMode=true
            elevation=dp(24).toFloat()
            setOnKeyListener { _,key,event ->
                if(key==KeyEvent.KEYCODE_BACK && event.action==KeyEvent.ACTION_UP) {
                    close();true
                } else false
            }
        }
        overlay.addView(ZoomReference(context),FrameLayout.LayoutParams(-1,-1))
        overlay.addView(TextView(context).apply {
            text="×"
            textSize=27f
            gravity=Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(238,117,64))
            contentDescription="关闭行动流程速查"
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x44ee7540),null,null
            )
            setOnClickListener { close() }
        },FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.END).apply {
            topMargin=dp(6)
            marginEnd=dp(6)
        })
        root.addView(overlay,ViewGroup.LayoutParams(-1,-1))
        overlay.requestFocus()
    }
}

class ZoomReference(context: Context, asset: String? = "missions-2026-reference-v2.png", resource: Int? = null) : androidx.appcompat.widget.AppCompatImageView(context) {
    private val transform = Matrix()
    private var lastX = 0f; private var lastY = 0f; private var tapTime = 0L
    private var factor = 1f
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val next = (factor * d.scaleFactor).coerceIn(1f, 8f)
            transform.postScale(next / factor, next / factor, d.focusX, d.focusY)
            factor = next; imageMatrix = transform; return true
        }
    })
    init {
        scaleType = ImageView.ScaleType.MATRIX
        if (resource != null) setImageBitmap(BitmapFactory.decodeResource(resources, resource))
        else context.assets.open(requireNotNull(asset)).use { setImageBitmap(BitmapFactory.decodeStream(it)) }
        contentDescription = "行动流程速查完整图，支持双指缩放"
    }
    private fun reset() {
        val d = drawable ?: return
        val s = minOf(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        transform.setScale(s, s); transform.postTranslate((width-d.intrinsicWidth*s)/2, (height-d.intrinsicHeight*s)/2)
        factor = 1f; imageMatrix = transform
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w,h,oldw,oldh); reset() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true); detector.onTouchEvent(event)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { if (event.eventTime-tapTime < 300) reset(); tapTime=event.eventTime; lastX=event.x; lastY=event.y }
            MotionEvent.ACTION_MOVE -> { if (!detector.isInProgress && event.pointerCount == 1) { transform.postTranslate(event.x-lastX,event.y-lastY); imageMatrix=transform }; lastX=event.x;lastY=event.y }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
