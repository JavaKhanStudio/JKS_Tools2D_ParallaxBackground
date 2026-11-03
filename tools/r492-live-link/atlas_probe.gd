extends SceneTree
## Prints what the Godot reader's parser reads of an atlas: atlas-parsers.sh runs it on the live link's generated atlas.


func _init() -> void:
	var path: String = OS.get_cmdline_user_args()[0]
	var atlas := PlaxAtlas.load_atlas(path)
	for r in atlas.regions:
		print("%s %d,%d %dx%d orig %dx%d" % [r.name, r.x, r.y, r.width, r.height, r.original_width, r.original_height])
	quit()
