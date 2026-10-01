#!/usr/bin/env bash
# headless: runs :core:test only, opens no window.
# r182: breaks the SEQUENCE code one way at a time and checks BrowserSuiteTest (ReaderCases) fails on each.
# Restores every file after each run. Usage: tools/r182-sequence/mutate.sh
set -u
cd "$(dirname "$0")/../.."
LAYER=core/src/jks/tools2d/parallax/ParallaxLayer.java
READER=core/src/jks/tools2d/parallax/ParallaxPageReader.java
CYCLE=core/src/jks/tools2d/parallax/SequenceCycle.java

# Put back whatever file is mutated if the script is stopped, a pipe into head included.
restore() { for f in "$LAYER" "$READER" "$CYCLE"; do [ -f "$f.r182" ] && mv "$f.r182" "$f"; done; }
trap restore EXIT INT TERM PIPE

if ! ./gradlew :core:test --tests '*BrowserSuiteTest' -q > /dev/null 2>&1; then
	echo "the unmutated code fails: fix that first"
	exit 1
fi

mutate() # name file python-old python-new
{
	local name=$1 file=$2 old=$3 new=$4
	cp "$file" "$file.r182"
	python3 - "$file" "$old" "$new" <<'PY'
import sys
path, old, new = sys.argv[1:]
s = open(path).read()
if s.count(old) != 1:
    sys.exit("not found once: " + old)
open(path, 'w').write(s.replace(old, new))
PY
	[ $? -eq 0 ] || { echo "NOT APPLIED: $name"; mv "$file.r182" "$file"; return; }
	if ./gradlew :core:test --tests '*BrowserSuiteTest' -q > /dev/null 2>&1; then
		echo "PASSED (bad): $name"
	else
		echo "FAILED (good): $name"
	fi
	mv "$file.r182" "$file"
}

# Dropping only the break walks every slot and draws the same: a cost the draws cannot show, so not mutated here.
mutate "the walk draws past the view" "$LAYER" "			if (left >= toX)
			{" "			if (false)
			{"
mutate "the walk draws slots left of the view" "$LAYER" "			if (left + width <= fromX)
				continue;" ""
mutate "the search starts one slot late" "$LAYER" "					first = middle + 1;" "					first = middle + 2;"
mutate "no padX between slots" "$LAYER" "			float left = x + height * cycleEdges[slot] + slot * padX;" "			float left = x + height * cycleEdges[slot];"
mutate "every segment as wide as the first" "$LAYER" "			segmentAspect[i] = i == 0 ? regionWidth / regionHeight : imageWidth / imageHeight;" "			segmentAspect[i] = regionWidth / regionHeight;"
mutate "xorshift 12 instead of 13" "$CYCLE" "		state ^= state << 13;" "		state ^= state << 12;"
mutate "a signed shift" "$CYCLE" "		state ^= state >>> 17;" "		state ^= state >> 17;"
mutate "the pick without the unsigned halving" "$CYCLE" "			int pick = (state >>> 1) % (total > 0 ? total : weights.length);" "			int pick = Math.abs(state) % (total > 0 ? total : weights.length);"
mutate "the incoming page ignores the game's seed" "$READER" "			drawCycle(incoming);
" ""
mutate "no XOR with the page's seed" "$READER" "sequenceSeed ^ layer.getSequenceSeed()" "sequenceSeed"
mutate "mirror flips the wrong axis" "$READER" "mirror && !onX ? !layer.flipX : layer.flipX, mirror && onX ? !layer.flipY : layer.flipY" "mirror && onX ? !layer.flipX : layer.flipX, mirror && !onX ? !layer.flipY : layer.flipY"
