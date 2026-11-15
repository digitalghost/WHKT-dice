extends SceneTree

## Headless Automated Core Rules & AI Simulation Test Suite
## Run via: godot --headless --path kt-game -s res://tests/run_all.gd

var _passed: int = 0
var _failed: int = 0


func _init() -> void:
	print("=== KT-GAME HEADLESS UNIT & RULES TEST SUITE ===")
	_test_data_loading()
	_test_geometry_movement_and_los()
	_test_combat_audit_fixes_and_factions()
	_test_engine_flow_undo_and_ai()
	_test_deployment_phase()
	_test_prebattle_setup()
	_test_kill_op_and_wipeout()
	_test_initiative_cards_and_primary_op()
	_test_equipment()
	_test_tac_ops()
	_test_universal_equipment()
	_test_universal_equipment_deployables()
	_test_grenade_equipment()
	_test_all_official_maps()

	print("------------------------------------------------")
	print("RESULTS: %d PASSED, %d FAILED" % [_passed, _failed])
	quit(0 if _failed == 0 else 1)


func _assert_true(cond: bool, label: String) -> void:
	if cond:
		_passed += 1
		print("  [PASS] " + label)
	else:
		_failed += 1
		printerr("  [FAIL] " + label)


func _find_by_op_id(eng: KTEngine, op_id: String) -> Dictionary:
	for op in eng.state["operatives"]:
		if str(op["op_id"]) == op_id:
			return op
	return {}


func _test_data_loading() -> void:
	var aod: Dictionary = KTData.get_team("IMP-AOD")
	var pm: Dictionary = KTData.get_team("CHAOS-PM")
	_assert_true(str(aod.get("team_id", "")) == "IMP-AOD", "IMP-AOD team JSON loaded")
	_assert_true(str(pm.get("team_id", "")) == "CHAOS-PM", "CHAOS-PM team JSON loaded")
	_assert_true((aod.get("operatives", []) as Array).size() >= 8, "IMP-AOD has >= 8 operative archetypes")
	_assert_true((pm.get("operatives", []) as Array).size() >= 7, "CHAOS-PM has >= 7 operative archetypes")
	var map_d: Dictionary = KTData.create_volkus_killzone()
	_assert_true((map_d["objectives"] as Array).size() == 3, "Killzone Volkus has 3 objectives")
	_assert_true((map_d["terrains"] as Array).size() >= 10, "Killzone Volkus terrain loaded")


func _test_geometry_movement_and_los() -> void:
	var eng := KTEngine.new(101)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	eng.auto_deploy_all()
	var cap: Dictionary = _find_by_op_id(eng, "aod_captain")

	# Coordinates below are for official map 4 (data/maps/volkus_25_04.json) as rebuilt by
	# tools/build_volkus_map.py after its conventions were fitted to the printed map tiles.
	# 1. Straight path through the north small ruin's Heavy wall (`small_ruin_02`, Rect2(11.0, 2.5, 6.0, 1.0)) should fail
	cap["pos"] = Vector2(14.0, 1.2)
	for o in eng.state["operatives"]:
		if str(o["side"]) != str(cap["side"]):
			o["pos"] = Vector2(28.0 + 0.01 * float(str(o["uid"]).length()), 21.0) # keep enemies out of control range
	var blocked_pts: Array[Vector2] = [Vector2(14.0, 1.2), Vector2(14.0, 5.5)]
	var res_blk: Dictionary = KTGeometry.validate_move_path(blocked_pts, cap, "reposition", 6.0, eng.state)
	_assert_true(not bool(res_blk["ok"]) and str(res_blk["reason"]).contains("地形"), "Heavy wall blocks straight walk-through")

	# 2. Climbing onto the SW large ruin's Vantage deck (`large_ruin_deck`: Rect2(4.5, 15.5, 6.5, 4.0), +3.0") adds +2" climb cost
	cap["pos"] = Vector2(7.0, 14.5) # just north of the deck (open side)
	var climb_pts: Array[Vector2] = [Vector2(7.0, 14.5), Vector2(7.0, 16.5)] # 2" horizontal + 2" climb = 4"
	var res_clb: Dictionary = KTGeometry.validate_move_path(climb_pts, cap, "reposition", 6.0, eng.state)
	_assert_true(bool(res_clb["ok"]) and absf(float(res_clb["cost"]) - 4.0) < 0.15, "Vantage +2\" climb cost calculated accurately")

	# 3. Concealed target behind Light Rubble (`light_rubble_03`: Rect2(24.31, 11.33, 3.33, 1.95), h=0.95") vs Ground shooter (Mortis profile) -> untargetable!
	var sniper: Dictionary = _find_by_op_id(eng, "aod_sniper")
	var target: Dictionary = _find_by_op_id(eng, "pm_champion")
	sniper["pos"] = Vector2(26.0, 7.5)
	target["pos"] = Vector2(26.0, 14.1)
	target["order"] = "conceal"
	var exec_sniper: Dictionary = sniper["weapons"][1]   # Executioner (Seek Light + Saturate)
	var mortis_sniper: Dictionary = sniper["weapons"][3] # Mortis (Piercing 1 + Devastating 3, no Seek Light)
	var ev_ground: Dictionary = KTGeometry.evaluate_shot(sniper, target, mortis_sniper, eng.state)
	_assert_true(str(ev_ground["cover"]) != "none" and not bool(ev_ground["valid"]), "Concealed operative in Light Cover cannot be shot from ground without Seek Light")

	# 3b. Executioner profile (Seek Light) CAN target Concealed operative in Light Cover from ground!
	var ev_seek: Dictionary = KTGeometry.evaluate_shot(sniper, target, exec_sniper, eng.state)
	_assert_true(bool(ev_seek["valid"]), "Executioner Seek Light breaks Light-Cover Conceal from ground")

	# 4. Move Sniper onto the NE large ruin's Vantage deck (`large_ruin_02_deck`: Rect2(22.5, 2.5, 6.0, 3.5), +3.0") shooting a
	#    Concealed target behind `light_rubble_02` (x 23..25, y 6.5..10) with Mortis
	sniper["pos"] = Vector2(25.0, 5.0)
	target["pos"] = Vector2(23.6, 10.7)
	var ev_vant_conceal: Dictionary = KTGeometry.evaluate_shot(sniper, target, mortis_sniper, eng.state)
	_assert_true(bool(ev_vant_conceal["valid"]) and str(ev_vant_conceal["cover"]) == "improved", "Vantage breaks Light-Cover Conceal and grants defender Improved Cover")

	# 4b. Against Engage target, Vantage (+3.0") grants Accurate 1!
	target["order"] = "engage"
	var ev_vant_engage: Dictionary = KTGeometry.evaluate_shot(sniper, target, mortis_sniper, eng.state)
	_assert_true(bool(ev_vant_engage["valid"]) and int(ev_vant_engage["vantage_accurate"]) == 1, "Vantage grants Accurate 1 against Engage target")

	# 6. 3D visibility: the SE small ruin's 2.21" heavy wall (`small_ruin`, Rect2(23.0, 18.5, 4.0, 1.0)) between
	#    two ground-level operatives blocks line of sight (both eye 1.4" and model top 1.6" < 2.21").
	var ters: Array = eng.state["map"]["terrains"]
	var los_blocked := KTGeometry.has_visibility(Vector2(25.0, 17.3), 0.63, 0.0, Vector2(25.0, 20.8), 0.63, 0.0, ters)
	_assert_true(not los_blocked, "2.2\" heavy ruin wall blocks ground-to-ground visibility")
	#    From +3.0" up (eye at 4.4") the same wall does not block.
	var los_from_deck := KTGeometry.has_visibility(Vector2(25.0, 15.5), 0.63, 3.0, Vector2(25.0, 20.8), 0.63, 0.0, ters)
	_assert_true(los_from_deck, "Vantage shooter sees over a 2.2\" ruin wall")
	#    A ~1" light rubble (`light_rubble_03`, h=0.95") never blocks visibility.
	var los_light := KTGeometry.has_visibility(Vector2(26.0, 7.5), 0.63, 0.0, Vector2(26.0, 14.1), 0.63, 0.0, ters)
	_assert_true(los_light, "Light barricade does not block visibility")
	#    Concealed target standing 2.5" behind the ruin wall (no cover, no LoS) is NOT a valid target.
	var pm_fighter: Dictionary = _find_by_op_id(eng, "pm_fighter")
	var aod_gunner: Dictionary = _find_by_op_id(eng, "aod_gunner")
	aod_gunner["pos"] = Vector2(3.8, 1.4)
	aod_gunner["order"] = "engage"
	pm_fighter["pos"] = Vector2(3.8, 6.0)
	pm_fighter["order"] = "conceal"
	var g_w: Array = eng.get_usable_ranged_weapons(aod_gunner)
	if not g_w.is_empty():
		var ev_hidden: Dictionary = KTGeometry.evaluate_shot(aod_gunner, pm_fighter, g_w[0], eng.state)
		_assert_true(not bool(ev_hidden["valid"]) and not bool(ev_hidden["visible"]), "Concealed operative fully behind a 2.2\" wall is not visible, so not a valid target")


func _test_combat_audit_fixes_and_factions() -> void:
	var eng := KTEngine.new(202)
	eng.start_match("hotseat", "A", "stealthy", "sharpshooter")
	eng.auto_deploy_all()
	var att: Dictionary = _find_by_op_id(eng, "aod_heavy_gunner")
	var def: Dictionary = _find_by_op_id(eng, "pm_fighter")
	att["pos"] = Vector2(15.0, 16.0)
	def["pos"] = Vector2(15.0, 10.0)
	def["order"] = "engage"

	# Audit Fix #3 check: Punishing converts 1 fail -> retained_from_fail, NOT rolled_normals,
	# so when 1 natural crit + 0 rolled normals + 3 fails occur, Punishing creates 1 normal and Rending MUST NOT upgrade that Punishing normal into a 2nd crit!
	var test_w := {
		"name": "测试重爆矢",
		"kind": "ranged",
		"attacks": 4,
		"hit": 3,
		"normal_dmg": 3,
		"crit_dmg": 4,
		"profile": "4 | 3+ | 3/4",
		"rules": {"punishing": true, "rending": true}
	}
	var shot_ev: Dictionary = KTGeometry.evaluate_shot(att, def, test_w, eng.state)
	shot_ev["vantage_accurate"] = 0
	# Isolate the Punishing/Rending interaction: terrain between these two spots on the official map
	# would make the target Obscured (crits -> normals), which is a different rule under test elsewhere.
	shot_ev["obscured"] = false
	shot_ev["cover"] = "none"
	var rng := RandomNumberGenerator.new()
	rng.seed = 42
	# Disable Purity Seals for deterministic check
	eng.state["sides"]["A"]["purity_used_tp"] = true
	var sess: Dictionary = KTCombat.create_shooting_session(att, def, test_w, shot_ev, eng.state, rng)
	sess["auto_attack_normals"] = 0
	# 1 natural crit (6), 0 natural normals, 3 failures (2, 2, 1)
	sess["atk_dice"] = [
		{"id": 0, "val": 6, "rerolled": false, "locked": false},
		{"id": 1, "val": 2, "rerolled": false, "locked": false},
		{"id": 2, "val": 2, "rerolled": false, "locked": false},
		{"id": 3, "val": 1, "rerolled": false, "locked": false},
	]
	KTCombat.lock_in_shooting_attacks(sess, eng.state, rng)
	var ret: Dictionary = sess["retained_atk"]
	_assert_true(int(ret["crits"]) == 1 and int(ret["normals"]) == 1, "Audit Fix verified: Punishing normal does not chain into Rending crit")

	# Resolve defence and verify Plague Marine Disgustingly Resilient runs cleanly
	KTCombat.finalize_shooting_resolution(sess, eng.state, rng)
	_assert_true(str(sess["step"]) == "resolved", "Shooting session resolves cleanly through Disgustingly Resilient")

	# Test Melee Alternating Duel with Dueller / Shock / Iron Halo
	var m_att: Dictionary = _find_by_op_id(eng, "aod_captain")
	var m_def: Dictionary = _find_by_op_id(eng, "pm_champion")
	m_att["pos"] = Vector2(12.0, 12.0)
	m_def["pos"] = Vector2(12.8, 12.0)
	var ms: Dictionary = KTCombat.create_melee_session(
		m_att, m_def,
		KTAI.pick_best_melee_weapon(m_att),
		KTAI.pick_best_melee_weapon(m_def),
		eng.state, rng
	)
	var safety := 0
	while str(ms["step"]) == "duel" and safety < 24:
		var act_str: String = KTCombat.choose_ai_melee_action(ms, eng.state)
		KTCombat.execute_melee_step(ms, act_str, eng.state, rng)
		safety += 1
	_assert_true(str(ms["step"]) == "resolved", "Melee alternating Strike/Parry duel completes cleanly")


func _test_engine_flow_undo_and_ai() -> void:
	var eng := KTEngine.new(303)
	eng.start_match("ai", "A", "dueller", "hardy")
	eng.auto_deploy_all()
	eng.auto_initiative()
	_assert_true(str(eng.state["phase"]) == "STRATEGY", "Initiative roll transitions to STRATEGY")

	var cp_before := int(eng.state["sides"]["A"]["cp"])
	eng.state["turn_side"] = "A"
	eng.use_strategic_ploy("A", "doctrine_tactical")
	_assert_true(int(eng.state["sides"]["A"]["cp"]) == cp_before - 1, "Strategic ploy spends 1 CP")

	# Undo should restore CP!
	var undone := eng.pop_undo()
	_assert_true(undone and int(eng.state["sides"]["A"]["cp"]) == cp_before, "Undo snapshot cleanly restores full game state")

	# Simulate AI decision functions without crashing
	_assert_true(not bool(eng.pass_strategy("A")["ok"]), "TP1: cannot pass before secretly choosing a Primary Op (p.106)")
	eng.choose_primary_op("A", "kill")
	eng.choose_primary_op("B", "key")
	eng.pass_strategy("A")
	eng.pass_strategy("B")
	_assert_true(str(eng.state["phase"]) == "FIREFIGHT", "Both Primary Ops chosen + two passes -> FIREFIGHT")
	eng.state["turn_side"] = "B"
	var ai_pick: Dictionary = KTAI.pick_operative_to_activate(eng, "B")
	_assert_true(not ai_pick.is_empty(), "AI selects operative and order in Firefight phase")
	eng.activate_operative(str(ai_pick["uid"]), str(ai_pick["order"]))
	var ai_op: Dictionary = eng.get_op(str(ai_pick["uid"]))
	var ai_step: Dictionary = KTAI.decide_next_operative_action(eng, ai_op)
	_assert_true(ai_step.has("kind"), "AI tactical action planner returns valid action dictionary")


func _test_deployment_phase() -> void:
	var eng := KTEngine.new(303)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	eng.auto_setup() # drop-zone choice + operative selection with defaults
	_assert_true(str(eng.state["phase"]) == "DEPLOY", "after setup the match is in DEPLOY (rulebook p.105 step 3)")
	var first := eng.deploy_side()
	_assert_true(first in ["A", "B"], "Pre-battle roll-off picks a deploying side")
	var groups: Array = eng.state["deploy"]["groups"][first]
	_assert_true(groups == [2, 2, 2], "6-operative team deploys in thirds 2/2/2")
	# Illegal: outside own drop zone
	var op0: Dictionary = eng.undeployed_ops(first)[0]
	var far := Vector2(15.0, 11.0)
	_assert_true(not bool(eng.deploy_operative(str(op0["uid"]), far)["ok"]), "Cannot deploy outside own drop zone")
	# Legal: inside zone, then the turn passes after the group is full
	var dz: Rect2 = eng.state["map"]["drop_zone_a"] if first == "A" else eng.state["map"]["drop_zone_b"]
	var r := float(op0["base_radius"])
	var spots: Array[Vector2] = []
	var x := dz.position.x + r + 0.6
	while spots.size() < 2 and x < dz.end.x - r:
		var cand := Vector2(x, dz.get_center().y)
		if bool(eng.validate_deploy_pos(op0, cand)["ok"]):
			spots.append(cand)
		x += 1.6
	_assert_true(spots.size() == 2, "Found two legal deployment spots in the drop zone")
	var ops := eng.undeployed_ops(first)
	eng.deploy_operative(str(ops[0]["uid"]), spots[0])
	_assert_true(eng.deploy_side() == first, "Turn stays with the same side until the group is complete")
	eng.deploy_operative(str(ops[1]["uid"]), spots[1])
	_assert_true(eng.deploy_side() != first, "After a full group the other side deploys")
	_assert_true(str(ops[0]["order"]) == "conceal" and bool(ops[0]["deployed"]), "Deployed operative has Conceal order")
	eng.auto_deploy_all()
	_assert_true(str(eng.state["phase"]) == "INITIATIVE", "All operatives deployed -> INITIATIVE")
	var all_in := true
	for op in eng.state["operatives"]:
		var z: Rect2 = eng.state["map"]["drop_zone_a"] if str(op["side"]) == "A" else eng.state["map"]["drop_zone_b"]
		if not z.has_point(op["pos"]):
			all_in = false
	_assert_true(all_in, "Every operative ends inside its own drop zone")


func _test_prebattle_setup() -> void:
	var eng := KTEngine.new(404)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	_assert_true(str(eng.state["phase"]) == "DROPZONE", "Match starts with the drop-zone choice (p.105 step 1)")
	_assert_true(not bool(eng.roll_initiative()["ok"]), "Initiative cannot be rolled before setup is done")
	_assert_true(eng.setup_side() in ["A", "B"], "Roll-off winner chooses the drop zone")
	var aod: Dictionary = KTData.get_team("IMP-AOD")
	var bad: Array = ["aod_captain", "aod_captain", "aod_grenadier", "aod_sniper", "aod_gunner", "aod_heavy_gunner"]
	_assert_true(not bool(KTEngine.validate_roster(aod, bad)["ok"]), "Two leaders is an illegal roster")
	var two_warriors: Array = ["aod_captain", "aod_assault_warrior", "aod_warrior", "aod_grenadier", "aod_sniper", "aod_gunner"]
	_assert_true(bool(KTEngine.validate_roster(aod, two_warriors)["ok"]), "Angels of Death may take duplicate warriors")
	var pm: Dictionary = KTData.get_team("CHAOS-PM")
	var pm_dup: Array = ["pm_champion", "pm_warrior", "pm_warrior", "pm_fighter", "pm_bombardier", "pm_heavy_gunner"]
	_assert_true(not bool(KTEngine.validate_roster(pm, pm_dup)["ok"]), "Plague Marines may not duplicate operatives")
	var short_roster: Array = ["aod_captain", "aod_grenadier", "aod_sniper", "aod_gunner", "aod_heavy_gunner"]
	_assert_true(not bool(KTEngine.validate_roster(aod, short_roster)["ok"]), "Roster must contain exactly 6 operatives")
	var zone_before: Vector2 = (eng.state["map"]["drop_zone_a"] as Rect2).position
	eng.choose_drop_zone(true)
	_assert_true(str(eng.state["phase"]) == "SELECT", "Choosing a drop zone advances to operative selection")
	_assert_true((eng.state["map"]["drop_zone_a"] as Rect2).position != zone_before, "Taking the other zone swaps the two zones")
	var sel := eng.setup_side()
	eng.confirm_roster(sel, eng.default_roster(sel))
	_assert_true(str(eng.state["phase"]) == "SELECT", "The other side still has to pick its team")
	eng.auto_setup()
	_assert_true(str(eng.state["phase"]) == "DEPLOY", "Both teams confirmed -> DEPLOY")
	_assert_true(eng.state["operatives"].size() == 12, "12 operatives spawned from the chosen rosters")


func _test_kill_op_and_wipeout() -> void:
	# Rulebook p.107 Kill Grade table, enemy starting with 6 operatives: thresholds 1/2/4/5/6
	_assert_true(KTEngine.kill_op_vp(0, 6) == 0, "Kill op: 0 kills of 6 = 0 VP")
	_assert_true(KTEngine.kill_op_vp(1, 6) == 1 and KTEngine.kill_op_vp(2, 6) == 2, "Kill op: 1 and 2 kills of 6 = 1 and 2 VP")
	_assert_true(KTEngine.kill_op_vp(3, 6) == 2, "Kill op: 3 kills of 6 is still Kill Grade 2 (table skips to 4)")
	_assert_true(KTEngine.kill_op_vp(4, 6) == 3 and KTEngine.kill_op_vp(6, 6) == 5, "Kill op: 4 kills = 3 VP, wipe = 5 VP")
	_assert_true(KTEngine.kill_op_vp(4, 11) == 2 and KTEngine.kill_op_vp(11, 11) == 5, "Kill op: 11-operative row (2/4/7/9/11)")
	var eng := KTEngine.new(505)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	eng.auto_deploy_all()
	eng.auto_initiative()
	for op in eng.state["operatives"]:
		if str(op["side"]) == "B":
			op["incapacitated"] = true
	eng.recalculate_objectives_and_kills()
	_assert_true(str(eng.state["phase"]) != "GAME_OVER", "Wiping out a team does NOT end the battle (p.107)")
	_assert_true(int(eng.state["sides"]["A"]["vp_kill"]) == 5, "Wipeout of a 6-operative team scores 5 Kill VP before the end-of-battle bonus")


func _test_initiative_cards_and_primary_op() -> void:
	var eng := KTEngine.new(606)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	var first := str(eng.state["setup"]["first_side"])
	var second := "B" if first == "A" else "A"
	eng.auto_deploy_all()
	_assert_true((eng.state["sides"][second]["init_cards"] as Array) == [0] and (eng.state["sides"][first]["init_cards"] as Array).is_empty(), "Only the player WITHOUT pre-battle initiative holds the Re-roll card (p.105)")
	_assert_true(int(eng.state["sides"]["A"]["cp"]) == 2, "Each player starts with 2CP (p.106)")

	var res := eng.roll_initiative()
	_assert_true(bool(res["ok"]) and eng.init_pending() and str(eng.state["phase"]) == "INITIATIVE", "Initiative roll opens the initiative-card step")
	# Force a tie: not re-rolled, the player without (pre-battle) initiative wins
	eng.state["init"]["roll"] = {"A": 4, "B": 4}
	_assert_true(eng.init_leader() == second, "Tied TP1 roll-off: player without pre-battle initiative wins")
	# Modifier cards may push the result above 6
	eng.state["init"]["roll"] = {"A": 5, "B": 6}
	eng.state["sides"]["A"]["init_cards"] = [2]
	eng.state["sides"]["B"]["init_cards"] = []
	eng.state["init"]["turn"] = "A"
	eng.state["init"]["passes"] = 0
	_assert_true(KTAI.decide_initiative_card(eng, "A").get("card", -1) == 2, "AI spends a modifier card that flips a losing roll-off")
	eng.init_play_card("A", 2, 1)
	_assert_true(eng.init_value("A") == 7 and str(eng.state["init"]["winner"]) == "A", "5 with +2 card = 7 beats a 6 (results may exceed 6)")
	_assert_true(str(eng.state["phase"]) == "STRATEGY" and str(eng.state["initiative_side"]) == "A", "Card step ends -> winner has initiative, STRATEGY")
	_assert_true((eng.state["sides"]["B"]["init_cards"] as Array) == [1], "TP1 roll-off loser gains the +1/-1 card")
	_assert_true(int(eng.state["sides"]["A"]["cp"]) == 3 and int(eng.state["sides"]["B"]["cp"]) == 3, "TP1 Ready step: each player gains 1CP (2 -> 3)")

	# Re-roll overrides earlier modifiers
	var e2 := KTEngine.new(607)
	e2.start_match("hotseat", "A", "dueller", "hardy")
	e2.auto_deploy_all()
	e2.roll_initiative()
	e2.state["sides"]["A"]["init_cards"] = [0, 3]
	e2.state["sides"]["B"]["init_cards"] = [1]
	e2.state["init"]["turn"] = "A"
	e2.init_play_card("A", 3, -1)
	_assert_true(int(e2.state["init"]["mod"]["A"]) == -3 and str(e2.state["init"]["turn"]) == "B", "-3 card lowers the result; opponent acts next")
	e2.init_pass("B")
	e2.init_play_card("A", 0)
	_assert_true(int(e2.state["init"]["mod"]["A"]) == 0, "Re-roll card overrides all earlier modifiers")

	# Primary Op: bonus = half of that op's VP, rounded up, only at the end of the battle
	eng.choose_primary_op(str(eng.state["turn_side"]), "kill")
	eng.choose_primary_op(str(eng.state["turn_side"]), "key")
	_assert_true(not bool(eng.choose_primary_op(str(eng.state["turn_side"]), "tac")["ok"]), "Primary Op can only be chosen once")
	var kill_side := "A" if str(eng.state["sides"]["A"]["primary_op"]) == "kill" else "B"
	eng.state["sides"][kill_side]["vp_kill"] = 3
	_assert_true(eng.get_total_vp(kill_side) == 3, "Primary Op bonus is not counted before the battle ends")
	eng.state["phase"] = "GAME_OVER"
	_assert_true(eng.get_total_vp(kill_side) == 3 + 2, "Primary Op bonus = ceil(3 / 2) = 2 VP at the end of the battle")


func _test_equipment() -> void:
	var eng := KTEngine.new(707)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	eng.choose_drop_zone(false)
	while str(eng.state["phase"]) == "SELECT":
		var sd := eng.setup_side()
		eng.confirm_roster(sd, eng.default_roster(sd))
	_assert_true(str(eng.state["phase"]) == "EQUIP", "Roster reveal -> EQUIP (secret equipment selection, p.106)")
	_assert_true(eng.equipment_options("A").size() == 4 and eng.equipment_options("B").size() == 4, "Each team offers its 4 faction equipment options")
	var picks := {"A": ["IMP-AOD-EQ-purityseals"], "B": ["CHAOS-PM-EQ-plagueammo", "CHAOS-PM-EQ-blightgrenade"]}
	var es := eng.setup_side()
	_assert_true(not bool(eng.confirm_equipment(es, [picks[es][0], picks[es][0]])["ok"]), "An equipment option cannot be selected twice")
	_assert_true(not bool(eng.confirm_equipment(es, ["NOPE"])["ok"]), "Unknown equipment is rejected")
	eng.confirm_equipment(es, picks[es])
	_assert_true(str(eng.state["phase"]) == "EQUIP", "Equipment stays secret until both players have chosen")
	es = eng.setup_side()
	eng.confirm_equipment(es, picks[es])
	_assert_true(str(eng.state["phase"]) == "TACOP", "Both equipment choices revealed -> TACOP (secret tac op, p.106)")
	while str(eng.state["phase"]) == "TACOP":
		eng.choose_tac_op(eng.setup_side(), "dominate")
	_assert_true(str(eng.state["phase"]) == "DEPLOY", "Both tac ops chosen -> DEPLOY")
	var sb: Dictionary = eng.state["sides"]["B"]
	_assert_true(not bool(sb["has_plague_bell"]) and not KTCombat.has_equipment(eng.state["sides"]["A"], "auspex"), "Unselected equipment has no effect (no Plague Bell, no Auspex)")
	var carriers := 0
	var ammo_ok := true
	var grenade_profile_ok := true
	var thrower: Dictionary = {}
	for op in eng.state["operatives"]:
		if str(op["side"]) != "B":
			continue
		for w in op["weapons"]:
			if str(w["id"]) == KTEngine.EQ_BLIGHT_GRENADE_ID:
				carriers += 1
				thrower = op
				if int(w["hit"]) != 4 or bool(w["rules"]["toxic"]):
					grenade_profile_ok = false
			if str(w["name"]) in ["爆矢枪", "爆矢手枪"] and not (bool(w["rules"]["poison"]) and bool(w["rules"]["severe"])):
				ammo_ok = false
	_assert_true(ammo_ok, "Plague Rounds: boltguns and bolt pistols gain Poison and Severe")
	_assert_true(carriers >= 1 and grenade_profile_ok and int(sb["blight_grenades_left"]) == 2, "Blight grenades: team-wide 4+ weapon, 2 uses per battle")
	var gw: Dictionary = {}
	for w in thrower["weapons"]:
		if str(w["id"]) == KTEngine.EQ_BLIGHT_GRENADE_ID:
			gw = w
	eng.commit_shooting_action_cost(thrower, gw)
	eng.commit_shooting_action_cost(thrower, gw)
	var left := 0
	for op in eng.state["operatives"]:
		for w in op["weapons"]:
			if str(w["id"]) == KTEngine.EQ_BLIGHT_GRENADE_ID:
				left += 1
	_assert_true(left == 0, "After 2 uses nobody has Blight grenades any more")


func _tac_match(seed_v: int, tac_a: String, tac_b: String) -> KTEngine:
	var eng := KTEngine.new(seed_v)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	eng.choose_drop_zone(false)
	while str(eng.state["phase"]) == "SELECT":
		var sd := eng.setup_side()
		eng.confirm_roster(sd, eng.default_roster(sd))
	while str(eng.state["phase"]) == "EQUIP":
		eng.confirm_equipment(eng.setup_side(), [])
	while str(eng.state["phase"]) == "TACOP":
		var ts := eng.setup_side()
		eng.choose_tac_op(ts, tac_a if ts == "A" else tac_b)
	eng.auto_deploy_all()
	eng.auto_initiative()
	return eng


func _side_ops(eng: KTEngine, side_id: String) -> Array:
	var res: Array = []
	for op in eng.state["operatives"]:
		if str(op["side"]) == side_id:
			res.append(op)
	return res


func _test_tac_ops() -> void:
	var e0 := KTEngine.new(808)
	e0.start_match("hotseat", "A", "dueller", "hardy")
	e0.auto_setup()
	_assert_true(e0.tac_op_options("A").size() == 6, "Tac ops: 2 archetypes x 3 = 6 options (p.106)")
	_assert_true(e0.tac_op_of("A") == "dominate" and str(e0.state["phase"]) == "DEPLOY", "auto_setup picks a default tac op and reaches DEPLOY")
	var e1 := KTEngine.new(809)
	e1.start_match("hotseat", "A", "dueller", "hardy")
	e1.choose_drop_zone(false)
	while str(e1.state["phase"]) == "SELECT":
		e1.confirm_roster(e1.setup_side(), e1.default_roster(e1.setup_side()))
	while str(e1.state["phase"]) == "EQUIP":
		e1.confirm_equipment(e1.setup_side(), [])
	_assert_true(not bool(e1.choose_tac_op(e1.setup_side(), "flank")["ok"]), "Tac op of another archetype is rejected")
	_assert_true(not bool(e1.choose_tac_op(e1.setup_side(), "plant_banner")["ok"]), "Not-yet-implemented tac op cannot be picked")

	# 主宰 (A) vs 殉道者 (B)
	var eng := _tac_match(810, "dominate", "martyrs")
	var a_ops := _side_ops(eng, "A")
	var b_ops := _side_ops(eng, "B")
	var obj: Dictionary = eng.state["map"]["objectives"][0]
	var oid := str(obj["id"])
	b_ops[0]["pos"] = obj["pos"]
	b_ops[0]["wounds_cur"] = 0
	b_ops[0]["incapacitated"] = true
	b_ops[0]["killed_by"] = str(a_ops[0]["uid"])
	eng.recalculate_objectives_and_kills()
	var exp_tok := 2 if int(b_ops[0]["wounds_max"]) >= 12 else 1
	_assert_true(int(a_ops[0].get("dominate_tokens", 0)) == exp_tok, "Dominate: killer gains a token (two vs a 12+ wound operative, V11.5)")
	_assert_true(int(eng.state["objectives_state"][oid].get("martyr_B", 0)) == 1, "Martyrs: operative incapacitated while contesting leaves a marker")
	eng.recalculate_objectives_and_kills()
	_assert_true(int(a_ops[0]["dominate_tokens"]) == exp_tok, "Casualties are only processed once")
	eng.state["tp"] = 2
	eng._score_tac_op_end_of_tp()
	_assert_true(int(eng.state["sides"]["A"]["vp_tac"]) == 0, "Dominate does not score before the end of TP3")
	_assert_true(int(eng.state["sides"]["B"]["vp_tac"]) == 0, "Martyrs: no VP while nobody contests the objective")
	b_ops[1]["pos"] = obj["pos"]
	eng.recalculate_objectives_and_kills()
	eng.state["tp"] = 3
	eng._score_tac_op_end_of_tp()
	_assert_true(int(eng.state["sides"]["A"]["vp_tac"]) == exp_tok and int(a_ops[0]["dominate_tokens"]) == 0, "Dominate: end of TP3 removes tokens for 1VP each")
	_assert_true(int(eng.state["sides"]["B"]["vp_tac"]) == 2 and int(eng.state["objectives_state"][oid]["martyr_B"]) == 0, "Martyrs: marker on a controlled objective is worth 2VP")

	# 特使 (A) vs 击溃 (B)
	var e2 := _tac_match(811, "envoy", "rout")
	a_ops = _side_ops(e2, "A")
	b_ops = _side_ops(e2, "B")
	e2.state["tp"] = 2
	e2.state["phase"] = "STRATEGY"
	e2.state["turn_side"] = "A"
	var dz_a: Rect2 = e2.state["map"]["drop_zone_a"]
	var dz_b: Rect2 = e2.state["map"]["drop_zone_b"]
	b_ops[0]["pos"] = dz_a.get_center()
	a_ops[5]["wounds_cur"] = 0
	a_ops[5]["incapacitated"] = true
	a_ops[5]["killed_by"] = str(b_ops[0]["uid"])
	e2.recalculate_objectives_and_kills()
	_assert_true(int(e2.state["sides"]["B"]["vp_tac"]) == (2 if int(a_ops[5]["wounds_max"]) >= 12 else 1), "Rout: kill made within 6\" of the enemy drop zone scores (2VP vs 12+ wounds)")
	_assert_true(e2.can_choose_envoy("A"), "Envoy can be named as a strategic gambit from TP2")
	var env_uid := str(a_ops[0]["uid"])
	_assert_true(bool(e2.choose_envoy("A", env_uid)["ok"]) and str(e2.state["turn_side"]) == "B", "Naming the Envoy uses the strategic gambit")
	for b in b_ops:
		b["pos"] = Vector2(-99.0, -99.0)
	a_ops[0]["pos"] = dz_b.get_center()
	e2._score_tac_op_end_of_tp()
	_assert_true(int(e2.state["sides"]["A"]["vp_tac"]) == 2, "Envoy wholly in enemy territory, unhurt this TP = 2VP")
	e2.state["tp"] = 3
	e2.state["turn_side"] = "A"
	_assert_true(not bool(e2.choose_envoy("A", env_uid)["ok"]), "Last turning point's Envoy cannot be named again")
	e2.choose_envoy("A", str(a_ops[1]["uid"]))
	a_ops[1]["pos"] = dz_b.get_center()
	a_ops[1]["wounds_cur"] = int(a_ops[1]["wounds_cur"]) - 1
	e2.recalculate_objectives_and_kills()
	e2._score_tac_op_end_of_tp()
	_assert_true(int(e2.state["sides"]["A"]["vp_tac"]) == 3, "Damaged Envoy scores only 1VP")


func _test_all_official_maps() -> void:
	_assert_true(KTData.MAP_IDS.size() == 6, "All six Approved Ops 2025 Volkus maps are listed")
	var all_ok := true
	var zones_ok := true
	var spawns_ok := true
	var deploy_ok := true
	for map_id in KTData.MAP_IDS:
		var num := int(map_id.right(2))
		var eng := KTEngine.new(300 + num)
		eng.start_match("hotseat", "A", "dueller", "hardy", map_id)
		var m: Dictionary = eng.state["map"]
		if str(m.get("id", "")) != map_id or (m["objectives"] as Array).size() != 3 or (m["visuals"] as Array).size() != 14:
			all_ok = false
		# Rulebook: maps 1-3 deploy in 6" strips on the short edges, maps 4-6 in 3" strips on the long edges.
		var dz_a: Rect2 = m["drop_zone_a"]
		var dz_b: Rect2 = m["drop_zone_b"]
		var want := Vector2(6.0, 22.0) if num <= 3 else Vector2(30.0, 3.0)
		if not dz_a.size.is_equal_approx(want) or not dz_b.size.is_equal_approx(want) or dz_a.intersects(dz_b):
			zones_ok = false
		for t in m["terrains"]:
			var tr: Rect2 = t["rect"]
			if tr.position.x < -0.01 or tr.position.y < -0.01 or tr.end.x > 30.01 or tr.end.y > 22.01:
				all_ok = false
		for key in ["spawn_a", "spawn_b"]:
			var dz: Rect2 = dz_a if key == "spawn_a" else dz_b
			for sp in m[key]:
				var spv: Vector2 = sp
				if not dz.grow(-0.79).has_point(spv):
					spawns_ok = false
				for t in m["terrains"]:
					if KTGeometry.circle_intersects_rect(spv, 0.79, t["rect"]):
						spawns_ok = false
		eng.auto_deploy_all()
		if str(eng.state["phase"]) != "INITIATIVE":
			deploy_ok = false
		for o in eng.state["operatives"]:
			var own: Rect2 = eng.state["map"]["drop_zone_a"] if str(o["side"]) == "A" else eng.state["map"]["drop_zone_b"]
			var opos: Vector2 = o["pos"]
			if not bool(o.get("deployed", false)) or not own.has_point(opos):
				deploy_ok = false
	_assert_true(all_ok, "Every official map loads with 3 objectives, 14 terrain pieces and all rule rects on the board")
	_assert_true(zones_ok, "Maps 1-3 use 6\" short-edge drop zones, maps 4-6 use 3\" long-edge drop zones")
	_assert_true(spawns_ok, "Default spawn spots sit in the drop zone clear of all terrain on every map")
	_assert_true(deploy_ok, "Both kill teams can fully deploy inside their drop zones on every map")


func _test_universal_equipment() -> void:
	var eng := KTEngine.new(909)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	eng.choose_drop_zone(false)
	while str(eng.state["phase"]) == "SELECT":
		eng.confirm_roster(eng.setup_side(), eng.default_roster(eng.setup_side()))
	_assert_true(KTEngine.universal_equipment_options().size() == 11, "Universal equipment catalogue lists 11 rulebook options (p.98-103)")
	_assert_true(not bool(eng.confirm_equipment(eng.setup_side(), ["UNI-EQ-nope"])["ok"]), "Unknown universal equipment is rejected")
	var picks := {"A": ["UNI-EQ-lightbarricades", "UNI-EQ-mines"], "B": ["UNI-EQ-heavybarricade"]}
	while str(eng.state["phase"]) == "EQUIP":
		var es := eng.setup_side()
		eng.confirm_equipment(es, picks[es])
	eng.auto_setup()
	_assert_true(str(eng.state["phase"]) == "EQDEPLOY", "Pre-battle equipment is set up before operatives (p.106 step 3)")
	var first := str(eng.state["setup"]["first_side"])
	_assert_true(eng.eq_deploy_side() == first and eng.eq_pending("A").size() == 3 and eng.eq_pending("B").size() == 1, "Initiative player sets up first; 2 light barricades + 1 mine vs 1 heavy barricade")
	var enemy_dz: Rect2 = eng.state["map"]["drop_zone_b"] if first == "A" else eng.state["map"]["drop_zone_a"]
	var k0 := str(eng.eq_pending(first)[0])
	_assert_true(not bool(eng.validate_equipment_pos(first, k0, enemy_dz.get_center(), false)["ok"]), "Equipment cannot be set up outside own territory")
	var n_terr: int = (eng.state["map"]["terrains"] as Array).size()
	var order: Array = []
	var guard := 0
	while str(eng.state["phase"]) == "EQDEPLOY" and guard < 12:
		guard += 1
		order.append(eng.eq_deploy_side())
		eng.ai_eq_deploy_next()
	_assert_true(order.size() == 4 and str(order[0]) == first and str(order[1]) != first, "Players alternate one item at a time")
	_assert_true(str(eng.state["phase"]) == "DEPLOY", "All equipment set up -> operatives DEPLOY")
	_assert_true((eng.state["map"]["terrains"] as Array).size() == n_terr + 3 and (eng.state["eq_markers"] as Array).size() == 1, "Barricades become terrain (2 light + 1 heavy), the mine a marker")
	var zones_ok := true
	for t in eng.state["map"]["terrains"]:
		if bool(t.get("equipment", false)):
			var kind := "light_barricade" if str(t["kind"]) == "light" else "heavy_barricade"
			if not eng.equipment_zone(str(t["side"]), kind).encloses(t["rect"]):
				zones_ok = false
	_assert_true(zones_ok, "Light barricades wholly in own territory, heavy barricade wholly within 4\" of own drop zone")
	eng.auto_deploy_all()
	_assert_true(str(eng.state["phase"]) == "INITIATIVE" and (eng.state["eq_markers"] as Array).size() == 1, "Operatives deploy around the equipment without setting the mine off")
	var mine: Dictionary = eng.state["eq_markers"][0]
	var victim: Dictionary = eng.state["operatives"][0]
	var hp := int(victim["wounds_cur"])
	_assert_true(eng._mine_check(victim, (mine["pos"] as Vector2) + Vector2(0.9, 0.0)), "Mine goes off the first time it is within an operative's control range")
	var lost := hp - int(victim["wounds_cur"])
	_assert_true(lost >= 4 and lost <= 6 and (eng.state["eq_markers"] as Array).is_empty(), "Mine inflicts D3+3 damage and is removed")


func _test_universal_equipment_deployables() -> void:
	# 1. Pre-battle pending item counts for all 5 new deployable equipment options (ladder x2, others x1)
	var e_pend := KTEngine.new(950)
	e_pend.start_match("hotseat", "A", "dueller", "hardy")
	e_pend.choose_drop_zone(false)
	while str(e_pend.state["phase"]) == "SELECT":
		var sd := e_pend.setup_side()
		e_pend.confirm_roster(sd, e_pend.default_roster(sd))
	var picks5 := {
		"A": ["UNI-EQ-ammocache", "UNI-EQ-razorwire", "UNI-EQ-comms", "UNI-EQ-ladders"],
		"B": ["UNI-EQ-portablebarricade"],
	}
	while str(e_pend.state["phase"]) == "EQUIP":
		var es := e_pend.setup_side()
		e_pend.confirm_equipment(es, picks5[es])
	e_pend.auto_setup()
	var pend_a: Array = e_pend.eq_pending("A")
	var pend_b: Array = e_pend.eq_pending("B")
	_assert_true(
		str(e_pend.state["phase"]) == "EQDEPLOY" and pend_a.size() == 5 and pend_a.count("ladder") == 2 and pend_b == ["portable_barricade"],
		"Selecting the 5 new universal equipment options queues expected eq_pending counts (2 ladders)"
	)

	# 2. Ladder placement validation & Comms marker deployment
	_assert_true(
		not bool(e_pend.validate_equipment_pos("A", "ladder", Vector2(2.0, 15.0), false)["ok"]),
		"Ladder deployment in open ground far from tall terrain is rejected"
	)
	_assert_true(
		bool(e_pend.validate_equipment_pos("A", "ladder", Vector2(7.0, 15.3), false)["ok"]),
		"Ladder deployment within 0.35\" outside a >=2\" vantage edge is accepted"
	)
	if e_pend.eq_deploy_side() == "B":
		e_pend.ai_eq_deploy_next()
	var comms_res: Dictionary = e_pend.deploy_equipment("A", "comms", Vector2(9.5, 13.0), false)
	var has_comms := false
	for m in e_pend.state.get("eq_markers", []):
		if str(m.get("kind", "")) == "comms" and str(m.get("side", "")) == "A":
			has_comms = true
	_assert_true(bool(comms_res["ok"]) and has_comms, "Comms device deploys into eq_markers")
	if e_pend.eq_deploy_side() == "B" and not e_pend.eq_pending("B").is_empty():
		e_pend.ai_eq_deploy_next()
	var lad1_res: Dictionary = e_pend.deploy_equipment("A", "ladder", Vector2(7.0, 15.3), false)
	_assert_true(
		bool(lad1_res["ok"]) and not bool(e_pend.validate_equipment_pos("A", "ladder", Vector2(8.0, 15.3), false)["ok"]),
		"Second ladder within 2\" of an existing ladder is rejected"
	)

	# 3. Light terrain (+2\"), Razor Wire (+3\", visibility, impassable stand), and Ladder (-1\" climb)
	var eng := KTEngine.new(951)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	eng.auto_deploy_all()
	eng.auto_initiative()
	eng.choose_primary_op(str(eng.state["turn_side"]), "kill")
	eng.choose_primary_op(str(eng.state["turn_side"]), "key")
	eng.pass_strategy(str(eng.state["turn_side"]))
	eng.pass_strategy(str(eng.state["turn_side"]))

	# Park all operatives out of the test corridor (x ~ 9.5, y ~ 6..15)
	for idx in range(eng.state["operatives"].size()):
		var o: Dictionary = eng.state["operatives"][idx]
		o["pos"] = Vector2(1.2 + 0.9 * float(idx % 3), 20.5 if str(o["side"]) == "A" else 1.5)

	var a_ops := _side_ops(eng, "A")
	var b_ops := _side_ops(eng, "B")
	var mover: Dictionary = a_ops[0]
	var terrains: Array = eng.state["map"]["terrains"]

	# Ladder climb discount onto large_ruin_deck_3 (Rect2(4.5, 15.5, 6.5, 4.0), +3.0\") on Map 4
	mover["pos"] = Vector2(7.0, 14.5)
	var climb_pts: Array[Vector2] = [Vector2(7.0, 14.5), Vector2(7.0, 16.5)]
	eng.state["eq_markers"] = []
	var res_no_lad: Dictionary = KTGeometry.validate_move_path(climb_pts, mover, "reposition", 6.0, eng.state)
	eng.state["eq_markers"] = [{"kind": "ladder", "side": "B", "pos": Vector2(7.0, 15.3)}]
	var res_lad: Dictionary = KTGeometry.validate_move_path(climb_pts, mover, "reposition", 6.0, eng.state)
	_assert_true(
		bool(res_lad["ok"]) and absf(float(res_no_lad["cost"]) - float(res_lad["cost"]) - 1.0) < 0.05,
		"Ladder outside vantage edge reduces climb path cost by 1\" for either side"
	)

	# Keep only large_ruin_deck_3 while testing synthetic equipment terrain pieces in an open corridor
	var saved_terrains: Array = terrains.duplicate(true)
	terrains.clear()
	for st in saved_terrains:
		if str(st.get("id", "")) == "large_ruin_deck_3":
			terrains.append(st)

	# Light terrain crossing: 4\" horizontal + 2\" minimum climb = 6\"
	var test_light := {
		"id": "test_light_eq",
		"name": "轻型掩体",
		"kind": "light",
		"elevation": 1.0,
		"rect": KTEngine.equipment_rect("light_barricade", Vector2(15.0, 10.0), false),
		"equipment": true,
		"side": "A",
	}
	terrains.append(test_light)
	mover["pos"] = Vector2(15.0, 8.0)
	var cross_pts: Array[Vector2] = [Vector2(15.0, 8.0), Vector2(15.0, 12.0)]
	var res_light: Dictionary = KTGeometry.validate_move_path(cross_pts, mover, "reposition", 8.0, eng.state)
	_assert_true(
		bool(res_light["ok"]) and absf(float(res_light["cost"]) - 6.0) < 0.08,
		"Traversing light terrain costs straight-line distance + 2\""
	)
	terrains.erase(test_light)

	# Razor wire crossing: 4\" horizontal + 2\" climb + 1\" Obstructing = 7\"; does not block LoS; cannot stand on it
	var test_rw := {
		"id": "test_rw_eq",
		"name": "铁丝网",
		"kind": "razor_wire",
		"elevation": 1.5,
		"rect": KTEngine.equipment_rect("razor_wire", Vector2(15.0, 10.0), false),
		"equipment": true,
		"side": "A",
	}
	terrains.append(test_rw)
	var res_rw: Dictionary = KTGeometry.validate_move_path(cross_pts, mover, "reposition", 8.0, eng.state)
	_assert_true(
		bool(res_rw["ok"]) and absf(float(res_rw["cost"]) - 7.0) < 0.08,
		"Traversing razor wire costs straight-line distance + 3\""
	)
	_assert_true(
		KTGeometry.has_visibility(Vector2(15.0, 8.0), 0.63, 0.0, Vector2(15.0, 12.0), 0.63, 0.0, terrains),
		"Razor wire does not block visibility"
	)
	_assert_true(
		not bool(KTGeometry.is_valid_stand_pos(Vector2(15.0, 10.0), float(mover["base_radius"]), str(mover["uid"]), eng.state)["ok"]),
		"Operative cannot stand on razor wire"
	)
	mover["pos"] = Vector2(15.0, 10.8)
	mover["order"] = "engage"
	var shooter_b: Dictionary = b_ops[0]
	shooter_b["pos"] = Vector2(15.0, 7.0)
	shooter_b["order"] = "engage"
	var b_gun: Dictionary = eng.get_usable_ranged_weapons(shooter_b)[0]
	var ev_rw: Dictionary = KTGeometry.evaluate_shot(shooter_b, mover, b_gun, eng.state)
	_assert_true(
		bool(ev_rw["valid"]) and str(ev_rw["cover"]) == "none" and not bool(ev_rw["obscured"]),
		"Razor wire is Exposed and grants neither cover nor obscured"
	)
	terrains.erase(test_rw)

	# 4. Ammo Cache: 0AP eq_resupply, once per TP per marker, enemy marker rejected, ammo_reroll on datacard weapons
	eng.state["eq_markers"] = [
		{"kind": "ammo_cache", "side": "A", "pos": Vector2(9.5, 14.0)},
		{"kind": "ammo_cache", "side": "B", "pos": Vector2(9.5, 6.0)},
	]
	var op_a1: Dictionary = a_ops[0]
	var op_a2: Dictionary = a_ops[1]
	op_a1["pos"] = Vector2(9.5, 6.8)
	_assert_true(
		not bool(eng.execute_unique_action(str(op_a1["uid"]), "eq_resupply")["ok"]),
		"Operative cannot use an enemy ammo cache"
	)
	op_a1["pos"] = Vector2(9.5, 14.8)
	op_a1["ap_left"] = 3
	var sup_ok: Dictionary = eng.execute_unique_action(str(op_a1["uid"]), "eq_resupply")
	_assert_true(
		bool(sup_ok["ok"]) and int(op_a1["ap_left"]) == 3,
		"Friendly controlled ammo cache allows eq_resupply without spending AP"
	)
	op_a2["pos"] = Vector2(10.2, 14.0)
	_assert_true(
		not bool(eng.execute_unique_action(str(op_a2["uid"]), "eq_resupply")["ok"]),
		"Second operative cannot reuse the same ammo cache in the same turning point"
	)
	op_a1["order"] = "engage"
	shooter_b["pos"] = Vector2(9.5, 10.5)
	shooter_b["order"] = "engage"
	var a1_gun: Dictionary = eng.get_usable_ranged_weapons(op_a1)[0]
	var ev_ammo: Dictionary = KTGeometry.evaluate_shot(op_a1, shooter_b, a1_gun, eng.state)
	var sess_ammo: Dictionary = KTCombat.create_shooting_session(op_a1, shooter_b, a1_gun, ev_ammo, eng.state, eng.rng)
	_assert_true(
		bool(sess_ammo["ammo_reroll"]) and not bool(sess_ammo["ammo_reroll_used"]),
		"Shooting session after eq_resupply sets ammo_reroll == true for datacard weapons"
	)
	var rr1: Dictionary = KTCombat.reroll_shooting_die(sess_ammo, "atk_dice", 0, "ammo", eng.state, eng.rng)
	var rr2: Dictionary = KTCombat.reroll_shooting_die(sess_ammo, "atk_dice", 1, "ammo", eng.state, eng.rng)
	_assert_true(
		bool(rr1["ok"]) and bool(sess_ammo["ammo_reroll_used"]) and not bool(rr2["ok"]),
		"Ammo reroll rerolls at most one attack die per shooting session"
	)
	var eq_weapon: Dictionary = a1_gun.duplicate(true)
	eq_weapon["id"] = "eq_frag_grenade"
	var sess_eq_w: Dictionary = KTCombat.create_shooting_session(op_a1, shooter_b, eq_weapon, ev_ammo, eng.state, eng.rng)
	_assert_true(
		not bool(sess_eq_w["ammo_reroll"]),
		"Equipment weapons (id starting with eq_) do not gain ammo_reroll"
	)

	# 5. Portable Barricade: contact-only cover, protective save +1, Move With Barricade (Reposition - 2\", no climb, redeploy)
	eng.state["eq_markers"] = []
	var pb_entry := {
		"id": "test_portable_eq",
		"name": "可移动掩体",
		"kind": "light",
		"elevation": 1.6,
		"blocks_los_ground": false,
		"rect": KTEngine.equipment_rect("portable_barricade", Vector2(9.5, 12.5), false),
		"parent": "",
		"equipment": true,
		"portable": true,
		"side": "A",
	}
	terrains.append(pb_entry)
	var r_a1 := float(op_a1["base_radius"])
	shooter_b["pos"] = Vector2(9.5, 7.5)
	shooter_b["order"] = "engage"
	var plain_gun := {
		"id": "test_bolter",
		"name": "爆矢枪",
		"kind": "ranged",
		"attacks": 4,
		"hit": 3,
		"normal_dmg": 3,
		"crit_dmg": 4,
		"rules": {},
	}
	# Contact: dist_def_to_terr = 0.1 <= 0.2
	op_a1["pos"] = Vector2(9.5, 12.5 + 0.35 + r_a1 + 0.1)
	op_a1["order"] = "engage"
	var ev_pb_contact: Dictionary = KTGeometry.evaluate_shot(shooter_b, op_a1, plain_gun, eng.state)
	_assert_true(
		str(ev_pb_contact["cover"]) == "standard" and bool(ev_pb_contact["protective"]),
		"Portable barricade grants cover and protective == true when operative is in contact"
	)
	var sess_pb: Dictionary = KTCombat.create_shooting_session(shooter_b, op_a1, plain_gun, ev_pb_contact, eng.state, eng.rng)
	_assert_true(
		int(sess_pb["def_save"]) == maxi(2, int(op_a1["save"]) - 1),
		"Protective rule improves defender save by 1 (max 2+)"
	)
	# Step back to 0.6\" from barricade: no longer in contact -> no cover
	op_a1["pos"] = Vector2(9.5, 12.5 + 0.35 + r_a1 + 0.6)
	var ev_pb_far: Dictionary = KTGeometry.evaluate_shot(shooter_b, op_a1, plain_gun, eng.state)
	_assert_true(
		str(ev_pb_far["cover"]) == "none" and not bool(ev_pb_far["protective"]),
		"Portable barricade no longer grants cover once operative is 0.6\" away"
	)

	# Activate op_a1 in contact and execute Move With Barricade
	shooter_b["pos"] = Vector2(28.0, 2.0)
	op_a1["pos"] = Vector2(9.5, 12.5 + 0.35 + r_a1 + 0.1)
	op_a1["ready"] = true
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.activate_operative(str(op_a1["uid"]), "engage")
	_assert_true(
		absf(eng.get_effective_move_inches(op_a1, "barricade_move") - (eng.get_effective_move_inches(op_a1, "reposition") - 2.0)) < 0.01,
		"Move With Barricade max distance equals Reposition distance minus 2\""
	)
	var bad_climb_pts: Array[Vector2] = [op_a1["pos"] as Vector2, Vector2(7.0, 16.5)]
	_assert_true(
		not bool(eng.execute_move(str(op_a1["uid"]), "barricade_move", bad_climb_pts)["ok"]),
		"Move With Barricade rejects paths that climb to a different elevation"
	)
	var dst_pb: Vector2 = (op_a1["pos"] as Vector2) + Vector2(0.0, -2.0)
	var bm_pts: Array[Vector2] = [op_a1["pos"] as Vector2, dst_pb]
	var bm_res: Dictionary = eng.execute_move(str(op_a1["uid"]), "barricade_move", bm_pts)
	var expected_pb_center := dst_pb + Vector2(0.0, -1.0) * (r_a1 + 0.4)
	var pb_followed := false
	for t in eng.state["map"]["terrains"]:
		if str(t.get("id", "")) == "test_portable_eq":
			var tr: Rect2 = t["rect"]
			if tr.get_center().distance_to(expected_pb_center) < 0.05:
				pb_followed = true
	_assert_true(
		bool(bm_res["ok"]) and pb_followed,
		"Move With Barricade moves the portable barricade rect to the new front position"
	)
	# Next activation: moving the portable barricade right up to the board edge removes it instead of redeploying out of bounds
	op_a1["pos"] = Vector2(15.0, 1.5)
	pb_entry["rect"] = KTEngine.equipment_rect("portable_barricade", Vector2(15.0, 1.5 - r_a1 - 0.4), false)
	op_a1["ready"] = true
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.activate_operative(str(op_a1["uid"]), "engage")
	var edge_dst := Vector2(15.0, r_a1 + 0.1)
	var bm_edge: Dictionary = eng.execute_move(str(op_a1["uid"]), "barricade_move", [op_a1["pos"] as Vector2, edge_dst])
	var pb_still_on_board := false
	for t in eng.state["map"]["terrains"]:
		if str(t.get("id", "")) == "test_portable_eq":
			pb_still_on_board = true
	_assert_true(
		bool(bm_edge["ok"]) and not pb_still_on_board,
		"Portable barricade is removed from killzone when its new placement would go out of bounds"
	)


func _test_grenade_equipment() -> void:
	# 1. Selection & grenade_mix opts validation + Breaching Charge
	var eng := KTEngine.new(980)
	eng.start_match("hotseat", "A", "dueller", "hardy")
	eng.choose_drop_zone(false)
	while str(eng.state["phase"]) == "SELECT":
		var sd := eng.setup_side()
		eng.confirm_roster(sd, eng.default_roster(sd))

	var cur_eq_side := eng.setup_side()
	var bad1: Dictionary = eng.confirm_equipment(cur_eq_side, ["UNI-EQ-utilitygrenades"], {"utility": "invalid"})
	var bad2: Dictionary = eng.confirm_equipment(cur_eq_side, ["UNI-EQ-explosivegrenades"], {"explosive": "frag3"})
	var bad3: Dictionary = eng.confirm_equipment(cur_eq_side, ["UNI-EQ-utilitygrenades"], {"unknown_key": "mix"})
	_assert_true(
		not bool(bad1["ok"]) and not bool(bad2["ok"]) and not bool(bad3["ok"]),
		"Invalid grenade mix opts are rejected by confirm_equipment"
	)

	while str(eng.state["phase"]) == "EQUIP":
		var es := eng.setup_side()
		if es == "A":
			var ok_a: Dictionary = eng.confirm_equipment(
				"A",
				["UNI-EQ-explosivegrenades", "UNI-EQ-utilitygrenades", "UNI-EQ-breachingcharge"],
				{"explosive": "frag2"}
			)
			if not bool(ok_a["ok"]):
				_assert_true(false, "Side A valid grenade equipment confirmation failed")
		else:
			eng.confirm_equipment("B", ["UNI-EQ-explosivegrenades", "UNI-EQ-utilitygrenades"])

	while str(eng.state["phase"]) == "TACOP":
		eng.choose_tac_op(eng.setup_side(), "dominate")

	_assert_true(
		str(eng.state["phase"]) == "DEPLOY" and eng.eq_pending("A").is_empty() and eng.eq_pending("B").is_empty(),
		"Breaching charge is accepted and adds nothing to eq_pending"
	)
	_assert_true(
		eng.grenades_left("B", "frag") == 1 and eng.grenades_left("B", "krak") == 1 and eng.grenades_left("B", "smoke") == 1 and eng.grenades_left("B", "stun") == 1,
		"Default grenade opts allocate 1 of each selected grenade type"
	)

	var any_a_krak := false
	for op in eng.state["operatives"]:
		if str(op["side"]) == "A":
			for w in op["weapons"]:
				if str(w["id"]) == KTEngine.EQ_KRAK_GRENADE_ID:
					any_a_krak = true
	_assert_true(
		eng.grenades_left("A", "frag") == 2 and eng.grenades_left("A", "krak") == 0 and not any_a_krak,
		"Opts explosive=frag2 gives 2 frag and 0 krak, and nobody on that side gets eq_krak_grenade"
	)

	# 2. Explosive grenades: ordinary operatives vs grenadier specialists, and team-wide removal at 0
	var cap: Dictionary = _find_by_op_id(eng, "aod_captain")
	var pm_champ: Dictionary = _find_by_op_id(eng, "pm_champion")
	var cap_frag: Dictionary = {}
	for w in cap["weapons"]:
		if str(w["id"]) == KTEngine.EQ_FRAG_GRENADE_ID:
			cap_frag = w
	var pm_has_frag_4 := false
	for w in pm_champ["weapons"]:
		if str(w["id"]) == KTEngine.EQ_FRAG_GRENADE_ID and int(w["hit"]) == 4:
			pm_has_frag_4 = true
	_assert_true(
		not cap_frag.is_empty() and int(cap_frag["hit"]) == 4 and str(cap_frag["profile"]).contains("4+") and pm_has_frag_4,
		"Ordinary operatives on both teams receive eq_frag_grenade with 4+ hit stat"
	)

	var grenadier: Dictionary = _find_by_op_id(eng, "aod_grenadier")
	var bombardier: Dictionary = _find_by_op_id(eng, "pm_bombardier")
	var spec_has_eq := false
	for sp in [grenadier, bombardier]:
		for w in sp["weapons"]:
			if str(w["id"]) in [KTEngine.EQ_FRAG_GRENADE_ID, KTEngine.EQ_KRAK_GRENADE_ID]:
				spec_has_eq = true
	_assert_true(
		not spec_has_eq,
		"Grenadier specialists (aod_grenadier / pm_bombardier) do not receive eq_ grenades"
	)

	eng.auto_deploy_all()
	eng.auto_initiative()
	eng.choose_primary_op(str(eng.state["turn_side"]), "kill")
	eng.choose_primary_op(str(eng.state["turn_side"]), "key")
	eng.pass_strategy(str(eng.state["turn_side"]))
	eng.pass_strategy(str(eng.state["turn_side"]))

	# Verify KTAI handles universal grenades cleanly before consuming them
	var ai_plan: Dictionary = KTAI.decide_next_operative_action(eng, pm_champ)
	_assert_true(ai_plan.has("kind"), "KTAI action planner handles operatives with universal grenades cleanly")

	eng.commit_shooting_action_cost(cap, cap_frag)
	var after_one := eng.grenades_left("A", "frag")
	eng.commit_shooting_action_cost(cap, cap_frag)
	var frag_carriers_after := 0
	for op in eng.state["operatives"]:
		if str(op["side"]) == "A":
			for w in op["weapons"]:
				if str(w["id"]) == KTEngine.EQ_FRAG_GRENADE_ID:
					frag_carriers_after += 1
	_assert_true(
		after_one == 1 and eng.grenades_left("A", "frag") == 0 and frag_carriers_after == 0,
		"Using team explosive grenades up to the limit removes the weapon from the whole team"
	)

	# 3. Smoke grenade: 1AP placement, range check, obscured (>2\"), close range (<=2\"), ignore_obscured, Piercing 2->1
	var terrains: Array = eng.state["map"]["terrains"]
	terrains.clear()
	for idx in range(eng.state["operatives"].size()):
		var o: Dictionary = eng.state["operatives"][idx]
		o["pos"] = Vector2(1.5 + 1.1 * float(idx % 6), 20.5 if str(o["side"]) == "A" else 1.5)
		o["ready"] = true
		o["order"] = "engage"

	var sniper: Dictionary = _find_by_op_id(eng, "aod_sniper")
	sniper["pos"] = Vector2(10.0, 15.0)
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.activate_operative(str(sniper["uid"]), "engage")
	var ap_before_smoke := int(sniper["ap_left"])
	var ua_smoke_err: Dictionary = eng.execute_unique_action(str(sniper["uid"]), "eq_smoke")
	var far_smoke_res: Dictionary = eng.use_smoke_grenade(str(sniper["uid"]), Vector2(10.0, 7.0))
	_assert_true(
		not bool(ua_smoke_err["ok"]) and not bool(far_smoke_res["ok"]) and int(sniper["ap_left"]) == ap_before_smoke,
		"Smoke grenade placement beyond 6\" (or without target point) is rejected without spending AP"
	)

	var smoke_pos := Vector2(10.0, 10.0)
	var ok_smoke: Dictionary = eng.use_smoke_grenade(str(sniper["uid"]), smoke_pos)
	var smoke_markers := 0
	for m in eng.state.get("eq_markers", []):
		if str((m as Dictionary).get("kind", "")) == "smoke":
			smoke_markers += 1
	_assert_true(
		bool(ok_smoke["ok"]) and smoke_markers == 1 and int(sniper["ap_left"]) == ap_before_smoke - 1 and eng.grenades_left("A", "smoke") == 0,
		"Placing smoke grenade adds smoke marker, spends 1 AP, and decrements smoke count"
	)

	var smoke_def: Dictionary = _find_by_op_id(eng, "pm_fighter")
	smoke_def["pos"] = smoke_pos # distance 0 + radius 0.63 <= 1.4 -> wholly in smoke
	smoke_def["order"] = "engage"
	var shooter_far: Dictionary = _find_by_op_id(eng, "aod_gunner")
	shooter_far["pos"] = Vector2(10.0, 14.5) # edge_distance ~ 3.24\" > 2.0\"
	shooter_far["order"] = "engage"
	shooter_far["ignore_obscured"] = false
	var melta := {
		"id": "test_melta",
		"name": "热熔枪",
		"kind": "ranged",
		"attacks": 4,
		"hit": 3,
		"normal_dmg": 6,
		"crit_dmg": 3,
		"rules": {"piercing": 2, "piercing_crits": 2, "devastating": 4},
	}
	var ev_far_smoke: Dictionary = KTGeometry.evaluate_shot(shooter_far, smoke_def, melta, eng.state)
	_assert_true(
		bool(ev_far_smoke["valid"]) and bool(ev_far_smoke["obscured"]) and bool(ev_far_smoke["smoke"]),
		"Target wholly in smoke is obscured and marked smoke==true against shooter >2\" away"
	)

	# Vice versa: shooter wholly in smoke shooting target >2\" outside smoke -> obscured == true, smoke == false
	smoke_def["pos"] = Vector2(14.5, 10.0)
	shooter_far["pos"] = smoke_pos
	var ev_vice_versa: Dictionary = KTGeometry.evaluate_shot(shooter_far, smoke_def, melta, eng.state)
	_assert_true(
		bool(ev_vice_versa["valid"]) and bool(ev_vice_versa["obscured"]) and not bool(ev_vice_versa["smoke"]),
		"Shooter wholly in smoke also suffers obscured against target >2\" outside smoke"
	)

	# Restore target in smoke, test shooter within 1.5\" edge distance
	smoke_def["pos"] = smoke_pos
	var r_sum := float(shooter_far["base_radius"]) + float(smoke_def["base_radius"])
	shooter_far["pos"] = smoke_pos + Vector2(0.0, r_sum + 1.5)
	var ev_close_smoke: Dictionary = KTGeometry.evaluate_shot(shooter_far, smoke_def, melta, eng.state)
	_assert_true(
		bool(ev_close_smoke["valid"]) and not bool(ev_close_smoke["obscured"]) and bool(ev_close_smoke["smoke"]),
		"Shooter within 1.5\" of target in smoke is not obscured"
	)

	# Sniper with ignore_obscured >2\" away ignores smoke obscuration
	sniper["pos"] = Vector2(10.0, 15.0)
	sniper["ignore_obscured"] = true
	var ev_optics_smoke: Dictionary = KTGeometry.evaluate_shot(sniper, smoke_def, melta, eng.state)
	_assert_true(
		bool(ev_optics_smoke["valid"]) and not bool(ev_optics_smoke["obscured"]) and bool(ev_optics_smoke["smoke"]),
		"Shooter with ignore_obscured is not obscured by smoke"
	)

	# Piercing 2 -> 1 in create_shooting_session against target in smoke
	var sess_smoke: Dictionary = KTCombat.create_shooting_session(shooter_far, smoke_def, melta, ev_far_smoke, eng.state, eng.rng)
	var sess_rules: Dictionary = sess_smoke["rules"]
	_assert_true(
		int(sess_rules["piercing"]) == 1 and int(sess_rules["piercing_crits"]) == 1,
		"Piercing 2 weapon shooting target in smoke has piercing reduced to 1 in session"
	)

	# 4. Smoke duration: survives current TP _finish_turning_point -> Ready step rolls D3 -> removed after acts_left normal activations
	eng.end_operative_activation()
	eng._finish_turning_point()
	var smoke_after_tp1: Dictionary = {}
	for m in eng.state.get("eq_markers", []):
		if str((m as Dictionary).get("kind", "")) == "smoke":
			smoke_after_tp1 = m
	_assert_true(
		not smoke_after_tp1.is_empty() and int(smoke_after_tp1["acts_left"]) == -1,
		"Smoke placed in current turning point survives _finish_turning_point"
	)

	eng.auto_initiative()
	var acts_rolled := int(smoke_after_tp1.get("acts_left", -1))
	_assert_true(
		acts_rolled >= 1 and acts_rolled <= 3,
		"Next Ready step rolls D3 (1..3) into smoke acts_left"
	)

	eng.pass_strategy(str(eng.state["turn_side"]))
	eng.pass_strategy(str(eng.state["turn_side"]))
	for n_act in range(acts_rolled):
		var t_side := str(eng.state["turn_side"])
		var pick_op: Dictionary = {}
		for o in eng.state["operatives"]:
			if str(o["side"]) == t_side and bool(o["ready"]) and not bool(o["incapacitated"]):
				pick_op = o
				break
		eng.activate_operative(str(pick_op["uid"]), "engage")
		eng.end_operative_activation()
	var smoke_still_present := false
	for m in eng.state.get("eq_markers", []):
		if str((m as Dictionary).get("kind", "")) == "smoke":
			smoke_still_present = true
	_assert_true(
		not smoke_still_present,
		"Smoke marker is removed after acts_left normal activations complete"
	)

	# 5. Stun grenade: stun_targets (<=6\" visible enemy only), 1\" blast on friend/foe, 3+ sets apl_mod == -1, count -1
	for idx in range(eng.state["operatives"].size()):
		var o: Dictionary = eng.state["operatives"][idx]
		o["pos"] = Vector2(2.0 + 1.2 * float(idx % 6), 20.5 if str(o["side"]) == "A" else 1.5)
		o["ready"] = true
		o["apl_mod"] = 0
		o["stun_skip_current_end"] = false

	# Add one tall heavy wall to block visibility to a hidden enemy within 6\"
	terrains.append({
		"id": "test_stun_wall",
		"name": "测试高墙",
		"kind": "heavy_wall",
		"elevation": 4.0,
		"blocks_los_ground": true,
		"rect": Rect2(13.0, 9.0, 4.0, 1.0),
	})
	var thrower: Dictionary = _find_by_op_id(eng, "aod_captain")
	var ally_near: Dictionary = _find_by_op_id(eng, "aod_assault_warrior")
	var enemy_vis: Dictionary = _find_by_op_id(eng, "pm_champion")
	var enemy_near: Dictionary = _find_by_op_id(eng, "pm_fighter")
	var enemy_hidden: Dictionary = _find_by_op_id(eng, "pm_heavy_gunner")
	var enemy_far: Dictionary = _find_by_op_id(eng, "pm_icon_bearer")

	thrower["pos"] = Vector2(8.0, 12.0)
	enemy_vis["pos"] = Vector2(8.0, 8.0)     # ~2.6\" edge dist, clear LoS
	enemy_near["pos"] = Vector2(9.8, 8.0)    # ~0.54\" edge dist from enemy_vis (<=1.0\"), >2\" from thrower
	ally_near["pos"] = Vector2(6.2, 8.0)     # ~0.54\" edge dist from enemy_vis (<=1.0\", friendly!)
	terrains[0]["rect"] = Rect2(10.5, 9.5, 1.0, 5.0)
	enemy_hidden["pos"] = Vector2(13.0, 12.0) # ~3.6\" edge dist from (8, 12), completely behind 5\"-long wall at x=10.5..11.5
	enemy_far["pos"] = Vector2(20.0, 12.0)   # >10\" away

	var st_list: Array[Dictionary] = eng.stun_targets(thrower)
	var st_uids: Array[String] = []
	for st in st_list:
		st_uids.append(str(st["uid"]))
	_assert_true(
		st_uids.has(str(enemy_vis["uid"])) and st_uids.has(str(enemy_near["uid"]))
		and not st_uids.has(str(enemy_hidden["uid"])) and not st_uids.has(str(enemy_far["uid"]))
		and not st_uids.has(str(ally_near["uid"])),
		"stun_targets contains only visible enemies within 6\""
	)

	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.activate_operative(str(thrower["uid"]), "engage")
	eng.rng.seed = 2026
	var stun_res: Dictionary = eng.use_stun_grenade(str(thrower["uid"]), str(enemy_vis["uid"]))
	var rolls: Array = stun_res.get("rolls", [])
	var rolled_uids: Array[String] = []
	var stun_logic_ok := true
	var any_stunned := false
	for r_entry in rolls:
		var rd: Dictionary = r_entry
		var r_uid := str(rd["uid"])
		var r_val := int(rd["roll"])
		var r_st := bool(rd["stunned"])
		rolled_uids.append(r_uid)
		var v_op: Dictionary = eng.get_op(r_uid)
		if r_st != (r_val >= 3):
			stun_logic_ok = false
		if r_st:
			any_stunned = true
			if int(v_op["apl_mod"]) != -1:
				stun_logic_ok = false
		else:
			if int(v_op["apl_mod"]) != 0:
				stun_logic_ok = false
	_assert_true(
		bool(stun_res["ok"]) and rolls.size() == 3
		and rolled_uids.has(str(enemy_vis["uid"])) and rolled_uids.has(str(enemy_near["uid"])) and rolled_uids.has(str(ally_near["uid"]))
		and not rolled_uids.has(str(enemy_far["uid"])) and eng.grenades_left("A", "stun") == 0,
		"use_stun_grenade rolls for target and all operatives within 1\" (friend or foe), excluding farther ones, and decrements count"
	)
	_assert_true(
		stun_logic_ok and any_stunned,
		"Stun grenade sets apl_mod == -1 on operatives rolling 3+"
	)
