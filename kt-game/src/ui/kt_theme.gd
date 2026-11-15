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
static var _display_font: Font = null

# --- Type scale (px). Three roles only: display (banner/title), heading (panel titles), body/caption. ---
const FS_DISPLAY := 34
const FS_HEADING := 16
const FS_BODY := 13
const FS_CAPTION := 11


## Display font for Latin/numeric titles (Teko, condensed). Chinese glyphs fall back to the CJK font
## automatically because the FontVariation chains them.
static func get_display_font() -> Font:
	if _display_font != null:
		return _display_font
	var teko_path := "res://assets/fonts/teko_variable.ttf"
	if ResourceLoader.exists(teko_path):
		var fv := FontVariation.new()
		fv.base_font = load(teko_path)
		fv.fallbacks = [get_cjk_font()]
		fv.variation_opentype = {TextServerManager.get_primary_interface().name_to_tag("wght"): 600}
		_display_font = fv
	else:
		_display_font = get_cjk_font()
	return _display_font


## ------------------------------------------------------------------------------------------
## UI KIT (assets/ui_kit): nine-slice panels, buttons, icons generated from docs/UI_ART_BRIEF.md.
## Every accessor falls back to the procedural StyleBoxFlat / vector icon when the file is missing,
## so a fresh checkout without the kit still runs.
## ------------------------------------------------------------------------------------------
const UI_KIT_DIR := "res://assets/ui_kit/"
static var _kit_tex_cache: Dictionary = {}
static var _kit_checked: bool = false
static var _kit_present: bool = false


static func kit_available() -> bool:
	if not _kit_checked:
		_kit_checked = true
		_kit_present = ResourceLoader.exists(UI_KIT_DIR + "panel_primary.png")
	return _kit_present


static func kit_tex(name: String) -> Texture2D:
	if _kit_tex_cache.has(name):
		return _kit_tex_cache[name]
	var p := UI_KIT_DIR + name + ".png"
	var t: Texture2D = KTData.load_tex(p) if ResourceLoader.exists(p) or FileAccess.file_exists(ProjectSettings.globalize_path(p)) else null
	_kit_tex_cache[name] = t
	return t


## A kit icon pre-shrunk to `px` with a Lanczos filter and cached. Buttons draw their icon at native
## size, and a 256px source squeezed to 20px by the GPU looks like a smudge; a proper downscale
## keeps the 10px strokes crisp.
static func kit_tex_scaled(name: String, px: int) -> Texture2D:
	var key := "%s@%d" % [name, px]
	if _kit_tex_cache.has(key):
		return _kit_tex_cache[key]
	var src := kit_tex(name)
	if src == null or src.get_image() == null:
		_kit_tex_cache[key] = null
		return null
	var img := src.get_image().duplicate()
	img.resize(px, px, Image.INTERPOLATE_LANCZOS)
	var t := ImageTexture.create_from_image(img)
	_kit_tex_cache[key] = t
	return t


const TOKEN_DIR := "res://assets/tokens/"


## order: "conceal" | "engage"；ready=true 取亮面（就绪），false 取暗面（已激活）
static func order_token_tex(order: String, ready: bool) -> Texture2D:
	return KTData.load_tex(TOKEN_DIR + "order_%s_%s.png" % ["engage" if order == "engage" else "conceal", "ready" if ready else "activated"])


## kind: "poison" | "injured" | "apl_minus" | "apl_plus"；side 是「带着这个标记的特工」所属方 "A"/"B"
## 颜色约定：A 方=蓝、B 方=红。受创用己方颜色；毒素是敌方放上来的，用敌方颜色。
static func status_token_tex(kind: String, side: String) -> Texture2D:
	match kind:
		"injured":
			return KTData.load_tex(TOKEN_DIR + ("wound_blue.png" if side == "A" else "wound_red.png"))
		"poison":
			# The Plague Marines' own Poison token (only they hand these out)
			return KTData.load_tex(TOKEN_DIR + "pm_poison.png")
		"dominate":
			return KTData.load_tex(TOKEN_DIR + ("status_alert_blue.png" if side == "A" else "status_alert_red.png"))
		"envoy":
			return KTData.load_tex(TOKEN_DIR + ("banner_blue.png" if side == "A" else "banner_red.png"))
		"apl_minus":
			return KTData.load_tex(TOKEN_DIR + "apl_minus.png")
		"apl_plus":
			return KTData.load_tex(TOKEN_DIR + "apl_plus.png")
		_:
			return null


## 返回 Array[String]，元素取自 "injured","poison","apl_minus","apl_plus","dominate"(主宰标记),"envoy"(本转折点的特使)
static func status_kinds_for(op: Dictionary) -> Array[String]:
	var kinds: Array[String] = []
	if int(op["wounds_cur"]) * 2 < int(op["wounds_max"]):
		kinds.append("injured")
	if bool(op.get("poisoned", false)):
		kinds.append("poison")
	var apl_mod: int = int(op.get("apl_mod", 0))
	if apl_mod < 0:
		kinds.append("apl_minus")
	elif apl_mod > 0:
		kinds.append("apl_plus")
	if int(op.get("dominate_tokens", 0)) > 0:
		kinds.append("dominate")
	if bool(op.get("is_envoy", false)):
		kinds.append("envoy")
	return kinds


## Nine-slice StyleBoxTexture from a 512x512 kit panel. The kit art's frame + corner ornaments live
## in the outer 64px; we pre-shrink the whole texture to 256px so those become 32px on screen,
## which fits a 40px-tall HUD strip without the frame swallowing the content.
## `tint` multiplies the texture (faction-coloured variants of the neutral panel); `pad` is the content margin.
static var _kit_panel_tex_cache: Dictionary = {}

static func kit_panel(name: String, pad: int = 10, tint: Color = Color(1, 1, 1, 1), margin: int = 32) -> StyleBox:
	var tex: Texture2D = _kit_panel_tex_cache.get(name, null)
	if tex == null:
		var src := kit_tex(name)
		if src == null or src.get_image() == null:
			return null
		var img := src.get_image().duplicate()
		img.resize(256, 256, Image.INTERPOLATE_LANCZOS)
		tex = ImageTexture.create_from_image(img)
		_kit_panel_tex_cache[name] = tex
	var sb := StyleBoxTexture.new()
	sb.texture = tex
	sb.texture_margin_left = margin
	sb.texture_margin_right = margin
	sb.texture_margin_top = margin
	sb.texture_margin_bottom = margin
	sb.axis_stretch_horizontal = StyleBoxTexture.AXIS_STRETCH_MODE_STRETCH
	sb.axis_stretch_vertical = StyleBoxTexture.AXIS_STRETCH_MODE_STRETCH
	sb.content_margin_left = pad
	sb.content_margin_right = pad
	sb.content_margin_top = pad
	sb.content_margin_bottom = pad
	sb.modulate_color = tint
	return sb


## Three panel tiers so the eye can rank information:
##   "primary"  thick gold frame + glow       : the one thing you act on now (hotbar, combat console)
##   "floating" thin brass, translucent       : context HUD (hero card, tooltips)
##   "tag"      no border, dark pill          : labels, counters
## With the UI kit present these map to panel_primary / panel_floating / panel_tag textures.
## `accent` tints the kit texture slightly toward a faction colour; alpha of the tint is ignored.
static func panel(tier: String, accent: Color = COL_GOLD) -> StyleBox:
	if kit_available():
		var tint := Color(1, 1, 1, 1)
		if not accent.is_equal_approx(COL_GOLD) and not accent.is_equal_approx(COL_BRASS):
			# Push the neutral brass frame toward the faction accent without killing the art
			tint = Color(1, 1, 1, 1).lerp(accent.lightened(0.35), 0.35)
			tint.a = 1.0
		match tier:
			"primary":
				var p := kit_panel("panel_primary", 10, tint)
				if p != null: return p
			"floating":
				var f := kit_panel("panel_floating", 8, tint)
				if f != null: return f
			_:
				var t := kit_panel("panel_tag", 6, Color(1, 1, 1, 1), 20)
				if t != null: return t
	match tier:
		"primary":
			return make_panel_style(Color(0.030, 0.036, 0.052, 0.95), accent, 10, 2, 10, true, accent.lightened(0.2))
		"floating":
			return make_panel_style(Color(0.030, 0.036, 0.052, 0.86), Color(accent.r, accent.g, accent.b, 0.55), 8, 1, 8)
		_:
			var sb := make_panel_style(Color(0.02, 0.025, 0.04, 0.80), Color(0, 0, 0, 0), 6, 0, 6)
			sb.shadow_size = 0
			return sb


## Kit button styleboxes (normal/hover/pressed/danger) or null when the kit is absent.
static func kit_button(state: String, pad_h: int = 12, pad_v: int = 7) -> StyleBox:
	var name := "btn_" + state
	var tex := kit_tex(name)
	if tex == null:
		return null
	# Buttons are small; shrink the 512 source to 128 so the 1px hairline + bevel stay ~1-2px on screen
	var small: Texture2D = _kit_panel_tex_cache.get(name + "@btn", null)
	if small == null:
		var img := tex.get_image().duplicate()
		img.resize(128, 128, Image.INTERPOLATE_LANCZOS)
		small = ImageTexture.create_from_image(img)
		_kit_panel_tex_cache[name + "@btn"] = small
	var sb := StyleBoxTexture.new()
	sb.texture = small
	for side in ["left", "right", "top", "bottom"]:
		sb.set("texture_margin_" + side, 12)
	sb.content_margin_left = pad_h
	sb.content_margin_right = pad_h
	sb.content_margin_top = pad_v
	sb.content_margin_bottom = pad_v
	return sb


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

	# Chamfered Grimdark Tactical Buttons (kit textures when present, procedural otherwise)
	var b_norm: StyleBox = kit_button("normal")
	var b_hov: StyleBox = kit_button("hover")
	var b_prs: StyleBox = kit_button("pressed")
	th.set_stylebox("normal", "Button", b_norm if b_norm != null else make_btn_style(Color(0.11, 0.135, 0.185, 0.95), COL_BRASS_DIM))
	th.set_stylebox("hover", "Button", b_hov if b_hov != null else make_btn_style(Color(0.18, 0.22, 0.29, 0.98), COL_GOLD_BRIGHT, 6, 12, 7, true))
	th.set_stylebox("pressed", "Button", b_prs if b_prs != null else make_btn_style(Color(0.28, 0.22, 0.11, 1.0), COL_GOLD_BRIGHT, 6, 12, 7, true))
	var b_dis: StyleBox = kit_button("normal")
	if b_dis != null:
		(b_dis as StyleBoxTexture).modulate_color = Color(0.55, 0.55, 0.58, 0.7)
	th.set_stylebox("disabled", "Button", b_dis if b_dis != null else make_btn_style(Color(0.065, 0.075, 0.095, 0.62), Color(0.22, 0.25, 0.30, 0.42)))
	th.set_stylebox("focus", "Button", b_hov if b_hov != null else make_btn_style(Color(0.15, 0.185, 0.25, 0.98), COL_BRASS))
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
