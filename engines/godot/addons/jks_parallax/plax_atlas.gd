class_name PlaxAtlas
extends RefCounted
## A libGDX texture atlas (.atlas, the text file the editor and TexturePacker write), read without libGDX.
##
## Reads both layouts: the one before libGDX 1.9.13 ("xy:", "size:", "orig:", "offset:") and the one after it
## ("bounds:", "offsets:"). Regions keep the order libGDX gives them: file order, then sorted by "index" when any
## region has one, a region without an index last (TextureAtlasData does the same). A page names its layers by that
## order (see find_region), so it must not change.

## Every region, in libGDX's order. Each is a Dictionary:
## name, index, texture, rotate, x, y, width, height (packed, in pixels of the image),
## original_width, original_height, offset_x, offset_y (libGDX's: from the bottom left of the original image).
var regions: Array[Dictionary] = []

var _dir: String
var _page_path := ""
var _page_mipmaps := false
var _page_texture: Texture2D = null


## Reads the atlas at `path` (res://, user:// or an absolute path); its images are looked for next to it.
static func load_atlas(path: String) -> PlaxAtlas:
	if not FileAccess.file_exists(path):
		push_error("PlaxAtlas: no atlas at " + path)
		return null
	var atlas := PlaxAtlas.new()
	atlas._dir = path.get_base_dir()
	atlas._parse(FileAccess.get_file_as_string(path))
	return atlas


## The n-th region called `name`, counting from 0 in libGDX's order: how a page names its layers
## (regionName and regionPosition). Empty when there is none.
func find_region(name: String, position: int) -> Dictionary:
	var seen := 0
	for region in regions:
		if region.name == name:
			if seen == position:
				return region
			seen += 1
	return {}


func _parse(text: String) -> void:
	var region := {}
	var in_header := false
	for raw in text.split("\n"):
		var line := raw.strip_edges(false, true)
		if line.strip_edges().is_empty():
			# A blank line ends a page: the next line names the image of the next one.
			_close(region)
			region = {}
			_page_path = ""
			continue
		var indented := line.begins_with(" ") or line.begins_with("\t")
		var colon := line.find(":")
		if _page_path.is_empty():
			_page_path = _dir.path_join(line.strip_edges())
			_page_mipmaps = false
			_page_texture = null
			in_header = true
		elif in_header and not indented and colon >= 0:
			if line.substr(0, colon).strip_edges() == "filter":
				_page_mipmaps = line.contains("MipMap")
		elif not indented:
			_close(region)
			in_header = false
			if _page_texture == null:
				_page_texture = _load_texture(_page_path, _page_mipmaps)
			region = {"name": line.strip_edges(), "index": -1, "texture": _page_texture, "rotate": false,
				"x": 0, "y": 0, "width": 0, "height": 0,
				"original_width": 0, "original_height": 0, "offset_x": 0, "offset_y": 0}
		elif colon >= 0 and not region.is_empty():
			_field(region, line.substr(0, colon).strip_edges(), line.substr(colon + 1).strip_edges())
	_close(region)
	for r in regions:
		if r.index != -1:
			_stable_sort_by_index()
			break


func _field(region: Dictionary, key: String, value: String) -> void:
	var n := []
	for part in value.split(","):
		n.append(part.strip_edges().to_float())
	match key:
		"rotate":
			region.rotate = value == "true" or value == "90"
		"xy":
			region.x = n[0]; region.y = n[1]
		"size":
			region.width = n[0]; region.height = n[1]
		"bounds":
			region.x = n[0]; region.y = n[1]; region.width = n[2]; region.height = n[3]
		"orig":
			region.original_width = n[0]; region.original_height = n[1]
		"offset":
			region.offset_x = n[0]; region.offset_y = n[1]
		"offsets":
			region.offset_x = n[0]; region.offset_y = n[1]
			region.original_width = n[2]; region.original_height = n[3]
		"index":
			region.index = int(n[0])


func _close(region: Dictionary) -> void:
	if region.is_empty():
		return
	if region.original_width == 0 and region.original_height == 0:
		region.original_width = region.width
		region.original_height = region.height
	regions.append(region)


## By index, a region without one (-1) last, equal indexes in file order: sort_custom alone is not stable.
func _stable_sort_by_index() -> void:
	var keyed := []
	for i in regions.size():
		var index: int = regions[i].index
		keyed.append([index if index != -1 else 0x7fffffff, i, regions[i]])
	keyed.sort_custom(func(a, b): return a[0] < b[0] or (a[0] == b[0] and a[1] < b[1]))
	regions.clear()
	for k in keyed:
		regions.append(k[2])


## An imported resource when the project has one (res:// in a game), else the PNG read straight from disk.
static func _load_texture(image_path: String, mipmaps: bool) -> Texture2D:
	var texture: Texture2D = null
	if ResourceLoader.exists(image_path):
		texture = load(image_path) as Texture2D
	if texture == null:
		var image := Image.load_from_file(image_path)
		if image == null:
			push_error("PlaxAtlas: cannot read the image " + image_path)
			return null
		if mipmaps:
			image.generate_mipmaps()
		texture = ImageTexture.create_from_image(image)
	texture.set_meta("plax_mipmaps", mipmaps)
	return texture
