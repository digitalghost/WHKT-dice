class_name KTDice3D
extends Node3D

## Physical 3D Tabletop Velvet & Brass Dice Arena + Tumbling 6-Face Warhammer D6 Engine:
## - Sculpted Gothic Brass & Dual-Velvet Dice Box resting physically on the wargaming table
## - Procedural 256x256 mipmapped 6-face Warhammer D6 cubes (chamfered 3D edges, pips 1-5, 8-point Crit Star on 6!)
## - Multi-bounce 3D parabolic throw trajectories, quaternion tumble-to-face-up physics, bounce clatter SFX,
##   direct 3D raycast hover/click selection for Re-rolls, and Melee Strike/Parry 3D die clash animations!

const TRAY_W: float = 14.6
const TRAY_D: float = 8.8
const FELT_Y: float = 0.16
const DIE_SIZE: float = 1.36
const DIE_REST_Y: float = FELT_Y + DIE_SIZE * 0.502

var _tray_felt_tex: ImageTexture = null
var _face_mats: Dictionary = {}
var _body_mats: Dictionary = {}
var _ring_mesh: TorusMesh = null

var interactive_picking_enabled: bool = false
var left_bay_label: String = ""
var right_bay_label: String = ""
var active_mode: String = "idle" # "idle" | "initiative" | "shooting" | "melee"

var _divider_mi: MeshInstance3D = null
var _tray_light: OmniLight3D = null
var _dice_root: Node3D = null
var _fx_root: Node3D = null

var _target_tray_pos: Vector3 = Vector3(9.2, -0.24, 15.6)
var _target_tray_yaw: float = 0.0

# Active dice entries:
# { "node": Node3D, "cube": Node3D, "ring": MeshInstance3D, "ring_mat": StandardMaterial3D,
#   "pool": String, "idx": int, "val": int, "kind": String, "rerolled": bool, "selected": bool,
#   "interactive": bool, "start_local": Vector3, "end_local": Vector3, "start_q": Quaternion,
#   "end_q": Quaternion, "spin_axis": Vector3, "delay": float, "t": float, "dur": float,
#   "bounces_played": int, "settled": bool, "hover_t": float }
var _dice_entries: Array[Dictionary] = []
var _flying_fx_dice: Array[Dictionary] = []
var _hovered_key: String = ""
var _pulse_time: float = 0.0
var _rng: RandomNumberGenerator = RandomNumberGenerator.new()


func _ready() -> void:
	_rng.randomize()
	_build_sculpted_tray()
	_dice_root = Node3D.new()
	add_child(_dice_root)
	_fx_root = Node3D.new()
	add_child(_fx_root)
	place_tray_idle(true)


# ==============================================================================
# 1. SCULPTED 3D GOTHIC BRASS & VELVET TABLETOP DICE BOX
# ==============================================================================

func _build_sculpted_tray() -> void:
	var mat_iron := KTModels3D.make_pbr(Color(0.055, 0.062, 0.082), 0.72, 0.44)
	var mat_brass := KTModels3D.make_pbr(Color(0.82, 0.64, 0.28), 0.86, 0.28)
	var mat_gold_stud := KTModels3D.make_pbr(Color(0.98, 0.80, 0.34), 0.90, 0.22, Color(0.45, 0.32, 0.08), 0.35)

	# Solid Obsidian Base Slab (y = 0.00 .. 0.14)
	KTModels3D.add_box(self, Vector3(0.0, 0.07, 0.0), Vector3(TRAY_W, 0.14, TRAY_D), mat_iron)

	# Recessed Dual-Bay Velvet Felt Surface (y = FELT_Y = 0.16, strictly above 0.14 -> zero Z-fighting!)
	var mat_felt := StandardMaterial3D.new()
	mat_felt.albedo_texture = _get_tray_felt_tex()
	mat_felt.roughness = 0.92
	mat_felt.metallic = 0.04
	mat_felt.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	var pm := PlaneMesh.new()
	pm.size = Vector2(TRAY_W - 0.88, TRAY_D - 0.88)
	var felt_mi := MeshInstance3D.new()
	felt_mi.mesh = pm
	felt_mi.material_override = mat_felt
	felt_mi.position = Vector3(0.0, FELT_Y, 0.0)
	add_child(felt_mi)

	# Four Bevelled Gothic Brass & Dark-Iron Walls (height = 0.64)
	var wall_t: float = 0.48
	var wall_h: float = 0.64
	# North & South long walls
	for sz in [-1.0, 1.0]:
		var wz: float = sz * (TRAY_D * 0.5 - wall_t * 0.5)
		KTModels3D.add_box(self, Vector3(0.0, wall_h * 0.5, wz), Vector3(TRAY_W, wall_h, wall_t), mat_iron)
		KTModels3D.add_box(self, Vector3(0.0, wall_h + 0.04, wz), Vector3(TRAY_W + 0.10, 0.09, wall_t + 0.06), mat_brass)
	# West & East side walls
	for sx in [-1.0, 1.0]:
		var wx: float = sx * (TRAY_W * 0.5 - wall_t * 0.5)
		KTModels3D.add_box(self, Vector3(wx, wall_h * 0.5, 0.0), Vector3(wall_t, wall_h, TRAY_D - wall_t * 1.9), mat_iron)
		KTModels3D.add_box(self, Vector3(wx, wall_h + 0.04, 0.0), Vector3(wall_t + 0.06, 0.09, TRAY_D - wall_t * 1.8), mat_brass)

	# 4 Corner Imperial Brass Studs
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			var cp := Vector3(sx * (TRAY_W * 0.5 - 0.24), wall_h + 0.11, sz * (TRAY_D * 0.5 - 0.24))
			KTModels3D.add_cyl(self, cp, 0.22, 0.28, 0.14, mat_gold_stud, Vector3.ZERO, 8)

	# Center Brass Divider Bar (separates Left & Right Bays)
	_divider_mi = KTModels3D.add_box(
		self,
		Vector3(0.0, FELT_Y + 0.16, 0.0),
		Vector3(0.24, 0.30, TRAY_D - 1.05),
		mat_brass
	)

	# Dedicated Overhead Warm Tabletop Specular Light so bevelled 3D dice pop vividly!
	_tray_light = OmniLight3D.new()
	_tray_light.position = Vector3(0.0, 6.2, 1.4)
	_tray_light.light_color = Color(1.0, 0.95, 0.84)
	_tray_light.light_energy = 1.15
	_tray_light.omni_range = 18.0
	add_child(_tray_light)


func _get_tray_felt_tex() -> ImageTexture:
	if _tray_felt_tex != null:
		return _tray_felt_tex
	var w: int = 512
	var h: int = 320
	var img := Image.create(w, h, true, Image.FORMAT_RGBA8)

	for y in range(h):
		var v: float = float(y) / float(h)
		var dv: float = absf(v - 0.5) * 2.0
		for x in range(w):
			var u: float = float(x) / float(w)
			var du: float = absf(u - 0.5) * 2.0
			var vignette: float = 1.0 - clampf(pow(maxf(du, dv), 2.2) * 0.42, 0.0, 0.45)
			var weave: float = (float(((x * 13 + y * 29) ^ (x * 7)) & 15) / 15.0 - 0.5) * 0.018
			var base_c: Color
			if x < w / 2:
				# Left Bay: Deep Imperial Crimson-Burgundy Velvet
				base_c = Color(0.24 * vignette + weave, 0.055 * vignette + weave * 0.5, 0.085 * vignette + weave * 0.6, 1.0)
			else:
				# Right Bay: Deep Auspex Midnight-Emerald / Slate Velvet
				base_c = Color(0.055 * vignette + weave * 0.5, 0.145 * vignette + weave, 0.185 * vignette + weave, 1.0)
			img.set_pixel(x, y, base_c)

	# Subtle inlaid gold filigree inner border
	var gold_line := Color(0.78, 0.62, 0.28, 0.65)
	img.fill_rect(Rect2i(14, 14, w - 28, 2), gold_line)
	img.fill_rect(Rect2i(14, h - 16, w - 28, 2), gold_line)
	img.fill_rect(Rect2i(14, 14, 2, h - 28), gold_line)
	img.fill_rect(Rect2i(w - 16, 14, 2, h - 28), gold_line)

	img.generate_mipmaps()
	_tray_felt_tex = ImageTexture.create_from_image(img)
	return _tray_felt_tex


# ==============================================================================
# 2. PROCEDURAL 6-FACE WARHAMMER D6 MESH & TEXTURE FACTORY
# ==============================================================================

func _get_body_mat(theme_id: String) -> StandardMaterial3D:
	if _body_mats.has(theme_id):
		return _body_mats[theme_id]
	var col := Color(0.76, 0.14, 0.16)
	match theme_id:
		"imperial":
			col = Color(0.14, 0.36, 0.78)
		"nurgle":
			col = Color(0.32, 0.46, 0.16)
		"bone":
			col = Color(0.86, 0.80, 0.68)
		"gold":
			col = Color(0.88, 0.66, 0.20)
		_:
			col = Color(0.72, 0.13, 0.16)
	var m := KTModels3D.make_pbr(col, 0.24, 0.26)
	_body_mats[theme_id] = m
	return m


func _get_face_mat(theme_id: String, face_val: int) -> StandardMaterial3D:
	var key: String = "%s_%d" % [theme_id, face_val]
	if _face_mats.has(key):
		return _face_mats[key]

	var sz: int = 192
	var img := Image.create(sz, sz, true, Image.FORMAT_RGBA8)

	var bg := Color(0.62, 0.10, 0.13, 1.0)
	var rim := Color(0.92, 0.74, 0.34, 1.0)
	var pip := Color(0.99, 0.96, 0.86, 1.0)
	match theme_id:
		"imperial":
			bg = Color(0.11, 0.28, 0.66, 1.0)
			rim = Color(0.88, 0.74, 0.36, 1.0)
			pip = Color(0.98, 0.97, 0.92, 1.0)
		"nurgle":
			bg = Color(0.24, 0.36, 0.12, 1.0)
			rim = Color(0.78, 0.66, 0.28, 1.0)
			pip = Color(0.86, 0.98, 0.56, 1.0)
		"bone":
			bg = Color(0.88, 0.83, 0.71, 1.0)
			rim = Color(0.58, 0.44, 0.22, 1.0)
			pip = Color(0.14, 0.12, 0.10, 1.0)
		"gold":
			bg = Color(0.32, 0.22, 0.05, 1.0)
			rim = Color(1.0, 0.86, 0.38, 1.0)
			pip = Color(1.0, 0.95, 0.68, 1.0)

	if face_val == 6:
		# Face 6 Crit always gets radiant golden Warhammer crest emphasis!
		pip = Color(1.0, 0.92, 0.46, 1.0)
		rim = Color(1.0, 0.86, 0.36, 1.0)

	img.fill(bg)

	# Recessed dish shading + crisp metallic inner bevel frame
	img.fill_rect(Rect2i(6, 6, sz - 12, 5), rim)
	img.fill_rect(Rect2i(6, sz - 11, sz - 12, 5), rim)
	img.fill_rect(Rect2i(6, 6, 5, sz - 12), rim)
	img.fill_rect(Rect2i(sz - 11, 6, 5, sz - 12), rim)

	var c := Vector2(float(sz) * 0.5, float(sz) * 0.5)
	if face_val == 6:
		# Iconic Warhammer 8-Pointed Crit Starburst + Central Imperial Ring on Face 6!
		for y in range(22, sz - 22):
			for x in range(22, sz - 22):
				var p := Vector2(float(x) - c.x, float(y) - c.y)
				var r: float = p.length()
				if r < 11.0:
					img.set_pixel(x, y, bg if r < 6.0 else pip)
				elif r < 64.0:
					var ang: float = atan2(p.y, p.x)
					var s8: float = absf(cos(ang * 4.0))
					var spike_len: float = lerpf(24.0, 62.0, pow(s8, 3.2))
					if r <= spike_len:
						img.set_pixel(x, y, pip)
		# Corner micro-pips on 6 so it's unmistakably 6 from every angle!
		for sx_p in [-1.0, 1.0]:
			for sy_p in [-1.0, 1.0]:
				_stamp_circle(img, c + Vector2(sx_p * 56.0, sy_p * 56.0), 8.5, pip)
	else:
		var off: float = 46.0
		var pr: float = 17.5
		var pts: Array[Vector2] = []
		if face_val in [1, 3, 5]:
			pts.append(c)
		if face_val >= 2:
			pts.append(c + Vector2(-off, -off))
			pts.append(c + Vector2(off, off))
		if face_val >= 4:
			pts.append(c + Vector2(off, -off))
			pts.append(c + Vector2(-off, off))
		for pt in pts:
			_stamp_circle(img, pt + Vector2(0.0, 2.5), pr + 1.5, Color(0, 0, 0, 0.35))
			_stamp_circle(img, pt, pr, pip)

	img.generate_mipmaps()
	var tex := ImageTexture.create_from_image(img)
	var mat := StandardMaterial3D.new()
	mat.albedo_texture = tex
	mat.roughness = 0.26
	mat.metallic = 0.14
	mat.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	mat.emission_enabled = true
	mat.emission_texture = tex
	mat.emission_energy_multiplier = 0.42 if face_val == 6 else 0.18
	_face_mats[key] = mat
	return mat


static func _stamp_circle(img: Image, center: Vector2, radius: float, col: Color) -> void:
	var x0: int = maxi(0, int(center.x - radius - 1.0))
	var x1: int = mini(img.get_width() - 1, int(center.x + radius + 1.0))
	var y0: int = maxi(0, int(center.y - radius - 1.0))
	var y1: int = mini(img.get_height() - 1, int(center.y + radius + 1.0))
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			var d: float = Vector2(float(x) + 0.5, float(y) + 0.5).distance_to(center)
			if d <= radius:
				var aa: float = clampf(radius - d + 0.5, 0.0, 1.0)
				var prev := img.get_pixel(x, y)
				img.set_pixel(x, y, prev.lerp(col, aa * col.a))


func _create_die_node(theme_id: String) -> Dictionary:
	var holder := Node3D.new()
	var cube := Node3D.new()
	holder.add_child(cube)

	var body_mat := _get_body_mat(theme_id)
	# 3 intersecting slabs form crisp 3D bevelled/chamfered edges!
	KTModels3D.add_box(cube, Vector3.ZERO, Vector3(DIE_SIZE, DIE_SIZE * 0.88, DIE_SIZE * 0.88), body_mat)
	KTModels3D.add_box(cube, Vector3.ZERO, Vector3(DIE_SIZE * 0.88, DIE_SIZE, DIE_SIZE * 0.88), body_mat)
	KTModels3D.add_box(cube, Vector3.ZERO, Vector3(DIE_SIZE * 0.88, DIE_SIZE * 0.88, DIE_SIZE), body_mat)

	# 6 High-Res Face Plates (offset strictly outside DIE_SIZE * 0.5 -> zero Z-fighting!)
	var half_f: float = DIE_SIZE * 0.503
	var fsize := Vector2(DIE_SIZE * 0.86, DIE_SIZE * 0.86)
	var face_defs: Array[Dictionary] = [
		{"v": 6, "p": Vector3(0, half_f, 0), "r": Vector3(-90, 0, 0)},
		{"v": 1, "p": Vector3(0, -half_f, 0), "r": Vector3(90, 0, 0)},
		{"v": 2, "p": Vector3(0, 0, half_f), "r": Vector3(0, 0, 0)},
		{"v": 5, "p": Vector3(0, 0, -half_f), "r": Vector3(0, 180, 0)},
		{"v": 3, "p": Vector3(half_f, 0, 0), "r": Vector3(0, 90, 0)},
		{"v": 4, "p": Vector3(-half_f, 0, 0), "r": Vector3(0, -90, 0)},
	]
	for fd in face_defs:
		var qm := QuadMesh.new()
		qm.size = fsize
		var mi := MeshInstance3D.new()
		mi.mesh = qm
		mi.material_override = _get_face_mat(theme_id, int(fd["v"]))
		mi.position = fd["p"]
		mi.rotation_degrees = fd["r"]
		cube.add_child(mi)

	# Flat-on-felt glowing status ring underneath cube
	if _ring_mesh == null:
		_ring_mesh = TorusMesh.new()
		_ring_mesh.inner_radius = DIE_SIZE * 0.56
		_ring_mesh.outer_radius = DIE_SIZE * 0.74
		_ring_mesh.rings = 28
		_ring_mesh.ring_segments = 10

	var rmat := StandardMaterial3D.new()
	rmat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	rmat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	rmat.depth_draw_mode = BaseMaterial3D.DEPTH_DRAW_DISABLED
	rmat.render_priority = 4
	rmat.albedo_color = Color(1.0, 0.84, 0.36, 0.0)

	var ring_mi := MeshInstance3D.new()
	ring_mi.mesh = _ring_mesh
	ring_mi.material_override = rmat
	ring_mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	ring_mi.position = Vector3(0.0, -DIE_SIZE * 0.48, 0.0)
	holder.add_child(ring_mi)

	_dice_root.add_child(holder)
	return {"holder": holder, "cube": cube, "ring": ring_mi, "ring_mat": rmat}


static func _basis_for_top_face(face_val: int, yaw_rad: float) -> Basis:
	var align_b := Basis.IDENTITY
	match clampi(face_val, 1, 6):
		6:
			align_b = Basis.IDENTITY
		1:
			align_b = Basis(Vector3.RIGHT, PI)
		2:
			align_b = Basis(Vector3.RIGHT, -PI * 0.5)
		5:
			align_b = Basis(Vector3.RIGHT, PI * 0.5)
		3:
			align_b = Basis(Vector3(0.0, 0.0, 1.0), PI * 0.5)
		4:
			align_b = Basis(Vector3(0.0, 0.0, 1.0), -PI * 0.5)
	return Basis(Vector3.UP, yaw_rad) * align_b


# ==============================================================================
# 3. TABLETOP PLACEMENT & ROLL CHOREOGRAPHIES
# ==============================================================================

func clear_active_roll() -> void:
	interactive_picking_enabled = false
	left_bay_label = ""
	right_bay_label = ""
	active_mode = "idle"
	_hovered_key = ""
	for c in _dice_root.get_children():
		c.queue_free()
	for c in _fx_root.get_children():
		c.queue_free()
	_dice_entries.clear()
	_flying_fx_dice.clear()


func place_tray_idle(instant: bool = false) -> void:
	clear_active_roll()
	_divider_mi.visible = true
	# Idle: parked on the table apron, well clear of the 30"x22" killzone and the bottom HUD.
	# Only a sliver of the brass rim stays visible at the default camera so the player still
	# knows the tray is "on the table"; a roll slides it in to the action.
	# Near (south) apron, right of centre: the table props now run along the east edge, so the
	# idle tray parks below the board where the default camera shows just its brass rim.
	_target_tray_pos = Vector3(9.5, -0.24, 19.6)
	_target_tray_yaw = 0.0
	if instant:
		position = _target_tray_pos
		rotation.y = _target_tray_yaw

	# Place 2 decorative resting faction dice inside the idle tray so the table looks genuinely lived-in!
	var d_imp := _create_die_node("imperial")
	var h_imp: Node3D = d_imp["holder"]
	var c_imp: Node3D = d_imp["cube"]
	h_imp.position = Vector3(-3.4, DIE_REST_Y, -0.6)
	c_imp.basis = _basis_for_top_face(6, 0.28)

	var d_pm := _create_die_node("nurgle")
	var h_pm: Node3D = d_pm["holder"]
	var c_pm: Node3D = d_pm["cube"]
	h_pm.position = Vector3(3.3, DIE_REST_Y, 0.5)
	c_pm.basis = _basis_for_top_face(6, -0.34)


func move_tray_location(world_pos: Vector3, yaw_rad: float, instant: bool = false) -> void:
	_target_tray_pos = world_pos
	_target_tray_yaw = yaw_rad
	if instant:
		position = _target_tray_pos
		rotation.y = _target_tray_yaw


func move_tray_for_action(focus_mid: Vector3, cam_yaw_deg: float, instant: bool = false) -> Vector3:
	var yaw_r: float = deg_to_rad(cam_yaw_deg)
	var cz: float = cos(yaw_r)
	var sx: float = sin(yaw_r)
	var cam_right_2d := Vector2(cz, -sx)

	# Place tray physically on the foreground-right gaming table apron closest to the camera!
	var tx: float = 0.0
	var tz: float = 16.6
	var tyaw: float = 0.0
	if absf(cz) >= absf(sx):
		var sgn_z: float = 1.0 if cz >= 0.0 else -1.0
		tz = sgn_z * 16.6
		tx = clampf(focus_mid.x + cam_right_2d.x * 8.2, -5.5, 10.5)
		tyaw = 0.0 if sgn_z > 0.0 else PI
	else:
		var sgn_x: float = 1.0 if sx >= 0.0 else -1.0
		tx = sgn_x * 21.0
		tz = clampf(focus_mid.z + cam_right_2d.y * 6.8, -5.0, 6.5)
		tyaw = PI * 0.5 if sgn_x > 0.0 else -PI * 0.5

	_target_tray_pos = Vector3(tx, -0.24, tz)
	_target_tray_yaw = tyaw
	if instant:
		position = _target_tray_pos
		rotation.y = _target_tray_yaw
	return _target_tray_pos


func get_tray_world_center() -> Vector3:
	return _target_tray_pos + Vector3(0.0, 0.35, 0.0)


func get_bay_header_world_pos(is_left: bool) -> Vector3:
	var lx: float = -3.5 if is_left else 3.5
	var local_p := Vector3(lx, 0.92, -TRAY_D * 0.50)
	return _target_tray_pos + Basis(Vector3.UP, _target_tray_yaw) * local_p


func get_active_dice_hud_tags() -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	if active_mode == "idle":
		return out
	for e in _dice_entries:
		var holder: Node3D = e["node"]
		if not is_instance_valid(holder) or not holder.visible or not bool(e["settled"]):
			continue
		out.append({
			"wpos": holder.global_position + Basis(Vector3.UP, _target_tray_yaw) * Vector3(0.0, -DIE_SIZE * 0.46, DIE_SIZE * 0.48),
			"val": int(e["val"]),
			"kind": str(e["kind"]),
			"rerolled": bool(e["rerolled"]),
			"selected": bool(e["selected"]),
			"hovered": float(e["hover_t"]) > 0.25,
			"interactive": interactive_picking_enabled and bool(e["interactive"]),
		})
	return out


func _compute_bay_slots(count: int, is_left_bay: bool, full_tray: bool = false) -> Array[Vector3]:
	var slots: Array[Vector3] = []
	if count <= 0:
		return slots
	var x_min: float = -6.1 if full_tray else (-6.15 if is_left_bay else 0.75)
	var x_max: float = 6.1 if full_tray else (-0.75 if is_left_bay else 6.15)
	var cols: int = maxi(1, mini(count, 4 if full_tray else 3))
	var rows: int = int(ceil(float(count) / float(cols)))
	var span_x: float = x_max - x_min
	var step_x: float = span_x / float(maxi(1, cols))
	var step_z: float = minf(2.35, 5.9 / float(maxi(1, rows)))

	for i in range(count):
		var col_i: int = i % cols
		var row_i: int = int(i / cols)
		var cx: float = x_min + step_x * (float(col_i) + 0.5)
		var cz: float = (float(row_i) - float(rows - 1) * 0.5) * step_z
		# Subtle organic tabletop scatter while guaranteeing zero overlap!
		var jx: float = sin(float(i * 17 + (3 if is_left_bay else 11))) * 0.16
		var jz: float = cos(float(i * 23 + (7 if is_left_bay else 5))) * 0.16
		slots.append(Vector3(cx + jx, DIE_REST_Y, cz + jz))
	return slots


func _spawn_die_entry(
	theme_id: String,
	pool_key: String,
	idx: int,
	face_val: int,
	kind: String,
	rerolled: bool,
	selected: bool,
	interactive: bool,
	target_local: Vector3,
	from_dir_local: Vector3,
	delay_sec: float,
	instant: bool
) -> void:
	var built := _create_die_node(theme_id)
	var holder: Node3D = built["holder"]
	var cube: Node3D = built["cube"]
	var yaw_end: float = sin(float(idx * 31 + face_val * 13)) * 0.42
	var end_basis := _basis_for_top_face(face_val, yaw_end)
	var end_q := end_basis.get_rotation_quaternion()

	var start_local := target_local + from_dir_local * 5.2 + Vector3(0.0, 4.2, 0.0)
	var start_q := Quaternion.from_euler(Vector3(_rng.randf() * TAU, _rng.randf() * TAU, _rng.randf() * TAU))
	var spin_axis := Vector3(_rng.randf_range(-1.0, 1.0), _rng.randf_range(-0.6, 0.6), _rng.randf_range(-1.0, 1.0)).normalized()
	if spin_axis.length_squared() < 0.05:
		spin_axis = Vector3(1.0, 0.3, 0.7).normalized()

	var entry := {
		"node": holder,
		"cube": cube,
		"ring": built["ring"],
		"ring_mat": built["ring_mat"],
		"pool": pool_key,
		"idx": idx,
		"val": face_val,
		"kind": kind,
		"rerolled": rerolled,
		"selected": selected,
		"interactive": interactive and not rerolled,
		"start_local": start_local,
		"end_local": target_local,
		"start_q": start_q,
		"end_q": end_q,
		"spin_axis": spin_axis,
		"delay": 0.0 if instant else delay_sec,
		"t": 0.68 if instant else 0.0,
		"dur": 0.66,
		"bounces_played": 2 if instant else 0,
		"settled": instant,
		"hover_t": 0.0,
	}
	_dice_entries.append(entry)
	_apply_entry_pose(entry)


# --- A. INITIATIVE ROLL (Imperial D6 vs Nurgle D6!) ---
func roll_initiative_dice(val_a: int, val_b: int, winner: String, instant: bool = false) -> void:
	clear_active_roll()
	active_mode = "initiative"
	_divider_mi.visible = true
	left_bay_label = "死亡天使 [%d]%s" % [val_a, " ★先手" if winner == "A" else ""]
	right_bay_label = "瘟疫战士 [%d]%s" % [val_b, " ★先手" if winner == "B" else ""]

	_spawn_die_entry(
		"imperial", "init_a", 0, val_a,
		"crit" if winner == "A" else "init",
		false, false, false,
		Vector3(-3.3, DIE_REST_Y, 0.0), Vector3(-1.0, 0.0, 0.35).normalized(),
		0.0, instant
	)
	_spawn_die_entry(
		"nurgle", "init_b", 0, val_b,
		"crit" if winner == "B" else "init",
		false, false, false,
		Vector3(3.3, DIE_REST_Y, 0.0), Vector3(1.0, 0.0, 0.35).normalized(),
		0.08, instant
	)


# --- B. RANGED SHOOTING DICE (Attack Bay + Defence Save Bay + Single-Die Reroll!) ---
func sync_shooting_dice(
	session: Dictionary,
	selected_pool: String,
	selected_idx: int,
	animate_pool: String = "",
	rerolled_single_idx: int = -1,
	instant: bool = false
) -> void:
	var step: String = str(session.get("step", "attack_reroll"))
	interactive_picking_enabled = (step in ["attack_reroll", "defence_roll"])

	# Fast single-die tabletop re-roll in-place!
	if rerolled_single_idx >= 0 and not animate_pool.is_empty():
		for e in _dice_entries:
			if str(e["pool"]) == animate_pool and int(e["idx"]) == rerolled_single_idx:
				var pool_arr: Array = session[animate_pool]
				var d_new: Dictionary = pool_arr[rerolled_single_idx]
				var nv: int = int(d_new["val"])
				e["val"] = nv
				e["rerolled"] = true
				e["interactive"] = false
				e["selected"] = false
				e["kind"] = _eval_shooting_die_kind(session, animate_pool, nv)
				var holder_n: Node3D = e["node"]
				var theme_s: String = "attack" if animate_pool == "atk_dice" else ("imperial" if str(session["defender_side"]) == "A" else "nurgle")
				holder_n.queue_free()
				var rebuilt := _create_die_node(theme_s)
				e["node"] = rebuilt["holder"]
				e["cube"] = rebuilt["cube"]
				e["ring"] = rebuilt["ring"]
				e["ring_mat"] = rebuilt["ring_mat"]
				e["start_local"] = e["end_local"] + Vector3(0.0, 3.8, 0.0)
				e["end_q"] = _basis_for_top_face(nv, _rng.randf_range(-0.4, 0.4)).get_rotation_quaternion()
				e["delay"] = 0.0
				e["t"] = 0.0
				e["dur"] = 0.56
				e["bounces_played"] = 0
				e["settled"] = false
				_apply_entry_pose(e)
				return

	clear_active_roll()
	active_mode = "shooting"
	interactive_picking_enabled = (step in ["attack_reroll", "defence_roll"])
	_divider_mi.visible = true

	var hit_req: int = int(session["effective_hit"])
	var save_req: int = int(session["def_save"])
	left_bay_label = "🔴 攻击骰 (%d+命中)" % hit_req
	right_bay_label = "🛡️ 防御骰 (%d+豁免)" % save_req if step != "attack_reroll" else "🛡️ 待投防御骰"

	# Left Bay: Attack Dice (+ Accurate auto-hit cubes if any)
	var atk_dice: Array = session.get("atk_dice", [])
	var auto_n: int = int(session.get("auto_attack_normals", 0))
	var total_atk: int = atk_dice.size() + auto_n
	var atk_slots := _compute_bay_slots(total_atk, true, false)
	var anim_atk: bool = (animate_pool == "atk_dice" or animate_pool == "both") and not instant

	for i in range(atk_dice.size()):
		var d: Dictionary = atk_dice[i]
		var v: int = int(d["val"])
		var rr: bool = bool(d.get("rerolled", false))
		var k: String = _eval_shooting_die_kind(session, "atk_dice", v)
		var sel: bool = (step == "attack_reroll" and selected_pool == "atk_dice" and selected_idx == i)
		_spawn_die_entry(
			"attack", "atk_dice", i, v, k, rr, sel,
			step == "attack_reroll",
			atk_slots[i], Vector3(-1.0, 0.0, -0.35).normalized(),
			float(i) * 0.055, not anim_atk
		)

	for a in range(auto_n):
		var slot_i: int = atk_dice.size() + a
		_spawn_die_entry(
			"gold", "auto_atk", a, hit_req, "norm", true, false, false,
			atk_slots[slot_i], Vector3(-1.0, 0.0, -0.2).normalized(),
			float(slot_i) * 0.05, not anim_atk
		)

	# Right Bay: Defence Dice (+ Cover auto-save cubes if any)
	if step != "attack_reroll":
		var def_dice: Array = session.get("def_dice", [])
		var auto_dn: int = int(session.get("auto_def_normals", 0))
		var auto_dc: int = int(session.get("auto_def_crits", 0))
		var total_def: int = def_dice.size() + auto_dn + auto_dc
		var def_slots := _compute_bay_slots(total_def, false, false)
		var def_theme: String = "imperial" if str(session["defender_side"]) == "A" else "nurgle"
		var anim_def: bool = (animate_pool == "def_dice" or animate_pool == "both") and not instant

		for j in range(def_dice.size()):
			var dd: Dictionary = def_dice[j]
			var vd: int = int(dd["val"])
			var rrd: bool = bool(dd.get("rerolled", false))
			var kd: String = _eval_shooting_die_kind(session, "def_dice", vd)
			var sel_d: bool = (step == "defence_roll" and selected_pool == "def_dice" and selected_idx == j)
			_spawn_die_entry(
				def_theme, "def_dice", j, vd, kd, rrd, sel_d,
				step == "defence_roll",
				def_slots[j], Vector3(1.0, 0.0, -0.35).normalized(),
				float(j) * 0.06, not anim_def
			)

		var cur_s: int = def_dice.size()
		for _an in range(auto_dn):
			_spawn_die_entry(
				"gold", "auto_def", cur_s, save_req, "norm", true, false, false,
				def_slots[cur_s], Vector3(1.0, 0.0, 0.0), float(cur_s) * 0.05, not anim_def
			)
			cur_s += 1
		for _ac in range(auto_dc):
			_spawn_die_entry(
				"gold", "auto_def_c", cur_s, 6, "crit", true, false, false,
				def_slots[cur_s], Vector3(1.0, 0.0, 0.0), float(cur_s) * 0.05, not anim_def
			)
			cur_s += 1


func _eval_shooting_die_kind(session: Dictionary, pool_key: String, v: int) -> String:
	if v <= 1:
		return "fail"
	if pool_key == "atk_dice":
		if v >= int(session["lethal_thresh"]):
			return "crit"
		elif v >= int(session["effective_hit"]):
			return "norm"
		return "fail"
	else:
		if v >= int(session["def_crit_save"]):
			return "crit"
		elif v >= int(session["def_save"]):
			return "norm"
		return "fail"


func update_selection_highlight(selected_pool: String, selected_idx: int) -> void:
	for e in _dice_entries:
		e["selected"] = (str(e["pool"]) == selected_pool and int(e["idx"]) == selected_idx)


## Rule-trigger badge on a die: a short text ("严重 ↑★", "恼人韧性 −1") drawn by the 2D HUD above the
## die for `dur` seconds while the die itself pulses in `col`. Several badges may stack on one die.
## `pool` + `idx` identify the die; idx = -1 badges every die in the pool (e.g. 重型遮挡).
var _die_badges: Array[Dictionary] = []

func flash_rule_on_dice(pool: String, idx: int, text: String, col: Color, dur: float = 1.6) -> void:
	for e in _dice_entries:
		if str(e["pool"]) != pool:
			continue
		if idx >= 0 and int(e["idx"]) != idx:
			continue
		e["rule_flash_t"] = 1.0
		e["rule_flash_col"] = col
		_die_badges.append({"pool": pool, "idx": int(e["idx"]), "text": text, "col": col, "t": 0.0, "dur": dur})


func get_die_badges() -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	for b in _die_badges:
		for e in _dice_entries:
			if str(e["pool"]) == str(b["pool"]) and int(e["idx"]) == int(b["idx"]):
				var holder: Node3D = e["node"]
				if is_instance_valid(holder) and holder.visible:
					out.append({
						"wpos": holder.global_position + Vector3(0.0, DIE_SIZE * 0.95, 0.0),
						"text": str(b["text"]), "col": b["col"],
						"alpha": 1.0 - clampf((float(b["t"]) - float(b["dur"]) * 0.6) / (float(b["dur"]) * 0.4), 0.0, 1.0),
						"rise": clampf(float(b["t"]) / 0.35, 0.0, 1.0),
					})
				break
	return out


# --- C. MELEE ALTERNATING DUEL DICE (Dual Bay + Physical Strike/Parry Clash!) ---
func sync_melee_dice(session: Dictionary, animate_fresh: bool = false, instant: bool = false) -> void:
	clear_active_roll()
	active_mode = "melee"
	interactive_picking_enabled = false
	_divider_mi.visible = true

	var att_p: Dictionary = session["att"]
	var def_p: Dictionary = session["def"]
	var att_imp: bool = str(session["attacker_side"]) == "A"
	left_bay_label = "⚔️ 攻方骰池 (%d★/%d●)" % [int(att_p["crits"]), int(att_p["normals"])]
	right_bay_label = "🛡️ 守方骰池 (%d★/%d●)" % [int(def_p["crits"]), int(def_p["normals"])]

	_populate_melee_bay(att_p, true, "imperial" if att_imp else "nurgle", "melee_att", animate_fresh and not instant)
	_populate_melee_bay(def_p, false, "nurgle" if att_imp else "imperial", "melee_def", animate_fresh and not instant)


func _populate_melee_bay(pool: Dictionary, is_left: bool, theme_id: String, pool_key: String, animate: bool) -> void:
	var crits: int = int(pool["crits"])
	var norms: int = int(pool["normals"])
	var total: int = crits + norms
	var slots := _compute_bay_slots(total, is_left, false)
	var hit_v: int = maxi(3, int(pool.get("effective_hit", 3)))
	var dir := Vector3(-1.0 if is_left else 1.0, 0.0, -0.30).normalized()

	var idx: int = 0
	for _c in range(crits):
		_spawn_die_entry(theme_id, pool_key, idx, 6, "crit", false, false, false, slots[idx], dir, float(idx) * 0.055, not animate)
		idx += 1
	for n in range(norms):
		var face_n: int = clampi(hit_v + (n % 2), 2, 5)
		_spawn_die_entry(theme_id, pool_key, idx, face_n, "norm", false, false, false, slots[idx], dir, float(idx) * 0.055, not animate)
		idx += 1


func play_melee_step_clash(
	is_attacker: bool,
	choice: String,
	target_world_pos: Vector3,
	updated_session: Dictionary
) -> void:
	var my_pool_key: String = "melee_att" if is_attacker else "melee_def"
	var opp_pool_key: String = "melee_def" if is_attacker else "melee_att"
	var use_crit: bool = choice.ends_with("crit")

	# Record start world positions from current tray BEFORE syncing remaining dice!
	var actor_start := _find_die_world_pos(my_pool_key, use_crit)
	var opp_start := _find_die_world_pos(opp_pool_key, use_crit)
	var actor_theme: String = "imperial" if ((is_attacker and str(updated_session["attacker_side"]) == "A") or ((not is_attacker) and str(updated_session["defender_side"]) == "A")) else "nurgle"

	# Refresh remaining resting cubes in both bays without re-throwing all of them!
	sync_melee_dice(updated_session, false, true)

	if choice.begins_with("strike"):
		_spawn_flying_fx_die(actor_theme, 6 if use_crit else 4, actor_start, target_world_pos + Vector3(0.0, 1.1, 0.0), 0.26)
	else:
		var clash_mid := to_global(Vector3(0.0, 2.5, 0.0))
		var opp_theme: String = "nurgle" if actor_theme == "imperial" else "imperial"
		_spawn_flying_fx_die(actor_theme, 6 if use_crit else 4, actor_start, clash_mid, 0.22)
		_spawn_flying_fx_die(opp_theme, 6 if use_crit else 4, opp_start, clash_mid, 0.22)


func _find_die_world_pos(pool_key: String, prefer_crit: bool) -> Vector3:
	for e in _dice_entries:
		if str(e["pool"]) == pool_key and (str(e["kind"]) == ("crit" if prefer_crit else "norm")):
			return (e["node"] as Node3D).global_position
	for e2 in _dice_entries:
		if str(e2["pool"]) == pool_key:
			return (e2["node"] as Node3D).global_position
	return to_global(Vector3(-2.5 if pool_key == "melee_att" else 2.5, DIE_REST_Y, 0.0))


func _spawn_flying_fx_die(theme_id: String, face_val: int, w_from: Vector3, w_to: Vector3, dur: float) -> void:
	var built := _create_die_node(theme_id)
	var holder: Node3D = built["holder"]
	holder.reparent(_fx_root)
	holder.global_position = w_from
	_flying_fx_dice.append({
		"node": holder,
		"cube": built["cube"],
		"from": w_from,
		"to": w_to,
		"t": 0.0,
		"dur": dur,
		"val": face_val,
	})


# ==============================================================================
# 4. 3D RAYCAST HOVER & CLICK PICKING ON PHYSICAL TABLETOP DICE
# ==============================================================================

func pick_die_from_ray(ray_origin: Vector3, ray_dir: Vector3) -> Dictionary:
	if not interactive_picking_enabled:
		return {}
	var best_hit := {}
	var best_dist: float = 9999.0
	for e in _dice_entries:
		if not bool(e["interactive"]):
			continue
		var holder: Node3D = e["node"]
		if not is_instance_valid(holder):
			continue
		var center: Vector3 = holder.global_position
		var to_c: Vector3 = center - ray_origin
		var proj: float = to_c.dot(ray_dir)
		if proj <= 0.0:
			continue
		var closest: Vector3 = ray_origin + ray_dir * proj
		if closest.distance_to(center) <= DIE_SIZE * 0.78 and proj < best_dist:
			best_dist = proj
			best_hit = {"pool": str(e["pool"]), "index": int(e["idx"])}
	return best_hit


func update_hover_from_ray(ray_origin: Vector3, ray_dir: Vector3) -> bool:
	var hit := pick_die_from_ray(ray_origin, ray_dir)
	var new_key: String = "" if hit.is_empty() else ("%s_%d" % [str(hit["pool"]), int(hit["index"])])
	if new_key != _hovered_key:
		_hovered_key = new_key
		if not new_key.is_empty():
			KTSfx.play_hover()
	return not hit.is_empty()


# ==============================================================================
# 5. REAL-TIME TUMBLE, MULTI-BOUNCE & HALO ANIMATION LOOP
# ==============================================================================

func _process(delta: float) -> void:
	_pulse_time += delta
	position = position.lerp(_target_tray_pos, clampf(delta * 10.0, 0.0, 1.0))
	rotation.y = lerp_angle(rotation.y, _target_tray_yaw, clampf(delta * 10.0, 0.0, 1.0))

	var bi := _die_badges.size() - 1
	while bi >= 0:
		_die_badges[bi]["t"] = float(_die_badges[bi]["t"]) + delta
		if float(_die_badges[bi]["t"]) >= float(_die_badges[bi]["dur"]):
			_die_badges.remove_at(bi)
		bi -= 1

	for e in _dice_entries:
		var holder: Node3D = e["node"]
		if not is_instance_valid(holder):
			continue
		if e.has("rule_flash_t") and float(e["rule_flash_t"]) > 0.0:
			e["rule_flash_t"] = maxf(0.0, float(e["rule_flash_t"]) - delta * 1.6)
		if float(e["delay"]) > 0.0:
			e["delay"] = maxf(0.0, float(e["delay"]) - delta)
			holder.visible = false
			continue
		holder.visible = true

		if not bool(e["settled"]):
			var prev_t: float = float(e["t"])
			var dur: float = float(e["dur"])
			var nt: float = minf(dur, prev_t + delta)
			e["t"] = nt
			var u: float = nt / dur

			# Trigger tactile tabletop clack SFX at bounce 1 (u=0.46) and bounce 2 (u=0.76)!
			var bp: int = int(e["bounces_played"])
			if bp == 0 and u >= 0.46:
				e["bounces_played"] = 1
				KTSfx.play("dice_clack", -5.5, 0.14)
			elif bp == 1 and u >= 0.76:
				e["bounces_played"] = 2
				KTSfx.play("dice_clack", -10.5, 0.18)

			if nt >= dur - 0.001:
				e["settled"] = true
				if str(e["kind"]) == "crit":
					KTSfx.play("crit_chime", -7.5, 0.05)

		var key_e: String = "%s_%d" % [str(e["pool"]), int(e["idx"])]
		var want_hov: float = 1.0 if (interactive_picking_enabled and bool(e["interactive"]) and key_e == _hovered_key) else 0.0
		e["hover_t"] = move_toward(float(e["hover_t"]), want_hov, delta * 10.0)
		_apply_entry_pose(e)

	# Update flying Strike / Parry FX cubes
	var f_i: int = _flying_fx_dice.size() - 1
	while f_i >= 0:
		var fd: Dictionary = _flying_fx_dice[f_i]
		if not is_instance_valid(fd["node"]):
			_flying_fx_dice.remove_at(f_i)
			f_i -= 1
			continue
		var fn: Node3D = fd["node"]
		fd["t"] = float(fd["t"]) + delta
		var fu: float = clampf(float(fd["t"]) / float(fd["dur"]), 0.0, 1.0)
		var fpos: Vector3 = (fd["from"] as Vector3).lerp(fd["to"] as Vector3, fu)
		fpos.y += sin(fu * PI) * 2.2
		fn.global_position = fpos
		var fcube: Node3D = fd["cube"]
		fcube.rotate_x(delta * 18.0)
		fcube.rotate_z(delta * 14.0)
		if fu >= 1.0:
			fn.queue_free()
			_flying_fx_dice.remove_at(f_i)
		f_i -= 1


func _apply_entry_pose(e: Dictionary) -> void:
	var holder: Node3D = e["node"]
	var cube: Node3D = e["cube"]
	var rmat: StandardMaterial3D = e["ring_mat"]

	if not bool(e["settled"]):
		var u: float = clampf(float(e["t"]) / float(e["dur"]), 0.0, 1.0)
		var ease_xz: float = 1.0 - pow(1.0 - u, 2.4)
		var p_start: Vector3 = e["start_local"]
		var p_end: Vector3 = e["end_local"]
		var px: float = lerpf(p_start.x, p_end.x, ease_xz)
		var pz: float = lerpf(p_start.z, p_end.z, ease_xz)

		# 3-phase gravity bounce curve onto FELT_Y!
		var py: float = p_end.y
		if u < 0.46:
			var s0: float = u / 0.46
			py = lerpf(p_start.y, p_end.y, s0 * s0)
		elif u < 0.76:
			var s1: float = (u - 0.46) / 0.30
			py = p_end.y + 4.0 * s1 * (1.0 - s1) * 1.45
		else:
			var s2: float = (u - 0.76) / 0.24
			py = p_end.y + 4.0 * s2 * (1.0 - s2) * 0.38

		holder.position = Vector3(px, py, pz)

		# Wild multi-axis tumble blending cleanly into exact face-up orientation!
		var q_end: Quaternion = e["end_q"]
		if u < 0.72:
			var spin_amt: float = (1.0 - u / 0.72) * TAU * 2.2
			var spin_q := Quaternion(e["spin_axis"] as Vector3, spin_amt)
			var base_q: Quaternion = (e["start_q"] as Quaternion).slerp(q_end, u / 0.72)
			cube.quaternion = (spin_q * base_q).normalized()
		else:
			var settle_u: float = (u - 0.72) / 0.28
			var tip_q := Quaternion(e["spin_axis"] as Vector3, (1.0 - settle_u) * 0.55)
			cube.quaternion = (tip_q * q_end).normalized()
		rmat.albedo_color.a = 0.0
	else:
		var end_l: Vector3 = e["end_local"]
		var hov_t: float = float(e["hover_t"])
		var sel: bool = bool(e["selected"])
		var lift: float = 0.28 * hov_t + (0.32 if sel else 0.0)
		holder.position = Vector3(end_l.x, end_l.y + lift, end_l.z)
		cube.quaternion = e["end_q"]

		var ring_mi: MeshInstance3D = e["ring"]
		ring_mi.position.y = -DIE_SIZE * 0.48 - lift

		var kind: String = str(e["kind"])
		var pulse: float = 0.5 + 0.5 * sin(_pulse_time * 6.0)
		var rf: float = float(e.get("rule_flash_t", 0.0))
		if rf > 0.01:
			# Rule just touched this die: lift it and flood the halo with the rule colour
			var rc: Color = e.get("rule_flash_col", Color(1, 1, 1))
			holder.position.y += 0.22 * rf
			rmat.albedo_color = Color(rc.r, rc.g, rc.b, 0.55 + 0.45 * rf)
		elif sel:
			rmat.albedo_color = Color(1.0, 0.96, 0.78, 0.95)
		elif hov_t > 0.05:
			rmat.albedo_color = Color(1.0, 0.88, 0.42, 0.75 * hov_t)
		elif kind == "crit":
			rmat.albedo_color = Color(1.0, 0.82, 0.24, 0.72 + 0.22 * pulse)
		elif kind == "norm":
			rmat.albedo_color = Color(0.24, 0.90, 0.58, 0.58)
		else:
			rmat.albedo_color = Color(0.38, 0.42, 0.50, 0.18)
