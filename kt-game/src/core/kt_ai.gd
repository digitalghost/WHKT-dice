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

	# TP1: the Primary Op must be chosen as a strategic gambit (p.106). Tac ops are not modelled yet,
	# so elite teams back their kill op and the rest their key op.
	if engine.needs_primary_op(side_id):
		return {"action": "primary", "op": "kill" if str(s["team_id"]) == "IMP-AOD" else "key"}

	# 特使 tac op: name the operative that is deepest towards the enemy drop zone
	if engine.can_choose_envoy(side_id):
		var enemy_dz: Rect2 = engine.state["map"]["drop_zone_b"] if side_id == "A" else engine.state["map"]["drop_zone_a"]
		var best_uid := ""
		var best_d := 999.0
		for cand in engine.envoy_candidates(side_id):
			var d := (cand["pos"] as Vector2).distance_to(enemy_dz.get_center())
			if d < best_d:
				best_d = d
				best_uid = str(cand["uid"])
		if not best_uid.is_empty():
			return {"action": "envoy", "uid": best_uid}

	# Smart CP budgeting: Use 1~2 impactful ploys per TP while keeping >=1 CP for Command Reroll / Firefight!
	for opt in options:
		if bool(opt["used"]) or not bool(opt.get("can_use", true)):
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


## Initiative-card step (p.106): when losing the roll-off, spend the smallest modifier that flips it,
## otherwise re-roll a poor die; never spend cards while already winning.
static func decide_initiative_card(engine: KTEngine, side_id: String) -> Dictionary:
	if engine.init_leader() == side_id:
		return {"action": "pass"}
	var cards: Array = (engine.state["sides"][side_id]["init_cards"] as Array).duplicate()
	cards.sort()
	var opp := "B" if side_id == "A" else "A"
	var tie_wins := str(engine.state["initiative_side"]) != side_id
	for c in cards:
		if int(c) == KTEngine.INIT_CARD_REROLL:
			continue
		var v := engine.init_value(side_id) + int(c)
		if v > engine.init_value(opp) or (v == engine.init_value(opp) and tie_wins):
			return {"action": "card", "card": int(c), "sign": 1}
	if cards.has(KTEngine.INIT_CARD_REROLL) and int(engine.state["init"]["roll"][side_id]) <= 3:
		return {"action": "card", "card": KTEngine.INIT_CARD_REROLL, "sign": 1}
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

	# 3. Unique Psychic / Special / Equipment / Tac-Op Abilities
	for ua in engine.get_unique_actions(op):
		if bool(ua["enabled"]):
			var uid_ab := str(ua["id"])
			if uid_ab == "eq_resupply":
				return {"kind": "unique", "ability_id": uid_ab}
			if uid_ab == "pm_vitality":
				var vit_terr: Array = engine.state["map"]["terrains"]
				var z_me_v := KTGeometry.elevation_at(op["pos"], vit_terr)
				var has_hurt_ally := false
				for al_v in engine.state["operatives"]:
					var ald: Dictionary = al_v
					if not engine._on_board(ald) or str(ald["side"]) != str(op["side"]):
						continue
					if int(ald["wounds_cur"]) < int(ald["wounds_max"]) - 2:
						var z_al := KTGeometry.elevation_at(ald["pos"], vit_terr)
						var d_al := KTGeometry.edge_distance_3d(op["pos"], float(op["base_radius"]), z_me_v, ald["pos"], float(ald["base_radius"]), z_al)
						if d_al <= 3.05 and KTGeometry.has_visibility(op["pos"], float(op["base_radius"]), z_me_v, ald["pos"], float(ald["base_radius"]), z_al, vit_terr):
							has_hurt_ally = true
							break
				if has_hurt_ally:
					return {"kind": "unique", "ability_id": uid_ab}
			if uid_ab == "pm_miasma":
				if not engine._find_miasma_target(op).is_empty():
					return {"kind": "unique", "ability_id": uid_ab}
			if uid_ab == "pm_flail":
				var fl_terr: Array = engine.state["map"]["terrains"]
				var z_fl := KTGeometry.elevation_at(op["pos"], fl_terr)
				var fl_score := 0
				for fo in engine.state["operatives"]:
					var fod: Dictionary = fo
					if not engine._on_board(fod) or str(fod["uid"]) == str(op["uid"]):
						continue
					var z_fo := KTGeometry.elevation_at(fod["pos"], fl_terr)
					if KTGeometry.edge_distance_3d(op["pos"], float(op["base_radius"]), z_fl, fod["pos"], float(fod["base_radius"]), z_fo) <= 2.05 \
							and KTGeometry.has_visibility(op["pos"], float(op["base_radius"]), z_fl, fod["pos"], float(fod["base_radius"]), z_fo, fl_terr):
						fl_score += 2 if str(fod["side"]) != str(op["side"]) else -3
				if fl_score >= 2:
					return {"kind": "unique", "ability_id": uid_ab}
			if uid_ab == "aod_optics":
				# Only spend 1AP on Sniper Optics when an obscured shootable enemy actually exists!
				var usable_opt: Array = engine.get_usable_ranged_weapons(op)
				for w_opt in usable_opt:
					for e_opt in engine.state["operatives"]:
						if not bool(e_opt["incapacitated"]) and bool(e_opt.get("deployed", true)) and str(e_opt["side"]) != str(op["side"]):
							var ev_opt: Dictionary = KTGeometry.evaluate_shot(op, e_opt, w_opt, engine.state)
							if bool(ev_opt["valid"]) and bool(ev_opt["obscured"]):
								return {"kind": "unique", "ability_id": uid_ab}
			if uid_ab == "tac_surveil":
				var sv_cands: Array[Dictionary] = engine.surveil_targets(op)
				if not sv_cands.is_empty():
					return {"kind": "target_action", "ability_id": uid_ab, "target_uid": str(sv_cands[0]["uid"])}
			if uid_ab == "eq_stun":
				var st_cands: Array[Dictionary] = engine.stun_targets(op)
				var st_terr: Array = engine.state["map"]["terrains"]
				for st_t in st_cands:
					var z_st := KTGeometry.elevation_at(st_t["pos"], st_terr)
					var cluster_cnt: int = 1
					for other_s in engine.state["operatives"]:
						if not bool(other_s["incapacitated"]) and bool(other_s.get("deployed", true)) and str(other_s["uid"]) != str(st_t["uid"]):
							var z_os := KTGeometry.elevation_at(other_s["pos"], st_terr)
							if KTGeometry.edge_distance_3d(st_t["pos"], float(st_t["base_radius"]), z_st, other_s["pos"], float(other_s["base_radius"]), z_os) <= 1.001:
								cluster_cnt += 1 if str(other_s["side"]) != str(op["side"]) else -2
					if cluster_cnt >= 2:
						return {"kind": "target_action", "ability_id": uid_ab, "target_uid": str(st_t["uid"])}
			# Tac-op mission actions that need no target: take them whenever they are available
			if uid_ab.begins_with("tac_") and not bool(ua.get("needs_target", false)):
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

			# Account for Blast X / Torrent X secondary targets (Rulebook p.92 & p.94)!
			var w_rules: Dictionary = w.get("rules", {})
			if float(w_rules.get("blast", 0.0)) > 0.01:
				for sec_b in KTGeometry.get_blast_secondary_targets(op, enemy, w, ev, engine.state):
					var sd_b: Dictionary = sec_b
					if bool(sd_b.get("friendly", false)):
						dmg_est -= 36.0 # Avoid friendly fire with Blast!
					else:
						dmg_est += 18.0
			elif float(w_rules.get("torrent", 0.0)) > 0.01:
				var sec_t: Array[Dictionary] = KTGeometry.get_torrent_secondary_targets(op, enemy, w, engine.state)
				dmg_est += 16.0 * float(sec_t.size())

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

	# Already standing on the chosen objective and holding it: no need to move.
	var goal_id := ""
	for obj in engine.state["map"]["objectives"]:
		if (obj["pos"] as Vector2).distance_to(target_goal) < 0.01:
			goal_id = str(obj["id"])
			break
	if start.distance_to(target_goal) <= 1.1 and not goal_id.is_empty() and str(engine.state["objectives_state"].get(goal_id, {}).get("controlled_by", "")) == str(op["side"]):
		return []

	var best_pts: Array[Vector2] = []
	var best_val := -999.0
	var base_dir := (target_goal - start).normalized()
	if base_dir.length_squared() < 0.001:
		base_dir = Vector2(1.0, 0.0) if str(op["side"]) == "A" else Vector2(-1.0, 0.0)

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

	# Corner / wall detour: when a ruin or stronghold wall blocks direct forward movement (e.g. Volkus Map 2),
	# try 2-waypoint L-detours around the obstacle and lateral wall-clearing steps!
	if best_pts.is_empty() or best_val < 3.0:
		var perp := Vector2(-base_dir.y, base_dir.x)
		for side_sign in [1.0, -1.0]:
			var s_sign: float = float(side_sign)
			for side_d_v in [2.0, 3.0, 4.0]:
				var side_d: float = float(side_d_v)
				var rem_f: float = max_m - ceilf(side_d)
				if rem_f < 1.0:
					continue
				var mid: Vector2 = start + perp * (s_sign * side_d)
				var end_p: Vector2 = mid + base_dir * rem_f
				var l_pts: Array[Vector2] = [start, mid, end_p]
				var chk_l := KTGeometry.validate_move_path(l_pts, op, move_kind, max_m, engine.state)
				if not bool(chk_l["ok"]):
					continue
				var sc_l: float = (start.distance_to(target_goal) - end_p.distance_to(target_goal)) * 3.0
				if sc_l > best_val and sc_l > 0.4:
					best_val = sc_l
					best_pts = l_pts

	if best_pts.is_empty():
		# Last-resort lateral slide along a long wall toward a gap/edge so operatives never get wall-trapped
		var map_terrains: Array = engine.state["map"]["terrains"]
		var r_op: float = float(op["base_radius"])
		var best_esc := -999.0
		for ang_e in [-90, 90, -110, 110, -75, 75]:
			for rat_v in [1.0, 0.72]:
				var cand_e: Vector2 = start + base_dir.rotated(deg_to_rad(float(ang_e))) * (max_m * float(rat_v))
				var e_pts: Array[Vector2] = [start, cand_e]
				var chk_e := KTGeometry.validate_move_path(e_pts, op, move_kind, max_m, engine.state)
				if not bool(chk_e["ok"]):
					continue
				var probe_fwd: Vector2 = cand_e + base_dir * 2.5
				var fwd_clear := true
				for t_e in map_terrains:
					if KTGeometry.is_impassable_terrain(t_e) and KTGeometry.segment_intersects_rect(cand_e, probe_fwd, (t_e["rect"] as Rect2).grow(r_op * 0.6)):
						fwd_clear = false
						break
				var sc_e: float = (6.0 if fwd_clear else 0.0) + (start.distance_to(target_goal) - cand_e.distance_to(target_goal)) * 1.5 + start.distance_to(cand_e) * 0.35
				if sc_e > best_esc:
					best_esc = sc_e
					best_pts = e_pts

	return best_pts
