extends CanvasLayer

signal fire_pressed
signal melee_pressed
signal heavy_pressed
signal special_pressed
signal reload_pressed
signal jump_pressed
signal slide_pressed
signal interact_pressed
signal block_pressed

@onready var stick_base: Control = $Left/StickBase
@onready var stick_knob: Control = $Left/StickBase/Knob

var dragging := false
var stick_radius := 90.0
var move_axis: Vector2 = Vector2.ZERO

func _ready() -> void:
	if has_node("Right/BtnFire"):
		$Right/BtnFire.pressed.connect(func(): emit_signal("fire_pressed"))
	if has_node("Right/BtnMelee"):
		$Right/BtnMelee.pressed.connect(func(): emit_signal("melee_pressed"))
	if has_node("Right/BtnReload"):
		$Right/BtnReload.pressed.connect(func(): emit_signal("reload_pressed"))
	if has_node("Right/BtnJump"):
		$Right/BtnJump.pressed.connect(func(): emit_signal("jump_pressed"))
	if has_node("Right/BtnSlide"):
		$Right/BtnSlide.pressed.connect(func(): emit_signal("slide_pressed"))

func _input(event: InputEvent) -> void:
	if event is InputEventScreenTouch or event is InputEventScreenDrag or event is InputEventMouseButton or event is InputEventMouseMotion:
		_handle_stick(event)

func _handle_stick(event: InputEvent) -> void:
	var pos: Vector2
	var pressed := false
	if event is InputEventScreenTouch:
		pos = event.position
		pressed = event.pressed
		if not pressed and dragging:
			dragging = false
			move_axis = Vector2.ZERO
			_reset_knob()
			_push_axis()
			return
	elif event is InputEventScreenDrag:
		pos = event.position
		pressed = true
	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		pos = event.position
		pressed = event.pressed
		if not pressed and dragging:
			dragging = false
			move_axis = Vector2.ZERO
			_reset_knob()
			_push_axis()
			return
	elif event is InputEventMouseMotion and Input.is_mouse_button_pressed(MOUSE_BUTTON_LEFT):
		pos = event.position
		pressed = true
	else:
		return
	var vp := get_viewport().get_visible_rect().size
	if pos.x > vp.x * 0.45 and not dragging:
		return
	var center := stick_base.global_position + stick_base.size * 0.5
	if not dragging:
		if pressed and pos.distance_to(center) < stick_radius * 1.4:
			dragging = true
	if dragging and pressed:
		var delta := pos - center
		if delta.length() > stick_radius:
			delta = delta.normalized() * stick_radius
		stick_knob.position = (stick_base.size * 0.5) + delta - stick_knob.size * 0.5
		move_axis = delta / stick_radius
		move_axis.y = -move_axis.y
		_push_axis()

func _reset_knob() -> void:
	stick_knob.position = (stick_base.size - stick_knob.size) * 0.5

func _push_axis() -> void:
	var player := get_tree().get_first_node_in_group("player")
	if player:
		player.set_meta("v_move", move_axis)
