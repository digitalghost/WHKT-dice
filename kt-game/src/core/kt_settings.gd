class_name KTSettings
extends RefCounted

## Player-facing options persisted to user://settings.cfg.
## Keep this tiny: every key needs a UI control in the settings panel.

const PATH := "user://settings.cfg"

static var _cfg: ConfigFile = null

const DEFAULTS := {
	"combat/rule_step_pause": 0.3,   # seconds to hold on each rule trigger during dice resolution (0 = off)
	"combat/kill_cam": true,
	"audio/sfx_db": -4.0,
	"audio/ambience_db": -22.0,
	"hud/show_inch_grid": true,
	"hud/compact_tags": true,
	"board/mini_style": "model",         # "model" = painted 3D miniature, "standee" = acrylic card token
}


static func _ensure() -> void:
	if _cfg != null:
		return
	_cfg = ConfigFile.new()
	_cfg.load(PATH) # missing file is fine


static func get_value(key: String):
	_ensure()
	var parts := key.split("/")
	return _cfg.get_value(parts[0], parts[1], DEFAULTS.get(key))


static func set_value(key: String, v) -> void:
	_ensure()
	var parts := key.split("/")
	_cfg.set_value(parts[0], parts[1], v)
	_cfg.save(PATH)


static func rule_step_pause() -> float:
	return float(get_value("combat/rule_step_pause"))
