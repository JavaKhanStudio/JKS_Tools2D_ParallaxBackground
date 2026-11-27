extends SceneTree
## Prints what the Godot reader's parser (PlaxAtlas) reads of each atlas named, in AtlasList.java's words:
## atlas-check.sh compares the two.


func _init() -> void:
	for path in OS.get_cmdline_user_args():
		print("== " + path)
		var atlas := PlaxAtlas.load_atlas(path)
		if atlas == null:
			continue
		for r in atlas.regions:
			print("%s at %d,%d size %dx%d orig %dx%d offset %d,%d rotate %s" % [r.name, r.x, r.y,
				r.width, r.height, r.original_width, r.original_height, r.offset_x, r.offset_y, str(r.rotate)])
	quit()
