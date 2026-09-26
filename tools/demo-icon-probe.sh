#!/usr/bin/env bash
# Proves the demo window wears the logo (r49): runs the installed demo headless in cage, on a nested
# Xwayland, and reads the window's _NET_WM_ICON, the property GLFW fills from setWindowIcon.
# Prints the icon's size and exits 0 when it is set, 1 when it is not. Needs cage, Xwayland, xprop.
#   ./gradlew :demo:installDist && tools/demo-icon-probe.sh [shot.png]
set -u
cd "$(dirname "$0")/.."
BIN="$PWD/demo/build/install/demo/bin/demo"
SHOT="${1:-}"
OUT=$(mktemp -d)
inner=$(cat <<EOF
Xwayland :9 -geometry 1280x720 & X=\$!
sleep 2
( cd demo/assets && DISPLAY=:9 "$BIN" >"$OUT/demo.log" 2>&1 ) & D=\$!
sleep 8
DISPLAY=:9 xprop -name "Parallax demo" -len 16 -f _NET_WM_ICON 32cc ' = \$0, \$1\n' _NET_WM_ICON >"$OUT/icon.txt" 2>&1
[ -n "$SHOT" ] && ffmpeg -loglevel error -y -f x11grab -video_size 1280x720 -i :9 -frames:v 1 "$SHOT"
kill \$D \$X 2>/dev/null
EOF
)
WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 40 cage -- bash -c "$inner" >"$OUT/cage.log" 2>&1
if grep -qE "_NET_WM_ICON.* = [0-9]+, [0-9]+" "$OUT/icon.txt" 2>/dev/null; then
  echo "icon set, first image $(grep -oE "[0-9]+, [0-9]+" "$OUT/icon.txt" | head -1 | sed "s/, / x /") px"
  exit 0
fi
echo "no _NET_WM_ICON on the demo window: $(cat "$OUT/icon.txt" 2>/dev/null)"; echo "demo log:"; tail -20 "$OUT/demo.log"
exit 1
