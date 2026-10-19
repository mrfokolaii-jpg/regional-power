extends Node3D

@export var player_path: NodePath
@export var objective_marker: NodePath
@export var extract_marker: NodePath

var stage_num: int = 1
var info: Dictionary = {}
var kills: int = 0
var need_kills: int = 2
var phase: int = 0
var civilian_rescued: bool = false
var active: bool = true

signal message(text: String)
signal objective(text: String)
signal mission_done(success: bool, body: String)

func _ready() -> void:
	add_to_group("stage_controller")
	stage_num = GameState.play_stage
	info = StageDB.get_stage(stage_num)
	need_kills = int(info.get("enemies", 2))
	emit_signal("message", str(info.get("brief", "")))
	emit_signal("objective", "ورود به %s" % str(info.get("fa", "")))
	phase = 0

func _process(_d: float) -> void:
	if not active:
		return
	var player := get_node_or_null(player_path) as Node3D
	if player == null:
		return
	match phase:
		0:
			var obj := get_node_or_null(objective_marker) as Node3D
			if obj and player.global_position.distance_to(obj.global_position) < 3.5:
				phase = 1
				emit_signal("objective", "پاک‌سازی نیروهای پیاده دشمن")
				_spawn_enemies()
		1:
			if kills >= need_kills:
				if info.get("civilian", false):
					phase = 2
					emit_signal("objective", "غیرنظامی را محافظت کن")
				else:
					phase = 3
					emit_signal("objective", "به نقطهٔ استخراج برو")
		2:
			if civilian_rescued:
				phase = 3
				emit_signal("objective", "به نقطهٔ استخراج برو")
		3:
			var ext := get_node_or_null(extract_marker) as Node3D
			if ext and player.global_position.distance_to(ext.global_position) < 3.5:
				_win()

func _spawn_enemies() -> void:
	var parent := get_parent()
	var count := need_kills
	for i in count:
		var e := _make_enemy_runtime()
		parent.add_child(e)
		e.global_position = global_position + Vector3(randf_range(-8, 8), 0.5, randf_range(6, 14))
		if e.get("archetype") != null:
			e.archetype = i % 3

func _make_enemy_runtime() -> CharacterBody3D:
	var e := CharacterBody3D.new()
	e.set_script(load("res://scripts/enemy/enemy.gd"))
	var mesh := MeshInstance3D.new()
	var cap := CapsuleMesh.new()
	cap.radius = 0.35
	cap.height = 1.6
	mesh.mesh = cap
	e.add_child(mesh)
	var col := CollisionShape3D.new()
	var shape := CapsuleShape3D.new()
	shape.radius = 0.35
	shape.height = 1.6
	col.shape = shape
	e.add_child(col)
	return e

func on_enemy_killed() -> void:
	kills += 1
	if kills >= need_kills and phase == 1:
		emit_signal("message", "موج دشمن خنثی شد")

func rescue_civilian() -> void:
	civilian_rescued = true
	emit_signal("message", "غیرنظامی امن شد")

func _win() -> void:
	active = false
	var xp := int(info.get("xp", 100))
	GameState.add_xp(xp)
	GameState.unlock_next(stage_num)
	emit_signal("mission_done", true, "%s پاک‌سازی شد" % str(info.get("fa", "")))
	emit_signal("message", "ماموریت کامل شد")
