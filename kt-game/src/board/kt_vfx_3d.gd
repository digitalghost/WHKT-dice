class_name KTVfx3D
extends Node3D

## Real-Time 3D Particle System & Combat Choreography Engine for Kill Team HD-2D:
## - Ambient HD-2D floating warzone embers & golden dust motes across the 3D Volkus diorama
## - Weapon-typed Ranged Shooting VFX (Bolter multi-round bursts, shell casings, Plasma sunbolts,
##   Sniper hyper-rails, Frag/Blight parabolic grenades, Psychic warp vortices)
## - Impact, Shield Block, Toxic Acid & Incapacitation Particle Explosions + Dynamic 3D Point Lights
## - Melee Strike & Parry VFX (3D Crescent Slash Arcs, Crossed-Blade Clash Sparks, Blood/Ceramite Shards, Shockwaves)

const Models3D = preload("res://src/board/kt_models_3d.gd")

static var _mat_add_soft: StandardMaterial3D = null
static var _mat_alpha_smoke: StandardMaterial3D = null
static var _mat_spark_streak: StandardMaterial3D = null

var _projectiles: Array[Dictionary] = []
var _transient_fx: Array[Dictionary] = []
var _ambient_motes: CPUParticles3D = null


func clear_transient() -> void:
	_projectiles.clear()
	_transient_fx.clear()
	for c in get_children():
		if c == _ambient_motes:
			continue
		c.queue_free()


static func _ensure_materials() -> void:
	if _mat_add_soft == null:
		_mat_add_soft = StandardMaterial3D.new()
		_mat_add_soft.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		_mat_add_soft.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
		_mat_add_soft.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		_mat_add_soft.vertex_color_use_as_albedo = true
		_mat_add_soft.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
		_mat_add_soft.billboard_keep_scale = true
		_mat_add_soft.albedo_texture = Models3D.get_soft_particle_tex()
		_mat_add_soft.albedo_color = Color(2.4, 2.4, 2.4, 1.0)

	if _mat_alpha_smoke == null:
		_mat_alpha_smoke = StandardMaterial3D.new()
		_mat_alpha_smoke.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		_mat_alpha_smoke.blend_mode = BaseMaterial3D.BLEND_MODE_MIX
		_mat_alpha_smoke.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		_mat_alpha_smoke.vertex_color_use_as_albedo = true
		_mat_alpha_smoke.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
		_mat_alpha_smoke.billboard_keep_scale = true
		_mat_alpha_smoke.albedo_texture = Models3D.get_soft_particle_tex()

	if _mat_spark_streak == null:
		_mat_spark_streak = StandardMaterial3D.new()
		_mat_spark_streak.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		_mat_spark_streak.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
		_mat_spark_streak.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		_mat_spark_streak.vertex_color_use_as_albedo = true
		_mat_spark_streak.cull_mode = BaseMaterial3D.CULL_DISABLED
		_mat_spark_streak.albedo_texture = Models3D.get_soft_particle_tex()
		_mat_spark_streak.albedo_color = Color(2.8, 2.8, 2.8, 1.0)


func _ready() -> void:
	_ensure_materials()
	_spawn_ambient_hd2d_motes()


func _spawn_ambient_hd2d_motes() -> void:
	# Subtle HD-2D drifting upper-air dust motes (kept high & soft so ground stays 100% calm!)
	var motes := CPUParticles3D.new()
	motes.position = Vector3(0.0, 3.6, 0.0)
	motes.amount = 28
	motes.lifetime = 6.5
	motes.preprocess = 3.0
	motes.local_coords = false

	var qm := QuadMesh.new()
	qm.size = Vector2(0.11, 0.11)
	qm.material = _mat_add_soft
	motes.mesh = qm

	motes.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	motes.emission_box_extents = Vector3(14.5, 1.8, 10.5)
	motes.direction = Vector3(0.35, 0.65, -0.25)
	motes.spread = 55.0
	motes.gravity = Vector3(0.05, 0.08, -0.04)
	motes.initial_velocity_min = 0.12
	motes.initial_velocity_max = 0.32
	motes.scale_amount_min = 0.40
	motes.scale_amount_max = 0.95

	var curve := Curve.new()
	curve.add_point(Vector2(0.0, 0.0))
	curve.add_point(Vector2(0.25, 1.0))
	curve.add_point(Vector2(0.75, 0.9))
	curve.add_point(Vector2(1.0, 0.0))
	motes.scale_amount_curve = curve

	var grad := Gradient.new()
	grad.set_color(0, Color(1.0, 0.78, 0.38, 0.0))
	grad.add_point(0.25, Color(1.0, 0.76, 0.36, 0.24))
	grad.add_point(0.70, Color(1.0, 0.56, 0.22, 0.18))
	grad.set_color(grad.get_point_count() - 1, Color(0.85, 0.35, 0.12, 0.0))
	motes.color_ramp = grad
	_ambient_motes = motes
	add_child(motes)


func _process(delta: float) -> void:
	# 1. Update high-velocity 3D projectiles (bolts, plasma orbs, grenades)
	var i := _projectiles.size() - 1
	while i >= 0:
		var pr: Dictionary = _projectiles[i]
		var delay_left: float = float(pr["delay"])
		if delay_left > 0.0:
			delay_left -= delta
			pr["delay"] = delay_left
			if delay_left <= 0.0:
				var pnode_start: Node3D = pr["node"]
				if is_instance_valid(pnode_start):
					pnode_start.visible = true
					_spawn_muzzle_burst(pr["from"] as Vector3, pr["dir"] as Vector3, pr["color"] as Color, str(pr["wtype"]))
			i -= 1
			continue

		var t_cur: float = float(pr["t"]) + delta
		var dur: float = float(pr["dur"])
		pr["t"] = t_cur
		var u: float = clampf(t_cur / maxf(0.01, dur), 0.0, 1.0)
		var p_from: Vector3 = pr["from"]
		var p_to: Vector3 = pr["to"]
		var pos_now: Vector3 = p_from.lerp(p_to, u)
		var arc_h: float = float(pr["arc"])
		if arc_h > 0.01:
			pos_now.y += sin(u * PI) * arc_h
		var pnode: Node3D = pr["node"]
		if is_instance_valid(pnode):
			pnode.position = pos_now

		if t_cur >= dur:
			var is_mini_impact: bool = bool(pr["impact_burst"])
			if is_mini_impact:
				_spawn_quick_hit_sparks(p_to, pr["color"] as Color, str(pr["wtype"]) == "blast")
			if is_instance_valid(pnode):
				pnode.queue_free()
			_projectiles.remove_at(i)
		i -= 1

	# 2. Update transient shockwaves, slash arcs, shield domes & dynamic lights
	var j := _transient_fx.size() - 1
	while j >= 0:
		var fx: Dictionary = _transient_fx[j]
		var ft: float = float(fx["t"]) + delta
		var fdur: float = float(fx["dur"])
		fx["t"] = ft
		var fu: float = clampf(ft / maxf(0.01, fdur), 0.0, 1.0)
		var fkind: String = str(fx["kind"])
		var fnode: Node3D = fx["node"]

		if is_instance_valid(fnode):
			if fkind == "shockwave":
				var s_min: float = float(fx["s0"])
				var s_max: float = float(fx["s1"])
				var sc: float = lerpf(s_min, s_max, 1.0 - pow(1.0 - fu, 2.4))
				fnode.scale = Vector3(sc, 1.0, sc)
				var smat: StandardMaterial3D = fx["mat"]
				var scol: Color = fx["col"]
				smat.albedo_color = Color(scol.r, scol.g, scol.b, (1.0 - fu) * 0.88)
			elif fkind == "slash_arc":
				var sc_a: float = lerpf(0.65, 1.38, 1.0 - pow(1.0 - fu, 2.2))
				fnode.scale = Vector3(sc_a, sc_a, sc_a)
				fnode.rotate_object_local(Vector3.UP, delta * 7.5)
				var amat: StandardMaterial3D = fx["mat"]
				var acol: Color = fx["col"]
				amat.albedo_color = Color(acol.r, acol.g, acol.b, (1.0 - fu) * 0.95)
			elif fkind == "shield_dome":
				var sc_d: float = lerpf(0.75, 1.22, sin(fu * PI * 0.65))
				fnode.scale = Vector3.ONE * sc_d
				var dmat: StandardMaterial3D = fx["mat"]
				var dcol: Color = fx["col"]
				dmat.albedo_color = Color(dcol.r, dcol.g, dcol.b, (1.0 - fu) * 0.62)
			elif fkind == "flash_light":
				var omni: OmniLight3D = fnode as OmniLight3D
				var e0: float = float(fx["energy"])
				omni.light_energy = e0 * (1.0 - fu) * (1.0 - fu)

		if ft >= fdur:
			if is_instance_valid(fnode):
				fnode.queue_free()
			_transient_fx.remove_at(j)
		j -= 1


# ==============================================================================
# WEAPON CLASSIFICATION HELPER
# ==============================================================================

static func classify_weapon_vfx(weapon: Dictionary, is_imperial: bool) -> Dictionary:
	var wname := str(weapon.get("name", ""))
	var rules: Dictionary = weapon.get("rules", {})
	if wname.contains("等离子") or bool(rules.get("hot", false)):
		return {"type": "plasma", "col": Color(0.24, 0.88, 1.0)}
	if bool(rules.get("psychic", false)) or wname.contains("灵能") or wname.contains("瘟疫之风") or wname.contains("衰败") or wname.contains("腐化法杖"):
		return {"type": "psychic", "col": Color(0.78, 0.34, 1.0)}
	if float(rules.get("blast", 0.0)) > 0.0 or wname.contains("手雷") or wname.contains("榴弹") or wname.contains("枯萎发射器"):
		var bcol := Color(1.0, 0.56, 0.16) if is_imperial else Color(0.62, 0.96, 0.22)
		return {"type": "blast", "col": bcol}
	if wname.contains("狙击"):
		return {"type": "sniper", "col": Color(1.0, 0.82, 0.28)}
	if bool(rules.get("poison", false)) or wname.contains("瘟疫") or wname.contains("腐败"):
		return {"type": "plague_bolter", "col": Color(0.64, 0.98, 0.24)}
	return {"type": "bolter", "col": Color(1.0, 0.68, 0.20) if is_imperial else Color(0.72, 0.95, 0.28)}


# ==============================================================================
# PUBLIC API: RANGED SHOOTING PARTICLE SALVOS & IMPACTS
# ==============================================================================

func spawn_ranged_salvo(w_from: Vector3, w_to: Vector3, weapon: Dictionary, is_imperial: bool) -> void:
	_ensure_materials()
	var info := classify_weapon_vfx(weapon, is_imperial)
	var wtype := str(info["type"])
	var col: Color = info["col"]
	var dir := (w_to - w_from).normalized()
	if dir.length_squared() < 0.001:
		dir = Vector3.FORWARD

	var muzzle_pos := w_from + dir * 0.55

	if wtype == "bolter" or wtype == "plague_bolter":
		var shots := clampi(int(weapon.get("attacks", 4)), 3, 5)
		for s in range(shots):
			var spread_off := Vector3(randf_range(-0.14, 0.14), randf_range(-0.08, 0.10), randf_range(-0.14, 0.14))
			_queue_projectile(muzzle_pos, w_to + spread_off, col, wtype, float(s) * 0.075, 0.20, 0.0, true)
	elif wtype == "sniper":
		_queue_projectile(muzzle_pos, w_to, col, wtype, 0.0, 0.13, 0.0, true)
		_spawn_shockwave_ring(muzzle_pos, col, 0.25, 1.35, 0.28)
	elif wtype == "plasma":
		_queue_projectile(muzzle_pos, w_to, col, wtype, 0.0, 0.22, 0.0, true)
	elif wtype == "blast":
		_queue_projectile(muzzle_pos, w_to, col, wtype, 0.0, 0.36, 2.35, true)
	elif wtype == "psychic":
		_spawn_warp_cast_vortex(muzzle_pos, col)
		for s in range(3):
			_queue_projectile(muzzle_pos, w_to, col if s % 2 == 0 else Color(0.55, 0.98, 0.26), wtype, float(s) * 0.06, 0.24, 0.35, true)


func spawn_ranged_resolution_explosion(
	w_target: Vector3,
	dmg: int,
	crits: int,
	killed: bool,
	poisoned: bool,
	weapon: Dictionary,
	is_imperial_att: bool
) -> void:
	_ensure_materials()
	var info := classify_weapon_vfx(weapon, is_imperial_att)
	var col: Color = info["col"]

	if dmg <= 0:
		# Dramatic Golden/Cyan Armour Save Shield Dome + Ricochet Sparks!
		var shield_c := Color(0.32, 0.78, 1.0) if not is_imperial_att else Color(0.68, 0.95, 0.32)
		_spawn_shield_dome(w_target, shield_c)
		_spawn_one_shot_sparks(w_target, Vector3.UP, shield_c, 34, 3.2, 6.8, 0.45, 0.22)
		_spawn_flash_light(w_target + Vector3(0.0, 0.5, 0.0), shield_c, 3.5, 5.5, 0.32)
		return

	var spark_count := mini(110, 48 + dmg * 6 + crits * 16)
	_spawn_one_shot_sparks(w_target, Vector3.UP, col, spark_count, 4.0, 9.5, 0.68, 0.28)
	_spawn_smoke_plume(w_target, Color(0.18, 0.19, 0.22, 0.78), 22 if not killed else 38)
	_spawn_shockwave_ring(Vector3(w_target.x, w_target.y - 0.85, w_target.z), col, 0.35, 2.6 if not killed else 4.2, 0.42)
	_spawn_flash_light(w_target + Vector3(0.0, 0.6, 0.0), col, 5.2 if not killed else 8.5, 8.0, 0.42)

	# Crimson ceramite/blood burst on heavy wound!
	_spawn_one_shot_sparks(w_target, Vector3.UP, Color(0.95, 0.18, 0.20), 26, 2.5, 6.2, 0.55, 0.24)

	if poisoned or bool(weapon.get("rules", {}).get("poison", false)):
		_spawn_toxic_miasma_burst(w_target)

	if killed:
		_spawn_shockwave_ring(Vector3(w_target.x, w_target.y - 0.80, w_target.z), Color(1.0, 0.30, 0.18), 0.4, 5.0, 0.58)
		_spawn_one_shot_sparks(w_target, Vector3.UP, Color(1.0, 0.86, 0.32), 85, 5.5, 12.5, 0.88, 0.36)


# ==============================================================================
# PUBLIC API: MELEE DUEL PARTICLE CHOREOGRAPHY (STRIKE & PARRY!)
# ==============================================================================

func spawn_melee_engage(w_att: Vector3, w_def: Vector3) -> void:
	_ensure_materials()
	var mid := w_att.lerp(w_def, 0.5)
	_spawn_shockwave_ring(Vector3(mid.x, mid.y - 0.85, mid.z), Color(1.0, 0.42, 0.22), 0.4, 2.8, 0.42)
	_spawn_one_shot_sparks(mid, Vector3.UP, Color(1.0, 0.78, 0.30), 36, 3.0, 6.8, 0.48, 0.22)
	_spawn_flash_light(mid + Vector3(0.0, 0.6, 0.0), Color(1.0, 0.68, 0.25), 4.0, 6.5, 0.30)


func spawn_melee_clash(
	w_actor: Vector3,
	w_target: Vector3,
	action_kind: String,
	weapon: Dictionary,
	is_imperial_actor: bool,
	killed: bool = false
) -> void:
	_ensure_materials()
	var is_parry := action_kind.begins_with("parry")
	var is_crit := action_kind.ends_with("crit")
	var mid := w_actor.lerp(w_target, 0.52)

	if is_parry:
		# Blinding Crossed-Steel Parry Sparks + Deflection Ring!
		var pcol := Color(1.0, 0.90, 0.42) if is_crit else Color(0.55, 0.86, 1.0)
		_spawn_slash_crescent(mid, (w_target - w_actor).normalized(), pcol, 28.0)
		_spawn_slash_crescent(mid, (w_actor - w_target).normalized(), Color(1.0, 1.0, 1.0), -36.0)
		_spawn_one_shot_sparks(mid, Vector3.UP, pcol, 68 if is_crit else 46, 4.5, 10.2, 0.52, 0.24)
		_spawn_shockwave_ring(Vector3(mid.x, mid.y - 0.82, mid.z), pcol, 0.30, 2.4, 0.34)
		_spawn_flash_light(mid + Vector3(0.0, 0.5, 0.0), pcol, 5.5, 7.0, 0.30)
		return

	# Brutal Melee Strike: Crescent Weapon Slash + Blood/Ceramite Spark Explosion!
	var wname := str(weapon.get("name", ""))
	var scol := Color(0.32, 0.86, 1.0) if (wname.contains("动力") or wname.contains("法杖")) else (
		Color(0.62, 0.98, 0.24) if not is_imperial_actor else Color(1.0, 0.34, 0.18)
	)
	if is_crit:
		scol = scol.lerp(Color(1.0, 0.88, 0.30), 0.42)

	var dir := (w_target - w_actor).normalized()
	if dir.length_squared() < 0.001:
		dir = Vector3.FORWARD

	_spawn_slash_crescent(w_target, dir, scol, 22.0 if not is_crit else -18.0)
	if is_crit:
		_spawn_slash_crescent(w_target + Vector3(0.0, 0.15, 0.0), dir, Color(1.0, 0.92, 0.48), 52.0)

	_spawn_one_shot_sparks(w_target, dir + Vector3(0.0, 0.65, 0.0), scol, 82 if is_crit else 54, 4.5, 10.8, 0.64, 0.28)
	_spawn_one_shot_sparks(w_target, Vector3.UP, Color(0.94, 0.14, 0.18), 32, 2.8, 6.8, 0.56, 0.25)
	_spawn_smoke_plume(w_target, Color(0.20, 0.16, 0.18, 0.72), 18)
	_spawn_shockwave_ring(Vector3(w_target.x, w_target.y - 0.85, w_target.z), scol, 0.35, 3.0 if is_crit else 2.2, 0.38)
	_spawn_flash_light(w_target + Vector3(0.0, 0.5, 0.0), scol, 6.2 if is_crit else 4.4, 7.5, 0.36)

	if not is_imperial_actor:
		_spawn_toxic_miasma_burst(w_target)

	if killed:
		_spawn_shockwave_ring(Vector3(w_target.x, w_target.y - 0.80, w_target.z), Color(1.0, 0.24, 0.18), 0.45, 4.8, 0.55)
		_spawn_one_shot_sparks(w_target, Vector3.UP, Color(1.0, 0.85, 0.30), 90, 5.5, 12.8, 0.85, 0.35)


# ==============================================================================
# INTERNAL PARTICLE & MESH PRIMITIVES
# ==============================================================================

func _queue_projectile(
	p_from: Vector3,
	p_to: Vector3,
	col: Color,
	wtype: String,
	delay_sec: float,
	dur_sec: float,
	arc_h: float,
	impact_burst: bool
) -> void:
	var holder := Node3D.new()
	holder.position = p_from
	holder.visible = (delay_sec <= 0.0)
	add_child(holder)

	# Glowing Projectile Core Sphere
	var core_mat := Models3D.make_holo(col.lightened(0.35), 0.98, 3.4)
	var core_r: float = 0.20 if wtype in ["plasma", "blast", "psychic"] else 0.12
	Models3D.add_sphere(holder, Vector3.ZERO, core_r, core_mat)

	# Attached Live Trail Particle Emitter!
	var trail := CPUParticles3D.new()
	trail.amount = 24
	trail.lifetime = 0.22
	trail.local_coords = false
	var qm := QuadMesh.new()
	qm.size = Vector2(core_r * 2.4, core_r * 2.4)
	qm.material = _mat_add_soft
	trail.mesh = qm
	trail.gravity = Vector3.ZERO
	trail.initial_velocity_min = 0.1
	trail.initial_velocity_max = 0.5
	var c_curve := Curve.new()
	c_curve.add_point(Vector2(0.0, 1.0))
	c_curve.add_point(Vector2(1.0, 0.05))
	trail.scale_amount_curve = c_curve
	var grad := Gradient.new()
	grad.set_color(0, Color(1.0, 0.98, 0.86, 0.95))
	grad.add_point(0.35, Color(col.r, col.g, col.b, 0.85))
	grad.set_color(grad.get_point_count() - 1, Color(col.r * 0.5, col.g * 0.5, col.b * 0.5, 0.0))
	trail.color_ramp = grad
	holder.add_child(trail)

	# Dynamic traveling point light on bolt!
	var p_light := OmniLight3D.new()
	p_light.light_color = col
	p_light.light_energy = 2.4
	p_light.omni_range = 4.5
	holder.add_child(p_light)

	var dir := (p_to - p_from).normalized()
	if delay_sec <= 0.0:
		_spawn_muzzle_burst(p_from, dir, col, wtype)

	_projectiles.append({
		"node": holder,
		"from": p_from,
		"to": p_to,
		"dir": dir,
		"color": col,
		"wtype": wtype,
		"delay": delay_sec,
		"t": 0.0,
		"dur": dur_sec,
		"arc": arc_h,
		"impact_burst": impact_burst,
	})


func _spawn_muzzle_burst(pos: Vector3, dir: Vector3, col: Color, wtype: String) -> void:
	_spawn_one_shot_sparks(pos, dir, col, 28, 3.5, 8.2, 0.26, 0.24)
	_spawn_flash_light(pos, col, 3.8, 5.2, 0.18)

	# Spinning Brass Shell Casings ejecting sideways with gravity for Bolters/Snipers!
	if wtype in ["bolter", "plague_bolter", "sniper"]:
		var side_dir := Vector3(-dir.z, 0.85, dir.x).normalized()
		_spawn_one_shot_sparks(pos, side_dir, Color(0.95, 0.78, 0.34), 8, 2.2, 4.2, 0.55, 0.13, Vector3(0.0, -14.0, 0.0))


func _spawn_quick_hit_sparks(pos: Vector3, col: Color, is_big_blast: bool) -> void:
	_spawn_one_shot_sparks(pos, Vector3.UP, col, 48 if is_big_blast else 22, 3.2, 7.8, 0.42, 0.22)
	_spawn_flash_light(pos, col, 4.2 if is_big_blast else 2.4, 5.5, 0.22)
	if is_big_blast:
		_spawn_smoke_plume(pos, Color(0.22, 0.24, 0.20, 0.80), 26)
		_spawn_shockwave_ring(Vector3(pos.x, pos.y - 0.80, pos.z), col, 0.35, 3.2, 0.38)


func _spawn_one_shot_sparks(
	pos: Vector3,
	dir: Vector3,
	col: Color,
	count: int,
	vmin: float,
	vmax: float,
	life: float,
	quad_sz: float,
	grav: Vector3 = Vector3(0.0, -11.5, 0.0)
) -> void:
	var cp := CPUParticles3D.new()
	cp.position = pos
	cp.emitting = false
	cp.one_shot = true
	cp.explosiveness = 0.92
	cp.amount = count
	cp.lifetime = life
	cp.local_coords = false

	var qm := QuadMesh.new()
	qm.size = Vector2(quad_sz, quad_sz)
	qm.material = _mat_add_soft
	cp.mesh = qm

	cp.emission_shape = CPUParticles3D.EMISSION_SHAPE_SPHERE
	cp.emission_sphere_radius = 0.14
	cp.direction = dir.normalized() if dir.length_squared() > 0.001 else Vector3.UP
	cp.spread = 68.0
	cp.gravity = grav
	cp.initial_velocity_min = vmin
	cp.initial_velocity_max = vmax
	cp.damping_min = 2.0
	cp.damping_max = 4.5
	cp.scale_amount_min = 0.55
	cp.scale_amount_max = 1.45

	var curve := Curve.new()
	curve.add_point(Vector2(0.0, 1.0))
	curve.add_point(Vector2(0.55, 0.72))
	curve.add_point(Vector2(1.0, 0.0))
	cp.scale_amount_curve = curve

	var grad := Gradient.new()
	grad.set_color(0, Color(1.0, 0.98, 0.88, 1.0))
	grad.add_point(0.28, Color(col.r, col.g, col.b, 0.95))
	grad.set_color(grad.get_point_count() - 1, Color(col.r * 0.45, col.g * 0.35, col.b * 0.35, 0.0))
	cp.color_ramp = grad

	add_child(cp)
	cp.emitting = true
	_auto_free_later(cp, life + 0.35)


func _spawn_smoke_plume(pos: Vector3, smoke_col: Color, count: int) -> void:
	var cp := CPUParticles3D.new()
	cp.position = pos
	cp.emitting = false
	cp.one_shot = true
	cp.explosiveness = 0.82
	cp.amount = count
	cp.lifetime = 0.95
	cp.local_coords = false

	var qm := QuadMesh.new()
	qm.size = Vector2(0.65, 0.65)
	qm.material = _mat_alpha_smoke
	cp.mesh = qm

	cp.emission_shape = CPUParticles3D.EMISSION_SHAPE_SPHERE
	cp.emission_sphere_radius = 0.28
	cp.direction = Vector3.UP
	cp.spread = 48.0
	cp.gravity = Vector3(0.0, 2.2, 0.0)
	cp.initial_velocity_min = 0.8
	cp.initial_velocity_max = 2.4
	cp.damping_min = 1.5
	cp.damping_max = 3.0

	var curve := Curve.new()
	curve.add_point(Vector2(0.0, 0.45))
	curve.add_point(Vector2(0.5, 1.15))
	curve.add_point(Vector2(1.0, 1.65))
	cp.scale_amount_curve = curve

	var grad := Gradient.new()
	grad.set_color(0, Color(smoke_col.r, smoke_col.g, smoke_col.b, smoke_col.a))
	grad.set_color(grad.get_point_count() - 1, Color(smoke_col.r, smoke_col.g, smoke_col.b, 0.0))
	cp.color_ramp = grad

	add_child(cp)
	cp.emitting = true
	_auto_free_later(cp, 1.35)


func _spawn_toxic_miasma_burst(pos: Vector3) -> void:
	_spawn_one_shot_sparks(pos, Vector3.UP, Color(0.56, 1.0, 0.18), 38, 1.2, 3.6, 0.95, 0.32, Vector3(0.0, 2.6, 0.0))
	_spawn_smoke_plume(pos, Color(0.32, 0.58, 0.14, 0.65), 18)


func _spawn_warp_cast_vortex(pos: Vector3, col: Color) -> void:
	_spawn_one_shot_sparks(pos, Vector3.UP, col, 44, 1.8, 4.8, 0.65, 0.28, Vector3(0.0, 3.2, 0.0))
	_spawn_shockwave_ring(pos, col, 0.2, 1.8, 0.38)


func _spawn_shockwave_ring(pos: Vector3, col: Color, s0: float, s1: float, dur: float) -> void:
	var mat := Models3D.make_holo(col, 0.85, 2.8)
	var holder := Node3D.new()
	holder.position = pos + Vector3(0.0, 0.06, 0.0)
	add_child(holder)
	Models3D.add_torus(holder, Vector3.ZERO, 0.86, 1.0, mat)
	_transient_fx.append({
		"kind": "shockwave",
		"node": holder,
		"mat": mat,
		"col": col,
		"s0": s0,
		"s1": s1,
		"t": 0.0,
		"dur": dur,
	})


func _spawn_slash_crescent(pos: Vector3, dir: Vector3, col: Color, tilt_z_deg: float) -> void:
	var mat := Models3D.make_holo(col.lightened(0.22), 0.95, 3.6)
	var holder := Node3D.new()
	holder.position = pos + Vector3(0.0, 0.25, 0.0)
	var yaw := atan2(dir.x, dir.z)
	holder.rotation = Vector3(0.0, yaw, deg_to_rad(tilt_z_deg))
	add_child(holder)

	# Stylized 3D glowing crescent blade ribbon using a flattened Torus arc!
	var tm := TorusMesh.new()
	tm.inner_radius = 0.92
	tm.outer_radius = 1.24
	tm.rings = 28
	tm.ring_segments = 8
	var mi := MeshInstance3D.new()
	mi.mesh = tm
	mi.material_override = mat
	mi.scale = Vector3(1.15, 1.0, 0.55)
	holder.add_child(mi)

	_transient_fx.append({
		"kind": "slash_arc",
		"node": holder,
		"mat": mat,
		"col": col,
		"t": 0.0,
		"dur": 0.30,
	})


func _spawn_shield_dome(pos: Vector3, col: Color) -> void:
	var mat := Models3D.make_holo(col, 0.55, 2.6)
	var holder := Node3D.new()
	holder.position = pos
	add_child(holder)
	Models3D.add_sphere(holder, Vector3.ZERO, 1.15, mat)
	_transient_fx.append({
		"kind": "shield_dome",
		"node": holder,
		"mat": mat,
		"col": col,
		"t": 0.0,
		"dur": 0.42,
	})


func _spawn_flash_light(pos: Vector3, col: Color, energy: float, lrange: float, dur: float) -> void:
	var omni := OmniLight3D.new()
	omni.position = pos
	omni.light_color = col
	omni.light_energy = energy
	omni.omni_range = lrange
	add_child(omni)
	_transient_fx.append({
		"kind": "flash_light",
		"node": omni,
		"energy": energy,
		"t": 0.0,
		"dur": dur,
	})


func _auto_free_later(node: Node, delay_sec: float) -> void:
	var wr: WeakRef = weakref(node)
	get_tree().create_timer(delay_sec).timeout.connect(func():
		var n: Node = wr.get_ref() as Node
		if n != null and not n.is_queued_for_deletion():
			n.queue_free()
	)
