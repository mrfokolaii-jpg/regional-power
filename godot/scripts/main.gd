extends Node2D
## Regional Power — complete mobile beat-em-up (menu / char / 100 stages)

enum Mode { MENU, CHAR, PLAY, RESULT }

var mode: Mode = Mode.MENU
var preview: int = 1
var stages: Array = []

var php: int = 100
var max_hp: int = 100
var kills: int = 0
var need: int = 3
var phase: int = 0
var weapon: int = 0
var ammo: int = 0
var city: String = ""
var story: String = ""
var win: bool = false
var msg: String = ""
var msg_t: float = 0.0
var auto_next_t: float = 0.0
var shake: float = 0.0

var px: float = 160.0
var py: float = 520.0
var facing: float = 1.0
var on_ground: bool = true
var jump_v: float = 0.0
var cam_x: float = 0.0
var move_x: float = 0.0
var obj_x: float = 900.0

var enemies: Array = []
var bullets: Array = []
var fx: Array = []

var punch_cd: float = 0.0
var kick_cd: float = 0.0
var jump_cd: float = 0.0
var special_cd: float = 0.0
var shielding: bool = false

var stick_center: Vector2
var stick_knob: Vector2
var stick_r: float = 90.0
var stick_id: int = -1
var btn_punch: Rect2
var btn_kick: Rect2
var btn_jump: Rect2
var btn_slide: Rect2
var btn_special: Rect2
var btn_shield: Rect2

func _ready() -> void:
	stages = StageDB.all()
	preview = GameState.unlocked_stage
	stick_center = Vector2(120, 600)
	stick_knob = stick_center
	_layout_buttons()
	set_process(true)
	set_process_input(true)

func _layout_buttons() -> void:
	var vs := get_viewport_rect().size
	var rx := vs.x - 120.0
	var ry := vs.y - 130.0
	btn_punch = Rect2(rx - 40, ry - 40, 80, 80)
	btn_kick = Rect2(rx - 130, ry - 20, 70, 70)
	btn_jump = Rect2(rx - 50, ry - 130, 66, 66)
	btn_slide = Rect2(rx - 140, ry - 100, 62, 62)
	btn_special = Rect2(rx + 10, ry - 140, 60, 60)
	btn_shield = Rect2(rx - 90, ry - 170, 56, 56)
	stick_center = Vector2(120, vs.y - 120)
	if stick_id < 0:
		stick_knob = stick_center

func _process(dt: float) -> void:
	if msg_t > 0.0: msg_t -= dt
	if shake > 0.0: shake -= dt * 8.0
	if punch_cd > 0.0: punch_cd -= dt
	if kick_cd > 0.0: kick_cd -= dt
	if jump_cd > 0.0: jump_cd -= dt
	if special_cd > 0.0: special_cd -= dt
	for i in range(fx.size() - 1, -1, -1):
		fx[i].t -= dt
		if fx[i].t <= 0.0: fx.remove_at(i)
	if mode == Mode.RESULT and win and auto_next_t > 0.0:
		auto_next_t -= dt
		if auto_next_t <= 0.0:
			auto_next_t = 0.0
			if preview < 100 and preview + 1 <= GameState.unlocked_stage:
				_start_stage(preview + 1)
			else:
				mode = Mode.MENU
	if mode == Mode.PLAY:
		_update_play(dt)
	queue_redraw()

func _update_play(dt: float) -> void:
	var spd: float = 195.0 + GameState.CHAR_SPD[GameState.selected_char] * 20.0
	px = clampf(px + move_x * spd * dt, 60.0, 2200.0)
	if move_x > 0.12: facing = 1.0
	elif move_x < -0.12: facing = -1.0
	if not on_ground:
		jump_v += 1450.0 * dt
		py += jump_v * dt
		if py >= 520.0:
			py = 520.0
			jump_v = 0.0
			on_ground = true
	else:
		py = 520.0
	cam_x = lerpf(cam_x, px - get_viewport_rect().size.x * 0.4, minf(1.0, dt * 5.0))
	for i in range(bullets.size() - 1, -1, -1):
		var b: Dictionary = bullets[i]
		b.x += b.vx * dt
		b.life -= dt
		var hit := false
		for e in enemies:
			if e.hp > 0 and absf(b.x - e.x) < 28.0 and absf(b.y - (e.y - 40.0)) < 40.0:
				e.hp -= b.dmg
				hit = true
				_fx(e.x, e.y - 40.0, 0.22, 1)
				if e.hp <= 0:
					e.hp = 0
					kills += 1
					_fx(e.x, e.y - 20.0, 0.4, 2)
				break
		if hit or b.life <= 0.0:
			bullets.remove_at(i)
		else:
			bullets[i] = b
	if phase == 0 and absf(px - obj_x) < 50.0:
		phase = 1
		_spawn()
		_toast("همه دشمنان را شکست بده!")
	elif phase == 1:
		for e in enemies:
			if e.hp <= 0: continue
			var dx: float = px - e.x
			if absf(dx) > 3.0:
				e.x += signf(dx) * e.spd * dt
			e.y = 520.0
			if not shielding and absf(e.x - px) < 40.0 and absf(e.y - py) < 30.0:
				if Time.get_ticks_msec() - e.last_hit > 600:
					e.last_hit = Time.get_ticks_msec()
					php = maxi(0, php - e.dmg)
					shake = 0.28
					_fx(px, py - 50.0, 0.2, 1)
					if php <= 0:
						mode = Mode.RESULT
						win = false
						auto_next_t = 0.0
		for i in range(enemies.size() - 1, -1, -1):
			if enemies[i].hp <= 0: enemies.remove_at(i)
		if kills >= need and enemies.is_empty():
			mode = Mode.RESULT
			win = true
			var gain: int = int(StageDB.get_stage(preview).get("xp", 100))
			GameState.on_stage_win(preview, gain)
			auto_next_t = 2.5
			_toast("پیروزی! مرحله بعد...")
	shielding = false

func _spawn() -> void:
	enemies.clear()
	for i in range(need):
		enemies.append({
			"x": obj_x + 80.0 + i * 55.0 + randf() * 40.0,
			"y": 520.0,
			"hp": 35 + preview * 3,
			"max": 35 + preview * 3,
			"spd": 65.0 + preview * 1.4,
			"dmg": 7 + preview / 10,
			"type": i % 3,
			"weapon": 2 if preview >= 40 else (1 if preview >= 15 else 0),
			"last_hit": 0
		})

func _start_stage(n: int) -> void:
	preview = n
	GameState.current_stage = n
	var s: Dictionary = StageDB.get_stage(n)
	city = str(s.get("fa", "شهر"))
	need = maxi(2, int(s.get("enemies", 3)))
	story = str(s.get("brief", "پاک‌سازی"))
	weapon = 2 if n >= 40 else (1 if n >= 15 else 0)
	ammo = 18 if weapon == 2 else 0
	max_hp = GameState.CHAR_HP[GameState.selected_char]
	php = max_hp
	kills = 0
	phase = 0
	px = 160.0
	py = 520.0
	obj_x = 900.0
	enemies.clear()
	bullets.clear()
	fx.clear()
	on_ground = true
	jump_v = 0.0
	auto_next_t = 0.0
	mode = Mode.PLAY
	_toast("%s — %s" % [city, story])

func _toast(m: String) -> void:
	msg = m
	msg_t = 3.0

func _fx(x: float, y: float, t: float, kind: int) -> void:
	fx.append({"x": x, "y": y, "t": t, "kind": kind})

func _draw() -> void:
	_layout_buttons()
	match mode:
		Mode.MENU: _draw_menu()
		Mode.CHAR: _draw_char()
		Mode.PLAY: _draw_play()
		Mode.RESULT: _draw_result()

func _draw_menu() -> void:
	var vs := get_viewport_rect().size
	draw_rect(Rect2(Vector2.ZERO, vs), Color(0.03, 0.05, 0.09))
	for i in range(14):
		var bx := i * (vs.x / 13.0)
		draw_rect(Rect2(bx, vs.y * 0.4 + (i % 4) * 25.0, vs.x / 14.0, vs.y), Color(0.05, 0.08, 0.12))
	draw_rect(Rect2(36, 36, vs.x - 72, vs.y - 72), Color(0, 0, 0, 0.55))
	draw_string(ThemeDB.fallback_font, Vector2(64, 100), "Regional Power", HORIZONTAL_ALIGNMENT_LEFT, -1, 42, Color(0.86, 0.72, 0.31))
	draw_string(ThemeDB.fallback_font, Vector2(64, 140), "داستان پاک‌سازی ۱۰۰ شهر — Godot", HORIZONTAL_ALIGNMENT_LEFT, -1, 20, Color(0.7, 0.75, 0.8))
	draw_string(ThemeDB.fallback_font, Vector2(64, 175), "پیشرفت %d/100    XP %d" % [GameState.unlocked_stage, GameState.xp], HORIZONTAL_ALIGNMENT_LEFT, -1, 20, Color(0.7, 0.75, 0.8))
	var ci := GameState.selected_char
	_draw_fighter(vs.x - 160, vs.y * 0.55, GameState.CHAR_COLORS[ci], true, 2.2, false, ci)
	draw_string(ThemeDB.fallback_font, Vector2(vs.x - 220, vs.y * 0.55 - 130), GameState.CHAR_NAMES[ci], HORIZONTAL_ALIGNMENT_LEFT, -1, 24, GameState.CHAR_COLORS[ci])
	var name := "%d — %s" % [preview, StageDB.get_stage(preview).get("fa", "شهر")]
	var lock := preview > GameState.unlocked_stage
	draw_string(ThemeDB.fallback_font, Vector2(64, 240), name + (" [قفل]" if lock else ""), HORIZONTAL_ALIGNMENT_LEFT, -1, 28, Color.GRAY if lock else Color.WHITE)
	draw_string(ThemeDB.fallback_font, Vector2(64, 275), str(StageDB.get_stage(preview).get("brief", "")), HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color(0.7, 0.75, 0.8))
	_btn(Rect2(vs.x * 0.05, vs.y - 120, 130, 52), "قبلی")
	_btn(Rect2(vs.x * 0.25, vs.y - 120, 150, 52), "شخصیت")
	_btn(Rect2(vs.x * 0.48, vs.y - 120, 160, 52), "قفل" if lock else "شروع")
	_btn(Rect2(vs.x * 0.72, vs.y - 120, 130, 52), "بعدی")

func _draw_char() -> void:
	var vs := get_viewport_rect().size
	draw_rect(Rect2(Vector2.ZERO, vs), Color(0.04, 0.06, 0.12))
	draw_string(ThemeDB.fallback_font, Vector2(vs.x * 0.5 - 80, 55), "انتخاب مبارز", HORIZONTAL_ALIGNMENT_LEFT, -1, 36, Color(0.86, 0.72, 0.31))
	var cell_w := (vs.x - 60.0) / 4.0
	for i in range(4):
		var x := 30.0 + i * cell_w
		var sel := i == GameState.selected_char
		draw_rect(Rect2(x, 90, cell_w - 14, vs.y - 210), Color(0.16, 0.22, 0.14) if sel else Color(0.07, 0.09, 0.14))
		if sel:
			draw_rect(Rect2(x, 90, cell_w - 14, vs.y - 210), GameState.CHAR_COLORS[i], false, 3.0)
		_draw_fighter(x + cell_w * 0.5 - 7, vs.y * 0.52, GameState.CHAR_COLORS[i], true, 1.8, false, i)
		draw_string(ThemeDB.fallback_font, Vector2(x + cell_w * 0.5 - 30, vs.y - 160), GameState.CHAR_NAMES[i], HORIZONTAL_ALIGNMENT_LEFT, -1, 22, Color.WHITE)
		draw_string(ThemeDB.fallback_font, Vector2(x + cell_w * 0.5 - 30, vs.y - 138), GameState.CHAR_ROLES[i], HORIZONTAL_ALIGNMENT_LEFT, -1, 14, GameState.CHAR_COLORS[i])
	_btn(Rect2(vs.x * 0.5 - 90, vs.y - 48, 180, 40), "تأیید")

func _draw_play() -> void:
	var vs := get_viewport_rect().size
	draw_rect(Rect2(Vector2.ZERO, vs), Color(0.06, 0.1, 0.16))
	var sx := (randf() - 0.5) * 14.0 * shake if shake > 0.0 else 0.0
	draw_set_transform(Vector2(-cam_x + sx, -60), 0, Vector2.ONE)
	draw_rect(Rect2(cam_x - 80, 540, vs.x + 160, 200), Color(0.12, 0.14, 0.17))
	for i in range(28):
		draw_rect(Rect2(i * 95.0, 552, 55, 8), Color(0.2, 0.21, 0.24))
	for i in range(18):
		var bx := i * 150.0
		var bh := 160 + (i * 41) % 140
		draw_rect(Rect2(bx, 540 - bh, 110, bh), Color(0.07 + (i % 4) * 0.02, 0.09, 0.14))
		for wy in range(5):
			for wx in range(2):
				draw_rect(Rect2(bx + 18 + wx * 40, 540 - bh + 18 + wy * 28, 18, 14), Color(1, 0.82, 0.35, 0.35))
	if phase == 0:
		draw_circle(Vector2(obj_x, 520), 45, Color(1, 0.78, 0.16, 0.28))
		draw_string(ThemeDB.fallback_font, Vector2(obj_x - 50, 460), "ورود به منطقه", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color(1, 0.82, 0.24))
	for e in enemies:
		if e.hp <= 0: continue
		var col := Color(0.59, 0.18, 0.18) if e.type == 1 else (Color(0.18, 0.27, 0.59) if e.type == 2 else Color(0.43, 0.33, 0.18))
		_draw_fighter(e.x, e.y, col, e.x < px, 1.05, e.weapon > 0, -1)
		var r: float = float(e.hp) / float(e.max)
		draw_rect(Rect2(e.x - 26, e.y - 100, 52, 8), Color(0, 0, 0, 0.7))
		draw_rect(Rect2(e.x - 26, e.y - 100, 52 * r, 8), Color(0.27, 0.78, 0.39) if r > 0.35 else Color(0.86, 0.2, 0.2))
	_draw_fighter(px, py, GameState.CHAR_COLORS[GameState.selected_char], facing > 0, 1.25, weapon > 0, GameState.selected_char)
	for b in bullets:
		draw_circle(Vector2(b.x, b.y), 5, Color(1, 0.9, 0.35))
	for f in fx:
		var a: float = clampf(f.t / 0.45, 0.0, 1.0)
		draw_circle(Vector2(f.x, f.y), 12 + (0.45 - f.t) * 36, Color(1, 0.55 if f.kind != 2 else 0.27, 0.16, a))
	draw_set_transform(Vector2.ZERO, 0, Vector2.ONE)
	draw_rect(Rect2(0, 0, vs.x, 70), Color(0.02, 0.02, 0.04, 0.9))
	draw_string(ThemeDB.fallback_font, Vector2(14, 26), "جان", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color.WHITE)
	draw_rect(Rect2(52, 12, 180, 18), Color(0.14, 0.14, 0.16))
	var hp_w: float = 180.0 * float(php) / float(maxi(1, max_hp))
	draw_rect(Rect2(52, 12, hp_w, 18), Color(0.24, 0.78, 0.43) if php > max_hp * 0.35 else Color(0.86, 0.2, 0.2))
	draw_string(ThemeDB.fallback_font, Vector2(58, 26), "%d/%d" % [php, max_hp], HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color.WHITE)
	var wpn := "اسلحه×%d" % ammo if weapon == 2 else ("چاقو" if weapon == 1 else "دست‌خالی")
	draw_string(ThemeDB.fallback_font, Vector2(vs.x * 0.5 - 120, 26), "%s  |  دشمن %d/%d  |  %s" % [city, kills, need, wpn], HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color(0.8, 0.85, 0.9))
	draw_string(ThemeDB.fallback_font, Vector2(vs.x - 100, 26), "XP %d" % GameState.xp, HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color(0.86, 0.72, 0.31))
	draw_string(ThemeDB.fallback_font, Vector2(14, 52), GameState.CHAR_NAMES[GameState.selected_char], HORIZONTAL_ALIGNMENT_LEFT, -1, 14, GameState.CHAR_COLORS[GameState.selected_char])
	draw_string(ThemeDB.fallback_font, Vector2(100, 52), story, HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color.GRAY)
	if msg_t > 0.0:
		draw_rect(Rect2(vs.x * 0.5 - 220, vs.y * 0.12 - 22, 440, 44), Color(0, 0, 0, 0.75))
		draw_string(ThemeDB.fallback_font, Vector2(vs.x * 0.5 - 180, vs.y * 0.12 + 6), msg, HORIZONTAL_ALIGNMENT_LEFT, -1, 20, Color.WHITE)
	draw_circle(stick_center, stick_r, Color(1, 1, 1, 0.18))
	draw_arc(stick_center, stick_r, 0, TAU, 48, Color(1, 1, 1, 0.5), 2.0)
	draw_circle(stick_knob, 32, Color(0.96, 0.96, 0.98, 0.9))
	_act(btn_punch, "مشت", Color(0.86, 0.35, 0.24))
	_act(btn_kick, "لگد", Color(0.78, 0.55, 0.2))
	_act(btn_jump, "پرش", Color(0.31, 0.59, 0.86))
	_act(btn_slide, "سر", Color(0.31, 0.75, 0.63))
	_act(btn_special, "ویژه", Color(0.78, 0.27, 0.71))
	_act(btn_shield, "سپر", Color(0.47, 0.55, 0.75))

func _draw_result() -> void:
	var vs := get_viewport_rect().size
	draw_rect(Rect2(Vector2.ZERO, vs), Color(0.03, 0.05, 0.07))
	draw_rect(Rect2(vs.x * 0.5 - 220, vs.y * 0.5 - 120, 440, 250), Color(0, 0, 0, 0.86))
	draw_string(ThemeDB.fallback_font, Vector2(vs.x * 0.5 - 60, vs.y * 0.5 - 50), "پیروزی" if win else "شکست", HORIZONTAL_ALIGNMENT_LEFT, -1, 40, Color(0.27, 0.82, 0.47) if win else Color(0.86, 0.22, 0.22))
	draw_string(ThemeDB.fallback_font, Vector2(vs.x * 0.5 - 100, vs.y * 0.5), (city + " پاک شد") if win else "جانت تمام شد", HORIZONTAL_ALIGNMENT_LEFT, -1, 20, Color.WHITE)
	if win and auto_next_t > 0.0:
		draw_string(ThemeDB.fallback_font, Vector2(vs.x * 0.5 - 90, vs.y * 0.5 + 35), "مرحله بعد به‌زودی...", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color(0.86, 0.72, 0.31))
	_btn(Rect2(vs.x * 0.5 - 100, vs.y * 0.5 + 60, 200, 48), "منو")

func _btn(r: Rect2, lab: String) -> void:
	draw_rect(r, Color(0.11, 0.2, 0.16))
	draw_rect(r, Color(0.83, 0.69, 0.35), false, 2.0)
	draw_string(ThemeDB.fallback_font, r.position + Vector2(r.size.x * 0.25, r.size.y * 0.65), lab, HORIZONTAL_ALIGNMENT_LEFT, -1, 18, Color.WHITE)

func _act(r: Rect2, lab: String, col: Color) -> void:
	draw_circle(r.get_center(), r.size.x * 0.5, Color(col.r, col.g, col.b, 0.55))
	draw_arc(r.get_center(), r.size.x * 0.5, 0, TAU, 32, Color(1, 1, 1, 0.8), 2.0)
	draw_string(ThemeDB.fallback_font, r.get_center() + Vector2(-14, 5), lab, HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color.WHITE)

func _draw_fighter(x: float, y: float, col: Color, face_right: bool, sc: float, armed: bool, style: int) -> void:
	var s := sc
	var dir := 1.0 if face_right else -1.0
	draw_circle(Vector2(x, y + 6), 22 * s, Color(0, 0, 0, 0.22))
	draw_rect(Rect2(x - 14 * s, y - 8 * s, 12 * s, 12 * s), Color(0.12, 0.12, 0.14))
	draw_rect(Rect2(x + 2 * s, y - 8 * s, 12 * s, 12 * s), Color(0.12, 0.12, 0.14))
	var leg := col.darkened(0.25)
	draw_rect(Rect2(x - 11 * s, y - 32 * s, 9 * s, 26 * s), leg)
	draw_rect(Rect2(x + 2 * s, y - 32 * s, 9 * s, 26 * s), leg)
	draw_rect(Rect2(x - 16 * s, y - 62 * s, 32 * s, 34 * s), col)
	draw_rect(Rect2(x - 16 * s, y - 34 * s, 32 * s, 6 * s), Color(0.16, 0.16, 0.18))
	draw_circle(Vector2(x, y - 76 * s), 14 * s, Color(0.84, 0.69, 0.57))
	match style:
		0: draw_arc(Vector2(x, y - 76 * s), 14 * s, deg_to_rad(200), deg_to_rad(340), 16, Color(0.16, 0.35, 0.71), 6.0 * s)
		1:
			draw_circle(Vector2(x, y - 80 * s), 16 * s, Color(0.35, 0.2, 0.12))
			draw_circle(Vector2(x + 12 * s * dir, y - 60 * s), 10 * s, Color(0.35, 0.2, 0.12))
		2: draw_arc(Vector2(x, y - 76 * s), 14 * s, deg_to_rad(200), deg_to_rad(340), 16, Color(0.1, 0.1, 0.11), 6.0 * s)
		3:
			draw_arc(Vector2(x, y - 76 * s), 15 * s, deg_to_rad(200), deg_to_rad(360), 16, Color(0.78, 0.16, 0.16), 7.0 * s)
			draw_rect(Rect2(x + 5 * s * dir, y - 80 * s, 17 * s, 6 * s), Color(0.78, 0.16, 0.16))
		_:
			draw_arc(Vector2(x, y - 76 * s), 13 * s, deg_to_rad(200), deg_to_rad(340), 16, Color(0.12, 0.12, 0.14), 5.0 * s)
	draw_rect(Rect2(x - 24 * s * dir - 5, y - 58 * s, 12 * s, 28 * s), col)
	draw_rect(Rect2(x + 10 * s * dir - 5, y - 56 * s, 12 * s, 28 * s), col)
	draw_circle(Vector2(x + 26 * s * dir, y - 34 * s), 8 * s, Color(0.9, 0.75, 0.63))
	if armed:
		draw_rect(Rect2(x + 20 * s * dir, y - 52 * s, 20 * s, 5 * s), Color(0.75, 0.76, 0.78))

func _input(event: InputEvent) -> void:
	if event is InputEventScreenTouch or event is InputEventMouseButton:
		var pressed: bool
		var pos: Vector2
		var idx: int
		if event is InputEventScreenTouch:
			var st := event as InputEventScreenTouch
			pressed = st.pressed
			pos = st.position
			idx = st.index
		else:
			var mb := event as InputEventMouseButton
			if mb.button_index != MOUSE_BUTTON_LEFT: return
			pressed = mb.pressed
			pos = mb.position
			idx = 0
		if pressed: _on_down(pos, idx)
		else: _on_up(pos, idx)
	elif event is InputEventScreenDrag:
		var dr := event as InputEventScreenDrag
		if dr.index == stick_id: _move_stick(dr.position)
	elif event is InputEventMouseMotion and stick_id == 0:
		_move_stick((event as InputEventMouseMotion).position)

func _on_down(pos: Vector2, idx: int) -> void:
	var vs := get_viewport_rect().size
	match mode:
		Mode.MENU:
			if pos.y > vs.y - 120 and pos.y < vs.y - 68:
				if pos.x > vs.x * 0.05 and pos.x < vs.x * 0.05 + 130: preview = maxi(1, preview - 1)
				elif pos.x > vs.x * 0.25 and pos.x < vs.x * 0.25 + 150: mode = Mode.CHAR
				elif pos.x > vs.x * 0.48 and pos.x < vs.x * 0.48 + 160:
					if preview <= GameState.unlocked_stage: _start_stage(preview)
				elif pos.x > vs.x * 0.72 and pos.x < vs.x * 0.72 + 130: preview = mini(100, preview + 1)
		Mode.CHAR:
			var cell_w := (vs.x - 60.0) / 4.0
			for i in range(4):
				var cx := 30.0 + i * cell_w
				if pos.x > cx and pos.x < cx + cell_w - 14 and pos.y > 90 and pos.y < vs.y - 120:
					GameState.selected_char = i
			if pos.y > vs.y - 48 and pos.y < vs.y - 8 and pos.x > vs.x * 0.5 - 90 and pos.x < vs.x * 0.5 + 90:
				GameState.save()
				mode = Mode.MENU
		Mode.RESULT:
			if pos.y > vs.y * 0.5 + 60 and pos.y < vs.y * 0.5 + 108:
				auto_next_t = 0.0
				mode = Mode.MENU
		Mode.PLAY:
			if pos.x < vs.x * 0.38:
				stick_id = idx
				_move_stick(pos)
			else:
				_press(pos)

func _on_up(_pos: Vector2, idx: int) -> void:
	if idx == stick_id:
		stick_id = -1
		move_x = 0.0
		stick_knob = stick_center

func _move_stick(pos: Vector2) -> void:
	var d: Vector2 = pos - stick_center
	if d.length() > stick_r: d = d.normalized() * stick_r
	stick_knob = stick_center + d
	move_x = d.x / stick_r

func _press(pos: Vector2) -> void:
	if btn_punch.has_point(pos): _punch()
	elif btn_kick.has_point(pos): _kick()
	elif btn_jump.has_point(pos): _jump()
	elif btn_slide.has_point(pos): _slide()
	elif btn_special.has_point(pos): _special()
	elif btn_shield.has_point(pos): shielding = true

func _punch() -> void:
	if punch_cd > 0.0: return
	punch_cd = 0.2
	if weapon == 2 and ammo > 0:
		_shoot()
		return
	var dmg := 16 + GameState.CHAR_PWR[GameState.selected_char] * 4 + (10 if weapon == 1 else 0)
	_hit_front(72.0, dmg, 0.22)

func _kick() -> void:
	if kick_cd > 0.0: return
	kick_cd = 0.3
	_hit_front(88.0, 24 + GameState.CHAR_PWR[GameState.selected_char] * 3, 0.28)

func _jump() -> void:
	if not on_ground or jump_cd > 0.0: return
	jump_cd = 0.38
	on_ground = false
	jump_v = -530.0

func _slide() -> void:
	px += facing * (90.0 + GameState.CHAR_SPD[GameState.selected_char] * 8.0)
	_hit_front(95.0, 18, 0.2)

func _special() -> void:
	if special_cd > 0.0: return
	special_cd = 1.1
	_hit_front(120.0, 38 + GameState.CHAR_PWR[GameState.selected_char] * 5, 0.4)
	shake = 0.32
	_toast("حمله ویژه!")

func _shoot() -> void:
	if ammo <= 0: return
	ammo -= 1
	bullets.append({"x": px + facing * 35.0, "y": py - 50.0, "vx": facing * 720.0, "dmg": 20, "life": 0.75})

func _hit_front(reach: float, dmg: int, t: float) -> void:
	for e in enemies:
		if e.hp <= 0: continue
		var dx: float = e.x - px
		if dx * facing > 0.0 and absf(dx) < reach and absf(e.y - py) < 45.0:
			e.hp -= dmg
			e.x += facing * 28.0
			_fx(e.x, e.y - 45.0, t, 1)
			shake = 0.14
			if e.hp <= 0:
				e.hp = 0
				kills += 1
				_fx(e.x, e.y - 25.0, 0.4, 2)
