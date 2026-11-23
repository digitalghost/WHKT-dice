class_name KTWidgets
extends RefCounted

## Video-Game HUD Custom Controls, Vector Tactical Iconography & Interactive Juice Library:
## Replaces flat helper-app form buttons with animated skill hotbar slots, playable stratagem cards,
## real tumbling graphic Warhammer D6 dice, segmented trauma health bars, and sliding unit medallions!


static func attach_button_juice(btn: Button, hover_scale: float = 1.05, sfx_click: String = "ui_click") -> void:
	btn.focus_mode = Control.FOCUS_NONE
	btn.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	var wr: WeakRef = weakref(btn)
	btn.resized.connect(func():
		var b: Button = wr.get_ref() as Button
		if b == null or b.is_queued_for_deletion():
			return
		b.pivot_offset = b.size * 0.5
	)
	btn.pivot_offset = btn.size * 0.5
	btn.mouse_entered.connect(func():
		var b: Button = wr.get_ref() as Button
		if b == null or b.is_queued_for_deletion() or b.disabled or not b.is_inside_tree():
			return
		b.pivot_offset = b.size * 0.5
		KTSfx.play_hover()
		var tw := b.create_tween().set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
		tw.tween_property(b, "scale", Vector2.ONE * hover_scale, 0.13)
	)
	btn.mouse_exited.connect(func():
		var b: Button = wr.get_ref() as Button
		if b == null or b.is_queued_for_deletion() or not b.is_inside_tree():
			return
		b.pivot_offset = b.size * 0.5
		var tw := b.create_tween().set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
		tw.tween_property(b, "scale", Vector2.ONE, 0.12)
	)
	btn.pressed.connect(func():
		var b: Button = wr.get_ref() as Button
		if b == null or b.is_queued_for_deletion() or not b.is_inside_tree():
			return
		b.pivot_offset = b.size * 0.5
		KTSfx.play(sfx_click, -4.0, 0.05)
		b.scale = Vector2.ONE * 0.93
		var tw := b.create_tween().set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
		tw.tween_property(b, "scale", Vector2.ONE * hover_scale, 0.15)
	)


static func draw_chamfer_rect(
	ci: CanvasItem,
	r: Rect2,
	cut: float,
	fill_col: Color,
	border_col: Color,
	border_w: float = 1.8
) -> void:
	# Controls can be drawn once before their first layout pass (size 0): a collapsed
	# rect makes a degenerate polygon and Godot logs "triangulation failed".
	if r.size.x < 1.0 or r.size.y < 1.0:
		return
	var c: float = minf(cut, minf(r.size.x, r.size.y) * 0.35)
	var pts := PackedVector2Array([
		Vector2(r.position.x + c, r.position.y),
		Vector2(r.end.x - c, r.position.y),
		Vector2(r.end.x, r.position.y + c),
		Vector2(r.end.x, r.end.y - c),
		Vector2(r.end.x - c, r.end.y),
		Vector2(r.position.x + c, r.end.y),
		Vector2(r.position.x, r.end.y - c),
		Vector2(r.position.x, r.position.y + c),
	])
	if fill_col.a > 0.005:
		ci.draw_colored_polygon(pts, fill_col)
	if border_w > 0.05 and border_col.a > 0.005:
		var loop_pts := pts.duplicate()
		loop_pts.append(pts[0])
		ci.draw_polyline(loop_pts, border_col, border_w, true)


static func draw_tactical_icon(ci: CanvasItem, c: Vector2, rad: float, icon_id: String, col: Color) -> void:
	match icon_id:
		"reposition": # Double forward tactical chevrons + orbit arc
			ci.draw_arc(c + Vector2(0, rad * 0.38), rad * 0.68, PI * 0.12, PI * 0.88, 16, Color(col.r, col.g, col.b, col.a * 0.55), 2.0, true)
			for dy in [rad * 0.18, -rad * 0.28]:
				var p := PackedVector2Array([
					c + Vector2(-rad * 0.48, dy + rad * 0.24),
					c + Vector2(0.0, dy - rad * 0.22),
					c + Vector2(rad * 0.48, dy + rad * 0.24),
				])
				ci.draw_polyline(p, col, 2.8, true)
		"dash": # High-voltage lightning bolt
			var bolt := PackedVector2Array([
				c + Vector2(rad * 0.18, -rad * 0.82),
				c + Vector2(-rad * 0.44, rad * 0.06),
				c + Vector2(rad * 0.04, rad * 0.06),
				c + Vector2(-rad * 0.20, rad * 0.84),
				c + Vector2(rad * 0.46, -rad * 0.10),
				c + Vector2(-rad * 0.02, -rad * 0.10),
			])
			ci.draw_colored_polygon(bolt, col)
		"charge": # Assault spearhead + speed wings
			var spear := PackedVector2Array([
				c + Vector2(0.0, -rad * 0.82),
				c + Vector2(rad * 0.44, rad * 0.10),
				c + Vector2(0.0, -rad * 0.08),
				c + Vector2(-rad * 0.44, rad * 0.10),
			])
			ci.draw_colored_polygon(spear, col)
			ci.draw_line(c + Vector2(0, -rad * 0.1), c + Vector2(0, rad * 0.78), col, 3.0, true)
			ci.draw_line(c + Vector2(-rad * 0.58, rad * 0.48), c + Vector2(-rad * 0.22, rad * 0.18), col, 2.2, true)
			ci.draw_line(c + Vector2(rad * 0.58, rad * 0.48), c + Vector2(rad * 0.22, rad * 0.18), col, 2.2, true)
		"fall_back": # Curved disengage arrow + shield arc
			ci.draw_arc(c + Vector2(0, -rad * 0.35), rad * 0.52, PI * 1.15, PI * 1.85, 14, col, 2.4, true)
			ci.draw_line(c + Vector2(0, -rad * 0.25), c + Vector2(0, rad * 0.62), col, 2.8, true)
			var arr := PackedVector2Array([
				c + Vector2(-rad * 0.38, rad * 0.32),
				c + Vector2(0.0, rad * 0.72),
				c + Vector2(rad * 0.38, rad * 0.32),
			])
			ci.draw_polyline(arr, col, 2.8, true)
		"shoot": # Precision 4-bracket sniper crosshair + diamond core
			ci.draw_arc(c, rad * 0.62, 0.0, TAU, 24, col, 2.1, true)
			for d in [Vector2.UP, Vector2.DOWN, Vector2.LEFT, Vector2.RIGHT]:
				ci.draw_line(c + d * (rad * 0.34), c + d * (rad * 0.86), col, 2.4, true)
			ci.draw_circle(c, rad * 0.15, col)
		"fight": # Crossed Warhammer combat blades
			for sx in [-1.0, 1.0]:
				var tip := c + Vector2(sx * rad * 0.68, -rad * 0.68)
				var pom := c + Vector2(-sx * rad * 0.64, rad * 0.64)
				ci.draw_line(pom, tip, col, 3.2, true)
				var grd_c := c + Vector2(-sx * rad * 0.26, rad * 0.26)
				var perp := Vector2(sx * 0.26, 0.26) * rad
				ci.draw_line(grd_c - perp, grd_c + perp, col, 2.8, true)
		"secure": # Imperial Objective Banner & Beacon Diamond
			ci.draw_line(c + Vector2(-rad * 0.42, -rad * 0.76), c + Vector2(-rad * 0.42, rad * 0.78), col, 2.8, true)
			var flag := PackedVector2Array([
				c + Vector2(-rad * 0.40, -rad * 0.72),
				c + Vector2(rad * 0.62, -rad * 0.42),
				c + Vector2(rad * 0.32, -rad * 0.14),
				c + Vector2(rad * 0.62, rad * 0.14),
				c + Vector2(-rad * 0.40, rad * 0.14),
			])
			ci.draw_colored_polygon(flag, col)
		"unique": # Auspex / Warp Eye starburst
			var eye_top := PackedVector2Array([
				c + Vector2(-rad * 0.76, 0.0),
				c + Vector2(-rad * 0.28, -rad * 0.44),
				c + Vector2(rad * 0.28, -rad * 0.44),
				c + Vector2(rad * 0.76, 0.0),
				c + Vector2(rad * 0.28, rad * 0.44),
				c + Vector2(-rad * 0.28, rad * 0.44),
			])
			var loop_e := eye_top.duplicate()
			loop_e.append(eye_top[0])
			ci.draw_polyline(loop_e, col, 2.3, true)
			ci.draw_circle(c, rad * 0.24, col)
		"ploy": # Tactical Stratagem 8-point Command Star
			for i in range(8):
				var a: float = float(i) * PI * 0.25
				var r_len: float = rad * (0.82 if i % 2 == 0 else 0.44)
				ci.draw_line(c, c + Vector2(cos(a), sin(a)) * r_len, col, 2.4, true)
			ci.draw_circle(c, rad * 0.24, col)
		"card": # Illuminated Codex Tome
			var r_bk := Rect2(c.x - rad * 0.56, c.y - rad * 0.68, rad * 1.12, rad * 1.36)
			ci.draw_rect(r_bk, col, false, 2.3)
			ci.draw_line(c + Vector2(-rad * 0.30, -rad * 0.26), c + Vector2(rad * 0.30, -rad * 0.26), col, 2.0)
			ci.draw_line(c + Vector2(-rad * 0.30, 0.04), c + Vector2(rad * 0.30, 0.04), col, 2.0)
			ci.draw_line(c + Vector2(-rad * 0.30, rad * 0.34), c + Vector2(rad * 0.16, rad * 0.34), col, 2.0)
		"end": # Victory Checkmark Crest
			ci.draw_arc(c, rad * 0.72, 0.0, TAU, 24, Color(col.r, col.g, col.b, col.a * 0.55), 2.0, true)
			var chk := PackedVector2Array([
				c + Vector2(-rad * 0.42, rad * 0.02),
				c + Vector2(-rad * 0.08, rad * 0.38),
				c + Vector2(rad * 0.48, -rad * 0.34),
			])
			ci.draw_polyline(chk, col, 3.2, true)
		_:
			ci.draw_circle(c, rad * 0.5, col)


# ==============================================================================
# 1. SEGMENTED VIDEO-GAME TRAUMA HEALTH BAR (Delayed Damage Ghost Drain!)
# ==============================================================================
class TraumaBar extends Control:
	var max_hp: int = 14
	var cur_hp: int = 14
	var ghost_hp: float = 14.0
	var bar_color: Color = Color(0.22, 0.86, 0.52)
	var _hold_timer: float = 0.0
	var _flash_t: float = 0.0

	func _init(p_cur: int = 14, p_max: int = 14, p_col: Color = Color(0.22, 0.86, 0.52)) -> void:
		max_hp = maxi(1, p_max)
		cur_hp = clampi(p_cur, 0, max_hp)
		ghost_hp = float(cur_hp)
		bar_color = p_col
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func set_hp(new_cur: int, new_max: int, animate: bool = true) -> void:
		max_hp = maxi(1, new_max)
		var clamped := clampi(new_cur, 0, max_hp)
		if animate and clamped < cur_hp:
			_hold_timer = 0.24
			_flash_t = 1.0
		elif not animate or clamped > int(ghost_hp):
			ghost_hp = float(clamped)
		cur_hp = clamped
		queue_redraw()

	func _process(delta: float) -> void:
		var dirty := false
		if _flash_t > 0.005:
			_flash_t = maxf(0.0, _flash_t - delta * 4.5)
			dirty = true
		if ghost_hp > float(cur_hp) + 0.01:
			if _hold_timer > 0.0:
				_hold_timer -= delta
			else:
				ghost_hp = move_toward(ghost_hp, float(cur_hp), delta * float(max_hp) * 1.35)
			dirty = true
		if dirty:
			queue_redraw()

	func _draw() -> void:
		var r := Rect2(Vector2.ZERO, size)
		KTWidgets.draw_chamfer_rect(self, r, 3.0, Color(0.04, 0.05, 0.075, 0.92), Color(0.32, 0.36, 0.44, 0.78), 1.2)
		var inner := r.grow(-2.0)
		if inner.size.x <= 4.0:
			return
		var ghost_ratio := clampf(ghost_hp / float(max_hp), 0.0, 1.0)
		var cur_ratio := clampf(float(cur_hp) / float(max_hp), 0.0, 1.0)

		# Trauma ghost trail (bright amber-crimson!)
		if ghost_ratio > cur_ratio + 0.005:
			var gw: float = inner.size.x * ghost_ratio
			draw_rect(Rect2(inner.position, Vector2(gw, inner.size.y)), Color(1.0, 0.62, 0.18, 0.92), true)

		# Current HP fill
		var hp_col := bar_color
		if cur_ratio < 0.30:
			hp_col = KTTheme.COL_CRIMSON
		elif cur_ratio < 0.55:
			hp_col = KTTheme.COL_GOLD_BRIGHT
		if _flash_t > 0.02:
			hp_col = hp_col.lerp(Color(1, 1, 1, 1), _flash_t * 0.8)
		if cur_ratio > 0.0:
			var cw: float = inner.size.x * cur_ratio
			draw_rect(Rect2(inner.position, Vector2(cw, inner.size.y)), hp_col, true)
			# Specular top sheen
			draw_rect(Rect2(inner.position, Vector2(cw, maxf(1.5, inner.size.y * 0.32))), Color(1, 1, 1, 0.24), true)

		# Crisp armor segment ticks (every 1 or 2 Wounds)
		var segs: int = mini(max_hp, 18)
		if segs > 1 and inner.size.x / float(segs) >= 4.5:
			for s in range(1, segs):
				var sx: float = inner.position.x + inner.size.x * (float(s) / float(segs))
				draw_line(Vector2(sx, inner.position.y), Vector2(sx, inner.end.y), Color(0.02, 0.03, 0.05, 0.72), 1.1)


# ==============================================================================
# 2. BOTTOM-CENTER TACTICAL SKILL HOTBAR SLOT (`[1]`..`[9]` + Spring Lift!)
# ==============================================================================
class SkillSlot extends Control:
	signal triggered()
	signal hovered_changed(is_hovered: bool, slot_ctrl: Control)

	var icon_id: String = "shoot"
	var short_label: String = "射击"
	var full_title: String = "远程射击"
	var desc_text: String = ""
	var hotkey_str: String = "1"
	var ap_cost: int = 1
	var accent_col: Color = KTTheme.COL_IMPERIAL
	var disabled: bool = false
	var active_armed: bool = false

	var _hover_t: float = 0.0
	var _press_t: float = 0.0
	var _pulse_t: float = 0.0
	var _is_hov: bool = false

	func setup(
		p_icon: String,
		p_short: String,
		p_title: String,
		p_desc: String,
		p_hotkey: String,
		p_ap: int,
		p_col: Color,
		p_disabled: bool,
		p_armed: bool
	) -> void:
		icon_id = p_icon
		short_label = p_short
		full_title = p_title
		desc_text = p_desc
		hotkey_str = p_hotkey
		ap_cost = p_ap
		accent_col = p_col
		disabled = p_disabled
		active_armed = p_armed
		focus_mode = Control.FOCUS_NONE
		custom_minimum_size = Vector2(74, 82)
		mouse_default_cursor_shape = Control.CURSOR_ARROW if disabled else Control.CURSOR_POINTING_HAND
		queue_redraw()

	func _notification(what: int) -> void:
		if what == NOTIFICATION_MOUSE_ENTER:
			_is_hov = true
			if not disabled:
				KTSfx.play_hover()
			hovered_changed.emit(true, self)
		elif what == NOTIFICATION_MOUSE_EXIT:
			_is_hov = false
			hovered_changed.emit(false, self)

	func _gui_input(event: InputEvent) -> void:
		if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
			accept_event()
			if disabled:
				KTSfx.play("ui_hover", -14.0, 0.2)
				return
			_press_t = 1.0
			KTSfx.play("ui_click", -3.5, 0.04)
			triggered.emit()

	func _process(delta: float) -> void:
		_pulse_t += delta
		var target_h: float = 1.0 if (_is_hov and not disabled) else 0.0
		var prev_h := _hover_t
		_hover_t = move_toward(_hover_t, target_h, delta * 9.5)
		if _press_t > 0.005:
			_press_t = maxf(0.0, _press_t - delta * 7.5)
		if absf(_hover_t - prev_h) > 0.001 or _press_t > 0.005 or active_armed:
			queue_redraw()

	func _draw() -> void:
		var font: Font = KTTheme.get_cjk_font()
		var lift_y: float = -8.0 * _hover_t + 3.5 * _press_t - (3.0 if active_armed else 0.0)
		var grow_px: float = 2.5 * _hover_t - 2.0 * _press_t
		var card_r := Rect2(2.0 - grow_px * 0.5, 8.0 + lift_y, size.x - 4.0 + grow_px, size.y - 10.0)

		# Outer neon aura shadow when hovered or armed!
		if _hover_t > 0.02 or active_armed:
			var glow_a: float = 0.34 if active_armed else (0.24 * _hover_t)
			KTWidgets.draw_chamfer_rect(self, card_r.grow(3.5), 10.0, Color(accent_col.r, accent_col.g, accent_col.b, glow_a), Color(0, 0, 0, 0), 0.0)

		var bg_col := Color(0.055, 0.066, 0.092, 0.94)
		if disabled:
			bg_col = Color(0.042, 0.046, 0.058, 0.76)
		elif active_armed:
			bg_col = Color(accent_col.r * 0.28 + 0.05, accent_col.g * 0.28 + 0.05, accent_col.b * 0.28 + 0.06, 0.97)
		elif _hover_t > 0.01:
			bg_col = bg_col.lerp(Color(accent_col.r * 0.22 + 0.07, accent_col.g * 0.22 + 0.08, accent_col.b * 0.22 + 0.10, 0.97), _hover_t)

		var rim_c := Color(0.24, 0.27, 0.33, 0.62) if disabled else accent_col.lerp(KTTheme.COL_GOLD_BRIGHT, 0.65 if active_armed else (_hover_t * 0.45))
		KTWidgets.draw_chamfer_rect(self, card_r, 8.5, bg_col, rim_c, 2.4 if (active_armed or _hover_t > 0.4) else 1.6)

		# Inner top specular armor bevel
		draw_line(
			Vector2(card_r.position.x + 9.0, card_r.position.y + 2.0),
			Vector2(card_r.end.x - 9.0, card_r.position.y + 2.0),
			Color(1, 1, 1, 0.08 + 0.18 * _hover_t), 1.2
		)

		# Hotkey Badge (Top-Left)
		if not hotkey_str.is_empty():
			var hk_r := Rect2(card_r.position.x + 4.0, card_r.position.y + 4.0, 20.0, 15.0)
			draw_rect(hk_r, Color(0.02, 0.03, 0.05, 0.88), true)
			draw_rect(hk_r, Color(rim_c.r, rim_c.g, rim_c.b, 0.65), false, 1.0)
			draw_string(font, Vector2(hk_r.position.x, hk_r.position.y + 11.5), hotkey_str, HORIZONTAL_ALIGNMENT_CENTER, hk_r.size.x, 10, KTTheme.COL_GOLD_BRIGHT if not disabled else KTTheme.COL_MUTED)

		# AP Diamond Pips (Top-Right)
		for p in range(ap_cost):
			var dc := Vector2(card_r.end.x - 9.0 - float(p) * 9.0, card_r.position.y + 11.0)
			var dr: float = 3.4
			var dcol := Color(0.35, 0.38, 0.44, 0.6) if disabled else KTTheme.COL_GOLD_BRIGHT
			draw_colored_polygon(PackedVector2Array([
				dc + Vector2(0, -dr), dc + Vector2(dr, 0), dc + Vector2(0, dr), dc + Vector2(-dr, 0)
			]), dcol)

		# Icon (Center): kit bitmap when available, procedural vector otherwise
		var ico_col := Color(0.38, 0.41, 0.48, 0.58) if disabled else accent_col.lightened(0.28 + 0.20 * _hover_t)
		if active_armed:
			var pulse_w: float = 0.5 + 0.5 * sin(_pulse_t * 6.5)
			ico_col = KTTheme.COL_GOLD_BRIGHT.lightened(0.18 * pulse_w)
		var ico_c := Vector2(card_r.get_center().x, card_r.position.y + 37.0)
		var ico_tex := KTTheme.kit_tex_scaled("icon_" + icon_id, 44)
		if ico_tex != null:
			var isz: float = 40.0 + 3.0 * _hover_t
			# Kit icons are already brass-coloured; modulate only for disabled / armed states
			var mod := Color(0.45, 0.47, 0.52, 0.55) if disabled else (Color(1.0, 1.0, 1.0, 1.0) if not active_armed else Color(1.0, 1.0, 0.85, 1.0))
			draw_texture_rect(ico_tex, Rect2(ico_c - Vector2(isz, isz) * 0.5, Vector2(isz, isz)), false, mod)
		else:
			KTWidgets.draw_tactical_icon(self, ico_c, 14.5 + 1.2 * _hover_t, icon_id, ico_col)

		# Bottom Name Strip
		var strip_r := Rect2(card_r.position.x + 3.0, card_r.end.y - 20.0, card_r.size.x - 6.0, 17.0)
		draw_rect(strip_r, Color(0.02, 0.025, 0.04, 0.78), true)
		var lbl_col := KTTheme.COL_MUTED.darkened(0.2) if disabled else (KTTheme.COL_GOLD_BRIGHT if active_armed else KTTheme.COL_TEXT)
		draw_string(font, Vector2(strip_r.position.x, strip_r.position.y + 13.0), short_label, HORIZONTAL_ALIGNMENT_CENTER, strip_r.size.x, 12, lbl_col)


# ==============================================================================
# 2b. WEAPON CARD (name + stat line + rule-keyword chips) for the shoot-mode strip
# ==============================================================================

class WeaponCard extends PanelContainer:
	signal selected()

	var weapon: Dictionary = {}
	var is_sel: bool = false
	var accent: Color = KTTheme.COL_IMPERIAL

	func setup(w: Dictionary, p_sel: bool, p_accent: Color) -> void:
		weapon = w
		is_sel = p_sel
		accent = p_accent
		focus_mode = Control.FOCUS_NONE
		mouse_filter = Control.MOUSE_FILTER_STOP
		mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
		custom_minimum_size = Vector2(200, 0)
		var rim := KTTheme.COL_GOLD_BRIGHT if is_sel else Color(accent.r, accent.g, accent.b, 0.7)
		var bg := Color(0.09, 0.075, 0.03, 0.96) if is_sel else Color(0.035, 0.042, 0.062, 0.94)
		add_theme_stylebox_override("panel", KTTheme.make_panel_style(bg, rim, 7, 2 if is_sel else 1, 8, is_sel, KTTheme.COL_GOLD_BRIGHT))

		for c in get_children():
			c.queue_free()
		var vb := VBoxContainer.new()
		vb.add_theme_constant_override("separation", 3)
		vb.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(vb)

		var hdr := HBoxContainer.new()
		hdr.mouse_filter = Control.MOUSE_FILTER_IGNORE
		vb.add_child(hdr)
		var name_l := Label.new()
		name_l.text = ("▶ " if is_sel else "") + str(w["name"]).replace("爆矢狙击步枪", "狙击步枪")
		name_l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		name_l.add_theme_font_size_override("font_size", 13)
		name_l.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT if is_sel else KTTheme.COL_TEXT)
		name_l.mouse_filter = Control.MOUSE_FILTER_IGNORE
		hdr.add_child(name_l)

		var stat_l := Label.new()
		stat_l.text = "%dA · %d+ · %d/%d★" % [int(w["attacks"]), int(w["hit"]), int(w["normal_dmg"]), int(w["crit_dmg"])]
		stat_l.add_theme_font_size_override("font_size", 12)
		stat_l.add_theme_color_override("font_color", KTTheme.COL_BRASS.lightened(0.15))
		stat_l.mouse_filter = Control.MOUSE_FILTER_IGNORE
		vb.add_child(stat_l)

		var kws: Array = w.get("keywords", [])
		if kws.is_empty():
			var none_l := Label.new()
			none_l.text = "常规弹药"
			none_l.add_theme_font_size_override("font_size", 10)
			none_l.add_theme_color_override("font_color", KTTheme.COL_MUTED)
			vb.add_child(none_l)
		else:
			vb.add_child(KTRuleChip.make_row(kws, Color(0, 0, 0, 0), 10))

	func _gui_input(ev: InputEvent) -> void:
		if ev is InputEventMouseButton and ev.pressed and ev.button_index == MOUSE_BUTTON_LEFT:
			accept_event()
			KTSfx.play("ui_click", -4.0, 0.04)
			selected.emit()

	func _notification(what: int) -> void:
		if what == NOTIFICATION_MOUSE_ENTER:
			KTSfx.play_hover()


# ==============================================================================
# 3. PLAYABLE TACTICAL STRATAGEM CARD (Fan / Lift-on-Hover Card Hand!)
# ==============================================================================
class StratagemCard extends Control:
	signal played()

	var title_str: String = ""
	var desc_str: String = ""
	var cost_str: String = "1 CP"
	var accent_col: Color = KTTheme.COL_IMPERIAL
	var disabled: bool = false
	var active_used: bool = false

	var _hover_t: float = 0.0
	var _is_hov: bool = false

	# Deal-in animation: card rises from below the hand with a slight fan tilt, staggered by index.
	var _deal_t: float = 0.0
	var _deal_delay: float = 0.0
	var _crest_tex: Texture2D = null
	var _kind_str: String = "战略计谋"

	func setup(p_title: String, p_desc: String, p_cost: String, p_col: Color, p_disabled: bool, p_used: bool = false, p_deal_index: int = 0, p_kind: String = "战略计谋") -> void:
		title_str = p_title
		desc_str = p_desc
		cost_str = p_cost
		accent_col = p_col
		disabled = p_disabled
		active_used = p_used
		_kind_str = p_kind
		_deal_delay = 0.045 * float(p_deal_index)
		var is_imp: bool = p_col.is_equal_approx(KTTheme.COL_IMPERIAL)
		_crest_tex = KTData.load_tex("res://assets/ui/team_mark_imp_aod.png" if is_imp else "res://assets/ui/team_mark_chaos_pm.png")
		focus_mode = Control.FOCUS_NONE
		custom_minimum_size = Vector2(176, 150)
		mouse_default_cursor_shape = Control.CURSOR_ARROW if disabled else Control.CURSOR_POINTING_HAND
		tooltip_text = p_title + "\n" + p_desc
		pivot_offset = Vector2(88.0, 150.0)
		queue_redraw()

	func _notification(what: int) -> void:
		if what == NOTIFICATION_MOUSE_ENTER:
			_is_hov = true
			if not disabled:
				KTSfx.play("card_lift", -7.0, 0.06)
		elif what == NOTIFICATION_MOUSE_EXIT:
			_is_hov = false

	func _gui_input(event: InputEvent) -> void:
		if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
			accept_event()
			if not disabled:
				KTSfx.play("order_servo", -4.0, 0.05)
				played.emit()
			else:
				KTSfx.play("ui_hover", -14.0, 0.2)

	func _process(delta: float) -> void:
		var dirty := false
		if _deal_t < 1.0:
			if _deal_delay > 0.0:
				_deal_delay -= delta
			else:
				_deal_t = minf(1.0, _deal_t + delta * 6.0)
			# Container owns `position`, so animate only what it doesn't: alpha, tilt and scale about the bottom pivot.
			var e: float = 1.0 - pow(1.0 - _deal_t, 3.0)
			modulate.a = e
			rotation = deg_to_rad(-9.0 * (1.0 - e))
			scale = Vector2.ONE * lerpf(0.82, 1.0, e)
			dirty = true
		var target_h: float = 1.0 if (_is_hov and not disabled) else 0.0
		if absf(_hover_t - target_h) > 0.002:
			_hover_t = move_toward(_hover_t, target_h, delta * 9.0)
			dirty = true
		if dirty:
			queue_redraw()

	func _draw() -> void:
		var font: Font = KTTheme.get_cjk_font()
		var lift_y: float = -16.0 * _hover_t
		var r := Rect2(2.0, 14.0 + lift_y, size.x - 4.0, size.y - 16.0)

		# Glow halo when hovered / played
		if _hover_t > 0.02 or active_used:
			KTWidgets.draw_chamfer_rect(self, r.grow(5.0), 8.0, Color(accent_col.r, accent_col.g, accent_col.b, 0.30 * maxf(_hover_t, 0.6 if active_used else 0.0)), Color(0, 0, 0, 0), 0.0)

		# Card stock: parchment-dark gradient like a printed KT24 ploy card
		var bg_top := Color(0.14, 0.145, 0.16, 0.97)
		var bg_bot := Color(0.075, 0.08, 0.095, 0.97)
		if active_used:
			bg_top = Color(accent_col.r * 0.30, accent_col.g * 0.30, accent_col.b * 0.30, 0.97)
			bg_bot = Color(accent_col.r * 0.16, accent_col.g * 0.16, accent_col.b * 0.16, 0.97)
		elif disabled:
			bg_top = Color(0.07, 0.072, 0.08, 0.80)
			bg_bot = Color(0.045, 0.047, 0.055, 0.80)
		var border_c := KTTheme.COL_GOLD_BRIGHT if (_hover_t > 0.45 or active_used) else (Color(0.26, 0.29, 0.35, 0.6) if disabled else accent_col)
		var stock := KTTheme.kit_tex("panel_card_stock")
		if stock != null:
			# Kit card stock (gunmetal brushed, brass inset) as the card body; draw via a nine-slice so the
			# brushed grain doesn't stretch. Tint for disabled / played states.
			var sb := KTTheme.kit_panel("panel_card_stock", 0) as StyleBoxTexture
			sb.modulate_color = Color(0.5, 0.5, 0.55, 0.85) if disabled else (Color(1.0, 1.0, 1.0, 1.0).lerp(accent_col.lightened(0.3), 0.35) if active_used else Color(1, 1, 1, 1))
			sb.draw(get_canvas_item(), r)
			KTWidgets.draw_chamfer_rect(self, r, 6.0, Color(0, 0, 0, 0), border_c, 2.4 if _hover_t > 0.3 else 1.4)
		else:
			KTWidgets.draw_chamfer_rect(self, r, 6.0, bg_bot, border_c, 2.4 if _hover_t > 0.3 else 1.6)
			draw_rect(Rect2(r.position.x + 2.0, r.position.y + 2.0, r.size.x - 4.0, r.size.y * 0.45), Color(bg_top.r, bg_top.g, bg_top.b, 0.55), true)

		# Faction crest watermark behind the text
		if _crest_tex != null:
			var cs := minf(r.size.x, r.size.y) * 0.72
			var crest_r := Rect2(r.get_center() - Vector2(cs * 0.5, cs * 0.5 - 10.0), Vector2(cs, cs))
			draw_texture_rect(_crest_tex, crest_r, false, Color(1, 1, 1, 0.10 if not disabled else 0.05))

		# Header: faction-colour band with kind label + CP gem
		var hdr_r := Rect2(r.position.x + 2.0, r.position.y + 2.0, r.size.x - 4.0, 22.0)
		draw_rect(hdr_r, Color(accent_col.r * 0.55, accent_col.g * 0.55, accent_col.b * 0.55, 0.85 if not disabled else 0.30), true)
		draw_string(font, Vector2(hdr_r.position.x + 8.0, hdr_r.position.y + 15.5), _kind_str, HORIZONTAL_ALIGNMENT_LEFT, hdr_r.size.x - 50.0, 10, Color(1, 1, 1, 0.85 if not disabled else 0.45))

		var cp_r := Rect2(hdr_r.end.x - 42.0, hdr_r.position.y + 2.0, 38.0, 18.0)
		KTWidgets.draw_chamfer_rect(self, cp_r, 3.0, Color(0.03, 0.04, 0.06, 0.95), KTTheme.COL_GOLD_BRIGHT if not disabled else KTTheme.COL_MUTED, 1.2)
		draw_string(font, Vector2(cp_r.position.x, cp_r.position.y + 13.5), cost_str, HORIZONTAL_ALIGNMENT_CENTER, cp_r.size.x, 11, KTTheme.COL_GOLD_BRIGHT if not disabled else KTTheme.COL_MUTED)

		# Title: bold, on a thin brass rule
		var t_col := KTTheme.COL_GOLD_BRIGHT if _hover_t > 0.3 else (KTTheme.COL_MUTED if disabled else KTTheme.COL_TEXT)
		var title_y := hdr_r.end.y + 20.0
		draw_string(font, Vector2(r.position.x + 8.0, title_y), title_str, HORIZONTAL_ALIGNMENT_LEFT, r.size.x - 16.0, 13, t_col)
		draw_rect(Rect2(r.position.x + 8.0, title_y + 5.0, r.size.x - 16.0, 1.0), Color(KTTheme.COL_BRASS.r, KTTheme.COL_BRASS.g, KTTheme.COL_BRASS.b, 0.7 if not disabled else 0.3), true)

		# Rule text
		draw_multiline_string(
			font,
			Vector2(r.position.x + 8.0, title_y + 22.0),
			desc_str,
			HORIZONTAL_ALIGNMENT_LEFT,
			r.size.x - 16.0,
			11,
			4,
			KTTheme.COL_TEXT.darkened(0.10) if not disabled else KTTheme.COL_MUTED.darkened(0.2)
		)


# ==============================================================================
# 4. REAL TUMBLING GRAPHIC WARHAMMER D6 DIE (Pips 1-5 + Glowing Skull/Star 6!)
# ==============================================================================
class GraphicDie extends Control:
	signal die_clicked()

	var face_val: int = 6
	var kind: String = "crit" # "crit" | "norm" | "fail"
	var rerolled: bool = false
	var selected: bool = false
	var interactive: bool = true

	var _roll_timer: float = 0.0
	var _roll_dur: float = 0.38
	var _roll_delay: float = 0.0
	var _display_face: int = 6
	var _hover_t: float = 0.0
	var _is_hov: bool = false
	var _pulse_t: float = 0.0

	func setup(p_face: int, p_kind: String, p_rerolled: bool, p_selected: bool, p_interactive: bool, roll_delay: float = -1.0) -> void:
		face_val = clampi(p_face, 1, 6)
		_display_face = face_val
		kind = p_kind
		rerolled = p_rerolled
		selected = p_selected
		interactive = p_interactive and not p_rerolled
		focus_mode = Control.FOCUS_NONE
		custom_minimum_size = Vector2(70, 78)
		mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND if interactive else Control.CURSOR_ARROW
		if roll_delay >= 0.0:
			_roll_delay = roll_delay
			_roll_timer = _roll_dur
		queue_redraw()

	func _notification(what: int) -> void:
		if what == NOTIFICATION_MOUSE_ENTER:
			_is_hov = true
			if interactive:
				KTSfx.play_hover()
		elif what == NOTIFICATION_MOUSE_EXIT:
			_is_hov = false

	func _gui_input(event: InputEvent) -> void:
		if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
			accept_event()
			if interactive:
				KTSfx.play("ui_click", -4.0, 0.06)
				die_clicked.emit()

	func _process(delta: float) -> void:
		_pulse_t += delta
		var dirty := false
		if _roll_delay > 0.0:
			_roll_delay -= delta
			dirty = true
		elif _roll_timer > 0.0:
			_roll_timer = maxf(0.0, _roll_timer - delta)
			if _roll_timer > 0.04:
				_display_face = 1 + (int(_pulse_t * 34.0) % 6)
			else:
				_display_face = face_val
			dirty = true

		var target_h: float = 1.0 if (_is_hov and interactive) else 0.0
		if absf(_hover_t - target_h) > 0.002:
			_hover_t = move_toward(_hover_t, target_h, delta * 10.0)
			dirty = true
		if selected or kind == "crit":
			dirty = true
		if dirty:
			queue_redraw()

	func _draw() -> void:
		var font: Font = KTTheme.get_cjk_font()
		var bounce_y: float = 0.0
		var spin_ang: float = 0.0
		if _roll_delay > 0.0:
			bounce_y = -14.0
		elif _roll_timer > 0.0:
			var u: float = 1.0 - (_roll_timer / _roll_dur)
			bounce_y = -absf(sin(u * PI * 2.2)) * 18.0 * (1.0 - u)
			spin_ang = (1.0 - u) * TAU * 1.5

		var lift_y: float = -6.0 * _hover_t + bounce_y - (4.0 if selected else 0.0)
		var cube_sz: float = 54.0
		var center := Vector2(size.x * 0.5, 34.0 + lift_y)

		var col := KTTheme.COL_GOLD_BRIGHT if kind == "crit" else (KTTheme.COL_EMERALD if kind == "norm" else Color(0.46, 0.50, 0.58))
		var bg := Color(0.24, 0.18, 0.05, 0.98) if kind == "crit" else (Color(0.05, 0.21, 0.14, 0.98) if kind == "norm" else Color(0.08, 0.09, 0.12, 0.94))

		draw_set_transform(center, spin_ang, Vector2.ONE * (1.0 + 0.06 * _hover_t))
		var local_r := Rect2(-cube_sz * 0.5, -cube_sz * 0.5, cube_sz, cube_sz)

		# Outer crit / selection aura
		if selected or kind == "crit" or _hover_t > 0.05:
			var aura_a: float = 0.42 if selected else (0.26 + 0.12 * sin(_pulse_t * 5.5) if kind == "crit" else 0.22 * _hover_t)
			KTWidgets.draw_chamfer_rect(self, local_r.grow(4.5), 10.0, Color(col.r, col.g, col.b, aura_a), Color(0, 0, 0, 0), 0.0)

		# 3D Bevelled Cube Face + Inner Recessed Dish
		var rim_col := Color(1, 1, 1, 1) if selected else col
		KTWidgets.draw_chamfer_rect(self, local_r, 8.0, bg, rim_col, 3.0 if selected else 2.0)
		KTWidgets.draw_chamfer_rect(self, local_r.grow(-5.0), 5.0, bg.lightened(0.08), Color(col.r, col.g, col.b, 0.32), 1.0)

		# Draw Pips 1-5 or Warhammer Crit Starburst on 6!
		var pip_col := Color(1.0, 0.95, 0.72) if kind == "crit" else (col.lightened(0.42) if kind == "norm" else Color(0.62, 0.66, 0.74, 0.72))
		var off: float = 12.5
		var pr: float = 4.2
		if _display_face == 6:
			# Iconic Warhammer Crit 8-Pointed Star / Imperial Crest on 6!
			for s in range(8):
				var a: float = float(s) * PI * 0.25
				var rl: float = 16.5 if s % 2 == 0 else 8.0
				draw_line(Vector2.ZERO, Vector2(cos(a), sin(a)) * rl, pip_col, 2.6, true)
			draw_circle(Vector2.ZERO, 5.2, pip_col)
			draw_circle(Vector2.ZERO, 2.2, bg)
		else:
			var pips: Array[Vector2] = []
			if _display_face in [1, 3, 5]:
				pips.append(Vector2.ZERO)
			if _display_face >= 2:
				pips.append(Vector2(-off, -off))
				pips.append(Vector2(off, off))
			if _display_face >= 4:
				pips.append(Vector2(off, -off))
				pips.append(Vector2(-off, off))
			for pp in pips:
				draw_circle(pp, pr, pip_col)

		draw_set_transform(Vector2.ZERO, 0.0, Vector2.ONE)

		# Crisp Status Tag Underneath Cube
		var tag_txt := "★暴击" if kind == "crit" else ("●命中" if kind == "norm" else "✕未中")
		if rerolled:
			tag_txt += "↻"
		draw_string(font, Vector2(0.0, size.y - 4.0), tag_txt, HORIZONTAL_ALIGNMENT_CENTER, size.x, 11, col.lightened(0.28))


# ==============================================================================
# 5. FLOATING EDGE SQUAD MEDALLION (Slides Out on Hover + Trauma HP Bar!)
# ==============================================================================
class SquadMedallion extends Control:
	signal clicked_unit()
	signal card_requested()
	signal hover_unit_changed(hovered: bool)

	var op_data: Dictionary = {}
	var is_left_side: bool = true
	var is_active: bool = false
	var is_selected: bool = false

	var _tex: Texture2D = null
	var _order_tex: Texture2D = null
	var _trauma_bar: TraumaBar = null
	var _hover_t: float = 0.0
	var _is_hov: bool = false

	func setup(
		op: Dictionary,
		left_side: bool,
		active_flag: bool,
		selected_flag: bool,
		standee_tex: Texture2D,
		ord_tex: Texture2D
	) -> void:
		op_data = op
		is_left_side = left_side
		is_active = active_flag
		is_selected = selected_flag
		_tex = standee_tex
		_order_tex = ord_tex
		focus_mode = Control.FOCUS_NONE
		custom_minimum_size = Vector2(186, 46)
		mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND

		var fcol := KTTheme.COL_IMPERIAL if left_side else KTTheme.COL_NURGLE
		if _trauma_bar == null:
			_trauma_bar = TraumaBar.new(int(op["wounds_cur"]), int(op["wounds_max"]), fcol.lightened(0.18))
			add_child(_trauma_bar)
		else:
			_trauma_bar.set_hp(int(op["wounds_cur"]), int(op["wounds_max"]), true)
		queue_redraw()

	func _notification(what: int) -> void:
		if what == NOTIFICATION_RESIZED:
			_layout_bar()
		elif what == NOTIFICATION_MOUSE_ENTER:
			_is_hov = true
			KTSfx.play_hover()
			hover_unit_changed.emit(true)
		elif what == NOTIFICATION_MOUSE_EXIT:
			_is_hov = false
			hover_unit_changed.emit(false)

	func _layout_bar() -> void:
		if _trauma_bar == null:
			return
		var slide_x: float = (10.0 if is_left_side else -10.0) * _hover_t
		if is_left_side:
			_trauma_bar.position = Vector2(50.0 + slide_x, 31.0)
			_trauma_bar.size = Vector2(104.0, 7.0)
		else:
			_trauma_bar.position = Vector2(32.0 + slide_x, 31.0)
			_trauma_bar.size = Vector2(104.0, 7.0)
		_trauma_bar.visible = not bool(op_data.get("incapacitated", false))

	func _gui_input(event: InputEvent) -> void:
		if event is InputEventMouseButton and event.pressed:
			if event.button_index == MOUSE_BUTTON_RIGHT:
				accept_event()
				KTSfx.play("card_lift", -5.0, 0.04)
				card_requested.emit()
			elif event.button_index == MOUSE_BUTTON_LEFT:
				accept_event()
				# Check mini codex icon region at far end
				var lx: float = event.position.x
				var hit_card: bool = (lx > size.x - 28.0) if is_left_side else (lx < 28.0)
				if hit_card:
					KTSfx.play("card_lift", -5.0, 0.04)
					card_requested.emit()
				else:
					KTSfx.play("ui_click", -4.0, 0.04)
					clicked_unit.emit()

	func _process(delta: float) -> void:
		var target_h: float = 1.0 if (_is_hov or is_active) else 0.0
		if absf(_hover_t - target_h) > 0.002:
			_hover_t = move_toward(_hover_t, target_h, delta * 10.0)
			_layout_bar()
			queue_redraw()

	func _draw() -> void:
		if op_data.is_empty():
			return
		_layout_bar()
		var font: Font = KTTheme.get_cjk_font()
		var dead: bool = bool(op_data.get("incapacitated", false))
		var ready: bool = bool(op_data.get("ready", false))
		var slide_x: float = (10.0 if is_left_side else -10.0) * _hover_t
		var r := Rect2(slide_x, 2.0, size.x, size.y - 4.0)

		var fcol := KTTheme.COL_IMPERIAL if is_left_side else KTTheme.COL_NURGLE
		var rim_c := KTTheme.COL_GOLD_BRIGHT if is_active else (Color(1, 1, 1, 0.85) if is_selected else fcol)
		if dead:
			rim_c = Color(0.28, 0.24, 0.26, 0.45)
		elif not ready and not is_active:
			rim_c = Color(fcol.r, fcol.g, fcol.b, 0.42)

		var bg := Color(0.038, 0.048, 0.072, 0.88 if not dead else 0.52)
		if is_active:
			bg = Color(0.16, 0.13, 0.04, 0.94)

		KTWidgets.draw_chamfer_rect(self, r, 7.0, bg, rim_c, 2.2 if is_active else 1.3)

		# Portrait Box
		var px: float = r.position.x + 5.0 if is_left_side else r.end.x - 45.0
		var port_r := Rect2(px, r.position.y + 3.0, 40.0, 36.0)
		if _tex != null:
			var tw: float = float(_tex.get_width())
			var th: float = float(_tex.get_height())
			var sc: float = minf(port_r.size.x / maxf(1.0, tw), port_r.size.y / maxf(1.0, th))
			var dw: float = tw * sc
			var dh: float = th * sc
			var dr := Rect2(port_r.position.x + (port_r.size.x - dw) * 0.5, port_r.end.y - dh, dw, dh)
			draw_texture_rect(_tex, dr, false, Color(0.35, 0.35, 0.38, 0.65) if dead else Color(1, 1, 1, 1))

		# Order Mini Badge
		if _order_tex != null and not dead:
			var badge_sz: float = 20.0
			var ox: float = (port_r.end.x - badge_sz + 1.0) if is_left_side else (port_r.position.x + 1.0)
			draw_texture_rect(_order_tex, Rect2(ox, port_r.end.y - badge_sz + 1.0, badge_sz, badge_sz), false)

		# Name + Wounds Text
		var short_n := str(op_data["name"]).replace("星际战士", "").replace("歼灭者", "").replace("仲裁者", "").replace("重装", "重").replace("瘟疫战士", "瘟疫")
		var tx: float = r.position.x + 48.0 if is_left_side else r.position.x + 28.0
		var status_tag := " 💀阵亡" if dead else (" %d/%d" % [int(op_data["wounds_cur"]), int(op_data["wounds_max"])])
		var name_col := KTTheme.COL_MUTED.darkened(0.25) if dead else (KTTheme.COL_GOLD_BRIGHT if is_active else (KTTheme.COL_TEXT if ready else KTTheme.COL_MUTED))
		draw_string(font, Vector2(tx, r.position.y + 19.0), short_n + status_tag, HORIZONTAL_ALIGNMENT_LEFT, 114.0, 12, name_col)

		# Ready Pip / Codex Mini Icon at Outer Tip
		var cx_ico: float = r.end.x - 14.0 if is_left_side else r.position.x + 14.0
		KTWidgets.draw_tactical_icon(self, Vector2(cx_ico, r.get_center().y), 7.5, "card", KTTheme.COL_GOLD_BRIGHT if _is_hov else Color(0.58, 0.63, 0.74, 0.65))
