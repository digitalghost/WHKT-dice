class_name KTCombat
extends RefCounted

## Interactive & deterministic Shooting (9-rule pipeline) and Melee (Alternating Strike/Parry Duel)
## resolver with full Angels of Death (IMP-AOD) and Plague Marines (CHAOS-PM) hooks.


## Structured rule-trigger events, appended alongside `resolution_log` strings so the UI can play a
## timeline (one beat per rule) and highlight the dice each rule touched.
## ev = { "rule": "严重", "text": "...", "pool": "atk_dice"|"def_dice"|"", "dice": [idx...], "kind": "upgrade|convert|reroll|block|damage|status|info" }
static func _log_event(session: Dictionary, rule: String, text: String, pool: String = "", dice: Array = [], kind: String = "info") -> void:
	(session["resolution_log"] as Array).append(text)
	if not session.has("rule_events"):
		session["rule_events"] = []
	session["rule_events"].append({"rule": rule, "text": text, "pool": pool, "dice": dice, "kind": kind})


static func _roll_d6(rng: RandomNumberGenerator) -> int:
	return rng.randi_range(1, 6)


static func has_chapter_tactic(side_state: Dictionary, op: Dictionary, tactic_id: String) -> bool:
	if str(side_state.get("team_id", "")) != "IMP-AOD":
		return false
	if str(side_state.get("primary_tactic", "")) == tactic_id:
		return true
	if str(side_state.get("secondary_tactic", "")) == tactic_id:
		return true
	if str(side_state.get("temp_tactic", "")) == tactic_id:
		return true
	if tactic_id == "stealthy" and bool(op.get("has_innate_stealthy", false)):
		return true
	return false


static func is_bolt_weapon(weapon_name: String) -> bool:
	return weapon_name.contains("爆矢")


static func is_contagion_affected(target: Dictionary, state: Dictionary) -> bool:
	# Plague Marines Strategic Ploy: 传染 (Contagion)
	var pm_side := ""
	for sid in ["A", "B"]:
		if str(state["sides"][sid]["team_id"]) == "CHAOS-PM" and state["sides"][sid]["active_ploys"].has("传染"):
			pm_side = sid
			break
	if pm_side == "" or str(target["side"]) == pm_side:
		return false

	var terrains: Array = state["map"]["terrains"]
	var z_t := KTGeometry.elevation_at(target["pos"], terrains)
	var poisoned := bool(target.get("poisoned", false))

	for op in state["operatives"]:
		if op["incapacitated"] or not bool(op.get("deployed", true)) or str(op["side"]) != pm_side:
			continue
		var z_o := KTGeometry.elevation_at(op["pos"], terrains)
		var d := KTGeometry.edge_distance_3d(op["pos"], float(op["base_radius"]), z_o, target["pos"], float(target["base_radius"]), z_t)
		if d > 3.05:
			continue
		var is_icon := str(op["op_id"]) == "pm_icon_bearer"
		if poisoned or is_icon:
			if KTGeometry.has_visibility(op["pos"], float(op["base_radius"]), z_o, target["pos"], float(target["base_radius"]), z_t, terrains):
				return true
	return false


## Has this side taken the faction equipment `key` (suffix of the rule entry id, e.g. "purityseals")?
static func has_equipment(side_state: Dictionary, key: String) -> bool:
	return (side_state.get("equipment", []) as Array).has("%s-EQ-%s" % [str(side_state.get("team_id", "")), key])


static func create_shooting_session(
	attacker: Dictionary,
	defender: Dictionary,
	weapon: Dictionary,
	shot_eval: Dictionary,
	state: Dictionary,
	rng: RandomNumberGenerator,
	populate_secondary: bool = true
) -> Dictionary:
	var att_side: Dictionary = state["sides"][attacker["side"]]
	var def_side: Dictionary = state["sides"][defender["side"]]
	var w_rules: Dictionary = weapon.get("rules", {}).duplicate(true)
	var mod_tags: Array[String] = []

	# 占卜仪 (once per TP): enemies within 8" cannot be obscured until the end of this activation
	if bool(shot_eval.get("auspex", false)):
		if not bool(attacker.get("auspex_active", false)):
			attacker["auspex_active"] = true
			att_side["auspex_used_tp"] = true
		mod_tags.append("占卜仪: 8\" 内目标不可被遮挡")

	# 烟雾手雷: shooting an operative wholly within smoke reduces Piercing 2 / Piercing Crits 2 to 1
	if bool(shot_eval.get("smoke", false)):
		var smoke_pierce_reduced := false
		if int(w_rules.get("piercing", 0)) == 2:
			w_rules["piercing"] = 1
			smoke_pierce_reduced = true
		if int(w_rules.get("piercing_crits", 0)) == 2:
			w_rules["piercing_crits"] = 1
			smoke_pierce_reduced = true
		if smoke_pierce_reduced:
			mod_tags.append("烟雾: 穿刺 2→1")

	# --- 1. Gather Faction / Ploy / Passive weapon rule modifiers ---
	var ignore_wounded_att: bool = bool(att_side["active_ploys"].has("他们无所畏惧")) or (str(att_side["team_id"]) == "CHAOS-PM" and bool(att_side.get("has_plague_bell", false)))
	var att_wounded: bool = (int(attacker["wounds_cur"]) * 2 < int(attacker["wounds_max"])) and not ignore_wounded_att

	var hit_delta: int = 0
	if att_wounded:
		hit_delta += 1
		mod_tags.append("受创: 命中恶化+1")
	elif is_contagion_affected(attacker, state):
		hit_delta += 1
		mod_tags.append("传染毒雾: 命中恶化+1")

	# Grenadier ability (+1 hit improvement -> -1 target number):
	# Specialists' own datacard grenades (aod_grenadier_*, pm_bombardier_*) ALREADY bake 4+ -> 3+ in team JSON;
	# only apply -1 if a raw 4+ eq_* equipment grenade is somehow used.
	if (str(attacker["op_id"]) in ["aod_grenadier", "pm_bombardier"]) and str(weapon.get("id", "")).begins_with("eq_") and str(weapon["name"]).contains("手雷"):
		hit_delta -= 1
		mod_tags.append("掷弹兵专精: 命中改善-1")

	# Single final clamp(2, 6) per audit fix #2!
	var effective_hit := clampi(int(weapon["hit"]) + hit_delta, 2, 6)

	var normal_dmg := int(weapon["normal_dmg"])
	var crit_dmg := int(weapon["crit_dmg"])
	if bool(w_rules.get("toxic", false)) and bool(defender.get("poisoned", false)):
		normal_dmg += 1
		crit_dmg += 1
		mod_tags.append("剧毒触发: 伤害 +1/+1")

	# Angels of Death: Sharpshooter (神射手)
	var moved_non_dash := bool(attacker.get("moved_reposition_or_charge", false))
	if has_chapter_tactic(att_side, attacker, "sharpshooter") and not moved_non_dash and is_bolt_weapon(str(weapon["name"])):
		w_rules["accurate"] = int(w_rules.get("accurate", 0)) + 1
		w_rules["severe"] = true
		mod_tags.append("神射手: 精准+1 & 严重")

	# Angels of Death: Combat Doctrine (战斗条令)
	var dist := float(shot_eval.get("distance", 10.0))
	var doctrine := str(att_side.get("combat_doctrine", ""))
	if doctrine == "devastator" and dist > 6.0:
		w_rules["balanced"] = true
		mod_tags.append("毁灭条令(>6\"): 平衡")
	elif doctrine == "tactical" and dist <= 6.0:
		w_rules["balanced"] = true
		mod_tags.append("战术条令(≤6\"): 平衡")

	# Plague Marines: Plodding Death / Lumbering Death (慢步死亡 — per-activation move <= 3", CHAOS-PM card)
	var att_moved_act: float = float(attacker.get("moved_distance_activation", attacker.get("moved_distance_tp", 0.0)))
	if att_side["active_ploys"].has("慢步死亡") and att_moved_act <= 3.05:
		w_rules["ceaseless"] = true
		mod_tags.append("慢步死亡(移动≤3\"): 无休")

	# Accurate total (Weapon + Vantage)
	var total_accurate := mini(2, int(w_rules.get("accurate", 0)) + int(shot_eval.get("vantage_accurate", 0)))
	if int(shot_eval.get("vantage_accurate", 0)) > 0:
		mod_tags.append("制高点加成: 精准 +%d" % int(shot_eval["vantage_accurate"]))

	var total_attacks := int(weapon["attacks"])
	var auto_normals := mini(total_attacks, total_accurate)
	var to_roll := maxi(0, total_attacks - auto_normals)

	var atk_dice: Array[Dictionary] = []
	for i in range(to_roll):
		atk_dice.append({
			"id": i,
			"val": _roll_d6(rng),
			"rerolled": false,
			"locked": false,
		})

	# Defender traits
	var def_save := int(defender["save"])
	if bool(shot_eval.get("protective", false)):
		def_save = maxi(2, def_save - 1)
		mod_tags.append("保护: 豁免+1")

	var def_crit_save := 6
	if has_chapter_tactic(def_side, defender, "hardy") or str(defender["op_id"]) == "pm_warrior":
		def_crit_save = 5
		mod_tags.append("防守方 5+ 暴击豁免")

	var cover_mode := str(shot_eval.get("cover", "none"))
	# IMP-AOD Stealthy (隐匿) & Sniper Camo Cloak (迷彩斗篷):
	# - Stealthy: on standard cover, choose 2 normal saves OR 1 crit save (does not stack with Vantage's improved cover,
	#   which already grants 2 normal OR 1 crit; DOES apply against Vantage Engage shots where cover_mode == "standard"!).
	# - Camo Cloak + team Stealthy: retains BOTH options simultaneously (1 crit + 1 normal cover save).
	var team_stealthy: bool = str(def_side.get("team_id", "")) == "IMP-AOD" and (
		str(def_side.get("primary_tactic", "")) == "stealthy"
		or str(def_side.get("secondary_tactic", "")) == "stealthy"
		or str(def_side.get("temp_tactic", "")) == "stealthy"
	)
	var camo_double: bool = bool(defender.get("has_innate_stealthy", false)) and team_stealthy
	var stealthy := has_chapter_tactic(def_side, defender, "stealthy")
	if camo_double and cover_mode in ["standard", "improved"]:
		cover_mode = "camo_double"
		mod_tags.append("迷彩斗篷+隐匿: 1暴+1普掩护豁免")
	elif stealthy and cover_mode == "standard":
		cover_mode = "stealth_choice"
		mod_tags.append("隐匿战术: 可选2普或1暴掩护豁免")

	var ammo_reroll: bool = int(attacker.get("ammo_resupply_tp", 0)) == int(state.get("tp", 1)) and not str(weapon.get("id", "")).begins_with("eq_")
	if ammo_reroll:
		mod_tags.append("弹药储备: 可重掷 1 枚攻击骰")

	var pending_targets: Array[Dictionary] = []
	var multi_mode: String = ""
	if populate_secondary:
		var blast_x: float = float(w_rules.get("blast", 0.0))
		var torrent_x: float = float(w_rules.get("torrent", 0.0))
		if blast_x > 0.01:
			multi_mode = "blast"
			pending_targets = KTGeometry.get_blast_secondary_targets(attacker, defender, weapon, shot_eval, state)
		elif torrent_x > 0.01:
			multi_mode = "torrent"
			pending_targets = KTGeometry.get_torrent_secondary_targets(attacker, defender, weapon, state)

	var session := {
		"kind": "shooting",
		"step": "attack_reroll", # "attack_reroll" -> "defence_roll" -> "resolved"
		"attacker_uid": attacker["uid"],
		"defender_uid": defender["uid"],
		"attacker_side": attacker["side"],
		"defender_side": defender["side"],
		"weapon": weapon,
		"rules": w_rules,
		"effective_hit": effective_hit,
		"worst_effective_hit": effective_hit,
		"lethal_thresh": int(w_rules.get("lethal", 6)),
		"normal_dmg": normal_dmg,
		"crit_dmg": crit_dmg,
		"obscured": bool(shot_eval.get("obscured", false)),
		"cover_mode": cover_mode,
		"improved_cover_choice": "2norm", # "2norm" | "1crit" (Rulebook p.36 L1528-1529)
		"def_total": 3,
		"def_save": def_save,
		"def_crit_save": def_crit_save,
		"auto_attack_normals": auto_normals,
		"atk_dice": atk_dice,
		"def_dice": [],
		"auto_def_normals": 0,
		"auto_def_crits": 0,
		"balanced_used": false,
		"ceaseless_used": false,
		"ammo_reroll": ammo_reroll,
		"ammo_reroll_used": false,
		"purity_seal_used": false,
		"transhuman_used": false,
		"indomitus_used": false,
		"curse_of_rot_used": false,
		"mod_tags": mod_tags,
		"retained_atk": {"crits": 0, "normals": 0, "devastating_mw": 0},
		"resolution_log": [],
		"total_damage_dealt": 0,
		"fnp_rolls": [],
		"hot_self_dmg": 0,
		"hot_resolved": false,
		"multi_mode": multi_mode, # "" | "blast" | "torrent"
		"multi_index": 1,
		"multi_total": 1 + pending_targets.size(),
		"pending_targets": pending_targets,
	}
	return session


static func reroll_shooting_die(session: Dictionary, pool_key: String, die_idx: int, mode: String, state: Dictionary, rng: RandomNumberGenerator) -> Dictionary:
	var dice: Array = session[pool_key]
	if die_idx < 0 or die_idx >= dice.size():
		return {"ok": false, "msg": "无效骰子"}
	var d: Dictionary = dice[die_idx]
	if bool(d.get("rerolled", false)) or bool(d.get("locked", false)):
		return {"ok": false, "msg": "同一枚骰子每场对抗最多只能重掷一次！"}

	var side_id := str(session["attacker_side"]) if pool_key == "atk_dice" else str(session["defender_side"])
	var side_state: Dictionary = state["sides"][side_id]

	if mode == "cp":
		if int(side_state["cp"]) < 1:
			return {"ok": false, "msg": "CP 命令点不足（需要 1CP）"}
		side_state["cp"] = int(side_state["cp"]) - 1
		var old_v := int(d["val"])
		d["val"] = _roll_d6(rng)
		d["rerolled"] = true
		_log_event(session, "指挥重掷", "⚡ 指挥重掷 (1CP)：%d → %d" % [old_v, int(d["val"])], "", [], "reroll")
		return {"ok": true, "msg": "指挥重掷完成"}

	if mode == "balanced" and pool_key == "atk_dice":
		if not bool(session["rules"].get("balanced", false)) or bool(session["balanced_used"]):
			return {"ok": false, "msg": "当前不可使用【平衡】重掷"}
		var old_b := int(d["val"])
		d["val"] = _roll_d6(rng)
		d["rerolled"] = true
		session["balanced_used"] = true
		_log_event(session, "平衡", "⚖️ 平衡重掷：%d → %d" % [old_b, int(d["val"])], "", [], "reroll")
		return {"ok": true, "msg": "平衡重掷完成"}

	if mode == "ammo" and pool_key == "atk_dice":
		if not bool(session.get("ammo_reroll", false)) or bool(session.get("ammo_reroll_used", false)):
			return {"ok": false, "msg": "当前不可使用【弹药储备】重掷"}
		var old_a := int(d["val"])
		d["val"] = _roll_d6(rng)
		d["rerolled"] = true
		session["ammo_reroll_used"] = true
		_log_event(session, "弹药储备", "📦 弹药储备重掷：%d → %d" % [old_a, int(d["val"])], "", [], "reroll")
		return {"ok": true, "msg": "弹药储备重掷完成"}

	if mode == "ceaseless" and pool_key == "atk_dice":
		if not bool(session["rules"].get("ceaseless", false)) or bool(session["ceaseless_used"]):
			return {"ok": false, "msg": "当前不可使用【无休】重掷"}
		var target_face := int(d["val"])
		var changed := 0
		for item in dice:
			if int(item["val"]) == target_face and not bool(item["rerolled"]) and not bool(item["locked"]):
				item["val"] = _roll_d6(rng)
				item["rerolled"] = true
				changed += 1
		session["ceaseless_used"] = true
		_log_event(session, "无休", "♻️ 无休重掷全部点数 %d（共 %d 枚）" % [target_face, changed], "", [], "reroll")
		return {"ok": true, "msg": "无休重掷完成"}

	return {"ok": false, "msg": "未知重掷类型"}


static func lock_in_shooting_attacks(session: Dictionary, state: Dictionary, rng: RandomNumberGenerator) -> void:
	## Evaluates Attack Retention (Lethal, Severe, Rending, Punishing isolated, Obscured, Devastating)
	## and rolls Defence Dice automatically with Piercing & Cover saves!
	var hit_req := int(session["effective_hit"])
	var lethal_req := int(session["lethal_thresh"])
	var obscured := bool(session["obscured"])
	var rules: Dictionary = session["rules"]

	var natural_crits: int = 0
	var rolled_normals: int = 0
	var failures: int = 0

	for d in session["atk_dice"]:
		var v := int(d["val"])
		if bool(d.get("locked", false)):
			# Curse of Rot: locked 3s cannot be retained as successes (so exclude from Purity Seals / Punishing!)
			continue
		elif v == 1:
			failures += 1
		elif v == 6 or (v >= hit_req and v >= lethal_req):
			# Rulebook p.93 & v11.5 §2.1: Lethal X+ only upgrades a SUCCESSFUL hit (natural 6 is always a crit).
			natural_crits += 1
		elif v >= hit_req:
			rolled_normals += 1
		else:
			failures += 1

	var retained_from_fail := int(session["auto_attack_normals"]) # Accurate + Punishing go here (cannot be Rended!)

	# Angels of Death Equipment: Purity Seals (纯洁印章) — 2+ fails -> discard 1, retain 1 normal
	var att_side: Dictionary = state["sides"][session["attacker_side"]]
	if has_equipment(att_side, "purityseals") and not bool(att_side.get("purity_used_tp", false)) and failures >= 2:
		failures -= 2
		retained_from_fail += 1
		att_side["purity_used_tp"] = true
		_log_event(session, "纯洁印章", "📜 纯洁印章自动生效：舍弃 2 枚失败骰，保底转化 1 枚普通命中！", "", [], "convert")

	if obscured:
		# KT3 Obscured: All crits become normals; Severe/Rending/Punishing cannot trigger; discard 1 success!
		var total_norm := natural_crits + rolled_normals + retained_from_fail
		natural_crits = 0
		if total_norm > 0:
			total_norm -= 1
			_log_event(session, "重型遮挡", "🌫️ 重型遮挡生效：暴击全部降为普通成功，并额外舍弃 1 枚命中！", "", [], "block")
		session["retained_atk"] = {"crits": 0, "normals": total_norm, "devastating_mw": 0}
	else:
		# Severe: if 0 crits and >=1 retained normal (rolled or auto-retained), upgrade 1 normal -> crit (disables Punishing/Rending)
		var used_severe := false
		if bool(rules.get("severe", false)) and natural_crits == 0 and (rolled_normals + retained_from_fail) > 0:
			if rolled_normals > 0:
				rolled_normals -= 1
			else:
				retained_from_fail -= 1
			natural_crits += 1
			used_severe = true
			_log_event(session, "严重", "🔥 【严重】词条触发：1 枚普通命中升格为暴击！", "", [], "upgrade")

		if not used_severe:
			# Punishing: if >=1 crit and >=1 fail, convert 1 fail -> retained_from_fail (Audit Fix #3: cannot chain into Rending!)
			if bool(rules.get("punishing", false)) and natural_crits > 0 and failures > 0:
				failures -= 1
				retained_from_fail += 1
				_log_event(session, "重击", "🔨 【重击】词条触发：1 枚失败转化为普通命中！", "", [], "convert")
			# Rending: if >=1 crit and >=1 rolled_normal, upgrade 1 rolled_normal -> crit!
			if bool(rules.get("rending", false)) and natural_crits > 0 and rolled_normals > 0:
				rolled_normals -= 1
				natural_crits += 1
				_log_event(session, "撕裂", "⚡ 【撕裂】词条触发：1 枚自然普通命中升格为暴击！", "", [], "upgrade")

		var dev_x := int(rules.get("devastating", 0))
		var dev_mw := natural_crits * dev_x
		if dev_mw > 0:
			_log_event(session, "毁灭", "💥 【毁灭 %d】爆发：立即造成 %d 点致命伤害！" % [dev_x, dev_mw], "", [], "damage")

		session["retained_atk"] = {
			"crits": natural_crits,
			"normals": rolled_normals + retained_from_fail,
			"devastating_mw": dev_mw,
		}

	# --- Prepare & Roll Defence Pool ---
	var piercing := int(rules.get("piercing", 0))
	if int(session["retained_atk"]["crits"]) > 0:
		piercing = maxi(piercing, int(rules.get("piercing_crits", 0)))
	if piercing > 0:
		_log_event(session, "穿刺", "🎯 穿甲生效：防守方减少 %d 枚防御骰！" % piercing, "", [], "block")

	var def_total := maxi(0, 3 - piercing)
	session["def_total"] = def_total
	var auto_def_n := 0
	var auto_def_c := 0
	var cm := str(session["cover_mode"])
	if cm == "standard" and def_total > 0:
		auto_def_n = 1
	elif cm in ["improved", "stealth_choice"] and def_total > 0:
		# Rulebook p.36 L1528-1529 & IMP-AOD Stealthy: Defender chooses 1 Crit save OR 2 Normal saves.
		if str(session.get("improved_cover_choice", "2norm")) == "1crit":
			auto_def_c = 1
		else:
			auto_def_n = mini(2, def_total)
	elif cm == "camo_double" and def_total > 0:
		# IMP-AOD Sniper Camo Cloak + team Stealthy: retain BOTH options (1 Crit + 1 Normal cover save)
		auto_def_c = 1
		auto_def_n = 1 if def_total >= 2 else 0
	elif cm == "stealth_upgraded" and def_total > 0:
		auto_def_c = 1

	var def_to_roll := maxi(0, def_total - auto_def_n - auto_def_c)
	var def_dice: Array[Dictionary] = []
	for i in range(def_to_roll):
		def_dice.append({
			"id": i,
			"val": _roll_d6(rng),
			"rerolled": false,
			"locked": false,
		})

	session["auto_def_normals"] = auto_def_n
	session["auto_def_crits"] = auto_def_c
	session["def_dice"] = def_dice
	session["step"] = "defence_roll"


## Rulebook p.36 L1528-1529 & IMP-AOD Stealthy: Switch Improved / Stealthy Cover save between "2norm" and "1crit"
## during defence_roll before any defence die reroll / Transhuman is used.
static func set_improved_cover_choice(session: Dictionary, choice: String, rng: RandomNumberGenerator) -> bool:
	if str(session.get("step", "")) != "defence_roll" or not (str(session.get("cover_mode", "")) in ["improved", "stealth_choice"]):
		return false
	if not (choice in ["2norm", "1crit"]):
		return false
	if str(session.get("improved_cover_choice", "2norm")) == choice:
		return true
	if bool(session.get("transhuman_used", false)):
		return false
	var cur_dice: Array = session.get("def_dice", [])
	for d_v in cur_dice:
		var dd: Dictionary = d_v
		if bool(dd.get("rerolled", false)) or bool(dd.get("locked", false)):
			return false
	var def_total: int = int(session.get("def_total", 3))
	if def_total <= 0:
		return false
	session["improved_cover_choice"] = choice
	var auto_n: int = 0
	var auto_c: int = 0
	if choice == "1crit":
		auto_c = 1
	else:
		auto_n = mini(2, def_total)
	var want_roll: int = maxi(0, def_total - auto_n - auto_c)
	while cur_dice.size() > want_roll:
		cur_dice.pop_back()
	while cur_dice.size() < want_roll:
		cur_dice.append({
			"id": cur_dice.size(),
			"val": _roll_d6(rng),
			"rerolled": false,
			"locked": false,
		})
	session["auto_def_normals"] = auto_n
	session["auto_def_crits"] = auto_c
	return true


## CHAOS-PM Firefight Ploy: Curse of Rot (腐烂诅咒, 1CP, 1/TP)
## When a friendly PM is shooting against or shot by an enemy within 3" (or 7" if poisoned),
## after opponent rolls attack/defence dice: each rolled 3 inflicts 1 damage on that enemy and
## cannot be retained as a success or rerolled.
static func can_use_curse_of_rot(session: Dictionary, pool_key: String, state: Dictionary) -> Dictionary:
	if str(session.get("kind", "")) != "shooting" or bool(session.get("curse_of_rot_used", false)):
		return {"ok": false, "threes": 0, "pm_side": ""}
	var att := _find_op(state, str(session.get("attacker_uid", "")))
	var def := _find_op(state, str(session.get("defender_uid", "")))
	if att.is_empty() or def.is_empty():
		return {"ok": false, "threes": 0, "pm_side": ""}
	# pool_key == "atk_dice": opponent is attacker, PM is defender
	# pool_key == "def_dice": opponent is defender, PM is attacker
	var pm_op: Dictionary = def if pool_key == "atk_dice" else att
	var enemy_op: Dictionary = att if pool_key == "atk_dice" else def
	if bool(pm_op.get("incapacitated", false)) or bool(enemy_op.get("incapacitated", false)):
		return {"ok": false, "threes": 0, "pm_side": ""}
	var pm_side_id := str(pm_op["side"])
	var pm_side: Dictionary = state["sides"][pm_side_id]
	if str(pm_side.get("team_id", "")) != "CHAOS-PM":
		return {"ok": false, "threes": 0, "pm_side": ""}
	if (pm_side.get("used_ploys_tp", []) as Array).has("curse_of_rot") or int(pm_side.get("cp", 0)) < 1:
		return {"ok": false, "threes": 0, "pm_side": ""}
	var terrains: Array = state["map"]["terrains"]
	var z_pm := KTGeometry.elevation_at(pm_op["pos"], terrains)
	var z_en := KTGeometry.elevation_at(enemy_op["pos"], terrains)
	var dist := KTGeometry.edge_distance_3d(pm_op["pos"], float(pm_op["base_radius"]), z_pm, enemy_op["pos"], float(enemy_op["base_radius"]), z_en)
	var max_r := 7.05 if bool(enemy_op.get("poisoned", false)) else 3.05
	if dist > max_r:
		return {"ok": false, "threes": 0, "pm_side": ""}
	var threes := 0
	for d_v in session.get(pool_key, []):
		var dd: Dictionary = d_v
		if int(dd.get("val", 0)) == 3 and not bool(dd.get("locked", false)):
			threes += 1
	return {"ok": threes > 0, "threes": threes, "pm_side": pm_side_id, "enemy_uid": str(enemy_op["uid"])}


static func apply_curse_of_rot(session: Dictionary, pool_key: String, state: Dictionary) -> Dictionary:
	var chk := can_use_curse_of_rot(session, pool_key, state)
	if not bool(chk["ok"]):
		return {"ok": false, "msg": "当前无法发动《腐烂诅咒》"}
	var pm_side_id := str(chk["pm_side"])
	var pm_side: Dictionary = state["sides"][pm_side_id]
	var enemy_op := _find_op(state, str(chk["enemy_uid"]))
	pm_side["cp"] = int(pm_side["cp"]) - 1
	(pm_side["used_ploys_tp"] as Array).append("curse_of_rot")
	session["curse_of_rot_used"] = true
	var locked_indices: Array = []
	var dice: Array = session.get(pool_key, [])
	for i in range(dice.size()):
		var dd: Dictionary = dice[i]
		if int(dd.get("val", 0)) == 3 and not bool(dd.get("locked", false)):
			dd["locked"] = true
			locked_indices.append(i)
	var dmg := locked_indices.size()
	var before_hp: int = int(enemy_op["wounds_cur"])
	enemy_op["wounds_cur"] = maxi(0, before_hp - dmg)
	var lost: int = before_hp - int(enemy_op["wounds_cur"])
	_trigger_grandfathers_blessing_combat(enemy_op, lost, state, session)
	if int(enemy_op["wounds_cur"]) <= 0:
		enemy_op["incapacitated"] = true
		enemy_op["killed_by"] = str(session["defender_uid"] if pool_key == "atk_dice" else session["attacker_uid"])
	_log_event(session, "腐烂诅咒", "☠️ 腐烂诅咒生效：锁死 %d 枚 3 点骰（不可保留/重掷），对【%s】造成 %d 点伤害！" % [dmg, str(enemy_op["name"]), lost], pool_key, locked_indices, "damage")
	return {"ok": true, "dmg": lost, "locked": locked_indices}


static func finalize_shooting_resolution(session: Dictionary, state: Dictionary, rng: RandomNumberGenerator) -> void:
	## Optimal defence allocation + Disgustingly Resilient / Iron Halo / Poison / Hot resolution!
	var attacker := _find_op(state, str(session["attacker_uid"]))
	var defender := _find_op(state, str(session["defender_uid"]))
	if attacker.is_empty() or defender.is_empty():
		session["step"] = "resolved"
		return

	var def_side: Dictionary = state["sides"][session["defender_side"]]
	var save_req := int(session["def_save"])
	var crit_save_req := int(session["def_crit_save"])

	var def_crits := int(session["auto_def_crits"])
	var def_norms := int(session["auto_def_normals"])
	var def_fails := 0

	for d in session["def_dice"]:
		var v := int(d["val"])
		if bool(d.get("locked", false)):
			# Curse of Rot: locked 3s cannot be retained as successes (so exclude from Indomitus!)
			continue
		elif v == 1:
			def_fails += 1
		elif v >= crit_save_req:
			def_crits += 1
		elif v >= save_req:
			def_norms += 1
		else:
			def_fails += 1

	# Angels of Death Strategic Ploy: Indomitus (不屈不挠) — 2+ fails -> 1 normal save
	if def_side["active_ploys"].has("不屈不挠") and def_fails >= 2:
		def_fails -= 2
		def_norms += 1
		_log_event(session, "不屈不挠", "🛡️ 不屈不挠生效：防守方将 2 枚失败骰转化为 1 枚普通豁免！", "", [], "convert")

	# Angels of Death Firefight Ploy: Transhuman Physiology (超人体格) if applied
	if bool(session["transhuman_used"]) and def_norms > 0:
		def_norms -= 1
		def_crits += 1
		_log_event(session, "超人体格", "🧬 超人体格生效：1 枚普通豁免升格为暴击豁免！", "", [], "upgrade")

	var atk_crits := int(session["retained_atk"]["crits"])
	var atk_norms := int(session["retained_atk"]["normals"])
	var n_dmg := int(session["normal_dmg"])
	var c_dmg := int(session["crit_dmg"])

	# Exact optimal defence allocation (minimize total incoming damage; Rulebook p.16 & p.92 Brutal)
	var is_brutal_shot: bool = bool((session.get("rules", {}) as Dictionary).get("brutal", false))
	var eff_def_norms: int = 0 if is_brutal_shot else def_norms
	var best_rem_c: int = atk_crits
	var best_rem_n: int = atk_norms
	var best_dmg: int = 999999
	# Choose how many crit saves spend on crits (always optimal to spend crit saves on crits first, then normals)
	var c_on_c: int = mini(def_crits, atk_crits)
	var rem_dc: int = def_crits - c_on_c
	var rem_ac_base: int = atk_crits - c_on_c
	var c_on_n: int = mini(rem_dc, atk_norms)
	var rem_an_base: int = atk_norms - c_on_n
	# Choose how many pairs of normal saves (2 normals -> 1 crit) to spend on remaining crits
	var max_pairs: int = mini(rem_ac_base, int(eff_def_norms / 2))
	for pairs in range(max_pairs + 1):
		var rc: int = rem_ac_base - pairs
		var rn: int = maxi(0, rem_an_base - (eff_def_norms - pairs * 2))
		var dmg_est: int = rc * c_dmg + rn * n_dmg
		if dmg_est < best_dmg or (dmg_est == best_dmg and rc < best_rem_c):
			best_dmg = dmg_est
			best_rem_c = rc
			best_rem_n = rn
	atk_crits = best_rem_c
	atk_norms = best_rem_n

	# Apply Devastating + Unblocked Attack Dice!
	var total_dealt := 0
	var dev_mw := int(session["retained_atk"]["devastating_mw"])
	if dev_mw > 0:
		total_dealt += _apply_damage_instance(defender, dev_mw, false, def_side, state, session, rng)

	for _i in range(atk_crits):
		if defender["incapacitated"]:
			break
		total_dealt += _apply_damage_instance(defender, c_dmg, true, def_side, state, session, rng)

	for _i in range(atk_norms):
		if defender["incapacitated"]:
			break
		# Space Marine Captain: Iron Halo (钢铁光环) ignores first Normal attack die once per battle!
		if str(defender["op_id"]) == "aod_captain" and not bool(defender.get("iron_halo_used", false)):
			defender["iron_halo_used"] = true
			_log_event(session, "钢铁光环", "✨ 钢铁光环触发！星际战士连长完全免疫了本次普通攻击骰伤害！", "", [], "block")
			continue
		total_dealt += _apply_damage_instance(defender, n_dmg, true, def_side, state, session, rng)

	# Poison weapon rule (毒素)
	if total_dealt > 0 and bool(session["rules"].get("poison", false)) and str(defender["team_id"]) != "CHAOS-PM":
		if not bool(defender.get("poisoned", false)):
			defender["poisoned"] = true
			_log_event(session, "毒素", "☣️ 【毒素】感染！【%s】获得纳垢毒素指示物！" % str(defender["name"]), "", [], "status")

	# Stun weapon rule on Crit hit (晕眩)
	if int(session["retained_atk"]["crits"]) > 0 and bool(session["rules"].get("stun", false)) and not defender["incapacitated"]:
		if not has_chapter_tactic(def_side, defender, "resolute"):
			defender["apl_mod"] = maxi(-1, int(defender.get("apl_mod", 0)) - 1)
			defender["stun_skip_current_end"] = (str(state.get("active_op_uid", "")) == str(defender["uid"]))
			_log_event(session, "晕眩", "💫 【晕眩】暴击触发！【%s】APL -1！" % str(defender["name"]), "", [], "status")

	# Prune incapacitated targets from pending Blast / Torrent queue
	var pend: Array = session.get("pending_targets", [])
	var pi := pend.size() - 1
	while pi >= 0:
		var pt_d: Dictionary = pend[pi]
		var pop_op := _find_op(state, str(pt_d.get("defender_uid", "")))
		if pop_op.is_empty() or bool(pop_op.get("incapacitated", false)):
			pend.remove_at(pi)
		pi -= 1
	if attacker["incapacitated"]:
		pend.clear()

	# Hot weapon rule check (过热, Rulebook p.93 L2780-2782):
	# If the weapon is used multiple times in one action (e.g. Blast / Torrent), roll ONLY 1D6 at the end!
	if pend.is_empty():
		_resolve_hot_if_needed(session, attacker, state, rng)

	session["total_damage_dealt"] = total_dealt
	session["step"] = "resolved"
	if defender["incapacitated"]:
		_log_event(session, "击杀", "💀 【%s】耐伤归零，已被击杀残废！" % str(defender["name"]), "", [], "damage")


static func _resolve_hot_if_needed(
	session: Dictionary,
	attacker: Dictionary,
	state: Dictionary,
	rng: RandomNumberGenerator
) -> void:
	if bool(session.get("hot_resolved", false)):
		return
	session["hot_resolved"] = true
	if bool(session["rules"].get("hot", false)) and not attacker.is_empty() and not bool(attacker["incapacitated"]):
		var hot_thresh: int = int(session.get("worst_effective_hit", session.get("effective_hit", 3)))
		var hot_roll := _roll_d6(rng)
		if hot_roll < hot_thresh:
			var self_dmg := hot_roll * 2
			var before_h := int(attacker["wounds_cur"])
			attacker["wounds_cur"] = maxi(0, before_h - self_dmg)
			var lost_h := before_h - int(attacker["wounds_cur"])
			session["hot_self_dmg"] = self_dmg
			_log_event(session, "过热", "⚠️ 等离子【过热】检定掷出 %d！%s 遭受 %d 点反噬灼伤！" % [hot_roll, str(attacker["name"]), self_dmg], "", [], "damage")
			_trigger_grandfathers_blessing_combat(attacker, lost_h, state, session)
			if int(attacker["wounds_cur"]) <= 0:
				attacker["incapacitated"] = true
				_log_event(session, "击杀", "💀 【%s】因等离子过热阵亡离场！" % str(attacker["name"]), "", [], "damage")
		else:
			_log_event(session, "过热", "✅ 等离子【过热】检定通过（掷出 %d）" % hot_roll, "", [], "damage")


## Advance a Blast / Torrent session to `pending_targets[target_idx]` (Rulebook p.92 L2728 & p.94 L2840).
static func advance_next_shooting_target(
	session: Dictionary,
	target_idx: int,
	state: Dictionary,
	rng: RandomNumberGenerator
) -> bool:
	var pend: Array = session.get("pending_targets", [])
	if target_idx < 0 or target_idx >= pend.size():
		return false
	var attacker := _find_op(state, str(session.get("attacker_uid", "")))
	if attacker.is_empty() or bool(attacker["incapacitated"]):
		pend.clear()
		return false
	var entry: Dictionary = pend[target_idx]
	pend.remove_at(target_idx)
	var next_def := _find_op(state, str(entry.get("defender_uid", "")))
	if next_def.is_empty() or bool(next_def["incapacitated"]):
		if pend.is_empty():
			_resolve_hot_if_needed(session, attacker, state, rng)
		return false
	var weapon: Dictionary = session.get("weapon", {})
	var next_eval: Dictionary = entry.get("shot_eval", {})
	var next_sess: Dictionary = create_shooting_session(attacker, next_def, weapon, next_eval, state, rng, false)
	var next_idx: int = int(session.get("multi_index", 1)) + 1
	var total_n: int = int(session.get("multi_total", next_idx))
	var m_mode: String = str(session.get("multi_mode", ""))
	var hot_done: bool = bool(session.get("hot_resolved", false))
	var worst_hit: int = maxi(int(session.get("worst_effective_hit", 3)), int(next_sess.get("effective_hit", 3)))
	for k in next_sess.keys():
		session[k] = next_sess[k]
	session["pending_targets"] = pend
	session["multi_mode"] = m_mode
	session["multi_index"] = next_idx
	session["multi_total"] = total_n
	session["hot_resolved"] = hot_done
	session["worst_effective_hit"] = worst_hit
	return true


## End optional Torrent secondary targets early (Rulebook p.94 L2840: "选择任意数量的...作为次要目标").
static func skip_remaining_torrent_targets(
	session: Dictionary,
	state: Dictionary,
	rng: RandomNumberGenerator
) -> void:
	if str(session.get("multi_mode", "")) != "torrent":
		return
	(session.get("pending_targets", []) as Array).clear()
	var attacker := _find_op(state, str(session.get("attacker_uid", "")))
	_resolve_hot_if_needed(session, attacker, state, rng)


## AI / auto-resolver helper: pick 1 Crit cover save over 2 Normal cover saves whenever there is an incoming
## crit OR Piercing 2 leaves only 1 defence die (1 Crit strictly dominates 1 Normal).
static func auto_choose_cover_option(session: Dictionary, rng: RandomNumberGenerator) -> void:
	if not (str(session.get("cover_mode", "")) in ["improved", "stealth_choice"]):
		return
	var incoming_crits: int = int((session.get("retained_atk", {}) as Dictionary).get("crits", 0))
	if incoming_crits > 0 or int(session.get("def_total", 3)) <= 1:
		set_improved_cover_choice(session, "1crit", rng)


## Auto-resolve current + all pending Blast/Torrent targets for AI / headless playtests.
static func resolve_all_shooting_targets_auto(
	session: Dictionary,
	state: Dictionary,
	rng: RandomNumberGenerator
) -> void:
	var guard := 0
	while guard < 16:
		guard += 1
		if str(session.get("step", "")) == "attack_reroll":
			lock_in_shooting_attacks(session, state, rng)
		if str(session.get("step", "")) == "defence_roll":
			auto_choose_cover_option(session, rng)
			finalize_shooting_resolution(session, state, rng)
		var pend: Array = session.get("pending_targets", [])
		if pend.is_empty():
			break
		if not advance_next_shooting_target(session, 0, state, rng):
			if (session.get("pending_targets", []) as Array).is_empty():
				break


static func _trigger_grandfathers_blessing_combat(
	victim: Dictionary,
	lost: int,
	state: Dictionary,
	session: Dictionary
) -> void:
	# Plague Marine Champion: Grandfather's Blessing (慈父赐福) — Poisoned enemy within 7" loses wounds -> heal up to 3/TP!
	if lost <= 0 or not bool(victim.get("poisoned", false)):
		return
	var terrains_gb: Array = state["map"]["terrains"]
	var z_tgt_gb := KTGeometry.elevation_at(victim["pos"], terrains_gb)
	for op in state["operatives"]:
		if not op["incapacitated"] and bool(op.get("deployed", true)) and str(op["op_id"]) == "pm_champion" and str(op["side"]) != str(victim["side"]):
			var z_op_gb := KTGeometry.elevation_at(op["pos"], terrains_gb)
			var d := KTGeometry.edge_distance_3d(op["pos"], float(op["base_radius"]), z_op_gb, victim["pos"], float(victim["base_radius"]), z_tgt_gb)
			var healed_tp := int(op.get("blessing_healed_tp", 0))
			if d <= 7.05 and healed_tp < 3 and int(op["wounds_cur"]) < int(op["wounds_max"]):
				var can_heal := mini(lost, mini(3 - healed_tp, int(op["wounds_max"]) - int(op["wounds_cur"])))
				if can_heal > 0:
					op["wounds_cur"] = int(op["wounds_cur"]) + can_heal
					op["blessing_healed_tp"] = healed_tp + can_heal
					_log_event(session, "慈父赐福", "💚 慈父赐福：瘟疫勇士吸收疫病恢复 +%d 耐伤！" % can_heal, "", [], "status")


static func _apply_damage_instance(
	target: Dictionary,
	raw_dmg: int,
	is_attack_die: bool,
	target_side: Dictionary,
	state: Dictionary,
	session: Dictionary,
	rng: RandomNumberGenerator
) -> int:
	if target["incapacitated"] or raw_dmg <= 0:
		return 0
	var dmg := raw_dmg

	# Plague Marines: Disgustingly Resilient (恼人韧性) — Attack die >= 3 dmg -> roll D6 4+ reduce by 1!
	if is_attack_die and str(target["team_id"]) == "CHAOS-PM" and dmg >= 3:
		if bool(target.get("auto_resilient_active", false)):
			dmg = maxi(2, dmg - 1)
			_log_event(session, "恶心韧性", "🦠 恶心韧性生效：免掷直接减免 1 点伤害（%d → %d）" % [raw_dmg, dmg], "", [], "block")
		else:
			var fnp := _roll_d6(rng)
			session["fnp_rolls"].append(fnp)
			if fnp >= 4:
				dmg -= 1
				_log_event(session, "恼人韧性", "🦠 恼人韧性检定 %d (4+成功)：减免 1 点伤害（%d → %d）" % [fnp, raw_dmg, dmg], "", [], "block")

	var before := int(target["wounds_cur"])
	target["wounds_cur"] = maxi(0, before - dmg)
	var lost := before - int(target["wounds_cur"])

	_trigger_grandfathers_blessing_combat(target, lost, state, session)

	if int(target["wounds_cur"]) <= 0:
		target["incapacitated"] = true
		# Remembered for tac ops that care who made the kill (主宰 / 击溃)
		var att_uid := str(session.get("attacker_uid", ""))
		target["killed_by"] = str(session.get("defender_uid", "")) if str(target["uid"]) == att_uid else att_uid
	return lost


# ==============================================================================
# MELEE ALTERNATING DUEL ENGINE (Simultaneous Roll -> Strike / Parry Turn-by-Turn)
# ==============================================================================

static func create_melee_session(
	attacker: Dictionary,
	defender: Dictionary,
	att_weapon: Dictionary,
	def_weapon: Dictionary,
	state: Dictionary,
	rng: RandomNumberGenerator
) -> Dictionary:
	var att_pool := _build_melee_fighter_pool(attacker, defender, att_weapon, true, state, rng)
	var def_pool := _build_melee_fighter_pool(defender, attacker, def_weapon, false, state, rng)
	# IMP-AOD Shock Assault (震慑突袭) lasts only until the end of THIS Fight action (not a 2nd Fight action!)
	attacker["shock_assault_active"] = false

	var sess := {
		"kind": "melee",
		"step": "duel", # "duel" -> "resolved"
		"turn": "attacker", # Attacker resolves first die!
		"attacker_uid": attacker["uid"],
		"defender_uid": defender["uid"],
		"attacker_side": attacker["side"],
		"defender_side": defender["side"],
		"att": att_pool,
		"def": def_pool,
		"att_first_strike_done": false,
		"def_first_strike_done": false,
		"att_shock_done": false,
		"def_shock_done": false,
		"resolution_log": [
			"⚔️ 近战搏杀开启！【%s】(%d暴/%d普) vs 【%s】(%d暴/%d普)" % [
				str(attacker["name"]), att_pool["crits"], att_pool["normals"],
				str(defender["name"]), def_pool["crits"], def_pool["normals"]
			]
		],
		"fnp_rolls": [],
	}

	# Rulebook p.94 (L2837): Stun triggers in the Roll Attack Dice step whenever any Critical success is RETAINED.
	if int(att_pool["crits"]) > 0 and bool(att_pool["rules"].get("stun", false)) and not bool(defender["incapacitated"]) and not bool(def_pool["resolute"]):
		defender["apl_mod"] = maxi(-1, int(defender.get("apl_mod", 0)) - 1)
		defender["stun_skip_current_end"] = (str(state.get("active_op_uid", "")) == str(defender["uid"]))
		_log_event(sess, "晕眩", "💫 【晕眩】暴击保留触发：【%s】APL -1！" % str(defender["name"]), "", [], "status")
	if int(def_pool["crits"]) > 0 and bool(def_pool["rules"].get("stun", false)) and not bool(attacker["incapacitated"]) and not bool(att_pool["resolute"]):
		attacker["apl_mod"] = maxi(-1, int(attacker.get("apl_mod", 0)) - 1)
		attacker["stun_skip_current_end"] = (str(state.get("active_op_uid", "")) == str(attacker["uid"]))
		_log_event(sess, "晕眩", "💫 【晕眩】反击暴击保留触发：【%s】APL -1！" % str(attacker["name"]), "", [], "status")

	return sess


static func _build_melee_fighter_pool(
	fighter: Dictionary,
	opponent: Dictionary,
	weapon: Dictionary,
	is_attacker: bool,
	state: Dictionary,
	rng: RandomNumberGenerator
) -> Dictionary:
	var side_state: Dictionary = state["sides"][fighter["side"]]
	var opp_side: Dictionary = state["sides"][opponent["side"]]
	var rules: Dictionary = weapon.get("rules", {}).duplicate(true)
	var tags: Array[String] = []

	# Chapter Tactics & Ploys
	if has_chapter_tactic(side_state, fighter, "aggressive"):
		rules["rending"] = true
		tags.append("猛攻: 撕裂")
	var dueller := has_chapter_tactic(side_state, fighter, "dueller")
	if dueller:
		tags.append("决斗家: 普骰可挡暴击")
	var resolute := has_chapter_tactic(side_state, fighter, "resolute")

	if str(side_state.get("combat_doctrine", "")) == "assault":
		rules["balanced"] = true
		tags.append("突击条令: 平衡")
	var fighter_moved_act: float = float(fighter.get("moved_distance_activation", fighter.get("moved_distance_tp", 0.0)))
	if side_state["active_ploys"].has("慢步死亡") and (not is_attacker or fighter_moved_act <= 3.05):
		rules["ceaseless"] = true
		tags.append("慢步死亡: 无休")

	var shock_assault := is_attacker and bool(fighter.get("shock_assault_active", false))
	if shock_assault:
		rules["shock"] = true
		tags.append("震慑突袭: 震荡 & 首击+1伤")

	# Combat Support (+1 hit per friendly engaged with opponent and not engaged with other enemies)
	var assists := 0
	if not has_chapter_tactic(opp_side, opponent, "siege_specialist"):
		var terrains: Array = state["map"]["terrains"]
		var z_opp := KTGeometry.elevation_at(opponent["pos"], terrains)
		for ally in state["operatives"]:
			if ally["incapacitated"] or not bool(ally.get("deployed", true)) or ally["side"] != fighter["side"] or ally["uid"] == fighter["uid"]:
				continue
			var z_ally := KTGeometry.elevation_at(ally["pos"], terrains)
			var r_ally := float(ally["base_radius"])
			if not KTGeometry.in_control_range_at(ally["pos"], r_ally, z_ally, opponent["pos"], float(opponent["base_radius"]), z_opp, terrains):
				continue
			var engaged_other := false
			for other_e in state["operatives"]:
				if other_e["incapacitated"] or not bool(other_e.get("deployed", true)) or other_e["side"] == fighter["side"] or other_e["uid"] == opponent["uid"]:
					continue
				var z_oe := KTGeometry.elevation_at(other_e["pos"], terrains)
				if KTGeometry.in_control_range_at(ally["pos"], r_ally, z_ally, other_e["pos"], float(other_e["base_radius"]), z_oe, terrains):
					engaged_other = true
					break
			if not engaged_other:
				assists += 1
	if assists > 0:
		tags.append("友方围攻协助 +%d" % assists)

	var ignore_wounded: bool = bool(side_state["active_ploys"].has("他们无所畏惧")) or (str(side_state["team_id"]) == "CHAOS-PM" and bool(side_state.get("has_plague_bell", false)))
	var wounded: bool = (int(fighter["wounds_cur"]) * 2 < int(fighter["wounds_max"])) and not ignore_wounded
	var hit_delta := -assists
	if wounded:
		hit_delta += 1
		tags.append("受创+1")
	elif is_contagion_affected(fighter, state):
		hit_delta += 1
		tags.append("传染+1")

	var effective_hit := clampi(int(weapon["hit"]) + hit_delta, 2, 6)
	var lethal_req := int(rules.get("lethal", 6))
	var n_dmg := int(weapon["normal_dmg"])
	var c_dmg := int(weapon["crit_dmg"])
	if bool(rules.get("toxic", false)) and bool(opponent.get("poisoned", false)):
		n_dmg += 1
		c_dmg += 1
		tags.append("剧毒 +1/+1")

	var dice: Array[int] = []
	var rerolled: Array[bool] = []
	for _i in range(int(weapon["attacks"])):
		dice.append(_roll_d6(rng))
		rerolled.append(false)

	# 偏移肩盾 (once per TP, 2026 errata: after opponent rolls attack dice, before rerolls):
	# Only consume when the rolled dice actually threaten a <6 critical (Lethal / Severe / Rending)!
	var crit_below_6: bool = lethal_req < 6 or bool(rules.get("severe", false)) or bool(rules.get("rending", false))
	if crit_below_6 and has_equipment(opp_side, "pauldronshields") and not bool(opp_side.get("shields_used_tp", false)):
		var has_six := false
		var has_lt6_hit := false
		var has_lt6_lethal := false
		for dv_s in dice:
			if dv_s == 6:
				has_six = true
			elif dv_s >= effective_hit and dv_s < 6:
				has_lt6_hit = true
				if dv_s >= lethal_req:
					has_lt6_lethal = true
		var has_severe_threat := bool(rules.get("severe", false)) and not has_six and has_lt6_hit
		var has_rending_threat := bool(rules.get("rending", false)) and (has_six or has_lt6_lethal) and has_lt6_hit
		if has_lt6_lethal or has_severe_threat or has_rending_threat:
			opp_side["shields_used_tp"] = true
			lethal_req = 6
			rules["severe"] = false
			rules["rending"] = false
			tags.append("偏移肩盾: 仅 6 为关键成功")

	# Auto-apply free weapon rerolls (Ceaseless & Balanced independently; each die at most once, Rulebook p.23)
	if bool(rules.get("ceaseless", false)):
		var fail_counts := {}
		for idx_c in range(dice.size()):
			var vc: int = dice[idx_c]
			if vc == 1 or vc < effective_hit:
				fail_counts[vc] = int(fail_counts.get(vc, 0)) + 1
		var best_face := 0
		var best_cnt := 0
		for k in fail_counts.keys():
			if int(fail_counts[k]) > best_cnt:
				best_cnt = int(fail_counts[k])
				best_face = int(k)
		if best_face > 0:
			for idx in range(dice.size()):
				if dice[idx] == best_face and not rerolled[idx]:
					dice[idx] = _roll_d6(rng)
					rerolled[idx] = true
	if bool(rules.get("balanced", false)):
		for idx in range(dice.size()):
			if not rerolled[idx] and (dice[idx] == 1 or dice[idx] < effective_hit):
				dice[idx] = _roll_d6(rng)
				rerolled[idx] = true
				break

	var crits := 0
	var rolled_norms := 0
	var retained_from_fail := 0
	var fails := 0
	for v in dice:
		if v == 1:
			fails += 1
		elif v == 6 or (v >= effective_hit and v >= lethal_req):
			# Rulebook p.93 & v11.5 §2.1: Lethal X+ only upgrades a successful hit (natural 6 always crits).
			crits += 1
		elif v >= effective_hit:
			rolled_norms += 1
		else:
			fails += 1

	# Purity Seals check (retained normal from failure cannot be upgraded by Rending per v1.5 FAQ)
	if has_equipment(side_state, "purityseals") and not bool(side_state.get("purity_used_tp", false)) and fails >= 2:
		fails -= 2
		retained_from_fail += 1
		side_state["purity_used_tp"] = true
		tags.append("纯洁印章保底+1普")

	var used_severe := false
	if bool(rules.get("severe", false)) and crits == 0 and (rolled_norms + retained_from_fail) > 0:
		if rolled_norms > 0:
			rolled_norms -= 1
		else:
			retained_from_fail -= 1
		crits += 1
		used_severe = true
	if not used_severe:
		# Punishing (重击): if >=1 crit and >=1 fail, convert 1 fail -> retained_from_fail (isolated from Rending!)
		if bool(rules.get("punishing", false)) and crits > 0 and fails > 0:
			fails -= 1
			retained_from_fail += 1
		if bool(rules.get("rending", false)) and crits > 0 and rolled_norms > 0:
			rolled_norms -= 1
			crits += 1

	var norms := rolled_norms + retained_from_fail

	return {
		"weapon": weapon,
		"rules": rules,
		"dice_raw": dice,
		"effective_hit": effective_hit,
		"normal_dmg": n_dmg,
		"crit_dmg": c_dmg,
		"crits": crits,
		"normals": norms,
		"dueller": dueller,
		"resolute": resolute,
		"shock_assault": shock_assault,
		"tags": tags,
	}


static func execute_melee_step(
	session: Dictionary,
	action_type: String, # "strike_crit" | "strike_norm" | "parry_crit" | "parry_norm"
	state: Dictionary,
	rng: RandomNumberGenerator,
	prefer_normal: bool = false
) -> Dictionary:
	if str(session["step"]) != "duel":
		return {"ok": false, "msg": "近战已结束"}

	var is_att := str(session["turn"]) == "attacker"
	var actor := _find_op(state, str(session["attacker_uid"] if is_att else session["defender_uid"]))
	var target := _find_op(state, str(session["defender_uid"] if is_att else session["attacker_uid"]))
	var my_pool: Dictionary = session["att"] if is_att else session["def"]
	var opp_pool: Dictionary = session["def"] if is_att else session["att"]
	var target_side: Dictionary = state["sides"][target["side"]]

	if my_pool["crits"] + my_pool["normals"] <= 0:
		_advance_melee_turn(session, state)
		return {"ok": true, "msg": "无可用成功骰，跳过", "removed": 0}

	var opp_brutal := bool(opp_pool["rules"].get("brutal", false))
	var removed := 0

	if action_type == "strike_crit":
		if int(my_pool["crits"]) <= 0:
			return {"ok": false, "msg": "没有可用的暴击骰"}
		my_pool["crits"] = int(my_pool["crits"]) - 1
		var dmg := int(my_pool["crit_dmg"])
		if is_att and bool(my_pool["shock_assault"]) and not bool(session["att_first_strike_done"]):
			dmg = mini(7, dmg + 1)
		if is_att:
			session["att_first_strike_done"] = true
		else:
			session["def_first_strike_done"] = true

		# Shock (震荡): First crit strike in combat also discards 1 opponent normal (or crit if none)!
		var shock_key := "att_shock_done" if is_att else "def_shock_done"
		if bool(my_pool["rules"].get("shock", false)) and not bool(session[shock_key]) and not bool(opp_pool["resolute"]):
			session[shock_key] = true
			if int(opp_pool["normals"]) > 0:
				opp_pool["normals"] = int(opp_pool["normals"]) - 1
				_log_event(session, "震荡", "⚡ 【震荡】生效：震碎了【%s】的 1 枚普通成功骰！" % str(target["name"]), "", [], "block")
			elif int(opp_pool["crits"]) > 0:
				opp_pool["crits"] = int(opp_pool["crits"]) - 1
				_log_event(session, "震荡", "⚡ 【震荡】生效：震碎了【%s】的 1 枚暴击骰！" % str(target["name"]), "", [], "block")

		var dealt := _apply_damage_instance(target, dmg, true, target_side, state, session, rng)
		_log_event(session, "出击", "🗡️ 【%s】暴击出击！对【%s】造成 %d 点伤害！" % [str(actor["name"]), str(target["name"]), dealt], "", [], "damage")
		_apply_melee_post_hit_effects(actor, target, my_pool, opp_pool, dealt, true, state, session)

	elif action_type == "strike_norm":
		if int(my_pool["normals"]) <= 0:
			return {"ok": false, "msg": "没有可用的普通成功骰"}
		my_pool["normals"] = int(my_pool["normals"]) - 1
		var dmg_n := int(my_pool["normal_dmg"])
		if is_att and bool(my_pool["shock_assault"]) and not bool(session["att_first_strike_done"]):
			dmg_n = mini(7, dmg_n + 1)
		if is_att:
			session["att_first_strike_done"] = true
		else:
			session["def_first_strike_done"] = true

		var dealt_n := 0
		if str(target["op_id"]) == "aod_captain" and not bool(target.get("iron_halo_used", false)):
			target["iron_halo_used"] = true
			_log_event(session, "钢铁光环", "✨ 钢铁光环触发！星际战士连长免疫了本次普通近战伤害！", "", [], "block")
		else:
			dealt_n = _apply_damage_instance(target, dmg_n, true, target_side, state, session, rng)
			_log_event(session, "出击", "⚔️ 【%s】普通出击！对【%s】造成 %d 点伤害！" % [str(actor["name"]), str(target["name"]), dealt_n], "", [], "damage")
		_apply_melee_post_hit_effects(actor, target, my_pool, opp_pool, dealt_n, false, state, session)

	elif action_type == "parry_crit":
		if int(my_pool["crits"]) <= 0:
			return {"ok": false, "msg": "没有可用的暴击骰"}
		my_pool["crits"] = int(my_pool["crits"]) - 1
		if int(opp_pool["crits"]) + int(opp_pool["normals"]) <= 0:
			removed = 0
			_log_event(session, "格挡", "🛡️ 【%s】以暴击进行空格挡（对手无剩余成功骰）！" % str(actor["name"]), "", [], "block")
		elif prefer_normal and int(opp_pool["normals"]) > 0:
			opp_pool["normals"] = int(opp_pool["normals"]) - 1
			removed = 1
			_log_event(session, "格挡", "🛡️ 【%s】以暴击格挡了对手的 1 枚普通成功骰！" % str(actor["name"]), "", [], "block")
		elif int(opp_pool["crits"]) > 0:
			opp_pool["crits"] = int(opp_pool["crits"]) - 1
			removed = 1
			_log_event(session, "格挡", "🛡️ 【%s】以暴击格挡了对手的 1 枚暴击骰！" % str(actor["name"]), "", [], "block")
		else:
			opp_pool["normals"] = int(opp_pool["normals"]) - 1
			removed = 1
			_log_event(session, "格挡", "🛡️ 【%s】以暴击格挡了对手的 1 枚普通成功骰！" % str(actor["name"]), "", [], "block")

	elif action_type == "parry_norm":
		if int(my_pool["normals"]) <= 0:
			return {"ok": false, "msg": "没有可用的普通成功骰"}
		if opp_brutal:
			return {"ok": false, "msg": "对手武器带有【残暴】，不能用普通成功进行格挡！"}
		var can_parry_crit := bool(my_pool["dueller"]) and int(opp_pool["crits"]) > 0
		my_pool["normals"] = int(my_pool["normals"]) - 1
		if prefer_normal and int(opp_pool["normals"]) > 0:
			opp_pool["normals"] = int(opp_pool["normals"]) - 1
			removed = 1
			_log_event(session, "格挡", "🛡️ 【%s】格挡了对手的 1 枚普通成功骰！" % str(actor["name"]), "", [], "block")
		elif can_parry_crit:
			opp_pool["crits"] = int(opp_pool["crits"]) - 1
			removed = 1
			_log_event(session, "决斗家", "🤺 【决斗家】绝技！【%s】用普通成功格挡了对手的暴击骰！" % str(actor["name"]), "", [], "block")
		elif int(opp_pool["normals"]) > 0:
			opp_pool["normals"] = int(opp_pool["normals"]) - 1
			removed = 1
			_log_event(session, "格挡", "🛡️ 【%s】格挡了对手的 1 枚普通成功骰！" % str(actor["name"]), "", [], "block")
		else:
			removed = 0
			_log_event(session, "格挡", "🛡️ 【%s】进行空格挡（未抵消对手成功骰）！" % str(actor["name"]), "", [], "block")
	else:
		return {"ok": false, "msg": "未知近战动作"}

	_advance_melee_turn(session, state)
	return {"ok": true, "msg": "已执行", "removed": removed}


static func _apply_melee_post_hit_effects(
	_actor: Dictionary,
	target: Dictionary,
	my_pool: Dictionary,
	_opp_pool: Dictionary,
	dealt: int,
	_is_crit: bool,
	_state: Dictionary,
	session: Dictionary
) -> void:
	if dealt > 0 and bool(my_pool["rules"].get("poison", false)) and str(target["team_id"]) != "CHAOS-PM":
		if not bool(target.get("poisoned", false)):
			target["poisoned"] = true
			_log_event(session, "毒素", "☣️ 【%s】感染纳垢毒素！" % str(target["name"]), "", [], "status")


static func _advance_melee_turn(session: Dictionary, state: Dictionary) -> void:
	var att := _find_op(state, str(session["attacker_uid"]))
	var def := _find_op(state, str(session["defender_uid"]))
	if att.is_empty() or def.is_empty() or bool(att["incapacitated"]) or bool(def["incapacitated"]):
		if not att.is_empty() and bool(att["incapacitated"]):
			_log_event(session, "击杀", "💀 【%s】在近战中倒下！" % str(att["name"]), "", [], "damage")
		if not def.is_empty() and bool(def["incapacitated"]):
			_log_event(session, "击杀", "💀 【%s】被近战斩杀！" % str(def["name"]), "", [], "damage")
		session["step"] = "resolved"
		return

	var att_left := int(session["att"]["crits"]) + int(session["att"]["normals"])
	var def_left := int(session["def"]["crits"]) + int(session["def"]["normals"])
	if att_left <= 0 and def_left <= 0:
		_log_event(session, "结算", "⚔️ 双方攻击骰全部结算完毕，近战结束。", "", [], "info")
		session["step"] = "resolved"
		return

	if str(session["turn"]) == "attacker":
		session["turn"] = "defender" if def_left > 0 else "attacker"
	else:
		session["turn"] = "attacker" if att_left > 0 else "defender"


static func choose_ai_melee_action(session: Dictionary, state: Dictionary) -> String:
	## Minimax-inspired smart melee choice:
	## 1. If a single strike right now kills the opponent -> ALWAYS STRIKE FOR LETHAL!
	## 2. If opponent's next strike would kill us and we can parry it -> PARRY TO SURVIVE!
	## 3. Otherwise strike with highest value die!
	var is_att := str(session["turn"]) == "attacker"
	var me := _find_op(state, str(session["attacker_uid"] if is_att else session["defender_uid"]))
	var opp := _find_op(state, str(session["defender_uid"] if is_att else session["attacker_uid"]))
	var my_pool: Dictionary = session["att"] if is_att else session["def"]
	var opp_pool: Dictionary = session["def"] if is_att else session["att"]

	var opp_hp := int(opp["wounds_cur"])
	var my_hp := int(me["wounds_cur"])
	var my_c := int(my_pool["crits"])
	var my_n := int(my_pool["normals"])
	var opp_c := int(opp_pool["crits"])
	var opp_n := int(opp_pool["normals"])

	# 1. Immediate lethal check!
	if my_n > 0 and int(my_pool["normal_dmg"]) >= opp_hp:
		return "strike_norm"
	if my_c > 0 and int(my_pool["crit_dmg"]) >= opp_hp:
		return "strike_crit"

	# 2. Defensive survival parry if opponent threatens lethal next swing
	var opp_max_swing := int(opp_pool["crit_dmg"]) if opp_c > 0 else (int(opp_pool["normal_dmg"]) if opp_n > 0 else 0)
	var opp_brutal := bool(opp_pool["rules"].get("brutal", false))
	if opp_max_swing >= my_hp:
		if opp_c > 0:
			if my_n > 0 and bool(my_pool["dueller"]) and not opp_brutal:
				return "parry_norm"
			if my_c > 0:
				return "parry_crit"
		elif opp_n > 0:
			if my_n > 0 and not opp_brutal:
				return "parry_norm"

	# 3. Default aggressive strike
	if my_c > 0:
		return "strike_crit"
	return "strike_norm"


static func _find_op(state: Dictionary, uid: String) -> Dictionary:
	for op in state["operatives"]:
		if str(op["uid"]) == uid:
			return op
	return {}
