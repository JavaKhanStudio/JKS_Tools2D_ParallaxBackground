#!/usr/bin/env bash
# r508: every still tools/depth-lab.sh makes (--pages, --angles, --shot), again and again under load. Each round's stills are kept in
# OUT/<arm>/roundNN; count.py then compares each with the median of its name.
#   tools/r508-stills/repeat.sh OUT ROUNDS [BURNERS, as many as there are cores; 0 when other work already loads the machine]
# Two arms side by side, same war, same load: noaa (what the tool does) and aa (DEPTH_AA=1, the multisampled control).
# headless: --pages, --angles and --shot only, and depth-lab.sh's headless Chrome goes through tools/browser.py; no window opens.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT=$(realpath -m "$1") ROUNDS=$2 BURNERS=${3:-$(nproc)}
BURN=() ARMS=()
trap 'kill "${BURN[@]}" "${ARMS[@]}" 2>/dev/null' EXIT
for _ in $(seq "$BURNERS"); do
	python3 -c 'while True: pass' &
	BURN+=($!)
done
arm() {
	for n in $(seq -w 1 "$ROUNDS"); do
		DIR="$OUT/$1/round$n"
		[ -f "$DIR/still.png" ] && continue # a stopped run goes on where it was: still.png is a round's last
		mkdir -p "$DIR"
		for mode in pages angles; do
			tools/depth-lab.sh --no-build "--$mode" "$DIR" >"$DIR/$mode.log" 2>&1 || echo "round $n $mode exit $?" >>"$OUT/$1/failed.txt"
		done
		tools/depth-lab.sh --no-build --shot "$DIR/still.png" >"$DIR/shot.log" 2>&1 || echo "round $n shot exit $?" >>"$OUT/$1/failed.txt"
		echo "$1 round $n, load $(cut -d' ' -f1 /proc/loadavg), $(date +%H:%M:%S)" >>"$OUT/progress.txt"
	done
}
mkdir -p "$OUT/aa" "$OUT/noaa"
DEPTH_AA=1 arm aa &
ARMS+=($!)
arm noaa &
ARMS+=($!)
wait "${ARMS[@]}"
kill "${BURN[@]}" 2>/dev/null
python3 tools/r508-stills/count.py "$OUT"
