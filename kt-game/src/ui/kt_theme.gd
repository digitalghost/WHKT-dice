class_name KTTheme
extends RefCounted

## Grimdark Warhammer 40K Tactical Auspex UI Palette & Chamfered Armor-Plate StyleBox Factory.

const COL_BG_DEEP := Color(0.035, 0.042, 0.058, 1.0)
const COL_PANEL := Color(0.062, 0.075, 0.102, 0.94)
const COL_PANEL_ELEV := Color(0.092, 0.110, 0.148, 0.96)
const COL_BRASS := Color(0.84, 0.67, 0.34, 1.0)
const COL_BRASS_DIM := Color(0.48, 0.39, 0.22, 0.88)
const COL_GOLD := Color(0.92, 0.75, 0.34, 1.0)
const COL_GOLD_BRIGHT := Color(1.0, 0.84, 0.36, 1.0)
const COL_IMPERIAL := Color(0.28, 0.62, 1.0, 1.0)
const COL_NURGLE := Color(0.56, 0.86, 0.26, 1.0)
const COL_CRIMSON := Color(0.94, 0.26, 0.26, 1.0)
const COL_EMERALD := Color(0.22, 0.88, 0.56, 1.0)
const COL_VIOLET := Color(0.76, 0.46, 1.0, 1.0)
const COL_TEXT := Color(0.95, 0.94, 0.90, 1.0)
const COL_MUTED := Color(0.64, 0.68, 0.74, 1.0)

static var _cached_theme: Theme = null
static var _cjk_font: SystemFont = null


static func get_cjk_font() -> SystemFont:
	if _cjk_font == null:
		_cjk_font = SystemFont.new()
		_cjk_font.font_names = PackedStringArray([
			"Hiragino Sans GB",
			"Heiti SC",
			"STHeiti",
			"PingFang SC",
			"Microsoft YaHei",
			"Noto Sans CJK SC",
			"Arial Unicode MS",
			"Apple Color Emoji",
			"sans-serif"
		])
		_cjk_font.antialiasing = TextServer.FONT_ANTIALIASING_GRAY
		_cjk_font.subpixel_positioning = TextServer.SUBPIXEL_POSITIONING_AUTO
	return _cjk_font


static func build_global_theme() -> Theme:
	if _cached_theme != null:
		return _cached_theme

	var th := Theme.new()
	var font := get_cjk_font()
	th.default_font = font
	th.default_font_size = 15

	th.set_color("font_color", "Label", COL_TEXT)
	th.set_color("font_color", "RichTextLabel", COL_TEXT)

	# Chamfered Grimdark Tactical Buttons
	th.set_stylebox("normal", "Button", make_btn_style(Color(0.11, 0.135, 0.185, 0.95), COL_BRASS_DIM))
	th.set_stylebox("hover", "Button", make_btn_style(Color(0.18, 0.22, 0.29, 0.98), COL_GOLD_BRIGHT, 6, 12, 7, true))
	th.set_stylebox("pressed", "Button", make_btn_style(Color(0.28, 0.22, 0.11, 1.0), COL_GOLD_BRIGHT, 6, 12, 7, true))
	th.set_stylebox("disabled", "Button", make_btn_style(Color(0.065, 0.075, 0.095, 0.62), Color(0.22, 0.25, 0.30, 0.42)))
	th.set_stylebox("focus", "Button", make_btn_style(Color(0.15, 0.185, 0.25, 0.98), COL_BRASS))
	th.set_color("font_color", "Button", COL_TEXT)
	th.set_color("font_hover_color", "Button", COL_GOLD_BRIGHT)
	th.set_color("font_pressed_color", "Button", COL_GOLD_BRIGHT)
	th.set_color("font_disabled_color", "Button", Color(0.42, 0.45, 0.50, 0.68))

	# PanelContainer default
	th.set_stylebox("panel", "PanelContainer", make_panel_style(COL_PANEL, COL_BRASS_DIM, 8, 1, 10))

	# Tooltip styling
	var tt := make_panel_style(Color(0.045, 0.055, 0.078, 0.98), COL_GOLD_BRIGHT, 6, 1, 9)
	th.set_stylebox("panel", "TooltipPanel", tt)
	th.set_color("font_color", "TooltipLabel", COL_TEXT)

	_cached_theme = th
	return th


static func make_panel_style(
	bg: Color,
	border: Color,
	corner_radius: int = 8,
	border_width: int = 1,
	padding: int = 10,
	glow_tint: bool = false,
	glow_col: Color = Color(0, 0, 0, 0)
) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color = bg
	sb.border_color = border
	sb.set_border_width_all(border_width)
	sb.border_width_top = maxi(border_width, 2) if border_width >= 2 else border_width
	sb.set_corner_radius_all(corner_radius)
	# corner_detail = 1 creates crisp 45-degree sci-fi / Warhammer armor-plate chamfers!
	sb.corner_detail = 1
	sb.content_margin_left = padding
	sb.content_margin_right = padding
	sb.content_margin_top = padding
	sb.content_margin_bottom = padding
	if glow_tint:
		var gc: Color = glow_col if glow_col.a > 0.01 else border
		sb.shadow_color = Color(gc.r * 0.55, gc.g * 0.55, gc.b * 0.55, 0.36)
		sb.shadow_size = 12
		sb.shadow_offset = Vector2.ZERO
	else:
		sb.shadow_color = Color(0, 0, 0, 0.58)
		sb.shadow_size = 9
		sb.shadow_offset = Vector2(0, 4)
	return sb


static func make_btn_style(
	bg: Color,
	border: Color,
	corner_radius: int = 6,
	pad_h: int = 12,
	pad_v: int = 7,
	with_glow: bool = false
) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color = bg
	sb.border_color = border
	sb.set_border_width_all(1)
	sb.border_width_bottom = 3
	sb.border_width_left = 2
	sb.set_corner_radius_all(corner_radius)
	sb.corner_detail = 1
	sb.content_margin_left = pad_h
	sb.content_margin_right = pad_h
	sb.content_margin_top = pad_v
	sb.content_margin_bottom = pad_v
	if with_glow:
		sb.shadow_color = Color(border.r * 0.65, border.g * 0.65, border.b * 0.65, 0.34)
		sb.shadow_size = 8
		sb.shadow_offset = Vector2.ZERO
	return sb


static func apply_accent_button(btn: Button, accent: Color, dark_fill: float = 0.22) -> void:
	var bg_norm := Color(accent.r * dark_fill, accent.g * dark_fill, accent.b * dark_fill, 0.96)
	var bg_hov := Color(accent.r * (dark_fill + 0.14), accent.g * (dark_fill + 0.14), accent.b * (dark_fill + 0.14), 0.99)
	var bg_press := Color(accent.r * 0.44, accent.g * 0.44, accent.b * 0.44, 1.0)
	btn.add_theme_stylebox_override("normal", make_btn_style(bg_norm, accent.darkened(0.08), 6, 12, 7, dark_fill >= 0.28))
	btn.add_theme_stylebox_override("hover", make_btn_style(bg_hov, accent.lightened(0.28), 6, 12, 7, true))
	btn.add_theme_stylebox_override("pressed", make_btn_style(bg_press, accent.lightened(0.38), 6, 12, 7, true))


static func make_chip(text_str: String, color: Color) -> PanelContainer:
	var p := PanelContainer.new()
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(color.r * 0.19, color.g * 0.19, color.b * 0.19, 0.94)
	sb.border_color = color
	sb.set_border_width_all(1)
	sb.border_width_left = 3
	sb.set_corner_radius_all(5)
	sb.corner_detail = 1
	sb.content_margin_left = 8
	sb.content_margin_right = 8
	sb.content_margin_top = 2
	sb.content_margin_bottom = 2
	p.add_theme_stylebox_override("panel", sb)

	var lbl := Label.new()
	lbl.text = text_str
	lbl.add_theme_font_size_override("font_size", 12)
	lbl.add_theme_color_override("font_color", color.lightened(0.30))
	p.add_child(lbl)
	return p
