#!/usr/bin/env bash
# r304: proves the f-string rewrite of the probes changed nothing they print. Runs each probe as it is at REF (default
# HEAD) and as it is in the working tree on the same input, and diffs what they print; the browser ones against one
# served war, in headless Chrome. Exit 1 on any difference.
#   tools/r304-fstrings/same.sh [REF] [--no-build]
# headless: Playwright drives the system Chrome headless on SwiftShader, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
REF=HEAD BUILD=1
for a in "$@"; do if [ "$a" = --no-build ]; then BUILD=0; else REF=$a; fi; done
[ $BUILD = 0 ] || ./gradlew -q :core:browserTestWar >/dev/null || exit 1
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
WAR="$PWD/core/build/browserTest/war"
TMP=$(mktemp -d)
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null; rm -rf "$TMP"' EXIT
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/index.html')" 2>/dev/null && break; sleep 0.1; done
BASE="http://127.0.0.1:$PORT"
fail=0
# same NAME TOOL ARGS...: TOOL at REF and in the working tree; OUTDIR in ARGS is replaced by a directory of each side's.
# With MASK=1 every digit is compared as 9: the shape of the line (widths, precision), for a live measure that moves.
same() {
  local name=$1 tool=$2 mask=${MASK:-0}; shift 2
  git show "$REF:$tool" > "$TMP/$name-old.py"
  for side in old new; do
    local script="$TMP/$name-old.py"; [ $side = new ] && script=$PWD/$tool
    mkdir -p "$TMP/$name-$side.d"
    local args=("${@//OUTDIR/$TMP/$name-$side.d}")
    python3 "$script" "${args[@]}" 2>&1 | sed "s#$TMP/$name-$side.d#OUTDIR#g; s#\"$script\"#SCRIPT#" \
      | if [ "$mask" = 1 ]; then tr 0-9 9; else cat; fi > "$TMP/$name-$side.txt"
    echo "rc=${PIPESTATUS[0]}" >> "$TMP/$name-$side.txt"
  done
  if diff "$TMP/$name-old.txt" "$TMP/$name-new.txt"; then
    echo "SAME $name: $(wc -l < "$TMP/$name-new.txt") lines, $(tail -1 "$TMP/$name-new.txt")"
  else
    echo "DIFFERENT $name"; fail=1
  fi
}
for a in core/test-data/shaders/HiverFog.atlas core/test-data/samples/Hiver.atlas core/test-data/floor/HiverFloor.atlas; do
  same "atlas_edges-$(basename "$a" .atlas)" tools/r218-seams/atlas_edges.py "$a"
done
same overlap tools/r183-sequence/overlap.py
same shots tools/r232-lab-kit/shots.py "$BASE" "$CHROME" OUTDIR
# probe.py fails on every lab since r248 put choices in them, at line 13, after the line r304 changed: same failure.
same probe tools/r232-lab-kit/probe.py "$BASE" "$CHROME" "fog-lab.html?clip=1" fogLabReady 1580 710
same pick tools/r248-lab-choices/pick.py "$BASE" "$CHROME"
same planted tools/r275-floor/planted.py "$WAR" "$CHROME"
MASK=1 same drag tools/r275-floor/drag.py "$BASE/effects-lab.html?lab=floor" "$CHROME" OUTDIR
same film tools/r275-floor/film.py "$BASE/effects-lab.html?lab=floor&clip=1" "$CHROME" OUTDIR/floor.mp4 1
for side in old new; do ls "$TMP/film-$side.d" > "$TMP/film-$side.ls"; done
diff "$TMP/film-old.ls" "$TMP/film-new.ls" > /dev/null && echo "SAME film: $(cat "$TMP/film-new.ls")" || { echo "DIFFERENT film files"; fail=1; }
python3 "$(dirname "$0")/formats.py" || fail=1
exit $fail
