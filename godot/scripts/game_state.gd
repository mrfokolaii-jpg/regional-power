extends Node
## Global progress, character, XP

const CHAR_NAMES := ["آرش", "سارا", "رستم", "کاوه"]
const CHAR_ROLES := ["متوازن", "سریع", "قدرت", "چابک"]
const CHAR_COLORS := [Color(0.27, 0.59, 0.86), Color(0.86, 0.27, 0.35), Color(0.86, 0.67, 0.2), Color(0.35, 0.78, 0.47)]
const CHAR_PWR := [3, 2, 5, 3]
const CHAR_SPD := [4, 5, 2, 5]
const CHAR_HP := [100, 90, 125, 95]

var unlocked_stage: int = 1
var xp: int = 0
var selected_char: int = 0
var current_stage: int = 1

func _ready() -> void:
	_load()

func _load() -> void:
	var cfg := ConfigFile.new()
	if cfg.load("user://save.cfg") == OK:
		unlocked_stage = int(cfg.get_value("p", "unlocked", 1))
		xp = int(cfg.get_value("p", "xp", 0))
		selected_char = int(cfg.get_value("p", "char", 0))

func save() -> void:
	var cfg := ConfigFile.new()
	cfg.set_value("p", "unlocked", unlocked_stage)
	cfg.set_value("p", "xp", xp)
	cfg.set_value("p", "char", selected_char)
	cfg.save("user://save.cfg")

func on_stage_win(stage_n: int, gain: int) -> void:
	xp += gain
	if stage_n >= unlocked_stage:
		unlocked_stage = mini(100, stage_n + 1)
	save()
