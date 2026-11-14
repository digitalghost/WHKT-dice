class_name CardLightbox
extends Control

## High-resolution official Chinese card viewer modal (Operative Datacards, Ploys, Faction Rules).

var _title_lbl: Label
var _tabs_box: HBoxContainer
var _card_tex_rect: TextureRect
var _body_lbl: RichTextLabel
var _current_cards: Array = []
var _current_idx: int = 0


func _init() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	visible = false

	var backdrop := ColorRect.new()
	backdrop.color = Color(0.02, 0.025, 0.035, 0.86)
	backdrop.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	backdrop.gui_input.connect(func(ev: InputEvent):
		if ev is InputEventMouseButton and ev.pressed and ev.button_index == MOUSE_BUTTON_LEFT:
			hide()
	)
	add_child(backdrop)

	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(center)

	var dialog := PanelContainer.new()
	dialog.custom_minimum_size = Vector2(1060, 740)
	dialog.add_theme_stylebox_override("panel", KTTheme.make_panel_style(KTTheme.COL_PANEL_ELEV, KTTheme.COL_BRASS, 10, 2, 18))
	center.add_child(dialog)

	var vbox := VBoxContainer.new()
	vbox.add_theme_constant_override("separation", 12)
	dialog.add_child(vbox)

	# Header
	var header := HBoxContainer.new()
	vbox.add_child(header)

	_title_lbl = Label.new()
	_title_lbl.text = "官方中文原卡查阅"
	_title_lbl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_title_lbl.add_theme_font_size_override("font_size", 20)
	_title_lbl.add_theme_color_override("font_color", KTTheme.COL_GOLD_BRIGHT)
	header.add_child(_title_lbl)

	var close_btn := Button.new()
	close_btn.text = "✕ 关闭 (ESC)"
	KTTheme.apply_accent_button(close_btn, KTTheme.COL_CRIMSON)
	close_btn.pressed.connect(func(): hide())
	header.add_child(close_btn)

	# Card selector tabs
	var scroll_tabs := ScrollContainer.new()
	scroll_tabs.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll_tabs.custom_minimum_size = Vector2(0, 40)
	vbox.add_child(scroll_tabs)

	_tabs_box = HBoxContainer.new()
	_tabs_box.add_theme_constant_override("separation", 8)
	scroll_tabs.add_child(_tabs_box)

	# Card display area
	_card_tex_rect = TextureRect.new()
	_card_tex_rect.custom_minimum_size = Vector2(1000, 560)
	_card_tex_rect.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_card_tex_rect.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_card_tex_rect.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	vbox.add_child(_card_tex_rect)

	_body_lbl = RichTextLabel.new()
	_body_lbl.bbcode_enabled = true
	_body_lbl.custom_minimum_size = Vector2(0, 70)
	_body_lbl.visible = false
	vbox.add_child(_body_lbl)


func show_operative_cards(op: Dictionary) -> void:
	var items: Array = []
	var cards: Array = op.get("cards", [])
	for i in range(cards.size()):
		var cpath := str(cards[i])
		var label_str := "人物数据卡" if i == 0 else "专属能力卡 #%d" % i
		items.append({"title": "%s · %s" % [str(op["name"]), label_str], "card": cpath, "body": ""})
	show_card_items("%s — 官方中文数据卡" % str(op["name"]), items)


func show_team_rulebook(team_id: String) -> void:
	var team := KTData.get_team(team_id)
	var items: Array = []
	for entry in team.get("rule_entries", []):
		var e_cards: Array = entry.get("cards", [])
		if e_cards.is_empty():
			items.append({
				"title": "[%s] %s" % [str(entry.get("category", "")), str(entry.get("title", ""))],
				"card": "",
				"body": str(entry.get("body", ""))
			})
		else:
			for idx in range(e_cards.size()):
				var suffix := (" (%d/%d)" % [idx + 1, e_cards.size()]) if e_cards.size() > 1 else ""
				items.append({
					"title": "%s%s" % [str(entry.get("title", "")), suffix],
					"card": str(e_cards[idx]),
					"body": str(entry.get("body", ""))
				})
	show_card_items("%s — 完整规则与计谋原卡库" % str(team.get("name", team_id)), items)


func show_card_items(header_title: String, items: Array) -> void:
	_title_lbl.text = "📖 " + header_title
	_current_cards = items
	_current_idx = 0
	for child in _tabs_box.get_children():
		child.queue_free()

	for i in range(items.size()):
		var b := Button.new()
		b.text = str(items[i]["title"])
		var idx := i
		b.pressed.connect(func(): _select_item(idx))
		_tabs_box.add_child(b)

	if not items.is_empty():
		_select_item(0)
	visible = true


func _select_item(idx: int) -> void:
	if idx < 0 or idx >= _current_cards.size():
		return
	_current_idx = idx
	var item: Dictionary = _current_cards[idx]
	var cpath := str(item.get("card", ""))
	if not cpath.is_empty():
		_card_tex_rect.texture = KTData.load_tex(cpath)
		_card_tex_rect.visible = true
		_body_lbl.visible = false
	else:
		_card_tex_rect.visible = false
		_body_lbl.visible = true
		_body_lbl.text = str(item.get("body", ""))

	for i in range(_tabs_box.get_child_count()):
		var btn := _tabs_box.get_child(i) as Button
		if btn:
			if i == idx:
				KTTheme.apply_accent_button(btn, KTTheme.COL_BRASS, 0.35)
			else:
				KTTheme.apply_accent_button(btn, KTTheme.COL_MUTED, 0.14)
