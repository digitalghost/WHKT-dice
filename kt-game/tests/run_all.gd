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
	_test_objectives_clear_of_walls()
	_test_tac_ops_extended()
	_test_r1_rule_defect_fixes()
	_test_handoff_rules_and_combat_extensions()
	_test_playtest_audit_fixes()


var _ran_scene_tests: bool = false


func _process(_delta: float) -> bool:
	if _ran_scene_tests:
		return true
	_ran_scene_tests = true
	_test_r2_ui_and_interaction_fixes()
	_test_handoff_ui_extensions()

	print("------------------------------------------------")
	print("RESULTS: %d PASSED, %d FAILED" % [_passed, _failed])
	quit(0 if _failed == 0 else 1)
	return true


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
	var map_d: Dictionary = KTData.load_map(KTData.DEFAULT_MAP_ID)
	_assert_true(str(map_d.get("id", "")) == KTData.DEFAULT_MAP_ID, "Default map loads from data/maps (not the built-in fallback)")
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

	# 2. Climbing onto the SW large ruin's Vantage deck (`large_ruin_deck`: Rect2(4.5, 15.5, 6.5, 4.0), +3.0"):
	#    Updated per Rulebook p.13 (L649) & p.31 (L1391-1403): ceil(2.0")=2.0" horiz + max(2.0", ceil(3.0"))=3.0" climb = 5.0" (was 4.0" under old flat +2" bug)
	cap["pos"] = Vector2(7.0, 14.5) # just north of the deck (open side)
	var climb_pts: Array[Vector2] = [Vector2(7.0, 14.5), Vector2(7.0, 16.5)]
	var res_clb: Dictionary = KTGeometry.validate_move_path(climb_pts, cap, "reposition", 6.0, eng.state)
	_assert_true(bool(res_clb["ok"]) and absf(float(res_clb["cost"]) - 5.0) < 0.15, "Vantage +3\" climb costs 2\" horizontal + 3\" vertical = 5\"")

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
	# Both stand on open ground either side of the SE small ruin wall (Rect2(23.0, 18.5, 4.0, 1.0), h=2.21").
	aod_gunner["pos"] = Vector2(25.0, 17.3)
	aod_gunner["order"] = "engage"
	pm_fighter["pos"] = Vector2(25.0, 20.8)
	pm_fighter["order"] = "conceal"
	for o in eng.state["operatives"]:
		if str(o["uid"]) != str(aod_gunner["uid"]) and str(o["uid"]) != str(pm_fighter["uid"]):
			o["pos"] = Vector2(-99.0, -99.0)
	_assert_true(bool(KTGeometry.is_valid_stand_pos(aod_gunner["pos"], float(aod_gunner["base_radius"]), str(aod_gunner["uid"]), eng.state)["ok"]) \
		and bool(KTGeometry.is_valid_stand_pos(pm_fighter["pos"], float(pm_fighter["base_radius"]), str(pm_fighter["uid"]), eng.state)["ok"]) \
		and KTGeometry.elevation_at(pm_fighter["pos"], ters) == 0.0, "Wall-visibility test stands both operatives on legal ground spots")
	var g_w: Array = eng.get_usable_ranged_weapons(aod_gunner)
	_assert_true(not g_w.is_empty(), "Gunner has a usable ranged weapon for the wall-visibility test")
	if not g_w.is_empty():
		var ev_hidden: Dictionary = KTGeometry.evaluate_shot(aod_gunner, pm_fighter, g_w[0], eng.state)
		_assert_true(not bool(ev_hidden["valid"]) and not bool(ev_hidden["visible"]), "Concealed operative fully behind a 2.2\" wall is not visible, so not a valid target")


func _test_combat_audit_fixes_and_factions() -> void:
	var eng := KTEngine.new(202)
	eng.start_match("hotseat", "A", "stealthy", "sharpshooter")
	eng.auto_deploy_all()
	var att: Dictionary = _find_by_op_id(eng, "aod_heavy_gunner")
	var def: Dictionary = _find_by_op_id(eng, "pm_fighter")
	att["pos"] = Vector2(10.0, 14.0)
	def["pos"] = Vector2(10.0, 9.0)
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
	_assert_true(bool(shot_ev["valid"]), "Combat audit setup shot is valid before isolating Punishing/Rending")
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
	_assert_true(first in ["A", "B"] and first == str(eng.state["setup"]["first_side"]), "Pre-battle roll-off winner deploys first")
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
	_assert_true(not bool(e1.choose_tac_op(e1.setup_side(), "nope_op")["ok"]), "Unknown tac op is rejected")

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
	# Negative case: the killer stands in its OWN drop zone, far from the enemy's -> no Rout VP,
	# even though the victim itself is inside the enemy (A) drop zone.
	b_ops[0]["pos"] = dz_b.get_center()
	a_ops[4]["pos"] = dz_a.get_center()
	a_ops[4]["wounds_cur"] = 0
	a_ops[4]["incapacitated"] = true
	a_ops[4]["killed_by"] = str(b_ops[0]["uid"])
	e2.recalculate_objectives_and_kills()
	_assert_true(int(e2.state["sides"]["B"]["vp_tac"]) == 0, "Rout: kill made by an operative more than 6\" from the enemy drop zone scores nothing")
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
	var light_eq_cnt := 0
	var heavy_eq_cnt := 0
	for t in eng.state["map"]["terrains"]:
		if bool(t.get("equipment", false)):
			var kind := "light_barricade" if str(t["kind"]) == "light" else "heavy_barricade"
			if kind == "light_barricade" and str(t["side"]) == "A":
				light_eq_cnt += 1
			elif kind == "heavy_barricade" and str(t["side"]) == "B":
				heavy_eq_cnt += 1
			if not eng.equipment_zone(str(t["side"]), kind).encloses(t["rect"]):
				zones_ok = false
	var first_mk: Dictionary = eng.state["eq_markers"][0]
	_assert_true(
		zones_ok and light_eq_cnt == 2 and heavy_eq_cnt == 1 and str(first_mk.get("kind", "")) == "mine",
		"2 light barricades (A), 1 heavy barricade (B), and 1 mine marker placed in legal zones"
	)
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
	# Updated per Rulebook p.31 (L1391-1403) & p.101 (L3097-3098): unassisted +3.0" climb costs 2"+3"=5", ladder sets vertical climb to 1" -> 2"+1"=3" (saves 2", was 1" under old flat +2" bug)
	mover["pos"] = Vector2(7.0, 14.5)
	var climb_pts: Array[Vector2] = [Vector2(7.0, 14.5), Vector2(7.0, 16.5)]
	eng.state["eq_markers"] = []
	var res_no_lad: Dictionary = KTGeometry.validate_move_path(climb_pts, mover, "reposition", 6.0, eng.state)
	eng.state["eq_markers"] = [{"kind": "ladder", "side": "B", "pos": Vector2(7.0, 15.3)}]
	var res_lad: Dictionary = KTGeometry.validate_move_path(climb_pts, mover, "reposition", 6.0, eng.state)
	_assert_true(
		bool(res_lad["ok"]) and absf(float(res_no_lad["cost"]) - 5.0) < 0.05 and absf(float(res_lad["cost"]) - 3.0) < 0.05,
		"Ladder outside +3\" vantage edge sets vertical climb distance to 1\" (5\" -> 3\") for either side"
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


func _test_r1_rule_defect_fixes() -> void:
	var eng := KTEngine.new(1101)
	eng.start_match("hotseat", "A", "sharpshooter", "hardy")
	eng.auto_deploy_all()
	eng.auto_initiative()
	eng.choose_primary_op(str(eng.state["turn_side"]), "kill")
	eng.choose_primary_op(str(eng.state["turn_side"]), "key")
	eng.pass_strategy(str(eng.state["turn_side"]))
	eng.pass_strategy(str(eng.state["turn_side"]))

	var terrains: Array = eng.state["map"]["terrains"]
	terrains.clear()
	eng.state["eq_markers"] = []
	eng.state["markers"] = []
	for idx in range(eng.state["operatives"].size()):
		var o: Dictionary = eng.state["operatives"][idx]
		o["pos"] = Vector2(1.5 + 1.2 * float(idx % 6), 20.5 if str(o["side"]) == "A" else 1.5)
		o["ready"] = true
		o["order"] = "engage"
		o["incapacitated"] = false
		o["apl_mod"] = 0

	# -------------------------------------------------------------------------
	# F1: Shoot after Fall Back (Rulebook p.15 Shoot, L730-732)
	# -------------------------------------------------------------------------
	var cap: Dictionary = _find_by_op_id(eng, "aod_captain")
	var pm_f: Dictionary = _find_by_op_id(eng, "pm_fighter")
	cap["pos"] = Vector2(12.0, 12.0)
	pm_f["pos"] = Vector2(12.0, 10.5) # within 1" control range
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.activate_operative(str(cap["uid"]), "engage")
	var acts_engaged: Dictionary = eng.get_available_actions(cap)
	_assert_true(
		not bool((acts_engaged["shoot"] as Dictionary)["enabled"]),
		"F1: Operative in enemy control range cannot Shoot (p.15 L732)"
	)
	var fb_pts: Array[Vector2] = [Vector2(12.0, 12.0), Vector2(12.0, 15.0)]
	var fb_res: Dictionary = eng.execute_move(str(cap["uid"]), "fall_back", fb_pts)
	var acts_after_fb: Dictionary = eng.get_available_actions(cap)
	_assert_true(
		bool(fb_res["ok"]) and int(cap["ap_left"]) == 1 and bool((acts_after_fb["shoot"] as Dictionary)["enabled"]),
		"F1: APL3 operative with Engage order can Shoot with remaining 1 AP after Fall Back (p.15 L730-732)"
	)
	# Under Conceal order (no Silent weapon), Shoot remains forbidden even after Fall Back
	cap["pos"] = Vector2(12.0, 12.0)
	cap["ready"] = true
	eng.state["active_op_uid"] = ""
	eng.activate_operative(str(cap["uid"]), "conceal")
	var fb_conceal: Dictionary = eng.execute_move(str(cap["uid"]), "fall_back", fb_pts)
	var acts_conceal_fb: Dictionary = eng.get_available_actions(cap)
	_assert_true(
		bool(fb_conceal["ok"]) and int(cap["ap_left"]) == 1 and not bool((acts_conceal_fb["shoot"] as Dictionary)["enabled"]),
		"F1: Operative with Conceal order still cannot Shoot after Fall Back (p.15 L732)"
	)
	eng.end_operative_activation()
	cap["ready"] = true
	cap["pos"] = Vector2(2.0, 20.5)
	pm_f["pos"] = Vector2(2.0, 1.5)

	# -------------------------------------------------------------------------
	# F2: Segment rounding, Climb ceil, Drop ignore 2", Dash no climb (Rulebook p.13 L649, p.14 L680-681, p.31 L1391-1408)
	# -------------------------------------------------------------------------
	var seg_pts: Array[Vector2] = [Vector2(10.0, 10.0), Vector2(12.4, 10.0)]
	var res_seg: Dictionary = KTGeometry.validate_move_path(seg_pts, cap, "reposition", 6.0, eng.state)
	_assert_true(
		bool(res_seg["ok"]) and is_equal_approx(float(res_seg["cost"]), 3.0),
		"F2: 2.4\" horizontal segment rounds up to 3\" (p.13 L649)"
	)
	var two_seg_pts: Array[Vector2] = [Vector2(10.0, 10.0), Vector2(11.2, 10.0), Vector2(11.2, 11.2)]
	var res_two_seg: Dictionary = KTGeometry.validate_move_path(two_seg_pts, cap, "reposition", 6.0, eng.state)
	_assert_true(
		bool(res_two_seg["ok"]) and is_equal_approx(float(res_two_seg["cost"]), 4.0),
		"F2: Each move segment rounds up independently (1.2\" + 1.2\" -> 2\" + 2\" = 4\", p.13 L649)"
	)

	terrains.append({
		"id": "f2_vantage_35",
		"name": "测试露台3.5",
		"kind": "vantage",
		"elevation": 3.5,
		"rect": Rect2(14.0, 10.0, 4.0, 4.0),
	})
	terrains.append({
		"id": "f2_vantage_42",
		"name": "测试高台4.2",
		"kind": "vantage",
		"elevation": 4.2,
		"rect": Rect2(20.0, 10.0, 4.0, 4.0),
	})
	# Climb 4.2" vantage with 1.0" horizontal step: 1" horiz + max(2, ceil(4.2))=5" = 6.0"
	var climb_42_pts: Array[Vector2] = [Vector2(19.5, 12.0), Vector2(20.5, 12.0)]
	var res_climb_42: Dictionary = KTGeometry.validate_move_path(climb_42_pts, cap, "reposition", 6.0, eng.state)
	_assert_true(
		bool(res_climb_42["ok"]) and is_equal_approx(float(res_climb_42["cost"]), 6.0),
		"F2: Climbing 4.2\" vantage rounds vertical rise up to 5\" (1\" horiz + 5\" climb = 6\", p.31 L1401-1402)"
	)
	# Drop 3.5" vantage with 1.0" horizontal step: 1" horiz + ceil(3.5 - 2.0)=2" vertical drop = 3.0" (drop itself costs only 2")
	var drop_35_pts: Array[Vector2] = [Vector2(14.5, 12.0), Vector2(13.5, 12.0)]
	var res_drop_35: Dictionary = KTGeometry.validate_move_path(drop_35_pts, cap, "reposition", 6.0, eng.state)
	_assert_true(
		bool(res_drop_35["ok"]) and is_equal_approx(float(res_drop_35["cost"]), 3.0),
		"F2: Dropping 3.5\" ignores 2\" and pays 2\" vertical cost (1\" horiz + 2\" drop = 3\", p.31 L1406-1408)"
	)
	# Dash cannot climb onto vantage, but CAN drop down from vantage
	var climb_35_pts: Array[Vector2] = [Vector2(13.5, 12.0), Vector2(14.5, 12.0)]
	var res_dash_climb: Dictionary = KTGeometry.validate_move_path(climb_35_pts, cap, "dash", 3.0, eng.state)
	var res_dash_drop: Dictionary = KTGeometry.validate_move_path(drop_35_pts, cap, "dash", 3.0, eng.state)
	_assert_true(
		not bool(res_dash_climb["ok"]) and str(res_dash_climb["reason"]) == "【冲刺】期间不能攀爬",
		"F2: Dash cannot climb onto vantage terrain (p.14 L680-681)"
	)
	_assert_true(
		bool(res_dash_drop["ok"]) and is_equal_approx(float(res_dash_drop["cost"]), 3.0),
		"F2: Dash can drop off a 3.5\" vantage within 3\" move allowance (p.14 L680-681)"
	)
	terrains.clear()

	# -------------------------------------------------------------------------
	# F3: Optimal defence dice allocation for 3/4 weapon vs 2 normal saves (Rulebook p.16 L799-812)
	# -------------------------------------------------------------------------
	var gunner: Dictionary = _find_by_op_id(eng, "aod_gunner")
	var pm_champ: Dictionary = _find_by_op_id(eng, "pm_champion")
	pm_champ["pos"] = Vector2(10.0, 8.0)
	gunner["pos"] = Vector2(10.0, 14.0)
	gunner["wounds_cur"] = int(gunner["wounds_max"])
	eng.state["sides"]["A"]["active_ploys"].clear()
	var w_34 := {
		"id": "test_bolter_34",
		"name": "测试爆矢枪",
		"kind": "ranged",
		"attacks": 4,
		"hit": 3,
		"normal_dmg": 3,
		"crit_dmg": 4,
		"rules": {},
	}
	var rng_f3 := RandomNumberGenerator.new()
	rng_f3.seed = 3030
	var ev_f3: Dictionary = KTGeometry.evaluate_shot(pm_champ, gunner, w_34, eng.state)
	var sess_f3: Dictionary = KTCombat.create_shooting_session(pm_champ, gunner, w_34, ev_f3, eng.state, rng_f3)
	sess_f3["retained_atk"] = {"crits": 1, "normals": 2, "devastating_mw": 0}
	sess_f3["auto_def_normals"] = 2
	sess_f3["auto_def_crits"] = 0
	sess_f3["def_dice"] = []
	KTCombat.finalize_shooting_resolution(sess_f3, eng.state, rng_f3)
	_assert_true(
		int(sess_f3["total_damage_dealt"]) == 4,
		"F3: Two normal saves against 1 crit + 2 normals (3/4) block 2 normals (4 dmg taken, not 6, p.16 L799-812)"
	)
	gunner["wounds_cur"] = int(gunner["wounds_max"])

	# -------------------------------------------------------------------------
	# F4: Declining Counteract only skips the current opportunity (Rulebook p.11 L576-582)
	# -------------------------------------------------------------------------
	for op_f4 in eng.state["operatives"]:
		var od_f4: Dictionary = op_f4
		if str(od_f4["side"]) == "A":
			od_f4["ready"] = false
			od_f4["counteracted_tp"] = false
		else:
			od_f4["ready"] = false
			od_f4["counteracted_tp"] = false
	var b_ops_f4 := _side_ops(eng, "B")
	var b_ready_1: Dictionary = b_ops_f4[0]
	var b_ready_2: Dictionary = b_ops_f4[1]
	b_ready_1["ready"] = true
	b_ready_2["ready"] = true
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.state["counteract_declined"] = false
	eng.state["counteract_declined_side"] = ""
	eng.state["turn_side"] = "A"
	_assert_true(eng._has_counteract_available("A"), "F4 setup: Side A has Counteract available when all expended and B has ready ops")
	eng.pass_counteract("A")
	var any_marked := false
	for op_chk in _side_ops(eng, "A"):
		if bool((op_chk as Dictionary)["counteracted_tp"]):
			any_marked = true
	_assert_true(
		bool(eng.state["counteract_declined"]) and not eng._has_counteract_available("A") and str(eng.state["turn_side"]) == "B" and not any_marked,
		"F4: Passing Counteract sets counteract_declined without marking friendly operatives counteracted_tp (p.11 L581)"
	)
	eng.activate_operative(str(b_ready_1["uid"]), "engage")
	eng.end_operative_activation()
	var a_cand: Dictionary = _side_ops(eng, "A")[0]
	var sel_after_b: Dictionary = eng.can_operative_be_selected_for_turn(a_cand)
	_assert_true(
		not bool(eng.state["counteract_declined"]) and eng._has_counteract_available("A") and str(eng.state["turn_side"]) == "A" and bool(sel_after_b["ok"]) and bool(sel_after_b["counteract"]),
		"F4: After opponent completes another activation, friendly side regains Counteract opportunity (p.11 L577-582)"
	)
	for op_rs in eng.state["operatives"]:
		(op_rs as Dictionary)["ready"] = true
		(op_rs as Dictionary)["counteracted_tp"] = false

	# -------------------------------------------------------------------------
	# F5: Combat Support requires ally NOT in another enemy's control range (Rulebook p.17 L858-860)
	# -------------------------------------------------------------------------
	for idx5 in range(eng.state["operatives"].size()):
		var o5: Dictionary = eng.state["operatives"][idx5]
		o5["pos"] = Vector2(1.5 + 1.2 * float(idx5 % 6), 20.5 if str(o5["side"]) == "A" else 1.5)
	var fighter_a: Dictionary = _find_by_op_id(eng, "aod_captain")
	var ally_a: Dictionary = _find_by_op_id(eng, "aod_gunner")
	var opp_b1: Dictionary = _find_by_op_id(eng, "pm_champion")
	var opp_b2: Dictionary = _find_by_op_id(eng, "pm_fighter")
	fighter_a["wounds_cur"] = int(fighter_a["wounds_max"])
	fighter_a["pos"] = Vector2(12.0, 12.0)
	opp_b1["pos"] = Vector2(13.5, 12.0)  # edge dist ~0.09" to fighter_a
	ally_a["pos"] = Vector2(14.8, 12.0)  # edge dist ~0.04" to opp_b1 (in opp_b1 control range)
	opp_b2["pos"] = Vector2(16.0, 12.0)  # edge dist ~0.0" to ally_a (also engaging ally_a!)
	var w_melee_4 := {
		"id": "test_blade",
		"name": "测试战刃",
		"kind": "melee",
		"attacks": 4,
		"hit": 4,
		"normal_dmg": 3,
		"crit_dmg": 4,
		"rules": {},
	}
	var ms_no_assist: Dictionary = KTCombat.create_melee_session(fighter_a, opp_b1, w_melee_4, w_melee_4, eng.state, rng_f3)
	var att_no_assist: Dictionary = ms_no_assist["att"]
	opp_b2["pos"] = Vector2(25.0, 1.5) # move second enemy away so ally_a is only engaged with opp_b1
	var ms_assist: Dictionary = KTCombat.create_melee_session(fighter_a, opp_b1, w_melee_4, w_melee_4, eng.state, rng_f3)
	var att_assist: Dictionary = ms_assist["att"]
	_assert_true(
		int(att_no_assist["effective_hit"]) == 4 and int(att_assist["effective_hit"]) == 3,
		"F5: Ally engaged with another enemy does not grant Combat Support; unengaged ally grants +1 hit (p.17 L858-860)"
	)

	# -------------------------------------------------------------------------
	# F6: Vantage terrain counts as Light terrain for cover, not Heavy/Obscured (Rulebook p.36 L1521-1534)
	# -------------------------------------------------------------------------
	for idx6 in range(eng.state["operatives"].size()):
		var o6: Dictionary = eng.state["operatives"][idx6]
		o6["pos"] = Vector2(1.5 + 1.2 * float(idx6 % 6), 20.5 if str(o6["side"]) == "A" else 1.5)
	terrains.clear()
	terrains.append({
		"id": "f6_high_vantage",
		"name": "高处制高点",
		"kind": "vantage",
		"elevation": 3.0,
		"rect": Rect2(8.0, 4.0, 4.0, 4.0),
	})
	terrains.append({
		"id": "f6_cover_vantage",
		"name": "低处制高点",
		"kind": "vantage",
		"elevation": 1.0,
		"rect": Rect2(8.0, 12.0, 4.0, 2.0),
	})
	gunner["pos"] = Vector2(10.0, 9.0) # ground z=0
	gunner["order"] = "engage"
	opp_b1["pos"] = Vector2(10.0, 14.5) # ground z=0, within 1" behind f6_cover_vantage
	opp_b1["order"] = "conceal"
	var ev_f6_ground: Dictionary = KTGeometry.evaluate_shot(gunner, opp_b1, w_34, eng.state)
	gunner["pos"] = Vector2(10.0, 6.0) # on f6_high_vantage (z=3.0, +3.0" >= 2.0" above target)
	var ev_f6_vant: Dictionary = KTGeometry.evaluate_shot(gunner, opp_b1, w_34, eng.state)
	_assert_true(
		not bool(ev_f6_ground["valid"]) and str(ev_f6_ground["cover_terrain_type"]) == "light",
		"F6: Vantage terrain grants Light cover (not Heavy), hiding Concealed target from ground shooter (p.36 L1521-1531)"
	)
	_assert_true(
		bool(ev_f6_vant["valid"]) and str(ev_f6_vant["cover"]) == "improved" and not bool(ev_f6_vant["obscured"]),
		"F6: Shooter 2\"+ higher on Vantage can target Concealed operative whose cover is Vantage terrain, without Obscured (p.36 L1526-1534)"
	)
	terrains.clear()

	# -------------------------------------------------------------------------
	# F7: Heavy weapon move flag reset on activation + no move after firing Heavy (Rulebook p.93 L2773-2776)
	# -------------------------------------------------------------------------
	for idx7 in range(eng.state["operatives"].size()):
		var o7: Dictionary = eng.state["operatives"][idx7]
		o7["pos"] = Vector2(1.5 + 1.2 * float(idx7 % 6), 20.5 if str(o7["side"]) == "A" else 1.5)
	var sniper_f7: Dictionary = _find_by_op_id(eng, "aod_sniper")
	sniper_f7["pos"] = Vector2(10.0, 15.0)
	sniper_f7["ready"] = true
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.activate_operative(str(sniper_f7["uid"]), "engage")
	var hg_move: Dictionary = eng.execute_move(str(sniper_f7["uid"]), "reposition", [Vector2(10.0, 15.0), Vector2(12.0, 15.0)])
	var usable_after_move: Array[Dictionary] = eng.get_usable_ranged_weapons(sniper_f7)
	var has_hb_after_move := false
	for w_chk in usable_after_move:
		if bool((w_chk["rules"] as Dictionary).get("heavy", false)):
			has_hb_after_move = true
	eng.end_operative_activation()
	# Now test Counteract in the same TP: moved_reposition_or_charge must reset on activate_operative!
	for op_ca in _side_ops(eng, "A"):
		(op_ca as Dictionary)["ready"] = false
		(op_ca as Dictionary)["counteracted_tp"] = false
	eng.state["turn_side"] = "A"
	eng.state["counteract_declined"] = false
	var ca_act: Dictionary = eng.activate_operative(str(sniper_f7["uid"]))
	var usable_in_ca: Array[Dictionary] = eng.get_usable_ranged_weapons(sniper_f7)
	var heavy_w: Dictionary = {}
	for w_ca in usable_in_ca:
		if bool((w_ca["rules"] as Dictionary).get("heavy", false)):
			heavy_w = w_ca
			break
	_assert_true(
		bool(hg_move["ok"]) and not has_hb_after_move and bool(ca_act["counteract"]) and not heavy_w.is_empty(),
		"F7a: moved_reposition_or_charge resets on next activation/Counteract so Heavy weapon can fire if unmoved this turn (p.93 L2773)"
	)
	eng.end_operative_activation()
	# Test F7b: Firing Heavy (Dash only) blocks reposition/charge/fall_back/barricade_move while STILL allowing Dash (p.93 L2774);
	# firing strict Heavy blocks all 5 moves including Dash!
	for op_r7 in eng.state["operatives"]:
		(op_r7 as Dictionary)["ready"] = true
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.activate_operative(str(sniper_f7["uid"]), "engage")
	eng.commit_shooting_action_cost(sniper_f7, heavy_w)
	var acts_after_heavy_dash: Dictionary = eng.get_available_actions(sniper_f7)
	var non_dash_blocked := true
	for mk in ["reposition", "charge", "fall_back", "barricade_move"]:
		if bool((acts_after_heavy_dash[mk] as Dictionary)["enabled"]):
			non_dash_blocked = false
	_assert_true(
		bool(sniper_f7["heavy_dash_only_fired"]) and non_dash_blocked and bool((acts_after_heavy_dash["dash"] as Dictionary)["enabled"]),
		"F7b1: Firing Heavy (Dash only) disables non-dash moves while still permitting Dash (p.93 L2774)"
	)
	var strict_heavy_w: Dictionary = heavy_w.duplicate(true)
	strict_heavy_w["rules"]["heavy_dash_only"] = false
	eng.commit_shooting_action_cost(sniper_f7, strict_heavy_w)
	var acts_after_heavy: Dictionary = eng.get_available_actions(sniper_f7)
	var all_moves_blocked := true
	var all_reasons_match := true
	for mk2 in ["reposition", "dash", "charge", "fall_back", "barricade_move"]:
		var minfo: Dictionary = acts_after_heavy[mk2]
		if bool(minfo["enabled"]):
			all_moves_blocked = false
		if str(minfo["reason"]) != "本轮已使用重型武器，不能再移动":
			all_reasons_match = false
	_assert_true(
		bool(sniper_f7["heavy_fired_this_activation"]) and all_moves_blocked and all_reasons_match,
		"F7b2: Firing a strict Heavy weapon disables reposition/dash/charge/fall_back/barricade_move for the rest of the activation (p.93 L2773-2774)"
	)
	eng.end_operative_activation()

	# -------------------------------------------------------------------------
	# F8 & F9: Empty parry allowed & crit parry prefer_normal option (Rulebook p.18 L886-898)
	# -------------------------------------------------------------------------
	fighter_a["pos"] = Vector2(12.0, 12.0)
	opp_b1["pos"] = Vector2(13.2, 12.0)
	var hp_opp_before := int(opp_b1["wounds_cur"])
	var ms_parry: Dictionary = KTCombat.create_melee_session(fighter_a, opp_b1, w_melee_4, w_melee_4, eng.state, rng_f3)
	# F9: Opponent has 1 crit and 1 normal; prefer_normal=true parries the normal instead of the crit
	(ms_parry["att"] as Dictionary)["crits"] = 2
	(ms_parry["att"] as Dictionary)["normals"] = 1
	(ms_parry["att"] as Dictionary)["dueller"] = false
	(ms_parry["def"] as Dictionary)["crits"] = 1
	(ms_parry["def"] as Dictionary)["normals"] = 1
	ms_parry["turn"] = "attacker"
	ms_parry["step"] = "duel"
	var p_pref_norm: Dictionary = KTCombat.execute_melee_step(ms_parry, "parry_crit", eng.state, rng_f3, true)
	_assert_true(
		bool(p_pref_norm["ok"]) and int(p_pref_norm["removed"]) == 1 and int((ms_parry["def"] as Dictionary)["normals"]) == 0 and int((ms_parry["def"] as Dictionary)["crits"]) == 1,
		"F9: Crit parry with prefer_normal=true discards opponent normal success instead of crit (p.18 L893)"
	)
	# Default prefer_normal=false parries the crit when opponent has both
	(ms_parry["def"] as Dictionary)["crits"] = 1
	(ms_parry["def"] as Dictionary)["normals"] = 1
	ms_parry["turn"] = "attacker"
	var p_def_crit: Dictionary = KTCombat.execute_melee_step(ms_parry, "parry_crit", eng.state, rng_f3)
	_assert_true(
		bool(p_def_crit["ok"]) and int(p_def_crit["removed"]) == 1 and int((ms_parry["def"] as Dictionary)["crits"]) == 0 and int((ms_parry["def"] as Dictionary)["normals"]) == 1,
		"F9: Default crit parry still discards opponent crit first (p.18 L893)"
	)
	# F8: Empty parry when opponent has 0 remaining successes
	(ms_parry["att"] as Dictionary)["crits"] = 1
	(ms_parry["att"] as Dictionary)["normals"] = 1
	(ms_parry["def"] as Dictionary)["crits"] = 0
	(ms_parry["def"] as Dictionary)["normals"] = 0
	ms_parry["turn"] = "attacker"
	ms_parry["step"] = "duel"
	var log_len_before: int = (ms_parry["resolution_log"] as Array).size()
	var p_empty_norm: Dictionary = KTCombat.execute_melee_step(ms_parry, "parry_norm", eng.state, rng_f3)
	var p_empty_crit: Dictionary = KTCombat.execute_melee_step(ms_parry, "parry_crit", eng.state, rng_f3)
	_assert_true(
		bool(p_empty_norm["ok"]) and int(p_empty_norm["removed"]) == 0
		and bool(p_empty_crit["ok"]) and int(p_empty_crit["removed"]) == 0
		and int(opp_b1["wounds_cur"]) == hp_opp_before
		and (ms_parry["resolution_log"] as Array).size() >= log_len_before + 2
		and str(ms_parry["step"]) == "resolved",
		"F8: Parrying when opponent has no unresolved successes succeeds with removed=0 and logs without dealing damage (p.18 L897)"
	)

	# -------------------------------------------------------------------------
	# F10: Shooter hugging the same long wall >2" away does NOT strip defender cover (Rulebook p.19 L926-931)
	# -------------------------------------------------------------------------
	for idx10 in range(eng.state["operatives"].size()):
		var o10: Dictionary = eng.state["operatives"][idx10]
		o10["pos"] = Vector2(1.5 + 1.2 * float(idx10 % 6), 20.5 if str(o10["side"]) == "A" else 1.5)
	terrains.clear()
	terrains.append({
		"id": "f10_long_wall",
		"name": "长矮墙",
		"kind": "light",
		"elevation": 1.0,
		"rect": Rect2(10.0, 8.0, 0.5, 8.0),
	})
	gunner["pos"] = Vector2(9.2, 9.0)   # r=0.63 -> dist_att_to_terr = 10.0 - 9.2 - 0.63 = 0.17" <= 0.35" (hugging wall)
	gunner["order"] = "engage"
	opp_b1["pos"] = Vector2(11.3, 14.0) # r=0.63 -> dist_def_to_terr = 11.3 - 10.5 - 0.63 = 0.17" <= 1.0"; edge_dist ~ 4.15" > 2"
	opp_b1["order"] = "engage"
	var ev_f10: Dictionary = KTGeometry.evaluate_shot(gunner, opp_b1, w_34, eng.state)
	_assert_true(
		bool(ev_f10["valid"]) and str(ev_f10["cover"]) == "standard",
		"F10: Defender within 1\" of a long wall retains cover even when shooter >2\" away hugs the same wall (p.19 L927-929)"
	)
	terrains.clear()

	# -------------------------------------------------------------------------
	# F11: Severe upgrades pre-retained normal success (Accurate / Vantage) when 0 crits (Rulebook p.94 L2827 & p.92 V1.5 FAQ L2720-2722)
	# -------------------------------------------------------------------------
	eng.state["sides"]["A"]["purity_used_tp"] = true
	var w_severe := {
		"id": "test_severe_acc",
		"name": "测试严重精准枪",
		"kind": "ranged",
		"attacks": 4,
		"hit": 3,
		"normal_dmg": 3,
		"crit_dmg": 4,
		"rules": {"severe": true, "accurate": 1},
	}
	var ev_f11: Dictionary = KTGeometry.evaluate_shot(gunner, opp_b1, w_severe, eng.state)
	ev_f11["obscured"] = false
	var sess_f11: Dictionary = KTCombat.create_shooting_session(gunner, opp_b1, w_severe, ev_f11, eng.state, rng_f3)
	# 1 pre-retained normal from Accurate 1; all 3 rolled dice fail (2, 2, 1) -> 0 rolled normals, 0 crits
	sess_f11["auto_attack_normals"] = 1
	sess_f11["atk_dice"] = [
		{"id": 0, "val": 2, "rerolled": false, "locked": false},
		{"id": 1, "val": 2, "rerolled": false, "locked": false},
		{"id": 2, "val": 1, "rerolled": false, "locked": false},
	]
	KTCombat.lock_in_shooting_attacks(sess_f11, eng.state, rng_f3)
	var ret_f11: Dictionary = sess_f11["retained_atk"]
	_assert_true(
		int(ret_f11["crits"]) == 1 and int(ret_f11["normals"]) == 0,
		"F11: Severe upgrades Accurate/Vantage pre-retained normal success into a crit when 0 crits retained (p.94 L2827, p.92 L2720-2722)"
	)

	# -------------------------------------------------------------------------
	# F12: Objective marker inside Vantage footprint uses Vantage elevation (Rulebook p.25-26 L1198, L1218-1220)
	# -------------------------------------------------------------------------
	for idx12 in range(eng.state["operatives"].size()):
		var o12: Dictionary = eng.state["operatives"][idx12]
		o12["pos"] = Vector2(1.5 + 1.2 * float(idx12 % 6), 20.5 if str(o12["side"]) == "A" else 1.5)
	terrains.clear()
	terrains.append({
		"id": "f12_vantage_obj",
		"name": "二楼目标平台",
		"kind": "vantage",
		"elevation": 3.0,
		"rect": Rect2(12.0, 9.0, 6.0, 6.0),
	})
	var obj0: Dictionary = (eng.state["map"]["objectives"] as Array)[0]
	var obj0_id := str(obj0["id"])
	obj0["pos"] = Vector2(15.0, 12.0) # sits on the +3.0" vantage deck
	gunner["pos"] = Vector2(15.5, 12.0) # also on the +3.0" vantage deck (z_op=3.0, z_obj=3.0)
	opp_b1["pos"] = Vector2(11.5, 12.0) # outside the deck on ground (z_op=0.0, d > 1.787 or z diff = 3.0 > 2.0)
	var opp_ground_near: Dictionary = _find_by_op_id(eng, "pm_fighter")
	opp_ground_near["pos"] = Vector2(11.8, 12.0) # horizontal d = 3.2 - 0.63 = 2.57; test closer edge!
	# Shrink deck to test horizontal <=1.787" from ground vs on-deck:
	(terrains[0] as Dictionary)["rect"] = Rect2(14.0, 10.0, 4.0, 4.0)
	obj0["pos"] = Vector2(14.5, 12.0)           # on vantage z=3.0
	opp_ground_near["pos"] = Vector2(13.5, 12.0) # ground z=0.0, horizontal d = 1.0 - 0.63 = 0.37 <= 1.787, but |0 - 3| = 3 > 2!
	gunner["pos"] = Vector2(15.2, 12.0)         # on vantage z=3.0, horizontal d = 0.7 - 0.63 = 0.07 <= 1.787, |3 - 3| = 0 <= 2!
	eng.recalculate_objectives_and_kills()
	var ost0: Dictionary = eng.state["objectives_state"][obj0_id]
	_assert_true(
		int(ost0["apl_a"]) > 0 and int(ost0["apl_b"]) == 0 and str(ost0["controlled_by"]) == "A",
		"F12: Objective inside Vantage rect is contested by operative on that Vantage deck and not from 3\" below (p.25-26 L1198, L1218-1220)"
	)


func _collect_focusable_buttons(node: Node, bad_out: Array[String]) -> void:
	if node is BaseButton and (node as Control).focus_mode != Control.FOCUS_NONE:
		bad_out.append("%s(%s)" % [node.name, node.get_class()])
	for ch in node.get_children():
		_collect_focusable_buttons(ch, bad_out)


func _make_key_event(keycode: int, ctrl: bool = false) -> InputEventKey:
	var ev := InputEventKey.new()
	ev.keycode = keycode
	ev.pressed = true
	ev.echo = false
	ev.ctrl_pressed = ctrl
	return ev


func _test_r2_ui_and_interaction_fixes() -> void:
	print("--- R2 UI & Interaction Defect Fixes ---")
	var mg := MainGame.new()
	root.add_child(mg)
	mg._title_overlay.visible = false
	mg._sel_mode = "ai"
	mg._sel_human_side = "A"
	mg._start_new_battle()
	mg.engine.auto_deploy_all()
	mg.engine.auto_initiative()
	mg.engine.choose_primary_op(str(mg.engine.state["turn_side"]), "kill")
	mg.engine.choose_primary_op(str(mg.engine.state["turn_side"]), "key")
	mg.engine.pass_strategy(str(mg.engine.state["turn_side"]))
	mg.engine.pass_strategy(str(mg.engine.state["turn_side"]))
	mg.engine.state["turn_side"] = "A"
	mg.engine.undo_stack.clear()
	mg._in_ai_undo_guard = false
	mg._pre_ai_undo_stack.clear()
	mg._refresh_all_ui()

	# U1: All buttons (including settings, dice modal, card lightbox, hotbar/hero) have FOCUS_NONE
	var test_btn := Button.new()
	KTWidgets.attach_button_juice(test_btn)
	_assert_true(test_btn.focus_mode == Control.FOCUS_NONE, "U1: KTWidgets.attach_button_juice sets focus_mode = FOCUS_NONE")
	test_btn.free()

	mg._open_settings()
	mg._settings_modal.visible = false
	mg._card_lightbox.show_team_rulebook("IMP-AOD")
	mg._card_lightbox.hide()
	var cap_a: Dictionary = _find_by_op_id(mg.engine, "aod_captain")
	var pm_b: Dictionary = _find_by_op_id(mg.engine, "pm_champion")
	var dummy_sess: Dictionary = KTCombat.create_melee_session(
		cap_a, pm_b,
		KTAI.pick_best_melee_weapon(cap_a),
		KTAI.pick_best_melee_weapon(pm_b),
		mg.engine.state, mg.engine.rng
	)
	mg._dice_modal.open_melee(mg.engine, dummy_sess)
	var bad_btns: Array[String] = []
	_collect_focusable_buttons(mg, bad_btns)
	_assert_true(bad_btns.is_empty(), "U1: Zero focusable BaseButtons across MainGame, Settings, CardLightbox & DiceCombatModal")

	# U6: Starting a new battle (or returning to main menu) while DiceCombatModal is open cleans modal & restores HUD
	mg._dice_modal._selected_pool = "atk_dice"
	mg._return_to_main_menu()
	_assert_true(not mg._dice_modal.visible and mg._dice_modal.session.is_empty() and mg._dice_modal._selected_pool == "", "U6a: Returning to main menu closes & resets DiceCombatModal")
	mg._title_overlay.visible = false
	mg._dice_modal.open_melee(mg.engine, dummy_sess)
	mg._dice_modal._selected_pool = "atk_dice"
	mg._start_new_battle()
	_assert_true(
		not mg._dice_modal.visible and mg._dice_modal.session.is_empty() and mg._dice_modal._selected_pool == "" and mg._top_crest.visible,
		"U6b: _start_new_battle resets lingering DiceCombatModal and keeps HUD visible"
	)

	# Re-enter clean open-ground Firefight state for U2–U11 checks
	mg.engine.auto_deploy_all()
	mg.engine.auto_initiative()
	mg.engine.choose_primary_op(str(mg.engine.state["turn_side"]), "kill")
	mg.engine.choose_primary_op(str(mg.engine.state["turn_side"]), "key")
	mg.engine.pass_strategy(str(mg.engine.state["turn_side"]))
	mg.engine.pass_strategy(str(mg.engine.state["turn_side"]))
	(mg.engine.state["map"]["terrains"] as Array).clear()
	for idx_u in range(mg.engine.state["operatives"].size()):
		var op_u: Dictionary = mg.engine.state["operatives"][idx_u]
		op_u["pos"] = Vector2(4.0 + 2.0 * float(idx_u % 6), 18.0 if str(op_u["side"]) == "A" else 4.0)
		op_u["ready"] = true
		op_u["incapacitated"] = false
	mg._killzone.sync_visual_positions(true)
	mg.engine.state["turn_side"] = "A"
	mg.engine.state["active_op_uid"] = ""
	mg.engine.undo_stack.clear()
	mg._in_ai_undo_guard = false
	mg._pre_ai_undo_stack.clear()
	mg._refresh_all_ui()

	# U3: Hotbar key uniqueness when operative has >=2 unique actions AND a firefight ploy
	mg.engine.state["mode"] = "hotseat"
	mg.engine.state["turn_side"] = "B"
	mg.engine.state["sides"]["B"]["cp"] = 2
	mg.engine.state["sides"]["B"]["grenades"] = {"frag": 0, "krak": 0, "smoke": 1, "stun": 1}
	var caster: Dictionary = _find_by_op_id(mg.engine, "pm_plaguecaster")
	if caster.is_empty():
		caster = _side_ops(mg.engine, "B")[0]
		caster["op_id"] = "pm_plaguecaster"
		caster["vitality_used_tp"] = false
	var opp_near: Dictionary = _side_ops(mg.engine, "A")[0]
	opp_near["pos"] = caster["pos"] + Vector2(0.0, 5.0) # within 6" for stun_targets!
	mg.engine.activate_operative(str(caster["uid"]), "engage")
	mg._refresh_all_ui()
	var seen_hk: Dictionary = {}
	var hk_collision := false
	for ch_s in mg._skill_bar_hb.get_children():
		if ch_s.is_queued_for_deletion():
			continue
		if ch_s is KTWidgets.SkillSlot:
			var sl := ch_s as KTWidgets.SkillSlot
			if not sl.hotkey_str.is_empty() and sl.hotkey_str != "␣":
				if seen_hk.has(sl.hotkey_str):
					hk_collision = true
				seen_hk[sl.hotkey_str] = sl
	_assert_true(
		not hk_collision
		and mg._active_hotbar_slots.has(KEY_8) and (mg._active_hotbar_slots[KEY_8] as KTWidgets.SkillSlot).hotkey_str == "8"
		and mg._active_hotbar_slots.has(KEY_9) and (mg._active_hotbar_slots[KEY_9] as KTWidgets.SkillSlot).hotkey_str == "9" and (mg._active_hotbar_slots[KEY_9] as KTWidgets.SkillSlot).icon_id == "unique"
		and mg._active_hotbar_slots.has(KEY_0) and (mg._active_hotbar_slots[KEY_0] as KTWidgets.SkillSlot).hotkey_str == "0" and (mg._active_hotbar_slots[KEY_0] as KTWidgets.SkillSlot).icon_id == "ploy",
		"U3: Unique actions bind to 8, 9 (3rd+ unbound) and Firefight Ploy binds to 0 with zero hotkey collisions"
	)
	mg.engine.end_operative_activation()

	# U2: Undo disabled in AI turn with prompt, and single Undo after AI turn returns to after Human's last op & before AI's actions
	mg.engine.state["mode"] = "ai"
	mg.engine.state["human_side"] = "A"
	for idx_r2 in range(mg.engine.state["operatives"].size()):
		var op_r2: Dictionary = mg.engine.state["operatives"][idx_r2]
		op_r2["pos"] = Vector2(4.0 + 2.0 * float(idx_r2 % 6), 18.0 if str(op_r2["side"]) == "A" else 4.0)
		op_r2["ready"] = true
		op_r2["incapacitated"] = false
	mg._killzone.sync_visual_positions(true)
	mg.engine.state["turn_side"] = "A"
	mg.engine.state["active_op_uid"] = ""
	mg.engine.undo_stack.clear()
	mg._in_ai_undo_guard = false
	mg._pre_ai_undo_stack.clear()
	mg._refresh_all_ui()

	var human_op: Dictionary = _side_ops(mg.engine, "A")[0]
	var human_uid := str(human_op["uid"])
	var start_pos := Vector2(6.0, 18.0)
	var dest_pos := Vector2(6.0, 15.0)
	human_op["pos"] = start_pos
	mg.engine.activate_operative(human_uid, "engage")
	var mv_res: Dictionary = mg.engine.execute_move(human_uid, "reposition", [start_pos, dest_pos])
	_assert_true(bool(mv_res["ok"]), "U2 setup: Human operative moves to (6.0, 15.0)")
	mg._end_human_activation() # Ends human activation -> switches to AI turn ("B")

	_assert_true(mg.engine.is_ai_turn() and mg._undo_btn.disabled, "U2a: Undo button is disabled during AI turn")
	mg._killzone._float_texts_3d.clear()
	var stack_len_during_ai := mg.engine.undo_stack.size()
	mg._unhandled_input(_make_key_event(KEY_Z, true)) # Ctrl+Z during AI turn must also be blocked!
	var blocked_msg_shown := not mg._killzone._float_texts_3d.is_empty() and str(mg._killzone._float_texts_3d[-1]["msg"]) == "敌方回合中无法撤回"
	_assert_true(mg.engine.is_ai_turn() and mg.engine.undo_stack.size() == stack_len_during_ai and blocked_msg_shown, "U2b: Z / Ctrl+Z during AI turn does not undo and shows '敌方回合中无法撤回'")

	# U8: While any modal overlay is open, _run_ai_step does not advance AI; closing it reschedules _ai_timer
	mg._ai_timer.stop()
	mg._card_lightbox.show_team_rulebook("IMP-AOD")
	mg._run_ai_step()
	_assert_true(str(mg.engine.state["active_op_uid"]) == "" and mg.engine.is_ai_turn(), "U8a: AI does not advance underneath open CardLightbox")
	mg._card_lightbox.hide()
	_assert_true(not mg._ai_timer.is_stopped(), "U8b: Closing overlay reschedules _ai_timer when still AI turn")
	mg._ai_timer.stop()

	# Let AI complete its entire activation
	var ai_guard := 0
	while mg.engine.is_ai_turn() and ai_guard < 16:
		mg._ai_timer.stop()
		mg._run_ai_step()
		if mg._dice_modal.visible:
			mg._dice_modal.visible = false
			mg._on_combat_modal_finished(mg._dice_modal.session)
		ai_guard += 1
	mg._ai_timer.stop()
	var ai_ready_after_ai := mg.engine.count_ready_operatives("B")
	_assert_true(not mg.engine.is_ai_turn() and ai_ready_after_ai < 6, "U2 setup: AI completed its activation and handed turn back to Human")

	# Press Undo ONCE -> must return to Human's moved state (6.0, 15.0) BEFORE AI acted (all 6 AI ops ready again!)
	mg._on_undo_pressed()
	mg._ai_timer.stop()
	var human_after_undo: Dictionary = mg.engine.get_op(human_uid)
	_assert_true(
		not mg.engine.is_ai_turn()
		and str(mg.engine.state["active_op_uid"]) == human_uid
		and (human_after_undo["pos"] as Vector2).distance_to(dest_pos) < 0.05
		and mg.engine.count_ready_operatives("B") == 6,
		"U2c: Single Undo restores state after Human's move and before all AI actions"
	)

	# U4 & U9: Settings / Help modal blocks hotkeys, Esc closes modal first, then Esc cancels interaction, F resets camera
	mg._killzone.start_move_mode(human_uid, "dash")
	mg._open_settings()
	_assert_true(mg._killzone.interaction_mode == "idle", "U9a: Opening settings clears active board interaction")
	mg._unhandled_input(_make_key_event(KEY_SPACE))
	_assert_true(str(mg.engine.state["active_op_uid"]) == human_uid, "U9b: Game hotkeys (Space) are blocked while settings modal is open")
	mg._unhandled_input(_make_key_event(KEY_ESCAPE))
	_assert_true(not mg._settings_modal.visible, "U4a: Esc closes open settings modal first")

	mg._killzone.start_move_mode(human_uid, "dash")
	mg._help_modal.visible = true
	mg._unhandled_input(_make_key_event(KEY_ESCAPE))
	_assert_true(not mg._help_modal.visible and mg._killzone.interaction_mode == "move", "U4b: Esc closes help modal with higher priority than cancelling interaction")
	mg._unhandled_input(_make_key_event(KEY_ESCAPE))
	_assert_true(mg._killzone.interaction_mode == "idle", "U4c: Esc cancels armed board interaction when no modal is open")

	mg._killzone.orbit_by(45.0)
	_assert_true(not mg._killzone.is_camera_default_isometric(), "U10a: Orbiting marks camera as non-default")
	var custom_yaw := mg._killzone.target_yaw_deg
	mg._on_viewport_resized()
	_assert_true(is_equal_approx(mg._killzone.target_yaw_deg, custom_yaw), "U10b: Viewport resize preserves custom camera angle without resetting to isometric")
	mg._unhandled_input(_make_key_event(KEY_F))
	_assert_true(mg._killzone.is_camera_default_isometric(), "U4d: F key resets camera to default isometric view")

	# U5: Clicking another friendly SquadMedallion while move/shoot is armed preserves selected_uid
	var ally_op: Dictionary = _side_ops(mg.engine, "A")[1]
	var ally_uid := str(ally_op["uid"])
	mg._killzone.start_move_mode(human_uid, "dash")
	mg._killzone._float_texts_3d.clear()
	var ally_med: KTWidgets.SquadMedallion = mg._medallions_by_uid[ally_uid]
	ally_med.clicked_unit.emit()
	_assert_true(
		mg._killzone.interaction_mode == "move"
		and mg._killzone.selected_uid == human_uid
		and not mg._killzone._float_texts_3d.is_empty(),
		"U5: Clicking another roster medallion while armed keeps selected_uid on active operative and floats hint"
	)

	# U7: Stale interaction_mode cleared when active_op_uid ends automatically; _on_board_move_committed guarded
	mg.engine.end_operative_activation()
	mg._ai_timer.stop()
	mg._refresh_all_ui()
	mg._ai_timer.stop()
	_assert_true(mg._killzone.interaction_mode == "idle", "U7a: _refresh_all_ui clears lingering move/shoot/fight interaction when activation ends")
	mg._killzone.interaction_mode = "move"
	mg._killzone.selected_uid = human_uid
	var pos_before_stale := human_after_undo["pos"] as Vector2
	mg._on_board_move_committed([pos_before_stale, pos_before_stale + Vector2(1.0, 0.0)])
	mg._ai_timer.stop()
	_assert_true(mg._killzone.interaction_mode == "idle" and (human_after_undo["pos"] as Vector2).is_equal_approx(pos_before_stale), "U7b: _on_board_move_committed rejects stale commit when not in local active turn")

	# U11: Click feedback for (1) clicking another ready op when one is active, (2) invalid shoot/fight target, (3) wrong-side deploy card
	mg.engine.state["mode"] = "hotseat"
	mg.engine.state["turn_side"] = "A"
	mg.engine.state["active_op_uid"] = ""
	human_op = _side_ops(mg.engine, "A")[0]
	ally_op = _side_ops(mg.engine, "A")[1]
	human_uid = str(human_op["uid"])
	ally_uid = str(ally_op["uid"])
	human_op["ready"] = true
	ally_op["ready"] = true
	mg.engine.activate_operative(human_uid, "engage")
	mg._refresh_all_ui()
	mg._killzone._float_texts_3d.clear()
	mg._on_board_operative_clicked(ally_uid)
	_assert_true(not mg._killzone._float_texts_3d.is_empty(), "U11a: Clicking another ready operative while one is active spawns floating text feedback")

	var usable_w: Array = mg.engine.get_usable_ranged_weapons(human_op)
	mg._killzone.start_shoot_mode(human_uid, usable_w[0])
	var hidden_enemy: Dictionary = _side_ops(mg.engine, "B")[0]
	mg._killzone.cached_shot_evals[str(hidden_enemy["uid"])] = {"valid": false, "reason": "隐匿且处于掩护中"}
	mg._killzone._float_texts_3d.clear()
	var scr_ally := mg._killzone._cam.unproject_position(mg._killzone.inches_to_world(ally_op["pos"], 0.9))
	mg._killzone._handle_left_click(scr_ally, false)
	_assert_true(not mg._killzone._float_texts_3d.is_empty(), "U11b: Clicking invalid target in shoot mode spawns floating text feedback")

	mg._killzone.start_fight_mode(human_uid)
	mg._killzone._float_texts_3d.clear()
	var scr_far_enemy := mg._killzone._cam.unproject_position(mg._killzone.inches_to_world(hidden_enemy["pos"], 0.9))
	mg._killzone._handle_left_click(scr_far_enemy, false)
	_assert_true(not mg._killzone._float_texts_3d.is_empty(), "U11c: Clicking out-of-range/invalid target in fight mode spawns floating text feedback")

	# U11d: Deployment phase clicking undeployed roster card of non-deploying side
	mg.engine.start_match("hotseat", "A", "sharpshooter", "dueller")
	mg.engine.auto_setup()
	mg._medallions_by_uid.clear()
	mg._refresh_all_ui()
	var dep_s := mg.engine.deploy_side()
	var other_s := "B" if dep_s == "A" else "A"
	var wrong_undep_uid := str(mg.engine.undeployed_ops(other_s)[0]["uid"])
	mg._killzone._float_texts_3d.clear()
	(mg._medallions_by_uid[wrong_undep_uid] as KTWidgets.SquadMedallion).clicked_unit.emit()
	_assert_true(not mg._killzone._float_texts_3d.is_empty(), "U11d: Clicking undeployed roster card of non-deploying side spawns floating text feedback")

	root.remove_child(mg)
	mg.free()


func _test_objectives_clear_of_walls() -> void:
	# Audit D1 + 5.3: 40mm objective markers (~0.79" radius) keep >= 0.70" clearance from impassable wall rule rects.
	var ok := true
	for map_id in KTData.MAP_IDS:
		var m: Dictionary = KTData.load_map(map_id)
		for o in m["objectives"]:
			var p: Vector2 = o["pos"]
			if p.x < 0.8 or p.y < 0.8 or p.x > 29.2 or p.y > 21.2:
				ok = false
			for t in m["terrains"]:
				var tr: Rect2 = t["rect"]
				if KTGeometry.is_impassable_terrain(t) and KTGeometry.point_to_rect_distance(p, tr) < 0.70:
					ok = false
	_assert_true(ok, "All 40mm objective markers keep >=0.70\" clearance from impassable terrain rects across maps 1–6")


func _tac_force(seed_v: int, side_id: String, tac_id: String) -> KTEngine:
	var eng := _tac_match(seed_v, "dominate", "dominate")
	eng.state["sides"][side_id]["tac_op"] = tac_id
	eng.state["phase"] = "FIREFIGHT"
	eng.state["tp"] = 2
	for op in eng.state["operatives"]:
		op["pos"] = Vector2(-99.0, -99.0)
	eng.recalculate_objectives_and_kills()
	return eng


func _tac_activate(eng: KTEngine, op: Dictionary, pos: Vector2) -> void:
	op["pos"] = pos
	op["ap_left"] = 3
	op["actions_taken"] = []
	eng.state["active_op_uid"] = str(op["uid"])
	eng.state["active_is_counteract"] = false
	eng.state["turn_side"] = str(op["side"])
	eng.recalculate_objectives_and_kills()


func _ua_enabled(eng: KTEngine, op: Dictionary, id: String) -> bool:
	for a in eng.get_unique_actions(op):
		if str(a["id"]) == id:
			return bool(a["enabled"])
	return false


func _vp_tac(eng: KTEngine, side_id: String) -> int:
	return int(eng.state["sides"][side_id]["vp_tac"])


func _test_tac_ops_extended() -> void:
	# All on default map 4: A deploys south, so B's territory is the north half; flanks are west / east.
	# --- 侧翼 ---
	var e := _tac_force(820, "A", "flank")
	var a := _side_ops(e, "A")
	var b := _side_ops(e, "B")
	a[0]["pos"] = Vector2(5.0, 5.0)
	_assert_true(e.flank_controller("west") == "A" and e.flank_controller("east") == "", "Flank: operative wholly in a flank and in enemy territory controls it")
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 1, "Flank: 1VP per controlled flank at the end of TP2")
	a[0]["pos"] = Vector2(15.2, 5.0)
	_assert_true(e.flank_controller("west") == "" and e.flank_controller("east") == "", "Flank: an operative straddling the dividing line contests neither flank")
	a[0]["pos"] = Vector2(5.0, 5.0)
	e._tac_new_tp()
	e.state["tp"] = 3
	e._score_tac_op_end_of_tp()
	e._tac_new_tp()
	e.state["tp"] = 4
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 4, "Flank: a flank also held at the end of TP3 is worth 2VP in TP4 (1+1+2)")

	# --- 插旗 ---
	e = _tac_force(821, "A", "plant_banner")
	a = _side_ops(e, "A")
	b = _side_ops(e, "B")
	_tac_activate(e, a[0], Vector2(3.0, 5.0))
	_assert_true(not _ua_enabled(e, a[0], "tac_plant_banner"), "Plant Banner: refused within 5\" of a neutral killzone edge")
	_tac_activate(e, a[0], Vector2(10.0, 19.0))
	_assert_true(not _ua_enabled(e, a[0], "tac_plant_banner"), "Plant Banner: refused outside enemy territory")
	_tac_activate(e, a[0], Vector2(10.0, 5.0))
	_assert_true(bool(e.execute_unique_action(str(a[0]["uid"]), "tac_plant_banner")["ok"]) and (e.state["eq_markers"] as Array).size() == 1 and int(a[0]["ap_left"]) == 2, "Plant Banner: 1AP action places the banner marker")
	a[0]["actions_taken"] = []
	_assert_true(not _ua_enabled(e, a[0], "tac_plant_banner"), "Plant Banner: only once per battle")
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 2, "Plant Banner: controlled banner in enemy territory with no enemy contesting = 2VP")
	a[0]["pos"] = Vector2(-99.0, -99.0)
	_tac_activate(e, b[0], Vector2(10.9, 5.0))
	e._tac_new_tp()
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 2, "Plant Banner: a banner the owner does not control scores nothing")
	_assert_true(bool(e.execute_unique_action(str(b[0]["uid"]), "tac_grab_banner")["ok"]) and (e.state["eq_markers"] as Array).is_empty() and (b[0]["carried"] as Array).size() == 1, "Plant Banner: the enemy can pick the banner up")

	# --- 放置装置 ---
	e = _tac_force(822, "A", "plant_devices")
	a = _side_ops(e, "A")
	var enemy_obj: Dictionary = {}
	var mid_obj: Dictionary = {}
	for o in e.state["map"]["objectives"]:
		if e.objective_owner(o) == "B":
			enemy_obj = o
		elif e.objective_owner(o) == "":
			mid_obj = o
	_assert_true(not enemy_obj.is_empty() and not mid_obj.is_empty(), "Map 4 has one objective in B's territory and one on the centre line")
	_tac_activate(e, a[0], enemy_obj["pos"])
	_assert_true(bool(e.execute_unique_action(str(a[0]["uid"]), "tac_plant_device")["ok"]), "Plant Devices: device placed on a controlled objective")
	a[0]["actions_taken"] = []
	_assert_true(not _ua_enabled(e, a[0], "tac_plant_device"), "Plant Devices: an objective cannot take a second device")
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 1, "Plant Devices: device on the enemy objective = 1VP")

	# --- 回收 ---
	e = _tac_force(823, "A", "retrieval")
	a = _side_ops(e, "A")
	var objs: Array = e.state["map"]["objectives"]
	e.state["tp"] = 1
	_tac_activate(e, a[0], objs[0]["pos"])
	_assert_true(not _ua_enabled(e, a[0], "tac_search"), "Retrieval: no Search in turning point 1")
	e.state["tp"] = 2
	_assert_true(bool(e.execute_unique_action(str(a[0]["uid"]), "tac_search")["ok"]) and _vp_tac(e, "A") == 1 and (a[0]["carried"] as Array).size() == 1, "Retrieval: Search scores 1VP immediately and gives a Retrieval marker")
	_tac_activate(e, a[0], objs[1]["pos"])
	_assert_true(not _ua_enabled(e, a[0], "tac_search"), "Retrieval: cannot Search while carrying a marker")
	a[0]["carried"] = []
	_tac_activate(e, a[0], objs[0]["pos"])
	_assert_true(not _ua_enabled(e, a[0], "tac_search"), "Retrieval: an objective can be searched only once")
	a[0]["carried"] = [{"item": "retrieval", "team": "A"}]
	e._tac_new_tp()
	e.state["tp"] = 4
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 2, "Retrieval: +1VP per carried Retrieval marker at the end of the battle")

	# --- 扫荡清理 (V11.5 errata) ---
	e = _tac_force(824, "A", "sweep_clear")
	a = _side_ops(e, "A")
	b = _side_ops(e, "B")
	mid_obj = {}
	for o in e.state["map"]["objectives"]:
		if e.objective_owner(o) == "":
			mid_obj = o
	var mid_id := str(mid_obj["id"])
	b[0]["pos"] = mid_obj["pos"]
	e.recalculate_objectives_and_kills()
	b[0]["wounds_cur"] = 0
	b[0]["incapacitated"] = true
	e.recalculate_objectives_and_kills()
	_assert_true(bool(e.state["objectives_state"][mid_id]["tac"].get("sweep_A", false)), "Sweep & Clear: enemy incapacitated while contesting an objective leaves a Sweep marker")
	_tac_activate(e, a[0], mid_obj["pos"])
	_assert_true(bool(e.execute_unique_action(str(a[0]["uid"]), "tac_clear")["ok"]), "Sweep & Clear: Clear action on a controlled objective")
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 2, "Sweep & Clear: cleared + swept objective with no enemy contesting = 2VP (capped per TP)")
	e._tac_new_tp()
	var tac_d: Dictionary = e.state["objectives_state"][mid_id]["tac"]
	_assert_true(not tac_d.has("sweep_A") and not tac_d.has("cleared_A"), "Sweep & Clear: markers are removed in the next strategy phase")

	# --- 追踪敌军 ---
	e = _tac_force(825, "A", "track_enemy")
	a = _side_ops(e, "A")
	b = _side_ops(e, "B")
	a[0]["pos"] = Vector2(26.0, 14.1)
	a[0]["order"] = "conceal"
	b[0]["pos"] = Vector2(26.0, 7.5)
	b[0]["order"] = "engage"
	_assert_true(e.tracked_enemies("A").size() == 1, "Track Enemy: concealed operative in cover within 6\" tracks an enemy it can target")
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 1, "Track Enemy: one tracked enemy = 1VP")
	a[0]["order"] = "engage"
	_assert_true(e.tracked_enemies("A").is_empty(), "Track Enemy: an Engage-order operative tracks nobody")

	# --- 敌情 ---
	e = _tac_force(826, "A", "surveil")
	a = _side_ops(e, "A")
	b = _side_ops(e, "B")
	b[0]["ready"] = true
	b[0]["pos"] = Vector2(26.0, 7.5)
	a[0]["order"] = "conceal"
	_tac_activate(e, a[0], Vector2(26.0, 14.1))
	_assert_true(e.surveil_targets(a[0]).is_empty(), "Surveillance: an enemy within 6\" cannot be chosen")
	b[0]["pos"] = Vector2(26.0, 6.3)
	_assert_true(bool(e.execute_target_action(str(a[0]["uid"]), "tac_surveil", str(b[0]["uid"]))["ok"]) and (b[0]["surveilled_by"] as Array).has("A"), "Surveillance: Surveil marks a visible ready enemy more than 6\" away")
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 1, "Surveillance: 1VP per surveilled enemy visible to a friendly operative")
	e._tac_new_tp()
	_assert_true((b[0]["surveilled_by"] as Array).is_empty(), "Surveillance: marks are cleared for the next turning point")
	b[0]["surveilled_by"] = ["A"]
	a[0]["pos"] = Vector2(25.0, 17.3)
	b[0]["pos"] = Vector2(25.0, 20.8)
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 1, "Surveillance: a surveilled enemy nobody can see scores nothing")

	# --- 窃取情报 ---
	e = _tac_force(827, "A", "steal_intel")
	a = _side_ops(e, "A")
	b = _side_ops(e, "B")
	b[0]["pos"] = Vector2(20.0, 20.0)
	b[0]["wounds_cur"] = 0
	b[0]["incapacitated"] = true
	e.recalculate_objectives_and_kills()
	var marks: Array = e.state["eq_markers"]
	_assert_true(marks.size() == 1 and str(marks[0]["kind"]) == "intel" and str(marks[0]["side"]) == "A", "Steal Intel: an incapacitated enemy leaves an Intel marker")
	_tac_activate(e, a[0], Vector2(20.9, 20.0))
	_assert_true(bool(e.execute_unique_action(str(a[0]["uid"]), "tac_pickup")["ok"]) and (a[0]["carried"] as Array).size() == 1 and marks.is_empty(), "Steal Intel: a friendly operative picks the marker up")
	e._score_tac_op_end_of_tp()
	_assert_true(_vp_tac(e, "A") == 1, "Steal Intel: 1VP while any friendly operative carries Intel")
	a[0]["carried"] = [{"item": "intel", "team": "A"}, {"item": "intel", "team": "A"}]
	a[0]["actions_taken"] = []
	marks.append({"kind": "intel", "side": "A", "pos": Vector2(20.0, 20.0)})
	_assert_true(not _ua_enabled(e, a[0], "tac_pickup"), "Steal Intel: an operative carries at most 2 Intel markers")


func _test_handoff_rules_and_combat_extensions() -> void:
	print("--- Handoff Rules & Combat Extensions (Blast / Torrent / Vantage Parent / Improved Cover / TP1 Secure) ---")
	var eng := KTEngine.new(2026)
	eng.start_match("hotseat", "A", "sharpshooter", "dueller")
	eng.auto_deploy_all()
	eng.auto_initiative()
	eng.choose_primary_op(str(eng.state["turn_side"]), "key")
	eng.choose_primary_op(str(eng.state["turn_side"]), "kill")
	eng.pass_strategy(str(eng.state["turn_side"]))
	eng.pass_strategy(str(eng.state["turn_side"]))

	# 1. Crit Op Secure (`占领`) blocked in TP1, enabled in TP2+ (MissionCards.kt L36)
	var terrains: Array = eng.state["map"]["terrains"]
	terrains.clear()
	for op_v in eng.state["operatives"]:
		var od: Dictionary = op_v
		od["pos"] = Vector2(2.0, 20.5 if str(od["side"]) == "A" else 1.5)
		od["ready"] = true
		od["order"] = "engage"
		od["incapacitated"] = false
	var a_ops := _side_ops(eng, "A")
	var b_ops := _side_ops(eng, "B")
	var obj0: Dictionary = (eng.state["map"]["objectives"] as Array)[0]
	var obj0_pos: Vector2 = obj0["pos"]
	a_ops[0]["pos"] = obj0_pos
	eng.recalculate_objectives_and_kills()
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.activate_operative(str(a_ops[0]["uid"]), "engage")
	var acts_tp1: Dictionary = eng.get_available_actions(a_ops[0])
	_assert_true(not bool((acts_tp1["secure"] as Dictionary)["enabled"]), "Crit Op Secure is disabled in TP1")
	eng.state["tp"] = 2
	var acts_tp2: Dictionary = eng.get_available_actions(a_ops[0])
	_assert_true(bool((acts_tp2["secure"] as Dictionary)["enabled"]), "Crit Op Secure is enabled in TP2+ when controlling objective")
	eng.end_operative_activation()
	a_ops[0]["ready"] = true

	# 2. Mine reverse 2" spacing check: non-mine equipment marker rejected within 2" of existing mine
	eng.state["eq_markers"] = [{"kind": "mine", "side": "A", "pos": Vector2(10.0, 18.0)}]
	var rev_mine_chk: Dictionary = eng.validate_equipment_pos("A", "comms", Vector2(11.2, 18.0), false)
	var far_mine_chk: Dictionary = eng.validate_equipment_pos("A", "comms", Vector2(13.2, 18.0), false)
	_assert_true(
		not bool(rev_mine_chk["ok"]) and bool(far_mine_chk["ok"]),
		"Mine 2\" exclusion applies symmetrically when placing another marker after a mine"
	)
	eng.state["eq_markers"] = []

	# 3. Vantage connected Heavy terrain (`parent` match, Rulebook p.36 L1530 / V1.4 FAQ L1539) & Fire Steps `exposed`
	terrains.clear()
	terrains.append({
		"id": "sh_deck", "name": "堡垒·二层制高点", "kind": "vantage",
		"elevation": 3.0, "rect": Rect2(8.0, 4.0, 4.0, 4.0), "parent": "stronghold_test"
	})
	terrains.append({
		"id": "sh_parapet", "name": "堡垒·二层墙垛", "kind": "heavy_wall",
		"elevation": 3.4, "blocks_los_ground": false, "rect": Rect2(8.0, 8.0, 4.0, 0.8), "parent": "stronghold_test"
	})
	terrains.append({
		"id": "mid_heavy", "name": "独立重型残垣", "kind": "heavy_low",
		"elevation": 1.6, "blocks_los_ground": false, "rect": Rect2(14.0, 10.0, 4.0, 1.0), "parent": "other_ruin"
	})
	var test_gun := {
		"id": "test_bolt", "name": "爆矢步枪", "kind": "ranged",
		"attacks": 4, "hit": 3, "normal_dmg": 3, "crit_dmg": 4, "rules": {}
	}
	var shooter: Dictionary = a_ops[0]
	var tgt_clear: Dictionary = b_ops[0]
	shooter["pos"] = Vector2(10.0, 6.2)    # on sh_deck (+3.0"), >1" from sh_parapet (y=8.0), sees over 3.4" parapet
	tgt_clear["pos"] = Vector2(10.0, 14.0) # ground, sightline crosses sh_parapet of SAME parent
	tgt_clear["order"] = "engage"
	var ev_same_parent: Dictionary = KTGeometry.evaluate_shot(shooter, tgt_clear, test_gun, eng.state)
	_assert_true(
		bool(ev_same_parent["valid"]) and not bool(ev_same_parent["obscured"]),
		"Vantage shooter ignores Heavy terrain sharing the same parent model for Obscured (p.36 L1530)"
	)
	shooter["pos"] = Vector2(11.5, 5.5)
	tgt_clear["pos"] = Vector2(17.0, 14.0) # sightline crosses mid_heavy (different parent, >1" from both)
	var ev_diff_parent: Dictionary = KTGeometry.evaluate_shot(shooter, tgt_clear, test_gun, eng.state)
	_assert_true(
		bool(ev_diff_parent["valid"]) and bool(ev_diff_parent["obscured"]),
		"Unrelated Heavy terrain >1\" from both shooter and target still causes Obscured"
	)

	var map_with_fs: Dictionary = KTData.load_map("volkus_25_01")
	var found_fs_exposed := false
	for mt in map_with_fs["terrains"]:
		var mtd: Dictionary = mt
		if str(mtd.get("parent", "")).begins_with("fire_steps") and bool(mtd.get("exposed", false)):
			found_fs_exposed = true
	_assert_true(found_fs_exposed, "Volkus official maps mark Fire Steps with exposed=true (Rulebook p.42)")

	# 4. Improved Cover choice toggle (`2norm` vs `1crit`, Rulebook p.36 L1528–1529 & p.113 L3540–3542)
	var ev_imp := {
		"valid": true, "visible": true, "dist": 6.0, "cover": "improved",
		"obscured": false, "vantage_accurate": 0, "protective": false, "smoke": false
	}
	var rng_ic := RandomNumberGenerator.new()
	rng_ic.seed = 777
	var sess_ic: Dictionary = KTCombat.create_shooting_session(shooter, tgt_clear, test_gun, ev_imp, eng.state, rng_ic, false)
	KTCombat.lock_in_shooting_attacks(sess_ic, eng.state, rng_ic)
	var ok_2n: bool = int(sess_ic["auto_def_normals"]) == 2 and int(sess_ic["auto_def_crits"]) == 0 and (sess_ic["def_dice"] as Array).size() == 1
	KTCombat.set_improved_cover_choice(sess_ic, "1crit", rng_ic)
	var ok_1c: bool = int(sess_ic["auto_def_normals"]) == 0 and int(sess_ic["auto_def_crits"]) == 1 and (sess_ic["def_dice"] as Array).size() == 2
	_assert_true(ok_2n and ok_1c, "Improved Cover toggles cleanly between 2 auto normal saves (+1 roll) and 1 auto crit save (+2 rolls)")

	# 5. Blast X + Hot once per Shoot action (Rulebook p.92 L2728–2745, p.93 L2780–2782)
	terrains.clear()
	terrains.append({
		"id": "blast_wall", "name": "爆炸隔墙", "kind": "heavy_wall",
		"elevation": 3.5, "blocks_los_ground": true, "rect": Rect2(16.2, 8.0, 0.8, 4.0), "parent": "w1"
	})
	terrains.append({
		"id": "blast_light", "name": "轻型掩体", "kind": "light",
		"elevation": 1.0, "rect": Rect2(12.8, 11.2, 2.4, 0.6), "parent": "l1"
	})
	for idx_b in range(eng.state["operatives"].size()):
		var ob: Dictionary = eng.state["operatives"][idx_b]
		ob["pos"] = Vector2(2.0 + 1.1 * float(idx_b % 6), 20.5 if str(ob["side"]) == "A" else 1.5)
		ob["wounds_cur"] = int(ob["wounds_max"])
		ob["incapacitated"] = false
	shooter["pos"] = Vector2(14.0, 16.0)
	shooter["order"] = "engage"
	var w_shooter_hp0: int = int(shooter["wounds_cur"])
	var b_prim: Dictionary = b_ops[0]
	var b_conceal_sec: Dictionary = b_ops[1]
	var b_walled: Dictionary = b_ops[2]
	var a_friendly_sec: Dictionary = a_ops[1]
	b_prim["pos"] = Vector2(14.5, 10.3)         # in light cover behind blast_light, Engage -> valid primary!
	b_prim["order"] = "engage"
	b_conceal_sec["pos"] = Vector2(13.1, 10.3)  # within 2" of b_prim, Concealed in cover -> still hit by Blast!
	b_conceal_sec["order"] = "conceal"
	a_friendly_sec["pos"] = Vector2(14.0, 8.5)  # friendly within 2" & visible to b_prim -> also hit by Blast!
	a_friendly_sec["order"] = "conceal"
	b_walled["pos"] = Vector2(17.6, 10.0)       # behind blast_wall -> NOT visible to b_prim -> excluded!
	b_walled["order"] = "engage"

	var blast_hot_w := {
		"id": "test_plasma_cannon", "name": "过载等离子炮", "kind": "ranged",
		"attacks": 4, "hit": 3, "normal_dmg": 4, "crit_dmg": 5,
		"rules": {"blast": 2.0, "hot": true}
	}
	var ev_blast_prim: Dictionary = KTGeometry.evaluate_shot(shooter, b_prim, blast_hot_w, eng.state)
	var rng_bl := RandomNumberGenerator.new()
	rng_bl.seed = 909
	var sess_bl: Dictionary = KTCombat.create_shooting_session(shooter, b_prim, blast_hot_w, ev_blast_prim, eng.state, rng_bl, true)
	var pend_bl: Array = sess_bl["pending_targets"]
	var pend_uids: Array[String] = []
	var inherited_cover_ok := true
	for pt in pend_bl:
		var ptd: Dictionary = pt
		pend_uids.append(str(ptd["defender_uid"]))
		var pev: Dictionary = ptd["shot_eval"]
		if str(pev["cover"]) != str(ev_blast_prim["cover"]) or bool(pev["obscured"]) != bool(ev_blast_prim["obscured"]):
			inherited_cover_ok = false
	_assert_true(
		pend_bl.size() == 2
		and pend_uids.has(str(b_conceal_sec["uid"]))
		and pend_uids.has(str(a_friendly_sec["uid"]))
		and not pend_uids.has(str(b_walled["uid"]))
		and inherited_cover_ok,
		"Blast 2\" hits Concealed enemy & nearby friendly visible to primary, excludes wall-blocked target, and inherits primary cover/obscured (p.92 L2728–2745)"
	)
	# Force roll of 1 on primary attack so worst_effective_hit == 1, verify Hot does NOT trigger before secondary targets finish!
	sess_bl["atk_dice"] = [
		{"id": 0, "val": 1, "rerolled": false, "locked": false},
		{"id": 1, "val": 5, "rerolled": false, "locked": false},
		{"id": 2, "val": 5, "rerolled": false, "locked": false},
		{"id": 3, "val": 5, "rerolled": false, "locked": false},
	]
	KTCombat.lock_in_shooting_attacks(sess_bl, eng.state, rng_bl)
	KTCombat.finalize_shooting_resolution(sess_bl, eng.state, rng_bl)
	var hot_deferred_ok: bool = not bool(sess_bl["hot_resolved"]) and int(shooter["wounds_cur"]) == w_shooter_hp0
	while not (sess_bl["pending_targets"] as Array).is_empty():
		KTCombat.advance_next_shooting_target(sess_bl, 0, eng.state, rng_bl)
		KTCombat.lock_in_shooting_attacks(sess_bl, eng.state, rng_bl)
		KTCombat.finalize_shooting_resolution(sess_bl, eng.state, rng_bl)
	_assert_true(
		hot_deferred_ok and bool(sess_bl["hot_resolved"]) and int(sess_bl["multi_index"]) == 3,
		"Hot weapon rule defers self-damage check until all Blast targets finish resolving (p.93 L2780–2782)"
	)

	# 6. Torrent X (Rulebook p.94 L2840–2848): valid enemies only, independent cover, early stop
	shooter["wounds_cur"] = int(shooter["wounds_max"])
	shooter["incapacitated"] = false
	a_friendly_sec["pos"] = Vector2(4.0, 19.5) # keep friendly out of enemy control range!
	b_walled["pos"] = Vector2(26.0, 2.0)
	var b_open_sec: Dictionary = b_ops[3]
	b_open_sec["pos"] = Vector2(15.2, 12.3) # within 2" of b_prim (14.5, 10.3) and in front of blast_light (y=11.2) -> no cover!
	b_open_sec["order"] = "engage"
	var torrent_w := {
		"id": "test_spewer", "name": "瘟疫喷吐器", "kind": "ranged",
		"attacks": 5, "hit": 2, "normal_dmg": 3, "crit_dmg": 3,
		"rules": {"torrent": 2.0}
	}
	var ev_tor_prim: Dictionary = KTGeometry.evaluate_shot(shooter, b_prim, torrent_w, eng.state)
	var sess_tor: Dictionary = KTCombat.create_shooting_session(shooter, b_prim, torrent_w, ev_tor_prim, eng.state, rng_bl, true)
	var pend_tor: Array = sess_tor["pending_targets"]
	var tor_uids: Array[String] = []
	var open_sec_cover := ""
	for pt_t in pend_tor:
		var ptd_t: Dictionary = pt_t
		tor_uids.append(str(ptd_t["defender_uid"]))
		if str(ptd_t["defender_uid"]) == str(b_open_sec["uid"]):
			open_sec_cover = str((ptd_t["shot_eval"] as Dictionary)["cover"])
	_assert_true(
		tor_uids.has(str(b_open_sec["uid"]))
		and not tor_uids.has(str(b_conceal_sec["uid"]))
		and not tor_uids.has(str(a_friendly_sec["uid"]))
		and str(ev_tor_prim["cover"]) == "standard" and open_sec_cover == "none",
		"Torrent 2\" targets only valid enemies within 2\" of primary and evaluates cover independently (p.94 L2840–2848)"
	)
	KTCombat.lock_in_shooting_attacks(sess_tor, eng.state, rng_bl)
	KTCombat.finalize_shooting_resolution(sess_tor, eng.state, rng_bl)
	KTCombat.skip_remaining_torrent_targets(sess_tor, eng.state, rng_bl)
	_assert_true((sess_tor["pending_targets"] as Array).is_empty(), "Torrent allows ending multi-target sweep early")

	# 7. pm_flail visibility check + tac_status_text readout
	var flail_op: Dictionary = _find_by_op_id(eng, "pm_fighter")
	if not flail_op.is_empty():
		flail_op["pos"] = Vector2(15.4, 10.0) # west of blast_wall (x=16.2..17.0)
		flail_op["order"] = "engage"
		flail_op["ready"] = true
		b_walled["pos"] = Vector2(17.5, 10.0) # east of blast_wall, < 2" away horizontally but blocked by 3.5" wall!
		var hp_walled_before := int(b_walled["wounds_cur"])
		eng.state["turn_side"] = str(flail_op["side"])
		eng.state["active_op_uid"] = ""
		eng.activate_operative(str(flail_op["uid"]), "engage")
		eng.execute_unique_action(str(flail_op["uid"]), "pm_flail")
		_assert_true(int(b_walled["wounds_cur"]) == hp_walled_before, "pm_flail only damages operatives visible to the Fighter")
		eng.end_operative_activation()

	eng.state["sides"]["A"]["tac_op"] = "flank"
	eng.state["sides"]["A"]["tac_revealed"] = false
	var hidden_tac := eng.tac_status_text("A")
	eng.state["sides"]["A"]["tac_revealed"] = true
	var shown_tac := eng.tac_status_text("A")
	_assert_true(hidden_tac.is_empty() and shown_tac.contains("西") and shown_tac.contains("东"), "tac_status_text hides unrevealed Tac Op and formats revealed status")


func _test_handoff_ui_extensions() -> void:
	print("--- Handoff UI & Hotseat Extensions ---")
	var mg := MainGame.new()
	mg.size = Vector2(1600, 960)
	root.add_child(mg)
	mg._ai_timer.stop()

	# 1. Title screen `▶ 继续战局` hidden before first battle, visible after battle started
	_assert_true(
		mg._btn_resume != null and not mg._btn_resume.visible,
		"Title screen hides '▶ 继续战局' before a real battle has started"
	)
	mg._sel_mode = "hotseat"
	mg._title_overlay.visible = false
	mg._start_new_battle()
	mg._return_to_main_menu()
	_assert_true(
		mg._btn_resume.visible and mg._battle_in_progress,
		"Title screen shows '▶ 继续战局' when returning to menu from an active battle"
	)

	# 2. Hotseat secret-step privacy curtain on side switch
	mg._title_overlay.visible = false
	mg._hotseat_privacy_enabled = true
	mg._start_new_battle()
	mg.engine.choose_drop_zone(false)
	mg._refresh_all_ui()
	_assert_true(not mg._needs_hotseat_curtain(), "Roll-off winner seamlessly enters SELECT without redundant curtain")
	var first_sel := mg.engine.setup_side()
	mg.engine.confirm_roster(first_sel, mg.engine.default_roster(first_sel))
	mg._refresh_all_ui()
	var has_curtain_btn := mg._context_hb.find_child("hotseat_ack_btn", true, false) != null
	_assert_true(
		mg._needs_hotseat_curtain() and has_curtain_btn,
		"Hotseat privacy curtain blocks opponent secret SELECT hand until acknowledged"
	)
	mg._unhandled_input(_make_key_event(KEY_SPACE))
	_assert_true(
		not mg._needs_hotseat_curtain(),
		"Pressing Space acknowledges Hotseat privacy curtain and reveals secret selection panel"
	)

	# 3. DiceCombatModal grow_vertical, melee empty parry & crit-parries-normal UI buttons, and [Z] undo in combat modal
	mg._hotseat_privacy_enabled = false
	mg.engine.auto_deploy_all()
	mg.engine.auto_initiative()
	mg.engine.choose_primary_op(str(mg.engine.state["turn_side"]), "kill")
	mg.engine.choose_primary_op(str(mg.engine.state["turn_side"]), "key")
	mg.engine.pass_strategy(str(mg.engine.state["turn_side"]))
	mg.engine.pass_strategy(str(mg.engine.state["turn_side"]))
	(mg.engine.state["map"]["terrains"] as Array).clear()
	var a_op: Dictionary = _side_ops(mg.engine, "A")[0]
	var b_op: Dictionary = _side_ops(mg.engine, "B")[0]
	a_op["pos"] = Vector2(10.0, 10.0)
	b_op["pos"] = Vector2(11.1, 10.0)
	var saved_pause_test: float = KTSettings.rule_step_pause()
	KTSettings.set_value("combat/rule_step_pause", 0.0)
	mg.engine.state["turn_side"] = "A"
	mg.engine.state["active_op_uid"] = ""
	mg.engine.activate_operative(str(a_op["uid"]), "engage")
	mg._on_board_fight_target_clicked(str(b_op["uid"]))
	_assert_true(
		mg._dice_modal.visible and mg._dice_modal._cmd_panel.grow_vertical == Control.GROW_DIRECTION_BEGIN,
		"DiceCombatModal grows upward from bottom anchor to prevent viewport button clipping"
	)
	# Force actor 1 crit + 1 normal, opponent 1 crit + 1 normal -> verify crit-parry-normal button appears and discards normal
	mg._dice_modal.session["turn"] = "attacker"
	mg._dice_modal.session["att"]["crits"] = 1
	mg._dice_modal.session["att"]["normals"] = 1
	mg._dice_modal.session["def"]["crits"] = 1
	mg._dice_modal.session["def"]["normals"] = 1
	mg._dice_modal._rebuild_ui()
	var crit_norm_btn: Button = null
	for ch_b in mg._dice_modal._action_bar.get_children():
		if not ch_b.is_queued_for_deletion() and ch_b is Button and (ch_b as Button).text.contains("暴击挡普骰"):
			crit_norm_btn = ch_b as Button
	_assert_true(crit_norm_btn != null and not crit_norm_btn.disabled, "DiceCombatModal exposes '暴击挡普骰' button when opponent has both crit and normal")
	if crit_norm_btn != null:
		crit_norm_btn.pressed.emit()
	_assert_true(
		int(mg._dice_modal.session["def"]["crits"]) == 1 and int(mg._dice_modal.session["def"]["normals"]) == 0,
		"Clicking '暴击挡普骰' discards opponent normal die while leaving crit intact (p.18 L893)"
	)
	# Force opponent 0 dice on actor's turn (no Brutal) -> verify empty parry button is enabled
	mg._dice_modal.session["step"] = "duel"
	mg._dice_modal.session["turn"] = "attacker"
	mg._dice_modal.session["att"]["crits"] = 0
	mg._dice_modal.session["att"]["normals"] = 1
	mg._dice_modal.session["def"]["crits"] = 0
	mg._dice_modal.session["def"]["normals"] = 0
	mg._dice_modal.session["def"]["rules"]["brutal"] = false
	mg._dice_modal._rebuild_ui()
	var empty_parry_btn: Button = null
	for ch_ep in mg._dice_modal._action_bar.get_children():
		if not ch_ep.is_queued_for_deletion() and ch_ep is Button and (ch_ep as Button).text.contains("空格挡"):
			empty_parry_btn = ch_ep as Button
	_assert_true(empty_parry_btn != null and not empty_parry_btn.disabled, "DiceCombatModal enables empty parry button when opponent has 0 unresolved successes (p.18 L897)")

	# Pressing [Z] while DiceCombatModal is visible triggers Undo and closes modal
	var a_uid_chk := str(a_op["uid"])
	mg._unhandled_input(_make_key_event(KEY_Z))
	var restored_a: Dictionary = mg.engine.get_op(a_uid_chk)
	_assert_true(
		not mg._dice_modal.visible and int(restored_a["ap_left"]) == int(restored_a["apl_base"]),
		"Pressing [Z] inside DiceCombatModal cleanly undoes combat and restores AP"
	)

	# UI Audit fixes: Ceaseless button, Transhuman 1/TP + 0-norm disable, _tac_strip isolation from _top_crest
	mg.engine.state["sides"]["A"]["tac_op"] = "martyrs"
	mg.engine.state["sides"]["A"]["tac_revealed"] = true
	mg.engine.state["sides"]["B"]["tac_op"] = "dominate"
	mg.engine.state["sides"]["B"]["tac_revealed"] = true
	mg._refresh_all_ui()
	_assert_true(
		mg._tac_strip != null and mg._tac_strip.visible and mg._tac_strip_hb.get_child_count() == 2 and mg._top_crest.offset_right - mg._top_crest.offset_left <= 820.0,
		"Revealed Tac Ops render in dedicated _tac_strip below _top_crest without colliding with _sys_bar"
	)

	# Shooting modal Ceaseless button + Transhuman 1/TP enforcement
	var shooter_b: Dictionary = _find_by_op_id(mg.engine, "pm_champion")
	var target_a: Dictionary = _find_by_op_id(mg.engine, "aod_gunner")
	mg.engine.state["sides"]["B"]["active_ploys"] = ["慢步死亡"]
	shooter_b["moved_distance_tp"] = 0.0
	var cs_w: Dictionary = {"id": "t_bolt", "name": "爆矢枪", "kind": "ranged", "attacks": 4, "hit": 3, "normal_dmg": 3, "crit_dmg": 4, "rules": {}}
	var cs_eval: Dictionary = {"valid": true, "distance": 6.0, "visible": true, "cover": "none", "obscured": false, "vantage_accurate": 0}
	var cs_sess: Dictionary = KTCombat.create_shooting_session(shooter_b, target_a, cs_w, cs_eval, mg.engine.state, mg.engine.rng, false)
	mg._dice_modal.open_shooting(mg.engine, cs_sess, false)
	var has_cs_btn := false
	for ch_cs in mg._dice_modal._action_bar.get_children():
		if not ch_cs.is_queued_for_deletion() and ch_cs is Button and (ch_cs as Button).text.contains("无休"):
			has_cs_btn = true
	_assert_true(has_cs_btn, "DiceCombatModal displays '无休重掷' button when shooting weapon has Ceaseless (慢步死亡)")

	# Advance to defence_roll and verify Transhuman enforces 1/TP and disables when 0 normal saves
	mg.engine.state["sides"]["A"]["cp"] = 3
	mg.engine.state["sides"]["A"]["used_ploys_tp"] = []
	cs_sess["step"] = "defence_roll"
	cs_sess["auto_def_normals"] = 0
	cs_sess["auto_def_crits"] = 0
	cs_sess["def_dice"] = [{"id": 0, "val": 1, "rerolled": false, "locked": false}, {"id": 1, "val": 2, "rerolled": false, "locked": false}]
	mg._dice_modal._rebuild_ui()
	var th_btn_0: Button = null
	for ch_t0 in mg._dice_modal._action_bar.get_children():
		if not ch_t0.is_queued_for_deletion() and ch_t0 is Button and (ch_t0 as Button).text.contains("超人体格"):
			th_btn_0 = ch_t0 as Button
	_assert_true(th_btn_0 != null and th_btn_0.disabled, "Transhuman Physiology button is disabled when defender has 0 normal saves")
	cs_sess["def_dice"] = [{"id": 0, "val": 4, "rerolled": false, "locked": false}]
	mg._dice_modal._rebuild_ui()
	var th_btn_1: Button = null
	for ch_t1 in mg._dice_modal._action_bar.get_children():
		if not ch_t1.is_queued_for_deletion() and ch_t1 is Button and (ch_t1 as Button).text.contains("超人体格"):
			th_btn_1 = ch_t1 as Button
	if th_btn_1 != null:
		th_btn_1.pressed.emit()
	_assert_true(
		(mg.engine.state["sides"]["A"]["used_ploys_tp"] as Array).has("transhuman_physiology") and bool(cs_sess["transhuman_used"]),
		"Using Transhuman Physiology records 'transhuman_physiology' in used_ploys_tp (1/TP limit, Rulebook p.28)"
	)

	# R9-6: DiceCombatModal exposes Sickening Resilience & Curse of Rot for PM and caps Shock Assault preview at -7W
	mg.engine.state["map"]["terrains"] = []
	var a_cap_ui: Dictionary = _find_by_op_id(mg.engine, "aod_captain")
	var b_ch_ui: Dictionary = _find_by_op_id(mg.engine, "pm_champion")
	a_cap_ui["pos"] = Vector2(12.0, 12.0)
	b_ch_ui["pos"] = Vector2(14.0, 12.0) # edge dist ~0.58" <= 3"
	a_cap_ui["wounds_cur"] = 15
	b_ch_ui["wounds_cur"] = 15
	a_cap_ui["incapacitated"] = false
	b_ch_ui["incapacitated"] = false
	mg.engine.state["sides"]["B"]["cp"] = 3
	mg.engine.state["sides"]["B"]["used_ploys_tp"].clear()
	var w_ui_r9 := {"id": "w_ui_r9", "name": "测试步枪", "kind": "ranged", "attacks": 3, "hit": 3, "normal_dmg": 3, "crit_dmg": 4, "rules": {}}
	var ev_ui_r9 := {"valid": true, "distance": 0.58, "visible": true, "cover": "none", "obscured": false, "vantage_accurate": 0}
	var sess_pm_ui := KTCombat.create_shooting_session(a_cap_ui, b_ch_ui, w_ui_r9, ev_ui_r9, mg.engine.state, mg.engine.rng, false)
	sess_pm_ui["atk_dice"] = [{"id": 0, "val": 3, "rerolled": false, "locked": false}, {"id": 1, "val": 5, "rerolled": false, "locked": false}]
	mg._dice_modal.open_shooting(mg.engine, sess_pm_ui)
	var has_cor_btn := false
	for ch_cor in mg._dice_modal._action_bar.get_children():
		if not ch_cor.is_queued_for_deletion() and ch_cor is Button and (ch_cor as Button).text.contains("腐烂诅咒"):
			has_cor_btn = true
	KTCombat.lock_in_shooting_attacks(sess_pm_ui, mg.engine.state, mg.engine.rng)
	mg._dice_modal._rebuild_ui()
	var has_dr_btn := false
	for ch_dr in mg._dice_modal._action_bar.get_children():
		if not ch_dr.is_queued_for_deletion() and ch_dr is Button and (ch_dr as Button).text.contains("恶心韧性"):
			has_dr_btn = true
	# Melee Shock Assault on Captain Power Fist (5/7) must show -7W cap (not -8W!)
	a_cap_ui["shock_assault_active"] = true
	var fist_w: Dictionary = {}
	for w_cf in a_cap_ui["weapons"]:
		if str((w_cf as Dictionary)["name"]).contains("动力拳"):
			fist_w = w_cf
			break
	var ms_ui_cap := KTCombat.create_melee_session(a_cap_ui, b_ch_ui, fist_w, KTAI.pick_best_melee_weapon(b_ch_ui), mg.engine.state, mg.engine.rng)
	(ms_ui_cap["att"] as Dictionary)["crits"] = 1
	(ms_ui_cap["att"] as Dictionary)["normals"] = 1
	ms_ui_cap["turn"] = "attacker"
	ms_ui_cap["step"] = "duel"
	mg._dice_modal.open_melee(mg.engine, ms_ui_cap)
	var crit_lbl_ok := false
	for ch_m9 in mg._dice_modal._action_bar.get_children():
		if not ch_m9.is_queued_for_deletion() and ch_m9 is Button and (ch_m9 as Button).text.contains("暴击斩击"):
			crit_lbl_ok = (ch_m9 as Button).text.contains("-7W") and not (ch_m9 as Button).text.contains("-8W")
	_assert_true(
		has_cor_btn and has_dr_btn and crit_lbl_ok,
		"R9-6: DiceCombatModal shows Curse of Rot & Sickening Resilience for PM and caps Shock Assault Power Fist label at -7W"
	)

	# A4: CardLightbox removes old tab buttons immediately before _select_item(0) + FOCUS_NONE
	mg._card_lightbox.show_card_items("测试1", [
		{"title": "卡A", "card": "", "body": "内容A"},
		{"title": "卡B", "card": "", "body": "内容B"},
		{"title": "卡C", "card": "", "body": "内容C"},
	])
	mg._card_lightbox.show_card_items("测试2", [
		{"title": "新卡1", "card": "", "body": "新内容1"},
		{"title": "新卡2", "card": "", "body": "新内容2"},
	])
	var lb_tabs_cnt: int = mg._card_lightbox._tabs_box.get_child_count()
	var lb_tab0 := mg._card_lightbox._tabs_box.get_child(0) as Button if lb_tabs_cnt > 0 else null
	_assert_true(
		lb_tabs_cnt == 2 and lb_tab0 != null and lb_tab0.text == "新卡1" and lb_tab0.focus_mode == Control.FOCUS_NONE,
		"A4: CardLightbox removes stale tabs before selecting index 0 and sets FOCUS_NONE"
	)
	mg._card_lightbox.hide()

	# A5: KTRulePopover has FOCUS_NONE on close button and consumes ESC cleanly
	var pop_a5 := KTRuleChip.ensure_popover(root)
	pop_a5.show_for(mg, KTGlossary.lookup("穿刺 1"), true)
	var pop_focus_ok: bool = pop_a5._close_btn.focus_mode == Control.FOCUS_NONE
	var esc_ev_a5 := InputEventKey.new()
	esc_ev_a5.pressed = true
	esc_ev_a5.keycode = KEY_ESCAPE
	pop_a5._unhandled_input(esc_ev_a5)
	_assert_true(
		pop_focus_ok and not pop_a5.visible and not pop_a5.pinned,
		"A5: KTRulePopover uses FOCUS_NONE on close button and dismisses pinned popover on ESC"
	)

	# A9: DiceCombatModal ignores 3D clicks on Curse-of-Rot locked dice, and KillzoneView syncs emptied operatives on Undo
	sess_pm_ui["step"] = "attack_reroll"
	sess_pm_ui["atk_dice"] = [
		{"id": 0, "val": 3, "rerolled": false, "locked": true},
		{"id": 1, "val": 4, "rerolled": false, "locked": false},
	]
	mg._dice_modal.visible = true
	mg._dice_modal._modal_mode = "shooting"
	mg._dice_modal.session = sess_pm_ui
	mg._dice_modal._selected_pool = ""
	mg._dice_modal._selected_die_idx = -1
	mg._dice_modal.on_tabletop_die_clicked("atk_dice", 0)
	var locked_click_ignored: bool = mg._dice_modal._selected_die_idx == -1
	mg._dice_modal.on_tabletop_die_clicked("atk_dice", 1)
	var unlocked_click_accepted: bool = mg._dice_modal._selected_die_idx == 1
	mg._dice_modal.visible = false
	var ops_backup_a9: Array = mg.engine.state["operatives"]
	mg.engine.state["operatives"] = []
	mg._killzone.sync_visual_positions(true)
	var op_nodes_cleared_a9: bool = mg._killzone._op_nodes.is_empty()
	mg.engine.state["operatives"] = ops_backup_a9
	_assert_true(
		locked_click_ignored and unlocked_click_accepted and op_nodes_cleared_a9,
		"A9: Locked dice ignore 3D click selection while unlocked dice select, and KillzoneView clears stale 3D miniatures when Undo empties operatives"
	)

	KTSettings.set_value("combat/rule_step_pause", saved_pause_test)

	root.remove_child(mg)
	mg.free()


func _test_playtest_audit_fixes() -> void:
	print("--- Playtest Core Rule Audit Regression Fixes ---")
	var eng := KTEngine.new(20261123)
	eng.start_match("hotseat", "A", "sharpshooter", "dueller")
	eng.auto_deploy_all()
	eng.auto_initiative()
	eng.choose_primary_op("A", "key")
	eng.choose_primary_op("B", "key")
	eng.pass_strategy("A")
	eng.pass_strategy("B")
	eng.state["map"]["terrains"] = []
	for idx in range(eng.state["operatives"].size()):
		var o: Dictionary = eng.state["operatives"][idx]
		o["pos"] = Vector2(2.0 + 1.5 * float(idx % 6), 20.0 if str(o["side"]) == "A" else 2.0)

	var rng_t := RandomNumberGenerator.new()
	rng_t.seed = 4242

	# 1. Lethal X+ requires hit success first (hit 6+ with Lethal 5+: roll 5 must FAIL, roll 6 must CRIT)
	var a_cap: Dictionary = _find_by_op_id(eng, "aod_captain")
	var b_war: Dictionary = _find_by_op_id(eng, "pm_heavy_gunner")
	a_cap["pos"] = Vector2(10.0, 12.0)
	b_war["pos"] = Vector2(16.0, 12.0)
	var w_l5_h6: Dictionary = {"id": "test_l5_h6", "name": "测试枪", "kind": "ranged", "attacks": 2, "hit": 6, "normal_dmg": 3, "crit_dmg": 5, "rules": {"lethal": 5}}
	var ev_open: Dictionary = {"valid": true, "distance": 6.0, "visible": true, "cover": "none", "obscured": false, "vantage_accurate": 0}
	var s_leth: Dictionary = KTCombat.create_shooting_session(a_cap, b_war, w_l5_h6, ev_open, eng.state, rng_t, false)
	s_leth["atk_dice"] = [
		{"id": 0, "val": 5, "rerolled": false, "locked": false},
		{"id": 1, "val": 6, "rerolled": false, "locked": false},
	]
	KTCombat.lock_in_shooting_attacks(s_leth, eng.state, rng_t)
	_assert_true(
		int(s_leth["retained_atk"]["crits"]) == 1 and int(s_leth["retained_atk"]["normals"]) == 0,
		"Lethal 5+ with effective hit 6+ fails on roll of 5 and crits on 6 (Rulebook p.93 / v11.5 §2.1)"
	)

	# 2. Optimal shooting defence allocation: 3 normal saves vs 1 crit (4 dmg) + 2 normals (3 dmg) -> takes 3 dmg, not 4!
	var s_def: Dictionary = KTCombat.create_shooting_session(a_cap, b_war, {"id": "t_34", "name": "步枪", "kind": "ranged", "attacks": 3, "hit": 3, "normal_dmg": 3, "crit_dmg": 4, "rules": {}}, ev_open, eng.state, rng_t, false)
	s_def["step"] = "defence_roll"
	s_def["retained_atk"] = {"crits": 1, "normals": 2, "devastating_mw": 0}
	s_def["auto_def_normals"] = 0
	s_def["auto_def_crits"] = 0
	s_def["def_dice"] = [
		{"id": 0, "val": 4, "rerolled": false, "locked": false},
		{"id": 1, "val": 4, "rerolled": false, "locked": false},
		{"id": 2, "val": 4, "rerolled": false, "locked": false},
	]
	b_war["team_id"] = "TEST_NO_FNP"
	b_war["wounds_cur"] = 14
	KTCombat.finalize_shooting_resolution(s_def, eng.state, rng_t)
	b_war["team_id"] = "CHAOS-PM"
	_assert_true(
		int(s_def["total_damage_dealt"]) == 3,
		"Optimal defence allocation spends 2 normals on crit + 1 normal on normal (3 dmg instead of 4)"
	)

	# 3. Blast + Saturate preserves primary raw_cover for secondary target with Camo Cloak (1.2 FAQ)
	for o_rst in eng.state["operatives"]:
		(o_rst as Dictionary)["pos"] = Vector2(2.0, 20.0 if str((o_rst as Dictionary)["side"]) == "A" else 2.0)
	eng.state["map"]["terrains"] = [
		{"id": "low_c", "name": "轻型掩体", "kind": "light", "elevation": 1.0, "rect": Rect2(14.0, 11.2, 0.4, 2.0)}
	]
	var a_sniper: Dictionary = _find_by_op_id(eng, "aod_sniper")
	var a_gunner: Dictionary = _find_by_op_id(eng, "aod_gunner")
	var b_bomb: Dictionary = _find_by_op_id(eng, "pm_bombardier")
	b_bomb["pos"] = Vector2(11.0, 12.0)
	a_gunner["pos"] = Vector2(15.2, 12.0) # behind light cover, NO camo cloak
	a_gunner["order"] = "engage"
	a_sniper["pos"] = Vector2(16.8, 12.6) # within 2" of gunner, HAS camo cloak!
	a_sniper["order"] = "engage"
	var w_blast_sat: Dictionary = {"id": "t_bs", "name": "破片手雷", "kind": "ranged", "attacks": 4, "hit": 4, "normal_dmg": 2, "crit_dmg": 4, "rules": {"range": 6.0, "blast": 2.0, "saturate": true}}
	var ev_bs: Dictionary = KTGeometry.evaluate_shot(b_bomb, a_gunner, w_blast_sat, eng.state)
	var secs_bs: Array[Dictionary] = KTGeometry.get_blast_secondary_targets(b_bomb, a_gunner, w_blast_sat, ev_bs, eng.state)
	var sniper_sec_cover := ""
	for sc in secs_bs:
		if str(sc["defender_uid"]) == str(a_sniper["uid"]):
			sniper_sec_cover = str((sc["shot_eval"] as Dictionary).get("cover", ""))
	_assert_true(
		bool(ev_bs["valid"]) and str(ev_bs["cover"]) == "none" and str(ev_bs.get("raw_cover", "")) == "standard" and sniper_sec_cover == "standard",
		"Blast + Saturate preserves raw_cover so secondary target with Camo Cloak retains inherited cover (1.2 FAQ)"
	)

	# 4. Vantage + Seek Light against Concealed target in Light cover yields standard cover, NOT improved cover (p.36 v1.4 FAQ)
	for o_rst2 in eng.state["operatives"]:
		(o_rst2 as Dictionary)["pos"] = Vector2(2.0, 20.0 if str((o_rst2 as Dictionary)["side"]) == "A" else 2.0)
	eng.state["map"]["terrains"] = [
		{"id": "v_high", "name": "高台", "kind": "vantage", "elevation": 3.5, "rect": Rect2(8.0, 10.0, 3.0, 3.0)},
		{"id": "l_wall", "name": "轻墙", "kind": "light", "elevation": 1.0, "rect": Rect2(14.0, 11.0, 0.4, 2.0)},
	]
	a_sniper["pos"] = Vector2(9.5, 11.5)
	b_war["pos"] = Vector2(15.2, 11.5)
	b_war["order"] = "conceal"
	var w_seek_l: Dictionary = {"id": "t_sl", "name": "狙击枪", "kind": "ranged", "attacks": 4, "hit": 2, "normal_dmg": 3, "crit_dmg": 4, "rules": {"seek_light": true}}
	var ev_vsl: Dictionary = KTGeometry.evaluate_shot(a_sniper, b_war, w_seek_l, eng.state)
	_assert_true(
		bool(ev_vsl["valid"]) and str(ev_vsl["cover"]) == "standard",
		"Vantage + Seek Light uses Seek Light against concealed target in light cover without granting Improved Cover (p.36 v1.4 FAQ)"
	)

	# 5. Smoke grenade on 2F Vantage does NOT obscure operative on ground floor below it (p.103)
	eng.state["eq_markers"] = [{"kind": "smoke", "side": "A", "pos": Vector2(9.5, 11.5), "tp_placed": 1, "acts_left": -1}]
	var smoke_on_vantage := KTGeometry.in_smoke(Vector2(9.5, 11.5), 0.63, eng.state)
	var smoke_on_ground_near := KTGeometry.in_smoke(Vector2(11.3, 11.5), 0.63, eng.state) # ground floor within horizontal smoke range of 2F marker
	_assert_true(
		smoke_on_vantage and not smoke_on_ground_near,
		"Smoke marker on Vantage does not obscure operatives on lower elevation below the marker (Rulebook p.103)"
	)
	eng.state["eq_markers"] = []

	# 6. Melee Stun triggers on Crit retention (Roll Attack Dice step) before any strike executes (p.94)
	eng.state["map"]["terrains"] = []
	eng.state["sides"]["A"]["shields_used_tp"] = true # disable Tilting Shields for deterministic lethal 2+
	var b_psy: Dictionary = _find_by_op_id(eng, "pm_plaguecaster")
	b_psy["pos"] = Vector2(12.0, 12.0)
	a_gunner["pos"] = Vector2(13.2, 12.0)
	a_gunner["apl_mod"] = 0
	var w_stun_m: Dictionary = {"id": "t_stun_m", "name": "腐化法杖", "kind": "melee", "attacks": 6, "hit": 2, "normal_dmg": 3, "crit_dmg": 5, "rules": {"stun": true, "lethal": 2}}
	var w_plain_m: Dictionary = {"id": "t_plain_m", "name": "双拳", "kind": "melee", "attacks": 4, "hit": 3, "normal_dmg": 3, "crit_dmg": 4, "rules": {}}
	var ms_stun: Dictionary = KTCombat.create_melee_session(b_psy, a_gunner, w_stun_m, w_plain_m, eng.state, rng_t)
	_assert_true(
		int(ms_stun["att"]["crits"]) > 0 and int(a_gunner["apl_mod"]) == -1,
		"Melee Stun applies APL -1 immediately upon retaining a Critical success (Rulebook p.94)"
	)

	# 7. pm_miasma with no visible/valid target fails cleanly without burning AP or polluting actions_taken;
	#    and CAN target a valid shooting target beyond 7" (CHAOS-PM card);
	#    pm_flail cannot be used twice in the same activation; Champion heals via Grandfather's Blessing on poison tick!
	eng.state["map"]["terrains"] = [
		{"id": "miasma_block", "name": "高墙", "kind": "heavy_wall", "elevation": 4.2, "blocks_los_ground": true, "rect": Rect2(8.0, 0.5, 1.0, 21.0)}
	]
	b_psy["pos"] = Vector2(2.0, 2.0)
	b_psy["ready"] = true
	for oa in _side_ops(eng, "A"):
		(oa as Dictionary)["pos"] = Vector2(25.0, 20.0)
	eng.state["turn_side"] = "B"
	eng.state["active_op_uid"] = ""
	eng.activate_operative(str(b_psy["uid"]), "engage")
	var ap_before_m := int(b_psy["ap_left"])
	var undo_before_m := eng.undo_stack.size()
	var res_m_fail: Dictionary = eng.execute_unique_action(str(b_psy["uid"]), "pm_miasma")
	_assert_true(
		not bool(res_m_fail["ok"]) and int(b_psy["ap_left"]) == ap_before_m and not (b_psy["actions_taken"] as Array).has("pm_miasma") and eng.undo_stack.size() == undo_before_m,
		"Failed pm_miasma (no visible target in 7\" or valid target) does not spend AP, lock actions_taken, or push junk undo snapshot"
	)
	eng.state["map"]["terrains"] = []
	a_gunner["pos"] = Vector2(14.0, 2.0) # 12" away, Engage in open -> valid shooting target!
	a_gunner["order"] = "engage"
	a_gunner["poisoned"] = false
	var res_m_far: Dictionary = eng.execute_unique_action(str(b_psy["uid"]), "pm_miasma")
	_assert_true(
		bool(res_m_far["ok"]) and bool(a_gunner["poisoned"]),
		"pm_miasma can target a valid shooting target beyond 7\" (CHAOS-PM card)"
	)
	eng.end_operative_activation()

	var b_flail: Dictionary = _find_by_op_id(eng, "pm_fighter")
	b_flail["pos"] = Vector2(15.0, 12.0)
	b_flail["ready"] = true
	eng.state["turn_side"] = "B"
	eng.state["active_op_uid"] = ""
	eng.activate_operative(str(b_flail["uid"]), "engage")
	var fl1: Dictionary = eng.execute_unique_action(str(b_flail["uid"]), "pm_flail")
	var fl2: Dictionary = eng.execute_unique_action(str(b_flail["uid"]), "pm_flail")
	_assert_true(
		bool(fl1["ok"]) and not bool(fl2["ok"]),
		"pm_flail cannot be executed twice in the same activation (Action Restriction p.11)"
	)
	eng.end_operative_activation()

	var b_champ: Dictionary = _find_by_op_id(eng, "pm_champion")
	b_champ["pos"] = Vector2(15.0, 12.0)
	b_champ["wounds_cur"] = 10
	b_champ["blessing_healed_tp"] = 0
	a_gunner["pos"] = Vector2(20.0, 12.0) # >3" (outside Poison Vents), <=7" (inside Grandfather's Blessing!)
	a_gunner["poisoned"] = true
	a_gunner["ready"] = true
	a_gunner["wounds_cur"] = 10
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.activate_operative(str(a_gunner["uid"]), "engage")
	_assert_true(
		int(a_gunner["wounds_cur"]) == 9 and int(b_champ["wounds_cur"]) == 11,
		"Plague Marine Champion heals via Grandfather's Blessing when a poisoned enemy within 7\" loses wounds on activation poison tick"
	)
	eng.end_operative_activation()

	# --- Round 2 Deep Rule Audit Regression Tests ---
	print("--- Round 2 Deep Rule Audit Regression Fixes ---")
	# R2-1: Grenadier & Bombardier datacard grenades keep 3+ hit (no double-dipped 2+!)
	var a_gren: Dictionary = _find_by_op_id(eng, "aod_grenadier")
	var gren_frag := {}
	for gw in a_gren["weapons"]:
		if str(gw["id"]) == "aod_grenadier_frag":
			gren_frag = gw
			break
	var bomb_blight := {}
	for bw in b_bomb["weapons"]:
		if str(bw["id"]) == "pm_bombardier_blight_grenade":
			bomb_blight = bw
			break
	a_gren["wounds_cur"] = int(a_gren["wounds_max"])
	b_bomb["wounds_cur"] = int(b_bomb["wounds_max"])
	var s_gf: Dictionary = KTCombat.create_shooting_session(a_gren, b_war, gren_frag, ev_open, eng.state, rng_t, false)
	var s_bb: Dictionary = KTCombat.create_shooting_session(b_bomb, a_gunner, bomb_blight, ev_open, eng.state, rng_t, false)
	_assert_true(
		int(s_gf["effective_hit"]) == 3 and int(s_bb["effective_hit"]) == 3,
		"R2-1: Grenadier & Bombardier datacard grenades hit on 3+, not double-dipped to 2+"
	)

	# R2-2: Dash cannot climb over Light terrain or Razor Wire (p.14)
	for op_r2 in eng.state["operatives"]:
		(op_r2 as Dictionary)["pos"] = Vector2(2.0, 20.0 if str((op_r2 as Dictionary)["side"]) == "A" else 2.0)
	eng.state["map"]["terrains"] = [
		{"id": "l_dash", "name": "轻型路障", "kind": "light", "elevation": 1.0, "rect": Rect2(10.0, 10.0, 0.4, 3.0)},
		{"id": "r_dash", "name": "铁丝网", "kind": "razor_wire", "elevation": 1.0, "rect": Rect2(14.0, 10.0, 0.4, 3.0)},
	]
	a_cap["pos"] = Vector2(9.0, 11.5)
	var d_light := KTGeometry.validate_move_path([Vector2(9.0, 11.5), Vector2(11.4, 11.5)], a_cap, "dash", 3.0, eng.state)
	var d_razor := KTGeometry.validate_move_path([Vector2(13.0, 11.5), Vector2(15.4, 11.5)], a_cap, "dash", 3.0, eng.state)
	_assert_true(
		not bool(d_light["ok"]) and not bool(d_razor["ok"]),
		"R2-2: Dash cannot climb over Light terrain or Razor Wire (p.14)"
	)

	# R2-3: Multiple drops in one action only ignore 2" TOTAL (p.31) + Drop & Light obstacle both charged
	eng.state["map"]["terrains"] = [
		{"id": "v_top", "name": "上层台", "kind": "vantage", "elevation": 4.0, "rect": Rect2(6.0, 10.0, 3.0, 3.0)},
		{"id": "v_mid", "name": "中层台", "kind": "vantage", "elevation": 2.0, "rect": Rect2(9.0, 10.0, 3.0, 3.0)},
		{"id": "l_after", "name": "地面轻障", "kind": "light", "elevation": 1.0, "rect": Rect2(12.8, 10.0, 0.4, 3.0)},
	]
	# Step 1: 4.0 -> 2.0 (drop 2.0", free! drop_free_remaining -> 0); Step 2: 2.0 -> 0.0 (drop 2.0", costs +2") + crosses light (+2")!
	# Horiz: ceil(2.0)=2.0" + ceil(4.0)=4.0" = 6.0"; Drop cost: 0 + 2 = 2"; Light cost: 2"; Total = 10.0"
	var multi_drop := KTGeometry.validate_move_path(
		[Vector2(8.0, 11.5), Vector2(10.0, 11.5), Vector2(14.0, 11.5)],
		a_cap, "reposition", 10.0, eng.state
	)
	_assert_true(
		bool(multi_drop["ok"]) and is_equal_approx(float(multi_drop["cost"]), 10.0),
		"R2-3: Multiple drops ignore only 2\" total per action (p.31) and drop does not swallow Light climb cost"
	)

	# R2-4: Reposition may pass through friendly-engaged enemy CR (cannot end there); Charge cannot leave unengaged enemy CR
	eng.state["map"]["terrains"] = []
	for op_r4 in eng.state["operatives"]:
		(op_r4 as Dictionary)["pos"] = Vector2(2.0, 20.0 if str((op_r4 as Dictionary)["side"]) == "A" else 2.0)
	b_war["pos"] = Vector2(12.0, 11.5) # Enemy in middle
	a_cap["pos"] = Vector2(9.5, 11.5)  # Moving operative
	# Without friendly engaged: passing through b_war's CR is blocked
	var pass_unengaged := KTGeometry.validate_move_path([Vector2(9.5, 11.5), Vector2(14.5, 11.5)], a_cap, "reposition", 6.0, eng.state)
	# With friendly a_gunner already engaged with b_war: passing THROUGH is legal, ENDING in CR is still illegal!
	a_gunner["pos"] = Vector2(12.0, 13.0) # ~0.24" edge distance -> in b_war's CR!
	var pass_engaged := KTGeometry.validate_move_path([Vector2(9.5, 11.5), Vector2(14.5, 11.5)], a_cap, "reposition", 6.0, eng.state)
	var end_in_engaged := KTGeometry.validate_move_path([Vector2(9.5, 11.5), Vector2(10.7, 11.5)], a_cap, "reposition", 6.0, eng.state)
	_assert_true(
		not bool(pass_unengaged["ok"]) and bool(pass_engaged["ok"]) and not bool(end_in_engaged["ok"]),
		"R2-4a: Reposition may pass through enemy CR only if another friendly is engaged, and cannot end in enemy CR (p.13)"
	)
	# Charge through unengaged b_war to reach b_champ behind it must fail!
	a_gunner["pos"] = Vector2(2.0, 20.0) # remove friendly engagement
	b_champ["pos"] = Vector2(16.0, 11.5)
	var charge_past := KTGeometry.validate_move_path([Vector2(9.5, 11.5), Vector2(14.6, 11.5)], a_cap, "charge", 8.0, eng.state)
	_assert_true(
		not bool(charge_past["ok"]),
		"R2-4b: Charge cannot leave an unengaged enemy's control range after entering it (p.14)"
	)

	# R2-5: Stealthy works vs Vantage Engage + offers 2norm/1crit toggle; Sniper Camo Cloak + team Stealthy = 1 crit + 1 norm!
	eng.state["sides"]["A"]["primary_tactic"] = "stealthy"
	var ev_vant_std: Dictionary = {"valid": true, "distance": 8.0, "visible": true, "cover": "standard", "obscured": false, "vantage_accurate": 1}
	var s_st_gun: Dictionary = KTCombat.create_shooting_session(b_war, a_gunner, w_plain_m, ev_vant_std, eng.state, rng_t, false)
	KTCombat.lock_in_shooting_attacks(s_st_gun, eng.state, rng_t)
	var st_2n_ok: bool = str(s_st_gun["cover_mode"]) == "stealth_choice" and int(s_st_gun["auto_def_normals"]) == 2 and int(s_st_gun["auto_def_crits"]) == 0
	KTCombat.set_improved_cover_choice(s_st_gun, "1crit", rng_t)
	var st_1c_ok: bool = int(s_st_gun["auto_def_normals"]) == 0 and int(s_st_gun["auto_def_crits"]) == 1
	var s_camo_dbl: Dictionary = KTCombat.create_shooting_session(b_war, a_sniper, w_plain_m, ev_vant_std, eng.state, rng_t, false)
	KTCombat.lock_in_shooting_attacks(s_camo_dbl, eng.state, rng_t)
	var camo_dbl_ok: bool = str(s_camo_dbl["cover_mode"]) == "camo_double" and int(s_camo_dbl["auto_def_crits"]) == 1 and int(s_camo_dbl["auto_def_normals"]) == 1 and (s_camo_dbl["def_dice"] as Array).size() == 1
	_assert_true(
		st_2n_ok and st_1c_ok and camo_dbl_ok,
		"R2-5: Stealthy applies vs Vantage Engage with 2norm/1crit toggle, and Sniper Camo Cloak + team Stealthy grants 1 crit + 1 normal save"
	)

	# R2-6: Sergeant & Assault Sergeant Doctrine Warfare (0CP once per battle each)
	var eng_sgt := KTEngine.new(2029)
	eng_sgt.start_match("hotseat", "A", "sharpshooter", "dueller")
	eng_sgt.choose_drop_zone(false)
	while str(eng_sgt.state["phase"]) == "SELECT":
		var sd_s := eng_sgt.setup_side()
		if sd_s == "A":
			eng_sgt.confirm_roster("A", ["aod_assault_sergeant", "aod_grenadier", "aod_sniper", "aod_gunner", "aod_heavy_gunner", "aod_assault_warrior"])
		else:
			eng_sgt.confirm_roster("B", eng_sgt.default_roster("B"))
	eng_sgt.auto_deploy_all()
	eng_sgt.auto_initiative()
	eng_sgt.state["turn_side"] = "A"
	eng_sgt.state["sides"]["A"]["cp"] = 2
	var opts_asgt := eng_sgt.get_strategic_ploy_options("A")
	var ass_cost := -1
	var dev_cost := -1
	var tac_cost := -1
	for o_s in opts_asgt:
		if str(o_s["id"]) == "doctrine_assault":
			ass_cost = int(o_s["cost"])
		elif str(o_s["id"]) == "doctrine_devastator":
			dev_cost = int(o_s["cost"])
		elif str(o_s["id"]) == "doctrine_tactical":
			tac_cost = int(o_s["cost"])
	eng_sgt.use_strategic_ploy("A", "doctrine_assault")
	var cp_after_free := int(eng_sgt.state["sides"]["A"]["cp"])
	# Next TP: doctrine_assault should now cost 1CP while doctrine_tactical is still 0CP
	eng_sgt.state["sides"]["A"]["used_ploys_tp"].clear()
	var opts_asgt2 := eng_sgt.get_strategic_ploy_options("A")
	var ass_cost2 := -1
	var tac_cost2 := -1
	for o_s2 in opts_asgt2:
		if str(o_s2["id"]) == "doctrine_assault":
			ass_cost2 = int(o_s2["cost"])
		elif str(o_s2["id"]) == "doctrine_tactical":
			tac_cost2 = int(o_s2["cost"])
	_assert_true(
		ass_cost == 0 and tac_cost == 0 and dev_cost == 1 and cp_after_free == 2 and ass_cost2 == 1 and tac_cost2 == 0,
		"R2-6: Assault Sergeant Doctrine Warfare makes Assault & Tactical 0CP once per battle each (Devastator stays 1CP)"
	)

	# R2-7: Objective visibility check (blocked through solid heavy wall) & Cloud of Flies wholly-within-1"
	eng.state["map"]["terrains"] = [
		{"id": "obj_wall", "name": "重墙", "kind": "heavy_wall", "elevation": 4.2, "blocks_los_ground": true, "rect": Rect2(14.2, 8.0, 0.5, 6.0)}
	]
	var test_obj: Dictionary = (eng.state["map"]["objectives"] as Array)[0]
	test_obj["pos"] = Vector2(15.2, 11.0)
	for op_r7 in eng.state["operatives"]:
		(op_r7 as Dictionary)["pos"] = Vector2(2.0, 20.0 if str((op_r7 as Dictionary)["side"]) == "A" else 2.0)
	a_cap["pos"] = Vector2(13.5, 11.0) # 1.7" from center (<1.787), but behind 4.2" heavy wall!
	eng.recalculate_objectives_and_kills()
	var ctrl_walled: String = str(eng.state["objectives_state"][str(test_obj["id"])]["controlled_by"])
	a_cap["pos"] = Vector2(15.5, 11.0) # visible & within control range
	eng.recalculate_objectives_and_kills()
	var ctrl_open: String = str(eng.state["objectives_state"][str(test_obj["id"])]["controlled_by"])
	_assert_true(
		ctrl_walled == "" and ctrl_open == "A",
		"R2-7a: Objective control requires visibility (cannot contest through a solid Heavy wall, p.19)"
	)

	eng.state["map"]["terrains"] = []
	eng.state["sides"]["A"]["auspex_used_tp"] = true # isolate Cloud of Flies from Auspex 8" anti-obscured
	eng.state["markers"] = [{"kind": "cloud_of_flies", "side": "B", "pos": Vector2(15.0, 10.0), "name": "飞蝇云"}]
	a_cap["pos"] = Vector2(5.0, 10.0) # >3" away (and >8" outside Auspex)
	b_war["pos"] = Vector2(15.0, 10.0) # centered on marker -> wholly within 1" (0 + 0.63 <= 1.4)
	b_war["order"] = "engage"
	var ev_cof_in := KTGeometry.evaluate_shot(a_cap, b_war, w_seek_l, eng.state)
	b_war["pos"] = Vector2(16.1, 10.0) # 1.1" center dist -> far edge 1.73" > 1.4" (NOT wholly within 1"!)
	var ev_cof_out := KTGeometry.evaluate_shot(a_cap, b_war, w_seek_l, eng.state)
	eng.state["markers"] = []
	_assert_true(
		bool(ev_cof_in["obscured"]) and not bool(ev_cof_out["obscured"]),
		"R2-7b: Cloud of Flies only obscures operatives WHOLLY within 1\" of the marker (CHAOS-PM)"
	)

	# R2-8: Icon Bearer +1 APL on marker control, TP1 tac_pickup, Dynamic siege_specialist, Counteract eq_resupply, drop zone spawn swap
	var b_icon: Dictionary = _find_by_op_id(eng, "pm_icon_bearer")
	a_cap["pos"] = Vector2(10.0, 10.0)
	b_icon["pos"] = Vector2(11.3, 10.0) # Both within 1" of marker at (10.65, 10.0); A=3 APL vs Icon=3+1=4 APL!
	var m_apl_chk := eng._marker_apl(Vector2(10.65, 10.0))
	_assert_true(
		int(m_apl_chk["A"]) == 3 and int(m_apl_chk["B"]) == 4,
		"R2-8a: Plague Marine Icon Bearer gets +1 APL on all marker control checks (CHAOS-PM)"
	)
	# Dynamic siege_specialist via temp_tactic strips cover
	b_icon["pos"] = Vector2(22.0, 2.0) # move b_icon away so a_cap is not engaged
	eng.state["sides"]["A"]["primary_tactic"] = "dueller"
	eng.state["sides"]["A"]["secondary_tactic"] = "hardy"
	eng.state["sides"]["A"]["temp_tactic"] = "siege_specialist"
	eng.state["map"]["terrains"] = [{"id": "l_siege", "name": "轻墙", "kind": "light", "elevation": 1.0, "rect": Rect2(13.0, 9.0, 0.4, 2.0)}]
	a_cap["pos"] = Vector2(9.0, 10.0)
	b_war["pos"] = Vector2(14.1, 10.0)
	b_war["order"] = "engage"
	var ev_dyn_siege := KTGeometry.evaluate_shot(a_cap, b_war, w_seek_l, eng.state)
	eng.state["sides"]["A"]["temp_tactic"] = ""
	_assert_true(
		bool(ev_dyn_siege["valid"]) and str(ev_dyn_siege["cover"]) == "none",
		"R2-8b: Dynamic siege_specialist (temp_tactic) grants Saturate and strips cover"
	)
	# Counteract eq_resupply consumes the 1 Counteract action
	eng.state["map"]["terrains"] = []
	eng.state["eq_markers"] = [{"kind": "ammo_cache", "side": "A", "pos": Vector2(10.0, 15.0), "used_tp": 0}]
	a_cap["pos"] = Vector2(10.0, 15.0)
	b_war["pos"] = Vector2(20.0, 5.0)
	b_icon["pos"] = Vector2(21.0, 5.0)
	eng.state["active_op_uid"] = str(a_cap["uid"])
	eng.state["active_is_counteract"] = true
	a_cap["ap_left"] = 1
	a_cap["actions_taken"] = []
	var res_ca_ammo := eng.execute_unique_action(str(a_cap["uid"]), "eq_resupply")
	_assert_true(
		bool(res_ca_ammo["ok"]) and str(eng.state["active_op_uid"]) == "" and int(a_cap["ap_left"]) == 0,
		"R2-8c: 0AP eq_resupply during Counteract consumes the single Counteract action and auto-ends Counteract (p.11)"
	)
	# choose_drop_zone(true) swaps spawn_a & spawn_b along with drop_zone_a & drop_zone_b
	var eng_dz := KTEngine.new(2030)
	eng_dz.start_match("hotseat", "A", "dueller", "hardy")
	var orig_sp_a0: Vector2 = (eng_dz.state["map"]["spawn_a"] as Array)[0]
	var orig_sp_b0: Vector2 = (eng_dz.state["map"]["spawn_b"] as Array)[0]
	eng_dz.choose_drop_zone(true)
	_assert_true(
		(eng_dz.state["map"]["spawn_a"] as Array)[0] == orig_sp_b0 and (eng_dz.state["map"]["spawn_b"] as Array)[0] == orig_sp_a0,
		"R2-8d: choose_drop_zone(true) swaps spawn_a and spawn_b along with drop zones"
	)

	# --- Round 3 Deep Rule Audit Regression Tests ---
	print("--- Round 3 Deep Rule Audit Regression Fixes ---")
	# R3-1: get_available_actions["shoot"] disables when no usable ranged weapon remains (Captain after 1 Plasma shot)
	eng.state["map"]["terrains"] = []
	for op_r3 in eng.state["operatives"]:
		(op_r3 as Dictionary)["pos"] = Vector2(2.0, 20.0 if str((op_r3 as Dictionary)["side"]) == "A" else 2.0)
	a_cap["pos"] = Vector2(10.0, 12.0)
	a_cap["ready"] = true
	a_cap["incapacitated"] = false
	a_cap["wounds_cur"] = int(a_cap["wounds_max"])
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.activate_operative(str(a_cap["uid"]), "engage")
	var cap_plasma: Dictionary = (a_cap["weapons"] as Array)[0]
	eng.commit_shooting_action_cost(a_cap, cap_plasma)
	var cap_acts_after: Dictionary = eng.get_available_actions(a_cap)
	_assert_true(
		int(a_cap["ap_left"]) >= 1 and not bool((cap_acts_after["shoot"] as Dictionary)["enabled"]),
		"R3-1: Shoot action disables after non-Bolt shot when operative has no Bolt/Psychic weapon left (Astartes)"
	)
	eng.end_operative_activation()

	# R3-2: Plodding Death checks per-activation move distance (<=3"), not cumulative TP distance (Counteract still gets Ceaseless!)
	eng.state["sides"]["B"]["active_ploys"] = ["慢步死亡"]
	b_war["moved_distance_tp"] = 5.0
	b_war["moved_distance_activation"] = 0.0
	var s_plod_ca: Dictionary = KTCombat.create_shooting_session(b_war, a_cap, w_seek_l, ev_open, eng.state, rng_t, false)
	b_war["moved_distance_activation"] = 4.0
	var s_plod_moved: Dictionary = KTCombat.create_shooting_session(b_war, a_cap, w_seek_l, ev_open, eng.state, rng_t, false)
	_assert_true(
		bool((s_plod_ca["rules"] as Dictionary).get("ceaseless", false)) and not bool((s_plod_moved["rules"] as Dictionary).get("ceaseless", false)),
		"R3-2: Plodding Death checks per-activation move distance (<=3\"), so Counteract still gains Ceaseless after a 5\" move earlier in TP"
	)

	# R3-3: Wrath of Vengeance rejected outside Counteract; Shock Assault rejected without Charge & lasts only 1 Fight action!
	eng.state["sides"]["A"]["cp"] = 3
	eng.state["sides"]["A"]["used_ploys_tp"].clear()
	a_cap["ready"] = true
	a_cap["charged_this_tp"] = false
	eng.state["turn_side"] = "A"
	eng.state["active_op_uid"] = ""
	eng.state["active_is_counteract"] = false
	eng.activate_operative(str(a_cap["uid"]), "engage")
	var wov_bad := eng.use_firefight_ploy("A", "wrath_of_vengeance", str(a_cap["uid"]))
	var sa_bad := eng.use_firefight_ploy("A", "shock_assault", str(a_cap["uid"]))
	a_cap["charged_this_tp"] = true
	var sa_ok := eng.use_firefight_ploy("A", "shock_assault", str(a_cap["uid"]))
	b_war["pos"] = Vector2(11.2, 12.0)
	var ms_sa1 := KTCombat.create_melee_session(a_cap, b_war, w_plain_m, w_plain_m, eng.state, rng_t)
	var ms_sa2 := KTCombat.create_melee_session(a_cap, b_war, w_plain_m, w_plain_m, eng.state, rng_t)
	_assert_true(
		not bool(wov_bad["ok"]) and not bool(sa_bad["ok"]) and bool(sa_ok["ok"]) and bool((ms_sa1["att"] as Dictionary)["shock_assault"]) and not bool((ms_sa2["att"] as Dictionary)["shock_assault"]),
		"R3-3: Wrath of Vengeance requires Counteract; Shock Assault requires Charge and lasts only ONE Fight action"
	)
	eng.end_operative_activation()

	# R3-4: Melee Punishing converts 1 fail -> normal without chaining into Rending
	eng.state["sides"]["A"]["purity_used_tp"] = true
	eng.state["sides"]["B"]["active_ploys"] = []
	var w_pun_m: Dictionary = {"id": "t_pun_m", "name": "重击锤", "kind": "melee", "attacks": 5, "hit": 5, "normal_dmg": 3, "crit_dmg": 5, "rules": {"punishing": true}}
	var rng_p := RandomNumberGenerator.new()
	rng_p.seed = 123
	var saw_pun_trigger := false
	for _try in range(20):
		var ms_p := KTCombat.create_melee_session(a_cap, b_war, w_pun_m, w_plain_m, eng.state, rng_p)
		var raw_d: Array = (ms_p["att"] as Dictionary)["dice_raw"]
		var raw_c := 0
		var raw_n := 0
		var raw_f := 0
		for dv in raw_d:
			var iv := int(dv)
			if iv == 6:
				raw_c += 1
			elif iv >= 5:
				raw_n += 1
			else:
				raw_f += 1
		if raw_c > 0 and raw_f > 0:
			_assert_true(
				int((ms_p["att"] as Dictionary)["normals"]) == raw_n + 1,
				"R3-4: Melee Punishing converts 1 failure into 1 normal hit when at least 1 crit is rolled"
			)
			saw_pun_trigger = true
			break
	if not saw_pun_trigger:
		_assert_true(false, "R3-4: Melee Punishing deterministic seed did not trigger")

	# --- Round 4-6 Deep Audit Regression Tests (3D Distance, Counteract Cap, Curse of Rot, Sickening Resilience Undo) ---
	print("--- Round 4-6 Deep Audit Regression Fixes ---")
	# R4-1: 3D vertical distance blocks Vantage Close Quarters reveal, Blast 2", Contagion 3", Poison Vents 3"
	eng.state["map"]["terrains"] = [
		{"id": "v35_r4", "name": "高台", "kind": "vantage", "elevation": 3.5, "rect": Rect2(10.0, 10.0, 4.0, 4.0)},
		{"id": "hc_r4", "name": "重型低墙", "kind": "heavy_low", "elevation": 2.0, "blocks_los_ground": false, "rect": Rect2(14.0, 10.0, 0.4, 4.0)},
	]
	for op_r4b in eng.state["operatives"]:
		(op_r4b as Dictionary)["pos"] = Vector2(2.0, 20.0 if str((op_r4b as Dictionary)["side"]) == "A" else 2.0)
		(op_r4b as Dictionary)["incapacitated"] = false
		(op_r4b as Dictionary)["wounds_cur"] = int((op_r4b as Dictionary)["wounds_max"])
	a_sniper["pos"] = Vector2(13.0, 12.0) # on 3.5" Vantage
	b_war["pos"] = Vector2(15.1, 12.0)    # ground behind heavy_low, Concealed
	b_war["order"] = "conceal"
	var w_plain_r4 := {"id": "t_pr4", "name": "步枪", "kind": "ranged", "attacks": 4, "hit": 3, "normal_dmg": 3, "crit_dmg": 4, "rules": {}}
	var ev_cq_r4 := KTGeometry.evaluate_shot(a_sniper, b_war, w_plain_r4, eng.state)
	eng.state["sides"]["B"]["active_ploys"] = ["传染"]
	a_sniper["poisoned"] = true
	b_flail["pos"] = Vector2(14.8, 12.0)
	_assert_true(
		not bool(ev_cq_r4["valid"]) and not KTCombat.is_contagion_affected(a_sniper, eng.state),
		"R4-1: 3D base-to-base distance prevents 3.5\" Vantage overhead from triggering 2\" Close Quarters or 3\" Contagion (p.22)"
	)
	# Blast 2" on ground does not splash 3.5" overhead
	a_cap["pos"] = Vector2(18.0, 12.0)
	b_war["order"] = "engage"
	var w_bl_r4 := {"id": "t_blr4", "name": "手雷", "kind": "ranged", "attacks": 4, "hit": 3, "normal_dmg": 2, "crit_dmg": 4, "rules": {"blast": 2.0}}
	var ev_bl_r4 := KTGeometry.evaluate_shot(a_cap, b_war, w_bl_r4, eng.state)
	var secs_bl_r4 := KTGeometry.get_blast_secondary_targets(a_cap, b_war, w_bl_r4, ev_bl_r4, eng.state)
	var sniper_hit_by_ground_blast := false
	for sc4 in secs_bl_r4:
		if str(sc4["defender_uid"]) == str(a_sniper["uid"]):
			sniper_hit_by_ground_blast = true
	_assert_true(
		not sniper_hit_by_ground_blast,
		"R4-2: Ground Blast 2\" respects 3D vertical distance and does not hit operative 3.5\" above on Vantage"
	)

	# R4-3: Counteract 2" TOTAL move cap across Wrath of Vengeance & forbids 2AP Fall Back without Mobile
	eng.state["map"]["terrains"] = []
	eng.state["sides"]["A"]["primary_tactic"] = "sharpshooter"
	eng.state["sides"]["A"]["secondary_tactic"] = "dueller"
	eng.state["sides"]["A"]["cp"] = 3
	eng.state["sides"]["A"]["used_ploys_tp"].clear()
	a_cap["pos"] = Vector2(5.0, 11.0)
	b_war["pos"] = Vector2(6.2, 11.0) # engaged!
	eng.state["active_op_uid"] = str(a_cap["uid"])
	eng.state["active_is_counteract"] = true
	a_cap["ap_left"] = 1
	a_cap["moved_distance_activation"] = 0.0
	a_cap["actions_taken"] = []
	var wov_r4 := eng.use_firefight_ploy("A", "wrath_of_vengeance", str(a_cap["uid"]))
	var acts_ca_fb4 := eng.get_available_actions(a_cap)
	b_war["pos"] = Vector2(24.0, 5.0)
	var ca_mv_pts: Array[Vector2] = [Vector2(5.0, 11.0), Vector2(7.0, 11.0)]
	var ca_mv_res := eng.execute_move(str(a_cap["uid"]), "reposition", ca_mv_pts)
	var acts_ca_after4 := eng.get_available_actions(a_cap)
	_assert_true(
		bool(wov_r4["ok"]) and not bool((acts_ca_fb4["fall_back"] as Dictionary)["enabled"]) and bool(ca_mv_res["ok"]) and not bool((acts_ca_after4["dash"] as Dictionary)["enabled"]),
		"R4-3: Counteract forbids 2AP Fall Back without Mobile and caps total movement at 2\" across Wrath of Vengeance (p.11)"
	)
	eng.end_operative_activation()

	# R4-4: Curse of Rot (腐烂诅咒) locks 3s (immune to Purity Seals / Indomitus / rerolls), inflicts 1 dmg per 3, 1/TP
	a_cap["pos"] = Vector2(12.0, 12.0)
	a_cap["wounds_cur"] = 15
	a_cap["poisoned"] = true
	b_champ["pos"] = Vector2(18.0, 12.0)
	b_champ["wounds_cur"] = 10
	b_champ["blessing_healed_tp"] = 0
	eng.state["sides"]["A"]["purity_used_tp"] = false
	eng.state["sides"]["B"]["cp"] = 2
	eng.state["sides"]["B"]["used_ploys_tp"].clear()
	var s_cor := KTCombat.create_shooting_session(a_cap, b_champ, w_plain_r4, ev_open, eng.state, rng_t, false)
	s_cor["atk_dice"] = [
		{"id": 0, "val": 3, "rerolled": false, "locked": false},
		{"id": 1, "val": 3, "rerolled": false, "locked": false},
		{"id": 2, "val": 5, "rerolled": false, "locked": false},
		{"id": 3, "val": 2, "rerolled": false, "locked": false},
	]
	var cor_r4 := KTCombat.apply_curse_of_rot(s_cor, "atk_dice", eng.state)
	var cor_rr_bad := KTCombat.reroll_shooting_die(s_cor, "atk_dice", 0, "cp", eng.state, rng_t)
	KTCombat.lock_in_shooting_attacks(s_cor, eng.state, rng_t)
	_assert_true(
		bool(cor_r4["ok"]) and int(cor_r4["dmg"]) == 2 and int(a_cap["wounds_cur"]) == 13 and int(b_champ["wounds_cur"]) == 12 and not bool(cor_rr_bad["ok"]) and int(s_cor["retained_atk"]["normals"]) == 1,
		"R4-4: Curse of Rot deals 1 dmg per rolled 3, locks 3s from rerolls & Purity Seals, triggers Grandfather's Blessing"
	)

	# --- Round 9 Deep Audit Regression Fixes ---
	print("--- Round 9 Deep Audit Regression Fixes ---")
	# R9-1: AoD Captain Heroic Leader (0CP firefight ploy) reverts heroic_used_tp cleanly on pop_undo()
	eng.state["sides"]["A"]["heroic_used_tp"] = false
	eng.state["sides"]["A"]["used_ploys_tp"].clear()
	eng.state["sides"]["A"]["cp"] = 2
	a_cap["charged_this_tp"] = true
	a_cap["shock_assault_active"] = false
	eng.state["active_op_uid"] = str(a_cap["uid"])
	var sa_cap_res := eng.use_firefight_ploy("A", "shock_assault", str(a_cap["uid"]))
	var heroic_set_after := bool(eng.state["sides"]["A"]["heroic_used_tp"])
	var cp_unchanged := int(eng.state["sides"]["A"]["cp"]) == 2
	eng.pop_undo()
	_assert_true(
		bool(sa_cap_res["ok"]) and heroic_set_after and cp_unchanged and not bool(eng.state["sides"]["A"]["heroic_used_tp"]),
		"R9-1: AoD Captain Heroic Leader mutates heroic_used_tp AFTER push_undo so Undo cleanly restores 0CP allowance"
	)
	eng.state["active_op_uid"] = ""
	# Re-fetch operative dict references after pop_undo() replaced eng.state!
	a_cap = _find_by_op_id(eng, "aod_captain")
	a_sniper = _find_by_op_id(eng, "aod_sniper")
	b_war = _find_by_op_id(eng, "pm_heavy_gunner")
	b_champ = _find_by_op_id(eng, "pm_champion")

	# R9-2: Sweep & Clear (扫荡清理) V11.5 errata — clearing TWO un-swept non-home objectives scores 1VP (not 2VP),
	# while clearing a swept objective scores 2VP
	var eng_sc := KTEngine.new(9092)
	eng_sc.start_match("hotseat", "A")
	eng_sc.auto_deploy_all()
	eng_sc.auto_initiative()
	eng_sc.state["tp"] = 2
	eng_sc.state["sides"]["A"]["tac_op"] = "sweep_clear"
	eng_sc.state["sides"]["A"]["vp_tac"] = 0
	eng_sc.state["sides"]["A"]["tac_tp_gain"] = 0
	# Clear all objectives' enemy APL and mark both non-A objectives as cleared (without sweep markers)
	for o_sc in eng_sc.state["operatives"]:
		(o_sc as Dictionary)["pos"] = Vector2(1.5, 20.5 if str((o_sc as Dictionary)["side"]) == "A" else 1.5)
	eng_sc.recalculate_objectives_and_kills()
	var non_a_oids: Array[String] = []
	for obj_sc in eng_sc.state["map"]["objectives"]:
		if eng_sc.objective_owner(obj_sc) != "A":
			non_a_oids.append(str(obj_sc["id"]))
	for oid_sc in non_a_oids:
		eng_sc.state["objectives_state"][oid_sc]["tac"] = {"cleared_A": true}
	eng_sc._score_tac_op_end_of_tp()
	var vp_two_unswept := int(eng_sc.state["sides"]["A"]["vp_tac"])
	# Now mark one of them as swept as well and re-score next TP -> should give +2VP
	eng_sc.state["sides"]["A"]["vp_tac"] = 0
	eng_sc.state["sides"]["A"]["tac_tp_gain"] = 0
	(eng_sc.state["objectives_state"][non_a_oids[0]]["tac"] as Dictionary)["sweep_A"] = true
	eng_sc._score_tac_op_end_of_tp()
	var vp_one_swept := int(eng_sc.state["sides"]["A"]["vp_tac"])
	_assert_true(
		non_a_oids.size() >= 2 and vp_two_unswept == 1 and vp_one_swept == 2,
		"R9-2: Sweep & Clear V11.5 errata scores 1VP for >=1 un-swept cleared non-home objective and 2VP when cleared+swept (p.111)"
	)

	# R9-3: set_improved_cover_choice refuses toggling after Curse of Rot locks defence dice
	eng.state["sides"]["B"]["cp"] = 3
	eng.state["sides"]["B"]["used_ploys_tp"].clear()
	eng.state["sides"]["A"]["primary_tactic"] = "stealthy"
	a_cap["pos"] = Vector2(12.0, 12.0)
	b_champ["pos"] = Vector2(14.0, 12.0)
	var ev_ic_r9 := {"valid": true, "distance": 0.74, "visible": true, "cover": "standard", "obscured": false, "vantage_accurate": 0}
	var s_ic_r9 := KTCombat.create_shooting_session(b_champ, a_cap, w_plain_r4, ev_ic_r9, eng.state, rng_t, false)
	KTCombat.lock_in_shooting_attacks(s_ic_r9, eng.state, rng_t)
	s_ic_r9["def_dice"] = [{"id": 0, "val": 3, "rerolled": false, "locked": false}]
	var cor_def_r9 := KTCombat.apply_curse_of_rot(s_ic_r9, "def_dice", eng.state)
	var ic_blocked_r9 := not KTCombat.set_improved_cover_choice(s_ic_r9, "1crit", rng_t)
	_assert_true(
		bool(cor_def_r9["ok"]) and ic_blocked_r9,
		"R9-3: set_improved_cover_choice is blocked after Curse of Rot locks defence dice"
	)

	# R9-4: surveil_targets & tracked_enemies respect 3D vertical distance (p.22)
	eng.state["map"]["terrains"] = [
		{"id": "v35_t9", "name": "高台", "kind": "vantage", "elevation": 3.5, "rect": Rect2(10.0, 10.0, 4.0, 4.0)},
	]
	for op_t9 in eng.state["operatives"]:
		(op_t9 as Dictionary)["pos"] = Vector2(2.0, 20.0 if str((op_t9 as Dictionary)["side"]) == "A" else 2.0)
		(op_t9 as Dictionary)["ready"] = false
	a_sniper["pos"] = Vector2(12.0, 12.0) # Vantage z=3.5
	a_sniper["order"] = "conceal"
	a_sniper["ready"] = true
	b_war["pos"] = Vector2(18.7, 12.0) # horiz edge = 6.7 - 1.26 = 5.44" (<=6" in 2D), 3D = sqrt(5.44^2 + 3.5^2) = 6.47" (>6" in 3D!)
	b_war["order"] = "engage"
	b_war["ready"] = true
	var sv_3d := eng.surveil_targets(a_sniper)
	var tr_3d := eng.tracked_enemies("A")
	var b_in_sv := false
	for sv_o in sv_3d:
		if str(sv_o["uid"]) == str(b_war["uid"]):
			b_in_sv = true
	var b_in_tr := false
	for tr_o in tr_3d:
		if str(tr_o["uid"]) == str(b_war["uid"]):
			b_in_tr = true
	_assert_true(
		b_in_sv and not b_in_tr,
		"R9-4: surveil_targets (>6\") and tracked_enemies (<=6\") use 3D vertical distance across Vantage elevation (p.22)"
	)

	# R9-5: ai_equipment_spot finds legal ladder spots on official Killzone Volkus maps
	var eng_lad := KTEngine.new(9095)
	eng_lad.start_match("ai", "A")
	var lad_a := eng_lad.ai_equipment_spot("A", "ladder")
	var lad_b := eng_lad.ai_equipment_spot("B", "ladder")
	_assert_true(
		bool(lad_a.get("ok", false)) and bool(lad_b.get("ok", false)),
		"R9-5: ai_equipment_spot locates legal ladder placements against 2\"+ walls/vantage on Killzone Volkus"
	)

	# R10-1: Cloud of Flies respects 3D elevation (ground marker does not obscure PM 3.5" above on Vantage)
	eng.state["map"]["terrains"] = [
		{"id": "v35_cof", "name": "高台", "kind": "vantage", "elevation": 3.5, "rect": Rect2(10.0, 10.0, 4.0, 4.0)},
	]
	eng.state["markers"] = [{"kind": "cloud_of_flies", "side": "B", "pos": Vector2(14.6, 12.0), "name": "飞蝇云"}]
	a_cap["pos"] = Vector2(22.0, 12.0) # ground, >3" away
	b_champ["pos"] = Vector2(13.8, 12.0) # on 3.5" Vantage (2D dist to marker 0.8 + 0.63 = 1.43? test closer: marker at (13.9, 12.0) on ground? wait: marker inside vantage rect has z=3.5!)
	# Place marker right outside Vantage edge on ground (z=0.0) at (14.1, 12.0), b_champ on Vantage edge at (13.5, 12.0):
	# 2D dist + r_b = 0.6 + 0.63 = 1.23 <= 1.401 (would falsely trigger in 2D!), but |z_b - z_m| = 3.5 > 1.0!
	eng.state["markers"] = [{"kind": "cloud_of_flies", "side": "B", "pos": Vector2(14.1, 12.0), "name": "飞蝇云"}]
	b_champ["pos"] = Vector2(13.5, 12.0)
	b_champ["order"] = "engage"
	var ev_cof_3d := KTGeometry.evaluate_shot(a_cap, b_champ, w_plain_r4, eng.state)
	_assert_true(
		not bool(ev_cof_3d["obscured"]),
		"R10-1: Cloud of Flies respects 3D elevation (ground marker does not obscure operative 3.5\" above on Vantage)"
	)
	eng.state["markers"] = []

	# R10-2: AI navigates around ruin/stronghold walls on Killzone Volkus Map 2 (volkus_25_02) instead of getting wall-trapped
	var eng_m2 := KTEngine.new(1002)
	eng_m2.start_match("hotseat", "A", "dueller", "hardy", "volkus_25_02")
	eng_m2.auto_deploy_all()
	eng_m2.auto_initiative()
	var m2_s1: String = str(eng_m2.state["turn_side"])
	var m2_s2: String = "B" if m2_s1 == "A" else "A"
	eng_m2.choose_primary_op(m2_s1, "key")
	eng_m2.choose_primary_op(m2_s2, "key")
	eng_m2.pass_strategy(m2_s1)
	eng_m2.pass_strategy(m2_s2)
	var m2_op_a: Dictionary = _find_by_op_id(eng_m2, "aod_captain")
	m2_op_a["pos"] = Vector2(2.95, 16.45) # right behind large_ruin_wall_0 (x=5.5..6.5, y=14..20.5)
	eng_m2.state["turn_side"] = "A"
	eng_m2.activate_operative(str(m2_op_a["uid"]), "engage")
	var m2_path := KTAI._find_best_advance_path(eng_m2, m2_op_a, "reposition")
	var m2_mv := eng_m2.execute_move(str(m2_op_a["uid"]), "reposition", m2_path) if not m2_path.is_empty() else {"ok": false}
	_assert_true(
		not m2_path.is_empty() and bool(m2_mv.get("ok", false)),
		"R10-2: KTAI navigates around ruin/stronghold walls on volkus_25_02 via corner detour / wall-escape pathing"
	)

	# A1: Incapacitated Captain must NOT ghost-trigger Iron Halo on remaining atk_norms
	var eng_a1 := KTEngine.new(11011)
	eng_a1.start_match("hotseat", "A", "dueller", "hardy")
	eng_a1.auto_deploy_all()
	eng_a1.auto_initiative()
	var a1_cap: Dictionary = _find_by_op_id(eng_a1, "aod_captain")
	var a1_pm: Dictionary = _find_by_op_id(eng_a1, "pm_champion")
	a1_cap["wounds_cur"] = 3
	a1_cap["iron_halo_used"] = false
	a1_cap["incapacitated"] = false
	var w_a1 := {"id": "w_a1", "name": "测试等离子", "kind": "ranged", "attacks": 4, "hit": 3, "normal_dmg": 4, "crit_dmg": 5, "rules": {}}
	var ev_a1 := {"valid": true, "distance": 5.0, "visible": true, "cover": "none", "obscured": false, "vantage_accurate": 0}
	var s_a1 := KTCombat.create_shooting_session(a1_pm, a1_cap, w_a1, ev_a1, eng_a1.state, rng_t, false)
	s_a1["step"] = "defence_roll"
	s_a1["retained_atk"] = {"crits": 1, "normals": 2, "devastating_mw": 0}
	s_a1["def_dice"] = []
	s_a1["auto_def_crits"] = 0
	s_a1["auto_def_normals"] = 0
	KTCombat.finalize_shooting_resolution(s_a1, eng_a1.state, rng_t)
	_assert_true(
		bool(a1_cap["incapacitated"]) and not bool(a1_cap.get("iron_halo_used", false)),
		"A1: Captain killed by crit hit does NOT ghost-trigger Iron Halo on leftover normal attack dice"
	)

	# A2: Hot self-damage on a poisoned enemy within 7" triggers PM Champion's Grandfather's Blessing
	eng_a1.state["map"]["terrains"] = []
	var a2_att: Dictionary = _find_by_op_id(eng_a1, "aod_gunner")
	a2_att["pos"] = Vector2(12.0, 12.0)
	a2_att["wounds_cur"] = 14
	a2_att["poisoned"] = true
	a2_att["incapacitated"] = false
	a1_pm["pos"] = Vector2(16.0, 12.0) # within 7"
	a1_pm["wounds_cur"] = 10
	a1_pm["blessing_healed_tp"] = 0
	a1_pm["incapacitated"] = false
	var hot_healed := false
	for seed_hot in range(1, 30):
		var rng_hot := RandomNumberGenerator.new()
		rng_hot.seed = seed_hot
		a2_att["wounds_cur"] = 14
		a2_att["incapacitated"] = false
		a1_pm["wounds_cur"] = 10
		a1_pm["blessing_healed_tp"] = 0
		var s_hot := {"rules": {"hot": true}, "effective_hit": 6, "worst_effective_hit": 6, "hot_resolved": false, "resolution_log": []}
		KTCombat._resolve_hot_if_needed(s_hot, a2_att, eng_a1.state, rng_hot)
		if int(s_hot.get("hot_self_dmg", 0)) > 0:
			hot_healed = (int(a1_pm["wounds_cur"]) > 10 and int(a1_pm["blessing_healed_tp"]) > 0)
			break
	_assert_true(
		hot_healed,
		"A2: Hot weapon self-damage on poisoned enemy within 7\" triggers PM Champion Grandfather's Blessing heal"
	)

	# A3: Tilting Shields (偏移肩盾) is NOT wasted when opponent rolls zero <6 crit threats, and triggers when rolled
	eng_a1.state["sides"]["A"]["equipment"] = ["IMP-AOD-EQ-pauldronshields"]
	var a3_aod: Dictionary = _find_by_op_id(eng_a1, "aod_gunner")
	a3_aod["pos"] = Vector2(12.0, 12.0)
	a1_pm["pos"] = Vector2(13.2, 12.0)
	var w_a3_lethal := {"id": "w_a3", "name": "测试致命武器", "kind": "melee", "attacks": 2, "hit": 3, "normal_dmg": 3, "crit_dmg": 5, "rules": {"lethal": 5}}
	var saw_preserved := false
	var saw_triggered := false
	for seed_sh in range(1, 200):
		var rng_sh := RandomNumberGenerator.new()
		rng_sh.seed = seed_sh
		eng_a1.state["sides"]["A"]["shields_used_tp"] = false
		var pool_sh := KTCombat._build_melee_fighter_pool(a1_pm, a3_aod, w_a3_lethal, true, eng_a1.state, rng_sh)
		var raw_d: Array = pool_sh["dice_raw"]
		var has_5 := raw_d.has(5)
		var used_sh: bool = bool(eng_a1.state["sides"]["A"].get("shields_used_tp", false))
		if not has_5 and not used_sh:
			saw_preserved = true
		if has_5 and used_sh and (pool_sh["tags"] as Array).has("偏移肩盾: 仅 6 为关键成功"):
			saw_triggered = true
		if saw_preserved and saw_triggered:
			break
	_assert_true(
		saw_preserved and saw_triggered,
		"A3: Tilting Shields is preserved when opponent rolls no <6 crit threat and triggers when a <6 Lethal crit is rolled"
	)

	# A6: KTGlossary.split_keyword caches compiled RegEx and parses numeric/range keywords accurately
	var kw1 := KTGlossary.split_keyword("致命 5+")
	var kw2 := KTGlossary.split_keyword("范围 8\"")
	_assert_true(
		kw1[0] == "致命" and kw1[1] == "5+" and kw2[0] == "范围" and kw2[1] == "8\"" and KTGlossary._kw_re != null,
		"A6: KTGlossary.split_keyword caches static RegEx and parses keyword/value pairs"
	)
