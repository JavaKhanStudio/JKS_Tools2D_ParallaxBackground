#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# sweep.sh [BUILD] — r261: creature.py over every AI strip on disk (none may be flagged) and over planted ones (each
# must be). The strips are scratch from r239/r242/r243/r246 runs (run.sh, run-xl.sh, ai-page.sh); a missing one is
# skipped. Prints FLAG/PASS per strip and exits 1 on a wrong answer. SHOW=DIR: creature.py --show's picture of each.
set -u
cd "$(dirname "$0")/../.."
[ -n "${SHOW:-}" ] && mkdir -p "$SHOW"
B="${1:-build}"
D="${R239_DIR:-tools/r239-ai-strips}" # mutate.sh points it at a mutated copy
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
bad=0
check() { # WANT(0|4) CUT [SRC]
	want=$1 cut=$2
	shift 2
	out=$(python3 $D/creature.py "$cut" ${1:+--src "$1"} ${SHOW:+--show "$SHOW/$(basename "$cut")"} 2>&1)
	rc=$?
	echo "$([ $rc = 4 ] && echo FLAG || echo PASS) $cut ${out:+($(echo "$out" | sed 's/.*: //' | paste -sd';'))}"
	[ $rc = "$want" ] || { echo "  WRONG: wanted $([ "$want" = 4 ] && echo a flag || echo none)"; bad=1; }
}
# WANT SRC OUT [layer.py args]: layer.py's cut of SRC, checked; layer.py's own exit (3: art on the top row) ignored
cut() {
	want=$1 src=$2 out=$3
	shift 3
	python3 $D/layer.py "$src" "$out" --no-creatures "$@" 2>/dev/null
	[ -f "$out" ] && check "$want" "$out" "$src"
}
# Today's layer.py on r239's strips (its own _layer.png files predate d454292's fill fix).
for k in mountains hills; do
	[ -f "$B/r239/${k}_ai.png" ] && cut 0 "$B/r239/${k}_ai.png" "$T/r239_$k.png"
done
for f in "$B"/r239/pinesw?.png "$B"/r239/pinesv_?.png; do
	[ -f "$f" ] && cut 0 "$f" "$T/r239_$(basename "$f")"
done
for f in "$B"/r242/run/*_layer.png "$B"/r246/*/strips/*_cut.png; do
	[ -f "$f" ] || continue
	src="${f%_cut.png}_ai.png" # ai-page.sh's names; r242's run.sh names are hills_ai.png, pinesw0.png
	[ -f "$src" ] || src="${f%_layer.png}_ai.png"
	[ -f "$src" ] || src="${f%_layer.png}.png"
	# The painted strip gives the sky's colour; pixel art (one pixel per art cell) is only seen there.
	check 0 "$f" "$src"
done
for k in hills mountains pines; do
	[ -f "$B/r243/${k}_xl.png" ] && check 0 "$B/r243/${k}_xlcut.png" "$B/r243/${k}_xl.png"
done
# Planted: each must be flagged.
plant() { # NAME SRC AT KIND COLOUR(- for plant.py's) [layer.py args]
	n=$1 src=$2 at=$3 kind=$4 col=$5
	shift 5
	[ -f "$src" ] || return 0
	[ "$col" = - ] && col=
	# shellcheck disable=SC2086 # $col is empty or "--colour HEX"
	python3 tools/r261-creature/plant.py "$src" "$T/p_$n.png" --at "$at" --kind "$kind" $col &&
		cut 4 "$T/p_$n.png" "$T/p_${n}_cut.png" "$@"
}
plant mountains_bird "$B/r239/mountains_ai.png" 300,300 bird -
plant hills_bird "$B/r239/hills_ai.png" 200,320 bird -
plant hills_deer "$B/r239/hills_ai.png" 600,330 deer -
plant pines_bird "$B/r239/pinesw1.png" 300,340 bird -
# brown: a third of a dark pine's colour is a deer nobody sees
plant pines_deer "$B/r239/pinesw1.png" 640,350 deer "--colour 7a5a3a"
S="$B/r246/snow/strips"
plant snow0_bird "$S/l0_ai.png" 300,300 bird - --mask "$S/l0_mask.png" --grow 8
plant snow3_bird "$S/l3_ai.png" 500,330 bird - --mask "$S/l3_mask.png" --grow 40
# Pixel art (r243): pixel.py looks at the painted SDXL strip, its art pixels 16 px cells; a bird 4 times the size
# (120 x 48 px, ~9 cells: under 6 is texture).
if [ -f "$B/r243/hills_xl.png" ]; then
	python3 tools/r261-creature/plant.py "$B/r243/hills_xl.png" "$T/p_xl_hills_bird.png" --at 700,470 --size 4
	python3 $D/pixel.py "$T/p_xl_hills_bird.png" "$T/p_xl_hills_bird_cut.png" --mask "$B/r243/hills_mask.png" 2>"$T/log"
	rc=$?
	echo "$([ $rc = 4 ] && echo FLAG || echo PASS) p_xl_hills_bird (pixel.py) $(grep creature "$T/log" | sed 's/.*: //')"
	[ $rc = 4 ] || { echo "  WRONG: wanted a flag (pixel.py exit $rc)"; bad=1; }
fi
[ $bad = 0 ] && echo "sweep: every answer right" || echo "sweep: WRONG answers above"
exit $bad
