extends CharacterBody3D

@export var move_speed := 6.0
@export var jump_velocity := 5.5
@export var max_health := 100
@export var melee_damage := 18

var health: int
var gravity: float = ProjectSettings.get_setting("physics/3d/default_gravity")
var ammo: int = 30
var max_ammo: int = 30
var fire_cd: float = 0.0
var slide_timer: float = 0.0

signal died
signal health_changed(h: int, mx: int)
signal ammo_changed(a: int, mx: int)

func _ready() -> void:
	health = max_health
	emit_signal("health_changed", health, max_health)
	emit_signal("ammo_changed", ammo, max_ammo)

func _physics_process(delta: float) -> void:
	if not is_on_floor():
		velocity.y -= gravity * delta
	else:
		if Input.is_action_just_pressed("ui_accept") or Input.is_action_just_pressed("jump"):
			velocity.y = jump_velocity
	var input_dir := Vector2.ZERO
	input_dir.x = Input.get_axis("ui_left", "ui_right")
	input_dir.y = Input.get_axis("ui_up", "ui_down")
	if has_meta("v_move"):
		input_dir = get_meta("v_move")
	var direction := (transform.basis * Vector3(input_dir.x, 0, input_dir.y)).normalized()
	if slide_timer > 0.0:
		slide_timer -= delta
		velocity.x = -transform.basis.z.x * 10.0
		velocity.z = -transform.basis.z.z * 10.0
	elif direction:
		velocity.x = direction.x * move_speed
		velocity.z = direction.z * move_speed
	else:
		velocity.x = move_toward(velocity.x, 0, move_speed)
		velocity.z = move_toward(velocity.z, 0, move_speed)
	move_and_slide()
	if fire_cd > 0.0:
		fire_cd -= delta

func apply_damage(amount: int) -> void:
	health = maxi(0, health - amount)
	emit_signal("health_changed", health, max_health)
	if health <= 0:
		emit_signal("died")

func try_fire() -> void:
	if fire_cd > 0.0 or ammo <= 0:
		return
	ammo -= 1
	fire_cd = 0.18
	emit_signal("ammo_changed", ammo, max_ammo)
	var space := get_world_3d().direct_space_state
	var origin := global_position + Vector3.UP * 1.4
	var to := origin + (-transform.basis.z) * 40.0
	var q := PhysicsRayQueryParameters3D.create(origin, to)
	q.exclude = [self]
	var hit := space.intersect_ray(q)
	if hit and hit.collider and hit.collider.has_method("apply_damage"):
		hit.collider.apply_damage(20)

func try_melee() -> void:
	for b in get_tree().get_nodes_in_group("enemies"):
		if global_position.distance_to(b.global_position) < 2.4:
			if b.has_method("apply_damage"):
				b.apply_damage(melee_damage)

func reload() -> void:
	ammo = max_ammo
	emit_signal("ammo_changed", ammo, max_ammo)

func start_slide() -> void:
	if is_on_floor():
		slide_timer = 0.45
