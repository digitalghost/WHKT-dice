class_name KTSfx
extends Node

## Procedural Tactical Audio & UI Feedback Synthesizer for Kill Team HD-2D:
## Generates crisp sci-fi/grimdark PCM sound effects in memory at startup (zero external WAV assets needed)
## plus loads `dice_roll.mp3`, playing through a polyphonic AudioStreamPlayer pool!

static var _instance: KTSfx = null

var _players: Array[AudioStreamPlayer] = []
var _next_idx: int = 0
var _clips: Dictionary = {}
var _headless: bool = false
var _last_hover_ms: int = 0


static func ensure_attached(parent: Node) -> KTSfx:
	if _instance != null and is_instance_valid(_instance):
		return _instance
	var sfx := KTSfx.new()
	sfx.name = "KTSfx"
	parent.add_child(sfx)
	_instance = sfx
	return sfx


static func play(clip_name: String, vol_db: float = -4.0, pitch_jitter: float = 0.05) -> void:
	if _instance == null or not is_instance_valid(_instance):
		return
	_instance._play_internal(clip_name, vol_db, pitch_jitter)


static func play_hover() -> void:
	if _instance == null or not is_instance_valid(_instance):
		return
	var now_ms: int = Time.get_ticks_msec()
	if now_ms - _instance._last_hover_ms < 42:
		return
	_instance._last_hover_ms = now_ms
	_instance._play_internal("ui_hover", -11.0, 0.06)


func _ready() -> void:
	_headless = DisplayServer.get_name() == "headless"
	if _headless:
		return

	for i in range(10):
		var p := AudioStreamPlayer.new()
		add_child(p)
		_players.append(p)

	for dice_path in ["res://assets/audio/dice_roll.ogg", "res://assets/audio/dice_roll.mp3"]:
		if ResourceLoader.exists(dice_path):
			_clips["dice_roll"] = load(dice_path)
			break

	_clips["ui_hover"] = _synth_clip(0.032, 1)    # Crisp vox micro-tick
	_clips["ui_click"] = _synth_clip(0.075, 2)    # Metallic armor latch click
	_clips["card_lift"] = _synth_clip(0.060, 3)   # Parchment/holo card slide
	_clips["order_servo"] = _synth_clip(0.14, 4)  # Power-armor stance servo
	_clips["move_confirm"] = _synth_clip(0.13, 5) # Heavy ceramite boot step
	_clips["bolter"] = _synth_clip(0.24, 6)       # Heavy bolter bark
	_clips["sniper"] = _synth_clip(0.30, 7)       # Suppressed hyper-rail crack
	_clips["explosion"] = _synth_clip(0.42, 8)    # Armour-shattering boom
	_clips["slash"] = _synth_clip(0.22, 9)        # Chainsword rip / blade cleave
	_clips["parry"] = _synth_clip(0.26, 10)       # Crossed-blade metallic clang
	_clips["phase_horn"] = _synth_clip(0.48, 11)  # Deep Gothic brass chord
	_clips["dice_clack"] = _synth_clip(0.058, 12) # Tactile bone/ivory D6 felt-brass clack
	_clips["crit_chime"] = _synth_clip(0.28, 13)  # Golden Crit 6 harmonic chime

	# Recorded CC0 samples (assets/audio/LICENSE.md) override the procedural fallbacks when present,
	# so the synth clips still cover a fresh checkout before `sync_assets.py` / asset download ran.
	for clip_name in ["bolter", "sniper", "explosion", "kill_boom", "parry", "slash", "dice_clack",
			"phase_horn", "ui_click", "card_lift", "move_confirm"]:
		var p := "res://assets/audio/sfx_%s.ogg" % clip_name
		if ResourceLoader.exists(p):
			_clips[clip_name] = load(p)

	_start_ambience()


## Low-volume looping room-tone / machine hum under the whole match. Kept very quiet so it reads
## as "the table is in a place" rather than as music; replace `amb_loop.ogg` for a real score.
var _amb_player: AudioStreamPlayer = null

func _start_ambience() -> void:
	var p := "res://assets/audio/amb_loop.ogg"
	if not ResourceLoader.exists(p):
		return
	var stream: AudioStream = load(p)
	if stream is AudioStreamOggVorbis:
		(stream as AudioStreamOggVorbis).loop = true
	_amb_player = AudioStreamPlayer.new()
	_amb_player.stream = stream
	_amb_player.volume_db = float(KTSettings.get_value("audio/ambience_db"))
	_amb_player.autoplay = true
	add_child(_amb_player)
	_amb_player.play()


func _play_internal(clip_name: String, vol_db: float, pitch_jitter: float) -> void:
	if _headless or _players.is_empty() or not _clips.has(clip_name):
		return
	var stream: AudioStream = _clips[clip_name]
	if stream == null:
		return
	var p: AudioStreamPlayer = _players[_next_idx]
	_next_idx = (_next_idx + 1) % _players.size()
	p.stop()
	p.stream = stream
	var sfx_offset: float = float(KTSettings.get_value("audio/sfx_db")) - float(KTSettings.DEFAULTS["audio/sfx_db"])
	p.volume_db = vol_db + sfx_offset
	p.pitch_scale = randf_range(1.0 - pitch_jitter, 1.0 + pitch_jitter)
	p.play()


func _synth_clip(dur_sec: float, mode: int) -> AudioStreamWAV:
	var rate: int = 22050
	var count: int = maxi(64, int(dur_sec * float(rate)))
	var data := PackedByteArray()
	data.resize(count * 2)

	var rng := RandomNumberGenerator.new()
	rng.seed = 1337 + mode * 97
	var phase: float = 0.0
	var phase2: float = 0.0

	for i in range(count):
		var t: float = float(i) / float(rate)
		var u: float = float(i) / float(count)
		var env: float = pow(1.0 - u, 1.8)
		var sample: float = 0.0

		match mode:
			1: # ui_hover: crisp high vox tick
				var f1: float = lerpf(1650.0, 2350.0, u)
				phase += TAU * f1 / float(rate)
				sample = sin(phase) * pow(1.0 - u, 3.2) * 0.42
			2: # ui_click: dual-tone mechanical latch
				var f2: float = 920.0 if u < 0.32 else 1380.0
				phase += TAU * f2 / float(rate)
				var n2: float = rng.randf_range(-1.0, 1.0) * pow(1.0 - u, 5.0) * 0.35
				sample = (sin(phase) * 0.55 + n2) * pow(1.0 - u, 2.2) * 0.58
			3: # card_lift: soft airy whoosh + chime
				var f3: float = lerpf(520.0, 880.0, u)
				phase += TAU * f3 / float(rate)
				sample = (sin(phase) * 0.35 + rng.randf_range(-1.0, 1.0) * 0.18) * sin(u * PI) * 0.45
			4: # order_servo: sci-fi power armor frequency sweep
				var f4: float = lerpf(280.0, 640.0, u)
				phase += TAU * f4 / float(rate)
				phase2 += TAU * (f4 * 1.5) / float(rate)
				sample = (sin(phase) * 0.5 + sin(phase2) * 0.3) * sin(u * PI) * 0.55
			5: # move_confirm: low ceramite boot thud
				var f5: float = lerpf(165.0, 58.0, u)
				phase += TAU * f5 / float(rate)
				sample = (sin(phase) * 0.75 + rng.randf_range(-1.0, 1.0) * 0.25 * pow(1.0 - u, 3.0)) * env * 0.68
			6: # bolter: punchy kinetic bark (2 rapid thumps)
				var sub_u: float = fmod(u * 2.0, 1.0)
				var sub_e: float = pow(1.0 - sub_u, 2.4)
				var f6: float = lerpf(220.0, 65.0, sub_u)
				phase += TAU * f6 / float(rate)
				sample = (sin(phase) * 0.65 + rng.randf_range(-1.0, 1.0) * 0.55) * sub_e * 0.75
			7: # sniper: supersonic crack + energy tail
				var f7: float = lerpf(1800.0, 140.0, pow(u, 0.45))
				phase += TAU * f7 / float(rate)
				sample = (sin(phase) * 0.6 + rng.randf_range(-1.0, 1.0) * pow(1.0 - u, 2.8) * 0.6) * env * 0.75
			8: # explosion: deep sub-bass boom + debris crackle
				var f8: float = lerpf(135.0, 34.0, pow(u, 0.55))
				phase += TAU * f8 / float(rate)
				var rumble: float = rng.randf_range(-1.0, 1.0) * pow(1.0 - u, 1.3)
				sample = (sin(phase) * 0.72 + rumble * 0.58) * pow(1.0 - u, 1.25) * 0.82
			9: # slash: chainsword snarl + air cleave
				var f9: float = lerpf(420.0, 175.0, u) + sin(t * 95.0) * 48.0
				phase += TAU * f9 / float(rate)
				var saw: float = (fmod(phase / TAU, 1.0) * 2.0 - 1.0) * 0.45
				sample = (saw + rng.randf_range(-1.0, 1.0) * 0.42) * sin(pow(u, 0.6) * PI) * 0.72
			10: # parry: resonant steel ring clang!
				phase += TAU * 1180.0 / float(rate)
				phase2 += TAU * 1795.0 / float(rate)
				var ring: float = sin(phase) * 0.5 + sin(phase2) * 0.42 + sin(phase * 2.41) * 0.25
				var hit_n: float = rng.randf_range(-1.0, 1.0) * pow(1.0 - u, 6.0) * 0.6
				sample = (ring + hit_n) * pow(1.0 - u, 1.9) * 0.74
			11: # phase_horn: low Gothic fifth chord (D2 + A2)
				phase += TAU * 110.0 / float(rate)
				phase2 += TAU * 164.81 / float(rate)
				var swell: float = sin(u * PI)
				var brass_w: float = tanh((sin(phase) * 0.65 + sin(phase2) * 0.50) * 1.8)
				sample = brass_w * swell * 0.52
			12: # dice_clack: woody-ivory D6 impact knock
				var f12: float = lerpf(880.0, 320.0, pow(u, 0.5))
				phase += TAU * f12 / float(rate)
				phase2 += TAU * (f12 * 1.62) / float(rate)
				var transient: float = rng.randf_range(-1.0, 1.0) * pow(1.0 - u, 6.5) * 0.55
				sample = (sin(phase) * 0.55 + sin(phase2) * 0.30 + transient) * pow(1.0 - u, 3.0) * 0.72
			13: # crit_chime: golden imperial harmonic triad (A5 + E6)
				phase += TAU * 880.0 / float(rate)
				phase2 += TAU * 1318.5 / float(rate)
				sample = (sin(phase) * 0.52 + sin(phase2) * 0.38) * pow(1.0 - u, 1.8) * 0.56

		var pcm: int = clampi(int(sample * 32767.0), -32768, 32767)
		data[i * 2] = pcm & 0xFF
		data[i * 2 + 1] = (pcm >> 8) & 0xFF

	var wav := AudioStreamWAV.new()
	wav.format = AudioStreamWAV.FORMAT_16_BITS
	wav.mix_rate = rate
	wav.stereo = false
	wav.data = data
	return wav
