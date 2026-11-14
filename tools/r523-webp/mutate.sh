#!/usr/bin/env bash
# mutate.sh [OUT] — r523: proves the WebP checks see a wrong decoder. Two mutants, each restored after:
#   swap   ParallaxWebp.fromArgb writes blue for red: ParallaxWebpTest and frame-check.sh must fail;
#   bare   WebpFrameCheck never calls ParallaxWebp.install(): libGDX alone must fail to load the WebP page.
# Then the control: both pass again. Exit 1 when a mutant passes or the control fails.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1
OUT=$(realpath -m "${1:-build/r523-mutate}")
mkdir -p "$OUT"
SRC=webp/src/jks/tools2d/parallax/webp/ParallaxWebp.java
RUNNER=webp/tests/jks/tools2d/parallax/webp/WebpFrameCheck.java
cp "$SRC" "$OUT/ParallaxWebp.java.orig"
cp "$RUNNER" "$OUT/WebpFrameCheck.java.orig"
restore() { cp "$OUT/ParallaxWebp.java.orig" "$SRC"; cp "$OUT/WebpFrameCheck.java.orig" "$RUNNER"; }
trap restore EXIT
bad=0

sed -i 's/bytes.put((byte) (c >>> 16));/bytes.put((byte) c); bytes.put((byte) (c >>> 8)); bytes.put((byte) (c >>> 16)); if (true) continue;/' "$SRC"
grep -q 'if (true) continue' "$SRC" || { echo "swap: mutant not applied"; exit 1; }
if ./gradlew -q :webp:test >"$OUT/swap-test.log" 2>&1; then echo "swap: ParallaxWebpTest PASSED (wrong)"; bad=1; else echo "swap: ParallaxWebpTest fails (right)"; fi
# Leaves the alpha as the red was: one byte too few a pixel, the image shifts. Either way the frames must differ.
if tools/r523-webp/frame-check.sh "" "$OUT/swap" >"$OUT/swap-frames.log" 2>&1; then echo "swap: frame check PASSED (wrong)"; bad=1; else echo "swap: frame check fails (right)"; fi
restore

sed -i 's/^\t\tParallaxWebp.install();/\t\t\/\/ mutant: not installed/' "$RUNNER"
grep -q 'mutant: not installed' "$RUNNER" || { echo "bare: mutant not applied"; exit 1; }
if tools/r523-webp/frame-check.sh "" "$OUT/bare" >"$OUT/bare-frames.log" 2>&1; then echo "bare: frame check PASSED (wrong)"; bad=1; else echo "bare: frame check fails (right): $(grep -m1 -o 'GdxRuntimeException.*' "$OUT/bare/check.log")"; fi
restore

if ./gradlew -q :webp:test >"$OUT/control-test.log" 2>&1; then echo "control: ParallaxWebpTest passes"; else echo "control: ParallaxWebpTest FAILS"; bad=1; fi
if tools/r523-webp/frame-check.sh "" "$OUT/control" >"$OUT/control-frames.log" 2>&1; then echo "control: frame check passes"; else echo "control: frame check FAILS"; bad=1; fi
exit $bad
