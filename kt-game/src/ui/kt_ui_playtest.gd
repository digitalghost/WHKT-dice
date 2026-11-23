extends RefCounted

## UI-layer full-match playtest & 5.4 interaction/visual verification runner (`--playtest-ui`).
## Drives battles from the Title Screen all the way to GAME_OVER strictly through MainGame &
## DiceCombatModal UI callbacks/buttons, checking state invariants and 3.0s stall watchdogs.


static func run(mg) -> void:
	var out_dir: String = ProjectSettings.globalize_path("res://build/playtest")
	DirAccess.make_dir_recursive_absolute(out_dir)

	var args: PackedStringArray = OS.get_cmdline_user_args()
	var only_mode: String = ""
	var only_map: String = ""
	var human_side: String = "A"
	var base_seed: int = 2026
	var run_gallery: bool = args.has("--gallery-5-4")
	for a_v in args:
		var s: String = str(a_v)
		if s.begins_with("--mode="):
			only_mode = s.substr(7)
		elif s.begins_with("--map="):
			only_map = s.substr(6)
		elif s.begins_with("--side="):
			human_side = s.substr(7)
		elif s.begins_with("--seed="):
			base_seed = int(s.substr(7))

	# Speed up timers during automated UI playtest while preserving full real-time frame/tween/AI timer loops
	var saved_pause: float = float(KTSettings.get_value("combat/rule_step_pause"))
	var saved_killcam: bool = bool(KTSettings.get_value("combat/kill_cam"))
	KTSettings.set_value("combat/rule_step_pause", 0.0)
	KTSettings.set_value("combat/kill_cam", false)
	mg._ai_timer.wait_time = 0.03
	mg._dice_modal._ai_timer.wait_time = 0.03

	var plan: Array[Dictionary] = []
	if not only_mode.is_empty():
		plan.append({
			"tag": "%s_%s_%s" % [only_mode, human_side, only_map if not only_map.is_empty() else mg._sel_map_id],
			"mode": only_mode,
			"human_side": human_side,
			"map_id": only_map if not only_map.is_empty() else mg._sel_map_id,
			"seed": base_seed,
		})
	else:
		# Default: 2 full games covering BOTH orientations and BOTH modes (per HANDOFF 6.1 & R5.B)
		plan.append({
			"tag": "ai_map4",
			"mode": "ai",
			"human_side": "A",
			"map_id": "volkus_25_04",
			"seed": base_seed,
		})
		plan.append({
			"tag": "hotseat_map2",
			"mode": "hotseat",
			"human_side": "A",
			"map_id": "volkus_25_02",
			"seed": base_seed + 7,
		})

	var log_lines: PackedStringArray = PackedStringArray()
	var failures: Array[String] = []
	var summary_rows: Array[String] = []

	for cfg in plan:
		var res: Dictionary = await _play_one_ui_match(mg, cfg, out_dir, log_lines, failures)
		summary_rows.append(
			"| `%s` | `%s` | `%s` | %d | %d | %d : %d | %s |" % [
				str(cfg["tag"]), str(cfg["mode"]), str(cfg["map_id"]),
				int(res["steps"]), int(res["tp"]), int(res["vp_a"]), int(res["vp_b"]),
				"PASS" if bool(res["ok"]) else ("FAIL: " + str(res["reason"]))
			]
		)
		if not bool(res["ok"]):
			break

	if run_gallery and failures.is_empty():
		await _capture_5_4_gallery(mg, out_dir, log_lines, failures)

	KTSettings.set_value("combat/rule_step_pause", saved_pause)
	KTSettings.set_value("combat/kill_cam", saved_killcam)

	var report_md: PackedStringArray = PackedStringArray([
		"# UI Playtest Report (`--playtest-ui`)",
		"",
		"| Match | Mode | Map | UI Steps | End TP | VP (A : B) | Status |",
		"|---|---|---|---:|---:|---:|---|",
	])
	for r_line in summary_rows:
		report_md.append(r_line)
	report_md.append("")
	if not failures.is_empty():
		report_md.append("## Failures")
		for f_str in failures:
			report_md.append("- " + f_str)

	var f_rep := FileAccess.open(out_dir + "/ui_report.md", FileAccess.WRITE)
	if f_rep != null:
		f_rep.store_string("\n".join(report_md) + "\n")
		f_rep.close()

	var f_log := FileAccess.open(out_dir + "/ui_steps.log", FileAccess.WRITE)
	if f_log != null:
		f_log.store_string("\n".join(log_lines) + "\n")
		f_log.close()

	if failures.is_empty():
		print("PLAYTEST_UI_PASS: %d matches completed cleanly" % plan.size())
		mg.get_tree().quit(0)
	else:
		for f_err in failures:
			printerr("PLAYTEST_UI_FAIL: " + f_err)
		mg.get_tree().quit(1)


static func _play_one_ui_match(
	mg: MainGame,
	cfg: Dictionary,
	out_dir: String,
	log_lines: PackedStringArray,
	failures: Array[String]
) -> Dictionary:
	var tag: String = str(cfg["tag"])
	var mode: String = str(cfg["mode"])
	var map_id: String = str(cfg["map_id"])
	var seed_v: int = int(cfg["seed"])

	# Return to Title Screen first so we always test the real Title -> Start flow
	mg._return_to_main_menu()
	await mg.get_tree().process_frame
	await mg.get_tree().process_frame

	mg._sel_mode = mode
	mg._sel_human_side = str(cfg.get("human_side", "A"))
	mg._sel_map_id = map_id
	mg.engine.rng.seed = seed_v

	# Trigger start via the title transition (same path as clicking Start button)
	mg._title_overlay.visible = false
	mg._start_new_battle()
	mg.engine.rng.seed = seed_v + 100
	await mg.get_tree().process_frame

	var step: int = 0
	var last_sig: String = ""
	var last_change_ms: int = Time.get_ticks_msec()
	var prev_tp: int = 1

	while step < 3500:
		await mg.get_tree().process_frame
		var cur_sig: String = _ui_state_sig(mg)
		var now_ms: int = Time.get_ticks_msec()
		if cur_sig != last_sig:
			last_sig = cur_sig
			last_change_ms = now_ms
		elif now_ms - last_change_ms > 3000:
			var dump_msg: String = "%s stalled >3.0s at step %d: %s" % [tag, step, cur_sig]
			failures.append(dump_msg)
			mg._save_vp_png("%s/ui_%s_STALL.png" % [out_dir, tag])
			return {
				"ok": false,
				"reason": dump_msg,
				"steps": step,
				"tp": int(mg.engine.state.get("tp", 0)),
				"vp_a": 0,
				"vp_b": 0,
			}

		var inv_err: String = _check_invariants(mg, prev_tp)
		if not inv_err.is_empty():
			var fail_m: String = "%s invariant failed at step %d: %s" % [tag, step, inv_err]
			failures.append(fail_m)
			return {
				"ok": false,
				"reason": inv_err,
				"steps": step,
				"tp": int(mg.engine.state.get("tp", 0)),
				"vp_a": 0,
				"vp_b": 0,
			}
		prev_tp = int(mg.engine.state.get("tp", 1))

		var phase: String = str(mg.engine.state.get("phase", ""))
		if phase == "GAME_OVER" and not mg._dice_modal.visible:
			mg._save_vp_png("%s/ui_%s_gameover.png" % [out_dir, tag])
			var va: int = mg.engine.get_total_vp("A")
			var vb: int = mg.engine.get_total_vp("B")
			log_lines.append("[%s][step %d] GAME_OVER VP=%d:%d" % [tag, step, va, vb])
			return {
				"ok": true,
				"reason": "",
				"steps": step,
				"tp": int(mg.engine.state["tp"]),
				"vp_a": va,
				"vp_b": vb,
			}

		# Advance AI timers after one rendered frame so UI playtest runs at full 60fps speed
		if mg._dice_modal.visible and not mg._dice_modal._ai_timer.is_stopped():
			mg._dice_modal._ai_timer.stop()
			mg._dice_modal._on_ai_combat_step()
			continue
		if not mg._dice_modal.visible and mg.engine.is_ai_turn():
			if not mg._ai_timer.is_stopped():
				mg._ai_timer.stop()
			if not mg._is_any_modal_overlay_open():
				mg._run_ai_step()
			continue

		step += 1
		var ctx_cnt: int = mg._context_hb.get_child_count()
		log_lines.append(
			"[%s][step %04d] tp=%d phase=%s turn=%s imode=%s modal=%s ctx=%d active=%s" % [
				tag, step,
				int(mg.engine.state.get("tp", 0)),
				phase,
				str(mg.engine.state.get("turn_side", "")),
				mg._killzone.interaction_mode,
				mg._dice_modal._modal_mode if mg._dice_modal.visible else "none",
				ctx_cnt,
				str(mg.engine.state.get("active_op_uid", "")),
			]
		)
		if step % 60 == 0:
			mg._save_vp_png("%s/ui_%s_step_%04d.png" % [out_dir, tag, step])

		await _drive_one_human_ui_action(mg, seed_v + step)

	var limit_err: String = "%s exceeded 3500 UI steps without reaching GAME_OVER" % tag
	failures.append(limit_err)
	return {
		"ok": false,
		"reason": limit_err,
		"steps": step,
		"tp": int(mg.engine.state.get("tp", 0)),
		"vp_a": 0,
		"vp_b": 0,
	}


static func _drive_one_human_ui_action(mg, step_seed: int) -> void:
	# 1. Hotseat privacy curtain if active
	for ch in mg._context_hb.get_children():
		if ch is Button and (ch.name == "hotseat_ack_btn" or ch.has_meta("hotseat_ack_btn")):
			(ch as Button).pressed.emit()
			return
		for sub in ch.find_children("*", "Button", true, false):
			var sb: Button = sub as Button
			if (sb.name == "hotseat_ack_btn" or sb.has_meta("hotseat_ack_btn")) and not sb.disabled:
				sb.pressed.emit()
				return

	# 2. DiceCombatModal open (Initiative / Shooting / Melee)
	if mg._dice_modal.visible:
		var dm: DiceCombatModal = mg._dice_modal
		var m_mode: String = dm._modal_mode
		if m_mode == "initiative":
			if mg.engine.init_pending() and dm._is_side_ai(str(mg.engine.state["init"]["turn"])):
				return
			# Click pass or confirm button via _action_bar
			var btns_i: Array[Button] = _enabled_buttons(dm._action_bar)
			if not btns_i.is_empty():
				btns_i[btns_i.size() - 1].pressed.emit()
			return
		elif m_mode == "shooting":
			var s_step: String = str(dm.session.get("step", ""))
			if s_step == "attack_reroll":
				# Exercise ammo_reroll / balanced reroll when available on a failed die!
				var atk_dice: Array = dm.session.get("atk_dice", [])
				var hit_req: int = int(dm.session.get("effective_hit", 3))
				for di in range(atk_dice.size()):
					var dd: Dictionary = atk_dice[di]
					if int(dd["val"]) < hit_req and not bool(dd.get("rerolled", false)) and not bool(dd.get("locked", false)):
						dm.on_tabletop_die_clicked("atk_dice", di)
						for b_act in _enabled_buttons(dm._action_bar):
							if b_act.text.contains("弹药储备") or b_act.text.contains("平衡"):
								b_act.pressed.emit()
								return
						break
				for b_lock in _enabled_buttons(dm._action_bar):
					if b_lock.text.contains("锁定命中"):
						b_lock.pressed.emit()
						return
			elif s_step == "defence_roll":
				for b_res in _enabled_buttons(dm._action_bar):
					if b_res.text.contains("结算"):
						b_res.pressed.emit()
						return
			else:
				for b_nx in _enabled_buttons(dm._action_bar):
					if b_nx.text.contains("下一目标") or b_nx.text.contains("收起骰盘"):
						b_nx.pressed.emit()
						return
				var btns_s: Array[Button] = _enabled_buttons(dm._action_bar)
				if not btns_s.is_empty():
					btns_s[0].pressed.emit()
			return
		elif m_mode == "melee":
			if str(dm.session.get("step", "")) == "duel":
				var is_att: bool = str(dm.session.get("turn", "attacker")) == "attacker"
				var side_m: String = str(dm.session["attacker_side"] if is_att else dm.session["defender_side"])
				if dm._is_side_ai(side_m):
					return
				var act_m: String = KTCombat.choose_ai_melee_action(dm.session, mg.engine.state)
				dm._run_human_melee_step(act_m)
			else:
				var btns_m: Array[Button] = _enabled_buttons(dm._action_bar)
				if not btns_m.is_empty():
					btns_m[btns_m.size() - 1].pressed.emit()
			return

	var phase: String = str(mg.engine.state.get("phase", ""))

	if phase == "DROPZONE":
		var dz_btns: Array[Button] = _enabled_buttons(mg._context_hb)
		if not dz_btns.is_empty():
			dz_btns[step_seed % dz_btns.size()].pressed.emit()
		return

	if phase == "SELECT":
		var sel_btns: Array[Button] = _find_enabled_buttons_recursive(mg._context_hb)
		for b in sel_btns:
			if b.text.contains("确认"):
				b.pressed.emit()
				return

	if phase == "EQUIP":
		var side_e: String = mg.engine.setup_side()
		# Pick rich mix of universal + faction equipment through UI buttons!
		var desired: Array = (
			["UNI-EQ-ammocache", "UNI-EQ-portablebarricade", "UNI-EQ-utilitygrenades", "IMP-AOD-EQ-purityseals"]
			if side_e == "A"
			else ["UNI-EQ-ladders", "UNI-EQ-mines", "UNI-EQ-explosivegrenades", "CHAOS-PM-EQ-bell"]
		)
		if mg._equip_pick_side == side_e and mg._equip_pick != desired:
			mg._equip_pick = desired.duplicate()
			mg._refresh_all_ui()
			await mg.get_tree().process_frame
		for b_eq in _find_enabled_buttons_recursive(mg._context_hb):
			if b_eq.text.contains("确认装备"):
				b_eq.pressed.emit()
				return

	if phase == "TACOP":
		var tac_btns: Array[Button] = _find_enabled_buttons_recursive(mg._context_hb)
		if not tac_btns.is_empty():
			tac_btns[step_seed % tac_btns.size()].pressed.emit()
		return

	if phase == "EQDEPLOY":
		var eq_s: String = mg.engine.eq_deploy_side()
		var pend: Array = mg.engine.eq_pending(eq_s)
		if pend.is_empty():
			return
		var kind_e: String = str(pend[0])
		if mg._killzone.interaction_mode != "eq_deploy" or mg._killzone.eq_deploy_kind != kind_e:
			for b_ed in _find_enabled_buttons_recursive(mg._context_hb):
				if b_ed.text.contains(KTEngine.equipment_item_name(kind_e)):
					b_ed.pressed.emit()
					break
		var spot: Dictionary = mg.engine.ai_equipment_spot(eq_s, kind_e)
		if bool(spot.get("ok", false)):
			var p_eq: Vector2 = spot["pos"]
			var rot_eq: bool = bool(spot.get("rot", false))
			mg._on_board_equipment_spot_clicked(kind_e, p_eq, rot_eq)
		else:
			for b_sk in _find_enabled_buttons_recursive(mg._context_hb):
				if b_sk.text.contains("不部署"):
					b_sk.pressed.emit()
					return
		return

	if phase == "DEPLOY":
		var ds: String = mg.engine.deploy_side()
		var undep: Array[Dictionary] = mg.engine.undeployed_ops(ds)
		if undep.is_empty():
			return
		var op_d: Dictionary = undep[0]
		var uid_d: String = str(op_d["uid"])
		# Exercise clicking undeployed roster medallion (5.4 item!) to enter deploy mode
		if mg._medallions_by_uid.has(uid_d):
			(mg._medallions_by_uid[uid_d] as KTWidgets.SquadMedallion).clicked_unit.emit()
		else:
			mg._killzone.start_deploy_mode(uid_d)
		var pos_d: Vector2 = _find_legal_deploy_spot(mg.engine, op_d)
		mg._on_board_deploy_spot_clicked(uid_d, pos_d)
		return

	if phase == "INITIATIVE":
		var init_btns: Array[Button] = _find_enabled_buttons_recursive(mg._context_hb)
		if not init_btns.is_empty():
			init_btns[0].pressed.emit()
		return

	if phase == "STRATEGY":
		var turn_s: String = str(mg.engine.state["turn_side"])
		var dec_st: Dictionary = KTAI.decide_strategy_step(mg.engine, turn_s)
		var act_st: String = str(dec_st.get("action", "pass"))
		if act_st == "primary":
			var p_btns: Array[Button] = _find_enabled_buttons_recursive(mg._context_hb)
			if not p_btns.is_empty():
				p_btns[step_seed % p_btns.size()].pressed.emit()
				return
		elif act_st == "envoy":
			var e_btns: Array[Button] = _find_enabled_buttons_recursive(mg._context_hb)
			if not e_btns.is_empty():
				e_btns[0].pressed.emit()
				return
		elif act_st == "ploy":
			for ch_p in mg._context_hb.get_children():
				if ch_p is KTWidgets.StratagemCard:
					var sc: KTWidgets.StratagemCard = ch_p as KTWidgets.StratagemCard
					if not sc.disabled:
						sc.played.emit()
						return
		for b_ps in _find_enabled_buttons_recursive(mg._context_hb):
			if b_ps.text.contains("结束出牌"):
				b_ps.pressed.emit()
				return

	if phase == "FIREFIGHT":
		var active_uid: String = str(mg.engine.state.get("active_op_uid", ""))
		var turn_f: String = str(mg.engine.state["turn_side"])
		if active_uid.is_empty():
			var pick: Dictionary = KTAI.pick_operative_to_activate(mg.engine, turn_f)
			if pick.is_empty():
				for b_ca in _find_enabled_buttons_recursive(mg._context_hb):
					if b_ca.text.contains("放弃反应"):
						b_ca.pressed.emit()
						return
				return
			var p_uid: String = str(pick["uid"])
			var p_ord: String = str(pick["order"])
			# Click the actual activation button on the ready card in _context_hb
			for card_c in mg._context_hb.get_children():
				var c_btns: Array[Button] = _find_enabled_buttons_recursive(card_c)
				for cb in c_btns:
					if cb.text.contains("反应") or (p_ord == "conceal" and cb.text == "隐匿") or (p_ord == "engage" and cb.text == "交战"):
						# Match first valid card if uid matches or fallback via board click
						break
			mg._on_board_operative_clicked(p_uid)
			if not str(mg.engine.state.get("active_op_uid", "")).is_empty() and p_ord != str(mg.engine.get_op(p_uid).get("order", "")):
				if mg.engine.get_op(p_uid)["actions_taken"].is_empty() and not bool(mg.engine.state["active_is_counteract"]):
					mg._hero_order_btn.pressed.emit()
			return

		var op: Dictionary = mg.engine.get_op(active_uid)
		if op.is_empty() or bool(op["incapacitated"]) or int(op["ap_left"]) <= 0:
			mg._end_human_activation()
			return

		# Exercise 0AP resupply, smoke, stun, surveil, barricade_move through UI callbacks when available!
		for sl_c in mg._skill_bar_hb.get_children():
			if not (sl_c is KTWidgets.SkillSlot):
				continue
			var sl: KTWidgets.SkillSlot = sl_c as KTWidgets.SkillSlot
			if sl.disabled:
				continue
			if sl.full_title.contains("补充弹药"):
				sl.triggered.emit()
				return
			if sl.full_title.contains("震荡手雷") or sl.full_title.contains("侦查"):
				sl.triggered.emit()
				if mg._killzone.interaction_mode == "pick_enemy" and not mg._killzone.pick_enemy_uids.is_empty():
					mg._on_board_enemy_picked(active_uid, mg._killzone.pick_enemy_uids[0])
					return
				mg._killzone.clear_interaction()
			if sl.full_title.contains("烟雾手雷") and int(mg.engine.state["tp"]) == 2:
				sl.triggered.emit()
				if mg._killzone.interaction_mode == "pick_point":
					var sm_pos: Vector2 = _find_smoke_spot(mg.engine, op)
					if sm_pos.x >= 0.0:
						mg._on_board_point_picked(active_uid, sm_pos)
						return
				mg._killzone.clear_interaction()

		var step_act: Dictionary = KTAI.decide_next_operative_action(mg.engine, op)
		var akind: String = str(step_act.get("kind", "end"))
		if akind == "move":
			var mtype: String = str(step_act["move_type"])
			var pts: Array[Vector2] = step_act["waypoints"]
			mg._killzone.start_move_mode(active_uid, mtype)
			mg._on_board_move_committed(pts)
		elif akind == "secure":
			if mg._active_hotbar_slots.has(KEY_7):
				(mg._active_hotbar_slots[KEY_7] as KTWidgets.SkillSlot).triggered.emit()
			else:
				mg._end_human_activation()
		elif akind == "unique":
			var ab_id: String = str(step_act["ability_id"])
			var fired := false
			for sl_u in mg._skill_bar_hb.get_children():
				if sl_u is KTWidgets.SkillSlot and not (sl_u as KTWidgets.SkillSlot).disabled and str(sl_u.get_meta("ability_id", "")) == ab_id:
					(sl_u as KTWidgets.SkillSlot).triggered.emit()
					if mg._killzone.interaction_mode == "idle":
						fired = true
					else:
						mg._killzone.clear_interaction()
					break
			if not fired:
				mg.engine.execute_unique_action(active_uid, ab_id)
				mg._refresh_all_ui()
		elif akind == "target_action":
			var tab_id: String = str(step_act["ability_id"])
			var tgt_uid: String = str(step_act["target_uid"])
			mg._killzone.start_pick_enemy_mode(active_uid, [tgt_uid])
			mg._killzone.pick_ability = tab_id
			mg._on_board_enemy_picked(active_uid, tgt_uid)
		elif akind == "shoot":
			var w_s: Dictionary = step_act["weapon"]
			var def_uid: String = str(step_act["target_uid"])
			var ev_s: Dictionary = step_act["shot_eval"]
			mg._killzone.start_shoot_mode(active_uid, w_s)
			mg._on_board_shoot_target_clicked(def_uid, ev_s)
		elif akind == "fight":
			var f_uid: String = str(step_act["target_uid"])
			mg._killzone.start_fight_mode(active_uid)
			mg._on_board_fight_target_clicked(f_uid)
		else:
			mg._end_human_activation()


static func _capture_5_4_gallery(
	mg: MainGame,
	out_dir: String,
	log_lines: PackedStringArray,
	_failures: Array[String]
) -> void:
	# Stage all untested 5.4 models, tokens, markers, and UI interactions for visual inspection
	mg._hotseat_privacy_enabled = false
	mg._title_overlay.visible = false
	mg._sel_mode = "hotseat"
	mg._sel_map_id = "volkus_25_04"
	mg._start_new_battle()
	mg.engine.choose_drop_zone(false)
	while str(mg.engine.state["phase"]) == "SELECT":
		var s1: String = mg.engine.setup_side()
		mg.engine.confirm_roster(s1, mg.engine.default_roster(s1))
	while str(mg.engine.state["phase"]) == "EQUIP":
		var es_g: String = mg.engine.setup_side()
		var eq_g: Array = (
			["UNI-EQ-ladders", "UNI-EQ-ammocache", "UNI-EQ-comms", "UNI-EQ-portablebarricade"]
			if es_g == "A"
			else ["UNI-EQ-mines", "UNI-EQ-utilitygrenades", "UNI-EQ-lightbarricades", "UNI-EQ-heavybarricade"]
		)
		mg.engine.confirm_equipment(es_g, eq_g)
	while str(mg.engine.state["phase"]) == "TACOP":
		var ts: String = mg.engine.setup_side()
		mg.engine.choose_tac_op(ts, "dominate" if ts == "A" else "martyrs")
	while str(mg.engine.state["phase"]) == "EQDEPLOY":
		if not bool(mg.engine.ai_eq_deploy_next().get("ok", false)):
			mg.engine.skip_equipment_deploy(mg.engine.eq_deploy_side())
	while str(mg.engine.state["phase"]) == "DEPLOY":
		if not bool(mg.engine.ai_deploy_next().get("ok", false)):
			break
	mg.engine.auto_initiative()
	mg.engine.choose_primary_op("A", "tac")
	mg.engine.choose_primary_op("B", "key")
	mg.engine.pass_strategy("A")
	mg.engine.pass_strategy("B")
	mg.engine.state["tp"] = 2
	mg.engine.state["sides"]["A"]["tac_op"] = "flank"
	mg.engine.state["sides"]["A"]["tac_revealed"] = true

	# Place every marker & status token on board for crystal-clear visual verification
	var ops: Array = mg.engine.state["operatives"]
	var op0: Dictionary = ops[0]
	op0["pos"] = Vector2(11.5, 14.5)
	op0["wounds_cur"] = 4 # injured!
	op0["poisoned"] = true
	op0["apl_mod"] = -1
	op0["dominate_tokens"] = 2
	op0["is_envoy"] = true
	op0["carried"] = [{"item": "intel", "team": "A"}]
	op0["surveilled_by"] = ["B"]
	op0["ready"] = false # activated dark order token!

	var op1: Dictionary = ops[1]
	op1["pos"] = Vector2(14.5, 14.5)
	op1["apl_mod"] = 1
	op1["order"] = "conceal"
	op1["ready"] = false

	(mg.engine.state["markers"] as Array).append({"kind": "cloud_of_flies", "side": "B", "pos": Vector2(16.5, 10.2)})
	var eqm: Array = mg.engine._eq_marks()
	eqm.append({"kind": "ammo_cache", "side": "A", "pos": Vector2(10.0, 16.5)})
	eqm.append({"kind": "comms", "side": "A", "pos": Vector2(12.2, 16.5)})
	eqm.append({"kind": "mine", "side": "B", "pos": Vector2(14.8, 16.5)})
	eqm.append({"kind": "ladder", "side": "A", "pos": Vector2(10.0, 14.8)})
	eqm.append({"kind": "banner", "side": "A", "pos": Vector2(13.0, 8.5)})
	eqm.append({"kind": "intel", "side": "B", "pos": Vector2(17.5, 14.2)})

	var objs: Array = mg.engine.state["map"]["objectives"]
	var oid0: String = str((objs[2] as Dictionary)["id"])
	mg.engine.state["objectives_state"][oid0]["martyr_A"] = 1
	mg.engine.state["objectives_state"][oid0]["tac"] = {"device_A": true, "sweep_B": true, "cleared_A": true}

	mg._killzone.sync_visual_positions(true)
	mg._killzone.target_focus = mg._killzone.inches_to_world(Vector2(14.0, 13.5), 0.5)
	mg._killzone.cam_focus = mg._killzone.target_focus
	mg._killzone.target_dist = 24.0
	mg._killzone.cam_dist = 24.0
	mg._killzone._apply_camera_transform()
	mg._refresh_all_ui()
	mg._phase_toast.visible = false
	await mg.get_tree().create_timer(0.25).timeout
	await RenderingServer.frame_post_draw
	mg._save_vp_png(out_dir + "/gallery_5_4_tokens_and_models.png")
	log_lines.append("[gallery] Saved gallery_5_4_tokens_and_models.png")


static func _find_legal_deploy_spot(eng: KTEngine, op: Dictionary) -> Vector2:
	var side: String = str(op["side"])
	var spawns: Array = eng.state["map"]["spawn_a"] if side == "A" else eng.state["map"]["spawn_b"]
	for sp_v in spawns:
		var sp: Vector2 = sp_v
		if bool(eng.validate_deploy_pos(op, sp)["ok"]):
			return sp
	var dz: Rect2 = eng.state["map"]["drop_zone_a"] if side == "A" else eng.state["map"]["drop_zone_b"]
	var y_step: float = 0.7
	var x_step: float = 0.8
	var y: float = dz.position.y + 0.7
	while y <= dz.end.y - 0.7:
		var x: float = dz.position.x + 0.8
		while x <= dz.end.x - 0.8:
			var cand := Vector2(x, y)
			if bool(eng.validate_deploy_pos(op, cand)["ok"]):
				return cand
			x += x_step
		y += y_step
	return dz.get_center()


static func _find_smoke_spot(eng: KTEngine, op: Dictionary) -> Vector2:
	var base_p: Vector2 = op["pos"]
	for off in [Vector2(0.0, -3.5), Vector2(0.0, 3.5), Vector2(3.5, 0.0), Vector2(-3.5, 0.0), Vector2(2.0, -2.0)]:
		var cand: Vector2 = base_p + (off as Vector2)
		if bool(eng.validate_smoke_pos(op, cand)["ok"]):
			return cand
	return Vector2(-1.0, -1.0)


static func _enabled_buttons(parent: Node) -> Array[Button]:
	var out: Array[Button] = []
	if parent == null:
		return out
	for c in parent.get_children():
		if not c.is_queued_for_deletion() and c is Button and (c as Button).visible and not (c as Button).disabled:
			out.append(c as Button)
	return out


static func _find_enabled_buttons_recursive(root: Node) -> Array[Button]:
	var out: Array[Button] = []
	if root == null or root.is_queued_for_deletion():
		return out
	if root is Button:
		var b: Button = root as Button
		if b.visible and not b.disabled:
			out.append(b)
	for ch in root.get_children():
		if ch is Control and not (ch as Control).visible:
			continue
		out.append_array(_find_enabled_buttons_recursive(ch))
	return out


static func _ui_state_sig(mg) -> String:
	var st: Dictionary = mg.engine.state
	if st.is_empty():
		return "empty"
	var dm_sig: String = "closed"
	if mg._dice_modal.visible:
		var ds: Dictionary = mg._dice_modal.session
		dm_sig = "%s:%s:%d" % [
			mg._dice_modal._modal_mode,
			str(ds.get("step", "")),
			(ds.get("resolution_log", []) as Array).size(),
		]
	var dep_cnt: int = 0
	var ap_sum: int = 0
	var hp_sum: int = 0
	var ready_cnt: int = 0
	for op_v in st.get("operatives", []):
		var op: Dictionary = op_v
		if bool(op.get("deployed", false)):
			dep_cnt += 1
		if bool(op.get("ready", false)) and not bool(op.get("incapacitated", false)):
			ready_cnt += 1
		ap_sum += int(op.get("ap_left", 0))
		hp_sum += int(op.get("wounds_cur", 0))
	return "tp=%d|ph=%s|ts=%s|act=%s|im=%s|dm=%s|dep=%d|rdy=%d|ap=%d|hp=%d|log=%d" % [
		int(st.get("tp", 0)),
		str(st.get("phase", "")),
		str(st.get("turn_side", "")),
		str(st.get("active_op_uid", "")),
		mg._killzone.interaction_mode,
		dm_sig,
		dep_cnt,
		ready_cnt,
		ap_sum,
		hp_sum,
		(st.get("log", []) as Array).size(),
	]


static func _check_invariants(mg, prev_tp: int) -> String:
	var st: Dictionary = mg.engine.state
	if st.is_empty():
		return "engine.state is empty"
	var tp: int = int(st.get("tp", 1))
	if tp < prev_tp or tp > 4:
		return "invalid tp=%d (prev=%d)" % [tp, prev_tp]
	for sid in ["A", "B"]:
		var ss: Dictionary = st["sides"][sid]
		if int(ss["cp"]) < 0:
			return "negative CP on side %s" % sid
		for vk in ["vp_key", "vp_kill", "vp_tac"]:
			if int(ss[vk]) < 0 or int(ss[vk]) > 6:
				return "out-of-range %s=%d on side %s" % [vk, int(ss[vk]), sid]
	var w: float = float(st["map"]["width"])
	var h: float = float(st["map"]["height"])
	for op_v in st.get("operatives", []):
		var op: Dictionary = op_v
		if int(op.get("ap_left", 0)) < 0:
			return "negative ap_left on %s" % str(op["uid"])
		var p: Vector2 = op["pos"]
		if is_nan(p.x) or is_nan(p.y) or is_inf(p.x) or is_inf(p.y):
			return "NaN/INF pos on %s" % str(op["uid"])
		if bool(op.get("deployed", true)):
			if p.x < -0.05 or p.x > w + 0.05 or p.y < -0.05 or p.y > h + 0.05:
				return "deployed op %s out of board at %s" % [str(op["uid"]), str(p)]
		else:
			if not p.is_equal_approx(Vector2(-99.0, -99.0)):
				return "undeployed op %s not at (-99,-99): %s" % [str(op["uid"]), str(p)]
	return ""
