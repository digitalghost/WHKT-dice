class_name KTGeometry
extends RefCounted

## Continuous tabletop geometry solver (Inches):
## - Circular miniature bases (25mm/32mm/40mm)
## - 2.5D Vantage elevation & climb/drop traversal cost
## - 1.0" Control Range engagement zones
## - Outer-tangent Cover Cone solver for Visibility, Cover, Obscured, and Vantage

const CONTROL_RANGE_INCHES: float = 1.0
const COVER_PROXIMITY_INCHES: float = 1.0
const OBSCURED_BUFFER_INCHES: float = 1.0
const CLOSE_QUARTERS_REVEAL_INCHES: float = 2.0
const SMOKE_RADIUS_INCHES: float = 1.4
# 3D visibility: a 28-32mm infantry model stands ~1.6" tall; eye line ~1.4" above its base.
const MODEL_HEIGHT_INCHES: float = 1.6
const EYE_HEIGHT_INCHES: float = 1.4


static func edge_distance(pos_a: Vector2, r_a: float, pos_b: Vector2, r_b: float) -> float:
	return maxf(0.0, pos_a.distance_to(pos_b) - r_a - r_b)


## Rulebook p.22: true 3D base-to-base distance between operatives at different elevations.
static func edge_distance_3d(pos_a: Vector2, r_a: float, z_a: float, pos_b: Vector2, r_b: float, z_b: float) -> float:
	var dh := edge_distance(pos_a, r_a, pos_b, r_b)
	var dz := absf(z_a - z_b)
	return sqrt(dh * dh + dz * dz)


static func elevation_at(pos: Vector2, terrains: Array) -> float:
	var best_z: float = 0.0
	for t in terrains:
		if str(t.get("kind", "")) == "vantage":
			var rect: Rect2 = t["rect"]
			if rect.has_point(pos):
				best_z = maxf(best_z, float(t.get("elevation", 3.5)))
	return best_z


static func is_impassable_terrain(t: Dictionary) -> bool:
	var k := str(t.get("kind", ""))
	return k == "heavy_wall" or k == "heavy_low" or k == "light" or k == "razor_wire"


static func circle_intersects_rect(center: Vector2, radius: float, rect: Rect2) -> bool:
	var closest_x := clampf(center.x, rect.position.x, rect.position.x + rect.size.x)
	var closest_y := clampf(center.y, rect.position.y, rect.position.y + rect.size.y)
	var dx := center.x - closest_x
	var dy := center.y - closest_y
	return (dx * dx + dy * dy) < (radius * radius - 0.0004)


static func segment_intersects_rect(p1: Vector2, p2: Vector2, rect: Rect2) -> bool:
	if rect.has_point(p1) or rect.has_point(p2):
		return true
	var tl := rect.position
	var tr := Vector2(rect.position.x + rect.size.x, rect.position.y)
	var br := rect.position + rect.size
	var bl := Vector2(rect.position.x, rect.position.y + rect.size.y)
	if Geometry2D.segment_intersects_segment(p1, p2, tl, tr) != null:
		return true
	if Geometry2D.segment_intersects_segment(p1, p2, tr, br) != null:
		return true
	if Geometry2D.segment_intersects_segment(p1, p2, br, bl) != null:
		return true
	if Geometry2D.segment_intersects_segment(p1, p2, bl, tl) != null:
		return true
	return false


static func point_to_rect_distance(pt: Vector2, rect: Rect2) -> float:
	var cx := clampf(pt.x, rect.position.x, rect.position.x + rect.size.x)
	var cy := clampf(pt.y, rect.position.y, rect.position.y + rect.size.y)
	return pt.distance_to(Vector2(cx, cy))


static func point_to_segment_distance(pt: Vector2, a: Vector2, b: Vector2) -> float:
	var ab := b - a
	var len_sq := ab.length_squared()
	if len_sq < 1e-6:
		return pt.distance_to(a)
	var t := clampf((pt - a).dot(ab) / len_sq, 0.0, 1.0)
	return pt.distance_to(a + ab * t)


static func in_control_range_at(
	pos_a: Vector2,
	r_a: float,
	z_a: float,
	pos_b: Vector2,
	r_b: float,
	z_b: float,
	terrains: Array
) -> bool:
	if absf(z_a - z_b) > 2.0:
		return false
	if edge_distance(pos_a, r_a, pos_b, r_b) > CONTROL_RANGE_INCHES + 0.02:
		return false
	return has_visibility(pos_a, r_a, z_a, pos_b, r_b, z_b, terrains)


static func is_in_enemy_control_range(op: Dictionary, pos: Vector2, state: Dictionary) -> Array[Dictionary]:
	var terrains: Array = state["map"]["terrains"]
	var z_self := elevation_at(pos, terrains)
	var r_self := float(op["base_radius"])
	var enemies: Array[Dictionary] = []
	for other in state["operatives"]:
		if other["incapacitated"] or not bool(other.get("deployed", true)) or other["side"] == op["side"] or other["uid"] == op["uid"]:
			continue
		var z_other := elevation_at(other["pos"], terrains)
		if in_control_range_at(pos, r_self, z_self, other["pos"], float(other["base_radius"]), z_other, terrains):
			enemies.append(other)
	return enemies


## Rulebook p.13/14 helper: is another living deployed friendly operative ALREADY inside `enemy`'s control range?
static func _has_other_friendly_in_enemy_cr(enemy: Dictionary, moving_uid: String, moving_side: String, state: Dictionary) -> bool:
	var terrains: Array = state["map"]["terrains"]
	var e_pos: Vector2 = enemy["pos"]
	var e_r := float(enemy["base_radius"])
	var e_z := elevation_at(e_pos, terrains)
	for ally in state["operatives"]:
		if ally["incapacitated"] or not bool(ally.get("deployed", true)):
			continue
		if str(ally["side"]) != moving_side or str(ally["uid"]) == moving_uid:
			continue
		var a_pos: Vector2 = ally["pos"]
		var a_z := elevation_at(a_pos, terrains)
		if in_control_range_at(a_pos, float(ally["base_radius"]), a_z, e_pos, e_r, e_z, terrains):
			return true
	return false


static func is_valid_stand_pos(pos: Vector2, radius: float, ignore_uid: String, state: Dictionary) -> Dictionary:
	var w: float = float(state["map"]["width"])
	var h: float = float(state["map"]["height"])
	if pos.x - radius < 0.05 or pos.x + radius > w - 0.05 or pos.y - radius < 0.05 or pos.y + radius > h - 0.05:
		return {"ok": false, "reason": "超出杀戮区边界"}

	for t in state["map"]["terrains"]:
		if is_impassable_terrain(t) and circle_intersects_rect(pos, radius * 0.92, t["rect"]):
			return {"ok": false, "reason": "底盘与地形障碍【%s】重叠" % str(t.get("name", "墙体"))}

	for other in state["operatives"]:
		if other["incapacitated"] or not bool(other.get("deployed", true)) or other["uid"] == ignore_uid:
			continue
		var min_dist := radius + float(other["base_radius"]) - 0.02
		if pos.distance_to(other["pos"]) < min_dist:
			return {"ok": false, "reason": "与其他特工底盘重叠"}

	return {"ok": true, "reason": ""}


static func validate_move_path(
	waypoints: Array[Vector2],
	op: Dictionary,
	action_kind: String, # "reposition" | "dash" | "charge" | "fall_back"
	max_inches: float,
	state: Dictionary,
	allow_charge_from_engagement: bool = false
) -> Dictionary:
	if waypoints.size() < 2:
		return {"ok": false, "cost": 0.0, "max": max_inches, "reason": "请选择目标落脚点"}

	var terrains: Array = state["map"]["terrains"]
	var r := float(op["base_radius"])
	var start_pos: Vector2 = waypoints[0]
	var end_pos: Vector2 = waypoints[waypoints.size() - 1]

	var start_enemies := is_in_enemy_control_range(op, start_pos, state)
	if action_kind in ["reposition", "dash"] and not start_enemies.is_empty():
		return {"ok": false, "cost": 0.0, "max": max_inches, "reason": "处于敌方 1\" 控制范围内，只能执行【后撤】或【近战】"}
	if action_kind == "charge" and not start_enemies.is_empty() and not allow_charge_from_engagement:
		return {"ok": false, "cost": 0.0, "max": max_inches, "reason": "已处于敌方控制范围内，不能发起冲锋（机动战团战术除外）"}
	if action_kind == "fall_back" and start_enemies.is_empty():
		return {"ok": false, "cost": 0.0, "max": max_inches, "reason": "未处于敌方控制范围内，无需执行后撤"}

	var stand_check := is_valid_stand_pos(end_pos, r, op["uid"], state)
	if not stand_check["ok"]:
		return {"ok": false, "cost": 0.0, "max": max_inches, "reason": stand_check["reason"]}

	var total_cost: float = 0.0
	var prev_z := elevation_at(start_pos, terrains)
	var eq_markers: Array = state.get("eq_markers", [])
	var used_ladder := false
	# Rulebook p.31 L1406-1408: across multiple drops in one action, ignore at most 2" vertical drop TOTAL.
	var drop_free_remaining: float = 2.0
	var op_uid := str(op["uid"])
	var op_side := str(op["side"])
	var start_enemy_uids: Dictionary = {}
	for se in start_enemies:
		start_enemy_uids[str(se["uid"])] = true
	# Rulebook p.14 L696-698: Charge cannot leave the CR of an enemy that had no other friendly operative in its CR.
	var entered_solo_enemies: Dictionary = {}

	for i in range(waypoints.size() - 1):
		var a: Vector2 = waypoints[i]
		var b: Vector2 = waypoints[i + 1]
		var seg_len := a.distance_to(b)
		if seg_len < 0.05:
			continue
		var horiz := ceilf(maxf(0.0, seg_len - 1e-4))

		# Sample along segment for Heavy wall collision, distinct climbable obstacles & enemy control range checks
		var steps := maxi(4, int(ceil(seg_len / 0.25)))
		var crossed_light_ids: Dictionary = {}
		var crossed_razor_ids: Dictionary = {}
		for s in range(1, steps + 1):
			var t_ratio := float(s) / float(steps)
			var sample := a.lerp(b, t_ratio)
			for ti in range(terrains.size()):
				var terr: Dictionary = terrains[ti]
				var tk := str(terr.get("kind", ""))
				if tk in ["heavy_wall", "heavy_low"]:
					if circle_intersects_rect(sample, r * 0.85, terr["rect"]):
						return {
							"ok": false,
							"cost": total_cost + horiz,
							"max": max_inches,
							"reason": "路径无法穿过重型地形【%s】（请点击地面打折绕行）" % str(terr.get("name", ""))
						}
				elif tk == "light":
					if circle_intersects_rect(sample, r * 0.6, terr["rect"]):
						crossed_light_ids[ti] = true
				elif tk == "razor_wire":
					if circle_intersects_rect(sample, r * 0.6, terr["rect"]):
						crossed_razor_ids[ti] = true

			if action_kind in ["reposition", "dash"]:
				var near_enemies := is_in_enemy_control_range(op, sample, state)
				for ne in near_enemies:
					# Rulebook p.13 L650-652: Reposition/Dash may pass through an enemy's CR ONLY if another
					# friendly operative is already within that enemy's CR (cannot end there).
					if not _has_other_friendly_in_enemy_cr(ne, op_uid, op_side, state):
						return {
							"ok": false,
							"cost": total_cost + horiz,
							"max": max_inches,
							"reason": "移动路径不能进入【%s】的 1\" 控制范围" % str(ne["name"])
						}
			elif action_kind == "charge":
				var near_charge := is_in_enemy_control_range(op, sample, state)
				var near_uid_set: Dictionary = {}
				for nc in near_charge:
					near_uid_set[str(nc["uid"])] = true
				for locked_uid in entered_solo_enemies.keys():
					if not near_uid_set.has(locked_uid):
						return {
							"ok": false,
							"cost": total_cost + horiz,
							"max": max_inches,
							"reason": "【冲锋】进入无友军接战的敌方【%s】控制范围后不能离开" % str(entered_solo_enemies[locked_uid])
						}
				for nc in near_charge:
					var nc_uid := str(nc["uid"])
					if start_enemy_uids.has(nc_uid) and allow_charge_from_engagement:
						continue
					if not entered_solo_enemies.has(nc_uid) and not _has_other_friendly_in_enemy_cr(nc, op_uid, op_side, state):
						entered_solo_enemies[nc_uid] = str(nc["name"])

		var next_z := elevation_at(b, terrains)
		var rise := next_z - prev_z
		var drop := prev_z - next_z
		var climb_penalty: float = 0.0
		# Rulebook p.14: Dash cannot climb (neither elevation rise nor climbing over Light/Razor Wire obstacles).
		if action_kind == "dash" and (rise > 0.5 or not crossed_light_ids.is_empty() or not crossed_razor_ids.is_empty()):
			return {
				"ok": false,
				"cost": total_cost + horiz,
				"max": max_inches,
				"reason": "【冲刺】期间不能攀爬"
			}
		if rise > 0.5:
			# Core rules p.31: minimum 2" vertical climb, rounded up to nearest inch above 2"; 1.0" with ladder (once per action)
			climb_penalty += maxf(2.0, ceilf(rise - 1e-4))
			if not used_ladder:
				for m in eq_markers:
					if str(m.get("kind", "")) == "ladder":
						var mp: Vector2 = m["pos"]
						if point_to_segment_distance(mp, a, b) <= r + CONTROL_RANGE_INCHES + 0.4:
							climb_penalty = 1.0
							used_ladder = true
							break
		elif drop > 0.5:
			# Core rules p.31: ignore 2" vertical drop distance TOTAL across the whole action, round remaining drop up
			var eff_drop: float = maxf(0.0, drop - drop_free_remaining)
			drop_free_remaining = maxf(0.0, drop_free_remaining - drop)
			if eff_drop > 0.01:
				climb_penalty += ceilf(eff_drop - 1e-4)

		# Charge each distinct light (+2") and razor wire (+3") obstacle climbed over independently of drops
		if not crossed_razor_ids.is_empty():
			climb_penalty += float(crossed_razor_ids.size()) * 3.0
		if not crossed_light_ids.is_empty():
			climb_penalty += float(crossed_light_ids.size()) * 2.0

		total_cost += horiz + climb_penalty
		prev_z = next_z

	var end_enemies := is_in_enemy_control_range(op, end_pos, state)
	if action_kind in ["reposition", "dash"] and not end_enemies.is_empty():
		return {
			"ok": false,
			"cost": total_cost,
			"max": max_inches,
			"reason": "移动终点不能停留在【%s】的 1\" 控制范围内" % str(end_enemies[0]["name"])
		}
	if action_kind == "charge" and end_enemies.is_empty():
		return {
			"ok": total_cost <= max_inches + 0.05 and false,
			"cost": total_cost,
			"max": max_inches,
			"reason": "【冲锋】终点必须进入至少一名敌方特工的 1\" 控制范围内"
		}
	if action_kind == "fall_back" and not end_enemies.is_empty():
		return {
			"ok": false,
			"cost": total_cost,
			"max": max_inches,
			"reason": "【后撤】终点必须脱离所有敌方特工的 1\" 控制范围"
		}

	if total_cost > max_inches + 0.04:
		return {
			"ok": false,
			"cost": total_cost,
			"max": max_inches,
			"reason": "超出最大移动距离（需 %.1f\" / 上限 %.1f\"）" % [total_cost, max_inches]
		}

	return {
		"ok": true,
		"cost": total_cost,
		"max": max_inches,
		"elevation_end": prev_z,
		"engaged_enemies": end_enemies,
		"reason": "合法路径（消耗 %.1f\" / %.1f\"）" % [total_cost, max_inches]
	}


static func has_visibility(
	pos_a: Vector2,
	r_a: float,
	z_a: float,
	pos_b: Vector2,
	r_b: float,
	z_b: float,
	terrains: Array
) -> bool:
	## Core rules p.29 "可见": draw a 1mm line from the operative's head to any part of the target;
	## the target is visible if any such line is unbroken. In 3D: a solid heavy piece blocks the line
	## only where the line passes *below* the piece's top. We sample three sightlines across the
	## bases and, for each, test every heavy piece whose footprint the 2D line crosses, comparing the
	## line's height at the crossing against the piece height.
	##
	## Heights: eye level is EYE_HEIGHT_INCHES above the base elevation; the target counts as the
	## full model (0 .. MODEL_HEIGHT_INCHES above its base), so a shooter can see the top of a model
	## poking over a low wall exactly as the rulebook example shows (p.29, model half behind ruins).
	## Light terrain never blocks visibility (it only gives cover). Vantage decks the shooter or
	## target is standing ON are ignored (校注 p.25: 脚下的制高点楼板 is not an obstruction to them).
	var dir := (pos_b - pos_a).normalized()
	if dir.length_squared() < 0.001:
		return true
	var perp := Vector2(-dir.y, dir.x)
	var a_pts := [pos_a, pos_a + perp * (r_a * 0.8), pos_a - perp * (r_a * 0.8)]
	var b_pts := [pos_b, pos_b + perp * (r_b * 0.8), pos_b - perp * (r_b * 0.8)]
	var eye_a: float = z_a + EYE_HEIGHT_INCHES
	var top_b: float = z_b + MODEL_HEIGHT_INCHES

	for pa in a_pts:
		for pb in b_pts:
			var blocked := false
			for t in terrains:
				var tk := str(t.get("kind", ""))
				if tk == "light" or tk == "razor_wire":
					continue
				var rect: Rect2 = t["rect"]
				if not segment_intersects_rect(pa, pb, rect):
					continue
				var wall_h := float(t.get("elevation", 4.0))
				if tk == "vantage":
					# Standing on it: not an obstruction. Otherwise its deck is a solid slab at `elevation`
					# (walls under the deck are modelled as separate heavy pieces in the map data).
					if rect.has_point(pos_a) or rect.has_point(pos_b):
						continue
				# Where along the line (t in 0..1) it enters / leaves the piece footprint
				var hit_t := _segment_rect_entry_t(pa, pb, rect)
				if hit_t < 0.0:
					continue
				var t_exit := _segment_rect_exit_t(pa, pb, rect)
				# Sightline from eye to the HIGHEST point of the target model. Seeing any part is enough
				# for visibility, so only this top line matters: if it stays below the wall top across the
				# whole crossing, the piece blocks this sightline.
				var h_entry_top: float = lerpf(eye_a, top_b, hit_t)
				var h_exit_top: float = lerpf(eye_a, top_b, t_exit)
				if maxf(h_entry_top, h_exit_top) < wall_h - 0.05:
					blocked = true
					break
			if not blocked:
				return true
	return false


## Parametric t (0..1) along pa->pb where the segment first enters `rect`, or -1 if it never does.
static func _segment_rect_entry_t(pa: Vector2, pb: Vector2, rect: Rect2) -> float:
	var d := pb - pa
	var t0 := 0.0
	var t1 := 1.0
	for axis in range(2):
		var p0 := pa[axis]
		var dd := d[axis]
		var lo := rect.position[axis]
		var hi := rect.end[axis]
		if absf(dd) < 1e-6:
			if p0 < lo or p0 > hi:
				return -1.0
			continue
		var ta := (lo - p0) / dd
		var tb := (hi - p0) / dd
		if ta > tb:
			var tmp := ta
			ta = tb
			tb = tmp
		t0 = maxf(t0, ta)
		t1 = minf(t1, tb)
		if t0 > t1:
			return -1.0
	return t0


static func _segment_rect_exit_t(pa: Vector2, pb: Vector2, rect: Rect2) -> float:
	var d := pb - pa
	var t0 := 0.0
	var t1 := 1.0
	for axis in range(2):
		var p0 := pa[axis]
		var dd := d[axis]
		var lo := rect.position[axis]
		var hi := rect.end[axis]
		if absf(dd) < 1e-6:
			if p0 < lo or p0 > hi:
				return -1.0
			continue
		var ta := (lo - p0) / dd
		var tb := (hi - p0) / dd
		if ta > tb:
			var tmp := ta
			ta = tb
			tb = tmp
		t0 = maxf(t0, ta)
		t1 = minf(t1, tb)
		if t0 > t1:
			return -1.0
	return t1


static func get_cover_cone(attacker_pos: Vector2, defender_pos: Vector2, defender_radius: float) -> Dictionary:
	## Computes the two outer tangent points from attacker_pos to defender's circular base.
	var delta := defender_pos - attacker_pos
	var dist := delta.length()
	if dist <= defender_radius + 0.01:
		var n := Vector2(1, 0)
		return {
			"apex": attacker_pos,
			"left": defender_pos + n * defender_radius,
			"right": defender_pos - n * defender_radius,
		}
	var base_angle := delta.angle()
	var spread := asin(clampf(defender_radius / dist, -0.99, 0.99))
	var tangent_len := sqrt(maxf(0.01, dist * dist - defender_radius * defender_radius))
	var left_pt := attacker_pos + Vector2.from_angle(base_angle - spread) * tangent_len
	var right_pt := attacker_pos + Vector2.from_angle(base_angle + spread) * tangent_len
	return {
		"apex": attacker_pos,
		"left": left_pt,
		"right": right_pt,
	}


static func in_smoke(pos: Vector2, radius: float, state: Dictionary) -> bool:
	var marks: Array = state.get("eq_markers", [])
	var terrains: Array = (state.get("map", {}) as Dictionary).get("terrains", [])
	var z_op: float = elevation_at(pos, terrains)
	for m in marks:
		var md: Dictionary = m
		if str(md.get("kind", "")) == "smoke":
			var mp: Vector2 = md["pos"]
			# Rulebook p.103: horizontal 1" from marker edge, infinite height upward, does NOT affect below marker.
			var z_m: float = elevation_at(mp, terrains)
			if z_op + 0.1 < z_m:
				continue
			if pos.distance_to(mp) + radius <= SMOKE_RADIUS_INCHES + 0.001:
				return true
	return false


static func evaluate_shot(
	attacker: Dictionary,
	defender: Dictionary,
	weapon: Dictionary,
	state: Dictionary,
	attacker_pos_override = null
) -> Dictionary:
	var terrains: Array = state["map"]["terrains"]
	var pos_a: Vector2 = attacker_pos_override if attacker_pos_override != null else attacker["pos"]
	var r_a := float(attacker["base_radius"])
	var z_a := elevation_at(pos_a, terrains)

	var pos_d: Vector2 = defender["pos"]
	var r_d := float(defender["base_radius"])
	var z_d := elevation_at(pos_d, terrains)

	var w_rules: Dictionary = weapon.get("rules", {})
	var cone := get_cover_cone(pos_a, pos_d, r_d)
	# Rulebook p.22: measure 3D base-to-base distance across elevations (prevents 3.5" Vantage directly overhead triggering 2" Close Quarters!)
	var dist_edges := edge_distance_3d(pos_a, r_a, z_a, pos_d, r_d, z_d)
	var def_in_smoke: bool = in_smoke(pos_d, r_d, state)
	var att_in_smoke: bool = in_smoke(pos_a, r_a, state)

	var out := {
		"valid": false,
		"distance": dist_edges,
		"visible": false,
		"cover": "none",          # "none" | "standard" | "improved"
		"cover_terrain_type": "", # "" | "light" | "heavy"
		"protective": false,
		"obscured": false,
		"smoke": def_in_smoke,
		"vantage_accurate": 0,
		"cone": cone,
		"tags": [],
		"reason": "",
	}

	# 0. Engaged / friendly engagement check
	if attacker_pos_override == null:
		var self_engaged := is_in_enemy_control_range(attacker, pos_a, state)
		if not self_engaged.is_empty():
			out["reason"] = "攻击者处于敌方 1\" 控制范围内，无法远程射击"
			return out

	var def_engaged_with_friendlies := false
	for other in state["operatives"]:
		if other["incapacitated"] or not bool(other.get("deployed", true)) or other["side"] != attacker["side"] or other["uid"] == attacker["uid"]:
			continue
		var z_o := elevation_at(other["pos"], terrains)
		if in_control_range_at(other["pos"], float(other["base_radius"]), z_o, pos_d, r_d, z_d, terrains):
			def_engaged_with_friendlies = true
			break
	if def_engaged_with_friendlies:
		out["reason"] = "目标正与己方其他特工近战纠缠中，无法射击"
		return out

	# 1. Weapon Range check
	var max_range := float(w_rules.get("range", 0.0))
	if max_range > 0.0 and dist_edges > max_range + 0.04:
		out["reason"] = "超出武器射程（距离 %.1f\" > 范围 %.0f\"）" % [dist_edges, max_range]
		return out

	# 2. Visibility check
	var vis := has_visibility(pos_a, r_a, z_a, pos_d, r_d, z_d, terrains)
	out["visible"] = vis
	if not vis:
		out["reason"] = "视线被高耸重型墙体完全阻挡"
		return out

	# 3. Cover Lines & Obscured geometry check (3 rays across Cover Cone: left, center, right)
	var rays := [
		[cone["apex"], cone["left"]],
		[cone["apex"], pos_d],
		[cone["apex"], cone["right"]],
	]

	var has_light_cover := false
	var has_heavy_cover := false
	var has_protective_cover := false
	var is_obscured := false

	# Within 2" close quarters: targets do NOT gain cover!
	var close_quarters := dist_edges <= CLOSE_QUARTERS_REVEAL_INCHES + 0.02

	# Rulebook p.36 L1530 & V1.4 FAQ L1539: Heavy terrain connected to (part of the same terrain model as)
	# Vantage terrain that the active operative or potential target is standing on is ignored for Obscured.
	var vantage_parents: Array[String] = []
	var vantage_rects_unparented: Array[Rect2] = []
	for vt in terrains:
		var vtk: String = str(vt.get("kind", ""))
		if vtk == "vantage":
			var vrect: Rect2 = vt["rect"]
			if vrect.has_point(pos_a) or vrect.has_point(pos_d):
				var vpar: String = str(vt.get("parent", ""))
				if not vpar.is_empty():
					if not vantage_parents.has(vpar):
						vantage_parents.append(vpar)
				else:
					vantage_rects_unparented.append(vrect)

	for t in terrains:
		var tk := str(t.get("kind", ""))
		var rect: Rect2 = t["rect"]
		# Ignore the floor deck the attacker itself is standing on unless shooting up from below
		if tk == "vantage" and rect.has_point(pos_a):
			continue

		var crosses_cone := false
		for ray in rays:
			if segment_intersects_rect(ray[0], ray[1], rect):
				crosses_cone = true
				break
		if not crosses_cone:
			continue

		var dist_def_to_terr := point_to_rect_distance(pos_d, rect) - r_d
		var dist_att_to_terr := point_to_rect_distance(pos_a, rect) - r_a

		# Cover check: Defender is within 1" of terrain crossed by cover lines (<= 0.2" contact for portable barricade); only exception is attacker within 2" (p.19)
		var is_portable := bool(t.get("portable", false))
		var is_exposed := bool(t.get("exposed", false))
		var cover_prox := 0.2 if is_portable else (COVER_PROXIMITY_INCHES + 0.05)
		var gave_cover_this_terr := false
		if not close_quarters and not is_exposed and dist_def_to_terr <= cover_prox:
			if tk in ["heavy_wall", "heavy_low"]:
				has_heavy_cover = true
				gave_cover_this_terr = true
			elif tk in ["light", "vantage"]:
				has_light_cover = true
				gave_cover_this_terr = true
				if is_portable:
					has_protective_cover = true

		# Obscured check (Rulebook p.15 L751, p.26 & p.36 L1530/L1539): Heavy terrain crossed > 1" from BOTH attacker and defender,
		# cannot grant both Cover and Obscured from the same terrain piece, ignoring Heavy parts connected to a Vantage feature either operative stands on.
		if tk in ["heavy_wall", "heavy_low"] and not is_exposed and not gave_cover_this_terr:
			var t_par: String = str(t.get("parent", ""))
			var connected_to_vantage: bool = (not t_par.is_empty()) and vantage_parents.has(t_par)
			if not connected_to_vantage and t_par.is_empty():
				for uvr in vantage_rects_unparented:
					if uvr.grow(0.15).intersects(rect):
						connected_to_vantage = true
						break
			if not connected_to_vantage and dist_att_to_terr > OBSCURED_BUFFER_INCHES and dist_def_to_terr > OBSCURED_BUFFER_INCHES:
				var th := float(t.get("elevation", 2.2))
				var hit_t_obs := _segment_rect_entry_t(pos_a, pos_d, rect)
				var line_z: float = lerpf(z_a, z_d, clampf(hit_t_obs, 0.0, 1.0)) if hit_t_obs >= 0.0 else minf(z_a, z_d)
				if line_z < th - 0.05:
					is_obscured = true

	# Plague Marines Cloud of Flies marker check (飞蝇云, CHAOS-PM: wholly within 1" of 20mm marker -> dist + r_d <= 1.0 + 0.4, 3D elevation)
	for marker in state.get("markers", []):
		if str(marker.get("kind", "")) == "cloud_of_flies" and str(marker.get("side", "")) == str(defender["side"]):
			var z_m := elevation_at(marker["pos"], terrains)
			if dist_edges > 3.0 and absf(z_d - z_m) <= 1.001 and pos_d.distance_to(marker["pos"]) + r_d <= 1.401:
				is_obscured = true

	# Smoke grenade area check (烟雾手雷)
	if (def_in_smoke or att_in_smoke) and dist_edges > 2.0:
		is_obscured = true
		(out["tags"] as Array).append("烟雾")

	# Sniper Optics (瞄准) ignores Obscured!
	if bool(attacker.get("ignore_obscured", false)):
		is_obscured = false

	# 占卜仪 (AoD equipment, once per TP): enemies within 8" cannot be obscured for this activation
	if is_obscured and dist_edges <= 8.0:
		var a_side: Dictionary = (state.get("sides", {}) as Dictionary).get(attacker["side"], {})
		if bool(attacker.get("auspex_active", false)) or (KTCombat.has_equipment(a_side, "auspex") and not bool(a_side.get("auspex_used_tp", false))):
			is_obscured = false
			out["auspex"] = true
			out["tags"].append("占卜仪")

	# Determine base cover state
	if has_heavy_cover:
		out["cover"] = "standard"
		out["cover_terrain_type"] = "heavy"
	elif has_light_cover:
		out["cover"] = "standard"
		out["cover_terrain_type"] = "light"
	if has_protective_cover and str(out["cover"]) != "none":
		out["protective"] = true
		(out["tags"] as Array).append("保护: 豁免+1")

	# 4. Conceal Order Target Legality & Vantage Interaction
	var def_concealed := str(defender.get("order", "conceal")) == "conceal"
	var height_diff := z_a - z_d
	var has_vantage := height_diff >= 2.0
	var seek_all := bool(w_rules.get("seek", false))
	var seek_light := bool(w_rules.get("seek_light", false)) or seek_all

	if def_concealed and out["cover"] != "none":
		if out["cover_terrain_type"] == "heavy":
			if not seek_all:
				out["reason"] = "目标处于【隐匿】命令且依托重型掩体，无法选为目标"
				return out
		elif out["cover_terrain_type"] == "light":
			if seek_light:
				# Rulebook p.36 v1.4 FAQ (L1537): On Vantage with a Seek Light weapon, attacker may use
				# Seek Light instead of Vantage so the target does NOT gain Improved Cover!
				out["tags"].append("追踪轻型破隐")
			elif has_vantage:
				# KT3 Vantage rule: Can target Concealed operative in Light cover, AND they get Improved Cover!
				out["cover"] = "improved"
				out["tags"].append("制高点破轻掩隐匿")
			else:
				out["reason"] = "目标处于【隐匿】命令且依托轻型掩体（需制高点、追踪词条或突进至 2\" 内）"
				return out

	# Preserve pre-Saturate terrain cover so Blast secondary targets with Camo Cloak inherit it properly (1.2 FAQ)
	out["raw_cover"] = str(out["cover"])

	# 5. Saturate (集中) removes cover saves (unless defender has Camo Cloak 迷彩斗篷!)
	var att_side_sat: Dictionary = (state.get("sides", {}) as Dictionary).get(attacker["side"], {})
	var has_siege_sat: bool = KTCombat.has_chapter_tactic(att_side_sat, attacker, "siege_specialist") if not att_side_sat.is_empty() else bool(attacker.get("siege_saturate", false))
	var has_saturate := bool(w_rules.get("saturate", false)) or has_siege_sat
	var ignore_saturate := bool(defender.get("camo_cloak", false))
	if has_saturate and not ignore_saturate and out["cover"] != "none":
		out["cover"] = "none"
		out["tags"].append("集中·无视掩护")

	# 6. Vantage Accurate bonus against Engage targets!
	if has_vantage and not def_concealed:
		if height_diff >= 4.0:
			out["vantage_accurate"] = 2
			out["tags"].append("制高点 ≥4\": 精准2")
		else:
			out["vantage_accurate"] = 1
			out["tags"].append("制高点 ≥2\": 精准1")

	out["obscured"] = is_obscured
	if out["cover"] == "standard":
		out["tags"].append("普通掩护")
	elif out["cover"] == "improved":
		out["tags"].append("强化掩护")
	else:
		out["tags"].append("暴露无掩护")

	if is_obscured:
		out["tags"].append("重型遮挡")

	out["valid"] = true
	out["reason"] = "有效目标 · 距离 %.1f\" · %s" % [dist_edges, " / ".join(out["tags"])]
	return out


## Rulebook p.92 (L2728-2745, 1.2 FAQ & 1.6 FAQ):
## Blast X: every OTHER operative (friendly or enemy, including shooter if in Blast X"!) within X" of
## the primary target AND visible to the primary target is a mandatory secondary target, regardless of
## Conceal order or engagement. Each secondary target directly inherits the primary target's Cover
## (none / standard / improved) and Obscured determination.
static func get_blast_secondary_targets(
	attacker: Dictionary,
	primary: Dictionary,
	weapon: Dictionary,
	primary_eval: Dictionary,
	state: Dictionary
) -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	var w_rules: Dictionary = weapon.get("rules", {})
	var blast_x: float = float(w_rules.get("blast", 0.0))
	if blast_x <= 0.01 or primary.is_empty():
		return out

	var terrains: Array = state["map"]["terrains"]
	var pos_p: Vector2 = primary["pos"]
	var r_p: float = float(primary["base_radius"])
	var z_p: float = elevation_at(pos_p, terrains)

	var pos_a: Vector2 = attacker["pos"]
	var r_a: float = float(attacker["base_radius"])
	var z_a: float = elevation_at(pos_a, terrains)

	var inh_cover: String = str(primary_eval.get("raw_cover", primary_eval.get("cover", "none")))
	var inh_cover_type: String = str(primary_eval.get("cover_terrain_type", ""))
	var inh_protective: bool = bool(primary_eval.get("protective", false))
	var inh_obscured: bool = bool(primary_eval.get("obscured", false))
	var att_side_bs: Dictionary = (state.get("sides", {}) as Dictionary).get(attacker["side"], {})
	var has_siege_bs: bool = KTCombat.has_chapter_tactic(att_side_bs, attacker, "siege_specialist") if not att_side_bs.is_empty() else bool(attacker.get("siege_saturate", false))
	var has_saturate: bool = bool(w_rules.get("saturate", false)) or has_siege_bs

	for other in state["operatives"]:
		var od: Dictionary = other
		if bool(od.get("incapacitated", false)) or not bool(od.get("deployed", true)):
			continue
		if str(od["uid"]) == str(primary["uid"]):
			continue
		var pos_s: Vector2 = od["pos"]
		if pos_s.x < 0.0 or pos_s.y < 0.0:
			continue
		var r_s: float = float(od["base_radius"])
		var z_s: float = elevation_at(pos_s, terrains)
		var d_from_primary: float = edge_distance_3d(pos_p, r_p, z_p, pos_s, r_s, z_s)
		if d_from_primary > blast_x + 0.02:
			continue
		if not has_visibility(pos_p, r_p, z_p, pos_s, r_s, z_s, terrains):
			continue

		var eff_cover: String = inh_cover
		var ignore_sat: bool = bool(od.get("camo_cloak", false))
		if has_saturate and not ignore_sat and eff_cover != "none":
			eff_cover = "none"

		# Vantage Accurate against Engage secondary target (p.36 L1523)
		var vant_acc: int = 0
		var h_diff: float = z_a - z_s
		if h_diff >= 2.0 and str(od.get("order", "conceal")) != "conceal":
			vant_acc = 2 if h_diff >= 4.0 else 1

		var sec_in_smoke: bool = in_smoke(pos_s, r_s, state)
		var tags: Array = ["爆炸 %.0f\" 波及" % blast_x]
		if str(od["side"]) == str(attacker["side"]):
			tags.append("友军误伤!")
		if eff_cover == "standard":
			tags.append("继承普通掩护")
		elif eff_cover == "improved":
			tags.append("继承强化掩护")
		if inh_obscured:
			tags.append("继承遮挡")

		var sec_eval: Dictionary = {
			"valid": true,
			"distance": edge_distance_3d(pos_a, r_a, z_a, pos_s, r_s, z_s),
			"visible": true,
			"cover": eff_cover,
			"cover_terrain_type": inh_cover_type,
			"protective": inh_protective and eff_cover != "none",
			"obscured": inh_obscured,
			"smoke": sec_in_smoke,
			"vantage_accurate": vant_acc,
			"cone": get_cover_cone(pos_a, pos_s, r_s),
			"tags": tags,
			"reason": "爆炸次要目标 · 距主目标 %.1f\"" % d_from_primary,
		}
		out.append({
			"defender_uid": str(od["uid"]),
			"friendly": str(od["side"]) == str(attacker["side"]),
			"shot_eval": sec_eval,
		})
	return out


## Rulebook p.94 (L2840-2848, v1.4 update):
## Torrent X: after selecting a valid primary target, select any number of OTHER valid enemy targets
## within X" of the primary target that are not within friendly operatives' control range.
## Each target's Visibility, Cover, and Obscured are evaluated independently from the shooter!
static func get_torrent_secondary_targets(
	attacker: Dictionary,
	primary: Dictionary,
	weapon: Dictionary,
	state: Dictionary
) -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	var w_rules: Dictionary = weapon.get("rules", {})
	var torrent_x: float = float(w_rules.get("torrent", 0.0))
	if torrent_x <= 0.01 or primary.is_empty():
		return out

	var terrains_t: Array = state["map"]["terrains"]
	var pos_p: Vector2 = primary["pos"]
	var r_p: float = float(primary["base_radius"])
	var z_p: float = elevation_at(pos_p, terrains_t)

	for other in state["operatives"]:
		var od: Dictionary = other
		if bool(od.get("incapacitated", false)) or not bool(od.get("deployed", true)):
			continue
		if str(od["side"]) == str(attacker["side"]) or str(od["uid"]) == str(primary["uid"]):
			continue
		var pos_s: Vector2 = od["pos"]
		if pos_s.x < 0.0 or pos_s.y < 0.0:
			continue
		var r_s: float = float(od["base_radius"])
		var z_s: float = elevation_at(pos_s, terrains_t)
		var d_from_primary: float = edge_distance_3d(pos_p, r_p, z_p, pos_s, r_s, z_s)
		if d_from_primary > torrent_x + 0.02:
			continue
		var ev: Dictionary = evaluate_shot(attacker, od, weapon, state)
		if bool(ev.get("valid", false)):
			out.append({
				"defender_uid": str(od["uid"]),
				"friendly": false,
				"shot_eval": ev,
			})
	return out
