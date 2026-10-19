extends Node

func _ready() -> void:
	var mc_scene := load("res://scenes/mobile_controls.tscn")
	if mc_scene:
		var mc = mc_scene.instantiate()
		get_parent().add_child(mc)
		var player := get_tree().get_first_node_in_group("player")
		if player:
			mc.fire_pressed.connect(func():
				if player.has_method("try_fire"): player.try_fire())
			mc.melee_pressed.connect(func():
				if player.has_method("try_melee"): player.try_melee())
			mc.reload_pressed.connect(func():
				if player.has_method("reload"): player.reload())
			mc.jump_pressed.connect(func():
				if player.is_on_floor():
					player.velocity.y = player.jump_velocity)
			mc.slide_pressed.connect(func():
				if player.has_method("start_slide"): player.start_slide())
