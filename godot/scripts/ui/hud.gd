extends CanvasLayer

@onready var hp_label: Label = $TopLeft/HPLabel
@onready var hp_bar: ProgressBar = $TopLeft/HPBar
@onready var xp_label: Label = $TopRight/XPLabel
@onready var obj_label: Label = $Objective
@onready var msg_label: Label = $Message

func _ready() -> void:
	var player := get_tree().get_first_node_in_group("player")
	if player:
		bind_player(player)
	var stage := get_tree().get_first_node_in_group("stage_controller")
	if stage:
		if stage.has_signal("message"):
			stage.message.connect(show_msg)
		if stage.has_signal("objective"):
			stage.objective.connect(set_objective)
	if has_node("TopRight/XPLabel"):
		xp_label.text = "XP %d" % GameState.xp

func bind_player(p: Node) -> void:
	if p.has_signal("health_changed"):
		p.health_changed.connect(_on_hp)
	if p.has_signal("ammo_changed"):
		p.ammo_changed.connect(_on_ammo)

func _on_hp(h: int, m: int) -> void:
	hp_label.text = "HP %d" % h
	hp_bar.max_value = m
	hp_bar.value = h

func _on_ammo(a: int, m: int) -> void:
	pass

func set_objective(t: String) -> void:
	obj_label.text = t

func show_msg(t: String) -> void:
	msg_label.text = t
	msg_label.visible = true
	get_tree().create_timer(4.0).timeout.connect(func(): msg_label.visible = false)
