class_name KTAI
extends RefCounted

## Tactical Heuristic AI for Single-Player Mode:
## - Evaluates Strategic Ploys per Turning Point
## - Chooses optimal Operative Activation order & Engage/Conceal stance
## - Samples polar movement positions around cover & objectives
## - Executes Ranged Attacks, Unique Psychic/Flail abilities, Charges, and Melee Duels


static func decide_strategy_step(engine: KTEngine, side_id: String) -> Dictionary:
	var s: Dictionary = engine.state["sides"][side_id]
	var cp := int(s["cp"])
	var tp := int(engine.state["tp"])
	var options := engine.get_strategic_ploy_options(side_id)

	# Smart CP budgeting: Use 1~2 impactful ploys per TP while keeping >=1 CP for Command Reroll / Firefight!
	for opt in options:
		if bool(opt["used"]):
			continue
		var cost := int(opt["cost"])
		if cost == 0:
			return {"action": "ploy", "id": str(opt["id"])}
		if cp >= 2:
			var oid := str(opt["id"])
			if tp == 1 and oid in ["plodding_death", "cloud_of_flies", "doctrine_devastator", "indomitus"]:
				return {"action": "ploy", "id": oid}
			if tp >= 2 and oid in ["contagion", "plodding_death", "doctrine_tactical", "doctrine_assault"]:
				return {"action": "ploy", "id": oid}
	return {"action": "pass"}


static func pick_operative_to_activate(engine: KTEngine, side_id: String) -> Dictionary:
	var candidates: Array[Dictionary] = []
	for op in engine.state["operatives"]:
		if op["incapacitated"] or str(op["side"]) != side_id:
			continue
		var chk := engine.can_operative_be_selected_for_turn(op)
		if bool(chk["ok"]):
			candidates.append(op)

	if candidates.is_empty():
		return {}

	# Prioritize: 1) Engaged operatives who can strike first; 2) Operatives with clear shots; 3) Objective runners
	var best_op := candidates[0]
	var best_score := -999.0

	for op in candidates:
		var sc := 0.0
		var engaged := not KTGeometry.is_in_enemy_control_range(op, op["pos"], engine.state).is_empty()
		if engaged:
			sc += 50.0
		var min_obj_d := 99.0
		for obj in engine.state["map"]["objectives"]:
			min_obj_d = minf(min_obj_d, op["pos"].distance_to(obj["pos"]))
		sc += maxf(0.0, 15.0 - min_obj_d)
		# Decide Engage vs Conceal: TP1 cautious advance unless clear shot; TP2+ aggressive Engage!
		if sc > best_score:
			best_score = sc
			best_op = op

	var order := "engage"
	if int(engine.state["tp"]) == 1:
		# Check if immediate shot exists from current or forward move; keep half concealed advancing on cover
		var has_long_gun := false
		for w in best_op["weapons"]:
			if str(w["kind"]) == "ranged" and float(w["rules"].get("range", 0.0)) == 0.0:
				has_long_gun = true
		order = "engage" if has_long_gun else "conceal"

	return {"uid": str(best_op["uid"]), "order": order}


static func decide_next_operative_action(engine: KTEngine, op: Dictionary) -> Dictionary:
	## Returns one atomic step for the active AI operative so the UI can animate each step clearly!
	var acts := engine.get_available_actions(op)

	# 1. If engaged in melee -> Fight immediately!
	if bool(acts["fight"]["enabled"]):
		var best_enemy := _pick_best_melee_target(op, engine.state)
		if not best_enemy.is_empty():
			return {"kind": "fight", "target_uid": str(best_enemy["uid"])}

	# 2. If standing on a controlled unsecured objective -> Secure it for VP!
	if bool(acts["secure"]["enabled"]):
		return {"kind": "secure"}

	# 3. Unique Psychic / Special Abilities (Plaguecaster Vitality heal / Miasma / Sniper Optics)
	for ua in engine.get_unique_actions(op):
		if bool(ua["enabled"]):
			var uid_ab := str(ua["id"])
			if uid_ab == "pm_vitality" and int(op["wounds_cur"]) < int(op["wounds_max"]) - 2:
				return {"kind": "unique", "ability_id": uid_ab}
			if uid_ab == "pm_miasma":
				# Check if an enemy is within 7" visible
				var terrains: Array = engine.state["map"]["terrains"]
				var z_me := KTGeometry.elevation_at(op["pos"], terrains)
				for e in engine.state["operatives"]:
					if not e["incapacitated"] and e["side"] != op["side"]:
						var d := KTGeometry.edge_distance(op["pos"], float(op["base_radius"]), e["pos"], float(e["base_radius"]))
						var z_e := KTGeometry.elevation_at(e["pos"], terrains)
						if d <= 7.0 and KTGeometry.has_visibility(op["pos"], float(op["base_radius"]), z_me, e["pos"], float(e["base_radius"]), z_e, terrains):
							return {"kind": "unique", "ability_id": uid_ab}
			if uid_ab == "aod_optics":
				return {"kind": "unique", "ability_id": uid_ab}

	# 4. If a high-value Ranged Shot is available right now -> Shoot!
	if bool(acts["shoot"]["enabled"]):
		var shot_plan := _find_best_shot(engine, op)
		if not shot_plan.is_empty():
			return shot_plan

	# 5. Consider Charge if Melee specialist and an enemy is within Charge reach!
	if bool(acts["charge"]["enabled"]) and _is_melee_oriented(op):
		var charge_path := _find_best_charge_path(engine, op)
		if not charge_path.is_empty():
			return {"kind": "move", "move_type": "charge", "waypoints": charge_path}

	# 6. Reposition toward nearest contested/uncontrolled Objective & Cover!
	if bool(acts["reposition"]["enabled"]):
		var move_path := _find_best_advance_path(engine, op, "reposition")
		if not move_path.is_empty():
			return {"kind": "move", "move_type": "reposition", "waypoints": move_path}

	# 7. Dash for extra cover / objective proximity if still holding AP
	if bool(acts["dash"]["enabled"]):
		var dash_path := _find_best_advance_path(engine, op, "dash")
		if not dash_path.is_empty():
			return {"kind": "move", "move_type": "dash", "waypoints": dash_path}

	return {"kind": "end"}


static func _is_melee_oriented(op: Dictionary) -> bool:
	var oid := str(op["op_id"])
	return oid in ["pm_fighter", "pm_champion", "pm_icon_bearer", "aod_captain", "aod_assault_warrior", "aod_assault_sergeant"]


static func _pick_best_melee_target(op: Dictionary, state: Dictionary) -> Dictionary:
	var enemies := KTGeometry.is_in_enemy_control_range(op, op["pos"], state)
	if enemies.is_empty():
		return {}
	enemies.sort_custom(func(a, b): return int(a["wounds_cur"]) < int(b["wounds_cur"]))
	return enemies[0]


static func pick_best_melee_weapon(op: Dictionary) -> Dictionary:
	var best := {}
	var best_ev := -1.0
	for w in op["weapons"]:
		if str(w["kind"]) == "melee":
			var ev := float(w["attacks"]) * (float(w["normal_dmg"]) + float(w["crit_dmg"]) * 0.35)
			if ev > best_ev:
				best_ev = ev
				best = w
	if best.is_empty() and not op["weapons"].is_empty():
		return op["weapons"][0]
	return best


static func _find_best_shot(engine: KTEngine, op: Dictionary) -> Dictionary:
	var usable := engine.get_usable_ranged_weapons(op)
	var best := {}
	var best_score := -1.0

	for w in usable:
		# Avoid overheating plasma if low HP
		if bool(w["rules"].get("hot", false)) and int(op["wounds_cur"]) <= 7:
			continue
		for enemy in engine.state["operatives"]:
			if enemy["incapacitated"] or enemy["side"] == op["side"]:
				continue
			var ev := KTGeometry.evaluate_shot(op, enemy, w, engine.state)
			if not bool(ev["valid"]):
				continue
			var dmg_est := float(w["attacks"]) * float(7 - int(w["hit"])) / 6.0 * float(w["normal_dmg"])
			if bool(ev["obscured"]):
				dmg_est *= 0.55
			if str(ev["cover"]) == "none":
				dmg_est *= 1.25
			if int(enemy["wounds_cur"]) <= dmg_est:
				dmg_est += 8.0 # Prioritize finishing off wounded targets!
			if dmg_est > best_score:
				best_score = dmg_est
				best = {
					"kind": "shoot",
					"weapon": w,
					"target_uid": str(enemy["uid"]),
					"shot_eval": ev,
				}
	return best


static func _find_best_charge_path(engine: KTEngine, op: Dictionary) -> Array[Vector2]:
	var max_m := engine.get_effective_move_inches(op, "charge")
	var start: Vector2 = op["pos"]
	var side_state: Dictionary = engine.state["sides"][op["side"]]
	var mobile := KTCombat.has_chapter_tactic(side_state, op, "mobile")

	for enemy in engine.state["operatives"]:
		if enemy["incapacitated"] or enemy["side"] == op["side"]:
			continue
		var epos: Vector2 = enemy["pos"]
		if start.distance_to(epos) > max_m + 1.5:
			continue
		for deg in [0, 30, -30, 60, -60, 90, -90, 180]:
			var dir := (start - epos).normalized().rotated(deg_to_rad(float(deg)))
			var stand_dist := float(op["base_radius"]) + float(enemy["base_radius"]) + 0.45
			var cand := epos + dir * stand_dist
			var pts: Array[Vector2] = [start, cand]
			var chk := KTGeometry.validate_move_path(pts, op, "charge", max_m, engine.state, mobile)
			if bool(chk["ok"]):
				return pts
	return []


static func _find_best_advance_path(engine: KTEngine, op: Dictionary, move_kind: String) -> Array[Vector2]:
	var max_m := engine.get_effective_move_inches(op, move_kind)
	var start: Vector2 = op["pos"]

	# Pick highest-priority objective (nearest uncontrolled or contested)
	var target_goal := Vector2(15.0, 11.0)
	var best_obj_d := 999.0
	for obj in engine.state["map"]["objectives"]:
		var oid := str(obj["id"])
		var st: Dictionary = engine.state["objectives_state"][oid]
		var weight := 0.0 if str(st["secured_by"]) != str(op["side"]) else 4.0
		var d := start.distance_to(obj["pos"]) + weight
		if d < best_obj_d:
			best_obj_d = d
			target_goal = obj["pos"]

	if start.distance_to(target_goal) <= 1.1 and str(engine.state["objectives_state"].get("obj_2", {}).get("controlled_by", "")) == str(op["side"]):
		return []

	var best_pts: Array[Vector2] = []
	var best_val := -999.0
	var base_dir := (target_goal - start).normalized()

	for ratio in [1.0, 0.78, 0.55]:
		var ratio_f: float = float(ratio)
		for ang_deg in [0, -22, 22, -45, 45, -70, 70]:
			var step_vec: Vector2 = base_dir.rotated(deg_to_rad(float(ang_deg))) * (max_m * ratio_f)
			var cand: Vector2 = start + step_vec
			var pts: Array[Vector2] = [start, cand]
			var chk := KTGeometry.validate_move_path(pts, op, move_kind, max_m, engine.state)
			if not bool(chk["ok"]):
				continue

			# Score candidate: closer to objective + near terrain cover + shooting opportunity!
			var score: float = (start.distance_to(target_goal) - cand.distance_to(target_goal)) * 3.0
			for t in engine.state["map"]["terrains"]:
				var d_terr := KTGeometry.point_to_rect_distance(cand, t["rect"]) - float(op["base_radius"])
				if d_terr <= 0.95:
					score += 2.2 # Loves hugging cover!
			if KTGeometry.elevation_at(cand, engine.state["map"]["terrains"]) >= 2.0 and not _is_melee_oriented(op):
				score += 3.0 # Ranged operatives love Vantage!

			if score > best_val and score > 0.4:
				best_val = score
				best_pts = pts

	return best_pts
