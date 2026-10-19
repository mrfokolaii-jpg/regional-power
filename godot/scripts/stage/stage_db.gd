extends Node
class_name StageDB

static var _cache: Array = []

static func all() -> Array:
	if _cache.is_empty():
		var f := FileAccess.open("res://data/stages_100.json", FileAccess.READ)
		if f:
			var json := JSON.new()
			if json.parse(f.get_as_text()) == OK:
				_cache = json.data
	return _cache

static func get_stage(n: int) -> Dictionary:
	var list := all()
	n = clampi(n, 1, list.size())
	return list[n - 1]
