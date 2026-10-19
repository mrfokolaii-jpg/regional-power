extends Node

var current_level: int = 1
var play_stage: int = 1
var xp: int = 0
var reputation: int = 0
var selected_faction: int = 0
var mode_solo: bool = true

const SAVE_PATH := "user://regional_power.save"
const FACTIONS_FA := ["ایران", "روسیه", "چین", "کره شمالی"]

func _ready() -> void:
	load_game()

func save_game() -> void:
	var f := FileAccess.open(SAVE_PATH, FileAccess.WRITE)
	if f:
		f.store_var({"level": current_level, "xp": xp, "rep": reputation, "faction": selected_faction})

func load_game() -> void:
	if not FileAccess.file_exists(SAVE_PATH):
		return
	var f := FileAccess.open(SAVE_PATH, FileAccess.READ)
	if f:
		var d = f.get_var()
		if typeof(d) == TYPE_DICTIONARY:
			current_level = int(d.get("level", 1))
			xp = int(d.get("xp", 0))
			reputation = int(d.get("rep", 0))
			selected_faction = int(d.get("faction", 0))

func add_xp(amount: int) -> void:
	xp += amount
	save_game()

func unlock_next(stage_num: int) -> void:
	if stage_num >= current_level:
		current_level = mini(100, stage_num + 1)
		save_game()
