#!/usr/bin/env bash
# r278: proves the browser suite's dolly cases see a wrong dolly in core. Puts in one mutant at a time in
# ParallaxPageReader or DollyBatch, runs BrowserSuiteTest for each, and fails unless every run FAILS on the case named:
# every layer at the camera's zoom; layers placed from the zoomed view's corner; k squared; the effects begun on the
# DollyBatch; the layers' corner not handed to the effects; the particles' vertices not scaled. Restores the files on
# exit. Writes build/r278/mutant-core.log.
# headless: a JUnit run, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
DIR=core/src/jks/tools2d/parallax
SAVED=$(mktemp -d)
cp "$DIR/ParallaxPageReader.java" "$DIR/DollyBatch.java" "$SAVED"
restore() { cp "$SAVED/ParallaxPageReader.java" "$SAVED/DollyBatch.java" "$DIR"; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r278/mutant-core.log
XML=core/build/test-results/test/TEST-jks.tools2d.parallax.browsertest.BrowserSuiteTest.xml
mkdir -p build/r278
status=0
mutant() {
	# $1 the file, $2 what it is, $3 a case it must fail, $4 the text as it is, $5 the text mutated
	FROM="$4" TO="$5" python3 - "$DIR/$1" <<'PY' || { echo "mutation did not apply: $2"; exit 2; }
import os, sys
path = sys.argv[1]
text = open(path).read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
	if ./gradlew :core:test --tests '*BrowserSuiteTest' -q >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $2 passes BrowserSuiteTest"; status=1
	elif grep -A1 "testcase name=\"reader: $3\"" "$XML" | grep -q "<failure"; then
		echo "caught: $2 ($3 fails, $(grep -c "<failure" "$XML") cases in all)"
	else
		echo "FAILED ELSEWHERE: $2 did not fail $3, see $LOG"; status=1
	fi
	restore
}
mutant ParallaxPageReader.java "every layer at the camera's zoom" dollyScalesEachLayerByItsDepthAboutTheViewsMiddle \
	'float zoom = dolly ? dollyZoom(' 'float zoom = false ? dollyZoom('
mutant ParallaxPageReader.java "placed from the zoomed view's corner" dollyScalesEachLayerByItsDepthAboutTheViewsMiddle \
	'anchorLeft = dolly ? cameraX - viewportWidth / 2 : viewLeft;' 'anchorLeft = viewLeft;'
mutant ParallaxPageReader.java "k squared" dollyZoomFollowsTheLayersDepth \
	'return 1 - k * (1 - zoom);' 'return 1 - k * k * (1 - zoom);'
mutant ParallaxPageReader.java "the effects begun on the DollyBatch" dollyKeepsTheEffectsOnTheGamesBatchInTheLayersView \
	'drawImageLayer(layer, batch, drawn, originX, originY, incoming);' 'drawImageLayer(layer, drawn, drawn, originX, originY, incoming);'
mutant ParallaxPageReader.java "the layers' corner not handed to the effects" dollyKeepsTheEffectsOnTheGamesBatchInTheLayersView \
	'getLayerEffects().setView(viewWidth, viewHeight, anchorLeft - viewLeft, drawingHeight + anchorBottom - viewBottom);' \
	'getLayerEffects().setView(viewWidth, viewHeight, 0, drawingHeight);'
mutant DollyBatch.java "the particles' vertices not scaled" dollyScalesTheParticlesAboutTheViewsMiddle \
	'moved[i] = x(spriteVertices[offset + i]);' 'moved[i] = spriteVertices[offset + i];'
exit $status
