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


static func edge_distance(pos_a: Vector2, r_a: float, pos_b: Vector2, r_b: float) -> float:
	return maxf(0.0, pos_a.distance_to(pos_b) - r_a - r_b)


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
	return k == "heavy_wall" or k == "heavy_low" or k == "light"


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
		if other["incapacitated"] or other["side"] == op["side"] or other["uid"] == op["uid"]:
			continue
		var z_other := elevation_at(other["pos"], terrains)
		if in_control_range_at(pos, r_self, z_self, other["pos"], float(other["base_radius"]), z_other, terrains):
			enemies.append(other)
	return enemies


static func is_valid_stand_pos(pos: Vector2, radius: float, ignore_uid: String, state: Dictionary) -> Dictionary:
	var w: float = float(state["map"]["width"])
	var h: float = float(state["map"]["height"])
	if pos.x - radius < 0.05 or pos.x + radius > w - 0.05 or pos.y - radius < 0.05 or pos.y + radius > h - 0.05:
		return {"ok": false, "reason": "超出杀戮区边界"}

	for t in state["map"]["terrains"]:
		if is_impassable_terrain(t) and circle_intersects_rect(pos, radius * 0.92, t["rect"]):
			return {"ok": false, "reason": "底盘与地形障碍【%s】重叠" % str(t.get("name", "墙体"))}

	for other in state["operatives"]:
		if other["incapacitated"] or other["uid"] == ignore_uid:
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

	for i in range(waypoints.size() - 1):
		var a: Vector2 = waypoints[i]
		var b: Vector2 = waypoints[i + 1]
		var seg_len := a.distance_to(b)
		if seg_len < 0.05:
			continue

		# Sample along segment for Heavy wall collision & enemy control range checks
		var steps := maxi(4, int(ceil(seg_len / 0.25)))
		var crossed_light := false
		for s in range(1, steps + 1):
			var t_ratio := float(s) / float(steps)
			var sample := a.lerp(b, t_ratio)
			for terr in terrains:
				var tk := str(terr.get("kind", ""))
				if tk in ["heavy_wall", "heavy_low"]:
					if circle_intersects_rect(sample, r * 0.85, terr["rect"]):
						return {
							"ok": false,
							"cost": total_cost + seg_len,
							"max": max_inches,
							"reason": "路径无法穿过重型地形【%s】（请点击地面打折绕行）" % str(terr.get("name", ""))
						}
				elif tk == "light":
					if circle_intersects_rect(sample, r * 0.6, terr["rect"]):
						crossed_light = true

			if action_kind in ["reposition", "dash"]:
				var near_enemies := is_in_enemy_control_range(op, sample, state)
				if not near_enemies.is_empty():
					return {
						"ok": false,
						"cost": total_cost + seg_len,
						"max": max_inches,
						"reason": "移动路径不能进入【%s】的 1\" 控制范围" % str(near_enemies[0]["name"])
					}

		var next_z := elevation_at(b, terrains)
		var climb_penalty: float = 0.0
		if next_z > prev_z + 0.5:
			# Climbing onto Vantage (+3.5" -> 2.0" vertical climb surcharge in KT3 simplified ramp/wall)
			climb_penalty = 2.0
		elif crossed_light:
			# Traversing a light barricade costs +1"
			climb_penalty = 1.0

		total_cost += seg_len + climb_penalty
		prev_z = next_z

	var end_enemies := is_in_enemy_control_range(op, end_pos, state)
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
	# Check if at least one sightline from attacker base perimeter/center reaches target base
	# without hitting tall solid heavy_wall terrain that exceeds both elevations.
	var dir := (pos_b - pos_a).normalized()
	if dir.length_squared() < 0.001:
		return true
	var perp := Vector2(-dir.y, dir.x)
	var a_pts := [pos_a, pos_a + perp * (r_a * 0.8), pos_a - perp * (r_a * 0.8)]
	var b_pts := [pos_b, pos_b + perp * (r_b * 0.8), pos_b - perp * (r_b * 0.8)]

	for pa in a_pts:
		for pb in b_pts:
			var blocked := false
			for t in terrains:
				if bool(t.get("blocks_los_ground", false)):
					var wall_h := float(t.get("elevation", 4.0))
					# Solid tall wall blocks visibility unless both are elevated above it
					if maxf(z_a, z_b) + 1.2 < wall_h and segment_intersects_rect(pa, pb, t["rect"]):
						blocked = true
						break
			if not blocked:
				return true
	return false


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
	var dist_edges := edge_distance(pos_a, r_a, pos_d, r_d)

	var out := {
		"valid": false,
		"distance": dist_edges,
		"visible": false,
		"cover": "none",          # "none" | "standard" | "improved"
		"cover_terrain_type": "", # "" | "light" | "heavy"
		"obscured": false,
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
		if other["incapacitated"] or other["side"] != attacker["side"] or other["uid"] == attacker["uid"]:
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
	var is_obscured := false

	# Within 2" close quarters: targets do NOT gain cover!
	var close_quarters := dist_edges <= CLOSE_QUARTERS_REVEAL_INCHES + 0.02

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

		# Cover check: Defender is within 1" of terrain crossed by cover lines, and attacker is further away
		if not close_quarters and dist_def_to_terr <= COVER_PROXIMITY_INCHES + 0.05 and dist_att_to_terr > 0.35:
			if tk in ["heavy_wall", "heavy_low", "vantage"]:
				has_heavy_cover = true
			elif tk == "light":
				has_light_cover = true

		# Obscured check: Heavy terrain crossed > 1" from BOTH attacker and defender
		if tk in ["heavy_wall", "heavy_low"]:
			var th := float(t.get("elevation", 2.2))
			if maxf(z_a, z_d) < th + 1.5 and dist_att_to_terr > OBSCURED_BUFFER_INCHES and dist_def_to_terr > OBSCURED_BUFFER_INCHES:
				is_obscured = true

	# Plague Marines Cloud of Flies marker check (飞蝇云)
	for marker in state.get("markers", []):
		if str(marker.get("kind", "")) == "cloud_of_flies" and str(marker.get("side", "")) == str(defender["side"]):
			if dist_edges > 3.0 and pos_d.distance_to(marker["pos"]) <= 1.0 + r_d:
				is_obscured = true

	# Sniper Optics (瞄准) ignores Obscured!
	if bool(attacker.get("ignore_obscured", false)):
		is_obscured = false

	# Determine base cover state
	if has_heavy_cover:
		out["cover"] = "standard"
		out["cover_terrain_type"] = "heavy"
	elif has_light_cover:
		out["cover"] = "standard"
		out["cover_terrain_type"] = "light"

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
			if has_vantage:
				# KT3 Vantage rule: Can target Concealed operative in Light cover, AND they get Improved Cover!
				out["cover"] = "improved"
				out["tags"].append("制高点破轻掩隐匿")
			elif seek_light:
				out["tags"].append("追踪轻型破隐")
			else:
				out["reason"] = "目标处于【隐匿】命令且依托轻型掩体（需制高点、追踪词条或突进至 2\" 内）"
				return out

	# 5. Saturate (集中) removes cover saves (unless defender has Camo Cloak 迷彩斗篷!)
	var has_saturate := bool(w_rules.get("saturate", false)) or bool(attacker.get("siege_saturate", false))
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
