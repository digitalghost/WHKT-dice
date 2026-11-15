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

	# 1. Straight path through impassable Heavy wall (`small_ruin_02_12`: Rect2(12.5, 18.5, 6.0, 1.0)) should fail
	cap["pos"] = Vector2(15.2, 20.8)
	var blocked_pts: Array[Vector2] = [Vector2(15.2, 20.8), Vector2(15.2, 16.8)]
	var res_blk: Dictionary = KTGeometry.validate_move_path(blocked_pts, cap, "reposition", 6.0, eng.state)
	_assert_true(not bool(res_blk["ok"]), "Heavy wall blocks straight walk-through")

	# 2. Climbing onto SW Vantage deck (`large_ruin_02_deck_71`: Rect2(0.0, 16.0, 6.0, 3.5), +3.0") adds +2" climb cost
	cap["pos"] = Vector2(2.5, 15.0) # Just north of SW Vantage deck (open side)
	var climb_pts: Array[Vector2] = [Vector2(2.5, 15.0), Vector2(2.5, 17.0)] # 2" horizontal + 2" climb = 4"
	var res_clb: Dictionary = KTGeometry.validate_move_path(climb_pts, cap, "reposition", 6.0, eng.state)
	_assert_true(bool(res_clb["ok"]) and absf(float(res_clb["cost"]) - 4.0) < 0.15, "Vantage +2\" climb cost calculated accurately")

	# 3. Concealed target behind Light Rubble (`light_rubble_18`: Rect2(24.21, 7.29, 3.33, 1.95), h=0.95") vs Ground shooter (Mortis profile) -> untargetable!
	var sniper: Dictionary = _find_by_op_id(eng, "aod_sniper")
	var target: Dictionary = _find_by_op_id(eng, "pm_champion")
	sniper["pos"] = Vector2(20.0, 8.2)
	target["pos"] = Vector2(28.4, 8.2)
	target["order"] = "conceal"
	var exec_sniper: Dictionary = sniper["weapons"][1]   # Executioner (Seek Light + Saturate)
	var mortis_sniper: Dictionary = sniper["weapons"][3] # Mortis (Piercing 1 + Devastating 3, no Seek Light)
	var ev_ground: Dictionary = KTGeometry.evaluate_shot(sniper, target, mortis_sniper, eng.state)
	_assert_true(str(ev_ground["cover"]) != "none" and not bool(ev_ground["valid"]), "Concealed operative in Light Cover cannot be shot from ground without Seek Light")

	# 3b. Executioner profile (Seek Light) CAN target Concealed operative in Light Cover from ground!
	var ev_seek: Dictionary = KTGeometry.evaluate_shot(sniper, target, exec_sniper, eng.state)
	_assert_true(bool(ev_seek["valid"]), "Executioner Seek Light breaks Light-Cover Conceal from ground")

	# 4. Move Sniper onto NE Vantage (`large_ruin_deck_4`, +3.0", at x=22.0, y=5.0) shooting Concealed target behind `light_rubble_18` (x=26.0, y=10.0) with Mortis
	sniper["pos"] = Vector2(22.0, 5.0)
	target["pos"] = Vector2(26.0, 10.0)
	var ev_vant_conceal: Dictionary = KTGeometry.evaluate_shot(sniper, target, mortis_sniper, eng.state)
	_assert_true(bool(ev_vant_conceal["valid"]) and str(ev_vant_conceal["cover"]) == "improved", "Vantage breaks Light-Cover Conceal and grants defender Improved Cover")

	# 4b. Against Engage target, Vantage (+3.0") grants Accurate 1!
	target["order"] = "engage"
	var ev_vant_engage: Dictionary = KTGeometry.evaluate_shot(sniper, target, mortis_sniper, eng.state)
	_assert_true(bool(ev_vant_engage["valid"]) and int(ev_vant_engage["vantage_accurate"]) == 1, "Vantage grants Accurate 1 against Engage target")

	# 6. 3D visibility: a 2.21" heavy ruin wall (`small_ruin_9` x 2.5..7.5, y 2.5..3.5) between
	#    two ground-level operatives blocks line of sight (both eye 1.4" and model top 1.6" < 2.21").
	var ters: Array = eng.state["map"]["terrains"]
	var los_blocked := KTGeometry.has_visibility(Vector2(3.8, 1.4), 0.63, 0.0, Vector2(3.8, 6.0), 0.63, 0.0, ters)
	_assert_true(not los_blocked, "2.2\" heavy ruin wall blocks ground-to-ground visibility")
	#    From the +3.0" vantage deck (`stronghold_deck_33`, eye at 4.4") the same wall does not block.
	var los_from_deck := KTGeometry.has_visibility(Vector2(14.2, 8.0), 0.63, 3.0, Vector2(3.8, 1.4), 0.63, 0.0, ters)
	_assert_true(los_from_deck, "Vantage shooter sees over a 2.2\" ruin wall")
	#    A ~1" light rubble (`light_rubble_18`, h=0.95") never blocks visibility.
	var los_light := KTGeometry.has_visibility(Vector2(20.0, 8.2), 0.63, 0.0, Vector2(28.4, 8.2), 0.63, 0.0, ters)
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
	_assert_true(str(eng.state["phase"]) == "DEPLOY", "Both equipment choices revealed -> DEPLOY")
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
