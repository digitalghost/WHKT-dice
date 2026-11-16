class_name DiceCombatModal
extends Control

## Physical 3D Tabletop Roll HUD & Non-Occluding Combat Command Console:
## - Zero scrim / zero right-side wall: `mouse_filter = MOUSE_FILTER_IGNORE` so 100% of the 3D Tabletop
##   Velvet & Brass Dice Box AND the 3D Fighters Duel Stage stay completely open, unoccluded & clickable!
## - Top Broadcast Versus / Initiative Clash Header (`_top_vs_panel`, animated Trauma Ghost-Drain HP Bars)
## - Sleek Bottom-Left Floating Command Console (`_cmd_panel`, shifts Bottom-Center for Initiative close-up)
## - Full 2-way sync with `KTDice3D` (3D raycast hover/click on physical dice + single-die jump-spin rerolls + 3D Strike/Parry clash!)

signal combat_finished(session: Dictionary)
signal combat_vfx_triggered(ev: Dictionary)
signal initiative_finished()

var engine: KTEngine = null
var _killzone: KillzoneView = null
var session: Dictionary = {}
var _init_result: Dictionary = {}
var _modal_mode: String = "shooting" # "initiative" | "shooting" | "melee"

var _top_vs_panel: PanelContainer
var _top_vs_hb: HBoxContainer
var _att_hp_bar: KTWidgets.TraumaBar = null
var _def_hp_bar: KTWidgets.TraumaBar = null
var _att_hp_lbl: Label = null
var _def_hp_lbl: Label = null
var _last_vs_key: String = ""

var _cmd_panel: PanelContainer
var _phase_lbl: Label
var _mini_dice_hb: HBoxContainer
var _ticker_lbl: Label
var _action_bar: HBoxContainer
var _fx_overlay: Control

# Rule timeline: one row per structured rule event (session["rule_events"]), revealed one beat at
# a time. Beat length comes from KTSettings "combat/rule_step_pause" (0 = everything at once).
var _timeline_vb: VBoxContainer
var _timeline_panel: PanelContainer
var _timeline_shown: int = 0
var _timeline_timer: float = 0.0
var _timeline_playing: bool = false

var _selected_pool: String = ""
var _selected_die_idx: int = -1
var _ai_timer: Timer
var _impact_played_for_session: bool = false
var _animate_next_roll: bool = false

var _ui_sparks: Array[Dictionary] = []
var _ui_rings: Array[Dictionary] = []
var _entrance_tween: Tween = null


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false

	# --- 1. TOP BROADCAST VERSUS / INITIATIVE CLASH HEADER ---
	_top_vs_panel = PanelContainer.new()
	_top_vs_panel.anchor_left = 0.5
	_top_vs_panel.anchor_right = 0.5
	_top_vs_panel.anchor_top = 0.0
	_top_vs_panel.anchor_bottom = 0.0
	_top_vs_panel.offset_left = -408.0
	_top_vs_panel.offset_right = 408.0
	_top_vs_panel.offset_top = 8.0
	_top_vs_panel.offset_bottom = 98.0
	_top_vs_panel.mouse_filter = Control.MOUSE_FILTER_STOP
	_top_vs_panel.add_theme_stylebox_override(
		"panel",
		KTTheme.make_panel_style(Color(0.030, 0.038, 0.060, 0.94), KTTheme.COL_GOLD, 12, 2, 9, true, KTTheme.COL_GOLD_BRIGHT)
	)
	add_child(_top_vs_panel)

	_top_vs_hb = HBoxContainer.new()
	_top_vs_hb.add_theme_constant_override("separation", 14)
	_top_vs_panel.add_child(_top_vs_hb)

	# --- 2. SLEEK FLOATING COMMAND CONSOLE (Bottom-Left in Combat -> 0% Overlap with Right 3D Dice Tray!) ---
	_cmd_panel = PanelContainer.new()
	_cmd_panel.anchor_left = 0.0
	_cmd_panel.anchor_right = 0.0
	_cmd_panel.anchor_top = 1.0
	_cmd_panel.anchor_bottom = 1.0
	_cmd_panel.offset_left = 20.0
	_cmd_panel.offset_right = 776.0
	_cmd_panel.offset_top = -142.0
	_cmd_panel.offset_bottom = -14.0
	_cmd_panel.mouse_filter = Control.MOUSE_FILTER_STOP
	_cmd_panel.add_theme_stylebox_override(
		"panel",
		KTTheme.make_panel_style(Color(0.028, 0.036, 0.058, 0.94), KTTheme.COL_GOLD, 12, 2, 10, true, KTTheme.COL_GOLD_BRIGHT)
	)
	add_child(_cmd_panel)

	var root_vb := VBoxContainer.new()
	root_vb.add_theme_constant_override("separation", 6)
	_cmd_panel.add_child(root_vb)

	# Row 1: Phase Ribbon + Synced Quick-Select Mini-Die Chips + Hotkey Hint
	var hdr_hb := HBoxContainer.new()
	hdr_hb.add_theme_constant_override("separation", 8)
	root_vb.add_child(hdr_hb)

	var orn_l := Label.new()
	orn_l.text = "🎲"
	orn_l.add_theme_font_size_override("font_size", 15)
	hdr_hb.add_child(orn_l)

	_phase_lbl = Label.new()
	_phase_lbl.add_theme_font_size_override("font_size", 15)
	_phase_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	hdr_hb.add_child(_phase_lbl)

	_mini_dice_hb = HBoxContainer.new()
	_mini_dice_hb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_mini_dice_hb.alignment = BoxContainer.ALIGNMENT_CENTER
	_mini_dice_hb.add_theme_constant_override("separation", 5)
	hdr_hb.add_child(_mini_dice_hb)

	var hint_hk := Label.new()
	hint_hk.text = "右键环绕 · 滚轮缩放 · [Space] 推进"
	hint_hk.add_theme_font_size_override("font_size", 11)
	hint_hk.add_theme_color_override("font_color", KTTheme.COL_MUTED)
	hdr_hb.add_child(hint_hk)

	# Row 2: Rule timeline (replaces the one-line ticker). Each row = icon · rule chip · what it did.
	_timeline_panel = PanelContainer.new()
	_timeline_panel.add_theme_stylebox_override(
		"panel",
		KTTheme.make_panel_style(Color(0.018, 0.024, 0.038, 0.90), Color(0.24, 0.28, 0.36, 0.65), 6, 1, 6)
	)
	_timeline_panel.custom_minimum_size = Vector2(0, 26)
	root_vb.add_child(_timeline_panel)

	_timeline_vb = VBoxContainer.new()
	_timeline_vb.add_theme_constant_override("separation", 2)
	_timeline_panel.add_child(_timeline_vb)

	# Ticker label kept as the timeline's "empty state" hint row
	_ticker_lbl = Label.new()
	_ticker_lbl.clip_text = true
	_ticker_lbl.custom_minimum_size = Vector2(0, 18)
	_ticker_lbl.add_theme_font_size_override("font_size", 12)
	_ticker_lbl.add_theme_color_override("font_color", KTTheme.COL_MUTED)
	_timeline_vb.add_child(_ticker_lbl)

	# Row 3: Action Blades Bar
	_action_bar = HBoxContainer.new()
	_action_bar.alignment = BoxContainer.ALIGNMENT_END
	_action_bar.add_theme_constant_override("separation", 8)
	root_vb.add_child(_action_bar)

	# 2D Spark & Shockwave Canvas Layer (strictly MOUSE_FILTER_IGNORE)
	_fx_overlay = Control.new()
	_fx_overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_fx_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_fx_overlay.draw.connect(_draw_ui_fx)
	add_child(_fx_overlay)

	_ai_timer = Timer.new()
	_ai_timer.one_shot = true
	_ai_timer.timeout.connect(_on_ai_combat_step)
	add_child(_ai_timer)


func bind_killzone(kz: KillzoneView) -> void:
	_killzone = kz
	if _killzone != null and not _killzone.tabletop_die_clicked.is_connected(on_tabletop_die_clicked):
		_killzone.tabletop_die_clicked.connect(on_tabletop_die_clicked)


func on_tabletop_die_clicked(pool_key: String, die_idx: int) -> void:
	if not visible or _modal_mode != "shooting":
		return
	var step := str(session.get("step", ""))
	if step == "attack_reroll" and pool_key == "atk_dice":
		var atk_arr: Array = session.get("atk_dice", [])
		if die_idx >= 0 and die_idx < atk_arr.size() and not bool((atk_arr[die_idx] as Dictionary).get("rerolled", false)):
			_selected_pool = pool_key
			_selected_die_idx = die_idx
			if _killzone != null and _killzone.get_dice_3d() != null:
				_killzone.get_dice_3d().update_selection_highlight(_selected_pool, _selected_die_idx)
			_rebuild_ui()
	elif step == "defence_roll" and pool_key == "def_dice":
		var def_arr: Array = session.get("def_dice", [])
		if die_idx >= 0 and die_idx < def_arr.size() and not bool((def_arr[die_idx] as Dictionary).get("rerolled", false)):
			_selected_pool = pool_key
			_selected_die_idx = die_idx
			if _killzone != null and _killzone.get_dice_3d() != null:
				_killzone.get_dice_3d().update_selection_highlight(_selected_pool, _selected_die_idx)
			_rebuild_ui()


func _unhandled_input(event: InputEvent) -> void:
	if not visible:
		return
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode in [KEY_SPACE, KEY_ENTER, KEY_KP_ENTER]:
			# Trigger primary CTA button (rightmost enabled button in _action_bar!)
			var btns: Array[Button] = []
			for c in _action_bar.get_children():
				if c is Button and not (c as Button).disabled:
					btns.append(c as Button)
			if not btns.is_empty():
				accept_event()
				var primary_btn: Button = btns[btns.size() - 1] if _modal_mode != "shooting" or str(session.get("step", "")) != "attack_reroll" else btns[btns.size() - 2 if btns.size() >= 2 else 0]
				primary_btn.pressed.emit()
				return


func _process(delta: float) -> void:
	if not visible:
		return
	_advance_rule_timeline(delta)
	var dirty := false
	var i := _ui_sparks.size() - 1
	while i >= 0:
		var sp: Dictionary = _ui_sparks[i]
		var t_cur: float = float(sp["t"]) + delta
		sp["t"] = t_cur
		var vel: Vector2 = sp["vel"]
		vel.y += 520.0 * delta
		vel *= pow(0.18, delta)
		sp["vel"] = vel
		sp["pos"] = (sp["pos"] as Vector2) + vel * delta
		if t_cur >= float(sp["dur"]):
			_ui_sparks.remove_at(i)
		i -= 1
		dirty = true

	var j := _ui_rings.size() - 1
	while j >= 0:
		var rg: Dictionary = _ui_rings[j]
		var tr: float = float(rg["t"]) + delta
		rg["t"] = tr
		if tr >= float(rg["dur"]):
			_ui_rings.remove_at(j)
		j -= 1
		dirty = true

	if dirty:
		_fx_overlay.queue_redraw()


func _spawn_ui_burst(col: Color, count: int = 28) -> void:
	var origin := _cmd_panel.get_global_rect().get_center()
	if origin.length_squared() < 10.0:
		origin = Vector2(420, 880)
	for _k in range(count):
		var ang: float = randf() * TAU
		var spd: float = randf_range(120.0, 420.0)
		_ui_sparks.append({
			"pos": origin + Vector2(randf_range(-55.0, 55.0), randf_range(-16.0, 16.0)),
			"vel": Vector2(cos(ang), sin(ang)) * spd + Vector2(0.0, -95.0),
			"col": col.lightened(randf_range(0.05, 0.35)),
			"t": 0.0,
			"dur": randf_range(0.30, 0.58),
			"sz": randf_range(2.2, 4.8),
		})
	_ui_rings.append({
		"pos": origin,
		"col": col,
		"t": 0.0,
		"dur": 0.38,
		"r0": 16.0,
		"r1": 145.0,
	})
	_fx_overlay.queue_redraw()


func _draw_ui_fx() -> void:
	for rg in _ui_rings:
		var u: float = clampf(float(rg["t"]) / float(rg["dur"]), 0.0, 1.0)
		var rad: float = lerpf(float(rg["r0"]), float(rg["r1"]), 1.0 - pow(1.0 - u, 2.5))
		var rc: Color = rg["col"]
		_fx_overlay.draw_arc(rg["pos"] as Vector2, rad, 0.0, TAU, 40, Color(rc.r, rc.g, rc.b, (1.0 - u) * 0.68), lerpf(3.6, 1.0, u), true)

	for sp in _ui_sparks:
		var u2: float = clampf(float(sp["t"]) / float(sp["dur"]), 0.0, 1.0)
		var sc: Color = sp["col"]
		var p: Vector2 = sp["pos"]
		var v: Vector2 = sp["vel"]
		var tail: Vector2 = p - v.normalized() * 10.0
		var alpha: float = 1.0 - u2
		_fx_overlay.draw_line(tail, p, Color(sc.r, sc.g, sc.b, alpha * 0.9), float(sp["sz"]) * 0.65)
		_fx_overlay.draw_circle(p, float(sp["sz"]) * (1.0 - u2 * 0.5), Color(1.0, 0.98, 0.88, alpha))


func play_dice_sfx() -> void:
	KTSfx.play("dice_roll", -2.5, 0.05)


func _layout_cmd_panel_for_mode(instant: bool) -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	if _entrance_tween != null and _entrance_tween.is_valid():
		_entrance_tween.kill()

	_top_vs_panel.offset_left = -408.0
	_top_vs_panel.offset_right = 408.0

	var tgt_top_t: float = 8.0
	var tgt_top_b: float = 98.0
	var tgt_cmd_l: float = 20.0
	var tgt_cmd_r: float = 776.0
	var tgt_cmd_t: float = -142.0
	var tgt_cmd_b: float = -14.0

	if _modal_mode == "initiative":
		_cmd_panel.anchor_left = 0.5
		_cmd_panel.anchor_right = 0.5
		tgt_cmd_l = -370.0
		tgt_cmd_r = 370.0
		tgt_cmd_t = -132.0
		tgt_cmd_b = -14.0
	else:
		_cmd_panel.anchor_left = 0.0
		_cmd_panel.anchor_right = 0.0

	if instant:
		_top_vs_panel.offset_top = tgt_top_t
		_top_vs_panel.offset_bottom = tgt_top_b
		_cmd_panel.offset_left = tgt_cmd_l
		_cmd_panel.offset_right = tgt_cmd_r
		_cmd_panel.offset_top = tgt_cmd_t
		_cmd_panel.offset_bottom = tgt_cmd_b
		return

	_top_vs_panel.offset_top = -60.0
	_top_vs_panel.offset_bottom = 30.0
	_cmd_panel.offset_left = tgt_cmd_l
	_cmd_panel.offset_right = tgt_cmd_r
	_cmd_panel.offset_top = 20.0
	_cmd_panel.offset_bottom = 148.0

	_entrance_tween = create_tween().set_parallel(true).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	_entrance_tween.tween_property(_top_vs_panel, "offset_top", tgt_top_t, 0.24)
	_entrance_tween.tween_property(_top_vs_panel, "offset_bottom", tgt_top_b, 0.24)
	_entrance_tween.tween_property(_cmd_panel, "offset_top", tgt_cmd_t, 0.24)
	_entrance_tween.tween_property(_cmd_panel, "offset_bottom", tgt_cmd_b, 0.24)


func snap_entrance_for_capture() -> void:
	_layout_cmd_panel_for_mode(true)


func reset_for_new_match() -> void:
	visible = false
	if _ai_timer != null:
		_ai_timer.stop()
	if _entrance_tween != null and _entrance_tween.is_valid():
		_entrance_tween.kill()
		_entrance_tween = null
	session = {}
	_init_result = {}
	_modal_mode = "shooting"
	_selected_pool = ""
	_selected_die_idx = -1
	_impact_played_for_session = false
	_animate_next_roll = false
	_last_vs_key = ""
	_att_hp_bar = null
	_def_hp_bar = null
	_att_hp_lbl = null
	_def_hp_lbl = null
	_ui_sparks.clear()
	_ui_rings.clear()
	if _fx_overlay != null:
		_fx_overlay.queue_redraw()
	if _top_vs_hb != null:
		for c in _top_vs_hb.get_children():
			c.queue_free()
	if _mini_dice_hb != null:
		for c in _mini_dice_hb.get_children():
			c.queue_free()
	if _action_bar != null:
		for c in _action_bar.get_children():
			c.queue_free()
	if _timeline_vb != null:
		_reset_rule_timeline()
	if _killzone != null and _killzone.get_dice_3d() != null:
		_killzone.get_dice_3d().place_tray_idle(true)


# ==============================================================================
# PUBLIC ENTRYPOINTS: INITIATIVE / SHOOTING / MELEE
# ==============================================================================

func open_initiative(eng: KTEngine, init_res: Dictionary, instant: bool = false) -> void:
	engine = eng
	_init_result = init_res
	session = {"kind": "initiative"}
	_modal_mode = "initiative"
	_selected_pool = ""
	_selected_die_idx = -1
	_last_vs_key = ""
	visible = true
	play_dice_sfx()
	_layout_cmd_panel_for_mode(instant)
	if _killzone != null:
		_killzone.frame_initiative_dice_roll(
			int(init_res.get("roll_a", 4)),
			int(init_res.get("roll_b", 3)),
			str(init_res.get("winner", "A")),
			instant
		)
	_spawn_ui_burst(KTTheme.COL_GOLD_BRIGHT, 22)
	_rebuild_ui()
	_schedule_ai_initiative_if_needed()


func open_shooting(eng: KTEngine, sess: Dictionary, auto_fast_for_ai: bool = false) -> void:
	engine = eng
	session = sess
	_modal_mode = "shooting"
	_selected_pool = ""
	_selected_die_idx = -1
	_impact_played_for_session = false
	_last_vs_key = ""
	_animate_next_roll = true
	visible = true
	play_dice_sfx()
	_layout_cmd_panel_for_mode(false)
	_reset_rule_timeline()
	_spawn_ui_burst(KTTheme.COL_GOLD_BRIGHT, 20)

	var anim_pool: String = "atk_dice"
	if auto_fast_for_ai:
		KTCombat.lock_in_shooting_attacks(session, engine.state, engine.rng)
		anim_pool = "both"
		var human_defending := (str(engine.state["mode"]) == "ai" and str(session["defender_side"]) == str(engine.state["human_side"]))
		if not human_defending:
			KTCombat.finalize_shooting_resolution(session, engine.state, engine.rng)
			engine.recalculate_objectives_and_kills()
			_emit_ranged_impact_once()

	if _killzone != null and _killzone.get_dice_3d() != null:
		_killzone.get_dice_3d().sync_shooting_dice(session, _selected_pool, _selected_die_idx, anim_pool, -1, false)
	_rebuild_ui()


func open_melee(eng: KTEngine, sess: Dictionary) -> void:
	engine = eng
	session = sess
	_modal_mode = "melee"
	_selected_pool = ""
	_selected_die_idx = -1
	_impact_played_for_session = false
	_last_vs_key = ""
	_animate_next_roll = true
	visible = true
	play_dice_sfx()
	_layout_cmd_panel_for_mode(false)
	_reset_rule_timeline()
	_spawn_ui_burst(KTTheme.COL_CRIMSON, 24)
	if _killzone != null and _killzone.get_dice_3d() != null:
		_killzone.get_dice_3d().sync_melee_dice(session, true, false)
	_rebuild_ui()
	_schedule_ai_melee_if_needed()


func _emit_ranged_impact_once() -> void:
	if _impact_played_for_session:
		return
	_impact_played_for_session = true
	var def := engine.get_op(str(session.get("defender_uid", "")))
	var ret: Dictionary = session.get("retained_atk", {"crits": 0})
	var dmg := int(session.get("total_damage_dealt", 0))
	_spawn_ui_burst(KTTheme.COL_CRIMSON if dmg > 0 else KTTheme.COL_EMERALD, 36 if dmg > 0 else 22)
	combat_vfx_triggered.emit({
		"kind": "ranged_impact",
		"attacker_uid": str(session.get("attacker_uid", "")),
		"defender_uid": str(session.get("defender_uid", "")),
		"weapon": session.get("weapon", {}),
		"dmg": dmg,
		"crits": int(ret.get("crits", 0)),
		"killed": bool(def.get("incapacitated", false)),
		"poisoned": bool(def.get("poisoned", false)),
	})


func _execute_and_emit_melee_step(choice: String) -> void:
	var is_att_turn: bool = str(session.get("turn", "attacker")) == "attacker"
	var actor_uid: String = str(session["attacker_uid"] if is_att_turn else session["defender_uid"])
	var target_uid: String = str(session["defender_uid"] if is_att_turn else session["attacker_uid"])
	var my_pool: Dictionary = session["att"] if is_att_turn else session["def"]
	var weapon_d: Dictionary = my_pool.get("weapon", {})

	var tgt_before := engine.get_op(target_uid)
	var hp_before: int = int(tgt_before.get("wounds_cur", 0))

	KTCombat.execute_melee_step(session, choice, engine.state, engine.rng)
	engine.recalculate_objectives_and_kills()

	var tgt_after := engine.get_op(target_uid)
	var hp_after: int = int(tgt_after.get("wounds_cur", 0))
	var dmg_dealt: int = maxi(0, hp_before - hp_after)
	var killed: bool = bool(tgt_after.get("incapacitated", false))

	if _killzone != null and _killzone.get_dice_3d() != null and not tgt_after.is_empty():
		var w_tgt := _killzone.inches_to_world(tgt_after["pos"], _killzone.elevation_at_inches(tgt_after["pos"]))
		_killzone.get_dice_3d().play_melee_step_clash(is_att_turn, choice, w_tgt, session)

	var burst_c := KTTheme.COL_IMPERIAL if choice.begins_with("parry") else (KTTheme.COL_GOLD_BRIGHT if choice.ends_with("crit") else KTTheme.COL_CRIMSON)
	_spawn_ui_burst(burst_c, 32 if choice.begins_with("strike") else 22)

	combat_vfx_triggered.emit({
		"kind": "melee_step",
		"actor_uid": actor_uid,
		"target_uid": target_uid,
		"action": choice,
		"weapon": weapon_d,
		"dmg": dmg_dealt,
		"killed": killed,
	})


func _is_side_ai(side_id: String) -> bool:
	if str(engine.state.get("mode", "ai")) != "ai":
		return false
	return side_id != str(engine.state["human_side"])


func _schedule_ai_melee_if_needed() -> void:
	if not visible or _modal_mode != "melee" or str(session.get("step", "")) != "duel":
		return
	var active_side := str(session["attacker_side"] if str(session["turn"]) == "attacker" else session["defender_side"])
	if _is_side_ai(active_side):
		_ai_timer.start(0.58)


func _on_ai_combat_step() -> void:
	if visible and _modal_mode == "initiative":
		_on_ai_initiative_step()
		return
	if not visible or _modal_mode != "melee" or str(session.get("step", "")) != "duel":
		return
	var choice := KTCombat.choose_ai_melee_action(session, engine.state)
	_execute_and_emit_melee_step(choice)
	_rebuild_ui()
	_schedule_ai_melee_if_needed()


# ==============================================================================
# TOP HEADER & BOTTOM COMMAND CONSOLE REBUILDERS
# ==============================================================================

func _rebuild_ui() -> void:
	for c in _mini_dice_hb.get_children():
		c.queue_free()
	for c in _action_bar.get_children():
		c.queue_free()

	if _modal_mode == "initiative":
		_build_initiative_ui()
		return

	var att := engine.get_op(str(session["attacker_uid"]))
	var def := engine.get_op(str(session["defender_uid"]))
	_update_top_vs_banner(att, def)

	if _modal_mode == "shooting":
		_build_shooting_ui(att, def)
	else:
		_build_melee_ui(att, def)

	_animate_next_roll = false
	_sync_rule_timeline()


# ------------------------------------------------------------------------------
# RULE TIMELINE
# ------------------------------------------------------------------------------

const _EVENT_KIND_COL := {
	"upgrade": Color(1.0, 0.84, 0.30), "convert": Color(0.42, 0.86, 1.0), "reroll": Color(0.30, 0.88, 0.60),
	"block": Color(0.56, 0.72, 1.0), "damage": Color(1.0, 0.40, 0.34), "status": Color(0.80, 0.52, 1.0), "info": Color(0.64, 0.68, 0.74),
}


func _reset_rule_timeline() -> void:
	_timeline_shown = 0
	_timeline_timer = 0.0
	_timeline_playing = false
	for c in _timeline_vb.get_children():
		if c != _ticker_lbl:
			c.queue_free()
	_ticker_lbl.visible = true
	if _modal_mode == "shooting":
		_ticker_lbl.text = "👆 点击桌面 3D 骰子选中后可重掷 · 规则触发会逐条显示在这里"
	else:
		_ticker_lbl.text = "⚔️ 点击下方招式按钮出击 / 招架 · 规则触发会逐条显示在这里"


## Called from _rebuild_ui. Reveals rule_events[_timeline_shown:] one beat at a time; while the
## timeline is still playing the primary CTA buttons are disabled so the player sees every rule land.
func _sync_rule_timeline() -> void:
	var events: Array = session.get("rule_events", [])
	if events.size() < _timeline_shown:
		_reset_rule_timeline()
	if events.size() > _timeline_shown and not _timeline_playing:
		_timeline_playing = true
		_timeline_timer = 0.0
	_set_cta_enabled(not _timeline_playing)


func _advance_rule_timeline(delta: float) -> void:
	if not _timeline_playing:
		return
	var events: Array = session.get("rule_events", [])
	var pause := KTSettings.rule_step_pause()
	_timeline_timer -= delta
	while _timeline_shown < events.size() and (_timeline_timer <= 0.0 or pause <= 0.0):
		_append_timeline_row(events[_timeline_shown])
		_timeline_shown += 1
		_timeline_timer = pause
	if _timeline_shown >= events.size():
		_timeline_playing = false
		_set_cta_enabled(true)


func _append_timeline_row(ev: Dictionary) -> void:
	_ticker_lbl.visible = false
	var rule := str(ev.get("rule", ""))
	var kind := str(ev.get("kind", "info"))
	var col: Color = _EVENT_KIND_COL.get(kind, KTTheme.COL_MUTED)
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 6)
	_timeline_vb.add_child(row)

	var dot := Label.new()
	dot.text = "▸"
	dot.add_theme_font_size_override("font_size", 12)
	dot.add_theme_color_override("font_color", col)
	row.add_child(dot)

	var text := str(ev.get("text", ""))
	if not rule.is_empty() and KTGlossary.has(rule):
		var chip := KTRuleChip.new()
		chip.setup(rule, col, 11)
		row.add_child(chip)
		# strip the rule name / 【】 from the message to avoid saying it twice
		text = text.replace("【%s】" % rule, "").replace(rule + "：", "").replace(rule + ":", "").replace(rule, "").strip_edges()
		text = text.trim_prefix("词条触发：").trim_prefix("生效：").trim_prefix("触发！").strip_edges()

	var lbl := Label.new()
	lbl.text = text
	lbl.clip_text = true
	lbl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lbl.add_theme_font_size_override("font_size", 12)
	lbl.add_theme_color_override("font_color", KTTheme.COL_TEXT if kind != "info" else KTTheme.COL_MUTED)
	row.add_child(lbl)

	# Keep the panel to the last 4 rows
	var rows := _timeline_vb.get_children().filter(func(c): return c != _ticker_lbl)
	while rows.size() > 4:
		(rows.pop_front() as Node).queue_free()

	# Fade/slide in
	row.modulate.a = 0.0
	var tw := row.create_tween()
	tw.tween_property(row, "modulate:a", 1.0, 0.18)

	# Dice feedback: flash the pool the rule touched, with a badge
	if _killzone != null and _killzone.get_dice_3d() != null:
		var d3 := _killzone.get_dice_3d()
		var pool := str(ev.get("pool", ""))
		var dice: Array = ev.get("dice", [])
		if pool.is_empty():
			pool = _guess_pool_for_event(ev)
		if not pool.is_empty():
			var badge := rule if not rule.is_empty() else kind
			if dice.is_empty():
				d3.flash_rule_on_dice(pool, -1, badge, col, 1.4)
			else:
				for di in dice:
					d3.flash_rule_on_dice(pool, int(di), badge, col, 1.6)
		if kind in ["upgrade", "convert"]:
			KTSfx.play("crit_chime", -8.0, 0.05)
		elif kind == "block":
			KTSfx.play("parry", -10.0, 0.08)
		elif kind == "reroll":
			KTSfx.play("dice_clack", -7.0, 0.1)


## Rule events from the engine don't yet carry dice indices for everything; map rule -> pool by name.
func _guess_pool_for_event(ev: Dictionary) -> String:
	var rule := str(ev.get("rule", ""))
	if _modal_mode == "melee":
		return ""
	match rule:
		"严重", "撕裂", "重击", "纯洁印章", "重型遮挡", "毁灭", "平衡", "弹药储备", "指挥重掷", "无休":
			return "atk_dice"
		"不屈不挠", "超人体格", "穿刺", "恼人韧性", "恶心韧性", "钢铁光环":
			return "def_dice"
	return ""


func _set_cta_enabled(enabled: bool) -> void:
	for c in _action_bar.get_children():
		if c is Button:
			var b := c as Button
			if b.has_meta("locked_by_timeline"):
				if enabled:
					b.disabled = bool(b.get_meta("was_disabled", false))
					b.remove_meta("locked_by_timeline")
			elif not enabled:
				b.set_meta("was_disabled", b.disabled)
				b.set_meta("locked_by_timeline", true)
				b.disabled = true


func _init_card_name(c: int) -> String:
	return "重投" if c == KTEngine.INIT_CARD_REROLL else "+%d/-%d" % [c, c]


func _init_hand_text(side_id: String) -> String:
	var names := PackedStringArray()
	for c in engine.state["sides"][side_id].get("init_cards", []):
		names.append(_init_card_name(int(c)))
	return "无" if names.is_empty() else "、".join(names)


func _init_score_text(side_id: String, fallback: int) -> String:
	if not engine.state.has("init"):
		return "%d 点" % fallback
	var m: int = int(engine.state["init"]["mod"][side_id])
	if m == 0:
		return "%d 点" % engine.init_value(side_id)
	return "%d%+d=%d 点" % [int(engine.state["init"]["roll"][side_id]), m, engine.init_value(side_id)]


## Rulebook p.106 step 4: roll-off, then (starting with the loser) players alternate playing an
## initiative card or passing until both pass in succession.
func _build_initiative_ui() -> void:
	var tp: int = int(engine.state.get("tp", 1))
	var pending: bool = engine.init_pending()
	var win: String = str(_init_result.get("winner", "A"))
	if engine.state.has("init"):
		win = engine.init_leader() if pending else str(engine.state["init"]["winner"])
	var win_name: String = "死亡天使 (帝国)" if win == "A" else "瘟疫战士 (纳垢)"
	var star: String = "★暂时领先" if pending else "★夺得先手"
	var sa: String = _init_score_text("A", int(_init_result.get("roll_a", 4)))
	var sb: String = _init_score_text("B", int(_init_result.get("roll_b", 3)))

	var vs_key: String = "init_%d_%s_%s_%s_%s" % [tp, sa, sb, win, str(pending)]
	if _last_vs_key != vs_key:
		_last_vs_key = vs_key
		for c in _top_vs_hb.get_children():
			c.queue_free()

		var crest_a := TextureRect.new()
		crest_a.texture = KTData.load_tex("res://assets/ui/team_mark_imp_aod.png")
		crest_a.custom_minimum_size = Vector2(54, 54)
		crest_a.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		crest_a.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		_top_vs_hb.add_child(crest_a)

		var lbl_a := Label.new()
		lbl_a.text = "死亡天使  [%s]%s" % [sa, (" " + star) if win == "A" else ""]
		lbl_a.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		lbl_a.add_theme_font_size_override("font_size", 17)
		lbl_a.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT if win == "A" else KTTheme.COL_IMPERIAL.lightened(0.28))
		_top_vs_hb.add_child(lbl_a)

		var mid_l := Label.new()
		mid_l.text = "⚜️ TP %d 先攻权拼骰 ⚜️" % tp
		mid_l.add_theme_font_size_override("font_size", 18)
		mid_l.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
		_top_vs_hb.add_child(mid_l)

		var lbl_b := Label.new()
		lbl_b.text = "%s[%s]  瘟疫战士" % [(star + " ") if win == "B" else "", sb]
		lbl_b.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		lbl_b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		lbl_b.add_theme_font_size_override("font_size", 17)
		lbl_b.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT if win == "B" else KTTheme.COL_NURGLE.lightened(0.28))
		_top_vs_hb.add_child(lbl_b)

		var crest_b := TextureRect.new()
		crest_b.texture = KTData.load_tex("res://assets/ui/team_mark_chaos_pm.png")
		crest_b.custom_minimum_size = Vector2(54, 54)
		crest_b.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		crest_b.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		_top_vs_hb.add_child(crest_b)

	var hands: String = "先手权卡 — 死亡天使：%s ｜ 瘟疫战士：%s" % [_init_hand_text("A"), _init_hand_text("B")]
	if pending:
		var t: String = str(engine.state["init"]["turn"])
		var t_name: String = str(engine.state["sides"][t]["name"])
		_phase_lbl.text = "第 %d 转折点 · 先手权卡步骤" % tp
		_ticker_lbl.text = "🎴 轮到【%s】：打出一张先手权卡或让过（双方连续让过后结算，平手不重投）\n%s" % [t_name, hands]
		if _is_side_ai(t):
			var wait_l := Label.new()
			wait_l.text = "🤖 【%s】正在考虑先手权卡…" % t_name
			wait_l.add_theme_font_size_override("font_size", 15)
			wait_l.add_theme_color_override("font_color", KTTheme.COL_NURGLE.lightened(0.28))
			_action_bar.add_child(wait_l)
			return
		var cards: Array = (engine.state["sides"][t]["init_cards"] as Array).duplicate()
		cards.sort()
		for c_v in cards:
			var c: int = int(c_v)
			if c == KTEngine.INIT_CARD_REROLL:
				_add_init_card_button("🔁 重投卡", t, c, 1)
			else:
				_add_init_card_button("🎴 +%d" % c, t, c, 1)
				_add_init_card_button("🎴 -%d" % c, t, c, -1)
		var btn_pass := Button.new()
		btn_pass.focus_mode = Control.FOCUS_NONE
		btn_pass.text = "⏭ 让过 [Space]"
		btn_pass.custom_minimum_size = Vector2(150, 38)
		KTTheme.apply_accent_button(btn_pass, KTTheme.COL_GOLD_BRIGHT, 0.36)
		KTWidgets.attach_button_juice(btn_pass)
		btn_pass.pressed.connect(func():
			if bool(engine.init_pass(t).get("ok", false)):
				_after_init_step(false)
		)
		_action_bar.add_child(btn_pass)
		return

	_phase_lbl.text = "第 %d 转折点 · 桌面先攻权拼骰" % tp
	_ticker_lbl.text = "🎲 帝国蓝骰 [%s] vs 纳垢绿骰 [%s]  ➔  【%s】夺得先攻权并补充命令点 CP！\n%s" % [sa, sb, win_name, hands]

	var btn_go := Button.new()
	btn_go.focus_mode = Control.FOCUS_NONE
	btn_go.text = "✓ 确认桌面结果 · 进入战略阶段 [Space]"
	btn_go.custom_minimum_size = Vector2(320, 38)
	KTTheme.apply_accent_button(btn_go, KTTheme.COL_GOLD_BRIGHT, 0.36)
	KTWidgets.attach_button_juice(btn_go)
	btn_go.pressed.connect(func():
		visible = false
		initiative_finished.emit()
	)
	_action_bar.add_child(btn_go)


func _add_init_card_button(text: String, side_id: String, card: int, sign_i: int) -> void:
	var b := Button.new()
	b.focus_mode = Control.FOCUS_NONE
	b.text = text
	b.custom_minimum_size = Vector2(104, 38)
	KTTheme.apply_accent_button(b, KTTheme.COL_IMPERIAL if side_id == "A" else KTTheme.COL_NURGLE, 0.32)
	KTWidgets.attach_button_juice(b)
	b.pressed.connect(func():
		var res: Dictionary = engine.init_play_card(side_id, card, sign_i)
		if bool(res.get("ok", false)):
			_after_init_step(bool(res.get("reroll", false)))
	)
	_action_bar.add_child(b)


func _after_init_step(rerolled: bool) -> void:
	if rerolled and _killzone != null:
		play_dice_sfx()
		var st: Dictionary = engine.state["init"]
		var lead: String = engine.init_leader() if engine.init_pending() else str(st["winner"])
		_killzone.frame_initiative_dice_roll(int(st["roll"]["A"]), int(st["roll"]["B"]), lead, false)
	_rebuild_ui()
	_schedule_ai_initiative_if_needed()


func _schedule_ai_initiative_if_needed() -> void:
	if visible and _modal_mode == "initiative" and engine.init_pending() and _is_side_ai(str(engine.state["init"]["turn"])):
		_ai_timer.start(0.9)


func _on_ai_initiative_step() -> void:
	if not engine.init_pending():
		return
	var t: String = str(engine.state["init"]["turn"])
	if not _is_side_ai(t):
		return
	var saved_undo: Array[String] = engine.undo_stack.duplicate()
	var dec: Dictionary = KTAI.decide_initiative_card(engine, t)
	var rerolled := false
	if str(dec["action"]) == "card":
		var res: Dictionary = engine.init_play_card(t, int(dec["card"]), int(dec["sign"]))
		rerolled = bool(res.get("reroll", false))
	else:
		engine.init_pass(t)
	engine.undo_stack = saved_undo
	_after_init_step(rerolled)


func _update_top_vs_banner(att: Dictionary, def: Dictionary) -> void:
	var vs_key := "%s_%s_%s" % [str(att["uid"]), str(def["uid"]), str(session.get("kind", ""))]
	if vs_key != _last_vs_key:
		_last_vs_key = vs_key
		for c in _top_vs_hb.get_children():
			c.queue_free()

		var is_melee := str(session.get("kind", "")) == "melee"
		var att_sub := ""
		var def_sub := ""
		if is_melee:
			var wa: Dictionary = session["att"]["weapon"]
			var wd: Dictionary = session["def"]["weapon"]
			att_sub = "%s (%d/%d★)" % [str(wa["name"]), int(session["att"]["normal_dmg"]), int(session["att"]["crit_dmg"])]
			def_sub = "%s (%d/%d★)" % [str(wd["name"]), int(session["def"]["normal_dmg"]), int(session["def"]["crit_dmg"])]
		else:
			var ws: Dictionary = session["weapon"]
			att_sub = "%s (%dA · %d+ · %d/%d★)" % [str(ws["name"]), int(ws["attacks"]), int(session["effective_hit"]), int(session["normal_dmg"]), int(session["crit_dmg"])]
			def_sub = "护甲豁免 %d+ (暴击 %d+)" % [int(session["def_save"]), int(session["def_crit_save"])]

		var left_d := _build_vs_wing(att, att_sub, true)
		_att_hp_bar = left_d["bar"]
		_att_hp_lbl = left_d["lbl"]
		_top_vs_hb.add_child(left_d["node"])

		var mid_vb := VBoxContainer.new()
		mid_vb.alignment = BoxContainer.ALIGNMENT_CENTER
		mid_vb.add_theme_constant_override("separation", 3)
		_top_vs_hb.add_child(mid_vb)

		var vs_l := Label.new()
		vs_l.text = "⚔ VS" if is_melee else "🎯 VS"
		vs_l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		vs_l.add_theme_font_size_override("font_size", 17)
		vs_l.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
		mid_vb.add_child(vs_l)

		var chips := HFlowContainer.new()
		chips.alignment = FlowContainer.ALIGNMENT_CENTER
		chips.custom_minimum_size = Vector2(180, 0)
		chips.add_theme_constant_override("h_separation", 4)
		chips.add_theme_constant_override("v_separation", 2)
		mid_vb.add_child(chips)

		# Active modifiers as glossary chips: hover = one-line effect, click = full rule text.
		# Tag strings look like "神射手: 精准+1 & 严重"; the chip shows the source rule (before ':')
		# and the popover explains it, so the player learns *why* the dice changed.
		var tag_sets: Array = []
		if is_melee:
			tag_sets.append([(session["att"] as Dictionary).get("tags", []), KTTheme.COL_IMPERIAL if str(att["side"]) == "A" else KTTheme.COL_NURGLE])
			tag_sets.append([(session["def"] as Dictionary).get("tags", []), KTTheme.COL_IMPERIAL if str(def["side"]) == "A" else KTTheme.COL_NURGLE])
		else:
			tag_sets.append([session.get("mod_tags", []), Color(0, 0, 0, 0)])
		for ts in tag_sets:
			for t in ts[0]:
				var raw := str(t)
				var src := raw.split(":")[0].split("：")[0].strip_edges()
				var chip := KTRuleChip.new()
				chip.setup(src if KTGlossary.has(src) else raw, ts[1], 11)
				chip.tooltip_text = raw
				chips.add_child(chip)

		var right_d := _build_vs_wing(def, def_sub, false)
		_def_hp_bar = right_d["bar"]
		_def_hp_lbl = right_d["lbl"]
		_top_vs_hb.add_child(right_d["node"])
	else:
		if _att_hp_bar != null:
			_att_hp_bar.set_hp(int(att["wounds_cur"]), int(att["wounds_max"]), true)
		if _def_hp_bar != null:
			_def_hp_bar.set_hp(int(def["wounds_cur"]), int(def["wounds_max"]), true)
		if _att_hp_lbl != null:
			_att_hp_lbl.text = "%s  %d/%d W%s" % [str(att["name"]), int(att["wounds_cur"]), int(att["wounds_max"]), " 💀" if bool(att["incapacitated"]) else ""]
		if _def_hp_lbl != null:
			_def_hp_lbl.text = "%s  %d/%d W%s" % [str(def["name"]), int(def["wounds_cur"]), int(def["wounds_max"]), " 💀" if bool(def["incapacitated"]) else ""]


func _build_vs_wing(op: Dictionary, sub_txt: String, is_left: bool) -> Dictionary:
	var hb := HBoxContainer.new()
	hb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	hb.add_theme_constant_override("separation", 10)

	var st_path := str(op.get("standee", ""))
	var tex := KTData.load_tex(st_path if not st_path.is_empty() else str(op["portrait"]))
	var port := TextureRect.new()
	port.texture = tex
	port.custom_minimum_size = Vector2(70, 58)
	port.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	port.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED

	var vb := VBoxContainer.new()
	vb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	vb.alignment = BoxContainer.ALIGNMENT_CENTER
	vb.add_theme_constant_override("separation", 3)

	var fcol := KTTheme.COL_IMPERIAL if str(op["side"]) == "A" else KTTheme.COL_NURGLE
	var n_lbl := Label.new()
	n_lbl.text = "%s  %d/%d W" % [str(op["name"]), int(op["wounds_cur"]), int(op["wounds_max"])]
	n_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT if is_left else HORIZONTAL_ALIGNMENT_RIGHT
	n_lbl.add_theme_font_size_override("font_size", 14)
	n_lbl.add_theme_color_override("font_color", fcol.lightened(0.32))
	vb.add_child(n_lbl)

	var tbar := KTWidgets.TraumaBar.new(int(op["wounds_cur"]), int(op["wounds_max"]), fcol.lightened(0.20))
	tbar.custom_minimum_size = Vector2(0, 11)
	vb.add_child(tbar)

	var s_lbl := Label.new()
	s_lbl.text = sub_txt
	s_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT if is_left else HORIZONTAL_ALIGNMENT_RIGHT
	s_lbl.add_theme_font_size_override("font_size", 11)
	s_lbl.add_theme_color_override("font_color", KTTheme.COL_MUTED)
	s_lbl.clip_text = true
	vb.add_child(s_lbl)

	if is_left:
		hb.add_child(port)
		hb.add_child(vb)
	else:
		hb.add_child(vb)
		hb.add_child(port)

	return {"node": hb, "bar": tbar, "lbl": n_lbl}


func _add_mini_die_chip(pool_key: String, idx: int, val: int, kind: String, rerolled: bool, interactive: bool) -> void:
	var sel: bool = (_selected_pool == pool_key and _selected_die_idx == idx)
	var b := Button.new()
	b.focus_mode = Control.FOCUS_NONE
	var sym := "★" if kind == "crit" else ("●" if kind == "norm" else "✕")
	b.text = "%s%d%s" % [sym, val, "↻" if rerolled else ""]
	b.custom_minimum_size = Vector2(40, 26)
	b.add_theme_font_size_override("font_size", 12)
	b.disabled = (not interactive) or rerolled
	var col := KTTheme.COL_GOLD_BRIGHT if kind == "crit" else (KTTheme.COL_EMERALD if kind == "norm" else KTTheme.COL_MUTED)
	if sel:
		col = Color(1.0, 0.96, 0.72)
	KTTheme.apply_accent_button(b, col, 0.44 if sel else 0.18)
	b.pressed.connect(func():
		on_tabletop_die_clicked(pool_key, idx)
	)
	_mini_dice_hb.add_child(b)


func _build_shooting_ui(att: Dictionary, def: Dictionary) -> void:
	var step := str(session["step"])
	_phase_lbl.text = "远程交火 · " + (
		"桌面攻击投骰" if step == "attack_reroll" else ("桌面护甲豁免" if step == "defence_roll" else "弹道战果")
	)

	var hit_req := int(session["effective_hit"])
	var crit_req := int(session["lethal_thresh"])
	var save_req := int(session["def_save"])
	var crit_save_req := int(session["def_crit_save"])

	if step == "attack_reroll":
		var atk_dice: Array = session["atk_dice"]
		for i in range(atk_dice.size()):
			var d: Dictionary = atk_dice[i]
			var v := int(d["val"])
			var kind := "crit" if (v >= crit_req and v > 1) else ("norm" if (v >= hit_req and v > 1) else "fail")
			_add_mini_die_chip("atk_dice", i, v, kind, bool(d["rerolled"]), true)

		var w_rules: Dictionary = session.get("rules", {})
		var bal_avail: bool = bool(w_rules.get("balanced", false)) and not bool(session.get("balanced_used", false))
		if bal_avail:
			var b_bal := Button.new()
			b_bal.focus_mode = Control.FOCUS_NONE
			b_bal.text = "🎯 平衡免费重掷"
			b_bal.disabled = _selected_die_idx < 0
			KTTheme.apply_accent_button(b_bal, KTTheme.COL_EMERALD)
			KTWidgets.attach_button_juice(b_bal)
			b_bal.pressed.connect(func():
				var r_idx := _selected_die_idx
				KTCombat.reroll_shooting_die(session, "atk_dice", r_idx, "balanced", engine.state, engine.rng)
				_selected_pool = ""
				_selected_die_idx = -1
				play_dice_sfx()
				if _killzone != null and _killzone.get_dice_3d() != null:
					_killzone.get_dice_3d().sync_shooting_dice(session, "", -1, "atk_dice", r_idx, false)
				_spawn_ui_burst(KTTheme.COL_EMERALD, 18)
				_rebuild_ui()
			)
			_action_bar.add_child(b_bal)

		var ammo_avail: bool = bool(session.get("ammo_reroll", false)) and not bool(session.get("ammo_reroll_used", false))
		if ammo_avail:
			var b_ammo := Button.new()
			b_ammo.focus_mode = Control.FOCUS_NONE
			b_ammo.text = "📦 弹药储备重掷"
			b_ammo.disabled = _selected_die_idx < 0
			KTTheme.apply_accent_button(b_ammo, KTTheme.COL_EMERALD)
			KTWidgets.attach_button_juice(b_ammo)
			b_ammo.pressed.connect(func():
				var r_idx_a := _selected_die_idx
				KTCombat.reroll_shooting_die(session, "atk_dice", r_idx_a, "ammo", engine.state, engine.rng)
				_selected_pool = ""
				_selected_die_idx = -1
				play_dice_sfx()
				if _killzone != null and _killzone.get_dice_3d() != null:
					_killzone.get_dice_3d().sync_shooting_dice(session, "", -1, "atk_dice", r_idx_a, false)
				_spawn_ui_burst(KTTheme.COL_EMERALD, 18)
				_rebuild_ui()
			)
			_action_bar.add_child(b_ammo)

		var att_cp := int(engine.state["sides"][att["side"]]["cp"])
		var b_cp := Button.new()
		b_cp.focus_mode = Control.FOCUS_NONE
		b_cp.text = "⚡ 指挥重掷 (1CP·剩%d)" % att_cp
		b_cp.disabled = _selected_die_idx < 0 or att_cp < 1
		KTTheme.apply_accent_button(b_cp, KTTheme.COL_BRASS)
		KTWidgets.attach_button_juice(b_cp)
		b_cp.pressed.connect(func():
			var r_idx2 := _selected_die_idx
			KTCombat.reroll_shooting_die(session, "atk_dice", r_idx2, "cp", engine.state, engine.rng)
			_selected_pool = ""
			_selected_die_idx = -1
			play_dice_sfx()
			if _killzone != null and _killzone.get_dice_3d() != null:
				_killzone.get_dice_3d().sync_shooting_dice(session, "", -1, "atk_dice", r_idx2, false)
			_spawn_ui_burst(KTTheme.COL_GOLD_BRIGHT, 20)
			_rebuild_ui()
		)
		_action_bar.add_child(b_cp)

		var btn_lock := Button.new()
		btn_lock.focus_mode = Control.FOCUS_NONE
		btn_lock.text = "锁定命中 · 投防御骰 ➔"
		KTTheme.apply_accent_button(btn_lock, KTTheme.COL_IMPERIAL, 0.34)
		KTWidgets.attach_button_juice(btn_lock)
		btn_lock.pressed.connect(func():
			KTCombat.lock_in_shooting_attacks(session, engine.state, engine.rng)
			_selected_pool = ""
			_selected_die_idx = -1
			play_dice_sfx()
			if _is_side_ai(str(def["side"])):
				KTCombat.finalize_shooting_resolution(session, engine.state, engine.rng)
				engine.recalculate_objectives_and_kills()
				_emit_ranged_impact_once()
			if _killzone != null and _killzone.get_dice_3d() != null:
				_killzone.get_dice_3d().sync_shooting_dice(session, "", -1, "def_dice", -1, false)
			_rebuild_ui()
		)
		_action_bar.add_child(btn_lock)

		var btn_fast := Button.new()
		btn_fast.focus_mode = Control.FOCUS_NONE
		btn_fast.text = "⚡ 一键开火结算"
		KTTheme.apply_accent_button(btn_fast, KTTheme.COL_GOLD_BRIGHT, 0.34)
		KTWidgets.attach_button_juice(btn_fast)
		btn_fast.pressed.connect(func():
			KTCombat.lock_in_shooting_attacks(session, engine.state, engine.rng)
			KTCombat.finalize_shooting_resolution(session, engine.state, engine.rng)
			engine.recalculate_objectives_and_kills()
			play_dice_sfx()
			if _killzone != null and _killzone.get_dice_3d() != null:
				_killzone.get_dice_3d().sync_shooting_dice(session, "", -1, "def_dice", -1, false)
			_emit_ranged_impact_once()
			_rebuild_ui()
		)
		_action_bar.add_child(btn_fast)

	elif step == "defence_roll":
		var def_dice: Array = session["def_dice"]
		for j in range(def_dice.size()):
			var dd: Dictionary = def_dice[j]
			var vd := int(dd["val"])
			var kd := "crit" if (vd >= crit_save_req and vd > 1) else ("norm" if (vd >= save_req and vd > 1) else "fail")
			_add_mini_die_chip("def_dice", j, vd, kd, bool(dd["rerolled"]), true)

		var b_def_cp := Button.new()
		b_def_cp.focus_mode = Control.FOCUS_NONE
		b_def_cp.text = "⚡ 防御重掷 (1CP)"
		b_def_cp.disabled = _selected_die_idx < 0 or int(engine.state["sides"][def["side"]]["cp"]) < 1
		KTTheme.apply_accent_button(b_def_cp, KTTheme.COL_BRASS)
		KTWidgets.attach_button_juice(b_def_cp)
		b_def_cp.pressed.connect(func():
			var rd_idx := _selected_die_idx
			KTCombat.reroll_shooting_die(session, "def_dice", rd_idx, "cp", engine.state, engine.rng)
			_selected_pool = ""
			_selected_die_idx = -1
			play_dice_sfx()
			if _killzone != null and _killzone.get_dice_3d() != null:
				_killzone.get_dice_3d().sync_shooting_dice(session, "", -1, "def_dice", rd_idx, false)
			_spawn_ui_burst(KTTheme.COL_IMPERIAL, 18)
			_rebuild_ui()
		)
		_action_bar.add_child(b_def_cp)

		var def_side_st: Dictionary = engine.state["sides"][def["side"]]
		if str(def_side_st["team_id"]) == "IMP-AOD" and not bool(session["transhuman_used"]) and int(def_side_st["cp"]) >= 1:
			var b_th := Button.new()
			b_th.focus_mode = Control.FOCUS_NONE
			b_th.text = "🧬 超人体格 (1CP)"
			KTTheme.apply_accent_button(b_th, KTTheme.COL_IMPERIAL)
			KTWidgets.attach_button_juice(b_th)
			b_th.pressed.connect(func():
				def_side_st["cp"] = int(def_side_st["cp"]) - 1
				session["transhuman_used"] = true
				_spawn_ui_burst(KTTheme.COL_IMPERIAL, 22)
				_rebuild_ui()
			)
			_action_bar.add_child(b_th)

		var btn_res := Button.new()
		btn_res.focus_mode = Control.FOCUS_NONE
		btn_res.text = "💥 引爆破甲结算! [Space]"
		KTTheme.apply_accent_button(btn_res, KTTheme.COL_CRIMSON, 0.36)
		KTWidgets.attach_button_juice(btn_res)
		btn_res.pressed.connect(func():
			KTCombat.finalize_shooting_resolution(session, engine.state, engine.rng)
			engine.recalculate_objectives_and_kills()
			_emit_ranged_impact_once()
			_rebuild_ui()
		)
		_action_bar.add_child(btn_res)

	else:
		var dealt := int(session["total_damage_dealt"])
		var res_col := KTTheme.COL_CRIMSON if dealt > 0 else KTTheme.COL_EMERALD
		var sum_lbl := Label.new()
		sum_lbl.text = "💥 造成 %d 点总伤害！%s" % [
			dealt,
			"💀 目标已被击杀！" if bool(def["incapacitated"]) else ("目标剩余 %d/%d W" % [int(def["wounds_cur"]), int(def["wounds_max"])])
		]
		sum_lbl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		sum_lbl.add_theme_font_size_override("font_size", 15)
		sum_lbl.add_theme_color_override("font_color", res_col.lightened(0.32))
		_action_bar.add_child(sum_lbl)

		var btn_done := Button.new()
		btn_done.focus_mode = Control.FOCUS_NONE
		btn_done.text = "✓ 收起骰盘 · 继续战斗 [Space]"
		KTTheme.apply_accent_button(btn_done, KTTheme.COL_EMERALD, 0.36)
		KTWidgets.attach_button_juice(btn_done)
		btn_done.pressed.connect(func():
			visible = false
			combat_finished.emit(session)
		)
		_action_bar.add_child(btn_done)


func _build_melee_ui(att: Dictionary, def: Dictionary) -> void:
	var step := str(session["step"])
	var att_p: Dictionary = session["att"]
	var def_p: Dictionary = session["def"]

	_phase_lbl.text = "白刃死斗 · " + ("交替选骰挥砍 / 招架" if step == "duel" else "对决结束")

	var pool_tag := Label.new()
	pool_tag.text = "攻方剩 [%d★/%d●]  vs  守方剩 [%d★/%d●]" % [
		int(att_p["crits"]), int(att_p["normals"]),
		int(def_p["crits"]), int(def_p["normals"])
	]
	pool_tag.add_theme_font_size_override("font_size", 13)
	pool_tag.add_theme_color_override("font_color", KTTheme.COL_TEXT)
	_mini_dice_hb.add_child(pool_tag)

	if step == "duel":
		var is_att := str(session["turn"]) == "attacker"
		var actor := att if is_att else def
		var active_side := str(actor["side"])
		var my_p: Dictionary = att_p if is_att else def_p
		var opp_p: Dictionary = def_p if is_att else att_p

		if _is_side_ai(active_side):
			var ai_l := Label.new()
			ai_l.text = "🤖 【%s】正在从桌面骰盘抓骰出招…" % str(actor["name"])
			ai_l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			ai_l.add_theme_color_override("font_color", KTTheme.COL_NURGLE.lightened(0.3))
			_action_bar.add_child(ai_l)
		else:
			var opp_brutal := bool(opp_p["rules"].get("brutal", false))
			var opp_total := int(opp_p["crits"]) + int(opp_p["normals"])
			var bonus_1st: int = 1 if (is_att and bool(my_p["shock_assault"]) and not bool(session["att_first_strike_done"])) else 0

			var b_sc := Button.new()
			b_sc.focus_mode = Control.FOCUS_NONE
			b_sc.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			b_sc.text = "🗡️ 暴击斩击 (-%dW%s)" % [int(my_p["crit_dmg"]) + bonus_1st, "·震荡" if bool(my_p["rules"].get("shock", false)) else ""]
			b_sc.disabled = int(my_p["crits"]) <= 0
			KTTheme.apply_accent_button(b_sc, KTTheme.COL_GOLD_BRIGHT, 0.34)
			KTWidgets.attach_button_juice(b_sc, 1.04, "slash")
			b_sc.pressed.connect(func(): _run_human_melee_step("strike_crit"))
			_action_bar.add_child(b_sc)

			var b_sn := Button.new()
			b_sn.focus_mode = Control.FOCUS_NONE
			b_sn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			b_sn.text = "⚔️ 普通出击 (-%dW)" % (int(my_p["normal_dmg"]) + bonus_1st)
			b_sn.disabled = int(my_p["normals"]) <= 0
			KTTheme.apply_accent_button(b_sn, KTTheme.COL_CRIMSON, 0.32)
			KTWidgets.attach_button_juice(b_sn, 1.04, "slash")
			b_sn.pressed.connect(func(): _run_human_melee_step("strike_norm"))
			_action_bar.add_child(b_sn)

			var b_pc := Button.new()
			b_pc.focus_mode = Control.FOCUS_NONE
			b_pc.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			b_pc.text = "🛡️ 暴击弹反 (碎敌1骰)"
			b_pc.disabled = int(my_p["crits"]) <= 0 or opp_total <= 0
			KTTheme.apply_accent_button(b_pc, KTTheme.COL_IMPERIAL, 0.30)
			KTWidgets.attach_button_juice(b_pc, 1.04, "parry")
			b_pc.pressed.connect(func(): _run_human_melee_step("parry_crit"))
			_action_bar.add_child(b_pc)

			var can_pn := int(my_p["normals"]) > 0 and not opp_brutal and (int(opp_p["normals"]) > 0 or (bool(my_p["dueller"]) and int(opp_p["crits"]) > 0))
			var b_pn := Button.new()
			b_pn.focus_mode = Control.FOCUS_NONE
			b_pn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			b_pn.text = "🤺 普通招架%s" % ("(残暴禁)" if opp_brutal else ("(可挡暴)" if bool(my_p["dueller"]) else ""))
			b_pn.disabled = not can_pn
			KTTheme.apply_accent_button(b_pn, KTTheme.COL_EMERALD, 0.28)
			KTWidgets.attach_button_juice(b_pn, 1.04, "parry")
			b_pn.pressed.connect(func(): _run_human_melee_step("parry_norm"))
			_action_bar.add_child(b_pn)
	else:
		var end_lbl := Label.new()
		end_lbl.text = "⚔️ 近战白刃对决结算完毕"
		end_lbl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		end_lbl.add_theme_font_size_override("font_size", 14)
		end_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
		_action_bar.add_child(end_lbl)

		var btn_done := Button.new()
		btn_done.focus_mode = Control.FOCUS_NONE
		btn_done.text = "✓ 收起骰盘 · 结束对决 [Space]"
		KTTheme.apply_accent_button(btn_done, KTTheme.COL_EMERALD, 0.36)
		KTWidgets.attach_button_juice(btn_done)
		btn_done.pressed.connect(func():
			visible = false
			combat_finished.emit(session)
		)
		_action_bar.add_child(btn_done)


func _run_human_melee_step(act: String) -> void:
	_execute_and_emit_melee_step(act)
	_rebuild_ui()
	_schedule_ai_melee_if_needed()
