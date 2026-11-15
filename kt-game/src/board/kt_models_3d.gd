class_name KTModels3D
extends RefCounted

## HD-2D (Octopath Traveler Style) 3D Killzone Architecture & 360° Billboard Standee Factory:
## - Multi-elevation 3D Volkus Strongholds (+3.5" 2-story decks, stairs, 4.2" Bunker Walls, Gothic Ruins, Barricades)
## - Atmospheric flickering 3D Gothic Fire Braziers & Nurgle Censers with live 3D flame/ember particles
## - Sculpted 3D tabletop miniature bases paired with razor-sharp 360° camera-facing 2D Character Standees
##   extracted from official Kill Team rule cards, casting real 3D silhouette shadows!

static var _cached_soft_dot_tex: ImageTexture = null
static var _cached_battlemat_tex: ImageTexture = null
## Set by killzone_view before build_tabletop_base(): res:// path of the map's official mat image.
static var _battlefield_mat_path: String = ""


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


static var _tex_mat_cache: Dictionary = {}


## Textured PBR material from the CC0 ambientCG sets in assets/textures (see LICENSE.md there).
## `tint` multiplies albedo so one texture set can serve several colour variants; `uv_scale` is
## in repeats per world unit (1 unit = 1 inch), so 0.5 means the texture tiles every 2".
static func make_textured_pbr(
	set_name: String,
	tint: Color = Color(1, 1, 1),
	uv_scale: float = 0.5,
	roughness_mul: float = 1.0,
	metallic_override: float = -1.0
) -> StandardMaterial3D:
	var key := "%s|%s|%.3f|%.2f|%.2f" % [set_name, tint.to_html(false), uv_scale, roughness_mul, metallic_override]
	if _tex_mat_cache.has(key):
		return _tex_mat_cache[key]
	var base := "res://assets/textures/%s_" % set_name
	var m := StandardMaterial3D.new()
	m.albedo_color = tint
	m.albedo_texture = KTData.load_tex(base + "albedo.jpg")
	var nrm := KTData.load_tex(base + "normal.jpg")
	if nrm != null:
		m.normal_enabled = true
		m.normal_texture = nrm
		m.normal_scale = 1.0
	var rgh := KTData.load_tex(base + "rough.jpg")
	if rgh != null:
		m.roughness_texture = rgh
		m.roughness_texture_channel = BaseMaterial3D.TEXTURE_CHANNEL_RED
	m.roughness = clampf(roughness_mul, 0.0, 1.0)
	var mtl := KTData.load_tex(base + "metal.jpg")
	if metallic_override >= 0.0:
		m.metallic = metallic_override
	elif mtl != null:
		m.metallic = 1.0
		m.metallic_texture = mtl
		m.metallic_texture_channel = BaseMaterial3D.TEXTURE_CHANNEL_RED
	else:
		m.metallic = 0.05
	var ao := KTData.load_tex(base + "ao.jpg")
	if ao != null:
		m.ao_enabled = true
		m.ao_texture = ao
		m.ao_texture_channel = BaseMaterial3D.TEXTURE_CHANNEL_RED
	m.uv1_scale = Vector3(uv_scale, uv_scale, uv_scale)
	m.uv1_triplanar = true
	m.uv1_world_triplanar = true
	m.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	_tex_mat_cache[key] = m
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
	# Transparent tactical overlay: drawn on top of the textured flagstone ground.
	var img := Image.create(tex_w, tex_h, true, Image.FORMAT_RGBA8)
	img.fill(Color(0.0, 0.0, 0.0, 0.0))

	# 1. Central roadway: a slightly darker translucent band with brass edge trims
	var road_y0 := int((height_in * 0.5 - 1.9) * ppi)
	var road_h := int(3.8 * ppi)
	img.fill_rect(Rect2i(int(0.3 * ppi), road_y0, int((width_in - 0.6) * ppi), road_h), Color(0.02, 0.02, 0.03, 0.28))
	img.fill_rect(Rect2i(int(0.4 * ppi), road_y0, int((width_in - 0.8) * ppi), 3), Color(0.56, 0.43, 0.20, 0.85))
	img.fill_rect(Rect2i(int(0.4 * ppi), road_y0 + road_h - 3, int((width_in - 0.8) * ppi), 3), Color(0.56, 0.43, 0.20, 0.85))

	# 2. Deployment zone tints (North = Nurgle green, South = Imperial blue)
	var rect_b := Rect2i(int(dz_b.position.x * ppi), int(dz_b.position.y * ppi), int(dz_b.size.x * ppi), int(dz_b.size.y * ppi)).intersection(Rect2i(0, 0, tex_w, tex_h))
	img.fill_rect(rect_b, Color(0.30, 0.56, 0.14, 0.11))
	var rect_a := Rect2i(int(dz_a.position.x * ppi), int(dz_a.position.y * ppi), int(dz_a.size.x * ppi), int(dz_a.size.y * ppi)).intersection(Rect2i(0, 0, tex_w, tex_h))
	img.fill_rect(rect_a, Color(0.14, 0.40, 0.84, 0.12))

	# 3. 1" tactical grid (faint) & 6" brass lines (stronger) — kept visible on purpose: still a tabletop game
	for ix in range(1, int(width_in)):
		var px_x := ix * ppi
		var is_maj: bool = (ix % 6 == 0)
		var gcol := Color(0.72, 0.56, 0.26, 0.70) if is_maj else Color(0.80, 0.84, 0.92, 0.16)
		var half_t: int = 1 if is_maj else 0
		for dx in range(-half_t, half_t + 1):
			var xx := clampi(px_x + dx, 0, tex_w - 1)
			for y in range(tex_h):
				img.set_pixel(xx, y, img.get_pixel(xx, y).blend(gcol))

	for iz in range(1, int(height_in)):
		var px_y := iz * ppi
		var is_maj_z: bool = (iz == 11)
		var gcol_z := Color(0.76, 0.60, 0.28, 0.80) if is_maj_z else Color(0.80, 0.84, 0.92, 0.16)
		var half_tz: int = 1 if is_maj_z else 0
		for dy in range(-half_tz, half_tz + 1):
			var yy := clampi(px_y + dy, 0, tex_h - 1)
			for x in range(tex_w):
				img.set_pixel(x, yy, img.get_pixel(x, yy).blend(gcol_z))

	# 4. Crisp deployment zone boundary lines
	var dz_b_line_y := clampi(int(dz_b.end.y * ppi), 1, tex_h - 3)
	img.fill_rect(Rect2i(0, dz_b_line_y - 1, tex_w, 3), Color(0.48, 0.80, 0.22, 0.95))
	var dz_a_line_y := clampi(int(dz_a.position.y * ppi), 1, tex_h - 3)
	img.fill_rect(Rect2i(0, dz_a_line_y - 1, tex_w, 3), Color(0.26, 0.62, 0.98, 0.95))

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
	var mat_desk := make_pbr(Color(0.060, 0.052, 0.046), 0.10, 0.88)   # dark hobby-table felt
	var mat_desk_trim := make_pbr(Color(0.36, 0.28, 0.15), 0.72, 0.42)
	var mat_plinth := make_textured_pbr("gunmetal", Color(0.40, 0.42, 0.46), 0.6, 0.9)
	var mat_brass_rim := make_pbr(Color(0.70, 0.53, 0.24), 0.80, 0.36)

	# Extended Wargaming Table Surface surrounding the Killzone Battlemat (top = -0.24, Trim top = -0.30)
	# Provides a real grounded tabletop apron where the 3D Velvet & Brass Dice Tray sits!
	add_box(parent, Vector3(0.0, -0.84, 0.0), Vector3(width_in + 36.0, 1.20, height_in + 30.0), mat_desk)
	add_box(parent, Vector3(0.0, -0.92, 0.0), Vector3(width_in + 37.2, 1.24, height_in + 31.2), mat_desk_trim)

	# Heavy Bevelled Wargaming Table Plinth — strictly stepped in Y so NO two top faces ever touch!
	# Desk top = -0.24, Plinth top = -0.16, Brass Rim top = -0.07, Battlemat top = 0.000!
	add_box(parent, Vector3(0.0, -0.82, 0.0), Vector3(width_in + 1.9, 1.32, height_in + 1.9), mat_plinth)
	add_box(parent, Vector3(0.0, -0.22, 0.0), Vector3(width_in + 0.62, 0.30, height_in + 0.62), mat_brass_rim)

	# Battlemat = textured ruined flagstone ground (top = 0.000) + a separate translucent tactical
	# overlay plane carrying the 1" grid, 6" brass lines, deployment zones and the central roadway.
	# The grid stays visible (this is still a tabletop game) but now sits ON a surface instead of BEING it.
	# Ground: the official Volkus battlefield mat when present (one image covering the 30x22 board),
	# else the generic tiled flagstone.
	var mat_ground: StandardMaterial3D
	var official_mat: Texture2D = KTData.load_tex(str(_battlefield_mat_path)) if not _battlefield_mat_path.is_empty() else null
	if official_mat != null:
		mat_ground = StandardMaterial3D.new()
		mat_ground.albedo_texture = official_mat
		mat_ground.albedo_color = Color(0.86, 0.86, 0.86)
		mat_ground.roughness = 0.92
		mat_ground.metallic = 0.0
		mat_ground.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
		# PlaneMesh UVs run 0..1 across; the mat image is already the full board, so no tiling.
		# Checked against the map-4 rule rects: the mat's printed rubble heaps line up with the
		# terrain only when the image is flipped left-right (the mat is authored in TTS's mirrored
		# frame, like the layout JSON). Flip U.
		mat_ground.uv1_scale = Vector3(-1.0, 1.0, 1.0)
		mat_ground.uv1_offset = Vector3(1.0, 0.0, 0.0)
	else:
		mat_ground = make_textured_pbr("flagstone", Color(0.46, 0.47, 0.50), 0.5, 1.0, 0.0)
		mat_ground.uv1_triplanar = false
		mat_ground.uv1_world_triplanar = false
		mat_ground.uv1_scale = Vector3(width_in * 0.5, height_in * 0.5, 1.0)

	var mat_plane := PlaneMesh.new()
	mat_plane.size = Vector2(width_in, height_in)
	var ground_mi := MeshInstance3D.new()
	ground_mi.mesh = mat_plane
	ground_mi.material_override = mat_ground
	ground_mi.position = Vector3(0.0, 0.0, 0.0)
	parent.add_child(ground_mi)

	var mat_overlay := StandardMaterial3D.new()
	mat_overlay.albedo_texture = _get_battlemat_tex(width_in, height_in, dz_a, dz_b)
	mat_overlay.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat_overlay.depth_draw_mode = BaseMaterial3D.DEPTH_DRAW_DISABLED
	mat_overlay.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat_overlay.cull_mode = BaseMaterial3D.CULL_DISABLED
	mat_overlay.render_priority = -1
	mat_overlay.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	var overlay_plane := PlaneMesh.new()
	overlay_plane.size = Vector2(width_in, height_in)
	var overlay_mi := MeshInstance3D.new()
	overlay_mi.mesh = overlay_plane
	overlay_mi.material_override = mat_overlay
	overlay_mi.position = Vector3(0.0, 0.006, 0.0)
	overlay_mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	overlay_mi.set_meta("kt_grid_overlay", true)
	overlay_mi.visible = bool(KTSettings.get_value("hud/show_inch_grid"))
	parent.add_child(overlay_mi)

	# Solid Side Skirt under the Battlemat (`y = -0.08..0.0`? Keep top at `-0.015` so never coplanar with PlaneMesh!)
	add_box(parent, Vector3(0.0, -0.05, 0.0), Vector3(width_in, 0.07, height_in), mat_plinth)

	# Corner Imperial Brass Studs outside the play area
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			var cx: float = sx * (width_in * 0.5 + 0.55)
			var cz: float = sz * (height_in * 0.5 + 0.55)
			add_cyl(parent, Vector3(cx, 0.06, cz), 0.38, 0.28, 0.28, mat_brass_rim)


## ------------------------------------------------------------------------------------------
## PAINTED 3D MINIATURES (assets/minis/manifest.json, geometry + diffuse only from the TTS pack)
## ------------------------------------------------------------------------------------------
static var _minis_manifest: Dictionary = {}
static var _minis_loaded: bool = false
static var _mini_mesh_cache: Dictionary = {}


static func _mini_entry(op_id: String) -> Dictionary:
	if not _minis_loaded:
		_minis_loaded = true
		var p := "res://assets/minis/manifest.json"
		if FileAccess.file_exists(p):
			var parsed = JSON.parse_string(FileAccess.get_file_as_string(p))
			if typeof(parsed) == TYPE_DICTIONARY:
				_minis_manifest = parsed.get("minis", {})
	return _minis_manifest.get(op_id, {})


## Builds the painted miniature on top of the sculpted base. Returns {} when no model exists for
## this operative (caller falls back to the acrylic standee). Dictionary keys mirror the standee
## path: "standee_holder" (lift/breath), "tilt_pivot" (recoil/topple), "standee_mat" (hit flash).
static func _build_painted_mini(root: Node3D, body_pivot: Node3D, op: Dictionary, base_r: float, base_h: float) -> Dictionary:
	var e := _mini_entry(str(op.get("op_id", "")))
	if e.is_empty():
		return {}
	var mesh_path := str(e.get("mesh", ""))
	if mesh_path.is_empty() or not ResourceLoader.exists(mesh_path):
		return {}
	var mesh: Mesh = _mini_mesh_cache.get(mesh_path, null)
	if mesh == null:
		mesh = load(mesh_path) as Mesh
		if mesh == null:
			return {}
		_mini_mesh_cache[mesh_path] = mesh

	# Holder sits on the base top; facing follows body_pivot (so the mini turns toward its target
	# like the order cone does) instead of billboarding to the camera.
	var holder := Node3D.new()
	holder.position = Vector3(0.0, base_h + 0.02, 0.0)
	body_pivot.add_child(holder)
	var tilt := Node3D.new()
	holder.add_child(tilt)

	# TTS places the child mesh with its own yaw relative to the base; the mesh's -Z is "forward" in
	# Blender exports, Godot's forward is also -Z, so apply the TTS child yaw directly. Scale the model
	# so its base footprint matches the operative's rule base_radius (minis were sculpted for their
	# own base size; small drift is normal).
	var child_rot := float(e.get("child_rot_y", 0.0))
	var mi := MeshInstance3D.new()
	mi.mesh = mesh
	var aabb := mesh.get_aabb()
	# Lift so the lowest vertex touches the base top (some exports start at y≈0.13)
	mi.position = Vector3(0.0, -aabb.position.y, 0.0)
	mi.rotation_degrees = Vector3(0.0, child_rot, 0.0)
	var mat := StandardMaterial3D.new()
	var tex: Texture2D = KTData.load_tex(str(e.get("diffuse", "")))
	if tex != null:
		mat.albedo_texture = tex
		# TTS diffuse maps are authored for a brighter scene; lift them so painted colours read
		# under our dim gothic lighting instead of going to charcoal.
		mat.albedo_color = Color(1.25, 1.23, 1.20)
	else:
		mat.albedo_color = Color(0.5, 0.5, 0.55)
	mat.roughness = 0.58
	mat.metallic = 0.06
	mat.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	# Emission channel is what killzone_view drives for hit-flash / highlight (same as the standee)
	mat.emission_enabled = true
	mat.emission = Color(1, 1, 1)
	mat.emission_texture = tex
	mat.emission_energy_multiplier = 0.0
	mi.material_override = mat
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
	tilt.add_child(mi)

	return {
		"root": root,
		"pivot": body_pivot,
		"standee_holder": holder,
		"tilt_pivot": tilt,
		"standee_mesh": mi,
		"standee_mat": mat,
		"edge_mat": null,
		"name_label": null,
		"token_h": aabb.size.y,
		"is_model": true,
	}


## Real terrain model from the official Volkus set (assets/terrain/volkus/<slug>/model_00_root_mesh.obj
## + diffuse). `v` is one entry of map["visuals"]: pos_in (board inches), rot_y_deg, scale.
## Returns null when the mesh is missing so the caller can fall back to the procedural block.
static var _terrain_mesh_cache: Dictionary = {}

static func build_terrain_model(parent: Node3D, v: Dictionary, board_w: float, board_h: float) -> Node3D:
	var mesh_path := str(v.get("mesh", ""))
	if mesh_path.is_empty() or not ResourceLoader.exists(mesh_path):
		return null
	var mesh: Mesh = _terrain_mesh_cache.get(mesh_path, null)
	if mesh == null:
		mesh = load(mesh_path) as Mesh
		if mesh == null:
			return null
		_terrain_mesh_cache[mesh_path] = mesh

	var root := Node3D.new()
	var p_in: Vector2 = v["pos_in"]
	root.position = Vector3(p_in.x - board_w * 0.5, 0.0, p_in.y - board_h * 0.5)
	# TTS scene is mirrored in X vs the printed maps (see tools/build_volkus_map.py). The build
	# script applies R(−θ) then mirrors X. Godot composes scale then rotation (T·R·S), and a
	# mirror flips the rotation sense, so the equivalent here is scale.x = −s with rotation.y = +θ.
	# Verified on the L-shaped large_ruin: this combination overlays its rule rectangles exactly.
	var s := float(v.get("scale", 1.0))
	root.scale = Vector3(-s, s, s)
	root.rotation_degrees = Vector3(0.0, float(v.get("rot_y_deg", 0.0)), 0.0)
	parent.add_child(root)

	var mi := MeshInstance3D.new()
	mi.mesh = mesh
	var mat := StandardMaterial3D.new()
	var diff := str(v.get("diffuse", ""))
	var tex: Texture2D = KTData.load_tex(diff) if not diff.is_empty() else null
	if tex != null:
		mat.albedo_texture = tex
		mat.albedo_color = Color(0.92, 0.90, 0.88)
	else:
		mat.albedo_color = Color(0.42, 0.42, 0.45)
	mat.roughness = 0.88
	mat.metallic = 0.02
	# Mirrored scale flips winding; render both faces so walls don't vanish from inside.
	mat.cull_mode = BaseMaterial3D.CULL_DISABLED
	mat.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	mi.material_override = mat
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
	root.add_child(mi)
	return root


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

	# Painted-terrain look: real surface textures, colour-coded trims kept but muted so they read as
	# painted hazard markings on a hobby table rather than plastic block tops.
	var mat_ferrocrete := make_textured_pbr("ferrocrete", Color(0.86, 0.80, 0.72), 0.55, 1.0, 0.0)
	var mat_dark_steel := make_textured_pbr("gunmetal", Color(0.66, 0.66, 0.68), 0.80, 0.85, 0.55)
	var mat_deck_plate := make_textured_pbr("deckplate", Color(0.92, 0.88, 0.82), 0.42, 0.95, 0.30)
	var mat_brass := make_pbr(Color(0.75, 0.57, 0.26), 0.86, 0.32)
	var mat_hazard := make_textured_pbr("rust", Color(1.0, 0.78, 0.30), 0.9, 1.0, 0.2)
	var mat_heavy_trim := make_textured_pbr("rust", Color(0.80, 0.34, 0.24), 0.9, 1.0, 0.2)
	var mat_ruin_stone := make_textured_pbr("rubble_rock", Color(1.0, 0.96, 0.90), 0.45, 1.0, 0.0)
	var mat_violet_trim := make_textured_pbr("rust", Color(0.62, 0.50, 0.80), 0.9, 1.0, 0.2)
	var mat_rubble := make_textured_pbr("rubble_rock", Color(0.92, 0.90, 0.86), 1.2, 1.0, 0.0)

	# Scatter debris around the footprint so pieces feel bedded into the battlefield
	var rng := RandomNumberGenerator.new()
	rng.seed = hash(str(t.get("id", "")))
	var debris_n: int = 4 + int(sx * sz * 0.35)
	for _i in range(mini(debris_n, 14)):
		var ang: float = rng.randf() * TAU
		var dist: float = rng.randf_range(0.15, 0.55)
		var dp := Vector3(cos(ang) * (sx * 0.5 + dist), 0.0, sin(ang) * (sz * 0.5 + dist))
		var dsz := Vector3(rng.randf_range(0.18, 0.46), rng.randf_range(0.08, 0.20), rng.randf_range(0.16, 0.40))
		add_box(root, dp + Vector3(0.0, dsz.y * 0.5 - 0.01, 0.0), dsz, mat_rubble, Vector3(rng.randf_range(-8, 8), rng.randf() * 360.0, rng.randf_range(-8, 8)))

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

	# --- 2a. Painted 3D miniature (assets/minis/<op_id>/mini.obj) when available and selected ---
	# Returns the same dictionary shape as the standee path so killzone_view's animation code
	# (standee_holder lift, tilt_pivot recoil/topple, standee_mat flash) keeps working unchanged.
	var want_model: bool = str(KTSettings.get_value("board/mini_style")) == "model"
	if want_model:
		var mini := _build_painted_mini(root, body_pivot, op, base_r, base_h)
		if not mini.is_empty():
			mini["rim_mat"] = rim_mat
			mini["faction_ring"] = faction_ring
			mini["sel_ring"] = sel_ring
			mini["sel_mat"] = sel_mat
			mini["order_mat"] = order_mat
			mini["order_cone"] = order_cone
			return mini

	# --- 2b. Acrylic Standee Token (printed card between two acrylic sheets, slotted into the base) ---
	# The token turns to face the camera around Y (done manually each frame in killzone_view so that
	# the tilt pivot below can still add recoil / topple rotation), but stays standing upright.
	var standee_path := str(op.get("standee", ""))
	var tex: Texture2D = KTData.load_tex(standee_path)
	if tex == null:
		tex = KTData.load_tex(str(op.get("portrait", "")))

	var standee_holder := Node3D.new()
	standee_holder.position = Vector3(0.0, base_h + 0.02, 0.0)
	root.add_child(standee_holder)

	# Tilt pivot: receives hit recoil (lean back) and death (topple over) animations.
	var tilt_pivot := Node3D.new()
	standee_holder.add_child(tilt_pivot)

	var tex_aspect: float = 1.0
	if tex != null and tex.get_height() > 0:
		tex_aspect = float(tex.get_width()) / float(tex.get_height())
	var token_h: float = 1.72 if base_r >= 0.70 else 1.58
	var token_w: float = token_h * tex_aspect
	var token_thick := 0.045

	# Printed face (front & back share the same artwork)
	var qm := QuadMesh.new()
	qm.size = Vector2(token_w, token_h)
	qm.center_offset = Vector3(0.0, token_h * 0.5 - 0.10, 0.0) # peg sinks ~0.10 into the base

	var smat := StandardMaterial3D.new()
	smat.albedo_texture = tex
	smat.albedo_color = Color(0.92, 0.93, 0.95, 1.0)
	smat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
	smat.alpha_scissor_threshold = 0.40
	smat.cull_mode = BaseMaterial3D.CULL_DISABLED
	smat.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	smat.roughness = 0.22
	smat.metallic = 0.05
	smat.specular_mode = BaseMaterial3D.SPECULAR_SCHLICK_GGX
	smat.backlight_enabled = true
	smat.backlight = Color(0.30, 0.30, 0.34)
	if tex != null:
		smat.emission_enabled = true
		smat.emission_texture = tex
		smat.emission_energy_multiplier = 0.08

	var standee_mi := MeshInstance3D.new()
	standee_mi.mesh = qm
	standee_mi.material_override = smat
	standee_mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_DOUBLE_SIDED
	tilt_pivot.add_child(standee_mi)

	# Acrylic edge: a thin dark box slightly behind the face gives the token visible thickness
	# and a solid silhouette for shadows. It also Y-billboards (same material flag) so it stays aligned.
	var edge_mat := StandardMaterial3D.new()
	edge_mat.albedo_color = Color(0.05, 0.055, 0.07, 0.85)
	edge_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	edge_mat.roughness = 0.15
	edge_mat.metallic = 0.1
	var edge_qm := QuadMesh.new()
	edge_qm.size = Vector2(token_w * 0.985, token_h * 0.985)
	edge_qm.center_offset = Vector3(0.0, token_h * 0.5 - 0.10, -token_thick)
	var edge_mi := MeshInstance3D.new()
	edge_mi.mesh = edge_qm
	edge_mi.material_override = edge_mat
	edge_mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	tilt_pivot.add_child(edge_mi)

	# Name plate rendered in-engine on the token's bottom band
	# Plate text = short role (e.g. "连长"), not the full operative name: at table distance a 7-glyph
	# name is unreadable on a 1.7" token, and the full name already lives in the floating HUD tag.
	var name_lbl := Label3D.new()
	var role_txt := str(op.get("role", ""))
	name_lbl.text = role_txt if not role_txt.is_empty() else str(op.get("name", ""))
	name_lbl.font = KTTheme.get_cjk_font()
	# Large glyphs at a small pixel_size: same world size, far sharper when seen from table distance.
	name_lbl.font_size = 128
	name_lbl.pixel_size = 0.0020
	name_lbl.outline_size = 18
	name_lbl.autowrap_mode = TextServer.AUTOWRAP_OFF
	name_lbl.width = token_w / 0.0020 * 0.90
	name_lbl.modulate = Color(0.96, 0.92, 0.80)
	name_lbl.outline_modulate = Color(0.05, 0.05, 0.07)
	name_lbl.billboard = BaseMaterial3D.BILLBOARD_DISABLED
	name_lbl.no_depth_test = false
	name_lbl.double_sided = true
	name_lbl.alpha_cut = Label3D.ALPHA_CUT_DISCARD
	name_lbl.render_priority = 2
	# Texture layout (560x560): body = 516px (peg 44 below), frame 22, plate band 84 above the frame.
	# Label3D's local Y grows upward while texture rows grow downward, so measure from the bottom.
	var plate_center_from_bottom_px: float = 44.0 + 22.0 + 84.0 * 0.5
	name_lbl.position = Vector3(0.0, token_h * (plate_center_from_bottom_px / 560.0) - 0.10, 0.004)
	tilt_pivot.add_child(name_lbl)

	# Soft contact shadow disc under the token so it reads as sitting on the base
	var contact_mat := StandardMaterial3D.new()
	contact_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	contact_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	contact_mat.albedo_texture = get_soft_particle_tex()
	contact_mat.albedo_color = Color(0.0, 0.0, 0.0, 0.55)
	contact_mat.cull_mode = BaseMaterial3D.CULL_DISABLED
	var contact_qm := QuadMesh.new()
	contact_qm.size = Vector2(base_r * 1.5, base_r * 0.9)
	var contact_mi := MeshInstance3D.new()
	contact_mi.mesh = contact_qm
	contact_mi.material_override = contact_mat
	contact_mi.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
	contact_mi.position = Vector3(0.0, base_h + 0.052, 0.0)
	contact_mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	root.add_child(contact_mi)

	return {
		"root": root,
		"pivot": body_pivot,
		"standee_holder": standee_holder,
		"tilt_pivot": tilt_pivot,
		"standee_mesh": standee_mi,
		"standee_mat": smat,
		"edge_mat": edge_mat,
		"name_label": name_lbl,
		"token_h": token_h,
		"rim_mat": rim_mat,
		"faction_ring": faction_ring,
		"sel_ring": sel_ring,
		"sel_mat": sel_mat,
		"order_mat": order_mat,
		"order_cone": order_cone,
	}
