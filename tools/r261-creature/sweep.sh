#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# sweep.sh [BUILD] — r261: creature.py over every AI strip on disk (none may be flagged) and over planted ones (each
# must be). The strips are scratch from r239/r242/r243/r246 runs (run.sh, run-xl.sh, ai-page.sh), and parallax-ai's
# R1 and R6a rounds (R362_R1, beside this checkout); a missing one is skipped. Prints FLAG/PASS per strip and exits 1 on a wrong answer. SHOW=DIR: creature.py --show's picture of each.
set -u
cd "$(dirname "$0")/../.." || exit 1
[ -n "${SHOW:-}" ] && mkdir -p "$SHOW"
B="${1:-build}"
D="${R239_DIR:-tools/r239-ai-strips}" # mutate.sh points it at a mutated copy
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
bad=0
check() { # WANT(0|4) CUT [SRC]
	want=$1 cut=$2
	shift 2
	out=$(python3 "$D"/creature.py "$cut" ${1:+--src "$1"} ${SHOW:+--show "$SHOW/$(basename "$cut")"} 2>&1)
	rc=$?
	echo "$([ $rc = 4 ] && echo FLAG || echo PASS) $cut ${out:+($(echo "$out" | sed 's/.*: //' | paste -sd';'))}"
	[ $rc = "$want" ] || { echo "  WRONG: wanted $([ "$want" = 4 ] && echo a flag || echo none)"; bad=1; }
}
# WANT SRC OUT [layer.py args]: layer.py's cut of SRC, checked; layer.py's own exit (3: art on the top row) ignored
cut() {
	want=$1 src=$2 out=$3
	shift 3
	python3 "$D"/layer.py "$src" "$out" --no-creatures "$@" 2>/dev/null
	[ -f "$out" ] && check "$want" "$out" "$src"
}
# Today's layer.py on r239's strips (its own _layer.png files predate bcb8df6's fill fix).
for k in mountains hills; do
	[ -f "$B/r239/${k}_ai.png" ] && cut 0 "$B/r239/${k}_ai.png" "$T/r239_$k.png"
done
for f in "$B"/r239/pinesw?.png "$B"/r239/pinesv_?.png; do
	[ -f "$f" ] && cut 0 "$f" "$T/r239_$(basename "$f")"
done
# r349: ai-page.sh's examples (r239's six, painted and pixel) and r346's SDXL pages, painted at 1536 px.
for f in "$B"/r242/run/*_layer.png "$B"/r246/*/strips/*_cut.png "$B"/ai/examples/*/strips/*_cut.png \
	"$B"/r346/turbo/strips/*_cut.png; do
	[ -f "$f" ] || continue
	src="${f%_cut.png}_ai.png" # ai-page.sh's names; r242's run.sh names are hills_ai.png, pinesw0.png
	[ -f "$src" ] || src="${f%_layer.png}_ai.png"
	[ -f "$src" ] || src="${f%_layer.png}.png"
	# The painted strip gives the sky's colour; pixel art (one pixel per art cell) is only seen there.
	check 0 "$f" "$src"
done
# r438: DreamShaperXL Turbo's beach (r437), its dunes' slip faces a crescent of shadow on each crest: none a creature.
for f in "$B"/r437/beach/strips/l?_cut.png "$B"/r437/dunes/s*_cut.png; do
	[ -f "$f" ] && check 0 "$f" "${f%_cut.png}_ai.png"
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
# On those dunes (r438): a dark bird and deer (the shading rule's luminance bound) and a pale grey gull at the sand's
# own luminance (its hue bound).
D2="$B/r437/beach/strips"
plant dunes_bird "$D2/l2_ai.png" 300,480 bird - --mask "$D2/l2_mask.png" --grow 8
plant dunes_deer "$D2/l2_ai.png" 700,520 deer - --mask "$D2/l2_mask.png" --grow 8
plant dunes_gull "$D2/l2_ai.png" 1200,470 bird "--colour c8c8c8" --mask "$D2/l2_mask.png" --grow 8
# Standing on the ridge (r349): keyed mostly clear, never inside the mass. novaCartoonXL's people on its hills (r346),
# and planted ones on r239's hills and r246's snowy mountains.
N="$B/r346/nova/strips"
[ -f "$N/l1_ai.png" ] && cut 4 "$N/l1_ai.png" "$T/nova_l1_cut.png" --mask "$N/l1_mask.png" --grow 8
# With sil.py's mask, as ai-page.sh cuts: without one, a dark figure keys opaque and is the mass's own edge.
plant hills_person "$B/r239/hills_ai.png" 600,0 person "--colour 2a2420" --mask "$B/r239/hills_mask.png" --grow 8
plant hills_red_person "$B/r239/hills_ai.png" 300,0 person "--colour 8a2a2a --size 0.8" \
	--mask "$B/r239/hills_mask.png" --grow 8
plant snow0_person "$S/l0_ai.png" 700,0 person "--colour 3a2a20" --mask "$S/l0_mask.png" --grow 8
# A figure the mass's own colour, or too big for find()'s box (r362): on_neck's round head on a thin neck.
# novaCartoonXL's on parallax-ai's R1 ruins (l2 standing, black on brown; l3 on a roof, sil.py's cut keyed its head
# off), and one planted on r239's ruins, cut by colour: sil.py's mask keys a planted figure off whole, harmlessly.
R1="${R362_R1:-../JKS_Tools2D_ParallaxAI/build/rounds/r1/nova-postapo/strips}"
for l in l2 l3; do
	[ -f "$R1/${l}_cut.png" ] && check 4 "$R1/${l}_cut.png" "$R1/${l}_ai.png"
done
# and none in R1's far towers, half sky (l3v3), in palms whose fronds reach the strip's top (r239-ex's beach2), nor in
# rooftop clutter under masts sil.py keyed off (parallax-ai's R6a, bir-postapo l1: the painted strip has them)
[ -f "$R1/l3v3_cut.png" ] && check 0 "$R1/l3v3_cut.png" "$R1/l3v3_ai.png"
R6="${R1%/r1/*}/r6a/bir-postapo/strips"
[ -f "$R6/l1_cut.png" ] && check 0 "$R6/l1_cut.png" "$R6/l1_ai.png"
P="$B/r239-ex/beach2-painted/strips"
[ -f "$P/l3v3_cut.png" ] && check 0 "$P/l3v3_cut.png" "$P/l3v3_ai.png"
plant postapo_figure "$B/ai/examples/postapo-painted/strips/l3_ai.png" 520,0 figure "--colour 1f1e1e --size 3"
# Pixel art (r243): pixel.py looks at the painted SDXL strip, its art pixels 16 px cells; a bird 4 times the size
# (120 x 48 px, ~9 cells: under 6 is texture).
if [ -f "$B/r243/hills_xl.png" ]; then
	python3 tools/r261-creature/plant.py "$B/r243/hills_xl.png" "$T/p_xl_hills_bird.png" --at 700,470 --size 4
	python3 "$D"/pixel.py "$T/p_xl_hills_bird.png" "$T/p_xl_hills_bird_cut.png" --mask "$B/r243/hills_mask.png" 2>"$T/log"
	rc=$?
	echo "$([ $rc = 4 ] && echo FLAG || echo PASS) p_xl_hills_bird (pixel.py) $(grep creature "$T/log" | sed 's/.*: //')"
	[ $rc = 4 ] || { echo "  WRONG: wanted a flag (pixel.py exit $rc)"; bad=1; }
fi
[ $bad = 0 ] && echo "sweep: every answer right" || echo "sweep: WRONG answers above"
exit $bad
