#!/usr/bin/env bash
# start-demo-check.sh — the Godot demo (engines/godot/demo.gd, the board's "Start the Godot demo") off screen in
# cage: SPACE and N pressed, one frame 3.5 s later in build/start-demo/godot-keys.png (Printemps, night tint).
# Needs godot 4.x, cage, Xwayland.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
GODOT="${GODOT:-godot}"
OUT="$ROOT/build/start-demo"
mkdir -p "$OUT"; rm -f "$OUT/godot-keys.png"
"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1 || true
RUN="$GODOT --path \"$ROOT/engines/godot\" --resolution 1280x720 res://tests/demo_keys.tscn -- \"$OUT/godot-keys.png\" >\"$OUT/godot-keys.log\" 2>&1"
inner="Xwayland :9 -geometry 1280x720 & X=\$!; sleep 2; DISPLAY=:9 $RUN; kill \$X 2>/dev/null"
WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 120 cage -- bash -c "$inner" >/dev/null 2>&1
grep -i "error" "$OUT/godot-keys.log" && { echo "FAIL: $OUT/godot-keys.log"; exit 1; }
[ -f "$OUT/godot-keys.png" ] || { echo "FAIL: no frame"; exit 1; }
echo "$OUT/godot-keys.png"
