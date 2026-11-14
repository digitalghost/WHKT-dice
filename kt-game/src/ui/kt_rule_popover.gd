class_name KTRulePopover
extends PanelContainer

## Floating rule card. Hover state = compact (icon, name, kind, one-line effect).
## Pinned state (click) = expanded with the full rulebook text and a close button.
## One instance shared by all KTRuleChips; positioned beside the anchor, clamped to the viewport.

var pinned: bool = false
var _anchor: Control = null
var _entry: Dictionary = {}

var _icon_lbl: Label
var _name_lbl: Label
var _kind_lbl: Label
var _short_lbl: Label
var _full_rtl: RichTextLabel
var _hint_lbl: Label
var _close_btn: Button
var _fade_t: float = 0.0


func _ready() -> void:
	visible = false
	z_index = 200
	mouse_filter = Control.MOUSE_FILTER_STOP
	custom_minimum_size = Vector2(300, 0)
	add_theme_stylebox_override(
		"panel",
		KTTheme.make_panel_style(Color(0.035, 0.040, 0.055, 0.97), KTTheme.COL_GOLD, 8, 1, 12, true, KTTheme.COL_GOLD_BRIGHT)
	)

	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 6)
	add_child(vb)

	var hdr := HBoxContainer.new()
	hdr.add_theme_constant_override("separation", 8)
	vb.add_child(hdr)

	_icon_lbl = Label.new()
	_icon_lbl.add_theme_font_size_override("font_size", 18)
	hdr.add_child(_icon_lbl)

	var name_vb := VBoxContainer.new()
	name_vb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	name_vb.add_theme_constant_override("separation", 0)
	hdr.add_child(name_vb)

	_name_lbl = Label.new()
	_name_lbl.add_theme_font_size_override("font_size", 15)
	_name_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	name_vb.add_child(_name_lbl)

	_kind_lbl = Label.new()
	_kind_lbl.add_theme_font_size_override("font_size", 10)
	_kind_lbl.add_theme_color_override("font_color", KTTheme.COL_MUTED)
	name_vb.add_child(_kind_lbl)

	_close_btn = Button.new()
	_close_btn.text = "✕"
	_close_btn.flat = true
	_close_btn.visible = false
	_close_btn.add_theme_font_size_override("font_size", 12)
	_close_btn.pressed.connect(hide_popover)
	hdr.add_child(_close_btn)

	var rule := ColorRect.new()
	rule.custom_minimum_size = Vector2(0, 1)
	rule.color = Color(KTTheme.COL_BRASS.r, KTTheme.COL_BRASS.g, KTTheme.COL_BRASS.b, 0.55)
	vb.add_child(rule)

	_short_lbl = Label.new()
	_short_lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_short_lbl.custom_minimum_size = Vector2(276, 0)
	_short_lbl.add_theme_font_size_override("font_size", 13)
	_short_lbl.add_theme_color_override("font_color", KTTheme.COL_TEXT)
	vb.add_child(_short_lbl)

	_full_rtl = RichTextLabel.new()
	_full_rtl.visible = false
	_full_rtl.fit_content = true
	_full_rtl.scroll_active = false
	_full_rtl.custom_minimum_size = Vector2(276, 0)
	_full_rtl.add_theme_font_size_override("normal_font_size", 12)
	_full_rtl.add_theme_color_override("default_color", KTTheme.COL_MUTED.lightened(0.18))
	vb.add_child(_full_rtl)

	_hint_lbl = Label.new()
	_hint_lbl.add_theme_font_size_override("font_size", 10)
	_hint_lbl.add_theme_color_override("font_color", KTTheme.COL_MUTED.darkened(0.15))
	vb.add_child(_hint_lbl)


func show_for(anchor: Control, entry: Dictionary, pin: bool) -> void:
	_anchor = anchor
	_entry = entry
	pinned = pin or (pinned and _entry.get("name", "") == entry.get("name", ""))
	pinned = pin
	_icon_lbl.text = str(entry.get("icon", "•"))
	_name_lbl.text = str(entry.get("display", entry.get("name", "")))
	var team := str(entry.get("team", ""))
	var kind_txt := str(KTGlossary.KIND_LABEL.get(str(entry.get("kind", "")), "规则"))
	_kind_lbl.text = kind_txt + ("" if team.is_empty() else " · " + ("死亡天使" if team == "IMP-AOD" else ("瘟疫战士" if team == "CHAOS-PM" else team)))
	_short_lbl.text = str(entry.get("short", ""))
	var full := str(entry.get("full", "")).strip_edges()
	var has_full := not full.is_empty() and full != str(entry.get("short", ""))
	_full_rtl.visible = pinned and has_full
	if _full_rtl.visible:
		# Collapse the source PDF's hard line breaks into paragraphs
		var paras := full.replace("\r", "").split("\n\n")
		var cleaned: PackedStringArray = []
		for p in paras:
			cleaned.append(str(p).replace("\n", "").strip_edges())
		_full_rtl.text = "\n\n".join(cleaned)
	_close_btn.visible = pinned
	_hint_lbl.text = ("点击空白处关闭" if pinned else ("点击查看规则原文" if has_full else ""))
	_hint_lbl.visible = not _hint_lbl.text.is_empty()
	visible = true
	modulate.a = 0.0
	_fade_t = 0.0
	reset_size()
	call_deferred("_place")


func _place() -> void:
	if _anchor == null or not is_instance_valid(_anchor):
		return
	var vp := get_viewport_rect().size
	var a := _anchor.get_global_rect()
	var sz := size
	var x := clampf(a.position.x + a.size.x * 0.5 - sz.x * 0.5, 12.0, maxf(12.0, vp.x - sz.x - 12.0))
	var y := a.position.y - sz.y - 10.0
	if y < 12.0:
		y = a.end.y + 10.0
	global_position = Vector2(x, y)


func hide_popover() -> void:
	pinned = false
	visible = false


func _process(delta: float) -> void:
	if not visible:
		return
	if _fade_t < 1.0:
		_fade_t = minf(1.0, _fade_t + delta * 9.0)
		modulate.a = _fade_t
		# slide up 6px while fading in
		if _anchor != null and is_instance_valid(_anchor):
			_place()
			global_position.y += 6.0 * (1.0 - _fade_t)


func _unhandled_input(ev: InputEvent) -> void:
	if not visible or not pinned:
		return
	if ev is InputEventMouseButton and ev.pressed:
		if not get_global_rect().has_point(ev.position):
			hide_popover()
	elif ev is InputEventKey and ev.pressed and ev.keycode == KEY_ESCAPE:
		hide_popover()
