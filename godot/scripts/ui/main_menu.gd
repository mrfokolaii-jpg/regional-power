extends Control

@onready var city_label: Label = $Panel/CityLabel
@onready var brief_label: Label = $Panel/BriefLabel
@onready var progress_label: Label = $Panel/ProgressLabel
@onready var play_btn: Button = $Panel/PlayButton
@onready var prev_btn: Button = $Panel/PrevButton
@onready var next_btn: Button = $Panel/NextButton
@onready var solo_btn: Button = $Panel/SoloButton
@onready var coop_btn: Button = $Panel/CoopButton

var preview: int = 1

func _ready() -> void:
	preview = GameState.current_level
	_refresh()
	play_btn.pressed.connect(_on_play)
	prev_btn.pressed.connect(func(): preview = maxi(1, preview - 1); _refresh())
	next_btn.pressed.connect(func(): preview = mini(100, preview + 1); _refresh())
	solo_btn.pressed.connect(func(): GameState.mode_solo = true; _refresh())
	coop_btn.pressed.connect(func(): GameState.mode_solo = false; _refresh())

func _refresh() -> void:
	var s: Dictionary = StageDB.get_stage(preview)
	var locked: bool = preview > GameState.current_level
	city_label.text = "مرحله %d — %s%s" % [preview, str(s.get("fa", "")), " [قفل]" if locked else ""]
	brief_label.text = str(s.get("brief", ""))
	progress_label.text = "پیشرفت: %d/100 | XP: %d | حالت: %s" % [
		GameState.current_level, GameState.xp, "تک‌نفره" if GameState.mode_solo else "تیمی"
	]
	play_btn.disabled = locked

func _on_play() -> void:
	GameState.play_stage = preview
	get_tree().change_scene_to_file("res://scenes/game.tscn")
