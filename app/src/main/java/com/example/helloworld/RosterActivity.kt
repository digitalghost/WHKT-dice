package com.example.helloworld

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.util.UUID

/**
 * Roster editor. The team being built comes first; operatives are added from a sheet.
 * Every change is saved immediately, so there is no save button and no unsaved-changes prompt.
 */
class RosterActivity : AppCompatActivity() {
    private var pendingAvatarResult: ((String) -> Unit)? = null
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null
    private val importPhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingAvatarResult?.invoke(uri.toString())
        }
        pendingAvatarResult = null
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) pendingAvatarResult?.invoke(uri.toString()) else pendingCameraFile?.delete()
        pendingAvatarResult = null
        pendingCameraUri = null
        pendingCameraFile = null
    }

    private fun choosePhoto(onChosen: (String) -> Unit) {
        pendingAvatarResult = onChosen
        importPhoto.launch(arrayOf("image/*"))
    }

    private fun capturePhoto(onCaptured: (String) -> Unit) {
        val directory = File(filesDir, "operative_photos").apply { mkdirs() }
        val output = File(directory, "operative-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", output)
        pendingAvatarResult = onCaptured
        pendingCameraFile = output
        pendingCameraUri = uri
        takePhoto.launch(uri)
    }

    private lateinit var ui: RecordUi
    private lateinit var kit: CombatKit
    private lateinit var store: RosterStore
    private lateinit var team: KillTeamCatalog
    private lateinit var roster: SavedRoster
    private lateinit var page: CombatKit.Page

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = RecordUi(this)
        kit = CombatKit(ui)
        store = RosterStore(this)
        val requested = (savedInstanceState?.getString(EXTRA_ROSTER_ID) ?: intent.getStringExtra(EXTRA_ROSTER_ID))?.let(store::load)
        team = RosterCatalog.team(requested?.teamId ?: intent.getStringExtra(EXTRA_CREATE_TEAM_ID) ?: store.selectedTeamId())
        roster = requested ?: store.createDraft(team)
        store.setSelectedTeam(team.id)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (roster.updatedAt > 0L) outState.putString(EXTRA_ROSTER_ID, roster.rosterId)
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    /** Autosave. An empty new draft is never written, so backing out leaves nothing behind. */
    private fun persist() {
        if (roster.members.isEmpty() && roster.updatedAt == 0L) return
        roster = store.save(roster)
    }

    private fun render() {
        val previousY = if (::page.isInitialized) page.scroll.scrollY else 0
        val top = kit.topBar(roster.name, "${team.name} · ${roster.members.size} 名成员", null, ::finish, {},
            trailing = kit.iconButton("✎", "改名") { rename() }.apply {
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(ui.dp(48), ui.dp(48))
            })
        page = kit.page(this, top)
        if (isTablet) {
            page.body.addView(ui.row().apply {
                gravity = Gravity.TOP
                addView(ui.column().apply { buildMembers(this) }, LinearLayout.LayoutParams(0, -2, 1.1f).apply { marginEnd = ui.dp(16) })
                addView(ui.column().apply { buildCatalog(this) }, LinearLayout.LayoutParams(0, -2, 1f))
            })
        } else {
            buildMembers(page.body)
            page.bar.addView(kit.primaryButton("＋ 添加成员") { catalogSheet() })
        }
        kit.finishBar(page)
        page.scroll.post { page.scroll.scrollTo(0, previousY) }
    }

    private fun buildMembers(box: LinearLayout) {
        box.addView(ui.row().apply {
            addView(kit.emblem(team.id, 40))
            addView(ui.column().apply {
                addView(ui.text(team.name, 16f, ui.ink, true), ui.wrap(2))
                addView(ui.text(TeamArchetypes.forTeam(team.id).joinToString(" · "), 12f, ui.muted, true), ui.wrap(0))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(kit.tag("小队规则", ui.accent) { BattleReference(ui).faction(team.id, includeComposition = false) })
        }, ui.wrap(10))
        box.addView(compositionCard(), ui.wrap(14))
        box.addView(kit.section("成员", ui.text("${roster.members.size} 名", 13f, ui.accent, true)), ui.wrap(6))
        if (roster.members.isEmpty()) {
            box.addView(ui.text(if (isTablet) "从右侧选择成员加入" else "点下方「添加成员」", 14f, ui.muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(28), 0, ui.dp(28))
            }, ui.wrap())
        }
        roster.members.forEachIndexed { index, member ->
            val operative = RosterCatalog.operative(team, member.operativeId) ?: return@forEachIndexed
            box.addView(memberRow(index, member, operative), ui.wrap(8))
        }
    }

    /** Composition rule: summary plus the original cards and a one-tap canonical roster preset. */
    private fun compositionCard(): View = ui.column().apply {
        setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
        background = ui.bg(0xff191919.toInt(), 0xff343432.toInt())
        addView(ui.row().apply {
            addView(ui.text("编成要求", 13f, ui.accent, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(kit.tag("一键标准编成", ui.accent) {
                val canonical = CanonicalRosters.forTeam(team.id)
                roster.members.clear()
                roster.members.addAll(canonical.members.map { it.copy(id = UUID.randomUUID().toString()) })
                persist(); render()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) }
            })
            val cards = compositionCards()
            if (cards.isNotEmpty()) addView(kit.tag("原卡", ui.muted) { showComposition(cards) })
        }, ui.wrap(4))
        addView(ui.text(RosterComposition.hint(team.id), 13f, ui.ink), ui.wrap(0))
    }

    private fun compositionCards() = CardAssets.list(this, "team_rules")
        .filter { it.startsWith("${team.id}--") && it.contains("-小队选择-") }.sorted()

    private fun showComposition(cards: List<String>) {
        ui.reference("${team.name} · 编成规则") { box, _ ->
            cards.forEach { file ->
                val title = file.substringAfter("-小队选择-").removeSuffix(".png")
                box.addView(ui.text(title, 15f, ui.accent, true), ui.wrap(4))
                ui.preview(box, title, "team_rules/$file")
            }
        }
    }

    private fun memberRow(index: Int, member: RosterMember, operative: OperativeTemplate): View {
        val weapons = operative.weapons.filter { operative.matchesWeaponId(it.id, member.weaponIds) }
        val noWeaponsAvailable = operative.weapons.isEmpty()
        val extra = ui.column().apply {
            addView(kit.stats("APL" to "${operative.apl}", "移动" to operative.move, "豁免" to operative.save, "耐伤" to "${operative.wounds}"), ui.wrap(4))
            addView(ui.text(when {
                noWeaponsAvailable -> "无武器（支援/设备单位）"
                weapons.isEmpty() -> "未选择武器"
                else -> weapons.joinToString(" · ") { it.name }
            }, 13f, if (weapons.isEmpty() && !noWeaponsAvailable) ui.orange else if (noWeaponsAvailable) ui.muted else ui.ink, true).apply { maxLines = 2 }, ui.wrap(0))
        }
        return kit.listRow(
            title = member.callsign.ifBlank { operative.name },
            subtitle = if (member.callsign.isBlank()) operative.role else "${operative.name} · ${operative.role}",
            leading = ui.memberPortrait(member.customAvatarUri, operative.cardRes, 56, "${operative.name} · 棋子照片", fallbackAsset = operative.primaryCardAsset),
            trailing = kit.iconButton("⋯", "更多：${member.callsign.ifBlank { operative.name }}") { memberMenu(index, member, operative) }.apply {
                layoutParams = LinearLayout.LayoutParams(ui.dp(44), ui.dp(48))
            },
            onClick = { memberEditor(operative, member) },
            extra = extra
        )
    }

    private fun memberMenu(index: Int, member: RosterMember, operative: OperativeTemplate) {
        val name = member.callsign.ifBlank { operative.name }
        val cardCount = operative.cardSources().size
        val cardActionTitle = if (cardCount > 1) "人物卡（${cardCount} 张）" else "人物卡"
        kit.actionSheet(name, null, listOf(
            CombatKit.SheetAction("编辑", primary = true) { memberEditor(operative, member) },
            CombatKit.SheetAction(cardActionTitle) { showCard(operative) },
            CombatKit.SheetAction("复制一名") {
                roster.members.add(index + 1, member.copy(id = UUID.randomUUID().toString()))
                persist(); render()
            },
            CombatKit.SheetAction("移出小队") {
                roster.members.removeAll { it.id == member.id }
                persist(); render()
                Snackbar.make(page.root, "已移出 $name", Snackbar.LENGTH_LONG).setAnchorView(page.bar).setAction("撤回") {
                    roster.members.add(index.coerceAtMost(roster.members.size), member)
                    persist(); render()
                }.show()
            }
        ))
    }

    // ---------------------------------------------------------------- catalog

    private fun buildCatalog(box: LinearLayout) {
        box.addView(kit.section("可选成员", ui.text("${team.operatives.size} 种", 13f, ui.muted, true)), ui.wrap(6))
        team.operatives.forEach { operative -> box.addView(catalogRow(operative), ui.wrap(8)) }
        box.addView(ui.divider())
        box.addView(ui.link("从其他小队征用特工（仅限规则允许，例如审判庭征用命令）›") { borrowTeamSheet() }, ui.wrap())
    }

    private fun catalogRow(operative: OperativeTemplate, onPicked: (() -> Unit)? = null): View {
        val count = roster.members.count { it.operativeId == operative.id }
        return kit.listRow(
            title = operative.name,
            subtitle = operative.role + (RosterCatalog.teamOf(operative.id)?.takeIf { it.id != team.id }?.let { " · 征用自${it.name}" } ?: "") +
                if (count > 0) " · 已有 $count" else "",
            leading = ui.memberPortrait(null, operative.cardRes, 48, operative.name, fallbackAsset = operative.primaryCardAsset),
            trailing = ui.text("＋", 24f, ui.accent, true).apply { setPadding(ui.dp(8), 0, ui.dp(8), 0) },
            titleColor = if (count > 0) ui.accent else ui.ink,
            onClick = { onPicked?.invoke(); memberEditor(operative, null) },
            extra = kit.stats("APL" to "${operative.apl}", "移动" to operative.move, "豁免" to operative.save, "耐伤" to "${operative.wounds}")
        )
    }

    private fun catalogSheet() {
        ui.dialog("添加成员 · ${team.name}", expanded = true) { box, sheet ->
            box.addView(ui.text(RosterComposition.hint(team.id), 13f, ui.muted), ui.wrap(10))
            team.operatives.forEach { operative -> box.addView(catalogRow(operative) { sheet.dismiss() }, ui.wrap(8)) }
            box.addView(ui.divider())
            box.addView(ui.link("从其他小队征用特工（仅限规则允许，例如审判庭征用命令）›") {
                sheet.dismiss()
                android.os.Handler(android.os.Looper.getMainLooper()).post { borrowTeamSheet() }
            }, ui.wrap())
        }
    }

    /** Rules that let a kill team include operatives from another team's catalogue. */
    private fun borrowTeamSheet() {
        kit.actionSheet("从哪支小队征用", ui.text("征用的特工使用原小队的数据卡与武器；原小队的计谋和规则是否可用，以本队规则为准。", 12f, ui.muted),
            RosterCatalog.teams.filter { it.id != team.id }.map { other ->
                CombatKit.SheetAction("${other.name} · ${other.operatives.size} 种特工") {
                    ui.dialog("征用 · ${other.name}", expanded = true) { box, sheet ->
                        other.operatives.forEach { operative -> box.addView(catalogRow(operative) { sheet.dismiss() }, ui.wrap(8)) }
                    }
                }
            })
    }

    // ---------------------------------------------------------------- member editor

    private fun memberEditor(operative: OperativeTemplate, existing: RosterMember?) {
        val defaultSelection = when {
            existing != null -> existing.weaponIds.map { wid ->
                operative.weapons.firstOrNull { operative.matchesWeaponId(it.id, listOf(wid)) }?.id ?: wid
            }
            operative.id == "nec_can_tc" && roster.members.any { it.operativeId == "nec_can_tc" && "nec_can_tc_tdi_01" in it.weaponIds } ->
                listOf("nec_can_tc_tgr_01", "nec_can_tc_tgr_02", "nec_can_tc_c_01")
            operative.id == "elucidian_starstriders_07" &&
                roster.members.count { it.operativeId == "elucidian_starstriders_07" } == 3 &&
                roster.members.none { it.operativeId == "elucidian_starstriders_07" && "elucidian_starstriders_07_w02" in it.weaponIds } ->
                listOf("elucidian_starstriders_07_w02", "elucidian_starstriders_07_w03", "elucidian_starstriders_07_w04")
            else -> operative.defaultWeaponIds
        }
        val selected = defaultSelection.toMutableSet()
        var avatarUri = existing?.customAvatarUri
        ui.dialog(if (existing == null) "加入 · ${operative.name}" else operative.name, expanded = true) { box, sheet ->
            box.addView(ui.row().apply {
                addView(ui.memberPortrait(null, operative.cardRes, 72, operative.name, fallbackAsset = operative.primaryCardAsset).apply {
                    setOnClickListener { showCard(operative) }
                    contentDescription = "查看${operative.name}人物卡"
                })
                addView(ui.column().apply {
                    addView(ui.text(operative.name, 18f, ui.ink, true), ui.wrap(2))
                    addView(ui.text(operative.role, 13f, ui.accent, true), ui.wrap(6))
                    addView(kit.stats("APL" to "${operative.apl}", "移动" to operative.move, "豁免" to operative.save, "耐伤" to "${operative.wounds}"))
                    val meta = buildList {
                        operative.baseSizeMm?.let { add("底座 ${it}mm") }
                        addAll(operative.keywords)
                    }
                    if (meta.isNotEmpty()) {
                        addView(ui.text(meta.joinToString(" · "), 11f, ui.muted), ui.wrap(4))
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }, ui.wrap(6))
            val cardCount = operative.cardSources().size
            val cardLinkText = if (cardCount > 1) "查看人物卡（共 ${cardCount} 张）›" else "查看人物卡 ›"
            box.addView(ui.link(cardLinkText) { showCard(operative) }, ui.wrap(4))
            box.addView(ui.link("查看成员规则 · 能力与关键字 ›") {
                BattleReference(ui).member(team.id, operative.id, operative.name)
            }, ui.wrap(12))

            box.addView(kit.section("武器"), ui.wrap(4))
            val weaponRows = ui.column()
            fun renderWeapons() {
                weaponRows.removeAllViews()
                if (operative.weapons.isEmpty()) {
                    weaponRows.addView(ui.text("此成员无武器（支援/设备单位）。", 13f, ui.muted), ui.wrap(4))
                }
                operative.weapons.forEach { weapon ->
                    val on = weapon.id in selected
                    weaponRows.addView(ui.row().apply {
                        minimumHeight = ui.dp(56)
                        setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
                        background = ui.bg(if (on) 0xff2a1d15.toInt() else 0xff1a1a1a.toInt(), if (on) ui.accent else 0xff2c2c2a.toInt())
                        addView(ui.text(if (on) "✓" else "", 18f, ui.accent, true).apply { gravity = Gravity.CENTER },
                            LinearLayout.LayoutParams(ui.dp(28), -2))
                        addView(ui.column().apply {
                            addView(ui.text(weapon.name, 15f, if (on) ui.ink else ui.muted, true), ui.wrap(2))
                            addView(WeaponRulesUi.profile(ui, weapon.name, weapon.profile, weapon.stats.keywords, 12f, ui.muted, bold = true, teamId = team.id), ui.wrap(0))
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        isClickable = true
                        setOnClickListener {
                            if (on) selected.remove(weapon.id) else selected.add(weapon.id)
                            renderWeapons()
                        }
                        contentDescription = "${weapon.name}，${if (on) "已选" else "未选"}"
                    }, ui.wrap(6))
                }
            }
            renderWeapons()
            box.addView(weaponRows, ui.wrap(10))

            box.addView(kit.section("称号（选填）"), ui.wrap(4))
            val callsign: EditText = ui.input(existing?.callsign.orEmpty(), "例如：蓝盔")
            box.addView(callsign, ui.wrap(12))

            box.addView(kit.section("棋子照片（选填）"), ui.wrap(4))
            val photo = ui.memberPortrait(avatarUri, operative.cardRes, 64, "${operative.name} · 棋子照片", fallbackAsset = operative.primaryCardAsset)
            val removeButton = ui.button("移除") {}
            fun showAvatar(value: String?) {
                val previous = avatarUri
                avatarUri = value
                if (!previous.isNullOrBlank() && previous != value) AvatarImages.evict(this, previous)
                ui.bindMemberPortrait(photo, value, operative.cardRes, "${operative.name} · 棋子照片")
                removeButton.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            removeButton.setOnClickListener { showAvatar(null) }
            removeButton.visibility = if (avatarUri.isNullOrBlank()) View.GONE else View.VISIBLE
            box.addView(ui.row().apply {
                addView(photo)
                addView(ui.button("拍摄") { capturePhoto(::showAvatar) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(6) })
                addView(ui.button("相册") { choosePhoto(::showAvatar) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(6) })
                addView(removeButton, LinearLayout.LayoutParams(0, -2, .8f))
            }, ui.wrap(16))

            box.addView(kit.primaryButton(if (existing == null) "加入小队" else "保存") {
                val updated = RosterMember(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    operativeId = operative.id,
                    callsign = callsign.text.toString().trim(),
                    weaponIds = operative.weapons.map { it.id }.filter { it in selected },
                    customAvatarUri = avatarUri
                )
                if (existing == null) roster.members.add(updated)
                else roster.members.indexOfFirst { it.id == existing.id }.takeIf { it >= 0 }?.let { roster.members[it] = updated }
                persist()
                sheet.dismiss()
                render()
            }, ui.wrap())
        }
    }

    private fun showCard(operative: OperativeTemplate) {
        val cards = operative.cardSources()
        val dialogTitle = if (cards.size > 1) "${operative.name}（共 ${cards.size} 张卡）" else operative.name
        ui.reference(dialogTitle) { box, _ ->
            cards.forEachIndexed { index, source ->
                if (cards.size > 1) {
                    box.addView(
                        ui.text("${index + 1}. ${source.label}", 13f, ui.accent, true),
                        ui.wrap(4)
                    )
                }
                val previewTitle = if (cards.size > 1) "${operative.name} · ${source.label}" else operative.name
                ui.preview(
                    box,
                    previewTitle,
                    asset = source.asset,
                    resource = source.resource.takeIf { source.asset == null }
                )
            }
        }
    }

    private fun rename() {
        ui.dialog("小队名称") { box, sheet ->
            val input = ui.input(roster.name, "小队名称")
            box.addView(input, ui.wrap(12))
            box.addView(kit.primaryButton("保存") {
                roster = roster.copy(name = input.text.toString().trim().ifBlank { RosterStore.defaultName(team) })
                persist()
                sheet.dismiss()
                render()
            })
        }
    }

    override fun finish() {
        if (roster.members.isEmpty() && roster.updatedAt > 0L) {
            MaterialAlertDialogBuilder(this)
                .setTitle("小队没有成员")
                .setMessage("保留这支空小队吗？")
                .setNegativeButton("删除") { _, _ -> store.delete(roster.rosterId); super.finish() }
                .setPositiveButton("保留") { _, _ -> super.finish() }
                .show()
            return
        }
        super.finish()
    }

    companion object {
        const val EXTRA_ROSTER_ID = "roster_id"
        const val EXTRA_CREATE_TEAM_ID = "create_team_id"
    }
}
