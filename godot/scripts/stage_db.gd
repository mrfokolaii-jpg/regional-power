extends Node
class_name StageDB

static var _cache: Array = []

static func all() -> Array:
	if _cache.size() > 0:
		return _cache
	_cache = []
	for part in range(1, 5):
		var path := "res://data/stages_part%d.json" % part
		if FileAccess.file_exists(path):
			var f := FileAccess.open(path, FileAccess.READ)
			var data = JSON.parse_string(f.get_as_text())
			if typeof(data) == TYPE_ARRAY:
				for item in data:
					_cache.append(item)
	if _cache.is_empty():
		for i in range(1, 101):
			_cache.append({
				"n": i,
				"fa": "شهر %d" % i,
				"enemies": 2 + i / 12,
				"xp": 80 + i * 6,
				"brief": "پاک‌سازی منطقه را انجام دهید."
			})
	return _cache

static func get_stage(n: int) -> Dictionary:
	var list := all()
	if n < 1 or n > list.size():
		return {"n": n, "fa": "مرحله %d" % n, "enemies": 3, "xp": 100, "brief": "پاک‌سازی"}
	return list[n - 1]
