extends CharacterBody3D

enum Archetype { GRUNT, RUSHER, SHOOTER }

@export var archetype: Archetype = Archetype.GRUNT
@export var max_health := 50
@export var move_speed := 3.2
@export var detect_range := 14.0
@export var attack_range := 2.0
@export var damage := 10

var health: int
var player: Node3D
var state := "patrol"
var reaction := 0.0
var attack_cd := 0.0
var patrol_origin: Vector3
var patrol_target: Vector3
var gravity: float = ProjectSettings.get_setting("physics/3d/default_gravity")

func _ready() -> void:
	health = max_health
	add_to_group("enemies")
	patrol_origin = global_position
	_pick_patrol()
	match archetype:
		Archetype.RUSHER:
			move_speed = 4.5
			attack_range = 2.3
			damage = 14
		Archetype.SHOOTER:
			move_speed = 2.8
			detect_range = 18.0
			damage = 9

func _physics_process(delta: float) -> void:
	if health <= 0:
		return
	if not is_on_floor():
		velocity.y -= gravity * delta
	if player == null:
		player = get_tree().get_first_node_in_group("player") as Node3D
	attack_cd = maxf(0.0, attack_cd - delta)
	reaction = maxf(0.0, reaction - delta)
	var dist := 9999.0
	if player:
		dist = global_position.distance_to(player.global_position)
	match state:
		"patrol":
			_do_patrol(delta)
			if player and dist < detect_range:
				reaction = randf_range(0.15, 0.4)
				state = "alert"
		"alert":
			velocity.x = 0
			velocity.z = 0
			if reaction <= 0.0:
				state = "chase"
		"chase":
			if player == null:
				state = "patrol"
			elif dist <= attack_range:
				state = "attack"
			elif dist > detect_range * 1.6:
				state = "patrol"
			else:
				_move_toward(player.global_position, delta)
		"attack":
			if player == null or dist > attack_range * 1.4:
				state = "chase"
			else:
				velocity.x = 0
				velocity.z = 0
				look_at(Vector3(player.global_position.x, global_position.y, player.global_position.z), Vector3.UP)
				if attack_cd <= 0.0:
					attack_cd = 1.0
					if player.has_method("apply_damage"):
						player.apply_damage(damage)
	move_and_slide()

func _do_patrol(_d: float) -> void:
	if global_position.distance_to(patrol_target) < 0.8:
		_pick_patrol()
	else:
		_move_toward(patrol_target, _d)

func _pick_patrol() -> void:
	patrol_target = patrol_origin + Vector3(randf_range(-6, 6), 0, randf_range(-6, 6))

func _move_toward(target: Vector3, _d: float) -> void:
	var dir := (target - global_position)
	dir.y = 0
	if dir.length() > 0.1:
		dir = dir.normalized()
		velocity.x = dir.x * move_speed
		velocity.z = dir.z * move_speed
		look_at(global_position + dir, Vector3.UP)

func apply_damage(amount: int) -> void:
	health -= amount
	if state == "patrol":
		reaction = 0.1
		state = "alert"
	if health <= 0:
		var stage = get_tree().get_first_node_in_group("stage_controller")
		if stage and stage.has_method("on_enemy_killed"):
			stage.on_enemy_killed()
		queue_free()
