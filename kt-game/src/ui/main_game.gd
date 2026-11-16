class_name MainGame
extends Control

## Master Scene Controller & Video-Game Tactical HUD Orchestrator for KT-Game:
## - 100% Edge-to-Edge Fullscreen 3D HD-2D Killzone Diorama (zero helper-app sidebar/form walls!)
## - Top Broadcast Match Crest with glowing VP Gems & CP Diamonds + Floating Micro-Toolbar
## - Left & Right Edge Sliding Squad Medallions with persistent Trauma Ghost-Drain HP Bars
## - Bottom-Left Hero Portrait Console + Bottom-Center Octagonal Skill Hotbar `[1]`..`[9]` + Tooltip Popover
## - Playable Lift-on-Hover Stratagem Card Hand + Asymmetric AAA Tactical Main Menu

var engine: KTEngine = KTEngine.new()

var _killzone: KillzoneView

# Top Broadcast HUD & System Micro-Toolbar
var _top_crest: PanelContainer
var _team_a_pips: ScorePipStrip
var _team_b_pips: ScorePipStrip
var _team_a_ploys_box: HBoxContainer
var _team_b_ploys_box: HBoxContainer
var _tp_title_lbl: Label
var _phase_sub_lbl: Label

var _sys_bar: PanelContainer
var _undo_btn: Button

# Subtle Floating Tactical Objective / Hover Ticker Pill
var _tactical_pill: PanelContainer
var _guide_lbl: Label
var _hover_lbl: Label
var _hover_text_cache: String = ""

# Dramatic Phase Transition Banner
const PHASE_TOAST_DUR: float = 2.1
var _phase_toast: PanelContainer
var _phase_toast_lbl: Label
var _phase_toast_timer: float = 0.0
var _last_phase_sig: String = ""

# Left & Right Edge Sliding Squad Medallions (Persistent for Smooth Trauma Drain!)
var _left_squad_box: VBoxContainer
var _right_squad_box: VBoxContainer
var _medallions_by_uid: Dictionary = {}

# Bottom Video-Game HUD Stage
var _hero_panel: PanelContainer
var _hero_portrait: TextureRect
var _hero_order_ico: TextureRect
var _hero_name_lbl: Label
var _hero_stat_lbl: Label
var _hero_hp_bar: KTWidgets.TraumaBar
var _hero_hp_lbl: Label
var _hero_ap_strip: ApDiamondStrip
var _hero_order_btn: Button
var _hero_card_btn: Button
var _hero_abilities_flow: HFlowContainer
var _hero_uid: String = ""

var _skill_bar_panel: PanelContainer
var _skill_bar_hb: HBoxContainer
var _active_hotbar_slots: Dictionary = {} # int keycode -> KTWidgets.SkillSlot

var _weapon_strip: PanelContainer
var _weapon_hb: HBoxContainer

var _skill_tooltip: PanelContainer
var _tip_title_lbl: Label
var _tip_meta_lbl: Label
var _tip_desc_lbl: Label

var _context_stage: Control
var _context_hb: HBoxContainer

# Floating Battle Log Drawer, Modals & Asymmetric Main Menu
var _log_drawer: PanelContainer
var _log_rtl: RichTextLabel
var _dice_modal: DiceCombatModal
var _card_lightbox: CardLightbox
var _help_modal: Control
var _title_overlay: Control

var _ai_timer: Timer
var _pre_ai_undo_stack: Array[String] = []
var _in_ai_undo_guard: bool = false

# Title screen selections
var _sel_mode: String = "ai"
var _sel_human_side: String = "A"
var _sel_primary_tactic: String = "sharpshooter"
var _sel_secondary_tactic: String = "dueller"
var _sel_map_id: String = KTData.DEFAULT_MAP_ID
## True once a real battle was started; the title-screen map picker must not wipe it (▶ 继续战局).
var _battle_in_progress: bool = false

# Pending weapon selection for active operative
var _selected_weapon_idx: int = 0


# ==============================================================================
# CUSTOM DRAWN HUD MICRO-CONTROLS (VP Gems / CP Diamonds / AP Diamonds)
# ==============================================================================

class ScorePipStrip extends Control:
	var vp_val: int = 0
	var cp_val: int = 3
	var is_left_team: bool = true

	func setup(p_vp: int, p_cp: int, p_left: bool) -> void:
		vp_val = p_vp
		cp_val = p_cp
		is_left_team = p_left
		custom_minimum_size = Vector2(154, 34)
		mouse_filter = Control.MOUSE_FILTER_IGNORE
		queue_redraw()

	# Compact numeric readout. The pips/diamonds are gone: CP chips and the VP rail on the table
	# are the primary display now; this is just the quick-glance number in the display face.
	func _draw() -> void:
		var font: Font = KTTheme.get_cjk_font()
		var dfont: Font = KTTheme.get_display_font()
		var fcol: Color = KTTheme.COL_IMPERIAL.lightened(0.28) if is_left_team else KTTheme.COL_NURGLE.lightened(0.24)
		var vp_num := str(vp_val)
		var cp_num := str(cp_val)
		var align := HORIZONTAL_ALIGNMENT_LEFT if is_left_team else HORIZONTAL_ALIGNMENT_RIGHT
		var x0 := 0.0
		var w := size.x
		# "VP" small caps + big number, then "CP" + number, as two tight groups
		if is_left_team:
			draw_string(font, Vector2(x0, 12.0), "VP", HORIZONTAL_ALIGNMENT_LEFT, 24.0, 10, KTTheme.COL_MUTED)
			draw_string(dfont, Vector2(x0 + 22.0, 24.0), vp_num, HORIZONTAL_ALIGNMENT_LEFT, 30.0, 26, KTTheme.COL_GOLD_BRIGHT)
			draw_string(font, Vector2(x0 + 58.0, 12.0), "CP", HORIZONTAL_ALIGNMENT_LEFT, 24.0, 10, KTTheme.COL_MUTED)
			draw_string(dfont, Vector2(x0 + 80.0, 24.0), cp_num, HORIZONTAL_ALIGNMENT_LEFT, 30.0, 26, fcol)
		else:
			draw_string(dfont, Vector2(w - 110.0, 24.0), cp_num, HORIZONTAL_ALIGNMENT_RIGHT, 30.0, 26, fcol)
			draw_string(font, Vector2(w - 78.0, 12.0), "CP", HORIZONTAL_ALIGNMENT_LEFT, 24.0, 10, KTTheme.COL_MUTED)
			draw_string(dfont, Vector2(w - 52.0, 24.0), vp_num, HORIZONTAL_ALIGNMENT_RIGHT, 30.0, 26, KTTheme.COL_GOLD_BRIGHT)
			draw_string(font, Vector2(w - 20.0, 12.0), "VP", HORIZONTAL_ALIGNMENT_LEFT, 24.0, 10, KTTheme.COL_MUTED)


class ApDiamondStrip extends Control:
	var ap_cur: int = 3
	var ap_max: int = 3

	func setup(p_cur: int, p_max: int) -> void:
		ap_cur = maxi(0, p_cur)
		ap_max = maxi(1, maxi(p_cur, p_max))
		custom_minimum_size = Vector2(96, 20)
		mouse_filter = Control.MOUSE_FILTER_IGNORE
		queue_redraw()

	func _draw() -> void:
		var font: Font = KTTheme.get_cjk_font()
		for i in range(ap_max):
			var c := Vector2(10.0 + float(i) * 17.0, 10.0)
			var r: float = 6.2
			var lit: bool = i < ap_cur
			var pts := PackedVector2Array([
				c + Vector2(0, -r), c + Vector2(r, 0), c + Vector2(0, r), c + Vector2(-r, 0)
			])
			if lit:
				draw_colored_polygon(pts, KTTheme.COL_GOLD_BRIGHT)
				draw_circle(c, 2.2, Color(1.0, 0.98, 0.86))
			else:
				draw_colored_polygon(pts, Color(0.12, 0.15, 0.22, 0.9))
				var loop_p := pts.duplicate()
				loop_p.append(pts[0])
				draw_polyline(loop_p, Color(0.38, 0.42, 0.52, 0.7), 1.2, true)
		var txt_x: float = 8.0 + float(ap_max) * 17.0
		draw_string(font, Vector2(txt_x, 14.5), "%d AP" % ap_cur, HORIZONTAL_ALIGNMENT_LEFT, 48.0, 12, KTTheme.COL_GOLD_BRIGHT if ap_cur > 0 else KTTheme.COL_MUTED)


# ==============================================================================
# LIFECYCLE & SCENE ASSEMBLY
# ==============================================================================

func _ready() -> void:
	theme = KTTheme.build_global_theme()
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	KTSfx.ensure_attached(self)

	# 1. 100% Edge-to-Edge Fullscreen 3D HD-2D Tabletop World Layer
	_killzone = KillzoneView.new()
	_killzone.operative_clicked.connect(_on_board_operative_clicked)
	_killzone.ground_move_committed.connect(_on_board_move_committed)
	_killzone.shoot_target_clicked.connect(_on_board_shoot_target_clicked)
	_killzone.fight_target_clicked.connect(_on_board_fight_target_clicked)
	_killzone.hover_info_changed.connect(_on_board_hover_changed)
	_killzone.deploy_spot_clicked.connect(_on_board_deploy_spot_clicked)
	_killzone.equipment_spot_clicked.connect(_on_board_equipment_spot_clicked)
	_killzone.point_picked.connect(_on_board_point_picked)
	_killzone.enemy_picked.connect(_on_board_enemy_picked)
	_killzone.cancel_interaction_requested.connect(func():
		_hide_skill_tooltip()
		_refresh_all_ui()
	)
	add_child(_killzone)

	# 2. Floating Video-Game HUD Widgets (Zero Boxed Sidebar Walls!)
	_build_top_broadcast_crest()
	_build_sys_micro_toolbar()
	_build_tactical_pill()
	_build_edge_squad_medallions()
	_build_hero_unit_console()
	_build_skill_hotbar()
	_build_weapon_cards_strip()
	_build_context_stage()
	_build_skill_tooltip()
	_build_log_drawer()
	_build_phase_toast()

	# 3. Combat Stage, Card Lightbox, Help & Asymmetric Main Menu
	_dice_modal = DiceCombatModal.new()
	_dice_modal.bind_killzone(_killzone)
	_dice_modal.combat_vfx_triggered.connect(_on_combat_vfx_triggered)
	_dice_modal.combat_finished.connect(_on_combat_modal_finished)
	_dice_modal.initiative_finished.connect(_on_initiative_modal_finished)
	add_child(_dice_modal)

	_card_lightbox = CardLightbox.new()
	add_child(_card_lightbox)

	for arg in OS.get_cmdline_user_args():
		var arg_s: String = str(arg)
		if arg_s.begins_with("--map="):
			var mid: String = arg_s.substr(6)
			if KTData.MAP_IDS.has(mid):
				_sel_map_id = mid

	_build_help_modal()
	_build_title_overlay()

	# 4. AI Step Timer
	_ai_timer = Timer.new()
	_ai_timer.one_shot = true
	_ai_timer.wait_time = 0.65
	_ai_timer.timeout.connect(_run_ai_step)
	add_child(_ai_timer)

	_card_lightbox.visibility_changed.connect(func():
		if _card_lightbox.visible:
			if _ai_timer != null:
				_ai_timer.stop()
		else:
			_refresh_all_ui()
	)

	_strip_button_focus_recursive(self)
	if get_tree() != null and not get_tree().node_added.is_connected(_on_tree_node_added):
		get_tree().node_added.connect(_on_tree_node_added)

	resized.connect(_on_viewport_resized)

	# Initialize background demo match so 3D diorama is alive on the open right half of the Main Menu!
	_restart_menu_backdrop()
	call_deferred("_on_viewport_resized")

	var args := OS.get_cmdline_user_args()
	if args.has("--autotest-screenshots"):
		call_deferred("_run_autotest_screenshot_tour")


func _exit_tree() -> void:
	if get_tree() != null and get_tree().node_added.is_connected(_on_tree_node_added):
		get_tree().node_added.disconnect(_on_tree_node_added)


func _on_tree_node_added(n: Node) -> void:
	if n is BaseButton:
		(n as Control).focus_mode = Control.FOCUS_NONE


func _strip_button_focus_recursive(root_node: Node) -> void:
	if root_node is BaseButton:
		(root_node as Control).focus_mode = Control.FOCUS_NONE
	for ch in root_node.get_children():
		_strip_button_focus_recursive(ch)


func _is_any_modal_overlay_open() -> bool:
	if _title_overlay != null and _title_overlay.visible:
		return true
	if _dice_modal != null and _dice_modal.visible:
		return true
	if _card_lightbox != null and _card_lightbox.visible:
		return true
	if _help_modal != null and _help_modal.visible:
		return true
	if _settings_modal != null and _settings_modal.visible:
		return true
	return false


func _process(delta: float) -> void:
	# Slowly orbit the live HD-2D standee diorama in the open right stage of the Main Menu!
	if _title_overlay != null and _title_overlay.visible and _killzone != null:
		_killzone.target_yaw_deg += delta * 6.5
		_killzone.target_pitch_deg = 31.0
		_killzone.target_dist = 43.0
		var yaw_r: float = deg_to_rad(_killzone.target_yaw_deg)
		var cam_right := Vector3(cos(yaw_r), 0.0, -sin(yaw_r))
		_killzone.target_focus = -cam_right * 6.8 + Vector3(0.0, 0.8, 0.0)

	if _phase_toast != null and _phase_toast.visible:
		_phase_toast_timer -= delta
		if _phase_toast_timer <= 0.0:
			_phase_toast.visible = false
		else:
			# Sweep in (first 0.35s): the label slides from the left edge to centre with ease-out.
			var elapsed: float = PHASE_TOAST_DUR - _phase_toast_timer
			var sweep: float = clampf(elapsed / 0.35, 0.0, 1.0)
			var ease_s: float = 1.0 - pow(1.0 - sweep, 3.0)
			var vpw: float = get_viewport_rect().size.x
			_phase_toast_lbl.position.x = lerpf(-vpw * 0.6, 0.0, ease_s)
			_phase_toast.modulate.a = minf(clampf(_phase_toast_timer / 0.45, 0.0, 1.0), clampf(elapsed / 0.15, 0.0, 1.0))


func _on_viewport_resized() -> void:
	var vp := get_viewport_rect().size
	if vp.x < 100 or vp.y < 100:
		vp = Vector2(1600, 960)
	if _title_overlay != null and _title_overlay.visible:
		var yaw_r: float = deg_to_rad(_killzone.target_yaw_deg)
		var cam_right := Vector3(cos(yaw_r), 0.0, -sin(yaw_r))
		_killzone.target_focus = -cam_right * 6.8 + Vector3(0.0, 0.8, 0.0)
		_killzone.cam_focus = _killzone.target_focus
		_killzone.target_dist = 43.0
		_killzone.cam_dist = 43.0
		_killzone.target_pitch_deg = 31.0
		_killzone.cam_pitch_deg = 31.0
	else:
		if _killzone.is_camera_default_isometric():
			var avail := Rect2(198.0, 56.0, maxf(640.0, vp.x - 396.0), maxf(420.0, vp.y - 216.0))
			_killzone.fit_board_to_rect(avail, true)
		else:
			_killzone.refresh_projection_only()
		if _dice_modal != null and _dice_modal.visible:
			_dice_modal.snap_entrance_for_capture()
		if _skill_tooltip != null and _skill_tooltip.visible:
			_hide_skill_tooltip()


func _unhandled_input(event: InputEvent) -> void:
	if _title_overlay != null and _title_overlay.visible:
		return
	if _dice_modal != null and _dice_modal.visible:
		return
	if _settings_modal != null and _settings_modal.visible:
		if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
			_settings_modal.visible = false
			accept_event()
			_refresh_all_ui()
		return
	if _help_modal != null and _help_modal.visible:
		if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
			_help_modal.visible = false
			accept_event()
			_refresh_all_ui()
		return
	if _card_lightbox != null and _card_lightbox.visible:
		if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
			_card_lightbox.hide()
			accept_event()
		return

	if event is InputEventKey and event.pressed and not event.echo:
		var kc: int = event.keycode
		if kc == KEY_ESCAPE:
			if _killzone != null and _killzone.interaction_mode != "idle":
				accept_event()
				_killzone.clear_interaction()
				_hide_skill_tooltip()
				_refresh_all_ui()
				return
		elif kc == KEY_F:
			if _killzone != null and not engine.state.is_empty():
				accept_event()
				_killzone.reset_isometric_view(false)
				return
		elif kc == KEY_F1:
			accept_event()
			if _ai_timer != null:
				_ai_timer.stop()
			_card_lightbox.show_team_rulebook("IMP-AOD")
			return
		elif kc == KEY_F2:
			accept_event()
			if _ai_timer != null:
				_ai_timer.stop()
			_card_lightbox.show_team_rulebook("CHAOS-PM")
			return
		elif kc == KEY_Z:
			if engine.is_ai_turn():
				accept_event()
				_notify_illegal_click("敌方回合中无法撤回")
				return
			if _can_human_undo():
				accept_event()
				_on_undo_pressed()
				return

		if engine.state.is_empty() or engine.is_ai_turn():
			return

		var phase: String = str(engine.state["phase"])
		if kc == KEY_R and phase == "EQDEPLOY" and _killzone.interaction_mode == "eq_deploy":
			accept_event()
			var r_spec: Dictionary = KTEngine.EQ_ITEM_SPECS.get(_killzone.eq_deploy_kind, {})
			if str(r_spec.get("terrain", "")) != "":
				_killzone.eq_deploy_rot = not _killzone.eq_deploy_rot
			return
		if kc == KEY_SPACE:
			if phase == "DROPZONE":
				accept_event()
				engine.choose_drop_zone(false)
				_refresh_all_ui()
				return
			elif phase == "SELECT":
				accept_event()
				var sd: String = engine.setup_side()
				var res_s: Dictionary = engine.confirm_roster(sd, _roster_pick if (_roster_pick_side == sd and not _roster_pick.is_empty()) else engine.default_roster(sd))
				if bool(res_s.get("ok", false)):
					_roster_pick_side = ""
				else:
					_killzone.spawn_floating_text(Vector2(15.0, 11.0), str(res_s.get("msg", "")), KTTheme.COL_CRIMSON)
				_refresh_all_ui()
				return
			elif phase == "EQUIP":
				accept_event()
				var es_k: String = engine.setup_side()
				var use_pick: Array = _equip_pick if _equip_pick_side == es_k else engine.default_equipment(es_k)
				var use_opts: Dictionary = _equip_opts if _equip_pick_side == es_k else {}
				if bool(engine.confirm_equipment(es_k, use_pick, use_opts).get("ok", false)):
					_equip_pick_side = ""
					_equip_opts = {}
				_refresh_all_ui()
				return
			elif phase == "INITIATIVE":
				accept_event()
				KTSfx.play("phase_horn", -5.0, 0.03)
				_roll_or_resume_initiative()
				return
			elif phase == "STRATEGY":
				accept_event()
				KTSfx.play("ui_click", -4.0, 0.04)
				var res_p: Dictionary = engine.pass_strategy(str(engine.state["turn_side"]))
				if not bool(res_p.get("ok", false)):
					_killzone.spawn_floating_text(Vector2(15.0, 11.0), str(res_p.get("msg", "")), KTTheme.COL_CRIMSON)
				_refresh_all_ui()
				return
			elif phase == "FIREFIGHT" and not str(engine.state["active_op_uid"]).is_empty():
				accept_event()
				_end_human_activation()
				return

		if phase == "FIREFIGHT" and not str(engine.state["active_op_uid"]).is_empty():
			var aop: Dictionary = engine.get_op(str(engine.state["active_op_uid"]))
			if kc == KEY_TAB and not aop.is_empty():
				accept_event()
				KTSfx.play("card_lift", -5.0, 0.04)
				_card_lightbox.show_operative_cards(aop)
				return
			if kc == KEY_C and not aop.is_empty():
				if aop["actions_taken"].is_empty() and not bool(engine.state["active_is_counteract"]):
					accept_event()
					KTSfx.play("order_servo", -4.0, 0.04)
					var next_ord: String = "engage" if str(aop["order"]) == "conceal" else "conceal"
					engine.set_active_order(next_ord)
					_killzone.clear_interaction()
					_refresh_all_ui()
					return
			if _active_hotbar_slots.has(kc):
				var slot: KTWidgets.SkillSlot = _active_hotbar_slots[kc]
				if slot != null and not slot.disabled:
					accept_event()
					slot._press_t = 1.0
					KTSfx.play("ui_click", -3.5, 0.04)
					slot.triggered.emit()
					return


# ==============================================================================
# FLOATING VIDEO-GAME HUD CONSTRUCTION
# ==============================================================================

## Top bar (route B): one slim strip. CP / VP / TP numbers now live on the physical table
## (KTTableProps3D), so the strip only carries what has no physical form: faction crests with
## active-ploy chips, the phase medallion, and the rulebook buttons. Score pips stay as a compact
## readout for players who don't want to pan the camera to the table edge.
func _build_top_broadcast_crest() -> void:
	_top_crest = PanelContainer.new()
	_top_crest.anchor_left = 0.5
	_top_crest.anchor_right = 0.5
	_top_crest.anchor_top = 0.0
	_top_crest.anchor_bottom = 0.0
	_top_crest.offset_left = -400.0
	_top_crest.offset_right = 400.0
	_top_crest.offset_top = 6.0
	_top_crest.offset_bottom = 44.0
	_top_crest.clip_contents = true
	_top_crest.add_theme_stylebox_override("panel", KTTheme.panel("floating", KTTheme.COL_BRASS))
	add_child(_top_crest)

	var hb := HBoxContainer.new()
	hb.add_theme_constant_override("separation", 10)
	hb.alignment = BoxContainer.ALIGNMENT_CENTER
	_top_crest.add_child(hb)

	# --- Left: Angels of Death crest (click = rulebook) + ploy chips + pips ---
	var crest_a := _make_crest_button("res://assets/ui/team_mark_imp_aod.png", "死亡天使 — 阵营规则与计谋原卡 [F1]", KTTheme.COL_IMPERIAL, func(): _card_lightbox.show_team_rulebook("IMP-AOD"))
	hb.add_child(crest_a)

	_team_a_pips = ScorePipStrip.new()
	_team_a_pips.setup(0, 3, true)
	_team_a_pips.custom_minimum_size = Vector2(128, 30)
	hb.add_child(_team_a_pips)

	_team_a_ploys_box = HBoxContainer.new()
	_team_a_ploys_box.add_theme_constant_override("separation", 4)
	_team_a_ploys_box.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	hb.add_child(_team_a_ploys_box)

	# --- Centre: phase medallion (TP + phase + acting side) ---
	var center_med := PanelContainer.new()
	center_med.custom_minimum_size = Vector2(216, 30)
	# The primary kit frame has 64px corner ornaments; at 30px tall they collide with the text, so the
	# medallion uses the slimmer floating frame with a gold tint instead.
	center_med.add_theme_stylebox_override("panel", KTTheme.panel("floating", KTTheme.COL_GOLD_BRIGHT))
	hb.add_child(center_med)

	var cm_hb := HBoxContainer.new()
	cm_hb.alignment = BoxContainer.ALIGNMENT_CENTER
	cm_hb.add_theme_constant_override("separation", 8)
	center_med.add_child(cm_hb)

	_tp_title_lbl = Label.new()
	_tp_title_lbl.add_theme_font_override("font", KTTheme.get_display_font())
	_tp_title_lbl.add_theme_font_size_override("font_size", 22)
	_tp_title_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	cm_hb.add_child(_tp_title_lbl)

	var sep := ColorRect.new()
	sep.custom_minimum_size = Vector2(1, 16)
	sep.color = Color(KTTheme.COL_BRASS.r, KTTheme.COL_BRASS.g, KTTheme.COL_BRASS.b, 0.6)
	cm_hb.add_child(sep)

	_phase_sub_lbl = Label.new()
	_phase_sub_lbl.add_theme_font_size_override("font_size", KTTheme.FS_BODY)
	_phase_sub_lbl.add_theme_color_override("font_color", KTTheme.COL_TEXT)
	cm_hb.add_child(_phase_sub_lbl)

	# --- Right: ploy chips + pips + Plague Marines crest ---
	_team_b_ploys_box = HBoxContainer.new()
	_team_b_ploys_box.alignment = BoxContainer.ALIGNMENT_END
	_team_b_ploys_box.add_theme_constant_override("separation", 4)
	_team_b_ploys_box.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	hb.add_child(_team_b_ploys_box)

	_team_b_pips = ScorePipStrip.new()
	_team_b_pips.setup(0, 3, false)
	_team_b_pips.custom_minimum_size = Vector2(128, 30)
	hb.add_child(_team_b_pips)

	var crest_b := _make_crest_button("res://assets/ui/team_mark_chaos_pm.png", "瘟疫战士 — 阵营规则与计谋原卡 [F2]", KTTheme.COL_NURGLE, func(): _card_lightbox.show_team_rulebook("CHAOS-PM"))
	hb.add_child(crest_b)


func _make_crest_button(tex_path: String, tip: String, accent: Color, on_press: Callable) -> Button:
	var b := Button.new()
	b.focus_mode = Control.FOCUS_NONE
	b.custom_minimum_size = Vector2(34, 30)
	b.tooltip_text = tip
	b.icon = KTData.load_tex(tex_path)
	b.expand_icon = true
	b.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
	b.flat = true
	b.add_theme_stylebox_override("normal", StyleBoxEmpty.new())
	b.add_theme_stylebox_override("hover", KTTheme.make_btn_style(Color(accent.r * 0.25, accent.g * 0.25, accent.b * 0.25, 0.9), accent, 6, 2, 2, true))
	b.add_theme_stylebox_override("pressed", KTTheme.make_btn_style(Color(accent.r * 0.4, accent.g * 0.4, accent.b * 0.4, 1.0), accent, 6, 2, 2, true))
	KTWidgets.attach_button_juice(b, 1.1, "card_lift")
	b.pressed.connect(on_press)
	return b


func _build_sys_micro_toolbar() -> void:
	_sys_bar = PanelContainer.new()
	_sys_bar.anchor_left = 1.0
	_sys_bar.anchor_right = 1.0
	_sys_bar.anchor_top = 0.0
	_sys_bar.anchor_bottom = 0.0
	_sys_bar.offset_left = -342.0
	_sys_bar.offset_right = -8.0
	_sys_bar.offset_top = 8.0
	_sys_bar.offset_bottom = 44.0
	_sys_bar.add_theme_stylebox_override("panel", KTTheme.panel("tag"))
	add_child(_sys_bar)

	var hb := HBoxContainer.new()
	hb.alignment = BoxContainer.ALIGNMENT_END
	hb.add_theme_constant_override("separation", 4)
	_sys_bar.add_child(hb)

	# Icon-only system buttons. Camera orbit lives on Q/E + right-drag; the two orbit buttons stay for
	# mouse-only players but as glyphs. Everything is 28px tall so the strip reads as one quiet row.
	var specs: Array[Dictionary] = [
		{"t": "⟲", "ico": "sys_orbit_left", "tip": "左旋视角 [Q / 右键拖拽]", "cb": func(): _killzone.orbit_by(-35.0)},
		{"t": "◈", "ico": "sys_cam_iso", "tip": "等距俯视 [F]", "cb": func(): _killzone.set_camera_angle_preset("iso")},
		{"t": "◢", "ico": "sys_cam_low", "tip": "低角特写", "cb": func(): _killzone.set_camera_angle_preset("low")},
		{"t": "⟳", "ico": "sys_orbit_right", "tip": "右旋视角 [E]", "cb": func(): _killzone.orbit_by(35.0)},
		{"t": "↶", "ico": "sys_undo", "tip": "撤回上一步 [Z]", "cb": _on_undo_pressed, "undo": true},
		{"t": "≣", "ico": "sys_log", "tip": "战报", "cb": func(): _log_drawer.visible = not _log_drawer.visible},
		{"t": "?", "ico": "sys_help", "tip": "热键与规则指南", "cb": func():
			if _ai_timer != null:
				_ai_timer.stop()
			_help_modal.visible = true
			move_child(_help_modal, get_child_count() - 1)},
		{"t": "⚙", "ico": "sys_settings", "tip": "设置", "cb": func(): _open_settings()},
		{"t": "☰", "ico": "sys_menu", "tip": "返回主菜单", "cb": _return_to_main_menu},
	]
	for sp in specs:
		var b := Button.new()
		b.focus_mode = Control.FOCUS_NONE
		var ico_tex := KTTheme.kit_tex_scaled(str(sp["ico"]), 20)
		if ico_tex != null:
			b.icon = ico_tex
			b.expand_icon = false
			b.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
			b.add_theme_color_override("icon_normal_color", Color(0.88, 0.90, 0.95))
			b.add_theme_color_override("icon_hover_color", KTTheme.COL_GOLD_BRIGHT)
		else:
			b.text = str(sp["t"])
			b.add_theme_font_size_override("font_size", 14)
		b.tooltip_text = str(sp["tip"])
		b.custom_minimum_size = Vector2(30, 28)
		var kb := KTTheme.kit_button("normal", 5, 3)
		b.add_theme_stylebox_override("normal", kb if kb != null else KTTheme.make_btn_style(Color(0.08, 0.09, 0.12, 0.85), Color(KTTheme.COL_BRASS.r, KTTheme.COL_BRASS.g, KTTheme.COL_BRASS.b, 0.45), 5, 6, 3))
		KTWidgets.attach_button_juice(b, 1.1)
		var cb_fn: Callable = sp["cb"]
		b.pressed.connect(cb_fn)
		hb.add_child(b)
		if bool(sp.get("undo", false)):
			_undo_btn = b
			_undo_btn.gui_input.connect(func(ev: InputEvent):
				if ev is InputEventMouseButton and ev.pressed and ev.button_index == MOUSE_BUTTON_LEFT and engine.is_ai_turn():
					_notify_illegal_click("敌方回合中无法撤回")
			)


## Guidance line: sits just under the top strip, no border, fades in only when the text changes
## and then settles to ~70% so it reads as a whisper rather than a second header.
func _build_tactical_pill() -> void:
	_tactical_pill = PanelContainer.new()
	_tactical_pill.anchor_left = 0.5
	_tactical_pill.anchor_right = 0.5
	_tactical_pill.anchor_top = 0.0
	_tactical_pill.anchor_bottom = 0.0
	_tactical_pill.offset_left = -330.0
	_tactical_pill.offset_right = 330.0
	_tactical_pill.offset_top = 52.0
	_tactical_pill.offset_bottom = 76.0
	_tactical_pill.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_tactical_pill.add_theme_stylebox_override("panel", KTTheme.panel("tag"))
	add_child(_tactical_pill)

	var hb := HBoxContainer.new()
	hb.alignment = BoxContainer.ALIGNMENT_CENTER
	hb.add_theme_constant_override("separation", 12)
	hb.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_tactical_pill.add_child(hb)

	_guide_lbl = Label.new()
	_guide_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_guide_lbl.add_theme_font_size_override("font_size", KTTheme.FS_CAPTION)
	_guide_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT.lerp(KTTheme.COL_TEXT, 0.35))
	hb.add_child(_guide_lbl)

	_hover_lbl = Label.new()
	_hover_lbl.add_theme_font_size_override("font_size", 12)
	_hover_lbl.add_theme_color_override("font_color", KTTheme.COL_EMERALD.lightened(0.25))
	_hover_lbl.visible = false
	hb.add_child(_hover_lbl)


func _build_edge_squad_medallions() -> void:
	_left_squad_box = VBoxContainer.new()
	_left_squad_box.anchor_left = 0.0
	_left_squad_box.anchor_right = 0.0
	_left_squad_box.anchor_top = 0.0
	_left_squad_box.anchor_bottom = 0.0
	_left_squad_box.offset_left = 10.0
	_left_squad_box.offset_right = 198.0
	_left_squad_box.offset_top = 80.0
	_left_squad_box.offset_bottom = 396.0
	_left_squad_box.add_theme_constant_override("separation", 5)
	_left_squad_box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_left_squad_box)

	_right_squad_box = VBoxContainer.new()
	_right_squad_box.anchor_left = 1.0
	_right_squad_box.anchor_right = 1.0
	_right_squad_box.anchor_top = 0.0
	_right_squad_box.anchor_bottom = 0.0
	_right_squad_box.offset_left = -198.0
	_right_squad_box.offset_right = -10.0
	_right_squad_box.offset_top = 80.0
	_right_squad_box.offset_bottom = 396.0
	_right_squad_box.add_theme_constant_override("separation", 5)
	_right_squad_box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_right_squad_box)


func _build_hero_unit_console() -> void:
	_hero_panel = PanelContainer.new()
	_hero_panel.anchor_left = 0.0
	_hero_panel.anchor_right = 0.0
	_hero_panel.anchor_top = 1.0
	_hero_panel.anchor_bottom = 1.0
	# Hero card and hotbar form ONE bottom console: hero on the left (floating tier), hotbar
	# immediately to its right (primary tier) sharing the same baseline and height.
	_hero_panel.offset_left = 14.0
	_hero_panel.offset_right = 356.0
	_hero_panel.offset_top = -150.0
	_hero_panel.offset_bottom = -12.0
	_hero_panel.visible = false
	_hero_panel.add_theme_stylebox_override("panel", KTTheme.panel("floating", KTTheme.COL_BRASS))
	add_child(_hero_panel)

	var hb := HBoxContainer.new()
	hb.add_theme_constant_override("separation", 10)
	_hero_panel.add_child(hb)

	# Sculpted Standee Bust Frame
	var port_frame := PanelContainer.new()
	port_frame.custom_minimum_size = Vector2(76, 92)
	port_frame.add_theme_stylebox_override(
		"panel",
		KTTheme.make_panel_style(Color(0.06, 0.08, 0.12, 0.96), KTTheme.COL_BRASS, 8, 1, 3)
	)
	hb.add_child(port_frame)

	var port_stack := Control.new()
	port_stack.custom_minimum_size = Vector2(68, 84)
	port_frame.add_child(port_stack)

	_hero_portrait = TextureRect.new()
	_hero_portrait.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_hero_portrait.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_hero_portrait.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	port_stack.add_child(_hero_portrait)

	_hero_order_ico = TextureRect.new()
	_hero_order_ico.anchor_left = 1.0
	_hero_order_ico.anchor_right = 1.0
	_hero_order_ico.anchor_top = 1.0
	_hero_order_ico.anchor_bottom = 1.0
	_hero_order_ico.offset_left = -34.0
	_hero_order_ico.offset_top = -34.0
	_hero_order_ico.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_hero_order_ico.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	port_stack.add_child(_hero_order_ico)

	# Right Column: Name, Trauma HP Bar, AP Diamonds, Stance & Codex Triggers
	var vb := VBoxContainer.new()
	vb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	vb.alignment = BoxContainer.ALIGNMENT_CENTER
	vb.add_theme_constant_override("separation", 5)
	hb.add_child(vb)

	var top_r := HBoxContainer.new()
	top_r.add_theme_constant_override("separation", 6)
	vb.add_child(top_r)

	_hero_name_lbl = Label.new()
	_hero_name_lbl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_hero_name_lbl.add_theme_font_size_override("font_size", KTTheme.FS_HEADING)
	_hero_name_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	_hero_name_lbl.clip_text = true
	top_r.add_child(_hero_name_lbl)

	_hero_hp_lbl = Label.new()
	_hero_hp_lbl.add_theme_font_override("font", KTTheme.get_display_font())
	_hero_hp_lbl.add_theme_font_size_override("font_size", 20)
	_hero_hp_lbl.add_theme_color_override("font_color", KTTheme.COL_EMERALD.lightened(0.2))
	top_r.add_child(_hero_hp_lbl)

	_hero_hp_bar = KTWidgets.TraumaBar.new(14, 14, KTTheme.COL_EMERALD)
	_hero_hp_bar.custom_minimum_size = Vector2(0, 11)
	vb.add_child(_hero_hp_bar)

	var mid_r := HBoxContainer.new()
	mid_r.add_theme_constant_override("separation", 8)
	vb.add_child(mid_r)

	_hero_ap_strip = ApDiamondStrip.new()
	_hero_ap_strip.setup(3, 3)
	mid_r.add_child(_hero_ap_strip)

	_hero_stat_lbl = Label.new()
	_hero_stat_lbl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_hero_stat_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	_hero_stat_lbl.add_theme_font_size_override("font_size", 11)
	_hero_stat_lbl.add_theme_color_override("font_color", KTTheme.COL_MUTED)
	mid_r.add_child(_hero_stat_lbl)

	# Ability chips: faction/operative rules as glossary chips (hover = effect, click = rule text)
	_hero_abilities_flow = HFlowContainer.new()
	_hero_abilities_flow.add_theme_constant_override("h_separation", 4)
	_hero_abilities_flow.add_theme_constant_override("v_separation", 2)
	vb.add_child(_hero_abilities_flow)

	var bot_r := HBoxContainer.new()
	bot_r.add_theme_constant_override("separation", 6)
	vb.add_child(bot_r)

	_hero_order_btn = Button.new()
	_hero_order_btn.focus_mode = Control.FOCUS_NONE
	_hero_order_btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_hero_order_btn.expand_icon = true
	_hero_order_btn.add_theme_constant_override("icon_max_width", 20)
	_hero_order_btn.add_theme_font_size_override("font_size", 12)
	KTWidgets.attach_button_juice(_hero_order_btn, 1.04, "order_servo")
	_hero_order_btn.pressed.connect(func():
		var aop: Dictionary = engine.get_op(_hero_uid)
		if not aop.is_empty() and aop["actions_taken"].is_empty() and not bool(engine.state["active_is_counteract"]):
			var next_ord: String = "engage" if str(aop["order"]) == "conceal" else "conceal"
			engine.set_active_order(next_ord)
			_killzone.clear_interaction()
			_refresh_all_ui()
	)
	bot_r.add_child(_hero_order_btn)

	_hero_card_btn = Button.new()
	_hero_card_btn.focus_mode = Control.FOCUS_NONE
	var card_ico := KTTheme.kit_tex_scaled("icon_card", 16)
	if card_ico != null:
		_hero_card_btn.icon = card_ico
		_hero_card_btn.text = "原卡 [Tab]"
	else:
		_hero_card_btn.text = "📖原卡[Tab]"
	_hero_card_btn.add_theme_font_size_override("font_size", 11)
	KTWidgets.attach_button_juice(_hero_card_btn, 1.05, "card_lift")
	_hero_card_btn.pressed.connect(func():
		var aop: Dictionary = engine.get_op(_hero_uid)
		if not aop.is_empty():
			_card_lightbox.show_operative_cards(aop)
	)
	bot_r.add_child(_hero_card_btn)


func _build_skill_hotbar() -> void:
	_skill_bar_panel = PanelContainer.new()
	_skill_bar_panel.anchor_left = 0.0
	_skill_bar_panel.anchor_right = 0.0
	_skill_bar_panel.anchor_top = 1.0
	_skill_bar_panel.anchor_bottom = 1.0
	_skill_bar_panel.offset_left = 364.0
	_skill_bar_panel.offset_right = 1060.0
	_skill_bar_panel.offset_top = -150.0
	_skill_bar_panel.offset_bottom = -12.0
	_skill_bar_panel.grow_horizontal = Control.GROW_DIRECTION_END
	_skill_bar_panel.visible = false
	_skill_bar_panel.add_theme_stylebox_override("panel", KTTheme.panel("primary", KTTheme.COL_GOLD))
	add_child(_skill_bar_panel)

	_skill_bar_hb = HBoxContainer.new()
	_skill_bar_hb.alignment = BoxContainer.ALIGNMENT_CENTER
	_skill_bar_hb.add_theme_constant_override("separation", 6)
	_skill_bar_panel.add_child(_skill_bar_hb)


func _build_weapon_cards_strip() -> void:
	_weapon_strip = PanelContainer.new()
	# Weapon cards pop up directly above the hotbar's shoot slot, like a drawer sliding out of it.
	_weapon_strip.anchor_left = 0.0
	_weapon_strip.anchor_right = 0.0
	_weapon_strip.anchor_top = 1.0
	_weapon_strip.anchor_bottom = 1.0
	_weapon_strip.offset_left = 364.0
	_weapon_strip.offset_right = 1060.0
	_weapon_strip.offset_top = -248.0
	_weapon_strip.offset_bottom = -156.0
	_weapon_strip.grow_horizontal = Control.GROW_DIRECTION_END
	_weapon_strip.visible = false
	_weapon_strip.add_theme_stylebox_override("panel", KTTheme.panel("floating", KTTheme.COL_CRIMSON))
	add_child(_weapon_strip)

	_weapon_hb = HBoxContainer.new()
	_weapon_hb.alignment = BoxContainer.ALIGNMENT_CENTER
	_weapon_hb.add_theme_constant_override("separation", 8)
	_weapon_strip.add_child(_weapon_hb)


func _build_context_stage() -> void:
	_context_stage = Control.new()
	_context_stage.anchor_left = 0.5
	_context_stage.anchor_right = 0.5
	_context_stage.anchor_top = 1.0
	_context_stage.anchor_bottom = 1.0
	_context_stage.offset_left = -560.0
	_context_stage.offset_right = 560.0
	_context_stage.offset_top = -176.0
	_context_stage.offset_bottom = -12.0
	_context_stage.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_context_stage)

	_context_hb = HBoxContainer.new()
	_context_hb.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_context_hb.grow_vertical = Control.GROW_DIRECTION_BEGIN
	_context_hb.alignment = BoxContainer.ALIGNMENT_CENTER
	_context_hb.add_theme_constant_override("separation", 10)
	_context_hb.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_context_stage.add_child(_context_hb)


func _build_skill_tooltip() -> void:
	_skill_tooltip = PanelContainer.new()
	_skill_tooltip.visible = false
	_skill_tooltip.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_skill_tooltip.z_index = 50
	_skill_tooltip.custom_minimum_size = Vector2(268, 76)
	_skill_tooltip.add_theme_stylebox_override(
		"panel",
		KTTheme.make_panel_style(Color(0.025, 0.034, 0.056, 0.97), KTTheme.COL_GOLD_BRIGHT, 9, 2, 10, true)
	)
	add_child(_skill_tooltip)

	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 4)
	vb.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_skill_tooltip.add_child(vb)

	var hdr := HBoxContainer.new()
	hdr.add_theme_constant_override("separation", 8)
	vb.add_child(hdr)

	_tip_title_lbl = Label.new()
	_tip_title_lbl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_tip_title_lbl.add_theme_font_size_override("font_size", 14)
	_tip_title_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	hdr.add_child(_tip_title_lbl)

	_tip_meta_lbl = Label.new()
	_tip_meta_lbl.add_theme_font_size_override("font_size", 11)
	_tip_meta_lbl.add_theme_color_override("font_color", KTTheme.COL_EMERALD.lightened(0.25))
	hdr.add_child(_tip_meta_lbl)

	_tip_desc_lbl = Label.new()
	_tip_desc_lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_tip_desc_lbl.custom_minimum_size = Vector2(248, 0)
	_tip_desc_lbl.add_theme_font_size_override("font_size", 12)
	_tip_desc_lbl.add_theme_color_override("font_color", KTTheme.COL_TEXT)
	vb.add_child(_tip_desc_lbl)

	# Rule keywords mentioned in the description, as glossary chips
	_tip_rules_flow = HFlowContainer.new()
	_tip_rules_flow.add_theme_constant_override("h_separation", 4)
	_tip_rules_flow.add_theme_constant_override("v_separation", 3)
	vb.add_child(_tip_rules_flow)


var _tip_rules_flow: HFlowContainer

func _show_skill_tooltip(slot: KTWidgets.SkillSlot) -> void:
	if _skill_tooltip == null or slot == null:
		return
	_tip_title_lbl.text = slot.full_title
	if slot.hotkey_str.is_empty():
		_tip_meta_lbl.text = "%d AP" % slot.ap_cost
	else:
		_tip_meta_lbl.text = "[%s] · %d AP" % [slot.hotkey_str, slot.ap_cost] if slot.ap_cost > 0 else "[%s]" % slot.hotkey_str
	_tip_desc_lbl.text = slot.desc_text
	for c in _tip_rules_flow.get_children():
		c.queue_free()
	var found := KTGlossary.find_in_text(slot.full_title + " " + slot.desc_text)
	_tip_rules_flow.visible = not found.is_empty()
	for e in found:
		var chip := KTRuleChip.new()
		chip.setup(str(e["name"]), Color(0, 0, 0, 0), 11)
		_tip_rules_flow.add_child(chip)
	# The tooltip itself must accept the mouse so chips inside it can be hovered/clicked
	_skill_tooltip.mouse_filter = Control.MOUSE_FILTER_PASS if not found.is_empty() else Control.MOUSE_FILTER_IGNORE
	_skill_tooltip.reset_size()
	_skill_tooltip.visible = true
	var gp: Vector2 = slot.global_position
	var tw: float = maxf(268.0, _skill_tooltip.size.x)
	var th: float = maxf(78.0, _skill_tooltip.size.y)
	var vp: Vector2 = get_viewport_rect().size
	var tx: float = clampf(gp.x + slot.size.x * 0.5 - tw * 0.5, 16.0, maxf(20.0, vp.x - tw - 16.0))
	var ty: float = maxf(68.0, gp.y - th - 12.0)
	_skill_tooltip.position = Vector2(tx, ty)


func _hide_skill_tooltip() -> void:
	if _skill_tooltip != null:
		_skill_tooltip.visible = false


func _build_phase_toast() -> void:
	# Cinematic phase banner: a full-width dark band with gold rules that sweeps in from the left,
	# holds, then fades. Replaces the old static pill so phase changes feel like a cut-scene beat.
	_phase_toast = PanelContainer.new()
	_phase_toast.anchor_left = 0.0
	_phase_toast.anchor_right = 1.0
	_phase_toast.anchor_top = 0.5
	_phase_toast.anchor_bottom = 0.5
	_phase_toast.offset_left = 0
	_phase_toast.offset_right = 0
	_phase_toast.offset_top = -150
	_phase_toast.offset_bottom = -78
	_phase_toast.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_phase_toast.visible = false
	var banner_tex := KTTheme.kit_tex("banner_phase")
	if banner_tex != null:
		# Kit banner: 2048x512 with alpha fade at both ends; stretch only the centre so the fades stay crisp
		var sbt := StyleBoxTexture.new()
		sbt.texture = banner_tex
		sbt.texture_margin_left = 512
		sbt.texture_margin_right = 512
		sbt.texture_margin_top = 0
		sbt.texture_margin_bottom = 0
		sbt.content_margin_top = 10
		sbt.content_margin_bottom = 10
		_phase_toast.add_theme_stylebox_override("panel", sbt)
		_phase_toast.offset_top = -170
		_phase_toast.offset_bottom = -58
	else:
		var band := StyleBoxFlat.new()
		band.bg_color = Color(0.03, 0.025, 0.02, 0.88)
		band.border_color = KTTheme.COL_GOLD_BRIGHT
		band.border_width_top = 2
		band.border_width_bottom = 2
		band.content_margin_top = 10
		band.content_margin_bottom = 10
		_phase_toast.add_theme_stylebox_override("panel", band)
	add_child(_phase_toast)

	_phase_toast_lbl = Label.new()
	_phase_toast_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_phase_toast_lbl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_phase_toast_lbl.add_theme_font_size_override("font_size", 30)
	_phase_toast_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	_phase_toast_lbl.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.8))
	_phase_toast_lbl.add_theme_constant_override("shadow_offset_x", 2)
	_phase_toast_lbl.add_theme_constant_override("shadow_offset_y", 2)
	_phase_toast.add_child(_phase_toast_lbl)


func _build_log_drawer() -> void:
	_log_drawer = PanelContainer.new()
	_log_drawer.anchor_left = 1.0
	_log_drawer.anchor_right = 1.0
	_log_drawer.anchor_top = 0.0
	_log_drawer.anchor_bottom = 1.0
	_log_drawer.offset_left = -400
	_log_drawer.offset_right = -14
	_log_drawer.offset_top = 64
	_log_drawer.offset_bottom = -168
	_log_drawer.visible = false
	_log_drawer.add_theme_stylebox_override(
		"panel",
		KTTheme.make_panel_style(Color(0.032, 0.042, 0.064, 0.94), KTTheme.COL_GOLD, 10, 2, 10, true)
	)
	add_child(_log_drawer)

	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 6)
	_log_drawer.add_child(vb)

	var hdr := HBoxContainer.new()
	vb.add_child(hdr)
	var lbl := Label.new()
	lbl.text = "📜 实时战斗行动日志"
	lbl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	hdr.add_child(lbl)
	var close_b := Button.new()
	close_b.focus_mode = Control.FOCUS_NONE
	close_b.text = "✕"
	KTWidgets.attach_button_juice(close_b, 1.08)
	close_b.pressed.connect(func(): _log_drawer.visible = false)
	hdr.add_child(close_b)

	_log_rtl = RichTextLabel.new()
	_log_rtl.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_log_rtl.scroll_following = true
	_log_rtl.add_theme_font_size_override("normal_font_size", 12)
	vb.add_child(_log_rtl)


# ==============================================================================
# ASYMMETRIC AAA VIDEO-GAME MAIN MENU OVERLAY & HELP MODAL
# ==============================================================================

func _build_title_overlay() -> void:
	_title_overlay = Control.new()
	_title_overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_title_overlay.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(_title_overlay)

	# Kit keyart behind the whole title screen (the live 3D diorama still renders beneath, so keep it
	# at partial alpha: the art sets the mood, the table shows through on the right).
	var keyart := KTTheme.kit_tex("title_keyart")
	if keyart != null:
		var ka := TextureRect.new()
		ka.texture = keyart
		ka.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		ka.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		ka.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		ka.modulate = Color(1, 1, 1, 0.94)
		ka.mouse_filter = Control.MOUSE_FILTER_IGNORE
		_title_overlay.add_child(ka)

	# Left-only dark command scrim so the entire Right 58% of the screen showcases
	# the live orbiting 3D HD-2D Volkus Killzone Diorama completely unobstructed!
	var left_blade := PanelContainer.new()
	left_blade.anchor_left = 0.0
	left_blade.anchor_right = 0.0
	left_blade.anchor_top = 0.0
	left_blade.anchor_bottom = 1.0
	left_blade.offset_left = 24.0
	left_blade.offset_right = 668.0
	left_blade.offset_top = 22.0
	left_blade.offset_bottom = -22.0
	var blade_sb: StyleBox = KTTheme.kit_panel("panel_primary", 18)
	left_blade.add_theme_stylebox_override(
		"panel",
		blade_sb if blade_sb != null else KTTheme.make_panel_style(Color(0.028, 0.036, 0.058, 0.93), KTTheme.COL_GOLD, 14, 2, 18, true, KTTheme.COL_GOLD_BRIGHT)
	)
	_title_overlay.add_child(left_blade)

	# Floating Diorama Showcase Badge in Top-Right Open 3D Stage!
	var stage_badge := PanelContainer.new()
	stage_badge.anchor_left = 1.0
	stage_badge.anchor_right = 1.0
	stage_badge.anchor_top = 0.0
	stage_badge.anchor_bottom = 0.0
	stage_badge.offset_left = -460.0
	stage_badge.offset_right = -24.0
	stage_badge.offset_top = 24.0
	stage_badge.offset_bottom = 66.0
	stage_badge.mouse_filter = Control.MOUSE_FILTER_IGNORE
	stage_badge.add_theme_stylebox_override(
		"panel",
		KTTheme.make_panel_style(Color(0.025, 0.032, 0.052, 0.80), KTTheme.COL_BRASS, 10, 1, 8)
	)
	_title_overlay.add_child(stage_badge)

	var sb_lbl := Label.new()
	sb_lbl.text = "🗺️ 实时演算战区：沃库斯 (30\"×22\") · 认证行动 2025 官方地图 1–6"
	sb_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	sb_lbl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	sb_lbl.add_theme_font_size_override("font_size", 12)
	sb_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	stage_badge.add_child(sb_lbl)

	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 11)
	left_blade.add_child(vb)

	# Aquila Winged Skull + Video-Game Title Crest
	var skull_row := HBoxContainer.new()
	skull_row.add_theme_constant_override("separation", 14)
	vb.add_child(skull_row)

	var skull := TextureRect.new()
	var emblem := KTTheme.kit_tex("title_emblem")
	skull.texture = emblem if emblem != null else KTData.load_tex("res://assets/ui/header_winged_skull.png")
	skull.custom_minimum_size = Vector2(72, 72) if emblem != null else Vector2(108, 44)
	skull.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	skull.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	skull_row.add_child(skull)

	var title_vb := VBoxContainer.new()
	title_vb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	skull_row.add_child(title_vb)

	var h1 := Label.new()
	h1.text = "WARHAMMER 40,000 : KILL TEAM"
	h1.add_theme_font_size_override("font_size", 13)
	h1.add_theme_color_override("font_color", KTTheme.COL_BRASS)
	title_vb.add_child(h1)

	var h2 := Label.new()
	h2.text = "杀戮小队 · HD-2D 战棋对决"
	h2.add_theme_font_size_override("font_size", 26)
	h2.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	title_vb.add_child(h2)

	# Two Stacked Rich Faction Cards (Angels of Death & Plague Marines)
	var teams_vb := VBoxContainer.new()
	teams_vb.size_flags_vertical = Control.SIZE_EXPAND_FILL
	teams_vb.add_theme_constant_override("separation", 10)
	vb.add_child(teams_vb)

	teams_vb.add_child(_build_title_team_card("IMP-AOD", true))
	teams_vb.add_child(_build_title_team_card("CHAOS-PM", false))

	# Mode & Faction Control Blades
	var cfg_hb := HBoxContainer.new()
	cfg_hb.alignment = BoxContainer.ALIGNMENT_CENTER
	cfg_hb.add_theme_constant_override("separation", 8)
	vb.add_child(cfg_hb)

	var btn_ai := Button.new()
	btn_ai.focus_mode = Control.FOCUS_NONE
	var btn_hot := Button.new()
	btn_hot.focus_mode = Control.FOCUS_NONE
	var btn_side_a := Button.new()
	btn_side_a.focus_mode = Control.FOCUS_NONE
	var btn_side_b := Button.new()
	btn_side_b.focus_mode = Control.FOCUS_NONE
	for mb in [btn_ai, btn_hot, btn_side_a, btn_side_b]:
		mb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		mb.custom_minimum_size = Vector2(0, 36)
		mb.add_theme_font_size_override("font_size", 12)
		KTWidgets.attach_button_juice(mb, 1.05)

	var refresh_cfg_btns := func():
		btn_ai.text = "✓ 🤖 单人战役 vs AI" if _sel_mode == "ai" else "🤖 单人战役 vs AI"
		btn_hot.text = "✓ 👥 双人同屏对决" if _sel_mode == "hotseat" else "👥 双人同屏对决"
		KTTheme.apply_accent_button(btn_ai, KTTheme.COL_GOLD_BRIGHT if _sel_mode == "ai" else KTTheme.COL_MUTED, 0.30 if _sel_mode == "ai" else 0.14)
		KTTheme.apply_accent_button(btn_hot, KTTheme.COL_GOLD_BRIGHT if _sel_mode == "hotseat" else KTTheme.COL_MUTED, 0.30 if _sel_mode == "hotseat" else 0.14)

		btn_side_a.visible = (_sel_mode == "ai")
		btn_side_b.visible = (_sel_mode == "ai")
		btn_side_a.text = "✓ 执掌：死亡天使" if _sel_human_side == "A" else "执掌：死亡天使"
		btn_side_b.text = "✓ 执掌：瘟疫战士" if _sel_human_side == "B" else "执掌：瘟疫战士"
		KTTheme.apply_accent_button(btn_side_a, KTTheme.COL_IMPERIAL if _sel_human_side == "A" else KTTheme.COL_MUTED, 0.30 if _sel_human_side == "A" else 0.14)
		KTTheme.apply_accent_button(btn_side_b, KTTheme.COL_NURGLE if _sel_human_side == "B" else KTTheme.COL_MUTED, 0.30 if _sel_human_side == "B" else 0.14)

	btn_ai.pressed.connect(func():
		_sel_mode = "ai"
		refresh_cfg_btns.call()
	)
	btn_hot.pressed.connect(func():
		_sel_mode = "hotseat"
		refresh_cfg_btns.call()
	)
	btn_side_a.pressed.connect(func():
		_sel_human_side = "A"
		refresh_cfg_btns.call()
	)
	btn_side_b.pressed.connect(func():
		_sel_human_side = "B"
		refresh_cfg_btns.call()
	)

	cfg_hb.add_child(btn_ai)
	cfg_hb.add_child(btn_hot)
	cfg_hb.add_child(btn_side_a)
	cfg_hb.add_child(btn_side_b)
	refresh_cfg_btns.call()

	# Killzone Map Selector (Approved Ops 2025 · Volkus)
	var map_vb := VBoxContainer.new()
	map_vb.add_theme_constant_override("separation", 5)
	vb.add_child(map_vb)

	var map_hdr_lbl := Label.new()
	map_hdr_lbl.text = "🗺️ 选择杀戮区地图（认证行动 2025 · 沃库斯）"
	map_hdr_lbl.add_theme_font_size_override("font_size", 13)
	map_hdr_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	map_vb.add_child(map_hdr_lbl)

	var map_hb := HBoxContainer.new()
	map_hb.add_theme_constant_override("separation", 6)
	map_vb.add_child(map_hb)

	var map_hint_lbl := Label.new()
	map_hint_lbl.add_theme_font_size_override("font_size", 12)
	map_hint_lbl.add_theme_color_override("font_color", KTTheme.COL_MUTED)
	map_vb.add_child(map_hint_lbl)

	var map_btns: Dictionary = {}
	var refresh_map_btns := func() -> void:
		for mid_k in map_btns.keys():
			var mid_s: String = str(mid_k)
			var mb_item: Button = map_btns[mid_k]
			var is_sel: bool = (mid_s == _sel_map_id)
			var short_lbl: String = KTData.map_short_label(mid_s)
			mb_item.text = ("✓ " + short_lbl) if is_sel else short_lbl
			KTTheme.apply_accent_button(mb_item, KTTheme.COL_GOLD_BRIGHT if is_sel else KTTheme.COL_MUTED, 0.30 if is_sel else 0.14)
		var hint: String = KTData.map_deploy_hint(_sel_map_id)
		if _battle_in_progress and _sel_map_id != str(engine.state["map"].get("id", "")):
			hint += " · 新开一局后生效，当前战局不受影响"
		map_hint_lbl.text = hint

	for mid in KTData.MAP_IDS:
		var mid_str: String = str(mid)
		var mbtn := Button.new()
		mbtn.focus_mode = Control.FOCUS_NONE
		mbtn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		mbtn.custom_minimum_size = Vector2(0, 34)
		mbtn.add_theme_font_size_override("font_size", 12)
		KTWidgets.attach_button_juice(mbtn, 1.05)
		mbtn.pressed.connect(func() -> void:
			_sel_map_id = mid_str
			refresh_map_btns.call()
			if not _battle_in_progress:
				_restart_menu_backdrop()
		)
		map_btns[mid_str] = mbtn
		map_hb.add_child(mbtn)
	refresh_map_btns.call()

	# Heroic Launch Blade Row
	var launch_row := HBoxContainer.new()
	launch_row.add_theme_constant_override("separation", 12)
	vb.add_child(launch_row)

	var btn_start := Button.new()
	btn_start.focus_mode = Control.FOCUS_NONE
	btn_start.text = "⚔️  开始空投部署 · 进入杀戮战区"
	btn_start.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	btn_start.custom_minimum_size = Vector2(0, 52)
	btn_start.add_theme_font_size_override("font_size", 19)
	KTTheme.apply_accent_button(btn_start, KTTheme.COL_GOLD_BRIGHT, 0.36)
	KTWidgets.attach_button_juice(btn_start, 1.04, "phase_horn")
	btn_start.pressed.connect(func():
		_play_deploy_transition()
	)
	launch_row.add_child(btn_start)

	var btn_resume := Button.new()
	btn_resume.focus_mode = Control.FOCUS_NONE
	btn_resume.text = "▶ 继续战局"
	btn_resume.custom_minimum_size = Vector2(132, 52)
	KTWidgets.attach_button_juice(btn_resume, 1.05)
	btn_resume.pressed.connect(func():
		_title_overlay.visible = false
		_killzone.reset_isometric_view(false)
		_on_viewport_resized()
		_refresh_all_ui()
	)
	launch_row.add_child(btn_resume)


func _build_title_team_card(team_id: String, is_aod: bool) -> PanelContainer:
	var tdata: Dictionary = KTData.get_team(team_id)
	var col: Color = KTTheme.COL_IMPERIAL if is_aod else KTTheme.COL_NURGLE
	var p := PanelContainer.new()
	p.size_flags_vertical = Control.SIZE_EXPAND_FILL
	p.clip_contents = true
	p.add_theme_stylebox_override("panel", KTTheme.make_panel_style(Color(0.052, 0.066, 0.096, 0.95), col, 10, 2, 0, true))

	var stack := MarginContainer.new()
	p.add_child(stack)

	var splash := TextureRect.new()
	var kit_splash := KTTheme.kit_tex("splash_imperial" if is_aod else "splash_nurgle")
	splash.texture = kit_splash if kit_splash != null else KTData.load_tex("res://assets/ui/kt_aod.png" if is_aod else "res://assets/ui/kt_pm.png")
	splash.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	splash.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	splash.modulate = Color(1.0, 1.0, 1.0, 0.34 if kit_splash != null else 0.22)
	splash.mouse_filter = Control.MOUSE_FILTER_IGNORE
	stack.add_child(splash)

	var inner_pad := MarginContainer.new()
	inner_pad.add_theme_constant_override("margin_left", 14)
	inner_pad.add_theme_constant_override("margin_right", 14)
	inner_pad.add_theme_constant_override("margin_top", 10)
	inner_pad.add_theme_constant_override("margin_bottom", 10)
	stack.add_child(inner_pad)

	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 8)
	inner_pad.add_child(vb)

	var hdr := HBoxContainer.new()
	hdr.add_theme_constant_override("separation", 10)
	vb.add_child(hdr)

	var mark := TextureRect.new()
	mark.texture = KTData.load_tex("res://assets/ui/team_mark_imp_aod.png" if is_aod else "res://assets/ui/team_mark_chaos_pm.png")
	mark.custom_minimum_size = Vector2(38, 38)
	mark.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	mark.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	hdr.add_child(mark)

	var title_v := VBoxContainer.new()
	title_v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	title_v.add_theme_constant_override("separation", 1)
	hdr.add_child(title_v)

	var nm := Label.new()
	nm.text = "%s · %s" % [str(tdata.get("name", "")), str(tdata.get("name_en", ""))]
	nm.add_theme_font_size_override("font_size", 17)
	nm.add_theme_color_override("font_color", col.lightened(0.32))
	title_v.add_child(nm)

	var sb_l := Label.new()
	sb_l.text = str(tdata.get("subtitle", "")) + " · 6 人精英小队"
	sb_l.add_theme_font_size_override("font_size", 11)
	sb_l.add_theme_color_override("font_color", KTTheme.COL_MUTED)
	title_v.add_child(sb_l)

	var btn_cards := Button.new()
	btn_cards.focus_mode = Control.FOCUS_NONE
	btn_cards.text = "📖 原卡库"
	btn_cards.add_theme_font_size_override("font_size", 11)
	KTWidgets.attach_button_juice(btn_cards, 1.06, "card_lift")
	btn_cards.pressed.connect(func(): _card_lightbox.show_team_rulebook(team_id))
	hdr.add_child(btn_cards)

	# 6 Canonical Operatives Standee Showcase Row
	var ports_hb := HBoxContainer.new()
	ports_hb.add_theme_constant_override("separation", 6)
	vb.add_child(ports_hb)

	var ops_map: Dictionary = {}
	for o in tdata.get("operatives", []):
		var od: Dictionary = o
		ops_map[str(od["id"])] = od
	for rid in tdata.get("canonical_roster", []):
		var rid_s: String = str(rid)
		if not ops_map.has(rid_s):
			continue
		var op_d: Dictionary = ops_map[rid_s]
		var op_btn := Button.new()
		op_btn.focus_mode = Control.FOCUS_NONE
		op_btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		op_btn.custom_minimum_size = Vector2(0, 74)
		op_btn.tooltip_text = "点击查看【%s】高清中文数据卡" % str(op_d["name"])
		KTWidgets.attach_button_juice(op_btn, 1.08, "card_lift")
		op_btn.pressed.connect(func(): _card_lightbox.show_operative_cards(op_d))

		var ov := VBoxContainer.new()
		ov.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		ov.mouse_filter = Control.MOUSE_FILTER_IGNORE
		ov.alignment = BoxContainer.ALIGNMENT_CENTER
		ov.add_theme_constant_override("separation", 2)
		op_btn.add_child(ov)

		var tr := TextureRect.new()
		var st_res: String = str(op_d.get("standee", ""))
		tr.texture = KTData.load_tex(st_res if not st_res.is_empty() else str(op_d["portrait"]))
		tr.custom_minimum_size = Vector2(54, 46)
		tr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		tr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
		ov.add_child(tr)

		var rl := Label.new()
		rl.text = str(op_d["role"])
		rl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		rl.add_theme_font_size_override("font_size", 10)
		ov.add_child(rl)

		ports_hb.add_child(op_btn)

	if is_aod:
		var tac_flow := HFlowContainer.new()
		tac_flow.add_theme_constant_override("h_separation", 5)
		tac_flow.add_theme_constant_override("v_separation", 5)
		vb.add_child(tac_flow)

		var tac_btns: Dictionary = {}
		var refresh_tacs := func():
			for tid in tac_btns.keys():
				var b: Button = tac_btns[tid]
				var tid_s: String = str(tid)
				var active: bool = (tid_s == _sel_primary_tactic or tid_s == _sel_secondary_tactic)
				KTTheme.apply_accent_button(b, KTTheme.COL_IMPERIAL if active else KTTheme.COL_MUTED, 0.34 if active else 0.12)

		for ct in KTData.CHAPTER_TACTICS:
			var ctd: Dictionary = ct
			var tid: String = str(ctd["id"])
			var tb := Button.new()
			tb.focus_mode = Control.FOCUS_NONE
			tb.text = str(ctd["short"])
			tb.add_theme_font_size_override("font_size", 11)
			tb.tooltip_text = str(ctd["name"]) + "：" + str(ctd["desc"])
			KTWidgets.attach_button_juice(tb, 1.06)
			tb.pressed.connect(func():
				if tid != _sel_primary_tactic and tid != _sel_secondary_tactic:
					_sel_secondary_tactic = _sel_primary_tactic
					_sel_primary_tactic = tid
					refresh_tacs.call()
			)
			tac_btns[tid] = tb
			tac_flow.add_child(tb)
		refresh_tacs.call()
	else:
		var pm_lbl := Label.new()
		pm_lbl.text = "☣️ 纳垢毒素持续掉血  ｜  🦠 恼人韧性 4+ 减伤  ｜  🔔 瘟疫之钟全军无视受创惩罚"
		pm_lbl.add_theme_font_size_override("font_size", 11)
		pm_lbl.add_theme_color_override("font_color", KTTheme.COL_NURGLE.lightened(0.28))
		pm_lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		vb.add_child(pm_lbl)

	return p


## Settings sheet: the handful of KTSettings keys, each with a plain control. Opened from ⚙.
var _settings_modal: Control = null

func _open_settings() -> void:
	if _ai_timer != null:
		_ai_timer.stop()
	if _killzone != null:
		_killzone.clear_interaction()
	_hide_skill_tooltip()
	if _settings_modal == null:
		_settings_modal = Control.new()
		_settings_modal.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		_settings_modal.z_index = 150
		add_child(_settings_modal)

		var bg := ColorRect.new()
		bg.color = Color(0.02, 0.025, 0.035, 0.80)
		bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		bg.gui_input.connect(func(ev: InputEvent):
			if ev is InputEventMouseButton and ev.pressed:
				_settings_modal.visible = false
				_refresh_all_ui()
		)
		_settings_modal.add_child(bg)

		var center := CenterContainer.new()
		center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		center.mouse_filter = Control.MOUSE_FILTER_IGNORE
		_settings_modal.add_child(center)

		var card := PanelContainer.new()
		card.custom_minimum_size = Vector2(520, 0)
		card.add_theme_stylebox_override("panel", KTTheme.panel("primary", KTTheme.COL_GOLD))
		center.add_child(card)

		var vb := VBoxContainer.new()
		vb.add_theme_constant_override("separation", 14)
		card.add_child(vb)

		var hdr := Label.new()
		hdr.text = "设置"
		hdr.add_theme_font_override("font", KTTheme.get_display_font())
		hdr.add_theme_font_size_override("font_size", KTTheme.FS_DISPLAY)
		hdr.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
		vb.add_child(hdr)

		# Rule step pause
		var row1 := _settings_row(vb, "结算时每条规则停留", "骰子落定后，每条触发的规则依次显示并高亮对应骰子。0 为全部一次显示。")
		var pause_opt := OptionButton.new()
		pause_opt.focus_mode = Control.FOCUS_NONE
		for v in [0.0, 0.15, 0.3, 0.5, 0.8]:
			pause_opt.add_item(("关闭" if v == 0.0 else "%.2f 秒" % v).replace(".00", "").replace("0 秒", " 秒"), int(v * 100))
		var cur_p := int(round(KTSettings.rule_step_pause() * 100.0))
		for i in range(pause_opt.item_count):
			if pause_opt.get_item_id(i) == cur_p:
				pause_opt.select(i)
		pause_opt.item_selected.connect(func(i: int): KTSettings.set_value("combat/rule_step_pause", float(pause_opt.get_item_id(i)) / 100.0))
		row1.add_child(pause_opt)

		# Kill cam
		var row2 := _settings_row(vb, "击杀慢放镜头", "特工阵亡时短暂慢放并推近。")
		var kc := CheckButton.new()
		kc.focus_mode = Control.FOCUS_NONE
		kc.button_pressed = bool(KTSettings.get_value("combat/kill_cam"))
		kc.toggled.connect(func(on: bool): KTSettings.set_value("combat/kill_cam", on))
		row2.add_child(kc)

		# Miniature style
		var row_m := _settings_row(vb, "棋子表现", "3D 涂装模型（官方造型）或亚克力立牌（卡图胸像）。切换后重开一局生效。")
		var mini_opt := OptionButton.new()
		mini_opt.focus_mode = Control.FOCUS_NONE
		mini_opt.add_item("3D 涂装模型", 0)
		mini_opt.add_item("亚克力立牌", 1)
		mini_opt.select(0 if str(KTSettings.get_value("board/mini_style")) == "model" else 1)
		mini_opt.item_selected.connect(func(i: int):
			KTSettings.set_value("board/mini_style", "model" if i == 0 else "standee")
			_killzone.rebuild_operatives()
		)
		row_m.add_child(mini_opt)

		# Inch grid
		var row3 := _settings_row(vb, "显示英寸格线", "桌面上的 1\" 战术格与 6\" 铜线。")
		var grid := CheckButton.new()
		grid.focus_mode = Control.FOCUS_NONE
		grid.button_pressed = bool(KTSettings.get_value("hud/show_inch_grid"))
		grid.toggled.connect(func(on: bool):
			KTSettings.set_value("hud/show_inch_grid", on)
			_killzone.set_grid_visible(on)
		)
		row3.add_child(grid)

		var ok := Button.new()
		ok.focus_mode = Control.FOCUS_NONE
		ok.text = "完成"
		KTTheme.apply_accent_button(ok, KTTheme.COL_GOLD_BRIGHT, 0.30)
		KTWidgets.attach_button_juice(ok, 1.04)
		ok.pressed.connect(func():
			_settings_modal.visible = false
			_refresh_all_ui()
		)
		vb.add_child(ok)
	_settings_modal.visible = true
	move_child(_settings_modal, get_child_count() - 1)
	_refresh_all_ui()


func _settings_row(parent: Control, title: String, hint: String) -> HBoxContainer:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 12)
	parent.add_child(row)
	var tv := VBoxContainer.new()
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tv.add_theme_constant_override("separation", 1)
	row.add_child(tv)
	var t := Label.new()
	t.text = title
	t.add_theme_font_size_override("font_size", KTTheme.FS_HEADING)
	t.add_theme_color_override("font_color", KTTheme.COL_TEXT)
	tv.add_child(t)
	var h := Label.new()
	h.text = hint
	h.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	h.add_theme_font_size_override("font_size", KTTheme.FS_CAPTION)
	h.add_theme_color_override("font_color", KTTheme.COL_MUTED)
	tv.add_child(h)
	return row


func _build_help_modal() -> void:
	_help_modal = Control.new()
	_help_modal.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_help_modal.visible = false
	add_child(_help_modal)

	var bg := ColorRect.new()
	bg.color = Color(0.02, 0.025, 0.035, 0.85)
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.gui_input.connect(func(ev: InputEvent):
		if ev is InputEventMouseButton and ev.pressed:
			_help_modal.visible = false
			_refresh_all_ui()
	)
	_help_modal.add_child(bg)

	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_help_modal.add_child(center)

	var card := PanelContainer.new()
	card.custom_minimum_size = Vector2(780, 520)
	card.add_theme_stylebox_override("panel", KTTheme.make_panel_style(KTTheme.COL_PANEL_ELEV, KTTheme.COL_GOLD, 12, 2, 22, true))
	center.add_child(card)

	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 12)
	card.add_child(vb)

	var hdr := Label.new()
	hdr.text = "❓ 战棋操控热键与电子规则指南"
	hdr.add_theme_font_size_override("font_size", 22)
	hdr.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	vb.add_child(hdr)

	var rtl := RichTextLabel.new()
	rtl.bbcode_enabled = true
	rtl.size_flags_vertical = Control.SIZE_EXPAND_FILL
	rtl.text = (
		"[color=#facc15]1. 战棋快捷键与镜头操控[/color]\n" +
		"  • [b]数字键 1 ~ 9[/b]：一键触发底部八角技能槽（转移 / 冲刺 / 冲锋 / 后撤 / 射击 / 近战 / 占领 / 特技 / 计谋）！\n" +
		"  • [b]Space 空格键[/b]：投掷先手骰 / 跳过战略出牌 / 结束当前特工激活 / 推进骰子结算。\n" +
		"  • [b]C 键[/b]：行动前一键切换【隐匿/交战】姿态； [b]Tab 键[/b]：弹出当前特工官方中文数据卡； [b]Z 键[/b]：无限步撤回。\n" +
		"  • [b]按住右键拖拽 / Q·E 键 / 滚轮 / WASD[/b]：360° 自由环绕与缩放 2.55D 立体杀戮区。\n\n" +
		"[color=#facc15]2. 三维高低差攀爬与落点视线预瞄[/color]\n" +
		"  • 按 [b][1] 转移[/b] 指向二层露台（+3.5\"）自动吸附并折算 +2\" 攀爬距离；[b]Shift+左键[/b] 添加绕墙拐点。\n\n" +
		"[color=#facc15]3. 真实 3D 桌面天鹅绒骰盘与交互重掷[/color]\n" +
		"  • 先攻权对抗、远程射击与近战格斗的所有 D6 骰子均真实抛投在战棋桌前沿的 3D 黄铜天鹅绒骰盘中！\n" +
		"  • 可直接[b]左键点击桌面上的 3D 骰子[/b]选中并执行平衡/指挥重掷，亦可右键拖拽环绕或滚轮微距检视骰面！"
	)
	vb.add_child(rtl)

	var ok_b := Button.new()
	ok_b.focus_mode = Control.FOCUS_NONE
	ok_b.text = "明白 · 返回杀戮区"
	KTTheme.apply_accent_button(ok_b, KTTheme.COL_GOLD_BRIGHT, 0.30)
	KTWidgets.attach_button_juice(ok_b, 1.04)
	ok_b.pressed.connect(func():
		_help_modal.visible = false
		_refresh_all_ui()
	)
	vb.add_child(ok_b)


# ==============================================================================
# GAMEPLAY ORCHESTRATION & DYNAMIC VIDEO-GAME HUD REFRESH
# ==============================================================================

## Deploy transition: the menu blade slides off-screen left while the camera swoops from the
## slow menu orbit down to the battle isometric view, then the HUD fades in. Gives "pressing start"
## a cinematic beat instead of a hard cut.
var _deploy_fade: ColorRect = null

func _play_deploy_transition() -> void:
	if _deploy_fade == null:
		_deploy_fade = ColorRect.new()
		_deploy_fade.color = Color(0, 0, 0, 0)
		_deploy_fade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		_deploy_fade.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(_deploy_fade)
	_deploy_fade.visible = true
	move_child(_deploy_fade, get_child_count() - 1)
	var tw := create_tween()
	tw.set_parallel(false)
	# Slide the left blade out and dim
	tw.tween_property(_title_overlay, "position:x", -720.0, 0.32).set_ease(Tween.EASE_IN).set_trans(Tween.TRANS_CUBIC)
	tw.parallel().tween_property(_deploy_fade, "color:a", 0.85, 0.32)
	tw.tween_callback(func():
		_title_overlay.visible = false
		_title_overlay.position.x = 0.0
		_start_new_battle()
	)
	tw.tween_property(_deploy_fade, "color:a", 0.0, 0.55).set_ease(Tween.EASE_OUT)
	tw.tween_callback(func(): _deploy_fade.visible = false)


## Menu backdrop only: a throw-away match on the selected map so the 3D diorama behind the title
## screen is alive. Skips the interactive pre-battle steps.
func _restart_menu_backdrop() -> void:
	_pre_ai_undo_stack.clear()
	_in_ai_undo_guard = false
	if _dice_modal != null:
		_dice_modal.visible = false
		_dice_modal.reset_for_new_match()
	if _killzone != null:
		_killzone.clear_transient_combat_fx()
		_killzone.clear_interaction()
	engine.start_match(_sel_mode, _sel_human_side, _sel_primary_tactic, _sel_secondary_tactic, _sel_map_id)
	engine.auto_deploy_all()
	_killzone.bind_engine(engine)
	_refresh_all_ui()


func _return_to_main_menu() -> void:
	if _ai_timer != null:
		_ai_timer.stop()
	if _dice_modal != null:
		var was_combat: bool = _dice_modal.visible and _dice_modal._modal_mode in ["shooting", "melee"]
		_dice_modal.visible = false
		_dice_modal.reset_for_new_match()
		if was_combat and not engine.state.is_empty():
			var act_uid: String = str(engine.state.get("active_op_uid", ""))
			if not act_uid.is_empty():
				var active_op: Dictionary = engine.get_op(act_uid)
				if active_op.is_empty() or bool(active_op["incapacitated"]) or (bool(engine.state["active_is_counteract"]) and int(active_op["ap_left"]) <= 0):
					engine.end_operative_activation()
	if _killzone != null:
		_killzone.clear_transient_combat_fx()
		_killzone.clear_interaction()
	if _card_lightbox != null:
		_card_lightbox.hide()
	if _help_modal != null:
		_help_modal.visible = false
	if _settings_modal != null:
		_settings_modal.visible = false
	_title_overlay.visible = true
	_on_viewport_resized()
	_refresh_all_ui()


func _start_new_battle() -> void:
	_ai_timer.stop()
	_pre_ai_undo_stack.clear()
	_in_ai_undo_guard = false
	if _dice_modal != null:
		_dice_modal.visible = false
		_dice_modal.reset_for_new_match()
	if _killzone != null:
		_killzone.clear_transient_combat_fx()
		_killzone.clear_interaction()
	if _card_lightbox != null:
		_card_lightbox.hide()
	if _help_modal != null:
		_help_modal.visible = false
	if _settings_modal != null:
		_settings_modal.visible = false
	_hide_skill_tooltip()
	_hero_uid = ""
	for m in _medallions_by_uid.values():
		if is_instance_valid(m):
			(m as Control).queue_free()
	_medallions_by_uid.clear()

	_battle_in_progress = true
	engine.start_match(_sel_mode, _sel_human_side, _sel_primary_tactic, _sel_secondary_tactic, _sel_map_id)
	_killzone.bind_engine(engine)
	# Camera swoops from wherever the menu orbit left it into the isometric battle view.
	_killzone.reset_isometric_view(false)
	_on_viewport_resized()
	_refresh_all_ui()


func _sync_ai_undo_guard() -> void:
	if engine.state.is_empty() or str(engine.state.get("mode", "")) != "ai":
		_in_ai_undo_guard = false
		_pre_ai_undo_stack.clear()
		return
	if engine.is_ai_turn():
		if not _in_ai_undo_guard:
			_pre_ai_undo_stack = engine.undo_stack.duplicate()
			_in_ai_undo_guard = true
		else:
			engine.undo_stack = _pre_ai_undo_stack.duplicate()
	elif _in_ai_undo_guard:
		engine.undo_stack = _pre_ai_undo_stack.duplicate()
		_in_ai_undo_guard = false


func _can_human_undo() -> bool:
	if engine.state.is_empty() or engine.is_ai_turn():
		return false
	return engine.can_undo()


func _end_human_activation() -> void:
	if engine.state.is_empty() or engine.is_ai_turn():
		return
	if str(engine.state.get("active_op_uid", "")).is_empty():
		return
	KTSfx.play("move_confirm", -5.0, 0.04)
	_killzone.clear_interaction()
	_hide_skill_tooltip()
	engine.push_undo()
	engine.end_operative_activation()
	_refresh_all_ui()


func _safe_board_pos(pos_in: Vector2) -> Vector2:
	if pos_in.x < 0.0 or pos_in.y < 0.0 or pos_in.x > KTData.BOARD_WIDTH_INCHES or pos_in.y > KTData.BOARD_HEIGHT_INCHES:
		var act_uid: String = str(engine.state.get("active_op_uid", "")) if not engine.state.is_empty() else ""
		if not act_uid.is_empty():
			var aop: Dictionary = engine.get_op(act_uid)
			if not aop.is_empty():
				var ap: Vector2 = aop.get("pos", Vector2(-99.0, -99.0))
				if ap.x >= 0.0 and ap.y >= 0.0 and ap.x <= KTData.BOARD_WIDTH_INCHES and ap.y <= KTData.BOARD_HEIGHT_INCHES:
					return ap
		return Vector2(15.0, 11.0)
	return pos_in


func _notify_illegal_click(msg: String, pos_in: Vector2 = Vector2(-99.0, -99.0), col: Color = KTTheme.COL_CRIMSON) -> void:
	KTSfx.play("ui_hover", -14.0, 0.2)
	if _killzone != null and not msg.strip_edges().is_empty():
		_killzone.spawn_floating_text(_safe_board_pos(pos_in), msg, col)


func _on_undo_pressed() -> void:
	if engine.is_ai_turn():
		_notify_illegal_click("敌方回合中无法撤回")
		return
	_ai_timer.stop()
	if _dice_modal.visible:
		_dice_modal.visible = false
		_dice_modal.reset_for_new_match()
		_killzone.restore_post_combat_camera()
	_hide_skill_tooltip()
	var prev_state_snap: String = var_to_str(engine.state)
	var prev_stack: Array[String] = engine.undo_stack.duplicate()
	if engine.pop_undo():
		if str(engine.state.get("mode", "")) == "ai":
			while engine.is_ai_turn() and engine.can_undo():
				engine.pop_undo()
			if engine.is_ai_turn():
				var fallback = str_to_var(prev_state_snap)
				if typeof(fallback) == TYPE_DICTIONARY:
					engine.state = fallback
				engine.undo_stack = prev_stack
				return
		_in_ai_undo_guard = false
		_pre_ai_undo_stack.clear()
		_killzone.sync_visual_positions(true)
		_killzone.clear_interaction()
		_refresh_all_ui()


func _refresh_all_ui() -> void:
	var on_title: bool = (_title_overlay != null and _title_overlay.visible)
	var in_combat: bool = (_dice_modal != null and _dice_modal.visible)
	var hud_vis: bool = (not on_title) and (not in_combat)

	if _top_crest != null:
		_top_crest.visible = hud_vis
	if _sys_bar != null:
		_sys_bar.visible = not on_title
	if _tactical_pill != null:
		_tactical_pill.visible = hud_vis
	# Only the acting side's roster is docked on screen; the opponent is read off the board itself
	# (tokens + compact tags). In hotseat the roster swaps sides with the turn.
	var roster_side: String = str(engine.state.get("turn_side", "A")) if not engine.state.is_empty() else "A"
	if _left_squad_box != null:
		_left_squad_box.visible = hud_vis and roster_side == "A"
	if _right_squad_box != null:
		_right_squad_box.visible = hud_vis and roster_side == "B"
	if _killzone != null:
		_killzone.set_hud_labels_visible(not on_title)
	if on_title:
		_hide_skill_tooltip()
		if _hero_panel != null:
			_hero_panel.visible = false
		if _skill_bar_panel != null:
			_skill_bar_panel.visible = false
		if _weapon_strip != null:
			_weapon_strip.visible = false
		if _context_stage != null:
			_context_stage.visible = false
		if _log_drawer != null:
			_log_drawer.visible = false
	elif in_combat:
		_hide_skill_tooltip()
		if _phase_toast != null:
			_phase_toast.visible = false
		if _hero_panel != null:
			_hero_panel.visible = false
		if _skill_bar_panel != null:
			_skill_bar_panel.visible = false
		if _weapon_strip != null:
			_weapon_strip.visible = false
		if _context_stage != null:
			_context_stage.visible = false

	if engine.state.is_empty():
		return

	_sync_ai_undo_guard()

	# U7: Clear stale interaction_mode when active operative ended / changed or turn handed off
	if _killzone != null:
		var imode: String = _killzone.interaction_mode
		if imode in ["move", "shoot", "fight", "pick_point", "pick_enemy"]:
			var cur_active_uid: String = str(engine.state.get("active_op_uid", ""))
			var armed_uid: String = _killzone.pick_uid if imode.begins_with("pick_") else _killzone.selected_uid
			if str(engine.state.get("phase", "")) != "FIREFIGHT" or engine.is_ai_turn() or cur_active_uid.is_empty() or cur_active_uid != armed_uid:
				_killzone.clear_interaction()
		elif imode == "deploy" and (str(engine.state.get("phase", "")) != "DEPLOY" or engine.is_ai_turn()):
			_killzone.clear_interaction()
		elif imode == "eq_deploy" and (str(engine.state.get("phase", "")) != "EQDEPLOY" or engine.is_ai_turn()):
			_killzone.clear_interaction()

	# 1. Update Top Broadcast Crest (VP Gems, CP Diamonds, Ploy Chips & Phase Medallion)
	var sa: Dictionary = engine.state["sides"]["A"]
	var sb: Dictionary = engine.state["sides"]["B"]
	_team_a_pips.setup(engine.get_total_vp("A"), int(sa["cp"]), true)
	_team_b_pips.setup(engine.get_total_vp("B"), int(sb["cp"]), false)

	for c in _team_a_ploys_box.get_children():
		c.queue_free()
	for p in sa["active_ploys"]:
		var ps: String = str(p).split("：")[0]
		_team_a_ploys_box.add_child(KTTheme.make_chip(ps, KTTheme.COL_IMPERIAL))

	for c in _team_b_ploys_box.get_children():
		c.queue_free()
	for p in sb["active_ploys"]:
		var ps_b: String = str(p).split("：")[0]
		_team_b_ploys_box.add_child(KTTheme.make_chip(ps_b, KTTheme.COL_NURGLE))

	var phase: String = str(engine.state["phase"])
	var tp: int = int(engine.state["tp"])
	var turn_side: String = str(engine.state["turn_side"])
	var turn_name: String = str(engine.state["sides"][turn_side]["name"])
	var phase_cn: String = str({
		"DROPZONE": "战前 · 选择降落区",
		"SELECT": "战前 · 选择特工",
		"EQUIP": "战前 · 选择装备",
		"TACOP": "战前 · 选择战术行动",
		"EQDEPLOY": "战前 · 部署装备",
		"DEPLOY": "部署阶段 · " + (str(engine.state["sides"][engine.deploy_side()]["name"]) if not engine.deploy_side().is_empty() else ""),
		"INITIATIVE": "先攻权对抗",
		"STRATEGY": "战略阶段 · " + turn_name,
		"FIREFIGHT": "交战阶段 · " + turn_name,
		"GAME_OVER": "战局终了",
	}.get(phase, phase))
	_tp_title_lbl.text = "TP %d/4" % tp
	_phase_sub_lbl.text = phase_cn
	var can_u: bool = _can_human_undo()
	_undo_btn.disabled = not can_u
	_undo_btn.modulate = Color(1, 1, 1, 1) if can_u else Color(0.50, 0.52, 0.58, 0.52)
	_undo_btn.tooltip_text = "敌方回合中无法撤回" if engine.is_ai_turn() else "撤回上一步 [Z]"
	_killzone.sync_table_props()

	var phase_sig: String = "%d_%s" % [tp, phase]
	if phase_sig != _last_phase_sig:
		_last_phase_sig = phase_sig
		if hud_vis and _phase_toast != null:
			KTSfx.play("phase_horn", -8.0, 0.04)
			_phase_toast_lbl.text = "⚜️  第 %d 转折点  ·  %s  ⚜️" % [tp, phase_cn]
			_phase_toast.visible = true
			_phase_toast.modulate.a = 0.0
			_phase_toast_timer = PHASE_TOAST_DUR

	# 2. Update Persistent Left/Right Squad Medallions (even during combat so trauma bars stay synced!)
	_sync_edge_squad_medallions()

	# 3. Update Tactical Ticker & Context-Sensitive Bottom Video-Game HUD
	if hud_vis:
		_update_tactical_pill()
		_rebuild_bottom_game_hud()

	# 4. Update Battle Log Drawer
	if _log_rtl != null:
		var tail_logs: Array = engine.state["log"]
		var start_i: int = maxi(0, tail_logs.size() - 28)
		var lines: PackedStringArray = []
		for i in range(start_i, tail_logs.size()):
			lines.append(str(tail_logs[i]))
		_log_rtl.text = "\n".join(lines)

	_killzone.queue_redraw()
	_killzone.request_hud_redraw()

	# Trigger AI turn automatically if applicable (pause while any modal/overlay is open)
	if not _is_any_modal_overlay_open() and engine.is_ai_turn():
		if _ai_timer.is_stopped():
			_ai_timer.start(0.60)
	elif _ai_timer != null and not _ai_timer.is_stopped():
		_ai_timer.stop()


func _update_tactical_pill() -> void:
	var phase: String = str(engine.state["phase"])
	if phase == "DROPZONE":
		var zs := engine.setup_side()
		if engine.is_ai_turn():
			_guide_lbl.text = "🤖 【%s】赢得战前拼骰，正在选择降落区…" % str(engine.state["sides"][zs]["name"])
		else:
			_guide_lbl.text = "🧭 【%s】赢得战前拼骰（%d : %d）：选择一个降落区，对手使用另一个" % [str(engine.state["sides"][zs]["name"]), int(engine.state["setup"]["roll_a"]), int(engine.state["setup"]["roll_b"])]
	elif phase == "SELECT":
		var ss := engine.setup_side()
		if engine.is_ai_turn():
			_guide_lbl.text = "🤖 【%s】正在秘密选择出战特工…" % str(engine.state["sides"][ss]["name"])
		else:
			_guide_lbl.text = "📋 【%s】选择 6 名出战特工：左键加入 / 取消，右键移除，然后确认" % str(engine.state["sides"][ss]["name"])
	elif phase == "EQUIP":
		var eqs := engine.setup_side()
		if engine.is_ai_turn():
			_guide_lbl.text = "🤖 【%s】正在秘密选择装备…" % str(engine.state["sides"][eqs]["name"])
		else:
			_guide_lbl.text = "🎒 【%s】秘密选择最多 4 件装备选项（每种限一次），双方选定后同时揭示" % str(engine.state["sides"][eqs]["name"])
	elif phase == "TACOP":
		var tcs := engine.setup_side()
		if engine.is_ai_turn():
			_guide_lbl.text = "🤖 【%s】正在秘密选择战术行动 op…" % str(engine.state["sides"][tcs]["name"])
		else:
			_guide_lbl.text = "🂡 【%s】根据小队原型秘密选择一个战术行动 op（悬停看卡面全文）" % str(engine.state["sides"][tcs]["name"])
	elif phase == "EQDEPLOY":
		var eds := engine.eq_deploy_side()
		var edname := str(engine.state["sides"][eds]["name"]) if not eds.is_empty() else ""
		if engine.is_ai_turn():
			_guide_lbl.text = "🤖 【%s】正在部署装备…" % edname
		elif _killzone.interaction_mode == "eq_deploy":
			_guide_lbl.text = "🧱 在高亮区域内点击放置【%s】｜ [R] 旋转 90° ｜ 右键取消" % KTEngine.equipment_item_name(_killzone.eq_deploy_kind)
		else:
			_guide_lbl.text = "🧱 【%s】部署战前装备：双方轮流、每次一件（还剩 %d 件）" % [edname, engine.eq_pending(eds).size()]
	elif phase == "DEPLOY":
		var ds := engine.deploy_side()
		var dname := str(engine.state["sides"][ds]["name"]) if not ds.is_empty() else ""
		if engine.is_ai_turn():
			_guide_lbl.text = "🤖 【%s】正在部署特工…" % dname
		elif _killzone.interaction_mode == "deploy":
			_guide_lbl.text = "📍 在己方降落区内点击落点部署（本组还需 %d 名）｜ 右键取消" % engine.deploy_remaining_in_group(ds)
		else:
			_guide_lbl.text = "📍 【%s】部署：点击下方待部署特工，再在降落区内选落点（本组 %d 名）" % [dname, engine.deploy_remaining_in_group(ds)]
	elif phase == "INITIATIVE":
		_guide_lbl.text = "🎲 投掷转折点先攻权骰 [Space] — 决定先后手并领取命令点 CP"
	elif phase == "STRATEGY":
		if engine.is_ai_turn():
			_guide_lbl.text = "🤖 敌方统帅正在检视战略计谋手牌…"
		elif engine.needs_primary_op(str(engine.state["turn_side"])):
			_guide_lbl.text = "🂠 第一转折点战略计划：秘密选择你的主要行动 op（战斗结束时揭示，额外获得该行动 VP 的一半）"
		else:
			_guide_lbl.text = "🃏 悬停抽选下方【战略计谋卡】打出，或按 [Space] 进入交战"
	elif phase == "FIREFIGHT":
		if engine.is_ai_turn():
			_guide_lbl.text = "🤖 敌方特工机动交战中…"
		elif str(engine.state["active_op_uid"]).is_empty():
			_guide_lbl.text = "⚡ 点击战场立绘棋子、两侧头像勋章或下方特工卡开始激活"
		elif _killzone.interaction_mode == "move":
			_guide_lbl.text = "📏 左键点击绿色全息圈落脚 ｜ Shift+左键添加绕墙拐点 ｜ 右键取消"
		elif _killzone.interaction_mode == "shoot":
			_guide_lbl.text = "🎯 悬停敌军查看锁定命中率与掩护锥，左键点击有效目标开火 ｜ 右键取消"
		elif _killzone.interaction_mode == "fight":
			_guide_lbl.text = "⚔️ 点击近战控制区内的敌方棋子展开白刃格斗 ｜ 右键取消"
		elif _killzone.interaction_mode == "pick_point":
			_guide_lbl.text = "💨 在 6\" 内可见位置点击放置烟雾手雷 ｜ 右键取消"
		elif _killzone.interaction_mode == "pick_enemy":
			if _killzone.pick_ability == "tac_surveil":
				_guide_lbl.text = "📡 点击 6\" 外可见的敌方就绪特工进行侦查 ｜ 右键取消"
			else:
				_guide_lbl.text = "💫 点击 6\" 内可见的敌方特工投掷震荡手雷 ｜ 右键取消"
		else:
			var aop: Dictionary = engine.get_op(str(engine.state["active_op_uid"]))
			_guide_lbl.text = "⚡ 【%s】听候指令 — 按 [1]~[9] 触发技能槽，[Space] 结束激活" % str(aop.get("name", ""))
	else:
		var va: int = engine.get_total_vp("A")
		var vb: int = engine.get_total_vp("B")
		var res_str: String = "死亡天使凯旋！" if va > vb else ("瘟疫战士获胜！" if vb > va else "双方浴血平局！")
		_guide_lbl.text = "🏆 %s  最终比分 %d : %d" % [res_str, va, vb]

	_hover_lbl.visible = not _hover_text_cache.is_empty()
	_hover_lbl.text = _hover_text_cache


func _sync_edge_squad_medallions() -> void:
	var active_uid: String = str(engine.state.get("active_op_uid", ""))
	var sel_uid: String = _killzone.selected_uid if _killzone != null else ""

	for op_v in engine.state["operatives"]:
		var op: Dictionary = op_v
		var uid: String = str(op["uid"])
		var is_left: bool = (str(op["side"]) == "A")
		var med: KTWidgets.SquadMedallion = null

		if _medallions_by_uid.has(uid) and is_instance_valid(_medallions_by_uid[uid]):
			med = _medallions_by_uid[uid]
		else:
			med = KTWidgets.SquadMedallion.new()
			_medallions_by_uid[uid] = med
			if is_left:
				_left_squad_box.add_child(med)
			else:
				_right_squad_box.add_child(med)
			var bound_uid: String = uid
			med.clicked_unit.connect(func():
				var cur_op: Dictionary = engine.get_op(bound_uid)
				if cur_op.is_empty():
					return
				if bool(cur_op["incapacitated"]):
					_notify_illegal_click("该特工已阵亡", cur_op.get("pos", Vector2(15.0, 11.0)))
					return
				if engine.is_ai_turn():
					_notify_illegal_click("敌方回合行动中", cur_op.get("pos", Vector2(15.0, 11.0)))
					return
				var ph: String = str(engine.state.get("phase", ""))
				if ph == "DEPLOY" and not bool(cur_op.get("deployed", false)):
					# Still in the drop zone: clicking the roster card picks it up for placement
					# instead of flying the camera to its off-board (-99,-99) parking spot.
					if str(cur_op["side"]) == engine.deploy_side():
						_killzone.start_deploy_mode(bound_uid)
						KTSfx.play("card_lift", -5.0, 0.05)
					else:
						var ds_now: String = engine.deploy_side()
						var ds_name: String = str(engine.state["sides"][ds_now]["name"]) if not ds_now.is_empty() else ""
						_notify_illegal_click(("轮到【%s】部署" % ds_name) if not ds_name.is_empty() else "当前不可部署该特工", Vector2(15.0, 11.0))
					_refresh_all_ui()
					return
				if _killzone.interaction_mode != "idle":
					_killzone.hovered_uid = bound_uid
					var cmd_uid: String = str(engine.state.get("active_op_uid", ""))
					if cmd_uid.is_empty():
						cmd_uid = _killzone.selected_uid
					var cmd_op: Dictionary = engine.get_op(cmd_uid)
					var hint_msg: String = ("正在指挥【%s】" % str(cmd_op["name"])) if not cmd_op.is_empty() else "请先完成当前操作"
					_notify_illegal_click(hint_msg, cur_op.get("pos", cmd_op.get("pos", Vector2(15.0, 11.0))), KTTheme.COL_GOLD_BRIGHT)
					_killzone.request_hud_redraw()
					return
				_killzone.focus_on_operative(bound_uid)
				_on_board_operative_clicked(bound_uid)
			)
			med.card_requested.connect(func():
				var cur_op2: Dictionary = engine.get_op(bound_uid)
				if not cur_op2.is_empty():
					_card_lightbox.show_operative_cards(cur_op2)
			)
			med.hover_unit_changed.connect(func(hov: bool):
				if _killzone != null:
					if hov:
						_killzone.hovered_uid = bound_uid
					elif _killzone.hovered_uid == bound_uid:
						_killzone.hovered_uid = ""
					_killzone.queue_redraw()
					_killzone.request_hud_redraw()
			)

		var st_path: String = str(op.get("standee", ""))
		var st_tex: Texture2D = KTData.load_tex(st_path if not st_path.is_empty() else str(op["portrait"]))
		var ready_flag: bool = bool(op["ready"])
		var ord_str: String = str(op["order"])
		var ord_tex: Texture2D = KTTheme.order_token_tex(ord_str, ready_flag)

		med.setup(op, is_left, uid == active_uid, uid == sel_uid, st_tex, ord_tex)
		# Not yet on the table: dim its card so it doesn't read as "click me to look at it".
		med.modulate.a = 1.0 if bool(op.get("deployed", true)) else 0.62


func _rebuild_bottom_game_hud() -> void:
	_hide_skill_tooltip()
	_active_hotbar_slots.clear()

	for c in _context_hb.get_children():
		c.queue_free()
	for c in _skill_bar_hb.get_children():
		c.queue_free()
	for c in _weapon_hb.get_children():
		c.queue_free()

	var phase: String = str(engine.state["phase"])
	var active_uid: String = str(engine.state.get("active_op_uid", ""))

	# Mode A: Active Operative Tactical Console (Hero Portrait Bottom-Left + Octagonal Skill Hotbar Bottom-Center!)
	if phase == "FIREFIGHT" and not active_uid.is_empty() and not engine.is_ai_turn():
		_context_stage.visible = false
		var op: Dictionary = engine.get_op(active_uid)
		_populate_hero_and_hotbar(op)
		return

	# Mode B: Context Stage (Initiative / Playable Stratagem Card Hand / Ready Unit Deck / Victory)
	_hero_panel.visible = false
	_skill_bar_panel.visible = false
	_weapon_strip.visible = false
	_context_stage.visible = true

	if phase == "DROPZONE":
		_build_dropzone_choice()

	elif phase == "SELECT":
		_build_roster_select()

	elif phase == "EQUIP":
		_build_equip_select()

	elif phase == "TACOP":
		_build_tacop_choice()

	elif phase == "EQDEPLOY":
		_build_eq_deploy_cards()

	elif phase == "DEPLOY":
		_build_deploy_cards()

	elif phase == "INITIATIVE":
		var roll_btn := Button.new()
		roll_btn.focus_mode = Control.FOCUS_NONE
		roll_btn.text = ("🎴  继续第 %d 转折点先手权卡步骤  [Space]" if engine.init_pending() else "🎲  投掷第 %d 转折点先攻权对抗骰  [Space]") % int(engine.state["tp"])
		roll_btn.custom_minimum_size = Vector2(450, 62)
		roll_btn.size_flags_vertical = Control.SIZE_SHRINK_END
		roll_btn.add_theme_font_size_override("font_size", 18)
		KTTheme.apply_accent_button(roll_btn, KTTheme.COL_GOLD_BRIGHT, 0.36)
		KTWidgets.attach_button_juice(roll_btn, 1.05, "phase_horn")
		roll_btn.pressed.connect(_roll_or_resume_initiative)
		_context_hb.add_child(roll_btn)

	elif phase == "STRATEGY":
		var turn_s: String = str(engine.state["turn_side"])
		if engine.is_ai_turn():
			var ai_pill := PanelContainer.new()
			ai_pill.size_flags_vertical = Control.SIZE_SHRINK_END
			ai_pill.add_theme_stylebox_override("panel", KTTheme.make_panel_style(Color(0.04, 0.06, 0.05, 0.92), KTTheme.COL_NURGLE, 10, 2, 14))
			var al := Label.new()
			al.text = "🤖 瘟疫领主正在谋划战略计谋…"
			al.add_theme_font_size_override("font_size", 16)
			al.add_theme_color_override("font_color", KTTheme.COL_NURGLE.lightened(0.28))
			ai_pill.add_child(al)
			_context_hb.add_child(ai_pill)
		elif engine.needs_primary_op(turn_s):
			_build_primary_op_choice(turn_s)
		else:
			var fcol: Color = KTTheme.COL_IMPERIAL if turn_s == "A" else KTTheme.COL_NURGLE
			var deal_i := 0
			if engine.can_choose_envoy(turn_s):
				_build_envoy_choice(turn_s)
			for opt_v in engine.get_strategic_ploy_options(turn_s):
				var opt: Dictionary = opt_v
				var used_flag: bool = bool(opt["used"])
				var cost_i: int = int(opt["cost"])
				var dis_flag: bool = used_flag or int(engine.state["sides"][turn_s]["cp"]) < cost_i
				var scard := KTWidgets.StratagemCard.new()
				scard.size_flags_vertical = Control.SIZE_SHRINK_END
				scard.setup(
					str(opt["title"]),
					"✓ 本回合已生效" if used_flag else str(opt["desc"]),
					"%d CP" % cost_i,
					fcol,
					dis_flag,
					used_flag,
					deal_i,
					"战略计谋"
				)
				deal_i += 1
				var oid: String = str(opt["id"])
				scard.played.connect(func():
					engine.use_strategic_ploy(turn_s, oid)
					_refresh_all_ui()
				)
				_context_hb.add_child(scard)

			var pass_b := Button.new()
			pass_b.focus_mode = Control.FOCUS_NONE
			pass_b.text = "⏭ 结束出牌\n进入交战 [Space]"
			pass_b.custom_minimum_size = Vector2(154, 82)
			pass_b.size_flags_vertical = Control.SIZE_SHRINK_END
			pass_b.add_theme_font_size_override("font_size", 14)
			KTTheme.apply_accent_button(pass_b, KTTheme.COL_EMERALD, 0.34)
			KTWidgets.attach_button_juice(pass_b, 1.06)
			pass_b.pressed.connect(func():
				engine.pass_strategy(turn_s)
				_refresh_all_ui()
			)
			_context_hb.add_child(pass_b)

	elif phase == "FIREFIGHT":
		_build_firefight_ready_cards()

	else:
		var over_panel := PanelContainer.new()
		over_panel.size_flags_vertical = Control.SIZE_SHRINK_END
		over_panel.add_theme_stylebox_override(
			"panel",
			KTTheme.make_panel_style(Color(0.04, 0.05, 0.08, 0.95), KTTheme.COL_GOLD_BRIGHT, 12, 2, 16, true)
		)
		_context_hb.add_child(over_panel)

		var ohb := HBoxContainer.new()
		ohb.add_theme_constant_override("separation", 20)
		over_panel.add_child(ohb)

		var sum_l := Label.new()
		sum_l.text = "🏆 终局战果：死亡天使 %d VP  vs  瘟疫战士 %d VP\n🂠 揭示主要行动 op：死亡天使【%s】+%d VP ｜ 瘟疫战士【%s】+%d VP" % [
			engine.get_total_vp("A"), engine.get_total_vp("B"),
			str(KTEngine.PRIMARY_OP_NAMES.get(str(engine.state["sides"]["A"]["primary_op"]), "未选择")), engine.primary_bonus("A"),
			str(KTEngine.PRIMARY_OP_NAMES.get(str(engine.state["sides"]["B"]["primary_op"]), "未选择")), engine.primary_bonus("B"),
		]
		sum_l.add_theme_font_size_override("font_size", 20)
		sum_l.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
		ohb.add_child(sum_l)

		var btn_again := Button.new()
		btn_again.focus_mode = Control.FOCUS_NONE
		btn_again.text = "🔄 重新空投再战"
		btn_again.custom_minimum_size = Vector2(180, 46)
		KTTheme.apply_accent_button(btn_again, KTTheme.COL_GOLD_BRIGHT, 0.34)
		KTWidgets.attach_button_juice(btn_again, 1.06, "phase_horn")
		btn_again.pressed.connect(func(): _start_new_battle())
		ohb.add_child(btn_again)


func _setup_wait_pill(msg: String) -> void:
	var wait_p := PanelContainer.new()
	wait_p.size_flags_vertical = Control.SIZE_SHRINK_END
	wait_p.add_theme_stylebox_override("panel", KTTheme.panel("floating", KTTheme.COL_NURGLE))
	var wl := Label.new()
	wl.text = msg
	wl.add_theme_font_size_override("font_size", 16)
	wl.add_theme_color_override("font_color", KTTheme.COL_NURGLE.lightened(0.26))
	wait_p.add_child(wl)
	_context_hb.add_child(wait_p)


## Human-readable edge name of a drop zone rectangle on the 30x22 board.
func _zone_label(dz: Rect2) -> String:
	var w: float = float(engine.state["map"]["width"])
	var h: float = float(engine.state["map"]["height"])
	if dz.size.x >= dz.size.y:
		return "北侧" if dz.get_center().y < h * 0.5 else "南侧"
	return "西侧" if dz.get_center().x < w * 0.5 else "东侧"


## Roll the TP initiative dice, or reopen the roll-off panel if the initiative-card step is still open.
func _roll_or_resume_initiative() -> void:
	if engine.init_pending():
		var st: Dictionary = engine.state["init"]
		_dice_modal.open_initiative(engine, {"roll_a": int(st["roll"]["A"]), "roll_b": int(st["roll"]["B"]), "winner": engine.init_leader()}, false)
	else:
		var res_i: Dictionary = engine.roll_initiative()
		if bool(res_i.get("ok", false)):
			_dice_modal.open_initiative(engine, res_i, false)
	_refresh_all_ui()


## Rulebook p.106: in TP1, as a strategic gambit, each player secretly picks their Primary Op.
func _build_primary_op_choice(side: String) -> void:
	var acc: Color = KTTheme.COL_IMPERIAL if side == "A" else KTTheme.COL_NURGLE
	var pnl := PanelContainer.new()
	pnl.size_flags_vertical = Control.SIZE_SHRINK_END
	pnl.add_theme_stylebox_override("panel", KTTheme.panel("floating", acc))
	_context_hb.add_child(pnl)
	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 8)
	pnl.add_child(vb)
	var hdr := Label.new()
	hdr.text = "🂠 【%s】秘密选择主要行动 op  ·  战斗结束时揭示，额外获得该行动 op 所得 VP 的一半（向上取整）" % str(engine.state["sides"][side]["name"])
	hdr.add_theme_font_size_override("font_size", 14)
	hdr.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	vb.add_child(hdr)
	var hb := HBoxContainer.new()
	hb.add_theme_constant_override("separation", 12)
	vb.add_child(hb)
	for opt in [
		["key", "关键行动 op", "通过执行任务行动和控制\n目标标识来获得 VP", true],
		["kill", "击杀行动 op", "通过使敌方特工残废\n来获得 VP", true],
		["tac", "战术行动 op", "通过战前秘密选定的\n战术行动 op 获得 VP", true],
	]:
		var op_id: String = str(opt[0])
		var b := Button.new()
		b.focus_mode = Control.FOCUS_NONE
		b.text = "%s\n%s" % [str(opt[1]), str(opt[2])]
		b.custom_minimum_size = Vector2(236, 84)
		b.add_theme_font_size_override("font_size", 14)
		b.disabled = not bool(opt[3])
		KTTheme.apply_accent_button(b, acc, 0.30)
		KTWidgets.attach_button_juice(b, 1.04, "order_servo")
		b.pressed.connect(func():
			engine.choose_primary_op(side, op_id)
			_refresh_all_ui()
		)
		hb.add_child(b)


## Rulebook p.105 step 1: the pre-battle roll-off winner picks a drop zone; the opponent takes the other.
func _build_dropzone_choice() -> void:
	var side: String = engine.setup_side()
	if engine.is_ai_turn():
		_setup_wait_pill("🤖 【%s】正在选择降落区…" % str(engine.state["sides"][side]["name"]))
		return
	var own_rect: Rect2 = engine.state["map"]["drop_zone_a"] if side == "A" else engine.state["map"]["drop_zone_b"]
	var other_rect: Rect2 = engine.state["map"]["drop_zone_b"] if side == "A" else engine.state["map"]["drop_zone_a"]
	var acc: Color = KTTheme.COL_IMPERIAL if side == "A" else KTTheme.COL_NURGLE
	for opt in [[own_rect, false], [other_rect, true]]:
		var rect: Rect2 = opt[0]
		var swap: bool = opt[1]
		var b := Button.new()
		b.focus_mode = Control.FOCUS_NONE
		b.text = "🧭 选择%s降落区\n%.0f\" × %.0f\"" % [_zone_label(rect), rect.size.x, rect.size.y]
		b.custom_minimum_size = Vector2(230, 72)
		b.size_flags_vertical = Control.SIZE_SHRINK_END
		b.add_theme_font_size_override("font_size", 15)
		KTTheme.apply_accent_button(b, acc, 0.30)
		KTWidgets.attach_button_juice(b, 1.05, "order_servo")
		b.pressed.connect(func():
			engine.choose_drop_zone(swap)
			_refresh_all_ui()
		)
		_context_hb.add_child(b)


## Rulebook p.106: each player secretly selects one tac op from their kill team's archetypes.
func _build_tacop_choice() -> void:
	var side: String = engine.setup_side()
	var sname: String = str(engine.state["sides"][side]["name"])
	if engine.is_ai_turn():
		_setup_wait_pill("🤖 【%s】正在秘密选择战术行动 op…" % sname)
		return
	var acc: Color = KTTheme.COL_IMPERIAL if side == "A" else KTTheme.COL_NURGLE
	var pnl := PanelContainer.new()
	pnl.size_flags_vertical = Control.SIZE_SHRINK_END
	pnl.add_theme_stylebox_override("panel", KTTheme.panel("floating", acc))
	_context_hb.add_child(pnl)
	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 6)
	pnl.add_child(vb)
	var info := Label.new()
	info.text = "【%s】秘密选择 1 个战术行动 op  ·  每个行动 op 最多 6VP  ·  悬停看公开时机与规则全文" % sname
	info.add_theme_font_size_override("font_size", 14)
	info.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	vb.add_child(info)
	var cards := HBoxContainer.new()
	cards.add_theme_constant_override("separation", 8)
	vb.add_child(cards)
	for t_v in engine.tac_op_options(side):
		var t: Dictionary = t_v
		var tid: String = str(t["id"])
		var ready: bool = bool(t["implemented"])
		var b := Button.new()
		b.focus_mode = Control.FOCUS_NONE
		b.text = "%s · %s\n%s" % [str(t["name"]), str(t["archetype"]), str(t["scoring"]) if ready else "（尚未实装，暂不可选）"]
		b.tooltip_text = "公开时机：%s\n\n%s\n\nVP 结算：%s" % [str(t["reveal"]), str(t["rule"]), str(t["scoring"])]
		b.custom_minimum_size = Vector2(208, 124)
		b.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		b.add_theme_font_size_override("font_size", 12)
		b.disabled = not ready
		KTTheme.apply_accent_button(b, acc, 0.28)
		KTWidgets.attach_button_juice(b, 1.03, "order_servo")
		b.pressed.connect(func():
			var res: Dictionary = engine.choose_tac_op(side, tid)
			if not bool(res.get("ok", false)):
				_killzone.spawn_floating_text(Vector2(15.0, 11.0), str(res.get("msg", "")), KTTheme.COL_CRIMSON)
			_refresh_all_ui()
		)
		cards.add_child(b)


## 特使 tac op: as a strategic gambit, name one operative as this turning point's Envoy.
func _build_envoy_choice(side: String) -> void:
	var acc: Color = KTTheme.COL_IMPERIAL if side == "A" else KTTheme.COL_NURGLE
	var pnl := PanelContainer.new()
	pnl.size_flags_vertical = Control.SIZE_SHRINK_END
	pnl.add_theme_stylebox_override("panel", KTTheme.panel("floating", acc))
	_context_hb.add_child(pnl)
	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 4)
	pnl.add_child(vb)
	var hdr := Label.new()
	hdr.text = "🕊️ 战略计划 · 指定特使"
	hdr.tooltip_text = str(KTEngine.tac_op_info("envoy").get("scoring", ""))
	hdr.mouse_filter = Control.MOUSE_FILTER_STOP
	hdr.add_theme_font_size_override("font_size", 13)
	hdr.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	vb.add_child(hdr)
	var grid := GridContainer.new()
	grid.columns = 2
	vb.add_child(grid)
	for c_v in engine.envoy_candidates(side):
		var c: Dictionary = c_v
		var cuid: String = str(c["uid"])
		var b := Button.new()
		b.focus_mode = Control.FOCUS_NONE
		b.text = str(c["role"]) if not str(c["role"]).is_empty() else str(c["name"])
		b.tooltip_text = str(c["name"])
		b.custom_minimum_size = Vector2(96, 26)
		b.add_theme_font_size_override("font_size", 12)
		KTTheme.apply_accent_button(b, acc, 0.26)
		b.pressed.connect(func():
			engine.choose_envoy(side, cuid)
			_refresh_all_ui()
		)
		grid.add_child(b)


## Rulebook p.106: each player secretly selects up to 4 equipment options (each at most once).
var _equip_pick: Array = []
var _equip_pick_side: String = ""
var _equip_opts: Dictionary = {}

func _build_equip_select() -> void:
	var side: String = engine.setup_side()
	var sname: String = str(engine.state["sides"][side]["name"])
	if engine.is_ai_turn():
		_setup_wait_pill("🤖 【%s】正在秘密选择装备…" % sname)
		return
	if _equip_pick_side != side:
		_equip_pick = engine.default_equipment(side)
		_equip_pick_side = side
		_equip_opts = {}
	var acc: Color = KTTheme.COL_IMPERIAL if side == "A" else KTTheme.COL_NURGLE
	var pnl := PanelContainer.new()
	pnl.size_flags_vertical = Control.SIZE_SHRINK_END
	pnl.add_theme_stylebox_override("panel", KTTheme.panel("floating", acc))
	_context_hb.add_child(pnl)
	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 6)
	pnl.add_child(vb)
	var hdr := HBoxContainer.new()
	hdr.add_theme_constant_override("separation", 12)
	vb.add_child(hdr)
	var info := Label.new()
	info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	info.text = "【%s】装备 %d / 4  ·  点击切换，悬停看原文  ·  下排为通用装备" % [sname, _equip_pick.size()]
	info.add_theme_font_size_override("font_size", 14)
	info.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	hdr.add_child(info)
	var ok_b := Button.new()
	ok_b.focus_mode = Control.FOCUS_NONE
	ok_b.text = "✓ 确认装备 [Space]"
	KTTheme.apply_accent_button(ok_b, KTTheme.COL_EMERALD, 0.34)
	KTWidgets.attach_button_juice(ok_b, 1.05, "phase_horn")
	ok_b.pressed.connect(func():
		if bool(engine.confirm_equipment(side, _equip_pick, _equip_opts).get("ok", false)):
			_equip_pick_side = ""
			_equip_opts = {}
		_refresh_all_ui()
	)
	hdr.add_child(ok_b)
	var cards := HBoxContainer.new()
	cards.add_theme_constant_override("separation", 8)
	vb.add_child(cards)
	for e_v in engine.equipment_options(side):
		var e: Dictionary = e_v
		var eid: String = str(e["id"])
		var title: String = str(e["title"]).get_slice("（", 0)
		var picked: bool = _equip_pick.has(eid)
		var b := Button.new()
		b.focus_mode = Control.FOCUS_NONE
		b.text = "%s%s\n%s" % ["✓ " if picked else "", title, str(KTGlossary.lookup(title).get("short", ""))]
		b.tooltip_text = str(e.get("body", ""))
		b.custom_minimum_size = Vector2(236, 88)
		b.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		b.add_theme_font_size_override("font_size", 13)
		KTTheme.apply_accent_button(b, KTTheme.COL_GOLD_BRIGHT if picked else acc, 0.34 if picked else 0.16)
		KTWidgets.attach_button_juice(b, 1.03, "order_servo")
		b.pressed.connect(func():
			if _equip_pick.has(eid):
				_equip_pick.erase(eid)
			elif _equip_pick.size() < 4:
				_equip_pick.append(eid)
			_refresh_all_ui()
		)
		cards.add_child(b)

	# Universal equipment (rulebook p.98-103): any kill team may take these
	var uni_row := HFlowContainer.new()
	uni_row.custom_minimum_size = Vector2(968, 0)
	uni_row.add_theme_constant_override("h_separation", 6)
	uni_row.add_theme_constant_override("v_separation", 6)
	vb.add_child(uni_row)
	for u_v in KTEngine.universal_equipment_options():
		var u: Dictionary = u_v
		var u_id: String = str(u["id"])
		var u_ready: bool = bool(u["implemented"])
		var u_picked: bool = _equip_pick.has(u_id)
		var ub := Button.new()
		ub.focus_mode = Control.FOCUS_NONE
		ub.text = "%s%s" % ["✓ " if u_picked else "", str(u["title"])]
		ub.tooltip_text = str(u["body"])
		ub.custom_minimum_size = Vector2(104, 34)
		ub.add_theme_font_size_override("font_size", 12)
		ub.disabled = not u_ready
		KTTheme.apply_accent_button(ub, KTTheme.COL_GOLD_BRIGHT if u_picked else acc, 0.34 if u_picked else 0.16)
		ub.pressed.connect(func():
			if _equip_pick.has(u_id):
				_equip_pick.erase(u_id)
			elif _equip_pick.size() < 4:
				_equip_pick.append(u_id)
			_refresh_all_ui()
		)
		uni_row.add_child(ub)

	var has_util: bool = _equip_pick.has("UNI-EQ-utilitygrenades")
	var has_exp: bool = _equip_pick.has("UNI-EQ-explosivegrenades")
	if has_util or has_exp:
		var gren_row := HBoxContainer.new()
		gren_row.add_theme_constant_override("separation", 8)
		vb.add_child(gren_row)
		if has_util:
			var cur_u: String = str(_equip_opts.get("utility", "mix"))
			var lbl_u: String = "烟雾×1 + 震荡×1"
			var next_u: String = "stun2"
			if cur_u == "smoke2":
				lbl_u = "烟雾×2"
				next_u = "mix"
			elif cur_u == "stun2":
				lbl_u = "震荡×2"
				next_u = "smoke2"
			var bu := Button.new()
			bu.focus_mode = Control.FOCUS_NONE
			bu.text = "💨 功能型手雷组合：%s（点击切换）" % lbl_u
			bu.custom_minimum_size = Vector2(280, 32)
			bu.add_theme_font_size_override("font_size", 12)
			KTTheme.apply_accent_button(bu, KTTheme.COL_GOLD_BRIGHT, 0.26)
			bu.pressed.connect(func():
				_equip_opts["utility"] = next_u
				_refresh_all_ui()
			)
			gren_row.add_child(bu)
		if has_exp:
			var cur_e: String = str(_equip_opts.get("explosive", "mix"))
			var lbl_e: String = "破片×1 + 穿甲×1"
			var next_e: String = "krak2"
			if cur_e == "frag2":
				lbl_e = "破片×2"
				next_e = "mix"
			elif cur_e == "krak2":
				lbl_e = "穿甲×2"
				next_e = "frag2"
			var be := Button.new()
			be.focus_mode = Control.FOCUS_NONE
			be.text = "💥 爆破型手雷组合：%s（点击切换）" % lbl_e
			be.custom_minimum_size = Vector2(280, 32)
			be.add_theme_font_size_override("font_size", 12)
			KTTheme.apply_accent_button(be, KTTheme.COL_GOLD_BRIGHT, 0.26)
			be.pressed.connect(func():
				_equip_opts["explosive"] = next_e
				_refresh_all_ui()
			)
			gren_row.add_child(be)


## Rulebook p.105 step 2: each player secretly selects operatives per the team's selection rules.
var _roster_pick: Array = []
var _roster_pick_side: String = ""

func _build_roster_select() -> void:
	var side: String = engine.setup_side()
	var sname: String = str(engine.state["sides"][side]["name"])
	if engine.is_ai_turn():
		_setup_wait_pill("🤖 【%s】正在秘密选择出战特工…" % sname)
		return
	if _roster_pick_side != side:
		_roster_pick = engine.default_roster(side)
		_roster_pick_side = side
	var team: Dictionary = KTData.get_team(str(engine.state["sides"][side]["team_id"]))
	var chk: Dictionary = KTEngine.validate_roster(team, _roster_pick)
	var acc: Color = KTTheme.COL_IMPERIAL if side == "A" else KTTheme.COL_NURGLE

	var roster_panel := PanelContainer.new()
	roster_panel.size_flags_vertical = Control.SIZE_SHRINK_END
	roster_panel.add_theme_stylebox_override("panel", KTTheme.panel("floating", acc))
	_context_hb.add_child(roster_panel)
	var root_vb := VBoxContainer.new()
	root_vb.add_theme_constant_override("separation", 6)
	roster_panel.add_child(root_vb)

	var hdr := HBoxContainer.new()
	hdr.add_theme_constant_override("separation", 12)
	root_vb.add_child(hdr)
	var info := Label.new()
	info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	info.text = "【%s】出战特工 %d / 6  ·  %s" % [sname, _roster_pick.size(), ("✓ 编成合法" if bool(chk["ok"]) else str(chk["reason"]))]
	info.add_theme_font_size_override("font_size", 14)
	info.add_theme_color_override("font_color", KTTheme.COL_EMERALD.lightened(0.2) if bool(chk["ok"]) else KTTheme.COL_GOLD_BRIGHT)
	hdr.add_child(info)
	var ok_b := Button.new()
	ok_b.focus_mode = Control.FOCUS_NONE
	ok_b.text = "✓ 确认出战"
	ok_b.disabled = not bool(chk["ok"])
	KTTheme.apply_accent_button(ok_b, KTTheme.COL_EMERALD, 0.34)
	KTWidgets.attach_button_juice(ok_b, 1.05, "phase_horn")
	ok_b.pressed.connect(func():
		var res: Dictionary = engine.confirm_roster(side, _roster_pick)
		if bool(res.get("ok", false)):
			_roster_pick_side = ""
		_refresh_all_ui()
	)
	hdr.add_child(ok_b)

	var cards := HBoxContainer.new()
	cards.add_theme_constant_override("separation", 6)
	root_vb.add_child(cards)
	for tpl_v in engine.roster_pool(side):
		var tpl: Dictionary = tpl_v
		var oid: String = str(tpl["id"])
		var cnt: int = _roster_pick.count(oid)
		var dup_ok: bool = KTEngine.roster_allows_duplicate(team, tpl)
		var card := PanelContainer.new()
		card.custom_minimum_size = Vector2(104, 90)
		card.mouse_filter = Control.MOUSE_FILTER_STOP
		card.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
		card.tooltip_text = "%s（%s）\nAPL %d · 移动 %.0f\" · 豁免 %d+ · 耐伤 %d%s" % [
			str(tpl["name"]), str(tpl["role"]), int(tpl["apl"]), float(tpl["move_inches"]), int(tpl["save"]), int(tpl["wounds"]),
			"\n可重复选择" if dup_ok else ""]
		card.add_theme_stylebox_override("panel", KTTheme.panel("primary" if cnt > 0 else "floating", KTTheme.COL_GOLD_BRIGHT if cnt > 0 else acc))
		if cnt == 0:
			card.modulate = Color(0.72, 0.72, 0.76)
		cards.add_child(card)

		var cv := VBoxContainer.new()
		cv.add_theme_constant_override("separation", 2)
		cv.mouse_filter = Control.MOUSE_FILTER_IGNORE
		card.add_child(cv)
		var pr := TextureRect.new()
		pr.texture = KTData.load_tex(str(tpl.get("portrait", "")))
		pr.custom_minimum_size = Vector2(0, 44)
		pr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		pr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		pr.mouse_filter = Control.MOUSE_FILTER_IGNORE
		cv.add_child(pr)
		var nl := Label.new()
		nl.text = str(tpl["role"]) + ("  ×%d" % cnt if cnt > 1 else ("  ✓" if cnt == 1 else ""))
		nl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		nl.add_theme_font_size_override("font_size", 12)
		nl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT if cnt > 0 else KTTheme.COL_TEXT)
		nl.mouse_filter = Control.MOUSE_FILTER_IGNORE
		cv.add_child(nl)
		var sub := Label.new()
		sub.text = str(tpl["name"]).replace("星际战士", "").replace("仲裁者", "").replace("瘟疫战士", "")
		sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		sub.clip_text = true
		sub.add_theme_font_size_override("font_size", 10)
		sub.add_theme_color_override("font_color", KTTheme.COL_MUTED)
		sub.mouse_filter = Control.MOUSE_FILTER_IGNORE
		cv.add_child(sub)

		card.gui_input.connect(func(ev: InputEvent):
			if not (ev is InputEventMouseButton and ev.pressed):
				return
			if ev.button_index == MOUSE_BUTTON_RIGHT:
				if _roster_pick.has(oid):
					_roster_pick.erase(oid)
			elif ev.button_index == MOUSE_BUTTON_LEFT:
				if _roster_pick.has(oid) and not dup_ok:
					_roster_pick.erase(oid)
				elif _roster_pick.size() < 6:
					_roster_pick.append(oid)
				else:
					return
			else:
				return
			KTSfx.play("card_lift", -6.0, 0.05)
			_refresh_all_ui()
		)


## Deployment hand: one card per undeployed operative of the deploying side. Click a card to pick
## it up, then click a legal spot in the drop zone. Alternates sides automatically per the engine.
func _build_deploy_cards() -> void:
	var ds: String = engine.deploy_side()
	if ds.is_empty():
		return
	var sname: String = str(engine.state["sides"][ds]["name"])
	if engine.is_ai_turn():
		var wait_p := PanelContainer.new()
		wait_p.size_flags_vertical = Control.SIZE_SHRINK_END
		wait_p.add_theme_stylebox_override("panel", KTTheme.panel("floating", KTTheme.COL_NURGLE))
		var wl := Label.new()
		wl.text = "🤖 【%s】正在部署特工…" % sname
		wl.add_theme_font_size_override("font_size", 16)
		wl.add_theme_color_override("font_color", KTTheme.COL_NURGLE.lightened(0.26))
		wait_p.add_child(wl)
		_context_hb.add_child(wait_p)
		return

	var c_acc: Color = KTTheme.COL_IMPERIAL if ds == "A" else KTTheme.COL_NURGLE
	var picked: String = _killzone.deploy_uid if _killzone.interaction_mode == "deploy" else ""
	for op in engine.undeployed_ops(ds):
		var uid: String = str(op["uid"])
		var is_picked: bool = uid == picked
		var card := PanelContainer.new()
		card.custom_minimum_size = Vector2(132, 96)
		card.size_flags_vertical = Control.SIZE_SHRINK_END
		card.mouse_filter = Control.MOUSE_FILTER_STOP
		card.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
		card.add_theme_stylebox_override("panel", KTTheme.panel("primary" if is_picked else "floating", KTTheme.COL_GOLD_BRIGHT if is_picked else c_acc))
		_context_hb.add_child(card)

		var cv := VBoxContainer.new()
		cv.add_theme_constant_override("separation", 4)
		cv.mouse_filter = Control.MOUSE_FILTER_IGNORE
		card.add_child(cv)

		var pr := TextureRect.new()
		pr.texture = KTData.load_tex(str(op.get("portrait", "")))
		pr.custom_minimum_size = Vector2(0, 48)
		pr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		pr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		pr.mouse_filter = Control.MOUSE_FILTER_IGNORE
		cv.add_child(pr)

		var nl := Label.new()
		nl.text = str(op.get("role", op["name"]))
		nl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		nl.add_theme_font_size_override("font_size", 12)
		nl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT if is_picked else KTTheme.COL_TEXT)
		nl.mouse_filter = Control.MOUSE_FILTER_IGNORE
		cv.add_child(nl)

		card.gui_input.connect(func(ev: InputEvent):
			if ev is InputEventMouseButton and ev.pressed and ev.button_index == MOUSE_BUTTON_LEFT:
				KTSfx.play("card_lift", -5.0, 0.05)
				_killzone.start_deploy_mode(uid)
				_refresh_all_ui()
		)

	var hint := Label.new()
	hint.text = "本组还需部署 %d 名" % engine.deploy_remaining_in_group(ds)
	hint.size_flags_vertical = Control.SIZE_SHRINK_END
	hint.add_theme_font_size_override("font_size", 13)
	hint.add_theme_color_override("font_color", c_acc.lightened(0.3))
	_context_hb.add_child(hint)


## Rulebook p.106 step 3: starting with the initiative player, players alternate setting up one
## item of pre-battle equipment at a time (before operatives are deployed).
func _build_eq_deploy_cards() -> void:
	var side: String = engine.eq_deploy_side()
	if side.is_empty():
		return
	if engine.is_ai_turn():
		_setup_wait_pill("🤖 【%s】正在部署装备…" % str(engine.state["sides"][side]["name"]))
		return
	var acc: Color = KTTheme.COL_IMPERIAL if side == "A" else KTTheme.COL_NURGLE
	var picked: String = _killzone.eq_deploy_kind if _killzone.interaction_mode == "eq_deploy" else ""
	var counts := {}
	for k in engine.eq_pending(side):
		counts[k] = int(counts.get(k, 0)) + 1
	for k_v in counts.keys():
		var kind: String = str(k_v)
		var is_picked: bool = kind == picked
		var b := Button.new()
		b.focus_mode = Control.FOCUS_NONE
		b.text = "🧱 %s ×%d\n%s" % [KTEngine.equipment_item_name(kind), int(counts[k_v]), "▶ 在棋盘上点击放置" if is_picked else "点击选择"]
		b.custom_minimum_size = Vector2(190, 72)
		b.size_flags_vertical = Control.SIZE_SHRINK_END
		b.add_theme_font_size_override("font_size", 14)
		KTTheme.apply_accent_button(b, KTTheme.COL_GOLD_BRIGHT if is_picked else acc, 0.34 if is_picked else 0.22)
		KTWidgets.attach_button_juice(b, 1.04, "card_lift")
		b.pressed.connect(func():
			_killzone.start_eq_deploy_mode(kind)
			_refresh_all_ui()
		)
		_context_hb.add_child(b)
	var rot_b := Button.new()
	rot_b.focus_mode = Control.FOCUS_NONE
	rot_b.text = "⟳ 旋转 90°\n[R]"
	rot_b.custom_minimum_size = Vector2(110, 72)
	rot_b.size_flags_vertical = Control.SIZE_SHRINK_END
	var picked_spec: Dictionary = KTEngine.EQ_ITEM_SPECS.get(picked, {})
	rot_b.disabled = picked.is_empty() or str(picked_spec.get("terrain", "")) == ""
	KTTheme.apply_accent_button(rot_b, acc, 0.20)
	rot_b.pressed.connect(func(): _killzone.eq_deploy_rot = not _killzone.eq_deploy_rot)
	_context_hb.add_child(rot_b)
	var skip_b := Button.new()
	skip_b.focus_mode = Control.FOCUS_NONE
	skip_b.text = "✕ 不部署\n剩余装备"
	skip_b.custom_minimum_size = Vector2(110, 72)
	skip_b.size_flags_vertical = Control.SIZE_SHRINK_END
	KTTheme.apply_accent_button(skip_b, KTTheme.COL_CRIMSON, 0.20)
	skip_b.pressed.connect(func():
		_killzone.clear_interaction()
		engine.skip_equipment_deploy(side)
		_refresh_all_ui()
	)
	_context_hb.add_child(skip_b)


func _on_board_equipment_spot_clicked(kind: String, pos_in: Vector2, rot: bool) -> void:
	if engine.state.is_empty() or engine.is_ai_turn():
		_killzone.clear_interaction()
		_refresh_all_ui()
		return
	var res: Dictionary = engine.deploy_equipment(engine.eq_deploy_side(), kind, pos_in, rot)
	if bool(res.get("ok", false)):
		KTSfx.play("move_confirm", -5.0, 0.04)
		_killzone.clear_interaction()
	else:
		_killzone.spawn_floating_text(pos_in, str(res.get("msg", "")), KTTheme.COL_CRIMSON)
	_refresh_all_ui()


func _on_board_deploy_spot_clicked(uid: String, pos_in: Vector2) -> void:
	if engine.state.is_empty() or engine.is_ai_turn():
		_killzone.clear_interaction()
		_refresh_all_ui()
		return
	var res: Dictionary = engine.deploy_operative(uid, pos_in)
	if bool(res.get("ok", false)):
		KTSfx.play("move_confirm", -5.0, 0.04)
		_killzone.clear_interaction()
		_killzone.sync_visual_positions(true)
	else:
		_killzone.spawn_floating_text(pos_in, str(res.get("msg", "")), KTTheme.COL_CRIMSON)
	_refresh_all_ui()


func _build_firefight_ready_cards() -> void:
	var turn_s: String = str(engine.state["turn_side"])
	var sname: String = str(engine.state["sides"][turn_s]["name"])

	if engine.is_ai_turn():
		var wait_p := PanelContainer.new()
		wait_p.size_flags_vertical = Control.SIZE_SHRINK_END
		wait_p.add_theme_stylebox_override("panel", KTTheme.make_panel_style(Color(0.04, 0.055, 0.05, 0.90), KTTheme.COL_NURGLE, 10, 2, 12))
		var wl := Label.new()
		wl.text = "🤖 【%s】正在调度特工行动…" % sname
		wl.add_theme_font_size_override("font_size", 16)
		wl.add_theme_color_override("font_color", KTTheme.COL_NURGLE.lightened(0.26))
		wait_p.add_child(wl)
		_context_hb.add_child(wait_p)
		return

	var has_ready: bool = engine.count_ready_operatives(turn_s) > 0
	var c_acc: Color = KTTheme.COL_IMPERIAL if turn_s == "A" else KTTheme.COL_NURGLE

	for op_v in engine.state["operatives"]:
		var op: Dictionary = op_v
		if bool(op["incapacitated"]) or str(op["side"]) != turn_s:
			continue
		var chk: Dictionary = engine.can_operative_be_selected_for_turn(op)
		if not bool(chk["ok"]):
			continue

		var card := PanelContainer.new()
		card.custom_minimum_size = Vector2(148, 104)
		card.size_flags_vertical = Control.SIZE_SHRINK_END
		card.add_theme_stylebox_override(
			"panel",
			KTTheme.make_panel_style(Color(0.036, 0.046, 0.072, 0.94), c_acc, 9, 2, 7, true)
		)
		_context_hb.add_child(card)

		var cv := VBoxContainer.new()
		cv.add_theme_constant_override("separation", 5)
		card.add_child(cv)

		var top_r := HBoxContainer.new()
		top_r.add_theme_constant_override("separation", 6)
		cv.add_child(top_r)

		var pr := TextureRect.new()
		var st_p: String = str(op.get("standee", ""))
		pr.texture = KTData.load_tex(st_p if not st_p.is_empty() else str(op["portrait"]))
		pr.custom_minimum_size = Vector2(38, 42)
		pr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		pr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		top_r.add_child(pr)

		var nl := Label.new()
		var short_nm: String = str(op["name"]).replace("星际战士", "").replace("仲裁者", "").replace("瘟疫战士", "瘟疫")
		nl.text = "%s\n%d/%dW · %dAP" % [short_nm, int(op["wounds_cur"]), int(op["wounds_max"]), int(op["apl_base"])]
		nl.add_theme_font_size_override("font_size", 11)
		nl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		top_r.add_child(nl)

		var uid: String = str(op["uid"])
		if bool(chk["counteract"]):
			var bc := Button.new()
			bc.focus_mode = Control.FOCUS_NONE
			bc.text = "⚡ 反应 (1AP)"
			bc.add_theme_font_size_override("font_size", 12)
			KTTheme.apply_accent_button(bc, KTTheme.COL_GOLD_BRIGHT, 0.30)
			KTWidgets.attach_button_juice(bc, 1.06, "order_servo")
			bc.pressed.connect(func():
				_killzone.focus_on_operative(uid)
				engine.activate_operative(uid)
				_refresh_all_ui()
			)
			cv.add_child(bc)
		else:
			var ord_btns := HBoxContainer.new()
			ord_btns.add_theme_constant_override("separation", 4)
			cv.add_child(ord_btns)

			var b_con := Button.new()
			b_con.focus_mode = Control.FOCUS_NONE
			b_con.text = "隐匿"
			var con_ico: Texture2D = KTTheme.order_token_tex("conceal", true)
			if con_ico != null:
				b_con.icon = con_ico
				b_con.expand_icon = true
				b_con.add_theme_constant_override("icon_max_width", 20)
			b_con.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			b_con.add_theme_font_size_override("font_size", 11)
			b_con.tooltip_text = "以【隐匿】姿态激活：依托掩体免疫远程锁定"
			KTTheme.apply_accent_button(b_con, KTTheme.COL_IMPERIAL, 0.28)
			KTWidgets.attach_button_juice(b_con, 1.06, "order_servo")
			b_con.pressed.connect(func():
				_killzone.focus_on_operative(uid)
				engine.activate_operative(uid, "conceal")
				_refresh_all_ui()
			)
			ord_btns.add_child(b_con)

			var b_eng := Button.new()
			b_eng.focus_mode = Control.FOCUS_NONE
			b_eng.text = "交战"
			var eng_ico: Texture2D = KTTheme.order_token_tex("engage", true)
			if eng_ico != null:
				b_eng.icon = eng_ico
				b_eng.expand_icon = true
				b_eng.add_theme_constant_override("icon_max_width", 20)
			b_eng.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			b_eng.add_theme_font_size_override("font_size", 11)
			b_eng.tooltip_text = "以【交战】姿态激活：自由开火与冲锋搏杀"
			KTTheme.apply_accent_button(b_eng, Color(0.95, 0.52, 0.18), 0.32)
			KTWidgets.attach_button_juice(b_eng, 1.06, "order_servo")
			b_eng.pressed.connect(func():
				_killzone.focus_on_operative(uid)
				engine.activate_operative(uid, "engage")
				_refresh_all_ui()
			)
			ord_btns.add_child(b_eng)

	if not has_ready:
		var skip_ca := Button.new()
		skip_ca.focus_mode = Control.FOCUS_NONE
		skip_ca.text = "⏭ 放弃反应"
		skip_ca.custom_minimum_size = Vector2(124, 64)
		skip_ca.size_flags_vertical = Control.SIZE_SHRINK_END
		KTTheme.apply_accent_button(skip_ca, KTTheme.COL_MUTED, 0.24)
		KTWidgets.attach_button_juice(skip_ca, 1.05)
		skip_ca.pressed.connect(func():
			engine.push_undo()
			engine.pass_counteract(turn_s)
			_refresh_all_ui()
		)
		_context_hb.add_child(skip_ca)


func _populate_hero_and_hotbar(op: Dictionary) -> void:
	if op.is_empty():
		_hero_panel.visible = false
		_skill_bar_panel.visible = false
		_weapon_strip.visible = false
		return

	var uid: String = str(op["uid"])
	var is_imp: bool = (str(op["side"]) == "A")
	var fcol: Color = KTTheme.COL_IMPERIAL if is_imp else KTTheme.COL_NURGLE

	# 1. Bottom-Left Hero Console (with persistent Trauma HP Bar!)
	_hero_panel.visible = true
	_hero_panel.add_theme_stylebox_override(
		"panel",
		KTTheme.make_panel_style(Color(0.032, 0.042, 0.066, 0.95), fcol.lerp(KTTheme.COL_GOLD, 0.45), 12, 2, 10, true, KTTheme.COL_GOLD_BRIGHT)
	)
	var st_path: String = str(op.get("standee", ""))
	_hero_portrait.texture = KTData.load_tex(st_path if not st_path.is_empty() else str(op["portrait"]))
	var ord_str: String = str(op["order"])
	_hero_order_ico.texture = KTTheme.order_token_tex(ord_str, true)
	_hero_name_lbl.text = str(op["name"])

	var w_cur: int = int(op["wounds_cur"])
	var w_max: int = int(op["wounds_max"])
	var same_hero: bool = (_hero_uid == uid)
	_hero_uid = uid
	_hero_hp_bar.bar_color = fcol.lightened(0.20)
	_hero_hp_bar.set_hp(w_cur, w_max, same_hero)
	_hero_hp_lbl.text = "%d / %d W%s" % [w_cur, w_max, " ☣" if bool(op["poisoned"]) else ""]

	var ap_left: int = int(op["ap_left"])
	var ap_base: int = int(op["apl_base"])
	_hero_ap_strip.setup(ap_left, ap_base)
	var stat_txt: String = "移 %.0f\" · 防 %d+" % [engine.get_effective_move_inches(op, "reposition"), int(op["save"])]
	var op_side_s: String = str(op["side"])
	var g_parts: PackedStringArray = PackedStringArray()
	var g_defs: Array = [["frag", "破"], ["krak", "穿"], ["smoke", "烟"], ["stun", "震"]]
	for gd_v in g_defs:
		var gd: Array = gd_v
		var g_cnt: int = engine.grenades_left(op_side_s, str(gd[0]))
		if g_cnt > 0:
			g_parts.append("%s%d" % [str(gd[1]), g_cnt])
	if not g_parts.is_empty():
		stat_txt += " · 雷 " + " ".join(g_parts)
	_hero_stat_lbl.text = stat_txt

	# Abilities as rule chips (skip the shared faction rule "阿斯塔特" to save space; it's on the crest)
	for c in _hero_abilities_flow.get_children():
		c.queue_free()
	var ab_names: Array = []
	for ab in op.get("abilities", []):
		var nm := str(ab.get("name", "")) if ab is Dictionary else str(ab)
		if not nm.is_empty() and nm != "阿斯塔特" and not ab_names.has(nm):
			ab_names.append(nm)
	if bool(op["poisoned"]):
		ab_names.append("毒素指示物")
	if int(op["wounds_cur"]) * 2 < int(op["wounds_max"]):
		ab_names.append("受创")
	for nm in ab_names.slice(0, 4):
		var chip := KTRuleChip.new()
		chip.setup(str(nm), Color(0, 0, 0, 0), 10)
		_hero_abilities_flow.add_child(chip)
	_hero_abilities_flow.visible = not ab_names.is_empty()

	var can_flip_order: bool = op["actions_taken"].is_empty() and not bool(engine.state["active_is_counteract"])
	_hero_order_btn.disabled = not can_flip_order
	var ord_ico: Texture2D = KTTheme.order_token_tex(ord_str, true)
	_hero_order_btn.icon = ord_ico
	_hero_order_btn.expand_icon = true
	_hero_order_btn.add_theme_constant_override("icon_max_width", 20)
	var ico_prefix := "" if ord_ico != null else ("🔵 " if ord_str == "conceal" else "🟠 ")
	if ord_str == "conceal":
		_hero_order_btn.text = ico_prefix + ("隐匿姿态 [C切换]" if can_flip_order else "隐匿姿态 (已锁定)")
		KTTheme.apply_accent_button(_hero_order_btn, KTTheme.COL_IMPERIAL, 0.30 if can_flip_order else 0.14)
	else:
		_hero_order_btn.text = ico_prefix + ("交战姿态 [C切换]" if can_flip_order else "交战姿态 (已锁定)")
		KTTheme.apply_accent_button(_hero_order_btn, Color(0.95, 0.52, 0.18), 0.32 if can_flip_order else 0.14)

	# 2. Bottom-Center Octagonal Skill Hotbar (`[1]`..`[9]` + `[Space]`)
	_skill_bar_panel.visible = true
	var acts: Dictionary = engine.get_available_actions(op)
	var usable_ranged: Array = engine.get_usable_ranged_weapons(op)

	_add_hotbar_move_slot(uid, "reposition", "转移", "战术转移 (Reposition)", KEY_1, "1", acts["reposition"], KTTheme.COL_IMPERIAL)
	_add_hotbar_move_slot(uid, "dash", "冲刺", "高速冲刺 (Dash · 3\")", KEY_2, "2", acts["dash"], Color(0.24, 0.78, 0.95))
	_add_hotbar_move_slot(uid, "charge", "冲锋", "突击冲锋 (Charge)", KEY_3, "3", acts["charge"], Color(0.96, 0.55, 0.18))
	_add_hotbar_move_slot(uid, "fall_back", "后撤", "脱离近战 (Fall Back)", KEY_4, "4", acts["fall_back"], KTTheme.COL_VIOLET)
	if acts.has("barricade_move"):
		var bm_info: Dictionary = acts["barricade_move"]
		if bool(bm_info["enabled"]):
			var bm_armed: bool = (_killzone.interaction_mode == "move" and _killzone.move_action_kind == "barricade_move")
			var slot_bm := _make_skill_slot(
				"reposition", "携盾", "与掩体移动 (1AP)", str(bm_info["reason"]),
				"", 1, Color(0.32, 0.72, 0.92), false, bm_armed, 0
			)
			slot_bm.triggered.connect(func():
				if bm_armed:
					_killzone.clear_interaction()
				else:
					_killzone.start_move_mode(uid, "barricade_move")
				_refresh_all_ui()
			)

	# [5] Shoot Slot
	var shoot_info: Dictionary = acts["shoot"]
	var shoot_dis: bool = not bool(shoot_info["enabled"]) or usable_ranged.is_empty()
	var shoot_armed: bool = (_killzone.interaction_mode == "shoot")
	var slot_shoot := _make_skill_slot(
		"shoot", "射击", "远程射击 (Shoot)",
		str(shoot_info["reason"]) if shoot_dis else "投射三维高低差激光视线并锁定敌军目标开火！",
		"5", 1, KTTheme.COL_CRIMSON, shoot_dis, shoot_armed, KEY_5
	)
	slot_shoot.triggered.connect(func():
		if shoot_armed:
			_killzone.clear_interaction()
		elif not usable_ranged.is_empty():
			_selected_weapon_idx = clampi(_selected_weapon_idx, 0, usable_ranged.size() - 1)
			_killzone.start_shoot_mode(uid, usable_ranged[_selected_weapon_idx])
		_refresh_all_ui()
	)

	# [6] Melee Fight Slot
	var fight_info: Dictionary = acts["fight"]
	var fight_dis: bool = not bool(fight_info["enabled"])
	var fight_armed: bool = (_killzone.interaction_mode == "fight")
	var slot_fight := _make_skill_slot(
		"fight", "近战", "白刃搏杀 (Fight)",
		str(fight_info["reason"]) if fight_dis else "与控制区内的敌军展开战锤交替出击/招架对决！",
		"6", 1, Color(0.95, 0.28, 0.34), fight_dis, fight_armed, KEY_6
	)
	slot_fight.triggered.connect(func():
		var enemies: Array = KTGeometry.is_in_enemy_control_range(op, op["pos"], engine.state)
		if enemies.size() == 1:
			var e0: Dictionary = enemies[0]
			_on_board_fight_target_clicked(str(e0["uid"]))
		elif fight_armed:
			_killzone.clear_interaction()
			_refresh_all_ui()
		else:
			_killzone.start_fight_mode(uid)
			_refresh_all_ui()
	)

	# [7] Secure Objective Slot
	var sec_info: Dictionary = acts["secure"]
	var sec_dis: bool = not bool(sec_info["enabled"])
	var slot_sec := _make_skill_slot(
		"secure", "占领", "占领战略信标 (Secure)",
		str(sec_info["reason"]) if sec_dis else "控制脚下的战略目标点以在转折点结算夺取 VP！",
		"7", 1, KTTheme.COL_GOLD_BRIGHT, sec_dis, false, KEY_7
	)
	slot_sec.triggered.connect(func():
		KTSfx.play("move_confirm", -4.0, 0.04)
		engine.execute_secure_objective(uid)
		_killzone.spawn_floating_text(op["pos"], "🚩 已占领目标点!", KTTheme.COL_GOLD_BRIGHT)
		_refresh_all_ui()
	)

	# [8] Unique Action Slot(s)
	var u_idx: int = 0
	for ua_v in engine.get_unique_actions(op):
		var ua: Dictionary = ua_v
		var ab_id: String = str(ua["id"])
		var ab_nm: String = str(ua["name"])
		var hk_num: String = "8" if u_idx == 0 else ("9" if u_idx == 1 else "")
		var kc_u: int = KEY_8 if u_idx == 0 else (KEY_9 if u_idx == 1 else 0)
		var u_cost: int = 0 if ab_id == "eq_resupply" else int(ua.get("cost", 1))
		var needs_pt: bool = bool(ua.get("needs_point", false))
		var needs_tgt: bool = bool(ua.get("needs_target", false))
		var u_armed: bool = (needs_pt and _killzone.interaction_mode == "pick_point") or (needs_tgt and _killzone.interaction_mode == "pick_enemy" and _killzone.pick_ability == ab_id)
		var short_u: String = ab_nm.split("(")[0].split("（")[0].strip_edges().substr(0, 4)
		var slot_u := _make_skill_slot(
			"unique", short_u, ab_nm, str(ua["desc"]),
			hk_num, u_cost, KTTheme.COL_VIOLET, not bool(ua["enabled"]), u_armed, kc_u
		)
		slot_u.triggered.connect(func():
			if needs_pt:
				if u_armed:
					_killzone.clear_interaction()
				else:
					_killzone.start_pick_point_mode(uid)
				_refresh_all_ui()
				return
			if needs_tgt:
				if u_armed:
					_killzone.clear_interaction()
				else:
					var cur_op: Dictionary = engine.get_op(uid)
					var t_uids: Array = []
					for st_op in engine.unique_action_targets(cur_op, ab_id):
						t_uids.append(str(st_op["uid"]))
					_killzone.start_pick_enemy_mode(uid, t_uids)
					_killzone.pick_ability = ab_id
				_refresh_all_ui()
				return
			var res: Dictionary = engine.execute_unique_action(uid, ab_id)
			if not bool(res["ok"]):
				_killzone.spawn_floating_text(op["pos"], str(res.get("msg", "")), KTTheme.COL_CRIMSON)
			else:
				KTSfx.play("order_servo", -3.5, 0.05)
				_killzone.spawn_floating_text(op["pos"], "✨ " + ab_nm, KTTheme.COL_VIOLET)
			_refresh_all_ui()
		)
		u_idx += 1

	# [0] Firefight Ploy Slot
	var side_st: Dictionary = engine.state["sides"][op["side"]]
	if is_imp and bool(op["charged_this_tp"]) and not side_st["used_ploys_tp"].has("shock_assault"):
		var sa_dis: bool = int(side_st["cp"]) < 1 and str(op["op_id"]) != "aod_captain"
		var slot_sa := _make_skill_slot(
			"ploy", "突袭", "交战计谋：震慑突袭 (1CP)",
			"冲锋后近战获得【震荡】且首次出击伤害 +1（队长免费）！",
			"0", 0, KTTheme.COL_GOLD_BRIGHT, sa_dis, false, KEY_0
		)
		slot_sa.triggered.connect(func():
			engine.use_firefight_ploy("A", "shock_assault", uid)
			_refresh_all_ui()
		)
	elif (not is_imp) and not side_st["used_ploys_tp"].has("virulent_poison") and int(side_st["cp"]) >= 1:
		var slot_vp := _make_skill_slot(
			"ploy", "剧毒", "交战计谋：致命毒素 (1CP)",
			"立即令 7\" 内可见最近敌军感染纳垢毒素！",
			"0", 0, KTTheme.COL_NURGLE, false, false, KEY_0
		)
		slot_vp.triggered.connect(func():
			engine.use_firefight_ploy("B", "virulent_poison", uid)
			_refresh_all_ui()
		)

	# [Space] End Activation Slot
	var slot_end := _make_skill_slot(
		"end", "结束", "结束当前特工激活 [Space]",
		"完成本名特工的行动并交还回合控制权。",
		"␣", 0, KTTheme.COL_EMERALD, false, false, 0
	)
	slot_end.triggered.connect(func():
		_end_human_activation()
	)

	_skill_bar_panel.reset_size()

	# 3. Weapon cards above the hotbar when Shoot Mode is armed. Each card: name + stat line + keyword chips
	#    (hover a chip for the rule's effect, click for the rulebook text).
	if _killzone.interaction_mode == "shoot" and not usable_ranged.is_empty():
		_weapon_strip.visible = true
		for wi in range(usable_ranged.size()):
			var w: Dictionary = usable_ranged[wi]
			var is_sel_w: bool = (wi == _selected_weapon_idx)
			var idx_w: int = wi
			var card := KTWidgets.WeaponCard.new()
			card.setup(w, is_sel_w, fcol)
			card.selected.connect(func():
				_selected_weapon_idx = idx_w
				_killzone.start_shoot_mode(uid, usable_ranged[idx_w])
				_refresh_all_ui()
			)
			_weapon_hb.add_child(card)
		_weapon_strip.reset_size()
	else:
		_weapon_strip.visible = false


func _add_hotbar_move_slot(
	uid: String,
	kind: String,
	short_lbl: String,
	full_title: String,
	keycode: int,
	hk_str: String,
	info: Dictionary,
	col: Color
) -> void:
	var dis: bool = not bool(info["enabled"])
	var armed: bool = (_killzone.interaction_mode == "move" and _killzone.move_action_kind == kind)
	var cost: int = int(info.get("cost", 1))
	var desc: String = str(info["reason"]) if dis else "在 3D 地形上绘出全息标尺与攀爬立柱，左键确认落脚点。"
	var slot := _make_skill_slot(kind, short_lbl, full_title, desc, hk_str, cost, col, dis, armed, keycode)
	slot.triggered.connect(func():
		if armed:
			_killzone.clear_interaction()
		else:
			_killzone.start_move_mode(uid, kind)
		_refresh_all_ui()
	)


func _make_skill_slot(
	icon_id: String,
	short_lbl: String,
	full_title: String,
	desc: String,
	hk_str: String,
	ap_cost: int,
	col: Color,
	dis: bool,
	armed: bool,
	keycode: int
) -> KTWidgets.SkillSlot:
	var slot := KTWidgets.SkillSlot.new()
	slot.setup(icon_id, short_lbl, full_title, desc, hk_str, ap_cost, col, dis, armed)
	slot.hovered_changed.connect(func(hov: bool, ctrl: Control):
		if hov:
			_show_skill_tooltip(ctrl as KTWidgets.SkillSlot)
		else:
			_hide_skill_tooltip()
	)
	_skill_bar_hb.add_child(slot)
	if keycode != 0:
		assert(not _active_hotbar_slots.has(keycode), "Duplicate hotbar keycode: %d" % keycode)
		_active_hotbar_slots[keycode] = slot
	return slot


# ==============================================================================
# BOARD SIGNALS & COMBAT LAUNCHERS
# ==============================================================================

func _has_local_active_operative(expected_uid: String = "") -> bool:
	if engine.state.is_empty() or engine.is_ai_turn():
		return false
	if str(engine.state.get("phase", "")) != "FIREFIGHT":
		return false
	var act_uid: String = str(engine.state.get("active_op_uid", ""))
	if act_uid.is_empty():
		return false
	if not expected_uid.is_empty() and act_uid != expected_uid:
		return false
	return true


func _on_board_hover_changed(info: String) -> void:
	_hover_text_cache = info
	if _hover_lbl != null:
		_hover_lbl.visible = not info.is_empty()
		_hover_lbl.text = info


func _on_board_operative_clicked(uid: String) -> void:
	var op: Dictionary = engine.get_op(uid)
	if op.is_empty():
		return
	if engine.is_ai_turn():
		_notify_illegal_click("敌方回合行动中", op.get("pos", Vector2(15.0, 11.0)))
		return
	if str(engine.state["phase"]) == "FIREFIGHT":
		var active_uid: String = str(engine.state["active_op_uid"])
		if active_uid.is_empty():
			var chk: Dictionary = engine.can_operative_be_selected_for_turn(op)
			if bool(chk["ok"]):
				KTSfx.play("order_servo", -4.0, 0.04)
				engine.activate_operative(uid)
				_refresh_all_ui()
				return
			_notify_illegal_click(str(chk.get("reason", "当前不可激活该特工")), op.get("pos", Vector2(15.0, 11.0)))
		elif uid != active_uid:
			var act_op: Dictionary = engine.get_op(active_uid)
			_killzone.selected_uid = active_uid
			var act_msg: String = ("请先结束【%s】激活" % str(act_op["name"])) if not act_op.is_empty() else "已有激活特工"
			_notify_illegal_click(act_msg, op.get("pos", Vector2(15.0, 11.0)), KTTheme.COL_GOLD_BRIGHT)
	_refresh_all_ui()


func _on_board_move_committed(waypoints: Array[Vector2]) -> void:
	if not _has_local_active_operative(_killzone.selected_uid):
		_killzone.clear_interaction()
		_refresh_all_ui()
		return
	var uid: String = str(engine.state["active_op_uid"])
	var res: Dictionary = engine.execute_move(uid, _killzone.move_action_kind, waypoints)
	if bool(res["ok"]):
		KTSfx.play("move_confirm", -4.0, 0.05)
		_killzone.clear_interaction()
	elif not waypoints.is_empty() and not str(res.get("msg", "")).is_empty():
		_killzone.spawn_floating_text(waypoints[-1], str(res.get("msg", "")), KTTheme.COL_CRIMSON)
	_refresh_all_ui()


func _on_board_point_picked(uid: String, pos_in: Vector2) -> void:
	if not _has_local_active_operative(uid):
		_killzone.clear_interaction()
		_refresh_all_ui()
		return
	var res: Dictionary = engine.use_smoke_grenade(uid, pos_in)
	if bool(res.get("ok", false)):
		_killzone.clear_interaction()
		KTSfx.play("order_servo", -3.5, 0.05)
		_killzone.spawn_floating_text(pos_in, "💨 烟雾手雷", KTTheme.COL_TEXT)
	else:
		_killzone.spawn_floating_text(pos_in, str(res.get("msg", "")), KTTheme.COL_CRIMSON)
	_refresh_all_ui()


func _on_board_enemy_picked(uid: String, target_uid: String) -> void:
	if not _has_local_active_operative(uid):
		_killzone.clear_interaction()
		_refresh_all_ui()
		return
	var pick_ab: String = _killzone.pick_ability if not _killzone.pick_ability.is_empty() else "eq_stun"
	var res: Dictionary = engine.execute_target_action(uid, pick_ab, target_uid)
	if bool(res.get("ok", false)):
		_killzone.clear_interaction()
		KTSfx.play("order_servo", -3.5, 0.05)
		if pick_ab == "tac_surveil":
			var sv_op: Dictionary = engine.get_op(target_uid)
			if not sv_op.is_empty():
				_killzone.spawn_floating_text(sv_op["pos"], "📡 已被监控", KTTheme.COL_GOLD_BRIGHT)
		var rolls: Array = res.get("rolls", [])
		for r_v in rolls:
			var r_d: Dictionary = r_v
			var v_op: Dictionary = engine.get_op(str(r_d["uid"]))
			if v_op.is_empty():
				continue
			var roll_i: int = int(r_d["roll"])
			var stunned: bool = bool(r_d["stunned"])
			var txt: String = ("💫 震荡 %d：APL-1" % roll_i) if stunned else ("震荡 %d：无效" % roll_i)
			_killzone.spawn_floating_text(v_op["pos"], txt, KTTheme.COL_GOLD_BRIGHT if stunned else KTTheme.COL_MUTED)
	else:
		var t_op: Dictionary = engine.get_op(target_uid)
		var f_pos: Vector2 = t_op["pos"] if not t_op.is_empty() else Vector2(15.0, 11.0)
		_killzone.spawn_floating_text(f_pos, str(res.get("msg", "")), KTTheme.COL_CRIMSON)
	_refresh_all_ui()


func _on_board_shoot_target_clicked(defender_uid: String, shot_eval: Dictionary) -> void:
	if not _has_local_active_operative(_killzone.selected_uid):
		_killzone.clear_interaction()
		_refresh_all_ui()
		return
	var att: Dictionary = engine.get_op(str(engine.state["active_op_uid"]))
	var def: Dictionary = engine.get_op(defender_uid)
	if att.is_empty() or def.is_empty():
		return
	var weapon: Dictionary = _killzone.active_shoot_weapon
	engine.push_undo()
	engine.commit_shooting_action_cost(att, weapon)
	_killzone.clear_interaction()
	_hide_skill_tooltip()
	_killzone.play_ranged_salvo_vfx(str(att["uid"]), defender_uid, weapon)

	var sess: Dictionary = KTCombat.create_shooting_session(att, def, weapon, shot_eval, engine.state, engine.rng)
	_dice_modal.open_shooting(engine, sess, false)
	_refresh_all_ui()


func _on_board_fight_target_clicked(defender_uid: String) -> void:
	var expected_f_uid: String = _killzone.selected_uid if _killzone.interaction_mode == "fight" else ""
	if not _has_local_active_operative(expected_f_uid):
		_killzone.clear_interaction()
		_refresh_all_ui()
		return
	var att: Dictionary = engine.get_op(str(engine.state["active_op_uid"]))
	var def: Dictionary = engine.get_op(defender_uid)
	if att.is_empty() or def.is_empty():
		return
	engine.push_undo()
	engine.commit_melee_action_cost(att)
	_killzone.clear_interaction()
	_hide_skill_tooltip()
	_killzone.play_melee_engage_vfx(str(att["uid"]), defender_uid)

	var att_w: Dictionary = KTAI.pick_best_melee_weapon(att)
	var def_w: Dictionary = KTAI.pick_best_melee_weapon(def)
	var sess: Dictionary = KTCombat.create_melee_session(att, def, att_w, def_w, engine.state, engine.rng)
	_dice_modal.open_melee(engine, sess)
	_refresh_all_ui()


func _on_combat_vfx_triggered(ev: Dictionary) -> void:
	var kind: String = str(ev.get("kind", ""))
	if kind == "ranged_impact":
		var w_d: Dictionary = ev.get("weapon", {})
		_killzone.play_ranged_resolution_vfx(
			str(ev.get("attacker_uid", "")),
			str(ev.get("defender_uid", "")),
			w_d,
			int(ev.get("dmg", 0)),
			int(ev.get("crits", 0)),
			bool(ev.get("killed", false)),
			bool(ev.get("poisoned", false))
		)
		_refresh_all_ui()
	elif kind == "melee_step":
		var wm_d: Dictionary = ev.get("weapon", {})
		_killzone.play_melee_step_vfx(
			str(ev.get("actor_uid", "")),
			str(ev.get("target_uid", "")),
			str(ev.get("action", "strike_norm")),
			wm_d,
			int(ev.get("dmg", 0)),
			bool(ev.get("killed", false))
		)
		_refresh_all_ui()


func _on_initiative_modal_finished() -> void:
	_killzone.restore_post_combat_camera()
	_refresh_all_ui()


func _on_combat_modal_finished(sess: Dictionary) -> void:
	_killzone.restore_post_combat_camera()
	var def: Dictionary = engine.get_op(str(sess.get("defender_uid", "")))
	var att: Dictionary = engine.get_op(str(sess.get("attacker_uid", "")))
	if str(sess.get("kind", "")) == "shooting":
		var dmg: int = int(sess.get("total_damage_dealt", 0))
		if not def.is_empty():
			engine.add_log("🎯 【%s】射击【%s】造成 %d 点伤害%s" % [
				str(att.get("name", "")), str(def["name"]), dmg,
				"（已击杀！）" if bool(def["incapacitated"]) else ""
			])
	else:
		if not def.is_empty() and bool(def["incapacitated"]):
			_killzone.spawn_floating_text(def["pos"], "💀 近战斩杀!", KTTheme.COL_CRIMSON)
		if not att.is_empty() and bool(att["incapacitated"]):
			_killzone.spawn_floating_text(att["pos"], "💀 反击倒下!", KTTheme.COL_CRIMSON)

	var act_uid: String = str(engine.state.get("active_op_uid", ""))
	if not act_uid.is_empty():
		var active_op: Dictionary = engine.get_op(act_uid)
		if active_op.is_empty() or bool(active_op["incapacitated"]) or (bool(engine.state["active_is_counteract"]) and int(active_op["ap_left"]) <= 0):
			engine.end_operative_activation()

	_refresh_all_ui()


# ==============================================================================
# AI TURN CHOREOGRAPHER
# ==============================================================================

func _run_ai_step() -> void:
	if _is_any_modal_overlay_open() or not engine.is_ai_turn():
		return
	_sync_ai_undo_guard()

	var ai_side: String = str(engine.state["turn_side"])
	var phase: String = str(engine.state["phase"])

	if phase == "DROPZONE":
		# AI keeps the zone the map assigns it (both are mirror images on the official layouts)
		engine.choose_drop_zone(false)
		_refresh_all_ui()
		return

	if phase == "SELECT":
		var sel_side: String = engine.setup_side()
		engine.confirm_roster(sel_side, engine.default_roster(sel_side))
		_refresh_all_ui()
		return

	if phase == "EQUIP":
		var eq_side: String = engine.setup_side()
		engine.confirm_equipment(eq_side, engine.default_equipment(eq_side))
		_refresh_all_ui()
		return

	if phase == "TACOP":
		var tc_side: String = engine.setup_side()
		engine.choose_tac_op(tc_side, engine.default_tac_op(tc_side))
		_refresh_all_ui()
		return

	if phase == "EQDEPLOY":
		if bool(engine.ai_eq_deploy_next().get("ok", false)):
			KTSfx.play("move_confirm", -8.0, 0.05)
		_refresh_all_ui()
		return

	if phase == "DEPLOY":
		var res_d: Dictionary = engine.ai_deploy_next()
		if bool(res_d.get("ok", false)):
			KTSfx.play("move_confirm", -8.0, 0.05)
		_refresh_all_ui()
		return

	if phase == "INITIATIVE":
		# AI owes an initiative-card decision but the roll-off panel is closed (e.g. after an undo)
		_roll_or_resume_initiative()
		return

	if phase == "STRATEGY":
		var dec: Dictionary = KTAI.decide_strategy_step(engine, ai_side)
		if str(dec["action"]) == "primary":
			engine.choose_primary_op(ai_side, str(dec["op"]))
		elif str(dec["action"]) == "envoy":
			engine.choose_envoy(ai_side, str(dec["uid"]))
		elif str(dec["action"]) == "ploy":
			engine.use_strategic_ploy(ai_side, str(dec["id"]))
		else:
			engine.pass_strategy(ai_side)
		_refresh_all_ui()
		return

	if phase == "FIREFIGHT":
		var active_uid: String = str(engine.state["active_op_uid"])
		if active_uid.is_empty():
			var pick: Dictionary = KTAI.pick_operative_to_activate(engine, ai_side)
			if pick.is_empty():
				engine.pass_counteract(ai_side)
			else:
				_killzone.selected_uid = str(pick["uid"])
				engine.activate_operative(str(pick["uid"]), str(pick["order"]))
			_refresh_all_ui()
			return

		var op: Dictionary = engine.get_op(active_uid)
		if op.is_empty() or bool(op["incapacitated"]) or int(op["ap_left"]) <= 0:
			engine.end_operative_activation()
			_refresh_all_ui()
			return

		var step_act: Dictionary = KTAI.decide_next_operative_action(engine, op)
		var kind: String = str(step_act.get("kind", "end"))

		if kind == "move":
			var mtype: String = str(step_act["move_type"])
			var pts: Array[Vector2] = step_act["waypoints"]
			engine.execute_move(active_uid, mtype, pts)
			KTSfx.play("move_confirm", -6.0, 0.06)
			_killzone.spawn_floating_text(op["pos"], "🤖 %s" % mtype.to_upper(), KTTheme.COL_NURGLE)
			_refresh_all_ui()
		elif kind == "secure":
			engine.execute_secure_objective(active_uid)
			_killzone.spawn_floating_text(op["pos"], "🚩 占领目标点!", KTTheme.COL_NURGLE)
			_refresh_all_ui()
		elif kind == "unique":
			engine.execute_unique_action(active_uid, str(step_act["ability_id"]))
			_killzone.spawn_floating_text(op["pos"], "✨ 灵能/特技!", KTTheme.COL_VIOLET)
			_refresh_all_ui()
		elif kind == "shoot":
			var def_s_uid: String = str(step_act["target_uid"])
			var def_s: Dictionary = engine.get_op(def_s_uid)
			var w_s: Dictionary = step_act["weapon"]
			engine.commit_shooting_action_cost(op, w_s)
			_killzone.play_ranged_salvo_vfx(active_uid, def_s_uid, w_s)
			var sess_s: Dictionary = KTCombat.create_shooting_session(op, def_s, w_s, step_act["shot_eval"], engine.state, engine.rng)
			_dice_modal.open_shooting(engine, sess_s, true)
			_refresh_all_ui()
		elif kind == "fight":
			var def_f_uid: String = str(step_act["target_uid"])
			var def_f: Dictionary = engine.get_op(def_f_uid)
			engine.commit_melee_action_cost(op)
			_killzone.play_melee_engage_vfx(active_uid, def_f_uid)
			var sess_m: Dictionary = KTCombat.create_melee_session(op, def_f, KTAI.pick_best_melee_weapon(op), KTAI.pick_best_melee_weapon(def_f), engine.state, engine.rng)
			_dice_modal.open_melee(engine, sess_m)
			_refresh_all_ui()
		else:
			engine.end_operative_activation()
			_refresh_all_ui()


# ==============================================================================
# AUTOMATED VISUAL QA SCREENSHOT TOUR (`-- --autotest-screenshots`)
# ==============================================================================

func _run_autotest_screenshot_tour() -> void:
	var out_dir: String = ProjectSettings.globalize_path("res://build/screenshots")
	DirAccess.make_dir_recursive_absolute(out_dir)

	await get_tree().process_frame
	await get_tree().process_frame
	_on_viewport_resized()
	await get_tree().create_timer(0.28).timeout
	_save_vp_png(out_dir + "/01_title_setup.png")

	# 2. Enter Hotseat battlefield -> Roll TP1 Initiative on the Close-Up 3D Tabletop Velvet Dice Tray!
	_sel_mode = "hotseat"
	_title_overlay.visible = false
	_start_new_battle()
	# Pre-battle step 2 capture: operative selection hand
	engine.choose_drop_zone(false)
	_refresh_all_ui()
	_phase_toast.visible = false
	await get_tree().create_timer(0.24).timeout
	_save_vp_png(out_dir + "/00a_roster_select.png")
	while str(engine.state["phase"]) == "SELECT":
		var rs: String = engine.setup_side()
		engine.confirm_roster(rs, engine.default_roster(rs))
	engine.state["setup"]["equip_side"] = str(engine.state["human_side"])
	_refresh_all_ui()
	_phase_toast.visible = false
	await get_tree().create_timer(0.24).timeout
	_save_vp_png(out_dir + "/00b_equipment_select.png")
	while str(engine.state["phase"]) == "EQUIP":
		var eqs_t: String = engine.setup_side()
		var eq_ids: Array = []
		if eqs_t == str(engine.state["human_side"]):
			eq_ids = ["UNI-EQ-razorwire", "UNI-EQ-portablebarricade", "UNI-EQ-ammocache", "UNI-EQ-utilitygrenades"]
		else:
			eq_ids = ["UNI-EQ-lightbarricades", "UNI-EQ-heavybarricade", "UNI-EQ-mines", "UNI-EQ-explosivegrenades"]
		engine.confirm_equipment(eqs_t, eq_ids)
	engine.state["setup"]["tac_side"] = str(engine.state["human_side"])
	_refresh_all_ui()
	_phase_toast.visible = false
	await get_tree().create_timer(0.24).timeout
	_save_vp_png(out_dir + "/00c_tac_op_select.png")
	engine.auto_setup()
	if str(engine.state["phase"]) == "EQDEPLOY":
		engine.ai_eq_deploy_next()
		engine.ai_eq_deploy_next()
		var eq_s: String = engine.eq_deploy_side()
		if not eq_s.is_empty() and not engine.eq_pending(eq_s).is_empty():
			var eq_k: String = str(engine.eq_pending(eq_s)[0])
			var eq_spot: Dictionary = engine.ai_equipment_spot(eq_s, eq_k)
			_killzone.start_eq_deploy_mode(eq_k)
			_killzone.eq_deploy_rot = bool(eq_spot.get("rot", false))
			_killzone.hovered_board_inches = eq_spot.get("pos", Vector2(15.0, 11.0))
		_refresh_all_ui()
		_phase_toast.visible = false
		await get_tree().create_timer(0.3).timeout
		_save_vp_png(out_dir + "/00d_equipment_deploy.png")
		_killzone.clear_interaction()
		while str(engine.state["phase"]) == "EQDEPLOY":
			if not bool(engine.ai_eq_deploy_next().get("ok", false)):
				break
		_refresh_all_ui()
	# Capture the deployment phase with half the teams placed, then auto-complete it
	for _i in range(6):
		engine.ai_deploy_next()
	_refresh_all_ui()
	_phase_toast.visible = false
	await get_tree().create_timer(0.24).timeout
	_save_vp_png(out_dir + "/00_deployment.png")
	while str(engine.state["phase"]) == "DEPLOY":
		if not bool(engine.ai_deploy_next().get("ok", false)):
			break
	var init_res: Dictionary = engine.auto_initiative()
	init_res["roll_a"] = 6
	init_res["roll_b"] = 3
	init_res["winner"] = "A"
	engine.state["init"]["roll"] = {"A": 6, "B": 3}
	engine.state["init"]["mod"] = {"A": 0, "B": 0}
	engine.state["init"]["winner"] = "A"
	engine.state["initiative_side"] = "A"
	engine.state["turn_side"] = "A"
	_dice_modal.open_initiative(engine, init_res, true)
	_refresh_all_ui()
	_phase_toast.visible = false
	await get_tree().create_timer(0.24).timeout
	_save_vp_png(out_dir + "/07_initiative_3d_dice.png")

	# 2b. Strategy Phase: Fullscreen 3D HD-2D Killzone + Broadcast Crest + Sliding Squad Medallions + Lifted Stratagem Card Hand!
	_dice_modal.visible = false
	_on_initiative_modal_finished()
	_killzone.reset_isometric_view(true)
	_phase_toast.visible = false
	await get_tree().create_timer(0.24).timeout
	_save_vp_png(out_dir + "/02a_primary_op.png")
	engine.choose_primary_op("A", "kill")
	engine.choose_primary_op("B", "key")
	engine.state["turn_side"] = "A"
	if not engine.state.has("eq_markers"):
		engine.state["eq_markers"] = []
	(engine.state["eq_markers"] as Array).append({"kind": "smoke", "side": "A", "pos": Vector2(20.0, 17.5), "tp_placed": 1, "acts_left": -1})
	_refresh_all_ui()
	_phase_toast.visible = false
	await get_tree().process_frame
	for ch in _context_hb.get_children():
		if ch is KTWidgets.StratagemCard:
			var sc: KTWidgets.StratagemCard = ch
			sc._is_hov = true
			sc._hover_t = 1.0
			sc.queue_redraw()
			break
	await get_tree().create_timer(0.24).timeout
	_save_vp_png(out_dir + "/02_strategy_phase.png")

	# 3. Firefight + 3D Climb Ruler + Orbit Camera (+142 deg) + Bottom-Left Hero Console + Octagonal Skill Hotbar + Tooltip Popover!
	engine.use_strategic_ploy("A", "doctrine_devastator")
	engine.use_strategic_ploy("B", "contagion")
	engine.pass_strategy("A")
	engine.pass_strategy("B")
	engine.state["turn_side"] = "A"
	_phase_toast.visible = false
	var first_op: Dictionary = engine.state["operatives"][0]
	var cap_uid: String = str(first_op["uid"])
	# Map 4: stand just north of the SW large ruin (deck Rect2(4.5, 15.5, 6.5, 4.0), open to the
	# north) and aim the ruler onto the deck: 3" along the ground + 2" climb.
	first_op["pos"] = Vector2(7.0, 13.6)
	_killzone.sync_visual_positions(true)
	engine.activate_operative(cap_uid, "engage")
	_killzone.start_move_mode(cap_uid, "reposition")
	_killzone.hovered_board_inches = Vector2(7.0, 16.6)
	_killzone.target_focus = _killzone.inches_to_world(Vector2(7.4, 15.4), 1.3)
	_killzone.target_yaw_deg = 142.0
	_killzone.target_pitch_deg = 31.0
	_killzone.target_dist = 31.0
	_killzone.cam_focus = _killzone.target_focus
	_killzone.cam_yaw_deg = _killzone.target_yaw_deg
	_killzone.cam_pitch_deg = _killzone.target_pitch_deg
	_killzone.cam_dist = _killzone.target_dist
	_refresh_all_ui()
	_phase_toast.visible = false
	await get_tree().process_frame
	if _active_hotbar_slots.has(KEY_1):
		var s1: KTWidgets.SkillSlot = _active_hotbar_slots[KEY_1]
		s1._is_hov = true
		s1._hover_t = 1.0
		s1.queue_redraw()
		_show_skill_tooltip(s1)
	await get_tree().create_timer(0.26).timeout
	_save_vp_png(out_dir + "/03_move_vantage_ruler.png")

	# 4. Heroic 2.55D Isometric View: Sniper on +3.5" 2F Stronghold + Rotating XCOM Target-Lock Reticle & Hit% Forecast + Floating Weapon Strip!
	_hide_skill_tooltip()
	_killzone.reset_isometric_view(true)
	var sniper_uid: String = ""
	var assault_uid: String = ""
	var pm_enemy_uid: String = ""
	var pm_fighter_uid: String = ""
	var pm_count: int = 0
	for op_v in engine.state["operatives"]:
		var op: Dictionary = op_v
		var oid: String = str(op["op_id"])
		if oid == "aod_sniper":
			sniper_uid = str(op["uid"])
			op["pos"] = Vector2(25.0, 5.0) # NE large ruin deck (+3.0"), map 4
		elif oid in ["aod_assault_warrior", "aod_assault_sergeant"] and assault_uid.is_empty():
			assault_uid = str(op["uid"])
		elif str(op["side"]) == "B":
			pm_count += 1
			if oid == "pm_fighter" and pm_fighter_uid.is_empty():
				pm_fighter_uid = str(op["uid"])
			if pm_count == 1:
				pm_enemy_uid = str(op["uid"])
				op["pos"] = Vector2(23.6, 10.7) # behind light rubble (x 23..25, y 6.5..10): standard cover
				op["order"] = "engage"
			elif pm_count == 2:
				op["pos"] = Vector2(22.6, 13.4)
				op["order"] = "engage"
			elif pm_count == 3:
				op["pos"] = Vector2(19.0, 5.5)
				op["order"] = "engage"
	if assault_uid.is_empty():
		assault_uid = cap_uid
	if pm_fighter_uid.is_empty():
		pm_fighter_uid = pm_enemy_uid

	_killzone.sync_visual_positions(true)
	engine.recalculate_objectives_and_kills()
	engine.end_operative_activation()
	engine.state["turn_side"] = "A"
	engine.activate_operative(sniper_uid, "engage")
	var sniper_op: Dictionary = engine.get_op(sniper_uid)
	var usable: Array = engine.get_usable_ranged_weapons(sniper_op)
	var mortis_idx: int = mini(3, usable.size() - 1)
	_selected_weapon_idx = mortis_idx
	_killzone.start_shoot_mode(sniper_uid, usable[mortis_idx])
	_killzone.hovered_uid = pm_enemy_uid
	_on_board_hover_changed("🎯 锁定【%s】 · 制高点精准1 / 普通掩护" % str(engine.get_op(pm_enemy_uid)["name"]))
	_refresh_all_ui()
	_phase_toast.visible = false
	await get_tree().create_timer(0.26).timeout
	_save_vp_png(out_dir + "/04_shoot_cover_cone.png")

	# 5. RANGED CLASH + PHYSICAL 3D TABLETOP DICE TRAY: Fighters & 3D Explosion on Left/Center + 3D Crimson/Emerald Dice in Foreground-Right Velvet Box!
	var shot_ev: Dictionary = _killzone.cached_shot_evals[pm_enemy_uid]
	_on_board_shoot_target_clicked(pm_enemy_uid, shot_ev)
	_killzone.cam_focus = _killzone.target_focus
	_killzone.cam_yaw_deg = _killzone.target_yaw_deg
	_killzone.cam_pitch_deg = _killzone.target_pitch_deg
	_killzone.cam_dist = _killzone.target_dist
	_killzone._apply_camera_transform()
	_dice_modal.session["atk_dice"] = [
		{"val": 6, "rerolled": false},
		{"val": 5, "rerolled": false},
		{"val": 4, "rerolled": false},
		{"val": 6, "rerolled": false},
	]
	KTCombat.lock_in_shooting_attacks(_dice_modal.session, engine.state, engine.rng)
	_dice_modal.session["def_dice"] = [
		{"val": 5, "rerolled": false},
		{"val": 2, "rerolled": false},
		{"val": 1, "rerolled": false},
	]
	KTCombat.finalize_shooting_resolution(_dice_modal.session, engine.state, engine.rng)
	engine.recalculate_objectives_and_kills()
	_dice_modal._animate_next_roll = false
	_dice_modal.snap_entrance_for_capture()
	if _killzone.get_dice_3d() != null:
		_killzone.get_dice_3d().sync_shooting_dice(_dice_modal.session, "", -1, "", -1, true)
	_dice_modal._emit_ranged_impact_once()
	_dice_modal._rebuild_ui()
	_killzone.play_ranged_salvo_vfx(sniper_uid, pm_enemy_uid, usable[0])
	_killzone.cam_focus = _killzone.target_focus
	_killzone.cam_yaw_deg = _killzone.target_yaw_deg
	_killzone.cam_pitch_deg = _killzone.target_pitch_deg
	_killzone.cam_dist = _killzone.target_dist
	_killzone._apply_camera_transform()
	await get_tree().create_timer(0.12).timeout
	_save_vp_png(out_dir + "/05_dice_combat_modal.png")

	# 6. MELEE DUEL + PHYSICAL 3D TABLETOP DICE TRAY: 3D Crescent Slash & Sparks + Dual-Bay 3D Melee Dice & Flying Clash Die!
	_dice_modal.visible = false
	_killzone.clear_transient_combat_fx()
	engine.end_operative_activation()
	var att_m: Dictionary = engine.get_op(assault_uid)
	var def_m: Dictionary = engine.get_op(pm_fighter_uid)
	att_m["incapacitated"] = false
	def_m["incapacitated"] = false
	att_m["wounds_cur"] = int(att_m["wounds_max"])
	def_m["wounds_cur"] = int(def_m["wounds_max"])
	att_m["ready"] = true
	att_m["ap_left"] = 3
	att_m["actions_taken"] = []
	att_m["pos"] = Vector2(10.6, 12.0) # open ground west of the central stronghold, map 4
	def_m["pos"] = Vector2(12.0, 11.7)
	_killzone.sync_visual_positions(true)
	engine.state["turn_side"] = "A"
	engine.activate_operative(assault_uid, "engage")
	_on_board_fight_target_clicked(pm_fighter_uid)
	_killzone.cam_focus = _killzone.target_focus
	_killzone.cam_yaw_deg = _killzone.target_yaw_deg
	_killzone.cam_pitch_deg = _killzone.target_pitch_deg
	_killzone.cam_dist = _killzone.target_dist
	_killzone._apply_camera_transform()
	_dice_modal._animate_next_roll = false
	_dice_modal.snap_entrance_for_capture()
	_dice_modal.session["att"]["crits"] = maxi(2, int(_dice_modal.session["att"]["crits"]))
	_dice_modal.session["att"]["normals"] = maxi(2, int(_dice_modal.session["att"]["normals"]))
	_dice_modal.session["def"]["crits"] = maxi(1, int(_dice_modal.session["def"]["crits"]))
	_dice_modal.session["def"]["normals"] = maxi(2, int(_dice_modal.session["def"]["normals"]))
	if _killzone.get_dice_3d() != null:
		_killzone.get_dice_3d().sync_melee_dice(_dice_modal.session, false, true)
	_dice_modal._run_human_melee_step("strike_crit")
	await get_tree().create_timer(0.16).timeout
	await get_tree().process_frame
	await RenderingServer.frame_post_draw
	_save_vp_png(out_dir + "/06_melee_particles_duel.png")

	print("AUTOTEST_SCREENSHOTS_COMPLETE")
	get_tree().quit(0)


func _save_vp_png(path: String) -> void:
	var img: Image = get_viewport().get_texture().get_image()
	if img != null:
		img.save_png(path)
