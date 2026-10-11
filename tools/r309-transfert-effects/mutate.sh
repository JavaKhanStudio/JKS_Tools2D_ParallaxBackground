#!/usr/bin/env bash
# r309: proves ReaderCases' shared TransfertEffects still sees the dissolve and the grade. Puts in one mutant at a time
# in ParallaxPageReader (never ask the engine for a dissolve; never for a grade), runs BrowserSuiteTest for each, and
# fails unless every run FAILS on that style's cases. Restores the file on exit. Writes build/r309/mutant.log.
# headless: a JUnit run, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
R=core/src/jks/tools2d/parallax/ParallaxPageReader.java
SAVED=$(mktemp -d)
cp "$R" "$SAVED"
trap 'cp "$SAVED/ParallaxPageReader.java" "$R"; rm -rf "$SAVED"' EXIT
LOG=build/r309/mutant.log
mkdir -p build/r309
status=0
mutant() {
	# $1 what it is, $2 a case it must fail, $3 the text as it is, $4 the text mutated
	FROM="$3" TO="$4" python3 - "$R" <<'PY' || { echo "mutation did not apply: $1"; exit 2; }
import os, sys
path = sys.argv[1]
text = open(path).read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
	if ./gradlew :core:test --tests '*BrowserSuiteTest' -q >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes BrowserSuiteTest"; status=1
	elif grep -h -A1 "testcase name=\"reader: $2\"" core/build/test-results/test/TEST-*BrowserSuite*.xml | grep -q "<failure"; then
		echo "caught: $1 ($2 fails, $(grep -c "<failure" core/build/test-results/test/TEST-*BrowserSuite*.xml) cases in all)"
	else
		echo "FAILED ELSEWHERE: $1 did not fail $2, see $LOG"; status=1
	fi
	cp "$SAVED/ParallaxPageReader.java" "$R"
}
mutant "the reader never asks for a dissolve" dissolveMasksEachSlotOnlyWhileInTransferInEveryRepeatMode \
	'&& getLayerEffects().setDissolve(incoming' '&& false && getLayerEffects().setDissolve(incoming'
mutant "the reader never asks for a grade" throughColorGradesOneSideOfEachSlotInEveryRepeatMode \
	'&& getLayerEffects().setGrade(' '&& false && getLayerEffects().setGrade('
exit $status
