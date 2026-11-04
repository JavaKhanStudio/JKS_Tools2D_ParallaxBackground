#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# sweep.sh [FIXTURES] — r261, r505: creature.py over painted strips with nothing in them (none may be flagged) and over
# planted figures (each must be). Every strip is a whole one committed under tools/r261-creature/fixtures (its README
# says why whole, and where each came from), never build/, which is scratch and gets thinned (r505): a fixture that is
# missing is a wrong answer, never a skip. Prints FLAG/PASS per case and exits 1 on a wrong answer. SHOW=DIR:
# creature.py --show's picture of each. disk.sh holds creature.py to whatever else is on disk.
set -u
export OMP_NUM_THREADS=1
cd "$(dirname "$0")/../.." || exit 1
[ -n "${SHOW:-}" ] && mkdir -p "$SHOW"
F="${1:-tools/r261-creature/fixtures}"
D="${R239_DIR:-tools/r239-ai-strips}" # mutate.sh points it at a mutated copy
PY="${PYTHON:-python3}" # sweep_test.py: the pinned numpy and Pillow CI runs it under
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
bad=0 asked=0
need() { # FILE: a fixture that is gone fails the sweep, it does not shrink it
	[ -f "$1" ] && return 0
	echo "MISSING $1"
	echo "  WRONG: a fixture is gone"
	bad=1
	return 1
}
check() { # WANT(0|4) CUT SRC
	want=$1 cut=$2 src=$3
	need "$cut" && need "$src" || return
	asked=$((asked + 1))
	name=$(basename "$src" _ai.png)
	out=$("$PY" "$D"/creature.py "$cut" --src "$src" ${SHOW:+--show "$SHOW/$name.png"} 2>&1)
	rc=$?
	echo "$([ $rc = 4 ] && echo FLAG || echo PASS) $name ${out:+($(echo "$out" | sed 's/.*: //' | paste -sd';'))}"
	[ $rc = "$want" ] || { echo "  WRONG: wanted $([ "$want" = 4 ] && echo a flag || echo none) (exit $rc)"; bad=1; }
}
clean() { # NAME: a strip with nothing in it, as ai-page.sh cut it
	check 0 "$F/$1_cut.png" "$F/$1_ai.png"
}
painted() { # NAME: a figure SD painted, as ai-page.sh cut it
	check 4 "$F/$1_cut.png" "$F/$1_ai.png"
}
plant() { # NAME STRIP AT KIND COLOUR(- for plant.py's): planted on the painting, looked at through the strip's own cut
	n=$1 s=$2 at=$3 kind=$4 col=$5
	need "$F/${s}_ai.png" || return
	[ "$col" = - ] && col=
	# shellcheck disable=SC2086 # $col is empty or "--colour HEX --size N"
	if "$PY" tools/r261-creature/plant.py "$F/${s}_ai.png" "$T/${n}_ai.png" --at "$at" --kind "$kind" $col; then
		check 4 "$F/${s}_cut.png" "$T/${n}_ai.png"
	else
		echo "  WRONG: plant.py failed on $n"
		bad=1
	fi
}
recut() { # NAME STRIP AT KIND COLOUR GROW: planted, then cut by layer.py from sil.py's mask, as ai-page.sh would
	n=$1 s=$2 at=$3 kind=$4 col=$5 grow=$6
	need "$F/${s}_ai.png" && need "$F/${s}_mask.png" || return
	[ "$col" = - ] && col=
	# shellcheck disable=SC2086 # $col is empty or "--colour HEX --size N"
	"$PY" tools/r261-creature/plant.py "$F/${s}_ai.png" "$T/${n}_ai.png" --at "$at" --kind "$kind" $col ||
		{ echo "  WRONG: plant.py failed on $n"; bad=1; return; }
	# layer.py's own exit (3: art on the top row) ignored; no cut at all is a wrong answer
	"$PY" "$D"/layer.py "$T/${n}_ai.png" "$T/${n}_cut.png" --no-creatures --mask "$F/${s}_mask.png" --grow "$grow" 2>/dev/null
	need "$T/${n}_cut.png" && check 4 "$T/${n}_cut.png" "$T/${n}_ai.png"
}
pixel() { # STRIP AT: pixel.py names the planted bird and nothing on the strip alone (its own exit, not creature.py's)
	s=$1 at=$2
	need "$F/${s}_ai.png" && need "$F/${s}_mask.png" || return
	asked=$((asked + 1))
	"$PY" "$D"/pixel.py "$F/${s}_ai.png" "$T/${s}_cut.png" --mask "$F/${s}_mask.png" 2>"$T/log"
	rc=$?
	echo "$([ $rc = 4 ] && echo FLAG || echo PASS) $s (pixel.py) $(grep creature "$T/log" | sed 's/.*: //')"
	[ $rc = 0 ] || { echo "  WRONG: wanted none (pixel.py exit $rc)"; bad=1; }
	"$PY" tools/r261-creature/plant.py "$F/${s}_ai.png" "$T/${s}_bird_ai.png" --at "$at" --size 4 ||
		{ echo "  WRONG: plant.py failed on $s"; bad=1; return; }
	"$PY" "$D"/pixel.py "$T/${s}_bird_ai.png" "$T/${s}_bird_cut.png" --mask "$F/${s}_mask.png" 2>"$T/log"
	rc=$?
	echo "$([ $rc = 4 ] && echo FLAG || echo PASS) ${s}_bird (pixel.py) $(grep creature "$T/log" | sed 's/.*: //')"
	[ $rc = 4 ] || { echo "  WRONG: wanted a flag (pixel.py exit $rc)"; bad=1; }
}
# Nothing in them. Each one a rule's reason (mutate.sh; tools/r505-sweep-fixtures/matrix.py says which case kills what):
clean forest_l3v3      # a gap's foot onto the mass's edge; a spire over a tier
clean beach_l3         # palm crowns under the next frond
clean beach_l3v1       # a horizon's lit edge, off the line from sky to mass: never above the mass
clean postapo_l2       # flecks of one colour, shape and size
clean postapo_l3v1     # ragged chunks on spikes, no head's shape
clean dunes            # r438: a slip face, the sand's own shading
clean nova_l3v3       # R1's far towers, half sky: not in the outline
clean bir_l1           # R6a's rooftop clutter under masts sil.py keyed off
clean oj4_beach_l2     # palm fronds at the strip's top, too far from the mass to be its outline
clean ruins_a          # r500: chunks on a mast that never widens again
clean ruins_b          # r500: a tower's top wider than any head; half keyed
# Figures novaCartoonXL painted among ruins, the mass's own colour or too big for find()'s box (r362).
painted nova_l2        # standing, black on brown
painted nova_l3        # on a roof, sil.py's cut keyed its head off
# Planted in the mass: a third of the colour under it, the shading rule's luminance bound; a deer big enough to pull the
# box's mean toward itself, the second pass's reason; a pale grey at the sand's luminance, its hue bound.
plant forest_bird forest_l1 300,300 bird -
plant forest_deer forest_l1 620,330 deer -
plant beach_gull beach_l2 800,310 bird "--colour c8c8c8"
plant dunes_bird dunes 300,480 bird "--size 1.5"
plant dunes_deer dunes 700,520 deer "--size 1.5"
# In the colour of the mass's flecks, and of no fleck's shape and size: the crowd rule's bounds. And where the mass is half
# keyed (alpha under 0.95, read as mass from 0.5): a far range, or SD's creature keyed part clear.
plant half_bird postapo_l2 347,293 bird "--colour 6c5f58"
# Standing on the ridge (r349), cut by layer.py from sil.py's mask: keyed mostly clear, never inside the mass. Off the line
# from sky to mass: the dark one by its distance from the sky, the red one from the mass as well.
recut forest_person forest_l1 600,0 person "--colour 2a2420" 8
recut beach_person beach_l2 300,0 person "--colour 8a2a2a --size 0.8" 8
# On the outline, a head on a neck (r362), too big for the box: only on_neck sees it.
recut beach_figure beach_l3 520,0 figure "--colour 1f1e1e --size 3" 8
# Pixel art (r243): pixel.py looks at the painted strip, its art pixels 16 px cells; a bird 4 times the size (120 x 48 px,
# ~9 cells: under 6 is texture), and the strip alone as control.
pixel pixel_forest_l1 700,470
[ "$asked" -gt 0 ] || { echo "  WRONG: nothing was asked"; bad=1; }
[ $bad = 0 ] && echo "sweep: every answer right ($asked)" || echo "sweep: WRONG answers above"
exit $bad
