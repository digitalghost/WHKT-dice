class_name KTTableProps3D
extends Node3D

## Physical tabletop bookkeeping that replaces 2D HUD numbers (route B: "playing a board game
## inside a video game"). Everything here sits on the table apron around the 30"x22" killzone:
##
##   - CP chip stacks (one brass stack per side; chips added / removed with a little hop)
##   - VP score track: a brass rail along the near table edge with two faction markers
##   - Turning-point tally: a small wooden block showing TP 1..4
##   - Datacard mats: a slot per side where the active operative's card lies face-up
##
## Values are pushed in by MainGame via sync(); the node tweens toward them so changes read
## as someone moving a token, not as a number flipping.

const CHIP_R: float = 0.42
const CHIP_H: float = 0.09
const VP_STEP: float = 0.9   # inches between VP notches (13 notches fit the 22" side)

var _side_roots: Dictionary = {}   # "A"/"B" -> Node3D
var _chip_nodes: Dictionary = {}   # side -> Array[MeshInstance3D]
var _vp_markers: Dictionary = {}   # side -> Node3D
var _vp_target_x: Dictionary = {}  # side -> float
var _tp_lbl: Label3D
var _tp_face_mat: StandardMaterial3D = null
var _tp_sheet: Texture2D = null
var _tp_face_cells: Array[Texture2D] = []
var _card_mats: Dictionary = {}    # side -> {root, sprite, name_lbl}
var _mat_chip_a: StandardMaterial3D
var _mat_chip_b: StandardMaterial3D
var _mat_brass: StandardMaterial3D
var _mat_wood: StandardMaterial3D

var _board_w: float = 30.0
var _board_h: float = 22.0
var _font: Font = null


func build(board_w: float, board_h: float) -> void:
	_board_w = board_w
	_board_h = board_h
	_font = KTTheme.get_cjk_font()
	for c in get_children():
		c.queue_free()
	_chip_nodes.clear()
	_vp_markers.clear()
	_card_mats.clear()

	_mat_chip_a = KTModels3D.make_pbr(Color(0.20, 0.42, 0.80), 0.30, 0.35)
	_mat_chip_b = KTModels3D.make_pbr(Color(0.42, 0.60, 0.18), 0.30, 0.35)
	_mat_brass = KTModels3D.make_pbr(Color(0.74, 0.56, 0.26), 0.86, 0.32)
	_mat_wood = KTModels3D.make_textured_pbr("rust", Color(0.55, 0.38, 0.22), 0.8, 1.0, 0.0)
	# UI kit overrides: real wood / brass textures and printed chip tops
	var wood_tex := KTTheme.kit_tex("tex_wood_dark")
	if wood_tex != null:
		_mat_wood = StandardMaterial3D.new()
		_mat_wood.albedo_texture = wood_tex
		_mat_wood.roughness = 0.7
		_mat_wood.uv1_triplanar = true
		_mat_wood.uv1_scale = Vector3(0.6, 0.6, 0.6)
	var brass_tex := KTTheme.kit_tex("tex_brass_brushed")
	if brass_tex != null:
		_mat_brass.albedo_texture = brass_tex
		_mat_brass.albedo_color = Color(0.95, 0.90, 0.80)
		_mat_brass.uv1_triplanar = true
		_mat_brass.uv1_scale = Vector3(0.6, 0.6, 0.6)

	var hw := board_w * 0.5
	var hh := board_h * 0.5
	# Side A (Angels, deploys south/+Z) keeps its bookkeeping on the near apron, left of the board;
	# side B (Plague, deploys north/-Z) mirrors it on the far apron, right of the board, rotated 180°
	# so its card reads correctly for a hotseat player sitting opposite.
	# Default camera sits SW of the board looking NE, so the readable apron is the RIGHT (east, +X)
	# edge. Props line up along it from near (south, +Z) to far:  [TP block]  [VP rail]  [A chips + A card].
	# Side B mirrors onto the LEFT (west) edge, rotated 180° for a hotseat opponent.
	# The side root is yawed -90° so its local +X runs north (-Z) along the apron and local +Z points
	# east (off the table); the card mat sits at local -X, i.e. nearer the camera than the chips.
	# Scaled up ~1.5x from "real" token size: at the default 60" camera distance the apron is small
	# on screen, so props need to be over-sized to read as objects rather than specks.
	# Order along the east apron from far (north) to near (south): [A card mat] [A chips] [VP rail] [TP].
	# Pulled ~3" south of the earlier layout so the card mat clears the top HUD bar at the default camera.
	_build_side("A", Vector3(hw + 5.4, -0.24, -1.8), -PI * 0.5)
	_build_side("B", Vector3(-hw - 5.4, -0.24, 1.8), PI * 0.5)
	_build_vp_track(Vector3(hw + 5.4, -0.24, 7.4), -PI * 0.5)
	_build_tp_block(Vector3(hw + 5.4, -0.24, hh + 2.4))
	for c in get_children():
		(c as Node3D).scale = Vector3.ONE * 1.5


func _build_side(side: String, origin: Vector3, yaw: float) -> void:
	var root := Node3D.new()
	root.position = origin
	root.rotation.y = yaw
	add_child(root)
	_side_roots[side] = root

	# Felt coaster for the chip stack
	var coaster_mat := KTModels3D.make_pbr(Color(0.10, 0.08, 0.075), 0.05, 0.95)
	KTModels3D.add_cyl(root, Vector3(0.0, 0.02, 0.0), 1.15, 1.15, 0.04, coaster_mat, Vector3.ZERO, 32)
	KTModels3D.add_torus(root, Vector3(0.0, 0.04, 0.0), 1.05, 1.15, _mat_brass)
	_chip_nodes[side] = []

	# "CP" engraved label on the coaster
	var l := Label3D.new()
	l.text = "CP"
	l.font = _font
	l.font_size = 64
	l.pixel_size = 0.006
	l.modulate = Color(0.74, 0.56, 0.26, 0.85)
	l.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
	l.position = Vector3(0.0, 0.05, 0.95)
	l.alpha_cut = Label3D.ALPHA_CUT_DISCARD
	root.add_child(l)

	# Datacard mat to the right of the chips along the apron, long edge parallel to the board edge,
	# text facing the player (no extra yaw: the side root already faces the owner).
	var mat_root := Node3D.new()
	mat_root.position = Vector3(-4.1, 0.0, 0.0)
	root.add_child(mat_root)
	var mat_tex := KTTheme.kit_tex("mat_datacard")
	if mat_tex != null:
		# Printed leather mat (kit art, 1024x768 -> 4.8" x 3.6")
		var mmat := StandardMaterial3D.new()
		mmat.albedo_texture = mat_tex
		mmat.roughness = 0.85
		mmat.metallic = 0.05
		var mq := QuadMesh.new()
		mq.size = Vector2(4.8, 3.6)
		var mmi := MeshInstance3D.new()
		mmi.mesh = mq
		mmi.material_override = mmat
		mmi.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
		mmi.position = Vector3(0.0, 0.04, 0.0)
		mat_root.add_child(mmi)
		KTModels3D.add_box(mat_root, Vector3(0.0, 0.02, 0.0), Vector3(4.8, 0.035, 3.6), KTModels3D.make_pbr(Color(0.10, 0.08, 0.07), 0.05, 0.9))
	else:
		var leather := KTModels3D.make_textured_pbr("rust", Color(0.30, 0.24, 0.20), 1.2, 1.0, 0.0)
		KTModels3D.add_box(mat_root, Vector3(0.0, 0.02, 0.0), Vector3(4.8, 0.04, 3.2), leather)
		KTModels3D.add_box(mat_root, Vector3(0.0, 0.035, 0.0), Vector3(4.5, 0.012, 2.9), _mat_brass)
		KTModels3D.add_box(mat_root, Vector3(0.0, 0.045, 0.0), Vector3(4.4, 0.012, 2.8), leather)
		for sx in [-1.0, 1.0]:
			for sz in [-1.0, 1.0]:
				KTModels3D.add_box(mat_root, Vector3(sx * 2.15, 0.05, sz * 1.4), Vector3(0.28, 0.02, 0.28), _mat_brass)

	var card := Sprite3D.new()
	# Datacards are 947x545 px -> ~4.3" x 2.5" on the table, inside the 4.6 x 3.1 mat.
	card.pixel_size = 0.0045
	card.modulate = Color(1.0, 1.0, 1.0, 1.0)
	card.shaded = false
	card.render_priority = 1
	card.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
	card.position = Vector3(0.0, 0.07, -0.12)
	card.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	card.alpha_cut = SpriteBase3D.ALPHA_CUT_DISCARD
	card.visible = false
	mat_root.add_child(card)

	# Empty-mat hint when no operative is active
	var empty_l := Label3D.new()
	empty_l.text = "数据卡"
	empty_l.font = _font
	empty_l.font_size = 48
	empty_l.pixel_size = 0.005
	empty_l.modulate = Color(0.74, 0.56, 0.26, 0.45)
	empty_l.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
	empty_l.position = Vector3(0.0, 0.06, 0.0)
	empty_l.alpha_cut = Label3D.ALPHA_CUT_DISCARD
	mat_root.add_child(empty_l)

	var name_l := Label3D.new()
	name_l.font = _font
	name_l.font_size = 56
	name_l.pixel_size = 0.0045
	name_l.outline_size = 10
	name_l.modulate = Color(0.96, 0.92, 0.80)
	name_l.outline_modulate = Color(0.04, 0.04, 0.05)
	name_l.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
	name_l.position = Vector3(0.0, 0.08, 1.85)
	name_l.alpha_cut = Label3D.ALPHA_CUT_DISCARD
	mat_root.add_child(name_l)
	_card_mats[side] = {"root": mat_root, "sprite": card, "name": name_l, "empty": empty_l}


func _build_vp_track(origin: Vector3, yaw: float = 0.0) -> void:
	var root := Node3D.new()
	root.position = origin
	root.rotation.y = yaw
	add_child(root)
	# Rail: 0..12 VP across 13 notches
	var rail_len := 12.0 * VP_STEP
	KTModels3D.add_box(root, Vector3(0.0, 0.03, 0.0), Vector3(rail_len + 1.2, 0.06, 0.9), _mat_wood)
	KTModels3D.add_box(root, Vector3(0.0, 0.065, 0.0), Vector3(rail_len + 0.6, 0.02, 0.10), _mat_brass)
	for i in range(13):
		var x := -rail_len * 0.5 + float(i) * VP_STEP
		KTModels3D.add_cyl(root, Vector3(x, 0.07, 0.0), 0.07, 0.07, 0.02, _mat_brass, Vector3.ZERO, 10)
		if i % 3 == 0:
			var nl := Label3D.new()
			nl.text = str(i)
			nl.font = _font
			nl.font_size = 96
			nl.pixel_size = 0.0045
			nl.outline_size = 10
			nl.outline_modulate = Color(0.05, 0.04, 0.03)
			nl.modulate = Color(0.74, 0.56, 0.26)
			nl.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
			nl.position = Vector3(x, 0.08, 0.32)
			nl.alpha_cut = Label3D.ALPHA_CUT_DISCARD
			root.add_child(nl)
	var vl := Label3D.new()
	vl.text = "VP"
	vl.font = _font
	vl.font_size = 60
	vl.pixel_size = 0.005
	vl.modulate = Color(0.74, 0.56, 0.26)
	vl.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
	vl.position = Vector3(-rail_len * 0.5 - 0.95, 0.08, 0.0)
	vl.alpha_cut = Label3D.ALPHA_CUT_DISCARD
	root.add_child(vl)

	for side in ["A", "B"]:
		var m := Node3D.new()
		m.position = Vector3(-rail_len * 0.5, 0.08, -0.22 if side == "A" else 0.22)
		root.add_child(m)
		var mm := _mat_chip_a if side == "A" else _mat_chip_b
		KTModels3D.add_cyl(m, Vector3(0.0, 0.08, 0.0), 0.26, 0.22, 0.16, mm, Vector3.ZERO, 20)
		KTModels3D.add_torus(m, Vector3(0.0, 0.16, 0.0), 0.12, 0.20, _mat_brass)
		_vp_markers[side] = m
		_vp_target_x[side] = m.position.x
	_vp_rail_len = rail_len


var _vp_rail_len: float = 12.0 * VP_STEP


func _build_tp_block(origin: Vector3) -> void:
	var root := Node3D.new()
	root.position = origin
	add_child(root)
	KTModels3D.add_box(root, Vector3(0.0, 0.45, 0.0), Vector3(1.6, 0.9, 1.6), _mat_wood)
	KTModels3D.add_box(root, Vector3(0.0, 0.905, 0.0), Vector3(1.3, 0.02, 1.3), _mat_brass)
	# Kit: engraved Roman numeral face plate (block_tp_faces.png, 4 cells). Shown instead of Label3D.
	# 3D materials ignore AtlasTexture regions, so the four cells are pre-cropped into ImageTextures.
	var tp_sheet := KTTheme.kit_tex("block_tp_faces")
	if tp_sheet != null and tp_sheet.get_image() != null:
		var src := tp_sheet.get_image()
		var cell := src.get_height()
		_tp_face_cells.clear()
		for i in range(4):
			var sub := src.get_region(Rect2i(cell * i, 0, cell, cell))
			sub.generate_mipmaps()
			_tp_face_cells.append(ImageTexture.create_from_image(sub))
		_tp_face_mat = StandardMaterial3D.new()
		_tp_face_mat.albedo_texture = _tp_face_cells[0]
		_tp_face_mat.roughness = 0.6
		var fq := QuadMesh.new()
		fq.size = Vector2(1.3, 1.3)
		var fmi := MeshInstance3D.new()
		fmi.mesh = fq
		fmi.material_override = _tp_face_mat
		fmi.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
		fmi.position = Vector3(0.0, 0.92, 0.0)
		root.add_child(fmi)
		_tp_sheet = tp_sheet
	_tp_lbl = Label3D.new()
	_tp_lbl.text = "TP 1"
	_tp_lbl.font = _font
	_tp_lbl.font_size = 96
	_tp_lbl.pixel_size = 0.0055
	_tp_lbl.outline_size = 10
	_tp_lbl.modulate = Color(0.97, 0.90, 0.72)
	_tp_lbl.outline_modulate = Color(0.1, 0.07, 0.03)
	_tp_lbl.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
	_tp_lbl.position = Vector3(0.0, 0.93, 0.0)
	_tp_lbl.alpha_cut = Label3D.ALPHA_CUT_DISCARD
	root.add_child(_tp_lbl)


## Push game state. `active_cards`: side -> {tex: Texture2D, name: String} or empty.
func sync(cp: Dictionary, vp: Dictionary, tp: int, active_cards: Dictionary) -> void:
	for side in ["A", "B"]:
		_sync_chips(side, int(cp.get(side, 0)))
		var v := clampi(int(vp.get(side, 0)), 0, 12)
		_vp_target_x[side] = -_vp_rail_len * 0.5 + float(v) * VP_STEP
		var cm: Dictionary = _card_mats.get(side, {})
		if not cm.is_empty():
			var info: Dictionary = active_cards.get(side, {})
			var spr: Sprite3D = cm["sprite"]
			var nl: Label3D = cm["name"]
			var el: Label3D = cm["empty"]
			if info.is_empty():
				spr.visible = false
				nl.text = ""
				el.visible = true
			else:
				var new_tex: Texture2D = info.get("tex", null)
				if spr.texture != new_tex and new_tex != null:
					# Card slides in from above and settles: "placing the card on the mat"
					spr.position.y = 0.9
					var tw := spr.create_tween()
					tw.tween_property(spr, "position:y", 0.07, 0.28).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_CUBIC)
					KTSfx.play("card_lift", -8.0, 0.05)
				spr.texture = new_tex
				spr.visible = spr.texture != null
				nl.text = str(info.get("name", ""))
				el.visible = false
	if _tp_lbl != null:
		_tp_lbl.text = "TP %d" % tp
		_tp_lbl.visible = _tp_sheet == null
	if _tp_face_mat != null and not _tp_face_cells.is_empty():
		_tp_face_mat.albedo_texture = _tp_face_cells[clampi(tp, 1, 4) - 1]


func _sync_chips(side: String, count: int) -> void:
	var arr: Array = _chip_nodes[side]
	var root: Node3D = _side_roots[side]
	var mm := _mat_chip_a if side == "A" else _mat_chip_b
	var chip_tex := KTTheme.kit_tex("chip_cp_imperial" if side == "A" else "chip_cp_nurgle")
	while arr.size() < count:
		var i := arr.size()
		var chip := KTModels3D.add_cyl(root, Vector3(0.0, 0.06 + float(i) * CHIP_H + 1.4, 0.0), CHIP_R, CHIP_R, CHIP_H * 0.9, mm, Vector3(0.0, float(i) * 23.0, 0.0), 24)
		if chip_tex != null:
			# Printed chip face on top (kit art), body stays enamel-coloured
			var face_mat := StandardMaterial3D.new()
			face_mat.albedo_texture = chip_tex
			face_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
			face_mat.alpha_scissor_threshold = 0.5
			face_mat.roughness = 0.35
			face_mat.metallic = 0.3
			var fq := QuadMesh.new()
			fq.size = Vector2(CHIP_R * 2.0, CHIP_R * 2.0)
			var fmi := MeshInstance3D.new()
			fmi.mesh = fq
			fmi.material_override = face_mat
			fmi.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
			fmi.position = Vector3(0.0, CHIP_H * 0.46, 0.0)
			chip.add_child(fmi)
		else:
			KTModels3D.add_torus(chip, Vector3(0.0, CHIP_H * 0.46, 0.0), CHIP_R * 0.62, CHIP_R * 0.74, _mat_brass)
		chip.set_meta("target_y", 0.06 + float(i) * CHIP_H)
		arr.append(chip)
	while arr.size() > count:
		var chip: MeshInstance3D = arr.pop_back()
		# spend: chip hops up and fades
		var tw := chip.create_tween()
		tw.tween_property(chip, "position:y", chip.position.y + 1.2, 0.22).set_ease(Tween.EASE_OUT)
		tw.tween_property(chip, "scale", Vector3.ONE * 0.01, 0.12)
		tw.tween_callback(chip.queue_free)


func _process(delta: float) -> void:
	for side in ["A", "B"]:
		for chip in _chip_nodes.get(side, []):
			if is_instance_valid(chip) and chip.has_meta("target_y"):
				var ty: float = float(chip.get_meta("target_y"))
				if absf(chip.position.y - ty) > 0.002:
					chip.position.y = lerpf(chip.position.y, ty, clampf(delta * 9.0, 0.0, 1.0))
		var m: Node3D = _vp_markers.get(side, null)
		if m != null:
			var tx: float = float(_vp_target_x.get(side, m.position.x))
			if absf(m.position.x - tx) > 0.003:
				m.position.x = lerpf(m.position.x, tx, clampf(delta * 5.0, 0.0, 1.0))
				m.position.y = 0.08 + 0.35 * sin(clampf(absf(m.position.x - tx) / VP_STEP, 0.0, 1.0) * PI)


func get_side_focus(side: String) -> Vector3:
	var r: Node3D = _side_roots.get(side, null)
	return r.global_position if r != null else Vector3.ZERO
