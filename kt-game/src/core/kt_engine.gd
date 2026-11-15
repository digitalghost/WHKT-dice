class_name KTEngine
extends RefCounted

## Core Turn, Activation, Ploy, Objective & Scoring State Machine for Kill Team (KT24).

var state: Dictionary = {}
var undo_stack: Array[String] = []
var rng: RandomNumberGenerator = RandomNumberGenerator.new()


func _init(seed_val: int = 0) -> void:
	if seed_val != 0:
		rng.seed = seed_val
	else:
		rng.randomize()


func start_match(
	mode: String = "ai", # "ai" | "hotseat"
	human_side: String = "A",
	aod_primary: String = "sharpshooter",
	aod_secondary: String = "dueller"
) -> void:
	undo_stack.clear()
	var map_data := KTData.load_map(KTData.DEFAULT_MAP_ID)
	var aod_team := KTData.get_team("IMP-AOD")
	var pm_team := KTData.get_team("CHAOS-PM")

	state = {
		"mode": mode,
		"human_side": human_side,
		"tp": 1,
		"phase": "INITIATIVE", # "INITIATIVE" | "STRATEGY" | "FIREFIGHT" | "GAME_OVER"
		"initiative_side": "A",
		"turn_side": "A",
		"consecutive_strategy_passes": 0,
		"active_op_uid": "",
		"active_is_counteract": false,
		"map": map_data,
		"markers": [], # e.g. Cloud of Flies marker
		"objectives_state": {
			"obj_1": {"controlled_by": "", "secured_by": "", "apl_a": 0, "apl_b": 0},
			"obj_2": {"controlled_by": "", "secured_by": "", "apl_a": 0, "apl_b": 0},
			"obj_3": {"controlled_by": "", "secured_by": "", "apl_a": 0, "apl_b": 0},
		},
		"sides": {
			"A": {
				"id": "A",
				"team_id": "IMP-AOD",
				"name": "死亡天使",
				"cp": 2,
				"vp_key": 0,
				"vp_kill": 0,
				"vp_tac": 0,
				"primary_op": "", # secretly chosen in TP1 as a strategic gambit (p.106): "key" | "kill" | "tac"
				"init_cards": [], # initiative cards held: 0 = re-roll, 1/2/3 = +N/-N
				"primary_tactic": aod_primary,
				"secondary_tactic": aod_secondary,
				"temp_tactic": "",
				"combat_doctrine": "", # "devastator" | "tactical" | "assault"
				"active_ploys": [],
				"used_ploys_tp": [],
				"purity_used_tp": false,
				"heroic_used_tp": false,
				"init_roll": 0,
			},
			"B": {
				"id": "B",
				"team_id": "CHAOS-PM",
				"name": "瘟疫战士",
				"cp": 2,
				"vp_key": 0,
				"vp_kill": 0,
				"vp_tac": 0,
				"primary_op": "",
				"init_cards": [],
				"primary_tactic": "",
				"secondary_tactic": "",
				"temp_tactic": "",
				"combat_doctrine": "",
				"active_ploys": [],
				"used_ploys_tp": [],
				"has_plague_bell": true,
				"init_roll": 0,
			},
		},
		"operatives": [],
		"log": [],
	}

	_teams = {"A": aod_team, "B": pm_team}
	_roster_override = {}

	# Rulebook p.105 "1.设置战斗": pre-battle roll-off (ties re-rolled). The winner has the pre-battle
	# initiative: they choose a drop zone (the opponent takes the other) and deploy first.
	var pre_a := rng.randi_range(1, 6)
	var pre_b := rng.randi_range(1, 6)
	while pre_a == pre_b:
		pre_a = rng.randi_range(1, 6)
		pre_b = rng.randi_range(1, 6)
	var first := "A" if pre_a > pre_b else "B"
	state["phase"] = "DROPZONE"
	state["setup"] = {
		"first_side": first, "roll_a": pre_a, "roll_b": pre_b,
		"select_side": first,
		"rosters": {"A": [], "B": []},
		"equipment": {"A": [], "B": []},
		"equip_done": {"A": false, "B": false},
		"equip_side": first,
	}
	state["turn_side"] = first
	# The side WITHOUT pre-battle initiative wins a tied TP1 initiative roll (校注 p.106)
	state["initiative_side"] = first
	recalculate_objectives_and_kills()
	add_log("⚔️ 战局开启：杀戮区·沃库斯 ·【死亡天使】迎战【瘟疫战士】！")
	add_log("🎲 战前拼骰 %d : %d → 【%s】取得战前先手：先选择降落区。" % [pre_a, pre_b, str(state["sides"][first]["name"])])


# ==============================================================================
# PRE-BATTLE SETUP: drop zone choice -> operative selection -> deployment
# ==============================================================================

var _teams: Dictionary = {}
var _roster_override: Dictionary = {}


## Side that must act now during DROPZONE / SELECT ("" otherwise).
func setup_side() -> String:
	var ph := str(state.get("phase", ""))
	if ph == "DROPZONE":
		return str(state["setup"]["first_side"])
	if ph == "SELECT":
		return str(state["setup"]["select_side"])
	if ph == "EQUIP":
		return str(state["setup"]["equip_side"])
	return ""


## The pre-battle initiative holder picks a drop zone. `take_other` = true swaps the two zones
## (map data assigns zone A to side A by default).
func choose_drop_zone(take_other: bool) -> Dictionary:
	if str(state["phase"]) != "DROPZONE":
		return {"ok": false}
	push_undo()
	var first := str(state["setup"]["first_side"])
	if take_other:
		var tmp: Rect2 = state["map"]["drop_zone_a"]
		state["map"]["drop_zone_a"] = state["map"]["drop_zone_b"]
		state["map"]["drop_zone_b"] = tmp
		# New id so the board view rebuilds its zone overlay
		state["map"]["id"] = str(state["map"]["id"]) + "_swapped"
	state["phase"] = "SELECT"
	state["turn_side"] = first
	# p.105: the opponent takes the other drop zone and gains the Re-roll initiative card
	var second := "B" if first == "A" else "A"
	state["sides"][second]["init_cards"] = [INIT_CARD_REROLL]
	add_log("🧭 【%s】选定降落区；【%s】获得重投先手权卡。双方秘密选择出战特工。" % [str(state["sides"][first]["name"]), str(state["sides"][second]["name"])])
	return {"ok": true}


func default_roster(side_id: String) -> Array:
	var team: Dictionary = _teams.get(side_id, {})
	return (team.get("canonical_roster", []) as Array).duplicate()


func roster_pool(side_id: String) -> Array:
	return (_teams.get(side_id, {}) as Dictionary).get("operatives", [])


## Only Angels of Death warriors may be taken more than once (team composition rules).
static func roster_allows_duplicate(team: Dictionary, tpl: Dictionary) -> bool:
	return str(team.get("team_id", "")) == "IMP-AOD" and str(tpl.get("role", "")) == "战士"


## Team selection rules (from each team's 编成 card): exactly 6 operatives, exactly 1 leader,
## every non-repeatable operative at most once.
static func validate_roster(team: Dictionary, ids: Array) -> Dictionary:
	var by_id := {}
	for o in team.get("operatives", []):
		by_id[str(o["id"])] = o
	if ids.size() != 6:
		return {"ok": false, "reason": "需要正好 6 名特工（当前 %d 名）" % ids.size()}
	var leaders := 0
	var counts := {}
	for id_v in ids:
		var id := str(id_v)
		if not by_id.has(id):
			return {"ok": false, "reason": "未知特工"}
		counts[id] = int(counts.get(id, 0)) + 1
		if str(by_id[id].get("role", "")) == "领袖":
			leaders += 1
	if leaders != 1:
		return {"ok": false, "reason": "必须正好选择 1 名领袖（当前 %d 名）" % leaders}
	for id in counts.keys():
		if int(counts[id]) > 1 and not roster_allows_duplicate(team, by_id[id]):
			return {"ok": false, "reason": "【%s】最多选择 1 名" % str(by_id[id]["name"])}
	return {"ok": true, "reason": ""}


func confirm_roster(side_id: String, ids: Array) -> Dictionary:
	if str(state["phase"]) != "SELECT" or side_id != setup_side():
		return {"ok": false, "msg": "现在不是该方选择特工"}
	var chk := validate_roster(_teams[side_id], ids)
	if not bool(chk["ok"]):
		return {"ok": false, "msg": str(chk["reason"])}
	push_undo()
	state["setup"]["rosters"][side_id] = ids.duplicate()
	var other := "B" if side_id == "A" else "A"
	if (state["setup"]["rosters"][other] as Array).is_empty():
		state["setup"]["select_side"] = other
		state["turn_side"] = other
		add_log("📋 【%s】已选定出战特工（暂不公开）。" % str(state["sides"][side_id]["name"]))
	else:
		add_log("📋 双方同时揭示出战特工。接下来秘密选择装备（最多 4 件选项）。")
		state["phase"] = "EQUIP"
		var first := str(state["setup"]["first_side"])
		state["setup"]["equip_side"] = first
		state["turn_side"] = first
	return {"ok": true, "msg": ""}


## Rulebook p.106: each player secretly selects up to 4 equipment options, each option at most once,
## then both reveal simultaneously. Only faction equipment is modelled so far (text from the team JSON).
func equipment_options(side_id: String) -> Array:
	var out: Array = []
	for e in (_teams.get(side_id, {}) as Dictionary).get("rule_entries", []):
		if str(e.get("category", "")) == "阵营装备":
			out.append(e)
	return out


func default_equipment(side_id: String) -> Array:
	var out: Array = []
	for e in equipment_options(side_id):
		if out.size() < 4:
			out.append(str(e["id"]))
	return out


func confirm_equipment(side_id: String, ids: Array) -> Dictionary:
	if str(state["phase"]) != "EQUIP" or side_id != setup_side():
		return {"ok": false, "msg": "现在不是该方选择装备"}
	if ids.size() > 4:
		return {"ok": false, "msg": "最多选择 4 件装备选项"}
	var known := {}
	for e in equipment_options(side_id):
		known[str(e["id"])] = true
	var seen := {}
	for i in ids:
		if not known.has(str(i)):
			return {"ok": false, "msg": "该小队没有这件装备"}
		if seen.has(str(i)):
			return {"ok": false, "msg": "每种装备选项只能选择一次"}
		seen[str(i)] = true
	push_undo()
	state["setup"]["equipment"][side_id] = ids.duplicate()
	state["setup"]["equip_done"][side_id] = true
	var other := "B" if side_id == "A" else "A"
	if not bool(state["setup"]["equip_done"][other]):
		state["setup"]["equip_side"] = other
		state["turn_side"] = other
		add_log("🎒 【%s】已选定装备（暂不公开）。" % str(state["sides"][side_id]["name"]))
	else:
		for sid in ["A", "B"]:
			var names := PackedStringArray()
			for e in equipment_options(sid):
				if (state["setup"]["equipment"][sid] as Array).has(str(e["id"])):
					names.append(str(e["title"]).get_slice("（", 0))
			add_log("🎒 揭示装备 ·【%s】：%s" % [str(state["sides"][sid]["name"]), "无" if names.is_empty() else "、".join(names)])
		_begin_deploy()
	return {"ok": true, "msg": ""}


const EQ_BLIGHT_GRENADE_ID := "eq_blight_grenade"


## Apply the revealed equipment to side state and freshly spawned operatives.
func _apply_equipment() -> void:
	for sid in ["A", "B"]:
		var ss: Dictionary = state["sides"][sid]
		ss["equipment"] = (state["setup"]["equipment"][sid] as Array).duplicate()
		ss["has_plague_bell"] = KTCombat.has_equipment(ss, "bell")
		var ammo: bool = KTCombat.has_equipment(ss, "plagueammo")
		var gren_tpl: Dictionary = {}
		if KTCombat.has_equipment(ss, "blightgrenade"):
			# "您不能在战斗中使用本武器超过两次"
			ss["blight_grenades_left"] = 2
			for o in (_teams[sid] as Dictionary).get("operatives", []):
				for w in o.get("weapons", []):
					if str(w["id"]) == "pm_bombardier_blight_grenade":
						gren_tpl = w
		for op in state["operatives"]:
			if str(op["side"]) != sid:
				continue
			if ammo:
				# 瘟疫弹药: boltguns and bolt pistols gain Poison and Severe
				for w in op["weapons"]:
					if str(w["name"]) in ["爆矢枪", "爆矢手枪"]:
						w["rules"]["poison"] = true
						w["rules"]["severe"] = true
						var kws: Array = w.get("keywords", [])
						for kw in ["严重", "毒素"]:
							if not kws.has(kw):
								kws.append(kw)
						w["keywords"] = kws
						w["profile"] = str(w.get("profile", "")) + " · 严重、毒素（瘟疫弹药）"
			# The Bombardier carries his own grenades (better profile, not counted against the limit)
			if not gren_tpl.is_empty() and str(op["op_id"]) != "pm_bombardier":
				var g: Dictionary = gren_tpl.duplicate(true)
				g["id"] = EQ_BLIGHT_GRENADE_ID
				g["hit"] = 4
				g["rules"]["toxic"] = false
				g["keywords"] = ["范围 6\"", "爆炸 2\"", "集中", "严重", "毒素"]
				g["profile"] = "4A · 4+ · 2/4 · 范围 6\"、爆炸 2\"、集中、严重、毒素（全队限 2 次）"
				(op["weapons"] as Array).append(g)


## 排毒口: is `op` within 3" of an enemy operative whose team took the Poison Vents?
func _ventilator_in_range(op: Dictionary) -> bool:
	var opp := "B" if str(op["side"]) == "A" else "A"
	if not KTCombat.has_equipment(state["sides"][opp], "ventilator"):
		return false
	for o in state["operatives"]:
		if o["incapacitated"] or str(o["side"]) != opp or not bool(o.get("deployed", true)):
			continue
		if KTGeometry.edge_distance(op["pos"], float(op["base_radius"]), o["pos"], float(o["base_radius"])) <= 3.0:
			return true
	return false


## Rulebook p.106 "3.部署特工": players alternate placing thirds of their team (rounded up, larger
## groups first: 6 -> 2/2/2, 7 -> 3/2/2) fully inside their own drop zone with a Conceal order.
func _begin_deploy() -> void:
	_roster_override = state["setup"]["rosters"]
	state["operatives"].clear()
	_spawn_roster("A", _teams["A"], state["map"]["spawn_a"])
	_spawn_roster("B", _teams["B"], state["map"]["spawn_b"])
	for op in state["operatives"]:
		op["deployed"] = false
		op["pos"] = Vector2(-99.0, -99.0)
	_apply_equipment()
	state["start_counts"] = {"A": _count_side_ops("A"), "B": _count_side_ops("B")}
	var first := str(state["setup"]["first_side"])
	state["phase"] = "DEPLOY"
	state["deploy"] = {
		"first_side": first,
		"turn_side": first,
		"groups": {"A": _deploy_groups(_count_side_ops("A")), "B": _deploy_groups(_count_side_ops("B"))},
		"group_idx": {"A": 0, "B": 0},
		"placed_in_group": {"A": 0, "B": 0},
	}
	state["turn_side"] = first
	recalculate_objectives_and_kills()
	add_log("📍 【%s】先部署。双方轮流部署特工（每次 1/3 小队，须完全位于己方降落区内并持隐匿命令）。" % str(state["sides"][first]["name"]))


## Run drop-zone choice and operative selection with defaults for whoever has not acted yet.
func auto_setup() -> void:
	if str(state["phase"]) == "DROPZONE":
		choose_drop_zone(false)
	var guard := 0
	while str(state["phase"]) == "SELECT" and guard < 4:
		guard += 1
		var sd := setup_side()
		confirm_roster(sd, default_roster(sd))
	guard = 0
	while str(state["phase"]) == "EQUIP" and guard < 4:
		guard += 1
		var es := setup_side()
		confirm_equipment(es, default_equipment(es))


func _in_enemy_territory(op: Dictionary) -> bool:
	var own: Rect2 = state["map"]["drop_zone_a"] if str(op["side"]) == "A" else state["map"]["drop_zone_b"]
	var w := float(state["map"]["width"])
	var h := float(state["map"]["height"])
	var c := own.get_center()
	var pp: Vector2 = op["pos"]
	if own.size.x >= own.size.y:
		return (pp.y > h * 0.5) if c.y < h * 0.5 else (pp.y < h * 0.5)
	return (pp.x > w * 0.5) if c.x < w * 0.5 else (pp.x < w * 0.5)


func _count_side_ops(side_id: String) -> int:
	var n := 0
	for op in state["operatives"]:
		if str(op["side"]) == side_id:
			n += 1
	return n


## Split n operatives into three groups, as even as possible, earlier groups larger (WCW FAQ).
static func _deploy_groups(n: int) -> Array[int]:
	var base := int(n / 3)
	var rem := n % 3
	var out: Array[int] = []
	for i in range(3):
		out.append(base + (1 if i < rem else 0))
	return out


# ==============================================================================
# PHASE 0: DEPLOYMENT (alternating thirds into own drop zone, Conceal order)
# ==============================================================================

func deploy_side() -> String:
	return str(state.get("deploy", {}).get("turn_side", ""))


func deploy_remaining_in_group(side_id: String) -> int:
	var d: Dictionary = state.get("deploy", {})
	if d.is_empty():
		return 0
	var gi := int(d["group_idx"][side_id])
	var groups: Array = d["groups"][side_id]
	if gi >= groups.size():
		return 0
	return int(groups[gi]) - int(d["placed_in_group"][side_id])


func undeployed_ops(side_id: String) -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	for op in state["operatives"]:
		if str(op["side"]) == side_id and not bool(op.get("deployed", false)):
			out.append(op)
	return out


## Legal spot check for a deployment: base fully inside the side's drop zone, on the ground floor,
## not overlapping terrain or other operatives.
func validate_deploy_pos(op: Dictionary, pos: Vector2) -> Dictionary:
	var dz: Rect2 = state["map"]["drop_zone_a"] if str(op["side"]) == "A" else state["map"]["drop_zone_b"]
	var r := float(op["base_radius"])
	var inner := Rect2(dz.position + Vector2(r, r), dz.size - Vector2(2.0 * r, 2.0 * r))
	if not inner.has_point(pos):
		return {"ok": false, "reason": "底座必须完全位于己方降落区内"}
	var stand := KTGeometry.is_valid_stand_pos(pos, r, str(op["uid"]), state)
	if not bool(stand["ok"]):
		return {"ok": false, "reason": str(stand.get("reason", "该位置被地形或其他特工占据"))}
	return {"ok": true, "reason": ""}


func deploy_operative(uid: String, pos: Vector2) -> Dictionary:
	if str(state["phase"]) != "DEPLOY":
		return {"ok": false, "msg": "当前不是部署阶段"}
	var op := get_op(uid)
	if op.is_empty() or bool(op.get("deployed", false)):
		return {"ok": false, "msg": "该特工已部署"}
	var side := str(op["side"])
	if side != deploy_side():
		return {"ok": false, "msg": "现在由对方部署"}
	var chk := validate_deploy_pos(op, pos)
	if not bool(chk["ok"]):
		return {"ok": false, "msg": str(chk["reason"])}
	push_undo()
	op["pos"] = pos
	op["deployed"] = true
	op["order"] = "conceal"
	var d: Dictionary = state["deploy"]
	d["placed_in_group"][side] = int(d["placed_in_group"][side]) + 1
	add_log("📍 【%s】部署【%s】于降落区 (%.1f\", %.1f\")，隐匿命令。" % [str(state["sides"][side]["name"]), str(op["name"]), pos.x, pos.y])

	# Group complete -> hand over to the other side (if they still have operatives to place)
	if deploy_remaining_in_group(side) <= 0:
		d["group_idx"][side] = int(d["group_idx"][side]) + 1
		d["placed_in_group"][side] = 0
		var other := "B" if side == "A" else "A"
		if not undeployed_ops(other).is_empty():
			d["turn_side"] = other
		elif undeployed_ops(side).is_empty():
			d["turn_side"] = ""
	state["turn_side"] = str(d["turn_side"]) if not str(d["turn_side"]).is_empty() else state["turn_side"]

	if undeployed_ops("A").is_empty() and undeployed_ops("B").is_empty():
		state["phase"] = "INITIATIVE"
		state.erase("deploy")
		recalculate_objectives_and_kills()
		add_log("✅ 双方部署完毕。请投掷第 1 转折点先手骰。")
	return {"ok": true, "msg": ""}


## Complete the whole deployment with the AI placer for both sides (tests, screenshots, quick-start).
func auto_deploy_all() -> void:
	auto_setup()
	var guard := 0
	while str(state["phase"]) == "DEPLOY" and guard < 64:
		guard += 1
		if not bool(ai_deploy_next().get("ok", false)):
			break


## AI deployment: pick a clear spot in own drop zone, spread along it, snipers/gunners toward the
## middle so they get early sightlines, melee toward the flanks closer to objectives.
func ai_deploy_next() -> Dictionary:
	var side := deploy_side()
	if side.is_empty():
		return {"ok": false}
	var pending := undeployed_ops(side)
	if pending.is_empty():
		return {"ok": false}
	var op: Dictionary = pending[0]
	var dz: Rect2 = state["map"]["drop_zone_a"] if side == "A" else state["map"]["drop_zone_b"]
	var r := float(op["base_radius"])
	var placed_n := _count_side_ops(side) - pending.size()
	var total := _count_side_ops(side)
	var horizontal := dz.size.x >= dz.size.y
	var best := Vector2.ZERO
	var found := false
	# Spread candidates along the zone's long axis; probe outward from the ideal slot
	var frac := (float(placed_n) + 0.5) / float(maxi(1, total))
	for attempt in range(40):
		var jitter := float(attempt) * 0.35 * (1.0 if attempt % 2 == 0 else -1.0)
		var along := frac * (dz.size.x if horizontal else dz.size.y) + jitter
		var across := (dz.size.y if horizontal else dz.size.x) * (0.5 + 0.25 * sin(float(attempt) * 1.7))
		var cand := dz.position + (Vector2(along, across) if horizontal else Vector2(across, along))
		cand.x = clampf(cand.x, dz.position.x + r + 0.05, dz.end.x - r - 0.05)
		cand.y = clampf(cand.y, dz.position.y + r + 0.05, dz.end.y - r - 0.05)
		if bool(validate_deploy_pos(op, cand)["ok"]):
			best = cand
			found = true
			break
	if not found:
		return {"ok": false}
	return deploy_operative(str(op["uid"]), best)


func _spawn_roster(side_id: String, team_data: Dictionary, spawns: Array) -> void:
	var roster_ids: Array = team_data.get("canonical_roster", [])
	var picked: Array = _roster_override.get(side_id, [])
	if not picked.is_empty():
		roster_ids = picked
	var ops_by_id := {}
	for o in team_data.get("operatives", []):
		ops_by_id[o["id"]] = o

	for idx in range(roster_ids.size()):
		var op_id := str(roster_ids[idx])
		if not ops_by_id.has(op_id):
			continue
		var tpl: Dictionary = ops_by_id[op_id]
		var def_wids: Array = tpl.get("default_weapon_ids", [])
		var equipped_weapons: Array = []
		for w in tpl.get("weapons", []):
			if def_wids.is_empty() or def_wids.has(w["id"]):
				equipped_weapons.append(w.duplicate(true))

		var pos: Vector2 = spawns[idx % spawns.size()]
		var has_siege := side_id == "A" and (
			str(state["sides"]["A"]["primary_tactic"]) == "siege_specialist"
			or str(state["sides"]["A"]["secondary_tactic"]) == "siege_specialist"
		)

		state["operatives"].append({
			"uid": "%s_%d_%s" % [side_id, idx + 1, op_id],
			"side": side_id,
			"team_id": str(team_data.get("team_id", "")),
			"op_id": op_id,
			"name": str(tpl["name"]),
			"role": str(tpl.get("role", "")),
			"apl_base": int(tpl["apl"]),
			"apl_mod": 0,
			"ap_left": int(tpl["apl"]),
			"move_base": float(tpl["move_inches"]),
			"save": int(tpl["save"]),
			"wounds_max": int(tpl["wounds"]),
			"wounds_cur": int(tpl["wounds"]),
			"base_mm": int(tpl["base_mm"]),
			"base_radius": float(tpl["base_radius_inches"]),
			"pos": pos,
			"order": "conceal", # Start concealed in Drop Zone!
			"ready": true,
			"incapacitated": false,
			"counteracted_tp": false,
			"guard_active": false,
			"poisoned": false,
			"camo_cloak": op_id == "aod_sniper",
			"has_innate_stealthy": op_id == "aod_sniper",
			"siege_saturate": has_siege,
			"ignore_obscured": false,
			"iron_halo_used": false,
			"vitality_used_tp": false,
			"blessing_healed_tp": 0,
			"shock_assault_active": false,
			"auto_resilient_active": false,
			"stun_skip_current_end": false,
			"charged_this_tp": false,
			"moved_distance_tp": 0.0,
			"moved_reposition_or_charge": false,
			"actions_taken": [],
			"shoot_count": 0,
			"fight_count": 0,
			"bolt_or_psychic_shot": false,
			"heavy_sniper_shot": false,
			"psychic_weapons_used": [],
			"portrait": str(tpl.get("portrait", "")),
			"standee": str(tpl.get("standee", "")),
			"cards": tpl.get("cards", []).duplicate(),
			"weapons": equipped_weapons,
			"abilities": tpl.get("abilities", []).duplicate(true),
		})


func push_undo() -> void:
	var snap := var_to_str(state)
	undo_stack.append(snap)
	if undo_stack.size() > 30:
		undo_stack.pop_front()


func can_undo() -> bool:
	return not undo_stack.is_empty()


func pop_undo() -> bool:
	if undo_stack.is_empty():
		return false
	var raw: String = undo_stack.pop_back()
	var restored = str_to_var(raw)
	if typeof(restored) == TYPE_DICTIONARY:
		state = restored
		return true
	return false


func is_ai_turn() -> bool:
	if str(state.get("mode", "ai")) != "ai":
		return false
	var phase := str(state.get("phase", ""))
	if phase == "DEPLOY":
		return deploy_side() != str(state["human_side"])
	if phase in ["DROPZONE", "SELECT", "EQUIP"]:
		return setup_side() != str(state["human_side"])
	if phase == "GAME_OVER":
		return false
	if phase == "INITIATIVE":
		return init_pending() and str(state["init"]["turn"]) != str(state["human_side"])
	var ai_side := "B" if str(state["human_side"]) == "A" else "A"
	return str(state.get("turn_side", "")) == ai_side


func get_op(uid: String) -> Dictionary:
	for op in state["operatives"]:
		if str(op["uid"]) == uid:
			return op
	return {}


func add_log(msg: String) -> void:
	var prefix := "[TP%d]" % int(state.get("tp", 1))
	state["log"].append("%s %s" % [prefix, msg])
	if state["log"].size() > 120:
		state["log"].pop_front()


# ==============================================================================
# PHASE 1: INITIATIVE & READY STEP
# ==============================================================================

const INIT_CARD_REROLL := 0


## True between the initiative roll and the end of the initiative-card step (p.106 step 4).
func init_pending() -> bool:
	return str(state.get("phase", "")) == "INITIATIVE" and state.has("init") and not bool(state["init"].get("resolved", true))


## A side's current roll-off result including card modifiers (may exceed 6 or drop below 1).
func init_value(side_id: String) -> int:
	return int(state["init"]["roll"][side_id]) + int(state["init"]["mod"][side_id])


## Side currently winning the roll-off. Ties are NOT re-rolled: the player who does not
## currently have initiative wins (in TP1: the player without the pre-battle initiative).
func init_leader() -> String:
	var va := init_value("A")
	var vb := init_value("B")
	if va > vb:
		return "A"
	if vb > va:
		return "B"
	return "B" if str(state["initiative_side"]) == "A" else "A"


func roll_initiative() -> Dictionary:
	if str(state["phase"]) != "INITIATIVE" or init_pending():
		return {"ok": false}
	push_undo()

	var ra := rng.randi_range(1, 6)
	var rb := rng.randi_range(1, 6)
	state["sides"]["A"]["init_roll"] = ra
	state["sides"]["B"]["init_roll"] = rb
	state["init"] = {"resolved": false, "roll": {"A": ra, "B": rb}, "mod": {"A": 0, "B": 0}, "passes": 0, "turn": "", "winner": ""}
	add_log("🎲 TP%d 先手权拼骰：死亡天使 [%d] vs 瘟疫战士 [%d]。" % [int(state["tp"]), ra, rb])

	# Starting with the loser of the roll-off, players alternate playing an initiative card or passing.
	var lead := init_leader()
	var loser := "B" if lead == "A" else "A"
	var lead_cards: Array = state["sides"][lead]["init_cards"]
	var loser_cards: Array = state["sides"][loser]["init_cards"]
	if lead_cards.is_empty() and loser_cards.is_empty():
		_resolve_initiative()
	elif loser_cards.is_empty():
		state["init"]["passes"] = 1
		state["init"]["turn"] = lead
		state["turn_side"] = lead
	else:
		state["init"]["turn"] = loser
		state["turn_side"] = loser
	return {"ok": true, "roll_a": ra, "roll_b": rb, "winner": init_leader() if init_pending() else str(state["init"]["winner"]), "pending": init_pending()}


## Play an initiative card. `card` 0 = re-roll (the new result overrides every earlier modifier);
## 1/2/3 = modify own result by +N (sign >= 0) or -N (sign < 0).
func init_play_card(side_id: String, card: int, sign: int = 1) -> Dictionary:
	if not init_pending() or str(state["init"]["turn"]) != side_id:
		return {"ok": false, "msg": "当前不是该方使用先手权卡的时机"}
	if not (state["sides"][side_id]["init_cards"] as Array).has(card):
		return {"ok": false, "msg": "没有这张先手权卡"}
	push_undo()
	(state["sides"][side_id]["init_cards"] as Array).erase(card)
	var sname := str(state["sides"][side_id]["name"])
	if card == INIT_CARD_REROLL:
		var r := rng.randi_range(1, 6)
		state["init"]["roll"][side_id] = r
		state["init"]["mod"][side_id] = 0
		state["sides"][side_id]["init_roll"] = r
		add_log("🔁 【%s】打出重投先手权卡 → 新结果 [%d]。" % [sname, r])
	else:
		var delta := card if sign >= 0 else -card
		state["init"]["mod"][side_id] = int(state["init"]["mod"][side_id]) + delta
		add_log("🎴 【%s】打出 %+d 先手权卡 → 结果修正为 [%d]。" % [sname, delta, init_value(side_id)])
	state["init"]["passes"] = 0
	_init_advance(side_id)
	return {"ok": true, "reroll": card == INIT_CARD_REROLL}


func init_pass(side_id: String) -> Dictionary:
	if not init_pending() or str(state["init"]["turn"]) != side_id:
		return {"ok": false, "msg": "当前不是该方行动"}
	push_undo()
	state["init"]["passes"] = int(state["init"]["passes"]) + 1
	if int(state["init"]["passes"]) >= 2:
		_resolve_initiative()
	else:
		_init_advance(side_id)
	return {"ok": true}


## Hand the card step to the opponent; a player holding no cards can only pass.
func _init_advance(side_id: String) -> void:
	var other := "B" if side_id == "A" else "A"
	if (state["sides"][other]["init_cards"] as Array).is_empty():
		state["init"]["passes"] = int(state["init"]["passes"]) + 1
		if int(state["init"]["passes"]) >= 2 or (state["sides"][side_id]["init_cards"] as Array).is_empty():
			_resolve_initiative()
			return
		state["init"]["turn"] = side_id
		state["turn_side"] = side_id
	else:
		state["init"]["turn"] = other
		state["turn_side"] = other


## Roll + both players pass on initiative cards (tests, scripted flows).
func auto_initiative() -> Dictionary:
	var res := roll_initiative()
	while init_pending():
		init_pass(str(state["init"]["turn"]))
	if bool(res.get("ok", false)):
		res["winner"] = str(state["init"]["winner"])
	return res


func _resolve_initiative() -> void:
	var tp := int(state["tp"])
	var ra := init_value("A")
	var rb := init_value("B")
	# The roll-off winner decides who has initiative; here the winner always takes it.
	var winner := init_leader()
	state["init"]["resolved"] = true
	state["init"]["winner"] = winner

	# Except in TP4, the LOSER of the roll-off gains the initiative card numbered after this TP.
	var card_note := ""
	if tp < 4:
		var card_side := "B" if winner == "A" else "A"
		(state["sides"][card_side]["init_cards"] as Array).append(tp)
		card_note = "【%s】获得 +%d/-%d 先手权卡。" % [str(state["sides"][card_side]["name"]), tp, tp]

	state["initiative_side"] = winner
	state["turn_side"] = winner
	state["consecutive_strategy_passes"] = 0

	# Ready step (rulebook p.8): each player gains 1CP; after TP1 the player WITHOUT initiative gains 2CP instead.
	# (The 2CP from pre-battle setup, p.106, are the starting CP.)
	if tp == 1:
		state["sides"]["A"]["cp"] = int(state["sides"]["A"]["cp"]) + 1
		state["sides"]["B"]["cp"] = int(state["sides"]["B"]["cp"]) + 1
	else:
		var loser := "B" if winner == "A" else "A"
		state["sides"][winner]["cp"] = int(state["sides"][winner]["cp"]) + 1
		state["sides"][loser]["cp"] = int(state["sides"][loser]["cp"]) + 2

	# Reset per-TP side & operative states
	for sid in ["A", "B"]:
		state["sides"][sid]["active_ploys"].clear()
		state["sides"][sid]["used_ploys_tp"].clear()
		state["sides"][sid]["combat_doctrine"] = ""
		state["sides"][sid]["temp_tactic"] = ""
		state["sides"][sid]["purity_used_tp"] = false
		state["sides"][sid]["shields_used_tp"] = false
		state["sides"][sid]["auspex_used_tp"] = false
		state["sides"][sid]["heroic_used_tp"] = false

	state["markers"].clear()
	for op in state["operatives"]:
		if not op["incapacitated"]:
			op["ready"] = true
			op["counteracted_tp"] = false
			op["guard_active"] = false
			op["charged_this_tp"] = false
			op["moved_distance_tp"] = 0.0
			op["moved_reposition_or_charge"] = false
			op["vitality_used_tp"] = false
			op["blessing_healed_tp"] = 0
			op["shock_assault_active"] = false
			op["auto_resilient_active"] = false

	state["phase"] = "STRATEGY"
	var win_name := str(state["sides"][winner]["name"])
	add_log("🎲 先手权结算：死亡天使 [%d] vs 瘟疫战士 [%d] → 【%s】夺得 TP%d 先手权！%s进入战略阶段。" % [ra, rb, win_name, tp, card_note])


# ==============================================================================
# PHASE 2: STRATEGY PHASE (Strategic Ploys & Pass)
# ==============================================================================

func get_strategic_ploy_options(side_id: String) -> Array[Dictionary]:
	var side_state: Dictionary = state["sides"][side_id]
	var team_id := str(side_state["team_id"])
	var used: Array = side_state["used_ploys_tp"]
	var out: Array[Dictionary] = []

	if team_id == "IMP-AOD":
		out.append({
			"id": "doctrine_devastator",
			"ploy_key": "战斗条令",
			"title": "战斗条令：毁灭 (>6\"平衡)",
			"cost": 1,
			"used": used.has("战斗条令"),
			"desc": "对 6\" 外目标远程射击获得【平衡】（免费重掷 1 枚攻击骰）。"
		})
		out.append({
			"id": "doctrine_tactical",
			"ploy_key": "战斗条令",
			"title": "战斗条令：战术 (≤6\"平衡)",
			"cost": 1,
			"used": used.has("战斗条令"),
			"desc": "对 6\" 内目标远程射击获得【平衡】（免费重掷 1 枚攻击骰）。"
		})
		out.append({
			"id": "doctrine_assault",
			"ploy_key": "战斗条令",
			"title": "战斗条令：突击 (近战平衡)",
			"cost": 1,
			"used": used.has("战斗条令"),
			"desc": "近战与反击获得【平衡】（免费重掷 1 枚攻击骰）。"
		})
		out.append({
			"id": "know_no_fear",
			"ploy_key": "他们无所畏惧",
			"title": "他们无所畏惧",
			"cost": 1,
			"used": used.has("他们无所畏惧"),
			"desc": "本转折点忽略全部死亡天使特工因【受创】导致的移动与命中恶化。"
		})
		out.append({
			"id": "indomitus",
			"ploy_key": "不屈不挠",
			"title": "不屈不挠",
			"cost": 1,
			"used": used.has("不屈不挠"),
			"desc": "被射击掷出 2 枚以上防御失败时，自动将其中 1 枚转化为普通豁免成功！"
		})
	else:
		# CHAOS-PM Strategic Ploys
		var free_contagion := false
		for op in state["operatives"]:
			if not op["incapacitated"] and str(op["op_id"]) == "pm_icon_bearer" and _in_enemy_territory(op):
				free_contagion = true
				break
		out.append({
			"id": "contagion",
			"ploy_key": "传染",
			"title": "传染%s" % (" (圣像0CP!)" if free_contagion else ""),
			"cost": 0 if free_contagion else 1,
			"used": used.has("传染"),
			"desc": "3\" 内可见带毒敌军（或持徽手 3\" 内任意敌军）移动 -2\" 且命中恶化 +1！"
		})
		out.append({
			"id": "plodding_death",
			"ploy_key": "慢步死亡",
			"title": "慢步死亡",
			"cost": 1,
			"used": used.has("慢步死亡"),
			"desc": "移动不超过 3\" 的激活中射击/近战及全部反击获得【无休】重掷！"
		})
		out.append({
			"id": "cloud_of_flies",
			"ploy_key": "飞蝇云",
			"title": "飞蝇云",
			"cost": 1,
			"used": used.has("飞蝇云"),
			"desc": "在战团中心释放飞蝇云标识，1\" 内友军受 3\" 外射击自动享有【重型遮挡】！"
		})
		out.append({
			"id": "nurglings",
			"ploy_key": "纳垢灵",
			"title": "纳垢灵骚扰",
			"cost": 1,
			"used": used.has("纳垢灵"),
			"desc": "令 3\" 内（或 7\" 内带毒）最近一名敌方特工陷入纠缠，APL -1！"
		})
	return out


func use_strategic_ploy(side_id: String, option_id: String) -> Dictionary:
	if str(state["phase"]) != "STRATEGY" or str(state["turn_side"]) != side_id:
		return {"ok": false, "msg": "当前不是该方的战略计谋时机"}

	var options := get_strategic_ploy_options(side_id)
	var chosen := {}
	for opt in options:
		if str(opt["id"]) == option_id:
			chosen = opt
			break
	if chosen.is_empty():
		return {"ok": false, "msg": "未找到计谋"}
	if bool(chosen["used"]):
		return {"ok": false, "msg": "本转折点已使用过该战略计谋"}

	var side_state: Dictionary = state["sides"][side_id]
	var cost := int(chosen["cost"])
	if int(side_state["cp"]) < cost:
		return {"ok": false, "msg": "CP 命令点不足"}

	push_undo()
	side_state["cp"] = int(side_state["cp"]) - cost
	var pkey := str(chosen["ploy_key"])
	side_state["used_ploys_tp"].append(pkey)
	if not side_state["active_ploys"].has(pkey):
		side_state["active_ploys"].append(pkey)

	if option_id == "doctrine_devastator":
		side_state["combat_doctrine"] = "devastator"
	elif option_id == "doctrine_tactical":
		side_state["combat_doctrine"] = "tactical"
	elif option_id == "doctrine_assault":
		side_state["combat_doctrine"] = "assault"
	elif option_id == "cloud_of_flies":
		# Place Cloud of Flies at the Plague Marine that has advanced furthest from its own zone
		var own_dz: Rect2 = state["map"]["drop_zone_a"] if side_id == "A" else state["map"]["drop_zone_b"]
		var anchor := Vector2(15.0, 11.0)
		var best_d := -1.0
		for op in state["operatives"]:
			if op["incapacitated"] or str(op["side"]) != side_id:
				continue
			var d := (op["pos"] as Vector2).distance_to(own_dz.get_center())
			if d > best_d:
				best_d = d
				anchor = op["pos"]
		state["markers"].append({"kind": "cloud_of_flies", "side": side_id, "pos": anchor, "name": "飞蝇云"})
	elif option_id == "nurglings":
		var victim := _find_nurgling_victim(side_id)
		if not victim.is_empty():
			var opp_side: Dictionary = state["sides"][victim["side"]]
			if not KTCombat.has_chapter_tactic(opp_side, victim, "resolute"):
				victim["apl_mod"] = -1
			add_log("🐛 纳垢灵扑向【%s】，使其下次激活 APL -1！" % str(victim["name"]))

	add_log("🃏 【%s】发动战略计谋《%s》(-%dCP)" % [str(side_state["name"]), str(chosen["title"]), cost])
	state["consecutive_strategy_passes"] = 0
	state["turn_side"] = "B" if side_id == "A" else "A"
	return {"ok": true}


func _find_nurgling_victim(pm_side: String) -> Dictionary:
	var best := {}
	var best_d := 999.0
	for enemy in state["operatives"]:
		if enemy["incapacitated"] or str(enemy["side"]) == pm_side:
			continue
		var max_r := 7.0 if bool(enemy.get("poisoned", false)) else 3.0
		for pm in state["operatives"]:
			if pm["incapacitated"] or str(pm["side"]) != pm_side:
				continue
			var d := KTGeometry.edge_distance(pm["pos"], float(pm["base_radius"]), enemy["pos"], float(enemy["base_radius"]))
			if d <= max_r and d < best_d:
				best_d = d
				best = enemy
	return best


const PRIMARY_OP_NAMES := {"key": "关键行动", "kill": "击杀行动", "tac": "战术行动"}


## Rulebook p.106: in TP1, as a strategic gambit, each player secretly picks one of the three ops
## as their Primary Op. Revealed at the end of the battle for bonus VP (half of that op, rounded up).
func needs_primary_op(side_id: String) -> bool:
	return str(state.get("phase", "")) == "STRATEGY" and int(state["tp"]) == 1 and str(state["sides"][side_id]["primary_op"]).is_empty()


func choose_primary_op(side_id: String, op_id: String) -> Dictionary:
	if str(state["phase"]) != "STRATEGY" or str(state["turn_side"]) != side_id:
		return {"ok": false, "msg": "当前不是该方行动"}
	if not needs_primary_op(side_id):
		return {"ok": false, "msg": "主要行动 op 只能在第一转折点选择一次"}
	if not PRIMARY_OP_NAMES.has(op_id):
		return {"ok": false, "msg": "未知的行动 op"}
	push_undo()
	state["sides"][side_id]["primary_op"] = op_id
	add_log("🂠 【%s】秘密选定了主要行动 op（战斗结束时揭示）。" % str(state["sides"][side_id]["name"]))
	state["consecutive_strategy_passes"] = 0
	state["turn_side"] = "B" if side_id == "A" else "A"
	return {"ok": true}


## VP a side has scored from one op ("key" | "kill" | "tac").
func op_vp(side_id: String, op_id: String) -> int:
	return int(state["sides"][side_id].get("vp_" + op_id, 0))


## End-of-battle Primary Op bonus: half the VP scored from that op, rounded up.
func primary_bonus(side_id: String) -> int:
	var prim := str(state["sides"][side_id]["primary_op"])
	if prim.is_empty():
		return 0
	return int((op_vp(side_id, prim) + 1) / 2)


func pass_strategy(side_id: String) -> Dictionary:
	if str(state["phase"]) != "STRATEGY" or str(state["turn_side"]) != side_id:
		return {"ok": false, "msg": "当前不是该方行动"}
	if needs_primary_op(side_id):
		return {"ok": false, "msg": "第一转折点须先秘密选择主要行动 op（规则 p.106）"}
	push_undo()
	var sname := str(state["sides"][side_id]["name"])
	state["consecutive_strategy_passes"] = int(state["consecutive_strategy_passes"]) + 1
	if int(state["consecutive_strategy_passes"]) >= 2:
		state["phase"] = "FIREFIGHT"
		state["turn_side"] = str(state["initiative_side"])
		add_log("⏭️ 【%s】跳过战略计谋。双方准备就绪，正式进入 TP%d【交战阶段】！" % [sname, int(state["tp"])])
	else:
		state["turn_side"] = "B" if side_id == "A" else "A"
		add_log("⏭️ 【%s】跳过战略计谋。" % sname)
	return {"ok": true}


# ==============================================================================
# PHASE 3: FIREFIGHT PHASE (Activations, Movement, Actions, Counteract)
# ==============================================================================

func can_operative_be_selected_for_turn(op: Dictionary) -> Dictionary:
	if str(state["phase"]) != "FIREFIGHT":
		return {"ok": false, "counteract": false, "reason": "当前不在交战阶段"}
	if not str(state["active_op_uid"]).is_empty():
		return {"ok": false, "counteract": false, "reason": "已有正在激活的特工"}
	if op["incapacitated"] or str(op["side"]) != str(state["turn_side"]):
		return {"ok": false, "counteract": false, "reason": "轮到【%s】行动" % str(state["sides"][state["turn_side"]]["name"])}

	var my_ready := count_ready_operatives(str(op["side"]))
	var opp_side := "B" if str(op["side"]) == "A" else "A"
	var opp_ready := count_ready_operatives(opp_side)

	if bool(op["ready"]):
		return {"ok": true, "counteract": false, "reason": "点击激活该特工"}

	# Counteract check (反应): All friendly operatives expended, opponent still has ready operatives,
	# and Astartes allows Counteract regardless of Engage/Conceal order!
	if my_ready == 0 and opp_ready > 0 and not bool(op["counteracted_tp"]):
		return {"ok": true, "counteract": true, "reason": "可执行阿斯塔特【反应 Counteract】（免费 1AP 行动）"}

	return {"ok": false, "counteract": false, "reason": "该特工本转折点已行动完毕"}


func count_ready_operatives(side_id: String) -> int:
	var c := 0
	for op in state["operatives"]:
		if not op["incapacitated"] and str(op["side"]) == side_id and bool(op["ready"]):
			c += 1
	return c


func activate_operative(uid: String, chosen_order: String = "") -> Dictionary:
	var op := get_op(uid)
	if op.is_empty():
		return {"ok": false, "msg": "特工不存在"}
	var check := can_operative_be_selected_for_turn(op)
	if not bool(check["ok"]):
		return {"ok": false, "msg": str(check["reason"])}

	push_undo()
	var is_counteract := bool(check["counteract"])
	state["active_op_uid"] = uid
	state["active_is_counteract"] = is_counteract

	op["ignore_obscured"] = false # Sniper Optics expires on next activation start
	op["auspex_active"] = false
	op["actions_taken"] = []
	op["shoot_count"] = 0
	op["fight_count"] = 0
	op["bolt_or_psychic_shot"] = false
	op["heavy_sniper_shot"] = false
	op["psychic_weapons_used"] = []

	if is_counteract:
		op["counteracted_tp"] = true
		op["ap_left"] = 1
		add_log("⚡ 【%s】发起阿斯塔特【反应】（获得 1AP，限移动 2\" 或开火/近战）！" % str(op["name"]))
	else:
		if chosen_order in ["conceal", "engage"]:
			op["order"] = chosen_order
		var side_state: Dictionary = state["sides"][op["side"]]
		var eff_apl := int(op["apl_base"])
		if not KTCombat.has_chapter_tactic(side_state, op, "resolute"):
			eff_apl = maxi(1, eff_apl + int(op["apl_mod"]))
		op["ap_left"] = eff_apl

		# Poison tick on activation! 排毒口 (within 3" of an enemy Plague Marine): no token -> D3, gain one
		# on a 3; already poisoned -> D3 damage instead of 1.
		var vented := _ventilator_in_range(op)
		if vented and not bool(op["poisoned"]):
			if rng.randi_range(1, 3) == 3:
				op["poisoned"] = true
				add_log("☣️ 排毒口毒雾（D3 = 3）：【%s】获得毒素指示物！" % str(op["name"]))
		elif bool(op["poisoned"]):
			var pdmg := rng.randi_range(1, 3) if vented else 1
			op["wounds_cur"] = maxi(0, int(op["wounds_cur"]) - pdmg)
			add_log("☣️ 纳垢毒素发作%s！【%s】在激活开始时失去 %d 点耐伤（剩余 %d/%d）！" % [
				"（排毒口：D3）" if vented else "", str(op["name"]), pdmg, int(op["wounds_cur"]), int(op["wounds_max"])
			])
			if int(op["wounds_cur"]) <= 0:
				op["incapacitated"] = true
				op["ready"] = false
				state["active_op_uid"] = ""
				add_log("💀 【%s】被毒素侵蚀倒下！" % str(op["name"]))
				recalculate_objectives_and_kills()
				_advance_firefight_turn(str(op["side"]))
				return {"ok": true, "died_to_poison": true}

		var ord_cn := "隐匿" if str(op["order"]) == "conceal" else "交战"
		add_log("▶ 激活【%s】（%s命令 · %d AP）" % [str(op["name"]), ord_cn, int(op["ap_left"])])

	return {"ok": true, "counteract": is_counteract}


func set_active_order(order_str: String) -> void:
	var uid := str(state.get("active_op_uid", ""))
	if uid.is_empty() or bool(state.get("active_is_counteract", false)):
		return
	var op := get_op(uid)
	if not op.is_empty() and op["actions_taken"].is_empty():
		op["order"] = order_str
		add_log("🔄 【%s】将命令切换为【%s】" % [str(op["name"]), "隐匿" if order_str == "conceal" else "交战"])


func get_effective_move_inches(op: Dictionary, action_kind: String) -> float:
	if bool(state.get("active_is_counteract", false)):
		return 2.0 # KT24 Counteract move cap is 2"!
	if action_kind == "dash":
		return 3.0

	var side_state: Dictionary = state["sides"][op["side"]]
	var m := float(op["move_base"])
	if action_kind == "charge":
		m += 2.0

	var ignore_wounded: bool = bool(side_state["active_ploys"].has("他们无所畏惧")) or (str(side_state["team_id"]) == "CHAOS-PM" and bool(side_state.get("has_plague_bell", false)))
	var wounded: bool = (int(op["wounds_cur"]) * 2 < int(op["wounds_max"])) and not ignore_wounded
	if wounded:
		m -= 2.0
	elif KTCombat.is_contagion_affected(op, state):
		m -= 2.0

	return maxf(2.0, m)


func get_available_actions(op: Dictionary) -> Dictionary:
	var side_state: Dictionary = state["sides"][op["side"]]
	var ap := int(op["ap_left"])
	var taken: Array = op["actions_taken"]
	var engaged := not KTGeometry.is_in_enemy_control_range(op, op["pos"], state).is_empty()
	var mobile := KTCombat.has_chapter_tactic(side_state, op, "mobile")

	var move_blocked := taken.has("reposition") or taken.has("charge") or taken.has("fall_back")
	var dash_blocked := taken.has("dash") or taken.has("charge")

	var fb_cost := 1 if mobile else 2

	# Shooting legality (Astartes allows 2 shoots if at least 1 is Bolt/Psychic)
	var has_ranged := false
	var has_silent := false
	for w in op["weapons"]:
		if str(w["kind"]) == "ranged":
			has_ranged = true
			if bool(w["rules"].get("silent", false)):
				has_silent = true
	var can_shoot_order := (str(op["order"]) == "engage") or has_silent
	var shoot_cap := 1 if bool(state["active_is_counteract"]) else 2
	var can_shoot := has_ranged and ap >= 1 and int(op["shoot_count"]) < shoot_cap and can_shoot_order and not engaged and not taken.has("fall_back")
	var shoot_reason := "选择远程武器瞄准射击"
	if not has_ranged:
		shoot_reason = "该特工未装备远程武器"
	elif engaged:
		shoot_reason = "处于敌方 1\" 控制范围内，无法远程射击"
	elif not can_shoot_order:
		shoot_reason = "【隐匿】命令下无法开火（除非武器带有【安静】词条，或先切换为交战命令）"
	elif int(op["shoot_count"]) >= shoot_cap:
		shoot_reason = "已达本次激活射击次数上限"
	elif ap < 1:
		shoot_reason = "AP 不足"

	var fight_cap := 1 if bool(state["active_is_counteract"]) else 2
	var can_fight := ap >= 1 and int(op["fight_count"]) < fight_cap and engaged
	var fight_reason := "与控制范围内的敌方特工展开近战搏杀" if engaged else "需位于敌方 1\" 控制范围内才可近战"

	# Objective secure check (TP >= 2, within 1" of controlled objective, not engaged)
	var can_secure := false
	var secure_obj_id := ""
	if int(state["tp"]) >= 1 and ap >= 1 and not engaged and not taken.has("secure"):
		for obj in state["map"]["objectives"]:
			var oid := str(obj["id"])
			var op_pos: Vector2 = op["pos"]
			var obj_pos: Vector2 = obj["pos"]
			var d: float = op_pos.distance_to(obj_pos) - float(op["base_radius"])
			if d <= 1.787 and str(state["objectives_state"][oid]["controlled_by"]) == str(op["side"]) and str(state["objectives_state"][oid]["secured_by"]) != str(op["side"]):
				can_secure = true
				secure_obj_id = oid
				break

	return {
		"reposition": {
			"enabled": ap >= 1 and not move_blocked and not engaged,
			"cost": 1,
			"reason": "常规移动 %.0f\"" % get_effective_move_inches(op, "reposition") if not engaged else "被敌方近战锁定，请后撤"
		},
		"dash": {
			"enabled": ap >= 1 and not dash_blocked and not engaged and not bool(state["active_is_counteract"]),
			"cost": 1,
			"reason": "冲刺突进 3\"（可与转移组合推进）"
		},
		"charge": {
			"enabled": ap >= 1 and not move_blocked and not taken.has("dash") and (not engaged or mobile) and str(op["order"]) == "engage",
			"cost": 1,
			"reason": "冲锋 %.0f\" 突入敌方 1\" 控制圈！" % get_effective_move_inches(op, "charge") if str(op["order"]) == "engage" else "【隐匿】命令下不能发起冲锋"
		},
		"fall_back": {
			"enabled": ap >= fb_cost and not move_blocked and engaged,
			"cost": fb_cost,
			"reason": "消耗 %dAP 脱离敌方近战控制圈" % fb_cost
		},
		"shoot": {
			"enabled": can_shoot,
			"cost": 1,
			"reason": shoot_reason
		},
		"fight": {
			"enabled": can_fight,
			"cost": 1,
			"reason": fight_reason
		},
		"secure": {
			"enabled": can_secure,
			"cost": 1,
			"obj_id": secure_obj_id,
			"reason": "占领脚下已控制的目标信标 (+VP)" if can_secure else "需站在己方控制且未占领的目标信标 1\" 内"
		},
	}


func get_usable_ranged_weapons(op: Dictionary) -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	var is_conceal := str(op["order"]) == "conceal"
	var second_shot := int(op["shoot_count"]) >= 1
	var moved_non_dash := bool(op["moved_reposition_or_charge"])

	for w in op["weapons"]:
		if str(w["kind"]) != "ranged":
			continue
		var rules: Dictionary = w["rules"]
		if is_conceal and not bool(rules.get("silent", false)):
			continue
		if bool(rules.get("heavy", false)) and moved_non_dash:
			continue
		if second_shot:
			# Astartes double-shoot constraint!
			var wname := str(w["name"])
			var is_bolt_or_psy := wname.contains("爆矢") or bool(rules.get("psychic", false))
			if not bool(op["bolt_or_psychic_shot"]) and not is_bolt_or_psy:
				continue
			if bool(rules.get("psychic", false)) and op["psychic_weapons_used"].has(w["id"]):
				continue
			var is_heavy_bolt_sniper := wname.contains("爆矢狙击步枪") or wname.contains("重型爆矢枪")
			if bool(op["heavy_sniper_shot"]) and is_heavy_bolt_sniper and int(op["ap_left"]) < 2:
				continue
		out.append(w)
	return out


func execute_move(uid: String, action_kind: String, waypoints: Array[Vector2]) -> Dictionary:
	var op := get_op(uid)
	if op.is_empty() or str(state["active_op_uid"]) != uid:
		return {"ok": false, "msg": "该特工未处于当前激活"}
	var acts := get_available_actions(op)
	if not acts.has(action_kind) or not bool(acts[action_kind]["enabled"]):
		return {"ok": false, "msg": "当前无法执行该移动"}

	var side_state: Dictionary = state["sides"][op["side"]]
	var mobile := KTCombat.has_chapter_tactic(side_state, op, "mobile")
	var max_m := get_effective_move_inches(op, action_kind)
	var check := KTGeometry.validate_move_path(waypoints, op, action_kind, max_m, state, mobile)
	if not bool(check["ok"]):
		return {"ok": false, "msg": str(check["reason"])}

	push_undo()
	var cost_ap := int(acts[action_kind]["cost"])
	op["ap_left"] = int(op["ap_left"]) - cost_ap
	op["pos"] = waypoints[waypoints.size() - 1]
	op["moved_distance_tp"] = float(op["moved_distance_tp"]) + float(check["cost"])
	if action_kind in ["reposition", "charge", "fall_back"]:
		op["moved_reposition_or_charge"] = true
	if action_kind == "charge":
		op["charged_this_tp"] = true
	op["actions_taken"].append(action_kind)

	var label_map := {"reposition": "转移", "dash": "冲刺", "charge": "冲锋", "fall_back": "后撤"}
	add_log("🏃 【%s】执行【%s】推进 %.1f\"" % [str(op["name"]), label_map.get(action_kind, action_kind), float(check["cost"])])
	recalculate_objectives_and_kills()
	_check_auto_end_counteract(op)
	return {"ok": true}


func execute_secure_objective(uid: String) -> Dictionary:
	var op := get_op(uid)
	var acts := get_available_actions(op)
	if not bool(acts["secure"]["enabled"]):
		return {"ok": false, "msg": str(acts["secure"]["reason"])}
	push_undo()
	var oid := str(acts["secure"]["obj_id"])
	op["ap_left"] = int(op["ap_left"]) - 1
	op["actions_taken"].append("secure")
	state["objectives_state"][oid]["secured_by"] = str(op["side"])
	add_log("🚩 【%s】成功占领了目标点【%s】！" % [str(op["name"]), oid.to_upper()])
	recalculate_objectives_and_kills()
	_check_auto_end_counteract(op)
	return {"ok": true}


func get_unique_actions(op: Dictionary) -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	var ap := int(op["ap_left"])
	var engaged := not KTGeometry.is_in_enemy_control_range(op, op["pos"], state).is_empty()
	var oid := str(op["op_id"])

	if oid == "aod_sniper":
		out.append({
			"id": "aod_optics",
			"name": "瞄准 (1AP)",
			"enabled": ap >= 1 and not engaged and not bool(op["ignore_obscured"]),
			"desc": "开启歼灭者光学瞄具：本次激活射击无视敌方【重型遮挡】！"
		})
	elif oid == "pm_fighter":
		out.append({
			"id": "pm_flail",
			"name": "腐蚀连枷横扫 (1AP)",
			"enabled": ap >= 1 and str(op["order"]) == "engage" and int(op["fight_count"]) < 2,
			"desc": "对 2\" 内所有可见特工造成 D3+2 伤害，掷出 3 附带纳垢毒素！"
		})
	elif oid == "pm_plaguecaster":
		out.append({
			"id": "pm_miasma",
			"name": "有毒瘴气 (1AP·灵能)",
			"enabled": ap >= 1 and not engaged and not op["actions_taken"].has("pm_miasma"),
			"desc": "令 7\" 内可见最近敌军感染毒素；若已带毒则直接造成 3 点灵能腐蚀伤害！"
		})
		out.append({
			"id": "pm_vitality",
			"name": "腐烂活力 (1AP·灵能)",
			"enabled": ap >= 1 and not engaged and not bool(op["vitality_used_tp"]),
			"desc": "为 3\" 内受伤最重的友军掷 2D6 恢复耐伤（和为圣数 7 时怒回 7 血！）"
		})
	return out


func execute_unique_action(uid: String, ability_id: String) -> Dictionary:
	var op := get_op(uid)
	if op.is_empty():
		return {"ok": false, "msg": "无效特工"}
	push_undo()
	op["ap_left"] = int(op["ap_left"]) - 1
	op["actions_taken"].append(ability_id)

	if ability_id == "aod_optics":
		op["ignore_obscured"] = true
		add_log("🔭 【歼灭者狙击手】开启【瞄准】：本轮射击完全无视重型遮挡！")
	elif ability_id == "pm_flail":
		op["fight_count"] = int(op["fight_count"]) + 1
		var hit_any := 0
		for other in state["operatives"]:
			if other["incapacitated"] or other["uid"] == op["uid"]:
				continue
			var d := KTGeometry.edge_distance(op["pos"], float(op["base_radius"]), other["pos"], float(other["base_radius"]))
			if d <= 2.05:
				var d3 := rng.randi_range(1, 3)
				var dmg := d3 + 2
				other["wounds_cur"] = maxi(0, int(other["wounds_cur"]) - dmg)
				if d3 == 3 and str(other["team_id"]) != "CHAOS-PM":
					other["poisoned"] = true
				if int(other["wounds_cur"]) <= 0:
					other["incapacitated"] = true
				hit_any += 1
				add_log("⛓️ 腐蚀连枷横扫命中【%s】，造成 %d 点伤害！" % [str(other["name"]), dmg])
		if hit_any == 0:
			add_log("⛓️ 【瘟疫斗士】挥舞腐蚀连枷（2\" 内暂无目标）。")
	elif ability_id == "pm_miasma":
		var best := {}
		var best_d := 99.0
		var terrains: Array = state["map"]["terrains"]
		var z_me := KTGeometry.elevation_at(op["pos"], terrains)
		for enemy in state["operatives"]:
			if enemy["incapacitated"] or enemy["side"] == op["side"]:
				continue
			var d := KTGeometry.edge_distance(op["pos"], float(op["base_radius"]), enemy["pos"], float(enemy["base_radius"]))
			var z_e := KTGeometry.elevation_at(enemy["pos"], terrains)
			if d <= 7.05 and d < best_d and KTGeometry.has_visibility(op["pos"], float(op["base_radius"]), z_me, enemy["pos"], float(enemy["base_radius"]), z_e, terrains):
				best_d = d
				best = enemy
		if best.is_empty():
			op["ap_left"] = int(op["ap_left"]) + 1
			return {"ok": false, "msg": "7\" 内没有可见的敌方特工"}
		if bool(best["poisoned"]):
			best["wounds_cur"] = maxi(0, int(best["wounds_cur"]) - 3)
			if int(best["wounds_cur"]) <= 0:
				best["incapacitated"] = true
			add_log("☠️ 【恶瘟投放者】释放有毒瘴气：引爆【%s】体内毒素造成 3 点真实伤害！" % str(best["name"]))
		else:
			best["poisoned"] = true
			add_log("☣️ 【恶瘟投放者】释放有毒瘴气：【%s】感染纳垢毒素！" % str(best["name"]))
	elif ability_id == "pm_vitality":
		op["vitality_used_tp"] = true
		var ally_best := op
		var max_missing := int(op["wounds_max"]) - int(op["wounds_cur"])
		for ally in state["operatives"]:
			if ally["incapacitated"] or ally["side"] != op["side"]:
				continue
			var d := KTGeometry.edge_distance(op["pos"], float(op["base_radius"]), ally["pos"], float(ally["base_radius"]))
			var miss := int(ally["wounds_max"]) - int(ally["wounds_cur"])
			if d <= 3.05 and miss > max_missing:
				max_missing = miss
				ally_best = ally
		var r1 := rng.randi_range(1, 6)
		var r2 := rng.randi_range(1, 6)
		var heal := 7 if (r1 + r2 == 7) else maxi(r1, r2)
		var before := int(ally_best["wounds_cur"])
		ally_best["wounds_cur"] = mini(int(ally_best["wounds_max"]), before + heal)
		add_log("💚 腐烂活力 (掷出 %d+%d)：【%s】恢复 +%d 点耐伤！" % [r1, r2, str(ally_best["name"]), int(ally_best["wounds_cur"]) - before])

	recalculate_objectives_and_kills()
	_check_auto_end_counteract(op)
	return {"ok": true}


func commit_shooting_action_cost(attacker: Dictionary, weapon: Dictionary) -> void:
	var wname := str(weapon["name"])
	var extra_ap := 0
	var is_heavy_sniper := wname.contains("爆矢狙击步枪") or wname.contains("重型爆矢枪")
	if int(attacker["shoot_count"]) >= 1 and bool(attacker["heavy_sniper_shot"]) and is_heavy_sniper:
		extra_ap = 1
	attacker["ap_left"] = maxi(0, int(attacker["ap_left"]) - (1 + extra_ap))
	attacker["shoot_count"] = int(attacker["shoot_count"]) + 1
	attacker["actions_taken"].append("shoot")
	if wname.contains("爆矢") or bool(weapon["rules"].get("psychic", false)):
		attacker["bolt_or_psychic_shot"] = true
	if is_heavy_sniper:
		attacker["heavy_sniper_shot"] = true
	if bool(weapon["rules"].get("psychic", false)):
		attacker["psychic_weapons_used"].append(weapon["id"])
	if str(weapon.get("id", "")) == EQ_BLIGHT_GRENADE_ID:
		var gs: Dictionary = state["sides"][attacker["side"]]
		gs["blight_grenades_left"] = int(gs.get("blight_grenades_left", 0)) - 1
		add_log("💣 疫病手雷已使用（全队剩余 %d 次）。" % maxi(0, int(gs["blight_grenades_left"])))
		if int(gs["blight_grenades_left"]) <= 0:
			for o in state["operatives"]:
				if str(o["side"]) == str(attacker["side"]):
					o["weapons"] = (o["weapons"] as Array).filter(func(w): return str(w.get("id", "")) != EQ_BLIGHT_GRENADE_ID)


func commit_melee_action_cost(attacker: Dictionary) -> void:
	attacker["ap_left"] = maxi(0, int(attacker["ap_left"]) - 1)
	attacker["fight_count"] = int(attacker["fight_count"]) + 1
	attacker["actions_taken"].append("fight")


func use_firefight_ploy(side_id: String, ploy_id: String, context_op_uid: String = "") -> Dictionary:
	var side_state: Dictionary = state["sides"][side_id]
	if side_state["used_ploys_tp"].has(ploy_id):
		return {"ok": false, "msg": "本转折点已使用过该交战计谋"}

	var op := get_op(context_op_uid) if not context_op_uid.is_empty() else get_op(str(state.get("active_op_uid", "")))
	var cost := 1
	if ploy_id == "wrath_of_vengeance" and not op.is_empty() and str(op["order"]) == "engage" and KTCombat.has_equipment(side_state, "chapterreliquaries"):
		cost = 0 # 战团圣物
	elif not op.is_empty() and str(op["op_id"]) == "aod_captain" and not bool(side_state.get("heroic_used_tp", false)):
		cost = 0
		side_state["heroic_used_tp"] = true

	if int(side_state["cp"]) < cost:
		return {"ok": false, "msg": "CP 命令点不足"}

	push_undo()
	side_state["cp"] = int(side_state["cp"]) - cost
	side_state["used_ploys_tp"].append(ploy_id)

	if ploy_id == "shock_assault" and not op.is_empty():
		op["shock_assault_active"] = true
		add_log("⚡ 交战计谋《震慑突袭》生效：【%s】本次近战获得【震荡】且首次出击伤害 +1！" % str(op["name"]))
	elif ploy_id == "wrath_of_vengeance" and not op.is_empty():
		op["ap_left"] = int(op["ap_left"]) + 1
		add_log("🔥 交战计谋《复仇之怒》生效：【%s】获得额外 1AP 行动点数！" % str(op["name"]))
	elif ploy_id == "virulent_poison" and not op.is_empty():
		var target := _find_nurgling_victim(side_id)
		if not target.is_empty():
			target["poisoned"] = true
			add_log("☣️ 交战计谋《致命毒素》：【%s】远距感染纳垢毒素！" % str(target["name"]))
	elif ploy_id == "disgustingly_resilient" and not op.is_empty():
		op["auto_resilient_active"] = true
		add_log("🦠 交战计谋《恶心韧性》：【%s】本轮受击免掷骰必减 1 伤！" % str(op["name"]))

	return {"ok": true}


func _check_auto_end_counteract(op: Dictionary) -> void:
	if bool(state["active_is_counteract"]) and int(op["ap_left"]) <= 0:
		end_operative_activation()


func end_operative_activation() -> void:
	var uid := str(state.get("active_op_uid", ""))
	if uid.is_empty():
		return
	var op := get_op(uid)
	var is_counteract := bool(state["active_is_counteract"])
	if not op.is_empty():
		if not is_counteract:
			op["ready"] = false
			# Clear Stun APL mod at end of activation (respecting Audit Fix #4 skip flag!)
			if int(op["apl_mod"]) < 0:
				if bool(op["stun_skip_current_end"]):
					op["stun_skip_current_end"] = false
				else:
					op["apl_mod"] = 0
		op["shock_assault_active"] = false
		op["auto_resilient_active"] = false

	state["active_op_uid"] = ""
	state["active_is_counteract"] = false
	recalculate_objectives_and_kills()

	var just_acted_side := str(op["side"]) if not op.is_empty() else str(state["turn_side"])
	_advance_firefight_turn(just_acted_side)


func _advance_firefight_turn(just_acted_side: String) -> void:
	var other_side := "B" if just_acted_side == "A" else "A"
	var ready_a := count_ready_operatives("A")
	var ready_b := count_ready_operatives("B")

	if ready_a == 0 and ready_b == 0:
		_finish_turning_point()
		return

	# Check if other_side has ready operatives OR eligible counteract operatives
	if count_ready_operatives(other_side) > 0 or _has_counteract_available(other_side):
		state["turn_side"] = other_side
	elif count_ready_operatives(just_acted_side) > 0 or _has_counteract_available(just_acted_side):
		state["turn_side"] = just_acted_side
	else:
		_finish_turning_point()


func _has_counteract_available(side_id: String) -> bool:
	var opp := "B" if side_id == "A" else "A"
	if count_ready_operatives(side_id) > 0 or count_ready_operatives(opp) == 0:
		return false
	for op in state["operatives"]:
		if not op["incapacitated"] and str(op["side"]) == side_id and not bool(op["counteracted_tp"]):
			return true
	return false


func pass_counteract(side_id: String) -> void:
	for op in state["operatives"]:
		if str(op["side"]) == side_id:
			op["counteracted_tp"] = true
	add_log("⏭️ 【%s】放弃剩余反应机会。" % str(state["sides"][side_id]["name"]))
	_advance_firefight_turn(side_id)


func recalculate_objectives_and_kills() -> void:
	var terrains: Array = state["map"]["terrains"]
	for obj in state["map"]["objectives"]:
		var oid := str(obj["id"])
		var opos: Vector2 = obj["pos"]
		var z_obj := 0.0
		var apl_a := 0
		var apl_b := 0

		for op in state["operatives"]:
			if op["incapacitated"]:
				continue
			var op_pos2: Vector2 = op["pos"]
			var d: float = op_pos2.distance_to(opos) - float(op["base_radius"])
			var z_op := KTGeometry.elevation_at(op["pos"], terrains)
			if d <= 1.787 and absf(z_op - z_obj) <= 2.0:
				var side_state: Dictionary = state["sides"][op["side"]]
				var eff := int(op["apl_base"])
				if not KTCombat.has_chapter_tactic(side_state, op, "resolute"):
					eff = maxi(1, eff + int(op["apl_mod"]))
				if str(op["op_id"]) == "pm_icon_bearer":
					eff += 1 # Icon Bearer +1 virtual APL on objectives!
				if str(op["side"]) == "A":
					apl_a += eff
				else:
					apl_b += eff

		state["objectives_state"][oid]["apl_a"] = apl_a
		state["objectives_state"][oid]["apl_b"] = apl_b
		if apl_a > apl_b:
			state["objectives_state"][oid]["controlled_by"] = "A"
		elif apl_b > apl_a:
			state["objectives_state"][oid]["controlled_by"] = "B"
		else:
			state["objectives_state"][oid]["controlled_by"] = ""

	# Kill Op (rulebook p.107): each new Kill Grade is worth 1 VP; grade thresholds depend on how
	# many operatives the ENEMY started with. Track starting counts once.
	# start_counts is written once by _begin_deploy(); before that there are no operatives yet.
	if not state.has("start_counts"):
		return
	var dead_a := 0
	var dead_b := 0
	for op in state["operatives"]:
		if op["incapacitated"]:
			if str(op["side"]) == "A":
				dead_a += 1
			else:
				dead_b += 1
	# kill_bonus = the end-of-battle "+1 VP if your Kill Grade is higher" (set in _finish_turning_point)
	state["sides"]["A"]["vp_kill"] = kill_op_vp(dead_b, int(state["start_counts"]["B"])) + int(state["sides"]["A"].get("kill_bonus", 0))
	state["sides"]["B"]["vp_kill"] = kill_op_vp(dead_a, int(state["start_counts"]["A"])) + int(state["sides"]["B"].get("kill_bonus", 0))

	# Wipeout does NOT end the battle (p.107 "5.结束战斗"): the surviving side plays out the
	# remaining turning points. Only the 4th TP ends the game.
	if dead_a >= int(state["start_counts"]["A"]) or dead_b >= int(state["start_counts"]["B"]):
		if not bool(state.get("wipeout_logged", false)):
			state["wipeout_logged"] = true
			add_log("💀 一方小队已被打光——战斗仍将继续至四个转折点结束。")


## Kill Op VP for `kills` enemy operatives down, given the enemy's starting count (rulebook p.107 table).
## Thresholds are the cumulative kills needed to reach Kill Grade 1..5.
const KILL_GRADE_TABLE := {
	5:  [1, 2, 3, 4, 5],
	6:  [1, 2, 4, 5, 6],
	7:  [1, 3, 4, 6, 7],
	8:  [2, 3, 5, 6, 8],
	9:  [2, 4, 5, 7, 9],
	10: [2, 4, 6, 8, 10],
	11: [2, 4, 7, 9, 11],
	12: [2, 5, 7, 10, 12],
	13: [3, 5, 8, 10, 13],
	14: [3, 6, 8, 11, 14],
}


static func kill_op_vp(kills: int, enemy_start: int) -> int:
	var row: Array = KILL_GRADE_TABLE.get(clampi(enemy_start, 5, 14), KILL_GRADE_TABLE[6])
	var vp := 0
	for t in row:
		if kills >= int(t):
			vp += 1
	return vp


## Kill Grade 0..5 for the given kill count (used by the end-of-battle +1 VP comparison).
static func kill_op_grade(kills: int, enemy_start: int) -> int:
	var row: Array = KILL_GRADE_TABLE.get(clampi(enemy_start, 5, 14), KILL_GRADE_TABLE[6])
	var g := 0
	for t in row:
		if kills >= int(t):
			g += 1
	return g


func _score_key_op_end_of_tp() -> void:
	# Approved Ops 2025 Crit Op "Secure (占领)" scoring from TP2..TP4 (and TP1 preview bonus if secured):
	if int(state["tp"]) < 2 and str(state["phase"]) != "GAME_OVER":
		return
	var sec_a := 0
	var sec_b := 0
	var ctrl_a := 0
	var ctrl_b := 0
	for oid in state["objectives_state"].keys():
		var st: Dictionary = state["objectives_state"][oid]
		if str(st["secured_by"]) == "A":
			sec_a += 1
		elif str(st["secured_by"]) == "B":
			sec_b += 1
		if str(st["controlled_by"]) == "A":
			ctrl_a += 1
		elif str(st["controlled_by"]) == "B":
			ctrl_b += 1

	var gain_a := sec_a + (1 if ctrl_a > ctrl_b else 0)
	var gain_b := sec_b + (1 if ctrl_b > ctrl_a else 0)
	if gain_a > 0:
		state["sides"]["A"]["vp_key"] = mini(6, int(state["sides"]["A"]["vp_key"]) + gain_a)
		add_log("🏅 【死亡天使】关键行动计分 +%d VP！" % gain_a)
	if gain_b > 0:
		state["sides"]["B"]["vp_key"] = mini(6, int(state["sides"]["B"]["vp_key"]) + gain_b)
		add_log("🏅 【瘟疫战士】关键行动计分 +%d VP！" % gain_b)


func _finish_turning_point() -> void:
	recalculate_objectives_and_kills()
	_score_key_op_end_of_tp()
	var tp := int(state["tp"])
	if tp >= 4:
		# End of battle Kill Grade superiority +1 VP & Primary Op bonus!
		var dead_kills: Dictionary = {"A": 0, "B": 0}
		for op in state["operatives"]:
			if op["incapacitated"]:
				dead_kills[str(op["side"])] = int(dead_kills[str(op["side"])]) + 1
		var grade_a := kill_op_grade(int(dead_kills["B"]), int(state["start_counts"]["B"]))
		var grade_b := kill_op_grade(int(dead_kills["A"]), int(state["start_counts"]["A"]))
		if grade_a > grade_b:
			state["sides"]["A"]["kill_bonus"] = 1
			add_log("🏅 【死亡天使】击杀等级 %d 高于对手，+1 VP！" % grade_a)
		elif grade_b > grade_a:
			state["sides"]["B"]["kill_bonus"] = 1
			add_log("🏅 【瘟疫战士】击杀等级 %d 高于对手，+1 VP！" % grade_b)
		recalculate_objectives_and_kills()
		state["phase"] = "GAME_OVER"
		add_log("🏁 第 4 转折点结束！全场战斗结算完成！")
	else:
		state["tp"] = tp + 1
		state["phase"] = "INITIATIVE"
		add_log("🔔 第 %d 转折点结束！请投掷第 %d 转折点先手权骰。" % [tp, tp + 1])


func get_total_vp(side_id: String) -> int:
	var s: Dictionary = state["sides"][side_id]
	var base_vp := int(s["vp_key"]) + int(s["vp_kill"]) + int(s["vp_tac"])
	if str(state["phase"]) == "GAME_OVER":
		base_vp += primary_bonus(side_id)
	return base_vp
