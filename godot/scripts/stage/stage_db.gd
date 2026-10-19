extends Node
class_name StageDB

static var _cache: Array = []

static func all() -> Array:
	if _cache.is_empty():
		_cache = []
		for path in ["res://data/stages_part1.json", "res://data/stages_100.json", "res://data/stages_part2.json"]:
			if FileAccess.file_exists(path):
				var f := FileAccess.open(path, FileAccess.READ)
				if f:
					var json := JSON.new()
					if json.parse(f.get_as_text()) == OK and typeof(json.data) == TYPE_ARRAY:
						_cache.append_array(json.data)
		# fallback synthetic 100 stages if empty
		if _cache.is_empty():
			for i in range(1, 101):
				_cache.append({"n": i, "fa": "شهر %d" % i, "en": "City%d" % i, "enemies": 2 + i / 20, "xp": 100 + i * 8, "civilian": i % 7 == 0, "boss": i % 10 == 0, "brief": "پاک‌سازی مرحله %d" % i})
	return _cache

static func get_stage(n: int) -> Dictionary:
	var list := all()
	n = clampi(n, 1, maxi(1, list.size()))
	return list[n - 1]
