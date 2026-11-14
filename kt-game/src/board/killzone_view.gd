class_name KillzoneView
extends Control

## 2.55D Isometric 3D Tactical Killzone Viewport & Spatial Controller:
## - True 3D multi-elevation Volkus architecture (+3.5" 2-story Strongholds with stairs, 4.2" Heavy Bunker Walls,
##   2.2" Gothic Ruins, 1.0" sloped Light Barricades) with real-time directional shadows & SSAO
## - Procedural 3D Space Marine & Plague Marine miniatures built from cylinders, cones, spheres, pauldrons & wargear
## - Telephoto 2.55D Isometric Camera (Q/E or Right-Drag Orbit & Tilt, WASD/Middle-Drag Pan, Wheel Zoom, F Reset)
## - Elevation-aware 3D Ray Picking (snaps directly onto +3.5" 2nd-floor Vantage platforms!)
## - 3D ImmediateMesh climbing move ruler, 3D diagonal LoS laser beams & volumetric Cover Cone + crisp 2D HUD projection

signal operative_clicked(uid: String)
signal ground_move_committed(waypoints: Array[Vector2])
signal shoot_target_clicked(defender_uid: String, shot_eval: Dictionary)
signal fight_target_clicked(defender_uid: String)
signal tabletop_die_clicked(pool_key: String, die_idx: int)
signal hover_info_changed(info_text: String)
signal cancel_interaction_requested()

const Models3D = preload("res://src/board/kt_models_3d.gd")
const Vfx3D = preload("res://src/board/kt_vfx_3d.gd")
const Dice3D = preload("res://src/board/kt_dice_3d.gd")

var engine: KTEngine = null
var font: Font = null

# Interaction state (public API compatible with MainGame)
var selected_uid: String = ""
var hovered_uid: String = ""
var hovered_board_inches: Vector2 = Vector2(15.0, 11.0)
var interaction_mode: String = "idle" # "idle" | "move" | "shoot" | "fight"
var move_action_kind: String = "reposition"
var move_waypoints: Array[Vector2] = []
var active_shoot_weapon: Dictionary = {}
var cached_shot_evals: Dictionary = {}

# 3D Scene Nodes
var _svc: SubViewportContainer
var _sv: SubViewport
var _world_root: Node3D
var _cam_pivot: Node3D
var _cam: Camera3D
var _static_map_root: Node3D
var _ops_root: Node3D
var _vfx: KTVfx3D
var _dice_3d: KTDice3D
var _table_props: KTTableProps3D = null
var _imm_lines: ImmediateMesh
var _imm_tris: ImmediateMesh
var _mat_lines: StandardMaterial3D
var _mat_tris: StandardMaterial3D
var _hud_overlay: Control

# 2.55D Isometric Camera State
var cam_focus: Vector3 = Vector3(0.0, 0.8, 0.6)
var target_focus: Vector3 = Vector3(0.0, 0.8, 0.6)
var cam_yaw_deg: float = -24.0
var target_yaw_deg: float = -24.0
var cam_pitch_deg: float = 42.0
var target_pitch_deg: float = 42.0
var cam_dist: float = 52.0
var target_dist: float = 52.0

# Mouse Orbit / Pan tracking
var _orbit_btn_down: bool = false
var _orbit_dragged: bool = false
var _orbit_start_pos: Vector2 = Vector2.ZERO
var _mid_pan_down: bool = false
var _last_mouse_pos: Vector2 = Vector2.ZERO

# Dynamic 3D Visual State
var _op_nodes: Dictionary = {}        # uid -> Dictionary from KTModels3D.build_operative_miniature
var _obj_nodes: Dictionary = {}       # obj_id -> Dictionary from KTModels3D.build_objective_beacon
var _visual_pos_3d: Dictionary = {}   # uid -> Vector3 (world X, elevation Y, world Z)
var _visual_facing_y: Dictionary = {} # uid -> float radians
var _op_lunge_offset: Dictionary = {} # uid -> Vector3 combat lunge/recoil offset
var _op_hit_flash: Dictionary = {}    # uid -> float [0..1] hit flash intensity
var _op_fall_t: Dictionary = {}       # uid -> float [0..1] topple animation progress for incapacitated tokens
var _fallen_markers: Dictionary = {}  # uid -> Node3D casualty marker left on the battlemat
var _tracers_3d: Array[Dictionary] = []
var _float_texts_3d: Array[Dictionary] = []
var _pulse_time: float = 0.0
var _shake_strength: float = 0.0
var _built_map_id: String = ""

var _bg_tex: Texture2D = null
var _tex_engage_ready: Texture2D = null
var _tex_engage_spent: Texture2D = null
var _tex_conceal_ready: Texture2D = null
var _tex_conceal_spent: Texture2D = null


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	font = KTTheme.get_cjk_font()

	_bg_tex = KTData.load_tex("res://assets/ui/kt_battlefield.png")
	_tex_engage_ready = KTData.load_tex("res://assets/ui/order_engage_ready.png")
	_tex_engage_spent = KTData.load_tex("res://assets/ui/order_engage_spent.png")
	_tex_conceal_ready = KTData.load_tex("res://assets/ui/order_conceal_ready.png")
	_tex_conceal_spent = KTData.load_tex("res://assets/ui/order_conceal_spent.png")

	_build_3d_viewport()

	_hud_overlay = Control.new()
	_hud_overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_hud_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_hud_overlay.draw.connect(_draw_projected_hud)
	add_child(_hud_overlay)


func _build_3d_viewport() -> void:
	_svc = SubViewportContainer.new()
	_svc.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_svc.stretch = true
	_svc.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_svc)

	_sv = SubViewport.new()
	_sv.own_world_3d = true
	_sv.msaa_3d = Viewport.MSAA_4X
	_sv.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	_svc.add_child(_sv)

	_world_root = Node3D.new()
	_sv.add_child(_world_root)

	# 1. HD-2D (Octopath Traveler Style) Atmospheric Environment (ACES + Warm HDR Bloom + SSAO)
	var we := WorldEnvironment.new()
	var env := Environment.new()
	# Dim hobby-room backdrop instead of pure black space: a warm-grey horizon gradient plus
	# distance fog so the table edge dissolves into the room rather than ending at a hard line.
	var sky_mat := ProceduralSkyMaterial.new()
	sky_mat.sky_top_color = Color(0.030, 0.032, 0.042)
	sky_mat.sky_horizon_color = Color(0.11, 0.095, 0.085)
	sky_mat.sky_curve = 0.22
	sky_mat.ground_bottom_color = Color(0.020, 0.018, 0.016)
	sky_mat.ground_horizon_color = Color(0.09, 0.08, 0.07)
	sky_mat.ground_curve = 0.08
	sky_mat.sun_angle_max = 0.0
	sky_mat.use_debanding = true
	var sky := Sky.new()
	sky.sky_material = sky_mat
	env.background_mode = Environment.BG_SKY
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.42, 0.46, 0.58)
	env.ambient_light_energy = 0.58
	env.fog_enabled = true
	env.fog_mode = Environment.FOG_MODE_EXPONENTIAL
	env.fog_light_color = Color(0.085, 0.078, 0.074)
	env.fog_density = 0.0085
	env.fog_sky_affect = 0.35
	env.fog_aerial_perspective = 0.25
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.tonemap_exposure = 0.94
	env.glow_enabled = true
	env.glow_intensity = 0.58
	env.glow_bloom = 0.03
	env.glow_hdr_threshold = 1.22
	env.ssao_enabled = true
	env.ssao_radius = 0.85
	env.ssao_intensity = 0.95
	we.environment = env

	# Subtle HD-2D Diorama Tilt-Shift Far Depth-of-Field!
	var cam_attr := CameraAttributesPractical.new()
	cam_attr.dof_blur_far_enabled = true
	cam_attr.dof_blur_far_distance = 76.0
	cam_attr.dof_blur_far_transition = 30.0
	cam_attr.dof_blur_amount = 0.040
	we.camera_attributes = cam_attr
	_world_root.add_child(we)

	# 2. Key Warm Golden Sunlight (Stable 2-Split Shadows + Clean Bias -> Zero Shadow Shimmer!)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-48.0, -34.0, 0.0)
	sun.light_color = Color(1.0, 0.93, 0.80)
	sun.light_energy = 1.24
	sun.shadow_enabled = true
	sun.shadow_opacity = 0.80
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
	sun.directional_shadow_max_distance = 86.0
	sun.shadow_bias = 0.04
	sun.shadow_normal_bias = 1.4
	_world_root.add_child(sun)

	var fill := DirectionalLight3D.new()
	fill.rotation_degrees = Vector3(-36.0, 146.0, 0.0)
	fill.light_color = Color(0.58, 0.66, 0.84)
	fill.light_energy = 0.26
	fill.shadow_enabled = false
	_world_root.add_child(fill)

	# Central Shrine Warm Spotlight Accent
	var center_omni := OmniLight3D.new()
	center_omni.position = Vector3(0.0, 6.5, 0.0)
	center_omni.light_color = Color(1.0, 0.80, 0.44)
	center_omni.light_energy = 0.45

	# Reading lamp over the right-hand apron so the CP chips / datacard / VP rail aren't in the dark
	var apron_lamp := OmniLight3D.new()
	apron_lamp.position = Vector3(21.0, 7.0, 4.0)
	apron_lamp.light_color = Color(1.0, 0.92, 0.78)
	apron_lamp.light_energy = 0.9
	apron_lamp.omni_range = 16.0
	apron_lamp.shadow_enabled = false
	_world_root.add_child(apron_lamp)
	center_omni.omni_range = 19.0
	_world_root.add_child(center_omni)

	# 3. 2.55D Telephoto Isometric Camera Rig
	_cam_pivot = Node3D.new()
	_world_root.add_child(_cam_pivot)

	_cam = Camera3D.new()
	_cam.projection = Camera3D.PROJECTION_PERSPECTIVE
	_cam.fov = 24.0 # Low-FOV telephoto lens = true 2.55D isometric feel + vivid 3D height!
	_cam.near = 6.0  # 6x tighter depth buffer precision across the tabletop (cam_dist is always >= 18)!
	_cam.far = 148.0
	_cam_pivot.add_child(_cam)

	# 4. World Containers + Real-Time 3D Particle Combat VFX + Physical 3D Tabletop Dice Tray
	_static_map_root = Node3D.new()
	_world_root.add_child(_static_map_root)

	_ops_root = Node3D.new()
	_world_root.add_child(_ops_root)

	_vfx = Vfx3D.new()
	_world_root.add_child(_vfx)

	_dice_3d = Dice3D.new()
	_world_root.add_child(_dice_3d)

	# 5. ImmediateMesh 3D Tactical Overlays (Ruler, LoS Lasers, Cover Cone, Tracers)
	_imm_lines = ImmediateMesh.new()
	_mat_lines = StandardMaterial3D.new()
	_mat_lines.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	_mat_lines.vertex_color_use_as_albedo = true
	_mat_lines.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	_mat_lines.depth_draw_mode = BaseMaterial3D.DEPTH_DRAW_DISABLED
	_mat_lines.render_priority = 5
	_mat_lines.emission_enabled = true
	_mat_lines.emission = Color(1, 1, 1)
	_mat_lines.emission_operator = BaseMaterial3D.EMISSION_OP_MULTIPLY
	_mat_lines.emission_energy_multiplier = 1.6
	var mi_lines := MeshInstance3D.new()
	mi_lines.mesh = _imm_lines
	mi_lines.material_override = _mat_lines
	mi_lines.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_world_root.add_child(mi_lines)

	_imm_tris = ImmediateMesh.new()
	_mat_tris = StandardMaterial3D.new()
	_mat_tris.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	_mat_tris.vertex_color_use_as_albedo = true
	_mat_tris.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	_mat_tris.depth_draw_mode = BaseMaterial3D.DEPTH_DRAW_DISABLED
	_mat_tris.render_priority = 4
	_mat_tris.cull_mode = BaseMaterial3D.CULL_DISABLED
	var mi_tris := MeshInstance3D.new()
	mi_tris.mesh = _imm_tris
	mi_tris.material_override = _mat_tris
	mi_tris.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_world_root.add_child(mi_tris)

	_apply_camera_transform()


# ==============================================================================
# COORDINATE CONVERSION (2D Board Inches <-> 3D World Space)
# ==============================================================================

func inches_to_world(pos_in: Vector2, elev_y: float = 0.0) -> Vector3:
	return Vector3(
		pos_in.x - KTData.BOARD_WIDTH_INCHES * 0.5,
		elev_y,
		pos_in.y - KTData.BOARD_HEIGHT_INCHES * 0.5
	)


func world_to_inches(wpos: Vector3) -> Vector2:
	return Vector2(
		wpos.x + KTData.BOARD_WIDTH_INCHES * 0.5,
		wpos.z + KTData.BOARD_HEIGHT_INCHES * 0.5
	)


func elevation_at_inches(pos_in: Vector2) -> float:
	if engine == null or engine.state.is_empty():
		return 0.0
	return KTGeometry.elevation_at(pos_in, engine.state["map"]["terrains"])


# ==============================================================================
# ENGINE BINDING & 3D DIORAMA RECONSTRUCTION
# ==============================================================================

func set_hud_labels_visible(vis: bool) -> void:
	if _hud_overlay != null:
		_hud_overlay.visible = vis
		_hud_overlay.queue_redraw()


func request_hud_redraw() -> void:
	if _hud_overlay != null:
		_hud_overlay.queue_redraw()


func bind_engine(eng: KTEngine) -> void:
	engine = eng
	_rebuild_3d_map_if_needed()
	_rebuild_3d_operatives()
	sync_visual_positions(true)
	clear_interaction()


func _rebuild_3d_map_if_needed() -> void:
	if engine == null or engine.state.is_empty():
		return
	var map_d: Dictionary = engine.state["map"]
	var mid := str(map_d.get("id", ""))
	if mid == _built_map_id and _static_map_root.get_child_count() > 0:
		return
	_built_map_id = mid

	for c in _static_map_root.get_children():
		c.queue_free()
	_obj_nodes.clear()

	var bw := float(map_d["width"])
	var bh := float(map_d["height"])
	Models3D.build_tabletop_base(_static_map_root, bw, bh, _bg_tex, map_d["drop_zone_a"], map_d["drop_zone_b"])

	# Physical bookkeeping on the apron: CP chip stacks, VP track, TP block, datacard mats
	if _table_props == null or not is_instance_valid(_table_props):
		_table_props = KTTableProps3D.new()
		_world_root.add_child(_table_props)
	_table_props.build(bw, bh)

	for t in map_d["terrains"]:
		Models3D.build_terrain_piece(_static_map_root, t, bw, bh)

	for obj in map_d["objectives"]:
		var oid := str(obj["id"])
		var opos: Vector2 = obj["pos"]
		_obj_nodes[oid] = Models3D.build_objective_beacon(_static_map_root, inches_to_world(opos, 0.0))


func _rebuild_3d_operatives() -> void:
	for c in _ops_root.get_children():
		c.queue_free()
	_op_nodes.clear()
	_visual_pos_3d.clear()
	_visual_facing_y.clear()
	_op_lunge_offset.clear()
	_op_hit_flash.clear()
	_op_fall_t.clear()
	for m in _fallen_markers.values():
		if is_instance_valid(m):
			(m as Node).queue_free()
	_fallen_markers.clear()

	if engine == null or engine.state.is_empty():
		return

	for op in engine.state["operatives"]:
		var uid := str(op["uid"])
		var built: Dictionary = Models3D.build_operative_miniature(op)
		var node_root: Node3D = built["root"]
		_ops_root.add_child(node_root)
		_op_nodes[uid] = built

		var p_in: Vector2 = op["pos"]
		var z_in := elevation_at_inches(p_in)
		var wpos := inches_to_world(p_in, z_in)
		_visual_pos_3d[uid] = wpos
		node_root.position = wpos

		# Default facing: Angels of Death face North (-Z -> PI), Plague Marines face South (+Z -> 0)
		var init_yaw: float = PI if str(op["side"]) == "A" else 0.0
		_visual_facing_y[uid] = init_yaw
		(built["pivot"] as Node3D).rotation.y = init_yaw


func _spawn_fallen_marker(uid: String, wpos: Vector3) -> void:
	# Casualty token: a flat faction-coloured disc with a skull glyph, left where the operative fell.
	_clear_fallen_marker(uid)
	var op := engine.get_op(uid) if engine != null else {}
	var is_aod: bool = (not op.is_empty()) and str(op["side"]) == "A"
	var fac_col := Color(0.24, 0.52, 0.90) if is_aod else Color(0.50, 0.74, 0.22)
	var marker := Node3D.new()
	marker.position = Vector3(wpos.x, wpos.y, wpos.z)
	_ops_root.add_child(marker)
	var disc_mat := Models3D.make_pbr(Color(0.07, 0.075, 0.09), 0.35, 0.6)
	Models3D.add_cyl(marker, Vector3(0.0, 0.03, 0.0), 0.62, 0.58, 0.06, disc_mat, Vector3.ZERO, 24)
	Models3D.add_torus(marker, Vector3(0.0, 0.06, 0.0), 0.50, 0.58, Models3D.make_pbr(fac_col.darkened(0.35), 0.6, 0.4))
	var skull := Label3D.new()
	skull.text = "☠"
	skull.font = font
	skull.font_size = 96
	skull.pixel_size = 0.0075
	skull.modulate = fac_col.lightened(0.25)
	skull.outline_size = 8
	skull.outline_modulate = Color(0.02, 0.02, 0.03)
	skull.rotation_degrees = Vector3(-90.0, 0.0, 0.0)
	skull.position = Vector3(0.0, 0.075, 0.0)
	skull.alpha_cut = Label3D.ALPHA_CUT_DISCARD
	marker.add_child(skull)
	_fallen_markers[uid] = marker


func _clear_fallen_marker(uid: String) -> void:
	if _fallen_markers.has(uid):
		var m: Node = _fallen_markers[uid]
		if is_instance_valid(m):
			m.queue_free()
		_fallen_markers.erase(uid)


func sync_visual_positions(instant: bool = false) -> void:
	if engine == null or engine.state.is_empty():
		return
	if _op_nodes.is_empty():
		_rebuild_3d_operatives()
		return
	for op in engine.state["operatives"]:
		var uid := str(op["uid"])
		var p_in: Vector2 = op["pos"]
		var z_in := elevation_at_inches(p_in)
		var wpos := inches_to_world(p_in, z_in)
		if instant or not _visual_pos_3d.has(uid):
			_visual_pos_3d[uid] = wpos
			if _op_nodes.has(uid):
				(_op_nodes[uid]["root"] as Node3D).position = wpos


# ==============================================================================
# CAMERA PRESETS & PUBLIC CONTROLS
# ==============================================================================

func fit_board_to_rect(_view_rect: Rect2, instant: bool = false) -> void:
	reset_isometric_view(instant)


func reset_isometric_view(instant: bool = false) -> void:
	# Focus a little south of centre and pull back slightly so the near apron (VP rail, TP block,
	# CP chips, datacard mat) is in frame under the board: the table is part of the picture.
	# Nudged east so the right-hand apron (CP chips, datacard, VP rail, TP block) stays in frame.
	target_focus = Vector3(1.4, 0.5, 2.6)
	target_yaw_deg = -24.0
	target_pitch_deg = 43.0
	target_dist = 61.0
	if instant:
		cam_focus = target_focus
		cam_yaw_deg = target_yaw_deg
		cam_pitch_deg = target_pitch_deg
		cam_dist = target_dist
		_apply_camera_transform()


func set_camera_angle_preset(preset: String) -> void:
	if preset == "low":
		# Dramatic low-angle miniature level (highlights towering 2-story height!)
		target_pitch_deg = 28.0
		target_dist = 36.0
	elif preset == "top":
		# High-angle tactical overview
		target_pitch_deg = 68.0
		target_yaw_deg = 0.0
		target_dist = 54.0
	else:
		# Classic 2.55D Isometric Diorama
		reset_isometric_view(false)


func orbit_by(delta_yaw_deg: float) -> void:
	target_yaw_deg += delta_yaw_deg


func focus_on_operative(uid: String) -> void:
	if engine == null:
		return
	var op := engine.get_op(uid)
	if op.is_empty():
		return
	selected_uid = uid
	var p_in: Vector2 = op["pos"]
	var z_in := elevation_at_inches(p_in)
	target_focus = inches_to_world(p_in, z_in + 0.8)
	target_dist = minf(target_dist, 42.0)


func _apply_camera_transform() -> void:
	if _cam_pivot == null or _cam == null:
		return
	var shake_v := Vector3.ZERO
	if _shake_strength > 0.05:
		shake_v = Vector3(
			randf_range(-_shake_strength, _shake_strength) * 0.04,
			randf_range(-_shake_strength, _shake_strength) * 0.03,
			randf_range(-_shake_strength, _shake_strength) * 0.04
		)
	_cam_pivot.position = cam_focus + shake_v
	_cam_pivot.rotation_degrees = Vector3(-cam_pitch_deg, cam_yaw_deg, 0.0)
	_cam.position = Vector3(0.0, 0.0, cam_dist)


# ==============================================================================
# INTERACTION MODES & COMBAT FX
# ==============================================================================

func clear_interaction() -> void:
	interaction_mode = "idle"
	move_waypoints.clear()
	active_shoot_weapon = {}
	cached_shot_evals.clear()


func start_move_mode(uid: String, move_kind: String) -> void:
	if engine == null:
		return
	var op := engine.get_op(uid)
	if op.is_empty():
		return
	selected_uid = uid
	interaction_mode = "move"
	move_action_kind = move_kind
	move_waypoints = [op["pos"]]
	active_shoot_weapon = {}
	cached_shot_evals.clear()


func start_shoot_mode(uid: String, weapon: Dictionary) -> void:
	if engine == null:
		return
	var attacker := engine.get_op(uid)
	if attacker.is_empty():
		return
	selected_uid = uid
	interaction_mode = "shoot"
	active_shoot_weapon = weapon
	cached_shot_evals.clear()
	for other in engine.state["operatives"]:
		if other["incapacitated"] or other["side"] == attacker["side"]:
			continue
		cached_shot_evals[str(other["uid"])] = KTGeometry.evaluate_shot(attacker, other, weapon, engine.state)


func start_fight_mode(uid: String) -> void:
	selected_uid = uid
	interaction_mode = "fight"
	move_waypoints.clear()
	active_shoot_weapon = {}
	cached_shot_evals.clear()


func spawn_tracer(from_in: Vector2, to_in: Vector2, col: Color) -> void:
	var y_from := elevation_at_inches(from_in) + 1.25
	var y_to := elevation_at_inches(to_in) + 1.05
	var w_from := inches_to_world(from_in, y_from)
	var w_to := inches_to_world(to_in, y_to)
	_tracers_3d.append({
		"from": w_from,
		"to": w_to,
		"color": col,
		"t": 0.0,
		"dur": 0.42,
	})
	# Rotate shooter toward target!
	if engine != null:
		var act_uid := str(engine.state.get("active_op_uid", ""))
		if not act_uid.is_empty():
			var dir := w_to - w_from
			if Vector2(dir.x, dir.z).length_squared() > 0.001:
				_visual_facing_y[act_uid] = atan2(dir.x, dir.z)


func spawn_floating_text(pos_in: Vector2, msg: String, col: Color) -> void:
	var y_h := elevation_at_inches(pos_in) + 2.35
	_float_texts_3d.append({
		"wpos": inches_to_world(pos_in, y_h),
		"msg": msg,
		"color": col,
		"t": 0.0,
		"dur": 1.45,
	})


func trigger_shake(amount: float = 7.0) -> void:
	_shake_strength = maxf(_shake_strength, amount)


func get_table_props() -> KTTableProps3D:
	return _table_props


var _grid_overlay_mi: MeshInstance3D = null

func set_grid_visible(on: bool) -> void:
	if _grid_overlay_mi == null or not is_instance_valid(_grid_overlay_mi):
		for c in _static_map_root.get_children():
			if c is MeshInstance3D and c.has_meta("kt_grid_overlay"):
				_grid_overlay_mi = c
				break
	if _grid_overlay_mi != null:
		_grid_overlay_mi.visible = on


## Push CP / VP / TP and the active datacards onto the physical table props.
func sync_table_props() -> void:
	if engine == null or engine.state.is_empty() or _table_props == null:
		return
	var cp := {}
	var vp := {}
	var cards := {}
	for side in ["A", "B"]:
		cp[side] = int(engine.state["sides"][side]["cp"])
		vp[side] = engine.get_total_vp(side)
	var act := engine.get_op(str(engine.state.get("active_op_uid", "")))
	if not act.is_empty():
		var card_paths: Array = act.get("cards", [])
		var tex: Texture2D = KTData.load_tex(str(card_paths[0])) if not card_paths.is_empty() else null
		cards[str(act["side"])] = {"tex": tex, "name": str(act["name"])}
	_table_props.sync(cp, vp, int(engine.state.get("tp", 1)), cards)


func get_dice_3d() -> KTDice3D:
	return _dice_3d


func frame_initiative_dice_roll(val_a: int, val_b: int, winner: String, instant: bool = false) -> void:
	if _dice_3d == null:
		return
	_dice_3d.move_tray_location(Vector3(0.0, -0.24, 16.6), 0.0, true)
	_dice_3d.roll_initiative_dice(val_a, val_b, winner, instant)
	target_yaw_deg = -10.0
	target_pitch_deg = 45.0
	target_dist = 31.0
	target_focus = Vector3(0.0, 0.35, 15.2)
	if instant:
		cam_yaw_deg = target_yaw_deg
		cam_pitch_deg = target_pitch_deg
		cam_dist = target_dist
		cam_focus = target_focus
		_apply_camera_transform()


func frame_combat_duel(att_uid: String, def_uid: String, is_melee: bool) -> void:
	if engine == null:
		return
	var att := engine.get_op(att_uid)
	var def := engine.get_op(def_uid)
	if att.is_empty() or def.is_empty():
		return
	var wa := inches_to_world(att["pos"], elevation_at_inches(att["pos"]) + 0.9)
	var wd := inches_to_world(def["pos"], elevation_at_inches(def["pos"]) + 0.9)
	var mid := wa.lerp(wd, 0.5)
	var span := wa.distance_to(wd)

	var tray_pos := Vector3(5.0, -0.24, 16.6)
	if _dice_3d != null:
		tray_pos = _dice_3d.move_tray_for_action(mid, target_yaw_deg, true)

	# Frame BOTH the two fighting standees (upper-left/center) AND the physical 3D Tabletop Velvet Dice Tray (foreground-right)!
	var d_mt := Vector2(mid.x - tray_pos.x, mid.z - tray_pos.z).length()
	target_focus = mid.lerp(tray_pos + Vector3(0.0, 0.4, 0.0), 0.50)
	target_dist = clampf(29.0 + maxf(d_mt * 0.84, span * 1.15), 39.5 if is_melee else 43.5, 54.0)
	target_pitch_deg = clampf(target_pitch_deg, 39.0, 44.0)


func clear_transient_combat_fx() -> void:
	_tracers_3d.clear()
	_float_texts_3d.clear()
	if _vfx != null:
		_vfx.clear_transient()
	request_hud_redraw()


func restore_post_combat_camera() -> void:
	if _dice_3d != null:
		_dice_3d.place_tray_idle(false)
	reset_isometric_view(false)


func play_ranged_salvo_vfx(att_uid: String, def_uid: String, weapon: Dictionary) -> void:
	if engine == null or _vfx == null:
		return
	var att := engine.get_op(att_uid)
	var def := engine.get_op(def_uid)
	if att.is_empty() or def.is_empty():
		return
	frame_combat_duel(att_uid, def_uid, false)
	var is_imp: bool = str(att["side"]) == "A"
	var w_from := inches_to_world(att["pos"], elevation_at_inches(att["pos"]) + 1.15)
	var w_to := inches_to_world(def["pos"], elevation_at_inches(def["pos"]) + 1.05)
	spawn_tracer(att["pos"], def["pos"], KTTheme.COL_IMPERIAL if is_imp else KTTheme.COL_NURGLE)
	_vfx.spawn_ranged_salvo(w_from, w_to, weapon, is_imp)
	var wname_l: String = str(weapon.get("name", ""))
	if "狙击" in wname_l or "等离子" in wname_l:
		KTSfx.play("sniper", -3.0, 0.04)
	else:
		KTSfx.play("bolter", -3.0, 0.05)
	var recoil := (w_from - w_to).normalized() * 0.24
	_op_lunge_offset[att_uid] = Vector3(recoil.x, 0.06, recoil.z)
	trigger_shake(2.8)


func play_ranged_resolution_vfx(
	att_uid: String,
	def_uid: String,
	weapon: Dictionary,
	dmg: int,
	crits: int,
	killed: bool,
	poisoned: bool
) -> void:
	if engine == null or _vfx == null:
		return
	var att := engine.get_op(att_uid)
	var def := engine.get_op(def_uid)
	if def.is_empty():
		return
	var is_imp: bool = str(att.get("side", "A")) == "A"
	var w_from := inches_to_world(att.get("pos", def["pos"]), elevation_at_inches(att.get("pos", def["pos"])) + 1.10)
	var w_to := inches_to_world(def["pos"], elevation_at_inches(def["pos"]) + 1.05)
	_vfx.spawn_ranged_resolution_explosion(w_to, dmg, crits, killed, poisoned, weapon, is_imp)

	if dmg > 0:
		KTSfx.play("explosion", -2.5 if killed else -4.5, 0.05)
		var knock := (w_to - w_from).normalized() * 0.36
		_op_lunge_offset[def_uid] = Vector3(knock.x, 0.14, knock.z)
		_op_hit_flash[def_uid] = 1.0
		trigger_shake(9.5 if killed else 5.5)
		spawn_floating_text(def["pos"], ("💀 毁灭击杀! -%d" % dmg) if killed else ("💥 -%d 耐伤!" % dmg), KTTheme.COL_CRIMSON if killed else KTTheme.COL_GOLD_BRIGHT)
		if killed:
			_start_kill_cam(def_uid)
	else:
		KTSfx.play("parry", -4.0, 0.06)
		_op_hit_flash[def_uid] = 0.55
		trigger_shake(2.2)
		spawn_floating_text(def["pos"], "🛡️ 护甲弹开!", KTTheme.COL_EMERALD)


func play_melee_engage_vfx(att_uid: String, def_uid: String) -> void:
	if engine == null or _vfx == null:
		return
	var att := engine.get_op(att_uid)
	var def := engine.get_op(def_uid)
	if att.is_empty() or def.is_empty():
		return
	frame_combat_duel(att_uid, def_uid, true)
	KTSfx.play("parry", -5.0, 0.08)
	var w_att := inches_to_world(att["pos"], elevation_at_inches(att["pos"]) + 1.05)
	var w_def := inches_to_world(def["pos"], elevation_at_inches(def["pos"]) + 1.05)
	var dir := (w_def - w_att).normalized()
	_op_lunge_offset[att_uid] = Vector3(dir.x * 0.32, 0.10, dir.z * 0.32)
	_op_lunge_offset[def_uid] = Vector3(-dir.x * 0.22, 0.08, -dir.z * 0.22)
	_vfx.spawn_melee_engage(w_att, w_def)
	trigger_shake(3.5)


func play_melee_step_vfx(
	actor_uid: String,
	target_uid: String,
	action_kind: String,
	weapon: Dictionary,
	dmg_dealt: int,
	killed: bool
) -> void:
	if engine == null or _vfx == null:
		return
	var actor := engine.get_op(actor_uid)
	var target := engine.get_op(target_uid)
	if actor.is_empty() or target.is_empty():
		return
	var is_imp: bool = str(actor["side"]) == "A"
	var w_act := inches_to_world(actor["pos"], elevation_at_inches(actor["pos"]) + 1.05)
	var w_tgt := inches_to_world(target["pos"], elevation_at_inches(target["pos"]) + 1.05)
	var dir := (w_tgt - w_act).normalized()
	if dir.length_squared() < 0.001:
		dir = Vector3.FORWARD

	_vfx.spawn_melee_clash(w_act, w_tgt, action_kind, weapon, is_imp, killed)

	if action_kind.begins_with("parry"):
		KTSfx.play("parry", -3.0, 0.05)
		_op_lunge_offset[actor_uid] = Vector3(dir.x * 0.45, 0.12, dir.z * 0.45)
		_op_lunge_offset[target_uid] = Vector3(-dir.x * 0.25, 0.06, -dir.z * 0.25)
		_op_hit_flash[actor_uid] = 0.55
		trigger_shake(4.0)
		spawn_floating_text(actor["pos"], "🛡️ 招架弹反!", KTTheme.COL_IMPERIAL if action_kind.ends_with("crit") else KTTheme.COL_EMERALD)
	else:
		var is_crit := action_kind.ends_with("crit")
		KTSfx.play("slash", -2.5 if is_crit else -4.0, 0.05)
		if killed:
			KTSfx.play("explosion", -5.0, 0.06)
		_op_lunge_offset[actor_uid] = Vector3(dir.x * 0.68, 0.18, dir.z * 0.68)
		_op_lunge_offset[target_uid] = Vector3(dir.x * 0.34, 0.12, dir.z * 0.34)
		_op_hit_flash[target_uid] = 1.0
		trigger_shake(10.0 if killed else (7.0 if is_crit else 4.8))
		var msg := ("💀 斩杀! -%d" % dmg_dealt) if killed else (("⚡ 暴击 -%d!" % dmg_dealt) if is_crit else ("⚔️ 出击 -%d" % dmg_dealt))
		spawn_floating_text(target["pos"], msg, KTTheme.COL_CRIMSON if killed else (KTTheme.COL_GOLD_BRIGHT if is_crit else KTTheme.COL_TEXT))
		if killed:
			_start_kill_cam(target_uid)


## Kill cam: brief slow-motion + punch-in on the fallen operative, then release.
## Engine.time_scale is restored by _process once the timer runs out, so it is safe even if the
## combat modal closes mid-effect.
var _kill_cam_t: float = 0.0
const KILL_CAM_DUR: float = 0.9

func _start_kill_cam(victim_uid: String) -> void:
	var v := engine.get_op(victim_uid)
	if v.is_empty():
		return
	if not bool(KTSettings.get_value("combat/kill_cam")):
		return
	_kill_cam_t = KILL_CAM_DUR
	Engine.time_scale = 0.35
	var wv := inches_to_world(v["pos"], elevation_at_inches(v["pos"]) + 0.9)
	target_focus = target_focus.lerp(wv, 0.55)
	target_dist = maxf(24.0, target_dist * 0.78)


# ==============================================================================
# FRAME UPDATE: SMOOTH 3D ANIMATION, LIGHTING PULSE & 3D OVERLAY REBUILD
# ==============================================================================

func _process(delta: float) -> void:
	_pulse_time += delta

	if _kill_cam_t > 0.0:
		# Count down in real time so slow-mo doesn't stretch itself
		_kill_cam_t -= delta / maxf(0.05, Engine.time_scale)
		if _kill_cam_t <= 0.0:
			_kill_cam_t = 0.0
			Engine.time_scale = 1.0
		else:
			# Ease back toward normal speed over the second half
			var k: float = clampf(1.0 - _kill_cam_t / KILL_CAM_DUR, 0.0, 1.0)
			Engine.time_scale = lerpf(0.35, 1.0, clampf((k - 0.45) / 0.55, 0.0, 1.0))

	# Keyboard Orbit (Q / E) & Pan (WASD / Arrows)
	if Input.is_key_pressed(KEY_Q):
		target_yaw_deg -= 68.0 * delta
	if Input.is_key_pressed(KEY_E):
		target_yaw_deg += 68.0 * delta

	var move_input := Vector2.ZERO
	if Input.is_key_pressed(KEY_W) or Input.is_key_pressed(KEY_UP):
		move_input.y -= 1.0
	if Input.is_key_pressed(KEY_S) or Input.is_key_pressed(KEY_DOWN):
		move_input.y += 1.0
	if Input.is_key_pressed(KEY_A) or Input.is_key_pressed(KEY_LEFT):
		move_input.x -= 1.0
	if Input.is_key_pressed(KEY_D) or Input.is_key_pressed(KEY_RIGHT):
		move_input.x += 1.0
	if move_input.length_squared() > 0.0:
		move_input = move_input.normalized() * 16.0 * delta
		var yaw_rad := deg_to_rad(cam_yaw_deg)
		var right_v := Vector3(cos(yaw_rad), 0.0, -sin(yaw_rad))
		var fwd_v := Vector3(sin(yaw_rad), 0.0, cos(yaw_rad))
		target_focus += right_v * move_input.x + fwd_v * move_input.y
		target_focus.x = clampf(target_focus.x, -22.0, 22.0)
		target_focus.z = clampf(target_focus.z, -19.0, 19.0)

	# Smoothly damp camera parameters
	var k := clampf(delta * 11.0, 0.0, 1.0)
	cam_focus = cam_focus.lerp(target_focus, k)
	cam_yaw_deg = lerpf(cam_yaw_deg, target_yaw_deg, k)
	cam_pitch_deg = lerpf(cam_pitch_deg, target_pitch_deg, k)
	cam_dist = lerpf(cam_dist, target_dist, k)
	if _shake_strength > 0.02:
		_shake_strength = lerpf(_shake_strength, 0.0, clampf(delta * 9.0, 0.0, 1.0))
	_apply_camera_transform()

	# Animate 3D Miniatures & Objective Beacons
	_update_3d_entities(delta)

	# Advance 3D tracers & floating texts
	var i := _tracers_3d.size() - 1
	while i >= 0:
		_tracers_3d[i]["t"] = float(_tracers_3d[i]["t"]) + delta
		if float(_tracers_3d[i]["t"]) >= float(_tracers_3d[i]["dur"]):
			_tracers_3d.remove_at(i)
		i -= 1

	var j := _float_texts_3d.size() - 1
	while j >= 0:
		_float_texts_3d[j]["t"] = float(_float_texts_3d[j]["t"]) + delta
		(_float_texts_3d[j]["wpos"] as Vector3).y += delta * 1.1
		if float(_float_texts_3d[j]["t"]) >= float(_float_texts_3d[j]["dur"]):
			_float_texts_3d.remove_at(j)
		j -= 1

	# Rebuild 3D ImmediateMesh overlays & trigger 2D crisp HUD redraw
	_rebuild_3d_tactical_overlays()
	_hud_overlay.queue_redraw()


func _update_3d_entities(delta: float) -> void:
	if engine == null or engine.state.is_empty():
		return
	_rebuild_3d_map_if_needed()

	var pulse := 0.5 + 0.5 * sin(_pulse_time * 4.2)
	var active_uid := str(engine.state.get("active_op_uid", ""))

	# Update Objective Holo-Beacons
	for obj in engine.state["map"]["objectives"]:
		var oid := str(obj["id"])
		if not _obj_nodes.has(oid):
			continue
		var bn: Dictionary = _obj_nodes[oid]
		var st: Dictionary = engine.state["objectives_state"][oid]
		var ctrl := str(st["controlled_by"])
		var sec := str(st["secured_by"])
		var col := KTTheme.COL_GOLD_BRIGHT
		if ctrl == "A" or (ctrl == "" and sec == "A"):
			col = KTTheme.COL_IMPERIAL
		elif ctrl == "B" or (ctrl == "" and sec == "B"):
			col = KTTheme.COL_NURGLE

		var rmat: StandardMaterial3D = bn["ring_mat"]
		rmat.albedo_color = Color(col.r, col.g, col.b, 0.36 + 0.18 * pulse)
		rmat.emission = Color(col.r, col.g, col.b, 1.0)
		var smat: StandardMaterial3D = bn["spire_mat"]
		smat.albedo_color = Color(col.r, col.g, col.b, 0.80)
		smat.emission = Color(col.r, col.g, col.b, 1.0)
		(bn["orb"] as MeshInstance3D).position.y = 0.52 + 0.07 * sin(_pulse_time * 3.0)
		(bn["holo_cone"] as MeshInstance3D).rotation.y = _pulse_time * 1.2

	# Update 3D Operative Miniatures (walking, climbing elevation arc, facing, order glow!)
	for op in engine.state["operatives"]:
		var uid := str(op["uid"])
		if not _op_nodes.has(uid):
			_rebuild_3d_operatives()
			break
		var node_d: Dictionary = _op_nodes[uid]
		var root_n: Node3D = node_d["root"]
		var pivot_n: Node3D = node_d["pivot"]

		var tilt_n: Node3D = node_d.get("tilt_pivot", null)
		if bool(op["incapacitated"]):
			# Topple animation: the acrylic token falls over backwards and sinks into the base,
			# then the whole miniature fades out and is hidden. Undo brings it straight back.
			var fall_t: float = float(_op_fall_t.get(uid, 0.0))
			if fall_t >= 1.0:
				root_n.visible = false
				continue
			if fall_t <= 0.0 and root_n.visible:
				_spawn_fallen_marker(uid, root_n.position)
			fall_t = minf(1.0, fall_t + delta * 1.35)
			_op_fall_t[uid] = fall_t
			root_n.visible = true
			if tilt_n != null:
				var k: float = clampf(fall_t * 1.6, 0.0, 1.0)
				var ease_k: float = 1.0 - pow(1.0 - k, 3.0)
				tilt_n.rotation_degrees.x = -92.0 * ease_k
				tilt_n.position.y = -0.04 * ease_k
			var fade: float = clampf((fall_t - 0.55) / 0.45, 0.0, 1.0)
			var smat_f: StandardMaterial3D = node_d["standee_mat"]
			smat_f.albedo_color = Color(0.5, 0.5, 0.55, 1.0).lerp(Color(0.2, 0.2, 0.22, 1.0), fade)
			smat_f.alpha_scissor_threshold = lerpf(0.40, 1.0, fade)
			continue
		if _op_fall_t.has(uid):
			_op_fall_t.erase(uid)
			if tilt_n != null:
				tilt_n.rotation_degrees = Vector3.ZERO
				tilt_n.position = Vector3.ZERO
			(node_d["standee_mat"] as StandardMaterial3D).alpha_scissor_threshold = 0.40
			_clear_fallen_marker(uid)
		root_n.visible = true

		var target_in: Vector2 = op["pos"]
		var target_y := elevation_at_inches(target_in)
		var target_w := inches_to_world(target_in, target_y)
		var cur_w: Vector3 = _visual_pos_3d.get(uid, target_w)
		var diff := target_w - cur_w
		var dist_rem := diff.length()

		var lunge_v: Vector3 = _op_lunge_offset.get(uid, Vector3.ZERO)
		if lunge_v.length_squared() > 0.0002:
			lunge_v = lunge_v.lerp(Vector3.ZERO, clampf(delta * 10.5, 0.0, 1.0))
			_op_lunge_offset[uid] = lunge_v
		else:
			lunge_v = Vector3.ZERO
			_op_lunge_offset[uid] = Vector3.ZERO

		var flash_v: float = float(_op_hit_flash.get(uid, 0.0))
		if flash_v > 0.01:
			flash_v = maxf(0.0, flash_v - delta * 4.2)
			_op_hit_flash[uid] = flash_v
		else:
			flash_v = 0.0

		if dist_rem > 0.02:
			var next_w := cur_w.lerp(target_w, clampf(delta * 12.0, 0.0, 1.0))
			# Brisk Octopath HD-2D step-hop arc when walking or climbing onto 2F!
			_visual_pos_3d[uid] = next_w
			var step_hop := absf(sin(_pulse_time * 18.0)) * 0.16 + minf(0.42, dist_rem * 0.20)
			root_n.position = next_w + Vector3(0.0, step_hop, 0.0) + lunge_v
			if Vector2(diff.x, diff.z).length_squared() > 0.001:
				_visual_facing_y[uid] = atan2(diff.x, diff.z)
		else:
			_visual_pos_3d[uid] = target_w
			var bob: float = 0.045 * sin(_pulse_time * 5.0) if uid == active_uid else 0.0
			root_n.position = target_w + Vector3(0.0, bob, 0.0) + lunge_v

		# Acrylic token feedback: idle sway (a standee never sits perfectly still on a table),
		# hit recoil tilts it back and flashes the print, active operative gets a slow "picked up" lift.
		if node_d.has("standee_holder") and node_d.has("standee_mat"):
			var sh: Node3D = node_d["standee_holder"]
			var smat_op: StandardMaterial3D = node_d["standee_mat"]
			var phase_off: float = float(uid.hash() % 13) * 0.5
			var sway_deg: float = (1.6 * sin(_pulse_time * 1.7 + phase_off)) if (uid == active_uid) else (0.7 * sin(_pulse_time * 1.1 + phase_off))
			var recoil_deg: float = -22.0 * flash_v * flash_v
			if tilt_n != null:
				tilt_n.rotation_degrees.x = recoil_deg
				tilt_n.rotation_degrees.z = sway_deg
			var lift: float = (0.05 + 0.03 * sin(_pulse_time * 2.4)) if (uid == active_uid) else 0.0
			sh.position.y = 0.22 + lift
			# Face the camera around Y only (upright acrylic standee). Done here instead of a material
			# billboard flag so the tilt pivot's recoil/topple rotations compose correctly on top.
			var to_cam := _cam.global_position - sh.global_position
			if Vector2(to_cam.x, to_cam.z).length_squared() > 0.0001:
				sh.global_rotation = Vector3(0.0, atan2(to_cam.x, to_cam.z), 0.0)
			var ready_tint := Color(0.92, 0.93, 0.95, 1.0) if (bool(op["ready"]) or uid == active_uid) else Color(0.62, 0.64, 0.70, 1.0)
			if flash_v > 0.01:
				smat_op.albedo_color = ready_tint.lerp(Color(1.0, 0.36, 0.30, 1.0), flash_v * 0.8)
				smat_op.emission_energy_multiplier = 0.2 + flash_v * 2.6
			else:
				smat_op.albedo_color = ready_tint
				smat_op.emission_energy_multiplier = 0.16 if (uid == active_uid or uid == hovered_uid) else 0.08

		# If aiming at hovered enemy in shoot mode, smoothly face hovered enemy!
		if uid == selected_uid and interaction_mode == "shoot" and not hovered_uid.is_empty():
			var hov_op := engine.get_op(hovered_uid)
			if not hov_op.is_empty() and str(hov_op["side"]) != str(op["side"]):
				var h_w := inches_to_world(hov_op["pos"], 0.0)
				var look_d := h_w - cur_w
				if Vector2(look_d.x, look_d.z).length_squared() > 0.001:
					_visual_facing_y[uid] = atan2(look_d.x, look_d.z)

		var target_yaw_op: float = float(_visual_facing_y.get(uid, 0.0))
		pivot_n.rotation.y = lerp_angle(pivot_n.rotation.y, target_yaw_op, clampf(delta * 12.0, 0.0, 1.0))

		# Base Faction Ring & Active Selection Ring
		var is_ready := bool(op["ready"])
		var is_active := (uid == active_uid)
		var is_hovered := (uid == hovered_uid)
		var fac_col := Color(0.22, 0.58, 1.0) if str(op["side"]) == "A" else Color(0.56, 0.86, 0.22)
		var rim_mat: StandardMaterial3D = node_d["rim_mat"]
		rim_mat.albedo_color = fac_col if (is_ready or is_active) else fac_col.darkened(0.55)
		rim_mat.emission_energy_multiplier = 2.0 if is_ready else 0.5

		var sel_ring: MeshInstance3D = node_d["sel_ring"]
		var sel_mat: StandardMaterial3D = node_d["sel_mat"]
		if is_active:
			sel_ring.visible = true
			sel_mat.albedo_color = Color(1.0, 0.84, 0.22, 0.90)
			sel_mat.emission = Color(1.0, 0.84, 0.22)
			sel_ring.scale = Vector3.ONE * (1.0 + 0.06 * pulse)
		elif is_hovered or uid == selected_uid:
			sel_ring.visible = true
			sel_mat.albedo_color = Color(1.0, 1.0, 1.0, 0.78)
			sel_mat.emission = Color(1.0, 1.0, 1.0)
			sel_ring.scale = Vector3.ONE
		elif str(engine.state["phase"]) == "FIREFIGHT" and active_uid.is_empty() and bool(engine.can_operative_be_selected_for_turn(op)["ok"]):
			sel_ring.visible = true
			sel_mat.albedo_color = Color(fac_col.r, fac_col.g, fac_col.b, 0.35 + 0.35 * pulse)
			sel_mat.emission = fac_col
			sel_ring.scale = Vector3.ONE
		else:
			sel_ring.visible = false

		# Front Order Cone Color (Blue = Conceal, Orange = Engage)
		var ord_mat: StandardMaterial3D = node_d["order_mat"]
		var ord_col := Color(0.22, 0.68, 1.0) if str(op["order"]) == "conceal" else Color(1.0, 0.52, 0.14)
		ord_mat.albedo_color = Color(ord_col.r, ord_col.g, ord_col.b, 0.92 if is_ready else 0.45)
		ord_mat.emission = ord_col


# ==============================================================================
# 3D IMMEDIATEMESH TACTICAL OVERLAYS (RULER + CLIMB + 3D LASERS + COVER CONE)
# ==============================================================================

func _rebuild_3d_tactical_overlays() -> void:
	_imm_lines.clear_surfaces()
	_imm_tris.clear_surfaces()
	if engine == null or engine.state.is_empty():
		return

	var has_lines := false
	var has_tris := false
	var line_pts: Array[Vector3] = []
	var line_cols: Array[Color] = []
	var tri_pts: Array[Vector3] = []
	var tri_cols: Array[Color] = []

	var add_seg := func(a: Vector3, b: Vector3, c: Color):
		line_pts.append(a)
		line_cols.append(c)
		line_pts.append(b)
		line_cols.append(c)

	var add_ring_3d := func(center: Vector3, radius: float, col: Color, fill_alpha: float = 0.0, segs: int = 40):
		for s in range(segs):
			var a0: float = TAU * float(s) / float(segs)
			var a1: float = TAU * float(s + 1) / float(segs)
			var p0 := center + Vector3(cos(a0) * radius, 0.0, sin(a0) * radius)
			var p1 := center + Vector3(cos(a1) * radius, 0.0, sin(a1) * radius)
			add_seg.call(p0, p1, col)
			if fill_alpha > 0.005:
				var fc := Color(col.r, col.g, col.b, fill_alpha)
				tri_pts.append(center)
				tri_cols.append(fc)
				tri_pts.append(p0)
				tri_cols.append(fc)
				tri_pts.append(p1)
				tri_cols.append(fc)

	var add_ribbon_3d := func(a: Vector3, b: Vector3, half_w: float, col: Color):
		var flat := Vector3(b.x - a.x, 0.0, b.z - a.z)
		var perp := Vector3(-flat.z, 0.0, flat.x).normalized() * half_w
		if perp.length_squared() < 0.0001:
			perp = Vector3(half_w, 0.0, 0.0)
		var v0 := a - perp
		var v1 := a + perp
		var v2 := b + perp
		var v3 := b - perp
		tri_pts.append(v0); tri_cols.append(col)
		tri_pts.append(v1); tri_cols.append(col)
		tri_pts.append(v2); tri_cols.append(col)
		tri_pts.append(v0); tri_cols.append(col)
		tri_pts.append(v2); tri_cols.append(col)
		tri_pts.append(v3); tri_cols.append(col)

	# 1. Cloud of Flies 3D Marker Ring
	for m in engine.state.get("markers", []):
		if str(m.get("kind", "")) == "cloud_of_flies":
			var mp: Vector2 = m["pos"]
			var mw := inches_to_world(mp, elevation_at_inches(mp) + 0.06)
			add_ring_3d.call(mw, 1.6, KTTheme.COL_NURGLE, 0.16, 36)

	# 2. MOVE MODE: 3D Enemy Engagement Rings + Elevation-Climbing 3D Ruler + Ghost Landing Hologram!
	if interaction_mode == "move":
		var op := engine.get_op(selected_uid)
		if not op.is_empty():
			# Reachable-range disc: translucent faction-coloured floor disc of radius = move allowance,
			# so the player sees "how far" at a glance before dragging the ruler (ruler still shows exact inches).
			var reach_in: float = engine.get_effective_move_inches(op, move_action_kind)
			var op_pos: Vector2 = op["pos"]
			var oy := elevation_at_inches(op_pos) + 0.045
			var oc := inches_to_world(op_pos, oy)
			var rcol_f := KTTheme.COL_GOLD_BRIGHT if move_action_kind == "charge" else (KTTheme.COL_EMERALD if move_action_kind == "dash" else KTTheme.COL_IMPERIAL if str(op["side"]) == "A" else KTTheme.COL_NURGLE)
			var fill_c := Color(rcol_f.r, rcol_f.g, rcol_f.b, 0.10)
			var segs_r := 56
			for si in range(segs_r):
				var a0: float = TAU * float(si) / float(segs_r)
				var a1: float = TAU * float(si + 1) / float(segs_r)
				var p0 := oc + Vector3(cos(a0) * reach_in, 0.0, sin(a0) * reach_in)
				var p1 := oc + Vector3(cos(a1) * reach_in, 0.0, sin(a1) * reach_in)
				tri_pts.append(oc); tri_cols.append(fill_c)
				tri_pts.append(p1); tri_cols.append(fill_c)
				tri_pts.append(p0); tri_cols.append(fill_c)
			add_ring_3d.call(oc, reach_in, rcol_f, 0.08, 64)
			# Dashed inner inch rings every 2" for scale reading
			var ring_r := 2.0
			while ring_r < reach_in - 0.1:
				add_ring_3d.call(oc, ring_r, Color(rcol_f.r, rcol_f.g, rcol_f.b, 0.35), 0.02, 48)
				ring_r += 2.0

			for other in engine.state["operatives"]:
				if other["incapacitated"] or other["side"] == op["side"]:
					continue
				var ep: Vector2 = other["pos"]
				var ew := inches_to_world(ep, elevation_at_inches(ep) + 0.05)
				var er := float(other["base_radius"]) + 1.0
				var rcol := KTTheme.COL_GOLD_BRIGHT if move_action_kind == "charge" else KTTheme.COL_CRIMSON
				add_ring_3d.call(ew, er, rcol, 0.10, 40)

			var preview_pts: Array[Vector2] = move_waypoints.duplicate()
			preview_pts.append(hovered_board_inches)
			var side_state: Dictionary = engine.state["sides"][op["side"]]
			var mobile := KTCombat.has_chapter_tactic(side_state, op, "mobile")
			var max_m := engine.get_effective_move_inches(op, move_action_kind)
			var chk := KTGeometry.validate_move_path(preview_pts, op, move_action_kind, max_m, engine.state, mobile)
			var ok := bool(chk["ok"])
			var lcol := KTTheme.COL_EMERALD if ok else KTTheme.COL_CRIMSON

			for idx in range(preview_pts.size() - 1):
				var p1_in := preview_pts[idx]
				var p2_in := preview_pts[idx + 1]
				var y1 := elevation_at_inches(p1_in) + 0.08
				var y2 := elevation_at_inches(p2_in) + 0.08
				var w1 := inches_to_world(p1_in, y1)
				var w2 := inches_to_world(p2_in, y2)

				# If climbing/dropping between elevations, draw explicit 3D vertical climb column + upper landing!
				if absf(y2 - y1) > 0.5:
					var w_edge_low := Vector3(w2.x, y1, w2.z)
					add_ribbon_3d.call(w1, w_edge_low, 0.10, Color(lcol.r, lcol.g, lcol.b, 0.72))
					add_seg.call(w1, w_edge_low, lcol)
					# Vertical glowing climb pillar!
					add_ribbon_3d.call(w_edge_low, w2, 0.12, Color(1.0, 0.84, 0.25, 0.80))
					add_seg.call(w_edge_low, w2, KTTheme.COL_GOLD_BRIGHT)
				else:
					add_ribbon_3d.call(w1, w2, 0.10, Color(lcol.r, lcol.g, lcol.b, 0.72))
					add_seg.call(w1, w2, lcol)

				# 1-Inch 3D Ruler Tick Marks
				var seg_len := p1_in.distance_to(p2_in)
				var dir_2d := (p2_in - p1_in).normalized()
				var perp_2d := Vector2(-dir_2d.y, dir_2d.x)
				var tick := 1.0
				while tick < seg_len:
					var t_in := p1_in + dir_2d * tick
					var ty := lerpf(y1, y2, tick / maxf(0.01, seg_len))
					var ta := inches_to_world(t_in - perp_2d * 0.22, ty + 0.02)
					var tb := inches_to_world(t_in + perp_2d * 0.22, ty + 0.02)
					add_seg.call(ta, tb, Color(1, 1, 1, 0.9))
					tick += 1.0

			# 3D Landing Base Ring + Vertical Hologram Silhouette Pillar at exact landing elevation!
			var end_y := elevation_at_inches(hovered_board_inches)
			var end_w := inches_to_world(hovered_board_inches, end_y + 0.06)
			var op_r := float(op["base_radius"])
			add_ring_3d.call(end_w, op_r, lcol, 0.30, 36)
			add_ring_3d.call(end_w + Vector3(0.0, 1.55, 0.0), op_r * 0.52, Color(lcol.r, lcol.g, lcol.b, 0.65), 0.15, 24)
			for post_a in [0.0, PI * 0.5, PI, PI * 1.5]:
				var rim_p := Vector3(cos(post_a) * op_r * 0.75, 0.0, sin(post_a) * op_r * 0.75)
				add_seg.call(end_w + rim_p, end_w + rim_p * 0.7 + Vector3(0.0, 1.55, 0.0), Color(lcol.r, lcol.g, lcol.b, 0.65))

			# 3D Ghost Sightlines from Landing Elevation Chest Height!
			if ok and move_action_kind in ["reposition", "dash"]:
				var usable := engine.get_usable_ranged_weapons(op)
				if not usable.is_empty():
					var w0: Dictionary = usable[0]
					var eye_from := inches_to_world(hovered_board_inches, end_y + 1.25)
					for enemy in engine.state["operatives"]:
						if enemy["incapacitated"] or enemy["side"] == op["side"]:
							continue
						var gshot := KTGeometry.evaluate_shot(op, enemy, w0, engine.state, hovered_board_inches)
						if bool(gshot["valid"]):
							var gcol := KTTheme.COL_VIOLET if bool(gshot["obscured"]) else (KTTheme.COL_GOLD_BRIGHT if str(gshot["cover"]) != "none" else KTTheme.COL_EMERALD)
							var eye_to := inches_to_world(enemy["pos"], elevation_at_inches(enemy["pos"]) + 1.05)
							add_seg.call(eye_from, eye_to, Color(gcol.r, gcol.g, gcol.b, 0.72))

	# 3. SHOOT MODE: True 3D Diagonal Laser Sightlines + 3D Volumetric Cover Cone!
	elif interaction_mode == "shoot":
		var att := engine.get_op(selected_uid)
		if not att.is_empty():
			var att_in: Vector2 = att["pos"]
			var att_eye := inches_to_world(att_in, elevation_at_inches(att_in) + 1.30)

			for def_uid in cached_shot_evals.keys():
				var def := engine.get_op(str(def_uid))
				if def.is_empty() or def["incapacitated"]:
					continue
				var ev: Dictionary = cached_shot_evals[def_uid]
				var def_in: Vector2 = def["pos"]
				var def_y := elevation_at_inches(def_in)
				var def_chest := inches_to_world(def_in, def_y + 1.05)
				var def_feet := inches_to_world(def_in, def_y + 0.06)
				var def_r := float(def["base_radius"])

				var is_hov: bool = (hovered_uid == def_uid)
				if not bool(ev["valid"]):
					# Unselectable targets: faint grey ring; the blocked sightline only when hovered.
					add_ring_3d.call(def_feet, def_r + 0.18, Color(0.55, 0.60, 0.68), 0.22 if is_hov else 0.10, 32)
					if is_hov:
						add_seg.call(att_eye, def_chest, Color(0.55, 0.60, 0.68, 0.45))
					continue

				var bcol := KTTheme.COL_EMERALD
				if bool(ev["obscured"]):
					bcol = KTTheme.COL_VIOLET
				elif str(ev["cover"]) != "none":
					bcol = KTTheme.COL_GOLD_BRIGHT

				# Non-hovered valid targets get ONLY a colour-coded ring (green exposed / gold cover /
				# violet obscured). The full sightline + cover cone appears for the hovered target alone,
				# so the board reads like a tabletop with one laser pointer instead of a web of lasers.
				add_ring_3d.call(def_feet, def_r + 0.18, bcol, 0.34 if is_hov else 0.20, 32)
				if not is_hov:
					continue
				add_seg.call(att_eye, def_chest, bcol.lightened(0.25))
				add_ribbon_3d.call(att_eye, def_chest, 0.095, Color(bcol.r, bcol.g, bcol.b, 0.90))

				# Draw 3D Volumetric Outer-Tangent Cover Cone when hovered!
				if is_hov:
					var cone: Dictionary = ev["cone"]
					var apex_3d := att_eye
					var left_3d := inches_to_world(cone["left"] as Vector2, def_y + 0.95)
					var right_3d := inches_to_world(cone["right"] as Vector2, def_y + 0.95)
					var cone_fill := Color(bcol.r, bcol.g, bcol.b, 0.32)
					tri_pts.append(apex_3d); tri_cols.append(cone_fill)
					tri_pts.append(left_3d); tri_cols.append(cone_fill)
					tri_pts.append(right_3d); tri_cols.append(cone_fill)
					add_ribbon_3d.call(apex_3d, left_3d, 0.04, Color(bcol.r, bcol.g, bcol.b, 0.85))
					add_ribbon_3d.call(apex_3d, right_3d, 0.04, Color(bcol.r, bcol.g, bcol.b, 0.85))

	# 4. FIGHT MODE: Crimson Melee Rings
	elif interaction_mode == "fight":
		var att_f := engine.get_op(selected_uid)
		if not att_f.is_empty():
			for enemy in KTGeometry.is_in_enemy_control_range(att_f, att_f["pos"], engine.state):
				var ep2: Vector2 = enemy["pos"]
				var ef2 := inches_to_world(ep2, elevation_at_inches(ep2) + 0.06)
				add_ring_3d.call(ef2, float(enemy["base_radius"]) + 0.22, KTTheme.COL_CRIMSON, 0.30, 32)

	# 5. Active 3D Weapon Bolt / Plasma Tracers!
	for tr in _tracers_3d:
		var prog := clampf(float(tr["t"]) / float(tr["dur"]), 0.0, 1.0)
		var wf: Vector3 = tr["from"]
		var wt: Vector3 = tr["to"]
		var head := wf.lerp(wt, minf(1.0, prog * 1.45))
		var tail := wf.lerp(wt, maxf(0.0, prog * 1.45 - 0.42))
		var tc: Color = tr["color"]
		add_ribbon_3d.call(tail, head, 0.11, Color(tc.r, tc.g, tc.b, 1.0 - prog * 0.4))
		add_seg.call(tail, head, Color(1.0, 0.96, 0.82, 1.0 - prog * 0.3))

	has_lines = line_pts.size() >= 2
	has_tris = tri_pts.size() >= 3

	if has_lines:
		_imm_lines.surface_begin(Mesh.PRIMITIVE_LINES)
		for k in range(line_pts.size()):
			_imm_lines.surface_set_color(line_cols[k])
			_imm_lines.surface_add_vertex(line_pts[k])
		_imm_lines.surface_end()

	if has_tris:
		_imm_tris.surface_begin(Mesh.PRIMITIVE_TRIANGLES)
		for k in range(tri_pts.size()):
			_imm_tris.surface_set_color(tri_cols[k])
			_imm_tris.surface_add_vertex(tri_pts[k])
		_imm_tris.surface_end()


# ==============================================================================
# CRISP 2D SCREEN-SPACE PROJECTED LABELS OVER THE 3D WORLD
# ==============================================================================

func _draw_projected_hud() -> void:
	if engine == null or engine.state.is_empty() or _cam == null:
		return

	# 0. Subtle Octopath HD-2D Atmospheric Edge Vignette Frame
	var vp_sz := _hud_overlay.size
	if vp_sz.x > 60 and vp_sz.y > 60:
		for band in range(6):
			var frac: float = float(6 - band) / 6.0
			var alpha_v: float = 0.048 * frac * frac
			var pad_v: float = float(band) * 18.0
			_hud_overlay.draw_rect(Rect2(0, pad_v, vp_sz.x, 18.0), Color(0.01, 0.014, 0.024, alpha_v), true)
			_hud_overlay.draw_rect(Rect2(0, vp_sz.y - pad_v - 18.0, vp_sz.x, 18.0), Color(0.01, 0.014, 0.024, alpha_v * 1.25), true)
			_hud_overlay.draw_rect(Rect2(pad_v, 0, 18.0, vp_sz.y), Color(0.01, 0.014, 0.024, alpha_v), true)
			_hud_overlay.draw_rect(Rect2(vp_sz.x - pad_v - 18.0, 0, 18.0, vp_sz.y), Color(0.01, 0.014, 0.024, alpha_v), true)

	# 1. Vantage deck labels: only while moving (when the climb matters) or when an operative stands up there.
	var show_vantage_tags := interaction_mode == "move"
	for t in engine.state["map"]["terrains"]:
		if str(t["kind"]) == "vantage":
			var rect: Rect2 = t["rect"]
			var elev := float(t["elevation"])
			var occupied := false
			if not show_vantage_tags:
				for op_v in engine.state["operatives"]:
					if not bool(op_v["incapacitated"]) and rect.has_point(op_v["pos"]):
						occupied = true
						break
			if not (show_vantage_tags or occupied):
				continue
			var corner_3d := inches_to_world(Vector2(rect.position.x + 0.9, rect.position.y + 0.45), elev + 0.35)
			if not _cam.is_position_behind(corner_3d):
				var sp := _cam.unproject_position(corner_3d)
				_draw_pill_2d(sp, "▲ +%.1f\"" % elev, KTTheme.COL_GOLD_BRIGHT, 11, 0.70)

	# 2. Objective Control Beacons
	for obj in engine.state["map"]["objectives"]:
		var oid := str(obj["id"])
		var st: Dictionary = engine.state["objectives_state"][oid]
		var opos: Vector2 = obj["pos"]
		var wpos := inches_to_world(opos, 1.28)
		if _cam.is_position_behind(wpos):
			continue
		var sp_o := _cam.unproject_position(wpos)
		var ctrl := str(st["controlled_by"])
		var sec := str(st["secured_by"])
		var col := KTTheme.COL_BRASS
		if ctrl == "A" or (ctrl == "" and sec == "A"):
			col = KTTheme.COL_IMPERIAL
		elif ctrl == "B" or (ctrl == "" and sec == "B"):
			col = KTTheme.COL_NURGLE
		# Objective marker: a numbered disc in the controller's colour. The APL tug-of-war shows only
		# when someone is actually contesting it; a secured flag shows as a small star.
		var num := oid.replace("obj_", "")
		var contested := int(st["apl_a"]) > 0 or int(st["apl_b"]) > 0
		var rad := 11.0
		_hud_overlay.draw_circle(sp_o, rad + 1.5, Color(0, 0, 0, 0.7))
		_hud_overlay.draw_circle(sp_o, rad, Color(col.r * 0.25, col.g * 0.25, col.b * 0.25, 0.92))
		_hud_overlay.draw_arc(sp_o, rad, 0.0, TAU, 32, col, 1.6, true)
		_hud_overlay.draw_string(font, sp_o + Vector2(-rad, 5.0), num, HORIZONTAL_ALIGNMENT_CENTER, rad * 2.0, 12, col.lightened(0.35))
		if not sec.is_empty():
			_hud_overlay.draw_string(font, sp_o + Vector2(rad - 2.0, -rad + 4.0), "★", HORIZONTAL_ALIGNMENT_LEFT, -1, 10, KTTheme.COL_GOLD_BRIGHT)
		if contested:
			_draw_pill_2d(sp_o + Vector2(0.0, 20.0), "APL %d : %d" % [int(st["apl_a"]), int(st["apl_b"])], col, 11, 0.80)

	# 3. Operative Nameplates, HP Bars, Order Icons & Elevation Badges!
	var active_uid := str(engine.state.get("active_op_uid", ""))
	for op in engine.state["operatives"]:
		if op["incapacitated"]:
			continue
		var uid := str(op["uid"])
		var w_feet: Vector3 = _visual_pos_3d.get(uid, inches_to_world(op["pos"], elevation_at_inches(op["pos"])))
		var w_head := w_feet + Vector3(0.0, 1.74, 0.0)
		if _cam.is_position_behind(w_head):
			continue
		var sp_op := _cam.unproject_position(w_head)

		var is_a := str(op["side"]) == "A"
		var fcol := KTTheme.COL_IMPERIAL if is_a else KTTheme.COL_NURGLE
		var is_active := (uid == active_uid)
		var is_ready := bool(op["ready"])
		var is_focus := is_active or uid == hovered_uid or uid == selected_uid
		var z_h := elevation_at_inches(op["pos"])
		var hp_ratio := clampf(float(op["wounds_cur"]) / float(maxi(1, int(op["wounds_max"]))), 0.0, 1.0)
		var hp_col := KTTheme.COL_EMERALD if hp_ratio >= 0.5 else (KTTheme.COL_GOLD_BRIGHT if hp_ratio >= 0.28 else KTTheme.COL_CRIMSON)
		var otex := _get_order_tex(str(op["order"]), is_ready or is_active)

		if not is_focus:
			# Compact tag: order icon + HP bar + status glyphs. The token itself already shows the role name.
			var cw := 58.0
			var cr := Rect2(sp_op.x - cw * 0.5, sp_op.y - 14.0, cw, 14.0)
			_hud_overlay.draw_rect(Rect2(cr.position.x - 1, cr.position.y - 1, cr.size.x + 2, cr.size.y + 2), Color(0, 0, 0, 0.55), true)
			_hud_overlay.draw_rect(cr, Color(0.032, 0.042, 0.065, 0.78 if is_ready else 0.55), true)
			_hud_overlay.draw_rect(cr, fcol if is_ready else fcol.darkened(0.45), false, 1.0)
			if otex != null:
				_hud_overlay.draw_texture_rect(otex, Rect2(cr.position.x + 1.0, cr.position.y + 1.0, 12.0, 12.0), false)
			var bar_x := cr.position.x + 15.0
			var bar_w := cr.end.x - 3.0 - bar_x
			_hud_overlay.draw_rect(Rect2(bar_x, cr.position.y + 5.0, bar_w, 4.0), Color(0.1, 0.1, 0.12, 0.9), true)
			_hud_overlay.draw_rect(Rect2(bar_x, cr.position.y + 5.0, bar_w * hp_ratio, 4.0), hp_col, true)
			var glyphs := ("☣" if bool(op["poisoned"]) else "") + ("⚡" if int(op["apl_mod"]) < 0 else "") + ("▲" if z_h >= 2.0 else "")
			if not glyphs.is_empty():
				_hud_overlay.draw_string(font, Vector2(cr.end.x + 2.0, cr.position.y + 11.0), glyphs, HORIZONTAL_ALIGNMENT_LEFT, -1, 10, KTTheme.COL_GOLD_BRIGHT)
			continue

		# Focused (active / hovered / selected): full nameplate with name, wounds and AP gems.
		var short_name := str(op["name"]).replace("仲裁者", "").replace("瘟疫战士", "瘟疫")
		var elev_badge := " ▲2F" if z_h >= 2.0 else ""
		var status_ico := (" ☣" if bool(op["poisoned"]) else "") + (" ⚡" if int(op["apl_mod"]) < 0 else "")
		var txt := "%s%s %d/%d%s" % [short_name, elev_badge, int(op["wounds_cur"]), int(op["wounds_max"]), status_ico]

		var ts := font.get_string_size(txt, HORIZONTAL_ALIGNMENT_LEFT, -1, 12)
		var pw := maxf(90.0, ts.x + 30.0)
		var pr := Rect2(sp_op.x - pw * 0.5, sp_op.y - 22.0, pw, 22.0)

		_hud_overlay.draw_rect(Rect2(pr.position.x - 1, pr.position.y - 1, pr.size.x + 2, pr.size.y + 2), Color(0, 0, 0, 0.65), true)
		_hud_overlay.draw_rect(pr, Color(0.032, 0.042, 0.065, 0.92), true)
		_hud_overlay.draw_rect(Rect2(pr.position.x, pr.position.y, 3.0, pr.size.y), KTTheme.COL_GOLD_BRIGHT if is_active else fcol, true)
		_hud_overlay.draw_rect(pr, KTTheme.COL_GOLD_BRIGHT if is_active else fcol, false, 1.8 if is_active else 1.1)

		if otex != null:
			_hud_overlay.draw_texture_rect(otex, Rect2(pr.position.x + 3.0, pr.position.y + 2.0, 18.0, 18.0), false)

		_hud_overlay.draw_string(font, Vector2(pr.position.x + 22.0, pr.position.y + 15.0), txt, HORIZONTAL_ALIGNMENT_CENTER, pw - 24.0, 12, KTTheme.COL_TEXT)
		_hud_overlay.draw_rect(Rect2(pr.position.x + 2, pr.end.y - 3.0, (pw - 4) * hp_ratio, 2.2), hp_col, true)

		if is_active:
			var gems := ""
			for _g in range(int(op["ap_left"])):
				gems += "◆ "
			_hud_overlay.draw_string(font, Vector2(sp_op.x - 40.0, pr.position.y - 5.0), gems.strip_edges(), HORIZONTAL_ALIGNMENT_CENTER, 80.0, 13, KTTheme.COL_GOLD_BRIGHT)

	# 4. Move Ruler Floating Callout (with Climb Indicator!)
	if interaction_mode == "move":
		var op_m := engine.get_op(selected_uid)
		if not op_m.is_empty():
			var pts_m: Array[Vector2] = move_waypoints.duplicate()
			pts_m.append(hovered_board_inches)
			var side_st: Dictionary = engine.state["sides"][op_m["side"]]
			var mob := KTCombat.has_chapter_tactic(side_st, op_m, "mobile")
			var max_m := engine.get_effective_move_inches(op_m, move_action_kind)
			var chk := KTGeometry.validate_move_path(pts_m, op_m, move_action_kind, max_m, engine.state, mob)
			var ok_m := bool(chk["ok"])
			var end_y := elevation_at_inches(hovered_board_inches)
			var start_y := elevation_at_inches(op_m["pos"])
			var climb_note := " [▲攀爬二层 +2\"]" if end_y > start_y + 0.5 else ""
			var msg_m := "%.1f\" / %.0f\"%s  %s" % [
				float(chk["cost"]), max_m, climb_note,
				"✓ 点击落脚" if ok_m else ("✕ " + str(chk["reason"]))
			]
			var w_end := inches_to_world(hovered_board_inches, end_y + 1.85)
			if not _cam.is_position_behind(w_end):
				_draw_pill_2d(_cam.unproject_position(w_end), msg_m, KTTheme.COL_EMERALD if ok_m else KTTheme.COL_CRIMSON, 14, 0.95)

	# 5. Shoot Mode Target Status Callouts + Animated XCOM Target-Lock Reticle & Shot Preview HUD!
	elif interaction_mode == "shoot":
		for def_uid in cached_shot_evals.keys():
			var def := engine.get_op(str(def_uid))
			if def.is_empty() or def["incapacitated"]:
				continue
			var ev: Dictionary = cached_shot_evals[def_uid]
			var elev_d := elevation_at_inches(def["pos"])
			var w_def := inches_to_world(def["pos"], elev_d + 2.15)
			var w_body := inches_to_world(def["pos"], elev_d + 0.92)
			if _cam.is_position_behind(w_def):
				continue
			var sp_d := _cam.unproject_position(w_def) + Vector2(0.0, -28.0)
			var sp_body := _cam.unproject_position(w_body)
			# Text callouts only for the hovered target; everyone else is described by ring colour.
			if hovered_uid != def_uid:
				continue
			if not bool(ev["valid"]):
				_draw_pill_2d(sp_d, "🔒 " + str(ev["reason"]), Color(0.68, 0.70, 0.75), 13, 0.94)
				continue
			var bcol := KTTheme.COL_EMERALD
			var st_txt := "🎯 暴露"
			if bool(ev["obscured"]):
				bcol = KTTheme.COL_VIOLET
				st_txt = "🌫️ 重型遮挡"
			elif str(ev["cover"]) != "none":
				bcol = KTTheme.COL_GOLD_BRIGHT
				st_txt = "🛡️ 掩护"
			if int(ev.get("vantage_accurate", 0)) > 0:
				st_txt += " · ▲精准+%d" % int(ev["vantage_accurate"])
			_draw_pill_2d(sp_d, "%s · %.1f\"" % [st_txt, float(ev["distance"])], bcol, 12, 0.92)
			_draw_target_lock_reticle(sp_body, bcol, active_shoot_weapon, ev)

	# 5b. Fight Mode Animated Melee Lock Reticle!
	elif interaction_mode == "fight" and not hovered_uid.is_empty():
		var def_f := engine.get_op(hovered_uid)
		if not def_f.is_empty() and not bool(def_f["incapacitated"]):
			var wb_f := inches_to_world(def_f["pos"], elevation_at_inches(def_f["pos"]) + 0.92)
			if not _cam.is_position_behind(wb_f):
				_draw_target_lock_reticle(_cam.unproject_position(wb_f), KTTheme.COL_CRIMSON, {}, {})

	# 6. Floating 3D Combat Damage Popups
	for ft in _float_texts_3d:
		var wp: Vector3 = ft["wpos"]
		if _cam.is_position_behind(wp):
			continue
		var sp_f := _cam.unproject_position(wp)
		var alpha := 1.0 - clampf(float(ft["t"]) / float(ft["dur"]), 0.0, 1.0)
		var cf: Color = ft["color"]
		var fmsg := str(ft["msg"])
		_hud_overlay.draw_string(font, sp_f - Vector2(139, -1), fmsg, HORIZONTAL_ALIGNMENT_CENTER, 280, 16, Color(0, 0, 0, alpha))
		_hud_overlay.draw_string(font, sp_f - Vector2(140, 0), fmsg, HORIZONTAL_ALIGNMENT_CENTER, 280, 16, Color(cf.r, cf.g, cf.b, alpha))

	# 7. Physical 3D Tabletop Dice Tray Floating Bay Headers & Settled Die Micro-Badges!
	if _dice_3d != null and _dice_3d.active_mode != "idle":
		if not _dice_3d.left_bay_label.is_empty():
			var wl := _dice_3d.get_bay_header_world_pos(true)
			if not _cam.is_position_behind(wl):
				_draw_pill_2d(_cam.unproject_position(wl) + Vector2(0.0, -10.0), _dice_3d.left_bay_label, KTTheme.COL_CRIMSON if _dice_3d.active_mode == "shooting" else KTTheme.COL_IMPERIAL, 12, 0.92)
		if not _dice_3d.right_bay_label.is_empty():
			var wr := _dice_3d.get_bay_header_world_pos(false)
			if not _cam.is_position_behind(wr):
				_draw_pill_2d(_cam.unproject_position(wr) + Vector2(0.0, -10.0), _dice_3d.right_bay_label, KTTheme.COL_EMERALD if _dice_3d.active_mode == "shooting" else KTTheme.COL_NURGLE, 12, 0.92)

		# Rule badges floating above dice the last rule touched (rise + fade)
		for bd in _dice_3d.get_die_badges():
			var bw: Vector3 = bd["wpos"]
			if _cam.is_position_behind(bw):
				continue
			var rise: float = float(bd["rise"])
			var sp_b := _cam.unproject_position(bw) + Vector2(0.0, -14.0 * rise)
			var bc: Color = bd["col"]
			var ba: float = float(bd["alpha"])
			var btxt := str(bd["text"])
			var ts_b := font.get_string_size(btxt, HORIZONTAL_ALIGNMENT_LEFT, -1, 12)
			var br_b := Rect2(sp_b.x - ts_b.x * 0.5 - 7.0, sp_b.y - 10.0, ts_b.x + 14.0, 20.0)
			_hud_overlay.draw_rect(br_b, Color(0.02, 0.025, 0.04, 0.92 * ba), true)
			_hud_overlay.draw_rect(br_b, Color(bc.r, bc.g, bc.b, ba), false, 1.6)
			_hud_overlay.draw_string(font, Vector2(br_b.position.x, br_b.position.y + 14.5), btxt, HORIZONTAL_ALIGNMENT_CENTER, br_b.size.x, 12, Color(bc.r, bc.g, bc.b, ba).lightened(0.3))

		for dt in _dice_3d.get_active_dice_hud_tags():
			var dw: Vector3 = dt["wpos"]
			if _cam.is_position_behind(dw):
				continue
			var sp_die := _cam.unproject_position(dw) + Vector2(0.0, 4.0)
			var dv: int = int(dt["val"])
			var dk: String = str(dt["kind"])
			var dsel: bool = bool(dt["selected"])
			var dhov: bool = bool(dt["hovered"])
			var drr: bool = bool(dt["rerolled"])
			var dcol: Color = KTTheme.COL_GOLD_BRIGHT if dk == "crit" else (KTTheme.COL_EMERALD if dk == "norm" else (KTTheme.COL_BRASS if dk == "init" else KTTheme.COL_MUTED))
			if dsel:
				dcol = Color(1.0, 0.96, 0.72)
			var prefix_s: String = "★" if dk == "crit" else ("●" if dk == "norm" else ("" if dk == "init" else "✕"))
			var suffix_s: String = " ✓选" if dsel else (" 重" if drr else (" 点" if dk == "init" else ""))
			var badge_txt: String = "%s%d%s" % [prefix_s, dv, suffix_s]
			var ts_d := font.get_string_size(badge_txt, HORIZONTAL_ALIGNMENT_LEFT, -1, 10)
			var bw_d := maxf(32.0, ts_d.x + 10.0)
			var br_d := Rect2(sp_die.x - bw_d * 0.5, sp_die.y - 8.0, bw_d, 16.0)
			_hud_overlay.draw_rect(br_d, Color(0.020, 0.026, 0.044, 0.92), true)
			_hud_overlay.draw_rect(br_d, dcol, false, 1.8 if (dsel or dhov) else 1.2)
			_hud_overlay.draw_string(font, Vector2(br_d.position.x, br_d.position.y + 12.0), badge_txt, HORIZONTAL_ALIGNMENT_CENTER, bw_d, 10, dcol.lightened(0.28))


func _draw_target_lock_reticle(sp_center: Vector2, col: Color, weapon: Dictionary, ev: Dictionary) -> void:
	var pulse_r: float = 34.0 + 3.5 * sin(_pulse_time * 7.5)
	var rot_a: float = _pulse_time * 1.65
	_hud_overlay.draw_arc(sp_center, pulse_r, rot_a, rot_a + PI * 0.35, 12, col, 2.5, true)
	_hud_overlay.draw_arc(sp_center, pulse_r, rot_a + PI * 0.5, rot_a + PI * 0.85, 12, col, 2.5, true)
	_hud_overlay.draw_arc(sp_center, pulse_r, rot_a + PI, rot_a + PI * 1.35, 12, col, 2.5, true)
	_hud_overlay.draw_arc(sp_center, pulse_r, rot_a + PI * 1.5, rot_a + PI * 1.85, 12, col, 2.5, true)

	# Corner Snap Brackets
	var br: float = pulse_r + 7.0
	var bl: float = 9.0
	for sx in [-1.0, 1.0]:
		for sy in [-1.0, 1.0]:
			var corner := sp_center + Vector2(sx * br, sy * br)
			_hud_overlay.draw_line(corner, corner + Vector2(-sx * bl, 0.0), KTTheme.COL_GOLD_BRIGHT, 2.2)
			_hud_overlay.draw_line(corner, corner + Vector2(0.0, -sy * bl), KTTheme.COL_GOLD_BRIGHT, 2.2)

	if not weapon.is_empty():
		var hit_req: int = int(weapon.get("hit", 3))
		var hit_pct: int = clampi(int(round(float(7 - hit_req) / 6.0 * 100.0)), 16, 95)
		var atk_n: int = int(weapon.get("attacks", 4))
		var ndmg: int = int(weapon.get("normal_dmg", 3))
		var cdmg: int = int(weapon.get("crit_dmg", 4))
		var card_pos := sp_center + Vector2(pulse_r + 16.0, -30.0)
		if card_pos.x + 196.0 > _hud_overlay.size.x - 20.0:
			card_pos.x = sp_center.x - pulse_r - 212.0
		var card_r := Rect2(card_pos, Vector2(196.0, 58.0))
		KTWidgets.draw_chamfer_rect(_hud_overlay, card_r, 7.0, Color(0.03, 0.04, 0.065, 0.94), col, 2.0)
		_hud_overlay.draw_string(font, card_r.position + Vector2(10.0, 20.0), "🎯 命中率 %d%% (%d+)" % [hit_pct, hit_req], HORIZONTAL_ALIGNMENT_LEFT, 180.0, 14, KTTheme.COL_GOLD_BRIGHT)
		_hud_overlay.draw_string(font, card_r.position + Vector2(10.0, 38.0), "%d 骰  ｜  伤害 %d / %d★" % [atk_n, ndmg, cdmg], HORIZONTAL_ALIGNMENT_LEFT, 180.0, 12, KTTheme.COL_TEXT)
		var note := "点击左键立即开火!" if not ev.is_empty() else ""
		_hud_overlay.draw_string(font, card_r.position + Vector2(10.0, 52.0), note, HORIZONTAL_ALIGNMENT_LEFT, 180.0, 10, col.lightened(0.28))


func _draw_pill_2d(center_px: Vector2, txt: String, col: Color, fsize: int = 13, bg_alpha: float = 0.92) -> void:
	var ts := font.get_string_size(txt, HORIZONTAL_ALIGNMENT_LEFT, -1, fsize)
	var w := maxf(96.0, ts.x + 20.0)
	var h := float(fsize) + 11.0
	var r := Rect2(center_px.x - w * 0.5, center_px.y - h * 0.5, w, h)
	_hud_overlay.draw_rect(r, Color(0.035, 0.045, 0.065, bg_alpha), true)
	_hud_overlay.draw_rect(r, col, false, 1.5)
	_hud_overlay.draw_string(font, Vector2(r.position.x, r.position.y + h - 6.5), txt, HORIZONTAL_ALIGNMENT_CENTER, w, fsize, col.lightened(0.25))


func _get_order_tex(order_str: String, ready_flag: bool) -> Texture2D:
	if order_str == "engage":
		return _tex_engage_ready if ready_flag else _tex_engage_spent
	return _tex_conceal_ready if ready_flag else _tex_conceal_spent


# ==============================================================================
# ELEVATION-AWARE 3D RAY PICKING & CAMERA INPUT CONTROLLER
# ==============================================================================

func _ray_pick_board_inches(screen_pos: Vector2) -> Vector2:
	if _cam == null or engine == null or engine.state.is_empty():
		return hovered_board_inches
	var ro := _cam.project_ray_origin(screen_pos)
	var rd := _cam.project_ray_normal(screen_pos)

	# 1. Check elevated Vantage Decks (+3.5") FIRST so hovering the 2nd floor snaps right onto the upper deck!
	for t in engine.state["map"]["terrains"]:
		if str(t["kind"]) != "vantage":
			continue
		var elev := float(t["elevation"])
		var hit_upper: Variant = Plane(Vector3.UP, elev).intersects_ray(ro, rd)
		if hit_upper != null:
			var cand_in := world_to_inches(hit_upper as Vector3)
			if (t["rect"] as Rect2).has_point(cand_in):
				return cand_in

	# 2. Intersect Ground Plane (Y = 0)
	var hit_ground: Variant = Plane(Vector3.UP, 0.0).intersects_ray(ro, rd)
	if hit_ground != null:
		var g_in := world_to_inches(hit_ground as Vector3)
		return Vector2(
			clampf(g_in.x, 0.4, KTData.BOARD_WIDTH_INCHES - 0.4),
			clampf(g_in.y, 0.4, KTData.BOARD_HEIGHT_INCHES - 0.4)
		)
	return hovered_board_inches


func _ray_pick_operative(screen_pos: Vector2) -> String:
	if _cam == null or engine == null or engine.state.is_empty():
		return ""
	var ro := _cam.project_ray_origin(screen_pos)
	var rd := _cam.project_ray_normal(screen_pos)

	var best_uid := ""
	var best_t := 9999.0

	for op in engine.state["operatives"]:
		if op["incapacitated"]:
			continue
		var uid := str(op["uid"])
		var base_w: Vector3 = _visual_pos_3d.get(uid, inches_to_world(op["pos"], elevation_at_inches(op["pos"])))
		# Test closest approach against miniature vertical capsule (base Y+0.0 to head Y+1.9)
		var r_pick := float(op["base_radius"]) + 0.18
		for sample_h in [0.25, 0.95, 1.55]:
			var center_s := base_w + Vector3(0.0, float(sample_h), 0.0)
			var to_c := center_s - ro
			var proj := to_c.dot(rd)
			if proj <= 0.0:
				continue
			var closest := ro + rd * proj
			if closest.distance_to(center_s) <= r_pick and proj < best_t:
				best_t = proj
				best_uid = uid
	return best_uid


func _gui_input(event: InputEvent) -> void:
	if engine == null or engine.state.is_empty():
		return

	if event is InputEventKey and event.pressed:
		if event.keycode == KEY_F:
			reset_isometric_view(false)
			accept_event()
			return
		if event.keycode == KEY_ESCAPE:
			if interaction_mode != "idle":
				clear_interaction()
				cancel_interaction_requested.emit()
				accept_event()
				return

	if event is InputEventMouseButton:
		# Smooth Mouse Wheel Zoom
		if event.button_index == MOUSE_BUTTON_WHEEL_UP and event.pressed:
			target_dist = clampf(target_dist * 0.90, 18.0, 68.0)
			accept_event()
			return
		elif event.button_index == MOUSE_BUTTON_WHEEL_DOWN and event.pressed:
			target_dist = clampf(target_dist * 1.11, 18.0, 68.0)
			accept_event()
			return

		# Middle Mouse Button = Pan
		if event.button_index == MOUSE_BUTTON_MIDDLE:
			_mid_pan_down = event.pressed
			_last_mouse_pos = event.position
			accept_event()
			return

		# Right Mouse Button = Drag to Orbit/Tilt 2.55D Camera, or Click to Cancel/Undo Waypoint!
		if event.button_index == MOUSE_BUTTON_RIGHT:
			if event.pressed:
				_orbit_btn_down = true
				_orbit_dragged = false
				_orbit_start_pos = event.position
				_last_mouse_pos = event.position
			else:
				var was_drag := _orbit_dragged
				_orbit_btn_down = false
				_orbit_dragged = false
				if not was_drag and interaction_mode != "idle":
					if interaction_mode == "move" and move_waypoints.size() > 1:
						move_waypoints.pop_back()
					else:
						clear_interaction()
						cancel_interaction_requested.emit()
			accept_event()
			return

		# Left Mouse Click = Select Operative / Place Waypoint / Commit Move / Shoot / Fight!
		if event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
			_handle_left_click(event.position, event.shift_pressed)
			accept_event()
			return

	if event is InputEventMouseMotion:
		if _mid_pan_down or (_orbit_btn_down and event.shift_pressed):
			var d_pan: Vector2 = event.position - _last_mouse_pos
			_last_mouse_pos = event.position
			var pan_scale := cam_dist * 0.0011
			var yaw_r := deg_to_rad(cam_yaw_deg)
			var right_v := Vector3(cos(yaw_r), 0.0, -sin(yaw_r))
			var fwd_v := Vector3(sin(yaw_r), 0.0, cos(yaw_r))
			target_focus -= right_v * (d_pan.x * pan_scale) + fwd_v * (d_pan.y * pan_scale)
			target_focus.x = clampf(target_focus.x, -22.0, 22.0)
			target_focus.z = clampf(target_focus.z, -19.0, 19.0)
			accept_event()
			return

		if _orbit_btn_down:
			if event.position.distance_to(_orbit_start_pos) > 5.0:
				_orbit_dragged = true
			var d_orb: Vector2 = event.position - _last_mouse_pos
			_last_mouse_pos = event.position
			target_yaw_deg -= d_orb.x * 0.34
			target_pitch_deg = clampf(target_pitch_deg + d_orb.y * 0.24, 24.0, 74.0)
			accept_event()
			return

		if _dice_3d != null and _dice_3d.active_mode != "idle":
			var ro_d := _cam.project_ray_origin(event.position)
			var rd_d := _cam.project_ray_normal(event.position)
			var hov_die := _dice_3d.update_hover_from_ray(ro_d, rd_d)
			mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND if hov_die else Control.CURSOR_ARROW
			if hov_die:
				hover_info_changed.emit("🎲 左键点击该 3D 桌面骰子可选中并执行【重掷】")
			return
		mouse_default_cursor_shape = Control.CURSOR_ARROW

		hovered_board_inches = _ray_pick_board_inches(event.position)
		var next_hov := _ray_pick_operative(event.position)
		if next_hov != hovered_uid and not next_hov.is_empty():
			KTSfx.play_hover()
		hovered_uid = next_hov
		_emit_hover_status()


func _handle_left_click(screen_pos: Vector2, shift_held: bool) -> void:
	if _dice_3d != null and _dice_3d.active_mode != "idle":
		var ro_c := _cam.project_ray_origin(screen_pos)
		var rd_c := _cam.project_ray_normal(screen_pos)
		var hit_d := _dice_3d.pick_die_from_ray(ro_c, rd_c)
		if not hit_d.is_empty():
			KTSfx.play("ui_click", -4.0, 0.05)
			tabletop_die_clicked.emit(str(hit_d["pool"]), int(hit_d["index"]))
		return

	hovered_board_inches = _ray_pick_board_inches(screen_pos)
	var clicked_op_uid := _ray_pick_operative(screen_pos)

	if interaction_mode == "move":
		var op := engine.get_op(selected_uid)
		if op.is_empty():
			clear_interaction()
			return
		var pts: Array[Vector2] = move_waypoints.duplicate()
		pts.append(hovered_board_inches)
		var side_st: Dictionary = engine.state["sides"][op["side"]]
		var mobile := KTCombat.has_chapter_tactic(side_st, op, "mobile")
		var max_m := engine.get_effective_move_inches(op, move_action_kind)
		var chk := KTGeometry.validate_move_path(pts, op, move_action_kind, max_m, engine.state, mobile)
		if shift_held:
			if move_waypoints[-1].distance_to(hovered_board_inches) >= 0.4:
				move_waypoints.append(hovered_board_inches)
		elif bool(chk["ok"]):
			ground_move_committed.emit(pts)
		return

	if interaction_mode == "shoot":
		if not clicked_op_uid.is_empty() and cached_shot_evals.has(clicked_op_uid):
			var ev: Dictionary = cached_shot_evals[clicked_op_uid]
			if bool(ev["valid"]):
				shoot_target_clicked.emit(clicked_op_uid, ev)
		return

	if interaction_mode == "fight":
		if not clicked_op_uid.is_empty():
			var att := engine.get_op(selected_uid)
			var def := engine.get_op(clicked_op_uid)
			if not att.is_empty() and not def.is_empty() and att["side"] != def["side"]:
				var z_a := elevation_at_inches(att["pos"])
				var z_d := elevation_at_inches(def["pos"])
				if KTGeometry.in_control_range_at(att["pos"], float(att["base_radius"]), z_a, def["pos"], float(def["base_radius"]), z_d, engine.state["map"]["terrains"]):
					fight_target_clicked.emit(clicked_op_uid)
		return

	if not clicked_op_uid.is_empty():
		selected_uid = clicked_op_uid
		operative_clicked.emit(clicked_op_uid)


func _emit_hover_status() -> void:
	if not hovered_uid.is_empty():
		var op := engine.get_op(hovered_uid)
		if not op.is_empty():
			if interaction_mode == "shoot" and cached_shot_evals.has(hovered_uid):
				var ev: Dictionary = cached_shot_evals[hovered_uid]
				hover_info_changed.emit("🎯 瞄准【%s】 · %s" % [str(op["name"]), str(ev["reason"])])
				return
			var z_op := elevation_at_inches(op["pos"])
			var ord_cn := "隐匿" if str(op["order"]) == "conceal" else "交战"
			hover_info_changed.emit("%s  ｜  耐伤 %d/%d · 命令:%s · 豁免:%d+%s" % [
				str(op["name"]), int(op["wounds_cur"]), int(op["wounds_max"]),
				ord_cn, int(op["save"]),
				(" · ▲二层制高点(+%.1f\")" % z_op) if z_op > 0.5 else ""
			])
			return

	var z_c := elevation_at_inches(hovered_board_inches)
	hover_info_changed.emit("坐标 (%.1f\", %.1f\")%s  ｜  按住右键拖拽旋转视角 · 滚轮缩放 · Q/E 环绕" % [
		hovered_board_inches.x, hovered_board_inches.y,
		(" · ▲二层露台(+%.1f\")" % z_c) if z_c > 0.5 else ""
	])
