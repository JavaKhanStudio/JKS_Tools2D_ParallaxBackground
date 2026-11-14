#!/usr/bin/env bash
# shots.sh ROOT OUT [CASE...] — every headless mode of the ten tools/*-lab.sh launchers (r528), run from checkout ROOT
# on its already built WAR (--no-build), into OUT/<case>/ with each run's output and exit code. Run it on a HEAD
# worktree, copy the changed launchers in, run it again into another OUT, and compare.py the two: pixel-identical
# stills and the same exit codes say the launchers' behaviour did not change. CASE picks some (default: all).
# BREAK=1 serves each lab page broken: its start throws where it sets window.<name>LabReady (a setter that throws, put
# in its <head>), as a lab whose Java fails does; every case must then fail. ROOT's WAR is restored after.
# headless: every launcher run here is given a headless mode (--shot, --views, --copy, ...), never the open one.
set -u
ROOT=$(realpath "$1") OUT=$(realpath -m "$2")
shift 2
CASES=(
	"anim-shot anim-lab.sh --shot @/a.png"
	"anim-views anim-lab.sh --views @"
	"anim-copy anim-lab.sh --copy @/a.png"
	"anim-slider anim-lab.sh --slider zoom=1.5 --shot @/a.png"
	"anim-clip anim-lab.sh --clip @/a.mp4"
	"clouds-shot clouds-lab.sh --shot @/a.png"
	"clouds-views clouds-lab.sh --views @"
	"clouds-copy clouds-lab.sh --copy @/a.png"
	"fog-shot fog-lab.sh --shot @/a.png"
	"fog-sets fog-lab.sh --sets @"
	"fog-copy fog-lab.sh --copy @/a.png"
	"haze-shot haze-lab.sh --shot @/a.png"
	"haze-same haze-lab.sh --same @/a.png"
	"haze-copy haze-lab.sh --copy @/a.png"
	"hills-shot hills-lab.sh --shot @/a.png"
	"hills-views hills-lab.sh --views @"
	"hills-copy hills-lab.sh --copy @/a.png"
	"pines-shot pines-lab.sh --shot @/a.png"
	"pines-views pines-lab.sh --views @"
	"pines-copy pines-lab.sh --copy @/a.png"
	"shadow-shot shadow-lab.sh --shot @/a.png"
	"shadow-views shadow-lab.sh --views @"
	"shadow-behind shadow-lab.sh --behind --shot @/a.png"
	"shadow-copy shadow-lab.sh --copy @/a.png"
	"transfert-sheet transfert-lab.sh --sheet @"
	"transfert-copy transfert-lab.sh --copy @/a.png"
	"depth-shot depth-lab.sh --shot @/a.png"
	"depth-nolab depth-lab.sh --slider nothere=1 --shot @/a.png"
	"effects-fog effects-lab.sh --lab fog --shot @/a.png"
	"effects-snow effects-lab.sh --lab snow --shot @/a.png"
	"effects-all effects-lab.sh --lab all --shot @/a.png"
	"effects-floor effects-lab.sh --lab floor --shot @/a.png"
	"effects-tower effects-lab.sh --lab tower --shot @/a.png"
	"effects-planet effects-lab.sh --lab planet --shot @/a.png"
	"effects-lurker effects-lab.sh --lab lurker --shot @/a.png"
	"effects-swell effects-lab.sh --lab swell --shot @/a.png"
	"effects-copy effects-lab.sh --lab tower --copy @/a.png"
	"effects-nolab effects-lab.sh --lab fog --slider nothere=1 --shot @/a.png"
)
WAR="$ROOT/core/build/browserTest/war"
if [ "${BREAK:-0}" = 1 ]; then
	mkdir -p "$OUT"
	cp -a "$WAR" "$OUT/war-saved"
	trap 'rm -rf "$WAR" && mv "$OUT/war-saved" "$WAR"' EXIT
	for f in "$WAR"/*-lab.html; do
		name=$(basename "$f" -lab.html)
		sed -i "s#<head>#<head><script>Object.defineProperty(window, '${name}LabReady', { set() { throw new Error('r528 broken on purpose'); } });</script>#" "$f"
	done
fi
for c in "${CASES[@]}"; do
	read -r name script rest <<<"$c"
	if [ $# -gt 0 ] && ! printf '%s\n' "$@" | grep -qx "$name"; then continue; fi
	dir="$OUT/$name"
	rm -rf "$dir" && mkdir -p "$dir"
	# shellcheck disable=SC2086
	(cd "$ROOT" && timeout 900 tools/$script ${rest//@/$dir} --no-build) >"$dir/log" 2>&1
	echo $? >"$dir/exit"
	echo "$name: exit $(cat "$dir/exit")"
done
