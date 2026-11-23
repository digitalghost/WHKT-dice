class_name KTRuleChip
extends PanelContainer

## A keyword pill ("穿刺 1", "恼人韧性") that knows its glossary entry.
## Hover: KTRulePopover shows the one-line effect. Click: expands to the full rule text.
## Used everywhere a rule name appears: weapon cards, hotbar tooltips, combat modifier row,
## dice badges, roster ability rows.

signal pressed()

var entry: Dictionary = {}
var accent: Color = KTTheme.COL_BRASS
var _lbl: Label
var _hover_t: float = 0.0
var _is_hov: bool = false

static var _popover: KTRulePopover = null


static func ensure_popover(root: Node) -> KTRulePopover:
	if _popover != null and is_instance_valid(_popover):
		return _popover
	_popover = KTRulePopover.new()
	root.add_child(_popover)
	return _popover


## `raw` may carry a number ("致命 5+"); `tint` overrides the kind colour.
func setup(raw: String, tint: Color = Color(0, 0, 0, 0), font_px: int = 12) -> void:
	entry = KTGlossary.lookup(raw)
	var known := not entry.is_empty()
	accent = tint if tint.a > 0.01 else _kind_colour(str(entry.get("kind", "")))
	if not known:
		accent = KTTheme.COL_MUTED
	mouse_filter = Control.MOUSE_FILTER_STOP
	mouse_default_cursor_shape = Control.CURSOR_HELP if known else Control.CURSOR_ARROW
	_apply_style(0.0)
	if _lbl == null:
		_lbl = Label.new()
		_lbl.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(_lbl)
	_lbl.text = raw.strip_edges()
	_lbl.add_theme_font_size_override("font_size", font_px)
	_lbl.add_theme_color_override("font_color", accent.lightened(0.32))
	if known:
		# dotted underline cue: "this word is a rule"
		_lbl.add_theme_constant_override("outline_size", 0)


static func _kind_colour(kind: String) -> Color:
	match kind:
		"weapon": return KTTheme.COL_BRASS
		"status": return KTTheme.COL_VIOLET
		"faction": return KTTheme.COL_GOLD_BRIGHT
		"ability": return KTTheme.COL_EMERALD
		"ploy": return KTTheme.COL_IMPERIAL
		"tactic": return KTTheme.COL_IMPERIAL
		"equipment": return Color(0.90, 0.62, 0.36)
	return KTTheme.COL_MUTED


func _apply_style(hov: float) -> void:
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(accent.r * 0.16, accent.g * 0.16, accent.b * 0.16, 0.92).lerp(Color(accent.r * 0.30, accent.g * 0.30, accent.b * 0.30, 0.98), hov)
	sb.border_color = accent.lerp(KTTheme.COL_GOLD_BRIGHT, hov * 0.6)
	sb.set_border_width_all(1)
	sb.border_width_left = 3
	sb.set_corner_radius_all(4)
	sb.corner_detail = 1
	sb.content_margin_left = 7
	sb.content_margin_right = 7
	sb.content_margin_top = 1
	sb.content_margin_bottom = 1
	add_theme_stylebox_override("panel", sb)


func _notification(what: int) -> void:
	if entry.is_empty():
		return
	if what == NOTIFICATION_MOUSE_ENTER:
		_is_hov = true
		KTSfx.play_hover()
		var pop := ensure_popover(get_tree().root)
		pop.show_for(self, entry, false)
	elif what == NOTIFICATION_MOUSE_EXIT:
		_is_hov = false
		if _popover != null and is_instance_valid(_popover) and not _popover.pinned:
			_popover.hide_popover()


func _gui_input(ev: InputEvent) -> void:
	if entry.is_empty():
		return
	if ev is InputEventMouseButton and ev.pressed and ev.button_index == MOUSE_BUTTON_LEFT:
		accept_event()
		KTSfx.play("card_lift", -6.0, 0.05)
		var pop := ensure_popover(get_tree().root)
		pop.show_for(self, entry, true)
		pressed.emit()


func _process(delta: float) -> void:
	var tgt := 1.0 if _is_hov else 0.0
	if absf(_hover_t - tgt) > 0.01:
		_hover_t = move_toward(_hover_t, tgt, delta * 10.0)
		_apply_style(_hover_t)


## Convenience: build a flow of chips from a list of raw keywords.
static func make_row(keywords: Array, tint: Color = Color(0, 0, 0, 0), font_px: int = 12) -> HFlowContainer:
	var flow := HFlowContainer.new()
	flow.add_theme_constant_override("h_separation", 4)
	flow.add_theme_constant_override("v_separation", 3)
	for k in keywords:
		var chip := KTRuleChip.new()
		chip.setup(str(k), tint, font_px)
		flow.add_child(chip)
	return flow
