extends Node
## Godot's latency on the bench round (parallax-readers:r20), as shots/.../ParallaxBench times libGDX: for each
## scene its load time (page and atlas read, until its first frame is drawn) and the time of each of FRAMES frames
## after WARMUP unmeasured ones, vsync off, the frame rate unbounded. GDScript cannot finish the GPU's work as glFinish
## does, so a frame is timed by Godot's own clocks: act on the wall clock, plus the root viewport's measured render
## time on the CPU and on the GPU (RenderingServer.viewport_get_measured_render_time_cpu / _gpu), no swap. A scene's
## "transfer" starts at load and lasts its seconds. "gpuMs" is the GPU's share of the mean, proof its timer reads.
## Writes OUT_JSON in ParallaxBench's shape; engines/godot/tools/bench.sh runs it.
##   godot --path engines/godot res://tests/bench.tscn -- <repo root> <round dir> <out json>

const WARMUP := 60
const FRAMES := 600
const STEP := 1.0 / 60.0


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var root: String = args[0]
	var round = JSON.parse_string(FileAccess.get_file_as_string(root.path_join(args[1]).path_join("round.json")))
	var out: String = args[2]
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_DISABLED)
	Engine.max_fps = 0
	var vp := get_viewport().get_viewport_rid()
	RenderingServer.viewport_set_measure_render_time(vp, true)
	var bg := PlaxBackground.new()
	bg.auto_act = false
	add_child(bg)
	var scenes := []
	for scene in round.scenes:
		var start := Time.get_ticks_usec()
		bg.load_page(root.path_join(scene.page), root.path_join(scene.atlasDir))
		bg.reset_positions()
		bg.speed_constant_x = 60.0
		bg.speed_constant_y = scene.get("speedY", 0.0)
		var transfers = scene.get("transfer", [])
		for t in [transfers] if transfers is Dictionary else transfers:
			var page := PlaxPage.load_page(root.path_join(t.page))
			var atlas_dir: String = root.path_join(t.get("atlasDir", scene.atlasDir))
			bg.transfert_into(page, PlaxAtlas.load_atlas(atlas_dir.path_join(page.atlas_name)), t.seconds, null)
		var layers := bg.layers.size() + bg.transfer_layers.size()
		await RenderingServer.frame_post_draw
		var load_ms := (Time.get_ticks_usec() - start) / 1000.0
		var ms := PackedFloat64Array()
		var gpu := 0.0
		for f in WARMUP + FRAMES:
			var t0 := Time.get_ticks_usec()
			bg.act(STEP)
			var act_ms := (Time.get_ticks_usec() - t0) / 1000.0
			await RenderingServer.frame_post_draw
			if f >= WARMUP:
				gpu += RenderingServer.viewport_get_measured_render_time_gpu(vp)
				ms.append(act_ms + RenderingServer.viewport_get_measured_render_time_cpu(vp)
						+ RenderingServer.viewport_get_measured_render_time_gpu(vp))
		var mean := 0.0
		for v in ms:
			mean += v
		mean /= ms.size()
		ms.sort()
		scenes.append({"id": scene.id, "layers": layers, "loadMs": snappedf(load_ms, 0.01), "meanMs": snappedf(mean, 0.001),
				"p50Ms": snappedf(ms[FRAMES / 2], 0.001), "p95Ms": snappedf(ms[FRAMES * 95 / 100], 0.001),
				"maxMs": snappedf(ms[FRAMES - 1], 0.001), "gpuMs": snappedf(gpu / FRAMES, 0.001)})
		print("bench: ", scene.id, " ", mean, " ms")
	var json := {"reader": "godot", "renderer": RenderingServer.get_video_adapter_name(),
			"measure": "act + render CPU + render GPU, Godot's timers (no swap)", "frames": FRAMES, "scenes": scenes}
	var f := FileAccess.open(out, FileAccess.WRITE)
	f.store_string(JSON.stringify(json, " ") + "\n")
	f.close()
	get_tree().quit()
