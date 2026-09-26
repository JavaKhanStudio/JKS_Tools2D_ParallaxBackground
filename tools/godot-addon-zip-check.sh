#!/usr/bin/env bash
# godot-addon-zip-check.sh — installs the release zip (tools/godot-addon-zip.sh) into a blank Godot 4 project, the way a
# game would, loads Hiver from res:// with the README's snippet, and saves one frame: build/godot-addon/check.png.
# Fails when the page does not load or the frame is blank. Needs godot 4.x, cage, Xwayland, python3 with Pillow
# (ATELIER_NO_OFFSCREEN=1 draws on $DISPLAY instead of cage).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
GODOT="${GODOT:-godot}"
ZIP=$(tools/godot-addon-zip.sh)
P=$(mktemp -d)
trap 'rm -rf "$P"' EXIT

(cd "$P" && unzip -q "$ZIP")
mkdir -p "$P/backgrounds"
cp demo/lab/round1/s03.jplax "$P/backgrounds/hiver.jplax"
cp demo/assets/Hiver.atlas demo/assets/Hiver*.png "$P/backgrounds/"
cat > "$P/project.godot" <<'CFG'
config_version=5
[application]
config/name="Blank game"
run/main_scene="res://main.tscn"
config/features=PackedStringArray("4.6", "GL Compatibility")
[display]
window/size/viewport_width=1280
window/size/viewport_height=720
[rendering]
renderer/rendering_method="gl_compatibility"
CFG
cat > "$P/main.gd" <<'GD'
extends Node

func _ready() -> void:
	var bg := PlaxBackground.new()
	bg.load_page("res://backgrounds/hiver.jplax")
	bg.speed_constant_x = 60
	add_child(bg)
	await get_tree().create_timer(1.0).timeout
	get_viewport().get_texture().get_image().save_png(OS.get_cmdline_user_args()[0])
	get_tree().quit()
GD
printf '[gd_scene format=3]\n[ext_resource type="Script" path="res://main.gd" id="1"]\n[node name="Main" type="Node"]\nscript = ExtResource("1")\n' > "$P/main.tscn"

OUT="$ROOT/build/godot-addon"
rm -f "$OUT/check.png"
"$GODOT" --headless --path "$P" --import >/dev/null 2>&1 || true
RUN="$GODOT --path \"$P\" --resolution 1280x720 -- \"$OUT/check.png\" >\"$OUT/check.log\" 2>&1"
if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
	timeout 120 bash -c "$RUN"
else
	inner="Xwayland :9 -geometry 1280x720 & X=\$!; sleep 2; DISPLAY=:9 $RUN; kill \$X 2>/dev/null"
	WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 120 cage -- bash -c "$inner" >/dev/null 2>&1
fi
grep -i "error" "$OUT/check.log" && { echo "FAIL: Godot reported errors, $OUT/check.log"; exit 1; }
python3 - "$OUT/check.png" <<'PY'
import sys
from PIL import Image, ImageStat
im = Image.open(sys.argv[1]).convert('RGB')
spread = max(ImageStat.Stat(im).stddev)
print('%s  %dx%d  colour spread %.1f' % (sys.argv[1], im.width, im.height, spread))
sys.exit(0 if spread > 10 else 'FAIL: the frame is blank')
PY
echo "PASS: the release zip loads a page in a blank project"
