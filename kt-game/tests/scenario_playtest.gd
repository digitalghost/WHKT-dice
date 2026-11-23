extends SceneTree
## Full-battle playtest: the AI drives BOTH sides from pre-battle setup to the end of turning point 4
## on every official map, with varied equipment / tac ops / primary ops per seed. After every step
## the state is checked against invariants. Not part of run_all.gd (it takes a while).
##   godot --headless --path . -s res://tests/scenario_playtest.gd -- --seeds=20
## Report: build/playtest/report.md. Exit code 1 if any battle fails.

const MAX_STEPS := 4000

var _failures: Array[String] = []
var _warnings: Dictionary = {}
var _rows: Array[String] = []
var _tac_played: Dictionary = {}
var _tac_scored: Dictionary = {}
var _total_steps: int = 0
var _games: int = 0
## --all-tacs: assign tac ops from the whole 12-card catalogue (ignoring team archetypes) so the
## cards neither launch team can normally pick still get exercised end to end.
var _all_tacs: bool = false
## --vary-rosters: cycle through all 8 AoD Chapter Tactics and alternate leaders/warriors across seeds.
var _vary_rosters: bool = false

const ALL_TACTICS: Array[String] = [
	"aggressive", "dueller", "resolute", "stealthy",
	"mobile", "hardy", "sharpshooter", "siege_specialist",
]
const ALT_AOD_ROSTERS: Array = [
	["aod_captain", "aod_grenadier", "aod_sniper", "aod_gunner", "aod_heavy_gunner", "aod_assault_warrior"],
	["aod_sergeant", "aod_grenadier", "aod_sniper", "aod_gunner", "aod_warrior", "aod_assault_warrior"],
	["aod_assault_sergeant", "aod_grenadier", "aod_sniper", "aod_heavy_gunner", "aod_warrior", "aod_warrior"],
]
const ALT_PM_ROSTERS: Array = [
	["pm_champion", "pm_bombardier", "pm_fighter", "pm_heavy_gunner", "pm_icon_bearer", "pm_plaguecaster"],
	["pm_champion", "pm_bombardier", "pm_fighter", "pm_heavy_gunner", "pm_icon_bearer", "pm_warrior"],
	["pm_champion", "pm_bombardier", "pm_fighter", "pm_plaguecaster", "pm_icon_bearer", "pm_warrior"],
]


func _init() -> void:
	var seeds: int = 20
	for a in OS.get_cmdline_user_args():
		var s: String = str(a)
		if s.begins_with("--seeds="):
			seeds = int(s.substr(8))
		if s == "--all-tacs":
			_all_tacs = true
		if s == "--vary-rosters":
			_vary_rosters = true
	var mi: int = 0
	for map_id in KTData.MAP_IDS:
		for k in range(seeds):
			_play(str(map_id), 1000 + mi * 100 + k)
		mi += 1
	_write_report()
	print("PLAYTEST: %d battles, %d failures, %d steps total" % [_games, _failures.size(), _total_steps])
	for f in _failures:
		printerr("  [FAIL] " + f)
	for w in _warnings.keys():
		print("  [warn] %s x%d" % [str(w), int(_warnings[w])])
	quit(0 if _failures.is_empty() else 1)


func _fail(tag: String, msg: String) -> void:
	_failures.append("%s: %s" % [tag, msg])


func _warn(key: String) -> void:
	_warnings[key] = int(_warnings.get(key, 0)) + 1


func _play(map_id: String, seed_v: int) -> void:
	_games += 1
	var tag: String = "%s seed %d" % [map_id, seed_v]
	var eng := KTEngine.new(seed_v)
	var t_prim: String = "dueller"
	var t_sec: String = "hardy"
	if _vary_rosters:
		t_prim = ALL_TACTICS[seed_v % ALL_TACTICS.size()]
		t_sec = ALL_TACTICS[(seed_v + 3) % ALL_TACTICS.size()]
	eng.start_match("hotseat", "A", t_prim, t_sec, map_id)
	eng.choose_drop_zone(seed_v % 2 == 1)
	var guard: int = 0
	while str(eng.state["phase"]) == "SELECT" and guard < 6:
		guard += 1
		var sd: String = eng.setup_side()
		var ros: Array = eng.default_roster(sd)
		if _vary_rosters:
			ros = (ALT_AOD_ROSTERS[seed_v % ALT_AOD_ROSTERS.size()] as Array) if sd == "A" else (ALT_PM_ROSTERS[seed_v % ALT_PM_ROSTERS.size()] as Array)
		var res_r: Dictionary = eng.confirm_roster(sd, ros)
		if not bool(res_r.get("ok", false)):
			_fail(tag, "confirm_roster %s rejected: %s" % [str(ros), str(res_r.get("msg", ""))])
			eng.confirm_roster(sd, eng.default_roster(sd))
	guard = 0
	while str(eng.state["phase"]) == "EQUIP" and guard < 6:
		guard += 1
		var es: String = eng.setup_side()
		var pool: Array = []
		for e in eng.equipment_options(es):
			pool.append(str(e["id"]))
		for u in KTEngine.universal_equipment_options():
			pool.append(str(u["id"]))
		var picks: Array = []
		var off: int = seed_v + (5 if es == "B" else 0)
		var j: int = 0
		while picks.size() < 4 and j < 40:
			var cand: String = str(pool[(off + j * 3) % pool.size()])
			if not picks.has(cand):
				picks.append(cand)
			j += 1
		var opts: Dictionary = {"utility": ["mix", "smoke2", "stun2"][seed_v % 3], "explosive": ["mix", "frag2", "krak2"][(seed_v / 3) % 3]}
		var res_e: Dictionary = eng.confirm_equipment(es, picks, opts)
		if not bool(res_e.get("ok", false)):
			_fail(tag, "confirm_equipment rejected %s: %s" % [str(picks), str(res_e.get("msg", ""))])
			eng.confirm_equipment(es, [])
	guard = 0
	var tacs: Dictionary = {}
	while str(eng.state["phase"]) == "TACOP" and guard < 6:
		guard += 1
		var ts: String = eng.setup_side()
		var t_opts: Array = eng.tac_op_options(ts)
		var t_id: String = str(t_opts[(seed_v + (3 if ts == "B" else 0)) % t_opts.size()]["id"])
		var res_t: Dictionary = eng.choose_tac_op(ts, t_id)
		if not bool(res_t.get("ok", false)):
			_fail(tag, "choose_tac_op %s rejected: %s" % [t_id, str(res_t.get("msg", ""))])
			eng.choose_tac_op(ts, eng.default_tac_op(ts))
		tacs[ts] = eng.tac_op_of(ts)
	if _all_tacs:
		var cat: Array = KTEngine.tac_op_catalog()
		for sid_f in ["A", "B"]:
			var f_id: String = str(cat[(seed_v + (5 if sid_f == "B" else 0)) % cat.size()]["id"])
			eng.state["sides"][sid_f]["tac_op"] = f_id
			tacs[sid_f] = f_id
	eng.auto_deploy_all()
	if str(eng.state["phase"]) != "INITIATIVE":
		_fail(tag, "setup did not reach INITIATIVE (stuck in %s)" % str(eng.state["phase"]))
		_rows.append("| %s | %d | - | - | - | ✘ setup |" % [map_id, seed_v])
		return

	var steps: int = 0
	var last_tp: int = 1
	var ok: bool = true
	while str(eng.state["phase"]) != "GAME_OVER" and ok:
		steps += 1
		if steps > MAX_STEPS:
			_fail(tag, "no GAME_OVER after %d steps (phase %s, tp %d, turn %s, active '%s')" % [MAX_STEPS, str(eng.state["phase"]), int(eng.state["tp"]), str(eng.state["turn_side"]), str(eng.state["active_op_uid"])])
			ok = false
			break
		ok = _step(eng, tag, seed_v)
		if ok:
			ok = _check(eng, tag, last_tp)
		last_tp = int(eng.state["tp"])
	_total_steps += steps
	var va: int = eng.get_total_vp("A")
	var vb: int = eng.get_total_vp("B")
	for sid in ["A", "B"]:
		var tid: String = str(tacs.get(sid, ""))
		_tac_played[tid] = int(_tac_played.get(tid, 0)) + 1
		if int(eng.state["sides"][sid]["vp_tac"]) > 0:
			_tac_scored[tid] = int(_tac_scored.get(tid, 0)) + 1
	_rows.append("| %s | %d | %s / %s | %d | %d : %d | %s |" % [map_id, seed_v, str(tacs.get("A", "")), str(tacs.get("B", "")), steps, va, vb, "✔" if ok else "✘"])


func _step(eng: KTEngine, tag: String, seed_v: int) -> bool:
	var phase: String = str(eng.state["phase"])
	if phase == "INITIATIVE":
		if not eng.init_pending():
			if not bool(eng.roll_initiative().get("ok", false)):
				_fail(tag, "roll_initiative refused")
				return false
			return true
		var t: String = str(eng.state["init"]["turn"])
		var dec_i: Dictionary = KTAI.decide_initiative_card(eng, t)
		var res_i: Dictionary = {}
		if str(dec_i["action"]) == "card":
			res_i = eng.init_play_card(t, int(dec_i["card"]), int(dec_i["sign"]))
		else:
			res_i = eng.init_pass(t)
		if not bool(res_i.get("ok", false)):
			_fail(tag, "initiative card step refused: %s" % str(res_i.get("msg", "")))
			return false
		return true

	if phase == "STRATEGY":
		var side: String = str(eng.state["turn_side"])
		var dec: Dictionary = KTAI.decide_strategy_step(eng, side)
		var act: String = str(dec["action"])
		var res: Dictionary = {}
		if act == "primary":
			res = eng.choose_primary_op(side, ["key", "kill", "tac"][(seed_v + (1 if side == "B" else 0)) % 3])
		elif act == "envoy":
			res = eng.choose_envoy(side, str(dec["uid"]))
		elif act == "ploy":
			res = eng.use_strategic_ploy(side, str(dec["id"]))
		else:
			res = eng.pass_strategy(side)
		if not bool(res.get("ok", false)):
			_warn("strategy action '%s' refused" % act)
			res = eng.pass_strategy(side)
			if not bool(res.get("ok", false)):
				_fail(tag, "pass_strategy refused: %s" % str(res.get("msg", "")))
				return false
		return true

	if phase == "FIREFIGHT":
		var ai_side: String = str(eng.state["turn_side"])
		var active_uid: String = str(eng.state["active_op_uid"])
		if active_uid.is_empty():
			var pick: Dictionary = KTAI.pick_operative_to_activate(eng, ai_side)
			if pick.is_empty():
				eng.pass_counteract(ai_side)
			else:
				var res_a: Dictionary = eng.activate_operative(str(pick["uid"]), str(pick["order"]))
				if not bool(res_a.get("ok", false)):
					_fail(tag, "activate_operative refused: %s" % str(res_a.get("msg", "")))
					return false
			return true
		var op: Dictionary = eng.get_op(active_uid)
		if op.is_empty() or bool(op["incapacitated"]) or int(op["ap_left"]) <= 0:
			eng.end_operative_activation()
			return true
		var step_act: Dictionary = KTAI.decide_next_operative_action(eng, op)
		var kind: String = str(step_act.get("kind", "end"))
		if kind == "move":
			var pts: Array[Vector2] = step_act["waypoints"]
			var res_m: Dictionary = eng.execute_move(active_uid, str(step_act["move_type"]), pts)
			if not bool(res_m.get("ok", false)):
				_warn("AI move '%s' refused" % str(step_act["move_type"]))
				_end_if_still_active(eng, active_uid)
		elif kind == "secure":
			if not bool(eng.execute_secure_objective(active_uid).get("ok", false)):
				_warn("AI secure refused")
				_end_if_still_active(eng, active_uid)
		elif kind == "unique":
			var res_u: Dictionary = eng.execute_unique_action(active_uid, str(step_act["ability_id"]))
			if not bool(res_u.get("ok", false)):
				_warn("AI unique '%s' refused" % str(step_act["ability_id"]))
				_end_if_still_active(eng, active_uid)
		elif kind == "target_action":
			var res_ta: Dictionary = eng.execute_target_action(active_uid, str(step_act["ability_id"]), str(step_act["target_uid"]))
			if not bool(res_ta.get("ok", false)):
				_warn("AI target_action '%s' refused" % str(step_act["ability_id"]))
				_end_if_still_active(eng, active_uid)
		elif kind == "shoot":
			var def_s: Dictionary = eng.get_op(str(step_act["target_uid"]))
			var w_s: Dictionary = step_act["weapon"]
			var ev_s: Dictionary = step_act["shot_eval"]
			eng.commit_shooting_action_cost(op, w_s)
			var sess: Dictionary = KTCombat.create_shooting_session(op, def_s, w_s, ev_s, eng.state, eng.rng)
			KTCombat.resolve_all_shooting_targets_auto(sess, eng.state, eng.rng)
			eng.recalculate_objectives_and_kills()
			if str(sess.get("step", "")) != "resolved" or not (sess.get("pending_targets", []) as Array).is_empty():
				_fail(tag, "shooting session did not resolve (step %s)" % str(sess.get("step", "")))
				return false
			_after_combat(eng)
		elif kind == "fight":
			var def_f: Dictionary = eng.get_op(str(step_act["target_uid"]))
			eng.commit_melee_action_cost(op)
			var ms: Dictionary = KTCombat.create_melee_session(op, def_f, KTAI.pick_best_melee_weapon(op), KTAI.pick_best_melee_weapon(def_f), eng.state, eng.rng)
			var g: int = 0
			while str(ms["step"]) == "duel" and g < 60:
				g += 1
				var choice: String = KTCombat.choose_ai_melee_action(ms, eng.state)
				var r_ms: Dictionary = KTCombat.execute_melee_step(ms, choice, eng.state, eng.rng)
				if not bool(r_ms.get("ok", false)):
					_fail(tag, "melee step '%s' refused: %s" % [choice, str(r_ms.get("msg", ""))])
					return false
				eng.recalculate_objectives_and_kills()
			if str(ms["step"]) != "resolved":
				_fail(tag, "melee duel did not resolve in 60 steps")
				return false
			_after_combat(eng)
		else:
			eng.end_operative_activation()
		return true

	_fail(tag, "unexpected phase %s" % phase)
	return false


func _end_if_still_active(eng: KTEngine, uid: String) -> void:
	if str(eng.state.get("active_op_uid", "")) == uid:
		eng.end_operative_activation()


## Mirrors MainGame._on_combat_modal_finished().
func _after_combat(eng: KTEngine) -> void:
	var act_uid: String = str(eng.state.get("active_op_uid", ""))
	if act_uid.is_empty():
		return
	var a_op: Dictionary = eng.get_op(act_uid)
	if a_op.is_empty() or bool(a_op["incapacitated"]) or (bool(eng.state["active_is_counteract"]) and int(a_op["ap_left"]) <= 0):
		eng.end_operative_activation()


func _check(eng: KTEngine, tag: String, last_tp: int) -> bool:
	var w: float = float(eng.state["map"]["width"])
	var h: float = float(eng.state["map"]["height"])
	var tp: int = int(eng.state["tp"])
	if tp < last_tp or tp < 1 or tp > 4:
		_fail(tag, "turning point went %d -> %d" % [last_tp, tp])
		return false
	for o in eng.state["operatives"]:
		var od: Dictionary = o
		var p: Vector2 = od["pos"]
		if not is_finite(p.x) or not is_finite(p.y):
			_fail(tag, "%s has a non-finite position" % str(od["uid"]))
			return false
		var off_board: bool = p.x < 0.0 or p.y < 0.0 or p.x > w or p.y > h
		if off_board and not (is_equal_approx(p.x, -99.0) and is_equal_approx(p.y, -99.0)):
			_fail(tag, "%s is off the board at %s" % [str(od["uid"]), str(p)])
			return false
		if int(od["ap_left"]) < 0:
			_fail(tag, "%s has negative AP (%d)" % [str(od["uid"]), int(od["ap_left"])])
			return false
		if int(od["wounds_cur"]) < 0 or int(od["wounds_cur"]) > int(od["wounds_max"]):
			_fail(tag, "%s wounds out of range (%d/%d)" % [str(od["uid"]), int(od["wounds_cur"]), int(od["wounds_max"])])
			return false
		if not bool(od["incapacitated"]) and int(od["wounds_cur"]) <= 0:
			_fail(tag, "%s has 0 wounds but is not incapacitated" % str(od["uid"]))
			return false
	for sid in ["A", "B"]:
		var ss: Dictionary = eng.state["sides"][sid]
		if int(ss["cp"]) < 0:
			_fail(tag, "side %s has negative CP" % str(sid))
			return false
		for k in ["vp_key", "vp_kill", "vp_tac"]:
			if int(ss[k]) < 0 or int(ss[k]) > 6:
				_fail(tag, "side %s %s out of range: %d" % [str(sid), str(k), int(ss[k])])
				return false
	return true


func _write_report() -> void:
	var dir: String = ProjectSettings.globalize_path("res://build/playtest")
	DirAccess.make_dir_recursive_absolute(dir)
	var f := FileAccess.open(dir + "/report.md", FileAccess.WRITE)
	if f == null:
		return
	f.store_line("# 全程对局测试报告")
	f.store_line("")
	f.store_line("- 对局数：%d，失败：%d，总步数：%d（平均 %d 步/局）" % [_games, _failures.size(), _total_steps, _total_steps / maxi(1, _games)])
	f.store_line("")
	f.store_line("## 战术行动覆盖（选用次数 / 至少得 1VP 的次数）")
	f.store_line("")
	for t in _tac_played.keys():
		f.store_line("- %s：%d / %d" % [str(t), int(_tac_played[t]), int(_tac_scored.get(t, 0))])
	f.store_line("")
	f.store_line("## 警告（AI 的决定被引擎拒绝，已跳过）")
	f.store_line("")
	for wk in _warnings.keys():
		f.store_line("- %s × %d" % [str(wk), int(_warnings[wk])])
	f.store_line("")
	f.store_line("## 失败")
	f.store_line("")
	for fl in _failures:
		f.store_line("- " + fl)
	f.store_line("")
	f.store_line("## 明细")
	f.store_line("")
	f.store_line("| 地图 | 种子 | 战术行动 A / B | 步数 | VP A : B | 结果 |")
	f.store_line("|---|---|---|---|---|---|")
	for r in _rows:
		f.store_line(r)
	f.close()
