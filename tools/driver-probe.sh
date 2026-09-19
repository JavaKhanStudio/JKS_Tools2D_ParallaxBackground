#!/usr/bin/env bash
# driver-probe.sh — start the editor with its driver port, off Simon's screen, send it commands, stop it.
#
#   tools/driver-probe.sh [project] < commands.txt
#   echo "list" | tools/driver-probe.sh Files/Demos/OneNight.plaxpj
#
# One command per line (see EditorDriver's javadoc); each reply is printed after "> command".
# A line "sleep N" waits N seconds instead. "shot x.png" paths are relative to editor/.
# The editor runs in cage's headless display (WLR_BACKENDS=headless), from the installDist build:
# run `./gradlew :editor:installDist` first. ATELIER_NO_OFFSCREEN=1 shows the window instead.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
PORT=${DRIVER_PORT:-47777}
BIN="$ROOT/editor/build/install/ParallaxEditor/bin/ParallaxEditor"
[[ -x "$BIN" ]] || { echo "missing $BIN: run ./gradlew :editor:installDist" >&2; exit 2; }

cd "$ROOT/editor"
if [[ "${ATELIER_NO_OFFSCREEN:-0}" != "1" ]] && command -v cage >/dev/null; then
	WLR_BACKENDS=headless ALSOFT_DRIVERS=null cage -- "$BIN" --driver-port="$PORT" "$@" >"$ROOT/editor/build/driver-probe.log" 2>&1 &
else
	"$BIN" --driver-port="$PORT" "$@" >"$ROOT/editor/build/driver-probe.log" 2>&1 &
fi
EDITOR_PID=$!
trap 'kill $EDITOR_PID 2>/dev/null; wait $EDITOR_PID 2>/dev/null' EXIT
trap 'exit 130' INT TERM

for _ in $(seq 60); do
	(exec 3<>/dev/tcp/127.0.0.1/$PORT) 2>/dev/null && break
	sleep 1
done

exec 3<>/dev/tcp/127.0.0.1/$PORT || { echo "the editor never opened port $PORT; see editor/build/driver-probe.log" >&2; exit 1; }
while IFS= read -r line; do
	[[ -z "$line" ]] && continue
	if [[ "$line" == sleep\ * ]]; then sleep "${line#sleep }"; continue; fi
	echo "> $line"
	echo "$line" >&3
	IFS= read -r reply <&3
	echo "$reply"
	# "list" answers "ok N" then N lines.
	if [[ "$line" == list && "$reply" == ok\ * ]]; then
		for _ in $(seq "${reply#ok }"); do IFS= read -r more <&3; echo "$more"; done
	fi
done
