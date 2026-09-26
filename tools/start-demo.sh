#!/usr/bin/env bash
# start-demo.sh libgdx|godot — opens a demo window on the desktop: the board's "Start the demo" buttons (r102,
# .atelier/actions.toml).
#   libgdx  demo/ (ParallaxDemo): SPACE next page, N tint, LEFT/RIGHT scroll, R reset.
#   godot   engines/godot (demo.tscn): Hiver through the Godot reader, LEFT/RIGHT scroll.
# The board's server runs as a service, without the desktop's display variables: this script defaults them to the
# logged-in session's (Wayland socket wayland-0, Xwayland :0 and mutter's cookie), so the window opens on the screen.
# It returns when the window is closed.
set -euo pipefail
cd "$(dirname "$0")/.."
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
[ -S "$XDG_RUNTIME_DIR/wayland-0" ] && export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"
export DISPLAY="${DISPLAY:-:0}"
if [ -z "${XAUTHORITY:-}" ]; then
	for f in "$XDG_RUNTIME_DIR"/.mutter-Xwaylandauth.*; do [ -f "$f" ] && export XAUTHORITY="$f"; done
fi

case "${1:-}" in
	libgdx)
		./gradlew -q :demo:installDist
		cd demo/assets
		exec ../build/install/demo/bin/demo ;;
	godot)
		GODOT="${GODOT:-godot}"
		"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1 || true
		exec "$GODOT" --path engines/godot ;;
	*)
		echo "usage: tools/start-demo.sh libgdx|godot" >&2
		exit 2 ;;
esac
