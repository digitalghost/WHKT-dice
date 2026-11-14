class_name KTModels3D
extends RefCounted

## HD-2D (Octopath Traveler Style) 3D Killzone Architecture & 360° Billboard Standee Factory:
## - Multi-elevation 3D Volkus Strongholds (+3.5" 2-story decks, stairs, 4.2" Bunker Walls, Gothic Ruins, Barricades)
## - Atmospheric flickering 3D Gothic Fire Braziers & Nurgle Censers with live 3D flame/ember particles
## - Sculpted 3D tabletop miniature bases paired with razor-sharp 360° camera-facing 2D Character Standees
##   extracted from official Kill Team rule cards, casting real 3D silhouette shadows!

static var _cached_soft_dot_tex: ImageTexture = null
static var _cached_battlemat_tex: ImageTexture = null


static func get_soft_particle_tex() -> ImageTexture:
	if _cached_soft_dot_tex != null:
		return _cached_soft_dot_tex
	var sz := 64
	var img := Image.create(sz, sz, false, Image.FORMAT_RGBA8)
	var half := float(sz) * 0.5
	for y in range(sz):
		for x in range(sz):
			var dx: float = (float(x) + 0.5 - half) / half
			var dy: float = (float(y) + 0.5 - half) / half
			var d: float = sqrt(dx * dx + dy * dy)
			var a: float = clampf(1.0 - d, 0.0, 1.0)
			a = a * a * (3.0 - 2.0 * a)
			var core: float = clampf(1.0 - d * 2.1, 0.0, 1.0)
			var lum: float = lerpf(0.82, 1.0, core)
			img.set_pixel(x, y, Color(lum, lum, lum, a))
	_cached_soft_dot_tex = ImageTexture.create_from_image(img)
	return _cached_soft_dot_tex


# ==============================================================================
# PBR & STANDEE MATERIAL HELPERS
# ==============================================================================

static func make_pbr(
	albedo: Color,
	metallic: float = 0.25,
	roughness: float = 0.55,
	emission: Color = Color(0, 0, 0, 0),
	emission_energy: float = 1.0
) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = albedo
	m.metallic = metallic
	m.roughness = roughness
	if emission.a > 0.01 or emission.r + emission.g + emission.b > 0.01:
		m.emission_enabled = true
		m.emission = Color(emission.r, emission.g, emission.b, 1.0)
		m.emission_energy_multiplier = emission_energy
	return m


static func make_holo(col: Color, alpha: float = 0.45, energy: float = 1.6, prio: int = 1) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.depth_draw_mode = BaseMaterial3D.DEPTH_DRAW_DISABLED
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	m.render_priority = prio
	m.albedo_color = Color(col.r, col.g, col.b, alpha)
	m.emission_enabled = true
	m.emission = Color(col.r, col.g, col.b, 1.0)
	m.emission_energy_multiplier = energy
	return m


static func make_particle_mat(energy: float = 2.4) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.vertex_color_use_as_albedo = true
	m.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	m.billboard_keep_scale = true
	m.albedo_texture = get_soft_particle_tex()
	m.albedo_color = Color(energy, energy, energy, 1.0)
	return m


static func add_cyl(
	parent: Node3D,
	pos: Vector3,
	r_bottom: float,
	r_top: float,
	height: float,
	mat: Material,
	rot_deg: Vector3 = Vector3.ZERO,
	radial_segs: int = 20
) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.bottom_radius = r_bottom
	cm.top_radius = r_top
	cm.height = height
	cm.radial_segments = radial_segs
	mi.mesh = cm
	mi.material_override = mat
	mi.position = pos
	mi.rotation_degrees = rot_deg
	parent.add_child(mi)
	return mi


static func add_cone(
	parent: Node3D,
	pos: Vector3,
	r_base: float,
	height: float,
	mat: Material,
	rot_deg: Vector3 = Vector3.ZERO,
	r_tip: float = 0.015
) -> MeshInstance3D:
	return add_cyl(parent, pos, r_base, r_tip, height, mat, rot_deg, 16)


static func add_sphere(
	parent: Node3D,
	pos: Vector3,
	radius: float,
	mat: Material,
	scale_vec: Vector3 = Vector3.ONE
) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = radius
	sm.height = radius * 2.0
	sm.radial_segments = 20
	sm.rings = 12
	mi.mesh = sm
	mi.material_override = mat
	mi.position = pos
	mi.scale = scale_vec
	parent.add_child(mi)
	return mi


static func add_box(
	parent: Node3D,
	pos: Vector3,
	size: Vector3,
	mat: Material,
	rot_deg: Vector3 = Vector3.ZERO
) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = mat
	mi.position = pos
	mi.rotation_degrees = rot_deg
	parent.add_child(mi)
	return mi


static func add_torus(
	parent: Node3D,
	pos: Vector3,
	inner_r: float,
	outer_r: float,
	mat: Material,
	rot_deg: Vector3 = Vector3.ZERO
) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var tm := TorusMesh.new()
	tm.inner_radius = inner_r
	tm.outer_radius = outer_r
	tm.rings = 32
	tm.ring_segments = 12
	mi.mesh = tm
	mi.material_override = mat
	mi.position = pos
	mi.rotation_degrees = rot_deg
	parent.add_child(mi)
	return mi


static func add_flaming_brazier(parent: Node3D, pos: Vector3, fire_col: Color, light_range: float = 4.6) -> void:
	var mat_iron := make_pbr(Color(0.11, 0.12, 0.15), 0.78, 0.42)
	var mat_brass := make_pbr(Color(0.76, 0.58, 0.26), 0.86, 0.30)
	var mat_coal := make_pbr(fire_col, 0.1, 0.3, fire_col, 2.2)

	add_cyl(parent, pos + Vector3(0.0, 0.12, 0.0), 0.10, 0.18, 0.24, mat_iron, Vector3.ZERO, 12)
	add_torus(parent, pos + Vector3(0.0, 0.24, 0.0), 0.13, 0.20, mat_brass)
	add_sphere(parent, pos + Vector3(0.0, 0.24, 0.0), 0.13, mat_coal, Vector3(1.0, 0.55, 1.0))

	# HD-2D Warm Flickering Point Light (balanced so nearby standees never wash out!)
	var omni := OmniLight3D.new()
	omni.position = pos + Vector3(0.0, 0.52, 0.0)
	omni.light_color = fire_col.lightened(0.12)
	omni.light_energy = 0.58
	omni.omni_range = light_range
	parent.add_child(omni)

	# Continuous 3D Flame & Rising Sparks Particle Emitter!
	var cp := CPUParticles3D.new()
	cp.position = pos + Vector3(0.0, 0.28, 0.0)
	cp.amount = 18
	cp.lifetime = 0.85
	cp.preprocess = 0.5
	cp.local_coords = false
	var qm := QuadMesh.new()
	qm.size = Vector2(0.28, 0.28)
	qm.material = make_particle_mat(2.2)
	cp.mesh = qm
	cp.emission_shape = CPUParticles3D.EMISSION_SHAPE_SPHERE
	cp.emission_sphere_radius = 0.09
	cp.direction = Vector3(0.0, 1.0, 0.0)
	cp.spread = 18.0
	cp.gravity = Vector3(0.0, 1.6, 0.0)
	cp.initial_velocity_min = 0.35
	cp.initial_velocity_max = 0.85
	cp.scale_amount_min = 0.55
	cp.scale_amount_max = 1.25

	var curve := Curve.new()
	curve.add_point(Vector2(0.0, 0.45))
	curve.add_point(Vector2(0.25, 1.0))
	curve.add_point(Vector2(1.0, 0.05))
	cp.scale_amount_curve = curve

	var grad := Gradient.new()
	grad.set_color(0, Color(1.0, 0.96, 0.78, 0.95))
	grad.add_point(0.35, Color(fire_col.r, fire_col.g, fire_col.b, 0.88))
	grad.set_color(grad.get_point_count() - 1, Color(fire_col.r * 0.45, fire_col.g * 0.22, fire_col.b * 0.15, 0.0))
	cp.color_ramp = grad
	parent.add_child(cp)


# ==============================================================================
# 1. TABLETOP PLINTH & MULTI-ELEVATION KILLZONE VOLKUS ARCHITECTURE
# ==============================================================================

static func _get_battlemat_tex(width_in: float, height_in: float, dz_a: Rect2, dz_b: Rect2) -> ImageTexture:
	if _cached_battlemat_tex != null:
		return _cached_battlemat_tex

	var ppi := 32 # 960 x 704 crisp tactical battlemat with mipmaps -> ZERO Z-fighting!
	var tex_w := int(width_in * float(ppi))
	var tex_h := int(height_in * float(ppi))
	var img := Image.create(tex_w, tex_h, true, Image.FORMAT_RGBA8)

	# 1. Base Sector Imperialis dark ferrocrete stone
	img.fill(Color(0.098, 0.108, 0.128, 1.0))

	# 2. Architectural stone plaza slabs with recessed dark mortar seams
	for tx in range(-2, 3):
		for tz in [-1, 1]:
			var cx_in: float = width_in * 0.5 + float(tx) * 5.6
			var cz_in: float = height_in * 0.5 + float(tz) * 5.2
			var groove_r := Rect2i(
				int((cx_in - 2.4) * ppi), int((cz_in - 2.2) * ppi),
				int(4.8 * ppi), int(4.4 * ppi)
			).intersection(Rect2i(0, 0, tex_w, tex_h))
			var slab_r := Rect2i(
				int((cx_in - 2.25) * ppi), int((cz_in - 2.05) * ppi),
				int(4.5 * ppi), int(4.1 * ppi)
			).intersection(Rect2i(0, 0, tex_w, tex_h))
			img.fill_rect(groove_r, Color(0.068, 0.076, 0.092, 1.0))
			img.fill_rect(slab_r, Color(0.124, 0.136, 0.160, 1.0))

	# 3. Central Imperial Iron Roadway & Recessed Brass Trim
	var road_y0 := int((height_in * 0.5 - 1.9) * ppi)
	var road_h := int(3.8 * ppi)
	img.fill_rect(Rect2i(int(0.3 * ppi), road_y0, int((width_in - 0.6) * ppi), road_h), Color(0.082, 0.092, 0.112, 1.0))
	img.fill_rect(Rect2i(int(0.4 * ppi), road_y0, int((width_in - 0.8) * ppi), 3), Color(0.56, 0.43, 0.20, 1.0))
	img.fill_rect(Rect2i(int(0.4 * ppi), road_y0 + road_h - 3, int((width_in - 0.8) * ppi), 3), Color(0.56, 0.43, 0.20, 1.0))

	# 4. Deployment Zone Tints (North = Nurgle Green, South = Imperial Blue) — baked cleanly into battlemat!
	var rect_b := Rect2i(int(dz_b.position.x * ppi), int(dz_b.position.y * ppi), int(dz_b.size.x * ppi), int(dz_b.size.y * ppi)).intersection(Rect2i(0, 0, tex_w, tex_h))
	for y in range(rect_b.position.y, rect_b.end.y):
		for x in range(rect_b.position.x, rect_b.end.x):
			var base_c := img.get_pixel(x, y)
			img.set_pixel(x, y, base_c.lerp(Color(0.28, 0.50, 0.14, 1.0), 0.24))

	var rect_a := Rect2i(int(dz_a.position.x * ppi), int(dz_a.position.y * ppi), int(dz_a.size.x * ppi), int(dz_a.size.y * ppi)).intersection(Rect2i(0, 0, tex_w, tex_h))
	for y in range(rect_a.position.y, rect_a.end.y):
		for x in range(rect_a.position.x, rect_a.end.x):
			var base_c := img.get_pixel(x, y)
			img.set_pixel(x, y, base_c.lerp(Color(0.14, 0.38, 0.78, 1.0), 0.26))

	# 5. Subtle 1-Inch Tactical Grid & 6-Inch Brass Lines (anti-aliased, zero flicker!)
	for ix in range(1, int(width_in)):
		var px_x := ix * ppi
		var is_maj: bool = (ix % 6 == 0)
		var gcol := Color(0.62, 0.49, 0.24, 1.0) if is_maj else Color(0.22, 0.26, 0.33, 1.0)
		var blend_w: float = 0.55 if is_maj else 0.28
		var half_t: int = 1 if is_maj else 0
		for dx in range(-half_t, half_t + 1):
			var xx := clampi(px_x + dx, 0, tex_w - 1)
			for y in range(tex_h):
				img.set_pixel(xx, y, img.get_pixel(xx, y).lerp(gcol, blend_w))

	for iz in range(1, int(height_in)):
		var px_y := iz * ppi
		var is_maj_z: bool = (iz == 11)
		var gcol_z := Color(0.66, 0.52, 0.25, 1.0) if is_maj_z else Color(0.22, 0.26, 0.33, 1.0)
		var blend_z: float = 0.60 if is_maj_z else 0.28
		var half_tz: int = 1 if is_maj_z else 0
		for dy in range(-half_tz, half_tz + 1):
			var yy := clampi(px_y + dy, 0, tex_h - 1)
			for x in range(tex_w):
				img.set_pixel(x, yy, img.get_pixel(x, yy).lerp(gcol_z, blend_z))

	# Crisp Deployment Zone Boundary Lines
	var dz_b_line_y := clampi(int(dz_b.end.y * ppi), 1, tex_h - 3)
	img.fill_rect(Rect2i(0, dz_b_line_y - 1, tex_w, 3), Color(0.48, 0.80, 0.22, 1.0))
	var dz_a_line_y := clampi(int(dz_a.position.y * ppi), 1, tex_h - 3)
	img.fill_rect(Rect2i(0, dz_a_line_y - 1, tex_w, 3), Color(0.26, 0.62, 0.98, 1.0))

	img.generate_mipmaps()
	_cached_battlemat_tex = ImageTexture.create_from_image(img)
	return _cached_battlemat_tex


static func build_tabletop_base(
	parent: Node3D,
	width_in: float,
	height_in: float,
	_bg_tex: Texture2D,
	dz_a: Rect2,
	dz_b: Rect2
) -> void:
	var mat_desk := make_pbr(Color(0.032, 0.036, 0.046), 0.25, 0.72)
	var mat_desk_trim := make_pbr(Color(0.36, 0.28, 0.15), 0.72, 0.42)
	var mat_plinth := make_pbr(Color(0.048, 0.055, 0.068), 0.45, 0.54)
	var mat_brass_rim := make_pbr(Color(0.70, 0.53, 0.24), 0.80, 0.36)

	# Extended Wargaming Table Surface surrounding the Killzone Battlemat (top = -0.24, Trim top = -0.30)
	# Provides a real grounded tabletop apron where the 3D Velvet & Brass Dice Tray sits!
	add_box(parent, Vector3(0.0, -0.84, 0.0), Vector3(width_in + 36.0, 1.20, height_in + 30.0), mat_desk)
	add_box(parent, Vector3(0.0, -0.92, 0.0), Vector3(width_in + 37.2, 1.24, height_in + 31.2), mat_desk_trim)

	# Heavy Bevelled Wargaming Table Plinth — strictly stepped in Y so NO two top faces ever touch!
	# Desk top = -0.24, Plinth top = -0.16, Brass Rim top = -0.07, Battlemat top = 0.000!
	add_box(parent, Vector3(0.0, -0.82, 0.0), Vector3(width_in + 1.9, 1.32, height_in + 1.9), mat_plinth)
	add_box(parent, Vector3(0.0, -0.22, 0.0), Vector3(width_in + 0.62, 0.30, height_in + 0.62), mat_brass_rim)

	# Single Solid Opaque PBR Battlemat Surface (top = 0.000, ZERO coplanar overlaps, ZERO Z-fighting!)
	var mat_battlemat := StandardMaterial3D.new()
	mat_battlemat.albedo_texture = _get_battlemat_tex(width_in, height_in, dz_a, dz_b)
	mat_battlemat.metallic = 0.08
	mat_battlemat.roughness = 0.88
	mat_battlemat.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	mat_battlemat.uv1_scale = Vector3(1.0, 1.0, 1.0)

	var mat_plane := PlaneMesh.new()
	mat_plane.size = Vector2(width_in, height_in)
	var ground_mi := MeshInstance3D.new()
	ground_mi.mesh = mat_plane
	ground_mi.material_override = mat_battlemat
	ground_mi.position = Vector3(0.0, 0.0, 0.0)
	parent.add_child(ground_mi)

	# Solid Side Skirt under the Battlemat (`y = -0.08..0.0`? Keep top at `-0.015` so never coplanar with PlaneMesh!)
	add_box(parent, Vector3(0.0, -0.05, 0.0), Vector3(width_in, 0.07, height_in), mat_plinth)

	# Corner Imperial Brass Studs outside the play area
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			var cx: float = sx * (width_in * 0.5 + 0.55)
			var cz: float = sz * (height_in * 0.5 + 0.55)
			add_cyl(parent, Vector3(cx, 0.06, cz), 0.38, 0.28, 0.28, mat_brass_rim)


static func build_terrain_piece(
	parent: Node3D,
	t: Dictionary,
	board_w: float,
	board_h: float
) -> Node3D:
	var root := Node3D.new()
	var rect: Rect2 = t["rect"]
	var kind := str(t["kind"])
	var elev := float(t.get("elevation", 1.0))
	var center_x: float = rect.get_center().x - board_w * 0.5
	var center_z: float = rect.get_center().y - board_h * 0.5
	root.position = Vector3(center_x, 0.0, center_z)
	parent.add_child(root)

	var sx: float = rect.size.x
	var sz: float = rect.size.y

	var mat_ferrocrete := make_pbr(Color(0.21, 0.23, 0.26), 0.22, 0.78)
	var mat_dark_steel := make_pbr(Color(0.12, 0.135, 0.165), 0.75, 0.40)
	var mat_deck_plate := make_pbr(Color(0.20, 0.23, 0.27), 0.68, 0.44)
	var mat_brass := make_pbr(Color(0.75, 0.57, 0.26), 0.86, 0.32)
	var mat_hazard := make_pbr(Color(0.84, 0.62, 0.20), 0.48, 0.44)
	var mat_heavy_trim := make_pbr(Color(0.58, 0.22, 0.16), 0.45, 0.52)
	var mat_ruin_stone := make_pbr(Color(0.24, 0.25, 0.28), 0.16, 0.84)
	var mat_violet_trim := make_pbr(Color(0.48, 0.38, 0.62), 0.55, 0.44)

	if kind == "vantage":
		var deck_thick := 0.28
		var leg_h: float = elev - deck_thick

		# Sunken bottom (-0.02), raised top (+0.08) so base never Z-fights with y=0 battlemat!
		add_box(root, Vector3(0.0, 0.03, 0.0), Vector3(sx + 0.15, 0.10, sz + 0.15), mat_dark_steel)

		var px_off: float = sx * 0.5 - 0.32
		var pz_off: float = sz * 0.5 - 0.32
		for dx in [-1.0, 1.0]:
			for dz in [-1.0, 1.0]:
				var col_pos := Vector3(dx * px_off, leg_h * 0.5, dz * pz_off)
				add_cyl(root, col_pos, 0.30, 0.26, leg_h, mat_ferrocrete, Vector3.ZERO, 12)
				add_cyl(root, Vector3(dx * px_off, 0.25, dz * pz_off), 0.38, 0.32, 0.36, mat_brass, Vector3.ZERO, 12)
				add_cyl(root, Vector3(dx * px_off, leg_h - 0.18, dz * pz_off), 0.30, 0.38, 0.34, mat_brass, Vector3.ZERO, 12)

		add_box(root, Vector3(0.0, leg_h - 0.16, -pz_off), Vector3(sx - 0.3, 0.26, 0.22), mat_dark_steel)
		add_box(root, Vector3(0.0, leg_h - 0.16, pz_off), Vector3(sx - 0.3, 0.26, 0.22), mat_dark_steel)
		add_box(root, Vector3(-px_off, leg_h - 0.16, 0.0), Vector3(0.22, 0.26, sz - 0.56), mat_dark_steel)
		add_box(root, Vector3(px_off, leg_h - 0.16, 0.0), Vector3(0.22, 0.26, sz - 0.56), mat_dark_steel)

		add_box(root, Vector3(0.0, elev - deck_thick * 0.5, 0.0), Vector3(sx, deck_thick, sz), mat_deck_plate)
		add_box(root, Vector3(0.0, elev + 0.045, -sz * 0.5 + 0.06), Vector3(sx, 0.09, 0.12), mat_hazard)
		add_box(root, Vector3(0.0, elev + 0.045, sz * 0.5 - 0.06), Vector3(sx, 0.09, 0.12), mat_hazard)
		add_box(root, Vector3(-sx * 0.5 + 0.06, elev + 0.045, 0.0), Vector3(0.12, 0.09, sz - 0.26), mat_hazard)
		add_box(root, Vector3(sx * 0.5 - 0.06, elev + 0.045, 0.0), Vector3(0.12, 0.09, sz - 0.26), mat_hazard)

		var g_count := int(sx / 1.1)
		for gi in range(1, g_count):
			var gx: float = -sx * 0.5 + float(gi) * (sx / float(g_count))
			add_box(root, Vector3(gx, elev + 0.022, 0.0), Vector3(0.05, 0.044, sz - 0.32), mat_dark_steel)

		var ramp_side := str(t.get("ramp_side", "south"))
		var rail_z_sign: float = 1.0 if ramp_side == "south" else -1.0

		var steps := 7
		var stair_w := 1.45
		var stair_run := 1.65
		var stair_x: float = (-sx * 0.24) if ramp_side == "south" else (sx * 0.24)
		for si in range(steps):
			var frac: float = float(si + 1) / float(steps)
			var step_y: float = elev * frac * 0.5
			var step_h: float = elev * frac
			var dist_out: float = stair_run * (1.0 - float(si) / float(steps))
			var step_z: float = rail_z_sign * (sz * 0.5 + dist_out - 0.12)
			add_box(root, Vector3(stair_x, step_y, step_z), Vector3(stair_w, step_h, stair_run / float(steps) + 0.02), mat_dark_steel)
			add_box(root, Vector3(stair_x, step_h + 0.025, step_z), Vector3(stair_w - 0.08, 0.04, 0.18), mat_brass)

		# HD-2D Signature Flaming Gothic Braziers on Stronghold Balcony Corners!
		var is_south_stronghold: bool = (center_z > 0.0)
		var brazier_col := Color(1.0, 0.58, 0.16) if is_south_stronghold else Color(0.62, 0.96, 0.24)
		add_flaming_brazier(root, Vector3(-sx * 0.5 + 0.28, elev + 0.06, -rail_z_sign * (sz * 0.5 - 0.28)), brazier_col, 5.2)
		add_flaming_brazier(root, Vector3(sx * 0.5 - 0.28, elev + 0.06, rail_z_sign * (sz * 0.5 - 0.28)), Color(1.0, 0.62, 0.18), 4.8)

	elif kind == "heavy_wall":
		add_box(root, Vector3(0.0, elev * 0.5, 0.0), Vector3(sx, elev, sz), mat_ferrocrete)
		add_box(root, Vector3(0.0, 0.19, 0.0), Vector3(sx + 0.14, 0.42, sz + 0.14), mat_dark_steel)
		add_box(root, Vector3(0.0, elev + 0.05, 0.0), Vector3(sx + 0.08, 0.10, sz + 0.08), mat_heavy_trim)

		var merlons := maxi(2, int(sx / 0.95))
		for mi_idx in range(merlons):
			var mx: float = -sx * 0.5 + (float(mi_idx) + 0.5) * (sx / float(merlons))
			var buttress_h: float = elev - 0.42
			add_box(root, Vector3(mx, 0.40 + buttress_h * 0.5, 0.0), Vector3(0.22, buttress_h, sz + 0.20), mat_dark_steel)
			if mi_idx % 2 == 0:
				add_box(root, Vector3(mx, elev + 0.30, 0.0), Vector3(0.46, 0.44, sz + 0.06), mat_ferrocrete)
				add_cone(root, Vector3(mx, elev + 0.64, 0.0), 0.12, 0.24, mat_brass)

	elif kind == "heavy_low":
		add_box(root, Vector3(0.0, elev * 0.5, 0.0), Vector3(sx, elev, sz), mat_ruin_stone)
		add_box(root, Vector3(0.0, 0.14, 0.0), Vector3(sx + 0.12, 0.32, sz + 0.12), mat_dark_steel)
		add_box(root, Vector3(0.0, elev + 0.04, 0.0), Vector3(sx + 0.05, 0.08, sz + 0.05), mat_violet_trim)

		var long_z: bool = sz > sx
		var span: float = sz if long_z else sx
		for pi in range(3):
			var offset: float = -span * 0.42 + float(pi) * (span * 0.42)
			var extra_h: float = 0.55 if pi == 1 else 0.25
			var p_pos := Vector3(0.0, (elev + extra_h) * 0.5, offset) if long_z else Vector3(offset, (elev + extra_h) * 0.5, 0.0)
			add_cyl(root, p_pos, 0.28, 0.22, elev + extra_h, mat_dark_steel, Vector3.ZERO, 10)
			var spire_pos := Vector3(p_pos.x, elev + extra_h + 0.18, p_pos.z)
			add_cone(root, spire_pos, 0.22, 0.36, mat_brass)
			if pi == 1:
				add_flaming_brazier(root, Vector3(p_pos.x, elev + extra_h + 0.26, p_pos.z), Color(1.0, 0.56, 0.20), 5.5)

	elif kind == "light":
		var long_x: bool = sx >= sz
		add_box(root, Vector3(0.0, 0.18, 0.0), Vector3(sx, 0.36, sz + 0.14 if long_x else sz), mat_dark_steel)
		var rot_shield := Vector3(16.0, 0.0, 0.0) if long_x else Vector3(0.0, 0.0, 16.0)
		add_box(root, Vector3(0.0, elev * 0.58, 0.0), Vector3(sx, elev * 0.85, sz * 0.65), mat_ferrocrete, rot_shield)
		add_box(root, Vector3(0.0, elev, 0.0), Vector3(sx + 0.04, 0.08, 0.14 if long_x else sz + 0.04), mat_brass)
		for side_s in [-1.0, 1.0]:
			var end_p := Vector3(side_s * (sx * 0.5 - 0.18), 0.42, 0.0) if long_x else Vector3(0.0, 0.42, side_s * (sz * 0.5 - 0.18))
			add_cyl(root, end_p, 0.22, 0.20, 0.84, mat_brass, Vector3.ZERO, 14)

	return root


static func build_objective_beacon(parent: Node3D, pos_3d: Vector3) -> Dictionary:
	var root := Node3D.new()
	root.position = pos_3d
	parent.add_child(root)

	var mat_pad := make_pbr(Color(0.12, 0.14, 0.18), 0.78, 0.32)
	var mat_brass := make_pbr(Color(0.85, 0.68, 0.30), 0.86, 0.28)
	var ring_mat := make_holo(Color(0.88, 0.72, 0.32), 0.48, 1.8, 0)
	var spire_mat := make_holo(Color(0.95, 0.80, 0.35), 0.78, 2.5, 2)

	add_cyl(root, Vector3(0.0, 0.04, 0.0), 0.787, 0.70, 0.10, mat_pad, Vector3.ZERO, 28)
	add_torus(root, Vector3(0.0, 0.07, 0.0), 0.66, 0.76, mat_brass)
	add_cyl(root, Vector3(0.0, 0.19, 0.0), 0.22, 0.12, 0.28, mat_brass, Vector3.ZERO, 16)
	var orb := add_sphere(root, Vector3(0.0, 0.52, 0.0), 0.14, spire_mat)
	var holo_cone := add_cone(root, Vector3(0.0, 0.85, 0.0), 0.28, 0.55, ring_mat, Vector3(180.0, 0.0, 0.0), 0.02)
	var ctrl_ring := add_torus(root, Vector3(0.0, 0.05, 0.0), 1.73, 1.80, ring_mat)

	return {
		"root": root,
		"ring_mat": ring_mat,
		"spire_mat": spire_mat,
		"orb": orb,
		"holo_cone": holo_cone,
		"ctrl_ring": ctrl_ring,
	}


# ==============================================================================
# 2. HD-2D 360° CAMERA-FACING CHARACTER STANDEES ON SCULPTED 3D MINIATURE BASES
# ==============================================================================

static func build_operative_miniature(op: Dictionary) -> Dictionary:
	var root := Node3D.new()
	var body_pivot := Node3D.new()
	root.add_child(body_pivot)

	var base_r: float = float(op["base_radius"])
	var is_aod: bool = str(op["side"]) == "A"

	# --- 1. Finely Sculpted 3D Tabletop Wargame Miniature Base ---
	var mat_base_skirt := make_pbr(Color(0.06, 0.07, 0.09), 0.35, 0.55)
	var mat_base_brass := make_pbr(Color(0.76, 0.58, 0.26) if is_aod else Color(0.62, 0.50, 0.24), 0.86, 0.30)
	var mat_base_slate := make_pbr(Color(0.18, 0.20, 0.25) if is_aod else Color(0.18, 0.21, 0.15), 0.18, 0.84)
	var base_h := 0.20

	add_cyl(root, Vector3(0.0, base_h * 0.5, 0.0), base_r, base_r * 0.90, base_h, mat_base_skirt, Vector3.ZERO, 32)
	add_torus(root, Vector3(0.0, base_h * 0.88, 0.0), base_r * 0.84, base_r * 0.93, mat_base_brass)
	add_cyl(root, Vector3(0.0, base_h + 0.012, 0.0), base_r * 0.85, base_r * 0.83, 0.035, mat_base_slate, Vector3.ZERO, 28)

	# Scenic miniature rubble stones on the 3D base top!
	add_box(root, Vector3(-base_r * 0.38, base_h + 0.04, base_r * 0.26), Vector3(0.20, 0.06, 0.15), mat_base_slate, Vector3(6, 24, -4))
	add_box(root, Vector3(base_r * 0.36, base_h + 0.035, -base_r * 0.22), Vector3(0.16, 0.05, 0.14), mat_base_brass, Vector3(-5, -18, 8))

	# Glowing Faction Base Aura Ring & Active Selection Halo (distinct Y & render_priority -> zero flicker!)
	var fac_col := Color(0.24, 0.62, 1.0) if is_aod else Color(0.58, 0.90, 0.22)
	var rim_mat := make_holo(fac_col, 0.85, 2.0, 1)
	var faction_ring := add_torus(root, Vector3(0.0, 0.062, 0.0), base_r * 0.96, base_r * 1.06, rim_mat)

	var sel_mat := make_holo(Color(1.0, 0.84, 0.26), 0.92, 2.6, 2)
	var sel_ring := add_torus(root, Vector3(0.0, 0.075, 0.0), base_r * 1.13, base_r * 1.27, sel_mat)
	sel_ring.visible = false

	# Order Indicator Gem on Base Rim
	var order_mat := make_holo(Color(0.25, 0.68, 1.0), 0.92, 2.2, 3)
	var order_cone := add_cone(body_pivot, Vector3(0.0, base_h + 0.09, base_r * 0.80), 0.13, 0.24, order_mat, Vector3(90.0, 0.0, 0.0))

	# --- 2. Octopath Traveler Style 2D Character Standee (360° Billboard + 3D Shadow!) ---
	var standee_path := str(op.get("standee", ""))
	var tex: Texture2D = KTData.load_tex(standee_path)
	if tex == null:
		tex = KTData.load_tex(str(op.get("portrait", "")))

	var standee_holder := Node3D.new()
	standee_holder.position = Vector3(0.0, base_h + 0.02, 0.0)
	root.add_child(standee_holder)

	var tex_aspect: float = 2.15
	if tex != null and tex.get_height() > 0:
		tex_aspect = float(tex.get_width()) / float(tex.get_height())
	var quad_h: float = 1.48 if base_r >= 0.70 else 1.38
	var quad_w: float = clampf(quad_h * tex_aspect, 1.60, 3.28)

	var qm := QuadMesh.new()
	qm.size = Vector2(quad_w, quad_h)
	# Pivot right at the bottom pedestal clasp so the standee stays welded to the exact
	# center of the 3D miniature base at EVERY 360° yaw & tilt angle!
	qm.center_offset = Vector3(0.0, quad_h * 0.486, 0.0)

	var smat := StandardMaterial3D.new()
	smat.albedo_texture = tex
	smat.albedo_color = Color(0.86, 0.87, 0.90, 1.0)
	smat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
	smat.alpha_scissor_threshold = 0.42
	smat.cull_mode = BaseMaterial3D.CULL_DISABLED
	smat.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	# Full camera-facing billboard: from ANY 360° horizontal orbit or pitch angle, you ALWAYS see
	# the crisp front of the character standee, AND in the directional shadow pass it casts a real 3D silhouette shadow!
	smat.billboard_mode = BaseMaterial3D.BILLBOARD_ENABLED
	smat.billboard_keep_scale = true
	smat.roughness = 0.90
	smat.metallic = 0.0
	# Balanced backlight + subtle self-illumination keeps painted Eavy-Metal colors deep and rich without overexposure!
	smat.backlight_enabled = true
	smat.backlight = Color(0.36, 0.36, 0.40)
	if tex != null:
		smat.emission_enabled = true
		smat.emission_texture = tex
		smat.emission_energy_multiplier = 0.10

	var standee_mi := MeshInstance3D.new()
	standee_mi.mesh = qm
	standee_mi.material_override = smat
	standee_mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_DOUBLE_SIDED
	standee_holder.add_child(standee_mi)

	return {
		"root": root,
		"pivot": body_pivot,
		"standee_holder": standee_holder,
		"standee_mesh": standee_mi,
		"standee_mat": smat,
		"rim_mat": rim_mat,
		"faction_ring": faction_ring,
		"sel_ring": sel_ring,
		"sel_mat": sel_mat,
		"order_mat": order_mat,
		"order_cone": order_cone,
	}
