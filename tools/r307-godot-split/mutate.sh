#!/usr/bin/env bash
# r307: proves the particles round still sees Godot's particle placement after it moved out of plax_background.gd into
# plax_particles.gd. Puts in one mutant (a VIEW layer's emitter offset moved the wrong way), runs
# engines/godot/tools/frame-check.sh engines/rounds/particles, and fails unless that run FAILS. Restores the file on
# exit. Writes build/r307/mutant.log. The floor's half (plax_floor.gd) is tools/r275-floor/mutate.sh's.
# headless: engines/godot/tools/frame-check.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
P=engines/godot/addons/jks_parallax/plax_particles.gd
SAVED=$(mktemp -d)
cp "$P" "$SAVED"
trap 'cp "$SAVED/plax_particles.gd" "$P"; rm -rf "$SAVED"' EXIT
LOG=build/r307/mutant.log
mkdir -p build/r307
FROM='-(l.travel_x - decal_x) * ppu, (l.travel_y - decal_y) * ppu, 0)' \
TO='(l.travel_x - decal_x) * ppu, (l.travel_y - decal_y) * ppu, 0)' python3 - "$P" <<'PY' || { echo "mutation did not apply"; exit 2; }
import os, sys
path = sys.argv[1]
text = open(path).read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
if engines/godot/tools/frame-check.sh engines/rounds/particles >"$LOG" 2>&1; then
	echo "NOT CAUGHT: the VIEW emitter moved the wrong way passes the particles round"; exit 1
fi
echo "caught: the VIEW emitter moved the wrong way ($(grep -c '^FAIL q' "$LOG") checks failed)"
