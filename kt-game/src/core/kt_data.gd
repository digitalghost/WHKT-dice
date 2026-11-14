class_name KTData
extends RefCounted

## Static loader & catalog for synced Kill Team data, textures, Chapter Tactics, and Killzone maps.

const BOARD_WIDTH_INCHES: float = 30.0
const BOARD_HEIGHT_INCHES: float = 22.0
const PIXELS_PER_INCH: float = 56.0

const CHAPTER_TACTICS: Array[Dictionary] = [
	{
		"id": "aggressive",
		"name": "1. 猛攻",
		"short": "猛攻",
		"desc": "近战武器获得【撕裂】（保留暴击时可将 1 枚普通成功升为暴击）。"
	},
	{
		"id": "dueller",
		"name": "2. 决斗家",
		"short": "决斗家",
		"desc": "近战中可用 1 枚普通成功格挡对手的暴击（除非敌方武器带【残暴】）。"
	},
	{
		"id": "resolute",
		"name": "3. 坚决",
		"short": "坚决",
		"desc": "忽略对该特工 APL 的任何修正，且免疫敌方近战【震荡】效果。"
	},
	{
		"id": "stealthy",
		"name": "4. 隐匿",
		"short": "隐匿",
		"desc": "受射击享有掩护时，可额外保留 1 枚普通掩护或将掩护升级为暴击。"
	},
	{
		"id": "mobile",
		"name": "5. 机动",
		"short": "机动",
		"desc": "【后撤】少耗 1AP（仅需 1AP）；且允许从敌方控制范围内发起【冲锋】。"
	},
	{
		"id": "hardy",
		"name": "6. 强健",
		"short": "强健",
		"desc": "被射击掷防御骰时，5+ 即视为暴击防御成功！"
	},
	{
		"id": "sharpshooter",
		"name": "7. 神射手",
		"short": "神射手",
		"desc": "本激活未执行转移/冲锋/后撤时，爆矢武器获得【精准 1】与【严重】。"
	},
	{
		"id": "siege_specialist",
		"name": "8. 攻城战专家",
		"short": "攻城专家",
		"desc": "全部远程武器获得【集中】（无视敌方普通掩护），且近战免疫敌方围攻协助。"
	},
]

static var _teams_cache: Dictionary = {}
static var _texture_cache: Dictionary = {}


static func get_team(team_id: String) -> Dictionary:
	if _teams_cache.has(team_id):
		return _teams_cache[team_id]
	var path := "res://data/teams/%s.json" % team_id
	if not FileAccess.file_exists(path):
		push_error("Missing synced team JSON: " + path)
		return {}
	var raw := FileAccess.get_file_as_string(path)
	var parsed = JSON.parse_string(raw)
	if typeof(parsed) == TYPE_DICTIONARY:
		_teams_cache[team_id] = parsed
		return parsed
	return {}


static func load_tex(res_path: String) -> Texture2D:
	if res_path.is_empty():
		return null
	if _texture_cache.has(res_path):
		return _texture_cache[res_path]
	if ResourceLoader.exists(res_path):
		var tex := load(res_path) as Texture2D
		if tex != null:
			_texture_cache[res_path] = tex
			return tex
	# Fallback direct Image load for runtime-synced PNGs before editor import
	var global_path := ProjectSettings.globalize_path(res_path)
	if FileAccess.file_exists(global_path):
		var img := Image.load_from_file(global_path)
		if img != null and not img.is_empty():
			var itex := ImageTexture.create_from_image(img)
			_texture_cache[res_path] = itex
			return itex
	return null


static func get_ploys(team_id: String, category: String) -> Array[Dictionary]:
	var team := get_team(team_id)
	var out: Array[Dictionary] = []
	for entry in team.get("rule_entries", []):
		if str(entry.get("category", "")) == category:
			out.append(entry)
	return out


static func create_volkus_killzone() -> Dictionary:
	## Official-scale 30" x 22" symmetrical Killzone Volkus layout:
	## - Side B (Plague Marines) deploys along North Drop Zone (y: 0.0" .. 3.2")
	## - Side A (Angels of Death) deploys along South Drop Zone (y: 18.8" .. 22.0")
	## - 3 Central Objectives across the midline (y = 11.0")
	## - Two 2-story Imperial Strongholds with Heavy walls + Vantage (+3.5") decks
	## - Midfield Heavy Ruin Pillars (Obscuring lanes) & Light Rubble Barricades
	return {
		"id": "killzone_volkus_01",
		"name": "杀戮区：沃库斯废墟 · 认证行动 #1",
		"width": BOARD_WIDTH_INCHES,
		"height": BOARD_HEIGHT_INCHES,
		"drop_zone_a": Rect2(0.0, 18.8, 30.0, 3.2),
		"drop_zone_b": Rect2(0.0, 0.0, 30.0, 3.2),
		"objectives": [
			{"id": "obj_1", "name": "1号信标 · 西翼军械库", "pos": Vector2(6.5, 11.0)},
			{"id": "obj_2", "name": "2号信标 · 帝国圣坛中枢", "pos": Vector2(15.0, 11.0)},
			{"id": "obj_3", "name": "3号信标 · 东翼反应堆", "pos": Vector2(23.5, 11.0)},
		],
		"terrains": [
			# --- South-West Imperial Stronghold (Vantage Deck + Heavy South/West Shield Walls) ---
			{
				"id": "vantage_sw",
				"name": "西南堡垒二层露台",
				"kind": "vantage",
				"elevation": 3.5,
				"rect": Rect2(4.2, 14.0, 4.8, 3.4),
				"ramp_side": "south",
			},
			{
				"id": "heavy_sw_north",
				"name": "西南堡垒重型胸墙",
				"kind": "heavy_wall",
				"elevation": 4.2,
				"blocks_los_ground": true,
				"rect": Rect2(4.2, 13.55, 3.2, 0.50),
			},
			# --- North-East Chaos Ruin Stronghold (Symmetrical Vantage Deck + Heavy Wall) ---
			{
				"id": "vantage_ne",
				"name": "东北废墟二层高台",
				"kind": "vantage",
				"elevation": 3.5,
				"rect": Rect2(21.0, 4.6, 4.8, 3.4),
				"ramp_side": "north",
			},
			{
				"id": "heavy_ne_south",
				"name": "东北废墟重型残垣",
				"kind": "heavy_wall",
				"elevation": 4.2,
				"blocks_los_ground": true,
				"rect": Rect2(22.6, 7.95, 3.2, 0.50),
			},
			# --- Midfield Heavy Ruin Walls (Create true Obscured & flanking lanes) ---
			{
				"id": "heavy_mid_west",
				"name": "西侧重型哥特断墙",
				"kind": "heavy_low",
				"elevation": 2.2,
				"blocks_los_ground": false,
				"rect": Rect2(10.6, 8.2, 1.3, 3.2),
			},
			{
				"id": "heavy_mid_east",
				"name": "东侧重型哥特断墙",
				"kind": "heavy_low",
				"elevation": 2.2,
				"blocks_los_ground": false,
				"rect": Rect2(18.1, 10.6, 1.3, 3.2),
			},
			{
				"id": "heavy_pillar_nw",
				"name": "西北重型装甲掩体",
				"kind": "heavy_wall",
				"elevation": 4.0,
				"blocks_los_ground": true,
				"rect": Rect2(7.0, 5.4, 2.8, 0.85),
			},
			{
				"id": "heavy_pillar_se",
				"name": "东南重型装甲掩体",
				"kind": "heavy_wall",
				"elevation": 4.0,
				"blocks_los_ground": true,
				"rect": Rect2(20.2, 15.75, 2.8, 0.85),
			},
			# --- Light Cover Barricades & Rubble ---
			{
				"id": "light_west_obj",
				"name": "西翼轻型掩体墙",
				"kind": "light",
				"elevation": 1.0,
				"rect": Rect2(4.8, 9.4, 2.4, 0.45),
			},
			{
				"id": "light_east_obj",
				"name": "东翼轻型掩体墙",
				"kind": "light",
				"elevation": 1.0,
				"rect": Rect2(22.8, 12.15, 2.4, 0.45),
			},
			{
				"id": "light_center_south",
				"name": "中枢南侧铁丝沙袋掩体",
				"kind": "light",
				"elevation": 1.0,
				"rect": Rect2(13.4, 13.7, 3.2, 0.45),
			},
			{
				"id": "light_center_north",
				"name": "中枢北侧铁丝沙袋掩体",
				"kind": "light",
				"elevation": 1.0,
				"rect": Rect2(13.4, 7.85, 3.2, 0.45),
			},
		],
		# Balanced tactical starting positions inside each side's Drop Zone
		"spawn_a": [
			Vector2(5.4, 19.9),   # Sniper / Gunner near SW Vantage
			Vector2(8.6, 20.1),   # Grenadier
			Vector2(13.5, 19.8),  # Leader (Captain / Sergeant)
			Vector2(16.8, 19.9),  # Assault Warrior
			Vector2(21.2, 19.8),  # Heavy Gunner behind SE bunker lane
			Vector2(24.6, 20.1),  # Gunner
		],
		"spawn_b": [
			Vector2(5.4, 2.1),
			Vector2(9.2, 2.0),
			Vector2(13.6, 2.1),
			Vector2(16.8, 2.0),
			Vector2(21.6, 2.1),
			Vector2(24.6, 1.9),
		],
	}
