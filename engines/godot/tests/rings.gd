extends SceneTree
## PlaxRings' numbers for each scene of a rings round (parallax-readers r49): the page loaded by PlaxBackground (out of
## the tree: its world 16:9), the scene's options over PlaxRings.ring_options (fogStrength left out, the page's own),
## PlaxRings.bands and gradient, written as OUT/<id>.json in the shape engines/rounds/reader's RingsRound writes core's
## (a still ring's radius null), for tools/compare-rings.py. Prints "rings: PASS" (every scene written) or FAIL lines.
## engines/godot/tools/frame-check.sh runs it whenever it checks engines/rounds/rings:
##   godot --headless --path engines/godot --script res://tests/rings.gd -- <repo root> <round dir> <out dir>

const KEYS := ["front", "far", "foot", "lift", "scale", "pageDegrees", "fogStrength"]


func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	var root: String = args[0]
	var round_dir: String = root.path_join(args[1])
	var out: String = args[2]
	DirAccess.make_dir_recursive_absolute(out)
	var scenes: Array = JSON.parse_string(FileAccess.get_file_as_string(round_dir.path_join("round.json"))).scenes
	var failed := 0
	for s in scenes:
		var bg := PlaxBackground.new()
		bg.load_page(root.path_join(s.page), root.path_join(s.atlasDir))
		if bg.page == null:
			print("FAIL %s: %s did not load" % [s.id, s.page])
			failed += 1
			bg.free()
			continue
		var given := {"fogStrength": bg.page.fog_strength}
		for k in KEYS:
			if s.get("rings", {}).has(k):
				given[k] = s.rings[k]
		var o := PlaxRings.ring_options(given)
		var bands := PlaxRings.bands(bg.layers, bg.world_width, o)
		for band in bands:
			if band.radius == INF:
				band.radius = null
		var numbers := {
			"options": o,
			"gradient": PlaxRings.gradient(bg.page.top_half_size, bg.page.bottom_half_size, bg.world_width, o),
			"bands": bands,
		}
		var file := FileAccess.open(out.path_join(s.id + ".json"), FileAccess.WRITE)
		file.store_string(JSON.stringify(numbers, " ", false, true))
		file.close()
		print("%s: %d bands" % [s.id, bands.size()])
		bg.free()
	print("rings: %d scenes, %s" % [scenes.size(), "FAIL" if failed else "PASS"])
	quit(1 if failed else 0)
