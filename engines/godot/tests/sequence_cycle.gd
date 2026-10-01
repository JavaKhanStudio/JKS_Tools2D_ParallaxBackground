extends SceneTree
## PlaxPage.draw_cycle against core's SequenceCycle (r183): the first picks ReaderCases.sequenceCycleOfAKnownSeedIsPinned
## pins, then every cycle of tests/sequence/picks.json (written by tools/r183-sequence/PickTable.java from the JVM's
## SequenceCycle: edge and random seeds, weights of every shape). Then PlaxBackground._draw_cycle, as ReaderCases holds
## ParallaxLayer.drawCycle: at many positions and pads, positive and going back, the slots it draws are the ones a walk
## over every slot finds in view, in order. Prints "sequence_cycle: PASS" or FAIL lines.
##   godot --headless --path engines/godot --script res://tests/sequence_cycle.gd

const PlaxPageScript := preload("res://addons/jks_parallax/plax_page.gd")

var _failures := 0


func _initialize() -> void:
	# Java's int of a state, as ReaderCases prints it.
	_equal(_signed(PlaxPageScript.sequence_next(1)), 270369, "xorshift32 (13, 17, 5) of 1")
	_equal(_cycle(42, [1, 2, 3], 12), [0, 1, 1, 2, 0, 2, 0, 2, 2, 2, 2, 2], "seed 42, weights 1 2 3")
	_equal(_cycle(-1640531527, [25, 50, 25], 12), [1, 1, 2, 0, 2, 0, 1, 0, 1, 2, 0, 0], "a negative seed, weights 25 50 25")
	_equal(_cycle(0, [1, 1], 8), [1, 0, 0, 0, 0, 1, 0, 1], "seed 0 starts from ZERO_SEED")
	_equal(_cycle(7, [0, 5, -3], 12), [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1], "only the weighted segment")
	_equal(PlaxPageScript.draw_cycle(5, PackedInt32Array([1]), 0).size(), 1, "a cycle holds one slot at least")

	var table = JSON.parse_string(FileAccess.get_file_as_string("res://tests/sequence/picks.json"))
	for pair in table.next:
		_equal(_signed(PlaxPageScript.sequence_next(int(pair[0]) & 0xFFFFFFFF)), int(pair[1]), "next(%d)" % int(pair[0]))
	for row in table.cycles:
		var weights := PackedInt32Array()
		for w in row.weights:
			weights.append(int(w))
		_equal(_cycle(int(row.seed), weights, row.cycle.size()), row.cycle.map(func(v): return int(v)),
				"seed %d, weights %s" % [int(row.seed), weights])
	var walks := _check_slots_in_view()
	print("sequence_cycle: %d cycles from the JVM, %d views walked, %s"
			% [table.cycles.size(), walks, "PASS" if _failures == 0 else "FAIL"])
	quit(1 if _failures else 0)


## Records what _draw_cycle draws instead of drawing it.
class Recorder extends PlaxBackground:
	var drawn: Array = []

	func _draw_region(l: Dictionary, x: float, _y: float, width: float, _height: float, _fx: bool, _fy: bool) -> void:
		drawn.append([l.id, x, width])


## A 24-slot sequence of segments 2, 0.75, 1.25 and 3.5 heights wide, its first (the layer) 14 wide: at pads from 3
## down to -12 (the slots going back after the narrowest from -5.25 on), seen through 0..40 from x -120 to 40.
func _check_slots_in_view() -> int:
	var bg := Recorder.new()
	var aspects := [2.0, 0.75, 1.25, 3.5]
	var walks := 0
	for pad in [3.0, 0.0, -2.0, -5.0, -5.5, -8.0, -12.0]:
		var segments: Array[Dictionary] = []
		for i in aspects.size():
			segments.append({"id": i, "aspect": aspects[i]})
		var l := {"model": {"kind": "SEQUENCE", "sequenceSeed": 183, "sequenceLength": 24, "padX": pad, "sizeRatio": 0.35},
			"segments": segments, "weights": PackedInt32Array([1, 3, 1, 1]), "image_w": 2.0, "image_h": 1.0,
			"narrowest": 0.75}
		bg._draw_cycle_of(l)
		var height: float = l.height
		for step in 161:
			var x := -120.0 + step
			bg.drawn = []
			bg._draw_cycle(l, x, 0, 0, 40, false, false)
			var expected := []
			for slot in l.cycle.size():
				var left: float = x + height * l.edges[slot] + slot * pad
				var width: float = height * aspects[l.cycle[slot]]
				if left < 40 and left + width > 0:
					expected.append([l.cycle[slot], left, width])
			walks += 1
			if bg.drawn != expected:
				_failures += 1
				print("FAIL: pad %s at x %s: drew %s, not %s" % [pad, x, bg.drawn, expected])
				break
	bg.free()
	return walks


func _cycle(seed: int, weights, length: int) -> Array:
	return Array(PlaxPageScript.draw_cycle(seed, PackedInt32Array(weights), length))


func _equal(actual, expected, what: String) -> void:
	if actual != expected:
		_failures += 1
		print("FAIL: %s: %s, not %s" % [what, actual, expected])


static func _signed(value: int) -> int:
	return value - 0x100000000 if value >= 0x80000000 else value
